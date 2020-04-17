package com.aemserviceworker.servlets;

import com.aemserviceworker.config.SWConfig;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import com.aemserviceworker.services.ServiceWorkerService;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.caconfig.ConfigurationBuilder;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.Servlet;

@Component(service=Servlet.class,
        property={
                Constants.SERVICE_DESCRIPTION + "=Contextual Service Worker Servlet",
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET,
                ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=sling/servlet/default",
                ServletResolverConstants.SLING_SERVLET_SELECTORS + "=sw",
                ServletResolverConstants.SLING_SERVLET_EXTENSIONS + "=js"
        })
public class ContextualServiceWorkerServlet extends SlingAllMethodsServlet {
    private static final long serialVersionUID = 2598426539166789515L;
    private static final Logger LOG = LoggerFactory.getLogger(ContextualServiceWorkerServlet.class);

    @Reference
    private ServiceWorkerService serviceWorkerService;

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        SWConfig config = request.getResource().adaptTo(ConfigurationBuilder.class).as(SWConfig.class);

        try {
            response.setContentType("text/javascript");
            response.getWriter().write(serviceWorkerService.getServiceWorker(config, request.getResource().getPath(), request.getResourceResolver()));
        } catch(Exception e) {
            LOG.error("ServiceWorkerServlet error", e);
        }
    }
}
