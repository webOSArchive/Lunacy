#!/bin/sh
# Packages busprobe-src/ under each app id below, for measuring which bus PalmServiceBridge
# reaches from an app of that id. Install the ipks with palm-install, launch each, palm-log.
cd "$(dirname "$0")"
for id in org.webosarchive.lunacy.busprobe com.palm.lunacy.busprobe com.webos.lunacy.busprobe; do
    rm -rf /tmp/$id && mkdir /tmp/$id && cp busprobe-src/* /tmp/$id/
    printf '{"id":"%s","version":"0.0.1","vendor":"webOS Archive","type":"web","main":"index.html","title":"Bus Probe","icon":"icon.png","uiRevision":2}\n' $id > /tmp/$id/appinfo.json
    palm-package /tmp/$id -o . >/dev/null && echo ${id}_0.0.1_all.ipk
done
