knuggetSidebarDirectives.service('dialogService', function () {
    this.template = "";
    this.folder = [];
});

knuggetSidebarDirectives.directive('dialog', ['dialogService', 'dialogFactory', '$rootScope', function (dialogService, dialogFactory, $rootScope) {
    return {
        restrict: 'A',
        controller: ['$scope', function ($scope) {
            $scope.dialogTemplate = function () {
                if (dialogService.template) {
                    $scope.dialogIsActive = true;
                    return dialogService.template;
                } else {
                    $scope.dialogIsActive = false;
                    return null;
                }
            };

            $scope.saveDialog = function () {
                dialogService.originReaponse.Text = dialogService.bodyElement.text();
                dialogService.originReaponse.Title = dialogService.titleElement.text();
                var method;
                if (dialogService.isNew) {
                    if (dialogService.index >= 0) {
                        method = "InsertAnswer"
                    } else {
                        method = 'addToBoard';
                    }
                } else {
                    method = "ReplaceAnswer";
                }
                KNUGGET.api(method, {
                    pos: dialogService.index,
                    answer: dialogService.originReaponse
                }, function (resp) {
                });
                $scope.closeDialog();
            };

            $scope.closeDialog = function () {
                $scope.dialogIsActive = false;
                dialogService.template = null;
                dialogService.originReaponse = null;
                dialogService.index = null;
                dialogService.titleElement = null;
                dialogService.bodyElement = null;
            };

            var factory = new dialogFactory($scope, $rootScope);
            angular.extend($scope, factory);
        }]
    };
}]);


knuggetSidebarDirectives.directive('editListener', ['dialogService', 'dialogFactory', '$rootScope', function (dialogService, dialogFactory, $rootScope) {
    return {
        restrict: 'A',
        link: function (scope, elm, attr) {
            if (attr.class == 'header') {
                dialogService.titleElement = elm
            } else {
                dialogService.bodyElement = elm
            }
        },
        controller: ['$scope', function ($scope) {
        }]
    };
}]);


knuggetSidebarDirectives.directive("editAnswer", ['dialogService', '$state', function (dialogService, $state) {
    return {
        restrict: "A",
        controller: ['$scope', function ($scope) {
            $scope.edit = function (resp, index) {
                if (resp.Type == 'text') {
                    dialogService.originReaponse = resp;
                    dialogService.index = index;
                    dialogService.template = KNUGGET.config.sidebarTemplatesRoot + "dialog.moreFolders";
                    dialogService.isNew = false;
                    dialogService.dialogIsActive = true;
                } else {
                    $scope.newAnswer(index);
                }
            };

            $scope.newAnswer = function(index) {
                dialogService.originReaponse = {Title: 'Заголовок', Text: 'Текст', Referer: '#', Type: 'text'};
                dialogService.index = index;
                dialogService.template = KNUGGET.config.sidebarTemplatesRoot + "dialog.moreFolders";
                dialogService.isNew = true;
                dialogService.dialogIsActive = true;
            }
        }]
    };
}]);