//Nearest plugin
$.fn.nearest = function (findChild, closestParent) {
    if (typeof closestParent == 'undefined') {
        if ($(document).find(findChild).length === 0) {
            return $([]);
        }
        var current = $(this);
        var child;
        while (current.length !== 0) {
            child = current.find(findChild);
            if (child.length) {
                return child;
            }
            if (current.parent() == current) {
                break;
            }
            current = current.parent();
        }
        return $([]);
    }
    var closest = $(this).closest(closestParent);
    if (closest.filter(findChild).length) {
        return closest.filter(findChild);
    }
    return closest.find(findChild);
};