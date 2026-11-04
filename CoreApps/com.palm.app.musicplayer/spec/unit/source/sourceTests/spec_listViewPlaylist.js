/*globals specOut, enyo, $L, event, Utilities, window, LibraryTools */

/*var creator = function(){
				return enyo.create({kind: "kindListViewPlaylist"});
			};
var objName = "kindListViewPlaylist";

var before = function (obj, callbacks) {
	
	obj.itemMousehold = function(){};
	
    
    obj.$.ctrlListViewHeader = {};
    obj.$.ctrlListViewHeader.setIntHeaderCount = function(){};
    obj.$.ctrlListViewHeader.setStrHeaderTitle = function(){};
    obj.$.ctrlListViewHeader.show = function(){};
    obj.$.listMedia = {};
    obj.$.HeaderEdit = {};
    obj.$.listMedia.refresh = function(){};
    obj.$.listMedia.resized = function(){};
    obj.$.listMedia.addRemoveClass = function(){};
    obj.$.listMedia.fetchRowIndex = function(){return 0;};
    obj.$.HeaderEdit.show = function(){};
    obj.$.HeaderEdit.hide = function(){};
    obj.arPlaylists = [{"_id":"++Hdmw3B0Gp1S6jL","_kind":"com.palm.music.staticplaylists:1","_rev":44641,"_sync":true,"name":"Tt","tracks":[{"_id":"ae60","artist":"Andrew Bird","path":"/media/internal/Music/Andrew Bird - The Mysterious Production Of Eggs [2005]/10 - The Naming Of Things.mp3","title":"The Naming Of Things"},{"_id":"ae62","artist":"Antsy Pants","path":"/media/internal/Music/Juno Soundtrack/15-Tree-Hugger.mp3","title":"Tree Hugger"}]}];
    
};
var event = [[null, {rowIndex:0, dispatchTarget: null}]];
var params = {
		 "dragObjType" : event,
		 "songMousehold" : event,
		 "albumMousehold" : event,
		 "albumGroupMousehold" : event,
		 "artistMousehold" : event,
		 "genreMousehold" : event,
		 "onclick_listmedia" : event,
		 "getDragIndex"	: event,
		 "mousehold_itemmedia" : event
		 
};

specOut(creator, objName, before, params);*/


