/* globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */
describe('Utilites Tests', function(){
	describe("check (formatTime)", function(){
		
		it("should set time_formatter if null", function(){
			Utilities.time_formatter = null;
			var fake = new enyo.g11n.DurationFmt();
			spyOn(enyo.g11n, "DurationFmt").andReturn(fake);
			Utilities.formatTime(0);
			expect(Utilities.time_formatter).not.toBe(null);
			expect(enyo.g11n.DurationFmt).toHaveBeenCalled();
			
		});
		
		describe("check the math", function(){
			beforeEach(function(){
				spyOn(Utilities.time_formatter, "format");
			});
			it("should format correct times 0", function(){
				var seconds = 0;
				Utilities.formatTime(seconds);
				expect(Utilities.time_formatter.format).toHaveBeenCalledWith({hours: 0, minutes:0, seconds: seconds});
				
			});
			
			/* shoudn't work with negative
			it("should format correct times -1", function(){
				var seconds = -1;
				Utilities.formatTime(seconds);
				expect(Utilities.time_formatter.format).toHaveBeenCalledWith({hours: 0, minutes:0, seconds: seconds});
				
			});*/
			
			it("should format correct times 59", function(){
				var seconds = 59;
				Utilities.formatTime(seconds);
				expect(Utilities.time_formatter.format).toHaveBeenCalledWith({hours: 0, minutes:0, seconds: seconds});
				
			});
			
			it("should format correct times 60", function(){
				var seconds = 60;
				Utilities.formatTime(seconds);
				expect(Utilities.time_formatter.format).toHaveBeenCalledWith({hours: 0, minutes:1, seconds: 0});
				
			});
			
			it("should format correct times 121", function(){
				var seconds = 121;
				Utilities.formatTime(seconds);
				expect(Utilities.time_formatter.format).toHaveBeenCalledWith({hours: 0, minutes:2, seconds: 1});
				
			});
			
			it("should format correct times 3599", function(){
				var seconds = 3599;
				Utilities.formatTime(seconds);
				expect(Utilities.time_formatter.format).toHaveBeenCalledWith({hours: 0, minutes:59, seconds: 59});
				
			});
			
			it("should format correct times 3600", function(){
				var seconds = 3600;
				Utilities.formatTime(seconds);
				expect(Utilities.time_formatter.format).toHaveBeenCalledWith({hours: 1, minutes:0, seconds: 0});
				
			});
			
			it("should format correct times 3600*24", function(){
				var seconds = 3600*24;
				Utilities.formatTime(seconds);
				expect(Utilities.time_formatter.format).toHaveBeenCalledWith({hours: 24, minutes:0, seconds: 0});
				
			});
		});
		
	});
	
	describe("check (isNumeric)", function(){
		it("should work test 1", function(){
			expect(Utilities.isNumeric(1)).toBe(true);
		});
		it("should work test 2", function(){
			expect(Utilities.isNumeric(-1)).toBe(true);
		});
		it("should work test 3", function(){
			expect(Utilities.isNumeric(0)).toBe(true);
		});
		it("should work test 4", function(){
			expect(Utilities.isNumeric(0.003)).toBe(true);
		});
		it("should work test 5", function(){
			expect(Utilities.isNumeric(0.000000000000000000000003)).toBe(true);
		});
		it("should work test 6", function(){
			expect(Utilities.isNumeric(9999999999999999999999)).toBe(true);
		});

		it("should work test false 7", function(){
			expect(Utilities.isNumeric("1")).toBe(false);
		});

		it("should work test false 8", function(){
			expect(Utilities.isNumeric(function(){return 1;})).toBe(false);
		});
		it("should work test false 9", function(){
			expect(Utilities.isNumeric({number:1})).toBe(false);
		});
		it("should work test false 10", function(){
			expect(Utilities.isNumeric([])).toBe(false);
		});
		it("should work test false 11", function(){
			expect(Utilities.isNumeric('a')).toBe(false);
		});
		it("should work test false 12", function(){
			expect(Utilities.isNumeric(null)).toBe(false);
		});
		it("should work test false 13", function(){
			expect(Utilities.isNumeric(undefined)).toBe(false);
		});
	
	});
	
	describe("check (s4)", function(){
		it("is using random", function(){
			spyOn(Math,"random");
			var ret = Utilities.S4();
			expect(Math.random).toHaveBeenCalled();
		});
		it("is returning a hex string", function(){
				var ret = Utilities.S4();
				expect(typeof ret).toBe("string");
		});
	});
	
	describe("check (generateGuid)", function(){
		it("is using S4", function(){
			spyOn(Utilities, "S4");
			Utilities.generateGuid();
			expect(Utilities.S4).toHaveBeenCalled();
		});
		it("it is all uppercase", function(){
			var ret = Utilities.generateGuid();
			expect(ret).toBe(ret.toUpperCase());
		});
		
		it("is has correct number of dashes", function(){
			var ret = Utilities.generateGuid();
			expect(ret.split('-').length).toBe(5);
		});
		it("is the correct length", function(){
			var ret = Utilities.generateGuid();
			expect(ret.length).toBe(52);
		});
	});
	
	describe("check (getTrackImage)", function(){
		it("should choose default of 90", function(){
			var ret = Utilities.getTrackImage("/path/to/file/filename.mp3:100:100");
			expect(ret).toBe("/var/luna/data/extractfs/path/to/file/filename.mp3:100:100:90:90:3");
		});
		it("should work with passed in value", function(){
			var ret = Utilities.getTrackImage("/filename.mp3:100:100", 50);
			expect(ret).toBe("/var/luna/data/extractfs/filename.mp3:100:100:50:50:3");
		});
	});
	
	
	describe("check (fastTrim)", function(){
		it("should only do it if it's a string", function(){
			var param = 1;
			var ret = Utilities.fastTrim(param);
			expect(ret).toBe(param);
		});
		it("should only do it if it's a string 2", function(){
			var param = function(){};
			var ret = Utilities.fastTrim(param);
			expect(ret).toBe(param);
		});
		it("should only do it if it's a string 3", function(){
			var param = {};
			var ret = Utilities.fastTrim(param);
			expect(ret).toBe(param);
		});
		it("should only do it if it's a string 4", function(){
			var param = [];
			var ret = Utilities.fastTrim(param);
			expect(ret).toBe(param);
		});
		
		it("should correctly trim left", function(){
			var param = "   hi";
			var ret = Utilities.fastTrim(param);
			expect(ret).toBe("hi");
		});
		it("should correctly trim right", function(){
			var param = "hi   ";
			var ret = Utilities.fastTrim(param);
			expect(ret).toBe("hi");
		});		
		it("should correctly trim both", function(){
			var param = "    hi   ";
			var ret = Utilities.fastTrim(param);
			expect(ret).toBe("hi");
		});
	});
	
	describe("check (getItemThumb)", function(){
		it("catches 0 thumbs case", function(){
			var ret = Utilities.getItemThumb([], 0);
			expect(ret.strThumbURL).toBe("");
			expect(ret.intThumbsTotalCount).toBe(0);
		});
		it("correctly counds number of thumbs 1", function(){
			var thumb = {data: ""};
			var ret = Utilities.getItemThumb([thumb], 0);
			expect(ret.intThumbsTotalCount).toBe(1);
		});
		it("correctly counds number of thumbs 6", function(){
			var thumb = {data: ""};
			var ret = Utilities.getItemThumb([thumb, thumb, thumb, thumb, thumb, thumb], 0);
			expect(ret.intThumbsTotalCount).toBe(6);
		});
		
		it("grabs first thumbnail", function(){
			var thumb = null;
			var special = {data: "spec"};
			spyOn(Utilities, "getTrackImage");
			var ret = Utilities.getItemThumb([thumb, thumb, special, thumb, thumb, thumb], 0);
			expect(Utilities.getTrackImage).toHaveBeenCalledWith(special.data, 0);
		});
		
		it("grabs first thumbnail 2", function(){
			var thumb = null;
			var special = {data: "spec"};
			var special2 = {data: "spec2"};
			spyOn(Utilities, "getTrackImage");
			var ret = Utilities.getItemThumb([thumb, thumb, special, special2, thumb, thumb], 0);
			expect(Utilities.getTrackImage).toHaveBeenCalledWith(special.data, 0);
		});
		

		it("uses default size", function(){
			var thumb = null;
			var special = {data: "spec"};
			spyOn(Utilities, "getTrackImage");
			var ret = Utilities.getItemThumb([thumb, thumb, special, thumb, thumb, thumb]);
			expect(Utilities.getTrackImage).toHaveBeenCalledWith(special.data, 38);
		});
		
		it("uses given size", function(){
			var thumb = null;
			var special = {data: "spec"};
			spyOn(Utilities, "getTrackImage");
			var ret = Utilities.getItemThumb([thumb, thumb, special, thumb, thumb, thumb], 50);
			expect(Utilities.getTrackImage).toHaveBeenCalledWith(special.data, 50);
		});
	});
	
	describe("check (getBaseLabelChar)", function(){
		
		it("should not fail on empty string", function(){
			var ret = Utilities.getBaseLabelChar('');
			expect(ret).toBe('#');
		});
		
		it("should call appropriate g11n calls", function(){
			spyOn(enyo.g11n.Char, "isLetter").andCallThrough();
			spyOn(enyo.g11n.Char, "toUpper").andCallThrough();
			spyOn(enyo.g11n.Char, "getBaseString").andCallThrough();
			Utilities.getBaseLabelChar("hello");
			expect(enyo.g11n.Char.isLetter).toHaveBeenCalledWith('h');
			expect(enyo.g11n.Char.toUpper).toHaveBeenCalledWith('h');
			expect(enyo.g11n.Char.toUpper).toHaveBeenCalledWith('h');
		});
		it("should call appropriate g11n calls on odd word", function(){
			spyOn(enyo.g11n.Char, "isLetter").andCallThrough();
			spyOn(enyo.g11n.Char, "toUpper").andCallThrough();
			spyOn(enyo.g11n.Char, "getBaseString").andCallThrough();
			Utilities.getBaseLabelChar("123hello");
			expect(enyo.g11n.Char.isLetter).toHaveBeenCalledWith('1');
			expect(enyo.g11n.Char.toUpper).not.toHaveBeenCalled();
			expect(enyo.g11n.Char.toUpper).not.toHaveBeenCalled();
		});
		
		it("should return properly", function(){
			var ret  = Utilities.getBaseLabelChar("hello");
			expect(ret).toBe('H');
		});

		it("should return properly on odd word", function(){
			var ret  = Utilities.getBaseLabelChar("123hello");
			expect(ret).toBe('#');
		});
	});
	
	describe("check (getPlaylistSongData)", function(){
		
		it("ret should only have 3 params", function(){
			var ret = Utilities.getPlaylistSongData({});
			var i = 0;
			for(var j in ret){
				if(typeof j !== 'undefined'){
					i++;
				}
			}
			expect(i).toBe(3);
		});
		
		it("should not fail on undefined", function(){
			Utilities.getPlaylistSongData();
		});
		
		it("should properly grab attributes", function(){
			var title = "asdf";
			var artist = "gsdfg";
			var path = "/asdf/badf/asdf";
			var song = {title:title, artist:artist, path:path};
			var ret = Utilities.getPlaylistSongData(song);
			expect(ret.title).toBe(title);
			expect(ret.artist).toBe(artist);
			expect(ret.path).toBe(path);
		});
		
	});
	
	describe("check (capitalize)", function(){
		it("shouldn't fail on empty string", function(){
			var ret = Utilities.capitalize('');
			expect(ret).toBe('');
		});
		it("shouldn't fail on one char", function(){
			var ret = 	Utilities.capitalize('a');
			expect(ret).toBe('A');
			ret = 	Utilities.capitalize('A');
			expect(ret).toBe('A');
		});
		it("shouldn't fail on weird cahr", function(){
			var ret = 	Utilities.capitalize('#a');
			expect(ret).toBe('#a');
			ret = 	Utilities.capitalize('#a');
			expect(ret).toBe('#a');
		});
		it("should work", function(){
			var ret = 	Utilities.capitalize('hello');
			expect(ret).toBe('Hello');
		});
	});
	
});