angular.module('knuggetApiFactory', []).factory('knuggetApi', ['$http', '$q', '$interval', '$timeout', 'fileBlob', function ($http, $q, $interval, $timeout, $fileBlob) {

    var convert = function(el) {
        if (el.Type == 'link') {
            if (el.Base64Image != undefined) {
                return {
                    image: {
                        image: el.Base64Image,
                        title: el.Title,
                        referer: el.Referer
                    }
                };
            } else {
                return {
                    link: {
                        href: el.Href,
                        title: el.Title,
                        referer: el.Referer
                    }
                };
            }
        } else if (el.Type == 'text') {
            return {
                text: {
                    text: el.Text,
                    title: el.Title,
                    referer: el.Referer
                }
            };
        } else if (el.Type == 'picture' || el.Type == 'apicture') {
            return {
                image: {
                    image: el.Image, //todo make base64
                    title: el.Title,
                    referer: el.Referer
                }
            };
        } else {
            console.log('WARNING! UNEXPECTED RESPONCE ELEMENT TYPE: ' + el.Type);
            console.log(el);
        }
    };

    var sync = function(type, data, callback) {

        var res = $http.post('http://localhost:8080', {
            type: type,
            data: data
        });
        res.success(function(data, status, headers, config) {
            console.log(status);
            console.log(data);
            console.log(headers);
            callback(status);
        });
        res.error(function(data, status, headers, config) {
            console.log(status);
            console.log(data);
            console.log(headers);
            callback(status)
        });

    };

    var addToBoardSync = function(answer, callback) {
        if (answer.Base64Image != undefined) {
            return;
        }
        KNUGGET.storage.get("Board", function (value) {
            value = value ? JSON.parse(value) : [];
            if (typeof answer == 'string') {
                value.splice(0, 0, answer);
            } else {
                value.splice(0, 0, JSON.stringify(answer));
            }
            KNUGGET.storage.set("Board", JSON.stringify(value));
            console.log(JSON.stringify(value));
            callback();
        });
    };


    var removeRequest = function(request, callback) {
        KNUGGET.storage.get("Requests", function (value) {
            value = value ? JSON.parse(value) : [];
            result = [];
            value.forEach(function(el, i) {
                if (request.id != el.id)
                    result.push(el);
            });
            KNUGGET.storage.set("Requests", JSON.stringify(result));
            callback();
        });
    };

    var clearBoard = function() {
        KNUGGET.storage.set("Board", JSON.stringify([]));
    };


    api = {

        PageVisited: function(data) {
            console.log(JSON.stringify(data));
            var defer = $q.defer();
            var tabUrl = data.tabUrl;
            sync("pageVisited", tabUrl, function (status) {
                if (status != 200) {
                    clearBoard();
                }
                defer.resolve({status: status});
            })
        },


        addToBoard: function(data) {
            var answer = typeof(data.answer) == 'string' ? JSON.parse(data.answer) : data.answer;
            var defer = $q.defer();
            addToBoardSync(answer, function() {
                sync("newItem", convert(answer), function(status) {
                    defer.resolve({status: status});
                });
            });
            return defer.promise;
        },

        Upload: function(data, senderId) {

            data.SenderId = senderId;

            if ((data.Files && data.Files[0]) || data.Base64Image || data.MakeSnapshot || data.Image) {

                var fileProcessing = $fileBlob.fileProcessing(data);

                fileProcessing.success = function (fn) {
                    fileProcessing.then(function (response) {
                        fn(response.data, response.status, response.headers);
                    });
                    return fileProcessing;
                };

                fileProcessing.error = function (fn) {
                    fileProcessing.then(null, function (response) {
                        fn(response.data, response.status, response.headers);
                    });
                    return fileProcessing;
                };

                return fileProcessing;

            } else {

                // Define a promise
                var itemCreated = $http.post(apiUrl + '/item/add', data);

                return itemCreated;
            }
        },

    };

    return api;
}]);
