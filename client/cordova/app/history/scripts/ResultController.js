angular
  .module('history')
  .controller('ResultController', function($scope, supersonic) {

    $scope.backToHistory = function() {
      supersonic.ui.modal.hide();
    };

    // Временно положил в глобальный window.ordersHistory чтобы сделать демо-режим на jQuery
    window.ordersHistory = [
      {
        id: 2,
        text: 'Самый большой по площади парк Питера',
        near: false,
        expert: false,
        urgency: 5, // От 0 до 5
        started: 1443710220000,
        ended: 1443710220001,
        answerMode: 'chat',
        messages: [
          {
            type: 'client',
            title: 'Необходимо найти',
            text: 'Самый большой по площади парк Питера<br> срочность поиска: 15 минут',
            timestamp: null
          },
          {
            type: 'server',
            title: 'Поиск исполнителя',
            text: 'Исполнитель найден. Вы получите уведомление, когда исполнитель найдет необходимую информацию.',
            timestamp: null
          },
          {
            type: 'server',
            title: 'Результаты поиска',
            text: 'Сосновка, площадь 286 га.',
            timestamp: null
          },
          {
            type: 'client',
            title: '',
            text: 'А во сколько раз приморский парк победы больше, чем цпкио?',
            timestamp: null
          },
          {
            type: 'client',
            title: '',
            text: 'В 4 раза есть, хотя б?',
            timestamp: null
          },
          {
            type: 'server',
            title: 'Дополнительный ответ',
            text: 'Площадь ЦПКиО 94 га. Площадь Приморского парка Победы 140 га. То есть, примерно в полтора раза. В 4 раза не получается. :(',
            timestamp: null
          }
        ]
      },
      {
        id: 1,
        text: 'Правила парковки в Финляндии<br> срочность поиска: в течение дня',
        near: false,
        expert: false,
        urgency: 0, // От 0 до 5
        started: 1443565680000,
        ended: 1443565680001,
        answerMode: 'plain',
        messages: [
          {
            type: 'client',
            title: 'Необходимо найти',
            text: 'Правила парковки в Финляндии',
            timestamp: null
          },
          {
            type: 'server',
            title: 'Поиск исполнителя',
            text: 'Исполнитель найден. Вы получите уведомление, когда исполнитель найдет необходимую информацию.',
            timestamp: null
          }
        ]
      },
      {
        id: 0,
        text: 'круглосуточная аптека',
        near: 'рядом с адресом: Москва, ул. Большая Декабрьская 10',
        expert: false,
        urgency: 2, // От 0 до 5
        started: 1443356040000,
        ended: 1443356040001,
        answerMode: 'plain',
        messages: [
          {
            type: 'client',
            title: 'Необходимо найти',
            text: 'круглосуточная аптека рядом с адресом: Москва, ул. Большая Декабрьска 10<br> срочность поиска: 2 часа',
            timestamp: null
          },
          {
            type: 'server',
            title: 'Поиск исполнителя',
            text: 'Исполнитель найден. Вы получите уведомление, когда исполнитель найдет необходимую информацию.',
            timestamp: null
          },
          {
            type: 'server',
            title: 'Результаты поиска',
            text: 'Ближайшая круглосуточная аптека — «Старый лекарь»',
            extra: {
              address: '123022, Москва, ул. 1905 года, 19',
              tel: '8 (499) 253-52-61'
            },
            timestamp: null
          }
        ]
      }
    ];
    $scope.history = window.ordersHistory;

    // Ищем конкретную запись в массиве history по id, переданному в качестве параметра view
    $scope.currentHistoryItemId = steroids.view.params.id;
    $scope.currentHistoryItem = [];
    if ($scope.currentHistoryItemId != undefined) {
      for (var i = 0; i < $scope.history.length; i++) {
        if ($scope.history[i].id == $scope.currentHistoryItemId) {
          $scope.currentHistoryItem = $scope.history[i];
          break;
        }
      }
    }
  });
