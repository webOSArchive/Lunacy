var NewMemoView = "NewMemoView";

enyo.kind({
  name: NewMemoView,
  kind: "Control",
  className: "new-memo",
  events: {
    onNewMemoClick: ''
  },
  components: [
    {
      kind: "Control",
      className: "swatch"
    }
  ],

  create: function() {
    this.inherited(arguments);
  },

  clickHandler: function() {
    this.doNewMemoClick();
  }
});
