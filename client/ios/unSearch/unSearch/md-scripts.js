function showHide(id,id_a) {
    var element = document.getElementById(id);
    var el = document.getElementById(id_a);
    
    if (element.style.display == 'none') {
        element.style.display = 'block'
        el.className = "cut_open"
    }
    else {
        element.style.display = 'none'
        el.className = "cut"
    }
    
}