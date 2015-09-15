/*=====================================================
=            Events particular for iframes            =
=====================================================*/

if (window.top != window.self) {

    $(document).on("dragstart", function(event, innerIframe) {

        event.stopPropagation();

        // If event is passed from inner iframe, set it as original event
        if (innerIframe) {
            event = innerIframe;
        }

        if (event.originalEvent) {
            window.parent.$(window.parent.document).trigger("dragstart", event);
        }

    })

    $(document).on("dragend", function(event, innerIframe) {

        event.stopPropagation();

        window.parent.$(window.parent.document).trigger("dragend", event);
    })

    $(document).on("dragenter", function(event, innerIframe) {

        event.stopPropagation();

        // If event is passed from inner iframe, set it as original event
        if (innerIframe) {
            event = innerIframe;
        }

        if (event.originalEvent) {
            window.parent.$(window.parent.document).trigger("dragenter", event);
        }
    })

    $(document).on("dragleave", function(event, innerIframe) {

        event.stopPropagation();

        window.parent.$(window.parent.document).trigger("dragleave", event);
    })
}