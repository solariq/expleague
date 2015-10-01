angular
  .module('history')
  .controller('IndexController', function($scope, supersonic) {
    // Controller functionality here
    $scope.showResults = function() {
      var modalView = new supersonic.ui.View('example#result');
      var options = {
        animate: false
      }
      supersonic.ui.modal.show(modalView, options);
    };
  });
