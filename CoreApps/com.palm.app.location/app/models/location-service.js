/* Copyright 2009 Palm, Inc.  All rights reserved. */

var LocationService = {
	getLocationServicePrefs: function (sceneController, successCallback, errorCallback) {
		var params = {
			method: 'getLocationServicePrefs',
			parameters: {},
			onSuccess: successCallback,
			onFailure: errorCallback
		};
		if(sceneController) {
			return sceneController.serviceRequest(LocationService.identifier, params);
		} else {
			return new Mojo.Service.Request(LocationService.identifier, params);
		}
	},
	
	setLocationServicePrefs: function (useLocationServices, useBackgroundDataCollection, successCallback, errorCallback) {
		return new Mojo.Service.Request(LocationService.identifier, {
			method: 'setLocationServicePrefs',
			parameters: {'useLocationServices':useLocationServices, 'useBackgroundDataCollection':useBackgroundDataCollection},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
	
	acceptTermsOfUse: function (useBackgroundDataCollection, appId, successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'acceptTermsOfUse',
			parameters: {'appId':appId,'useBackgroundDataCollection':useBackgroundDataCollection},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
	
	rejectTermsOfUse: function (appId, successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'rejectTermsOfUse',
			parameters: {'appId':appId},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
	
	setAutoLocate: function (autoLocate, successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'setAutoLocate',
			parameters: {'autoLocate':autoLocate},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
	
	setUseGoogle: function (useGoogle, successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'setUseGoogle',
			parameters: {'useGoogle':useGoogle},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
	
	getUseLocationServices: function (successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'getUseLocationServices',
			parameters: {},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
			
	setUseGps: function (useGps, successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'setUseGps',
			parameters: {'useGps':useGps},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
	
	getUseGps: function (successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'getUseGps',
			parameters: {},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
	
	setUseGeotagPhotos: function (useGeocodePhotos, successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'setGeotagPhotos',
			parameters: {'geotagPhotos':useGeocodePhotos},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
	
	getUseGeotagPhotos: function (successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'getGeotagPhotos',
			parameters: {},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},	
	
	setUseBackgroundDataCollection: function (useBackgroundDataCollection, successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'setUseBackgroundDataCollection',
			parameters: {'useBackgroundDataCollection':useBackgroundDataCollection},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
	setWebSetting: function (webSetting, successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'setWebSetting',
			parameters: {'webSetting':webSetting},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
	clearWebSetting:function(successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'clearWebSetting',
			parameters: {},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
	getUseBackgroundDataCollection: function (successCallback, errorCallback, sceneController) {
		return sceneController.serviceRequest(LocationService.identifier, {
			method: 'getUseBackgroundDataCollection',
			parameters: {},
			onSuccess: successCallback,
			onFailure: errorCallback
		});
	},
	
	launchBrowser: function(url){
		var openParams = {
			scene: 'page',
			url: url
		};
		return new Mojo.Service.Request('palm://com.palm.applicationManager', {
			method: 'open',
			parameters: {
				id: 'com.palm.app.browser',
				params: openParams
			}
		});
	},
	
	launchHelp: function(url) {
		return new Mojo.Service.Request("palm://com.palm.applicationManager", {
			method: "open",
			parameters: {
				id: 'com.palm.app.help',
				params: {
					target: url
				}
			}
		});
	},	
}

LocationService.identifier = 'palm://com.palm.location'; 