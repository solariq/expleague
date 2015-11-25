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
        id: 4,
        text: 'Что это за клевый мотоцикл?',
        near: false,
        expert: true,
        urgency: 1, // От 0 до 2
        started: 1447767190000,
        ended: 1447767190001
      },
      {
        id: 3,
        text: 'Захотела подключить колонки к телевизору, чтобы на них выводился звук. Помогите разобраться, пожалуйста. Вот фотка колонок, вот фотка телевизора. Как можно их подключить к телевизору? Какой нужен провод?',
        near: false,
        expert: true,
        urgency: 1, // От 0 до 2
        started: 1447671040000,
        ended: 1447671040001
      },
      {
        id: 2,
        text: 'Самый большой по площади парк Питера',
        near: false,
        expert: true,
        urgency: 2, // От 0 до 2
        started: 1443710220000,
        ended: 1443710220001
      },
      {
        id: 1,
        text: 'Правила парковки в Финляндии',
        near: false,
        expert: false,
        urgency: 0, // От 0 до 2
        started: 1443565680000,
        ended: 1443565680001
      },
      {
        id: 0,
        text: 'круглосуточная аптека',
        near: 'рядом с адресом: Москва, ул. Большая Декабрьская 10',
        expert: false,
        urgency: 2, // От 0 до 2
        started: 1443356040000,
        ended: 1443356040001
      }
    ];

  });
