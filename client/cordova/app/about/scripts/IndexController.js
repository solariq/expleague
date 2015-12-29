angular
  .module('about')
  .controller('IndexController', function($scope, supersonic) {

    // TODO: вынести эти переменные в глобальные нестройки
    var lsServerKeyName = 'tbtsServer';
    var lsUserKeyName = 'tbtsUser';

    var profileProduction = {
      profile: 'production',
      bosh: 'http://toobusytosearch.net:5280/',
      host: 'toobusytosearch.net',
      muc: 'muc.toobusytosearch.net',
      debug: false
    };

    var profileTest = {
      profile: 'test',
      bosh: 'http://test.expleague.com/http-bind',
      host: 'test.expleague.com',
      muc: 'muc.test.expleague.com',
      debug: true
    };

    var profileLocal = {
      profile: 'local',
      bosh: 'http://77.88.2.245:5280/',
      host: 'localhost/client',
      muc: 'muc.localhost',
      debug: true
    };

    // Если профиля нет в localStorage используем profileProduction, иначе загружаем из localStorage
    if (localStorage.getItem(lsServerKeyName) == undefined) {
      $scope.server = profileProduction;
      // Также нужно сохранить в localStorage, чтобы к нему был доступ из других страниц
      localStorage.setItem(lsServerKeyName, JSON.stringify($scope.server));
    } else {
      $scope.server = JSON.parse(localStorage.getItem(lsServerKeyName));
    }

    // Регистрируем нового пользователя, если он ещё не зарегистрирован
    var registerUser = function(){
      if (localStorage.getItem(lsUserKeyName) == undefined) {
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
            localStorage.setItem(lsUserKeyName, JSON.stringify(user));
            connection.disconnect();
          } else if (status === Strophe.Status.NOTACCEPTABLE || status === Strophe.Status.REGIFAIL) {
            // Если сервер не поддерживает регистрацию или произошла ошибка
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

      function errorHandler (error) {
        supersonic.logger.log('error = ' + error);
      }

      function registrationHandler (deviceToken) {
        supersonic.logger.log('deviceToken = ' + deviceToken);
        //save the deviceToken / registration ID to your Push Notification Server
      }

      window.plugins.pushNotification.register(
        registrationHandler,
        errorHandler, {
          "badge":"true",
          "sound":"true",
          "alert":"true"
        }
      );

    }, false);

    // Сохраняем текущие значения параметров сервера в localStorage
    $scope.saveServerParams = function(){
      localStorage.setItem(lsServerKeyName, JSON.stringify($scope.server));
      // При смене параметров сервера удаляем сохраненные данные user и регистрируемся заново
      localStorage.removeItem(lsUserKeyName);
      registerUser();
    };

    $scope.setProfile = function(profile){
      if (profile == 'production') {
        $scope.server = profileProduction;
      } else if (profile == 'test') {
        $scope.server = profileTest;
      } else if (profile == 'local') {
        $scope.server = profileLocal;
      }
      $scope.saveServerParams();
    };

  });
