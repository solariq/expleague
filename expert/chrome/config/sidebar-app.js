'use strict';

var knuggetSidebar = angular.module('knuggetSidebar', [
    'ngTagsInput',
    'ui.sortable',
    'ngTagsInput',
    'ngAnimate',
    'ngImgCrop',
    'ngFileUpload',
    //'yaMap',
    //'ymaps',
    'uiGmapgoogle-maps',
    'knuggetSidebarDirectives'
]);

// Prevent angulat from scrolling to top on bootstrap 
knuggetSidebar.value('$anchorScroll', angular.noop);

knuggetSidebar.config(['uiGmapGoogleMapApiProvider', function(uiGmapGoogleMapApiProvider) {
    uiGmapGoogleMapApiProvider.configure({
        //    key: 'your api key',
        v: '3.20', //defaults to latest 3.X anyhow
        libraries: 'weather,geometry,visualization'
    });
}]);

knuggetSidebar.config(['$httpProvider', '$compileProvider', '$sceDelegateProvider', function ($httpProvider, $compileProvider, $sceDelegateProvider) {

    // Whitelis local resource url as valid image locations
    //$compileProvider.imgSrcSanitizationWhitelist(/^\s*(https?|ftp|mailto|chrome-extension):/);
    $compileProvider.imgSrcSanitizationWhitelist(/^\s*((https?|ftp|mailto|chrome-extension):|data:image\/)/);
    $sceDelegateProvider.resourceUrlWhitelist([
         'self',
         'chrome-extension://**'
    ]);
}]);
// Config for tags input
knuggetSidebar.config(['tagsInputConfigProvider', function (tagsInputConfigProvider) {
    tagsInputConfigProvider
    .setDefaults('tagsInput', {
        placeholder: false,
        removeTagSymbol: false,
        addOnEnter: true,
        addOnBlur: true,
        addFromAutocompleteOnly: false,
        displayProperty: "tag",
        minLength: 2,
        maxLength: 26
    })
    .setActiveInterpolation('tagsInput', {});
}]);

var knuggetSidebarDirectives = angular.module("knuggetSidebarDirectives", []);

knuggetSidebar.run(['$templateCache', function ($templateCache) {

    $.each(KNUGGET.templates, function (key, value) {
        $templateCache.put(key, value);
    });

}]);
