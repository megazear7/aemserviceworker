package com.aemserviceworker.servlets;

import com.aemserviceworker.services.ServiceWorkerService;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.osgi.service.component.annotations.Component;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import javax.servlet.Servlet;
import org.osgi.framework.Constants;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.aemserviceworker.services.ServiceWorkerService.STRATEGY_CUSTOM;
import static com.aemserviceworker.services.ServiceWorkerService.STRATEGY_LOCALIZED_PRECACHE;
import static com.aemserviceworker.services.ServiceWorkerService.STRATEGY_PRECACHE;

@Component(service=Servlet.class,
        property={
                Constants.SERVICE_DESCRIPTION + "=Service Worker Servlet",
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET,
                ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=aemserviceworker/serviceworker",
                ServletResolverConstants.SLING_SERVLET_EXTENSIONS + "=js"
        })
public class ServiceWorkerServlet extends SlingAllMethodsServlet {
    private static final long serialVersionUID = 2598426539166789515L;
    private static final Logger LOG = LoggerFactory.getLogger(ServiceWorkerServlet.class);
    private static final String PROP_STRATEGY = "strategy";

    @Reference
    private ServiceWorkerService serviceWorkerService;

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        try {
            final String strategy = request.getResource().getValueMap().get(PROP_STRATEGY, String.class);
            String sw;

            if (STRATEGY_PRECACHE.equals(strategy)) {
                sw = serviceWorkerService.precacheSw(request.getResource());
            } else if (STRATEGY_LOCALIZED_PRECACHE.equals(strategy)) {
                sw = serviceWorkerService.localizedPrecacheSw(request.getResource());
            } else if (STRATEGY_CUSTOM.equals(strategy)) {
                sw = serviceWorkerService.customSw(request.getResource());
            } else {
                throw new ServiceWorkerServletException("Service worker config at " + request.getResource().getPath() + " had no strategy set");
            }

            response.setContentType("text/javascript");
            response.getWriter().write(sw);
        } catch(Exception e) {
            LOG.error("ServiceWorkerServlet error", e);
        }
    }

    public class ServiceWorkerServletException extends RuntimeException {
        public ServiceWorkerServletException(String errorMessage) {
            super(errorMessage);
        }
    }
}
