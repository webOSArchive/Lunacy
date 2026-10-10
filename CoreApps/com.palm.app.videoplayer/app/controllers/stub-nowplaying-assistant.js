/*globals AppAssistant Mojo*/

var NowplayingAssistant = function(params){
	this.params = params;
};

NowplayingAssistant.prototype = {
	setup : function() {
		this.controller.stageController.swapScene({
			name: AppAssistant.VideoLibrary.Nowplaying.Name,
			assistantConstructor:AppAssistant.VideoLibrary.Nowplaying.Assistant,
			sceneTemplate: AppAssistant.VideoLibrary.Nowplaying.Template,
			transition: "none"
			}, this.params);
	}
};

