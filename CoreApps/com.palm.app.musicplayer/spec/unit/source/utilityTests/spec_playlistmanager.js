/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return new kindPlaylistManager();
//			}
//var objName = "playlistManager";
//
//var before = function (obj, callbacks) {
//		obj.startWatch=function(){};
//	}
//var event = [[null, {rowIndex:0}]];
//var params = {
//		getPlaylists : [[{}]],
//		insertStaticPlaylist : [[{strPlaylistName: "name", callback : function(){}}]],
//		gotPlaylists : [[{},{},{}]],
//		onSuccess_PutStaticPlaylists : [[undefined, {results: [{id : 1}]}]],
//		addToPlaylist : [[{strMediaType : "song", strMediaFilter: "filter", strPlaylistID: 1}]],
//		addLooseSongs : [[{}]],
//		onSuccess_MergeStaticPlaylists : [[{},{},{objUpdatePlaylist: {callback: function(){}}}]],
//		deletePlaylist : [[{}]]
//};
//
//specOut(creator, objName, before, params);

describe('playlistManager tests', function(){
	var creator = function (){
				return new kindPlaylistManager();
			};
	var playlistManager;
	beforeEach(function(){
		playlistManager = creator();
		var before = function (obj, callbacks) {
		obj.startWatch=function(){};
	};
		var callback = function(one, fun){return fun;};
		before(playlistManager,callback);
	});
	it('should correctly set up object', function(){
		expect(playlistManager._componentNameMap).toEqual({"dbService":2});
		expect(typeof playlistManager.$).toBe('object');
		expect(playlistManager.id).toEqual("");
		expect(playlistManager.kind).toEqual("Component");
		expect(playlistManager.events).toEqual({"onSetPlaybackList":"","onRequestMedia":"","onRefreshPlaylists":"","onPlaylistDeleted":""});
		expect(playlistManager.kindName).toEqual("kindPlaylistManager");
		expect(playlistManager.kindComponents).toEqual([{"kind":"DbService","dbKind":"com.palm.music.staticplaylists:1","onFailure":"onFailure_dbsStaticPlaylists","components":[{"name":"dbsGetStaticPlaylists","method":"find","onSuccess":"gotPlaylists"},{"name":"dbsPutStaticPlaylists","method":"put","onSuccess":"onSuccess_PutStaticPlaylists"},{"name":"dbsMergeStaticPlaylists","method":"merge","onSuccess":"onSuccess_MergeStaticPlaylists","onFailure":"onFailure_MergeStaticPlaylists"},{"name":"dbsDelStaticPlaylists","method":"del","onSuccess":"onSuccess_DeleStaticPlaylists"}]},{"name":"dbsFilePlaylists","kind":"DbService","dbKind":"com.palm.media.playlist.object:1","onSuccess":"gotPlaylists","onFailure":"onFailure_dbsStaticPlaylists","components":[]}]);
		expect(playlistManager.onSetPlaybackList).toEqual("");
		expect(playlistManager.onRequestMedia).toEqual("");
		expect(playlistManager.onRefreshPlaylists).toEqual("");
		expect(playlistManager.onPlaylistDeleted).toEqual("");
		expect(playlistManager.published).toEqual({"owner":null,"name":""});
		expect(playlistManager.defaultKind).toEqual("Component");
		expect(playlistManager.wantsEvents).toEqual(true);
		expect(playlistManager.owner).toEqual(null);
		expect(playlistManager.name).toEqual("");
	});
	describe('test (startWatch)', function(){
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(playlistManager,'inherited');
			playlistManager.create();
			expect(playlistManager.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (requestPlaylists)', function(){
		it('should call getPlaylists',function(){
			spyOn(playlistManager,'getPlaylists');
			playlistManager.requestPlaylists();
			expect(playlistManager.getPlaylists).toHaveBeenCalledWith(undefined);
		});
		it('should call getPlaylists with params',function(){
			spyOn(playlistManager,'getPlaylists');
			var parm = {"myobj": "asdf"};
			playlistManager.requestPlaylists(parm);
			expect(playlistManager.getPlaylists).toHaveBeenCalledWith(parm);
		});
	});
	describe('test (getPlaylists)', function(){
		
			it('should call setDbKind',function(){
				spyOn(playlistManager.$.dbsGetStaticPlaylists,'setDbKind');
				var parm = {"myobj": "asdf"};
				playlistManager.getPlaylists(parm);
				expect(playlistManager.$.dbsGetStaticPlaylists.setDbKind).toHaveBeenCalled();
			});
			it('should call setDbKind with playlistKind',function(){
				spyOn(playlistManager.$.dbsGetStaticPlaylists,'setDbKind');
				var parm = {"myobj": "asdf", playlistKind: "myKind"};
				playlistManager.getPlaylists(parm);
				expect(playlistManager.$.dbsGetStaticPlaylists.setDbKind).toHaveBeenCalledWith("myKind");
			});
			
			it('should call this.$.dbsGetStaticPlaylists.call',function(){
				spyOn(playlistManager.$.dbsGetStaticPlaylists,'call');
				var parm = {"myobj": "asdf"};
				playlistManager.getPlaylists(parm);
				expect(playlistManager.$.dbsGetStaticPlaylists.call).toHaveBeenCalled();
			});
	});
	
	describe('test (gotPlaylists)', function(){
		
		it("should call objGetPlaylistRequest on inRequest", function(){
			var inRequest = {objGetPlaylistsRequest : {callback: function(){}}};
			var inResponse = {results: [], objGetPlaylistsRequest: {callback : function(){}}}
			spyOn(inRequest.objGetPlaylistsRequest,'callback');
			var parm = {"myobj": "asdf"};
			playlistManager.gotPlaylists(this, inResponse, inRequest);
			expect(inRequest.objGetPlaylistsRequest.callback).toHaveBeenCalledWith(inResponse, inRequest);
		});
		
		
	});
	describe('test (insertStaticPlaylist)', function(){
		it("should set this.putStaticPlaylistsCallback", function(){
			playlistManager.$.dbsPutStaticPlaylists.call = function(){};
			var objPutPlaylistsRequest = {strPlaylistName: "name", callback : function(){}};
			playlistManager.putStaticPlaylistsCallback = null;
			playlistManager.insertStaticPlaylist(objPutPlaylistsRequest);
			expect(playlistManager.putStaticPlaylistsCallback).toBe(objPutPlaylistsRequest.callback);
		});
		it("should call this.$.dbsPutStaticPlaylists.call", function(){
			var objPutPlaylistsRequest = {strPlaylistName: "name", callback : function(){}};
			playlistManager.putStaticPlaylistsCallback = null;
			spyOn(playlistManager.$.dbsPutStaticPlaylists, "call");
			playlistManager.insertStaticPlaylist(objPutPlaylistsRequest);
			expect(playlistManager.$.dbsPutStaticPlaylists.call).toHaveBeenCalledWith({objects: [{"_kind":"com.palm.music.staticplaylists:1","name": objPutPlaylistsRequest.strPlaylistName, "tracks":[]}]});
		});
	});
	describe('test (onSuccess_PutStaticPlaylists)', function(){
		beforeEach(function(){
			playlistManager.putStaticPlaylistsCallback = function(){};
		});
		describe("with results", function(){
			var response = {results : [{id: "name"}]};
			beforeEach(function(){
				response = {results : [{id: "name"}]};
			});
			it("should call putStaticPlaylistsCallback", function(){
				spyOn(playlistManager, "putStaticPlaylistsCallback");
				playlistManager.onSuccess_PutStaticPlaylists(this, response);
				expect(playlistManager.putStaticPlaylistsCallback).toHaveBeenCalledWith("name");
			});

			it("should delete putStaticPlaylistsCallback", function(){
				//Can't test due to pitfalls in jasmine
				//playlistManager.onSuccess_PutStaticPlaylists(this, response);
				//expect(typeof playlistManager.putStaticPlaylistsCallback).toBe("undefined");
			});
		});
		describe("without results", function(){
			var response = {results : []};
			beforeEach(function(){
				response = {results : []};
			});
			it("should call putStaticPlaylistsCallback", function(){
				spyOn(playlistManager, "putStaticPlaylistsCallback");
				playlistManager.onSuccess_PutStaticPlaylists(this, response);
				expect(playlistManager.putStaticPlaylistsCallback).not.toHaveBeenCalled();
			});

			it("should delete putStaticPlaylistsCallback", function(){
				//Can't test due to pitfalls in jasmine
				//playlistManager.onSuccess_PutStaticPlaylists(this, response);
				//expect(typeof playlistManager.putStaticPlaylistsCallback).toBe("undefined");
			});
		});

	});
	describe('test (onFailure_dbsStaticPlaylists)', function(){
		it("should have the function", function(){
			playlistManager.onFailure_dbsStaticPlaylists();
		});
	});
	describe('test (addToPlaylist)', function(){
		/*
		describe('testing (addToPlaylist) with params [{"strMediaType":"song","strMediaFilter":"filter","strPlaylistID":1}]', function(){
			it('should call addLooseSongs',function(){
				spyOn(playlistManager,'addLooseSongs');
				var obj = {"strMediaType":"song","strMediaFilter":"filter","strPlaylistID":1};
				var ret = playlistManager.addToPlaylist(obj);
				expect(playlistManager.addLooseSongs).toHaveBeenCalled();
				expect(ret).toBe(true);
			});
		});
		*/
		describe('testing (addToPlaylist) with not a song', function(){
			it('should call doRequestMedia with artist',function(){
				spyOn(playlistManager,'doRequestMedia');
				var objAddToPlaylist = {"strMediaType":"artist","strMediaFilter":"filter","strMediaFilterSub": "filter2", "strPlaylistID":1};
				var ret = playlistManager.addToPlaylist(objAddToPlaylist);
				expect(playlistManager.doRequestMedia).toHaveBeenCalled();
			});
			it('should call doRequestMedia with album',function(){
				spyOn(playlistManager,'doRequestMedia');
				var objAddToPlaylist = {"strMediaType":"album","strMediaFilter":"filter","strMediaFilterSub": "filter2", "strPlaylistID":1};
				var ret = playlistManager.addToPlaylist(objAddToPlaylist);
				expect(playlistManager.doRequestMedia).toHaveBeenCalled();
			});
			it('should call doRequestMedia with genre',function(){
				spyOn(playlistManager,'doRequestMedia');
				var objAddToPlaylist = {"strMediaType":"genre","strMediaFilter":"filter","strMediaFilterSub": "filter2", "strPlaylistID":1};
				var ret = playlistManager.addToPlaylist(objAddToPlaylist);
				expect(playlistManager.doRequestMedia).toHaveBeenCalled();
			});
			it('should call doRequestMedia with nothing',function(){
				spyOn(playlistManager,'doRequestMedia');
				var objAddToPlaylist = {"strMediaType":"nothing","strMediaFilter":"filter","strMediaFilterSub": "filter2", "strPlaylistID":1};
				var ret = playlistManager.addToPlaylist(objAddToPlaylist);
				expect(playlistManager.doRequestMedia).toHaveBeenCalledWith(undefined);
			});
		});
		
		
	});
	describe('test (addLooseSongs)', function(){
		describe('testing (addLooseSongs) with params [{}]', function(){
			it('should call getPlaylists',function(){
				spyOn(playlistManager,'getPlaylists');
				playlistManager.addLooseSongs({});
				expect(playlistManager.getPlaylists).toHaveBeenCalled();
			});
		});
	});
	describe('test (gotAddTracks)', function(){
		var inResponse = {results: [{"title": "mytitle"}]};
		var inRequest = {objGetMediaRequest: {"plid": "mytitle"}};
		beforeEach(function(){
			inResponse = {results: [{"title": "mytitle"}]};
			inRequest = {objGetMediaRequest: {"plid": "mytitle"}};
		})
		it("should call Utilities.getPlaylistSongData", function(){
			spyOn(Utilities, "getPlaylistSongData");
			playlistManager.gotAddTracks(inResponse, inRequest);
			expect(Utilities.getPlaylistSongData).toHaveBeenCalled();
		});
		it("should call playlistManager.getPlaylists", function(){
			spyOn(playlistManager, "getPlaylists");
			playlistManager.gotAddTracks(inResponse, inRequest);
			expect(playlistManager.getPlaylists).toHaveBeenCalled();
		});
		it("shouldn't call playlistManager.getPlaylists if no inRequest", function(){
			spyOn(playlistManager, "getPlaylists");
			playlistManager.gotAddTracks(inResponse, undefined);
			expect(playlistManager.getPlaylists).not.toHaveBeenCalled();
		});
		it("shouldn't call Utilities.getPlaylistSongData if no results", function(){
			inResponse.results = [];
			spyOn(Utilities, "getPlaylistSongData");
			playlistManager.gotAddTracks(inResponse, inRequest);
			expect(Utilities.getPlaylistSongData).not.toHaveBeenCalled();
		});
		it("shouldn't call UplaylistManager.getPlaylists if no results", function(){
			inResponse.results = [];
			spyOn(playlistManager, "getPlaylists");
			playlistManager.gotAddTracks(inResponse, inRequest);
			expect(playlistManager.getPlaylists).not.toHaveBeenCalled();
		});
	});
	describe('test (gotAddPlaylist)', function(){
		var inResponse = {results : [{tracks : []}]}; 
		var inRequest = {objGetPlaylistsRequest : {arAddTracks: []}};
		beforeEach(function(){
			
		});
		it("should call this.$.dbsMergeStaticPlaylists.call", function(){
			spyOn(playlistManager.$.dbsMergeStaticPlaylists, "call");
			playlistManager.gotAddPlaylist(inResponse, inRequest);
			expect(playlistManager.$.dbsMergeStaticPlaylists.call).toHaveBeenCalled();
		});
		it("shouldn't call this.$.dbsMergeStaticPlaylists.call with  no tracks", function(){

			inResponse = {results : [{}]}; 
			spyOn(playlistManager.$.dbsMergeStaticPlaylists, "call");
			playlistManager.gotAddPlaylist(inResponse, inRequest);
			expect(playlistManager.$.dbsMergeStaticPlaylists.call).not.toHaveBeenCalled();
		});
		it("shouldn't call this.$.dbsMergeStaticPlaylists.call with  no results", function(){

			inResponse = {results : []}; 
			spyOn(playlistManager.$.dbsMergeStaticPlaylists, "call");
			playlistManager.gotAddPlaylist(inResponse, inRequest);
			expect(playlistManager.$.dbsMergeStaticPlaylists.call).not.toHaveBeenCalled();
		});
		it("shouldn't call this.$.dbsMergeStaticPlaylists.call with  no inrequest", function(){
			spyOn(playlistManager.$.dbsMergeStaticPlaylists, "call");
			playlistManager.gotAddPlaylist(inResponse, undefined);
			expect(playlistManager.$.dbsMergeStaticPlaylists.call).not.toHaveBeenCalled();
		});


	});
	describe('test (updateStaticPlaylist)', function(){
		it("should call  this.$.dbsMergeStaticPlaylists.call with objPlaylist", function(){
			spyOn(playlistManager.$.dbsMergeStaticPlaylists, "call");
			var param = {objPlaylist: []};
			playlistManager.updateStaticPlaylist(param);
			expect(playlistManager.$.dbsMergeStaticPlaylists.call).toHaveBeenCalledWith({objects: [param.objPlaylist]});
		});
		it("should handle breaks", function(){
			spyOn(playlistManager.$.dbsMergeStaticPlaylists, "call");
			playlistManager.updateStaticPlaylist( undefined);
			expect(playlistManager.$.dbsMergeStaticPlaylists.call).not.toHaveBeenCalled();
		});
	});
	describe('test (onSuccess_MergeStaticPlaylists)', function(){
		describe('testing (onSuccess_MergeStaticPlaylists) with params [{},{},{"objUpdatePlaylist":{callback:function(){}}}]', function(){
			/*
			it("should call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				playlistManager.onSuccess_MergeStaticPlaylists( {},{},{"objUpdatePlaylist":{callback:function(){}}});
				expect(enyo.windows.addBannerMessage).toHaveBeenCalledWith($L("Playlist updated"), "{}", "images/notification-small.png");
			});
			it("should doRefreshPlaylists",function(){
				spyOn(playlistManager, "doRefreshPlaylists");
				playlistManager.onSuccess_MergeStaticPlaylists( {},{},{"objUpdatePlaylist":{callback:function(){}}});
				expect(playlistManager.doRefreshPlaylists).toHaveBeenCalled();
			});
			it("should callback",function(){
				spyOn(playlistManager, "doRefreshPlaylists");
				var inRequest ={"objUpdatePlaylist":{callback:function(){}}};
				spyOn(inRequest.objUpdatePlaylist, "callback");
				playlistManager.onSuccess_MergeStaticPlaylists( {},{},inRequest);
				expect(inRequest.objUpdatePlaylist.callback).toHaveBeenCalled();
			
			});
			*/
		});
		/*
		describe('testing (onSuccess_MergeStaticPlaylists) with params [{},{},{"objUpdatePlaylist":{}}]', function(){
			it("should call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				playlistManager.onSuccess_MergeStaticPlaylists( {},{},{"objUpdatePlaylist":{}});
				expect(enyo.windows.addBannerMessage).toHaveBeenCalledWith($L("Playlist updated"), "{}", "images/notification-small.png");
			});
			it("should doRefreshPlaylists",function(){
				spyOn(playlistManager, "doRefreshPlaylists");
				playlistManager.onSuccess_MergeStaticPlaylists( {},{},{"objUpdatePlaylist":{}});
				expect(playlistManager.doRefreshPlaylists).toHaveBeenCalled();
			});
		
		});
		*/
		describe('testing (onSuccess_MergeStaticPlaylists) with params [{},{},{}]', function(){
			/*
			it("should call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				playlistManager.onSuccess_MergeStaticPlaylists( {},{},{});
				expect(enyo.windows.addBannerMessage).toHaveBeenCalledWith($L("Playlist updated"), "{}", "images/notification-small.png");
			});
			*/
			it("should doRefreshPlaylists",function(){
				spyOn(playlistManager, "doRefreshPlaylists");
				playlistManager.onSuccess_MergeStaticPlaylists( {},{},{});
				expect(playlistManager.doRefreshPlaylists).not.toHaveBeenCalled();
			});
		
			
		});
	});
	describe('test (onFailure_MergeStaticPlaylists)', function(){
		describe('with callback', function(){
			/*
			it("should call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				playlistManager.onFailure_MergeStaticPlaylists( {},{},{"objUpdatePlaylist":{callback: function(){}}});
				expect(enyo.windows.addBannerMessage).toHaveBeenCalledWith($L("Error updating playlist"), "{}", "images/notification-small.png");
			});
			*/
			it("should callback",function(){
				spyOn(playlistManager, "doRefreshPlaylists");
				var inRequest ={"objUpdatePlaylist":{callback:function(){}}};
				spyOn(inRequest.objUpdatePlaylist, "callback");
				playlistManager.onFailure_MergeStaticPlaylists( {},{},inRequest);
				expect(inRequest.objUpdatePlaylist.callback).toHaveBeenCalled();
			
			});
		});
		describe('without callback', function(){
			/*
			it("should call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				playlistManager.onFailure_MergeStaticPlaylists( {},{},{});
				expect(enyo.windows.addBannerMessage).toHaveBeenCalledWith($L("Error updating playlist"), "{}", "images/notification-small.png");
			});
			*/
		});
	
	
	});
	describe('test (deletePlaylist)', function(){
		it("should call  this.$.dbsMergeStaticPlaylists.call with objPlaylist", function(){
			spyOn(playlistManager.$.dbsDelStaticPlaylists, "call").andReturn({});
			var param = {strPlaylistID: 1};
			playlistManager.deletePlaylist(param);
			expect(playlistManager.$.dbsDelStaticPlaylists.call).toHaveBeenCalledWith({ids: [param.strPlaylistID]});
		});
		it("should handle breaks", function(){
			spyOn(playlistManager.$.dbsDelStaticPlaylists, "call").andReturn({});
			playlistManager.deletePlaylist({});
			expect(playlistManager.$.dbsDelStaticPlaylists.call).not.toHaveBeenCalled();
		});
	});
	describe('test (onSuccess_DeleStaticPlaylists)', function(){
		it('should call doRefreshPlaylists',function(){
			spyOn(playlistManager,'doRefreshPlaylists');
			playlistManager.onSuccess_DeleStaticPlaylists(null,null,{objDeletePlaylist: {callback : function(){}}});
			expect(playlistManager.doRefreshPlaylists).toHaveBeenCalled();
		});
		it('should call doPlaylistDeleted',function(){
			spyOn(playlistManager,'doPlaylistDeleted');
			playlistManager.onSuccess_DeleStaticPlaylists(null,null,{objDeletePlaylist: {callback : function(){}}});
			expect(playlistManager.doPlaylistDeleted).toHaveBeenCalled();
		});
		it('should call callback',function(){
			var param ={objDeletePlaylist: {callback : function(){}}};
			spyOn(param.objDeletePlaylist,'callback');
			playlistManager.onSuccess_DeleStaticPlaylists(null,null,param);
			expect(param.objDeletePlaylist.callback).toHaveBeenCalled();
		});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(playlistManager,'_constructor');
			playlistManager.base();
			expect(playlistManager._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(playlistManager,'constructed');
			playlistManager.base();
			expect(playlistManager.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(playlistManager,'_constructor');
			playlistManager.ctor();
			expect(playlistManager._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(playlistManager,'constructed');
			playlistManager.ctor();
			expect(playlistManager.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doSetPlaybackList)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playlistManager,'dispatchIndirectly');
			playlistManager.doSetPlaybackList();
			expect(playlistManager.dispatchIndirectly).toHaveBeenCalledWith("onSetPlaybackList",{});
		});
	});
	describe('test (doRequestMedia)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playlistManager,'dispatchIndirectly');
			playlistManager.doRequestMedia();
			expect(playlistManager.dispatchIndirectly).toHaveBeenCalledWith("onRequestMedia",{});
		});
	});
	describe('test (doRefreshPlaylists)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playlistManager,'dispatchIndirectly');
			playlistManager.doRefreshPlaylists();
			expect(playlistManager.dispatchIndirectly).toHaveBeenCalledWith("onRefreshPlaylists",{});
		});
	});
	describe('test (doPlaylistDeleted)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playlistManager,'dispatchIndirectly');
			playlistManager.doPlaylistDeleted();
			expect(playlistManager.dispatchIndirectly).toHaveBeenCalledWith("onPlaylistDeleted",{});
		});
	});
	describe('test (constructed)', function(){
		it('should call create',function(){
			spyOn(playlistManager,'create');
			playlistManager.constructed();
			expect(playlistManager.create).toHaveBeenCalledWith(undefined);
		});
		it('should call ready',function(){
			spyOn(playlistManager,'ready');
			playlistManager.constructed();
			expect(playlistManager.ready).toHaveBeenCalled();
		});
	});
	describe('test (initComponents)', function(){
		it('should call createComponents',function(){
			spyOn(playlistManager,'createComponents');
			playlistManager.initComponents();
			expect(playlistManager.createComponents).toHaveBeenCalledWith([{"kind":"DbService","dbKind":"com.palm.music.staticplaylists:1","onFailure":"onFailure_dbsStaticPlaylists","components":[{"name":"dbsGetStaticPlaylists","method":"find","onSuccess":"gotPlaylists"},{"name":"dbsPutStaticPlaylists","method":"put","onSuccess":"onSuccess_PutStaticPlaylists"},{"name":"dbsMergeStaticPlaylists","method":"merge","onSuccess":"onSuccess_MergeStaticPlaylists","onFailure":"onFailure_MergeStaticPlaylists"},{"name":"dbsDelStaticPlaylists","method":"del","onSuccess":"onSuccess_DeleStaticPlaylists"}]},{"name":"dbsFilePlaylists","kind":"DbService","dbKind":"com.palm.media.playlist.object:1","onSuccess":"gotPlaylists","onFailure":"onFailure_dbsStaticPlaylists","components":[]}]);
		});
		it('should call createContainedComponents',function(){
			spyOn(playlistManager,'createContainedComponents');
			playlistManager.initComponents();
			expect(playlistManager.createContainedComponents).toHaveBeenCalledWith(undefined);
		});
	});
	describe('test (ready)', function(){
	});
	describe('test (destroy)', function(){
		it('should change destroyed',function(){
			playlistManager.destroy();
			expect(playlistManager.destroyed).toEqual(true);
		});
		it('should call destroyComponents',function(){
			spyOn(playlistManager,'destroyComponents');
			playlistManager.destroy();
			expect(playlistManager.destroyComponents).toHaveBeenCalled();
		});
		it('should call setOwner',function(){
			spyOn(playlistManager,'setOwner');
			playlistManager.destroy();
			expect(playlistManager.setOwner).toHaveBeenCalledWith(null);
		});
	});
	describe('test (destroyComponents)', function(){
		it('should call getComponents',function(){
			spyOn(playlistManager,'getComponents');
			playlistManager.destroyComponents();
			expect(playlistManager.getComponents).toHaveBeenCalled();
		});
	});
	describe('test (importProps)', function(){
	});
	describe('test (getId)', function(){
	});
	describe('test (makeId)', function(){
	});
	describe('test (ownerChanged)', function(){
		it('should call makeId',function(){
			spyOn(playlistManager,'makeId');
			playlistManager.ownerChanged();
			expect(playlistManager.makeId).toHaveBeenCalled();
		});
	});
});