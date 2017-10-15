/* EveKit Data Platform Module */
(function(){
    var ekdp = angular.module('ekdp', [
        'ngResource',
        'ekdpDialog',
        'ekdpRemoteServices',
        'ekdpServicesWS',
        'ekdpTokenList'
    ]);

    // Capture any authorization errors before we process the rest of the window location
    var searchParams = window.location.search;
    var auth_error = null;
    if (searchParams && searchParams.length > 1) {
        var vals = searchParams.substr(1).split('&');
        for (var i = 0; i < vals.length; i++) {
            var next = vals[i].split('=');
            if (next[0] == 'auth_error') {
                auth_error = decodeURIComponent(next[1].replace(/\+/g,' '));
                break;
            }
        }
    }

    /* Add scrolling directive to handle hash scrolling. */
    /* nicked from here: http://stackoverflow.com/questions/14712223/how-to-handle-anchor-hash-linking-in-angularjs */
    ekdp.directive('scrollTo', function ($location, $anchorScroll) {
        return function(scope, element, attrs) {

            element.bind('click', function(event) {
                event.stopPropagation();
                var off = scope.$on('$locationChangeStart', function(ev) {
                    off();
                    ev.preventDefault();
                });
                var location = attrs.scrollTo;
                $location.hash(location);
                $anchorScroll();
            });
        }});

    /* Inband controller for setting the version for the page */
    ekdp.controller('EKDPVersionCtrl', ['$scope', 'ReleaseService',
        function($scope, ReleaseService) {
            ReleaseService.buildDate().then(function (val) {
                $scope.$apply(function() {
                    $scope.ekdpBuildDate = val;
                });
            });
            ReleaseService.version().then(function (val) {
                $scope.$apply(function() {
                    $scope.ekdpVersion = val;
                });
            });
        }]);

    /* Inband controller for setting authentication status and other container menu settings. */
    ekdp.controller('EKDPAuthCtrl', ['$scope', '$timeout', 'UserCredentialsService', 'AccountWSService', 'DialogService',
        function($scope, $timeout, UserCredentialsService, AccountWSService, DialogService) {
            // Set up user credential management
            $scope.userInfo = UserCredentialsService.getUser();
            $scope.userSource = UserCredentialsService.getUserSource();
            $scope.$on('UserInfoChange', function(event, ui) { $scope.userInfo = ui; });
            $scope.$on('UserSourceChange', function(event, us) { $scope.userSource = us; });
            // Check for authentication error and post an appropriate dialog
            if (auth_error !== null) {
                $timeout(function () { DialogService.simpleErrorMessage(auth_error, 20) }, 1);
            }
        }]);

    /* Inband controller for controlling the top of window documentation area */
    ekdp.controller('MainCtrl', ['$scope', 'UserCredentialsService',
        function($scope, UserCredentialsService) {
            $scope.userInfo = UserCredentialsService.getUser();
            $scope.$on('UserInfoChange', function(event, ui) { $scope.userInfo = ui; });
        }]);

})();
