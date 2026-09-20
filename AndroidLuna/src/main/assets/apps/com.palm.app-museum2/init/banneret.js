// this file will set up the different banneret elements.
// banneret elements are those elements that are for general projects and are not specifically tailored to ShoMe.

window.banneret = window.banneret || {};
banneret.global = {};

banneret.global.categories = [
    { id: 0, label: "All", iconLocation: "category-icons/home/" },
    { id: 1, label: "Favourites", iconLocation: "category-icons/bookmarks/" },
    { id: 2, label: "Curator\'s Choice", iconLocation: "category-icons/curator/" },
    { id: 3, label: "Revisionist History", iconLocation: "category-icons/revisions/" },
    { id: 4, label: "Books", iconLocation: "category-icons/books/" },
    { id: 5, label: "Business", iconLocation: "category-icons/business/" },
    { id: 6, label: "Education", iconLocation: "category-icons/education/" },
    { id: 7, label: "Entertainment", iconLocation: "category-icons/entertainment/" },
    { id: 8, label: "Finance", iconLocation: "category-icons/finance/" },
    { id: 9, label: "Food", iconLocation: "category-icons/food/" },
    { id: 10, label: "Games", iconLocation: "category-icons/games/" },
    { id: 11, label: "Health & Fitness", iconLocation: "category-icons/health-and-fitness/" },
    { id: 12, label: "Lifestyle", iconLocation: "category-icons/lifestyle/" },
    { id: 13, label: "Music", iconLocation: "category-icons/music/" },
    { id: 14, label: "Navigation", iconLocation: "category-icons/navigation/" },
    { id: 15, label: "News", iconLocation: "category-icons/news/" },
    { id: 16, label: "Photography", iconLocation: "category-icons/photography/" },
    { id: 17, label: "Productivity", iconLocation: "category-icons/productivity/" },
    { id: 18, label: "Reference", iconLocation: "category-icons/reference/" },
    { id: 19, label: "Social Networking", iconLocation: "category-icons/social-networking/" },
    { id: 20, label: "Sports", iconLocation: "category-icons/sports/" },
    { id: 21, label: "Travel", iconLocation: "category-icons/travel/" },
    { id: 22, label: "Weather", iconLocation: "category-icons/weather/" },
    /*{ id: 23, label: "Missing Apps", iconLocation: "category-icons/missing/" }*/
];

banneret.global.prefCookie = 'banneretAppMuseum';
banneret.global.favouritesCookie = 'banneretAppMuseumfavourites';
banneret.global.blacklistCookie = 'banneretAppMuseumBlacklist';

banneret.global.isTouchpad = (function() {
    // TODO: we should us the cleaner Palm-approach here.
    var minSize = Math.min(window.innerWidth, window.innerHeight);
    var touchpad = true;
    if (minSize < 600) {
        // we're on a touchpad (even the Pre3 will be 480 here);
        iconLocationPlus = "";
        touchpad = false;
    }

    var device;
    if (window.PalmSystem) {
        device = JSON.parse(PalmSystem.deviceInfo);
    } else {
        device = {
            modelNameAscii: "webOS device"
        }
    }
    return touchpad;
})();

banneret.global.orientation = (function() {
    if (window.innerWidth > window.innerHeight) {
        return "landscape";
    }
    return "portrait";
})();

window.getGlobal = banneret.getGlobal = function(variable) {
    if (variable === undefined) {
        return banneret.global;
    }
    return banneret.global[variable];
}

window.setGlobal = banneret.setGlobal = function(key, variable) {
    if (key !== null && typeof key === 'object') {
        // multiple variables can be globalized at the same time by putting them in an object.
        // the content of 'variable' will be ignored.
        for (variable in key) {
            banneret.global[variable] = key[variable];
        }
    } else {
        banneret.global[key] = variable;
    }
}

