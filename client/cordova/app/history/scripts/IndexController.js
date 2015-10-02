angular
  .module('history')
  .controller('IndexController', function($scope, supersonic) {

    $scope.showDateTime = function(timestamp) {
      var monthNames = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
      var date = new Date();
      date.setTime(timestamp);
      var day = date.getDate();
      var month = date.getMonth();
      var year = date.getFullYear();
      var hours = date.getHours();
      var minutes = date.getMinutes();
      return day + ' ' + monthNames[month] + ' ' + year + ', ' + hours + ':' + minutes;
    };

    $scope.showResults = function() {
      var modalView = new supersonic.ui.View('history#result');
      var options = {
        animate: false
      }
      supersonic.ui.modal.show(modalView, options);
    };

    $scope.history = [
      {
        id: 3,
        text: 'Самый большой по площади парк Питера',
        near: false,
        expert: false,
        urgency: 0, // От 0 до 5
        timestamp: 1443710220000
      },
      {
        id: 2,
        text: 'Правила парковки в Финляндии',
        near: false,
        expert: false,
        urgency: 0, // От 0 до 5
        timestamp: 1443565680000
      },
      {
        id: 1,
        text: 'круглосуточная аптека',
        near: 'рядом с адресом: Москва, ул. Большая Декабрьская 10',
        expert: false,
        urgency: 0, // От 0 до 5
        timestamp: 1443356040000
      }
    ];

  });
