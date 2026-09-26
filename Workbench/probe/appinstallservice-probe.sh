#!/bin/sh
# Records com.palm.appInstallService (LunaDownloadMgr) on a TouchPad, as App Catalog calls it: run
# there as root; the subscription goes to /tmp/ais-status.out. See AppInstallService.kt.
A="-a com.palm.app.enyo-findapps"
U=http://appstorage.webosarchive.org/packages/ca.canuckcoding.compass_1.0.1_all.ipk
luna-send -n 1 -a com.palm.app.enyo-findapps palm://com.palm.appInstallService/cancel "{\"id\":\"ca.canuckcoding.compass\"}"
(luna-send -i $A palm://com.palm.appInstallService/status '{"subscribe":true}' > /tmp/ais-status.out 2>&1 &)
sleep 2
echo "== install full"; luna-send -n 1 $A palm://com.palm.appInstallService/install "{\"catalogId\":\"999\",\"id\":\"ca.canuckcoding.compass\",\"title\":\"Compass\",\"version\":\"1.0.1\",\"vendor\":\"Canuck Coding\",\"vendorUrl\":\"http://canuckcoding.ca\",\"iconUrl\":\"http://appcatalog.webosarchive.org/AppImages/10760/icon/s/icon_1_0_1.png\",\"ipkUrl\":\"$U\",\"authToken\":\"archive-user\",\"deviceId\":\"touchpad-archive\",\"email\":\"archive@webosarchive.org\",\"noApp\":false,\"services\":[],\"accounts\":[],\"dockMode\":false,\"universalSearch\":{},\"loc_name\":\"Compass\",\"transactionId\":\"1\"}" 2>&1
sleep 45
echo "== installed?"; ls /media/cryptofs/apps/usr/palm/applications | grep compass
killall luna-send
