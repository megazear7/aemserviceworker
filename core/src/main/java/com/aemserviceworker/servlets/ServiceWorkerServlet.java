package com.aemserviceworker.servlets;

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
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

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

    private static final String PROP_VERSION = "version";
    private static final String DEFAULT_VERSION = "precache_v1";
    private static final String CONFIG_VERSION = "___VERSION___";
    private static final String PROP_PRECACHE_FILES = "precacheFiles";
    private static final String[] DEFAULT_PRECACHE_FILES = new String[0];
    private static final String CONFIG_PRECACHE_FILES = "___PRECACHE_FILES___";
    private static final String STRATEGY_PRECACHE = "const PRECACHE='___VERSION___';const PRECACHE_URLS=[___PRECACHE_FILES___];self.addEventListener('install',event=>{event.waitUntil(caches.open(PRECACHE).then(cache=>cache.addAll(PRECACHE_URLS)).then(self.skipWaiting()))});self.addEventListener('activate',event=>{const currentCaches=[PRECACHE];event.waitUntil(caches.keys().then(cacheNames=>{return cacheNames.filter(cacheName=>!currentCaches.includes(cacheName))}).then(cachesToDelete=>{return Promise.all(cachesToDelete.map(cacheToDelete=>{return caches.delete(cacheToDelete)}))}).then(()=>self.clients.claim()))});self.addEventListener('fetch',event=>{if(event.request.url.startsWith(self.location.origin)){event.respondWith(caches.match(event.request).then(cachedResponse=>{if(cachedResponse){return cachedResponse}return fetch(event.request)}))}})";

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) {
        try {
            final String version = request.getResource().getValueMap().get(PROP_VERSION, DEFAULT_VERSION);
            final String precacheFiles = Arrays.asList(request.getResource().getValueMap().get(PROP_PRECACHE_FILES, DEFAULT_PRECACHE_FILES)).stream()
                    .map(str -> "'" + str + "'").collect(Collectors.joining(","));
            final String sw = STRATEGY_PRECACHE.replace(CONFIG_VERSION, version).replace(CONFIG_PRECACHE_FILES, precacheFiles);

            response.setContentType("text/javascript");
            response.getWriter().write(sw);
        } catch(Exception e) {
            LOG.error("ServiceWorkerServlet error", e);
        }
    }
}
