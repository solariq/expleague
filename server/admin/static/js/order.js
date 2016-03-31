OrderFactory = function (model) {
    model.hasAnswer = function() {
        var lastStatus = _.last(model.statusHistoryRecords());
        JSON.stringify(lastStatus);
        return lastStatus && lastStatus.status() == "DONE";
    }.bind(model);

    model.answerTimestamp = function() {
        if (this.hasAnswer()) {
            var lastStatus = _.last(model.statusHistoryRecords());
            return lastStatus.timestamp();
        }
        return null;
    }.bind(model);
};