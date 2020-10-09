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

function input(name, value) {
  var el = document.createElement("input");
  el.value = value;
  el.name = name;
  el.type = "hidden";
  return el;
}

function login(user) {
  var data = users[user];
  var urltoken = data[0];
  var username = data[1];
  var password = data[2];

  var form = document.createElement("form");
  form.method = "POST";
  form.action = "https://auth.dvbib.se/";
  form.appendChild(input("Username", username));
  form.appendChild(input("Password", password));
  form.appendChild(input("ReturnURL", "https://bib.nacka.se:443/"));
  form.appendChild(input("RememberLogin", "true"));
  form.appendChild(input("UrlToken", urltoken));
  document.body.appendChild(form);
  form.submit();
}
