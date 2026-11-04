/*globals enyo, $L, event, Utilities, window */
enyo.kind({
	name: "navplaylists",
	kind: "Control",
	flex: 1,
	events: {onRequestPlaylists: "", onClickPlaylist: "", onCreatePlaylist: "", onDeletePlaylist: ""},
	published: {strEditPlaylistID: "", strSelectedPlaylistID: "", strSelectedPlaylistFile: ""},
	components: [
		{name: "txtResults"},
		{name: "listPlaylists", kind: "VirtualList", onSetupRow: "listSetupRow", flex:1, onclick: "onClick_listPlaylists", components: [
			{name: "itemPlaylist", kind: "SwipeableItem", onConfirm: "onclick_btnDelete", className: "playlist", layoutKind: "HFlexLayout", pack: "justify", align: "center", confirmCaption: $L("Delete"), ondragover: "dragOver", ondrop: "dragDrop", ondragout: "dragOut", components: [
				{name: "iconPlaylist", className:"icon"},
				{name: "playlistName", content: $L("My Playlist"), className:"name", flex: 1}
			//	{name: "btnDelete", onclick: "onclick_btnDelete", className: "delete", showing: false}

			]}
		]},
		{name: "popNewPlaylist", kind: "ModalDialog", onOpen: "onOpen_PopNewPlaylist", onClose: "onClose_PopNewPlaylist", caption: $L("New Playlist"), showKeyboardWhenOpening: true, components: [
			{name: "txtPlaylistName", kind: "Input", hint: $L("Type Playlist Name"), autocorrect: false, spellcheck: false, style: "margin:13px 0;", onkeypress: "onkeypress_txtPlaylistName"},
			{kind: "Control", layoutKind:"HFlexLayout", components: [
				{kind: "Button", flex: 1, caption: $L("Cancel"), onclick: "onclick_Cancel"},
				{kind: "Button", flex: 1, caption: $L("Create"), onclick: "onclick_Create"}
			]}
		]}	
    ],
	
	intJumpRowOffset: 1, //must start at 1, because we always show the one before
	_strNewPlaylistID : null, //for jumping when a playlist is created
	create: function ()
	{
		//test
		this.inherited(arguments);
	},
		
	ready: function ()
	{

		try
		{
			//this.renderList();
		}
		catch (err)
		{
			this.log("**** ready error: " + err);
			//this.$.txtResults.content += "err ready: " + err + " <br/>";
		}
		
	},
	
	renderList: function ()
	{
		//this.$.virtualScroller.render();
		this.log();
		this.arPlaylists = [];
		
		this.strCurrentRequestID = Utilities.generateGuid();
		this.intJumpRowOffset = 1;
		this.getPlaylists("com.palm.music.staticplaylists:1");
		this.getPlaylists("com.palm.media.playlist.file:1");
	},
	
	refreshList: function ()
	{
		this.$.listPlaylists.refresh();		
	},
	
	scrollerScroll: function (scrollTo)
	{
		//this.$.virtualScroller.scroll(inSender, inTop);
		var pageSize = this.$.listPlaylists.getPageSize();
		this.$.listPlaylists.$.scroller.adjustTop(scrollTo);
		this.$.listPlaylists.$.scroller.adjustBottom(scrollTo + pageSize);
		this.$.listPlaylists.$.scroller.top = scrollTo;
		this.$.listPlaylists.$.scroller.bottom = scrollTo + pageSize;
	},
	
	getPlaylists: function (strPlaylistKind) 
	{
		
		this.log(strPlaylistKind);
	
		var objGetPlaylistsRequest = {where: [], playlistKind: strPlaylistKind, callback: enyo.bind(this, "gotPlaylists"), strCurrentRequestID: this.strCurrentRequestID};

		this.log("**** raising doRequestMedia");
		this.log("**** objGetMediaRequest: ", objGetPlaylistsRequest);
		
		this.doRequestPlaylists(objGetPlaylistsRequest);
		
	},
	
	gotPlaylists: function (inResponse, inRequest)
	{
		this.log();
		try
			{
			
			//this.arPlaylists = inResponse.results.slice(0);
			
			if(this.strCurrentRequestID === inRequest.objGetPlaylistsRequest.strCurrentRequestID)
			{
				this.arPlaylists = this.arPlaylists.concat(inResponse.results);
			}
			/*
			else
			{
				this.arPlaylists = inResponse.results;
			}
			*/
			
			this.log("this.arPlaylists.length: " + this.arPlaylists.length);

			//if(this.strEditPlaylistID !== "")
			//{
			
			for(var intCheckIndex = 0; intCheckIndex < this.arPlaylists.length; intCheckIndex++)
			{
				this.log("intCheckIndex matched: " + intCheckIndex);
				if(this.arPlaylists[intCheckIndex]._id === this._strNewPlaylistID || this.arPlaylists[intCheckIndex].path === this.strSelectedPlaylistFile)
				{
					this.log("intCheckIndex matched: " + intCheckIndex);
					this.intJumpRowOffset = Math.max(1, intCheckIndex);
				}
			}
			//}
			
			this.$.listPlaylists.resized();
				
			this.$.listPlaylists.punt();

		}
		catch (err)
		{
			this.log("error: " + err);
		}
		
	},	
	
	listSetupRow: function (inSender, intIndex)
	{
		//this.log();
		try
		{
			intIndex = intIndex  + this.intJumpRowOffset-1;
		
			if(this.arPlaylists !== undefined)
			{
				//this.log("this.arPlaylists[" + intIndex + "]: " + this.arPlaylists[intIndex]);
				if(this.arPlaylists[intIndex] !== undefined)
				{
					//TODO: performance: should only be set once just before or after listSetupRow
					this._strNewPlaylistID = null;
					//this.log(this.arPlaylists[intIndex].name);
					
					//this.log(this.arPlaylists[intIndex]._kind);
					//this.log(this.arPlaylists[intIndex]._id);
					this.$.itemPlaylist.addRemoveClass("enyo-first", (intIndex < 1));
					//this.$.itemPlaylist.addRemoveClass("enyo-last", (intIndex === this.arPlaylists.length - 1));
					
					this.$.itemPlaylist.addRemoveClass("active", (this.arPlaylists[intIndex]._id === this.strSelectedPlaylistID || this.arPlaylists[intIndex].path === this.strSelectedPlaylistFile));

					switch(this.arPlaylists[intIndex]._kind)
					{

						case "com.palm.music.staticplaylists:1":
							this.$.playlistName.setContent(this.arPlaylists[intIndex].name);
					
							break;
													
						case "com.palm.media.playlist.file:1":
							this.$.playlistName.setContent(this.arPlaylists[intIndex].title);
						
							break;
						
					}

					this.$.itemPlaylist.addRemoveClass("readonly", (this.arPlaylists[intIndex]._kind === "com.palm.media.playlist.file:1"));
					
					//this.log("this.strEditPlaylistID: " + this.arPlaylists[intIndex]._id === this.strEditPlaylistID);
					this.$.itemPlaylist.addRemoveClass("editing", (this.arPlaylists[intIndex]._id === this.strEditPlaylistID));
					
					//this.$.btnDelete.setShowing(this.arPlaylists[intIndex]._id === this.strEditPlaylistID);
					
					
					
					return true;
				}
				
			}
		}
		catch(err)
		{
			this.log(err);
		}
		
		return false;

	},

	clearSelection: function()
	{
		this.setStrSelectedPlaylistID("");
		this.setStrEditPlaylistID("");
	},
	
	onClick_listPlaylists: function(sender, event)
	{
		
		this.log(event.rowIndex);
		
		var intIndex = event.rowIndex  + this.intJumpRowOffset - 1;
	
		if(intIndex !== undefined && this.arPlaylists[intIndex])
		{
			
			if(this.arPlaylists[intIndex]._id !== this.strEditPlaylistID)
			{
				this.strEditPlaylistID = "";
			
				this.log("this.arPlaylists[" + intIndex + "]: " + this.arPlaylists[intIndex]);
				
				if(this.arPlaylists[intIndex])
				{
					
					
					
					var strPlaylistNameField = "";

					switch(this.arPlaylists[intIndex]._kind)
					{
						
						
						case "com.palm.music.staticplaylists:1":
							strPlaylistNameField = "name";
							break;
													
						case "com.palm.media.playlist.file:1":
							strPlaylistNameField = "title";
							break;
						
					}					
					
					this.goToPlaylist({strPlaylistName	: this.arPlaylists[intIndex][strPlaylistNameField], 
									   strPlaylistID	: this.arPlaylists[intIndex]._id, 
									   strPlaylistKind  : this.arPlaylists[intIndex]._kind});
					
				}
				
			}
			
		}

	},
	
	/**
	 * @param objPlaylist
	 *				-strPlaylistName
	 *				-strPlaylistID
	 *				-strPlaylistKind
	 * @param forceRender - refreshes or rerenders playlist list
	 */
	goToPlaylist: function(objPlaylist, forceRender){
		
		this.setStrSelectedPlaylistID(objPlaylist.strPlaylistID);
		
		if(forceRender){
			this.renderList();
		}
		else
		{
			this.refreshList();
		}
		
		this.doClickPlaylist(objPlaylist);
	},
	
	strEditPlaylistIDChanged: function ()
	{
		this.log();	
		this.refreshList();
	},
	
	strSelectedPlaylistFileChanged: function ()
	{
		this.log();	
		this.refreshList();		
	},
	
	
	ShowPopNewPlaylist: function (sender, event)
	{
		this.$.popNewPlaylist.openAtCenter();
	},
	
	onOpen_PopNewPlaylist: function()
	{
		this.$.txtPlaylistName.forceFocus();
		//for some reason ManualMode was being set
		enyo.keyboard.setManualMode(false);
	},
	
	onClose_PopNewPlaylist: function(){
		this.$.txtPlaylistName.forceBlur();
		enyo.keyboard.hide();
		//must set back or keyboard won't come up anymore
		enyo.keyboard.setManualMode(false);
	},
	
	onkeypress_txtPlaylistName: function (sender, event)
	{
		this.log(event.keyCode);
		
		if(event.keyCode === 13)
		{
			this.onclick_Create();
		}
		
		
	},
	
	onclick_Create: function ()
	{
		if(Utilities.fastTrim( this.$.txtPlaylistName.getValue()) !== "")
		{
			var strNewPlaylistName = this.$.txtPlaylistName.getValue();
			var objPutPlaylistsRequest = {strPlaylistName: strNewPlaylistName, callback: enyo.bind(this, "onSuccess_GoToPlaylist", strNewPlaylistName)};
			
			this.$.txtPlaylistName.setValue("");
			this.$.popNewPlaylist.close();
			
			this.doCreatePlaylist(objPutPlaylistsRequest);
		
		}
		
	},
	
	onSuccess_GoToPlaylist: function(strPlaylistName, strPlaylistID){
		this._strNewPlaylistID = strPlaylistID;
        this.renderList();
		/*this.goToPlaylist({strPlaylistName	: strPlaylistName, 
							strPlaylistID	: strPlaylistID, 
							strPlaylistKind : "com.palm.music.staticplaylists:1"}, true);*/
		
	},
	
	onclick_Cancel: function (sender, event)
	{
		this.$.txtPlaylistName.setValue("");
		this.$.popNewPlaylist.close();
	},
	
	
	onclick_btnDelete: function (sender, intIndex)
	{
		intIndex += this.intJumpRowOffset - 1;
		
		if(intIndex !== undefined)
		{
			
			if(this.arPlaylists[intIndex])
			{
				var objDeletePlaylist = {strPlaylistID: this.arPlaylists[intIndex]._id};
				this.clearSelection();
				this.doDeletePlaylist(objDeletePlaylist);			
			}
		}
	
	
	},
	
	
	
//oks: this is what i imagine would happen when you edit the playlist.	
//	editPlaylist: function(item) {
//		
//		iappropriate item .addClass('editing');
//		appropriate item's btnDelete.show();
//
//	},
	dragOver: function(inSender, event) {
		// if there is drag info, indicate the target is being dragged over
		////this.log(event);
		
		//this.log(event);
		
		if (event.dragInfo !== undefined) {
//			inSender.applyStyle("background-image", "url(images/bg_drag_target.png)");
//			inSender.applyStyle("background-repeat", "repeat-x");
			//this.scrollerScroll(Math.max(0, event.rowIndex-2));
			if(this.arPlaylists[event.rowIndex  + this.intJumpRowOffset -1]._kind !== "com.palm.media.playlist.file:1")
			{
				inSender.addClass('editing');
				event.dragInfo.boolShowIcon = true;
			}
			else
			{
				event.dragInfo.boolShowIcon = false;				
			}
		}
		
	},
	dragOut: function(inSender, event) {
		////this.log(event)\;
		if (event.dragInfo !== undefined) {
			inSender.removeClass('editing');
//			inSender.applyStyle("background-image", null);
//			inSender.applyStyle("background-repeat", null);
			event.dragInfo.boolShowIcon =false;
		}
		
	},
	dragDrop: function(inSender, event) {
		// if there is drag info, do a drop
		this.log();
		this.log(inSender);
		//this.log(event);
		////this.log(event);
		if (event.dragInfo !== undefined) {
			inSender.removeClass('editing');
	
			var intIndex = event.rowIndex  + this.intJumpRowOffset -1;
			
			if(this.arPlaylists[intIndex]._kind !== "com.palm.media.playlist.file:1")
			{
//			inSender.applyStyle("background-image", null);
//			inSender.applyStyle("background-repeat", null);
			
				event.dragInfo.strPlaylistName = this.arPlaylists[intIndex].name;
				event.dragInfo.strPlaylistID = this.arPlaylists[intIndex]._id;
			}
			
		}
		
	},
	resize: function(intContentHeight) {
		//this.applyStyle("height",intContentHeight + "px");
		this.$.listPlaylists.applyStyle("height",intContentHeight + "px");
		this.resized();

		//this.$.listPlaylists.resized();
		//this.$.listPlaylists.punt();
	}



		
		
});
        