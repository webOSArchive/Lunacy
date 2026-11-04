/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return LibraryTools;
//			}
//var objName = "LibraryTools";
//
//var before = function (obj, callbacks) {
//	
//	}
//var event = [[null, {rowIndex:0}]];
//var params = {
//		sortFuncSong : [[{title: 'a'},{title: 'a'}],[{title: 'a'},{title: 'b'}],[{title: 'b'},{title: 'a'}]]
//		 
//};
//
//specOut(creator, objName, before, params);
var songs;


describe('LibraryTools tests', function(){
	//var creator = function (){
	//			return LibraryTools;
	//		};
	//var LibraryTools;
	beforeEach(function(){
		//LibraryTools = creator();
		songs = getJson("mock_manySongs");
		var before = function (obj, callbacks) {
	
		};
		var callback = function(one, fun){return fun;};
		before(LibraryTools,callback);
	});
	describe('test (sortbyArtist)', function(){
		it("should sort asc", function(){
			var asc = true;
			LibraryTools.sortbyArtist(songs, asc);
			var first = true;
			for(var i in songs)
			{
				if(first)
				{
					first = !first;
					continue;
				}
				
				if(enyo.g11n.Char.toUpper(songs[i].artist) === enyo.g11n.Char.toUpper(songs[i-1].artist))
				{
					if(enyo.g11n.Char.toUpper(songs[i].album) === enyo.g11n.Char.toUpper(songs[i-1].album))
					{
						expect(songs[i].track.position < songs[i-1].track.position).toBe(false);
					}else{
						expect(enyo.g11n.Char.toUpper(songs[i].album) < enyo.g11n.Char.toUpper(songs[i-1].album)).toBe(false);
					}
				}
				else
				{
					expect(enyo.g11n.Char.toUpper(songs[i].artist) < enyo.g11n.Char.toUpper(songs[i-1].artist)).toBe(!asc);
				}
			}
		});
			
		it("should sort dsec", function(){
			var asc = false;
			LibraryTools.sortbyArtist(songs, asc);
			var first = true;
			for(var i in songs)
			{
				if(first)
				{
					first = !first;
					continue;
				}
				
				if(enyo.g11n.Char.toUpper(songs[i].artist) === enyo.g11n.Char.toUpper(songs[i-1].artist)){
					if(enyo.g11n.Char.toUpper(songs[i].album) === enyo.g11n.Char.toUpper(songs[i-1].album)){
						expect(songs[i].track.position < songs[i-1].track.position).toBe(false);
					}else{
						expect(enyo.g11n.Char.toUpper(songs[i].album) < enyo.g11n.Char.toUpper(songs[i-1].album)).toBe(false);
					}
				}else{
					expect(enyo.g11n.Char.toUpper(songs[i].artist) < enyo.g11n.Char.toUpper(songs[i-1].artist)).toBe(!asc);
				}
			}
		});
		
		it("should sort undefined", function(){
			var asc = false;
			LibraryTools.sortbyArtist(songs);
			var first = true;
			for(var i in songs)
			{
				if(first)
				{
					first = !first;
					continue;
				}
				
				if(enyo.g11n.Char.toUpper(songs[i].artist) === enyo.g11n.Char.toUpper(songs[i-1].artist)){
					if(enyo.g11n.Char.toUpper(songs[i].album) === enyo.g11n.Char.toUpper(songs[i-1].album)){
						expect(songs[i].track.position < songs[i-1].track.position).toBe(false);
					}else{
						expect(enyo.g11n.Char.toUpper(songs[i].album) < enyo.g11n.Char.toUpper(songs[i-1].album)).toBe(false);
					}
				}else{
					expect(enyo.g11n.Char.toUpper(songs[i].artist) < enyo.g11n.Char.toUpper(songs[i-1].artist)).toBe(!asc);
				}
			}
		});
		
		
	});
	describe('test (sortbySong)', function(){
		
		it("should sort asc", function(){
			var asc = true;
			LibraryTools.sortbySong(songs, asc);
			var first = true;
			
			for(var i in songs)
			{
				
				
				if(first)
				{
					first = !first;
					continue;
				}
				
				if(enyo.g11n.Char.toUpper(songs[i].title) !== enyo.g11n.Char.toUpper(songs[i-1].title)){
					expect(enyo.g11n.Char.toUpper(songs[i].title) < enyo.g11n.Char.toUpper(songs[i-1].title)).toBe(!asc);
				}
			}
		});
			
		it("should sort desc", function(){
			var asc = false;
			LibraryTools.sortbySong(songs, asc);
			var first = true;
			for(var i in songs)
			{
				if(first)
				{
					first = !first;
					continue;
				}
				
				if(enyo.g11n.Char.toUpper(songs[i].title) !== enyo.g11n.Char.toUpper(songs[i-1].title)){
					expect(enyo.g11n.Char.toUpper(songs[i].title) < enyo.g11n.Char.toUpper(songs[i-1].title)).toBe(!asc);
				}
			}
		});
		it("should sort undefined", function(){
			var asc = true;
			LibraryTools.sortbySong(songs);
			var first = true;
			for(var i in songs)
			{
				if(first)
				{
					first = !first;
					continue;
				}
				
				if(enyo.g11n.Char.toUpper(songs[i].title) !== enyo.g11n.Char.toUpper(songs[i-1].title)){
					expect(enyo.g11n.Char.toUpper(songs[i].title) < enyo.g11n.Char.toUpper(songs[i-1].title)).toBe(asc);
				}
			}
		});
		
	});
	describe('test (sortFuncSong)', function(){
		it('testing (sortFuncSong) with params [{"title":"a"},{"title":"a"}]', function(){
			expect(LibraryTools.sortFuncSong({"title":"a"},{"title":"a"})).toBe(0);		
		});
		it('testing (sortFuncSong) with params [{"title":"a"},{"title":"b"}]', function(){
			expect(LibraryTools.sortFuncSong({"title":"a"},{"title":"b"})).toBe(-1);	
		});
		it('testing (sortFuncSong) with params [{"title":"a"},{"title":"B"}]', function(){
			expect(LibraryTools.sortFuncSong({"title":"a"},{"title":"B"})).toBe(-1);	
		});
		it('testing (sortFuncSong) with params [{"title":"b"},{"title":"a"}]', function(){
			expect(LibraryTools.sortFuncSong({"title":"b"},{"title":"a"})).toBe(1);	
		});
	});
	describe('test (sortbyPos)', function(){
		it("should sort asc", function(){
			var asc = true;
			LibraryTools.sortbyPos(songs, asc);
			var first = true;
			
			for(var i in songs)
			{
				
				
				if(first)
				{
					first = !first;
					continue;
				}
				
				if(songs[i].track.position !== songs[i-1].track.position){
					expect(songs[i].track.position < songs[i-1].track.position).toBe(!asc);
				}
			}
		});
		it("should sort desc", function(){
			var asc = false;
			LibraryTools.sortbyPos(songs, asc);
			var first = true;
			
			for(var i in songs)
			{
				
				
				if(first)
				{
					first = !first;
					continue;
				}
				
				if(songs[i].track.position !== songs[i-1].track.position){
					expect(songs[i].track.position < songs[i-1].track.position).toBe(!asc);
				}
			}
		});
		it("should sort undefined", function(){
			var asc = true;
			LibraryTools.sortbyPos(songs);
			var first = true;
			
			for(var i in songs)
			{
				
				
				if(first)
				{
					first = !first;
					continue;
				}
				
				if(songs[i].track.position !== songs[i-1].track.position){
					expect(songs[i].track.position < songs[i-1].track.position).toBe(asc);
				}
			}
		});
		
	});
	describe('test (sortbyAlbum)', function(){
		it("should sort asc", function(){
			var asc = true;
			LibraryTools.sortbyAlbum(songs, asc);
			var first = true;
			
			for(var i in songs)
			{
				
				
				if(first)
				{
					first = !first;
					continue;
				}
				
				if(enyo.g11n.Char.toUpper(songs[i].album) === enyo.g11n.Char.toUpper(songs[i-1].album)){
					expect(songs[i].track.position < songs[i-1].track.position).toBe(!asc);
				}else{
					expect(enyo.g11n.Char.toUpper(songs[i].album)< enyo.g11n.Char.toUpper(songs[i-1].album)).toBe(!asc);
				}
			}
		});
	});
	describe('test (sortRandom)', function(){
		it("it should call use random", function(){
			spyOn(Math, "random");
			LibraryTools.sortRandom(songs);
			expect(Math.random).toHaveBeenCalled();
		});
		it("should call sort", function(){
			spyOn(songs, "sort");
			LibraryTools.sortRandom(songs);
			expect(songs.sort).toHaveBeenCalled();
		});
	});
	describe('test (sortOrigOrder)', function(){
		it("should sort original order", function(){
			var asc = true;
			for(var i in songs){
				songs[i].origIndex = i;
			}
			
			LibraryTools.sortRandom(songs);
			LibraryTools.sortOrigOrder(songs);
			var first = true;
			for(var i in songs)
			{
				console.log(i);
				if(first || !songs[i] || !songs[i-1] )
				{
					first = !first;
					continue;
				}
				expect(true);
				expect(songs[i].origIndex < songs[i-1].origIndex).toBe(false);
				
			}
		});
	});
	describe('test (sort)', function(){
		it("testing title true", function(){
			spyOn(LibraryTools, "sortbySong");
			LibraryTools.sort("title", songs, true);
			expect(LibraryTools.sortbySong).toHaveBeenCalledWith(songs, true);
		});
		it("testing title true", function(){
			spyOn(LibraryTools, "sortbySong");
			LibraryTools.sort("title", songs, false);
			expect(LibraryTools.sortbySong).toHaveBeenCalledWith(songs, false);
		});
		it("testing pos true", function(){
			spyOn(LibraryTools, "sortbyPos");
			LibraryTools.sort("pos", songs, true);
			expect(LibraryTools.sortbyPos).toHaveBeenCalledWith(songs, true);
		});
		it("testing pos true", function(){
			spyOn(LibraryTools, "sortbyPos");
			LibraryTools.sort("pos", songs, false);
			expect(LibraryTools.sortbyPos).toHaveBeenCalledWith(songs, false);
		});
		it("testing album true", function(){
			spyOn(LibraryTools, "sortbyAlbum");
			LibraryTools.sort("album", songs, true);
			expect(LibraryTools.sortbyAlbum).toHaveBeenCalledWith(songs, true);
		});
		it("testing album true", function(){
			spyOn(LibraryTools, "sortbyAlbum");
			LibraryTools.sort("album", songs, false);
			expect(LibraryTools.sortbyAlbum).toHaveBeenCalledWith(songs, false);
		});
	});
	
	it("should set error.prototype", function(){
		expect(typeof Error.prototype._toString).toBe("function");
	});
	
	
});