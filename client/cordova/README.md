# Локальный запуск клиентского приложения

## Простой способ (если хочется быстро посмотреть на самую свежую версию клиента)

[Открываем ссылку](https://share.appgyver.com/?id=79223&hash=5353e5a22cbd2be55bdafcada9edc2ec2de94c9868e2a79195cc10e3d31ea085)

Откроется страница с QR-кодом

Этот код нужно отсканировать в приложение AppGyver Scanner:
* Для iPhone - https://itunes.apple.com/fi/app/appgyver-scanner/id575076515
* Для Android - https://play.google.com/store/apps/details?id=com.appgyver.freshandroid

## Сложный способ (если нужно править исходники)

AppGyver это такая оболочка над несколькими фреймворками: Apache Cordova и ionicframework
позволяющая очень удобно разрабатывать, деплоить и собирать приложения.

Для того, чтобы запускать приложения AppGyver необходимо зарегистрироваться на сайте:
http://www.appgyver.com/steroids_sign_up

Далее необходимо пройти визард, который поможет установить все консольные инструменты для вашей платформы:
https://academy.appgyver.com/installwizard/steps#/home

Перейти в директорию `tbts/client/cordova`

Выполнить комманду `steroids update`

Выполнить комманду `steroids connect`

В случае успеха: 

Откроется браузер с QR-кодом

Этот код нужно отсканировать в приложение AppGyver Scanner (телефон и компьютер должны быть в одной wifi-сети):
* Для iPhone - https://itunes.apple.com/fi/app/appgyver-scanner/id575076515
* Для Android - https://play.google.com/store/apps/details?id=com.appgyver.freshandroid

Теперь если код проекта редактировать и сохранять, Scanner будет автоматически перезагружать приложение с обновленным кодом.

В случае неуспеха:

На этапе `steroids connect` - может выпадать ошибка вида:
```
npm WARN grunt-steroids@1.2.0 requires a peer of grunt-contrib-clean@0.6.0 but none was installed.
npm WARN grunt-steroids@1.2.0 requires a peer of grunt-contrib-coffee@0.12.0 but none was installed.
npm WARN grunt-steroids@1.2.0 requires a peer of grunt-contrib-concat@0.5.0 but none was installed.
npm WARN grunt-steroids@1.2.0 requires a peer of grunt-contrib-copy@0.7.0 but none was installed.
npm WARN grunt-steroids@1.2.0 requires a peer of grunt-contrib-sass@0.8.1 but none was installed.
npm WARN grunt-steroids@1.2.0 requires a peer of grunt-extend-config@0.9.2 but none was installed.
```

Помогает выполнить поочередно команды:
`npm install`

`npm install grunt`

`npm install grunt-contrib-clean grunt-contrib-coffee grunt-contrib-concat grunt-contrib-copy grunt-contrib-sass grunt-extend-config`

После этого повторить команду `steroids connect`


<!--- https://github.com/adam-p/markdown-here/wiki/Markdown-Cheatsheet -->
