dragdisSidebarDirectives.directive('moreFoldersDialog', ['moreFoldersDialogFactory', 'dialogService', 'dataService', '$window', function (moreFoldersDialogFactory, dialogService, dataService, $window) {
    return {
        restrict: "A",
        link: function (scope, element) {
            var dialog = element.parents(".dialog-container");

            dialog.addClass("fullScreen");

            scope.$on("$destroy", function () {
                dialog.removeClass("fullScreen");
            });
        },
        controller: ['$scope', function ($scope) {

            $scope.domain = DRAGDIS.config.domain;
            $scope.tab = "invite";
            $scope.invitationUrl = dataService.invitationUrl || "...";
            $scope.referalBarWidth = 0;
            $scope.possibleFoldersByRefCount = dataService.possibleFoldersByRefCount || 20;
            $scope.foldersEarnedByRefCount = dataService.foldersEarnedByRefCount || 0;

            $scope.openTab = function (tab) {
                $scope.tab = tab;
            };

            $scope.openPayment = function () {

                new TrackEvent("Sidebar", "Open payment page").send();

                var url = DRAGDIS.config.domain + '#/account/payment';

                DRAGDIS.mouseIsOnSidebar = false;
                DRAGDIS.sidebarController.hide(true);

                $window.open(url);
            };

            $scope.getDetails = function () {
                DRAGDIS.api("getUserInfo", {}).then(function (response) {

                    dataService.updateReferralSystemValues(response);

                    $scope.invitationUrl = response.InvitationUrl;

                    //Referals
                    $scope.possibleFoldersByRefCount = parseInt(response.PossibleFoldersByRefCount);
                    $scope.foldersEarnedByRefCount = parseInt(response.FoldersEarnedByRefCount);

                    $scope.referalBarWidth = Math.floor(($scope.foldersEarnedByRefCount / $scope.possibleFoldersByRefCount) * 100);
                    if ($scope.referalBarWidth === NaN) {
                        $scope.referalBarWidth = 0;
                    }

                    if (!$scope.$$phase) {
                        $scope.$apply();
                    }

                }).fail(function (error) {
                    new TrackException("Failed to get userInfo on moreFolders dialog").send();
                    console.error(error);
                });
            };

            var factory = new moreFoldersDialogFactory($scope);
            angular.extend($scope, factory);

            $scope.getDetails();
        }]
    };
}]);



