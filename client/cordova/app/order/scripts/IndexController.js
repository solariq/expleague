angular
  .module('order')
  .controller('IndexController', function($scope, supersonic) {

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
