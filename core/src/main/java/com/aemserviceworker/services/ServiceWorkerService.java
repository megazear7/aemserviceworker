package com.aemserviceworker.services;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.Resource;
import com.aemserviceworker.config.SWConfig;
import java.util.List;
import java.util.Map;

public interface ServiceWorkerService {
    String PROP_STRATEGY = "strategy";
    String STRATEGY_PRECACHE = "precache";
    String STRATEGY_LOCALIZED_PRECACHE = "localizedPrecache";
    String STRATEGY_CUSTOM = "custom";

    /**
     * @param resource The resource to generate a service worker for. The 'strategy' property will be inspected and then the corresponding method called based on the strategy.
     * @return The rendered service worker JavaScript file.
     */
    String getServiceWorker(final Resource resource);

    /**
     * @param config The config to use to generate a service worker. The 'strategy' will be inspected and then the appropriate method called based on the strategy.
     * @param path The path to the url that a service worker is being generated for.
     * @param rr A resource resolver capable of reading the Asset pointed at by the 'template' property of the config, if the 'custom' strategy is being used.
     * @return The rendered service worker JavaScript file.
     */
    String getServiceWorker(final SWConfig config, final String path, final ResourceResolver rr);

    /**
     * @param resource The resource to use to generate the service worker. This should have non empty 'version' and 'precacheFiles' properties.
     * @return The rendered service worker using the precache strategy.
     */
    String precacheSw(final Resource resource);

    /**
     * @param config The config to use to generate the service worker. This should have a non empty 'version' and a non null 'files' values.
     * @return The rendered service worker using the precache strategy.
     */
    String precacheSw(final SWConfig config);

    /**
     *
     * @param version The version to use when generating the service worker precache.
     * @param files The list of relative urls to add to the service worker precache.
     * @return The rendered service worker using the precache strategy.
     */
    String precacheSw(final String version, final List<String> files);

    /**
     * @param resource The resource to use to generate the service worker. This should have a non empty 'version', an 'ignoreDepth' and 'prefixDepth' properties of greater than 0, and non null 'files' and 'prefixedFiles' properties.
     * @return The rendered service worker using the precache strategy.
     */
    String localizedPrecacheSw(final Resource resource);

    /**
     * @param path The url path to the service worker.
     * @param config The config to use to generate the service worker. This should have a non empty version, an ignoreDepth and prefixDepth of greater than 0, and non null files and prefixedFiles lists.
     * @return The rendered service worker using the precache strategy.
     */
    String localizedPrecacheSw(final String path, final SWConfig config);

    /**
     * @param version The version to use for the service workers precache.
     * @param path The path to the service worker.
     * @param ignoreDepth The number of segments of the path to ignore when generating the localized precache files, starting from the root.
     * @param prefixDepth The number of segments of the path to use starting after the ignored segments when generating the localized precache files.
     * @param precacheFiles The list of files to add to the precache. These values will not be modified.
     * @param localizedPrecacheFiles The list of files to localize based on the provided path, ignore depth, and prefix depth. They will then be added to the precache.
     * @return The rendered service worker using the precache strategy.
     */
    String localizedPrecacheSw(final String version, final String path, final int ignoreDepth, final int prefixDepth, final List<String> precacheFiles, final List<String> localizedPrecacheFiles);

    /**
     * @param version The version to use for the service worker.
     * @param files The list of files to precache. These should be relative urls.
     * @return The rendered service worker using the precache scheme.
     */
    String renderPrecache(final String version, final List<String> files);

    /**
     * @param resource A resource to generate a service worker for. The 'template' property should point to a JavaScript in the DAM. Any properties that begin and end with '__' will be used as key value replacements of this template.
     * @return The rendered JavaScript file based on the template with the key value replacements applies.
     */
    String customSw(final Resource resource);

    /**
     * @param config A context aware SWConfig. The 'template' property should be non empty. The 'configMapKeys' and 'configMapValues' properties should be of the same length.
     * @param rr A resource resolver capable of reading the original DAM asset indicated by the template property.
     * @return
     */
    String customSw(final SWConfig config, final ResourceResolver rr);

    /**
     * @param template A JavaScript file to be used as a template.
     * @param configMap The key value pairs to replace in the JavaScript file.
     * @return The finalized JavaScript file based on the template with the key value pairs replaced.
     */
    String customSw(final String template, final Map<String, String> configMap);
}
