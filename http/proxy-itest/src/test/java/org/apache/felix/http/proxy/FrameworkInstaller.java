package org.apache.felix.http.proxy;

import org.apache.felix.framework.Felix;
import org.apache.felix.main.AutoProcessor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Starts Felix and and attaches the BundleContext to the ServletContext.
 */
public class FrameworkInstaller implements ServletContextListener {

	private final File workingDir;
	private final List<String> bundlesToDeploy;
	private Felix felix;

	public FrameworkInstaller(File workingDir, String... bundlesToDeploy) {
		this.workingDir = workingDir;
		this.bundlesToDeploy = bundlesToDeploy != null ?
				Arrays.asList(bundlesToDeploy) :
				Collections.emptyList();
	}

	public BundleContext getBundleContext() {
		return requireNonNull(felix).getBundleContext();
	}

	@Override public void contextInitialized(ServletContextEvent sce) {
		Map<Object, Object> config = new HashMap<>();
		config.put("felix.cache.rootdir", workingDir.getAbsolutePath());
		config.put("org.osgi.framework.storage", "storage");
		config.put("org.osgi.framework.system.packages.extra", extraExports()); // Export javax.servlet.

		if (!bundlesToDeploy.isEmpty()) {
			config.put(AutoProcessor.AUTO_START_PROP, bundlesToDeploy.stream().collect(Collectors.joining(" ")));
		}

		felix = new Felix(config);
		try {
			felix.start();
			sce.getServletContext().setAttribute(BundleContext.class.getName(), felix.getBundleContext());
			// Install bundles

			AutoProcessor.process(config, felix.getBundleContext());
		}
		catch (BundleException e) {
			throw new RuntimeException(e);
		}
	}

	private String extraExports() {
		List<String> packages = Arrays.asList("javax.servlet", "javax.servlet.http",
				"javax.servlet.annotation", "javax.servlet.descriptor");
		return packages.stream().map(s -> s + ";version=3.1.0").collect(Collectors.joining(","));
	}

	@Override public void contextDestroyed(ServletContextEvent sce) {
		if (felix != null) {
			try {
				felix.stop();
			}
			catch (BundleException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
