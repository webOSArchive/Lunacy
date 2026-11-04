/*globals enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson */

describe("AudioManager", function(){

	beforeEach(function () {
		audioManager = enyo.create({kind: "kindAudioManager"});
	});
		
		it("setupAudio", function(){
			
			//var objAudioFake = new Audio();
			//spyOn(window, "Audio").andReturn(objAudioFake);
			spyOn(audioManager.objAudio, "addEventListener");
			
			audioManager.objAudio = undefined;
			audioManager.setupAudio();
			
			expect(audioManager.objAudio).toBeDefined();
			
			//expect(window.Audio).toHaveBeenCalled();
			
			//expect(audioManager.objAudio.addEventListener).toHaveBeenCalledWith('load', enyo.bind(this, this.onAudioLoaded), false);
			//expect(audioManager.objAudio.addEventListener).toHaveBeenCalledWith('play', enyo.bind(this, this.onAudioPlayed), false);
		});	
		
		it("playAudio", function(){
		
			spyOn(audioManager, "doSrcChanged");
			spyOn(audioManager.objAudio, "load");
			spyOn(audioManager.objAudio, "play");
			spyOn(audioManager, "_boolAudioLoaded");
			
			audioManager.playAudio("some.mp3", 0, false);
				
			expect(audioManager.doSrcChanged).toHaveBeenCalledWith(false);
			
			expect(audioManager._boolAudioLoaded).toBe(false);
			expect(audioManager.objAudio.src).toContain("some.mp3");
			expect(audioManager.objAudio.load).toHaveBeenCalled();
			expect(audioManager.objAudio.play).toHaveBeenCalled();

	
		});
		
		
		describe("pauseAudio", function(){
		
			it("when playing - no params", function(){
				spyOn(audioManager.objAudio, "pause");
				
				audioManager.boolAudioPlaying = true;
				var boolPauseResult = audioManager.pauseAudio();
					
				expect(audioManager.boolAudioPlaying).toEqual(false);
				expect(audioManager.objAudio.pause).toHaveBeenCalled();
			});
			
			it("when paused - no params", function(){
				spyOn(audioManager.objAudio, "play");
				
				audioManager.boolAudioPlaying = false;
				var boolPauseResult = audioManager.pauseAudio();
					
				expect(audioManager.boolAudioPlaying).toEqual(true);
				expect(audioManager.objAudio.play).toHaveBeenCalled();
			});
			
			it("when playing - boolPlayPause true", function(){
				spyOn(audioManager.objAudio, "play");
				
				audioManager.boolAudioPlaying = true;
				var boolPauseResult = audioManager.pauseAudio(true);
					
				expect(audioManager.boolAudioPlaying).toEqual(true);
				expect(audioManager.objAudio.play).toHaveBeenCalled();
			});
			
		});
		
		it("onAudioPlaying", function(){
			spyOn(audioManager, "doPausePlay");
			spyOn(audioManager, "doPlaying");
			spyOn(audioManager, "_boolAudioLoaded");
			audioManager.onAudioPlaying({});
				
			expect(audioManager.doPausePlay).toHaveBeenCalledWith(true);
			expect(audioManager.doPlaying).toHaveBeenCalled();
			expect(audioManager._boolAudioLoaded).toEqual(true);
			expect(audioManager.boolAudioPlaying).toEqual(true);
			expect(audioManager.boolAudioPaused).toEqual(false);
		});	
	
		it("onAudioPaused", function(){
			spyOn(audioManager, "doPausePlay");

			audioManager.onAudioPaused({});
				
			expect(audioManager.doPausePlay).toHaveBeenCalledWith(false);
			expect(audioManager.boolAudioPlaying).toEqual(false);
			expect(audioManager.boolAudioPaused).toEqual(true);
		});		
		
		it("onAudioEnded", function(){
			spyOn(audioManager, "doEnded");

			audioManager.onAudioEnded();
				
			expect(audioManager.doEnded).toHaveBeenCalled();
		});		
		
		it("setAudioTime", function(){
			audioManager._boolAudioLoaded = true;
			spyOn(audioManager, "getAudioDuration").andReturn(90);
			audioManager.objAudio = {src:"some.mp3", currentTime: 0}; //fake audio object
			audioManager.setAudioTime(50);

			expect(audioManager.objAudio.currentTime).toEqual(45);
		});	
		
		it("setAudioTime when not loaded", function(){
			spyOn(audioManager, "getAudioDuration").andReturn(90);
			audioManager._boolAudioLoaded = false;
			audioManager.objAudio = {src:"some.mp3", currentTime: 0}; //fake audio object
			audioManager.setAudioTime(50);

			expect(audioManager.objAudio.currentTime).toEqual(0);
		});	
		
		it("getAudioCurrentTime", function(){
			audioManager.objAudio = {src:"some.mp3", currentTime: 30}; //fake audio object
			
			expect(audioManager.getAudioCurrentTime()).toEqual(30);
			
		});		
		
		it("getAudioDuration", function(){
			audioManager.objAudio = {src:"some.mp3", currentTime: 30, duration: 90}; //fake audio object
			
			expect(audioManager.getAudioDuration()).toEqual(90);
			
		});
		
		it("setAudioVolume", function(){
			
			audioManager.setAudioVolume(50);

			expect(audioManager.objAudio.volume).toEqual(.5);
		});	
	
		it("setAudioVolume", function(){
			audioManager.objAudio.volume = .5

			expect(audioManager.getAudioVolume()).toEqual(.5);
		});		

		//calling onAudioPaused to update UI on error
		it("onError_Play", function(){
			spyOn(audioManager, "doAudioError");
			spyOn(audioManager, "onAudioPaused");
			audioManager._boolPlayingBeforeError = true;
			var event = {};
			audioManager.onError_Play(event,{});
			expect(event.forcePlay).toBe(true);
			expect(audioManager.onAudioPaused).toHaveBeenCalled();
			expect(audioManager.doAudioError).toHaveBeenCalled();
			
			
			
		});
	
		//calling onAudioPaused to update UI on error
		it("onError_Stall", function(){
			spyOn(audioManager, "doAudioError");
			spyOn(audioManager, "onAudioPaused");
			audioManager._boolPlayingBeforeError = true;
			var event = {};
			audioManager.onError_Stall(event,{});
			expect(event.forcePlay).toBe(true);
			expect(audioManager.onAudioPaused).toHaveBeenCalled();
			expect(audioManager.doAudioError).toHaveBeenCalled();
		});		
	
		it("onError_Watchdog", function(){
			spyOn(audioManager, "doAudioError");

			audioManager.onError_Watchdog();
				
			expect(audioManager.doAudioError).toHaveBeenCalled();
		});			
		
	
	
});