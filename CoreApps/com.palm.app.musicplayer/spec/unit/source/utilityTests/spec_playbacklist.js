/*globals LibraryTools, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, jasmine, getJson */
describe("PlaybackList Tests", function(){
		
	var playbackList;
	
	var song1 = getJson("mock_song1");
	var song2 = getJson("mock_song2");
	var song3 = getJson("mock_song3");
	var songs = getJson("mock_songArr");
	
	beforeEach(function () {
		playbackList = enyo.create({kind: "kindPlaybackList"});
	});
	
	
	
	describe("setPlaybackList", function(){

		it("setPlaybackList with different song list", function(){				
		
			var objSetPlaybackList = {arSetPlaybackList: songs, intStartTrackIndex: 0, intStartTrackTime: 0, strOriginListID: "NewListGUID"};
			playbackList.strOriginListID = "OldListGUID";
			
			expect(playbackList.setPlaybackList(objSetPlaybackList)).toEqual(true);
			expect(playbackList._arPlaybackList).toEqual(songs);
	
		});
	
		
		it("setPlaybackList with same song list", function(){				
			spyOn(playbackList, "switchTrack");
		
			var objSetPlaybackList = {arSetPlaybackList: songs, intStartTrackIndex: 0, intStartTrackTime: 0, strOriginListID: "OldListGUID"};
			playbackList.strOriginListID = "OldListGUID";
			
			
			expect(playbackList.setPlaybackList(objSetPlaybackList)).toEqual(true);
			//expect(playbackList.switchTrack).toHaveBeenCalledWith(0);
	
		});
		
		
		it("setPlaybackList with different song list & shuffle on", function(){				
			spyOn(playbackList, "shufflePlaylist");
		
			var objSetPlaybackList = {arSetPlaybackList: songs, intStartTrackIndex: 0, intStartTrackTime: 0, strOriginListID: "NewListGUID"};
			playbackList.strOriginListID = "OldListGUID";
			playbackList._boolShuffleOn = true;
			
			expect(playbackList.setPlaybackList(objSetPlaybackList)).toEqual(true);
			expect(playbackList._arPlaybackList).toEqual(songs);
			expect(playbackList.shufflePlaylist).toHaveBeenCalledWith(true);
	
		});
	
	});
	
	
	describe("nextTrack", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});
		
		describe("Repeat off", function(){
			beforeEach(function(){
				playbackList.intRepeatMode = 0; //Repeat Off
			});		

			it("not end of list - repeat off - not forced", function(){				
			
				playbackList.intCurrTrackIndex = 0;
				var boolForce = false;
				expect(playbackList.nextTrack(boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(1);
		
			});
			
			it("end of list - repeat off - not forced", function(){				
			
				playbackList.intCurrTrackIndex = 2;
				var boolForce = false;
				expect(playbackList.nextTrack(boolForce)).toEqual(false);
		
			});
			
			it("not end of list - repeat off - forced", function(){				
			
				playbackList.intCurrTrackIndex = 0;
				var boolForce = true;
				expect(playbackList.nextTrack(boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(1);
		
			});
			
			it("end of list - repeat off - forced", function(){				
			
				playbackList.intCurrTrackIndex = 2;
				var boolForce = true;
				expect(playbackList.nextTrack(boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(0);
	
			});
		});
		describe("Repeat All", function(){
			beforeEach(function(){
				playbackList.intRepeatMode = 1; //Repeat All
			});
			
			it("not end of list - repeat all - not forced", function(){				
			
				playbackList.intCurrTrackIndex = 0;
				var boolForce = false;
				expect(playbackList.nextTrack(boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(1);
		
			});
			
			it("end of list - repeat all - not forced", function(){				
			
				playbackList.intCurrTrackIndex = 2;
				var boolForce = false;
				expect(playbackList.nextTrack(boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(0);
		
			});
			
			it("not end of list - repeat all - forced", function(){				
			
				playbackList.intCurrTrackIndex = 0;
				var boolForce = true;
				expect(playbackList.nextTrack(boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(1);
		
			});
			
			it("end of list - repeat all - forced", function(){				
			
				playbackList.intCurrTrackIndex = 2;
				var boolForce = true;
				expect(playbackList.nextTrack(boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(0);
		
			});
		});
		
		describe("Repeat One", function(){
			beforeEach(function(){
				playbackList.intRepeatMode = 2; //Repeat One
			});
			
			it("not end of list - repeat one - not forced", function(){				
			
				playbackList.intCurrTrackIndex = 0;
				var boolForce = false;
				expect(playbackList.nextTrack(boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(0);
		
			});
			
			it("end of list - repeat one - not forced", function(){				
			
				playbackList.intCurrTrackIndex = 2;
				var boolForce = false;
				expect(playbackList.nextTrack(boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(2);
		
			});
			
			it("not end of list - repeat one - forced", function(){				
			
				playbackList.intCurrTrackIndex = 0;
				var boolForce = true;
				expect(playbackList.nextTrack(boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(1);
		
			});
			
			it("end of list - repeat one - forced", function(){				
			
				playbackList.intCurrTrackIndex = 2;
				var boolForce = true;
				expect(playbackList.nextTrack(boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(0);
		
			});
		});
					
	});
	
	
	
	
	describe("prevTrack", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});
		
		describe("Repeat off", function(){
			beforeEach(function(){
				playbackList.intRepeatMode = 0; //Repeat Off
			});		
			it("not start of list - repeat off - not forced", function(){				
			
				playbackList.intCurrTrackIndex = 2;
				var boolForce = false;
				expect(playbackList.prevTrack(0, boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(1);
		
			});
			
			it("start of list - repeat off - not forced", function(){				
			
				playbackList.intCurrTrackIndex = 0;
				var boolForce = false;
				expect(playbackList.prevTrack(0, boolForce)).toEqual(false);
		
			});
			
			it("not start of list - repeat off - forced", function(){				
			
				playbackList.intCurrTrackIndex = 2;
				var boolForce = true;
				expect(playbackList.prevTrack(0, boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(1);
		
			});
			
			it("start of list - repeat off - forced", function(){				
			
				playbackList.intCurrTrackIndex = 0;
				var boolForce = true;
				expect(playbackList.prevTrack(0, boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(2);
	
			});
		});
		describe("Repeat All", function(){
			beforeEach(function(){
				playbackList.intRepeatMode = 1; //Repeat All
			});
			
			it("not start of list - repeat all - not forced", function(){				
			
				playbackList.intCurrTrackIndex = 2;
				var boolForce = false;

				expect(playbackList.prevTrack(0, boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(1);
		
			});
			
			it("start of list - repeat all - not forced", function(){				
			
				playbackList.intCurrTrackIndex = 0;
				var boolForce = false;

				expect(playbackList.prevTrack(0, boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(2);
		
			});
			
			it("not start of list - repeat all - forced", function(){				
			
				playbackList.intCurrTrackIndex = 2;
				var boolForce = true;

				expect(playbackList.prevTrack(0, boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(1);
		
			});
			
			it("start of list - repeat all - forced", function(){				
			
				playbackList.intCurrTrackIndex = 0;
				var boolForce = true;

				expect(playbackList.prevTrack(0, boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(2);
		
			});
		});
		
		describe("Repeat One", function(){
			beforeEach(function(){
				playbackList.intRepeatMode = 2; //Repeat One
			});
			
			it("not start of list - repeat one - not forced", function(){				
			
				playbackList.intCurrTrackIndex = 1;
				var boolForce = false;

				expect(playbackList.prevTrack(0, boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(1);
		
			});
			
			it("start of list - repeat one - not forced", function(){				
			
				playbackList.intCurrTrackIndex = 0;
				var boolForce = false;

				expect(playbackList.prevTrack(0, boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(0);
		
			});
			
			it("not start of list - repeat one - forced", function(){				
			
				playbackList.intCurrTrackIndex = 1;
				var boolForce = true;

				expect(playbackList.prevTrack(0, boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(0);
		
			});
			
			it("start of list - repeat one - forced", function(){				
			
				playbackList.intCurrTrackIndex = 0;
				var boolForce = true;

				expect(playbackList.prevTrack(0, boolForce)).toEqual(true);
				expect(playbackList.intCurrTrackIndex).toEqual(2);
		
			});
		});
					
	});	
	

	


	describe("switchTrack", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});
		
		it("valid intSetTrackIndex", function(){				
			
			playbackList.intCurrTrackIndex = 0;
			playbackList.switchTrack(1);
			expect(playbackList.intCurrTrackIndex).toEqual(1);
		
		});
		
		it("undefined intSetTrackIndex", function(){				
			
			playbackList.intCurrTrackIndex = 0;
			playbackList.switchTrack(undefined);
			expect(playbackList.intCurrTrackIndex).toEqual(0);
				
		});
		
		it("invalid intSetTrackIndex out of range", function(){				
			
			playbackList.intCurrTrackIndex = 0;
			playbackList.switchTrack(3);
			expect(playbackList.intCurrTrackIndex).toEqual(0);
			
		});
		
		it("invalid intSetTrackIndex negative", function(){				
			
			playbackList.intCurrTrackIndex = 0;
			playbackList.switchTrack(3);
			expect(playbackList.intCurrTrackIndex).toEqual(0);
			
		});
		
		it("shuffled list ", function(){				
			
			spyOn(playbackList, "getCurrentSongIndex");
				
			var songsShuffled = getJson("mock_songArr_shuffled");

			playbackList.intCurrTrackIndex = 0;
			
			playbackList._arPlaybackList = songsShuffled;
			playbackList._boolShuffleOn = true;
			playbackList.switchTrack(2);
			
			expect(playbackList.getCurrentSongIndex).toHaveBeenCalledWith(2);
			
		});
		
	});
	
	describe("setRepeatMode", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});

		it("cycle repeat modes ", function(){
		
			playbackList.intRepeatMode = 0;
			
			playbackList.setRepeatMode();			
			expect(playbackList.intRepeatMode).toEqual(1);
			expect(playbackList._arRepeatMode[playbackList.intRepeatMode]).toEqual("all");

			playbackList.setRepeatMode();			
			expect(playbackList.intRepeatMode).toEqual(2);
			expect(playbackList._arRepeatMode[playbackList.intRepeatMode]).toEqual("one");

			playbackList.setRepeatMode();			
			expect(playbackList.intRepeatMode).toEqual(0);			
			expect(playbackList._arRepeatMode[playbackList.intRepeatMode]).toEqual("off");
		
		});
		
		it("force repeat mode ", function(){
			
			playbackList.setRepeatMode(1);			
			expect(playbackList.intRepeatMode).toEqual(1);
			expect(playbackList._arRepeatMode[playbackList.intRepeatMode]).toEqual("all");

		});

		
		it("force repeat mode invalid out of range", function(){
			
			playbackList.setRepeatMode(100);			
			expect(playbackList.intRepeatMode).toEqual(0);
			expect(playbackList._arRepeatMode[playbackList.intRepeatMode]).toEqual("off");
			
		});

	
	
	});
	

	describe("shufflePlaylist", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});

		it("toggle to on -  boolForceShuffle undefined", function(){
		
			spyOn(LibraryTools, "sortRandom");

			playbackList._boolShuffleOn = false;
		
			playbackList.shufflePlaylist();
			
			expect(playbackList._boolShuffleOn).toEqual(true);
			expect(LibraryTools.sortRandom).toHaveBeenCalledWith(playbackList._arPlaybackList);

		});
		
		it("toggle to off -  boolForceShuffle undefined", function(){
		
			spyOn(LibraryTools, "sortOrigOrder");

			playbackList._boolShuffleOn = true;
		
			playbackList.shufflePlaylist();
			
			expect(playbackList._boolShuffleOn).toEqual(false);
			expect(LibraryTools.sortOrigOrder).toHaveBeenCalledWith(playbackList._arPlaybackList);

		});

		it("Force to on -  boolForceShuffle true", function(){
		
			spyOn(LibraryTools, "sortRandom");

			playbackList._boolShuffleOn = true;
		
			playbackList.shufflePlaylist(true);
			
			expect(playbackList._boolShuffleOn).toEqual(true);
			expect(LibraryTools.sortRandom).toHaveBeenCalledWith(playbackList._arPlaybackList);

		});			

		it("Force to off -  boolForceShuffle false", function(){
		
			spyOn(LibraryTools, "sortOrigOrder");

			playbackList._boolShuffleOn = false;
		
			playbackList.shufflePlaylist(false);
			
			expect(playbackList._boolShuffleOn).toEqual(false);
			expect(LibraryTools.sortOrigOrder).toHaveBeenCalledWith(playbackList._arPlaybackList);

		});	
	
	
	
	});	
	

	describe("shufflePlaylist", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});
		
		it("not shuffled", function(){
		
			spyOn(playbackList, "getCurrentSongIndex");
			
			playbackList._boolShuffleOn = false;
			playbackList.songListChanged(1);
			
			expect(playbackList.getCurrentSongIndex).toHaveBeenCalledWith(1);
			
		});
		
		it("shuffled", function(){
		
			spyOn(playbackList, "shufflePlaylist");
			spyOn(playbackList, "getCurrentSongIndex");
			
			playbackList._boolShuffleOn = true;
			playbackList.songListChanged(1);
			
			expect(playbackList.shufflePlaylist).toHaveBeenCalledWith(true);
			expect(playbackList.getCurrentSongIndex).toHaveBeenCalledWith(1);
			
		});		
	
	});
	

	describe("getCurrentSongIndex", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});
		
		it("list original order", function(){
			expect(playbackList.getCurrentSongIndex(2)).toEqual(2);		
		});
		
		it("list sorted/shuffled", function(){
			
			var songsShuffled = getJson("mock_songArr_shuffled");			
			playbackList._arPlaybackList = songsShuffled;

			expect(playbackList.getCurrentSongIndex(2)).toEqual(1);		
		});
		
		it("invalid intOrigIndex", function(){
			expect(playbackList.getCurrentSongIndex(100)).toEqual(-1);		
		});
			
	});
	

	describe("getCurrSongOrigIndex", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});	
		
		it("list original order", function(){
		
			playbackList.intCurrTrackIndex = 1;
			expect(playbackList.getCurrSongOrigIndex()).toEqual(1);		
		
		});
		
		it("list sorted/shuffled", function(){
			
			var songsShuffled = getJson("mock_songArr_shuffled");			
			playbackList._arPlaybackList = songsShuffled;
			playbackList.intCurrTrackIndex = 1;

			expect(playbackList.getCurrSongOrigIndex()).toEqual(2);		
		});
		
		it("invalid intOrigIndex", function(){
			playbackList.intCurrTrackIndex = 100;
			expect(playbackList.getCurrSongOrigIndex()).toEqual(-9999);		
		});
		
		
	});
	
	describe("getCurrSongID", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});	
		
		it("valid playbackList.intCurrTrackIndex", function(){
		
			playbackList.intCurrTrackIndex = 0;
			expect(playbackList.getCurrSongID()).toEqual("++HLDrGuZj_6Vq9e");		
		
		});
		
		it("invalid playbackList.intCurrTrackIndex", function(){
		
			playbackList.intCurrTrackIndex = 100;
			expect(playbackList.getCurrSongID()).toEqual("");		
		
		});
	
	});
	
	
	describe("getPlaybackList", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});
	
		it("simple getPlaybackList", function(){
			
			expect(playbackList.getPlaybackList()).toEqual(songs);		
			
		});		
		
	});
	
	describe("getCurrentPlayBackItem", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});
	
		it("simple getCurrentPlayBackItem", function(){
			
			playbackList.intCurrTrackIndex = 0;
			expect(playbackList.getCurrentPlayBackItem()).toEqual(songs[0]);		
			
		});		
		
	});
	
	describe("getPlaybacklistLength", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});
	
		it("simple getPlaybacklistLength", function(){
			
			expect(playbackList.getPlaybacklistLength()).toEqual(3);		
			
		});		
		
	});
	
	describe("getCurrentPlayBackImage", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});
	
		it("simple getCurrentPlayBackImage", function(){
			
			expect(playbackList.getCurrentPlayBackImage()).toEqual("../music/Chris Cornell/Euphoria Morning/01 - Can't Change Me.mp3:78:36461");		
			
		});		
		
	});
	
	describe("getShuffleState", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});
	
		it("simple getShuffleState", function(){
			
			expect(playbackList.getShuffleState()).toEqual(false);		
			
		});		
		
	});	
	
	describe("getRepeatMode", function(){
		
		beforeEach(function(){
			playbackList._arPlaybackList = songs;
		});
	
		it("simple getRepeatMode", function(){
			
			expect(playbackList.getRepeatMode()).toEqual(0);		
			
		});
	
		it("formatted off", function(){
		
			playbackList.intRepeatMode = 0;
			expect(playbackList.getRepeatMode(true)).toEqual("off");		
			
		});
	
		it("formatted all", function(){
		
			playbackList.intRepeatMode = 1;
			expect(playbackList.getRepeatMode(true)).toEqual("all");		
			
		});
	
		it("formatted one", function(){
		
			playbackList.intRepeatMode = 2;
			expect(playbackList.getRepeatMode(true)).toEqual("one");		
			
		});

	});		
	
	
	
	
	
	
	
	
	
});

































