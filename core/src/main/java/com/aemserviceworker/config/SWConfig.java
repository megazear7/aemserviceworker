package com.aemserviceworker.config;

import org.apache.sling.caconfig.annotation.Configuration;
import org.apache.sling.caconfig.annotation.Property;

@Configuration(label = "Sample Configuration", description = "This is a sample configuration.")
public @interface SWConfig {

    @Property(label = "Version", description = "Service worker version. This should always increase with each change.")
    String version();

    @Property(label = "Strategy", description = "The strategy to use. 'precache', 'localizedPrecache', and 'custom' are currently supported.")
    String strategy();

    @Property(label = "Ignore depth", description = "Used for the 'localizedPrecache' strategy. This should be an integer containing the number of path segments that you want to ignore, starting from the root.")
    int ignoreDepth() default 0;

    @Property(label = "Prefix depth", description = "Used for the 'localizedPrecache' strategy. This should be an integer containing the number of path segments that you want to use as a prefix, starting from the ignored depth. This value should be 1 or greater.")
    int prefixDepth() default 0;

    @Property(label = "Template", description = "The JS to use as a template when generating the service worker.")
    String template();

    @Property(label = "Files", description = "This should be a multi valued string property with the urls that you want included in the precache. These files will not be prefixed.")
    String[] files() default { };

    @Property(label = "Files", description = "This should be a multi valued string property with urls that you want included in the precache. These files will be prefixed by the calculated prefix. This value should be 1 or greater.")
    String[] prefixedFiles() default { };

    @Property(label = "Config map values", description = "Used for the 'custom' strategy. The list of key to look for in the provided service worker template.")
    String[] configMapKeys() default { };

    @Property(label = "Config map values", description = "Used for the 'custom' strategy. The list of values to use to replace the corresponding keys in the provided service worker template.")
    String[] configMapValues() default { };
}
