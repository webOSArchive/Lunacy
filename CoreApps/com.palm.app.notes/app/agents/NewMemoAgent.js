function NewMemoAgent(view) {
  var self = this;

  self.generateNextMemoColor = function(lastColor) {
    var nextColor = Memo.colors[(Memo.colors.indexOf(lastColor) + 1) % Memo.colors.length];
    view.setCurrentColor(nextColor);
  };

  return self;
}