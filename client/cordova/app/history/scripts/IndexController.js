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
