dragdisSidebarDirectives.directive('folderCollaboration', ['$rootScope', '$injector', 'collaborationFactory', 'dialogService', 'Basics', function ($rootScope, $injector, collaborationFactory, dialogService, $basic) {
    return {
        restrict: "A",
        link: function (scope, element) {
            var dialog = element.parents(".dialog-container");

            dialog.addClass("fullScreen");

            scope.$on("destroy", function () {
                dialog.removeClass("fullScreen");
            });
        },
        controller: ['$scope', '$element', function ($scope, $element) {
            $scope.collaboratorsCount = 0;

            $scope.loadLinks = function () {

                //Get folder sharing links
                DRAGDIS.api("FolderGetSharingLinks", $scope.folder).then(function (response) {

                    $scope.shareFolderLink = DRAGDIS.config.shortUrlDomain + response.ShareUrl;
                    $scope.collaboratorsCount = response.CollaboratorsCount;

                    if (response.CollaborateUrl) {
                        $scope.collaborativeFolderLink = DRAGDIS.config.shortUrlDomain + response.CollaborateUrl;
                    } else {
                        $scope.collaborativeFolderLink = false;
                    }

                    $scope.folderAlredyLinked = response.IsLinked;

                    if (!$scope.$$phase) {
                        $scope.$apply();
                    }

                }, function (error) {
                    new TrackException("Failed to fetch sharing links").send();
                    console.error(error);
                });
            };

            //Generate or regenerate folder collaboration link
            $scope.generateLink = function () {

                new TrackEvent("Sidebar", "Collaboration link generated").send();

                DRAGDIS.api("FolderGetCollaborationLink", dialogService.folder).then(function (response) {

                    if (response) {
                        $scope.collaborativeFolderLink = DRAGDIS.config.shortUrlDomain + response;

                        if (!$scope.$$phase) {
                            $scope.$apply();
                        }
                    }

                }).fail(function (error) {
                    new TrackException("Failed to generate collaboration link").send();
                    console.error(error);
                });
            };

            //Make folder collaboration link invalid
            $scope.destroyLink = function () {

                new TrackEvent("Sidebar", "Collaboration link destroyed").send();

                DRAGDIS.api("FolderDestroyCollaborationLink", dialogService.folder).then(function (response) {

                    $scope.collaborativeFolderLink = false;

                    if (!$scope.$$phase) {
                        $scope.$apply();
                    }

                }).fail(function (error) {
                    new TrackException("Failed to destroy collaboration link").send();
                    console.error(error);
                });
            };

            //Revoke access to all folders that were already linked
            $scope.revokeFolderAccess = function () {

                new TrackEvent("Sidebar", "Collaboration link revoked", null, $scope.collaboratorsCount).send();

                DRAGDIS.api("FolderRevokeCollaboratorAccess", dialogService.folder).then(function (response) {

                    $scope.destroyLink();
                    $scope.collaboratorsCount = 0;

                    if (!$scope.$$phase) {
                        $scope.$apply();
                    }

                }).fail(function (error) {
                    new TrackException("Failed to revoke collaboration access").send();
                    console.error(error);
                });
            };


            $scope.collabLoad = function () {
                $scope.folder = dialogService.folder;
                $scope.loadLinks();
            };

            var factory = new collaborationFactory($scope);
            angular.extend($scope, factory);

            $scope.$watch('folder', function (newVal, oldVal) {

                //Prevent to fire watch on initialisation
                if (newVal === oldVal) return;

                if (newVal && newVal.ID > 0) {
                    $scope.loadLinks();
                }
            });

            $scope.collabLoad();
        }]
    };
}]);



