package com.aemserviceworker.services.impl;

import com.aemserviceworker.services.ServiceWorkerService;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.apache.sling.api.resource.Resource;
import com.aemserviceworker.config.SWConfig;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = ServiceWorkerService.class)
public class ServiceWorkerServiceImpl implements ServiceWorkerService {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceWorkerServiceImpl.class);
    private static final String CONFIG_BORDER = "___";
    private static final String CONFIG_VERSION = "version";
    private static final String CONFIG_PRECACHE_FILES = "files";
    private static final String CONFIG_PREFIXED_PRECACHE_FILES = "prefixedFiles";
    private static final String CONFIG_IGNORE_DEPTH = "ignoreDepth";
    private static final String CONFIG_PREFIX_DEPTH = "prefixDepth";
    private static final String PROP_CUSTOM_TEMPLATE = "template";
    private static final String SW_PRECACHE = "const PRECACHE='___version___';const PRECACHE_URLS=[___files___];self.addEventListener('install',event=>{event.waitUntil(caches.open(PRECACHE).then(cache=>cache.addAll(PRECACHE_URLS)).then(self.skipWaiting()))});self.addEventListener('activate',event=>{const currentCaches=[PRECACHE];event.waitUntil(caches.keys().then(cacheNames=>{return cacheNames.filter(cacheName=>!currentCaches.includes(cacheName))}).then(cachesToDelete=>{return Promise.all(cachesToDelete.map(cacheToDelete=>{return caches.delete(cacheToDelete)}))}).then(()=>self.clients.claim()))});self.addEventListener('fetch',event=>{if(event.request.url.startsWith(self.location.origin)){event.respondWith(caches.match(event.request).then(cachedResponse=>{if(cachedResponse){return cachedResponse}return fetch(event.request)}))}})";

    @Override
    public String getServiceWorker(final Resource resource) {
        final String strategy = resource.getValueMap().get(PROP_STRATEGY, String.class);

        if (STRATEGY_PRECACHE.equals(strategy)) {
            return precacheSw(resource);
        } else if (STRATEGY_LOCALIZED_PRECACHE.equals(strategy)) {
            return localizedPrecacheSw(resource);
        } else if (STRATEGY_CUSTOM.equals(strategy)) {
            return customSw(resource);
        } else {
            throw new ServiceWorkerException("Service worker config at " + resource.getPath() + " had no strategy set");
        }
    }

    @Override
    public String getServiceWorker(final SWConfig config, final String path, final ResourceResolver rr) {
        final String strategy = config.strategy();

        if (STRATEGY_PRECACHE.equals(strategy)) {
            return precacheSw(config);
        } else if (STRATEGY_LOCALIZED_PRECACHE.equals(strategy)) {
            return localizedPrecacheSw(path, config);
        } else if (STRATEGY_CUSTOM.equals(strategy)) {
            return customSw(config, rr);
        } else {
            throw new ServiceWorkerException("Service worker context aware config for resource: " + path + " had no strategy set");
        }
    }

    @Override
    public String precacheSw(final Resource resource) {
        if (resource.getValueMap().containsKey(CONFIG_VERSION) && resource.getValueMap().containsKey(CONFIG_PRECACHE_FILES)) {
            final String version = resource.getValueMap().get(CONFIG_VERSION, String.class);
            final List<String> files = Arrays.asList(resource.getValueMap().get(CONFIG_PRECACHE_FILES, String[].class));

            return precacheSw(version, files);
        } else {
            throw new ServiceWorkerException("Service worker config at " + resource.getPath() + " was set to precache strategy but is missing one of the following required properties: " + CONFIG_VERSION + " or " + CONFIG_PRECACHE_FILES + ". Make sure this service worker config has all of these properties set if using the 'precache' strategy.");
        }
    }

    @Override
    public String precacheSw(final SWConfig config) {
        if (config.version() != null && config.files() != null) {
            return precacheSw(config.version(), Arrays.asList(config.files()));
        } else {
            throw new ServiceWorkerException("Context aware config for ServiceWorkerServiceImpl is using the 'precache' strategy but is missing one of the following required properties: " + CONFIG_VERSION + " or " + CONFIG_PRECACHE_FILES + ". Make sure this service worker config has all of these properties set if using the 'precache' strategy.");
        }
    }

    @Override
    public String precacheSw(final String version, final List<String> files) {
        if (StringUtils.isNotBlank(version) && files != null) {
            return renderPrecache(version, files);
        } else {
            throw new ServiceWorkerException("The provided 'version' and 'files' were empty. When using the 'precache' strategy these values must be non empty.");
        }
    }

    @Override
    public String localizedPrecacheSw(final Resource resource) {
        if (resource.getValueMap().containsKey(CONFIG_VERSION) && resource.getValueMap().containsKey(CONFIG_PRECACHE_FILES) &&
                resource.getValueMap().containsKey(CONFIG_PREFIXED_PRECACHE_FILES) && resource.getValueMap().containsKey(CONFIG_IGNORE_DEPTH) &&
                resource.getValueMap().containsKey(CONFIG_PREFIX_DEPTH)) {
            final String version = resource.getValueMap().get(CONFIG_VERSION, String.class);
            final Integer ignoreDepth = resource.getValueMap().get(CONFIG_IGNORE_DEPTH, Integer.class);
            final Integer prefixDepth = resource.getValueMap().get(CONFIG_PREFIX_DEPTH, Integer.class);
            final List<String> precacheFiles = Arrays.asList(resource.getValueMap().get(CONFIG_PRECACHE_FILES, String[].class));
            final List<String> localizedPrecacheFiles = Arrays.asList(resource.getValueMap().get(CONFIG_PREFIXED_PRECACHE_FILES, String[].class));

            return localizedPrecacheSw(version, resource.getPath(), ignoreDepth, prefixDepth, precacheFiles, localizedPrecacheFiles);
        } else {
            throw new ServiceWorkerException("Service worker config at " + resource.getPath() + " was set to precache strategy but is missing one of: " + CONFIG_VERSION + ", " + CONFIG_PRECACHE_FILES + ", " + CONFIG_IGNORE_DEPTH + ", " + CONFIG_PREFIX_DEPTH + ", " + CONFIG_PREFIXED_PRECACHE_FILES + ". Make sure this service worker config has all of these properties set if using the 'localizedPrecache' strategy.");
        }
    }

    @Override
    public String localizedPrecacheSw(final String path, final SWConfig config) {
        if (config.version() != null && config.ignoreDepth() > 0 && config.prefixDepth() > 0 && config.files() != null && config.prefixedFiles() != null) {
            return localizedPrecacheSw(config.version(), path, config.ignoreDepth(), config.prefixDepth(), Arrays.asList(config.files()), Arrays.asList(config.prefixedFiles()));
        } else {
            throw new ServiceWorkerException("Context aware config for ServiceWorkerServiceImpl is using the 'localizedPrecache' strategy but is missing one of the following required properties: " + CONFIG_VERSION + ", " + CONFIG_PRECACHE_FILES + ", " + CONFIG_IGNORE_DEPTH + ", " + CONFIG_PREFIX_DEPTH + ", " + CONFIG_PREFIXED_PRECACHE_FILES + ". Make sure this context aware config has all of these properties set if using the 'localizedPrecache' strategy.");
        }
    }

    @Override
    public String localizedPrecacheSw(final String version, final String path, final int ignoreDepth, final int prefixDepth, final List<String> precacheFiles, final List<String> localizedPrecacheFiles) {
        final String prefix = "/" + Arrays.asList(Arrays.copyOfRange(path.split("/"), ignoreDepth + 1, ignoreDepth + 1 + prefixDepth)).stream().collect(Collectors.joining("/"));
        final List<String> preppedLocalizedPrecacheFiles = localizedPrecacheFiles.stream()
                .map(str -> prefix + str).collect(Collectors.toList());

        final List<String> files = new ArrayList<>();
        files.addAll(precacheFiles);
        files.addAll(preppedLocalizedPrecacheFiles);

        return renderPrecache(version, files);
    }

    @Override
    public String renderPrecache(final String version, final List<String> files) {
        final List<String> preppedFiles = files.stream()
                .map(str -> "'" + str + "'")
                .collect(Collectors.toList());

        return SW_PRECACHE
                .replace(CONFIG_BORDER + CONFIG_VERSION + CONFIG_BORDER, version)
                .replace(CONFIG_BORDER + CONFIG_PRECACHE_FILES + CONFIG_BORDER, preppedFiles.stream().collect(Collectors.joining(",")));
    }

    @Override
    public String customSw(final Resource resource) {
        if (resource.getValueMap().containsKey(PROP_CUSTOM_TEMPLATE)) {
            final String template = getTemplate(resource.getValueMap().get(PROP_CUSTOM_TEMPLATE, String.class), resource.getResourceResolver());
            final HashMap<String, String> configMap = new HashMap<>();

            for (String config : resource.getValueMap().keySet()) {
                if (config.startsWith(CONFIG_BORDER) && config.endsWith(CONFIG_BORDER)) {
                    configMap.put(config, resource.getValueMap().get(config, ""));
                }
            }

            return customSw(template, configMap);
        } else {
            throw new ServiceWorkerException("Service worker config at " + resource.getPath() + " was set to custom strategy but contained no custom template. Please add a custom service worker template to the " + PROP_CUSTOM_TEMPLATE + " variable.");
        }
    }

    @Override
    public String customSw(final SWConfig config, final ResourceResolver rr) {
        if (StringUtils.isNotBlank(config.template()) && config.configMapValues().length == config.configMapValues().length) {
            final String template = getTemplate(config.template(), rr);
            final Map<String, String> configMap = new HashMap<>();

            for (int index = 0; index < config.configMapKeys().length; index++) {
                configMap.put(config.configMapKeys()[index], config.configMapValues()[index]);
            }

            return customSw(template, configMap);
        } else {
            throw new ServiceWorkerException("Context aware config for ServiceWorkerServiceImpl is using the 'custom' strategy but is either missing the 'template' property or the map values property is of a different length than the map keys property.");
        }
    }

    @Override
    public String customSw(final String template, final Map<String, String> configMap) {
        String sw = template;

        for (final String key : configMap.keySet()) {
            sw = sw.replaceAll(key, configMap.get(key));
            if (LOG.isDebugEnabled()) LOG.debug("Replacing " + key + " with + '" + configMap.get(key) + "' for result: " + sw);
        }

        return sw;
    }

    /**
     * @param assetPath A path to a DAM asset.
     * @param rr A resource resolver with read access to the original rendition of the asset at assetPath.
     * @return The text contents of the file.
     */
    private String getTemplate(final String assetPath, final ResourceResolver rr) {
        final Resource assetResource = rr.resolve(assetPath);

        if (assetResource == null) {
            throw new ServiceWorkerException("No resource exists at provided asset path: " + assetPath);
        }

        final Asset asset = assetResource.adaptTo(Asset.class);

        if (asset == null) {
            throw new ServiceWorkerException("Could not adapt resource to an Asset at path: " + assetResource.getPath());
        }

        final Rendition rendition = asset.getOriginal();

        if (rendition == null) {
            throw new ServiceWorkerException("Could not find original rendition for asset at path: " + asset.getPath());
        }

        return new BufferedReader(new InputStreamReader(rendition.getStream())).lines().collect(Collectors.joining("\n"));
    }

    /**
     * A generic exception that is thrown when there is an error with the parameters passed into one of the methods of the ServiceWorkerServiceImpl.
     */
    public class ServiceWorkerException extends RuntimeException {
        public ServiceWorkerException(String errorMessage) {
            super(errorMessage);
        }
    }
}
