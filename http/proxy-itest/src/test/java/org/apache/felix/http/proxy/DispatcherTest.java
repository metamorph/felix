package org.apache.felix.http.proxy;

import org.apache.felix.framework.util.MapToDictionary;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;
import org.osgi.util.tracker.ServiceTracker;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Verifies that the proxy+bridge passes on requests to servlets deployed to the
 * OSGi container.
 */
public class DispatcherTest extends BootstrapSupport {

	public static class SampleServlet extends HttpServlet {
		@Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
				throws ServletException, IOException {
			resp.setContentType("text/plain");
			resp.getWriter().print("Hello");
		}
	}

	@Override protected String getProxyServletPath() {
		return "/osgi/*";
	}

	@Test
	public void shouldDispatchFromRootServlet() {
		// TODO: To support different jetties, add a `withJetty(args, Consumer<IntegrationContext>)` that starts and stops jetty.
		// The `IntegrationContext` would be a way to execute the http-request and to access the bundleContext.
	}

	@Test
	public void shouldDispatchFromRelativeServlet() {

	}

	@Test
	public void smokeTest() throws IOException, InterruptedException, ServletException, NamespaceException {
		Dictionary<String, Object> props = new Hashtable<>();
		props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/foo");
		getBundleContext().registerService(Servlet.class, new SampleServlet(), props);

		// Rely on synchronous whiteboard registration.

		request(new BasicHttpRequest("GET", "/osgi/foo"),
				r -> {
					try {
						assertEquals(r.getStatusLine().getReasonPhrase(), 200, r.getStatusLine().getStatusCode());
						assertEquals("Hello", EntityUtils.toString(r.getEntity()));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
		);
	}

}
