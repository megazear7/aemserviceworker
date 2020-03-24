# AEM Service Worker

This is a service worker generator for AEM. It allows you to place nodes with a specific resource type throughout your content hierarchy. When accessed with the '.js' extension these nodes will return a service worker based upon the properties on the node.

## Installation

Either install the package to the CRX or install the project using the below command.

```
mvn clean install
```

## Usage

Create a node under your content hierarchy with the following properties. This is your 'service worker resource'.

1. `jcr:primaryType=nt:unstructured`
1. `sling:resourceType=aemserviceworker/serviceworker`

Let's assume that your node is at `/content/myapp/us/en/sw`. In your page JavaScript add the following:

```js
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/content/myapp/us/en/sw.js');
}
```

## Configuration

The service worker resource needs a string property named 'strategy' with one of the following values:

1. `precache`
2. `localizedPrecache`    
3. `custom`

### Precache strategy

If you are using the precache strategy you will also need to set the following properties:

1. `version`: This should be the new version of the service worker.
1. `files`: This should be a multi valued string property with the urls that you want included in the precache.

### Localized precache strategy

This strategy is good if you want to configure the service worker once and use it on a large number of localized country and language sites.

If you are using the localized precache strategy you will also need to set the following properties:

1. `version`: This should be the new version of the service worker.
1. `startSegment`: This should be an integer containing the number of path segments that you want to ignore, starting from the root.
1. `prefixLength`: This should be an integer containing the number of path segments that you want to use as a prefix, starting from the ignored depth. This value should be 1 or greater.
1. `prefixedFiles`: This should be a multi valued string property with urls that you want included in the precache. These files will be prefixed by the calculated prefix. This value should be 1 or greater.
1. `files`: This should be a multi valued string property with the urls that you want included in the precache. These files will not be prefixed.

#### Example

Path of the the service worker resource: `/content/mysite/us/en/sw.js`;

```
ignoreDepth=2
prefixDepth=2
prefixedFiles=["/home.html", "/offline.html", "/news/newsfeed.html"]
```

This will ignore the first 2 path sections, `/content/mysite`, and use the next 2 as a prefix, `/us/en`. Using this calculated prefix the following urls will be added to the precache:

1. `/us/en/home.html`
1. `/us/en/offline.html`
1. `/us/en/news/newsfeed.html`

### Custom strategy

If you are using the custom strategy you need to set a property named `template` which contains the JavaScript of your service worker.

Any properties on the service worker resource that are surrounded with `___` will be used to replace the contents of the custom template.

For example if your custom template has `___EXAMPLE___` and your service worker resource has a property named `___EXAMPLE___` with the value `test`, your service worker template will have all instances of `___EXAMPLE___` replaced with `test`. These must be string properties.
