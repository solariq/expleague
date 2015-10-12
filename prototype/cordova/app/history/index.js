angular.module('history', [
  // Declare any module-specific AngularJS dependencies here
  'common',
  'ngSanitize'
]).config([
  '$compileProvider',
  function($compileProvider) {
    // Добавляем схему comgooglemaps к списку стандартных разрешенных схем
    $compileProvider.aHrefSanitizationWhitelist(/^\s*(https?|ftp|mailto|tel|file|comgooglemaps):/);
  }
]);
