/*============================================
=            Parent tracker class            =
============================================*/

var Analytics = function () {

    //Invoke singleton
    if ( arguments.callee._singletonInstance ) {
        return arguments.callee._singletonInstance;
    } else {
        arguments.callee._singletonInstance = this;
    }

    this.send = function () {

        var deffered = $.Deferred();

        try {

            this.sendToBackground({
                method: this.method,
                data: this.data
            }, function () {
                deffered.resolve();
            });

        } catch (err) {
            deffered.reject();
            console.error("Tracking error", err);
        }

        return deffered.promise();
    };

    this.sendToBackground = function (data, callback) {

        //Check if Tracker object exists - it means Analytics() vas initialized in background
        if (DRAGDIS.tracker) {
            DRAGDIS.tracker.add(data);
            return;
        }

        DRAGDIS.sendMessage({ Type: "Tracker", Value: data }, callback);
    };
};



/*=============================================
=            Tracking type classes            =
=============================================*/

var TrackPageView = function (title, url) {
    
    this.method = "Pageview";
    this.data = {
        'page': url,
        'title': title
    };
};

var TrackEvent = function (category, action, label, value) {

    this.method = "Event";
    this.data = {
        'category': category,
        'action': action,
        'label': label,
        'value': value
    };
};

var TrackTiming = function (category, variable, label) {

    this.startTime = new Date().getTime();
    this.method = "Timing";
    this.data = {
        'Category': category,
        'Variable': variable,
        'Value': 0,
        'Label': label
    };

    this.Stop = function () {
        var endTime = new Date().getTime();
        this.data.Value = endTime - this.startTime;
    };
};

var TrackException = function (error) {

    this.method = "exception";
    this.data = {
        'error': error
    };

};

//Extend tracker classes
TrackPageView.prototype = new Analytics();
TrackEvent.prototype = new Analytics();
TrackTiming.prototype = new Analytics();
TrackException.prototype = new Analytics();
