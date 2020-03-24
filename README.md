# AEM Service Worker

## Installation

```
mvn clean install
```

## Usage

Create a `sw.js` node under your content hierarchy with the following properties:

1. `jcr:primaryType=nt:unstructured`
1. `sling:resourceType=aemserviceworker/serviceworker`

In your page JavaScript add the following:

```js
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/your/content/path/sw.js');
}
```

## Configuration

TODO Enable Workbox configuration of service worker.
