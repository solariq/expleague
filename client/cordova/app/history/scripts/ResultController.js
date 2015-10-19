angular
  .module('history')
  .controller('ResultController', function($scope, supersonic) {

    $scope.messages = [];
    // Формат message
    // {
    //   type: 'client',         // 'client' или 'server'
    //   title: 'Заголовок',     // может быть пустым
    //   text: 'Текст сообщения' // может содержать html-теги
    // };

    $scope.backToHistory = function() {
      supersonic.ui.modal.hide();
    };

    // Вытаскиваем из localStorage значение order
    $scope.order = JSON.parse(localStorage.getItem('order'));
    localStorage.removeItem('order');

    // Вытаскиваем из localStorage значение server
    $scope.server = JSON.parse(localStorage.getItem('server'));

    // Вытаскиваем из localStorage значение user
    $scope.user = JSON.parse(localStorage.getItem('user'));

    $scope.connection = {
      status: null,
      jid: $scope.user.username + '@' + $scope.server.host
    };

    $scope.chatroom = {
      status: null,
      name: 'room' + (new Date().getTime()) + '@' + $scope.server.muc,
      topic: $scope.order.text
    };

    function playSound() {
      var sound = new Media('http://localhost/bb2.mp3');
      sound.play();
    }

    // Ешё не совсем понял суть Angular, без $apply не работает обновление значения status в html
    function changeConnectionStatus(status) {
      $scope.$apply(function() {
        $scope.connection.status = status;
      });
    };

    function changeChatroomStatus(status) {
      $scope.$apply(function() {
        $scope.chatroom.status = status;
      });
    };

    function onGroupchatMessage(message) {
      var messageText = $(message).children('body').text();
      var messageFrom = $(message).attr('from');
      if (messageText != 'Room is now unlocked' && messageText != '') {
        var messageType, messageTitle;
        // Если пришло собственное сообщение от клиента
        if (messageFrom == $scope.chatroom.name + '/' + $scope.user.username) {
          messageType = 'client';
          messageTitle = 'Клиент';
        } else {
          messageType = 'server';
          messageTitle = 'Сервер';
        }
        var message = {
          type: messageType,
          title: messageTitle,
          text: messageText
        };
        $scope.$apply(function() {
          $scope.messages.push(message);
        });
        $(window).scrollTop(document.body.clientHeight);
        playSound();
      }
      return true;
    }

    function createChatRoom(connection, roomName, userAlias, topic, messageHandler) {
      function onPresence(data) {
        console.log('presence', data);
        return true;
      }
      function onRoster(data) {
        console.log('roster', data);
        return true;
      }
      function onMessage(message) {
        console.log('message', message);
        var body = $(message).children('body').text();
        if (body == 'Room is locked. Please configure.') {
          connection.muc.saveConfiguration(roomName, [],
            function () {
              changeChatroomStatus('connected');
              connection.muc.setTopic(roomName, topic);
              connection.addHandler(messageHandler, null, 'message', 'groupchat');
            },
            function (err) {
              alert('Chatroom configuration error: ' + err);
            }
          );
        }
      }
      connection.muc.join(roomName, userAlias, onMessage, onPresence, onRoster);
    };

    var connection = new Strophe.Connection($scope.server.bosh);
    connection.connect($scope.connection.jid, $scope.user.password, function (status) {
      if (status === Strophe.Status.CONNECTING) {
        changeConnectionStatus('connecting');
      } else if (status === Strophe.Status.CONNECTED) {
        changeConnectionStatus('connected');
        createChatRoom(connection, $scope.chatroom.name, $scope.user.username, $scope.chatroom.topic, onGroupchatMessage);
      } else if (status == Strophe.Status.DISCONNECTING) {
        changeConnectionStatus('disconnecting');
      } else if (status === Strophe.Status.DISCONNECTED) {
        changeConnectionStatus('disconnected');
      } else if (status === Strophe.Status.AUTHFAIL) {
        changeConnectionStatus('failed to auth');
      } else if (status === Strophe.Status.ERROR || status === Strophe.Status.CONNFAIL) {
        changeConnectionStatus('failed to connect, status: ' + status);
      } else {
        changeConnectionStatus('unknown');
      }
    });

    // Отправка сообщения с клиента
    $scope.sendMessage = function() {
      var text = $('.input-message').val();
      connection.send(
        $msg({
          to: $scope.chatroom.name,
          type: 'groupchat'
        }).c('body').t(text)
      );
      $('.input-message').val('');
      $('.input-message').focus();
    };
  });
