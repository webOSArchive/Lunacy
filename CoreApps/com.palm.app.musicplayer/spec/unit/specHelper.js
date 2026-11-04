/*globals console, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, document */

function addCss(cssCode) {
	var styleElement = document.createElement("style");
  styleElement.type = "text/css";
  styleElement.appendChild(document.createTextNode(cssCode));
  document.getElementsByTagName("head")[0].appendChild(styleElement);
}

addCss("body { overflow: auto !important; }");

function getJson(filename) {
	return enyo.g11n.Utils.getJsonFile({path: 'spec/unit/source/mock', locale: filename});
}


function specOut(creator, objName, before, params, mTests){
	
	var listViewDetail = creator();
	 var event = {rowIndex: 0, dispatchTarget: null};
	
	var current;
	 var calls = [];
	var callbacks = function(name, fun){
	     return function(params){
	         if(current === name){
	        	 if(name.substr(0, 6)==="setStr"){
	        		return;        		 
	        	 }	        	 
	        	 if(name.substr(0, 2)==="on"){
	        		fun(null, event);
	        	}else{
	        		fun.apply(listViewDetail, params);
	        	}
	         }else{
	            calls.push({name:name, "pArguments": arguments});
	            return; 
	         } 
	     };
	 };
	
		
	before(listViewDetail, callbacks);
	
	var obj = listViewDetail;
		
		
		
		 
		for(var funi in obj){
		    if(typeof(obj[funi]) === "function"){
		    	obj[funi] = callbacks(funi, enyo.bind(obj, obj[funi]));
		    }
		}

		var wholeMsg = "describe('"+objName+" tests', function(){\n";
		wholeMsg += "\tvar creator = "+creator+";\n"+
					"\tvar "+objName+";\n";
		
		wholeMsg += "\tbeforeEach(function(){\n"+
		"\t\t"+objName+" = creator();\n"+
		"\t\tvar before = "+before+";\n"+
		"\t\tvar callback = function(one, fun){return fun;};\n"+
		"\t\tbefore("+objName+",callback);\n"+
		"\t});\n"	;
		
		if(typeof(mTests) !== "undefined"){
			wholeMsg += "\n\tdescribe('manual tests',"+mtests;
			wholeMsg += "\n\t);\n";	
		}
		
		var msg = "";
		try{
			wholeMsg += "\tit('should correctly set up object', function(){\n";
			
			for(var ni in obj){
				if(typeof(obj[ni]) !== "function"){
					try{
						var str = enyo.json.stringify(obj[ni]);
						//if(str.length < 20){
							wholeMsg += "\t\texpect("+objName+"."+ni+").toEqual("+str+");\n";
						//}
					}catch(err){
						wholeMsg += "\t\texpect(typeof "+objName+"."+ni+").toBe('"+typeof(obj[ni])+"');\n";	
					}
				}
			}
			wholeMsg += "\t});\n";
			
			for(var i in obj){
			    if(typeof(obj[i]) === "function"){

					 msg = "";
					
			    	msg += "\tdescribe('test ("+i+")', function(){\n";
			        var isParams = true;
			    	if(typeof(params[i]) === "undefined"){
			        	params[i] = [[]];
			        	isParams = false;
			        }
			        for(var pi in params[i]){
			        	if(typeof params[i][pi] !== "undefined"){
				        	if(isParams){
				        		msg += "\tdescribe('testing ("+i+") with params "+enyo.json.stringify(params[i][pi])+"', function(){\n";
				        	}
				        	
					    	calls = [];
					        current = i;
					        
					        //copy contents to check for changes
					        var mockObj = {};
					        for(var cindex in obj){
					        	if(typeof obj[cindex] !== "function"){
					        		mockObj[cindex] = obj[cindex];
					        	}
					        }
					        
					        
					        //call the function
					        enyo.bind(obj, obj[i])(params[i][pi]);    
					        
					        //checks for changes
					        for(var ci in obj){
					        	if(typeof obj[ci] !== "function"){
					        		if(mockObj[ci] !== obj[ci]){
					        			msg += "\t\tit('should change "+ci+"',function(){\n";
					        			
					        								            
							        	msg += "\t\t\t"+objName+"."+i+"(";
							        	
							        	if(i.substr(0,2)==="on"){
							        		msg += "null,"+enyo.json.stringify(event);
							        	}else{
							        		msg += (""+enyo.json.stringify(params[i][pi])).substr(1);
							        		msg = msg.slice(0,-1);
							        	}
							        	
							        	msg +=");\n";
							        	
							        	msg += "\t\t\texpect("+objName+"."+ci+").toEqual("+enyo.json.stringify(obj[ci])+");\n";
							        	
							        	
							        	
					        			msg += "\t\t});\n";
					        		}
					        	}
					        }
					        
					        
					        //Check out the calls it made
					        for(var k in calls){
					        	if(typeof calls[k] !== "undefined"){
						        	if(calls[k].name === "log"){
						        		continue;
						        	}
						        	msg += "\t\tit('should call "+calls[k].name+"',function(){\n";
						        	
						        	
						        	var toSpyOn = objName;
						        	var toSpyOnFun = calls[k].name;
						        	if(toSpyOnFun.lastIndexOf(".")>=0){
						        		toSpyOn += "." + toSpyOnFun.substr(0, toSpyOnFun.lastIndexOf("."));
						        		toSpyOnFun = toSpyOnFun.substr(toSpyOnFun.lastIndexOf(".")+1);
						        	}
						        	
						        	msg += "\t\t\tspyOn("+toSpyOn+",'"+toSpyOnFun+"');\n";
						            
						        	msg += "\t\t\t"+objName+"."+i+"(";
						        	
						        	if(i.substr(0,2)==="on"){
						        		msg += "null,"+enyo.json.stringify(event);
						        	}else{
						        		msg += (""+enyo.json.stringify(params[i][pi])).substr(1);
						        		msg = msg.slice(0,-1);
						        	}
						        	
						        	
						        	msg +=");\n";
			
						        	msg += "\t\t\texpect("+objName+"."+calls[k].name+").toHaveBeenCalled";
						        	
						        	if(calls[k].pArguments.length>0){
						        		msg += "With(";
							        	for(var j in calls[k].pArguments){
							        		if(typeof j !== "undefined"){
							        			try{
							        			msg+= enyo.json.stringify(calls[k].pArguments[j])+",";
							        			}catch(e){
							        				msg+= "jasmine.any(),";	
							        			}
							        		}
							        	}
							        	msg = msg.slice(0, -1);				        	
						        	}else{
						        		msg += "(";
								    }
						        	msg +=");\n";
						        	
						        	msg += "\t\t});\n";
					        	}
					        }
		
					      
				        	msg += "\t});\n";
			        	}
			        	if(isParams){
					     	msg += "\t});\n";
					    }
			        }
			        wholeMsg += msg;
			    }
			}
			wholeMsg += "\t});\n";
			console.log(wholeMsg);
		}catch(e){
			wholeMsg += "});\n";
			console.log(wholeMsg);
			console.log(msg, "caused below. (Need to spec out internals)");
			console.log(e);
		}
		
		
	
}