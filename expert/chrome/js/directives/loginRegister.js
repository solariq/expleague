dragdisSidebarDirectives.directive('login', function () {
    return {
        restrict: 'A',
        controller: ['$scope', function ($scope) {

            $scope.$parent.isLoginForm = true;
            $scope.loginForm = { rememberMe: true };

            $scope.loginUser = function (event) {

                //Prevent rapid clicks
                if ($scope.isLoginInProgress) {
                    return;
                }

                $scope.isLoginInProgress = true;

                DRAGDIS.api("loginUser", $scope.loginForm, function (response) {

                    $scope.isLoginInProgress = false;

                    //If no errors returned - registration were successfull
                    if (!response.data.error) {

                        //Clear registration error list
                        $scope.loginErrors = [];

                        //Connect extension to server
                        DRAGDIS.sendMessage({ Type: "RECONNECT" }, function () { });

                    } else {
                        $scope.loginErrors = response.data.messages;
                    }

                    if (!$scope.$$phase) {
                        $scope.$apply();
                    }
                });
            };
        }]
    };
});