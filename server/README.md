# tbts
Как установить сервер. 

"""
Качаем репозиторий, если его нет:
'git clone --recurse-submodules git@github.com:solariq/tbts.git'
Качаем так потому, что у меня идея почему то не подхватила commons.
После этого не забываем перевести commons на master. Для этого заходим в папку server/commons и там запускаем "git checkout master".
"""

"""
Ставим java 8
http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
Если всё будет сделано верно, то из консоли это будет выглядеть примерно так:
11:43:30 solar@solar-osx:~$ java -version
java version "1.8.0_60"
Java(TM) SE Runtime Environment (build 1.8.0_60-b27)
Java HotSpot(TM) 64-Bit Server VM (build 25.60-b23, mixed mode)
"""

"""
Ставим MySQL c сайта Oracle:
http://dev.mysql.com/downloads/file.php?id=458460
К сожалению они не дают загружать без регистрации, так что прийдется на это потратить несколько минут.
"""

Я исхожу из того, что вы скачали репозиторий, и у вас стоит java 8 и MySQL с сайта Oracle.

Вы находитесь в корне проекта: например этот файл тогда называется server/README.md.

1. Создаем базу данных, необходимую для работы сервера.
Сделать это можно несколькими способами. Самый простой -- запустить скрипт
  server/scripts/db-create-mysql.sh tigase <tigase_password> tigase root <root_password> localhost
штуки в ёлках надо заменить на ваши значения.

tigase_password -- придумываем из головы и запоминаем ее туда.
root_password появляется при инсталляции mysql. Если у вас ничего не спросили, его можно установить выдумав из головы вот так:
  а. запускаем из коммандной строки:
    mysql -u root
  b. в появившейся консоли вводим SET PASSWORD = <root_password>;

Для пользователей Windows: стоит поставить себе эмуляцию posix консоли. Мне известна такая https://www.cygwin.com/, но понятно,
что она не единственная. К сожалению подробностей установки mysql я пока не знаю, так что дальше бултыхайтесь сами, или уже
поставьте нормальную OS.

Для пользователей Mac: mysql ставится по умолчанию в /usr/local/mysql/. Соответственно запускать надо /usr/local/mysql/bin/mysql.
По умолчанию сервер _не_ стартует, его надо запустить из системных настроек, где появляется соответствующий пункт.


2. Дать серверу ссылку на базу данных:

В результате работы скрипта из предыдущего пункта будет выведено что-то такое:
.....
--user-db-uri=jdbc:mysql://localhost:3306/tigase?user=tigase&password=<tigase_password>&useUnicode=true&characterEncoding=UTF-8&autoCreateUser=true
.....
Эту строку надо вставить в файл
  server/etc/init.properties
вместо аналогичной, в которой мой пароль.


3. Собрать проект и запустить севрер

Для этого проще всего поставить JDK:
http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
Скачать IntelliJ IDEA Community Edition
https://www.jetbrains.com/idea/download/
и ее поствить. При запуске необходимо "открыть проект" и указать корневую директорию репозитория. В результате на верхнем тулбаре через
некоторое время появится пункт сервер, рядом с которым есть кнопочка запуска. Ее надо нажать, все остальное оно сделает само.

4. Сервер запущен. Чтобы проверить, что все хорошо, скачайте клиент Jabber'а или воспользуйтесь уже существующим и зарегистрируйте пользователя на сервере localhost.

HINT!!!
Для дебага очень полезно иметь продвинутый Jabber клиент:
http://psi-im.org/download/
В нем есть отличная опция: Tools/XML Console (не забудте нажать кнопку Enable). Она позволяет видеть весь XMPP траффик между сервером и клиентом.

