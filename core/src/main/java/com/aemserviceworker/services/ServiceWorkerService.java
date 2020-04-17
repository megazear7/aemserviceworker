package com.aemserviceworker.services;

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

@Component(service = ServiceWorkerService.class)
public class ServiceWorkerService {
    private static final String CONFIG_BORDER = "___";
    private static final String CONFIG_VERSION = "version";
    private static final String CONFIG_PRECACHE_FILES = "files";
    private static final String CONFIG_PREFIXED_PRECACHE_FILES = "prefixedFiles";
    private static final String CONFIG_IGNORE_DEPTH = "ignoreDepth";
    private static final String CONFIG_PREFIX_DEPTH = "prefixDepth";
    private static final String PROP_CUSTOM_TEMPLATE = "template";
    private static final String SW_PRECACHE = "const PRECACHE='___version___';const PRECACHE_URLS=[___files___];self.addEventListener('install',event=>{event.waitUntil(caches.open(PRECACHE).then(cache=>cache.addAll(PRECACHE_URLS)).then(self.skipWaiting()))});self.addEventListener('activate',event=>{const currentCaches=[PRECACHE];event.waitUntil(caches.keys().then(cacheNames=>{return cacheNames.filter(cacheName=>!currentCaches.includes(cacheName))}).then(cachesToDelete=>{return Promise.all(cachesToDelete.map(cacheToDelete=>{return caches.delete(cacheToDelete)}))}).then(()=>self.clients.claim()))});self.addEventListener('fetch',event=>{if(event.request.url.startsWith(self.location.origin)){event.respondWith(caches.match(event.request).then(cachedResponse=>{if(cachedResponse){return cachedResponse}return fetch(event.request)}))}})";

    public static final String PROP_STRATEGY = "strategy";
    public static final String STRATEGY_PRECACHE = "precache";
    public static final String STRATEGY_LOCALIZED_PRECACHE = "localizedPrecache";
    public static final String STRATEGY_CUSTOM = "custom";

    /**
     * @param resource The resource to generate a service worker for. The 'strategy' property will be inspected and then the corresponding method called based on the strategy.
     * @return The rendered service worker JavaScript file.
     */
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

    /**
     * @param config The config to use to generate a service worker. The 'strategy' will be inspected and then the appropriate method called based on the strategy.
     * @param path The path to the url that a service worker is being generated for.
     * @param rr A resource resolver capable of reading the Asset pointed at by the 'template' property of the config, if the 'custom' strategy is being used.
     * @return The rendered service worker JavaScript file.
     */
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

    /**
     * @param resource The resource to use to generate the service worker. This should have non empty 'version' and 'precacheFiles' properties.
     * @return The rendered service worker using the precache strategy.
     */
    public String precacheSw(final Resource resource) {
        if (resource.getValueMap().containsKey(CONFIG_VERSION) && resource.getValueMap().containsKey(CONFIG_PRECACHE_FILES)) {
            final String version = resource.getValueMap().get(CONFIG_VERSION, String.class);
            final List<String> files = Arrays.asList(resource.getValueMap().get(CONFIG_PRECACHE_FILES, String[].class));

            return precacheSw(version, files);
        } else {
            throw new ServiceWorkerException("Service worker config at " + resource.getPath() + " was set to precache strategy but is missing one of the following required properties: " + CONFIG_VERSION + " or " + CONFIG_PRECACHE_FILES + ". Make sure this service worker config has all of these properties set if using the 'precache' strategy.");
        }
    }

    /**
     * @param config The config to use to generate the service worker. This should have a non empty 'version' and a non null 'files' values.
     * @return The rendered service worker using the precache strategy.
     */
    public String precacheSw(final SWConfig config) {
        if (config.version() != null && config.files() != null) {
            return precacheSw(config.version(), Arrays.asList(config.files()));
        } else {
            throw new ServiceWorkerException("Context aware config for ServiceWorkerService is using the 'precache' strategy but is missing one of the following required properties: " + CONFIG_VERSION + " or " + CONFIG_PRECACHE_FILES + ". Make sure this service worker config has all of these properties set if using the 'precache' strategy.");
        }
    }

    /**
     *
     * @param version The version to use when generating the service worker precache.
     * @param files The list of relative urls to add to the service worker precache.
     * @return The rendered service worker using the precache strategy.
     */
    public String precacheSw(final String version, final List<String> files) {
        if (StringUtils.isNotBlank(version) && files != null) {
            return renderPrecache(version, files);
        } else {
            throw new ServiceWorkerException("The provided 'version' and 'files' were empty. When using the 'precache' strategy these values must be non empty.");
        }
    }

    /**
     * @param resource The resource to use to generate the service worker. This should have a non empty 'version', an 'ignoreDepth' and 'prefixDepth' properties of greater than 0, and non null 'files' and 'prefixedFiles' properties.
     * @return The rendered service worker using the precache strategy.
     */
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

