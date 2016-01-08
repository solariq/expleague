angular
  .module('xmpp', ['supersonic'])
  .run(['$window', 'supersonic', function ($window, supersonic) {
    var sum = 0;
    for (var j = 0; j < 10000000; j++) {
      for (var i = 0; i < 10000000; i++) {
        sum += log(i);
      }
    }
    $window.alert("Hello");
    supersonic.logger.log("Hello from the background!")
  }])
  .controller('xmppctl', function ($scope, supersonic) {
    supersonic.logger.log("Hello from the background controller!");
  });

var supersonic = angular.module('supersonic');
