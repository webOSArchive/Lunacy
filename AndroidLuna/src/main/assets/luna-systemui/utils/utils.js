Utils = {};

Utils.getObjectKeys = function(obj){
	var keys = [];
	for (var key in obj) 
		if (obj.hasOwnProperty(key))
			keys.push(key);
	return keys;
};

Utils.getObjectValues = function(obj){
	var values = [];
	for (var key in obj) 
		if (obj.hasOwnProperty(key))
			values.push(obj[key]);
	return values;
};

Utils.debounce = function debounce(onCall, onTimeout, delay, optionalWindow) {
	var timeoutID;
	var savedArgs;
	var triggerFunc, timeoutFunc;
	optionalWindow = optionalWindow || window;
	
	timeoutFunc = function() {
		timeoutID = undefined;
		onTimeout.apply(undefined, savedArgs);
		savedArgs = undefined;
	};
	
	triggerFunc = function() {
		savedArgs = arguments;
		if(timeoutID !== undefined) {
			optionalWindow.clearTimeout(timeoutID);
		}
		timeoutID = optionalWindow.setTimeout(timeoutFunc, delay*1000);
		return onCall && onCall.apply(this, arguments);
	};
	
	return triggerFunc;
};

