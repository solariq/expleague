angular
  .module('history')
  .controller('ResultController', function($scope, supersonic) {

    $scope.backToHistory = function() {
      supersonic.ui.modal.hide();
    };

    // Вытаскиваем из localStorage значение order
    $scope.order = JSON.parse(localStorage.getItem('order'));
    localStorage.removeItem('order');
  });
