angular
  .module('about')
  .controller('IndexController', function($scope, supersonic) {

    var profileRemote = {
      profile: 'remote',
      bosh: 'http://toobusytosearch.net:5280/',
      host: 'toobusytosearch.net',
      muc: 'muc.toobusytosearch.net'
    };

    var profileLocal = {
      profile: 'local',
      bosh: 'http://77.88.2.245:5280/',
      host: 'localhost/client',
      muc: 'muc.localhost'
    };

    // Если профиль сохранен в localStorage, загружаем его, иначе используем profileRemote
    $scope.server = (localStorage.getItem('server') == undefined) ? profileRemote : JSON.parse(localStorage.getItem('server'));

    // Регистрируем нового пользователя, если он ещё не зарегистрирован
    var registerUser = function(){
      if (localStorage.getItem('user') == undefined) {
        var connection = new Strophe.Connection($scope.server.bosh);
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
        connection.register.connect($scope.server.host, registerCallback, 60, 1);
      }
    };

    // Как только устройство готово (при старте приложения), регистрируем пользователя
    document.addEventListener('deviceready', function () {
      registerUser();
    }, false);

    // Сохраняем текущие значения параметров сервера в localStorage
    $scope.saveServerParams = function(){
      localStorage.setItem('server', JSON.stringify($scope.server));
      // При смене параметров сервера удаляем сохраненные данные user и регистрируемся заново
      localStorage.removeItem('user');
      registerUser();
    };

    $scope.setProfile = function(profile){
      if (profile == 'remote') {
        $scope.server = profileRemote;
      } else if (profile == 'local') {
        $scope.server = profileLocal;
      }
      $scope.saveServerParams();
    };

  });
