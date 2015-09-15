var dragdisApp = angular.module('app', ['dragdisApiFactory', 'fileBlobsFactory']);

dragdisApp.config(['$httpProvider', function ($httpProvider) {
    $httpProvider.defaults.headers.common["X-Requested-With"] = 'XMLHttpRequest';
}]);

dragdisApp.controller('DragdisCtrl', ['dragdisApi', function ($dragdisApi) {
    DRAGDIS.api = $dragdisApi;
}]);

