var util = {};

util.wrap = function(innerFunc, outerFunc) {
  return function() {
    var args = [innerFunc];
    args.push.apply(args, arguments);
    outerFunc.apply(null, args);
  };
};

util.swapMemoColorClassName = function(node, newColor) {
  util.removeClassNames(node, Memo.colors);
  node.addClass(newColor);
};

util.removeClassNames = function(node, classNames) {
  var currentClasses = node.getClassName().split(' ');
  var classesToRemove;
  if (classNames.length && typeof classNames != 'string') {
    classesToRemove = classNames;
  } else {
    classesToRemove = classNames.split(' ');
  }

  classesToRemove.forEach(function(name) {
    var index = currentClasses.indexOf(name);
    if (index >= 0) {
      currentClasses.splice(index, 1);
    }
  });

  node.setClassName(currentClasses.join(' '));
};

util.highlightString = function highlightString(filterText, unformattedText) {
	var highlightSpan = '<span class="string-highlight">ZZZZ</span>';
	var patternStr = "\\b(" + filterText + ")";
	var beginPattern = new RegExp(patternStr, 'ig');

	var formatText = unformattedText.replace(beginPattern, function(whole, match) {
		return highlightSpan.replace('ZZZZ', match);
	});
	return formatText;
};

util.extend = enyo.mixin;
