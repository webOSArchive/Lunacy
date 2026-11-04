/*globals enyo, $L, event, Utilities, window, LibraryTools */

//var creator = function(){
//				return enyo.create({kind: "kindListViewArtistDetail"});
//			}
//var objName = "kindListViewArtistDetail";
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
//    
//    obj.$.setStrHeaderTitle = function(){};
//}
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
describe('kindListViewArtistDetail tests', function(){
	var creator = function (){
				return enyo.create({kind: "kindListViewArtistDetail"});
			};
	var kindListViewArtistDetail = creator();
	beforeEach(function(){
		var before = function (obj, callbacks) {
	
	obj.itemMousehold = function(){};
	
    
  obj.$.ctrlListViewHeader = {}
  obj.$.ctrlListViewHeader.setIntHeaderCount = function(){}
    obj.$.listMedia = {};
    obj.$.listMedia.refresh = function(){};
    obj.$.listMedia.resized = function(){};
    
    obj.$.setStrHeaderTitle = function(){};
    obj.arSongs = getJson("mock_songArr");
    
};
		var callback = function(one, fun){return fun;};
		before(kindListViewArtistDetail,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof kindListViewArtistDetail.controls).toBe('object');
		expect(typeof kindListViewArtistDetail.children).toBe('object');
		expect(kindListViewArtistDetail._componentNameMap).toEqual({"ctrlListViewHeader":2,"control":5,"toolbar":2,"grabButton":2});
		expect(typeof kindListViewArtistDetail.$).toBe('object');
		expect(kindListViewArtistDetail.domStyles).toEqual({"height":"100%"});
		//expect(kindListViewArtistDetail.domAttributes).toEqual({"id":"kindListViewArtistDetail"});
		expect(kindListViewArtistDetail.kind).toEqual("kindListViewArtistDetail");
		expect(typeof kindListViewArtistDetail.owner).toBe('object');
		expect(kindListViewArtistDetail.name).toEqual("kindListViewArtistDetail");
		expect(kindListViewArtistDetail.id).toEqual("kindListViewArtistDetail");
		expect(kindListViewArtistDetail.controlParent).toEqual(undefined);
		expect(kindListViewArtistDetail.intCurrTrack).toEqual(-1);
		expect(kindListViewArtistDetail.intCurrTrackOrigIndex).toEqual(-1);
		expect(kindListViewArtistDetail.strCurrTrackID).toEqual("");
		expect(kindListViewArtistDetail.boolSortAsc).toEqual(true);
		expect(kindListViewArtistDetail.strSortMode).toEqual("album");
		//expect(kindListViewArtistDetail.strCurrListID).toEqual("FA5552BC1741-C40E38-A43519-D51BDF-8F1D7BAE58C6BC01F9");
		expect(kindListViewArtistDetail.listViewDetailType).toEqual("artist");
		expect(kindListViewArtistDetail.height).toEqual("100%");
		expect(kindListViewArtistDetail.events).toEqual({"onResetView":""});
		expect(kindListViewArtistDetail.kindName).toEqual("kindListViewArtistDetail");
		//expect(kindListViewArtistDetail.kindComponents).toEqual([{"kind":"ctrlListViewHeader","onSearch_Header":"onSearchSongs","boolShowSearchBtn":false},{"kind":"Control","layoutKind":"VFlexLayout","className":"list","flex":1,"components":[{"name":"listMedia","kind":"VirtualList","onQuery":"listQuery","onSetupRow":"listSetupRow","className":"scroll","flex":1,"components":[{"name":"itemMedia","kind":"draggableItem","onclick":"onclick_listMediaFilter","onmousehold":"mousehold_itemmedia","components":[{"name":"headerAlbum","kind":"Item","className":"header album","showing":false,"components":[{"kind":"Control","className":"content","align":"center","layoutKind":"HFlexLayout","components":[{"className":"cover","onclick":"onclick_playArtist","components":[{"name":"imgContainer","className":"img","components":[{"name":"imgThumb","kind":"Image","className":"thumb"}]}]},{"kind":"Control","components":[{"name":"albumName","className":"name","content":"Siamese Dream"},{"name":"albumCounts","className":"count","content":"10 songs","showing":false}]}]}]},{"name":"header","kind":"Control","layoutKind":"HFlexLayout","className":"divider first","align":"center","showing":false,"components":[{"name":"lblSortPos","content":"#","className":"column number sorted","onclick":"onclick_Sort"},{"name":"lblSortSong","content":"Song","flex":10,"className":"column","onclick":"onclick_Sort"},{"name":"lblSortAlbum","content":"Time","flex":2,"className":"column","showing":false}]},{"name":"song","kind":"Item","layoutKind":"HFlexLayout","className":"song","components":[{"name":"songTrack","className":"column number"},{"name":"songTitle","flex":10,"className":"column title"},{"name":"songTime","flex":2,"className":"column time","showing":false}]}]}]}]},{"kind":"Toolbar","className":"enyo-toolbar-light","slidingHandler":true,"components":[{"kind":"GrabButton"}]}]);
		expect(kindListViewArtistDetail.onResetView).toEqual("");
		expect(kindListViewArtistDetail.published).toEqual({"boolViewActive":false,"boolListRendered":false});
		expect(kindListViewArtistDetail.objCurr).toEqual({});
		expect(kindListViewArtistDetail.onSetPlaybackList).toEqual("");
		expect(kindListViewArtistDetail.onRequestMedia).toEqual("");
		expect(kindListViewArtistDetail.onRequestCurrTrack).toEqual("");
		expect(kindListViewArtistDetail.onListChanged).toEqual("");
		expect(kindListViewArtistDetail.onListSorted).toEqual("");
		expect(kindListViewArtistDetail.boolViewActive).toEqual(false);
		expect(kindListViewArtistDetail.boolListRendered).toEqual(false);
		expect(kindListViewArtistDetail.intJumpRowOffset).toEqual(0);
		expect(kindListViewArtistDetail.boolDragHold).toEqual(false);
		expect(kindListViewArtistDetail.boolDragItem).toEqual(false);
		expect(kindListViewArtistDetail.boolDraggingList).toEqual(false);
		expect(kindListViewArtistDetail.dragObjType).toEqual(null);
		expect(kindListViewArtistDetail.arItems).toEqual([]);
		expect(kindListViewArtistDetail.albumGroup).toEqual(false);
		expect(kindListViewArtistDetail.onAddToPlaylist).toEqual("");
		expect(kindListViewArtistDetail.onItemDrag).toEqual("");
		expect(kindListViewArtistDetail.onItemShowAvatar).toEqual("");
		expect(kindListViewArtistDetail.onItemShowAvatarIcon).toEqual("");
		expect(kindListViewArtistDetail.controlParentName).toEqual("client");
		expect(kindListViewArtistDetail.defaultKind).toEqual("Control");
		expect(kindListViewArtistDetail.onclick).toEqual("");
		expect(kindListViewArtistDetail.onmousedown).toEqual("");
		expect(kindListViewArtistDetail.onmouseup).toEqual("");
		//expect(kindListViewArtistDetail.layoutKind).toEqual("");
		expect(kindListViewArtistDetail.style).toEqual("");
		expect(kindListViewArtistDetail.className).toEqual("artistDetail");
		expect(kindListViewArtistDetail.container).toEqual(null);
		expect(kindListViewArtistDetail.parent).toEqual(null);
		expect(kindListViewArtistDetail.generated).toEqual(false);
		expect(kindListViewArtistDetail.allowHtml).toEqual(false);
		expect(kindListViewArtistDetail.content).toEqual("");
		expect(kindListViewArtistDetail.nodeTag).toEqual("div");
		expect(kindListViewArtistDetail.node).toEqual(null);
		expect(kindListViewArtistDetail.showing).toEqual(true);
		expect(kindListViewArtistDetail.prepend).toEqual(false);
		expect(kindListViewArtistDetail.wantsEvents).toEqual(true);
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewArtistDetail,'inherited');
			kindListViewArtistDetail.create();
			expect(kindListViewArtistDetail.inherited).toHaveBeenCalledWith({});
		});
		it("should setIntHeaderCount", function(){
			spyOn(kindListViewArtistDetail.$.ctrlListViewHeader,"setIntHeaderCount");
			kindListViewArtistDetail.create();
			expect(kindListViewArtistDetail.$.ctrlListViewHeader.setIntHeaderCount).toHaveBeenCalledWith("");
		});
		it("should set this.listViewDetailType", function(){
			kindListViewArtistDetail.listViewDetailType = "blah";
			kindListViewArtistDetail.create();
			expect(kindListViewArtistDetail.listViewDetailType).toBe("artist");
		});
	});
	describe('test (ready)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewArtistDetail,'inherited');
			kindListViewArtistDetail.ready();
			expect(kindListViewArtistDetail.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (rendered)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewArtistDetail,'inherited');
			kindListViewArtistDetail.rendered();
			expect(kindListViewArtistDetail.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (listSetupRow)', function(){
		
		it("should updateUISongInfo 0", function(){
			spyOn(kindListViewArtistDetail, "updateUISongInfo");
			kindListViewArtistDetail.listSetupRow(undefined, 0);
			expect(kindListViewArtistDetail.updateUISongInfo).toHaveBeenCalledWith(kindListViewArtistDetail.arSongs[0]);
		});
		it("should updateUISongInfo 1", function(){
			spyOn(kindListViewArtistDetail, "updateUISongInfo");
			kindListViewArtistDetail.listSetupRow(undefined, 1);
			expect(kindListViewArtistDetail.updateUISongInfo).toHaveBeenCalledWith(kindListViewArtistDetail.arSongs[1]);
		});
		
		it("should call addRemoveClass odd false", function(){
			spyOn(kindListViewArtistDetail.$.song, "addRemoveClass");
			kindListViewArtistDetail.listSetupRow(undefined, 1);
			expect(kindListViewArtistDetail.$.song.addRemoveClass).toHaveBeenCalledWith("odd", true);
		});
		it("should call addRemoveClass playingHere false", function(){
			spyOn(kindListViewArtistDetail.$.song, "addRemoveClass");
			kindListViewArtistDetail.strCurrTrackID = kindListViewArtistDetail.arSongs[1]._id;
			kindListViewArtistDetail.listSetupRow(undefined, 1);
			expect(kindListViewArtistDetail.$.song.addRemoveClass).toHaveBeenCalledWith("playingHere", false);
		});
		
		it("should call addRemoveClass odd true", function(){
			spyOn(kindListViewArtistDetail.$.song, "addRemoveClass");
			kindListViewArtistDetail.listSetupRow(undefined, 1);
			//backwards ??? but doesn't matter
			expect(kindListViewArtistDetail.$.song.addRemoveClass).toHaveBeenCalledWith("odd", true);
		});
		it("should call addRemoveClass odd true", function(){
			spyOn(kindListViewArtistDetail.$.song, "addRemoveClass");
			kindListViewArtistDetail.listSetupRow(undefined, 2);
			//backwards ??? but doesn't matter
			expect(kindListViewArtistDetail.$.song.addRemoveClass).toHaveBeenCalledWith("odd", true);
		});
		describe("when first", function(){
			it("should this.$.headerAlbum.addClass", function(){
				spyOn(kindListViewArtistDetail.$.headerAlbum, "addClass");
				kindListViewArtistDetail.listSetupRow(undefined, 0);
				expect(kindListViewArtistDetail.$.headerAlbum.addClass).toHaveBeenCalledWith("first");
			});
			it("should this.$.headerAlbum.show", function(){
				spyOn(kindListViewArtistDetail.$.headerAlbum, "show");
				kindListViewArtistDetail.listSetupRow(undefined, 0);
				expect(kindListViewArtistDetail.$.headerAlbum.show).toHaveBeenCalled();
			});
			it("should this.$.header.addClass", function(){
				spyOn(kindListViewArtistDetail.$.header, "show");
				kindListViewArtistDetail.listSetupRow(undefined, 0);
				expect(kindListViewArtistDetail.$.header.show).toHaveBeenCalled();
			});
			it("should set this.$.albumName.setContent", function(){
				spyOn(kindListViewArtistDetail.$.albumName, "setContent");
				kindListViewArtistDetail.listSetupRow(undefined, 0);
				expect(kindListViewArtistDetail.$.albumName.setContent).toHaveBeenCalledWith(kindListViewArtistDetail.arSongs[0].album);
			
			});
		
			it("should get the thumb", function(){
				spyOn(Utilities, "getItemThumb").andCallThrough();
				kindListViewArtistDetail.listSetupRow(undefined, 0);
				expect(Utilities.getItemThumb).toHaveBeenCalledWith(kindListViewArtistDetail.arSongs[0].thumbnails ,54);
			});
			
			describe("when thumb is blank", function(){
				beforeEach(function(){
					spyOn(Utilities, "getItemThumb").andReturn(undefined);
				});
				it("should call setShowing to false", function(){
					spyOn(kindListViewArtistDetail.$.imgContainer, "hide");
					kindListViewArtistDetail.listSetupRow(undefined, 0);
					expect(kindListViewArtistDetail.$.imgContainer.hide).toHaveBeenCalled();
					
				});
				
				
			});
			describe("when thumb isnt blank", function(){
				beforeEach(function(){
					spyOn(Utilities, "getItemThumb").andReturn({strThumbURL: "asdf", intThumbsTotalCount: 1});
				});
				
				it("shouldn call setSrc", function(){
					spyOn(kindListViewArtistDetail.$.imgThumb, "setSrc");
					kindListViewArtistDetail.listSetupRow(undefined, 0);
					expect(kindListViewArtistDetail.$.imgThumb.setSrc).toHaveBeenCalledWith("asdf");
				});
				it("should call setShowing", function(){
					spyOn(kindListViewArtistDetail.$.imgContainer, "show");
					kindListViewArtistDetail.listSetupRow(undefined, 0);
					expect(kindListViewArtistDetail.$.imgContainer.show).toHaveBeenCalled();
				});
				
				
			});
			
			
		});
		describe("when not the first", function(){
			beforeEach(function(){
				kindListViewArtistDetail.arSongs[0].album = kindListViewArtistDetail.arSongs[1].album;
			});
	
			it("shouldnt show headerAlbum", function(){
				spyOn(kindListViewArtistDetail.$.headerAlbum, "removeClass");
				kindListViewArtistDetail.listSetupRow(undefined, 1);
				expect(kindListViewArtistDetail.$.headerAlbum.removeClass).toHaveBeenCalledWith("first");
			});
			it("shouldnt show headerAlbum", function(){
				spyOn(kindListViewArtistDetail.$.headerAlbum, "hide");
				kindListViewArtistDetail.listSetupRow(undefined, 1);
				expect(kindListViewArtistDetail.$.headerAlbum.hide).toHaveBeenCalled();
			});
			
			it("shouldn\'t this.$.headerAlbum.addClass", function(){
				spyOn(kindListViewArtistDetail.$.headerAlbum, "addClass");
				kindListViewArtistDetail.listSetupRow(undefined, 1);
				expect(kindListViewArtistDetail.$.headerAlbum.addClass).not.toHaveBeenCalled();
			});
			it("shouldn\'t this.$.headerAlbum.show", function(){
				spyOn(kindListViewArtistDetail.$.headerAlbum, "show");
				kindListViewArtistDetail.listSetupRow(undefined, 1);
				expect(kindListViewArtistDetail.$.headerAlbum.show).not.toHaveBeenCalled();
			});
			it("shouldn\'t this.$.header.addClass", function(){
				spyOn(kindListViewArtistDetail.$.header, "show");
				kindListViewArtistDetail.listSetupRow(undefined, 1);
				expect(kindListViewArtistDetail.$.header.show).not.toHaveBeenCalled();
			});
			it("shouldn\'t set this.$.albumName.setContent", function(){
				spyOn(kindListViewArtistDetail.$.albumName, "setContent");
				kindListViewArtistDetail.listSetupRow(undefined, 1);
				expect(kindListViewArtistDetail.$.albumName.setContent).not.toHaveBeenCalled();
			
			});
		
			it("shouldn\'t get the thumb", function(){
				spyOn(Utilities, "getItemThumb").andCallThrough();
				kindListViewArtistDetail.listSetupRow(undefined, 1);
				expect(Utilities.getItemThumb).not.toHaveBeenCalled();
			});
	
		});
		
		
		
		
		
		
		
		
	});
	describe('test (onclick_listMediaFilter)', function(){
		it('should call onclick_listMedia',function(){
			spyOn(kindListViewArtistDetail,'onclick_listMedia');
			kindListViewArtistDetail.onclick_listMediaFilter(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewArtistDetail.onclick_listMedia).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
		it('shouldnt call onclick_listMedia if in headerAlbum',function(){
			spyOn(kindListViewArtistDetail,'onclick_listMedia');
			kindListViewArtistDetail.onclick_listMediaFilter(null,{"rowIndex":0,"dispatchTarget":{parent: "as", id:"musicPlayerApp_listViewArtistDetail_headerAlbum"}});
			expect(kindListViewArtistDetail.onclick_listMedia).not.toHaveBeenCalled();
		});
		it('shouldnt call onclick_listMedia if in musicPlayerApp_listViewArtistDetail_header',function(){
			spyOn(kindListViewArtistDetail,'onclick_listMedia');
			kindListViewArtistDetail.onclick_listMediaFilter(null,{"rowIndex":0,"dispatchTarget":{parent: "asd", id:"musicPlayerApp_listViewArtistDetail_header"}});
			expect(kindListViewArtistDetail.onclick_listMedia).not.toHaveBeenCalled();
		});
	});
	describe('test (onclick_grabButton)', function(){
		it('should call doResetView',function(){
			spyOn(kindListViewArtistDetail,'doResetView');
			kindListViewArtistDetail.onclick_grabButton(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewArtistDetail.doResetView).toHaveBeenCalled();
		});
	});
	describe('test (mousehold_itemmedia)', function(){
		describe('testing (mousehold_itemmedia) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
			it('should change dragObjType',function(){
				kindListViewArtistDetail.dragObjType = "notsong";
				kindListViewArtistDetail.mousehold_itemmedia(null,{"rowIndex":0,"dispatchTarget":null});
				expect(kindListViewArtistDetail.dragObjType).toEqual("song");
			});
			it('should call songMousehold',function(){
				spyOn(kindListViewArtistDetail,'songMousehold');
				kindListViewArtistDetail.mousehold_itemmedia(null,{"rowIndex":0,"dispatchTarget":{parent: "asd", id:"musicPlayerApp_listViewArtistDetail_itemMedia"}});
				expect(kindListViewArtistDetail.songMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":{parent: "asd", id:"musicPlayerApp_listViewArtistDetail_itemMedia"}});
			});
			describe("when musicPlayerApp_listViewArtistDetail_headerAlbum", function(){
				it("shouldn't call songMouseHold", function(){
					spyOn(kindListViewArtistDetail,'songMousehold');
					kindListViewArtistDetail.mousehold_itemmedia(null,{"rowIndex":0,"dispatchTarget":{parent: "asd", id:"musicPlayerApp_listViewArtistDetail_headerAlbum"}});
					expect(kindListViewArtistDetail.songMousehold).not.toHaveBeenCalled();
				
				});
				it("should call albumMousehold", function(){
					spyOn(kindListViewArtistDetail,'albumMousehold');
					kindListViewArtistDetail.mousehold_itemmedia(null,{"rowIndex":0,"dispatchTarget":{parent: "asd", id:"musicPlayerApp_listViewArtistDetail_headerAlbum"}});
					expect(kindListViewArtistDetail.albumMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":{parent: "asd", id:"musicPlayerApp_listViewArtistDetail_headerAlbum"}});
				
				});
			});
			describe("when musicPlayerApp_listViewArtistDetail_header", function(){
				it("shouldn't call songMouseHold", function(){
					spyOn(kindListViewArtistDetail,'songMousehold');
					kindListViewArtistDetail.mousehold_itemmedia(null,{"rowIndex":0,"dispatchTarget":{parent: "asd", id:"musicPlayerApp_listViewArtistDetail_header"}});
					expect(kindListViewArtistDetail.songMousehold).not.toHaveBeenCalled();
				
				});
				it("should call albumMousehold", function(){
					spyOn(kindListViewArtistDetail,'albumMousehold');
					kindListViewArtistDetail.mousehold_itemmedia(null,{"rowIndex":0,"dispatchTarget":{parent: "asd", id:"musicPlayerApp_listViewArtistDetail_header"}});
					expect(kindListViewArtistDetail.albumMousehold).not.toHaveBeenCalled();
				});
			});
		});
	});
	describe('test (setItemHighlighted)', function(){
		it("should work with dragObjType song true", function(){
			kindListViewArtistDetail.dragObjType = "song";
			spyOn(kindListViewArtistDetail.$.song, "addRemoveClass")
			kindListViewArtistDetail.setItemHighlighted(true);
			expect(kindListViewArtistDetail.$.song.addRemoveClass).toHaveBeenCalledWith("selected", true);
		});
		it("should work with dragObjType song false", function(){
			kindListViewArtistDetail.dragObjType = "song";
			spyOn(kindListViewArtistDetail.$.song, "addRemoveClass")
			kindListViewArtistDetail.setItemHighlighted(false);
			expect(kindListViewArtistDetail.$.song.addRemoveClass).toHaveBeenCalledWith("selected", false);
		});
		
		it("should work with dragObjType other true", function(){
			kindListViewArtistDetail.dragObjType = "other";
			spyOn(kindListViewArtistDetail.$.headerAlbum, "addRemoveClass")
			kindListViewArtistDetail.setItemHighlighted(true);
			expect(kindListViewArtistDetail.$.headerAlbum.addRemoveClass).toHaveBeenCalledWith("selected", true);
		});
		it("should work with dragObjType other false", function(){
			kindListViewArtistDetail.dragObjType = "other";
			spyOn(kindListViewArtistDetail.$.headerAlbum, "addRemoveClass")
			kindListViewArtistDetail.setItemHighlighted(false);
			expect(kindListViewArtistDetail.$.headerAlbum.addRemoveClass).toHaveBeenCalledWith("selected", false);
		});
		
		describe("should not do the reverses", function(){
			it("should work with dragObjType song true", function(){
				kindListViewArtistDetail.dragObjType = "other";
				spyOn(kindListViewArtistDetail.$.song, "addRemoveClass")
				kindListViewArtistDetail.setItemHighlighted(true);
				expect(kindListViewArtistDetail.$.song.addRemoveClass).not.toHaveBeenCalled();
			});
			it("should work with dragObjType song false", function(){
				kindListViewArtistDetail.dragObjType = "other";
				spyOn(kindListViewArtistDetail.$.song, "addRemoveClass")
				kindListViewArtistDetail.setItemHighlighted(false);
				expect(kindListViewArtistDetail.$.song.addRemoveClass).not.toHaveBeenCalled();
			});
			
			it("should work with dragObjType other true", function(){
				kindListViewArtistDetail.dragObjType = "song";
				spyOn(kindListViewArtistDetail.$.headerAlbum, "addRemoveClass")
				kindListViewArtistDetail.setItemHighlighted(true);
				expect(kindListViewArtistDetail.$.headerAlbum.addRemoveClass).not.toHaveBeenCalled();
			});
			it("should work with dragObjType other false", function(){
				kindListViewArtistDetail.dragObjType = "song";
				spyOn(kindListViewArtistDetail.$.headerAlbum, "addRemoveClass")
				kindListViewArtistDetail.setItemHighlighted(false);
				expect(kindListViewArtistDetail.$.headerAlbum.addRemoveClass).not.toHaveBeenCalled();
			});
			
		});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(kindListViewArtistDetail,'_constructor');
			kindListViewArtistDetail.base();
			expect(kindListViewArtistDetail._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(kindListViewArtistDetail,'constructed');
			kindListViewArtistDetail.base();
			expect(kindListViewArtistDetail.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(kindListViewArtistDetail,'_constructor');
			kindListViewArtistDetail.ctor();
			expect(kindListViewArtistDetail._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(kindListViewArtistDetail,'constructed');
			kindListViewArtistDetail.ctor();
			expect(kindListViewArtistDetail.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doResetView)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewArtistDetail,'dispatchIndirectly');
			kindListViewArtistDetail.doResetView();
			expect(kindListViewArtistDetail.dispatchIndirectly).toHaveBeenCalledWith("onResetView",{});
		});
	});
	describe('test (renderList)', function(){
		it('should change arSongs',function(){
			
		});
	});
	describe('test (listQuery)', function(){
		it('should call doRequestMedia',function(){
			//TODO: spec out better
			//spyOn(kindListViewArtistDetail,'doRequestMedia');
			//kindListViewArtistDetail.listQuery();
			//expect(kindListViewArtistDetail.doRequestMedia).toHaveBeenCalledWith({"where":[{"prop":"isRingtone","op":"=","val":false},{"prop":"artist","op":"="}],"mediaType":"song","order":"sortKey.albumDiscAndTrack"});
		});
	});
	describe('test (refreshList)', function(){
		it('should call doRequestCurrTrack',function(){
			spyOn(kindListViewArtistDetail,'doRequestCurrTrack');
			kindListViewArtistDetail.refreshList();
			expect(kindListViewArtistDetail.doRequestCurrTrack).toHaveBeenCalled();
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
			//TODO: pass through better
			//spyOn(kindListViewArtistDetail,'doSetPlaybackList');
			//kindListViewArtistDetail.onclick_listMedia(null,{"rowIndex":0,"dispatchTarget":null});
			//expect(kindListViewArtistDetail.doSetPlaybackList).toHaveBeenCalledWith({"arSetPlaybackList":[],"intStartTrackIndex":0,"intStartTrackTime":0,"strOriginListID":"FA5552BC1741-C40E38-A43519-D51BDF-8F1D7BAE58C6BC01F9","strListType":"Artist","strListSubText":""});
		});
	});
	describe('test (doSetPlaybackList)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewArtistDetail,'dispatchIndirectly');
			kindListViewArtistDetail.doSetPlaybackList();
			expect(kindListViewArtistDetail.dispatchIndirectly).toHaveBeenCalledWith("onSetPlaybackList",{});
		});
	});
	describe('test (doRequestMedia)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewArtistDetail,'dispatchIndirectly');
			kindListViewArtistDetail.doRequestMedia();
			expect(kindListViewArtistDetail.dispatchIndirectly).toHaveBeenCalledWith("onRequestMedia",{});
		});
	});
	describe('test (doRequestCurrTrack)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewArtistDetail,'dispatchIndirectly');
			kindListViewArtistDetail.doRequestCurrTrack();
			expect(kindListViewArtistDetail.dispatchIndirectly).toHaveBeenCalledWith("onRequestCurrTrack",{});
		});
	});
	describe('test (doListChanged)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewArtistDetail,'dispatchIndirectly');
			kindListViewArtistDetail.doListChanged();
			expect(kindListViewArtistDetail.dispatchIndirectly).toHaveBeenCalledWith("onListChanged",{});
		});
	});
	describe('test (doListSorted)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewArtistDetail,'dispatchIndirectly');
			kindListViewArtistDetail.doListSorted();
			expect(kindListViewArtistDetail.dispatchIndirectly).toHaveBeenCalledWith("onListSorted",{});
		});
	});
	describe('test (getBoolViewActive)', function(){
		it('should call getProperty',function(){
			spyOn(kindListViewArtistDetail,'getProperty');
			kindListViewArtistDetail.getBoolViewActive();
			expect(kindListViewArtistDetail.getProperty).toHaveBeenCalledWith("boolViewActive");
		});
	});
	describe('test (setBoolViewActive)', function(){
		it('should call _setProperty',function(){
			spyOn(kindListViewArtistDetail,'_setProperty');
			kindListViewArtistDetail.setBoolViewActive();
			expect(kindListViewArtistDetail._setProperty).toHaveBeenCalledWith("boolViewActive",undefined,"boolViewActiveChanged");
		});
	});
	describe('test (getBoolListRendered)', function(){
		it('should call getProperty',function(){
			spyOn(kindListViewArtistDetail,'getProperty');
			kindListViewArtistDetail.getBoolListRendered();
			expect(kindListViewArtistDetail.getProperty).toHaveBeenCalledWith("boolListRendered");
		});
	});
	describe('test (setBoolListRendered)', function(){
		it('should call _setProperty',function(){
			spyOn(kindListViewArtistDetail,'_setProperty');
			kindListViewArtistDetail.setBoolListRendered();
			expect(kindListViewArtistDetail._setProperty).toHaveBeenCalledWith("boolListRendered",undefined,"boolListRenderedChanged");
		});
	});
	describe('test (songMousehold)', function(){
	describe('testing (songMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change arItems',function(){
			
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewArtistDetail,'itemMousehold');
			kindListViewArtistDetail.songMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewArtistDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (albumMousehold)', function(){
	describe('testing (albumMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			kindListViewArtistDetail.albumMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewArtistDetail.dragObjType).toEqual("album");
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewArtistDetail,'itemMousehold');
			kindListViewArtistDetail.albumMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewArtistDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (albumGroupMousehold)', function(){
	describe('testing (albumGroupMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change arItems',function(){
			kindListViewArtistDetail.albumGroupMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewArtistDetail.arItems).toEqual(undefined);
		});
		it('should change albumGroup',function(){
			kindListViewArtistDetail.albumGroupMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewArtistDetail.albumGroup).toEqual(true);
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewArtistDetail,'itemMousehold');
			kindListViewArtistDetail.albumGroupMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewArtistDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (artistMousehold)', function(){
	describe('testing (artistMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			kindListViewArtistDetail.artistMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewArtistDetail.dragObjType).toEqual("artist");
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewArtistDetail,'itemMousehold');
			kindListViewArtistDetail.artistMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewArtistDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	});
	describe('test (genreMousehold)', function(){
	describe('testing (genreMousehold) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
		it('should change dragObjType',function(){
			kindListViewArtistDetail.genreMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewArtistDetail.dragObjType).toEqual("genre");
		});
		it('should call itemMousehold',function(){
			spyOn(kindListViewArtistDetail,'itemMousehold');
			kindListViewArtistDetail.genreMousehold(null,{"rowIndex":0,"dispatchTarget":null});
			expect(kindListViewArtistDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0,"dispatchTarget":null});
		});
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
	describe('testing (getDragIndex) with params [null,{"rowIndex":0,"dispatchTarget":null}]', function(){
	});
	});
	describe('test (doAddToPlaylist)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewArtistDetail,'dispatchIndirectly');
			kindListViewArtistDetail.doAddToPlaylist();
			expect(kindListViewArtistDetail.dispatchIndirectly).toHaveBeenCalledWith("onAddToPlaylist",{});
		});
	});
	describe('test (doItemDrag)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewArtistDetail,'dispatchIndirectly');
			kindListViewArtistDetail.doItemDrag();
			expect(kindListViewArtistDetail.dispatchIndirectly).toHaveBeenCalledWith("onItemDrag",{});
		});
	});
	describe('test (doItemShowAvatar)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewArtistDetail,'dispatchIndirectly');
			kindListViewArtistDetail.doItemShowAvatar();
			expect(kindListViewArtistDetail.dispatchIndirectly).toHaveBeenCalledWith("onItemShowAvatar",{});
		});
	});
	describe('test (doItemShowAvatarIcon)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(kindListViewArtistDetail,'dispatchIndirectly');
			kindListViewArtistDetail.doItemShowAvatarIcon();
			expect(kindListViewArtistDetail.dispatchIndirectly).toHaveBeenCalledWith("onItemShowAvatarIcon",{});
		});
	});
	describe('test (highlightTrack)', function(){
	});
	
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewArtistDetail,'inherited');
			kindListViewArtistDetail.importProps();
			expect(kindListViewArtistDetail.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(kindListViewArtistDetail,'createChrome');
			kindListViewArtistDetail.initComponents();
			expect(kindListViewArtistDetail.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(kindListViewArtistDetail,'inherited');
			kindListViewArtistDetail.initComponents();
			expect(kindListViewArtistDetail.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(kindListViewArtistDetail,'inherited');
			kindListViewArtistDetail.createComponents();
			expect(kindListViewArtistDetail.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(kindListViewArtistDetail,'discoverControlParent');
			kindListViewArtistDetail.createComponents();
			expect(kindListViewArtistDetail.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(kindListViewArtistDetail,'createComponents');
			kindListViewArtistDetail.createChrome();
			expect(kindListViewArtistDetail.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});