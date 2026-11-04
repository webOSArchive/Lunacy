/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */
describe('List View Draggable Tests', function(){
	
	var listViewDraggable;
	
	beforeEach(function () {
		listViewDraggable = enyo.create({kind: "listViewDraggable"});
		listViewDraggable.arSongs = getJson("mock_songArr");
		listViewDraggable.arItems = listViewDraggable.arSongs;
	});
	
	it('Check start variables', function(){				
		expect(listViewDraggable.intJumpRowOffset).toBe(0);	
		expect(listViewDraggable.boolDragHold).toBe(false);	
		expect(listViewDraggable.boolDragItem).toBe(false);	
		expect(listViewDraggable.boolDraggingList).toBe(false);	
		expect(listViewDraggable.dragObjType).toBe(null);
	});
	
	describe("test (create)", function(){
		it("should call inherit", function(){
			spyOn(listViewDraggable, "inherited");
			listViewDraggable.create();
			expect(listViewDraggable.inherited).toHaveBeenCalled();
		});
	});
	describe("test (ready)", function(){
		it("should call inherit", function(){
			spyOn(listViewDraggable, "inherited");
			listViewDraggable.create();
			expect(listViewDraggable.inherited).toHaveBeenCalled();
		});
		it("should set listmedia scrollStop", function(){
			listViewDraggable.$.listMedia = {$:{scroller: {}}};
			listViewDraggable.$.listMedia.$.scroller.scrollStop = "";
			listViewDraggable.ready();
			expect(listViewDraggable.$.listMedia.$.scroller.scrollStop).not.toBe("");
		});
		it("should set listmedia scrollStart", function(){
			listViewDraggable.$.listMedia = {$:{scroller: {}}};
			listViewDraggable.$.listMedia.$.scroller.scrollStart = "";
			listViewDraggable.ready();
			expect(listViewDraggable.$.listMedia.$.scroller.scrollStart).not.toBe("");
		});
	});
	describe('check adding to playlist (callAddToPlaylist)', function(){				
		it("should default to song", function(){
			spyOn(listViewDraggable, "doAddToPlaylist");
			
			listViewDraggable.callAddToPlaylist(0, 0);
			expect(listViewDraggable.doAddToPlaylist.mostRecentCall.args[0].strPlaylistID).toBe(0);
			expect(listViewDraggable.doAddToPlaylist.mostRecentCall.args[0].arTracks[0].path).toBe(listViewDraggable.arSongs[0].path);
			
		});
		it("should select the correct song", function(){
			spyOn(listViewDraggable, "doAddToPlaylist");
			
			listViewDraggable.callAddToPlaylist(1, 1);
			
			var argument = listViewDraggable.doAddToPlaylist.mostRecentCall.args[0];
			expect(argument.strMediaType).toBe("song");
			expect(argument.strPlaylistID).toBe(1);
			expect(argument.arTracks[0].path).toBe(listViewDraggable.arSongs[1].path);
			
		});
		
		it("should select the right album", function(){
			spyOn(listViewDraggable, "doAddToPlaylist");
	
			listViewDraggable.callAddToPlaylist(1, 0, "album");
			var argument = listViewDraggable.doAddToPlaylist.mostRecentCall.args[0];
			expect(argument.strMediaType).toBe("album");
			expect(argument.strPlaylistID).toBe(0);
			expect(argument.strMediaFilterSub).toBe(listViewDraggable.arSongs[1].albumArtist);
		});
	
		it("should select the right artist", function(){
			spyOn(listViewDraggable, "doAddToPlaylist");
	
			listViewDraggable.callAddToPlaylist(1, 0, "artist");
			var argument = listViewDraggable.doAddToPlaylist.mostRecentCall.args[0];
			expect(argument.strMediaType).toBe("artist");
			expect(argument.strPlaylistID).toBe(0);
			expect(argument.strMediaFilterSub).toBe(listViewDraggable.arSongs[1].name);
		});
		
		it("should select the right genre", function(){
			spyOn(listViewDraggable, "doAddToPlaylist");
	
			listViewDraggable.callAddToPlaylist(1, 0, "genre");
			var argument = listViewDraggable.doAddToPlaylist.mostRecentCall.args[0];
			expect(argument.strMediaType).toBe("genre");
			expect(argument.strPlaylistID).toBe(0);
			expect(argument.strMediaFilterSub).toBe(listViewDraggable.arSongs[1].name);
		});
	
	
	});
	
	it("should call mousehold on (songMousehold)", function(){
		spyOn(listViewDraggable, "itemMousehold");
		listViewDraggable.dragObjType = null;
		var one = jasmine.any();
		var two = jasmine.any();
		listViewDraggable.songMousehold(one, two);
		expect(listViewDraggable.dragObjType).toBe("song");
		expect(listViewDraggable.itemMousehold).toHaveBeenCalledWith(one, two);
	});
	it("should call mousehold on (albumMousehold)", function(){
		spyOn(listViewDraggable, "itemMousehold");
		listViewDraggable.dragObjType = null;
		var one = jasmine.any();
		var two = jasmine.any();
		listViewDraggable.albumMousehold(one, two);
		expect(listViewDraggable.dragObjType).toBe("album");
		expect(listViewDraggable.albumGroup).toBe(false);
		expect(listViewDraggable.itemMousehold).toHaveBeenCalledWith(one, two);
	});
	
	it("should call mousehold on (albumGrouphold)", function(){
		spyOn(listViewDraggable, "itemMousehold");
		listViewDraggable.dragObjType = null;
		var one = jasmine.any();
		var two = jasmine.any();
		listViewDraggable.albumGroupMousehold(one, two);
		expect(listViewDraggable.dragObjType).toBe("album");
		expect(listViewDraggable.albumGroup).toBe(true);
		expect(listViewDraggable.itemMousehold).toHaveBeenCalledWith(one, two);
	});
	
	it("should call mousehold on (artistMousehold)", function(){
		spyOn(listViewDraggable, "itemMousehold");
		listViewDraggable.dragObjType = null;
		var one = jasmine.any();
		var two = jasmine.any();
		listViewDraggable.artistMousehold(one, two);
		expect(listViewDraggable.dragObjType).toBe("artist");
		expect(listViewDraggable.itemMousehold).toHaveBeenCalledWith(one, two);
	});
	
	it("should call mousehold on (genreMousehold)", function(){
		spyOn(listViewDraggable, "itemMousehold");
		listViewDraggable.dragObjType = null;
		var one = jasmine.any();
		var two = jasmine.any();
		listViewDraggable.genreMousehold(one, two);
		expect(listViewDraggable.dragObjType).toBe("genre");
		expect(listViewDraggable.itemMousehold).toHaveBeenCalledWith(one, two);
	});
	
	describe("check (itemMousehold)", function(){
		beforeEach(function(){
			window.PalmSystem = true;
		});
		
		//shouldn't be able to drag when scrolling, throws jumprowoffset off and not expected behavior
		it("shouldnt set highlight when booleditMode is true", function(){
			listViewDraggable.boolEditMode = true;
			listViewDraggable.scrolling = false;
			spyOn(listViewDraggable, "setItemHighlighted");
			listViewDraggable.itemMousehold(jasmine.any(), jasmine.any());
			expect(listViewDraggable.setItemHighlighted).not.toHaveBeenCalled();
		});
		
		it("shouldnt set highlight when scrolling is true", function(){
			listViewDraggable.scrolling = true;
			listViewDraggable.boolEditMode = false;
			spyOn(listViewDraggable, "setItemHighlighted");
			listViewDraggable.itemMousehold(jasmine.any(), jasmine.any());
			expect(listViewDraggable.setItemHighlighted).not.toHaveBeenCalled();
		});
		////
		
		it("should highlight", function(){
			spyOn(listViewDraggable, "setItemHighlighted");
			listViewDraggable.itemMousehold(jasmine.any(), jasmine.any());
			expect(listViewDraggable.setItemHighlighted).toHaveBeenCalledWith(true);
		});
		it("should set proper variables song",function(){
			listViewDraggable.boolDragHold = null;
			listViewDraggable.itemMousehold(jasmine.any(), jasmine.any());
			expect(listViewDraggable.boolDragHold).toBe(true);
		});
		it("should call do item drag", function(){
			spyOn(listViewDraggable, "doItemDrag");
			var check = jasmine.any();
			listViewDraggable.itemMousehold(jasmine.any(), check);
			expect(listViewDraggable.doItemDrag).toHaveBeenCalledWith(check);
		});		
		it("should return false", function(){
			var ret = listViewDraggable.itemMousehold(jasmine.any(), jasmine.any());
			expect(ret).toBe(false);
		});
		it("should call do item show avatar", function(){
			spyOn(listViewDraggable, "doItemShowAvatar");
			listViewDraggable.itemMousehold(jasmine.any(), jasmine.any());
			expect(listViewDraggable.doItemShowAvatar).toHaveBeenCalledWith(true, "");
		});
		
		it("should fail gracefully", function(){
			spyOn(listViewDraggable, "doItemShowAvatar");
			var event = {};
			event.rowIndex = 30;
			listViewDraggable.itemMousehold(undefined, event);
			expect(listViewDraggable.doItemShowAvatar).toHaveBeenCalledWith(true, "");
		});
		
		it("should use the correct thumbnails", function(){
			spyOn(Utilities, "getItemThumb");
			var event = {};
			event.rowIndex = 1;
			listViewDraggable.itemMousehold(undefined, event);
			expect(Utilities.getItemThumb).toHaveBeenCalledWith(listViewDraggable.arSongs[1].thumbnails, 88);
		});
		
		it("should use the correct thumbnails on a different song", function(){
			spyOn(Utilities, "getItemThumb");
			var event = {};
			event.rowIndex = 2;
			listViewDraggable.itemMousehold(undefined, event);
			expect(Utilities.getItemThumb).toHaveBeenCalledWith(listViewDraggable.arSongs[2].thumbnails, 88);
		});
		
		it("should work with intJumpRowOffset", function(){
			spyOn(Utilities, "getItemThumb");
			var event = {};
			event.rowIndex = 1;
			listViewDraggable.intJumpRowOffset = 1;
			listViewDraggable.itemMousehold(undefined, event);
			expect(Utilities.getItemThumb).toHaveBeenCalledWith(listViewDraggable.arSongs[2].thumbnails, 88);
		});
		
		it("should not work if not on window.palmsystem", function(){
			spyOn(Utilities, "getItemThumb");
			var event = {};
			event.rowIndex = 1;
			window.PalmSystem = false;
			listViewDraggable.itemMousehold(undefined, event);
			expect(Utilities.getItemThumb).not.toHaveBeenCalled();
		});
	});
	
	describe("check (itemMouserelease)", function(){
		it("should return true", function(){
			expect(listViewDraggable.itemMouserelease()).toBe(true);
		});
		it("should set boolDragHold to false", function(){
			listViewDraggable.boolDragHold = null;
			listViewDraggable.itemMouserelease();
			expect(listViewDraggable.boolDragHold).toBe(false);
		});
		describe("when boolDragItem is false", function(){
			beforeEach(function(){
				listViewDraggable.boolDragItem = false;
			});
			
			it("should setItemHighlighted to false", function(){
				spyOn(listViewDraggable, "setItemHighlighted");
				listViewDraggable.itemMouserelease();
				expect(listViewDraggable.setItemHighlighted).toHaveBeenCalledWith(false);
			});
			
			it("should setItemHighlighted to false", function(){
				spyOn(listViewDraggable, "doItemShowAvatar");
				listViewDraggable.itemMouserelease();
				expect(listViewDraggable.doItemShowAvatar).toHaveBeenCalledWith(false);
			});
			it("should change dragObjType to null", function(){
				listViewDraggable.dragObjType = jasmine.any();
				listViewDraggable.itemMouserelease();
				expect(listViewDraggable.dragObjType).toBe(null);
			});
			
		});
		describe("when boolDragItem is true", function(){
			beforeEach(function(){
				listViewDraggable.boolDragItem = true;
			});
			
			it("should not setItemHighlighted to false", function(){
				spyOn(listViewDraggable, "setItemHighlighted");
				listViewDraggable.itemMouserelease();
				expect(listViewDraggable.setItemHighlighted).not.toHaveBeenCalled();
			});
			
			it("should not setItemHighlighted to false", function(){
				spyOn(listViewDraggable, "doItemShowAvatar");
				listViewDraggable.itemMouserelease();
				expect(listViewDraggable.doItemShowAvatar).not.toHaveBeenCalled();
			});
			it("should not change dragObjType to null", function(){
				var check = jasmine.any();
				listViewDraggable.dragObjType = check;
				listViewDraggable.itemMouserelease();
				expect(listViewDraggable.dragObjType).toBe(check);
			});
			
		});
	
	});
	describe("check (itemDragStart)", function(){
		beforeEach(function(){
			listViewDraggable.boolDragHold = true;
			event = {rowIndex:0};
		});
		
		describe("When the dragObjType is a song", function(){
			beforeEach(function(){
				listViewDraggable.dragObjType = "song";
			});
			it("should correctly determine type of dragged item", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addType).toBe("song");
			});
			it("should correctly choose the song", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addTitle).toBe(listViewDraggable.arSongs[0].title);
			});
			it("should correctly choose the song again", function(){
				spyOn(listViewDraggable, "doItemDrag");
				var event = {rowIndex:2};
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addTitle).toBe(listViewDraggable.arSongs[2].title);
			});
			it("should correctly use intJumpRowOffset", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.intJumpRowOffset = 1;
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addTitle).toBe(listViewDraggable.arSongs[1].title);
			});
			
			it("should do nothing if not holding", function(){
				listViewDraggable.boolDragHold = false;
				expect(listViewDraggable.itemDragStart(jasmine.any(),event)).not.toBe(true);
			});
			it("shoudl return true", function(){
				expect(listViewDraggable.itemDragStart(jasmine.any(),event)).toBe(true);
			});
			it("should set boolDragging list to true", function(){
				listViewDraggable.boolDraggingList = null;
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.boolDraggingList).toBe(true);
			});
			it("should set boolDragItem list to true", function(){
				listViewDraggable.boolDragItem = null;
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.boolDragItem).toBe(true);
			});
			it("should setItemHighlighted to true", function(){
				spyOn(listViewDraggable, "setItemHighlighted");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.setItemHighlighted).toHaveBeenCalledWith(true);
			});
			it("should call doItemDrag properly", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag).toHaveBeenCalledWith(event);
			});
		});
		describe("When the dragObjType is an album", function(){
			beforeEach(function(){
				listViewDraggable.dragObjType = "album";
			});
			it("should correctly determine type of dragged item", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addType).toBe("album");
			});
			it("should correctly choose the song", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addTitle).toBe(listViewDraggable.arSongs[0].album);
			});
			it("should correctly choose the song again", function(){
				spyOn(listViewDraggable, "doItemDrag");
				var event = {rowIndex:2};
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addTitle).toBe(listViewDraggable.arSongs[2].album);
			});
			it("should correctly use intJumpRowOffset", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.intJumpRowOffset = 1;
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addTitle).toBe(listViewDraggable.arSongs[1].album);
			});
			it("should set boolShowIcon to false on album", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.boolShowIcon).toBe(false);
			});
			it("should do nothing if not holding", function(){
				listViewDraggable.boolDragHold = false;
				expect(listViewDraggable.itemDragStart(jasmine.any(),event)).not.toBe(true);
			});
			it("shoudl return true", function(){
				expect(listViewDraggable.itemDragStart(jasmine.any(),event)).toBe(true);
			});
			it("should set boolDragging list to true", function(){
				listViewDraggable.boolDraggingList = null;
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.boolDraggingList).toBe(true);
			});
			it("should set boolDragItem list to true", function(){
				listViewDraggable.boolDragItem = null;
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.boolDragItem).toBe(true);
			});
			it("should setItemHighlighted to true", function(){
				spyOn(listViewDraggable, "setItemHighlighted");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.setItemHighlighted).toHaveBeenCalledWith(true);
			});
			it("should call doItemDrag properly", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag).toHaveBeenCalledWith(event);
			});
		});
		describe("When the dragObjType is an artist", function(){
			beforeEach(function(){
				listViewDraggable.dragObjType = "artist";
			});
			it("should correctly determine type of dragged item", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addType).toBe("artist");
			});
			it("should correctly choose the artist", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addTitle).toBe(listViewDraggable.arSongs[0].name);
			});
			it("should correctly choose the song again", function(){
				spyOn(listViewDraggable, "doItemDrag");
				var event = {rowIndex:2};
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addTitle).toBe(listViewDraggable.arSongs[2].name);
			});
			it("should correctly use intJumpRowOffset", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.intJumpRowOffset = 1;
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addTitle).toBe(listViewDraggable.arSongs[1].name);
			});
			it("should set boolShowIcon to false on artist", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.boolShowIcon).toBe(false);
			});
			it("should do nothing if not holding", function(){
				listViewDraggable.boolDragHold = false;
				expect(listViewDraggable.itemDragStart(jasmine.any(),event)).not.toBe(true);
			});
			it("shoudl return true", function(){
				expect(listViewDraggable.itemDragStart(jasmine.any(),event)).toBe(true);
			});
			it("should set boolDragging list to true", function(){
				listViewDraggable.boolDraggingList = null;
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.boolDraggingList).toBe(true);
			});
			it("should set boolDragItem list to true", function(){
				listViewDraggable.boolDragItem = null;
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.boolDragItem).toBe(true);
			});
			it("should setItemHighlighted to true", function(){
				spyOn(listViewDraggable, "setItemHighlighted");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.setItemHighlighted).toHaveBeenCalledWith(true);
			});
			it("should call doItemDrag properly", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag).toHaveBeenCalledWith(event);
			});
		});
		describe("When the dragObjType is an genre", function(){
			beforeEach(function(){
				listViewDraggable.dragObjType = "genre";
				listViewDraggable.getDragIndex = function(){return 0;};
			});
			it("should correctly determine type of dragged item", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addType).toBe("genre");
			});
			it("should correctly choose the artist", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addTitle).toBe(listViewDraggable.arSongs[0].name);
			});
			it("should correctly choose the song again", function(){
				spyOn(listViewDraggable, "doItemDrag");
				var event = {rowIndex:2};
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.addTitle).toBe(listViewDraggable.arSongs[2].name);
			});
			it("should set boolShowIcon to false on artist", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag.mostRecentCall.args[0].dragInfo.boolShowIcon).toBe(false);
			});
			it("should do nothing if not holding", function(){
				listViewDraggable.boolDragHold = false;
				expect(listViewDraggable.itemDragStart(jasmine.any(),event)).not.toBe(true);
			});
			it("shoudl return true", function(){
				expect(listViewDraggable.itemDragStart(jasmine.any(),event)).toBe(true);
			});
			it("should set boolDragging list to true", function(){
				listViewDraggable.boolDraggingList = null;
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.boolDraggingList).toBe(true);
			});
			it("should set boolDragItem list to true", function(){
				listViewDraggable.boolDragItem = null;
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.boolDragItem).toBe(true);
			});
			it("should setItemHighlighted to true", function(){
				spyOn(listViewDraggable, "setItemHighlighted");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.setItemHighlighted).toHaveBeenCalledWith(true);
			});
			it("should call doItemDrag properly", function(){
				spyOn(listViewDraggable, "doItemDrag");
				listViewDraggable.itemDragStart(jasmine.any(),event);
				expect(listViewDraggable.doItemDrag).toHaveBeenCalledWith(event);
			});
		});
		
	});
	
	describe("check (onDrag_itemMedia)", function(){
		it("should doItemShowAvatarIcon if dragInfo is set", function(){
			spyOn(listViewDraggable, "doItemShowAvatarIcon");
			event = {};
			listViewDraggable.onDrag_itemMedia(jasmine.any(), event);
			expect(listViewDraggable.doItemShowAvatarIcon).not.toHaveBeenCalled();
			
			event = {dragInfo: {boolShowIcon: true}};
			listViewDraggable.onDrag_itemMedia(jasmine.any(), event);
			expect(listViewDraggable.doItemShowAvatarIcon).toHaveBeenCalledWith(true);
			event = {dragInfo: {boolShowIcon: false}};
			listViewDraggable.onDrag_itemMedia(jasmine.any(), event);
			expect(listViewDraggable.doItemShowAvatarIcon).toHaveBeenCalledWith(false);
		});
		it("should doItemDrag if we are dragging an item", function(){
			spyOn(listViewDraggable, "doItemDrag");
			var event = {dragInfo: {boolShowIcon: true}};
			listViewDraggable.boolDragItem = false;
			listViewDraggable.onDrag_itemMedia(jasmine.any(), event);
			expect(listViewDraggable.doItemDrag).not.toHaveBeenCalledWith(event);
		
			listViewDraggable.boolDragItem = true;
			listViewDraggable.onDrag_itemMedia(jasmine.any(), event);
			expect(listViewDraggable.doItemDrag).toHaveBeenCalledWith(event);
		
			
		});
	});
	
	describe("check (itemDragFinish)", function(){
		describe("if boolDragItem is true", function(){
			beforeEach(function(){
				listViewDraggable.boolDragItem = true;
				var event = {dragInfo: {boolShowIcon: false}};
				listViewDraggable.$.listMedia = {prepareRow : function(){}};
				listViewDraggable.callAddToPlaylist = function(){};
				
			});
			it("should set boolDraggingList to false", function(){
				listViewDraggable.boolDraggingList = null;
				listViewDraggable.itemDragFinish(jasmine.any(), event);
				expect(listViewDraggable.boolDraggingList).toBe(false);
			});
			it("should set other variables", function(){
				listViewDraggable.boolDragItem = true;
				listViewDraggable.dragObjType = true;
				spyOn(listViewDraggable, "doItemShowAvatar");
				spyOn(listViewDraggable, "doItemShowAvatarIcon");
				spyOn(listViewDraggable, "setItemHighlighted");
				listViewDraggable.itemDragFinish(jasmine.any(), event);
				
				expect(listViewDraggable.setItemHighlighted).toHaveBeenCalledWith(false);
				expect(listViewDraggable.doItemShowAvatarIcon).toHaveBeenCalledWith(false);
				expect(listViewDraggable.doItemShowAvatar).toHaveBeenCalledWith(false);
				
				
				expect(listViewDraggable.boolDragItem).toBe(false);
				expect(listViewDraggable.dragObjType).toBe(null);
				
			});
			
			it("should only call callAddToPlaylist if there is a strPlaylistID", function(){
				spyOn(listViewDraggable, "callAddToPlaylist");
				listViewDraggable.itemDragFinish(jasmine.any(), event);
				expect(listViewDraggable.callAddToPlaylist).not.toHaveBeenCalled();
				listViewDraggable.boolDragItem = true;
				event = {dragInfo: {boolShowIcon: false, strPlaylistID: 2}};
				listViewDraggable.itemDragFinish(jasmine.any(), event);
				expect(listViewDraggable.callAddToPlaylist).toHaveBeenCalled();
			});
			
		});
		
		describe("if boolDragItem is false", function(){
			beforeEach(function(){
				listViewDraggable.boolDragItem = false;
				event = {dragInfo: {boolShowIcon: false}};
				listViewDraggable.$.listMedia = {prepareRow : function(){}};
				listViewDraggable.callAddToPlaylist = function(){};
			});
			it("should set boolDraggingList to false", function(){
				listViewDraggable.boolDraggingList = null;
				listViewDraggable.itemDragFinish(jasmine.any(), event);
				expect(listViewDraggable.boolDraggingList).toBe(false);
			});
			it("should not set other variables", function(){
				listViewDraggable.dragObjType = true;
				spyOn(listViewDraggable, "doItemShowAvatar");
				spyOn(listViewDraggable, "doItemShowAvatarIcon");
				spyOn(listViewDraggable, "setItemHighlighted");
				listViewDraggable.itemDragFinish(jasmine.any(), event);
				
				expect(listViewDraggable.setItemHighlighted).not.toHaveBeenCalledWith(false);
				expect(listViewDraggable.doItemShowAvatarIcon).not.toHaveBeenCalledWith(false);
				expect(listViewDraggable.doItemShowAvatar).not.toHaveBeenCalledWith(false);
				
				
				expect(listViewDraggable.boolDragItem).toBe(false);
				expect(listViewDraggable.dragObjType).toBe(true);
				
			});
			
			it("should only call callAddToPlaylist if tere is a strPlaylistID", function(){
				listViewDraggable.callAddToPlaylist = function(){};
				spyOn(listViewDraggable, "callAddToPlaylist");
				listViewDraggable.itemDragFinish(jasmine.any(), event);
				expect(listViewDraggable.callAddToPlaylist).not.toHaveBeenCalled();
				
				event = {dragInfo: {boolShowIcon: false, strPlaylistID: 2}};
				listViewDraggable.itemDragFinish(jasmine.any(), event);
				expect(listViewDraggable.callAddToPlaylist).not.toHaveBeenCalled();
			});
			
		});
	});
	
	describe("check (onDrop_itemMedia)", function(){
		it("should return true", function(){
			expect(listViewDraggable.onDrop_itemMedia(jasmine.any(), {keys:""})).toBe(true);
		});
		it("should only update if dragging list", function(){
			listViewDraggable.boolSuspendClick = false;
			listViewDraggable.boolDraggingList = false;
			listViewDraggable.onDrop_itemMedia(jasmine.any(), {keys:""});
			expect(listViewDraggable.boolSuspendClick).toBe(false);

			listViewDraggable.boolDraggingList = true;
			listViewDraggable.onDrop_itemMedia(jasmine.any(), {keys:""});
			expect(listViewDraggable.boolSuspendClick).toBe(true);
		});
	});
	
	describe("check the wiring", function(){
		describe("check draggableSong", function(){
			beforeEach(function(){
				enyoSong = enyo.create({kind: "draggableSong"});
			});
			it("should call songMousehold", function(){
				listViewDraggable[enyoSong.onmousehold](jasmine.any(), {rowIndex: 0});
			});
		});
		describe("check draggableAlbum", function(){
			beforeEach(function(){
				enyoAlbum = enyo.create({kind: "draggableAlbum"});
			});
			it("should call songMousehold", function(){
				listViewDraggable[enyoAlbum.onmousehold](jasmine.any(), {rowIndex: 0});
			});
		});
		describe("check draggableAlbumGroup", function(){
			beforeEach(function(){
				listViewDraggable.arAlbums = listViewDraggable.arItems;
				enyoAlbum = enyo.create({kind: "draggableAlbumGroup"});
			});
			it("should call songMousehold", function(){
				listViewDraggable[enyoAlbum.onmousehold](jasmine.any(), {rowIndex: 0});
			});
		});
		describe("check draggableArtist", function(){
			beforeEach(function(){
				enyoArtist = enyo.create({kind: "draggableArtist"});
				listViewDraggable.arArtists = listViewDraggable.arItems;
			});
			it("should call artistMousehold", function(){
				listViewDraggable[enyoArtist.onmousehold](jasmine.any(), {rowIndex: 0});
			});
		});
		describe("check draggableGenra", function(){
			beforeEach(function(){
				enyoGenra = enyo.create({kind: "draggableGenre"});
				listViewDraggable.arGenres = listViewDraggable.arItems;
			});
			it("should call All it's events", function(){
				var calls = {onmousehold: "genreMousehold", onmouserelease: "itemMouserelease", ondragstart: "itemDragStart", ondragfinish: "itemDragFinish", ondrag: "onDrag_itemMedia", ondrop: "onDrop_itemMedia"};
				var i;
				for(i in calls){
					//checks to make sure all of the functions calling are in listViewDraggable
					if(calls[i])
					{
						listViewDraggable[enyoGenra[i]](jasmine.any(), {rowIndex: 0});
					}
				}
			});
		});
		describe("check draggableItem", function(){
			beforeEach(function(){
				draggableItem = enyo.create({kind: "draggableItem"});
			});
			it("all callbacks should work", function(){
				var calls = {onmouserelease: "itemMouserelease", ondragstart: "itemDragStart", ondragfinish: "itemDragFinish", ondrag: "onDrag_itemMedia", ondrop: "onDrop_itemMedia"};
				var i;
				for(i in calls){
					//checks to make sure all of the functions calling are in listViewDraggable
					if(calls[i])
					{
						listViewDraggable[draggableItem[i]](jasmine.any(), {rowIndex: 0});
					}
				}
			});
		});
		
		describe("check (getDragIndex)", function(){
			it("should correctly get genra index", function(){
				listViewDraggable.dragObjType = "genre";
				listViewDraggable.intColumns = 1;
				expect(listViewDraggable.getDragIndex({intColumnIndex:1},{rowIndex: 1})).toBe(2);
				listViewDraggable.intColumns = 4;
				expect(listViewDraggable.getDragIndex({intColumnIndex:1},{rowIndex: 1})).toBe(5);
				expect(listViewDraggable.getDragIndex({intColumnIndex:1},{rowIndex: 2})).toBe(9);
				expect(listViewDraggable.getDragIndex({intColumnIndex:3},{rowIndex: 2})).toBe(11);
			});
			it("should correctly do the others", function(){
				listViewDraggable.dragObjType = jasmine.any();
				listViewDraggable.intJumpRowOffset = 3;
				expect(listViewDraggable.getDragIndex({intColumnIndex:1},{rowIndex: 1})).toBe(4);
				listViewDraggable.intJumpRowOffset = 4;
				expect(listViewDraggable.getDragIndex({intColumnIndex:1},{rowIndex: 1})).toBe(5);
				expect(listViewDraggable.getDragIndex({intColumnIndex:1},{rowIndex: 2})).toBe(6);
				expect(listViewDraggable.getDragIndex({intColumnIndex:3},{rowIndex: 2})).toBe(6);
			});
			
			
		});
	});
});