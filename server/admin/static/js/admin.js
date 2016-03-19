var Admin = {
    load: function(url, success) {
        $("#content").empty().append('<div class="loader" style=";">Loading...</div>');
        $.ajax({
            type: "GET",
            dataType: "json",
            url: url,
            success: success
        });
    },
    
    loadOpenOrders: function() {
        Admin.load("/open", function(data) {
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

    loadRelatedHandler: function(expertProfile) {
        return function() {
            Admin.loadRelated(expertProfile.jid.bare())
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
        Admin.load("/related/" + jid, function(data) {
            Admin.bindOrders(data);
        });
    },

    bindOrders: function(orders) {
        var ordersEl = $("#templates").find(".orders").clone();
        $("#content").empty().append(ordersEl);
        var model = ko.mapping.fromJS(orders);
        ko.applyBindings(model, ordersEl.get(0))
    },

    bindDump: function(dump) {
        var dumpEl = $("#templates").find(".dump").clone();
        $("#content").empty().append(dumpEl);
        var model = ko.mapping.fromJS(dump);
        ko.applyBindings(model, dumpEl.get(0))
    },

    init: function() {
        $(document).ready(function() {
            Admin.loadOpenOrders();
        });
    }
};