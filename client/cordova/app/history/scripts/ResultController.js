angular
  .module('history')
  .controller('ResultController', function($scope, $sce, supersonic) {

    // TODO: вынести эти переменные в глобальные нестройки
    var lsServerKeyName = 'tbtsServer';
    var lsUserKeyName = 'tbtsUser';
    var lsHistoryKeyName = 'tbtsHistory';

    // Вытаскиваем историю из localStorage
    var tbtsHistory = JSON.parse(localStorage.getItem(lsHistoryKeyName)) || [];
    // Ищем конкретную запись в массиве tbtsHistory по id, переданному в качестве параметра view,
    // если id не передали, берем последнюю запись в массиве
    var tbtsHistoryItemId = steroids.view.params.id || (tbtsHistory.length - 1);
    $scope.tbtsHistoryItem = tbtsHistory[tbtsHistoryItemId];

    $scope.messages = $scope.tbtsHistoryItem.messages;
    // Формат message
    // {
    //   type: 'client',         // 'client' или 'server'
    //   title: 'Заголовок',     // может быть пустым
    //   text: 'Текст сообщения' // может содержать html-теги
    // };

    // Вытаскиваем из localStorage значение server
    $scope.server = JSON.parse(localStorage.getItem(lsServerKeyName));

    // Вытаскиваем из localStorage значение user
    $scope.user = JSON.parse(localStorage.getItem(lsUserKeyName));

    // Усли комната ещё не создана, задаем имя комнаты
    $scope.tbtsHistoryItem.room = $scope.tbtsHistoryItem.room || ('room' + (new Date().getTime()) + '@' + $scope.server.muc);

    // Текущая высота окна, необходима для определения открыта или закрыта клавиатура
    $scope.currentWindowHeight;

    $scope.backToHistory = function() {
      supersonic.ui.modal.hide();
    };

    $scope.connection = {
      status: null,
      jid: $scope.user.username + '@' + $scope.server.host
    };

    $scope.chatroom = {
      status: null,
      name: $scope.tbtsHistoryItem.room,
      topic: $scope.tbtsHistoryItem.text
    };

    $scope.parseMessage = function(text) {
      try{
        var json = JSON.parse(text);
        var result = '';
        for (var i = 0, c = json.content; i < c.length; i++) {
          if (c[i].text != undefined) {
            var title = (c[i].text.title) ? '<div><b>' + c[i].text.title + '</b></div>' : '';
            var text = (c[i].text.text) ? '<div>' + c[i].text.text + '</div>' : '';
            result = result + title + text;
          }
          if (c[i].image != undefined) {
            var img = (c[i].image.image) ? '<img src="' + c[i].image.image + '" alt="">' : '';
            var referer = (c[i].image.referer) ? '<div><a href="' + c[i].image.referer + '" onclick="supersonic.app.openURL(this.href); return false">Источник</a></div>' : '';
            result = result + img + referer;
          }
        }
        return $sce.trustAsHtml(result);
      } catch(e) {
        return text;
      }
    };

    function playSound() {
      var sound = new Media('http://localhost/bb2.mp3');
      // http://ilee.co.uk/phonegap-plays-sound-on-mute/
      sound.play({ playAudioWhenScreenIsLocked : false });
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
        // Записываем историю localStorage
        localStorage.setItem(lsHistoryKeyName, JSON.stringify(tbtsHistory));
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
        if (/^Welcome to room/.test(body)) {
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
        // Отправляем presence, пользователь online
        connection.send($pres());
        // connection.send($pres().c('priority').t('5'));
        createChatRoom(connection, $scope.chatroom.name, $scope.user.username, $scope.chatroom.topic, onGroupchatMessage);
        window.cordova.plugins.NativeInput.show('');
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

    document.addEventListener('deviceready', function () {
      var params = {
        leftButton:{
          styleCSS: 'text: ;'
        },
        rightButton: {
          styleCSS: 'text: ;'
        },
        input:{
          placeHolder: 'Введите сообщение',
          type: 'normal',
          lines: 3
        }
      }
      window.cordova.plugins.NativeInput.setup(params);
      window.cordova.plugins.NativeInput.onButtonAction(function(side){
        if(side === 'left'){
          alert("left button tapped");
        }
        if(side === 'right'){
          window.cordova.plugins.NativeInput.getValue(function(value){
            if (value != '') {
              connection.send(
                $msg({
                  to: $scope.chatroom.name,
                  type: 'groupchat'
                }).c('body').t(value)
              );
              window.cordova.plugins.NativeInput.setValue('');
            }
          });
        }
      });
      window.cordova.plugins.NativeInput.onKeyboardAction(false, function(action){
        // TODO if (action == 'newline') {} - отправляем сообщение
      });

      // При изменение размера экрана определяем открыта или закрыта клавиатура
      $(window).resize(function(){
        $scope.currentWindowHeight = $scope.currentWindowHeight || $(window).height();
        if ($(window).height() > $scope.currentWindowHeight) {
          // Клавиатура закрыта
          $('.result').css('padding-bottom', '60px');
          $(window).scrollTop(document.body.clientHeight);
        } else {
          // Клавиатура открыта
          $('.result').css('padding-bottom', '0px');
          $(window).scrollTop(document.body.clientHeight);
        }
        $scope.currentWindowHeight = $(window).height();
      });
    }, false);
  });

