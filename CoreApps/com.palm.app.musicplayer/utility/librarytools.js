/*globals enyo, $L, event, Utilities, window, PalmSystem, MediaIndex */
var LibraryTools = {
	
	sortbyArtist: function (arSongList, boolAsc)
	{
		var sortFunc = function (boolAsc)
		{
			return function (a, b)
				{
					
					if (enyo.g11n.Char.toUpper(a.artist) === enyo.g11n.Char.toUpper(b.artist))
					{
						if (enyo.g11n.Char.toUpper(a.album) === enyo.g11n.Char.toUpper(b.album))
						{
							return a.track.position < b.track.position ? -1 : 1;
						}
						else
						{
							return enyo.g11n.Char.toUpper(a.album) < enyo.g11n.Char.toUpper(b.album) ? -1 : 1;
							
						}
						
					}
					else
					{
						if (boolAsc)
						{
							return enyo.g11n.Char.toUpper(a.artist) < enyo.g11n.Char.toUpper(b.artist) ? -1 : 1;
						}
						else
						{
							return enyo.g11n.Char.toUpper(a.artist) < enyo.g11n.Char.toUpper(b.artist) ? 1 : -1;
						}
					}
					
					
				};

		};
		
		if(arSongList && typeof(arSongList.sort) === "function"){
			arSongList.sort(sortFunc(boolAsc));
		}
		return arSongList;

		
	},
	
	
	sortbySong: function (arSongList, boolAsc)
	{
		
		if(arSongList && typeof(arSongList.sort) === "function"){
			arSongList.sort(this.sortFuncSong);
			
			if (!boolAsc)
			{
				arSongList.reverse();
			}
		}
		return arSongList;
		
		
	},
	
	sortFuncSong: function (a, b)
	{
		
		if (enyo.g11n.Char.toUpper(a.title) === enyo.g11n.Char.toUpper(b.title))
		{
			return 0;
		}
		return enyo.g11n.Char.toUpper(a.title) < enyo.g11n.Char.toUpper(b.title) ? -1 : 1;
		
		
	},
	
	sortbyPos: function (arSongList, boolAsc)
	{
	
		var sortFunc = function (boolAsc)
						{
							return function (a, b)
								{
									
									
									if (a.track.position === b.track.position)
									{
										return 0;
										
									}
									else
									{
										if (boolAsc)
										{
											return a.track.position < b.track.position ? -1 : 1;
										}
										else
										{
											return a.track.position < b.track.position ? 1 : -1;
										}
										
										
									}
					
									
									
								};
		
						};
		if(arSongList && typeof(arSongList.sort) === "function"){
			arSongList.sort(sortFunc(boolAsc));
		}
		return arSongList;
		
		
	},
	
	sortbyAlbum: function (arSongList, boolAsc)
	{
	
		var sortFunc = function (boolAsc)
						{
							return function (a, b)
								{
									
									
									if (enyo.g11n.Char.toUpper(a.album) === enyo.g11n.Char.toUpper(b.album))
									{
										return a.track.position < b.track.position ? -1 : 1;
									}
									else
									{
										if (boolAsc)
										{
											return enyo.g11n.Char.toUpper(a.album) < enyo.g11n.Char.toUpper(b.album) ? -1 : 1;
										}
										else
										{
											return enyo.g11n.Char.toUpper(a.album) < enyo.g11n.Char.toUpper(b.album) ? 1 : -1;
										}
										
										
									}
					
									
									
								};
		
						};
		if(arSongList && typeof(arSongList.sort) === "function"){
			arSongList.sort(sortFunc(boolAsc));
		}
		return arSongList;
		
		
	},
	
	
	sortRandom: function(arSongList)
	{
		if(arSongList && typeof(arSongList.sort) === "function"){
			arSongList.sort(function (){return (Math.round(Math.random())-0.5);});
		}
	},
	
	sortOrigOrder: function (arSongList)
	{
		
		var sortFunc = function ()
		{
			return function (a, b)
			{
				if (a.origIndex === b.origIndex)
				{
					return 0;
				}
				return a.origIndex < b.origIndex ? -1 : 1;				
			};
		};
		if(arSongList && typeof(arSongList.sort) === "function"){
			arSongList.sort(sortFunc());
		}
		return arSongList;
		
	},
	
	
	sort: function(type, arSongList, boolAsc){
		switch(type)
		{
			case "title":
				this.sortbySong(arSongList, boolAsc);
				break;
			case "pos":
				this.sortbyPos(arSongList, boolAsc);
				break;
			case "album":
				this.sortbyAlbum(arSongList, boolAsc);
				break;
		}
	}
	
	
	
	
	
	
};



Error.prototype._toString = Error.prototype.toString;
Error.prototype.toString = function() {
	return (this.stack || this._toString()) + "-----";
};













