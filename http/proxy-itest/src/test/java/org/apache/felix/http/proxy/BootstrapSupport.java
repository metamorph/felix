package org.apache.felix.http.proxy;

import org.apache.felix.http.proxy.impl.ProxyServletContextListener;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.AbstractConfiguration;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Base class used to start jetty with an embedded OSGi container.
 */
public class BootstrapSupport {

	@Rule
	public TemporaryFolder folders = new TemporaryFolder();

	private Server jettyServer = null;
	private Supplier<Integer> jettyPortSupplier = null;
	private Supplier<BundleContext> bundleContextSupplier = null;

	private void applyUrlHandlerPackages() {
		System.setProperty("java.protocol.handler.pkgs", "org.ops4j.pax.url");
		String localRepo =  Optional.ofNullable(System.getProperty("maven.repo.local"))
				.map(input -> {
					// Abuse this to trigger the side-effect.
					System.setProperty("org.ops4j.pax.url.mvn.localRepository", input);
					return input;
				}).orElse(null);
		if (localRepo != null) {
			System.out.println("Using local Maven repo: " + localRepo);
		}
	}

	@Before
	public void startJetty() throws Exception {
		applyUrlHandlerPackages();

		// Maybe a 'rule' for the Jetty server would be cleaner.
		jettyServer = new Server();
		final ServerConnector connector = new ServerConnector(jettyServer);
		jettyServer.addConnector(connector);

		FrameworkInstaller installer = new FrameworkInstaller(folders.newFolder("root"),
				"mvn:org.apache.felix/org.apache.felix.log",
				"mvn:org.apache.felix/org.apache.felix.http.bridge/3.0.7-SNAPSHOT",
				"mvn:org.apache.felix/org.apache.felix.http.whiteboard/3.0.1-SNAPSHOT",
				"mvn:javax.annotation/javax.annotation-api/1.2");

		WebAppContext context = new WebAppContext("WebApp", "/");
		context.setConfigurations(new Configuration[]{new AbstractConfiguration() {
			@Override public void configure(WebAppContext context) throws Exception {
				// Add a listener that will install the OSGi framework
				context.addEventListener(new ProxyServletContextListener());
				context.addEventListener(installer);
				// Add the proxy servlet.
				ServletHolder holder = new ServletHolder(ProxyServlet.class);
				holder.setInitOrder(1); // Needed to have the initializer called.
				context.addServlet(holder, getProxyServletPath());
			}
		}});

		jettyServer.setHandler(context);
		jettyServer.start();

		jettyPortSupplier = connector::getLocalPort;
		bundleContextSupplier = installer::getBundleContext;
	}

	/**
	 * Make a server-local request to the jetty-server.
	 * @param request .
	 * @param callback .
	 * @throws IOException .
	 */
	protected void request(HttpRequest request, Consumer<HttpResponse> callback) throws IOException {
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			HttpHost host = new HttpHost("localhost", getJettyPort(), "http");
			try (CloseableHttpResponse resp = client.execute(host, request)) {
				callback.accept(resp);
			}
		}
	}


	@After
	public void stopJetty() throws Exception {
		if (jettyServer != null) {
			jettyServer.stop();
		}
	}

	protected BundleContext getBundleContext() {
		return bundleContextSupplier.get();
	}

	protected String getProxyServletPath() {
		return "/*";
	}

	protected int getJettyPort() {
		return jettyPortSupplier.get();
	}

	protected String getContextPath() {
		return "/";
	}


}
