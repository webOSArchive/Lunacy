/*globals enyo, $L, event, Utilities, window, LibraryTools */

//var creator = function(){
//				return enyo.create({kind: "kindListViewAlbums"});
//			}
//var objName = "ListViewAlbums";
//
//var before = function (obj, callbacks) {
//	
//	obj.itemMousehold = function(){};
//	
//	obj.arArtists = [{}];
//  obj.$.ctrlListViewHeader = {}
//  obj.$.ctrlListViewHeader.setIntHeaderCount = function(){};
//  obj.$.ctrlListViewHeader.clearSearchInput = function(){};  
//  obj.$.listMedia = {};
//  obj.$.MediaItemMenu = {openMenu: function(){}};
//  obj.$.listMedia.setMultiSelect = function(){};
//  obj.$.listMedia.fetchRowIndex = function(){};
//    obj.$.listMedia.refresh = function(){};
//    obj.$.listMedia.resized = function(){};
//    obj.$.imgContainer.setShowing = function(){};
//	}
//var event = [[null, {rowIndex:0, dispatchTarget: null}]];
//var params = {
//		 "dragObjType" : event,
//		 "songMousehold" : event,
//		 "albumMousehold" : event,
//		 "albumGroupMousehold" : event,
//		 "artistMousehold" : event,
//		 "genreMousehold" : event,
//		 "onclick_listmedia" : event,
//		 "onclick_listmedia" : event,
//		 "getDragIndex"	: event,
//		 "mousehold_itemmedia" : event,
//		 "callPlayArtist" : [[0]],
//		 "onError_imgThumb" : [[{"getSrc": function(){}}, {}]]
//		 
//};
//
//specOut(creator, objName, before, params);

