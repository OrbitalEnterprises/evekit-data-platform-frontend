/* Proxy Services */
(function () {
    var servicesWS = angular.module('ekdpServicesWS', ['ekdpRemoteServices']);

    /**
     * Service for retrieving build and version info.
     */
    servicesWS.factory('ReleaseService', ['SwaggerService',
        function (SwaggerService) {
            return {
                'buildDate': function () {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Release.buildDate({}, {})
                                .then(function (result) {
                                    return result.status == 200 ? result.obj['buildDate'] : "";
                                })
                                .catch(function (error) {
                                    console.log(error);
                                    return "";
                                });
                        });
                },
                'version': function () {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Release.version({}, {})
                                .then(function (result) {
                                    return result.status == 200 ? result.obj['version'] : "";
                                })
                                .catch(function (error) {
                                    console.log(error);
                                    return "";
                                });
                        });
                }
            };
        }]);


    /**
     * Service for sharing authentication state among all controllers.
     */
    servicesWS.factory('AccountWSService', ['SwaggerService',
        function (SwaggerService) {
            return {
                'getUser': function () {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Account.getUser({}, {})
                                .then(function (result) {
                                    return result.obj;
                                }).catch(handleRemoteResponse);
                        });
                },
                'getUserLastSource': function (uid) {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Account.getUserLastSource({uid: uid || -1}, {})
                                .then(function (result) {
                                    return result.obj;
                                }).catch(handleRemoteResponse);
                        });
                }
            };
        }]);

    /**
     * Service to collect and periodically update user credentials.  Changes in credentials are broadcast as an event.
     */
    servicesWS.factory('UserCredentialsService', ['$rootScope', '$timeout', 'AccountWSService',
        function ($rootScope, $timeout, AccountWSService) {
            var userInfo = null;
            var userSource = null;
            var update = function (user, source) {
                $rootScope.$apply(function () {
                    if (user) $rootScope.$broadcast('UserInfoChange', userInfo);
                    if (source) $rootScope.$broadcast('UserSourceChange', userSource);
                });
            };
            var updateUserCredentials = function () {
                AccountWSService.getUser().then(function (val) {
                    if (val != null) {
                        userInfo = val;
                        update(true, false);
                    }
                }).catch(function () {
                    // Reset user on any error
                    userInfo = null;
                    update(true, false);
                });
                AccountWSService.getUserLastSource().then(function (val) {
                    if (val != null) {
                        userSource = val;
                        update(false, true);
                    }
                }).catch(function () {
                    // Reset source on any error
                    userSource = null;
                    update(false, true);
                });
                $timeout(updateUserCredentials, 1000 * 60 * 3);
            };
            updateUserCredentials();
            return {
                'getUser': function () {
                    return userInfo;
                },
                'getUserSource': function () {
                    return userSource;
                }
            };
        }]);


    /**
     * Service for managing EVE SSO tokens.
     */
    servicesWS.factory('TokenWSService', ['SwaggerService',
        function (SwaggerService) {
            return {
                'getTokenList': function () {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Token.getTokenList({}, {})
                                .then(function (result) {
                                    return result.obj;
                                }).catch(handleRemoteResponse);
                        });
                },
                'deleteToken': function (kid) {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Token.deleteToken({kid: kid}, {})
                                .then(function (result) {
                                    return true;
                                }).catch(handleRemoteResponse);
                        });
                },
                'reauthToken': function (kid) {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Token.reauthToken({kid: kid}, {})
                                .then(function (result) {
                                    return result.obj;
                                }).catch(handleRemoteResponse);
                        });
                },
                'getESIScopes': function () {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Token.getESIScopes({}, {})
                                .then(function (result) {
                                    return result.obj;
                                }).catch(handleRemoteResponse);
                        });
                },
                'createToken': function (scope) {
                    return SwaggerService.getSwagger()
                        .then(function (swg) {
                            return swg.Token.createToken({scope: scope}, {})
                                .then(function (result) {
                                    return result.obj;
                                }).catch(handleRemoteResponse);
                        });
                }
            };
        }]);

})();
