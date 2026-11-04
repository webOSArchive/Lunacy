/*globals enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson */
describe("Playback Tests", function(){
	
	var playback;
	
	var song1 = getJson("mock_song1");
	var song2 = getJson("mock_song2");
	var song3 = getJson("mock_song3");
	var songs = getJson("mock_songArr");
	
	beforeEach(function () {
		playback = enyo.create({kind: "kindPlayback"});
	});
	
	it("Check start variables", function(){				
		//expect(playback.intErrorCount).toBe(0);
		//expect(playback.INT_ERROR_MAX).toBe(3);
	});
	
	it("destroy - stops updating the time (destroy)", function(){
		spyOn(playback, "stopTrackTimeUpdate");
		playback.destroy();
		expect(playback.stopTrackTimeUpdate).toHaveBeenCalled();
	});
	
	describe("boolSuspendUpdatesChanged", function(){
		beforeEach(function(){
			spyOn(playback, "stopTrackTimeUpdate");
			spyOn(playback, "startTrackTimeUpdate");
			spyOn(playback, "onAudioSrcChanged");
		});
		it("boolSuspendUpdates set to true", function(){
			playback.boolSuspendUpdates = false;
			playback.setBoolSuspendUpdates(true);
			expect(playback.stopTrackTimeUpdate).toHaveBeenCalled();
			expect(playback.startTrackTimeUpdate).not.toHaveBeenCalled();
			expect(playback.onAudioSrcChanged).not.toHaveBeenCalled();
		});
		it("boolSuspendUpdates set to false with no audo", function(){
			playback.boolSuspendUpdates = true;
			playback.$.AudioManager.boolAudioPlaying = false;
			playback.setBoolSuspendUpdates(false);
			expect(playback.stopTrackTimeUpdate).not.toHaveBeenCalled();
			expect(playback.startTrackTimeUpdate).not.toHaveBeenCalled();
			expect(playback.onAudioSrcChanged).not.toHaveBeenCalled();
		});
		it("boolSuspendUpdates set to false with audo", function(){
			playback.boolSuspendUpdates = true;
			playback.$.AudioManager.boolAudioPlaying = true;
			playback.setBoolSuspendUpdates(false);
			expect(playback.stopTrackTimeUpdate).not.toHaveBeenCalled();
			expect(playback.startTrackTimeUpdate).toHaveBeenCalled();
			expect(playback.onAudioSrcChanged).toHaveBeenCalledWith(null, true);
		});

		//This allows to get an update if screen just came back on
		it("boolSuspendUpdates set to false with no audo should updateTrackTime", function(){
			playback.boolSuspendUpdates = false;
			playback.$.AudioManager.boolAudioPlaying = false;
			spyOn(playback, "updateTrackTime");
			playback.setBoolSuspendUpdates(false);
			expect(playback.updateTrackTime).toHaveBeenCalled();
			expect(playback.stopTrackTimeUpdate).not.toHaveBeenCalled();
			expect(playback.startTrackTimeUpdate).not.toHaveBeenCalled();
			expect(playback.onAudioSrcChanged).not.toHaveBeenCalled();
		});
	});
	
	it("boolSuspendPlaybackChanged", function(){
		spyOn(playback, "boolSuspendPlaybackChanged");
		playback.boolSuspendPlayback = true;
		playback.setBoolSuspendPlayback(false);
		expect(playback.boolSuspendPlaybackChanged).toHaveBeenCalled();
		
	});	
		
	describe("setPlaybackList", function(){
		
		it("simple", function(){				
			spyOn(playback, "startPlayback");
			spyOn(playback, "doPlaybackListSet");
			
			var objSetPlaybackList = {arSetPlaybackList: songs, intStartTrackIndex: 0, intStartTrackTime: 0};
			var setPlaybackResult = playback.setPlaybackList(objSetPlaybackList);
			
			
			expect(setPlaybackResult).toEqual(true);
			expect(playback.startPlayback).toHaveBeenCalledWith(0);
			expect(playback.doPlaybackListSet).toHaveBeenCalledWith(true, 3, "OFF", "off");
		});
		
		it("simple with shuffle and repeat", function(){				
			spyOn(playback, "startPlayback");
			spyOn(playback, "doPlaybackListSet");
			
			var objSetPlaybackList = {arSetPlaybackList: songs, intStartTrackIndex: 0, intStartTrackTime: 0};
			
			
			playback.$.PlaybackList._boolShuffleOn = true;
			playback.$.PlaybackList.setIntRepeatMode(1);
			
			var setPlaybackResult = playback.setPlaybackList(objSetPlaybackList);
			
			
			expect(setPlaybackResult).toEqual(true);
			expect(playback.startPlayback).toHaveBeenCalledWith(0);
			expect(playback.doPlaybackListSet).toHaveBeenCalledWith(true, 3, "ON", "all");
		});	
		
		it("with start offsets", function(){				
			var startTrack = 1;
			var startTime = 100;
			
			spyOn(playback, "startPlayback");
			spyOn(playback, "doPlaybackListSet");
			
			var objSetPlaybackList = {arSetPlaybackList: songs, intStartTrackIndex: startTrack, intStartTrackTime: startTime};
	
			var setPlaybackResult = playback.setPlaybackList(objSetPlaybackList);
			
			expect(setPlaybackResult).toEqual(true);
			expect(playback.$.PlaybackList.intCurrTrackIndex).toEqual(startTrack);
			expect(playback.startPlayback).toHaveBeenCalledWith(startTime);
			expect(playback.doPlaybackListSet).toHaveBeenCalledWith(true, 3, "OFF", "off");
			
			
		});
	
	});
	
	it("startPlayback", function(){				
		
		spyOn(playback, "playTrack");
		
		playback.startPlayback(0);
		
		expect(playback.playTrack).toHaveBeenCalledWith(0);
		
	});
	
	describe("pausePlayback", function(){
		
		//So that we reload the audio obj (maybe should skip to the next song that didn't have an error before this point
		it("should call playAudio if we hit the error count", function(){
			spyOn(playback.$.AudioManager, "playAudio");
			spyOn(playback, "startTrackTimeUpdate");
			spyOn(playback, "stopTrackTimeUpdate");
			spyOn(playback.$.PlaybackList,"getCurrentPlayBackItem").andReturn({});
			playback.boolSuspendPlayback = true;
			playback.intErrorCount = 10;
			playback.INT_ERROR_MAX = 10;
			playback.pausePlayback(false);
			expect(playback.$.AudioManager.playAudio).toHaveBeenCalled();
			
			
		});
		
		
		it("should work without any params", function(){
			spyOn(playback.$.AudioManager, "pauseAudio");
			playback.pausePlayback();
			expect(playback.$.AudioManager.pauseAudio).toHaveBeenCalledWith(undefined);
		});
		it("should force on true", function(){
			spyOn(playback, "startTrackTimeUpdate");
			playback.pausePlayback(true);
			expect(playback.startTrackTimeUpdate).toHaveBeenCalled();
		});	
		it("should force on false", function(){
			spyOn(playback, "stopTrackTimeUpdate");
			playback.pausePlayback(false);
			expect(playback.stopTrackTimeUpdate).toHaveBeenCalled();
		});
	});
		
		
	describe("playTrack", function(){
		beforeEach(function(){
			objSetPlaybackList = {arSetPlaybackList: songs, intStartTrackIndex: 0, intStartTrackTime: 0};
		});
		
		it("Simple", function(){				
			
			spyOn(playback.$.AudioManager, "playAudio");
			playback.$.PlaybackList.setPlaybackList(objSetPlaybackList);
			playback.playTrack(0);
			expect(playback.$.AudioManager.playAudio).toHaveBeenCalledWith(songs[0].path, 0, undefined);
		});

	});
	
	it("onAudioPlaying", function(){				
		
		spyOn(playback, "startTrackTimeUpdate");
		spyOn(playback, "doTrackPlaying");
		playback.onAudioPlaying();
		expect(playback.startTrackTimeUpdate).toHaveBeenCalledWith();
		expect(playback.doTrackPlaying).toHaveBeenCalledWith();
		//expect(playback.intErrorCount).toEqual(0);
		
	});	
	
	it("setTrackTime", function(){				
		
		spyOn(playback.$.AudioManager, "setAudioTime");
		playback.setTrackTime(100);
		expect(playback.$.AudioManager.setAudioTime).toHaveBeenCalledWith(100);
		
	});		
	
		
	describe("intUpdateRateChanged", function(){
		beforeEach(function(){
			//objSetPlaybackList = {arSetPlaybackList: songs, intStartTrackIndex: 0, intStartTrackTime: 0};
		});
		
		it("fired on setIntUpdateRate", function(){				
			
			spyOn(playback, "intUpdateRateChanged");
			spyOn(playback, "startTrackTimeUpdate");
			playback.setIntUpdateRate(10);
			//333 matches what's currently in app.js (at the time of this message)
			expect(playback.intUpdateRateChanged).toHaveBeenCalledWith(333);
	
		});
		
		it("executes correctly", function(){				
			playback.$.AudioManager.boolAudioPlaying = true;
			spyOn(playback, "startTrackTimeUpdate");
			playback.intervalCheckTrackTime = "anything but undefined";
			playback.intUpdateRateChanged();
			expect(playback.startTrackTimeUpdate).toHaveBeenCalledWith();
	
		});		
		
		
		it("executes correctly WHEN this.$.AudioManager.boolAudioPlaying is true", function(){				
			playback.$.AudioManager.boolAudioPlaying = true;
			spyOn(playback, "startTrackTimeUpdate");
			playback.intervalCheckTrackTime = "anything but undefined";
			playback.intUpdateRateChanged();
			expect(playback.startTrackTimeUpdate).toHaveBeenCalled();
	
		});		
		it("executes correctly WHEN this.$.AudioManager.boolAudioPlaying is true", function(){				
			playback.$.AudioManager.boolAudioPlaying = false;
			spyOn(playback, "startTrackTimeUpdate");
			playback.intervalCheckTrackTime = "anything but undefined";
			playback.intUpdateRateChanged();
			expect(playback.startTrackTimeUpdate).not.toHaveBeenCalled();
	
		});		
	});	
	
	it("startTrackTimeUpdate", function(){				
		
		
		spyOn(window, "setInterval");
		playback.startTrackTimeUpdate();
		expect(window.setInterval).toHaveBeenCalled();
		
	});
	
	it("updateTrackTime", function(){				
		
		
		spyOn(playback, "doUpdateTrackTime");
		playback.updateTrackTime();
		var objTrackTimes = {floatTrackCurrentTime: playback.getTrackCurrentTime(), floatTrackDuration: playback.getTrackDuration()};
		expect(playback.doUpdateTrackTime).toHaveBeenCalledWith(objTrackTimes);
		
	});
	
		
	it("onAudioSrcChanged", function(){				
		
		spyOn(playback, "doTrackSrcChanged");
		
		objSetPlaybackList = {arSetPlaybackList: songs, intStartTrackIndex: 0, intStartTrackTime: 0};
		playback.$.PlaybackList.setPlaybackList(objSetPlaybackList);

		playback.onAudioSrcChanged({});

		var objTrackInfo = {strTrackArtist: playback.getTrackArtist(), strTrackTitle: playback.getTrackTitle() , strTrackAlbum: playback.getTrackAlbum(), strTrackGenre: playback.getTrackGenre(), strTrackImage: playback.getTrackImage(), intTrackIndex: playback.$.PlaybackList.intCurrTrackIndex, intTrackOrigIndex:  playback.$.PlaybackList.getCurrentPlayBackItem().origIndex, strTrackID: playback.getTrackID(), intTrackTime: playback.getTrackCurrentTime(), intTrackDuration: playback.getTrackDuration(), strTrackDuration: playback.getTrackDuration(true), boolForced: undefined};

		expect(playback.doTrackSrcChanged).toHaveBeenCalledWith(objTrackInfo);
		
	});
	
	
	
	describe("check (onAudioError)", function(){				
		beforeEach(function(){
			playback.$.playbackList = {getRepeatMode : function(){return 0;}};
			playback.$.AudioManager = {playAudio : function(){return 0;}};
			playback.$.PlaybackList.getCurrentPlayBackItem = function(){return {path:""}};
			//playback.stopTrackTimeUpdate = function(){};
		});
		it("should call stopTrackTimeUpdate", function(){
			spyOn(playback, "nextTrack");
			spyOn(playback, "stopTrackTimeUpdate");
			playback.onAudioError({});
			expect(playback.stopTrackTimeUpdate).toHaveBeenCalled();
			
		});
		
		
		describe("when it's called the first time", function(){
			/*it("should increment error Count", function(){
				spyOn(playback, "nextTrack");
				playback.intErrorCount = 0;
				playback.onAudioError({});
				expect(playback.intErrorCount).toBe(1);
			});*/
			it("should call playAudio if repeat mode is 2", function(){
				playback.$.AudioManager = {playAudio : function(){return 2;}};
				spyOn(playback.$.AudioManager, "playAudio");
				spyOn(playback, "stopTrackTimeUpdate");
				playback.onAudioError({});
				expect(playback.$.AudioManager.playAudio).toHaveBeenCalled();
				
			});
			it("should not call playAudio if repeat mode is 0", function(){
				spyOn(playback, "nextTrack");
				playback.$.AudioManager = {playAudio : function(){return 0;}};
				spyOn(playback.$.AudioManager, "playAudio");
				spyOn(playback, "stopTrackTimeUpdate");
				playback.onAudioError({});
				expect(playback.$.AudioManager.playAudio).not.toHaveBeenCalled();
				expect(playback.nextTrack).toHaveBeenCalledWith(true, false);
				
			});
			
			it("should call nextTrack", function(){
				spyOn(playback, "nextTrack");
				playback.onAudioError({});
				expect(playback.nextTrack).toHaveBeenCalledWith(true, false);
			});
		});
		describe("when it's called three times in a row", function(){
		
			it("shouldnt increment error Count", function(){
				spyOn(playback, "nextTrack");
				playback.intErrorCount = 4;
				playback.onAudioError({});
				expect(playback.intErrorCount).toBe(5);
			});
			/*it("shouldnt call nextTrack", function(){
				playback.nextTrack = function(){};
				playback.intErrorCount = 0;
				playback.onAudioError({});
				expect(playback.intErrorCount).toBe(1);
				playback.onAudioError({});
				expect(playback.intErrorCount).toBe(2);
				playback.onAudioError({});
				expect(playback.intErrorCount).toBe(3);
				spyOn(playback, "nextTrack");
				playback.onAudioError({});
				expect(playback.nextTrack).not.toHaveBeenCalled();
			});*/
		});
		
	});
					
	describe("nextTrack", function(){
		beforeEach(function(){
			playback.$.PlaybackList._arPlaybackList = songs;
			playback.$.PlaybackList.intCurrTrackIndex = 1;
			spyOn(playback, "playTrack");
		});
		
		it("should call this.$.PlaybackList.nextTrack", function(){
			spyOn(playback.$.PlaybackList, "nextTrack");
			playback.nextTrack(true);
			expect(playback.$.PlaybackList.nextTrack).toHaveBeenCalledWith(true);
		});

		it("should call this.$.PlaybackList.nextTrack false", function(){
			spyOn(playback.$.PlaybackList, "nextTrack");
			playback.nextTrack(false);
			expect(playback.$.PlaybackList.nextTrack).toHaveBeenCalledWith(false);
		});
		
		it("should pausePlayback if not going to the nextSong", function(){
			spyOn(playback.$.PlaybackList, "nextTrack").andReturn(false);
			spyOn(playback, "pausePlayback");
			playback.nextTrack(true);
			expect(playback.pausePlayback).toHaveBeenCalledWith(false);
		});
		it("should not pausePlayback if going to the nextSong", function(){
			spyOn(playback.$.PlaybackList, "nextTrack").andReturn(true);
			spyOn(playback, "pausePlayback");
			playback.nextTrack(true);
			expect(playback.pausePlayback).not.toHaveBeenCalled();
		});
		
		it("boolForce undefined", function(){				
			
			playback.nextTrack();
			expect(playback.playTrack).toHaveBeenCalledWith(0, undefined, undefined);
			
		});
		
		it("boolForce true", function(){				
			
			playback.nextTrack(true);
			expect(playback.playTrack).toHaveBeenCalledWith(0, true, undefined);
		
		});		

	});		

	describe("prevTrack", function(){
		beforeEach(function(){
			playback.$.PlaybackList._arPlaybackList = songs;
			playback.$.PlaybackList.intCurrTrackIndex = 1;
			spyOn(playback, "playTrack");
		});
		
		it("boolForce undefined", function(){				
			
			playback.prevTrack();
			expect(playback.playTrack).toHaveBeenCalledWith(0, undefined);
			
		});
		
		it("boolForce true", function(){				
			
			playback.prevTrack(true);
			expect(playback.playTrack).toHaveBeenCalledWith(0, true);
		
		});		

	});

	
	describe("shufflePlaylist", function(){
		beforeEach(function(){
			spyOn(playback.$.PlaybackList, "shufflePlaylist");
		});
		
		it("boolForceShuffle undefined", function(){				
			
			playback.shufflePlaylist();
			expect(playback.$.PlaybackList.shufflePlaylist).toHaveBeenCalledWith(undefined);
			
		});
		
		it("boolForceShuffle true", function(){				
			
			playback.shufflePlaylist(true);
			expect(playback.$.PlaybackList.shufflePlaylist).toHaveBeenCalledWith(true);
		
		});		

	});
	
	describe("songListChanged", function(){
		beforeEach(function(){
			spyOn(playback.$.PlaybackList, "songListChanged");
		});
		
		it("intCurrTrackOrigIndex undefined", function(){				
			
			playback.songListChanged();
			expect(playback.$.PlaybackList.songListChanged).toHaveBeenCalledWith(undefined);
			
		});
		
		it("intCurrTrackOrigIndex valid", function(){				
			
			playback.songListChanged(1);
			expect(playback.$.PlaybackList.songListChanged).toHaveBeenCalledWith(1);
			
		});	

	});
		
	
	describe("setRepeatMode", function(){
		beforeEach(function(){
			spyOn(playback.$.PlaybackList, "setRepeatMode");
		});
		
		it("intForceMode undefined", function(){				
			
			playback.setRepeatMode();
			expect(playback.$.PlaybackList.setRepeatMode).toHaveBeenCalledWith(undefined);
			
		});
		
		it("intForceMode valid", function(){				
			
			playback.setRepeatMode(1);
			expect(playback.$.PlaybackList.setRepeatMode).toHaveBeenCalledWith(1);
			
		});	

	});


	
	describe("Volume", function(){
		
		it("setVolume", function(){				
			spyOn(playback.$.AudioManager, "setAudioVolume");
			
			playback.setVolume(50);
			expect(playback.$.AudioManager.setAudioVolume).toHaveBeenCalledWith(50);
			
		});
		
		it("getVolume", function(){				
			spyOn(playback.$.AudioManager, "getAudioVolume");
			
			playback.getVolume(1);
			expect(playback.$.AudioManager.getAudioVolume).toHaveBeenCalled();
			
		});	

	});
	
	describe("Get Track Info methods", function(){
		beforeEach(function(){
			objSetPlaybackList = {arSetPlaybackList: songs, intStartTrackIndex: 1, intStartTrackTime: 0};
			playback.$.PlaybackList.setPlaybackList(objSetPlaybackList);		
		});
		
		it("getTrackTitle", function(){				
			expect(playback.getTrackTitle()).toEqual("Vanishing Point");
		});
		
		it("getTrackArtist", function(){				
			expect(playback.getTrackArtist()).toEqual("New Order");
		});
		
		it("getTrackAlbum", function(){				
			expect(playback.getTrackAlbum()).toEqual("Technique");
		});
		
		it("getTrackGenre", function(){				
			expect(playback.getTrackGenre()).toEqual("Rock");
		});
		
		describe("getTrackCurrentTime", function(){

			it("unformatted", function(){				
				expect(playback.getTrackCurrentTime()).toEqual(0);
			});		
			
			it("unformatted", function(){				
				expect(playback.getTrackCurrentTime(true)).toEqual("0:00");
			});		

		});
		
		describe("getTrackDuration", function(){

			it("unformatted", function(){				
				expect(playback.getTrackDuration()).toEqual(0);
			});		
			
			it("unformatted", function(){				
				expect(playback.getTrackDuration(true)).toEqual("0:00");
			});		
			
		});
		
		it("getTrackPlaying", function(){				
			spyOn(playback.$.AudioManager, "getBoolAudioPlaying");
			playback.getTrackPlaying();
			expect(playback.$.AudioManager.getBoolAudioPlaying).toHaveBeenCalled();
			
		});
		
		it("getTrackImage", function(){				
			spyOn(playback.$.PlaybackList, "getCurrentPlayBackImage");
			playback.getTrackImage();
			expect(playback.$.PlaybackList.getCurrentPlayBackImage).toHaveBeenCalled();
		});
		
		it("getTrackID", function(){				
			spyOn(playback.$.PlaybackList, "getCurrSongID");
			playback.getTrackID();
			expect(playback.$.PlaybackList.getCurrSongID).toHaveBeenCalled();
		});
		
		it("getTrackList", function(){				
			spyOn(playback.$.PlaybackList, "getPlaybackList");
			playback.getTrackList();
			expect(playback.$.PlaybackList.getPlaybackList).toHaveBeenCalled();
		});
		
	});	
	describe("test onEnded", function(){
		describe("when boolSuspendUpdates is set to false", function(){
			beforeEach(function(){
				playback.boolSuspendUpdates = false;
			});
			it("should call doUpdateTrackTime", function(){
				spyOn(playback, "doUpdateTrackTime");
				playback.onEnded();
				expect(playback.doUpdateTrackTime).toHaveBeenCalled();
			});
			it("should call doSongEnd", function(){
				spyOn(playback, "doSongEnd");
				playback.onEnded();
				expect(playback.doSongEnd).toHaveBeenCalled();
			});
			it("should call getTrackDuration", function(){
				spyOn(playback, "getTrackDuration");
				playback.onEnded();
				expect(playback.getTrackDuration).toHaveBeenCalled();
			});
			//We don't care about it, should be 0 at this point
			it("should not call getTrackCurrentTime", function(){
				spyOn(playback, "getTrackCurrentTime");
				playback.onEnded();
				expect(playback.getTrackCurrentTime).not.toHaveBeenCalled();
			});
			
		});
		describe("when boolSuspendUpdates is set to true", function(){
			beforeEach(function(){
				playback.boolSuspendUpdates = true;
			});
			it("should  not call doUpdateTrackTime", function(){
				spyOn(playback, "doUpdateTrackTime");
				playback.onEnded();
				expect(playback.doUpdateTrackTime).not.toHaveBeenCalled();
			});
			it("should call doSongEnd", function(){
				spyOn(playback, "doSongEnd");
				playback.onEnded();
				expect(playback.doSongEnd).toHaveBeenCalled();
			});
			it("should not call getTrackDuration", function(){
				spyOn(playback, "getTrackDuration");
				playback.onEnded();
				expect(playback.getTrackDuration).not.toHaveBeenCalled();
			});
			//We don't care about it, should be 0 at this point
			it("should not call getTrackCurrentTime", function(){
				spyOn(playback, "getTrackCurrentTime");
				playback.onEnded();
				expect(playback.getTrackCurrentTime).not.toHaveBeenCalled();
			});
		});
	});
	
	
});