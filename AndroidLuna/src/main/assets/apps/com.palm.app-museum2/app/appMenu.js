enyo.kind({
    name: "appMuseum.AppMenu",
    kind: "enyo.AppMenu",
    published: {
        
    },
    events: {
        onItemSelected: ''
    },
    components: [
        {caption: "Preferences", icon: "./images/menu/appMenu_preferences.png", onclick: "menuClicked"},
        //{caption: "Help", icon: "./images/appMenu_help.png", onclick: "menuClicked"},
        {caption: "About the App Museum", icon: "./images/menu/appMenu_about.png", onclick: "menuClicked"}
    ],
    create:function() {
        this.inherited(arguments);
    },

    menuClicked:function (inSender, inEvent) {
        this.doItemSelected(inSender.value);
    }
});