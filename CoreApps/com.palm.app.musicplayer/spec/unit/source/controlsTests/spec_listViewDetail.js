/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return enyo.create({kind: "listViewDetail"});
//			}
//var objName = "listViewDetail";
//
//var before = function (obj, callbacks) {
//	
//	
//	obj.$.ctrlListViewHeader = {};
//	obj.$.ctrlListViewHeader.setIntHeaderCount = callbacks("$.ctrlListViewHeader.setIntHeaderCount", function(){});
//	
//	obj.$.listMedia = {};
//	obj.$.listMedia.refresh = callbacks("$.listMedia.refresh", function(){});
//	obj.$.listMedia.resized = callbacks("$.listMedia.resized", function(){});
//	
//	obj.$.header = {};
//	obj.$.header.children =[{setClassName:  callbacks("$.header.children[0].setClassName", function(){})},
//	                         {setClassName:  callbacks("$.header.children[1].setClassName", function(){})},
//	                         {setClassName:  callbacks("$.header.children[2].setClassName", function(){})}];
//	
//	obj.itemMousehold = function(){};
//
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
//		 "gotSongs" : [[{"returnValue": true,
//			    "results": [ {"title": "1"}, {"title": "2"}, {"title": "3"}],
//			    "count" : 3,
//			    "next" : "next"}, {"objGetMediaRequest" : {"page": true}}],
//			[{ "returnValue": true,
//			    "results": [ {"title": "1"}, {"title": "2"}, {"title": "3"}],
//			    "count" : 3,
//			    "next" : "next"
//			}, {"objGetMediaRequest" : {"page": false}}],
//			[{ "returnValue": true,
//			    "results": [],
//			    "count" : 0,
//			    "next" : ""
//			}, {"objGetMediaRequest" : {"page": false}}]]
//		 
//};
//
//specOut(creator, objName, before, params);

