'use strict';

var dragdisSidebar = angular.module('dragdisSidebar', [
    'ngTagsInput',
    'ui.sortable',
    'ngTagsInput',
    'ngAnimate',
    'dragdisSidebarDirectives'
]);

// Prevent angulat from scrolling to top on bootstrap 
dragdisSidebar.value('$anchorScroll', angular.noop);

dragdisSidebar.config(['$httpProvider', '$compileProvider', '$sceDelegateProvider', function ($httpProvider, $compileProvider, $sceDelegateProvider) {

    // Whitelis local resource url as valid image locations
    $compileProvider.imgSrcSanitizationWhitelist(/^\s*(https?|ftp|mailto|chrome-extension):/);

    $sceDelegateProvider.resourceUrlWhitelist([
         'self',
         'chrome-extension://**'
    ]);
}]);
// Config for tags input
dragdisSidebar.config(['tagsInputConfigProvider', function (tagsInputConfigProvider) {
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

var dragdisSidebarDirectives = angular.module("dragdisSidebarDirectives", []);

dragdisSidebar.run(['$templateCache', function ($templateCache) {

    $.each(DRAGDIS.templates, function (key, value) {
        $templateCache.put(key, value);
    });

}]);
