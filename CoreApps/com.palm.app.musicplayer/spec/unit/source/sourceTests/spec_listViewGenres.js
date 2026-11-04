/*globals enyo, $L, event, Utilities, window, LibraryTools */

//var creator = function(){
//				return enyo.create({kind: "kindListViewGenres"});
//			}
//var objName = "ListViewGenres";
//
//var before = function (obj, callbacks) {
//	
//	obj.itemMousehold = function(){};
//	
//    
//  obj.$.ctrlListViewHeader = {}
//  obj.$.ctrlListViewHeader.setIntHeaderCount = function(){};
//  obj.$.ctrlListViewHeader.clearSearchInput = function(){};
//  obj.$.listMedia.fetchRowIndex = function(){return 0;};
//  obj.$.itemRow.destroyControls = function(){};
//    obj.$.listMedia = {};
//    obj.$.listMedia.refresh = function(){};
//    obj.$.listMedia.resized = function(){};
//	}
//var event = [[{intColumnIndex: 0}, {rowIndex:0, dispatchTarget: null}]];
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
//		 "mousehold_itemmedia" : event
//		 
//};
//
//specOut(creator, objName, before, params);


describe('ListViewGenres tests', function(){
	var creator = function (){
				return enyo.create({kind: "kindListViewGenres"});
			};
	var ListViewGenres;
	beforeEach(function(){
		ListViewGenres = creator();
		var before = function (obj, callbacks) {
	
	obj.itemMousehold = function(){};
	
    
  obj.$.ctrlListViewHeader = {}
  obj.$.ctrlListViewHeader.setIntHeaderCount = function(){};
  obj.$.ctrlListViewHeader.clearSearchInput = function(){};
  obj.$.listMedia.fetchRowIndex = function(){return 0;};
  obj.$.itemRow.destroyControls = function(){};
    obj.$.listMedia = {};
    obj.$.listMedia.refresh = function(){};
    obj.$.listMedia.resized = function(){};
	};
		var callback = function(one, fun){return fun;};
		before(ListViewGenres,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof ListViewGenres.controls).toBe('object');
		expect(typeof ListViewGenres.children).toBe('object');
		expect(ListViewGenres._componentNameMap).toEqual({"ctrlListViewHeader":2,"spacer":3,"control":4,"image":2,"button":2});
		expect(typeof ListViewGenres.$).toBe('object');
		expect(ListViewGenres.domStyles).toEqual({"height":"100%"});
		expect(ListViewGenres.domAttributes).toEqual({"id":"kindListViewGenres"});
		expect(ListViewGenres.kind).toEqual("kindListViewGenres");
		expect(typeof ListViewGenres.owner).toBe('object');
		expect(ListViewGenres.name).toEqual("kindListViewGenres");
		expect(ListViewGenres.id).toEqual("kindListViewGenres");
		expect(ListViewGenres.controlParent).toEqual(undefined);
		expect(typeof ListViewGenres.layout).toBe('object');
		expect(ListViewGenres.strSortMode).toEqual("name");
		expect(ListViewGenres.boolSortAsc).toEqual(true);
		//expect(ListViewGenres.strCurrListID).toEqual("5F1697BD382F-E68FA7-9DD2FD-A57A02-48C53E7F933693E220");
		//expect(ListViewGenres.local_tracks_Templ).toEqual({"template":"#{num} songs"});
		//expect(ListViewGenres.local_track_Templ).toEqual({"template":"#{num} song"});
		expect(ListViewGenres.published).toEqual({"boolViewActive":false,"intColumns":4});
		expect(ListViewGenres.events).toEqual({"onSetPlaybackList":"","onRequestMedia":"","onListChanged":"","onListSorted":"","onSelectGenre":"","onRequestPlaylists":"","onShowBanner":""});
		expect(ListViewGenres.height).toEqual("100%");
		expect(ListViewGenres.intCurrTrack).toEqual(-1);
		expect(ListViewGenres.intCurrTrackOrigIndex).toEqual(-1);
		expect(ListViewGenres.boolNotifyListChange).toEqual(false);
		expect(ListViewGenres.boolNeedRendered).toEqual(true);
		expect(ListViewGenres.kindName).toEqual("kindListViewGenres");
		//expect(ListViewGenres.kindComponents).toEqual([{"kind":"ctrlListViewHeader","onSearch_Header":"onSearchGenres"},{"name":"content","kind":"Control","layoutKind":"VFlexLayout","className":"list","flex":1,"components":[{"name":"listMedia","kind":"VirtualList","onSetupRow":"listSetupRow","flex":1,"height":"100%","components":[{"name":"itemRow","kind":"Control","layoutKind":"HFlexLayout"}]}]},{"name":"empty","kind":"Control","layoutKind":"VFlexLayout","align":"center","height":"100%","pack":"justify","className":"empty","showing":false,"components":[{"kind":"Spacer","flex":1},{"kind":"Control","className":"content","components":[{"kind":"Image","src":"images/bg_empty_genres.png"},{"content":"There are no songs on your HP TouchPad","className":"heading"},{"content":"You can add music to your HP TouchPad with the HP Play music app, found at www.hpplay.com, or copy music to your HP TouchPad in USB mode."}]},{"kind":"Button","caption":"Add songs","showing":false},{"kind":"Spacer","flex":3}]},{"name":"txtResults"}]);
		expect(ListViewGenres.onSetPlaybackList).toEqual("");
		expect(ListViewGenres.onRequestMedia).toEqual("");
		expect(ListViewGenres.onListChanged).toEqual("");
		expect(ListViewGenres.onListSorted).toEqual("");
		expect(ListViewGenres.onSelectGenre).toEqual("");
		expect(ListViewGenres.onRequestPlaylists).toEqual("");
		expect(ListViewGenres.onShowBanner).toEqual("");
		expect(ListViewGenres.boolViewActive).toEqual(false);
		expect(ListViewGenres.intColumns).toEqual(4);
		expect(ListViewGenres.intJumpRowOffset).toEqual(0);
		expect(ListViewGenres.boolDragHold).toEqual(false);
		expect(ListViewGenres.boolDragItem).toEqual(false);
		expect(ListViewGenres.boolDraggingList).toEqual(false);
		expect(ListViewGenres.dragObjType).toEqual(null);
		expect(ListViewGenres.arItems).toEqual([]);
		expect(ListViewGenres.albumGroup).toEqual(false);
		expect(ListViewGenres.onAddToPlaylist).toEqual("");
		expect(ListViewGenres.onItemDrag).toEqual("");
		expect(ListViewGenres.onItemShowAvatar).toEqual("");
		expect(ListViewGenres.onItemShowAvatarIcon).toEqual("");
		expect(ListViewGenres.controlParentName).toEqual("client");
		expect(ListViewGenres.defaultKind).toEqual("Control");
		expect(ListViewGenres.onclick).toEqual("");
		expect(ListViewGenres.onmousedown).toEqual("");
		expect(ListViewGenres.onmouseup).toEqual("");
		expect(ListViewGenres.style).toEqual("");
		expect(ListViewGenres.className).toEqual("");
		expect(ListViewGenres.container).toEqual(null);
		expect(ListViewGenres.parent).toEqual(null);
		expect(ListViewGenres.generated).toEqual(false);
		expect(ListViewGenres.allowHtml).toEqual(false);
		expect(ListViewGenres.content).toEqual("");
		expect(ListViewGenres.nodeTag).toEqual("div");
		expect(ListViewGenres.node).toEqual(null);
		expect(ListViewGenres.showing).toEqual(true);
		expect(ListViewGenres.prepend).toEqual(false);
		expect(ListViewGenres.wantsEvents).toEqual(true);
	});
	describe('test (itemMousehold)', function(){
	});
	describe('test (create)', function(){
		it('should change strCurrListID',function(){
			ListViewGenres.strCurrListID = "";
			ListViewGenres.create();
			expect(ListViewGenres.strCurrListID).not.toEqual("");
		});
		it('should change local_tracks_Templ',function(){
			ListViewGenres.local_tracks_Templ = "";
			ListViewGenres.create();
			expect(ListViewGenres.local_tracks_Templ).not.toEqual("");
		});
		it('should change local_track_Templ',function(){
			ListViewGenres.local_track_Templ = "";
			ListViewGenres.create();
			expect(ListViewGenres.local_track_Templ).not.toEqual("");
		});
		it('should call inherited',function(){
			spyOn(ListViewGenres,'inherited');
			ListViewGenres.create();
			expect(ListViewGenres.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (ready)', function(){
	});
	describe('test (rendered)', function(){
		it('should call inherited',function(){
			spyOn(ListViewGenres,'inherited');
			ListViewGenres.rendered();
			expect(ListViewGenres.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (renderList)', function(){
		it('should call buildCells',function(){
			spyOn(ListViewGenres,'buildCells');
			ListViewGenres.renderList();
			expect(ListViewGenres.buildCells).toHaveBeenCalled();
		});
		it('should call listQuery',function(){
			spyOn(ListViewGenres,'listQuery');
			ListViewGenres.renderList();
			expect(ListViewGenres.listQuery).toHaveBeenCalled();
		});
	});
	describe('test (refreshList)', function(){
		it('should call renderList',function(){
			spyOn(ListViewGenres,'renderList');
			ListViewGenres.refreshList();
			expect(ListViewGenres.renderList).toHaveBeenCalled();
		});
	});
	describe('test (resizeList)', function(){
	});
	describe('test (listQuery)', function(){
		it('should call doRequestMedia',function(){
			spyOn(ListViewGenres,'doRequestMedia');
			ListViewGenres.listQuery();
			expect(ListViewGenres.doRequestMedia).toHaveBeenCalled();
		});
	});
	describe('test (gotGenres)', function(){
		it('should change arGenres',function(){
			ListViewGenres.gotGenres();
			expect(ListViewGenres.arGenres).toEqual([]);
		});
	});
	describe('test (onSearchGenres)', function(){
		it('should change arGenres',function(){
			ListViewGenres.onSearchGenres(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewGenres.arGenres).toEqual([]);
		});
		it('should call doRequestMedia',function(){
			spyOn(ListViewGenres,'doRequestMedia');
			ListViewGenres.onSearchGenres(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewGenres.doRequestMedia).toHaveBeenCalled();
		});
	});
	describe('test (intColumnsChanged)', function(){
		beforeEach(function(){
			spyOn(ListViewGenres, "buildCells");
			spyOn(ListViewGenres, "listQuery");
		});
		it("should make it dirty", function(){
			ListViewGenres.boolNeedRendered = false;
			ListViewGenres.intColumnsChanged(10);
			expect(ListViewGenres.boolNeedRendered).toBe(true);
		});
		
	});
	describe('test (buildCells)', function(){
	});
	describe('test (listSetupRow)', function(){
	});
	describe('test (onclick_listMedia)', function(){
	});
	describe('test (setItemHighlighted)', function(){
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(ListViewGenres,'_constructor');
			ListViewGenres.base();
			expect(ListViewGenres._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(ListViewGenres,'constructed');
			ListViewGenres.base();
			expect(ListViewGenres.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(ListViewGenres,'_constructor');
			ListViewGenres.ctor();
			expect(ListViewGenres._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(ListViewGenres,'constructed');
			ListViewGenres.ctor();
			expect(ListViewGenres.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doSetPlaybackList)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewGenres,'dispatchIndirectly');
			ListViewGenres.doSetPlaybackList();
			expect(ListViewGenres.dispatchIndirectly).toHaveBeenCalledWith("onSetPlaybackList",{});
		});
	});
	describe('test (doRequestMedia)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewGenres,'dispatchIndirectly');
			ListViewGenres.doRequestMedia();
			expect(ListViewGenres.dispatchIndirectly).toHaveBeenCalledWith("onRequestMedia",{});
		});
	});
	describe('test (doListChanged)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewGenres,'dispatchIndirectly');
			ListViewGenres.doListChanged();
			expect(ListViewGenres.dispatchIndirectly).toHaveBeenCalledWith("onListChanged",{});
		});
	});
	describe('test (doListSorted)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewGenres,'dispatchIndirectly');
			ListViewGenres.doListSorted();
			expect(ListViewGenres.dispatchIndirectly).toHaveBeenCalledWith("onListSorted",{});
		});
	});
	describe('test (doSelectGenre)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewGenres,'dispatchIndirectly');
			ListViewGenres.doSelectGenre();
			expect(ListViewGenres.dispatchIndirectly).toHaveBeenCalledWith("onSelectGenre",{});
		});
	});
	describe('test (doRequestPlaylists)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewGenres,'dispatchIndirectly');
			ListViewGenres.doRequestPlaylists();
			expect(ListViewGenres.dispatchIndirectly).toHaveBeenCalledWith("onRequestPlaylists",{});
		});
	});
	describe('test (doShowBanner)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewGenres,'dispatchIndirectly');
			ListViewGenres.doShowBanner();
			expect(ListViewGenres.dispatchIndirectly).toHaveBeenCalledWith("onShowBanner",{});
		});
	});
	describe('test (getBoolViewActive)', function(){
		it('should call getProperty',function(){
			spyOn(ListViewGenres,'getProperty');
			ListViewGenres.getBoolViewActive();
			expect(ListViewGenres.getProperty).toHaveBeenCalledWith("boolViewActive");
		});
	});
	describe('test (setBoolViewActive)', function(){
		it('should call _setProperty',function(){
			spyOn(ListViewGenres,'_setProperty');
			ListViewGenres.setBoolViewActive();
			expect(ListViewGenres._setProperty).toHaveBeenCalledWith("boolViewActive",undefined,"boolViewActiveChanged");
		});
	});
	describe('test (getIntColumns)', function(){
		it('should call getProperty',function(){
			spyOn(ListViewGenres,'getProperty');
			ListViewGenres.getIntColumns();
			expect(ListViewGenres.getProperty).toHaveBeenCalledWith("intColumns");
		});
	});
	describe('test (setIntColumns)', function(){
		it('should call _setProperty',function(){
			spyOn(ListViewGenres,'_setProperty');
			ListViewGenres.setIntColumns();
			expect(ListViewGenres._setProperty).toHaveBeenCalledWith("intColumns",undefined,"intColumnsChanged");
		});
	});
	describe('test (callAddToPlaylist)', function(){
		it('should call doAddToPlaylist',function(){
			spyOn(ListViewGenres,'doAddToPlaylist');
			ListViewGenres.callAddToPlaylist();
			expect(ListViewGenres.doAddToPlaylist).toHaveBeenCalledWith({"strMediaType":"song","arTracks":[{}]});
		});
	});
	describe('test (songMousehold)', function(){
	describe('testing (songMousehold) with params [{"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			ListViewGenres.songMousehold({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewGenres.dragObjType).toEqual("song");
		});
		it('should change arItems',function(){
			ListViewGenres.songMousehold({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewGenres.arItems).toEqual(undefined);
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewGenres,'itemMousehold');
			ListViewGenres.songMousehold({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewGenres.itemMousehold).toHaveBeenCalledWith({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (albumMousehold)', function(){
	describe('testing (albumMousehold) with params [{"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			ListViewGenres.albumMousehold({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewGenres.dragObjType).toEqual("album");
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewGenres,'itemMousehold');
			ListViewGenres.albumMousehold({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewGenres.itemMousehold).toHaveBeenCalledWith({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (albumGroupMousehold)', function(){
	describe('testing (albumGroupMousehold) with params [{"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change albumGroup',function(){
			ListViewGenres.albumGroupMousehold({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewGenres.albumGroup).toEqual(true);
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewGenres,'itemMousehold');
			ListViewGenres.albumGroupMousehold({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewGenres.itemMousehold).toHaveBeenCalledWith({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (artistMousehold)', function(){
	describe('testing (artistMousehold) with params [{"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			ListViewGenres.artistMousehold({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewGenres.dragObjType).toEqual("artist");
		});
		it('should call itemMousehold',function(){
			spyOn(ListViewGenres,'itemMousehold');
			ListViewGenres.artistMousehold({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
			expect(ListViewGenres.itemMousehold).toHaveBeenCalledWith({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (genreMousehold)', function(){
		describe('testing (genreMousehold) with params [{"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null}]', function(){
			it('should change dragObjType',function(){
				ListViewGenres.genreMousehold({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
				expect(ListViewGenres.dragObjType).toEqual("genre");
			});
			
			it('should call itemMousehold',function(){
				spyOn(ListViewGenres,'itemMousehold');
				ListViewGenres.genreMousehold({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
				expect(ListViewGenres.itemMousehold).toHaveBeenCalledWith({"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null});
			});
		});
	});
	describe('test (itemMouserelease)', function(){
		it('should change dragObjType',function(){
			ListViewGenres.itemMouserelease();
			expect(ListViewGenres.dragObjType).toEqual(null);
		});
		it('should call setItemHighlighted',function(){
			spyOn(ListViewGenres,'setItemHighlighted');
			ListViewGenres.itemMouserelease();
			expect(ListViewGenres.setItemHighlighted).toHaveBeenCalledWith(false);
		});
		it('should call doItemShowAvatar',function(){
			spyOn(ListViewGenres,'doItemShowAvatar');
			ListViewGenres.itemMouserelease();
			expect(ListViewGenres.doItemShowAvatar).toHaveBeenCalledWith(false);
		});
	});
	describe('test (itemDragStart)', function(){
	});
	describe('test (onDrag_itemMedia)', function(){
	});
	describe('test (itemDragFinish)', function(){
	});
	describe('test (onDrop_itemMedia)', function(){
	});
	describe('test (getDragIndex)', function(){
	describe('testing (getDragIndex) with params [{"intColumnIndex":0},{"rowIndex":0,"dispatchTarget":null}]', function(){
	});
	});
	describe('test (doAddToPlaylist)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewGenres,'dispatchIndirectly');
			ListViewGenres.doAddToPlaylist();
			expect(ListViewGenres.dispatchIndirectly).toHaveBeenCalledWith("onAddToPlaylist",{});
		});
	});
	describe('test (doItemDrag)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewGenres,'dispatchIndirectly');
			ListViewGenres.doItemDrag();
			expect(ListViewGenres.dispatchIndirectly).toHaveBeenCalledWith("onItemDrag",{});
		});
	});
	describe('test (doItemShowAvatar)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewGenres,'dispatchIndirectly');
			ListViewGenres.doItemShowAvatar();
			expect(ListViewGenres.dispatchIndirectly).toHaveBeenCalledWith("onItemShowAvatar",{});
		});
	});
	describe('test (doItemShowAvatarIcon)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ListViewGenres,'dispatchIndirectly');
			ListViewGenres.doItemShowAvatarIcon();
			expect(ListViewGenres.dispatchIndirectly).toHaveBeenCalledWith("onItemShowAvatarIcon",{});
		});
	});
	describe('test (highlightTrack)', function(){
	});
	describe('test (layoutKind)', function(){
		it('should call _constructor',function(){
			spyOn(ListViewGenres,'_constructor');
			ListViewGenres.layoutKind();
			expect(ListViewGenres._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(ListViewGenres,'constructed');
			ListViewGenres.layoutKind();
			expect(ListViewGenres.constructed).toHaveBeenCalled();
		});
	});
	describe('test (destroy)', function(){
		it('should call unregisterEvents',function(){
			spyOn(ListViewGenres,'unregisterEvents');
			ListViewGenres.destroy();
			expect(ListViewGenres.unregisterEvents).toHaveBeenCalled();
		});
		
		it('should call inherited',function(){
			spyOn(ListViewGenres,'inherited');
			ListViewGenres.destroy();
			expect(ListViewGenres.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(ListViewGenres,'inherited');
			ListViewGenres.importProps();
			expect(ListViewGenres.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(ListViewGenres,'createChrome');
			ListViewGenres.initComponents();
			expect(ListViewGenres.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(ListViewGenres,'inherited');
			ListViewGenres.initComponents();
			expect(ListViewGenres.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(ListViewGenres,'inherited');
			ListViewGenres.createComponents();
			expect(ListViewGenres.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(ListViewGenres,'discoverControlParent');
			ListViewGenres.createComponents();
			expect(ListViewGenres.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(ListViewGenres,'createComponents');
			ListViewGenres.createChrome();
			expect(ListViewGenres.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});