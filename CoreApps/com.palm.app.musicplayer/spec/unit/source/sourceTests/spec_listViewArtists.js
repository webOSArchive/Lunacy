/*globals enyo, $L, event, Utilities, window, LibraryTools */

//var creator = function(){
//				return enyo.create({kind: "kindListViewArtists"});
//			}
//var objName = "ListViewArtists";
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

describe('ListViewArtists tests', function(){
	var creator = function (){
				return enyo.create({kind: "kindListViewArtists"});
			};
	var ListViewArtists;
	beforeEach(function(){
		ListViewArtists = creator();
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
		before(ListViewArtists,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof ListViewArtists.controls).toBe('object');
		expect(typeof ListViewArtists.children).toBe('object');
		expect(ListViewArtists._componentNameMap).toEqual({"ctrlListViewHeader":2,"control":8,"spacer":3,"image":2,"button":2});
		expect(typeof ListViewArtists.$).toBe('object');
		expect(ListViewArtists.domStyles).toEqual({"height":"100%"});
		expect(ListViewArtists.domAttributes).toEqual({"className":"playlist","id":"kindListViewArtists"});
		expect(ListViewArtists.kind).toEqual("kindListViewArtists");
		expect(typeof ListViewArtists.owner).toBe('object');
		expect(ListViewArtists.name).toEqual("kindListViewArtists");
		expect(ListViewArtists.id).toEqual("kindListViewArtists");
		expect(ListViewArtists.controlParent).toEqual(undefined);
		expect(typeof ListViewArtists.layout).toBe('object');
		expect(ListViewArtists.strSortMode).toEqual("name");
		expect(ListViewArtists.boolSortAsc).toEqual(true);
		//expect(ListViewArtists.strCurrListID).toEqual("34A8D88FD583-F09F26-7D6739-756938-993540AC0FE95F28C7");
		//expect(ListViewArtists.local_tracks_Templ).toEqual({"template":"(#{num} songs)"});
		//expect(ListViewArtists.local_track_Templ).toEqual({"template":"(#{num} song)"});
		//expect(ListViewArtists.local_albums_Templ).toEqual({"template":"#{num} Albums"});
		//expect(ListViewArtists.local_album_Templ).toEqual({"template":"#{num} Album"});
		expect(ListViewArtists.arArtists).toEqual([{}]);
		expect(ListViewArtists.className).toEqual("playlist");
		expect(ListViewArtists.published).toEqual({"boolViewActive":false});
		expect(ListViewArtists.events).toEqual({"onSetPlaybackList":"","onRequestMedia":"","onListChanged":"","onListSorted":"","onSelectArtist":"","onRequestPlaylists":"","onShowBanner":""});
		expect(ListViewArtists.height).toEqual("100%");
		expect(ListViewArtists.intCurrTrack).toEqual(-1);
		expect(ListViewArtists.intCurrTrackOrigIndex).toEqual(-1);
		expect(ListViewArtists.intClickIndex).toEqual(-1);
		expect(ListViewArtists.boolMultiSelect).toEqual(false);
		expect(ListViewArtists.boolNotifyListChange).toEqual(false);
		expect(ListViewArtists.boolNeedRendered).toEqual(true);
		expect(ListViewArtists.objAlphaJumps).toEqual({});
		expect(ListViewArtists.kindName).toEqual("kindListViewArtists");
		//expect(ListViewArtists.kindComponents).toEqual([{"kind":"ctrlListViewHeader","onSearch_Header":"onSearchArtists","onSetMultiSelect":"onMultiSelect","boolShowMultiSelect":false},{"name":"content","kind":"Control","layoutKind":"VFlexLayout","className":"list","flex":1,"components":[{"kind":"Control","layoutKind":"HFlexLayout","flex":1,"components":[{"name":"listMedia","kind":"VirtualList","onSetupRow":"listSetupRow","className":"listMedia","flex":1,"components":[{"name":"header","kind":"Control","className":"divider","components":[{"name":"divider","kind":"Control","className":"column"}]},{"name":"itemMedia","kind":"draggableArtist","className":"artist","layoutKind":"HLayout","width":"100%","onclick":"onclick_listMedia","components":[{"className":"column play","onclick":"onclick_playArtist","components":[{"name":"imgPLay","kind":"ToolButton","icon":"images/bg_item_play.png"}]},{"name":"covers","className":"column cover","components":[{"name":"imgContainer","className":"img","showing":false,"components":[{"name":"imgThumb","kind":"Image","onerror":"onError_imgThumb"}]}]},{"kind":"Control","style":"vertical-align: middle;","components":[{"kind":"Control","layoutKind":"HLayout","components":[{"name":"artistName","className":"column name","content":""},{"name":"songCount","className":"column count","content":""}]},{"name":"albumCount","className":"column","content":"2 albums"}]}]},{"name":"MediaItemMenu","kind":"ctrMediaItemMenu","strMediaType":"Artist","onMenuClick":"onMenuClick","onRequestPlaylists":"doRequestPlaylists"}]},{"name":"alphaPicker","kind":"AlphaPicker","onAlphaPicked":"onAlphaPicked"}]}]},{"name":"empty","kind":"Control","layoutKind":"VFlexLayout","align":"center","height":"100%","pack":"justify","className":"empty","showing":false,"components":[{"kind":"Spacer","flex":1},{"kind":"Control","className":"content","components":[{"kind":"Image","src":"images/bg_empty_artists.png"},{"content":"There are no songs on your HP TouchPad","className":"heading"},{"content":"You can add music to your HP Touchpad with the HP Play music app, found at www.hpplay.com, or copy music to your HP TouchPad in USB mode."}]},{"kind":"Button","caption":"Add songs","showing":false},{"kind":"Spacer","flex":3}]},{"name":"txtResults"}]);
		expect(ListViewArtists.onSetPlaybackList).toEqual("");
		expect(ListViewArtists.onRequestMedia).toEqual("");
		expect(ListViewArtists.onListChanged).toEqual("");
		expect(ListViewArtists.onListSorted).toEqual("");
		expect(ListViewArtists.onSelectArtist).toEqual("");
		expect(ListViewArtists.onRequestPlaylists).toEqual("");
		expect(ListViewArtists.onShowBanner).toEqual("");
		expect(ListViewArtists.boolViewActive).toEqual(false);
		expect(ListViewArtists.intJumpRowOffset).toEqual(0);
		expect(ListViewArtists.boolDragHold).toEqual(false);
		expect(ListViewArtists.boolDragItem).toEqual(false);
		expect(ListViewArtists.boolDraggingList).toEqual(false);
		expect(ListViewArtists.dragObjType).toEqual(null);
		expect(ListViewArtists.arItems).toEqual([]);
		expect(ListViewArtists.albumGroup).toEqual(false);
		expect(ListViewArtists.onAddToPlaylist).toEqual("");
		expect(ListViewArtists.onItemDrag).toEqual("");
		expect(ListViewArtists.onItemShowAvatar).toEqual("");
		expect(ListViewArtists.onItemShowAvatarIcon).toEqual("");
		expect(ListViewArtists.controlParentName).toEqual("client");
		expect(ListViewArtists.defaultKind).toEqual("Control");
		expect(ListViewArtists.onclick).toEqual("");
		expect(ListViewArtists.onmousedown).toEqual("");
		expect(ListViewArtists.onmouseup).toEqual("");
		expect(ListViewArtists.style).toEqual("");
		expect(ListViewArtists.container).toEqual(null);
		expect(ListViewArtists.parent).toEqual(null);
		expect(ListViewArtists.generated).toEqual(false);
		expect(ListViewArtists.allowHtml).toEqual(false);
		expect(ListViewArtists.content).toEqual("");
		expect(ListViewArtists.nodeTag).toEqual("div");
		expect(ListViewArtists.node).toEqual(null);
		expect(ListViewArtists.showing).toEqual(true);
		expect(ListViewArtists.prepend).toEqual(false);
		expect(ListViewArtists.wantsEvents).toEqual(true);
	});
	describe('test (itemMousehold)', function(){
	});
	describe('test (create)', function(){
		it('should change strCurrListID',function(){
			ListViewArtists.strCurrListID = "";
			ListViewArtists.create();
			expect(ListViewArtists.strCurrListID).not.toEqual("");
		});
		it('should change local_tracks_Templ',function(){
			ListViewArtists.local_tracks_Templ = "";
			ListViewArtists.create();
			expect(ListViewArtists.local_tracks_Templ).not.toEqual("");
		});
		it('should change local_track_Templ',function(){
			ListViewArtists.local_track_Templ = "";
			ListViewArtists.create();
			expect(ListViewArtists.local_track_Templ).not.toEqual("");
		});
		it('should change local_albums_Templ',function(){
			ListViewArtists.local_albums_Templ = "";
			ListViewArtists.create();
			expect(ListViewArtists.local_albums_Templ).not.toEqual("");
		});
		it('should change local_album_Templ',function(){
			ListViewArtists.local_album_Templ = "";
			ListViewArtists.create();
			expect(ListViewArtists.local_album_Templ).not.toEqual("");
		});
		it('should call inherited',function(){
			spyOn(ListViewArtists,'inherited');
			ListViewArtists.create();
			expect(ListViewArtists.inherited).not.toEqual("");
		});
	});
	describe('test (ready)', function(){
		it('should call inherited',function(){
			spyOn(ListViewArtists,'inherited');
			ListViewArtists.ready();
			expect(ListViewArtists.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (rendered)', function(){
		it('should call inherited',function(){
			spyOn(ListViewArtists,'inherited');
			ListViewArtists.rendered();
			expect(ListViewArtists.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (renderList)', function(){
		it('should call listQuery',function(){
			spyOn(ListViewArtists,'listQuery');
			ListViewArtists.renderList();
			expect(ListViewArtists.listQuery).toHaveBeenCalled();
		});
	});
	describe('test (refreshList)', function(){
		it('should call renderList',function(){
			spyOn(ListViewArtists,'renderList');
			ListViewArtists.refreshList();
			expect(ListViewArtists.renderList).toHaveBeenCalled();
		});
	});
	describe('test (resizeList)', function(){
	});
	describe('test (listQuery)', function(){
		it('should call doRequestMedia',function(){
			spyOn(ListViewArtists,'doRequestMedia');
			ListViewArtists.listQuery();
			expect(ListViewArtists.doRequestMedia).toHaveBeenCalled();
		});
	});
	describe('test (gotArtists)', function(){
		it('should change arArtists',function(){
			ListViewArtists.gotArtists();
			expect(ListViewArtists.arArtists).toEqual([]);
		});
		it('should change objAlphaJumps',function(){
			ListViewArtists.gotArtists();
			expect(ListViewArtists.objAlphaJumps).toEqual({});
		});
	});
	describe('test (onSearchArtists)', function(){
		it('should change arArtists',function(){
			ListViewArtists.onSearchArtists(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.arArtists).toEqual([]);
		});
		it('should call doRequestMedia',function(){
			spyOn(ListViewArtists,'doRequestMedia');
			ListViewArtists.onSearchArtists(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.doRequestMedia).toHaveBeenCalled();
		});
	});
	describe('test (listSetupRow)', function(){
	});
	describe('test (onclick_listMedia)', function(){
		it('should call callSelectArtist',function(){
			spyOn(ListViewArtists,'callSelectArtist');
			ListViewArtists.onclick_listMedia(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.callSelectArtist).toHaveBeenCalledWith(0);
		});
	});
	describe('test (callSelectArtist)', function(){
		it('should call doSelectArtist',function(){
			ListViewArtists.arAlbums = [{}];
			spyOn(ListViewArtists,'doSelectArtist');
			ListViewArtists.callSelectArtist(0,false);
			expect(ListViewArtists.doSelectArtist).toHaveBeenCalledWith({boolForcePlay: false});
		});
	});
	describe('test (onclick_playArtist)', function(){
		beforeEach(function(){
			spyOn(ListViewArtists,'callPlayArtist');
			spyOn(ListViewArtists,'callSelectArtist');
		});
		it('should call callPlayArtist',function(){
			
			ListViewArtists.onclick_playArtist(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.callSelectArtist).toHaveBeenCalledWith(undefined,true);
		});
		it('should call callSelectArtist',function(){
			
			ListViewArtists.onclick_playArtist(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.callSelectArtist).toHaveBeenCalledWith(undefined,true);
		});
	});
	describe('test (callPlayArtist)', function(){
	describe('testing (callPlayArtist) with params [0]', function(){
	});
	});
	describe('test (onclick_imgMenu)', function(){
		it('should change intClickIndex',function(){
			ListViewArtists.onclick_imgMenu(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.intClickIndex).toEqual(0);
		});
	});
	describe('test (onAlphaPicked)', function(){
	});
	describe('test (onMenuClick)', function(){
		it('should call callAddToPlaylist',function(){
			spyOn(ListViewArtists,'callAddToPlaylist');
			ListViewArtists.onMenuClick(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.callAddToPlaylist).toHaveBeenCalled();
		});
	});
	describe('test (onMultiSelect)', function(){
		it('should change boolMultiSelect',function(){
			ListViewArtists.onMultiSelect(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.boolMultiSelect).toEqual(true);
		});
	});
	describe('test (onError_imgThumb)', function(){
	describe('testing (onError_imgThumb) with params [{},{}]', function(){
		it('should call error',function(){
			spyOn(ListViewArtists,'error');
			ListViewArtists.onError_imgThumb({getSrc: function(){}},{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.error).toHaveBeenCalled();
		});
	});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(ListViewArtists,'_constructor');
			ListViewArtists.base();
			expect(ListViewArtists._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(ListViewArtists,'constructed');
			ListViewArtists.base();
			expect(ListViewArtists.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(ListViewArtists,'_constructor');
			ListViewArtists.ctor();
			expect(ListViewArtists._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(ListViewArtists,'constructed');
			ListViewArtists.ctor();
			expect(ListViewArtists.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doSetPlaybackList)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewArtists,'dispatchIndirectly');
			ListViewArtists.doSetPlaybackList();
			expect(ListViewArtists.dispatchIndirectly).toHaveBeenCalledWith("onSetPlaybackList",{});
		});
	});
	describe('test (doRequestMedia)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewArtists,'dispatchIndirectly');
			ListViewArtists.doRequestMedia();
			expect(ListViewArtists.dispatchIndirectly).toHaveBeenCalledWith("onRequestMedia",{});
		});
	});
	describe('test (doListChanged)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewArtists,'dispatchIndirectly');
			ListViewArtists.doListChanged();
			expect(ListViewArtists.dispatchIndirectly).toHaveBeenCalledWith("onListChanged",{});
		});
	});
	describe('test (doListSorted)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewArtists,'dispatchIndirectly');
			ListViewArtists.doListSorted();
			expect(ListViewArtists.dispatchIndirectly).toHaveBeenCalledWith("onListSorted",{});
		});
	});
	describe('test (doSelectArtist)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewArtists,'dispatchIndirectly');
			ListViewArtists.doSelectArtist();
			expect(ListViewArtists.dispatchIndirectly).toHaveBeenCalledWith("onSelectArtist",{});
		});
	});
	describe('test (doRequestPlaylists)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewArtists,'dispatchIndirectly');
			ListViewArtists.doRequestPlaylists();
			expect(ListViewArtists.dispatchIndirectly).toHaveBeenCalledWith("onRequestPlaylists",{});
		});
	});
	describe('test (doShowBanner)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewArtists,'dispatchIndirectly');
			ListViewArtists.doShowBanner();
			expect(ListViewArtists.dispatchIndirectly).toHaveBeenCalledWith("onShowBanner",{});
		});
	});
	describe('test (getBoolViewActive)', function(){
		it('should call getProperty',function(){
			spyOn(ListViewArtists,'getProperty');
			ListViewArtists.getBoolViewActive();
			expect(ListViewArtists.getProperty).toHaveBeenCalledWith("boolViewActive");
		});
	});
	describe('test (setBoolViewActive)', function(){
		it('should call _setProperty',function(){
			spyOn(ListViewArtists,'_setProperty');
			ListViewArtists.setBoolViewActive();
			expect(ListViewArtists._setProperty).toHaveBeenCalledWith("boolViewActive",undefined,"boolViewActiveChanged");
		});
	});
	describe('test (callAddToPlaylist)', function(){
		it('should call doAddToPlaylist',function(){
			spyOn(ListViewArtists,'doAddToPlaylist');
			ListViewArtists.callAddToPlaylist();
			expect(ListViewArtists.doAddToPlaylist).toHaveBeenCalledWith({"strMediaType":"song","arTracks":[{}]});
		});
	});
	describe('test (songMousehold)', function(){
	describe('testing (songMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			ListViewArtists.songMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.dragObjType).toEqual("song");
		});
		it('should change arItems',function(){
			ListViewArtists.songMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.arItems).toEqual(undefined);
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewArtists,'itemMousehold');
			ListViewArtists.songMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (albumMousehold)', function(){
	describe('testing (albumMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			ListViewArtists.albumMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.dragObjType).toEqual("album");
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewArtists,'itemMousehold');
			ListViewArtists.albumMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (albumGroupMousehold)', function(){
	describe('testing (albumGroupMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change albumGroup',function(){
			ListViewArtists.albumGroupMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.albumGroup).toEqual(true);
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewArtists,'itemMousehold');
			ListViewArtists.albumGroupMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (artistMousehold)', function(){
	describe('testing (artistMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			ListViewArtists.artistMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.dragObjType).toEqual("artist");
		});
		
		it('should call itemMousehold',function(){
			spyOn(ListViewArtists,'itemMousehold');
			ListViewArtists.artistMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (genreMousehold)', function(){
	describe('testing (genreMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			ListViewArtists.genreMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.dragObjType).toEqual("genre");
		});
		it('should change arItems',function(){
			ListViewArtists.genreMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.arItems).toEqual(undefined);
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewArtists,'itemMousehold');
			ListViewArtists.genreMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewArtists.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (itemMouserelease)', function(){
		it('should change dragObjType',function(){
			ListViewArtists.itemMouserelease();
			expect(ListViewArtists.dragObjType).toEqual(null);
		});
		it('should call setItemHighlighted',function(){
			spyOn(ListViewArtists,'setItemHighlighted');
			ListViewArtists.itemMouserelease();
			expect(ListViewArtists.setItemHighlighted).toHaveBeenCalledWith(false);
		});
		it('should call doItemShowAvatar',function(){
			spyOn(ListViewArtists,'doItemShowAvatar');
			ListViewArtists.itemMouserelease();
			expect(ListViewArtists.doItemShowAvatar).toHaveBeenCalledWith(false);
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
			spyOn(ListViewArtists,'dispatchIndirectly');
			ListViewArtists.doAddToPlaylist();
			expect(ListViewArtists.dispatchIndirectly).toHaveBeenCalledWith("onAddToPlaylist",{});
		});
	});
	describe('test (doItemDrag)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewArtists,'dispatchIndirectly');
			ListViewArtists.doItemDrag();
			expect(ListViewArtists.dispatchIndirectly).toHaveBeenCalledWith("onItemDrag",{});
		});
	});
	describe('test (doItemShowAvatar)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewArtists,'dispatchIndirectly');
			ListViewArtists.doItemShowAvatar();
			expect(ListViewArtists.dispatchIndirectly).toHaveBeenCalledWith("onItemShowAvatar",{});
		});
	});
	describe('test (doItemShowAvatarIcon)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewArtists,'dispatchIndirectly');
			ListViewArtists.doItemShowAvatarIcon();
			expect(ListViewArtists.dispatchIndirectly).toHaveBeenCalledWith("onItemShowAvatarIcon",{});
		});
	});
	describe('test (highlightTrack)', function(){
	});
	describe('test (layoutKind)', function(){
		it('should call _constructor',function(){
			spyOn(ListViewArtists,'_constructor');
			ListViewArtists.layoutKind();
			expect(ListViewArtists._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(ListViewArtists,'constructed');
			ListViewArtists.layoutKind();
			expect(ListViewArtists.constructed).toHaveBeenCalled();
		});
	});
	describe('test (destroy)', function(){
		/*
		it('should call unregisterEvents',function(){
			spyOn(ListViewArtists,'unregisterEvents');
			ListViewArtists.destroy();
			expect(ListViewArtists.unregisterEvents).toHaveBeenCalled();
		});
		*/
		it('should call inherited',function(){
			spyOn(ListViewArtists,'inherited');
			ListViewArtists.destroy();
			expect(ListViewArtists.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(ListViewArtists,'inherited');
			ListViewArtists.importProps();
			expect(ListViewArtists.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(ListViewArtists,'createChrome');
			ListViewArtists.initComponents();
			expect(ListViewArtists.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(ListViewArtists,'inherited');
			ListViewArtists.initComponents();
			expect(ListViewArtists.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(ListViewArtists,'inherited');
			ListViewArtists.createComponents();
			expect(ListViewArtists.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(ListViewArtists,'discoverControlParent');
			ListViewArtists.createComponents();
			expect(ListViewArtists.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(ListViewArtists,'createComponents');
			ListViewArtists.createChrome();
			expect(ListViewArtists.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});
