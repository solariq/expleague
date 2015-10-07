knuggetSidebarDirectives.factory('dataService', ['$q', '$rootScope', '$timeout', function ($q, $rootScope, $timeout) {

    var referralSysValues = {
        InvitationUrl: "...",
        FoldersEarnedByRefCount: 0,
        PossibleFoldersByRefCount: 20,
        SubscriptionActive: false,
        SubscriptionSoonToExpire: false,

        UsedFoldersCount: 0,
        FreeFoldersCount: 5,
        TotalAvailableFolders: 4
    };

    return {
        updateScroll: null,

        invitationUrl: referralSysValues["InvitationUrl"],
        foldersEarnedByRefCount: referralSysValues["FoldersEarnedByRefCount"],
        possibleFoldersByRefCount: referralSysValues["PossibleFoldersByRefCount"],
        subscriptionActive: referralSysValues["SubscriptionActive"],
        subscriptionSoonToExpire: referralSysValues["SubscriptionSoonToExpire"],

        usedFoldersCount: referralSysValues["UsedFoldersCount"],
        freeFoldersCount: referralSysValues["FreeFoldersCount"],
        totalAvailableFolders: referralSysValues["TotalAvailableFolders"], // default value

		getReferralSystemValue: function (key) {
            return referralSysValues.hasOwnProperty(key) ? referralSysValues[key] : null;
        },
        updateReferralSystemValues: function (response) {
            if (response) {
                $.each(response, function (key, value) {
                    //console.log(key, value);
                    referralSysValues[key] = value;
                });
            }
        },
        updateFoldersCounters: function (operation, total) {

            //console.log("fire" + operation + " " + total);

            if (typeof operation === "string") {
                switch (operation) {
                    case "plus":
                        referralSysValues["UsedFoldersCount"]++;
                        break;
                    case "minus":
                        referralSysValues["UsedFoldersCount"] = referralSysValues["UsedFoldersCount"] - total;
                        break;
                }
            }

            $rootScope.$$childTail.folders.usedFoldersCount = referralSysValues["UsedFoldersCount"];
            $rootScope.$$childTail.folders.freeFoldersCount = referralSysValues["FreeFoldersCount"];

            //how many folders possible to add to sidebar
            referralSysValues["TotalAvailableFolders"] = referralSysValues["FreeFoldersCount"] - referralSysValues["UsedFoldersCount"];
            if (referralSysValues["TotalAvailableFolders"] < 0) {
                referralSysValues["TotalAvailableFolders"] = 0;
            }
            $rootScope.$$childTail.folders.totalAvailableFolders = referralSysValues["TotalAvailableFolders"];


            var barWidthforMenu = Math.floor((referralSysValues["UsedFoldersCount"] / referralSysValues["FreeFoldersCount"]) * 100);
            if (barWidthforMenu === NaN) {
                barWidthforMenu = 0;
            } else if (barWidthforMenu > 100) {
                barWidthforMenu = 100;
            }
            $rootScope.$$childTail.folders.barWidthForMenu = barWidthforMenu;

            if (!$rootScope.$$childTail.$$phase) {
                $rootScope.$$childTail.$apply();
            }
        }
    };
}]);