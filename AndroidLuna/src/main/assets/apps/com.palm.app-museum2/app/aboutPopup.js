enyo.kind({
    name: "appMuseum.AboutPopup",
    className: "enyo-popup",
    kind: "Toaster",
    modal: "true",
    flyInFrom: "bottom",
    width: "100%",
    height: "100%",
    layoutKind: "VFlexLayout",
    published: {
        
    },
    events: {
        
    },
    components: [
        {
            kind: "Scroller",
            autoHorizontal: false,
            horizontal: false,
            vertical: false,
            autoVertical: true,
            width: "100%",
            flex: 1,
            components: [
                {kind: "RowGroup", components: [
                    {layoutKind: "HFlexLayout", components: [
                        {kind: "Spacer"},
                        {kind: "Image", src: "./images/WebOSMuseumLogo.png", width: "128px", height: "128px"},
                        {kind: "Spacer"}
                    ]},
                    {content: "<span class='palmOrange'>web</span><span class='palmAccent'>OS App Museum II</span> is part of <span class='palmAccent'><a href=\"http://www.webosarchive.org\">webOS Archive</span></a>; a zero-profit (non-commercial) community-driven initiative to archive, and where possible provide access to, legacy <span class='palmOrange'> web</span><span class='palmAccent'>OS</span> functionality and information.<p>One of its main goals is to provide <span class='palmOrange'>web</span><span class='palmAccent'>OS</span>-users with functionality of the device as originally intended (when <span class='palmOrange'>web</span><span class='palmAccent'>OS</span> was created/the device was sold)."},
                    {layoutKind: "HFlexLayout", components: [
                        {kind: "Spacer"},
                        {kind: "Image", src: "./images/WebOSAppMuseumLogo.png", width: "128px", height: "128px"},
                        {kind: "Spacer"}
                    ]},
                    { w: "fill", name: "versionNum", domStyles: {"text-align": "center", "font-weight": "bold"}, content: "Version" },
                    {content: "This app is an exhibition of applications originally available to the platform. <span class='palmOrange'>web</span><span class='palmAccent'>OS Archive</span> provides these apps under Fair Use provisions as digital archives for historical purposes through <span class='palmOrange'>web</span><span class='palmAccent'>OS App Museum II</span>. If you have questions or comments, or to submit a take down request for IP you own the rights for, please email: <span class='palmOrange'><a href='mailto:curator@webosarchive.org'>curator@webosarchive.org</a></span>."},
                ]},
                {kind: "Control"},
                {
                    height: "8px"
                }
            ]
        },
        {
            layoutKind: "HFlexLayout",
            components: [
                {kind: "Spacer", flex: 1},
                {kind: "Button", caption: "Close", onclick: "closePopup"},
                {kind: "Spacer", flex: 1}
            ]
        }
    ],
    create:function() {
        this.inherited(arguments);
        var isTouchpad = banneret.getGlobal('isTouchpad');
        if (!isTouchpad) {
            this.setFlyInFrom('left');
        }
    },
    rendered: function () {
        this.inherited(arguments);
        var isTouchpad = banneret.getGlobal('isTouchpad');
        this.$.versionNum.setContent("Version: " + enyo.fetchAppInfo().version);
    },
    closePopup: function () {
        this.close();
        banneret.setPref('firstUse', false);
    },
});