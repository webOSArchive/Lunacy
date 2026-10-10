var logApiFailures = true;

var AppView = "AppView";

enyo.kind({
  name: AppView,
  kind: enyo.Control,
  className: "enyo-fit",
  components: [
    {
      kind: "ApplicationEvents",
      onApplicationRelaunch: "applicationRelaunchHandler"
    },
    {
      name: "grid",
      kind: GridView,
      className: 'enyo-fit',
      onEditMemo: "editMemo",
      onEditMemos: "onEditMemos",
      showing: true
    },
    {
      name: "edit",
      kind: EditView,
      className: 'edit-view enyo-fit',
      onAllMemos: "onAllMemos",
      showing: false
    },
    {
      name: "appMenu",
      kind: "AppMenu",
      components: [
        {
          kind: "HelpMenu",
          target: "http://help.palm.com/notes/index.html"
        }
      ]
    }
  ],

  create: function() {
    this.inherited(arguments);
    enyo.setAllowedOrientation('free');
  },

  go: function() {
    this.goToGrid(enyo.windowParams);
  },

  applicationRelaunchHandler: function() {
    this.go();
  },

  resizeHandler: function() {
    this.$.grid.resizeHandler();
    if (this.$.edit.showing) {
      this.$.edit.resizeHandler();
    }
  },


  // TODO: animations  editMemo: function(sender, memoView, memo, backButtonLabelText) {
  editMemo: function(sender, memo, backButtonLabelText) {
    this.$.edit.setBackButtonLabel(backButtonLabelText);
    this.$.edit.setMemo(memo);
    this.$.edit.show();
    this.$.edit.viewSelected();
  },

  onAllMemos: function() {
    this.$.edit.hide();
    this.$.grid.viewSelected();
  },

  goToGrid: function(params) {
    this.$.grid.viewSelected(params);
  },

  // TODO: perhaps these can go away now due to the ApplicationEvents control
  openAppMenuHandler: function() {
    this.$.appMenu.open();
  },

  closeAppMenuHandler: function() {
    this.$.appMenu.close();
  }
});
