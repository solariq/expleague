dragdisSidebarDirectives.service('dialogService', function () {
    this.template = "";
    this.folder = [];
});

dragdisSidebarDirectives.directive('dialog', ['dialogService', 'dialogFactory', '$rootScope', function (dialogService, dialogFactory, $rootScope) {
    return {
        restrict: 'A',
        controller: ['$scope', function ($scope) {
            $scope.folder = dialogService.folder;

            $scope.dialogTemplate = function () {
                if (dialogService.template) {
                    $scope.dialogIsActive = true;
                    return dialogService.template;
                } else {
                    $scope.dialogIsActive = false;
                    return null;
                }
            };

            $scope.closeDialog = function () {
                $scope.dialogIsActive = false;
                dialogService.template = null;
                dialogService.folder = [];
            };

            var factory = new dialogFactory($scope, $rootScope);
            angular.extend($scope, factory);
        }]
    };
}]);