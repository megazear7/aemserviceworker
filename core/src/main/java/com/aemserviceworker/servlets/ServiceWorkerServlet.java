package com.aemserviceworker.servlets;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.osgi.service.component.annotations.Component;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import javax.servlet.Servlet;
import org.osgi.framework.Constants;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.List;

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

    private static final String CONFIG_BORDER = "___";
    private static final String CONFIG_VERSION = "version";
    private static final String CONFIG_PRECACHE_FILES = "files";
    private static final String CONFIG_PREFIXED_PRECACHE_FILES = "prefixedFiles";
    private static final String CONFIG_IGNORE_DEPTH = "ignoreDepth";
    private static final String CONFIG_PREFIX_DEPTH = "prefixDepth";
    private static final String PROP_CUSTOM_TEMPLATE = "template";
    private static final String PROP_STRATEGY = "strategy";
    private static final String STRATEGY_PRECACHE = "precache";
    private static final String STRATEGY_LOCALIZED_PRECACHE = "localizedPrecache";
    private static final String STRATEGY_CUSTOM = "custom";
    private static final String SW_PRECACHE = "const PRECACHE='___version___';const PRECACHE_URLS=[___files___];self.addEventListener('install',event=>{event.waitUntil(caches.open(PRECACHE).then(cache=>cache.addAll(PRECACHE_URLS)).then(self.skipWaiting()))});self.addEventListener('activate',event=>{const currentCaches=[PRECACHE];event.waitUntil(caches.keys().then(cacheNames=>{return cacheNames.filter(cacheName=>!currentCaches.includes(cacheName))}).then(cachesToDelete=>{return Promise.all(cachesToDelete.map(cacheToDelete=>{return caches.delete(cacheToDelete)}))}).then(()=>self.clients.claim()))});self.addEventListener('fetch',event=>{if(event.request.url.startsWith(self.location.origin)){event.respondWith(caches.match(event.request).then(cachedResponse=>{if(cachedResponse){return cachedResponse}return fetch(event.request)}))}})";

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        try {
            final String strategy = request.getResource().getValueMap().get(PROP_STRATEGY, String.class);
            String sw;

            if (STRATEGY_PRECACHE.equals(strategy)) {
                sw = precacheSw(request.getResource());
            } else if (STRATEGY_LOCALIZED_PRECACHE.equals(strategy)) {
                sw = localizedPrecacheSw(request.getResource());
            } else if (STRATEGY_CUSTOM.equals(strategy)) {
                sw = customSw(request.getResource());
            } else {
                throw new ServiceWorkerException("Service worker config at " + request.getResource().getPath() + " had no strategy set");
            }

            response.setContentType("text/javascript");
            response.getWriter().write(sw);
        } catch(Exception e) {
            LOG.error("ServiceWorkerServlet error", e);
        }
    }

    private String precacheSw(final Resource resource) {
        if (resource.getValueMap().containsKey(CONFIG_VERSION) && resource.getValueMap().containsKey(CONFIG_PRECACHE_FILES)) {
            final String version = resource.getValueMap().get(CONFIG_VERSION, String.class);
            final String precacheFiles = Arrays.asList(resource.getValueMap().get(CONFIG_PRECACHE_FILES, String[].class)).stream()
                    .map(str -> "'" + str + "'")
                    .collect(Collectors.joining(","));

            return SW_PRECACHE
                    .replace(CONFIG_BORDER + CONFIG_VERSION + CONFIG_BORDER, version)
                    .replace(CONFIG_BORDER + CONFIG_PRECACHE_FILES + CONFIG_BORDER, precacheFiles);
        } else {
            throw new ServiceWorkerException("Service worker config at " + resource.getPath() + " was set to precache strategy but is either missing " + CONFIG_VERSION + " or " + CONFIG_PRECACHE_FILES + ". Make sure this service worker config has all of these properties set.");
        }
    }

    private String localizedPrecacheSw(final Resource resource) {
        if (resource.getValueMap().containsKey(CONFIG_VERSION) && resource.getValueMap().containsKey(CONFIG_PRECACHE_FILES) &&
                resource.getValueMap().containsKey(CONFIG_PREFIXED_PRECACHE_FILES) && resource.getValueMap().containsKey(CONFIG_IGNORE_DEPTH) &&
                resource.getValueMap().containsKey(CONFIG_PREFIX_DEPTH)) {
            final String version = resource.getValueMap().get(CONFIG_VERSION, String.class);
            final Integer ignoreDepth = resource.getValueMap().get(CONFIG_IGNORE_DEPTH, Integer.class);
            final Integer prefixDepth = resource.getValueMap().get(CONFIG_PREFIX_DEPTH, Integer.class);
            final String prefix = "/" + Arrays.asList(Arrays.copyOfRange(resource.getPath().split("/"), ignoreDepth + 1, ignoreDepth + 1 + prefixDepth)).stream().collect(Collectors.joining("/"));
            final List<String> precacheFiles = Arrays.asList(resource.getValueMap().get(CONFIG_PRECACHE_FILES, String[].class)).stream()
                    .map(str -> "'" + str + "'").collect(Collectors.toList());
            final List<String> localizedPrecacheFiles = Arrays.asList(resource.getValueMap().get(CONFIG_PREFIXED_PRECACHE_FILES, String[].class)).stream()
                    .map(str -> "'" + prefix + str + "'").collect(Collectors.toList());

            final List<String> files = new ArrayList<>();
            files.addAll(precacheFiles);
            files.addAll(localizedPrecacheFiles);

            return SW_PRECACHE
                    .replace(CONFIG_BORDER + CONFIG_VERSION + CONFIG_BORDER, version)
                    .replace(CONFIG_BORDER + CONFIG_PRECACHE_FILES + CONFIG_BORDER, files.stream().collect(Collectors.joining(",")));
        } else {
            throw new ServiceWorkerException("Service worker config at " + resource.getPath() + " was set to precache strategy but is missing one of: " + CONFIG_VERSION + ", " + CONFIG_PRECACHE_FILES + ", " + CONFIG_IGNORE_DEPTH + ", " + CONFIG_PREFIX_DEPTH + ", " + CONFIG_PREFIXED_PRECACHE_FILES + ". Make sure this service worker config has all of these properties set.");
        }
    }

    private String customSw(final Resource resource) {
        if (resource.getValueMap().containsKey(PROP_CUSTOM_TEMPLATE)) {
            String sw = resource.getValueMap().get(PROP_CUSTOM_TEMPLATE, String.class);

            for (String config : resource.getValueMap().keySet()) {
                if (config.startsWith(CONFIG_BORDER) && config.endsWith(CONFIG_BORDER)) {
                    sw = sw.replace(config, resource.getValueMap().get(config, ""));
                }
            }

            return sw;
        } else {
            throw new ServiceWorkerException("Service worker config at " + resource.getPath() + " was set to custom strategy but contained no custom template. Please add a custom service worker template to the " + PROP_CUSTOM_TEMPLATE + " variable.");
        }
    }

    public class ServiceWorkerException extends RuntimeException {
        public ServiceWorkerException(String errorMessage) {
            super(errorMessage);
        }
    }
}
