/* Copyright 2009-2011 Hewlett-Packard Development Company, L.P. All rights reserved. */
enyo.kind({
  name: "enyo.SearchInputWithCancel",
  kind: enyo.RoundedInput,
  style: "position: relative;",
  events: {
    onSearch: ""
  },
  components: [
    {
      kind: enyo.Control,
      onclick: "clearSearch",
      onmousedown: "cancelMouseDown",
      components: [
        {
          kind: "Control",
          style: "position:absolute;margin-top:-22px;margin-left:-25px;padding:10px;",
          components: [
            {
              name: "searchIcon",
              kind: "Image",
              src: "$palm-themes-Onyx/images/search-icon.png",
              style: "padding-top:4px;",
              onclick: "fireSearch"
            },
            {
              name: "cancelIcon",
              kind: "Image",
              src: "$palm-themes-Onyx/images/progress-button-cancel.png",
              showing: false
            }
          ]
        }
      ]
    }
  ],

  fireSearch: function() {
    this.doSearch(this.getValue());
  },

  clearSearch: function() {
    this.$.input.hasNode().focus();
    this.setValue('');
    this.fireSearch();
    this.toggleIcons(false);
  },

  inputHandler: function() {
    this.inherited(arguments);
    this.toggleIcons(Boolean(this.getValue().length > 0));
  },

  toggleIcons: function(value) {
    this.$.cancelIcon.setShowing(value);
    this.$.searchIcon.setShowing(!value);
  },

  cancelMouseDown: function(inSender, inEvent) {
    enyo.stopEvent(inEvent);
  }
});