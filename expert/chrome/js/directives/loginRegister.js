knuggetSidebarDirectives.directive('login', function () {
    return {
        restrict: 'A',
        controller: ['$scope', function ($scope) {

            $scope.$parent.isLoginForm = $scope.$parent.isLoginForm == undefined ? true : $scope.$parent.isLoginForm;
            $scope.$parent.isRegisterForm = $scope.$parent.isRegisterForm == undefined ? false : $scope.$parent.isRegisterForm;
            $scope.loginForm = { rememberMe: true };

            $scope.enableLogin = function() {
                $scope.$parent.isLoginForm = true;
                $scope.$parent.isRegisterForm = false;
            };

            $scope.enableRegister = function() {
                $scope.$parent.isRegisterForm = true;
                $scope.$parent.isLoginForm = false;
            };

            $scope.registerUser = function(event) {

                //Prevent rapid clicks
                if ($scope.isRegisterInProgress) {
                    return;
                }

                $scope.isRegisterInProgress = true;

                KNUGGET.api("registerUser", $scope.registerForm, function (response) {

                    console.log('register register callback');
                    $scope.isRegisterInProgress = false;

                    //If no errors returned - registration were successfull
                    if (response.status == 200) {

                        //Clear registration error list
                        $scope.regErrors = [];
                        $scope.loginForm = $scope.registerForm;
                        $scope.enableLogin();

                        $scope.loginUser(null);
                        //
                        ////Connect extension to server
                        //KNUGGET.sendMessage({ Type: "RECONNECT" }, function () { });

                    } else {
                        $scope.regErrors = [response.msg];
                    }

                    if (!$scope.$$phase) {
                        $scope.$apply();
                    }
                });
            };

            $scope.loginUser = function (event) {

                //Prevent rapid clicks
                if ($scope.isLoginInProgress) {
                    return;
                }

                $scope.isLoginInProgress = true;

                KNUGGET.api("loginUser", $scope.loginForm, function (response) {

                    console.log('login register callback');
                    $scope.isLoginInProgress = false;

                    //If no errors returned - registration were successfull
                    if (response.status == 200) {

                        //Clear registration error list
                        $scope.loginErrors = [];
                        //
                        ////Connect extension to server
                        //KNUGGET.sendMessage({ Type: "RECONNECT" }, function () { });

                    } else {
                        $scope.loginErrors = response.messages;
                    }

                    if (!$scope.$$phase) {
                        $scope.$apply();
                    }
                });
            };


            if (window.chrome && window.chrome.storage) {
                window.chrome.storage.onChanged.addListener(function (data) {

                    console.log("!!storage updated", data);

                    if (data.UserData) {
                        console.log("upd user data");
                        $scope.userData.update();
                    }

                    var timeout = setInterval(function(){
                        if (!$scope.$$phase) {
                            //alert('apply');
                            $scope.$apply();
                        }
                    }, 100);
                });
            }


            $scope.userData = {
                userLogin: null,
                userPassword: null,
                update: function() {
                    KNUGGET.storage.get(["UserData"], function (value) {
                        value = JSON.parse(value.UserData);
                        $scope.userData.userLogin = value.userLogin;
                        $scope.userData.userPassword = value.userPassword;
                        $scope.loginForm.Username = value.userLogin;
                        $scope.loginForm.Password = value.userPassword;
                    });
                }
            };

            $scope.init = function () {
                $scope.userData.update();
            };

            $scope.init();
        }]
    };
});