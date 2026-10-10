/**
 * Missing String functions
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.mixin(String.prototype, {
	startsWith: function (pattern) {
		return this.indexOf(pattern) === 0;
	},
	endsWith: function (pattern) {
		var d = this.length - pattern.length;
		return d >= 0 && this.lastIndexOf(pattern) === d;
	},
	blank: function() {
		return (/^\s*$/).test(this);
	},
	stripTags: function() {
		return this.replace(/<\/?[^>]+>/gi, '');
	},
	stripScripts: function() {
		return this.replace(/<script[^>]*>[\S\s]*?<\/script>/img, '');
	},
	parseQueryString: function (separator) {
		var queryStart = this.indexOf('?');
		var queryEnd = this.indexOf('#');
		
		queryStart = (queryStart > -1) ? queryStart+1 : 0;
		queryEnd = (queryEnd > -1) ? queryEnd : this.length;
		var str = this.substring(queryStart, queryEnd);
		
		var sep = separator || '&';
		var obj = {};
		
		// Split the string into pairs by separator
		var pairs = str.split(sep);
		var len = pairs.length;
		
		// For each pair, split into key and value.
		for (var i=0; i<len; ++i) {
			var pieces = pairs[i].split("=");
			var key = decodeURIComponent(pieces.shift());
			
			// If there's more than 1 "piece" left, join them all with "=".
			// If there's only 1, just use it.
			var value = undefined;
			if (pieces.length > 0) {
				value = (pieces.length > 1) ? pieces.join("=") : pieces[0];
				value = decodeURIComponent(value);
			}
			if (key) {
				if (obj.hasOwnProperty(key)) {
					var oldval = obj[key];
					if (typeof oldval === "array") {
						oldval.push(value);
					} else {
						oldval = [oldval, value];
					}
					obj[key] = oldval;
				} else {
					obj[key] = value;
				}
			}
		}
		return obj;
	}
});
