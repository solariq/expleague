/* ==========================================================================
Knugget indicator icon for images
========================================================================== */

$(function() {

    var isFacebook = window.location.host.indexOf("facebook") > -1;
    var isIconHovered;
    var mouseOutTarget;
    var mouseInTarget;
    var icon;
    var image;

    /*=========================================
    =            Create image icon            =
    =========================================*/
    $.get(chrome.extension.getURL('views/image_icon.html')).then(function (iconTemplate) {

        icon = $(iconTemplate);
        $('body').append(icon);

        $(icon)
            .on('click', function (event) {
                
                event.stopPropagation();

                if (!KNUGGET.sidebarController) {
                    KNUGGET.sidebarController = {};
                }

                if (KNUGGET.sidebarController.active) {
                    DRAGDIS_SIDEBAR.openedByIcon = 0;
                    KNUGGET.sidebarController.hide(true, true); //closeFast , isCloseManually

                    $(icon).removeClass('silence');

                } else {

                    //Set flag for manual initialization (required for user stats)
                    if (!KNUGGET.sidebarController.folders) {
                        KNUGGET.config.isInitializedManually = true;
                    }

                    DRAGDIS_SIDEBAR.openedByIcon = 1;
                    DRAGDIS_SIDEBAR.show({ isOpenedManually: true });

                    $(icon).addClass('silence');
                }
            })
            .on('mouseover', function (event) {

                if (KNUGGET.sidebarController) {
                    if (KNUGGET.sidebarController.active) {
                        $(icon).addClass('silence');
                    }
                }

                isIconHovered = true;
            })
            .on('mouseout', function (event) {
                isIconHovered = false;
                $(icon).removeClass('silence');
            })
    });


    /*====================================
    =            Image finder            =
    ====================================*/
    
    function getImage(event) {

        var image;

        if (event.target && !isFacebook) {

            image = (event.target.nodeType === 3) ? event.target.parentNode : event.target;

        } else if (event.target && isFacebook) {

            if (event.target.className.match( /(__c_|_2t9n|_4-eo)/)) {
                image = event.target.getElementsByTagName("img")[0];
            } else {
                return false;
            }

        } else {
            image = event.srcElement;
        }

        return image;
    }
    
    
    

    /*=========================================
    =            Bind hover events            =
    =========================================*/
    $('body')
        .on('mouseover', function (event) {

            image = getImage(event);

            //Skip small images
            if (image.clientHeight < 150 && image.clientWidth < 150) {
                return;
            };

            //Skip yahoo and bing search hovers
            if ($(image).parents('#ihover-node-cont, .irhc, .modalContainer').length) {
                return;
            } 


            if (image.tagName === 'IMG' && image.src) {

                mouseInTarget = image;
                
                var offset = $(image).offset();
                var width = image.clientWidth;

                offset.top += parseInt($(image).css('padding-top'));

                //Position icon to image container instead of image itself
                if (image.parentNode.offsetHeight >= 100) {
                    if (image.width > image.parentNode.offsetWidth || image.height > image.parentNode.offsetHeight) {
                        width = image.parentNode.offsetWidth;
                        offset = $(image).parent().offset();
                    } 
                }

                if (offset.left > document.documentElement.clientWidth / 2) {
                    $(icon).addClass('right');
                } else {
                    $(icon).removeClass('right');
                }

                $(icon).css({
                    left: offset.left + width - 30,
                    top: offset.top + 5,
                    display: 'block'
                });

            }
        })
        .on('mouseout', function (event) {

            image = getImage(event);  

            if (image.tagName === 'IMG' && image.src) {

                mouseOutTarget = image;
                
                setTimeout(function() {

                    if (!isIconHovered && mouseOutTarget == mouseInTarget) {

                        $(icon).css({
                            display: 'none'
                        });

                    }
                    
                });

            }
        })

});