banneret.factory_settings = {
    favourites: {},
    baseImageURL: "http://appcatalog.webosarchive.org/AppImages/",
    serviceBaseImageURL: "",
    catIconSize: banneret.getGlobal('isTouchpad') ? 1 : 0,
    appIconSize: banneret.getGlobal('isTouchpad') ? 1 : 0,
    showIconsWhileScrolling: banneret.getGlobal('isTouchpad') ? true : false,
    laodIconsWhileScrolling: banneret.getGlobal('isTouchpad') ? true : false,
    useAcceleratedScrolling: banneret.getGlobal('isTouchpad') ? true : false,
    firstUse: true,
    archiveUsage: true,
    archiveFTP: false,
    archiveLocation: "packages.webosarchive.org/AppPackages",
    serviceArchiveLocation: "",
    archiveLoginName: "",
    archiveLoginPassword: "",
    archiveFileFormatting: 1,
    detailLocation: "http://appcatalog.webosarchive.org/WebService/",
    detailPage: "getMuseumDetails",
    masterPage: "getMuseumMaster",
    showPrice: false,
    showRandomApp: false,
    showRandomButton: false,
    prefetchSize: 2,
    liveSearch: false,
    liveSearchThreshold: "6",
    indicateArchive: false,
    onlyArchived: false,
    backendVersion: "", // "", "_beta", or "_alpha"
    blacklistButton: false,
    showAdult:false
};

banneret.getSessionKey = function() {
    if (!banneret._sessionKey) {
        var key = "";
        var i, rand;
        for (i = 0; i < 3; i++) {
            rand = Date.now() * (Math.random() + 1);
            key += rand.toString(36).substr(2, 5);
        }
        banneret._sessionKey = key;
    }
    return banneret._sessionKey;
};

banneret.getPrefs = function(prefName) {
    var factory_settings;
    if (!enyo.getCookie(banneret.global.prefCookie)) {
        enyo.setCookie(banneret.global.prefCookie, enyo.json.stringify(banneret.factory_settings));
        banneret.global.prefs = banneret.factory_settings;
    } else {
        banneret.global.prefs = enyo.json.parse(enyo.getCookie(banneret.global.prefCookie));
        //Handle TLD change
        if (banneret.global.prefs["baseImageURL"].indexOf("webosarchive.com") != -1 || 
                banneret.global.prefs["archiveLocation"].indexOf("webosarchive.com") != -1 ||
                banneret.global.prefs["detailLocation"].indexOf("webosarchive.com") != -1
            ) {
            banneret.global.prefs = banneret.factory_settings;
        }
    }
    if (prefName) {
        return banneret.global.prefs[prefName];
    }
    return banneret.global.prefs;
};

banneret.resetPrefs = function() {
    enyo.setCookie(banneret.global.prefCookie, enyo.json.stringify(banneret.factory_settings));
    banneret.global.prefs = banneret.factory_settings;
};

banneret.setPref = function(prefName, prefSetting) {
    banneret.global.prefs[prefName] = prefSetting;
    enyo.setCookie(banneret.global.prefCookie, enyo.json.stringify(banneret.global.prefs));
};

banneret.setPrefs = function(prefObject) {
    var prefs = banneret.global.prefs;
    for (var prefName in prefObject) {
        prefs[prefName] = prefObject[prefName];
    }
    enyo.setCookie(banneret.global.prefCookie, enyo.json.stringify(banneret.global.prefs));
};

banneret.getBlacklist = function(vendorId) {
    if (!enyo.getCookie(banneret.global.blacklistCookie)) {
        enyo.setCookie(banneret.global.blacklistCookie, "[]");
        banneret.global.blacklist = [];
    } else {
        banneret.global.blacklist = enyo.json.parse(enyo.getCookie(banneret.global.blacklistCookie));
    }
    if (vendorId === undefined) {
        return banneret.global.blacklist;
    }
    var output = false;
    banneret.global.blacklist.some(function(bl, idx, arr) {
        if (bl.vendorId === vendorId) {
            output = bl;
            return true;
        }
    });
    return output;
}
banneret.addBlacklist = function(id) {
    var inData = banneret.getGlobal("appList"); // we can do this because we know that new blacklisted vendors
    // can only be added if we already have our applist.
    var creatorTable = {};
    var appCount = 0;
    var homepage = '';
    inData.forEach(function(d, idx, arr) {
        if (d) {
            if (d.vendorId === id && creatorTable[id] !== true) {
                d.author = banneret.cleanText(d.author);
                creatorTable[d.author] = true; // makes sure we're storing each name only once.
            }
            if (d.vendorId === id) {
                appCount++;
            }
            if (d.vendorId === id && d.detail !== undefined) {
                homepage = d.detail.homeURL || d.detail.supportURL;
            }
        }
    });
    var creatorNames = Object.keys(creatorTable).join(", ");

    var obj = {
        id: id,
        names: creatorNames,
        count: appCount,
        homepage: homepage
    };

    if (!enyo.getCookie(banneret.global.blacklistCookie)) {
        banneret.global.blacklist = [obj];
    } else {
        banneret.global.blacklist.push(obj);
    }
    enyo.setCookie(banneret.global.blacklistCookie, enyo.json.stringify(banneret.global.blacklist));
};

