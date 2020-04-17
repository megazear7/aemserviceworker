package com.aemserviceworker.services;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;
import org.apache.sling.api.resource.Resource;
import com.aemserviceworker.config.SWConfig;
import java.util.*;
import java.util.stream.Collectors;

@Component(service = ServiceWorkerService.class)
//@Designate(ocd = ServiceWorkerService.Config.class)
public class ServiceWorkerService {
    private static final String CONFIG_BORDER = "___";
    private static final String CONFIG_VERSION = "version";
    private static final String CONFIG_PRECACHE_FILES = "files";
    private static final String CONFIG_PREFIXED_PRECACHE_FILES = "prefixedFiles";
    private static final String CONFIG_IGNORE_DEPTH = "ignoreDepth";
    private static final String CONFIG_PREFIX_DEPTH = "prefixDepth";
    private static final String PROP_CUSTOM_TEMPLATE = "template";
    private static final String SW_PRECACHE = "const PRECACHE='___version___';const PRECACHE_URLS=[___files___];self.addEventListener('install',event=>{event.waitUntil(caches.open(PRECACHE).then(cache=>cache.addAll(PRECACHE_URLS)).then(self.skipWaiting()))});self.addEventListener('activate',event=>{const currentCaches=[PRECACHE];event.waitUntil(caches.keys().then(cacheNames=>{return cacheNames.filter(cacheName=>!currentCaches.includes(cacheName))}).then(cachesToDelete=>{return Promise.all(cachesToDelete.map(cacheToDelete=>{return caches.delete(cacheToDelete)}))}).then(()=>self.clients.claim()))});self.addEventListener('fetch',event=>{if(event.request.url.startsWith(self.location.origin)){event.respondWith(caches.match(event.request).then(cachedResponse=>{if(cachedResponse){return cachedResponse}return fetch(event.request)}))}})";

    public static final String STRATEGY_PRECACHE = "precache";
    public static final String STRATEGY_LOCALIZED_PRECACHE = "localizedPrecache";
    public static final String STRATEGY_CUSTOM = "custom";

    public String precacheSw(final Resource resource) {
        if (resource.getValueMap().containsKey(CONFIG_VERSION) && resource.getValueMap().containsKey(CONFIG_PRECACHE_FILES)) {
            final String version = resource.getValueMap().get(CONFIG_VERSION, String.class);
            final List<String> files = Arrays.asList(resource.getValueMap().get(CONFIG_PRECACHE_FILES, String[].class)).stream()
                    .map(str -> "'" + str + "'")
                    .collect(Collectors.toList());

            return precacheSw(version, files);
        } else {
            throw new ServiceWorkerException("Service worker config at " + resource.getPath() + " was set to precache strategy but is either missing " + CONFIG_VERSION + " or " + CONFIG_PRECACHE_FILES + ". Make sure this service worker config has all of these properties set.");
        }
    }

    public String precacheSw(final SWConfig config) {
        return precacheSw(config.version(), Arrays.asList(config.files()));
    }

    public String precacheSw(final String version, final List<String> files) {
        return renderPrecache(version, files);
    }

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
            throw new ServiceWorkerException("Service worker config at " + resource.getPath() + " was set to precache strategy but is missing one of: " + CONFIG_VERSION + ", " + CONFIG_PRECACHE_FILES + ", " + CONFIG_IGNORE_DEPTH + ", " + CONFIG_PREFIX_DEPTH + ", " + CONFIG_PREFIXED_PRECACHE_FILES + ". Make sure this service worker config has all of these properties set.");
        }
    }

    public String localizedPrecacheSw(final String path, final SWConfig config) {
        return localizedPrecacheSw(config.version(), path, config.ignoreDepth(), config.prefixDepth(), Arrays.asList(config.files()), Arrays.asList(config.prefixedFiles()));
    }

    public String localizedPrecacheSw(final String version, final String path, final int ignoreDepth, final int prefixDepth, final List<String> precacheFiles, final List<String> localizedPrecacheFiles) {
        final String prefix = "/" + Arrays.asList(Arrays.copyOfRange(path.split("/"), ignoreDepth + 1, ignoreDepth + 1 + prefixDepth)).stream().collect(Collectors.joining("/"));
        final List<String> preppedPrecacheFiles = precacheFiles.stream()
                .map(str -> "'" + str + "'").collect(Collectors.toList());
        final List<String> preppedLocalizedPrecacheFiles = localizedPrecacheFiles.stream()
                .map(str -> "'" + prefix + str + "'").collect(Collectors.toList());

        final List<String> files = new ArrayList<>();
        files.addAll(preppedPrecacheFiles);
        files.addAll(preppedLocalizedPrecacheFiles);

        return renderPrecache(version, files);
    }

    public String renderPrecache(final String version, final List<String> files) {
        return SW_PRECACHE
                .replace(CONFIG_BORDER + CONFIG_VERSION + CONFIG_BORDER, version)
                .replace(CONFIG_BORDER + CONFIG_PRECACHE_FILES + CONFIG_BORDER, files.stream().collect(Collectors.joining(",")));
    }

    public String customSw(final Resource resource) {
        if (resource.getValueMap().containsKey(PROP_CUSTOM_TEMPLATE)) {
            String template = resource.getValueMap().get(PROP_CUSTOM_TEMPLATE, String.class);
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

    public String customSw(final SWConfig config) {
        final Map<String, String> configMap = new HashMap<>();

        for (int index = 0; index < config.configMapKeys().length; index++) {
            configMap.put(config.configMapKeys()[index], config.configMapValues()[index]);
        }

        return customSw(config.template(), configMap);
    }

    public String customSw(final String template, final Map<String, String> configMap) {
        String sw = "";

        for (final String config : configMap.keySet()) {
            sw = template.replace(config, configMap.get(config));
        }

        return sw;
    }

    public class ServiceWorkerException extends RuntimeException {
        public ServiceWorkerException(String errorMessage) {
            super(errorMessage);
        }
    }
}
