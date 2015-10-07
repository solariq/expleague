var knuggetApp = angular.module('app', ['knuggetApiFactory', 'fileBlobsFactory']);

knuggetApp.config(['$httpProvider', function ($httpProvider) {
    $httpProvider.defaults.headers.common["X-Requested-With"] = 'XMLHttpRequest';
}]);

knuggetApp.controller('KnuggetCtrl', ['knuggetApi', function ($knuggetApi) {
    KNUGGET.api = $knuggetApi;
}]);

