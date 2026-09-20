var appMuseum = appMuseum || {};

appMuseum.Config = {
    wsTimeout: 60000,
    defaultPageSize: 50,
    testServerErrors: false,
    CacheConfig: {
        enabled: true, // enable/disable caching system wide
        persistent: false, // enable/disable persistent caching
        longTermTTL: (1000 * 60 * 60), //expires after 1 hour
        shortTermTTL: (1000 * 60 * 15) //expires after 15 minutes
    }
};


var showVendorIcon = function (inSenderDOM) {
    if (inSenderDOM.classList) {
        inSenderDOM.classList.remove("noLogo");
    } else {
        inSenderDOM.className = inSenderDOM.className.replace("noLogo", "");
    }
}
var hideVendorIcon = function (inSenderDOM) {
    if (inSenderDOM.classList) {
        inSenderDOM.classList.add("noLogo");
    } else {
        var classlist = inSenderDOM.className.split(" ");
        if (classlist.indexOf("noLogo") === -1) {
            inSenderDOM.className = inSenderDOM.className + (" noLogo");
        }
    }
}