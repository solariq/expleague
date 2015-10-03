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

    $scope.history = [
      {
        id: 2,
        text: 'Самый большой по площади парк Питера',
        near: false,
        expert: false,
        urgency: 0, // От 0 до 5
        started: 1443710220000,
        ended: 1443710220001
      },
      {
        id: 1,
        text: 'Правила парковки в Финляндии',
        near: false,
        expert: false,
        urgency: 0, // От 0 до 5
        started: 1443565680000,
        ended: 1443565680001
      },
      {
        id: 0,
        text: 'круглосуточная аптека',
        near: 'рядом с адресом: Москва, ул. Большая Декабрьская 10',
        expert: false,
        urgency: 0, // От 0 до 5
        started: 1443356040000,
        ended: 1443356040001
      }
    ];

  });
