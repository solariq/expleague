# Локальный запуск прототипа

## Простой способ (если хочется быстро посмотреть на самую свежую версию прототипа)

[Открываем ссылку](https://share.appgyver.com/?id=83596&hash=902d0f3e3519bb7b190c76c8171f77f64323deafbcbec1011b75981dcdea9840)

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

Перейти в директорию `tbts/prototype/cordova`

Выполнить комманду `steroids update`

Выполнить комманду `steroids connect`

Откроется браузер с QR-кодом

Этот код нужно отсканировать в приложение AppGyver Scanner (телефон и компьютер должны быть в одной wifi-сети):
* Для iPhone - https://itunes.apple.com/fi/app/appgyver-scanner/id575076515
* Для Android - https://play.google.com/store/apps/details?id=com.appgyver.freshandroid

Теперь если код проекта редактировать и сохранять, Scanner будет автоматически перезагружать приложение с обновленным кодом.

<!--- https://github.com/adam-p/markdown-here/wiki/Markdown-Cheatsheet -->