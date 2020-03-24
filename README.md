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
  navigator.serviceWorker.register('/content/myapp/us/en/sw');
}
```

## Configuration


On your service worker resource you can add the following properties:

1. A string property with the name 'version' and some value. This will be used to name the service worker cache.
2. A multi valued string property with the name 'precacheFiles'. This list of urls will be cached when the service worker is installed.
