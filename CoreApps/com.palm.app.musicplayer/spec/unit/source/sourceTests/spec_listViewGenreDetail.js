/*globals enyo, $L, event, Utilities, window, LibraryTools */

//var creator = function(){
//				return enyo.create({kind: "kindListViewGenreDetail"});
//			}
//var objName = "kindListViewGenreDetail";
//
//var before = function (obj, callbacks) {
//	
//	obj.itemMousehold = function(){};
//	
//    
//  obj.$.ctrlListViewHeader = {}
//  obj.$.ctrlListViewHeader.setIntHeaderCount = function(){}
//    obj.$.listMedia = {};
//    obj.$.listMedia.refresh = function(){};
//    obj.$.listMedia.resized = function(){};
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
//		 "mousehold_itemmedia" : event
//		 
//};
//
//specOut(creator, objName, before, params);

describe('kindListViewGenreDetail tests', function(){
	var creator = function (){
				return enyo.create({kind: "kindListViewGenreDetail"});
			};
	var kindListViewGenreDetail;
	beforeEach(function(){
		kindListViewGenreDetail = creator()
		var before = function (obj, callbacks) {
	
	obj.itemMousehold = function(){};
	
    
  obj.$.ctrlListViewHeader = {}
  obj.$.ctrlListViewHeader.setIntHeaderCount = function(){}
    obj.$.listMedia = {};
    obj.$.listMedia.refresh = function(){};
    obj.$.listMedia.resized = function(){};
    obj.arSongs = getJson("mock_songArr");
	};
		var callback = function(one, fun){return fun;};
		before(kindListViewGenreDetail,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof kindListViewGenreDetail.controls).toBe('object');
		expect(typeof kindListViewGenreDetail.children).toBe('object');
		expect(kindListViewGenreDetail._componentNameMap).toEqual({"ctrlListViewHeader":2,"control":2,"toolbar":2,"grabButton":2});
		expect(typeof kindListViewGenreDetail.$).toBe('object');
		expect(kindListViewGenreDetail.domStyles).toEqual({});
		expect(kindListViewGenreDetail.domAttributes).toEqual({"className":"playlist","id":"kindListViewGenreDetail"});
		expect(kindListViewGenreDetail.kind).toEqual("kindListViewGenreDetail");
		expect(typeof kindListViewGenreDetail.owner).toBe('object');
		expect(kindListViewGenreDetail.name).toEqual("kindListViewGenreDetail");
		expect(kindListViewGenreDetail.id).toEqual("kindListViewGenreDetail");
		expect(kindListViewGenreDetail.controlParent).toEqual(undefined);
		expect(kindListViewGenreDetail.intCurrTrack).toEqual(-1);
		expect(kindListViewGenreDetail.intCurrTrackOrigIndex).toEqual(-1);
		expect(kindListViewGenreDetail.strCurrTrackID).toEqual("");
		expect(kindListViewGenreDetail.boolSortAsc).toEqual(true);
		expect(kindListViewGenreDetail.strSortMode).toEqual("artist");
		//expect(kindListViewGenreDetail.strCurrListID).toEqual("457FF48EB884-04991C-9BDBC2-0038F5-E58BF546BF34B8AA59");
		expect(kindListViewGenreDetail.listViewDetailType).toEqual("genre");
		expect(kindListViewGenreDetail.className).toEqual("playlist");
		expect(kindListViewGenreDetail.kindName).toEqual("kindListViewGenreDetail");
		//expect(kindListViewGenreDetail.kindComponents).toEqual([ { kind : 'ctrlListViewHeader', onSearch_Header : 'onSearchSongs', boolShowSearchBtn : false }, { kind : 'Control', layoutKind : 'VFlexLayout', className : 'list', flex : 1, components : [ { name : 'header', kind : 'Control', layoutKind : 'HFlexLayout', className : 'divider first', align : 'center', components : [ { name : 'lblSortSong', content : 'Song', flex : 2, className : 'column', onclick : 'onclick_Sort' }, { name : 'lblSortArtist', content : 'Artist', flex : 1, className : 'column sorted down', onclick : 'onclick_Sort' }, { name : 'lblSortAlbum', content : 'Album', flex : 1, className : 'column', onclick : 'onclick_Sort' } ] }, { name : 'listMedia', kind : 'VirtualList', onQuery : 'listQuery', onSetupRow : 'listSetupRow', className : 'listMedia', flex : 1, components : [ { name : 'itemMedia', kind : 'draggableSong', className : 'song', layoutKind : 'HFlexLayout', onclick : 'onclick_listMedia', components : [ { name : 'songTitle', flex : 2, className : 'column title' }, { name : 'songArtist', flex : 1, className : 'column artist' }, { name : 'songAlbum', flex : 1, className : 'column album' } ] } ] }, { kind : 'Toolbar', className : 'enyo-toolbar-light', slidingHandler : true, components : [ { kind : 'GrabButton' } ] } ] } ] to equal [ { kind : 'ctrlListViewHeader', onSearch_Header : 'onSearchSongs', boolShowSearchBtn : false }, { kind : 'Control', layoutKind : 'VFlexLayout', className : 'list', flex : 1, components : [ { name : 'header', kind : 'Control', layoutKind : 'HFlexLayout', className : 'divider first', align : 'center', components : [ { content : ' ', className : 'column number' }, { name : 'lblSortSong', content : 'Song', flex : 2, className : 'column', onclick : 'onclick_Sort' }, { name : 'lblSortArtist', content : 'Artist', flex : 1, className : 'column sorted down', onclick : 'onclick_Sort' }, { name : 'lblSortAlbum', content : 'Album', flex : 1, className : 'column', onclick : 'onclick_Sort' } ] }, { name : 'listMedia', kind : 'VirtualList', onQuery : 'listQuery', onSetupRow : 'listSetupRow', className : 'listMedia', flex : 1, components : [ { name : 'itemMedia', kind : 'draggableSong', className : 'song', layoutKind : 'HFlexLayout', onclick : 'onclick_listMedia', components : [ { name : 'songNumber', className : 'column number', content : '00' }, { name : 'songTitle', flex : 2, className : 'column title' }, { name : 'songArtist', flex : 1, className : 'column artist' }, { name : 'songAlbum', flex : 1, className : 'column album' } ] } ] }, { kind : 'Toolbar', className : 'enyo-toolbar-light', slidingHandler : true, components : [ { kind : 'GrabButton' } ] } ] } ]);
		expect(kindListViewGenreDetail.published).toEqual({"boolViewActive":false,"boolListRendered":false});
		expect(kindListViewGenreDetail.events).toEqual({"onSetPlaybackList":"","onRequestMedia":"","onRequestCurrTrack":"","onListChanged":"","onListSorted":""});
		expect(kindListViewGenreDetail.objCurr).toEqual({});
		expect(kindListViewGenreDetail.onSetPlaybackList).toEqual("");
		expect(kindListViewGenreDetail.onRequestMedia).toEqual("");
		expect(kindListViewGenreDetail.onRequestCurrTrack).toEqual("");
		expect(kindListViewGenreDetail.onListChanged).toEqual("");
		expect(kindListViewGenreDetail.onListSorted).toEqual("");
		expect(kindListViewGenreDetail.boolViewActive).toEqual(false);
		expect(kindListViewGenreDetail.boolListRendered).toEqual(false);
		expect(kindListViewGenreDetail.intJumpRowOffset).toEqual(0);
		expect(kindListViewGenreDetail.boolDragHold).toEqual(false);
		expect(kindListViewGenreDetail.boolDragItem).toEqual(false);
		expect(kindListViewGenreDetail.boolDraggingList).toEqual(false);
		expect(kindListViewGenreDetail.dragObjType).toEqual(null);
		expect(kindListViewGenreDetail.arItems).toEqual([]);
		expect(kindListViewGenreDetail.albumGroup).toEqual(false);
		expect(kindListViewGenreDetail.onAddToPlaylist).toEqual("");
		expect(kindListViewGenreDetail.onItemDrag).toEqual("");
		expect(kindListViewGenreDetail.onItemShowAvatar).toEqual("");
		expect(kindListViewGenreDetail.onItemShowAvatarIcon).toEqual("");
		expect(kindListViewGenreDetail.controlParentName).toEqual("client");
		expect(kindListViewGenreDetail.defaultKind).toEqual("Control");
		expect(kindListViewGenreDetail.onclick).toEqual("");
		expect(kindListViewGenreDetail.onmousedown).toEqual("");
		expect(kindListViewGenreDetail.onmouseup).toEqual("");
		//expect(kindListViewGenreDetail.layoutKind).toEqual("");
		expect(kindListViewGenreDetail.style).toEqual("");
		expect(kindListViewGenreDetail.container).toEqual(null);
		expect(kindListViewGenreDetail.parent).toEqual(null);
		expect(kindListViewGenreDetail.generated).toEqual(false);
		expect(kindListViewGenreDetail.allowHtml).toEqual(false);
		expect(kindListViewGenreDetail.content).toEqual("");
		expect(kindListViewGenreDetail.nodeTag).toEqual("div");
		expect(kindListViewGenreDetail.node).toEqual(null);
		expect(kindListViewGenreDetail.showing).toEqual(true);
		expect(kindListViewGenreDetail.prepend).toEqual(false);
		expect(kindListViewGenreDetail.wantsEvents).toEqual(true);
	});
	describe('test (itemMousehold)', function(){
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewGenreDetail,'inherited');
			kindListViewGenreDetail.create();
			expect(kindListViewGenreDetail.inherited).toHaveBeenCalledWith({});
		});
		it("should set this.listViewDetailType", function(){
			kindListViewGenreDetail.listViewDetailType = "blah";
			kindListViewGenreDetail.create();
			expect(kindListViewGenreDetail.listViewDetailType).toBe("genre");
		});
		it("should set this.strSortMode", function(){
			kindListViewGenreDetail.strSortMode = "blah";
			kindListViewGenreDetail.create();
			expect(kindListViewGenreDetail.strSortMode).toBe("artist");
		});
	});
	describe('test (ready)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewGenreDetail,'inherited');
			kindListViewGenreDetail.ready();
			expect(kindListViewGenreDetail.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (rendered)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewGenreDetail,'inherited');
			kindListViewGenreDetail.rendered();
			expect(kindListViewGenreDetail.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (listSetupRow)', function(){
		
		it("should set songTitle", function(){
			kindListViewGenreDetail.$.songTitle.content = "asg";
			kindListViewGenreDetail.listSetupRow(this, 0);
			expect(kindListViewGenreDetail.$.songTitle.content).toBe(kindListViewGenreDetail.arSongs[0].title);
		});
		it("should set songArtist", function(){
			kindListViewGenreDetail.$.songArtist.content = "asg";
			kindListViewGenreDetail.listSetupRow(this, 0);
			expect(kindListViewGenreDetail.$.songArtist.content).toBe(kindListViewGenreDetail.arSongs[0].artist);
		});
		it("should set songArtist", function(){
			kindListViewGenreDetail.$.songAlbum.content = "asg";
			kindListViewGenreDetail.listSetupRow(this, 0);
			expect(kindListViewGenreDetail.$.songAlbum.content).toBe(kindListViewGenreDetail.arSongs[0].album);
		});
		it("should set setClassName", function(){
			spyOn(kindListViewGenreDetail.$.itemMedia, "setClassName");
			kindListViewGenreDetail.listSetupRow(this, 0);
			expect(kindListViewGenreDetail.$.itemMedia.setClassName).toHaveBeenCalledWith("enyo-item song");
		});
		it("should set setClassName on match", function(){
			kindListViewGenreDetail.strCurrTrackID = kindListViewGenreDetail.arSongs[0]._id;
			spyOn(kindListViewGenreDetail.$.itemMedia, "setClassName");
			kindListViewGenreDetail.listSetupRow(this, 0);
			expect(kindListViewGenreDetail.$.itemMedia.setClassName).toHaveBeenCalledWith("enyo-item song playing");
		});
		
		it("should not add class odd", function(){
			spyOn(kindListViewGenreDetail.$.itemMedia, "addClass");
			kindListViewGenreDetail.listSetupRow(this, 0);
			expect(kindListViewGenreDetail.$.itemMedia.addClass).not.toHaveBeenCalledWith("odd");
		});
		it("should add class odd", function(){
			spyOn(kindListViewGenreDetail.$.itemMedia, "addClass");
			kindListViewGenreDetail.listSetupRow(this, 1);
			expect(kindListViewGenreDetail.$.itemMedia.addClass).toHaveBeenCalledWith("odd");
		});
		
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(kindListViewGenreDetail,'_constructor');
			kindListViewGenreDetail.base();
			expect(kindListViewGenreDetail._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(kindListViewGenreDetail,'constructed');
			kindListViewGenreDetail.base();
			expect(kindListViewGenreDetail.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(kindListViewGenreDetail,'_constructor');
			kindListViewGenreDetail.ctor();
			expect(kindListViewGenreDetail._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(kindListViewGenreDetail,'constructed');
			kindListViewGenreDetail.ctor();
			expect(kindListViewGenreDetail.constructed).toHaveBeenCalled();
		});
	});
	describe('test (renderList)', function(){
		it('should change arSongs',function(){
		});
	});
	
	describe('test (refreshList)', function(){
		it('should call doRequestCurrTrack',function(){
			spyOn(kindListViewGenreDetail,'doRequestCurrTrack');
			kindListViewGenreDetail.refreshList();
			expect(kindListViewGenreDetail.doRequestCurrTrack).toHaveBeenCalled();
		});
	});
	describe('test (resizeList)', function(){
	});
	describe('test (setStrHeaderTitle)', function(){
	});
	describe('test (gotSongs)', function(){
	});
	describe('test (onclick_Sort)', function(){
	});
	describe('test (updateUISongInfo)', function(){
	});
	describe('test (resetMusicListHeaders)', function(){
	});
	describe('test (onclick_listMedia)', function(){
		it('should call doSetPlaybackList',function(){
			//TODO: spec out better
//			spyOn(kindListViewGenreDetail,'doSetPlaybackList');
//			kindListViewGenreDetail.onclick_listMedia(null,{"rowIndex":0,"dispatchTarget":null});
//			expect(kindListViewGenreDetail.doSetPlaybackList).toHaveBeenCalledWith({"arSetPlaybackList":[],"intStartTrackIndex":0,"intStartTrackTime":0,"strOriginListID":"457FF48EB884-04991C-9BDBC2-0038F5-E58BF546BF34B8AA59","strListType":"Genre","strListSubText":""});
		});
	});
	describe('test (doSetPlaybackList)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewGenreDetail,'dispatchIndirectly');
			kindListViewGenreDetail.doSetPlaybackList();
			expect(kindListViewGenreDetail.dispatchIndirectly).toHaveBeenCalledWith("onSetPlaybackList",{});
		});
	});
	describe('test (doRequestMedia)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewGenreDetail,'dispatchIndirectly');
			kindListViewGenreDetail.doRequestMedia();
			expect(kindListViewGenreDetail.dispatchIndirectly).toHaveBeenCalledWith("onRequestMedia",{});
		});
	});
	describe('test (doRequestCurrTrack)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewGenreDetail,'dispatchIndirectly');
			kindListViewGenreDetail.doRequestCurrTrack();
			expect(kindListViewGenreDetail.dispatchIndirectly).toHaveBeenCalledWith("onRequestCurrTrack",{});
		});
	});
	describe('test (doListChanged)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewGenreDetail,'dispatchIndirectly');
			kindListViewGenreDetail.doListChanged();
			expect(kindListViewGenreDetail.dispatchIndirectly).toHaveBeenCalledWith("onListChanged",{});
		});
	});
	describe('test (doListSorted)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewGenreDetail,'dispatchIndirectly');
			kindListViewGenreDetail.doListSorted();
			expect(kindListViewGenreDetail.dispatchIndirectly).toHaveBeenCalledWith("onListSorted",{});
		});
	});
	describe('test (getBoolViewActive)', function(){
		it('should call getProperty',function(){
			spyOn(kindListViewGenreDetail,'getProperty');
			kindListViewGenreDetail.getBoolViewActive();
			expect(kindListViewGenreDetail.getProperty).toHaveBeenCalledWith("boolViewActive");
		});
	});
	describe('test (setBoolViewActive)', function(){
		it('should call _setProperty',function(){
			spyOn(kindListViewGenreDetail,'_setProperty');
			kindListViewGenreDetail.setBoolViewActive();
			expect(kindListViewGenreDetail._setProperty).toHaveBeenCalledWith("boolViewActive",undefined,"boolViewActiveChanged");
		});
	});
	describe('test (getBoolListRendered)', function(){
		it('should call getProperty',function(){
			spyOn(kindListViewGenreDetail,'getProperty');
			kindListViewGenreDetail.getBoolListRendered();
			expect(kindListViewGenreDetail.getProperty).toHaveBeenCalledWith("boolListRendered");
		});
	});
	describe('test (setBoolListRendered)', function(){
		it('should call _setProperty',function(){
			spyOn(kindListViewGenreDetail,'_setProperty');
			kindListViewGenreDetail.setBoolListRendered();
			expect(kindListViewGenreDetail._setProperty).toHaveBeenCalledWith("boolListRendered",undefined,"boolListRenderedChanged");
		});
	});
	describe('test (callAddToPlaylist)', function(){
		it('should call doAddToPlaylist',function(){
			spyOn(kindListViewGenreDetail,'doAddToPlaylist');
			kindListViewGenreDetail.callAddToPlaylist();
			expect(kindListViewGenreDetail.doAddToPlaylist).toHaveBeenCalledWith({"strMediaType":"song","arTracks":[{}]});
		});
	});
	describe('test (songMousehold)', function(){
	describe('testing (songMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			kindListViewGenreDetail.songMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewGenreDetail.dragObjType).toEqual("song");
		});
		it('should change arItems',function(){
			
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewGenreDetail,'itemMousehold');
			kindListViewGenreDetail.songMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewGenreDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (albumMousehold)', function(){
	describe('testing (albumMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			kindListViewGenreDetail.albumMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewGenreDetail.dragObjType).toEqual("album");
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewGenreDetail,'itemMousehold');
			kindListViewGenreDetail.albumMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewGenreDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (albumGroupMousehold)', function(){
	describe('testing (albumGroupMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change arItems',function(){
			kindListViewGenreDetail.albumGroupMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewGenreDetail.arItems).toEqual(undefined);
		});
		it('should change albumGroup',function(){
			kindListViewGenreDetail.albumGroupMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewGenreDetail.albumGroup).toEqual(true);
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewGenreDetail,'itemMousehold');
			kindListViewGenreDetail.albumGroupMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewGenreDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (artistMousehold)', function(){
	describe('testing (artistMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			kindListViewGenreDetail.artistMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewGenreDetail.dragObjType).toEqual("artist");
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewGenreDetail,'itemMousehold');
			kindListViewGenreDetail.artistMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewGenreDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (genreMousehold)', function(){
	describe('testing (genreMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			kindListViewGenreDetail.genreMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewGenreDetail.dragObjType).toEqual("genre");
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewGenreDetail,'itemMousehold');
			kindListViewGenreDetail.genreMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewGenreDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (itemMouserelease)', function(){
		it('should change dragObjType',function(){
			kindListViewGenreDetail.itemMouserelease();
			expect(kindListViewGenreDetail.dragObjType).toEqual(null);
		});
		it('should call setItemHighlighted',function(){
			spyOn(kindListViewGenreDetail,'setItemHighlighted');
			kindListViewGenreDetail.itemMouserelease();
			expect(kindListViewGenreDetail.setItemHighlighted).toHaveBeenCalledWith(false);
		});
		it('should call doItemShowAvatar',function(){
			spyOn(kindListViewGenreDetail,'doItemShowAvatar');
			kindListViewGenreDetail.itemMouserelease();
			expect(kindListViewGenreDetail.doItemShowAvatar).toHaveBeenCalledWith(false);
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
			spyOn(kindListViewGenreDetail,'dispatchIndirectly');
			kindListViewGenreDetail.doAddToPlaylist();
			expect(kindListViewGenreDetail.dispatchIndirectly).toHaveBeenCalledWith("onAddToPlaylist",{});
		});
	});
	describe('test (doItemDrag)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewGenreDetail,'dispatchIndirectly');
			kindListViewGenreDetail.doItemDrag();
			expect(kindListViewGenreDetail.dispatchIndirectly).toHaveBeenCalledWith("onItemDrag",{});
		});
	});
	describe('test (doItemShowAvatar)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewGenreDetail,'dispatchIndirectly');
			kindListViewGenreDetail.doItemShowAvatar();
			expect(kindListViewGenreDetail.dispatchIndirectly).toHaveBeenCalledWith("onItemShowAvatar",{});
		});
	});
	describe('test (doItemShowAvatarIcon)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewGenreDetail,'dispatchIndirectly');
			kindListViewGenreDetail.doItemShowAvatarIcon();
			expect(kindListViewGenreDetail.dispatchIndirectly).toHaveBeenCalledWith("onItemShowAvatarIcon",{});
		});
	});
	describe('test (highlightTrack)', function(){
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewGenreDetail,'inherited');
			kindListViewGenreDetail.importProps();
			expect(kindListViewGenreDetail.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(kindListViewGenreDetail,'createChrome');
			kindListViewGenreDetail.initComponents();
			expect(kindListViewGenreDetail.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(kindListViewGenreDetail,'inherited');
			kindListViewGenreDetail.initComponents();
			expect(kindListViewGenreDetail.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewGenreDetail,'inherited');
			kindListViewGenreDetail.createComponents();
			expect(kindListViewGenreDetail.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(kindListViewGenreDetail,'discoverControlParent');
			kindListViewGenreDetail.createComponents();
			expect(kindListViewGenreDetail.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(kindListViewGenreDetail,'createComponents');
			kindListViewGenreDetail.createChrome();
			expect(kindListViewGenreDetail.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});