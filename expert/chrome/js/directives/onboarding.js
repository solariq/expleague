dragdisSidebarDirectives.directive('onboarding', ['$rootScope', '$templateCache', '$timeout', function ($rootScope, $templateCache, $timeout) {
    return {
        restrict: 'A',
        template: $templateCache.get("views/onboarding"),
        link: function ($scope, $element) {

            $scope.domain = DRAGDIS.config.domain;

            //Check if onboarding notification should be shown and return if not
            if (DRAGDIS.config.userSettings.showOnboardingNotification) {

                new TrackEvent("Notifications", "Show notification", "Onboarding").send();

                $timeout(function() {
                    $scope.showOnboardingNotification = true;
                });

            } else if (DRAGDIS.config.userSettings.displayNotification) {

                $scope.notification = DRAGDIS.config.userSettings.displayNotification;

                new TrackEvent("Notifications", "Show notification", $scope.notification.Type).send();

                $timeout(function() {
                    $scope.showNotification = true;
                });


            } else {

                $element.remove();
                return;
            }


            $scope.hideNotification = function (noTrack) {

                if ($scope.showOnboardingNotification) {

                    if (!noTrack) {
                        new TrackEvent("Notifications", "Hide notification", "Onboarding").send();
                    }

                    $scope.showOnboardingNotification = false;
                }

                if ($scope.showNotification) {

                    if (!noTrack) {
                        new TrackEvent("Notifications", "Hide notification", $scope.notification.Type).send();
                    }

                    $scope.showNotification = false;
                }
            };

            $scope.turnOffNotification = function(noTrack) {

                if ($scope.notification) {

                    if (!noTrack) {
                        new TrackEvent("Notifications", "Turn off notification", $scope.notification.Type).send();
                    }

                    DRAGDIS.api("deleteNotification", $scope.notification);

                    $scope.showNotification = false;

                }

                if ($scope.showOnboardingNotification) {

                    if (!noTrack) {
                        new TrackEvent("Notifications", "Turn off notification", "Onboarding").send();
                    }

                    DRAGDIS.api("setUserSettings", {
                        params: {
                            showOnboardingNotification: false,
                            showFirstItemNotification: true,
                            firstItemDragged: false
                        }
                    });

                    $scope.showOnboardingNotification = false;

                }
            };

            $scope.showDemo = function($event) {

                $event.preventDefault();

                new TrackEvent("Notifications", "View demo", $scope.notification ? $scope.notification.Type : "Onboarding").send();

                window.open('https://www.youtube.com/watch?v=PpKwMkyAtiU', '_blank');
            };

            $scope.finishOnboarding = function($event) {

                $event.preventDefault();

                new TrackEvent("Notifications", "Onboarding completed").send();

                window.location.href = $scope.domain + '#/first';
            };

            /*=========================================
            =            Onboarding events            =
            =========================================*/

            var onDragStart = function () {

                $scope.step = 1;
                $scope.showOnboardingNotification = false;
                $scope.showNotification = false;

                if (!$scope.$$phase) {
                    $scope.$apply();
                }
            };

            var onDragEnd = function () {

                $scope.step = 0;

                if (!$scope.$$phase) {
                    $scope.$apply();
                }
            };

            var onDragEnter = function () {

                $scope.step = 2;

                if (!$scope.$$phase) {
                    $scope.$apply();
                }
            };

            var onDragLeave = function () {

                $scope.step = 1;

                if (!$scope.$$phase) {
                    $scope.$apply();
                }
            };

            var onDragComplete = function () {

                //Hide tooltips while item is uploading
                $scope.step = 0;

                //Keep sidebar opened
                DRAGDIS_SIDEBAR.openedByIcon = true;
            };

            var onUploadComplete = function () {

                $scope.step = 3;

                //Turn off Onboarding notification from now on 
                $scope.showOnboardingNotification = false;
                $scope.showNotification = false;

                DRAGDIS.api("setUserSettings", {
                    params: {
                        showOnboardingNotification: false,
                        showFirstItemNotification: true,
                        firstItemDragged: false
                    }
                });

                if (!$scope.$$phase) {
                    $scope.$apply();
                }
            };
            
            if (DRAGDIS.config.userSettings.showOnboardingNotification) {
                document.addEventListener('dragdisDragStart', onDragStart);
                document.addEventListener('dragdisDragEnd', onDragEnd);
                document.addEventListener('dragEnterFolder', onDragEnter);
                document.addEventListener('dragLeaveFolder', onDragLeave);
                document.addEventListener('dragComplete', onDragComplete);
                document.addEventListener('uploadComplete', onUploadComplete);
            }

            if (DRAGDIS.config.userSettings.displayNotification) {
                document.addEventListener('dragdisDragStart', function() {
                    $scope.hideNotification('noTrack');
                });
                document.addEventListener('dragComplete', function() {
                    $scope.turnOffNotification('noTrack');
                });
            }

        }
    };
}]);