describe('ListViewAlbums tests', function(){
	var creator = function (){
				return enyo.create({kind: "kindListViewAlbums"});
			};
	var ListViewAlbums;
	beforeEach(function(){
		ListViewAlbums = creator();
		var before = function (obj, callbacks) {
	
	obj.itemMousehold = function(){};
	
	obj.arArtists = [{}];
  obj.$.ctrlListViewHeader = {}
  obj.$.ctrlListViewHeader.setIntHeaderCount = function(){};
  obj.$.ctrlListViewHeader.clearSearchInput = function(){};  
  obj.$.listMedia = {};
  obj.$.MediaItemMenu = {openMenu: function(){}};
  obj.$.listMedia.setMultiSelect = function(){};
  obj.$.listMedia.fetchRowIndex = function(){};
    obj.$.listMedia.refresh = function(){};
    obj.$.listMedia.resized = function(){};
    obj.$.imgContainer.setShowing = function(){};
	};
		var callback = function(one, fun){return fun;};
		before(ListViewAlbums,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof ListViewAlbums.controls).toBe('object');
		expect(typeof ListViewAlbums.children).toBe('object');
		expect(ListViewAlbums._componentNameMap).toEqual({"ctrlListViewHeader":2,"control":7,"spacer":3,"image":2,"button":2});
		expect(typeof ListViewAlbums.$).toBe('object');
		expect(ListViewAlbums.domStyles).toEqual({"height":"100%"});
		expect(ListViewAlbums.domAttributes).toEqual({"className":"artists","id":"kindListViewAlbums"});
		expect(ListViewAlbums.kind).toEqual("kindListViewAlbums");
		expect(typeof ListViewAlbums.owner).toBe('object');
		expect(ListViewAlbums.name).toEqual("kindListViewAlbums");
		expect(ListViewAlbums.id).toEqual("kindListViewAlbums");
		expect(ListViewAlbums.controlParent).toEqual(undefined);
		expect(typeof ListViewAlbums.layout).toBe('object');
		expect(ListViewAlbums.strSortMode).toEqual("name");
		expect(ListViewAlbums.boolSortAsc).toEqual(true);
		//expect(ListViewAlbums.strCurrListID).toEqual("98634506F298-387C6C-CE1D7C-FB1D21-D9DD0A531D14E76534");
		//expect(ListViewAlbums.local_tracks_Templ).toEqual({"template":"(#{num} songs)"});
		//expect(ListViewAlbums.local_track_Templ).toEqual({"template":"(#{num} song)"});
		expect(ListViewAlbums.arArtists).toEqual([{}]);
		expect(ListViewAlbums.published).toEqual({"boolViewActive":false});
		expect(ListViewAlbums.className).toEqual("artists");
		expect(ListViewAlbums.events).toEqual({"onSetPlaybackList":"","onRequestMedia":"","onListChanged":"","onListSorted":"","onSelectAlbum":"","onRequestPlaylists":""});
		expect(ListViewAlbums.height).toEqual("100%");
		expect(ListViewAlbums.intCurrTrack).toEqual(-1);
		expect(ListViewAlbums.intCurrTrackOrigIndex).toEqual(-1);
		expect(ListViewAlbums.boolNotifyListChange).toEqual(false);
		expect(ListViewAlbums.boolDragHold).toEqual(false);
		expect(ListViewAlbums.boolDragItem).toEqual(false);
		expect(ListViewAlbums.boolDraggingList).toEqual(false);
		expect(ListViewAlbums.boolNeedRendered).toEqual(true);
		expect(ListViewAlbums.kindName).toEqual("kindListViewAlbums");
	//expect(ListViewAlbums.kindComponents).toEqual([{"kind":"ctrlListViewHeader","onSearch_Header":"onSearchAlbums"},{"name":"content","kind":"Control","layoutKind":"VFlexLayout","className":"list","flex":1,"components":[{"name":"listMedia","kind":"VirtualList","onQuery":"listQuery","onSetupRow":"listSetupRow","className":"listMedia","flex":1,"components":[{"name":"header","kind":"Control","className":"divider","components":[{"name":"divider","kind":"Control","className":"column"}]},{"name":"itemMedia","kind":"draggableAlbumGroup","className":"album","layoutKind":"HFlexLayout","align":"center","onclick":"onclick_listMedia","components":[{"className":"column play","onclick":"onclick_playAlbum","components":[{"name":"imgPLay","kind":"ToolButton","icon":"images/bg_item_play.png"}]},{"name":"covers","className":"column cover","components":[{"name":"imgContainer","className":"img","showing":false,"components":[{"name":"imgThumb","kind":"Image","onerror":"onError_imgThumb"}]}]},{"kind":"Control","components":[{"kind":"Control","layoutKind":"HFlexLayout","components":[{"name":"albumName","className":"column name","content":""},{"name":"albumCounts","className":"column count","content":""}]},{"name":"albumArtist","className":"column","content":""}]}]}]}]},{"name":"empty","kind":"Control","layoutKind":"VFlexLayout","align":"center","height":"100%","pack":"justify","className":"empty","showing":false,"components":[{"kind":"Spacer","flex":1},{"kind":"Control","className":"content","components":[{"kind":"Image","src":"images/bg_empty_albums.png"},{"content":"There are no songs on your HP TouchPad","className":"heading"},{"content":"You can add music to your HP Touchpad with the HP Play music app, found at www.hpplay.com, or copy music to your HP TouchPad in USB mode."}]},{"kind":"Button","caption":"Add songs","showing":false},{"kind":"Spacer","flex":3}]},{"name":"txtResults"},{"name":"MediaItemMenu","kind":"ctrMediaItemMenu","strMediaType":"Album","onMenuClick":"onMenuClick","onRequestPlaylists":"doRequestPlaylists"}]);
		expect(ListViewAlbums.onSetPlaybackList).toEqual("");
		expect(ListViewAlbums.onRequestMedia).toEqual("");
		expect(ListViewAlbums.onListChanged).toEqual("");
		expect(ListViewAlbums.onListSorted).toEqual("");
		expect(ListViewAlbums.onSelectAlbum).toEqual("");
		expect(ListViewAlbums.onRequestPlaylists).toEqual("");
		expect(ListViewAlbums.boolViewActive).toEqual(false);
		expect(ListViewAlbums.intJumpRowOffset).toEqual(0);
		expect(ListViewAlbums.dragObjType).toEqual(null);
		expect(ListViewAlbums.arItems).toEqual([]);
		expect(ListViewAlbums.albumGroup).toEqual(false);
		expect(ListViewAlbums.onAddToPlaylist).toEqual("");
		expect(ListViewAlbums.onItemDrag).toEqual("");
		expect(ListViewAlbums.onItemShowAvatar).toEqual("");
		expect(ListViewAlbums.onItemShowAvatarIcon).toEqual("");
		expect(ListViewAlbums.controlParentName).toEqual("client");
		expect(ListViewAlbums.defaultKind).toEqual("Control");
		expect(ListViewAlbums.onclick).toEqual("");
		expect(ListViewAlbums.onmousedown).toEqual("");
		expect(ListViewAlbums.onmouseup).toEqual("");
		expect(ListViewAlbums.style).toEqual("");
		expect(ListViewAlbums.container).toEqual(null);
		expect(ListViewAlbums.parent).toEqual(null);
		expect(ListViewAlbums.generated).toEqual(false);
		expect(ListViewAlbums.allowHtml).toEqual(false);
		expect(ListViewAlbums.content).toEqual("");
		expect(ListViewAlbums.nodeTag).toEqual("div");
		expect(ListViewAlbums.node).toEqual(null);
		expect(ListViewAlbums.showing).toEqual(true);
		expect(ListViewAlbums.prepend).toEqual(false);
		expect(ListViewAlbums.wantsEvents).toEqual(true);
	});
	describe('test (itemMousehold)', function(){
	});
	describe('test (create)', function(){
		it('should change strCurrListID',function(){
			ListViewAlbums.strCurrListID = "";
			ListViewAlbums.create();
			expect(ListViewAlbums.strCurrListID).not.toEqual("");
		});
		it('should change local_tracks_Templ',function(){
			ListViewAlbums.local_tracks_Templ = "";
			ListViewAlbums.create();
			expect(ListViewAlbums.local_tracks_Templ).not.toEqual("");
		});
		it('should change local_track_Templ',function(){
			ListViewAlbums.local_track_Templ = "";
			ListViewAlbums.create();
			expect(ListViewAlbums.local_track_Templ).not.toEqual("");
		});
		it('should call inherited',function(){
			spyOn(ListViewAlbums,'inherited');
			ListViewAlbums.create();
			expect(ListViewAlbums.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (ready)', function(){
		it('should call inherited',function(){
			spyOn(ListViewAlbums,'inherited');
			ListViewAlbums.ready();
			expect(ListViewAlbums.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (rendered)', function(){
		it('should call inherited',function(){
			spyOn(ListViewAlbums,'inherited');
			ListViewAlbums.rendered();
			expect(ListViewAlbums.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (renderList)', function(){
		it('should call listQuery',function(){
			spyOn(ListViewAlbums,'listQuery');
			ListViewAlbums.renderList();
			expect(ListViewAlbums.listQuery).toHaveBeenCalled();
		});
	});
	describe('test (resizeList)', function(){
	});
	describe('test (refreshList)', function(){
		it('should call renderList',function(){
			spyOn(ListViewAlbums,'renderList');
			ListViewAlbums.refreshList();
			expect(ListViewAlbums.renderList).toHaveBeenCalled();
		});
	});
	describe('test (listQuery)', function(){
		it('should call doRequestMedia',function(){
			spyOn(ListViewAlbums,'doRequestMedia');
			ListViewAlbums.listQuery();
			expect(ListViewAlbums.doRequestMedia).toHaveBeenCalled();
		});
	});
	describe('test (gotAlbums)', function(){
		it('should change arAlbums',function(){
			ListViewAlbums.gotAlbums();
			expect(ListViewAlbums.arAlbums).toEqual([]);
		});
	});
	describe('test (onSearchAlbums)', function(){
		it('should change arAlbums',function(){
			ListViewAlbums.onSearchAlbums(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.arAlbums).toEqual([]);
		});
		it('should call doRequestMedia',function(){
			spyOn(ListViewAlbums,'doRequestMedia');
			ListViewAlbums.onSearchAlbums(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.doRequestMedia).toHaveBeenCalled();
		});
	});
	describe('test (listSetupRow)', function(){
	});
	describe('test (onclick_listMedia)', function(){
		it('should call callSelectAlbum',function(){
			spyOn(ListViewAlbums,'callSelectAlbum');
			ListViewAlbums.onclick_listMedia(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.callSelectAlbum).toHaveBeenCalledWith(0);
		});
	});
	describe('test (callSelectAlbum)', function(){
		it('should call doSelectAlbum',function(){
			ListViewAlbums.arAlbums = [{}];
			spyOn(ListViewAlbums,'doSelectAlbum');
			ListViewAlbums.callSelectAlbum(0,false);
			expect(ListViewAlbums.doSelectAlbum).toHaveBeenCalled();
		});
	});
	describe('test (onclick_playAlbum)', function(){
		it('should call callPlayAlbum',function(){
			spyOn(ListViewAlbums,'callSelectAlbum');
			ListViewAlbums.onclick_playAlbum(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.callSelectAlbum).toHaveBeenCalledWith(undefined, true);
		});
		it('should call callSelectAlbum',function(){
			spyOn(ListViewAlbums,'callSelectAlbum');
			//spyOn(ListViewAlbums,'callPlayAlbum');
			ListViewAlbums.onclick_playAlbum(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.callSelectAlbum).toHaveBeenCalledWith(undefined, true);
		});
	});
	describe('test (callPlayAlbum)', function(){
		//it should be using albumArtist instead of artist
	});
	
	/*
	describe('test (onclick_imgMenu)', function(){
		it('should change intClickIndex',function(){
			ListViewAlbums.onclick_imgMenu(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.intClickIndex).toEqual(0);
		});
	});
	describe('test (onMenuClick)', function(){
		it('should call callAddToPlaylist',function(){
			spyOn(ListViewAlbums,'callAddToPlaylist');
			ListViewAlbums.onMenuClick(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.callAddToPlaylist).toHaveBeenCalledWith(undefined,undefined);
		});
	});
	*/
	describe('test (onError_imgThumb)', function(){
	describe('testing (onError_imgThumb) with params [{},{}]', function(){
		it('should call error',function(){
			spyOn(ListViewAlbums,'error');
			ListViewAlbums.onError_imgThumb({getSrc : function(){}},{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.error).toHaveBeenCalled();
		});
	});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(ListViewAlbums,'_constructor');
			ListViewAlbums.base();
			expect(ListViewAlbums._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(ListViewAlbums,'constructed');
			ListViewAlbums.base();
			expect(ListViewAlbums.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(ListViewAlbums,'_constructor');
			ListViewAlbums.ctor();
			expect(ListViewAlbums._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(ListViewAlbums,'constructed');
			ListViewAlbums.ctor();
			expect(ListViewAlbums.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doSetPlaybackList)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewAlbums,'dispatchIndirectly');
			ListViewAlbums.doSetPlaybackList();
			expect(ListViewAlbums.dispatchIndirectly).toHaveBeenCalledWith("onSetPlaybackList",{});
		});
	});
	describe('test (doRequestMedia)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewAlbums,'dispatchIndirectly');
			ListViewAlbums.doRequestMedia();
			expect(ListViewAlbums.dispatchIndirectly).toHaveBeenCalledWith("onRequestMedia",{});
		});
	});
	describe('test (doListChanged)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewAlbums,'dispatchIndirectly');
			ListViewAlbums.doListChanged();
			expect(ListViewAlbums.dispatchIndirectly).toHaveBeenCalledWith("onListChanged",{});
		});
	});
	describe('test (doListSorted)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewAlbums,'dispatchIndirectly');
			ListViewAlbums.doListSorted();
			expect(ListViewAlbums.dispatchIndirectly).toHaveBeenCalledWith("onListSorted",{});
		});
	});
	describe('test (doSelectAlbum)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewAlbums,'dispatchIndirectly');
			ListViewAlbums.doSelectAlbum();
			expect(ListViewAlbums.dispatchIndirectly).toHaveBeenCalledWith("onSelectAlbum",{});
		});
	});
	describe('test (doRequestPlaylists)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewAlbums,'dispatchIndirectly');
			ListViewAlbums.doRequestPlaylists();
			expect(ListViewAlbums.dispatchIndirectly).toHaveBeenCalledWith("onRequestPlaylists",{});
		});
	});
	describe('test (getBoolViewActive)', function(){
		it('should call getProperty',function(){
			spyOn(ListViewAlbums,'getProperty');
			ListViewAlbums.getBoolViewActive();
			expect(ListViewAlbums.getProperty).toHaveBeenCalledWith("boolViewActive");
		});
	});
	describe('test (setBoolViewActive)', function(){
		it('should call _setProperty',function(){
			spyOn(ListViewAlbums,'_setProperty');
			ListViewAlbums.setBoolViewActive();
			expect(ListViewAlbums._setProperty).toHaveBeenCalledWith("boolViewActive",undefined,"boolViewActiveChanged");
		});
	});
	describe('test (callAddToPlaylist)', function(){
		it('should call doAddToPlaylist',function(){
			spyOn(ListViewAlbums,'doAddToPlaylist');
			ListViewAlbums.callAddToPlaylist();
			expect(ListViewAlbums.doAddToPlaylist).toHaveBeenCalledWith({"strMediaType":"song","arTracks":[{}]});
		});
	});
	describe('test (songMousehold)', function(){
	describe('testing (songMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			ListViewAlbums.songMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.dragObjType).toEqual("song");
		});
		it('should change arItems',function(){
			ListViewAlbums.songMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.arItems).toEqual(undefined);
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewAlbums,'itemMousehold');
			ListViewAlbums.songMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (albumMousehold)', function(){
	describe('testing (albumMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			ListViewAlbums.albumMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.dragObjType).toEqual("album");
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewAlbums,'itemMousehold');
			ListViewAlbums.albumMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (albumGroupMousehold)', function(){
	describe('testing (albumGroupMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change albumGroup',function(){
			ListViewAlbums.albumGroupMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.albumGroup).toEqual(true);
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewAlbums,'itemMousehold');
			ListViewAlbums.albumGroupMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (artistMousehold)', function(){
	describe('testing (artistMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			ListViewAlbums.artistMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.dragObjType).toEqual("artist");
		});
		it('should change arItems',function(){
			ListViewAlbums.artistMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.arItems).toEqual([{}]);
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewAlbums,'itemMousehold');
			ListViewAlbums.artistMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (genreMousehold)', function(){
	describe('testing (genreMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			ListViewAlbums.genreMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.dragObjType).toEqual("genre");
		});
		it('should change arItems',function(){
			ListViewAlbums.genreMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.arItems).toEqual(undefined);
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewAlbums,'itemMousehold');
			ListViewAlbums.genreMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewAlbums.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (itemMouserelease)', function(){
		it('should change dragObjType',function(){
			ListViewAlbums.itemMouserelease();
			expect(ListViewAlbums.dragObjType).toEqual(null);
		});
		it('should call setItemHighlighted',function(){
			spyOn(ListViewAlbums,'setItemHighlighted');
			ListViewAlbums.itemMouserelease();
			expect(ListViewAlbums.setItemHighlighted).toHaveBeenCalledWith(false);
		});
		it('should call doItemShowAvatar',function(){
			spyOn(ListViewAlbums,'doItemShowAvatar');
			ListViewAlbums.itemMouserelease();
			expect(ListViewAlbums.doItemShowAvatar).toHaveBeenCalledWith(false);
		});
	});
	describe('test (itemDragStart)', function(){
	});
	describe('test (onDrag_itemMedia)', function(){
	});
	describe('test (itemDragFinish)', function(){
	});
	describe('test (setItemHighlighted)', function(){
	});
	describe('test (onDrop_itemMedia)', function(){
	});
	describe('test (getDragIndex)', function(){
	describe('testing (getDragIndex) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
	});
	});
	describe('test (doAddToPlaylist)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewAlbums,'dispatchIndirectly');
			ListViewAlbums.doAddToPlaylist();
			expect(ListViewAlbums.dispatchIndirectly).toHaveBeenCalledWith("onAddToPlaylist",{});
		});
	});
	describe('test (doItemDrag)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewAlbums,'dispatchIndirectly');
			ListViewAlbums.doItemDrag();
			expect(ListViewAlbums.dispatchIndirectly).toHaveBeenCalledWith("onItemDrag",{});
		});
	});
	describe('test (doItemShowAvatar)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewAlbums,'dispatchIndirectly');
			ListViewAlbums.doItemShowAvatar();
			expect(ListViewAlbums.dispatchIndirectly).toHaveBeenCalledWith("onItemShowAvatar",{});
		});
	});
	describe('test (doItemShowAvatarIcon)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewAlbums,'dispatchIndirectly');
			ListViewAlbums.doItemShowAvatarIcon();
			expect(ListViewAlbums.dispatchIndirectly).toHaveBeenCalledWith("onItemShowAvatarIcon",{});
		});
	});
	describe('test (highlightTrack)', function(){
	});
	describe('test (layoutKind)', function(){
		it('should call _constructor',function(){
			spyOn(ListViewAlbums,'_constructor');
			ListViewAlbums.layoutKind();
			expect(ListViewAlbums._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(ListViewAlbums,'constructed');
			ListViewAlbums.layoutKind();
			expect(ListViewAlbums.constructed).toHaveBeenCalled();
		});
	});
	describe('test (destroy)', function(){
		/*
		it('should call unregisterEvents',function(){
			spyOn(ListViewAlbums,'unregisterEvents');
			ListViewAlbums.destroy();
			expect(ListViewAlbums.unregisterEvents).toHaveBeenCalled();
		});
		*/
		it('should call inherited',function(){
			spyOn(ListViewAlbums,'inherited');
			ListViewAlbums.destroy();
			expect(ListViewAlbums.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(ListViewAlbums,'inherited');
			ListViewAlbums.importProps();
			expect(ListViewAlbums.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(ListViewAlbums,'createChrome');
			ListViewAlbums.initComponents();
			expect(ListViewAlbums.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(ListViewAlbums,'inherited');
			ListViewAlbums.initComponents();
			expect(ListViewAlbums.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(ListViewAlbums,'inherited');
			ListViewAlbums.createComponents();
			expect(ListViewAlbums.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(ListViewAlbums,'discoverControlParent');
			ListViewAlbums.createComponents();
			expect(ListViewAlbums.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(ListViewAlbums,'createComponents');
			ListViewAlbums.createChrome();
			expect(ListViewAlbums.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});