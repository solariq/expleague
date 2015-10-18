angular
  .module('history')
  .controller('ResultController', function($scope, supersonic) {

    $scope.backToHistory = function() {
      supersonic.ui.modal.hide();
    };
  });
