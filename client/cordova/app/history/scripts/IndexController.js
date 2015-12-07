angular
  .module('history')
  .controller('IndexController', function($scope, supersonic) {

    $scope.showResults = function(id) {
      var modalView = new supersonic.ui.View('history#result?id=' + id);
      var options = {
        animate: false
      }
      supersonic.ui.modal.show(modalView, options);
    };

    $scope.history = JSON.parse(localStorage.getItem('tbtsHistory')) || [];

    // Необходимо обновить список истории, если получили сообщение из order#index
    supersonic.data.channel('announcements').subscribe(function(message) {
      if (message.content == 'refresh') {
        $scope.$apply(function() {
          $scope.history = JSON.parse(localStorage.getItem('tbtsHistory')) || [];
        });
      }
    });

  });
