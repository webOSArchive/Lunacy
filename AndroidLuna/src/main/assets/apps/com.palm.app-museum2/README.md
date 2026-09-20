# webos-catalog-frontend

![App Icon](icon.png)

Enyo-based App Catalog for legacy webOS, forked from App Museum. You can use this app on a Pre3 or Touchpad, or access the catalog in a browser at [http://appcatalog.webosarchive.org](http://appcatalog.webosarchive.org)

Back-end and additional documentation here: [https://github.com/webosarchive/webos-catalog-backend](https://github.com/webosarchive/webos-catalog-backend)

## What is This?

This is an app for the defunct mobile webOS platform, made by Palm and later acquired by HP. It runs on devices like the Palm Pre or Pixi, or the HP
Pre3 or TouchPad. 

webOS technology was acquired by LG and repurposed for TVs and IoT devices, but they made significant changes and this app will not run on those platforms.

Releases of this app, and many other new and restored apps, can be found in the [webOS Archive App Museum II](http://appcatalog.webosarchive.org).

## Why?

Aside from being a fan of the platform, the author thinks consumers have lost out now that the smart phone ecosystem has devolved into a duopoly.
Apple and Google take turns copying each other, and consumers line up to buy basically the same new phone every year. The era when webOS, Blackberry and 
Windows Phone were serious competitors was marked by creativity in form factor and software development, which has been lost. This app represents a (futile)
attempt to keep webOS mobile devices useful for as long as possible.

The website [webosarchive.org](http://www.webosarchive.org) recovers, archives and maintains material related to development, and hosts services
that restore functionality to webOS devices. A small but active [community](http://www.webosarchive.org/discord) of users take advantage of these services to keep their retro devices alive.

## How?

Mobile webOS was truly a web-derived platform. Based on Linux, and able to run properly compiled Linux binaries, developers could get raw resources access (including GPU) through a PDK (Plug-in Development Kit) but most apps were written in some flavor of Javascript, interacting with a WebKit-based browser. The toolkits were heavily optimized for the devices, and web-based apps usually felt pretty close to native. Services could be written using NodeJS and talk to each other through API calls structured to look like web service calls. App front-ends were usually written in either the Mojo (pre-tablet) or Enyo (tablet and newer phones) Javascript frameworks. Enyo apps can often be run with only minor modification in any WebKit based browser (eg: Safari or Chrome).

You can learn more about these frameworks at the restored [SDK](http://sdk.webosarchive.org).

webOS devices can be found cheaply on eBay, and while the phones will cease to be useful as phones when the 3G shutdown is through, both the phones and the Touchpad can be used around the home for a variety of [fun and useful things](http://www.webosarchive.org/docs/thingstotry/).

If you have a device, instructions for activating, getting online and getting apps installed can be found in the [webOS Archive Docs section](http://www.webosarchive.org/docs/activate/).
