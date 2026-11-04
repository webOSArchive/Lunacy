/*globals enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, jasmine */
describe('Media Index Tests', function(){
	
	var MediaIndex;
	
	beforeEach(function () {
		MediaIndex = enyo.create({kind: "kindMediaIndex"});
		MediaIndex.browserMode = false;
		enyo.PalmService = function(){
			//return mockService;
		};
			
	});
	
	it('Check start variables', function(){				
		expect(MediaIndex.browserMode).not.toBe(undefined);
	});
	
	it('Simple Get Songs', function(){
		
		var objGetMediaRequest = {where : "", order: "", callback:  function() {}};
		
		spyOn(MediaIndex.$.dbsFindMedia, 'call');
		MediaIndex.getMedia(objGetMediaRequest, "");
					
		expect(MediaIndex.$.dbsFindMedia.call).toHaveBeenCalledWith(jasmine.any(Object),{"objGetMediaRequest":objGetMediaRequest});
		
	});


	it('Get Songs With Sort', function(){				
		var objGetMediaRequest = {where : "whereField", order: "orderByField", callback:  function() {}};
		
		spyOn(MediaIndex.$.dbsFindMedia, 'call');
		MediaIndex.getMedia (objGetMediaRequest, "");
		
			
		expect(MediaIndex.$.dbsFindMedia.call).toHaveBeenCalledWith(jasmine.any(Object),{"objGetMediaRequest": objGetMediaRequest});
		
	});
	
	describe('Check to make sure correct dbFindMedia is being called', function(){
		
	/*	"dbsFindMedia", onSuccess: "gotMedia" , onFailure: "onFailure_dbsFindMedia"},
		{kind: "DbService", method: "find", name: "browserDBFindsong", onSuccess: "gotMedia" , onFailure: "onFailure_dbsFindMedia"},
		{kind: "DbService", method: "find", name: "browserDBFindartist", onSuccess: "gotMedia" , onFailure: "onFailure_dbsFindMedia"},
		{kind: "DbService", method: "find", name: "browserDBFindalbum", onSuccess: "gotMedia" , onFailure: "onFailure_dbsFindMedia"},
		{kind: "DbService", method: "find", name: "browserDBFindgenre", onSuccess: "gotMedia" , onFailure: "onFailure_dbsFindMedia" +
			*/	
		beforeEach(function(){
			MediaIndex.browserMode = true;
		});
		it('Get Undefined', function(){				
			var objGetMediaRequest = {where : "whereField", order: "orderByField", callback:  function() {}};
			spyOn(MediaIndex.$.dbsFindMedia, 'call');
			MediaIndex.getMedia (objGetMediaRequest, "");	
			//expect(MediaIndex.$.dbsFindMedia.call).toHaveBeenCalled();
		});
		it('Get Song', function(){				
			var objGetMediaRequest = {mediaType: "song", where : "whereField", order: "orderByField", callback:  function() {}};
			spyOn(MediaIndex.$.browserDBFindsong, 'call');
			MediaIndex.getMedia (objGetMediaRequest, "");	
			expect(MediaIndex.$.browserDBFindsong.call).toHaveBeenCalled();
		});
		it('Get artist', function(){				
			var objGetMediaRequest = {mediaType: "artist", where : "whereField", order: "orderByField", callback:  function() {}};
			spyOn(MediaIndex.$.browserDBFindartist, 'call');
			MediaIndex.getMedia (objGetMediaRequest, "");	
			expect(MediaIndex.$.browserDBFindartist.call).toHaveBeenCalled();
		});
		it('Get album', function(){				
			var objGetMediaRequest = {mediaType: "album", where : "whereField", order: "orderByField", callback:  function() {}};
			spyOn(MediaIndex.$.browserDBFindalbum, 'call');
			MediaIndex.getMedia (objGetMediaRequest, "");	
			expect(MediaIndex.$.browserDBFindalbum.call).toHaveBeenCalled();
		});
		it('Get genre', function(){				
			var objGetMediaRequest = {mediaType: "genre", where : "whereField", order: "orderByField", callback:  function() {}};
			spyOn(MediaIndex.$.browserDBFindgenre, 'call');
			MediaIndex.getMedia (objGetMediaRequest, "");	
			expect(MediaIndex.$.browserDBFindgenre.call).toHaveBeenCalled();
		});
		
		
		
	});
	
	it('Should set browserMode (pickBrowserMode)', function(){
		MediaIndex.browserMode = null;
		
		expect(MediaIndex.browserMode).toBe(null);
		MediaIndex.pickBrowserMode();
		expect(MediaIndex.browserMode).toNotBe(null);
		
	});
	
	it('Should return proper media kind (getFullMediaKind)', function(){
		expect(MediaIndex.getFullMediaKind("song")).toBe("com.palm.media.audio.file:1");
		expect(MediaIndex.getFullMediaKind("artist")).toBe("com.palm.media.audio.artist:1");
		expect(MediaIndex.getFullMediaKind("album")).toBe("com.palm.media.audio.album:1");
		expect(MediaIndex.getFullMediaKind("genre")).toBe("com.palm.media.audio.genre:1");
		expect(MediaIndex.getFullMediaKind("genre")).toBe("com.palm.media.audio.genre:1");
		expect(MediaIndex.getFullMediaKind("")).toBe(undefined);	
	});
	
	it('Checks validity of request Media (requestMedia)', function(){
		spyOn(MediaIndex, 'getMedia');
		
		var objGetMediaRequest = {where : "", order: "", callback:  function() {}};
		MediaIndex.requestMedia(objGetMediaRequest);
		expect(MediaIndex.getMedia).not.toHaveBeenCalled();
	
	
		objGetMediaRequest = {where : "", order: "", callback:  function() {}, mediaType: "song"};
		MediaIndex.requestMedia(objGetMediaRequest);
		expect(MediaIndex.getMedia).toHaveBeenCalledWith(objGetMediaRequest);
		
	});
	
	//enyo.g11n.Utils.getJsonFile({path: 'tests/mock', locale: filename});
	describe('gotMedia() Tests', function(){
		beforeEach(function () {
			spyOn(MediaIndex, 'getMedia');
			spyOn(MediaIndex, 'playSongs');
			var callbackFunction = function(){};
			
			this.callbackFunction = callbackFunction;
			spyOn(this, "callbackFunction");
				
		});
		
		afterEach(function (){
			delete this.callbackFunction;
		});
		
		it('with a next', function(){
			var objGetMediaRequest = {results: ['1', '2'], where : "", order: "", callback:  this.callbackFunction};
			var inResponse = {results: ['1', '2'], next: true};
			var inRequest = {objGetMediaRequest: objGetMediaRequest};
			
			//When a next is set
			MediaIndex.gotMedia(null, inResponse, inRequest);
			expect(MediaIndex.getMedia).toHaveBeenCalledWith(objGetMediaRequest, inResponse.next);
			expect(MediaIndex.playSongs).not.toHaveBeenCalled();
			expect(this.callbackFunction).not.toHaveBeenCalled();
			
		});
		
		it('without a next', function(){
			
			
			
			var objGetMediaRequest = {results: ['1', '2'], where : "", order: "", callback:  this.callbackFunction};
			var inResponse = {results: ['1', '2']};
			var inRequest = {objGetMediaRequest: objGetMediaRequest};
			
			MediaIndex.gotMedia(null, inResponse, inRequest);
			expect(MediaIndex.getMedia).not.toHaveBeenCalled();
			expect(MediaIndex.playSongs).not.toHaveBeenCalled();
			
			expect(this.callbackFunction).toHaveBeenCalled();
			
		});
		
		it('without a next and a forcePlay', function(){
					
			//forceplay false and not a song
			var objGetMediaRequest = {results: ['1', '2'], where : "", order: "", callback:  this.callbackFunction, boolForcePlay: false};
			var inResponse = {results: ['1', '2']};
			var inRequest = {objGetMediaRequest: objGetMediaRequest};
			
			MediaIndex.gotMedia(null, inResponse, inRequest);
			expect(MediaIndex.getMedia).not.toHaveBeenCalled();
			expect(MediaIndex.playSongs).not.toHaveBeenCalled();
			expect(this.callbackFunction).toHaveBeenCalled();
			

			//forceplay true and not a song
			objGetMediaRequest = {results: ['1', '2'], where : "", order: "", callback:  this.callbackFunction, boolForcePlay: true};
			inRequest = {objGetMediaRequest: objGetMediaRequest};
			
			MediaIndex.gotMedia(null, inResponse, inRequest);
			expect(MediaIndex.getMedia).not.toHaveBeenCalled();
			expect(MediaIndex.playSongs).not.toHaveBeenCalled();
			expect(this.callbackFunction).toHaveBeenCalled();
			
			//forceplay true and a song  
			objGetMediaRequest = {results: ['1', '2'], dbsKind: "com.palm.media.audio.file:1", where : "", order: "", callback:  this.callbackFunction, boolForcePlay: true};
			inRequest = {objGetMediaRequest: objGetMediaRequest};
			
			MediaIndex.gotMedia(null, inResponse, inRequest);
			expect(MediaIndex.getMedia).not.toHaveBeenCalled();
			expect(MediaIndex.playSongs).toHaveBeenCalledWith(objGetMediaRequest);
			expect(this.callbackFunction).toHaveBeenCalled();
			
		});
	});
	
	
	it('plays songs (playSongs)', function(){
		spyOn(MediaIndex, "doSetPlaybackList");
		MediaIndex.playSongs({results:[{}]});
		expect(MediaIndex.doSetPlaybackList).toHaveBeenCalled();
		
	});
	
	describe("check the wiring", function(){
		
		describe("check onSuccess", function(){
			
			beforeEach(function(){
				spyOn(MediaIndex, "gotMedia");
			});
			afterEach(function(){
				expect(MediaIndex.gotMedia).toHaveBeenCalled();
			});
			it("dbsFindMedia", function(){
				MediaIndex[MediaIndex.$.dbsFindMedia.onSuccess]();
			});
			it("browserDBFindsong", function(){
				MediaIndex[MediaIndex.$.browserDBFindsong.onSuccess]();
			});
			it("browserDBFindartist", function(){
				MediaIndex[MediaIndex.$.browserDBFindartist.onSuccess]();
			});
			it("browserDBFindalbum", function(){
				MediaIndex[MediaIndex.$.browserDBFindalbum.onSuccess]();
			});
			it("browserDBFindgenre", function(){
				MediaIndex[MediaIndex.$.browserDBFindgenre.onSuccess]();
			});
			
		});
		
		
		
		describe("check failures", function(){
			beforeEach(function(){
				spyOn(MediaIndex, "onFailure_dbsFindMedia");
			});
			afterEach(function(){
				expect(MediaIndex.onFailure_dbsFindMedia).toHaveBeenCalled();
			});
			it("dbsFindMedia", function(){
				MediaIndex[MediaIndex.$.dbsFindMedia.onFailure]();
			});
			it("browserDBFindsong", function(){
				MediaIndex[MediaIndex.$.browserDBFindsong.onFailure]();
			});
			it("browserDBFindartist", function(){
				MediaIndex[MediaIndex.$.browserDBFindartist.onFailure]();
			});
			it("browserDBFindalbum", function(){
				MediaIndex[MediaIndex.$.browserDBFindalbum.onFailure]();
			});
			it("browserDBFindgenre", function(){
				MediaIndex[MediaIndex.$.browserDBFindgenre.onFailure]();
			});
		});
		
		
	});
});