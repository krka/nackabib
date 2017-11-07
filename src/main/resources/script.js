var hour=60*60*1000;
setInterval(update, hour);

function update() {
  var now = new Date();
  var elements = document.getElementsByClassName("date");
  for (var i = 0; i < elements.length; ++i) {
    var item = elements[i];
    var text = item.textContent.split(" ")[0];

    var itemDate = new Date(text);
    var timeDiff = itemDate.getTime() - now.getTime();
    var diffDays = Math.ceil(timeDiff / (1000 * 3600 * 24));
    
    var wantedClass;
    if (diffDays >= 10) {
      wantedClass = "color-long";
    } else if (diffDays >= 5) {
      wantedClass = "color-short";
    } else if (diffDays >= 1) {
      wantedClass = "color-urgent";
    } else {
      wantedClass = "color-critical";
    }
    item.className = "date " + wantedClass;
    if (diffDays >= 0 && diffDays <= 9) {
      item.textContent = text + " [" +diffDays + "]"
    } else {
      item.textContent = text + "    ";
    }
  }
}
update();
