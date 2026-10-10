var MemoView = "MemoView";

enyo.kind({
	name: MemoView,
  kind: enyo.HFlexBox,
	className: "memo-preview",
  events: {
    onMemoClick: '',
    onDeleteMemoClick: ''
  },
	published: {
		memo: null
	},
	components: [
    {
      // TODO: Make this delete button a separate kind so we can use clickHandler 
      kind: "CustomButton",
      className: "delete-button",
      caption: ' ',
      onclick: 'deleteMemoClick'
    },
		{
      name: "text",
      className: "memo-preview-content"
    }
	],

	memoChanged: function() {
		this.$.text.content = this.memo.displayText;
		this.setClassName(this.className + " " + this.memo.color);
	},

  clickHandler: function(sender) {
    // sender.node.parentElement.getBoundingClientRect() - find the parentElement that is '.memo-preview'
    this.doMemoClick(this.memo);
  },

  deleteMemoClick: function() {
    this.doDeleteMemoClick(this.memo);
  }
});