describe('kindListViewPlaylist tests', function(){
	var creator = function (){
				return enyo.create({kind: "kindListViewPlaylist"});
			};
	var kindListViewPlaylist = creator();
	beforeEach(function(){
		var before = function (obj, callbacks) {
	
	obj.itemMousehold = function(){};
	
    
    obj.$.ctrlListViewHeader = {};
    obj.$.ctrlListViewHeader.setIntHeaderCount = function(){};
    obj.$.ctrlListViewHeader.setStrHeaderTitle = function(){};
    obj.$.ctrlListViewHeader.show = function(){};
    obj.$.listMedia = {};
    obj.$.HeaderEdit = {};
    obj.$.listMedia.refresh = function(){};
    obj.$.listMedia.resized = function(){};
    obj.$.listMedia.addRemoveClass = function(){};
    obj.$.listMedia.fetchRowIndex = function(){return 0;};
    obj.$.HeaderEdit.show = function(){};
    obj.$.HeaderEdit.hide = function(){};
    obj.arSongs = [{"thumbnails":""},{"thumbnails":""},{"thumbnails":""}];
    obj.arPlaylists = [{"_id":"++Hdmw3B0Gp1S6jL","_kind":"com.palm.music.staticplaylists:1","_rev":44641,"_sync":true,"name":"Tt","tracks":[{"_id":"ae60","artist":"Andrew Bird","path":"/media/internal/Music/Andrew Bird - The Mysterious Production Of Eggs [2005]/10 - The Naming Of Things.mp3","title":"The Naming Of Things"},{"_id":"ae62","artist":"Antsy Pants","path":"/media/internal/Music/Juno Soundtrack/15-Tree-Hugger.mp3","title":"Tree Hugger"}]}];
    
};
		var callback = function(one, fun){return fun;};
		before(kindListViewPlaylist,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof kindListViewPlaylist.controls).toBe('object');
		expect(typeof kindListViewPlaylist.children).toBe('object');
		expect(typeof kindListViewPlaylist.$).toBe('object');
		expect(kindListViewPlaylist.domStyles).toEqual({"height":"100%"});
		expect(typeof kindListViewPlaylist.owner).toBe('object');
		expect(typeof kindListViewPlaylist.controlParent).toBe('undefined');
		expect(typeof kindListViewPlaylist.layout).toBe('object');
		expect(kindListViewPlaylist.strSortMode).toEqual("position");
		expect(kindListViewPlaylist.boolSortAsc).toEqual(true);
		expect(kindListViewPlaylist.pack).toEqual("start");
		expect(kindListViewPlaylist.className).toEqual("playlist");
		expect(kindListViewPlaylist.height).toEqual("100%");
		expect(kindListViewPlaylist.intCurrTrack).toEqual(-1);
		expect(kindListViewPlaylist.intCurrTrackOrigIndex).toEqual(-1);
		expect(kindListViewPlaylist.strCurrTrackID).toEqual("");
		expect(kindListViewPlaylist.strPlaylistName).toEqual("");
		expect(kindListViewPlaylist.strPlaylistID).toEqual("");
		expect(kindListViewPlaylist.strPlaylistKind).toEqual("");
		expect(kindListViewPlaylist.boolEditMode).toEqual(false);
		expect(kindListViewPlaylist.arUpdateTracks).toEqual([]);
		//expect(kindListViewPlaylist.arSongs).toEqual([]);
		expect(kindListViewPlaylist.boolNotifyListChange).toEqual(false);
		expect(kindListViewPlaylist.onSetPlaybackList).toEqual("");
		expect(kindListViewPlaylist.onRequestMedia).toEqual("");
		expect(kindListViewPlaylist.onRequestPlaylist).toEqual("");
		expect(kindListViewPlaylist.onRequestCurrTrack).toEqual("");
		expect(kindListViewPlaylist.onUpdatePlaylist).toEqual("");
		expect(kindListViewPlaylist.onDeletePlaylist).toEqual("");
		expect(kindListViewPlaylist.onEditPlaylist).toEqual("");
		expect(kindListViewPlaylist.onAddToPlaylist).toEqual("");
		expect(kindListViewPlaylist.onItemDrag).toEqual("");
		expect(kindListViewPlaylist.onItemShowAvatar).toEqual("");
		expect(kindListViewPlaylist.onItemShowAvatarIcon).toEqual("");
		expect(kindListViewPlaylist.boolViewActive).toEqual(false);
		expect(kindListViewPlaylist.boolListRendered).toEqual(false);
		expect(kindListViewPlaylist.intJumpRowOffset).toEqual(0);
		expect(kindListViewPlaylist.boolDragHold).toEqual(false);
		expect(kindListViewPlaylist.boolDragItem).toEqual(false);
		expect(kindListViewPlaylist.boolDraggingList).toEqual(false);
		expect(kindListViewPlaylist.dragObjType).toEqual(null);
		expect(kindListViewPlaylist.arItems).toEqual([]);
		expect(kindListViewPlaylist.albumGroup).toEqual(false);
		expect(kindListViewPlaylist.controlParentName).toEqual("client");
		expect(kindListViewPlaylist.defaultKind).toEqual("Control");
		expect(kindListViewPlaylist.onclick).toEqual("");
		expect(kindListViewPlaylist.onmousedown).toEqual("");
		expect(kindListViewPlaylist.onmouseup).toEqual("");
		expect(kindListViewPlaylist.style).toEqual("");
		expect(kindListViewPlaylist.container).toEqual(null);
		expect(kindListViewPlaylist.parent).toEqual(null);
		expect(kindListViewPlaylist.generated).toEqual(false);
		expect(kindListViewPlaylist.allowHtml).toEqual(false);
		expect(kindListViewPlaylist.content).toEqual("");
		expect(kindListViewPlaylist.nodeTag).toEqual("div");
		expect(kindListViewPlaylist.node).toEqual(null);
		expect(kindListViewPlaylist.showing).toEqual(true);
		expect(kindListViewPlaylist.prepend).toEqual(false);
		expect(kindListViewPlaylist.wantsEvents).toEqual(true);
	});
	describe('test (itemMousehold)', function(){
	});
	describe('test (create)', function(){
		it('should set boolSortAsc',function(){
			kindListViewPlaylist.boolSortAsc = false;
			kindListViewPlaylist.create();
			expect(kindListViewPlaylist.boolSortAsc).toEqual(true);
		});
		it('should set strSortMode',function(){
			kindListViewPlaylist.strSortMode = false;
			kindListViewPlaylist.create();
			expect(kindListViewPlaylist.strSortMode).toEqual("position");
		});
		it('should change strCurrListID',function(){
			kindListViewPlaylist.create();
			expect(kindListViewPlaylist.strCurrListID).not.toEqual("");
		});
		it('should call inherited',function(){
			spyOn(kindListViewPlaylist,'inherited');
			kindListViewPlaylist.create();
			expect(kindListViewPlaylist.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (ready)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewPlaylist,'inherited');
			kindListViewPlaylist.ready();
			expect(kindListViewPlaylist.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (rendered)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewPlaylist,'inherited');
			kindListViewPlaylist.rendered();
			expect(kindListViewPlaylist.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (renderList)', function(){
		var objParams
		beforeEach(function(){
			objParams = {strPlaylistName: "strPlaylistName", strPlaylistID: "id", strPlaylistKind: "kind"};
			kindListViewPlaylist.$.ctrlListViewHeader.clearSearchInput = function(){};
			
		});
		it("should not headeredit (clearPlaylist does this)", function(){
			spyOn(kindListViewPlaylist.$.HeaderEdit,'hide');
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.$.HeaderEdit.hide).not.toHaveBeenCalled();
		});
		it("should show ctrlListViewHeader (done when we get data", function(){
			spyOn(kindListViewPlaylist.$.ctrlListViewHeader,'show');
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.$.ctrlListViewHeader.show).not.toHaveBeenCalled();
		});
		it("should show content (done when we get data", function(){
			spyOn(kindListViewPlaylist.$.content,'show');
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.$.content.show).not.toHaveBeenCalled();
		});
		it("should hide footerEdit (clearPlaylist does this)", function(){
			spyOn(kindListViewPlaylist.$.footerEdit,'hide');
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.$.footerEdit.hide).not.toHaveBeenCalled();
		});
		it('should set boolEditMode',function(){
			kindListViewPlaylist.boolEditMode = true;
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.boolEditMode).toEqual(false);
		});
		it("should hide txtResults (clearPlaylist does this)", function(){
			spyOn(kindListViewPlaylist.$.txtResults,'hide');
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.$.txtResults.hide).not.toHaveBeenCalled();
		});
		/* This test needs to be moved to gotPlaylists
		it("should setStrHeaderTitle", function(){
			spyOn(kindListViewPlaylist.$.ctrlListViewHeader,'setStrHeaderTitle');
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.$.ctrlListViewHeader.setStrHeaderTitle).toHaveBeenCalledWith("strPlaylistName");
		});
		*/
		it("should txtResults setContent", function(){
			spyOn(kindListViewPlaylist.$.txtResults,'setContent');
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.$.txtResults.setContent).toHaveBeenCalledWith($L("Playlist Deleted"));
		});
		it('should set boolSortAsc',function(){
			kindListViewPlaylist.strCurrListID = null;
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.strCurrListID).not.toBe(null);
		});
		it('shouldnt set boolSortAsc',function(){
			kindListViewPlaylist.strPlaylistName = "id";
			kindListViewPlaylist.strCurrListID = null;
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.strCurrListID).toBe(null);
		});
		/* This test needs to be moved to gotPlaylists
		it('should set strPlaylistName',function(){
			kindListViewPlaylist.strPlaylistName = null;
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.strPlaylistName).toBe("strPlaylistName");
		});
		*/
		it('should set strPlaylistID',function(){
			kindListViewPlaylist.strPlaylistID = null;
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.strPlaylistID).toBe("id");
		});
		it('should set strPlaylistKind',function(){
			kindListViewPlaylist.strPlaylistKind = null;
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.strPlaylistKind).toBe("kind");
		});
		it('should set arSongs',function(){
			kindListViewPlaylist.arSongs = null;
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.arSongs).toEqual([]);
		});
		it('should set searchQuery',function(){
			kindListViewPlaylist.searchQuery = null;
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.searchQuery).toBe("");
		});
		
		it("should call clearSearchInput", function(){
			spyOn(kindListViewPlaylist.$.ctrlListViewHeader,'clearSearchInput');
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.$.ctrlListViewHeader.clearSearchInput).toHaveBeenCalledWith(false);
		});
		it("should doRequestCurrTrack", function(){
			spyOn(kindListViewPlaylist,'doRequestCurrTrack');
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.doRequestCurrTrack).toHaveBeenCalled();
		});
		it("should getPlaylist", function(){
			spyOn(kindListViewPlaylist,'getPlaylist');
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.getPlaylist).toHaveBeenCalled();
		});
		it("should resetMusicListHeaders (clearPlaylist does this)", function(){
			spyOn(kindListViewPlaylist,'resetMusicListHeaders');
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.resetMusicListHeaders).not.toHaveBeenCalled();
		});

		it("should addClass", function(){
			spyOn(kindListViewPlaylist.$.lblSortPos,'addClass');
			kindListViewPlaylist.renderList(objParams);
			expect(kindListViewPlaylist.$.lblSortPos.addClass).toHaveBeenCalledWith("sorted");
		});
		/*
		it("should playlistDeleted", function(){
			spyOn(kindListViewPlaylist,'playlistDeleted');
			kindListViewPlaylist.strPlaylistName = undefined;
			kindListViewPlaylist.renderList(undefined);
			expect(kindListViewPlaylist.playlistDeleted).toHaveBeenCalled();
		});
		*/
		
		
	});
	describe('test (refreshList)', function(){
		it('should call refresh',function(){
			spyOn(kindListViewPlaylist.$.listMedia,'refresh');
			kindListViewPlaylist.refreshList();
			expect(kindListViewPlaylist.$.listMedia.refresh).toHaveBeenCalled();
		});
		it('should call doRequestCurrTrack',function(){
			spyOn(kindListViewPlaylist,'doRequestCurrTrack');
			kindListViewPlaylist.refreshList();
			expect(kindListViewPlaylist.doRequestCurrTrack).toHaveBeenCalled();
		});
	});
	describe('test (resizeList)', function(){
		it('should call resized',function(){
			spyOn(kindListViewPlaylist.$.listMedia,'resized');
			kindListViewPlaylist.resizeList();
			expect(kindListViewPlaylist.$.listMedia.resized).toHaveBeenCalled();
		});
	});
	describe('test (getPlaylist)', function(){
		it('should call doRequestPlaylist',function(){
			spyOn(kindListViewPlaylist,'doRequestPlaylist');
			kindListViewPlaylist.getPlaylist();
			expect(kindListViewPlaylist.doRequestPlaylist).toHaveBeenCalled();
		});
	});
	describe('test (gotPlaylist)', function(){
	});
	describe('test (parsePlaylistTracks)', function(){
	});
	describe('test (getSongs)', function(){
		it('should call doRequestMedia',function(){
			spyOn(kindListViewPlaylist,'doRequestMedia');
			kindListViewPlaylist.getSongs();
			expect(kindListViewPlaylist.doRequestMedia).toHaveBeenCalled();
		});
	});
	describe('test (gotSongs)', function(){
	});
	describe('test (listSetupRow)', function(){
	});
	describe('test (onclick_Sort)', function(){
	});
	
	describe("test (handlePlayingSong)", function(){
		describe("when listID and plaingID are the same", function(){
			
		});
		describe("when listID and plaingID are different", function(){
			
		});
		describe("when not playing song", function(){
		});
	});
	
	describe('test (resetMusicListHeaders)', function(){
	});
	
	describe('test (clearPlaylist)', function(){
		beforeEach(function(){
			kindListViewPlaylist.$.ctrlListViewHeader.setBoolShowEditPlaylist = function(){};
			kindListViewPlaylist.$.ctrlListViewHeader.setBoolShowSearchBtn = function(){};
		});
		it('should set strPlaylistName',function(){
			kindListViewPlaylist.strPlaylistName = "asdf";
			kindListViewPlaylist.clearPlaylist();
			expect(kindListViewPlaylist.strPlaylistName).toEqual("");
		});
		it('should set strPlaylistID',function(){
			kindListViewPlaylist.strPlaylistID = "asdf";
			kindListViewPlaylist.clearPlaylist();
			expect(kindListViewPlaylist.strPlaylistID).toEqual("");
		});
		it('should hide empty',function(){
			spyOn(kindListViewPlaylist.$.empty,'hide');
			kindListViewPlaylist.clearPlaylist();
			expect(kindListViewPlaylist.$.empty.hide).toHaveBeenCalled();
		});

		it('should hide HeaderEdit',function(){
			spyOn(kindListViewPlaylist.$.HeaderEdit,'hide');
			kindListViewPlaylist.clearPlaylist();
			expect(kindListViewPlaylist.$.HeaderEdit.hide).toHaveBeenCalled();
		});

		it('should hide ctrlListViewHeader',function(){
			spyOn(kindListViewPlaylist.$.ctrlListViewHeader,'show');
			kindListViewPlaylist.clearPlaylist();
			expect(kindListViewPlaylist.$.ctrlListViewHeader.show).toHaveBeenCalled();
		});
		it('should hide footerEdit',function(){
			spyOn(kindListViewPlaylist.$.footerEdit,'hide');
			kindListViewPlaylist.clearPlaylist();
			expect(kindListViewPlaylist.$.footerEdit.hide).toHaveBeenCalled();
		});
		it('should hide txtResults',function(){
			spyOn(kindListViewPlaylist.$.txtResults,'hide');
			kindListViewPlaylist.clearPlaylist();
			expect(kindListViewPlaylist.$.txtResults.hide).toHaveBeenCalled();
		});
		it('should call resetMusicListHeaders',function(){
			spyOn(kindListViewPlaylist,'resetMusicListHeaders');
			kindListViewPlaylist.clearPlaylist();
			expect(kindListViewPlaylist.resetMusicListHeaders).toHaveBeenCalled();
		});
		it('should call setStrHeaderTitle',function(){
			spyOn(kindListViewPlaylist.$.ctrlListViewHeader,'setStrHeaderTitle');
			kindListViewPlaylist.clearPlaylist();
			expect(kindListViewPlaylist.$.ctrlListViewHeader.setStrHeaderTitle).toHaveBeenCalledWith("");
		});
		it('should call setIntHeaderCount',function(){
			spyOn(kindListViewPlaylist.$.ctrlListViewHeader,'setIntHeaderCount');
			kindListViewPlaylist.clearPlaylist();
			expect(kindListViewPlaylist.$.ctrlListViewHeader.setIntHeaderCount).toHaveBeenCalledWith(-1);
		});
		it('should call setBoolShowEditPlaylist',function(){
			spyOn(kindListViewPlaylist.$.ctrlListViewHeader,'setBoolShowEditPlaylist');
			kindListViewPlaylist.clearPlaylist();
			expect(kindListViewPlaylist.$.ctrlListViewHeader.setBoolShowEditPlaylist).toHaveBeenCalledWith(false);
		});
		it('should call setIntHeaderCount',function(){
			spyOn(kindListViewPlaylist.$.ctrlListViewHeader,'setBoolShowSearchBtn');
			kindListViewPlaylist.clearPlaylist();
			expect(kindListViewPlaylist.$.ctrlListViewHeader.setBoolShowSearchBtn).toHaveBeenCalledWith(false);
		});
		
	});
	
	
	describe('test (onclick_listMedia)', function(){
		it('should change objPlaylist',function(){
			var temp = kindListViewPlaylist.objPlaylist;
			kindListViewPlaylist.onclick_listMedia(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewPlaylist.objPlaylist).not.toEqual(temp);
		});
		it('should call doSetPlaybackList',function(){
			spyOn(kindListViewPlaylist,'doSetPlaybackList');
			kindListViewPlaylist.onclick_listMedia(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewPlaylist.doSetPlaybackList).toHaveBeenCalled();
		});
	});
	describe('test (onStartEdit)', function(){
		it('should call onEditMode',function(){
			spyOn(kindListViewPlaylist,'onEditMode');
			kindListViewPlaylist.onStartEdit(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewPlaylist.onEditMode).toHaveBeenCalledWith(true);
		});
	});
	describe('test (onCancelEdit)', function(){
		it('should call onEditMode',function(){
			spyOn(kindListViewPlaylist,'onEditMode');
			kindListViewPlaylist.onCancelEdit(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewPlaylist.onEditMode).toHaveBeenCalledWith(false);
		});
	});
	describe('test (onDoneEdit)', function(){
		beforeEach(function(){
			kindListViewPlaylist.$.popupConfirmDone = {openAtCenter : function(){}}
		});	
		describe("when processDeletedTracks returns true:", function(){
			beforeEach(function(){
				spyOn(kindListViewPlaylist, "processDeletedTracks").andReturn(true);
			});	
			it('should call processDeletedTracks',function(){
			kindListViewPlaylist.onDoneEdit(null,{"rowIndex":0,"dispatchTarget":null});
				expect(kindListViewPlaylist.processDeletedTracks).toHaveBeenCalled();
			});

			it('should call this.$.popupConfirmDone openAtCenter',function(){
				spyOn(kindListViewPlaylist.$.popupConfirmDone,'openAtCenter');
				kindListViewPlaylist.onDoneEdit(null,{"rowIndex":0,"dispatchTarget":null});
				expect(kindListViewPlaylist.$.popupConfirmDone.openAtCenter).toHaveBeenCalled();
			});	
			it('should not call onConfirmDone',function(){
				spyOn(kindListViewPlaylist,'onConfirmDone');
				kindListViewPlaylist.onDoneEdit(null,{"rowIndex":0,"dispatchTarget":null});
				expect(kindListViewPlaylist.onConfirmDone).not.toHaveBeenCalled();
			});		
		});
		describe("when processDeletedTracks returns false:", function(){
			beforeEach(function(){
				spyOn(kindListViewPlaylist, "processDeletedTracks").andReturn(false);
				spyOn(kindListViewPlaylist,'onConfirmDone');
			});	
			it('should call processDeletedTracks',function(){
				kindListViewPlaylist.onDoneEdit(null,{"rowIndex":0,"dispatchTarget":null});
				expect(kindListViewPlaylist.processDeletedTracks).toHaveBeenCalled();
			});
			it('should not call this.$.popupConfirmDone openAtCenter',function(){
				spyOn(kindListViewPlaylist.$.popupConfirmDone,'openAtCenter');
				kindListViewPlaylist.onDoneEdit(null,{"rowIndex":0,"dispatchTarget":null});
				expect(kindListViewPlaylist.$.popupConfirmDone.openAtCenter).not.toHaveBeenCalled();
			});	
			it('should call onConfirmDone',function(){
				kindListViewPlaylist.onDoneEdit(null,{"rowIndex":0,"dispatchTarget":null});
				expect(kindListViewPlaylist.onConfirmDone).toHaveBeenCalled();
			});		
		});	
	});
	describe('test (onConfirmDone)', function(){
		beforeEach(function(){
			kindListViewPlaylist.$.popupConfirmDone = {close: function(){}};
			spyOn(kindListViewPlaylist,'onEditMode');
			spyOn(kindListViewPlaylist,'updatePlaylist');
		});
		it('should call .close',function(){
			spyOn(kindListViewPlaylist.$.popupConfirmDone,'close');
			kindListViewPlaylist.onConfirmDone(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewPlaylist.onEditMode).toHaveBeenCalledWith(false);
		});
		
		it('should call onEditMode',function(){
			kindListViewPlaylist.onConfirmDone(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewPlaylist.onEditMode).toHaveBeenCalledWith(false);
		});
		it('should call updatePlaylist',function(){
			kindListViewPlaylist.onConfirmDone(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewPlaylist.updatePlaylist).toHaveBeenCalled();
		});
	});
	describe('test (onCancelDone)', function(){
	});
	describe('test (onEditMode)', function(){
		it('should change boolEditMode',function(){
			kindListViewPlaylist.onEditMode(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewPlaylist.boolEditMode).toEqual(null);
		});
		it('should call doEditPlaylist',function(){
			spyOn(kindListViewPlaylist,'doEditPlaylist');
			kindListViewPlaylist.onEditMode(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewPlaylist.doEditPlaylist).toHaveBeenCalledWith("");
		});
	});
	describe('test (onclick_btnDeleteItem)', function(){
	});
	describe('test (processDeletedTracks)', function(){
		it('should change arUpdateTracks',function(){
			kindListViewPlaylist.arSongs = [];
			
			kindListViewPlaylist.processDeletedTracks();
			expect(kindListViewPlaylist.arUpdateTracks).toEqual([]);
		});
	});
/*	describe('test (updatePlaylist)', function(){
		it('should call doUpdatePlaylist',function(){
			spyOn(kindListViewPlaylist,'doUpdatePlaylist');
			kindListViewPlaylist.updatePlaylist();
			expect(kindListViewPlaylist.doUpdatePlaylist).toHaveBeenCalled();
		});
	});*/
	describe('test (playlistUpdated)', function(){
		it('should change strCurrListID',function(){
			var temp = kindListViewPlaylist.strCurrListID;
			kindListViewPlaylist.playlistUpdated();
			expect(kindListViewPlaylist.strCurrListID).not.toEqual(temp);
		});
		it('should call getPlaylist',function(){
			spyOn(kindListViewPlaylist,'getPlaylist');
			kindListViewPlaylist.playlistUpdated();
			expect(kindListViewPlaylist.getPlaylist).toHaveBeenCalled();
		});
	});
});