describe('listViewDetail tests', function(){
	var creator = function (){
				return enyo.create({kind: "listViewDetail"});
			};
	var listViewDetail;
	beforeEach(function(){
		var before = function (obj, callbacks) {
			listViewDetail = creator();
			obj = listViewDetail;
			obj.$.ctrlListViewHeader = {};
			obj.$.ctrlListViewHeader.setIntHeaderCount = callbacks("$.ctrlListViewHeader.setIntHeaderCount", function(){});
			
			obj.$.listMedia = {};
			obj.$.listMedia.refresh = callbacks("$.listMedia.refresh", function(){});
			obj.$.listMedia.resized = callbacks("$.listMedia.resized", function(){});
			obj.$.listMedia.hide = function(){};
			obj.$.listMedia.punt = function(){};
			obj.$.listMedia.show = function(){};
			obj.$.header = {};
			obj.$.header.children =[{setClassName:  callbacks("$.header.children[0].setClassName", function(){})},
			                         {setClassName:  callbacks("$.header.children[1].setClassName", function(){})},
			                         {setClassName:  callbacks("$.header.children[2].setClassName", function(){})}];
		
			
			//listViewDetail.setStrHeaderTitle = function(){};
			obj.itemMousehold = function(){};

	};
		var callback = function(one, fun){return fun;};
		before(listViewDetail,callback);
	});
	it('should correctly set up object', function(){
		expect(listViewDetail.controls).toEqual([]);
		expect(listViewDetail.children).toEqual([]);
		expect(listViewDetail._componentNameMap).toEqual({});
		//expect(listViewDetail.$).toEqual({"ctrlListViewHeader":{},"listMedia":{},"header":{"children":[{},{},{}]}});
		expect(listViewDetail.domStyles).toEqual({});
		expect(listViewDetail.domAttributes).toEqual({"id":"listViewDetail"});
		expect(listViewDetail.kind).toEqual("listViewDetail");
		expect(typeof listViewDetail.owner).toBe('object');
		expect(listViewDetail.name).toEqual("listViewDetail");
		expect(listViewDetail.id).toEqual("listViewDetail");
		expect(listViewDetail.controlParent).toEqual(undefined);
		expect(listViewDetail.intCurrTrack).toEqual(-1);
		expect(listViewDetail.intCurrTrackOrigIndex).toEqual(-1);
		expect(listViewDetail.strCurrTrackID).toEqual("");
		expect(listViewDetail.boolSortAsc).toEqual(true);
		expect(listViewDetail.strSortMode).toEqual("album");
		//expect(listViewDetail.strCurrListID).toEqual("8F0688716C64-4E61CB-9AFDB6-230EC6-1D9CC952EB886B9788");
		expect(listViewDetail.published).toEqual({"boolViewActive":false,"boolListRendered":false});
		expect(listViewDetail.events).toEqual({"onSetPlaybackList":"","onRequestMedia":"","onRequestCurrTrack":"","onListChanged":"","onListSorted":""});
		expect(listViewDetail.objCurr).toEqual({});
		expect(listViewDetail.listViewDetailType).toEqual("");
		expect(listViewDetail.kindName).toEqual("listViewDetail");
		expect(listViewDetail.onSetPlaybackList).toEqual("");
		expect(listViewDetail.onRequestMedia).toEqual("");
		expect(listViewDetail.onRequestCurrTrack).toEqual("");
		expect(listViewDetail.onListChanged).toEqual("");
		expect(listViewDetail.onListSorted).toEqual("");
		expect(listViewDetail.boolViewActive).toEqual(false);
		expect(listViewDetail.boolListRendered).toEqual(false);
		expect(listViewDetail.intJumpRowOffset).toEqual(0);
		expect(listViewDetail.boolDragHold).toEqual(false);
		expect(listViewDetail.boolDragItem).toEqual(false);
		expect(listViewDetail.boolDraggingList).toEqual(false);
		expect(listViewDetail.dragObjType).toEqual(null);
		expect(listViewDetail.arItems).toEqual([]);
		expect(listViewDetail.albumGroup).toEqual(false);
		expect(listViewDetail.onAddToPlaylist).toEqual("");
		expect(listViewDetail.onItemDrag).toEqual("");
		expect(listViewDetail.onItemShowAvatar).toEqual("");
		expect(listViewDetail.onItemShowAvatarIcon).toEqual("");
		expect(listViewDetail.controlParentName).toEqual("client");
		expect(listViewDetail.defaultKind).toEqual("Control");
		expect(listViewDetail.onclick).toEqual("");
		expect(listViewDetail.onmousedown).toEqual("");
		expect(listViewDetail.onmouseup).toEqual("");
		console.log(listViewDetail.layoutKind);
		//expect(listViewDetail.layoutKind).toEqual("");
		expect(listViewDetail.style).toEqual("");
		expect(listViewDetail.className).toEqual("");
		expect(listViewDetail.container).toEqual(null);
		expect(listViewDetail.parent).toEqual(null);
		expect(listViewDetail.generated).toEqual(false);
		expect(listViewDetail.allowHtml).toEqual(false);
		expect(listViewDetail.content).toEqual("");
		expect(listViewDetail.nodeTag).toEqual("div");
		expect(listViewDetail.node).toEqual(null);
		expect(listViewDetail.showing).toEqual(true);
		expect(listViewDetail.prepend).toEqual(false);
		expect(listViewDetail.wantsEvents).toEqual(true);
	});
	describe('test (itemMousehold)', function(){
	});
	describe('test (create)', function(){
		it('should change strCurrListID',function(){
			var tempVal = listViewDetail.strCurrListID;
			listViewDetail.create();
			expect(listViewDetail.strCurrListID).not.toEqual(tempVal);
		});
		it('shouldnt change others',function(){
			expect(listViewDetail.intCurrTrack).toEqual(-1);
			expect(listViewDetail.intCurrTrackOrigIndex).toEqual(-1);
			expect(listViewDetail.boolSortAsc).toEqual(true);
			expect(listViewDetail.strSortMode).toEqual("album");
		});
		it('should call inherited',function(){
			spyOn(listViewDetail,'inherited');
			listViewDetail.create();
			expect(listViewDetail.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (renderList)', function(){
		beforeEach(function(){
			listViewDetail.setStrHeaderTitle = function(){};
		});
		it('should change arSongs',function(){
			listViewDetail.renderList();
			expect(listViewDetail.arSongs).toEqual([]);
		});
		it('should call $.ctrlListViewHeader.setIntHeaderCount',function(){
			spyOn(listViewDetail.$.ctrlListViewHeader,'setIntHeaderCount');
			listViewDetail.renderList();
			expect(listViewDetail.$.ctrlListViewHeader.setIntHeaderCount).toHaveBeenCalledWith("");
		});
		it('should call Utilities.generateGuid',function(){
			var temp = jasmine.any();
			spyOn(Utilities,'generateGuid');
			listViewDetail.renderList(temp);
			expect( Utilities.generateGuid).toHaveBeenCalled();
		});
		it('should change strCurrListID',function(){
			var old = listViewDetail.strCurrListID;
			var temp = jasmine.any();
			spyOn(Utilities,'generateGuid');
			listViewDetail.renderList(temp);
			expect(listViewDetail.strCurrListID).not.toBe(old);
		});
		it('should set objCurr',function(){
			var temp = jasmine.any();
			listViewDetail.renderList(temp);
			expect(listViewDetail.objCurr).toBe(temp);
		});
		it('should call listQuery',function(){
			var temp = jasmine.any();
			spyOn(listViewDetail,'listQuery');
			listViewDetail.renderList(temp);
			expect(listViewDetail.listQuery).toHaveBeenCalled();
		});

		it('should setStrHeaderTitle to name',function(){
			var temp = {name : "hi"};
			spyOn(listViewDetail,'setStrHeaderTitle');
			listViewDetail.renderList(temp);
			expect(listViewDetail.setStrHeaderTitle).toHaveBeenCalledWith("hi");
		});
		it('should hide listMedia',function(){
			var temp = {name : "hi"};
			spyOn(listViewDetail.$.listMedia,'hide');
			listViewDetail.renderList(temp);
			expect(listViewDetail.$.listMedia.hide).toHaveBeenCalled();
		});
		
		
	});
	describe('test (listQuery)', function(){
		it('should call doRequestMedia on genre',function(){
			listViewDetail.listViewDetailType = 'genre';
			spyOn(listViewDetail,'doRequestMedia');
			listViewDetail.listQuery();
			expect(listViewDetail.doRequestMedia).toHaveBeenCalled();
		});
		it('should call doRequestMedia on album',function(){
			listViewDetail.listViewDetailType = 'album';
			spyOn(listViewDetail,'doRequestMedia');
			listViewDetail.listQuery();
			expect(listViewDetail.doRequestMedia).toHaveBeenCalled();
		});
		it('should call doRequestMedia on artist',function(){
			listViewDetail.listViewDetailType = 'artist';
			spyOn(listViewDetail,'doRequestMedia');
			listViewDetail.listQuery();
			expect(listViewDetail.doRequestMedia).toHaveBeenCalled();
		});
		it('shouldnt call doRequestMedia on anything else',function(){
			listViewDetail.listViewDetailType = jasmine.any();
			spyOn(listViewDetail,'doRequestMedia');
			listViewDetail.listQuery();
			expect(listViewDetail.doRequestMedia).not.toHaveBeenCalled();
		});
		
		it('should call doRequestMedia with correct params on genre',function(){
			listViewDetail.listViewDetailType = 'genre';
			spyOn(listViewDetail,'doRequestMedia');
			listViewDetail.listQuery();
			expect(listViewDetail.doRequestMedia).toHaveBeenCalled();
			expect(listViewDetail.doRequestMedia.mostRecentCall.args[0].desc).toBe(!listViewDetail.boolSortAsc);
			expect(listViewDetail.doRequestMedia.mostRecentCall.args[0].order).toBe(listViewDetail.strSortMode);
			
		});
		
		it('should call doRequestMedia with correct params on genre again',function(){
			listViewDetail.listViewDetailType = 'genre';
			listViewDetail.strSortMode = "songs";
			listViewDetail.boolSortAsc = true;
			spyOn(listViewDetail,'doRequestMedia');
			listViewDetail.listQuery();
			expect(listViewDetail.doRequestMedia).toHaveBeenCalled();
			expect(listViewDetail.doRequestMedia.mostRecentCall.args[0].desc).toBe(!listViewDetail.boolSortAsc);
			expect(listViewDetail.doRequestMedia.mostRecentCall.args[0].order).toBe(listViewDetail.strSortMode);
			
		});
		
		it('should call doRequestMedia with correct params on album',function(){
			listViewDetail.listViewDetailType = 'album';
			spyOn(listViewDetail,'doRequestMedia');
			listViewDetail.listQuery();
			expect(listViewDetail.doRequestMedia).toHaveBeenCalled();
			expect(listViewDetail.doRequestMedia.mostRecentCall.args[0].order).toBe("sortKey.trackAndDisc");
			
		});
		it('should call doRequestMedia with correct params on album again',function(){
			listViewDetail.listViewDetailType = 'album';
			listViewDetail.strSortMode = "yay";
			spyOn(listViewDetail,'doRequestMedia');
			listViewDetail.listQuery();
			expect(listViewDetail.doRequestMedia).toHaveBeenCalled();
			expect(listViewDetail.doRequestMedia.mostRecentCall.args[0].order).toBe("sortKey.trackAndDisc");
			
		});
		
		it('should call doRequestMedia with correct params on artist',function(){
			listViewDetail.listViewDetailType = 'artist';
			spyOn(listViewDetail,'doRequestMedia');
			listViewDetail.listQuery();
			expect(listViewDetail.doRequestMedia).toHaveBeenCalled();
			expect(listViewDetail.doRequestMedia.mostRecentCall.args[0].order).toBe("sortKey.albumDiscAndTrack");
			
		});
		it('should call doRequestMedia with correct params on artist again',function(){
			listViewDetail.listViewDetailType = 'artist';
			listViewDetail.strSortMode = "yay";
			spyOn(listViewDetail,'doRequestMedia');
			listViewDetail.listQuery();
			expect(listViewDetail.doRequestMedia).toHaveBeenCalled();
			expect(listViewDetail.doRequestMedia.mostRecentCall.args[0].order).toBe("sortKey.albumDiscAndTrack");
			
		});
		
	});
	describe('test (refreshList)', function(){
		it('should call $.listMedia.refresh',function(){
			spyOn(listViewDetail.$.listMedia,'refresh');
			listViewDetail.refreshList();
			expect(listViewDetail.$.listMedia.refresh).toHaveBeenCalled();
		});
		it('should call doRequestCurrTrack',function(){
			spyOn(listViewDetail,'doRequestCurrTrack');
			listViewDetail.refreshList();
			expect(listViewDetail.doRequestCurrTrack).toHaveBeenCalled();
		});
	});
	describe('test (resizeList)', function(){
		it('should call $.listMedia.resized',function(){
			spyOn(listViewDetail.$.listMedia,'resized');
			listViewDetail.resizeList();
			expect(listViewDetail.$.listMedia.resized).toHaveBeenCalled();
		});
	});
	describe('test (setStrHeaderTitle)', function(){
		it("shouldc call listViewDetail.$.ctrlListViewHeader.setStrHeaderTitle", function(){
			var temp = jasmine.any();
			listViewDetail.$.ctrlListViewHeader.setStrHeaderTitle = function(){};
			spyOn(listViewDetail.$.ctrlListViewHeader, 'setStrHeaderTitle');
			listViewDetail.setStrHeaderTitle(temp);
			expect(listViewDetail.$.ctrlListViewHeader.setStrHeaderTitle).toHaveBeenCalledWith(temp);
		});
	});
	describe('testing (gotSongs) with params first', function(){
		var query;
		beforeEach(function(){
			listViewDetail.arSongs = [];
			query = [{"returnValue":true,"results":[{"title":"1"},{"title":"2"},{"title":"3"}],"count":3,"next":"next"},{"objGetMediaRequest":{"page":true}}];
		})
		
		it('should change arSongs',function(){
			listViewDetail.gotSongs(query[0], query[1]);
			expect(listViewDetail.arSongs).toEqual(query[0].results);
		});
		it('should change strDbNext',function(){
			listViewDetail.gotSongs(query[0], query[1]);
			expect(listViewDetail.strDbNext).toEqual(query[0].next);
		});
		it('should change boolListRendered',function(){
			listViewDetail.gotSongs(query[0], query[1]);
			expect(listViewDetail.boolListRendered).toEqual(true);
		});
		it('should call listQuery',function(){
			spyOn(listViewDetail,'listQuery');
			listViewDetail.gotSongs(query[0], query[1]);
			expect(listViewDetail.listQuery).toHaveBeenCalled();
		});
		it('shouldn\'t punt',function(){
			spyOn(listViewDetail.$.listMedia,'punt');
			listViewDetail.gotSongs(query[0],query[1]);
			expect(listViewDetail.$.listMedia.punt).not.toHaveBeenCalled();
		});
	});
	describe('testing (gotSongs) with params last', function(){
		var query;
		beforeEach(function(){
			listViewDetail.arSongs = [];
			query = [{"returnValue":true,"results":[{"title":"1"},{"title":"2"},{"title":"3"}],"count":3},{"objGetMediaRequest":{"page":true}}];
		})
		
		it('should change arSongs',function(){
			listViewDetail.gotSongs(query[0], query[1]);
			expect(listViewDetail.arSongs).toEqual(query[0].results);
		});
		it('should change strDbNext',function(){
			listViewDetail.gotSongs(query[0], query[1]);
			expect(listViewDetail.strDbNext).toEqual("");
		});
		it('should change boolListRendered',function(){
			listViewDetail.gotSongs(query[0], query[1]);
			expect(listViewDetail.boolListRendered).toEqual(true);
		});
		it('should not call listQuery',function(){
			spyOn(listViewDetail,'listQuery');
			listViewDetail.gotSongs(query[0], query[1]);
			expect(listViewDetail.listQuery).not.toHaveBeenCalled();
		});
		
		it("should call $.ctrlListViewHeader.setIntHeaderCount(", function(){
			spyOn(listViewDetail.$.ctrlListViewHeader,'setIntHeaderCount');
			listViewDetail.gotSongs(query[0],query[1]);
			expect(listViewDetail.$.ctrlListViewHeader.setIntHeaderCount).toHaveBeenCalledWith(3);
		});
		
		it("should set strCurrAlbum to empty string", function(){
			listViewDetail.strCurrAlbum = "awdf";
			listViewDetail.gotSongs(query[0],query[1]);
			expect(listViewDetail.strCurrAlbum).toBe("");
		
		});
		it("should show listmedia", function(){
			spyOn(listViewDetail.$.listMedia,'show');
			listViewDetail.gotSongs(query[0],query[1]);
			expect(listViewDetail.$.listMedia.show).toHaveBeenCalled();
		});
		it("should resized listmedia", function(){
			spyOn(listViewDetail.$.listMedia,'resized');
			listViewDetail.gotSongs(query[0],query[1]);
			expect(listViewDetail.$.listMedia.resized).toHaveBeenCalled();
		});
		it("should punt listmedia", function(){
			spyOn(listViewDetail.$.listMedia,'punt');
			listViewDetail.gotSongs(query[0],query[1]);
			expect(listViewDetail.$.listMedia.punt).toHaveBeenCalled();
		});
		it("should doRequestcurrTrack listViewDetail", function(){
			spyOn(listViewDetail,'doRequestCurrTrack');
			listViewDetail.gotSongs(query[0],query[1]);
			expect(listViewDetail.doRequestCurrTrack).toHaveBeenCalled();
		});
		
	});
	describe('testing (gotSongs) with params 2', function(){
		beforeEach(function(){
			listViewDetail.arSongs = [];
		});
		it('should change arSongs',function(){
			listViewDetail.gotSongs({"returnValue":true,"results":[{"title":"1"},{"title":"2"},{"title":"3"}],"count":3,"next":"next"},{"objGetMediaRequest":{"page":false}});
			expect(listViewDetail.arSongs).toEqual([{"title":"1"},{"title":"2"},{"title":"3"}]);
		});
		it('should call listQuery',function(){
			spyOn(listViewDetail,'listQuery');
			listViewDetail.gotSongs({"returnValue":true,"results":[{"title":"1"},{"title":"2"},{"title":"3"}],"count":3,"next":"next"},{"objGetMediaRequest":{"page":false}});
			expect(listViewDetail.listQuery).toHaveBeenCalled();
		});
		it('shouldn\'t punt',function(){
			spyOn(listViewDetail.$.listMedia,'punt');
			listViewDetail.gotSongs({"returnValue":true,"results":[{"title":"1"},{"title":"2"},{"title":"3"}],"count":3,"next":"next"},{"objGetMediaRequest":{"page":false}});
			expect(listViewDetail.$.listMedia.punt).not.toHaveBeenCalled();
		});
	});
	
	
	describe('testing (gotSongs) with params [{"returnValue":true,"results":[],"count":0,"next":""},{"objGetMediaRequest":{"page":false}}]', function(){
		beforeEach(function(){
			listViewDetail.arSongs = [];
		});
		
		it('should change strDbNext',function(){
			listViewDetail.gotSongs({"returnValue":true,"results":[],"count":0,"next":""},{"objGetMediaRequest":{"page":false}});
			expect(listViewDetail.strDbNext).toEqual("");
		});
		it('should change strCurrAlbum',function(){
			var temp = "asdf";
			listViewDetail.strCurrAlbum = temp;
			listViewDetail.gotSongs({"returnValue":true,"results":[],"count":0,"next":""},{"objGetMediaRequest":{"page":false}});
			expect(listViewDetail.strCurrAlbum).toEqual(temp);
		});
		it('should call $.ctrlListViewHeader.setIntHeaderCount',function(){
			spyOn(listViewDetail.$.ctrlListViewHeader,'setIntHeaderCount');
			listViewDetail.gotSongs({"returnValue":true,"results":[],"count":0,"next":""},{"objGetMediaRequest":{"page":false}});
			expect(listViewDetail.$.ctrlListViewHeader.setIntHeaderCount).toHaveBeenCalledWith(0);
		});
		it('should punt',function(){
			spyOn(listViewDetail.$.listMedia,'punt');
			listViewDetail.gotSongs({"returnValue":true,"results":[],"count":0,"next":""},{"objGetMediaRequest":{"page":false}});
			expect(listViewDetail.$.listMedia.punt).toHaveBeenCalled();
		});
	});
	describe('test (onclick_Sort)', function(){
		var objSender;
		beforeEach(function(){
			listViewDetail.arSongs = ["one", "two", "three"];
			objSender = {name:"lblSortArtist", addClass: function(){}, addRemoveClass: function(){}};
			listViewDetail.resetMusicListHeaders = function(){};
		});
		it("should set strSortMode to title", function(){
			objSender.name = "lblSortSong";
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.strSortMode).toBe("title");
		});
		it("should set strSortMode to pos", function(){
			objSender.name = "lblSortPos";
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.strSortMode).toBe("pos");
		});
		it("should set strSortMode to Artist", function(){
			objSender.name = "lblSortArtist";
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.strSortMode).toBe("artist");
		});
		it("should set strSortMode to Album", function(){
			objSender.name = "lblSortAlbum";
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.strSortMode).toBe("album");
		});
		it("should not set strSortMode to anything", function(){
			listViewDetail.strSortMode = "asdf"
			listViewDetail.onclick_Sort({name:"lblSortsdffsbg"}, {rowIndex:0});
			expect(listViewDetail.strSortMode).toBe("asdf");
		});
		it("should do sorting logic properlylblSortSong", function(){
			objSender.name = "lblSortSong";
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.boolSortAsc).toBe(true);
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.boolSortAsc).toBe(false);	
			//boolNewSortAsc = this.strSortMode !== sortType || (this.strSortMode === sortType && !this.boolSortAsc);
	
		});
		it("should do sorting logic properlylblSortPos", function(){
			objSender.name = "lblSortPos";
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.boolSortAsc).toBe(true);
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.boolSortAsc).toBe(false);
			//boolNewSortAsc = this.strSortMode !== sortType || (this.strSortMode === sortType && !this.boolSortAsc);
	
		});
		it("should do sorting logic properlylblSortAlbum", function(){
			objSender.name = "lblSortAlbum";
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.boolSortAsc).toBe(false);
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.boolSortAsc).toBe(true);
					
			//boolNewSortAsc = this.strSortMode !== sortType || (this.strSortMode === sortType && !this.boolSortAsc);
	
		});
		it("should do sorting logic properlylblSortArtist", function(){
			objSender.name = "lblSortArtist";
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.boolSortAsc).toBe(true);
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.boolSortAsc).toBe(false);
			//boolNewSortAsc = this.strSortMode !== sortType || (this.strSortMode === sortType && !this.boolSortAsc);
	
		});
		it("should resetMusicListHeaders", function(){
			spyOn(listViewDetail, "resetMusicListHeaders");
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewDetail.resetMusicListHeaders).toHaveBeenCalled();
		});
		
		it("should call objSender calls", function(){
			spyOn(objSender, "addClass");
			spyOn(objSender, "addRemoveClass");
			listViewDetail.onclick_Sort(objSender, {rowIndex:0});
			expect(objSender.addClass).toHaveBeenCalled();
			expect(objSender.addRemoveClass).toHaveBeenCalled();
		});
		
		describe("with song listviewDetailtype",function(){
			beforeEach(function(){
				listViewDetail.listViewDetailType = "genre";
			});
			it("if genere should call listquery", function(){
				spyOn(listViewDetail, "listQuery");
				listViewDetail.onclick_Sort(objSender, {rowIndex:0});
				expect(listViewDetail.listQuery).toHaveBeenCalled();
			});
			it("shoud not sort", function(){
				spyOn(LibraryTools, "sort");
				listViewDetail.onclick_Sort(objSender, {rowIndex:0});
				expect(LibraryTools.sort).not.toHaveBeenCalled();
			});
			it("shoud not refresh listmedia", function(){
				spyOn(listViewDetail.$.listMedia, "refresh");
				listViewDetail.onclick_Sort(objSender, {rowIndex:0});
				expect(listViewDetail.$.listMedia.refresh).not.toHaveBeenCalled();
			});
			it("shoud generateGuid", function(){
				spyOn(Utilities, "generateGuid");
				listViewDetail.onclick_Sort(objSender, {rowIndex:0});
				expect(Utilities.generateGuid).toHaveBeenCalled();
			});
			it("should do list changed", function(){
				spyOn(listViewDetail, "doListChanged");
				listViewDetail.onclick_Sort(objSender, {rowIndex:0});
				expect(listViewDetail.doListChanged).toHaveBeenCalled();
			});
		});
		describe("with song listviewDetailtype",function(){
			beforeEach(function(){
				listViewDetail.listViewDetailType = "song";
			});
			it("if other should not call listquery", function(){
				spyOn(listViewDetail, "listQuery");
				listViewDetail.onclick_Sort(objSender, {rowIndex:0});
				expect(listViewDetail.listQuery).not.toHaveBeenCalled();
			});
			it("shoud sort", function(){
				spyOn(LibraryTools, "sort");
				listViewDetail.onclick_Sort(objSender, {rowIndex:0});
				expect(LibraryTools.sort).toHaveBeenCalled();
			});
			it("shoud refresh listmedia", function(){
				spyOn(listViewDetail.$.listMedia, "refresh");
				listViewDetail.onclick_Sort(objSender, {rowIndex:0});
				expect(listViewDetail.$.listMedia.refresh).toHaveBeenCalled();
			});
			//This needs to be done, or it doesn't play correctly after sort
			it("shoud generateGuid", function(){
				spyOn(Utilities, "generateGuid");
				listViewDetail.onclick_Sort(objSender, {rowIndex:0});
				expect(Utilities.generateGuid).toHaveBeenCalled();
			});
			
			it("should do list changed", function(){
				spyOn(listViewDetail, "doListChanged");
				listViewDetail.onclick_Sort(objSender, {rowIndex:0});
				expect(listViewDetail.doListChanged).toHaveBeenCalled();
			});
		});
		
		
	});
	describe('test (updateUISongInfo)', function(){
		var objSong;
		beforeEach(function(){
			listViewDetail.$.songTitle = {setContent: function(){}};
			listViewDetail.$.songTrack = {setContent: function(){}};
			objSong = {title: "myTitle", track: {position: null}};
		});
		it("should always set content on title", function(){
			spyOn(listViewDetail.$.songTitle, "setContent");
			listViewDetail.updateUISongInfo(objSong);
			expect(listViewDetail.$.songTitle.setContent).toHaveBeenCalledWith(objSong.title);
		});
		it("should always set content on track", function(){
			spyOn(listViewDetail.$.songTrack, "setContent");
			listViewDetail.updateUISongInfo(objSong);
			expect(listViewDetail.$.songTrack.setContent).toHaveBeenCalledWith("-");
		});
		
		it("should always set content on track using position if it exists", function(){
			objSong.track.position = 5;
			spyOn(listViewDetail.$.songTrack, "setContent");
			listViewDetail.updateUISongInfo(objSong);
			expect(listViewDetail.$.songTrack.setContent).toHaveBeenCalledWith(5);
		});
		it("should always set content on track using position if it exists 2", function(){
			objSong.track.position = 2;
			spyOn(listViewDetail.$.songTrack, "setContent");
			listViewDetail.updateUISongInfo(objSong);
			expect(listViewDetail.$.songTrack.setContent).toHaveBeenCalledWith(2);
		});
		
		
	});
	describe('test (resetMusicListHeaders)', function(){
		beforeEach(function(){
			listViewDetail.$.header.children[0].addClass = function(){};
			listViewDetail.$.header.children[1].addClass = function(){};
			listViewDetail.$.header.children[2].addClass = function(){};
			listViewDetail.$.header.children[0].removeClass = function(){};
			listViewDetail.$.header.children[1].removeClass = function(){};
			listViewDetail.$.header.children[2].removeClass = function(){};

			listViewDetail.$.header.children[listViewDetail.$.header.children.length-1].addClass = function(){};
			listViewDetail.$.header.children[listViewDetail.$.header.children.length-1].removeClass = function(){};
		});
		it('should call $.header.children[0].addClass',function(){
			spyOn(listViewDetail.$.header.children[0],'addClass');
			listViewDetail.resetMusicListHeaders();
			expect(listViewDetail.$.header.children[0].addClass).toHaveBeenCalledWith("column");
		});
		it('should call $.header.children[1].addClass',function(){
			spyOn(listViewDetail.$.header.children[1],'addClass');
			listViewDetail.resetMusicListHeaders();
			expect(listViewDetail.$.header.children[1].addClass).toHaveBeenCalledWith("column");
		});
		it('should call $.header.children[2].addClass',function(){
			spyOn(listViewDetail.$.header.children[2],'addClass');
			listViewDetail.resetMusicListHeaders();
			expect(listViewDetail.$.header.children[2].addClass).toHaveBeenCalledWith("column");
		});		
		it('should call $.header.children[0].removeClass',function(){
			spyOn(listViewDetail.$.header.children[0],'removeClass');
			listViewDetail.resetMusicListHeaders();
			expect(listViewDetail.$.header.children[0].removeClass).toHaveBeenCalledWith("sorted");
			expect(listViewDetail.$.header.children[0].removeClass).toHaveBeenCalledWith("asc");
			expect(listViewDetail.$.header.children[0].removeClass).toHaveBeenCalledWith("desc");
			
		});
		it('should call $.header.children[1].removeClass',function(){
			spyOn(listViewDetail.$.header.children[1],'removeClass');
			listViewDetail.resetMusicListHeaders();
			expect(listViewDetail.$.header.children[1].removeClass).toHaveBeenCalledWith("sorted");
			expect(listViewDetail.$.header.children[1].removeClass).toHaveBeenCalledWith("asc");
			expect(listViewDetail.$.header.children[1].removeClass).toHaveBeenCalledWith("desc");
		});
		it('should call $.header.children[2].removeClass',function(){
			spyOn(listViewDetail.$.header.children[2],'removeClass');
			listViewDetail.resetMusicListHeaders();
			expect(listViewDetail.$.header.children[2].removeClass).toHaveBeenCalledWith("sorted");
			expect(listViewDetail.$.header.children[2].removeClass).toHaveBeenCalledWith("asc");
			expect(listViewDetail.$.header.children[2].removeClass).toHaveBeenCalledWith("desc");
		});
		it('should call $.header.children[listViewDetail.$.header.children.length-1].removeClass',function(){
			spyOn(listViewDetail.$.header.children[listViewDetail.$.header.children.length-1],'removeClass');
			listViewDetail.resetMusicListHeaders();
			expect(listViewDetail.$.header.children[listViewDetail.$.header.children.length-1].removeClass).toHaveBeenCalledWith("sorted");
			expect(listViewDetail.$.header.children[listViewDetail.$.header.children.length-1].removeClass).toHaveBeenCalledWith("asc");
			expect(listViewDetail.$.header.children[listViewDetail.$.header.children.length-1].removeClass).toHaveBeenCalledWith("desc");
		});
	});
	describe('test (onclick_listMedia)', function(){
		/*it('should call doSetPlaybackList',function(){
			spyOn(listViewDetail,'doSetPlaybackList');
			listViewDetail.onclick_listMedia(null,{"rowIndex":0});
			expect(listViewDetail.doSetPlaybackList).toHaveBeenCalledWith({arSetPlaybackList	: listViewDetail.arSongs, 
				intStartTrackIndex	: 0, 
				intStartTrackTime	: 0, 
				strOriginListID		: listViewDetail.strCurrListID, 
				strListType			: Utilities.capitalize(listViewDetail.listViewDetailType), 
				strListText			: listViewDetail.objCurr.name, 
				strListSubText		: "", 
				strArtist			: listViewDetail.objCurr.artist,
				arThumbnails		: listViewDetail.objCurr.thumbnails});
			});
		
		it('should call doSetPlaybackList onj other index',function(){
			spyOn(listViewDetail,'doSetPlaybackList');
			listViewDetail.onclick_listMedia(null,{"rowIndex":1});
			expect(listViewDetail.doSetPlaybackList).toHaveBeenCalledWith({arSetPlaybackList	: listViewDetail.arSongs, 
				intStartTrackIndex	: 1, 
				intStartTrackTime	: 0, 
				strOriginListID		: listViewDetail.strCurrListID, 
				strListType			: Utilities.capitalize(listViewDetail.listViewDetailType), 
				strListText			: listViewDetail.objCurr.name, 
				strListSubText		: "", 
				strArtist			: listViewDetail.objCurr.artist,
				arThumbnails		: listViewDetail.objCurr.thumbnails});
			});*/
	});
	/*describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(listViewDetail,'_constructor');
			listViewDetail.base();
			expect(listViewDetail._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(listViewDetail,'constructed');
			listViewDetail.base();
			expect(listViewDetail.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(listViewDetail,'_constructor');
			listViewDetail.ctor();
			expect(listViewDetail._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(listViewDetail,'constructed');
			listViewDetail.ctor();
			expect(listViewDetail.constructed).toHaveBeenCalled();
		});
	});*/
	describe('test (doSetPlaybackList)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(listViewDetail,'dispatchIndirectly');
			listViewDetail.doSetPlaybackList();
			expect(listViewDetail.dispatchIndirectly).toHaveBeenCalledWith("onSetPlaybackList",{});
		});
	});
	describe('test (doRequestMedia)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(listViewDetail,'dispatchIndirectly');
			listViewDetail.doRequestMedia();
			expect(listViewDetail.dispatchIndirectly).toHaveBeenCalledWith("onRequestMedia",{});
		});
	});
	describe('test (doRequestCurrTrack)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(listViewDetail,'dispatchIndirectly');
			listViewDetail.doRequestCurrTrack();
			expect(listViewDetail.dispatchIndirectly).toHaveBeenCalledWith("onRequestCurrTrack",{});
		});
	});
	describe('test (doListChanged)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(listViewDetail,'dispatchIndirectly');
			listViewDetail.doListChanged();
			expect(listViewDetail.dispatchIndirectly).toHaveBeenCalledWith("onListChanged",{});
		});
	});
	describe('test (doListSorted)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(listViewDetail,'dispatchIndirectly');
			listViewDetail.doListSorted();
			expect(listViewDetail.dispatchIndirectly).toHaveBeenCalledWith("onListSorted",{});
		});
	});
	describe('test (getBoolViewActive)', function(){
		it('should call getProperty',function(){
			spyOn(listViewDetail,'getProperty');
			listViewDetail.getBoolViewActive();
			expect(listViewDetail.getProperty).toHaveBeenCalledWith("boolViewActive");
		});
	});
	describe('test (setBoolViewActive)', function(){
	});
	describe('test (getBoolListRendered)', function(){
		it('should call getProperty',function(){
			spyOn(listViewDetail,'getProperty');
			listViewDetail.getBoolListRendered();
			expect(listViewDetail.getProperty).toHaveBeenCalledWith("boolListRendered");
		});
	});
	describe('test (setBoolListRendered)', function(){
	});
	describe('test (callAddToPlaylist)', function(){
		it('should call doAddToPlaylist',function(){
			spyOn(listViewDetail,'doAddToPlaylist');
			listViewDetail.callAddToPlaylist();
			expect(listViewDetail.doAddToPlaylist).toHaveBeenCalledWith({"strMediaType":"song","arTracks":[{}]});
		});
	});
	describe('test (songMousehold)', function(){
	describe('testing (songMousehold) with params [null,{"rowIndex":0}]', function(){
		it('should change dragObjType',function(){
			listViewDetail.songMousehold(null,{"rowIndex":0});
			expect(listViewDetail.dragObjType).toEqual("song");
		});
		
		it('should call itemMousehold',function(){
			spyOn(listViewDetail,'itemMousehold');
			listViewDetail.songMousehold(null,{"rowIndex":0});
			expect(listViewDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0});
		});
	});
	});
	describe('test (albumMousehold)', function(){
	describe('testing (albumMousehold) with params [null,{"rowIndex":0}]', function(){
		it('should change dragObjType',function(){
			listViewDetail.albumMousehold(null,{"rowIndex":0});
			expect(listViewDetail.dragObjType).toEqual("album");
		});
		it('should call itemMousehold',function(){
			spyOn(listViewDetail,'itemMousehold');
			listViewDetail.albumMousehold(null,{"rowIndex":0});
			expect(listViewDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0});
		});
	});
	});
	describe('test (albumGroupMousehold)', function(){
	describe('testing (albumGroupMousehold) with params [null,{"rowIndex":0}]', function(){
		it('should change arItems',function(){
			listViewDetail.albumGroupMousehold(null,{"rowIndex":0});
			expect(listViewDetail.arItems).toEqual(undefined);
		});
		it('should change albumGroup',function(){
			listViewDetail.albumGroupMousehold(null,{"rowIndex":0});
			expect(listViewDetail.albumGroup).toEqual(true);
		});
		it('should call itemMousehold',function(){
			spyOn(listViewDetail,'itemMousehold');
			listViewDetail.albumGroupMousehold(null,{"rowIndex":0});
			expect(listViewDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0});
		});
	});
	});
	describe('test (artistMousehold)', function(){
	describe('testing (artistMousehold) with params [null,{"rowIndex":0}]', function(){
		it('should change dragObjType',function(){
			listViewDetail.artistMousehold(null,{"rowIndex":0});
			expect(listViewDetail.dragObjType).toEqual("artist");
		});
		it('should call itemMousehold',function(){
			spyOn(listViewDetail,'itemMousehold');
			listViewDetail.artistMousehold(null,{"rowIndex":0});
			expect(listViewDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0});
		});
	});
	});
	describe('test (genreMousehold)', function(){
	describe('testing (genreMousehold) with params [null,{"rowIndex":0}]', function(){
		it('should change dragObjType',function(){
			listViewDetail.genreMousehold(null,{"rowIndex":0});
			expect(listViewDetail.dragObjType).toEqual("genre");
		});
		it('should call itemMousehold',function(){
			spyOn(listViewDetail,'itemMousehold');
			listViewDetail.genreMousehold(null,{"rowIndex":0});
			expect(listViewDetail.itemMousehold).toHaveBeenCalledWith(null,{"rowIndex":0});
		});
	});
	});
	describe('test (itemMouserelease)', function(){
		it('should change dragObjType',function(){
			listViewDetail.itemMouserelease();
			expect(listViewDetail.dragObjType).toEqual(null);
		});
		it('should call setItemHighlighted',function(){
			spyOn(listViewDetail,'setItemHighlighted');
			listViewDetail.itemMouserelease();
			expect(listViewDetail.setItemHighlighted).toHaveBeenCalledWith(false);
		});
		it('should call doItemShowAvatar',function(){
			spyOn(listViewDetail,'doItemShowAvatar');
			listViewDetail.itemMouserelease();
			expect(listViewDetail.doItemShowAvatar).toHaveBeenCalledWith(false);
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
});