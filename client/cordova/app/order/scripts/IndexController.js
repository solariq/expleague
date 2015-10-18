angular
  .module('order')
  .controller('IndexController', function($scope, supersonic) {

    // Регистрируем нового пользователя, если он ещё не зарегистрирован
    document.addEventListener('deviceready', function () {
      if (localStorage.getItem('user') == undefined) {
        var connection = new Strophe.Connection("http://toobusytosearch.net:5280/");
        var registerCallback = function (status) {
          // Пока логин и пароль равны UUID устройства
          var username = device.uuid;
          var password = device.uuid;

          if (status === Strophe.Status.REGISTER) {
            connection.register.fields.username = username;
            connection.register.fields.password = password;
            connection.register.submit();
          } else if (status === Strophe.Status.REGISTERED || status === Strophe.Status.CONFLICT) {
            // Если пользователь был зарегистрирован только что (REGISTERED) или ранее (CONFLICT)
            // записываем в localStorage user.username и user.password
            var user = {
              username: username,
              password: password
            };
            localStorage.setItem('user', JSON.stringify(user));
            connection.disconnect();
          } else if (status === Strophe.Status.NOTACCEPTABLE || status === Strophe.Status.REGIFAIL) {
            // Если сервер не поддерñивает регистрацию или произошла ошибка
            // Сообщаем об ошибке, разъединяемся
            alert("Не удалось зарегистрировать нового пользователя");
            connection.disconnect();
          }
        };
        connection.register.connect("toobusytosearch.net", registerCallback, 60, 1);
      }
    }, false);


    // Количество денег у пользователя
    $scope.userBalance = 1000;

    // Массив с названиями интервалов срочности и их ценой
    var urgencyRange = [
      {
        name: 'в течение дня',
        price: 100
      },
      {
        name: '4 часа',
        price: 150
      },
      {
        name: '2 часа',
        price: 200
      },
      {
        name: '1 час',
        price: 250
      },
      {
        name: 'пол часа',
        price: 300
      },
      {
        name: 'в течение минуты',
        price: 350
      }
    ];

    // Тариф привлечения к поиску эксперта
    var expertRate = 100;

    // Начальные значения поисковой формы
    $scope.order = {
      text: '',
      near: false,
      expert: false,
      urgency: 2
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
      // Сохраняем результат введенный в поисковую форму в localStorage, чтобы получить к нему доступ на странице result
      localStorage.setItem('order', JSON.stringify($scope.order));
      // Переключаемся на таб history
      steroids.tabBar.selectTab(1);
      // Показываем модальный экран result
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
        urgency: 2
      }
      // Для корректной работы валидации, устанавливаем статус форму pristine
      $scope.orderForm.$setPristine();
    }
  });
