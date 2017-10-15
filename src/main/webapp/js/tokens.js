/* ESI Token List Page Module */
(function () {
    var ekdpTokenList = angular.module('ekdpTokenList', ['ngResource', 'ngSanitize', 'ekdpDialog', 'ekdpServicesWS']);

    ekdpTokenList.controller('EKDPTokenCtrl',
        ['$scope', '$location', '$window', '$filter', 'DialogService', 'AccountWSService', 'TokenWSService',
            function ($scope, $location, $window, $filter, DialogService, AccountWSService, TokenWSService) {
                $scope.loading = false;
                $scope.tokenList = [];
                $scope.userInfo = null;
                // State for create connection dialog
                $scope.currentScope = [];
                $scope.displayGroups = [];
                $scope.currentScopeSelection = {};
                // Reload token list.
                $scope.reloadList = function () {
                    $scope.loading = true;
                    AccountWSService.getUser().then(function (uval) {
                        if (uval == null || !uval.admin) {
                            $scope.$apply(function () {
                                // Not logged in, null out list which shouldn't be displayed anyway
                                $scope.tokenList = [];
                                $scope.userInfo = null;
                            });
                            return;
                        } else{
                            $scope.$apply(function() {
                               $scope.userInfo = uval;
                            });
                        }
                        // Valid user, look for token list
                        TokenWSService.getTokenList().then(function (result) {
                            $scope.$apply(function () {
                                $scope.loading = false;
                                $scope.tokenList = result;
                                // Compute displayable scope list.
                                for (var i = 0; i < $scope.tokenList.length; i++) {
                                    var scope_title = "Authorized Scopes:\n";
                                    var scopes = $scope.tokenList[i].scopes.split(' ');
                                    for (var j = 0; j < scopes.length; j++) {
                                        scope_title += scopes[j] + '\n';
                                    }
                                    // Remove the trailing newline.
                                    if (scope_title.length > 0) {
                                        scope_title = scope_title.substring(0, scope_title.length - 1);
                                    }
                                    $scope.tokenList[i].displayScopes = scope_title;
                                }
                            });
                        }).catch(function (err) {
                            $scope.$apply(function () {
                                $scope.loading = false;
                                DialogService.connectionErrorMessage('loading token list: ' + err.errorMessage, 20);
                            });
                        });
                    }).catch(function (err) {
                        $scope.$apply(function () {
                            // Assume not logged in and null out list
                            $scope.loading = false;
                            $scope.tokenList = [];
                        });
                    });
                };
                // Delete a token
                $scope.deleteToken = function (key) {
                    DialogService.yesNoDialog('warning', 'Really delete token?', false, function (answer) {
                        if (answer == 1) {
                            var info = DialogService.simpleInfoMessage('Deleting token...');
                            TokenWSService.deleteToken(key).then(function (result) {
                                $scope.$apply(function () {
                                    DialogService.removeMessage(info);
                                    $scope.reloadList();
                                });
                            }).catch(function (err) {
                                $scope.$apply(function () {
                                    DialogService.removeMessage(info);
                                    DialogService.connectionErrorMessage('removing token: ' + err.errorMessage, 20);
                                });
                            });
                        }
                    })
                };
                // Reauthorize a token
                $scope.reauthToken = function (key) {
                    DialogService.yesNoDialog('warning', 'Really re-authorize token?', false, function (answer) {
                        if (answer == 1) {
                            var info = DialogService.simpleInfoMessage('Re-authorizing token...');
                            TokenWSService.reauthToken(key).then(function (result) {
                                // On success, result is a redirect we need to manually insert.
                                // This is basically the same flow as creating a new connection.
                                $window.location.href = result['newLocation']
                            }).catch(function (err) {
                                $scope.$apply(function () {
                                    DialogService.removeMessage(info);
                                    DialogService.connectionErrorMessage('re-authorizing token: ' + err.errorMessage, 20);
                                });
                            });
                        }
                    })
                };
                // Retrieve available scopes
                $scope.getScopeList = function (cb) {
                    TokenWSService.getESIScopes().then(function (result) {
                        $scope.$apply(function () {
                            // Result is a map: scopeName -> scopeDefinition
                            // Transform to a list of objects before returning
                            scopeList = [];
                            for (var sc in result) {
                                if (result.hasOwnProperty(sc)) scopeList.push({value: sc, description: result[sc]});
                            }
                            $scope.currentScope = scopeList;
                            // Create sorted display groups to make the dialog easier to use
                            groups = {};
                            for (var i=0; i < scopeList.length; i++) {
                                scopeFamily = scopeList[i].value.substring(0, scopeList[i].value.indexOf('.'));
                                if (! (scopeFamily in groups)) groups[scopeFamily] = { 'name' : scopeFamily, 'members': [] };
                                groups[scopeFamily]['members'].push(scopeList[i]);
                            }
                            // Move to array and sort by length, largest first
                            groupList = [];
                            for (var i in groups) {
                                groups[i]['members'].sort(function(a,b) { return a.value.localeCompare(b.value); });
                                groupList.push(groups[i]);
                            }
                            groupList.sort(function(a,b) { return b['members'].length - a['members'].length; });
                            // Finally, organize into rows for display
                            maxRowLength = 2;
                            $scope.displayGroups = [];
                            nextGroup = [];
                            for (var i=0; i < groupList.length; i++) {
                                nextGroup.push(groupList[i]);
                                if (nextGroup.length == maxRowLength) {
                                    $scope.displayGroups.push(nextGroup);
                                    nextGroup = [];
                                }
                            }
                            if (nextGroup.length > 0) $scope.displayGroups.push(nextGroup);
                            // Invoke any provided callback
                            if (cb) cb();
                        });
                    }).catch(function (err) {
                        $scope.$apply(function () {
                            $('#createToken').modal('hide');
                            DialogService.connectionErrorMessage('retrieving scope list: ' + err.errorMessage, 20);
                        });
                    })
                };
                // Update scope list as needed
                $scope.updateDialogScope = function () {
                    var info = DialogService.simpleInfoMessage("Retrieving scope list...", 10);
                    if ($scope.currentScope.length > 0) {
                        // Already cached
                        DialogService.removeMessage(info);
                    } else {
                        // Retrieve latest from server
                        $scope.getScopeList(function () {
                            DialogService.removeMessage(info);
                        });
                    }
                };
                // Reset scopes
                $scope.clearAllScopes = function () {
                    $scope.currentScopeSelection = {};
                };
                // Select all scopes
                $scope.selectAllScopes = function () {
                    for (var i = 0; i < $scope.currentScope.length; i++) {
                        $scope.currentScopeSelection[$scope.currentScope[i].value] = true;
                    }
                };
                // Sanity check invalid form
                $scope.isFormInvalid = function () {
                    // The new token form is invalid only if no scopes have been selected
                    for (var key in $scope.currentScopeSelection) {
                        if ($scope.currentScopeSelection.hasOwnProperty())
                            return false;
                    }
                    return true;
                }
                // Create new token
                $scope.createToken = function () {
                    var scopeList = [];
                    for (var sc in $scope.currentScopeSelection) {
                        if ($scope.currentScopeSelection.hasOwnProperty(sc)) scopeList.push(sc);
                    }
                    TokenWSService.createToken(scopeList.join(' ')).then(function (result) {
                        // Result is a redirect to complete
                        $window.location.href = result['newLocation'];
                    }).catch(function (err) {
                        // Fail, show error message
                        $scope.$apply(function () {
                            DialogService.connectionErrorMessage('creating new token: ' + err.errorMessage, 20);
                        });
                    });
                };
                // Popup to create new connection
                $scope.create = function () {
                    $scope.currentScopeSelection = {};
                    $scope.updateDialogScope();
                    $('#createToken').modal({
                        backdrop: 'static',
                        keyboard: false
                    });
                };
                // Init
                $scope.reloadList();
            }]);


    // Validation functions.
    var isValidDate = function (str) {
        return /^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]$/.test(str);
    };

    var validateKeyExpiry = function (name) {
        if (!name || name.length == 0)
            return false;
        var trimmed = name.trim();
        if (trimmed == 'Never') return true;
        return isValidDate(trimmed);
    };

    var abstractValidator = function (name, popover, validator) {
        return function () {
            return {
                require: 'ngModel',
                restrict: 'A',
                link: function (scope, elm, attrs, ctrl) {
                    // if (!ngModel) return;
                    ctrl.$parsers.unshift(function (viewValue) {
                        var isValid = validator(viewValue);
                        var status = isValid ? 'hide' : 'show';
                        $('#' + popover).popover(status);
                        ctrl.$setValidity(name, isValid);
                        return viewValue;
                    });
                }
            };
        }
    };

    // Validators
    ekdpTokenList.directive('validatekeyexpiry', abstractValidator('validatekeyexpiry', 'expireInput', validateKeyExpiry));

    ekdpTokenList.controller('ConnectionsCtrl',
        ['$scope', '$location', '$window', '$filter', 'DialogService', 'AccountWSService', 'UserCredentialsService',
            function ($scope, $location, $window, $filter, DialogService, AccountWSService, UserCredentialsService) {
                $scope.sectionName = "Connection List";
                $scope.loading = false;
                $scope.connectionList = [];
                // State for create connection dialog
                $scope.serverType = 'latest';
                $scope.expiryDate = 'Never';
                $scope.currentScopeSelection = [];
                // Reload key list.
                $scope.reloadList = function () {
                    $scope.loading = true;
                    AccountWSService.getUser().then(function (uval) {
                        if (uval == null) {
                            $scope.$apply(function () {
                                // Not logged in, redirect
                                $location.path('/main');
                            });
                            return;
                        }
                        // Valid user, look for key list
                        AccountWSService.getAccessKeys().then(function (result) {
                            $scope.$apply(function () {
                                $scope.loading = false;
                                $scope.connectionList = result;
                                // Compute displayable scope list.
                                for (var i = 0; i < $scope.connectionList.length; i++) {
                                    var scope_title = "Authorized Scopes:\n";
                                    var scopes = $scope.connectionList[i].scopes.split(' ');
                                    for (var j = 0; j < scopes.length; j++) {
                                        scope_title += scopes[j] + '\n';
                                    }
                                    // Remove the trailing newline.
                                    if (scope_title.length > 0) {
                                        scope_title = scope_title.substring(0, scope_title.length - 1);
                                    }
                                    $scope.connectionList[i].displayScopes = scope_title;
                                }
                            });
                        }).catch(function (err) {
                            $scope.$apply(function () {
                                $scope.loading = false;
                                DialogService.connectionErrorMessage('loading connection list: ' + err.errorMessage, 20);
                            });
                        });
                    }).catch(function (err) {
                        $scope.$apply(function () {
                            // Assume not logged in and redirect
                            $location.path('/main');
                        });
                    });
                };
                // Delete a connection
                $scope.deleteConnection = function (key) {
                    DialogService.yesNoDialog('warning', 'Really delete connection?', false, function (answer) {
                        if (answer == 1) {
                            var info = DialogService.simpleInfoMessage('Deleting connection...');
                            AccountWSService.deleteAccessKey(key).then(function (result) {
                                $scope.$apply(function () {
                                    DialogService.removeMessage(info);
                                    $scope.reloadList();
                                });
                            }).catch(function (err) {
                                $scope.$apply(function () {
                                    DialogService.removeMessage(info);
                                    DialogService.connectionErrorMessage('removing connection: ' + err.errorMessage, 20);
                                });
                            });
                        }
                    })
                };
                // Retrieve scopes for a server type
                $scope.getScopeList = function (serverType, setter) {
                    AccountWSService.getScopes(serverType).then(function (result) {
                        $scope.$apply(function () {
                            // Result is a map: scopeName -> scopeDefinition
                            // Transform to a list of objects before returning
                            scopeList = [];
                            for (var sc in result) {
                                if (result.hasOwnProperty(sc)) scopeList.push({value: sc, description: result[sc]});
                            }
                            setter(scopeList);
                        });
                    }).catch(function (err) {
                        $scope.$apply(function () {
                            $('#createConnection').modal('hide');
                            DialogService.connectionErrorMessage('retrieving scope list: ' + err.errorMessage, 20);
                        });
                    })
                };
                // Popup to change expiry date for a connection
                $scope.changeConnectionExpiry = function (index) {
                    if (index < 0 || index >= $scope.connectionList.length) return;
                    $scope.modConnection = $scope.connectionList[index];
                    $scope.expiryDate = $scope.modConnection.expiry <= 0 ? 'Never' : $filter('date')($scope.modConnection.expiry, "yyyy-MM-dd");
                    $('#createConnection').modal({
                        backdrop: 'static',
                        keyboard: false
                    });
                };
                // Update scope list when server type changes
                $scope.updateDialogScope = function () {
                    var info = DialogService.simpleInfoMessage("Retrieving scope list...", 10);
                    if ($scope.serverType in $scope.scopeCache) {
                        // Cached, used the cached value
                        $scope.currentScope = $scope.scopeCache[$scope.serverType];
                        $scope.currentScopeSelection = {};
                        DialogService.removeMessage(info);
                    } else {
                        $scope.currentScope = [];
                        $scope.currentScopeSelection = {};
                        var scopeName = $scope.serverType;
                        $scope.getScopeList($scope.serverType, function (newScopeList) {
                            DialogService.removeMessage(info);
                            $scope.currentScope = $scope.scopeCache[scopeName] = newScopeList;
                        });
                    }
                };
                // Reset scopes
                $scope.clearAllScopes = function () {
                    $scope.currentScopeSelection = {};
                };
                // Select all scopes
                $scope.selectAllScopes = function () {
                    for (var i = 0; i < $scope.currentScope.length; i++) {
                        $scope.currentScopeSelection[$scope.currentScope[i].value] = true;
                    }
                };
                // Sanity chaeck invalid form
                $scope.isFormInvalid = function () {
                    if ($scope.expiryDate == 'Never') return true;
                    return isValidDate($scope.expiryDate.trim());
                }
                // Save new or changed connection
                $scope.saveConnection = function () {
                    if ($scope.modConnection != null) {
                        // Modifying existing connection
                        var expiry = -1;
                        $scope.expiryDate = $scope.expiryDate.trim();
                        if ($scope.expiryDate != 'Never') expiry = (new Date($scope.expiryDate)).getTime();
                        var changedConn = {
                            "kid": $scope.modConnection.kid,
                            "expiry": expiry
                        }
                        AccountWSService.saveAccessKey(changedConn).then(function (result) {
                            // Success, refresh list
                            $scope.reloadList();
                        }).catch(function (err) {
                            // Fail, show error message
                            $scope.$apply(function () {
                                DialogService.connectionErrorMessage('changing connection expiry: ' + err.errorMessage, 20);
                            });
                        });
                    } else {
                        // Creating new connection
                        var expiry = -1;
                        $scope.expiryDate = $scope.expiryDate.trim();
                        if ($scope.expiryDate != 'Never') expiry = (new Date($scope.expiryDate)).getTime();
                        var scopeList = [];
                        for (var sc in $scope.currentScopeSelection) {
                            if ($scope.currentScopeSelection.hasOwnProperty(sc)) scopeList.push(sc);
                        }
                        var newConn = {
                            "kid": -1,
                            "expiry": expiry,
                            "serverType": $scope.serverType,
                            "scopes": scopeList.join(' ')
                        };
                        AccountWSService.saveAccessKey(newConn).then(function (result) {
                            // Result is a redirect to complete
                            $window.location.href = result['newLocation'];
                        }).catch(function (err) {
                            // Fail, show error message
                            $scope.$apply(function () {
                                DialogService.connectionErrorMessage('saving new connection: ' + err.errorMessage, 20);
                            });
                        });
                    }
                };
                // Popup to create new connection
                $scope.create = function () {
                    $scope.modConnection = null;
                    $scope.scopeCache = {};
                    $scope.serverType = 'latest';
                    $scope.expiryDate = 'Never';
                    $scope.currentScopeSelection = {};
                    $scope.currentScope = [];
                    $scope.updateDialogScope();
                    $('#createConnection').modal({
                        backdrop: 'static',
                        keyboard: false
                    });
                };
                // Init
                $('#expireInput').datepicker({dateFormat: "yy-mm-dd"});
                $('#changeExpireInput').datepicker({dateFormat: "yy-mm-dd"});
                $scope.reloadList();
            }]);


})();
