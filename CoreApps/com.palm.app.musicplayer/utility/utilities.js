/*globals Utilities, enyo, $L, event, Utilities:true, window, PalmSystem, MediaIndex, escape */
Utilities = {
	
	time_formatter: null,  

	formatTime: function(floatSeconds)
	{
		if(this.time_formatter === null){
			this.time_formatter = new enyo.g11n.DurationFmt();	
		}
		var intMinutes = Math.floor(floatSeconds / 60);
		var intSeconds = Math.floor(floatSeconds % 60);
		
		
		var intHours = Math.floor(intMinutes / 60);
		intMinutes = Math.floor(intMinutes % 60);
								
		return this.time_formatter.format({hours: intHours, minutes:intMinutes, seconds: intSeconds});
	},
	
	
	isNumeric: function(objValue)
	{
		return typeof objValue === 'number' && isFinite(objValue);
	},
	
	/** @return a random hex string
	 * 
	 */
	S4: function()
	{
	    return (((1+Math.random())*100000000)|0).toString(16).substring(1);
	},


	/** @return a random string
	 * 
	 */
	generateGuid: function()
	{
        return (this.S4()+this.S4()+"-"+this.S4()+"-"+this.S4()+"-"+this.S4()+"-"+this.S4()+this.S4()+this.S4()).toUpperCase();
	},
	
	

	/** @param strImageFile file path
	 * @param intImageSize (optional) sets image to size
	 * @return filepath with size appended to the end
	 */
	getTrackImage: function (strImageFile, intImageSize)
	{
		
		//Album art isn't working on the browser
		if(intImageSize === undefined)
		{
			intImageSize = 90;
		}
			
		//Strip off the extract fs arguments that may already exist (/path/to/file/filename.mp3:100:100)
		var argsIndex = strImageFile.search(/:[0-9]*:[0-9]*$/);
		if(argsIndex > 0)
		{
			return "/var/luna/data/extractfs" + escape(strImageFile.substr(0, argsIndex)) + strImageFile.substr(argsIndex) + ":" + intImageSize + ":" + intImageSize + ":3";
		}
		else
		{
			return "/var/luna/data/extractfs" + escape(strImageFile) + ":" + intImageSize + ":" + intImageSize + ":3";
		}
	},
	
	fastTrim: function (strRaw)
	{
		if(typeof strRaw === "string")
		{
			return strRaw.replace(/^\s\s*/, '').replace(/\s\s*$/, '');
		}
		
		return strRaw;
	},
	
	
	/**
	* @breif finds the first thumbnail in the list and returns it and how many there are
	* @param arthumbs 
	*			-.data - path to thumbnail
	* @return first 
	*/
	getItemThumb: function (arThumbs, intThumbSize)
	{
		
		var intThumbsTotalCount = 0;
		
		var strThumbURL = "";
		if(arThumbs)
		{
			if(arThumbs.length > 0)
			{
				
				for(var intThumbCountIndex = 0; intThumbCountIndex < arThumbs.length; intThumbCountIndex++)
				{
					
					if(arThumbs[intThumbCountIndex])
					{
						intThumbsTotalCount++;
					}
				}
				
				if(intThumbSize === undefined)
				{
					intThumbSize = 38;
				}
				
				
	
				for(var intThumbIndex = 0; intThumbIndex < arThumbs.length; intThumbIndex++)
				{
					
					if(arThumbs[intThumbIndex] !== null && arThumbs[intThumbIndex] !== undefined)
					{
						
						
						strThumbURL = this.getTrackImage(arThumbs[intThumbIndex].data, intThumbSize);
				
						break;
						
					}
					else
					{
						enyo.log("thumbnail null");
					}
				}
				
				return {strThumbURL: strThumbURL, intThumbsTotalCount: intThumbsTotalCount};
				
			}
		}
		enyo.log("no thumbnail");
		return {strThumbURL: "", intThumbsTotalCount: 0};
	},
	
	
	getBaseLabelChar: function (strRaw)
	{
		
		var charFirst = strRaw.charAt(0);
		if(enyo.g11n.Char.isLetter(charFirst))
		{
			return enyo.g11n.Char.toUpper(enyo.g11n.Char.getBaseString(charFirst));
		}
		
		return "#";
		
	},
	
	getPlaylistSongData: function(objSong)
	{
		if(typeof objSong !== 'object')
		{
			return {};
		}
		var retObjSong = {};
		retObjSong.title = objSong.title;
		retObjSong.artist = objSong.artist;
		retObjSong.path = objSong.path;
		return retObjSong;		
	},
	
	capitalize: function(string)
	{
	    return string.charAt(0).toUpperCase() + string.slice(1);
	}
	
};

