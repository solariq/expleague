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
                if (dialogService.isText) {
                    dialogService.originReaponse.Text = dialogService.bodyElement.text();
                }

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

            $scope.nextDialog = function (next) {
                $scope.closeDialog();
                dialogService.template = KNUGGET.config.sidebarTemplatesRoot + next;
                dialogService.dialogIsActive = true;

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
                dialogService.isText = resp.Type == 'text';
                dialogService.originReaponse = resp;
                dialogService.index = index;
                dialogService.template = KNUGGET.config.sidebarTemplatesRoot + "editDialog";
                dialogService.isNew = false;
                dialogService.dialogIsActive = true;

            };

            $scope.newAnswer = function(index) {
                dialogService.originReaponse = {Title: 'Заголовок', Text: 'Текст', Referer: '#', Type: 'text'};
                dialogService.index = index;
                dialogService.template = KNUGGET.config.sidebarTemplatesRoot + "editDialog";
                dialogService.isNew = true;
                dialogService.dialogIsActive = true;
                dialogService.isText = true;
            };
        }]
    };
}]);


knuggetSidebarDirectives.directive("confirmAnswer", ['dialogService', '$state', function (dialogService, $state) {
    return {
        restrict: "A",
        controller: ['$scope', function ($scope) {
            $scope.confirm = function () {
                dialogService.template = KNUGGET.config.sidebarTemplatesRoot + "confirmAnswer";
                dialogService.dialogIsActive = true;
            };

            $scope.doConfirm = function() {
                $scope.send();
                $scope.closeDialog();
            }
        }]
    };
}]);

knuggetSidebarDirectives.directive("showQuestion", ['dialogService', '$state', function (dialogService, $state) {
    return {
        restrict: "A",
        controller: ['$scope', function ($scope) {
            $scope.showQuestion = function () {
                dialogService.template = KNUGGET.config.sidebarTemplatesRoot + "question";
                dialogService.dialogIsActive = true;
                dialogService.request = $scope.activeRequest.value;
            };

            $scope.getLeftTime = function() {
                return dialogService.request.timeleft;
            };

            $scope.getQuestion = function() {
                return dialogService.request.question;
            };

            $scope.hasImg = function() {
                return dialogService.request.img != undefined && dialogService.request.img != null;
            };

            $scope.getImg = function() {
                return dialogService.request.img;
            };

            $scope.isUnconfirmed = function() {
                return dialogService.request.isUnconfirmed == true;
            };

            $scope.getRequest = function() {
                return dialogService.request;
            };

        }]
    };
}]);


knuggetSidebarDirectives.directive("cropDialog", ['dialogService', '$state', function (dialogService, $state) {
    return {
        restrict: "A",
        controller: ['$scope', function ($scope) {
            $scope.openCrop = function (imgUrl) {
                dialogService.template = KNUGGET.config.sidebarTemplatesRoot + "cropImage";
                dialogService.originImage = imgUrl;
                dialogService.dialogIsActive = true;
            };

            $scope.closeCrop = function() {
                $scope.nextDialog('question');
            };

            $scope.originImage = dialogService.originImage;
            $scope.croppedImage = '';

            $scope.$watch('croppedImage',function(){
                dialogService.croppedImage = $scope.croppedImage;
            });

            $scope.imgsearch = function(host) {
                if (host == 'google') {
                    $scope.upload(dialogService.croppedImage, function(url) {
                        console.log('http://google.com/searchbyimage?image_url=' + url);
                        var win = window.open('http://google.com/searchbyimage?image_url=' + url, '_blank');
                        win.focus();
                    });
                    return;
                } else if (host == 'yandex') {
                }
                alert(host + ' is not supported yet');
            };

            $scope.upload = function(img, callback) {
               callback(dialogService.originImage);
            };
        }]
    };
}]);
