var Admin = {
    config: {
        PAGE_UPDATE_INTERVAL_MS: 10 * 60 * 1000
    },
    experts: null,

    load: function(url, success) {
        $("#content").empty().append('<div class="loader" style=";">Loading...</div>');
        $.ajax({
            type: "GET",
            dataType: "json",
            url: url,
            success: function(data) {
                Admin.experts = {};
                $.each(data["experts"], function(i, model) {
                    Admin.experts[model.jid.bare] = model;
                    console.log("Got expert: " + model.jid.bare);
                });
                success(data);
            },
            error: function(j, s, message) {
                $("#content").empty().text(message);
            }
        });
    },

    scheduledUpdates: [],
    schedule: function(id, callback, intervalMs) {
        callback();
        Admin.scheduledUpdates = [id];
        var fn = function() {
            if (Admin.scheduledUpdates.indexOf(id) != -1) {
                console.log("Scheduled update for " + id + " is in progress, updates: " + JSON.stringify(Admin.scheduledUpdates));
                callback();
                console.log("Scheduled update for " + id + " completed");
                setTimeout(fn, intervalMs);
            }
        };
        setTimeout(fn, intervalMs);
    },

    loadUpdatablePage: function(url, success) {
        Admin.schedule(
            url,
            function() {
                Admin.load(url, success);
            },
            Admin.config.PAGE_UPDATE_INTERVAL_MS
        );
    },

    loadOpenOrders: function() {
        Admin.loadUpdatablePage("/open", function(data) {
            Admin.bindOrders(data);
        });
    },

    loadClosedWithoutFeedbackOrders: function() {
        Admin.loadUpdatablePage("/closed/without/feedback", function(data) {
            Admin.bindOrders(data);
        });
    },

    loadClosedOrders: function() {
        Admin.loadUpdatablePage("/closed", function(data) {
            Admin.bindOrders(data);
        });
    },

    loadTopExperts: function() {
        Admin.load("/top/experts", function(data) {
            var experts = $("#templates").find(".experts").clone();
            $("#content").empty().append(experts);
            var model = ko.mapping.fromJS(data);
            ko.applyBindings(model, experts.get(0))
        });
    },

    loadRelatedHandler: function(jid) {
        return function() {
            Admin.loadRelated(jid.bare())
        }
    },

    loadDumpHandler: function(order) {
        return function() {
            Admin.load("/dump/" + order.offer.room.bare(), function(data) {
                Admin.bindDump(data);
            });
        }
    },

    loadRelated: function(jid) {
        Admin.loadUpdatablePage("/related/" + jid, function(data) {
            Admin.bindOrders(data);
        });
    },

    bindOrders: function(orders) {
        var ordersEl = $("#templates").find(".orders").clone();
        $("#content").empty().append(ordersEl);
        var model = ko.mapping.fromJS(orders);
        _.each(model.orderGroups(), function(orderGroup) {
            _.each(orderGroup.orders(), function(order) {
                OrderFactory(order);
                return true;
            });
            return true;
        });
        ordersEl.data("model", model);
        ko.applyBindings(model, ordersEl.get(0))
    },

    bindDump: function(dump) {
        var dumpEl = $("#templates").find(".dump").clone();
        $("#content").empty().append(dumpEl);
        var model = ko.mapping.fromJS(dump);
        ko.applyBindings(model, dumpEl.get(0));
        dumpEl.find("code.xml").each(function(i, el) {
            hljs.highlightBlock(el);
        });
    },

    formatExpiration: function(offerModel) {
        return moment(offerModel.expiresMs()).fromNow();
    },
    
    formatDate: function(offerModel) {
        return moment(offerModel.expiresMs()).format();
    },

    loadKpi: function() {
        Admin.load("/kpi", function(data) {
            var content = $('#content');
            content.empty();
            _.each(data["charts"], function(chart, index) {
                $("<div id='chart-" + index + "'/>").appendTo(content).highcharts({
                    chart: {
                        zoomType: 'x'
                    },
                    title: {
                        text: chart.chartTitle
                    },
                    subtitle: {
                        text: document.ontouchstart === undefined ?
                            'Click and drag in the plot area to zoom in' : 'Pinch the chart to zoom in'
                    },
                    xAxis: {
                        type: 'datetime',
                        dateTimeLabelFormats: {
                            month: "%e. %b",
                            year: "%b"
                        }
                    },
                    yAxis: {
                        title: {
                            text: chart.yAxisTitle
                        }
                    },
                    legend: {
                        enabled: true
                    },
                    series: _.map(chart.timeSeries, function(timeSeries) {
                        var seriesData = _.map(timeSeries.points, function(point) {
                            return [
                                point.timestamp,
                                point.value
                            ]
                        });
                        return {
                            name: timeSeries.title,
                            data: seriesData
                        };
                    })
                });
            });
        });
    },

    init: function() {
        Highcharts.setOptions({
            global: {
                useUTC: false
            }
        });

        $(document).ready(function() {
            Admin.loadOpenOrders();
        });
    }
};