angular
  .module('order')
  .controller('IndexController', function($scope, supersonic) {
    // Количество денег у пользователя
    $scope.userBalance = 1000;

    // Массив с названиями интервалов срочности и их ценой
    $scope.urgencyRange = [
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

    // Цена привлечения к поиску эксперта
    $scope.expertPrice = 100;

    // Начальные значения поисковой формы
    $scope.order = {
      text: '',
      near: false,
      expert: false,
      urgency: 2
    }

    // Возвращает имя выбраного пользователем (текущего) значения urgency
    $scope.getCurrentUrgencyName = function(){
      return $scope.urgencyRange[$scope.order.urgency].name;
    }

    // Общая цена поиска учитывающая срочность и превлечение эксперта
    $scope.getTotalPrice = function(){
      var urgencyPrice = $scope.urgencyRange[$scope.order.urgency].price;
      var expertPrice = $scope.order.expert ? $scope.expertPrice : 0;
      return urgencyPrice + expertPrice;
    }

    $scope.submitOrder = function(){
      // Это всё нужно переделать
      localStorage.setItem('text', $scope.order.text);
      localStorage.setItem('speed', $scope.getCurrentUrgencyName());
      steroids.tabBar.selectTab(1);
      var modalView = new supersonic.ui.View('history#result');
      var options = {
        animate: false
      }
      supersonic.ui.modal.show(modalView, options);
      //defaultFormState();
    }
  });
