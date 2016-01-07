angular
  .module('history')
  .controller('IndexController', function($scope, supersonic) {

    // TODO: вынести эти переменные в глобальные нестройки
    var lsHistoryKeyName = 'tbtsHistory';

    $scope.showResults = function(id) {
      var modalView = new supersonic.ui.View('history#result?id=' + id);
      var options = {
        animate: false
      };
      supersonic.ui.modal.show(modalView, options);
    };

    $scope.removeResults = function(id) {
      supersonic.logger.log("Removing: " + id);
      $scope.history = $scope.history.filter(function(item){
        return item.id !== id;
      });
      localStorage.setItem(lsHistoryKeyNamem, JSON.stringify($scope.history));
      supersonic.logger.log("Left: " + JSON.stringify($scope.history));
    };

    $scope.history = JSON.parse(localStorage.getItem(lsHistoryKeyName)) || [];

    // Необходимо обновить список истории, если получили сообщение из order#index
    supersonic.data.channel('announcements').subscribe(function(message) {
      if (message.content == 'refresh') {
        $scope.$apply(function() {
          $scope.history = JSON.parse(localStorage.getItem(lsHistoryKeyName)) || [];
        });
      }
    });

  });
