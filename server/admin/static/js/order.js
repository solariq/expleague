OrderFactory = function (model) {
    model.hasAnswer = ko.pureComputed(function() {
        var lastStatus = _.last(model.statusHistoryRecords());
        return lastStatus && lastStatus.status() == "DONE";
    });

    model.answerTimestamp = ko.pureComputed(function() {
        if (model.hasAnswer()) {
            var lastStatus = _.last(model.statusHistoryRecords());
            return lastStatus.timestamp();
        }
        return null;
    });
};