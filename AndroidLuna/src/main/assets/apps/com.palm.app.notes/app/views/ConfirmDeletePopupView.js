var ConfirmDeletePopupView = "ConfirmDeletePopupView";

enyo.kind({
  name: ConfirmDeletePopupView,
  kind: "Popup",
  scrim: true,
  modal: true,
  events: {
    onDelete: '',
    onCancel: ''
  },
  components: [
    {
      kind: enyo.Control,
      components: [
        {
          className: "large-text",
          content: $L("Are you sure you want to delete this memo?")
        },
        {
          className: "small-text",
          content: $L("You cannot undo this action.")
        }
      ]
    },
    {
      kind: enyo.HFlexBox,
      components: [
        {
          name: 'cancel',
          kind: "Button",
          flex: 1,
          caption: $L("Cancel"),
          onclick: "onCancelClick"
        },
        {
          kind: enyo.Control,
          className: 'button-spacer'
        },
        {
          name: 'confirm',
          kind: "Button",
          flex: 1,
          className: 'enyo-button-negative',
          content: $L("Delete"),
          onclick: "onDeleteClick"
        }
      ]
    }
  ],

  go: function(memo) {
    this.agent = new ConfirmDeletePopupAgent(this, memo);
  },

  onDeleteClick: function() {
    this.agent.deleteMemo();
  },

  onCancelClick: function() {
    this.doCancel();
  },

  // View Interface
  memoDeleted: function(memoId) {
    this.doDelete(memoId);
  }

});