banneret.removeBlacklist = function(settings) {
    if (!settings || typeof settings !== "object") { return }
    var idx, black;
    black = banneret.getBlacklist();
    if (settings.id !== undefined) {
        idx = 0;
        black.some(function(f, index, arr) {
            if (f.id === settings.id) {
                idx = index;
            }
        });

    } else if (settings.idx !== undefined) {
        idx = settings.idx;
    }
    black.splice(idx, 1);
    banneret.global.blacklist = black;
    enyo.setCookie(banneret.global.blacklistCookie, enyo.json.stringify(banneret.global.blacklist));
}

banneret.getFavourites = function() {
    if (!banneret.global.favourites) {
        if (!enyo.getCookie(banneret.global.favouritesCookie)) {
            enyo.setCookie(banneret.global.favouritesCookie, "{}");
            banneret.global.favourites = {};
        } else {
            banneret.global.favourites = enyo.json.parse(enyo.getCookie(banneret.global.favouritesCookie));
        }
    }
    return banneret.global.favourites;
}
banneret.setFavourites = function(favObj) {
    banneret.global.favourites = favObj;
    enyo.setCookie(banneret.global.favouritesCookie, enyo.json.stringify(favObj));
}
banneret.addFavourite = function(id) {
    var favourites = banneret.getFavourites();
    favourites[id] = true;
    banneret.setFavourites(favourites);
    banneret.updateFavouriteCount();
}
banneret.removeFavourite = function(id) {
    var favourites = banneret.getFavourites();
    delete favourites[id];
    banneret.setFavourites(favourites);
    banneret.updateFavouriteCount();
}
banneret.updateFavouriteCount = function() {
    var favourites = banneret.getFavourites()
    var catObj = banneret.WebService.owner.$.categories;
    var appCount = catObj.getCategoryCount();
    appCount.Favourites = Object.keys(favourites).length;
    catObj.setCategoryCount(appCount);
}

banneret.loadJSON = function(path, onSuccess, onError) {
    var xhr = new XMLHttpRequest();
    xhr.onreadystatechange = function() {
        if (xhr.readyState === XMLHttpRequest.DONE) {
            if (xhr.status === 200 || xhr.status === 0 /* 0 is needed for local files */ ) {
                if (typeof onSuccess === 'function') {
                    var i = path;
                    onSuccess(JSON.parse(xhr.responseText));
                }
            } else {
                if (typeof onError === 'function') {
                    onError(xhr);
                }
            }
        }
    }
    xhr.open("GET", path, true);
    xhr.send();
}
banneret.getCategoryLabel = function(categoryId) {
    return banneret.getGlobal("categories")[categoryId || 0]['label'];
}

