/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return new mediaWatcher();
//			}
//var objName = "mediaWatcher";
//
//var before = function (obj, callbacks) {
//		obj.startWatch=function(){};
//	}
//var event = [[null, {rowIndex:0}]];
//var params = {
//		gotFilenotifydResponse: [[{},{}],[{},{state:"queued"}],[{},{state:"idle"}]]
//};
//
//specOut(creator, objName, before, params);


describe('MediaWatcher tests', function(){
	var creator = function (){
				return new MediaWatcher();
			};
	var mediaWatcher;
	beforeEach(function(){
		mediaWatcher = creator();
		var before = function (obj, callbacks) {
	};
		var callback = function(one, fun){return fun;};
		before(mediaWatcher,callback);
		mediaWatcher.$.watchSongsAdded = {call: function(){return 1;}};
		mediaWatcher.$.watchSongsDeleted = {call: function(){return 2;}};
		mediaWatcher.$.watchPlaylistsAdded = {call: function(){return 3;}};
		mediaWatcher.$.watchPlaylistsDeleted = {call: function(){return 4;}};
		mediaWatcher.$.filenotifydMonitor = {call: function(){return 5;}};
	});
	
	
	it('should correctly set up object', function(){
		expect(mediaWatcher._componentNameMap).toEqual({});
		expect(typeof mediaWatcher.$).toBe('object');
		expect(mediaWatcher.id).toEqual("");
		expect(mediaWatcher.filenotifyd_is_busy).toEqual(false);
		expect(mediaWatcher.events).toEqual({"onSongChange":"","onPlaylistChange":"","onIndexingStart":"","onIndexingStop":""});
		expect(mediaWatcher.kindName).toEqual("MediaWatcher");
		//expect(mediaWatcher.kindComponents).toEqual([{"kind":"enyo.DbService","dbKind":"com.palm.media.audio.genre:1","method":"watch","name":"watchSongsAdded","subscribe":true,"onWatch":"gotSongs","onSuccess":"gotMedia","onFailure":"watchFailure","reCallWatches":false},{"kind":"enyo.DbService","dbKind":"com.palm.media.audio.genre:1","method":"watch","name":"watchSongsDeleted","subscribe":true,"onWatch":"deleteSongs","onSuccess":"gotMedia","onFailure":"watchFailure","reCallWatches":false},{"kind":"enyo.DbService","dbKind":"com.palm.media.playlist.file:1","method":"watch","name":"watchPlaylistsDeleted","subscribe":true,"onWatch":"gotPlaylists","onSuccess":"gotMedia","onFailure":"watchFailure","reCallWatches":false},{"kind":"enyo.DbService","dbKind":"com.palm.media.playlist.file:1","method":"watch","name":"watchPlaylistsAdded","subscribe":true,"onWatch":"gotPlaylists","onSuccess":"gotMedia","onFailure":"watchFailure","reCallWatches":false},{"kind":"enyo.PalmService","service":"palm://com.palm.filenotifyd/","method":"state","name":"filenotifydMonitor","subscribe":true,"onSuccess":"gotFilenotifydResponse","onFailure":"fileNotifydFailure"}]);
		expect(mediaWatcher.onSongChange).toEqual("");
		expect(mediaWatcher.onPlaylistChange).toEqual("");
		expect(mediaWatcher.onIndexingStart).toEqual("");
		expect(mediaWatcher.onIndexingStop).toEqual("");
		expect(mediaWatcher.published).toEqual({"owner":null,"name":""});
		expect(mediaWatcher.defaultKind).toEqual("Component");
		expect(mediaWatcher.wantsEvents).toEqual(true);
		expect(mediaWatcher.owner).toEqual(null);
		expect(mediaWatcher.name).toEqual("");
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(mediaWatcher,'inherited');
			mediaWatcher.create();
			expect(mediaWatcher.inherited).toHaveBeenCalledWith({});
		});
		it('should set filenotifyd_is_busy to false',function(){
			mediaWatcher.filenotifyd_is_busy = true;
			mediaWatcher.create();
			expect(mediaWatcher.filenotifyd_is_busy).toBe(false);
		});
	});
	describe('test (fileNotifydFailure)', function(){
		it("should have a fineNotifydFailure func", function(){
			mediaWatcher.fileNotifydFailure();
		});
	});
	describe('test (gotFilenotifydResponse)', function(){
		describe('testing (gotFilenotifydResponse) with params [{},{}]', function(){
			describe("when this.filenotifyd_is_busy is false", function(){
				beforeEach(function(){
					mediaWatcher.filenotifyd_is_busy = false;
					mediaWatcher.startWatch = function(){};
				});
				it('should not change filenotifyd_is_busy',function(){
					mediaWatcher.gotFilenotifydResponse({},{});
					expect(mediaWatcher.filenotifyd_is_busy).toEqual(false);
				});
				it('should not call doIndexingStart',function(){
					spyOn(mediaWatcher,'doIndexingStart');
					mediaWatcher.gotFilenotifydResponse({},{});
					expect(mediaWatcher.doIndexingStart).not.toHaveBeenCalled();
				});
			});
			describe("when this.filenotifyd_is_busy is true", function(){
				beforeEach(function(){
					mediaWatcher.filenotifyd_is_busy = true;
				});
				it('should not change filenotifyd_is_busy',function(){
					mediaWatcher.gotFilenotifydResponse({},{});
					expect(mediaWatcher.filenotifyd_is_busy).toEqual(true);
				});
				it('should  not call doIndexingStart',function(){
					spyOn(mediaWatcher,'doIndexingStart');
					mediaWatcher.gotFilenotifydResponse({},{});
					expect(mediaWatcher.doIndexingStart).not.toHaveBeenCalled();
				});
			});
		
		});
		describe('testing (gotFilenotifydResponse) with params [{},{"state":"queued"}]', function(){
			describe("when this.filenotifyd_is_busy is false", function(){
				beforeEach(function(){
					mediaWatcher.filenotifyd_is_busy = false;
				});
				it('should change filenotifyd_is_busy',function(){
					mediaWatcher.gotFilenotifydResponse({},{"state":"queued"});
					expect(mediaWatcher.filenotifyd_is_busy).toEqual(true);
				});
				it('should call doIndexingStart',function(){
					spyOn(mediaWatcher,'doIndexingStart');
					mediaWatcher.gotFilenotifydResponse({},{"state":"queued"});
					expect(mediaWatcher.doIndexingStart).toHaveBeenCalled();
				});
			});
			describe("when this.filenotifyd_is_busy is true", function(){
				beforeEach(function(){
					mediaWatcher.filenotifyd_is_busy = true;
				});
				it('should change filenotifyd_is_busy',function(){
					mediaWatcher.gotFilenotifydResponse({},{"state":"queued"});
					expect(mediaWatcher.filenotifyd_is_busy).toEqual(true);
				});
				it('should  not call doIndexingStart',function(){
					spyOn(mediaWatcher,'doIndexingStart');
					mediaWatcher.gotFilenotifydResponse({},{"state":"queued"});
					expect(mediaWatcher.doIndexingStart).not.toHaveBeenCalled();
				});
			});
			
			
		});
		describe('testing (gotFilenotifydResponse) with params [{},{"state":"idle"}]', function(){
			describe("when this.filenotifyd_is_busy is false", function(){
				beforeEach(function(){
					mediaWatcher.filenotifyd_is_busy = false;
				});
				it('should change filenotifyd_is_busy',function(){
					mediaWatcher.gotFilenotifydResponse({},{"state":"idle"});
					expect(mediaWatcher.filenotifyd_is_busy).toEqual(false);
				});
				it('should  not call doIndexingStop',function(){
					spyOn(mediaWatcher,'doIndexingStop');
					mediaWatcher.gotFilenotifydResponse({},{"state":"idle"});
					expect(mediaWatcher.doIndexingStop).not.toHaveBeenCalled();
				});
			});
			describe("when this.filenotifyd_is_busy is true", function(){
				beforeEach(function(){
					mediaWatcher.filenotifyd_is_busy = true;
				});
				it('should change filenotifyd_is_busy',function(){
					mediaWatcher.gotFilenotifydResponse({},{"state":"idle"});
					expect(mediaWatcher.filenotifyd_is_busy).toEqual(false);
				});
				it('should call doIndexingStop',function(){
					spyOn(mediaWatcher,'doIndexingStop');
					mediaWatcher.gotFilenotifydResponse({},{"state":"idle"});
					expect(mediaWatcher.doIndexingStop).toHaveBeenCalled();
				});
			});
		});
	});
	describe('test (watchFailure)', function(){
		it("should have the method", function(){
			mediaWatcher.watchFailure();
		});
	});
	describe('test (gotMedia)', function(){
		it("should have the method", function(){
			mediaWatcher.gotMedia();
		});
	});
	describe('test (gotSongs)', function(){
		it('should call doSongChange',function(){
			spyOn(mediaWatcher,'doSongChange');
			mediaWatcher.gotSongs();
			expect(mediaWatcher.doSongChange).toHaveBeenCalledWith(undefined);
		});
		it('should pass on params',function(){
			spyOn(mediaWatcher,'doSongChange');
			var myParam = {"hi": "you"};
			mediaWatcher.gotSongs(undefined, myParam, undefined);
			expect(mediaWatcher.doSongChange).toHaveBeenCalledWith(myParam);
		});
	});
	describe('test (deleteSongs)', function(){
		it('should call doSongChange',function(){
			spyOn(mediaWatcher,'doSongChange');
			mediaWatcher.deleteSongs();
			expect(mediaWatcher.doSongChange).toHaveBeenCalledWith(undefined);
		});
		it('should pass on params',function(){
			spyOn(mediaWatcher,'doSongChange');
			var myParam = {"hi": "you"};
			mediaWatcher.gotSongs(undefined, myParam, undefined);
			expect(mediaWatcher.doSongChange).toHaveBeenCalledWith(myParam);
		});
	});
	describe('test (gotPlaylists)', function(){
		it('should call doPlaylistChange',function(){
			spyOn(mediaWatcher,'doPlaylistChange');
			mediaWatcher.gotPlaylists();
			expect(mediaWatcher.doPlaylistChange).toHaveBeenCalledWith(undefined);
		});
		it('should pass on params',function(){
			spyOn(mediaWatcher,'doPlaylistChange');
			var myParam = {"hi": "you"};
			mediaWatcher.gotPlaylists(undefined, myParam, undefined);
			expect(mediaWatcher.doPlaylistChange).toHaveBeenCalledWith(myParam);
		});
	});
	describe('test (deletePlaylists)', function(){
		it('should call doPlaylistChange',function(){
			spyOn(mediaWatcher,'doPlaylistChange');
			mediaWatcher.deletePlaylists();
			expect(mediaWatcher.doPlaylistChange).toHaveBeenCalledWith(undefined);
		});
		it('should pass on params',function(){
			spyOn(mediaWatcher,'doPlaylistChange');
			var myParam = {"hi": "you"};
			mediaWatcher.deletePlaylists(undefined, myParam, undefined);
			expect(mediaWatcher.doPlaylistChange).toHaveBeenCalledWith(myParam);
		});
	});
	describe('test (cancelAllCalls)', function(){
		it("should set fileNotifyd_is_busy to false", function(){
			mediaWatcher.filenotifyd_is_busy = true;
			mediaWatcher.cancelAllCalls();
			expect(mediaWatcher.filenotifyd_is_busy).toBe(false);
		});
		it("should cancel songAddedWatcher", function(){
			mediaWatcher.$.watchSongsAdded = {cancelCall: function(){}};
			spyOn(mediaWatcher.$.watchSongsAdded, "cancelCall");
			mediaWatcher.songAddedWatcher = true;
			mediaWatcher.cancelAllCalls();
			expect(mediaWatcher.$.watchSongsAdded.cancelCall).toHaveBeenCalledWith(mediaWatcher.songAddedWatcher);
		});
		it("should cancel songDeletedWatcher", function(){
			mediaWatcher.$.watchSongsDeleted = {cancelCall: function(){}};
			spyOn(mediaWatcher.$.watchSongsDeleted, "cancelCall");
			mediaWatcher.songDeletedWatcher = true;
			mediaWatcher.cancelAllCalls();
			expect(mediaWatcher.$.watchSongsDeleted.cancelCall).toHaveBeenCalledWith(mediaWatcher.songDeletedWatcher);
		});
		it("should cancel playlistAddedWatcher", function(){
			mediaWatcher.$.watchPlaylistsAdded = {cancelCall: function(){}};
			spyOn(mediaWatcher.$.watchPlaylistsAdded, "cancelCall");
			mediaWatcher.playlistAddedWatcher = true;
			mediaWatcher.cancelAllCalls();
			expect(mediaWatcher.$.watchPlaylistsAdded.cancelCall).toHaveBeenCalledWith(mediaWatcher.playlistAddedWatcher);
		});
		it("should cancel playlistDeletedWatcher", function(){
			mediaWatcher.$.watchPlaylistsDeleted = {cancelCall: function(){}};
			spyOn(mediaWatcher.$.watchPlaylistsDeleted, "cancelCall");
			mediaWatcher.playlistDeletedWatcher = true;
			mediaWatcher.cancelAllCalls();
			expect(mediaWatcher.$.watchPlaylistsDeleted.cancelCall).toHaveBeenCalledWith(mediaWatcher.playlistDeletedWatcher);
		});
		it("should cancel filenotifydWatcher", function(){
			mediaWatcher.$.filenotifydMonitor = {cancelCall: function(){}};
			spyOn(mediaWatcher.$.filenotifydMonitor, "cancelCall");
			mediaWatcher.filenotifydWatcher = true;
			mediaWatcher.cancelAllCalls();
			expect(mediaWatcher.$.filenotifydMonitor.cancelCall).toHaveBeenCalledWith(mediaWatcher.filenotifydWatcher);
		});
		
	});
	describe('test (startWatch)', function(){
		beforeEach(function(){
			mediaWatcher.cancelAllCalls = function(){};
		});
		it("shoudl cancelAllCalls", function(){
			spyOn(mediaWatcher,"cancelAllCalls");
			mediaWatcher.startWatch();
			expect(mediaWatcher.cancelAllCalls).toHaveBeenCalled();
		});
		it("should cancel songAddedWatcher", function(){
			
			spyOn(mediaWatcher.$.watchSongsAdded, "call").andCallThrough();
			
			mediaWatcher.startWatch();
			expect(mediaWatcher.$.watchSongsAdded.call).toHaveBeenCalledWith({ query: { from: "com.palm.media.audio.genre:1", where: [{"prop": "_del","op": "=", "val": false}]} });
			expect(mediaWatcher.songAddedWatcher).toBe(1);
		});
		it("should cancel songDeletedWatcher", function(){
			
			spyOn(mediaWatcher.$.watchSongsDeleted, "call").andCallThrough();
			mediaWatcher.songDeletedWatcher = true;
			mediaWatcher.startWatch();
			expect(mediaWatcher.$.watchSongsDeleted.call).toHaveBeenCalledWith({ query: { from: "com.palm.media.audio.genre:1", where: [{"prop": "_del","op": "=", "val": true}]} });
			expect(mediaWatcher.songDeletedWatcher).toBe(2);
		});
		it("should cancel playlistAddedWatcher", function(){
			
			spyOn(mediaWatcher.$.watchPlaylistsAdded, "call").andCallThrough();
			mediaWatcher.playlistAddedWatcher = true;
			mediaWatcher.startWatch();
			expect(mediaWatcher.$.watchPlaylistsAdded.call).toHaveBeenCalledWith({ query: {from: "com.palm.media.playlist.file:1",  where: [{"prop": "_del","op": "=", "val": false}]} });
			expect(mediaWatcher.playlistAddedWatcher).toBe(3);
		});
		it("should cancel playlistDeletedWatcher", function(){
			
			spyOn(mediaWatcher.$.watchPlaylistsDeleted, "call").andCallThrough();
			mediaWatcher.playlistDeletedWatcher = true;
			mediaWatcher.startWatch();
			expect(mediaWatcher.$.watchPlaylistsDeleted.call).toHaveBeenCalledWith({ query: {from: "com.palm.media.playlist.file:1", where: [{"prop": "_del","op": "=", "val": true}]} });
			expect(mediaWatcher.playlistDeletedWatcher).toBe(4);
		});
		it("should cancel filenotifydWatcher", function(){
			
			spyOn(mediaWatcher.$.filenotifydMonitor, "call").andCallThrough();
			mediaWatcher.filenotifydWatcher = true;
			mediaWatcher.startWatch();
			expect(mediaWatcher.$.filenotifydMonitor.call).toHaveBeenCalledWith({});
			expect(mediaWatcher.filenotifydWatcher).toBe(5);
		});
	});
	describe('test (stopWatch)', function(){
		beforeEach(function(){
			spyOn(mediaWatcher,"cancelAllCalls");
		});
		it('should call cancelAllCalls',function(){
			mediaWatcher.stopWatch();
			expect(mediaWatcher.cancelAllCalls).toHaveBeenCalled();
		});
		it("should set filenotifydWatcher to undefined", function(){
			mediaWatcher.filenotifydWatcher = true;
			mediaWatcher.stopWatch();
			expect(mediaWatcher.filenotifydWatcher).toBe(undefined);
		});
		it("should set playlistAddedWatcher to undefined", function(){
			mediaWatcher.playlistAddedWatcher = true;
			mediaWatcher.stopWatch();
			expect(mediaWatcher.playlistAddedWatcher).toBe(undefined);
		});
		it("should set playlistDeletedWatcher to undefined", function(){
			mediaWatcher.playlistDeletedWatcher = true;
			mediaWatcher.stopWatch();
			expect(mediaWatcher.playlistDeletedWatcher).toBe(undefined);
		});
		it("should set songAddedWatcher to undefined", function(){
			mediaWatcher.songAddedWatcher = true;
			mediaWatcher.stopWatch();
			expect(mediaWatcher.songAddedWatcher).toBe(undefined);
		});
		it("should set songDeletedWatcher to undefined", function(){
			mediaWatcher.songDeletedWatcher = true;
			mediaWatcher.stopWatch();
			expect(mediaWatcher.songDeletedWatcher).toBe(undefined);
		});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(mediaWatcher,'_constructor');
			mediaWatcher.base();
			expect(mediaWatcher._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(mediaWatcher,'constructed');
			mediaWatcher.base();
			expect(mediaWatcher.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(mediaWatcher,'_constructor');
			mediaWatcher.ctor();
			expect(mediaWatcher._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(mediaWatcher,'constructed');
			mediaWatcher.ctor();
			expect(mediaWatcher.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doSongChange)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(mediaWatcher,'dispatchIndirectly');
			mediaWatcher.doSongChange();
			expect(mediaWatcher.dispatchIndirectly).toHaveBeenCalledWith("onSongChange",{});
		});
	});
	describe('test (doPlaylistChange)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(mediaWatcher,'dispatchIndirectly');
			mediaWatcher.doPlaylistChange();
			expect(mediaWatcher.dispatchIndirectly).toHaveBeenCalledWith("onPlaylistChange",{});
		});
	});
	describe('test (doIndexingStart)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(mediaWatcher,'dispatchIndirectly');
			mediaWatcher.doIndexingStart();
			expect(mediaWatcher.dispatchIndirectly).toHaveBeenCalledWith("onIndexingStart",{});
		});
	});
	describe('test (doIndexingStop)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(mediaWatcher,'dispatchIndirectly');
			mediaWatcher.doIndexingStop();
			expect(mediaWatcher.dispatchIndirectly).toHaveBeenCalledWith("onIndexingStop",{});
		});
	});
	describe('test (toString)', function(){
		
	});
	describe('test (constructed)', function(){
		it('should call create',function(){
			spyOn(mediaWatcher,'create');
			mediaWatcher.constructed();
			expect(mediaWatcher.create).toHaveBeenCalledWith(undefined);
		});
		it('should call ready',function(){
			spyOn(mediaWatcher,'ready');
			mediaWatcher.constructed();
			expect(mediaWatcher.ready).toHaveBeenCalled();
		});
	});
	describe('test (initComponents)', function(){
		
		it('should call createContainedComponents',function(){
			spyOn(mediaWatcher,'createContainedComponents');
			mediaWatcher.initComponents();
			expect(mediaWatcher.createContainedComponents).toHaveBeenCalledWith(undefined);
		});
	});
	describe('test (ready)', function(){
	});
	describe('test (destroy)', function(){
		
		it('should call destroyComponents',function(){
			spyOn(mediaWatcher,'destroyComponents');
			mediaWatcher.destroy();
			expect(mediaWatcher.destroyComponents).toHaveBeenCalled();
		});
		
	});
	describe('test (destroyComponents)', function(){
		it('should call getComponents',function(){
			spyOn(mediaWatcher,'getComponents');
			mediaWatcher.destroyComponents();
			expect(mediaWatcher.getComponents).toHaveBeenCalled();
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
			spyOn(mediaWatcher,'makeId');
			mediaWatcher.ownerChanged();
			expect(mediaWatcher.makeId).toHaveBeenCalled();
		});
	});
});
