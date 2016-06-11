var Admin = {
    config: {
        PAGE_UPDATE_INTERVAL_MS: 3 * 60 * 1000
    },
    experts: null,

    loadImpl: function(url, success) {
        Admin.resetContent().append('<div class="loader" style=";">Loading...</div>');
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
                Admin.resetContent().text(message);
            }
        });
    },

    scheduledUpdates: [],

    clearSchedule: function() {
        Admin.scheduledUpdates = [];
    },
    
    schedule: function(id, callback, intervalMs) {
        callback();
        Admin.scheduledUpdates = [id];
        var fn = function() {
            if (Admin.scheduledUpdates.indexOf(id) != -1) {
                callback();
                setTimeout(fn, intervalMs);
            }
        };
        setTimeout(fn, intervalMs);
    },

    loadPage: function(url, success) {
        Admin.clearSchedule();
        Admin.loadImpl(url, success);
    },

    loadUpdatablePage: function(url, success) {
        Admin.schedule(
            url,
            function() {
                Admin.loadImpl(url, success);
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
        Admin.loadPage("/top/experts", function(data) {
            var experts = $("#templates").find(".experts").clone();
            Admin.resetContent().append(experts);
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
            Admin.loadPage("/dump/" + order.offer.room.bare(), function(data) {
                Admin.bindDump(data);
            });
        }
    },

    loadRelated: function(jid) {
        Admin.loadUpdatablePage("/related/" + jid, function(data) {
            Admin.bindOrders(data);
        });
    },

    resetContent: function() {
        var content = $("#content");
        content.children().each(function() {
            ko.cleanNode(this);
        });
        content.empty();
        return content;
    },

    bindOrders: function(orders) {
        var ordersEl = $("#templates").find(".orders").clone();
        Admin.resetContent().append(ordersEl);
        var model = ko.mapping.fromJS(orders);
        _.each(model.orderGroups(), function(orderGroup) {
            _.each(orderGroup.orders(), function(order) {
                OrderFactory(order);
                return true;
            });
            return true;
        });
        ko.applyBindings(model, ordersEl.get(0))
    },

    bindDump: function(dump) {
        var dumpEl = $("#templates").find(".dump").clone();
        Admin.resetContent().append(dumpEl);
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
        Admin.loadPage("/kpi", function(data) {
            var content = Admin.resetContent();
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