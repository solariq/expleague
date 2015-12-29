angular
  .module('order')
  .controller('IndexController', function($scope, supersonic) {

    // TODO: вынести эти переменные в глобальные нестройки
    var lsServerKeyName = 'tbtsServer';
    var lsUserKeyName = 'tbtsUser';
    var lsHistoryKeyName = 'tbtsHistory';

    // Расширяющаяся textarea
    $('.order-textarea').textareaAutoSize();

    // Количество денег у пользователя
    $scope.userBalance = 1000;

    // Массив с названиями интервалов срочности и их ценой
    var urgencyRange = [
      {
        name: 'неважно',
        price: 100
      },
      {
        name: 'сегодня',
        price: 150
      },
      {
        name: 'срочно',
        price: 200
      }
    ];

    // Тариф привлечения к поиску эксперта
    var expertRate = 100;

    // Начальные значения поисковой формы
    $scope.order = {
      text: '',
      near: false,
      expert: false,
      urgency: 1
    }

    // Возвращает имя значения urgency выбраного пользователем
    $scope.getCurrentUrgencyName = function(){
      return urgencyRange[$scope.order.urgency].name;
    }

    // Общая цена поиска учитывающая срочность и превлечение эксперта
    $scope.getTotalPrice = function(){
      var urgencyPrice = urgencyRange[$scope.order.urgency].price;
      var expertPrice = ($scope.order.expert ? expertRate : 0);
      return urgencyPrice + expertPrice;
    }

    $scope.submitOrder = function(){
      // Сохраняем результат введенный в поисковую форму в localStorage (tbtsHistory), чтобы получить к нему доступ на странице result
      var tbtsHistory = JSON.parse(localStorage.getItem(lsHistoryKeyName)) || [];
      var historyItem = {
        id: tbtsHistory.length,
        text: $scope.order.text,
        near: $scope.order.near,
        expert: $scope.order.expert,
        urgency: $scope.order.urgency,
        started: new Date().getTime(),
        ended: null,
        room: null,
        answerMode: 'chat',
        messages: []
      }
      tbtsHistory.push(historyItem);
      localStorage.setItem(lsHistoryKeyName, JSON.stringify(tbtsHistory));
      // Отправляем history#index сообщение, что нужно обновить список истории
      supersonic.data.channel('announcements').publish({content: 'refresh'});
      // Переключаемся на таб history
      steroids.tabBar.selectTab(1);
      // Показываем модальный экран result, если не задан параметр id, то показываем самый последний объект history
      var modalView = new supersonic.ui.View('history#result');
      var options = {
        animate: false
      }
      supersonic.ui.modal.show(modalView, options);
      // Очищаем форму, чтобы при возвращение на таб order можно было задавать новый запрос
      // TODO: избавиться от дублирования кода для очистки формы
      $scope.order = {
        text: '',
        near: false,
        expert: false,
        urgency: 1
      }
      // Для корректной работы валидации, устанавливаем статус форму pristine
      $scope.orderForm.$setPristine();
      // Возвращаем floating-label в исходное состояние
      $('.input-label').removeClass('has-input');
    }

    // Количество экспертов online
    $scope.expersOnline = null;
    // Если в localStorage уже есть настройки server и user
    function presenceHandler(presence) {
      var from = $(presence).attr('from');
      if (from.split('@')[0] == 'experts-admin') {
        // Ешё не совсем понял суть Angular, без $apply не работает обновление значения expersOnline в html
        $scope.$apply(function() {
          $scope.expersOnline = parseInt($('status', presence).text());
        });
      }
      return true;
    }

    // if (localStorage.getItem(lsServerKeyName) != undefined && localStorage.getItem(lsUserKeyName) != undefined) {
    //   $scope.server = JSON.parse(localStorage.getItem(lsServerKeyName));
    //   $scope.user = JSON.parse(localStorage.getItem(lsUserKeyName));
    //   var connection = new Strophe.Connection($scope.server.bosh);
    //   connection.connect($scope.user.username + '@' + $scope.server.host, $scope.user.password, function (status) {
    //     if (status === Strophe.Status.CONNECTED) {
    //       connection.addHandler(presenceHandler, null, 'presence');
    //       connection.send($pres());
    //     }
    //   });
    // }

  })
  .directive('itemFloatingLabel', function() {
    return {
      restrict: 'C',
      link: function(scope, element) {
        var el = element[0];
        var input = el.querySelector('input, textarea');
        var inputLabel = el.querySelector('.input-label');

        if (!input || !inputLabel) return;

        var onInput = function() {
          if (input.value) {
            inputLabel.classList.add('has-input');
          } else {
            inputLabel.classList.remove('has-input');
          }
        };

        input.addEventListener('input', onInput);

        var ngModelCtrl = jqLite(input).controller('ngModel');
        if (ngModelCtrl) {
          ngModelCtrl.$render = function() {
            input.value = ngModelCtrl.$viewValue || '';
            onInput();
          };
        }

        scope.$on('$destroy', function() {
          input.removeEventListener('input', onInput);
        });
      }
    };
  });
