function showHide(id,id_a) {
    var element = document.getElementById(id);
    var el = document.getElementById(id_a);

    if (element.className == "cut" || element.style.display == "none") {
        element.style.display = null;
        element.className = "cut_open";
        el.className = "cut_open";
    }
    else {
        element.className = "cut";
        el.className = "cut";
    }
}