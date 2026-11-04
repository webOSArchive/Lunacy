/*globals enyo, $L, event, Utilities, window, PalmSystem, MediaIndex,  */
enyo.kind(
{
	name: "kindMediaIndex",
	kind: "Component",
	events: {onSetPlaybackList: ""},
	components: [
		{kind: "DbService", method: "find", name: "dbsFindMedia", onSuccess: "gotMedia" , onFailure: "onFailure_dbsFindMedia"},
		{kind: "DbService", method: "find", name: "browserDBFindsong", onSuccess: "gotMedia" , onFailure: "onFailure_dbsFindMedia"},
		{kind: "DbService", method: "find", name: "browserDBFindartist", onSuccess: "gotMedia" , onFailure: "onFailure_dbsFindMedia"},
		{kind: "DbService", method: "find", name: "browserDBFindalbum", onSuccess: "gotMedia" , onFailure: "onFailure_dbsFindMedia"},
		{kind: "DbService", method: "find", name: "browserDBFindgenre", onSuccess: "gotMedia" , onFailure: "onFailure_dbsFindMedia"}
	],
	
	browserMode: true,  //true if on device, false in browser
	
	create: function ()
	{
		this.inherited(arguments);
		this.pickBrowserMode();
		this.log("Is Browser Mode?  ", this.browserMode);
	},
	
	/**
	 *  @brief Switches browsermode to true if on device, false if on browser
	 */
	pickBrowserMode: function()
	{
		this.browserMode = window.navigator.userAgent.search(/webos/i) < 0 ? true: false;
	},
	
	/**
	 * @brief Returns mediakind from a string
	 * @param mediaType - "song", "artist", "album", or "genra"
	 * @return dbKind - The associated media kind
	 */
	getFullMediaKind: function(mediaType)
	{
		var dbKind;
		switch (mediaType)
		{
			case "song":
				dbKind = "com.palm.media.audio.file:1";
				break;
			
			case "artist":
				dbKind = "com.palm.media.audio.artist:1";
				break;
			
			case "album":
				dbKind = "com.palm.media.audio.album:1";
				break;
			
			case "genre":
				dbKind = "com.palm.media.audio.genre:1";
				break;
				
			default:
				dbKind = undefined;
				break;
		}
		return dbKind;
	},
	
	/**
	* @brief Get Media only if its a compatible Media kind
	* @param objGetMediaRequest
	*			.mediaType .order .desc .where
	*/
	requestMedia: function (objGetMediaRequest)
	{
		if(objGetMediaRequest.mediaType !== undefined)
		{
			var dbsKind = this.getFullMediaKind(objGetMediaRequest.mediaType);
			if (dbsKind)
			{
				objGetMediaRequest.dbsKind = dbsKind;
				objGetMediaRequest.results = [];
				this.getMedia(objGetMediaRequest);
			}
		}
		
	},
	/**
	 * @brief Get Media Based on Kind and recurse through all pages
	 * @param objGetMediaRequest 
	 *			.mediaType .order .desc .where
	 * @param nextPage (optional) 
	 */
	getMedia: function(objGetMediaRequest,nextPage)
	{
		var dbQuery = {
			watch:false,
			subscribe:false,
			count:true,
			query: {
				orderBy: objGetMediaRequest.order || "",
				desc: objGetMediaRequest.desc || false,
				where: objGetMediaRequest.where || [],
				page: nextPage || ""
			}
		};
		
		/*
		if(objGetMediaRequest.collate)
		{
			dbQuery.query.collate = objGetMediaRequest.collate;
		}
		*/
		
		this.log("query.desc: ", dbQuery.query.desc);

		//this.log("query.collate: ", dbQuery.query.collate);
		
		var serviceobject = (this.browserMode) ? this.$["browserDBFind"+objGetMediaRequest.mediaType] : this.$.dbsFindMedia;
		if(typeof(serviceobject) === "undefined"){
			serviceobject = this.$.dbsFindMedia;
		}
		serviceobject.setMethod(objGetMediaRequest.queryType || "find");
		serviceobject.setDbKind(objGetMediaRequest.dbsKind);
		this.log(dbQuery);
		serviceobject.call(dbQuery,{"objGetMediaRequest":objGetMediaRequest});
	},
	
	/**
	 * @brief Recieve Media response, get more media if additional page exists
	 * @param inSender 
	 * @param inResponse
	 *			.results .next(optional)
	 * @param inRequest
	 *			.objGetMediaRequest
	 */
	gotMedia: function(inSender, inResponse, inRequest)
	{
		var objGetMediaRequest = inRequest.objGetMediaRequest;
		
		this.log("inResponse.results: ", inResponse.results);
		
		objGetMediaRequest.results = objGetMediaRequest.results.concat(inResponse.results);
		
		this.log("objGetMediaRequest.results: ", objGetMediaRequest.results);
	
		if (inResponse.next)
		{
			this.getMedia(objGetMediaRequest,inResponse.next);
		}
		else
		{
			if (objGetMediaRequest.dbsKind === "com.palm.media.audio.file:1" && Boolean(objGetMediaRequest.boolForcePlay))
			{
				this.playSongs(objGetMediaRequest);
			}
			if (objGetMediaRequest.callback)
			{
				inResponse.results = objGetMediaRequest.results;
				objGetMediaRequest.callback(inResponse,inRequest);
			}
		}
	},
	
	/**
	 * @brief Report Service Error
	 */
	onFailure_dbsFindMedia: function (inSender, inResponse)
	{
		
		this.log();
		this.log(inSender);
		this.log(inResponse);
		
	},
	
	/**
	 *  @brief sends request to play a list of songs
	 *  @param objGetMediarequest
	 *			.results  .strListType .strListText .strListSubtext .strListImage
	 */
	playSongs: function (objGetMediaRequest)
	{
		//We keep this at 0 to play the first song, unless we want the possibility of shuffling
		var intStartTrackIndex = 0;
		if(objGetMediaRequest.playFirst === false){
			intStartTrackIndex = undefined;
		}
		
		var objSetPlaybackList = {arSetPlaybackList: objGetMediaRequest.results, intStartTrackIndex: intStartTrackIndex, intStartTrackTime: 0, 
								  strOriginListID: Utilities.generateGuid(), strListType: objGetMediaRequest.strListType, 
								  strListText: objGetMediaRequest.strListText, strListSubText: "", 
								  strArtist	: objGetMediaRequest.strArtist,
								  arThumbnails: objGetMediaRequest.results[0].thumbnails || []};
		
		
		this.doSetPlaybackList(objSetPlaybackList);

	}

});