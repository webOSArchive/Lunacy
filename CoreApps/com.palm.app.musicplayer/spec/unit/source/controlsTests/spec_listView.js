/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */
describe('List View Tests', function(){
	
	var listView;
	
	beforeEach(function () {
		listView = enyo.create({kind: "listView"});
		listView.arSongs = getJson("mock_songArr");
		listView.arItems = listView.arSongs;
	});
	
	describe('check (create)', function(){
		it('should call inherited', function(){
			spyOn(listView, "inherited");
			listView.create();
			expect(listView.inherited).toHaveBeenCalled();
		});
	})
	
	describe('check (highlightTrack)', function(){
		
		
		
		it('should refresh listmedia when boolViewActive is true', function(){
			listView.$.listMedia = {};
			listView.$.listMedia.refresh = function(){};
			listView.boolViewActive = true;
			spyOn(listView.$.listMedia, "refresh");
			listView.highlightTrack(null);
			expect(listView.$.listMedia.refresh).toHaveBeenCalled();
			
			
		});
		
		
		it('should not refresh listmedia when boolViewActive is false', function(){
			listView.$.listMedia = {};
			listView.$.listMedia.refresh = function(){};
			listView.boolViewActive = false;
			spyOn(listView.$.listMedia, "refresh");
			listView.highlightTrack(null);
			expect(listView.$.listMedia.refresh).not.toHaveBeenCalled();
		});
		
		it('should set all necessary variables', function(){
			var param = {intTrackIndex: 1, intTrackOrigIndex: 2, strTrackID: 3};
			listView.highlightTrack(param);
			expect(listView.intCurrTrack).toBe(param.intTrackIndex);
			expect(listView.intCurrTrackOrigIndex).toBe(param.intTrackOrigIndex);
			expect(listView.strCurrTrackID).toBe(param.strTrackID);
		});
		
		it('should set all necessary variables again', function(){
			var param = {intTrackIndex: 2, intTrackOrigIndex: 3, strTrackID: 4};
			listView.highlightTrack(param);
			expect(listView.intCurrTrack).toBe(param.intTrackIndex);
			expect(listView.intCurrTrackOrigIndex).toBe(param.intTrackOrigIndex);
			expect(listView.strCurrTrackID).toBe(param.strTrackID);
		});
	
	
	});
	
});
	