banneret.getMuseumList = function(settings, onSuccess, onError) {
    var museumCalls = banneret.getMuseumList.museumCalls = banneret.getMuseumList.museumCalls || {};
    banneret.getMuseumList.previousCall = banneret.getMuseumList.previousCall || undefined;
    var settingsString = JSON.stringify(settings);
    if (settingsString === banneret.getMuseumList.previousCall) {
        banneret.WebService.owner.MasterWebServiceFailureFn();
        return; // this is the same call as the previous call, and we want to prevent call-loops
    }
    banneret.getMuseumList.previousCall = settingsString;

    if (settings.category === "Missing Apps") {
        settings.category = "All";
        settings.show_only_missing = true;
    } else if (settings.category === "Favourites") {
        settings.useAppId = true;
        settings.appIds = Object.keys(banneret.getFavourites()).join(",");
    } else {
        var bList = [];
        var blacklist = banneret.getBlacklist();
        blacklist.forEach(function(bl, idx, arr) {
            bList.push(bl.id);
        })
        settings.blacklist = bList.join(",");
    }
    //settings.adult = "false";    //TODO: Make this a setting

    banneret.WebService = banneret.WebService || enyo.$.museumApp.$.MasterWebService;
    settings.key = banneret.getSessionKey();
    settings.hide_missing = banneret.getPrefs("onlyArchived");
    settings.adult = banneret.getPrefs("showAdult");
    settings.museumVersion = enyo.fetchAppInfo().version;

    if (typeof settings.category !== "string") {
        settings.category = banneret.getCategoryLabel(settings.category);
    }
    var category = settings.category;
    var device = settings.device;
    var page = settings.page;
    var sessionKey = banneret.getSessionKey();

    var blacklistedIds = banneret.getBlacklist().map(function(vendor, idx, arr) {
        return vendor.id;
    });

    var callString = JSON.stringify(settings);
    if (museumCalls[callString] === null) {
        return; // we know this call has been made, but the reponse is still pending.
        // thre is no need to make the call again.
    }
    if (settings.appIds !== "random" && museumCalls[callString]) {
        // we don't need to make a call again and can just use our buffered data.
        var pseudoResponse = JSON.parse(JSON.stringify(museumCalls[callString]));
        var appList = banneret.getGlobal("appList");
        pseudoResponse.request = settings;
        pseudoResponse.data = [];
        pseudoResponse.indices.forEach(function(i, idx, arr) {
            pseudoResponse.data[idx] = appList[i];
        });
        var pR = pseudoResponse;
        if (pR.request.page > 0 && (pR.appCount[pR.request.category] < ((pR.request.page + 1) * pR.request.count))) {
            banneret.WebService.owner.MasterWebServiceSuccessFn(undefined, undefined, "end call");
            return; // we have no data and expect data (probably indicating this is a redundant call)
        }
        banneret.WebService.owner.MasterWebServiceSuccessFn(undefined, pseudoResponse, undefined);
        return;
    } else {
        museumCalls[callString] = null;
    }

    banneret.WebService.call(settings);


    banneret.getMuseumList.successFn = function(inSender, inData, inResponse) {
        //console.log(inData);
        if (inSender !== undefined) { // we don't need this step when dealing with cached data
            var callString = JSON.stringify(inResponse.params);
            museumCalls[callString] = {
                indices: inData.indices,
                return_indices: inData.return_indices,
                first_position: inData.first_position,
                extraData: inData.extraData,
                appCount: inData.appCount
            };
        }
        var appList = banneret.getGlobal("appList") || [];
        inData.indices.forEach(function(i, idx, arr) {
            if (!appList[i]) {
                appList[i] = inData.data[idx];
            } else {
                if (!inData.data[idx]) {
                    inData.data[idx] = appList[i];
                }
            }
        });
        banneret.setGlobal("appList", appList);

        if (typeof onSuccess === "function") {
            onSuccess(inData, inResponse);
        }
    }.bind(this)
    banneret.getMuseumList.failureFn = function(inSender, inError, inResponse) {
        //console.log(inError);
        delete museumCalls[callString];
        if (typeof onError === "function") {
            onError(inError);
        }
    }.bind(this)
}
banneret.getAppByIndex = function(index) {
    var appList = banneret.getGlobal("appList") || [];
    return appList[index] ? appList[index] : null;
}
banneret.getAppById = function(appId) {
    var appObj = {};
    var appList = banneret.getGlobal("appList") || [];
    appList.some(function(app, idx, arr) {
        if (app.id == appId) { // intentional == to make sure the input can be both a Number and a String.
            appObj = app;
            return true;
        }
        return false;
    }.bind(this));
    return appObj;
}
banneret.cleanText = function(text) {
    var decText;
    try {
        decText = decodeURIComponent(text);
    } catch (e) {
        decText = text;
    }
    if (decText !== text) {
        decText = banneret.cleanText(decText);
    }
    return decText.replace(/\n\r?/g, "<br>");
}
banneret.restartApp = function(settings) {
    window.location.reload(settings);
}