    /**
     * @param path The url path to the service worker.
     * @param config The config to use to generate the service worker. This should have a non empty version, an ignoreDepth and prefixDepth of greater than 0, and non null files and prefixedFiles lists.
     * @return The rendered service worker using the precache strategy.
     */
    public String localizedPrecacheSw(final String path, final SWConfig config) {
        if (config.version() != null && config.ignoreDepth() > 0 && config.prefixDepth() > 0 && config.files() != null && config.prefixedFiles() != null) {
            return localizedPrecacheSw(config.version(), path, config.ignoreDepth(), config.prefixDepth(), Arrays.asList(config.files()), Arrays.asList(config.prefixedFiles()));
        } else {
            throw new ServiceWorkerException("Context aware config for ServiceWorkerService is using the 'localizedPrecache' strategy but is missing one of the following required properties: " + CONFIG_VERSION + ", " + CONFIG_PRECACHE_FILES + ", " + CONFIG_IGNORE_DEPTH + ", " + CONFIG_PREFIX_DEPTH + ", " + CONFIG_PREFIXED_PRECACHE_FILES + ". Make sure this context aware config has all of these properties set if using the 'localizedPrecache' strategy.");
        }
    }

    /**
     * @param version The version to use for the service workers precache.
     * @param path The path to the service worker.
     * @param ignoreDepth The number of segments of the path to ignore when generating the localized precache files, starting from the root.
     * @param prefixDepth The number of segments of the path to use starting after the ignored segments when generating the localized precache files.
     * @param precacheFiles The list of files to add to the precache. These values will not be modified.
     * @param localizedPrecacheFiles The list of files to localize based on the provided path, ignore depth, and prefix depth. They will then be added to the precache.
     * @return The rendered service worker using the precache strategy.
     */
    public String localizedPrecacheSw(final String version, final String path, final int ignoreDepth, final int prefixDepth, final List<String> precacheFiles, final List<String> localizedPrecacheFiles) {
        final String prefix = "/" + Arrays.asList(Arrays.copyOfRange(path.split("/"), ignoreDepth + 1, ignoreDepth + 1 + prefixDepth)).stream().collect(Collectors.joining("/"));
        final List<String> preppedLocalizedPrecacheFiles = localizedPrecacheFiles.stream()
                .map(str -> prefix + str).collect(Collectors.toList());

        final List<String> files = new ArrayList<>();
        files.addAll(precacheFiles);
        files.addAll(preppedLocalizedPrecacheFiles);

        return renderPrecache(version, files);
    }

    /**
     * @param version The version to use for the service worker.
     * @param files The list of files to precache. These should be relative urls.
     * @return The rendered service worker using the precache scheme.
     */
    public String renderPrecache(final String version, final List<String> files) {
        final List<String> preppedFiles = files.stream()
                .map(str -> "'" + str + "'")
                .collect(Collectors.toList());

        return SW_PRECACHE
                .replace(CONFIG_BORDER + CONFIG_VERSION + CONFIG_BORDER, version)
                .replace(CONFIG_BORDER + CONFIG_PRECACHE_FILES + CONFIG_BORDER, preppedFiles.stream().collect(Collectors.joining(",")));
    }

    /**
     * @param resource A resource to generate a service worker for. The 'template' property should point to a JavaScript in the DAM. Any properties that begin and end with '__' will be used as key value replacements of this template.
     * @return The rendered JavaScript file based on the template with the key value replacements applies.
     */
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

    /**
     * @param config A context aware SWConfig. The 'template' property should be non empty. The 'configMapKeys' and 'configMapValues' properties should be of the same length.
     * @param rr A resource resolver capable of reading the original DAM asset indicated by the template property.
     * @return
     */
    public String customSw(final SWConfig config, final ResourceResolver rr) {
        if (StringUtils.isNotBlank(config.template()) && config.configMapValues().length == config.configMapValues().length) {
            final String template = getTemplate(config.template(), rr);
            final Map<String, String> configMap = new HashMap<>();

            for (int index = 0; index < config.configMapKeys().length; index++) {
                configMap.put(config.configMapKeys()[index], config.configMapValues()[index]);
            }

            return customSw(template, configMap);
        } else {
            throw new ServiceWorkerException("Context aware config for ServiceWorkerService is using the 'custom' strategy but is either missing the 'template' property or the map values property is of a different length than the map keys property.");
        }
    }

    /**
     * @param template A JavaScript file to be used as a template.
     * @param configMap The key value pairs to replace in the JavaScript file.
     * @return The finalized JavaScript file based on the template with the key value pairs replaced.
     */
    public String customSw(final String template, final Map<String, String> configMap) {
        String sw = "";

        for (final String config : configMap.keySet()) {
            sw = template.replace(config, configMap.get(config));
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
     * A generic exception that is thrown when there is an error with the parameters passed into one of the methods of the ServiceWorkerService.
     */
    public class ServiceWorkerException extends RuntimeException {
        public ServiceWorkerException(String errorMessage) {
            super(errorMessage);
        }
    }
}
