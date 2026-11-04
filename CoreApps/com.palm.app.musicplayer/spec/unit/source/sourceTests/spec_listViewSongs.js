/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return new kindListViewSongs();
//			}
//var objName = "listViewSongs";
//
//var before = function (obj, callbacks) {
//	}
//var event = [[null, {rowIndex:0, dragInfo: {}}]];
//var params = {
//};
//specOut(creator, objName, before, params);


describe('listViewSongs tests', function(){
	var creator = function (){
				return new kindListViewSongs();
			};
	var listViewSongs;
	beforeEach(function(){
		listViewSongs = creator();
		var before = function (obj, callbacks) {
	};
		var callback = function(one, fun){return fun;};
		before(listViewSongs,callback);
		
	});
	it('should correctly set up object', function(){
		expect(typeof listViewSongs.controls).toBe('object');
		expect(typeof listViewSongs.children).toBe('object');
		expect(listViewSongs._componentNameMap).toEqual({"ctrlListViewHeader":2,"spacer":3,"control":4,"image":2,"button":2});
		expect(typeof listViewSongs.$).toBe('object');
		expect(listViewSongs.domStyles).toEqual({"height":"100%"});
		expect(listViewSongs.domAttributes).toEqual({"id":"kindListViewSongs"});
		expect(typeof listViewSongs.owner).toBe('object');
		expect(listViewSongs.name).toEqual("kindListViewSongs");
		expect(listViewSongs.id).toEqual("kindListViewSongs");
		expect(listViewSongs.controlParent).toEqual(undefined);
		expect(typeof listViewSongs.layout).toBe('object');
		expect(listViewSongs.strSortMode).toEqual("sortKey.artistAlbumDiscAndTrack");
		expect(listViewSongs.boolSortAsc).toEqual(true);
		//expect(listViewSongs.strCurrListID).toEqual("C6FEB6B3CEBB-80CBB2-CCDACE-E9F820-17B1E2C624C1D3ED35");
		expect(listViewSongs.kind).toEqual("listViewDraggable");
		expect(listViewSongs.published).toEqual({"boolViewActive":false,"boolRefreshData":true});
		expect(listViewSongs.events).toEqual({"onSetPlaybackList":"","onRequestMedia":"","onRequestCurrTrackInfo":"","onListChanged":"","onListSorted":"","onEnableControls":""});
		expect(listViewSongs.height).toEqual("100%");
		expect(listViewSongs.intCurrTrack).toEqual(-1);
		expect(listViewSongs.intCurrTrackOrigIndex).toEqual(-1);
		expect(listViewSongs.strCurrTrackID).toEqual("");
		expect(listViewSongs.searchQuery).toEqual("");
		expect(listViewSongs.strDbNext).toEqual("");
		expect(listViewSongs.arSongs).toEqual([]);
		expect(listViewSongs.boolNotifyListChange).toEqual(false);
		expect(listViewSongs.kindName).toEqual("kindListViewSongs");
		//expect(listViewSongs.kindComponents).toEqual([{"kind":"ctrlListViewHeader","onSearch_Header":"onSearchSongs"},{"name":"content","kind":"Control","layoutKind":"VFlexLayout","className":"list","flex":1,"components":[{"name":"header","kind":"Control","layoutKind":"HFlexLayout","className":"divider first","align":"center","components":[{"name":"lblSortSong","content":"Song","flex":2,"className":"column","onclick":"onclick_Sort"},{"name":"lblSortArtist","content":"Artist","flex":1,"className":"column sorted","onclick":"onclick_Sort"},{"name":"lblSortAlbum","content":"Album","flex":1,"className":"column","onclick":"onclick_Sort"}]},{"kind":"DbService","method":"find","name":"SongsDB","onSuccess":"gotSongs","dbKind":"com.palm.media.audio.file:1"},{"name":"listMedia","kind":"VirtualList","onSetupRow":"listSetupRow","flex":1,"components":[{"name":"itemMedia","kind":"draggableSong","layoutKind":"HFlexLayout","className":"song","onclick":"onclick_listMedia","components":[{"kind":"Control","name":"songPlaying","className":"column","showing":true},{"name":"songTitle","flex":2,"className":"column title"},{"name":"songArtist","flex":1,"className":"column artist"},{"name":"songAlbum","flex":1,"className":"column album"}]}]}]},{"name":"empty","kind":"Control","layoutKind":"VFlexLayout","align":"center","height":"100%","pack":"justify","className":"empty","showing":false,"components":[{"kind":"Spacer","flex":1},{"kind":"Control","className":"content","components":[{"kind":"Image","src":"images/bg_empty_songs.png"},{"content":"There are no songs on your HP TouchPad","className":"heading"},{"content":"You can add music to your HP Touchpad with the HP Play music app, found at www.hpplay.com, or copy music to your HP TouchPad in USB mode."}]},{"kind":"Button","caption":"Add songs","showing":false},{"kind":"Spacer","flex":3}]},{"name":"txtResults"}]);
		expect(listViewSongs.onSetPlaybackList).toEqual("");
		expect(listViewSongs.onRequestMedia).toEqual("");
		expect(listViewSongs.onRequestCurrTrackInfo).toEqual("");
		expect(listViewSongs.onListChanged).toEqual("");
		expect(listViewSongs.onListSorted).toEqual("");
		expect(listViewSongs.onEnableControls).toEqual("");
		expect(listViewSongs.boolViewActive).toEqual(false);
		expect(listViewSongs.boolRefreshData).toEqual(true);
		expect(listViewSongs.intJumpRowOffset).toEqual(0);
		expect(listViewSongs.boolDragHold).toEqual(false);
		expect(listViewSongs.boolDragItem).toEqual(false);
		expect(listViewSongs.boolDraggingList).toEqual(false);
		expect(listViewSongs.dragObjType).toEqual(null);
		expect(listViewSongs.arItems).toEqual([]);
		expect(listViewSongs.albumGroup).toEqual(false);
		expect(listViewSongs.onAddToPlaylist).toEqual("");
		expect(listViewSongs.onItemDrag).toEqual("");
		expect(listViewSongs.onItemShowAvatar).toEqual("");
		expect(listViewSongs.onItemShowAvatarIcon).toEqual("");
		expect(listViewSongs.controlParentName).toEqual("client");
		expect(listViewSongs.defaultKind).toEqual("Control");
		expect(listViewSongs.onclick).toEqual("");
		expect(listViewSongs.onmousedown).toEqual("");
		expect(listViewSongs.onmouseup).toEqual("");
		expect(listViewSongs.style).toEqual("");
		expect(listViewSongs.className).toEqual("");
		expect(listViewSongs.container).toEqual(null);
		expect(listViewSongs.parent).toEqual(null);
		expect(listViewSongs.generated).toEqual(false);
		expect(listViewSongs.allowHtml).toEqual(false);
		expect(listViewSongs.content).toEqual("");
		expect(listViewSongs.nodeTag).toEqual("div");
		expect(listViewSongs.node).toEqual(null);
		expect(listViewSongs.showing).toEqual(true);
		expect(listViewSongs.prepend).toEqual(false);
		expect(listViewSongs.wantsEvents).toEqual(true);
	});
	describe('test (create)', function(){
		it('should change strCurrListID',function(){
			listViewSongs.strCurrListID = null;
			listViewSongs.create();
			expect(listViewSongs.strCurrListID).not.toEqual(null);
		});
		it('should set strSortMode',function(){
			listViewSongs.strSortMode = null;
			listViewSongs.create();
			expect(listViewSongs.strSortMode).toEqual("sortKey.artistAlbumDiscAndTrack");
		});
		it('should set boolSortAsc',function(){
			listViewSongs.boolSortAsc = null;
			listViewSongs.create();
			expect(listViewSongs.boolSortAsc).toEqual(true);
		});
		it('should call inherited',function(){
			spyOn(listViewSongs,'inherited');
			listViewSongs.create();
			expect(listViewSongs.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (ready)', function(){
		it('should call inherited',function(){
			spyOn(listViewSongs,'inherited');
			listViewSongs.ready();
			expect(listViewSongs.inherited).toHaveBeenCalledWith({});
		});
		it("should set setStrHeaderTitle", function(){
			spyOn(listViewSongs.$.ctrlListViewHeader,'setStrHeaderTitle');
			listViewSongs.ready();
			expect(listViewSongs.$.ctrlListViewHeader.setStrHeaderTitle).toHaveBeenCalledWith($L("Songs"));
		});
	});
	describe('test (rendered)', function(){
		it('should call inherited',function(){
			spyOn(listViewSongs,'inherited');
			listViewSongs.rendered();
			expect(listViewSongs.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (renderList)', function(){
		it('should call ctrlListViewHeader',function(){
			spyOn(listViewSongs.$.ctrlListViewHeader,'clearSearchInput');
			listViewSongs.renderList();
			expect(listViewSongs.$.ctrlListViewHeader.clearSearchInput).toHaveBeenCalledWith(false);
		});
		it('should set searchQuery',function(){
			listViewSongs.searchQuery = null;
			listViewSongs.renderList();
			expect(listViewSongs.searchQuery).toEqual("");
		});
		it('should set strDbNext',function(){
			listViewSongs.strDbNext = null;
			listViewSongs.renderList();
			expect(listViewSongs.strDbNext).toEqual("");
		});
		it('should set intJumpRowOffset',function(){
			listViewSongs.intJumpRowOffset = null;
			listViewSongs.renderList();
			expect(listViewSongs.intJumpRowOffset).toEqual(0);
		});
		it('should call listQuery',function(){
			spyOn(listViewSongs,'listQuery');
			listViewSongs.renderList();
			expect(listViewSongs.listQuery).toHaveBeenCalled();
		});
	});
	describe('test (refreshList)', function(){
		beforeEach(function(){
			spyOn(listViewSongs,'listQuery');
		});
		it('should call ctrlListViewHeader',function(){
			spyOn(listViewSongs.$.ctrlListViewHeader,'clearSearchInput');
			listViewSongs.renderList();
			expect(listViewSongs.$.ctrlListViewHeader.clearSearchInput).toHaveBeenCalledWith(false);
		});
	
		it('should call doRequestCurrTrackInfo',function(){
			spyOn(listViewSongs,'doRequestCurrTrackInfo');
			listViewSongs.refreshList();
			expect(listViewSongs.doRequestCurrTrackInfo).toHaveBeenCalled();
		});
		describe("when boolRefreshData is true", function(){
			beforeEach(function(){
				listViewSongs.boolRefreshData = true;
			});

			it('should set searchQuery',function(){
				listViewSongs.searchQuery = null;
				listViewSongs.refreshList();
				expect(listViewSongs.searchQuery).toEqual("");
			});
			it('should change boolRefreshData',function(){
				listViewSongs.boolRefreshData = true;
				listViewSongs.refreshList();
				expect(listViewSongs.boolRefreshData).toEqual(false);
			});
			it('should call listQuery',function(){
				listViewSongs.refreshList();
				expect(listViewSongs.listQuery).toHaveBeenCalled();
			});
		});
		describe("when searchQuery is true", function(){
			beforeEach(function(){
				listViewSongs.searchQuery = true;
			});

			it('should set searchQuery',function(){
				listViewSongs.searchQuery = null;
				listViewSongs.refreshList();
				expect(listViewSongs.searchQuery).toEqual("");
			});
			it('should change boolRefreshData',function(){
				listViewSongs.boolRefreshData = null;
				listViewSongs.refreshList();
				expect(listViewSongs.boolRefreshData).toEqual(false);
			});
			it('should call listQuery',function(){
				listViewSongs.refreshList();
				expect(listViewSongs.listQuery).toHaveBeenCalled();
			});
		});		
		describe("when searchQuery is '' and boolRefreshData is false", function(){
			beforeEach(function(){
				listViewSongs.searchQuery = "";
				listViewSongs.boolRefreshData = false;
			});

		
			it('shouldnt call listQuery',function(){
				listViewSongs.refreshList();
				expect(listViewSongs.listQuery).not.toHaveBeenCalled();
			});
		});
	
	});
	describe('test (resizeList)', function(){
		it("should call this.$.listMedia.resized();", function(){
			spyOn(listViewSongs.$.listMedia,'resized');
			listViewSongs.resizeList();
			expect(listViewSongs.$.listMedia.resized).toHaveBeenCalled();
		});
		
	});
	describe('test (listQuery)', function(){
		it('should change arSongs',function(){
			listViewSongs.listQuery();
			expect(listViewSongs.arSongs).toEqual([]);
		});
		it('should change lastQuery',function(){
			listViewSongs.listQuery();
			//limit set to 300 to increase perforance when scrolling fast to the bottom
			//expect(listViewSongs.lastQuery).toEqual({"where":[{"prop":"isRingtone","op":"=","val":false}],"orderBy":"artist","desc":false,"limit":500,"page":""});
		});
		it("shouldnt call this.$.SongsDB.call", function(){
			spyOn(listViewSongs.$.SongsDB, "call");
			listViewSongs.listQuery();
			expect(listViewSongs.$.SongsDB.call).toHaveBeenCalled();
		});
		describe("when query matches", function(){
			beforeEach(function(){
				listViewSongs.lastQuery = "asdf";
			});
			it('should change  this.$.SongsDB.method to find',function(){
				listViewSongs.$.SongsDB.method = "";
				listViewSongs.listQuery(listViewSongs.lastQuery,{});
				expect(listViewSongs.$.SongsDB.method).toEqual("find");
			});
			it('should change  this.$.SongsDB.method to search',function(){
				listViewSongs.$.SongsDB.method = "";
				listViewSongs.searchQuery = "asdf";
				listViewSongs.listQuery(listViewSongs.lastQuery,{});
				expect(listViewSongs.$.SongsDB.method).toEqual("search");
			});
			it("should call this.$.SongsDB.call", function(){
				spyOn(listViewSongs.$.SongsDB, "call");
				listViewSongs.listQuery(listViewSongs.lastQuery,{});
				expect(listViewSongs.$.SongsDB.call).toHaveBeenCalled();
			});
			
		});
		
		
		
		
		
		
	});
	describe('test (gotSongs)', function(){
		//should take into account tokenizing
		var songs;
		var inResponse;
		var inRequest;
		beforeEach(function(){
			songs = getJson("mock_songArr");
			inResponse = {results: songs};
			inRequest = {userdata : listViewSongs.lastQuery}
		});
		
		describe("when next", function(){
			beforeEach(function(){
				inResponse.next = true;
			});
			it("should bind listQuery", function(){
				spyOn(enyo,"bind");
				listViewSongs.gotSongs(null, inResponse, inRequest);
				expect(enyo.bind.mostRecentCall.args[1]).toBe("listQuery");
			});
			it("should set listTimeout", function(){
				listViewSongs.listTimeout = null;
				listViewSongs.gotSongs(null, inResponse, inRequest);
				expect(listViewSongs.listTimeout).not.toBe(null);
				
			});
		});
		describe("when first", function(){
			beforeEach(function(){
				listViewSongs.arSongs =  [];
			});
			it("should call setIntHeaderCount", function(){
				spyOn(listViewSongs.$.ctrlListViewHeader,"setIntHeaderCount");
				inResponse.count = 4;
				listViewSongs.gotSongs(null, inResponse, inRequest);
				expect(listViewSongs.$.ctrlListViewHeader.setIntHeaderCount).toHaveBeenCalledWith(4);
			});
			it("should call Utilities.generateGuid", function(){
				spyOn(Utilities,"generateGuid");
				listViewSongs.gotSongs(null, inResponse, inRequest);
				expect(Utilities.generateGuid).toHaveBeenCalled();
			});
			describe("when empty", function(){
				beforeEach(function(){
					inResponse.results = []
				});
				it("should hide content", function(){
					spyOn(listViewSongs.$.content,"hide");
					listViewSongs.gotSongs(null, inResponse, inRequest);
					expect(listViewSongs.$.content.hide).toHaveBeenCalled();
				});
				it("should punt listmedia", function(){
					spyOn(listViewSongs.$.listMedia,"punt");
					listViewSongs.gotSongs(null, inResponse, inRequest);
					expect(listViewSongs.$.listMedia.punt).toHaveBeenCalled();
				});
				it("should hide empty", function(){
					listViewSongs.searchQuery = "something";
					spyOn(listViewSongs.$.empty,"hide");
					listViewSongs.gotSongs(null, inResponse, inRequest);
					expect(listViewSongs.$.empty.hide).toHaveBeenCalled();
				});
				it("should hide show", function(){
					listViewSongs.searchQuery = "";
					spyOn(listViewSongs.$.empty,"show");
					listViewSongs.gotSongs(null, inResponse, inRequest);
					expect(listViewSongs.$.empty.show).toHaveBeenCalled();
				});
			});
			describe("when not empty", function(){
				beforeEach(function(){
				});
				it("should show content", function(){
					spyOn(listViewSongs.$.content,"show");
					listViewSongs.gotSongs(null, inResponse, inRequest);
					expect(listViewSongs.$.content.show).toHaveBeenCalled();
				});
				/*it("should doRequestCurrTrackInfo", function(){
					spyOn(listViewSongs,"doRequestCurrTrackInfo");
					listViewSongs.gotSongs(null, inResponse, inRequest);
					expect(listViewSongs.doRequestCurrTrackInfo).toHaveBeenCalled();
				});*/
				
				it("shoud not refresh listmedia", function(){
					spyOn(listViewSongs.$.listMedia, "refresh");
					listViewSongs.gotSongs(null, inResponse, inRequest);
					expect(listViewSongs.$.listMedia.refresh).not.toHaveBeenCalled();
				});
				
				it("should not resized listmedia", function(){
					spyOn(listViewSongs.$.listMedia,"resized");
					listViewSongs.gotSongs(null, inResponse, inRequest);
					expect(listViewSongs.$.listMedia.resized).not.toHaveBeenCalled();
				});
				
				it("should punt listmedia", function(){
					spyOn(listViewSongs.$.listMedia,"punt");
					listViewSongs.gotSongs(null, inResponse, inRequest);
					expect(listViewSongs.$.listMedia.punt).toHaveBeenCalled();
				});
				it("should hide empty", function(){
					listViewSongs.searchQuery = "something";
					spyOn(listViewSongs.$.empty,"hide");
					listViewSongs.gotSongs(null, inResponse, inRequest);
					expect(listViewSongs.$.empty.hide).toHaveBeenCalled();
				});
				it("should hide empty", function(){
					listViewSongs.searchQuery = "";
					spyOn(listViewSongs.$.empty,"hide");
					listViewSongs.gotSongs(null, inResponse, inRequest);
					expect(listViewSongs.$.empty.hide).toHaveBeenCalled();
				});
			});
		});
		
	});
	describe('test (onSearchSongs)', function(){
		it('should change intJumpRowOffset',function(){
			listViewSongs.intJumpRowOffset = 5
			listViewSongs.onSearchSongs(null,{"rowIndex":0,"dispatchTarget":null});
			expect(listViewSongs.intJumpRowOffset).toEqual(0);
		});
		it('should change arSongs',function(){
			listViewSongs.arSongs = 'asdf';
			listViewSongs.onSearchSongs(null,{"rowIndex":0,"dispatchTarget":null});
			expect(listViewSongs.arSongs).toEqual([]);
		});
		it('should change searchQuery',function(){
			listViewSongs.onSearchSongs(null," ");
			expect(listViewSongs.searchQuery).toEqual("");
		});
		
		it('should call listQuery',function(){
			spyOn(listViewSongs,'listQuery');
			listViewSongs.onSearchSongs(null,{"rowIndex":0,"dispatchTarget":null});
			expect(listViewSongs.listQuery).toHaveBeenCalled();
		});
		
		describe("when searching string", function(){
			it('should call listQuery',function(){
				spyOn(listViewSongs,'listQuery');
				listViewSongs.onSearchSongs(null,"asdf");
				expect(listViewSongs.listQuery).toHaveBeenCalled();
			});
			it('should change searchQuery',function(){
				listViewSongs.onSearchSongs(null,"asdf ");
				expect(listViewSongs.searchQuery).toEqual("asdf ");
			});
		});
		
	});
	describe('test (listSetupRow)', function(){
		beforeEach(function(){
			listViewSongs.arSongs = getJson("mock_songArr");
		});
		describe("when it exists", function(){
			var toSend = 1
			it('should set artist',function(){
				listViewSongs.$.songArtist.content = null;
				listViewSongs.listSetupRow(0,toSend);
				expect(listViewSongs.$.songArtist.content).toBe(listViewSongs.arSongs[toSend].artist);
			});
			it('should set songTitle',function(){
				listViewSongs.$.songTitle.content = null;
				listViewSongs.listSetupRow(0,toSend);
				expect(listViewSongs.$.songTitle.content).toBe(listViewSongs.arSongs[toSend].title);
			});
			it('should set songAlbum',function(){
				listViewSongs.$.songAlbum.content = null;
				listViewSongs.listSetupRow(0,toSend);
				expect(listViewSongs.$.songAlbum.content).toBe(listViewSongs.arSongs[toSend].album);
			});
			it('should call addRemoveClass',function(){
				spyOn(listViewSongs.$.itemMedia,'addRemoveClass');
				listViewSongs.listSetupRow(0,toSend);
				expect(listViewSongs.$.itemMedia.addRemoveClass).toHaveBeenCalledWith("odd", false);
			});

			it('should call addRemoveClass true',function(){
				listViewSongs.strCurrTrackID = listViewSongs.arSongs[toSend]._id
				spyOn(listViewSongs.$.itemMedia,'addRemoveClass');
				listViewSongs.listSetupRow(0,toSend);
				expect(listViewSongs.$.itemMedia.addRemoveClass).toHaveBeenCalledWith("playingHere", true);
		
			});

			it('should call addRemoveClass false',function(){
				spyOn(listViewSongs.$.itemMedia,'addRemoveClass');
				listViewSongs.listSetupRow(0,toSend);
				expect(listViewSongs.$.itemMedia.addRemoveClass).toHaveBeenCalledWith("odd", false);
		
			});
			it('should call addRemoveClass true',function(){
				spyOn(listViewSongs.$.itemMedia,'addRemoveClass');
				listViewSongs.listSetupRow(0,toSend+1);
				expect(listViewSongs.$.itemMedia.addRemoveClass).toHaveBeenCalledWith("odd", true);
		
			});
		});
		describe("when it doesn't", function(){
			var toSend = 1000000
			it('shouldnt set artist',function(){
				listViewSongs.$.songArtist.content = null;
				listViewSongs.listSetupRow(0,toSend);
				expect(listViewSongs.$.songArtist.content).toBe(null);
			});
			it('shouldnt set songTitle',function(){
				listViewSongs.$.songTitle.content = null;
				listViewSongs.listSetupRow(0,toSend);
				expect(listViewSongs.$.songTitle.content).toBe(null);
			});
			it('shouldnt set songAlbum',function(){
				listViewSongs.$.songAlbum.content = null;
				listViewSongs.listSetupRow(0,toSend);
				expect(listViewSongs.$.songAlbum.content).toBe(null);
			});
			it('shouldnt call addRemoveClass',function(){
				spyOn(listViewSongs.$.itemMedia,'addRemoveClass');
				listViewSongs.listSetupRow(0,toSend);
				expect(listViewSongs.$.itemMedia.addRemoveClass).not.toHaveBeenCalled();
			});

			
		});
		
		
	});
	describe('test (onclick_Sort)', function(){
		var objSender;
		beforeEach(function(){
			listViewSongs.arSongs = ["one", "two", "three"];
			objSender = {name:"lblSortArtist", addClass: function(){}, addRemoveClass: function(){}};
			listViewSongs.resetMusicListHeaders = function(){};
		});
		it("should set strSortMode to title", function(){
			objSender.name = "lblSortSong";
			listViewSongs.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewSongs.strSortMode).toBe("title");
		});
		it("should set strSortMode to Artist", function(){
			objSender.name = "lblSortArtist";
			listViewSongs.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewSongs.strSortMode).toBe("sortKey.artistAlbumDiscAndTrack");
		});
		it("should set strSortMode to Album", function(){
			objSender.name = "lblSortAlbum";
			listViewSongs.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewSongs.strSortMode).toBe("sortKey.albumDiscAndTrack");
		});
		it("should not set strSortMode to anything", function(){
			listViewSongs.strSortMode = "asdf"
				objSender.name = "asdfasdf";
			listViewSongs.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewSongs.strSortMode).toBe("asdf");
		});
		it("should do sorting logic properlylblSortSong", function(){
			objSender.name = "lblSortSong";
			listViewSongs.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewSongs.boolSortAsc).toBe(true);
			listViewSongs.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewSongs.boolSortAsc).toBe(false);	
			//boolNewSortAsc = this.strSortMode !== sortType || (this.strSortMode === sortType && !this.boolSortAsc);
	
		});
		it("should do sorting logic properlylblSortAlbum", function(){
			objSender.name = "lblSortAlbum";
			listViewSongs.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewSongs.boolSortAsc).toBe(true);
			listViewSongs.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewSongs.boolSortAsc).toBe(false);
					
			//boolNewSortAsc = this.strSortMode !== sortType || (this.strSortMode === sortType && !this.boolSortAsc);
	
		});
		it("should do sorting logic properlylblSortArtist", function(){
			objSender.name = "lblSortArtist";
			listViewSongs.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewSongs.boolSortAsc).toBe(false);
			listViewSongs.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewSongs.boolSortAsc).toBe(true);
			//boolNewSortAsc = this.strSortMode !== sortType || (this.strSortMode === sortType && !this.boolSortAsc);
	
		});
		it("should resetMusicListHeaders", function(){
			spyOn(listViewSongs, "resetMusicListHeaders");
			listViewSongs.onclick_Sort(objSender, {rowIndex:0});
			expect(listViewSongs.resetMusicListHeaders).toHaveBeenCalled();
		});
		
		it("should call objSender calls", function(){
			spyOn(objSender, "addClass");
			spyOn(objSender, "addRemoveClass");
			listViewSongs.onclick_Sort(objSender, {rowIndex:0});
			expect(objSender.addClass).toHaveBeenCalled();
			expect(objSender.addRemoveClass).toHaveBeenCalled();
		});
		
		describe("with song listViewSongstype",function(){
			beforeEach(function(){
				listViewSongs.listViewSongsType = "genre";
			});
			it("if genere should call listquery", function(){
				spyOn(listViewSongs, "listQuery");
				listViewSongs.onclick_Sort(objSender, {rowIndex:0});
				expect(listViewSongs.listQuery).toHaveBeenCalled();
			});
			it("shoud not sort", function(){
				spyOn(LibraryTools, "sort");
				listViewSongs.onclick_Sort(objSender, {rowIndex:0});
				expect(LibraryTools.sort).not.toHaveBeenCalled();
			});
			it("shoud not refresh listmedia", function(){
				spyOn(listViewSongs.$.listMedia, "refresh");
				listViewSongs.onclick_Sort(objSender, {rowIndex:0});
				expect(listViewSongs.$.listMedia.refresh).not.toHaveBeenCalled();
			});
			it("shoud not generateGuid", function(){
				spyOn(Utilities, "generateGuid");
				listViewSongs.onclick_Sort(objSender, {rowIndex:0});
				expect(Utilities.generateGuid).not.toHaveBeenCalled();
			});
			it("should do list changed", function(){
				spyOn(listViewSongs, "doListChanged");
				listViewSongs.onclick_Sort(objSender, {rowIndex:0});
				expect(listViewSongs.doListChanged).toHaveBeenCalled();
			});
		});
		describe("with song listViewSongstype",function(){
			beforeEach(function(){
				listViewSongs.listViewSongsType = "song";
			});
			it("if other should call listquery", function(){
				spyOn(listViewSongs, "listQuery");
				listViewSongs.onclick_Sort(objSender, {rowIndex:0});
				expect(listViewSongs.listQuery).toHaveBeenCalled();
			});
			it("shoud not sort", function(){
				spyOn(LibraryTools, "sort");
				listViewSongs.onclick_Sort(objSender, {rowIndex:0});
				expect(LibraryTools.sort).not.toHaveBeenCalled();
			});
			
			
			it("should do list changed", function(){
				spyOn(listViewSongs, "doListChanged");
				listViewSongs.onclick_Sort(objSender, {rowIndex:0});
				expect(listViewSongs.doListChanged).toHaveBeenCalled();
			});
		});
		
	});
	describe('test (resetMusicListHeaders)', function(){
		beforeEach(function(){
			listViewSongs.$.header.children[0].removeClass = function(){};
			listViewSongs.$.header.children[1].removeClass = function(){};
			listViewSongs.$.header.children[2].removeClass = function(){};

			listViewSongs.$.header.children[listViewSongs.$.header.children.length-1].removeClass = function(){};
		});
		it('should call $.header.children[0].removeClass',function(){
			spyOn(listViewSongs.$.header.children[0],'removeClass');
			listViewSongs.resetMusicListHeaders();
			expect(listViewSongs.$.header.children[0].removeClass).toHaveBeenCalledWith("desc");
		});
		it('should call $.header.children[1].removeClass',function(){
			spyOn(listViewSongs.$.header.children[1],'removeClass');
			listViewSongs.resetMusicListHeaders();
			expect(listViewSongs.$.header.children[1].removeClass).toHaveBeenCalledWith("desc");
		});
		it('should call $.header.children[2].removeClass',function(){
			spyOn(listViewSongs.$.header.children[2],'removeClass');
			listViewSongs.resetMusicListHeaders();
			expect(listViewSongs.$.header.children[2].removeClass).toHaveBeenCalledWith("desc");
		});		
		it('should call $.header.children[0].removeClass',function(){
			spyOn(listViewSongs.$.header.children[0],'removeClass');
			listViewSongs.resetMusicListHeaders();
			expect(listViewSongs.$.header.children[0].removeClass).toHaveBeenCalledWith("sorted");
			expect(listViewSongs.$.header.children[0].removeClass).toHaveBeenCalledWith("asc");
			expect(listViewSongs.$.header.children[0].removeClass).toHaveBeenCalledWith("desc");
			
		});
		it('should call $.header.children[1].removeClass',function(){
			spyOn(listViewSongs.$.header.children[1],'removeClass');
			listViewSongs.resetMusicListHeaders();
			expect(listViewSongs.$.header.children[1].removeClass).toHaveBeenCalledWith("sorted");
			expect(listViewSongs.$.header.children[1].removeClass).toHaveBeenCalledWith("asc");
			expect(listViewSongs.$.header.children[1].removeClass).toHaveBeenCalledWith("desc");
		});
		it('should call $.header.children[2].removeClass',function(){
			spyOn(listViewSongs.$.header.children[2],'removeClass');
			listViewSongs.resetMusicListHeaders();
			expect(listViewSongs.$.header.children[2].removeClass).toHaveBeenCalledWith("sorted");
			expect(listViewSongs.$.header.children[2].removeClass).toHaveBeenCalledWith("asc");
			expect(listViewSongs.$.header.children[2].removeClass).toHaveBeenCalledWith("desc");
		});
		it('should call $.header.children[listViewSongs.$.header.children.length-1].removeClass',function(){
			spyOn(listViewSongs.$.header.children[listViewSongs.$.header.children.length-1],'removeClass');
			listViewSongs.resetMusicListHeaders();
			expect(listViewSongs.$.header.children[listViewSongs.$.header.children.length-1].removeClass).toHaveBeenCalledWith("sorted");
			expect(listViewSongs.$.header.children[listViewSongs.$.header.children.length-1].removeClass).toHaveBeenCalledWith("asc");
			expect(listViewSongs.$.header.children[listViewSongs.$.header.children.length-1].removeClass).toHaveBeenCalledWith("desc");
		});
	});
	describe('test (onclick_listMedia)', function(){
		beforeEach(function(){
			listViewSongs.arSongs = getJson("mock_songArr");
		});
		it('should call doSetPlaybackList',function(){
			spyOn(listViewSongs,'doSetPlaybackList');
			listViewSongs.onclick_listMedia(null,{"rowIndex":0});
			expect(listViewSongs.doSetPlaybackList).toHaveBeenCalled();
		});
//		//no longer neccessary
//		it("should call Utilities.getTrackImage", function(){
//			spyOn(Utilities,'getTrackImage');
//			listViewSongs.onclick_listMedia(null,{"rowIndex":0});
//			expect(Utilities.getTrackImage).toHaveBeenCalledWith(listViewSongs.arSongs[0].thumbnails[0].data, 54);
//		});
		it("should work with this.intJumpRowOffset", function(){
			listViewSongs.intJumpRowOffset=1;
			spyOn(listViewSongs,'doSetPlaybackList');
			listViewSongs.onclick_listMedia(null,{"rowIndex":0});
			expect(listViewSongs.doSetPlaybackList.mostRecentCall.args[0].intStartTrackIndex).toBe(1);
		});
		describe("when boolSuspendclick is true", function(){
			beforeEach(function(){
				listViewSongs.boolSuspendClick=true;
			});
			it('shouldnt call doSetPlaybackList',function(){
				spyOn(listViewSongs,'doSetPlaybackList');
				listViewSongs.onclick_listMedia(null,{"rowIndex":0});
				expect(listViewSongs.doSetPlaybackList).not.toHaveBeenCalled();
			});
			//no longer neccessary
//			it("shouldnt call Utilities.getTrackImage", function(){
//				spyOn(Utilities,'getTrackImage');
//				listViewSongs.onclick_listMedia(null,{"rowIndex":0});
//				expect(Utilities.getTrackImage).not.toHaveBeenCalled();
//			});
			
			
		});
		
	});
	describe('test (highlightTrack)', function(){
		describe("when param is given", function(){
			var param = "";
			beforeEach(function(){
				param = {intTrackIndex: 1, intTrackOrigIndex: 2, strTrackID: "3"};
			});
			it("should change intCurrTrack", function(){
				listViewSongs.intCurrTrack = null;
				listViewSongs.highlightTrack(param);
				expect(listViewSongs.intCurrTrack).toBe(1);
			});
			it("should change intCurrTrackOrigIndex", function(){
				listViewSongs.intCurrTrackOrigIndex = null;
				listViewSongs.highlightTrack(param);
				expect(listViewSongs.intCurrTrackOrigIndex).toBe(2);
			});
			it("should change strCurrTrackID", function(){
				listViewSongs.strCurrTrackID = null;
				listViewSongs.highlightTrack(param);
				expect(listViewSongs.strCurrTrackID).toBe("3");
			});
//			it("should change intJumpRowOffset", function(){
//				param.intTrackOrigIndex = 50;
//				listViewSongs.intJumpRowOffset = null;
//				listViewSongs.highlightTrack(param);
//				expect(listViewSongs.intJumpRowOffset).toBe(49);
//			});
//			it("should call punt", function(){
//				listViewSongs.boolViewActive = true;
//				spyOn(listViewSongs.$.listMedia, "punt");
//				listViewSongs.highlightTrack(param);
//				expect(listViewSongs.$.listMedia.punt).toHaveBeenCalled();
//			});
			it("shouldnt call punt", function(){
				listViewSongs.boolViewActive = false;
				spyOn(listViewSongs.$.listMedia, "punt");
				listViewSongs.highlightTrack(param);
				expect(listViewSongs.$.listMedia.punt).not.toHaveBeenCalled();
			});
			
		});
		describe("when param isnt given", function(){
			var param = false;
			beforeEach(function(){
				param = false;
			});
			it("shouldnt change intCurrTrack", function(){
				listViewSongs.intCurrTrack = null;
				listViewSongs.highlightTrack(param);
				expect(listViewSongs.intCurrTrack).toBe(null);
			});
			it("shouldnt change intCurrTrackOrigIndex", function(){
				listViewSongs.intCurrTrackOrigIndex = null;
				listViewSongs.highlightTrack(param);
				expect(listViewSongs.intCurrTrackOrigIndex).toBe(null);
			});
			it("shouldnt change strCurrTrackID", function(){
				listViewSongs.strCurrTrackID = null;
				listViewSongs.highlightTrack(param);
				expect(listViewSongs.strCurrTrackID).toBe(null);
			});
			it("shouldnt change intJumpRowOffset", function(){
				listViewSongs.intJumpRowOffset = null;
				listViewSongs.highlightTrack(param);
				expect(listViewSongs.intJumpRowOffset).toBe(null);
			});
			it("shouldnt call punt", function(){
				listViewSongs.boolViewActive = false;
				spyOn(listViewSongs.$.listMedia, "punt");
				listViewSongs.highlightTrack(param);
				expect(listViewSongs.$.listMedia.punt).not.toHaveBeenCalled();
			});
		});
		
		
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(listViewSongs,'_constructor');
			listViewSongs.base();
			expect(listViewSongs._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(listViewSongs,'constructed');
			listViewSongs.base();
			expect(listViewSongs.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(listViewSongs,'_constructor');
			listViewSongs.ctor();
			expect(listViewSongs._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(listViewSongs,'constructed');
			listViewSongs.ctor();
			expect(listViewSongs.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doSetPlaybackList)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(listViewSongs,'dispatchIndirectly');
			listViewSongs.doSetPlaybackList();
			expect(listViewSongs.dispatchIndirectly).toHaveBeenCalledWith("onSetPlaybackList",{});
		});
	});
	describe('test (doRequestMedia)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(listViewSongs,'dispatchIndirectly');
			listViewSongs.doRequestMedia();
			expect(listViewSongs.dispatchIndirectly).toHaveBeenCalledWith("onRequestMedia",{});
		});
	});
	describe('test (doRequestCurrTrackInfo)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(listViewSongs,'dispatchIndirectly');
			listViewSongs.doRequestCurrTrackInfo();
			expect(listViewSongs.dispatchIndirectly).toHaveBeenCalledWith("onRequestCurrTrackInfo",{});
		});
	});
	describe('test (doListChanged)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(listViewSongs,'dispatchIndirectly');
			listViewSongs.doListChanged();
			expect(listViewSongs.dispatchIndirectly).toHaveBeenCalledWith("onListChanged",{});
		});
	});
	describe('test (doListSorted)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(listViewSongs,'dispatchIndirectly');
			listViewSongs.doListSorted();
			expect(listViewSongs.dispatchIndirectly).toHaveBeenCalledWith("onListSorted",{});
		});
	});
	describe('test (doEnableControls)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(listViewSongs,'dispatchIndirectly');
			listViewSongs.doEnableControls();
			expect(listViewSongs.dispatchIndirectly).toHaveBeenCalledWith("onEnableControls",{});
		});
	});
	describe('test (getBoolViewActive)', function(){
		it('should call getProperty',function(){
			spyOn(listViewSongs,'getProperty');
			listViewSongs.getBoolViewActive();
			expect(listViewSongs.getProperty).toHaveBeenCalledWith("boolViewActive");
		});
	});
	describe('test (setBoolViewActive)', function(){
		it('should call _setProperty',function(){
			spyOn(listViewSongs,'_setProperty');
			listViewSongs.setBoolViewActive();
			expect(listViewSongs._setProperty).toHaveBeenCalledWith("boolViewActive",undefined,"boolViewActiveChanged");
		});
	});
	describe('test (getBoolRefreshData)', function(){
		it('should call getProperty',function(){
			spyOn(listViewSongs,'getProperty');
			listViewSongs.getBoolRefreshData();
			expect(listViewSongs.getProperty).toHaveBeenCalledWith("boolRefreshData");
		});
	});
	describe('test (setBoolRefreshData)', function(){
		it('should call _setProperty',function(){
			spyOn(listViewSongs,'_setProperty');
			listViewSongs.setBoolRefreshData();
			expect(listViewSongs._setProperty).toHaveBeenCalledWith("boolRefreshData",undefined,"boolRefreshDataChanged");
		});
	});
	describe('test (callAddToPlaylist)', function(){
		it('should call doAddToPlaylist',function(){
			spyOn(listViewSongs,'doAddToPlaylist');
			listViewSongs.callAddToPlaylist();
			expect(listViewSongs.doAddToPlaylist).toHaveBeenCalledWith({"strMediaType":"song","arTracks":[{}]});
		});
	});
	
});