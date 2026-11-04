/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return enyo.create({kind: "kindListViewAlbumDetail"});
//			}
//var objName = "kindListViewAlbumDetail";
//
//var before = function (obj, callbacks) {
//	
//	obj.itemMousehold = function(){};
//  obj.$.ctrlListViewHeader = {}
//  obj.$.ctrlListViewHeader.setIntHeaderCount = function(){}
//    obj.$.listMedia = {};
//    obj.$.listMedia.refresh = function(){};
//	}
//var event = [[null, {rowIndex:0}]];
//var params = {
//		 "dragObjType" : event,
//		 "songMousehold" : event,
//		 "albumMousehold" : event,
//		 "albumGroupMousehold" : event,
//		 "artistMousehold" : event,
//		 "genreMousehold" : event,
//		 "onclick_listmedia" : event,
//		 "onclick_listmedia" : event
//		 
//};
//
//specOut(creator, objName, before, params);

describe('kindListViewAlbumDetail tests', function(){
	var creator = function (){
				return enyo.create({kind: "kindListViewAlbumDetail"});
			};
	var kindListViewAlbumDetail = creator();
	beforeEach(function(){
		var before = function (obj, callbacks) {
	
	obj.itemMousehold = function(){};
	obj.$.ctrlListViewHeader = {};
	obj.$.ctrlListViewHeader.setIntHeaderCount = function(){}
    obj.$.listMedia = {};
    obj.$.listMedia.refresh = function(){};
	};
		var callback = function(one, fun){return fun;};
		before(kindListViewAlbumDetail,callback);
		kindListViewAlbumDetail.arSongs = getJson("mock_songArr");
		
		
	});
	it('should correctly set up object', function(){
		expect(typeof kindListViewAlbumDetail.controls).toBe('object');
		expect(typeof kindListViewAlbumDetail.children).toBe('object');
		expect(kindListViewAlbumDetail._componentNameMap).toEqual({"ctrlListViewHeader":2,"control":5,"toolbar":2,"grabButton":2});
		expect(typeof kindListViewAlbumDetail.$).toBe('object');
		expect(kindListViewAlbumDetail.domStyles).toEqual({});
		expect(kindListViewAlbumDetail.domAttributes).toEqual({"className":"DetailView","id":"kindListViewAlbumDetail"});
		expect(kindListViewAlbumDetail.kind).toEqual("kindListViewAlbumDetail");
		expect(typeof kindListViewAlbumDetail.owner).toBe('object');
		expect(kindListViewAlbumDetail.name).toEqual("kindListViewAlbumDetail");
		expect(kindListViewAlbumDetail.id).toEqual("kindListViewAlbumDetail");
		expect(kindListViewAlbumDetail.controlParent).toEqual(undefined);
		expect(kindListViewAlbumDetail.intCurrTrack).toEqual(-1);
		expect(kindListViewAlbumDetail.intCurrTrackOrigIndex).toEqual(-1);
		expect(kindListViewAlbumDetail.strCurrTrackID).toEqual("");
		expect(kindListViewAlbumDetail.boolSortAsc).toEqual(true);
		expect(kindListViewAlbumDetail.strSortMode).toEqual("album");
		//expect(kindListViewAlbumDetail.strCurrListID).toEqual("3A04D629FCC7-41EDB6-95A277-9D4DFF-3F9F1360EE180247D4");
		expect(kindListViewAlbumDetail.listViewDetailType).toEqual("album");
		//expect(kindListViewAlbumDetail.local_tracks_Templ).toEqual({"template":"(#{num} songs)"});
		//expect(kindListViewAlbumDetail.local_track_Templ).toEqual({"template":"(#{num} song)"});
		expect(kindListViewAlbumDetail.className).toEqual("DetailView");
		expect(kindListViewAlbumDetail.kindName).toEqual("kindListViewAlbumDetail");
		//expect(kindListViewAlbumDetail.kindComponents).toEqual([ { kind : 'ctrlListViewHeader', onSearch_Header : 'onSearchSongs', boolShowSearchBtn : false }, { kind : 'Control', layoutKind : 'VFlexLayout', className : 'list', flex : 1, components : [ { name : 'listMedia', kind : 'VirtualList', onQuery : 'listQuery', onSetupRow : 'listSetupRow', className : 'listMedia', flex : 1, components : [ { name : 'headerAlbum', kind : 'draggableAlbum', className : 'header album first', showing : false, components : [ { kind : 'Control', className : 'content', align : 'center', layoutKind : 'HFlexLayout', components : [ { className : 'cover', onclick : 'onclick_playArtist', components : [ { name : 'imgThumb', kind : 'Image', className : 'thumb' } ] }, { kind : 'Control', components : [ { name : 'albumName', className : 'name', content : 'Siamese Dream' }, { name : 'albumCounts', className : 'count', content : '10 songs' } ] } ] } ] }, { name : 'header', kind : 'HFlexBox', className : 'divider first', showing : false, components : [ { name : 'lblSortPos', content : '#', className : 'column number sorted', onclick : 'onclick_Sort' }, { name : 'lblSortSong', content : 'Song', flex : 1, className : 'column', onclick : 'onclick_Sort' } ] }, { name : 'itemMedia', kind : 'draggableSong', layoutKind : 'HFlexLayout', align : 'center', className : 'song', onclick : 'onclick_listMedia', components : [ { name : 'songTrack', className : 'column number' }, { name : 'songTitle', flex : 1, className : 'column title' } ] } ] } ] }, { kind : 'Toolbar', className : 'enyo-toolbar-light', slidingHandler : true, components : [ { kind : 'GrabButton' } ] } ]);
		expect(kindListViewAlbumDetail.published).toEqual({"boolViewActive":false,"boolListRendered":false});
		expect(kindListViewAlbumDetail.events).toEqual({"onSetPlaybackList":"","onRequestMedia":"","onRequestCurrTrack":"","onListChanged":"","onListSorted":""});
		expect(kindListViewAlbumDetail.objCurr).toEqual({});
		expect(kindListViewAlbumDetail.onSetPlaybackList).toEqual("");
		expect(kindListViewAlbumDetail.onRequestMedia).toEqual("");
		expect(kindListViewAlbumDetail.onRequestCurrTrack).toEqual("");
		expect(kindListViewAlbumDetail.onListChanged).toEqual("");
		expect(kindListViewAlbumDetail.onListSorted).toEqual("");
		expect(kindListViewAlbumDetail.boolViewActive).toEqual(false);
		expect(kindListViewAlbumDetail.boolListRendered).toEqual(false);
		expect(kindListViewAlbumDetail.intJumpRowOffset).toEqual(0);
		expect(kindListViewAlbumDetail.boolDragHold).toEqual(false);
		expect(kindListViewAlbumDetail.boolDragItem).toEqual(false);
		expect(kindListViewAlbumDetail.boolDraggingList).toEqual(false);
		expect(kindListViewAlbumDetail.dragObjType).toEqual(null);
		expect(kindListViewAlbumDetail.arItems).toEqual([]);
		expect(kindListViewAlbumDetail.albumGroup).toEqual(false);
		expect(kindListViewAlbumDetail.onAddToPlaylist).toEqual("");
		expect(kindListViewAlbumDetail.onItemDrag).toEqual("");
		expect(kindListViewAlbumDetail.onItemShowAvatar).toEqual("");
		expect(kindListViewAlbumDetail.onItemShowAvatarIcon).toEqual("");
		expect(kindListViewAlbumDetail.controlParentName).toEqual("client");
		expect(kindListViewAlbumDetail.defaultKind).toEqual("Control");
		expect(kindListViewAlbumDetail.onclick).toEqual("");
		expect(kindListViewAlbumDetail.onmousedown).toEqual("");
		expect(kindListViewAlbumDetail.onmouseup).toEqual("");
		//expect(kindListViewAlbumDetail.layoutKind).toEqual("");
		expect(kindListViewAlbumDetail.style).toEqual("");
		expect(kindListViewAlbumDetail.container).toEqual(null);
		expect(kindListViewAlbumDetail.parent).toEqual(null);
		expect(kindListViewAlbumDetail.generated).toEqual(false);
		expect(kindListViewAlbumDetail.allowHtml).toEqual(false);
		expect(kindListViewAlbumDetail.content).toEqual("");
		expect(kindListViewAlbumDetail.nodeTag).toEqual("div");
		expect(kindListViewAlbumDetail.node).toEqual(null);
		expect(kindListViewAlbumDetail.showing).toEqual(true);
		expect(kindListViewAlbumDetail.prepend).toEqual(false);
		expect(kindListViewAlbumDetail.wantsEvents).toEqual(true);
	});
	describe('test (itemMousehold)', function(){
	});
	describe('test (create)', function(){
		it("should set this.listViewDetailType", function(){
			kindListViewAlbumDetail.listViewDetailType = "blah";
			kindListViewAlbumDetail.create();
			expect(kindListViewAlbumDetail.listViewDetailType).toBe("album");
		});
		
		it('should change local_tracks_Templ',function(){
			kindListViewAlbumDetail.create();
			expect(kindListViewAlbumDetail.local_tracks_Templ).not.toBe(undefined);
		});
		it('should change local_track_Templ',function(){
			kindListViewAlbumDetail.create();
			expect(kindListViewAlbumDetail.local_track_Templ).not.toBe(undefined);
		});
		it('should call inherited',function(){
			spyOn(kindListViewAlbumDetail,'inherited');
			kindListViewAlbumDetail.create();
			expect(kindListViewAlbumDetail.inherited).toHaveBeenCalledWith({});
		});
		it("should setIntHeaderCount", function(){
			spyOn(kindListViewAlbumDetail.$.ctrlListViewHeader,"setIntHeaderCount");
			kindListViewAlbumDetail.create();
			expect(kindListViewAlbumDetail.$.ctrlListViewHeader.setIntHeaderCount).toHaveBeenCalledWith("");
		});
		
	});
	describe('test (ready)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewAlbumDetail,'inherited');
			kindListViewAlbumDetail.ready();
			expect(kindListViewAlbumDetail.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (rendered)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewAlbumDetail,'inherited');
			kindListViewAlbumDetail.rendered();
			expect(kindListViewAlbumDetail.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (setStrHeaderTitle)', function(){
		it("should exist", function(){
			kindListViewAlbumDetail.setStrHeaderTitle();
		});
	});
	describe('test (listSetupRow)', function(){
		
		it("should updateUISongInfo 0", function(){
			spyOn(kindListViewAlbumDetail, "updateUISongInfo");
			kindListViewAlbumDetail.listSetupRow(undefined, 0);
			expect(kindListViewAlbumDetail.updateUISongInfo).toHaveBeenCalledWith(kindListViewAlbumDetail.arSongs[0]);
		});
		it("should updateUISongInfo 1", function(){
			spyOn(kindListViewAlbumDetail, "updateUISongInfo");
			kindListViewAlbumDetail.listSetupRow(undefined, 1);
			expect(kindListViewAlbumDetail.updateUISongInfo).toHaveBeenCalledWith(kindListViewAlbumDetail.arSongs[1]);
		});
		
		it("should call addRemoveClass playing false", function(){
			spyOn(kindListViewAlbumDetail.$.itemMedia, "addRemoveClass");
			kindListViewAlbumDetail.listSetupRow(undefined, 1);
			expect(kindListViewAlbumDetail.$.itemMedia.addRemoveClass).toHaveBeenCalledWith("odd", false);
		});
		it("should call addRemoveClass playing true", function(){
			spyOn(kindListViewAlbumDetail.$.itemMedia, "addRemoveClass");
			kindListViewAlbumDetail.strCurrTrackID = kindListViewAlbumDetail.arSongs[1]._id;
			kindListViewAlbumDetail.listSetupRow(undefined, 1);
			expect(kindListViewAlbumDetail.$.itemMedia.addRemoveClass).toHaveBeenCalledWith("playingHere", false);
		});
		
		it("should call addRemoveClass odd false", function(){
			spyOn(kindListViewAlbumDetail.$.itemMedia, "addRemoveClass");
			kindListViewAlbumDetail.listSetupRow(undefined, 1);
			//backwards ??? but doesn't matter
			expect(kindListViewAlbumDetail.$.itemMedia.addRemoveClass).toHaveBeenCalledWith("odd", false);
		});
		it("should call addRemoveClass odd true", function(){
			spyOn(kindListViewAlbumDetail.$.itemMedia, "addRemoveClass");
			kindListViewAlbumDetail.listSetupRow(undefined, 2);
			//backwards ??? but doesn't matter
			expect(kindListViewAlbumDetail.$.itemMedia.addRemoveClass).toHaveBeenCalledWith("odd", true);
		});
		describe("when intIndex is 0", function(){
			it("should this.$.albumName.setContent", function(){
				spyOn(kindListViewAlbumDetail.$.albumName, "setContent");
				kindListViewAlbumDetail.listSetupRow(undefined, 0);
				expect(kindListViewAlbumDetail.$.albumName.setContent).toHaveBeenCalledWith(kindListViewAlbumDetail.arSongs[0].album);
			});
			it("should set this.$.albumCounts.setContent", function(){
				spyOn(kindListViewAlbumDetail.$.albumCounts, "setContent");
				kindListViewAlbumDetail.listSetupRow(undefined, 0);
				expect(kindListViewAlbumDetail.$.albumCounts.setContent).toHaveBeenCalled();
			
			});
			it("should show headerAlbum", function(){
				spyOn(kindListViewAlbumDetail.$.headerAlbum, "show");
				kindListViewAlbumDetail.listSetupRow(undefined, 0);
				expect(kindListViewAlbumDetail.$.headerAlbum.show).toHaveBeenCalled();
			});
			it("should show header", function(){
				spyOn(kindListViewAlbumDetail.$.header, "show");
				kindListViewAlbumDetail.listSetupRow(undefined, 0);
				expect(kindListViewAlbumDetail.$.header.show).toHaveBeenCalled();
			});
			it("should get the thumb", function(){
				spyOn(Utilities, "getItemThumb").andCallThrough();
				kindListViewAlbumDetail.listSetupRow(undefined, 0);
				expect(Utilities.getItemThumb).toHaveBeenCalledWith(kindListViewAlbumDetail.objCurr.thumbnails ,54);
			});
			
			describe("when thumb is blank", function(){
				beforeEach(function(){
					spyOn(Utilities, "getItemThumb").andReturn({strThumbURL: ""});
				});
				it("should call setShowing to false", function(){
					spyOn(kindListViewAlbumDetail.$.imgThumb, "setShowing");
					kindListViewAlbumDetail.listSetupRow(undefined, 0);
					expect(kindListViewAlbumDetail.$.imgThumb.setShowing).toHaveBeenCalledWith(false);
					
				});
				it("shouldn't call setSrc", function(){
					spyOn(kindListViewAlbumDetail.$.imgThumb, "setSrc");
					kindListViewAlbumDetail.listSetupRow(undefined, 0);
					expect(kindListViewAlbumDetail.$.imgThumb.setSrc).not.toHaveBeenCalled();
				});
				it("shouldn't call setShowing", function(){
					spyOn(kindListViewAlbumDetail.$.imgThumb, "setShowing");
					kindListViewAlbumDetail.listSetupRow(undefined, 0);
					expect(kindListViewAlbumDetail.$.imgThumb.setShowing).not.toHaveBeenCalledWith(true);
				});
				
				
			});
			describe("when thumb isnt blank", function(){
				beforeEach(function(){
					spyOn(Utilities, "getItemThumb").andReturn({strThumbURL: "asdf"});
				});
				it("should call setShowing to false", function(){
					spyOn(kindListViewAlbumDetail.$.imgThumb, "setShowing");
					kindListViewAlbumDetail.listSetupRow(undefined, 0);
					expect(kindListViewAlbumDetail.$.imgThumb.setShowing).toHaveBeenCalledWith(true);
					
				});
				it("shouldn call setSrc", function(){
					spyOn(kindListViewAlbumDetail.$.imgThumb, "setSrc");
					kindListViewAlbumDetail.listSetupRow(undefined, 0);
					expect(kindListViewAlbumDetail.$.imgThumb.setSrc).toHaveBeenCalledWith("asdf");
				});
				it("should call setShowing", function(){
					spyOn(kindListViewAlbumDetail.$.imgThumb, "setShowing");
					kindListViewAlbumDetail.listSetupRow(undefined, 0);
					expect(kindListViewAlbumDetail.$.imgThumb.setShowing).toHaveBeenCalledWith(true);
				});
				
				
			});
			
			
		});
		describe("when intIndex is not 0", function(){
			it("shouldnt this.$.albumName.setContent", function(){
				spyOn(kindListViewAlbumDetail.$.albumName, "setContent");
				kindListViewAlbumDetail.listSetupRow(undefined, 1);
				expect(kindListViewAlbumDetail.$.albumName.setContent).not.toHaveBeenCalled();
			});
			it("shouldnt this.$.albumCounts.setContent", function(){
				spyOn(kindListViewAlbumDetail.$.albumCounts, "setContent");
				kindListViewAlbumDetail.listSetupRow(undefined, 1);
				expect(kindListViewAlbumDetail.$.albumCounts.setContent).not.toHaveBeenCalled();
			});
			it("shouldnt show headerAlbum", function(){
				spyOn(kindListViewAlbumDetail.$.headerAlbum, "show");
				kindListViewAlbumDetail.listSetupRow(undefined, 1);
				expect(kindListViewAlbumDetail.$.headerAlbum.show).not.toHaveBeenCalled();
			});
			it("shouldnt show header", function(){
				spyOn(kindListViewAlbumDetail.$.header, "show");
				kindListViewAlbumDetail.listSetupRow(undefined, 1);
				expect(kindListViewAlbumDetail.$.header.show).not.toHaveBeenCalled();
			});
			it("shouldnt get the thumb", function(){
				spyOn(Utilities, "getItemThumb").andCallThrough();
				kindListViewAlbumDetail.listSetupRow(undefined, 1);
				expect(Utilities.getItemThumb).not.toHaveBeenCalled();
			});
			
	
		});
		
		
	});
	describe('test (setItemHighlighted)', function(){
		it("should work with dragObjType song true", function(){
			kindListViewAlbumDetail.dragObjType = "song";
			spyOn(kindListViewAlbumDetail.$.itemMedia, "addRemoveClass")
			kindListViewAlbumDetail.setItemHighlighted(true);
			expect(kindListViewAlbumDetail.$.itemMedia.addRemoveClass).toHaveBeenCalledWith("selected", true);
		});
		it("should work with dragObjType song false", function(){
			kindListViewAlbumDetail.dragObjType = "song";
			spyOn(kindListViewAlbumDetail.$.itemMedia, "addRemoveClass")
			kindListViewAlbumDetail.setItemHighlighted(false);
			expect(kindListViewAlbumDetail.$.itemMedia.addRemoveClass).toHaveBeenCalledWith("selected", false);
		});
		
		it("should work with dragObjType other true", function(){
			kindListViewAlbumDetail.dragObjType = "other";
			spyOn(kindListViewAlbumDetail.$.headerAlbum, "addRemoveClass")
			kindListViewAlbumDetail.setItemHighlighted(true);
			expect(kindListViewAlbumDetail.$.headerAlbum.addRemoveClass).toHaveBeenCalledWith("selected", true);
		});
		it("should work with dragObjType other false", function(){
			kindListViewAlbumDetail.dragObjType = "other";
			spyOn(kindListViewAlbumDetail.$.headerAlbum, "addRemoveClass")
			kindListViewAlbumDetail.setItemHighlighted(false);
			expect(kindListViewAlbumDetail.$.headerAlbum.addRemoveClass).toHaveBeenCalledWith("selected", false);
		});
		
		describe("should not do the reverses", function(){
			it("should work with dragObjType song true", function(){
				kindListViewAlbumDetail.dragObjType = "other";
				spyOn(kindListViewAlbumDetail.$.itemMedia, "addRemoveClass")
				kindListViewAlbumDetail.setItemHighlighted(true);
				expect(kindListViewAlbumDetail.$.itemMedia.addRemoveClass).not.toHaveBeenCalled();
			});
			it("should work with dragObjType song false", function(){
				kindListViewAlbumDetail.dragObjType = "other";
				spyOn(kindListViewAlbumDetail.$.itemMedia, "addRemoveClass")
				kindListViewAlbumDetail.setItemHighlighted(false);
				expect(kindListViewAlbumDetail.$.itemMedia.addRemoveClass).not.toHaveBeenCalled();
			});
			
			it("should work with dragObjType other true", function(){
				kindListViewAlbumDetail.dragObjType = "song";
				spyOn(kindListViewAlbumDetail.$.headerAlbum, "addRemoveClass")
				kindListViewAlbumDetail.setItemHighlighted(true);
				expect(kindListViewAlbumDetail.$.headerAlbum.addRemoveClass).not.toHaveBeenCalled();
			});
			it("should work with dragObjType other false", function(){
				kindListViewAlbumDetail.dragObjType = "song";
				spyOn(kindListViewAlbumDetail.$.headerAlbum, "addRemoveClass")
				kindListViewAlbumDetail.setItemHighlighted(false);
				expect(kindListViewAlbumDetail.$.headerAlbum.addRemoveClass).not.toHaveBeenCalled();
			});
			
		});
		
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(kindListViewAlbumDetail,'_constructor');
			kindListViewAlbumDetail.base();
			expect(kindListViewAlbumDetail._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(kindListViewAlbumDetail,'constructed');
			kindListViewAlbumDetail.base();
			expect(kindListViewAlbumDetail.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(kindListViewAlbumDetail,'_constructor');
			kindListViewAlbumDetail.ctor();
			expect(kindListViewAlbumDetail._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(kindListViewAlbumDetail,'constructed');
			kindListViewAlbumDetail.ctor();
			expect(kindListViewAlbumDetail.constructed).toHaveBeenCalled();
		});
	});
	describe('test (renderList)', function(){
		it('should change arSongs',function(){
		
		});
	});
	describe('test (listQuery)', function(){
		it('should call doRequestMedia',function(){
			//TODO: spec this out better
			//spyOn(kindListViewAlbumDetail,'doRequestMedia');
			//kindListViewAlbumDetail.listQuery();
			//expect(kindListViewAlbumDetail.doRequestMedia).toHaveBeenCalledWith({"where":[{"prop":"isRingtone","op":"=","val":false},{"prop":"album","op":"="},{"prop":"albumArtist","op":"="}],"mediaType":"song","order":"sortKey.trackAndDisc"});
		});
	});
	describe('test (refreshList)', function(){
		it('should call doRequestCurrTrack',function(){
			spyOn(kindListViewAlbumDetail,'doRequestCurrTrack');
			kindListViewAlbumDetail.refreshList();
			expect(kindListViewAlbumDetail.doRequestCurrTrack).toHaveBeenCalled();
		});
	});
	describe('test (resizeList)', function(){
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
			spyOn(kindListViewAlbumDetail,'doSetPlaybackList');
			kindListViewAlbumDetail.onclick_listMedia(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.doSetPlaybackList).toHaveBeenCalled();
		});
	});
	describe('test (doSetPlaybackList)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewAlbumDetail,'dispatchIndirectly');
			kindListViewAlbumDetail.doSetPlaybackList();
			expect(kindListViewAlbumDetail.dispatchIndirectly).toHaveBeenCalledWith("onSetPlaybackList",{});
		});
	});
	describe('test (doRequestMedia)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewAlbumDetail,'dispatchIndirectly');
			kindListViewAlbumDetail.doRequestMedia();
			expect(kindListViewAlbumDetail.dispatchIndirectly).toHaveBeenCalledWith("onRequestMedia",{});
		});
	});
	describe('test (doRequestCurrTrack)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewAlbumDetail,'dispatchIndirectly');
			kindListViewAlbumDetail.doRequestCurrTrack();
			expect(kindListViewAlbumDetail.dispatchIndirectly).toHaveBeenCalledWith("onRequestCurrTrack",{});
		});
	});
	describe('test (doListChanged)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewAlbumDetail,'dispatchIndirectly');
			kindListViewAlbumDetail.doListChanged();
			expect(kindListViewAlbumDetail.dispatchIndirectly).toHaveBeenCalledWith("onListChanged",{});
		});
	});
	describe('test (doListSorted)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewAlbumDetail,'dispatchIndirectly');
			kindListViewAlbumDetail.doListSorted();
			expect(kindListViewAlbumDetail.dispatchIndirectly).toHaveBeenCalledWith("onListSorted",{});
		});
	});
	describe('test (getBoolViewActive)', function(){
		it('should call getProperty',function(){
			spyOn(kindListViewAlbumDetail,'getProperty');
			kindListViewAlbumDetail.getBoolViewActive();
			expect(kindListViewAlbumDetail.getProperty).toHaveBeenCalledWith("boolViewActive");
		});
	});
	describe('test (setBoolViewActive)', function(){
		it('should call _setProperty',function(){
			spyOn(kindListViewAlbumDetail,'_setProperty');
			kindListViewAlbumDetail.setBoolViewActive();
			expect(kindListViewAlbumDetail._setProperty).toHaveBeenCalledWith("boolViewActive",undefined,"boolViewActiveChanged");
		});
	});
	describe('test (getBoolListRendered)', function(){
		it('should call getProperty',function(){
			spyOn(kindListViewAlbumDetail,'getProperty');
			kindListViewAlbumDetail.getBoolListRendered();
			expect(kindListViewAlbumDetail.getProperty).toHaveBeenCalledWith("boolListRendered");
		});
	});
	describe('test (setBoolListRendered)', function(){
		it('should call _setProperty',function(){
			spyOn(kindListViewAlbumDetail,'_setProperty');
			kindListViewAlbumDetail.setBoolListRendered();
			expect(kindListViewAlbumDetail._setProperty).toHaveBeenCalledWith("boolListRendered",undefined,"boolListRenderedChanged");
		});
	});
	describe('test (callAddToPlaylist)', function(){
		it('should call doAddToPlaylist',function(){
			spyOn(kindListViewAlbumDetail,'doAddToPlaylist');
			kindListViewAlbumDetail.callAddToPlaylist();
			expect(kindListViewAlbumDetail.doAddToPlaylist).toHaveBeenCalledWith({"strMediaType":"song","arTracks":[{}]});
		});
	});
	describe('test (songMousehold)', function(){
	describe('testing (songMousehold) with params [null,{"rowIndex":0}]', function(){
		it('should change dragObjType',function(){
			kindListViewAlbumDetail.songMousehold(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.dragObjType).toEqual("song");
		});
		it('should change arItems',function(){
			kindListViewAlbumDetail.songMousehold(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.arItems).not.toEqual(undefined);
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewAlbumDetail,'itemMousehold');
			kindListViewAlbumDetail.songMousehold(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0});
		});
	});
	});
	describe('test (albumMousehold)', function(){
	describe('testing (albumMousehold) with params [null,{"rowIndex":0}]', function(){
		it('should change dragObjType',function(){
			kindListViewAlbumDetail.albumMousehold(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.dragObjType).toEqual("album");
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewAlbumDetail,'itemMousehold');
			kindListViewAlbumDetail.albumMousehold(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0});
		});
	});
	});
	describe('test (albumGroupMousehold)', function(){
	describe('testing (albumGroupMousehold) with params [null,{"rowIndex":0}]', function(){
		it('should change arItems',function(){
			kindListViewAlbumDetail.albumGroupMousehold(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.arItems).toEqual(undefined);
		});
		it('should change albumGroup',function(){
			kindListViewAlbumDetail.albumGroupMousehold(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.albumGroup).toEqual(true);
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewAlbumDetail,'itemMousehold');
			kindListViewAlbumDetail.albumGroupMousehold(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0});
		});
	});
	});
	describe('test (artistMousehold)', function(){
	describe('testing (artistMousehold) with params [null,{"rowIndex":0}]', function(){
		it('should change dragObjType',function(){
			kindListViewAlbumDetail.artistMousehold(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.dragObjType).toEqual("artist");
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewAlbumDetail,'itemMousehold');
			kindListViewAlbumDetail.artistMousehold(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0});
		});
	});
	});
	describe('test (genreMousehold)', function(){
	describe('testing (genreMousehold) with params [null,{"rowIndex":0}]', function(){
		it('should change dragObjType',function(){
			kindListViewAlbumDetail.genreMousehold(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.dragObjType).toEqual("genre");
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewAlbumDetail,'itemMousehold');
			kindListViewAlbumDetail.genreMousehold(null,{"rowIndex":0});
			expect(kindListViewAlbumDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0});
		});
	});
	});
	describe('test (itemMouserelease)', function(){
		it('should change dragObjType',function(){
			spyOn(kindListViewAlbumDetail,'setItemHighlighted');
			kindListViewAlbumDetail.itemMouserelease();
			expect(kindListViewAlbumDetail.dragObjType).toEqual(null);
		});
		it('should call setItemHighlighted',function(){
			spyOn(kindListViewAlbumDetail,'setItemHighlighted');
			kindListViewAlbumDetail.itemMouserelease();
			expect(kindListViewAlbumDetail.setItemHighlighted).toHaveBeenCalledWith(false);
		});
		it('should call doItemShowAvatar',function(){
			spyOn(kindListViewAlbumDetail,'setItemHighlighted');
			spyOn(kindListViewAlbumDetail,'doItemShowAvatar');
			kindListViewAlbumDetail.itemMouserelease();
			expect(kindListViewAlbumDetail.doItemShowAvatar).toHaveBeenCalledWith(false);
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
});