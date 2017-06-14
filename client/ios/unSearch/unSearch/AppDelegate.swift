//
//  AppDelegate.swift
//  unSearch
//
//  Created by Игорь Кураленок on 09.01.16.
//  Copyright (c) 2016 Experts League Inc. All rights reserved.
//


import UIKit
import XMPPFramework
import CoreData
import MMMarkdown
import FBSDKCoreKit
import Intents

import unSearchCore

class Palette {
    static let CONTROL = UIColor(red: 17/256.0, green: 138/256.0, blue: 222/256.0, alpha: 1.0)
    static let CONTROL_BACKGROUND = UIColor(red: 249/256.0, green: 249/256.0, blue: 249/256.0, alpha: 1.0)
    static let CHAT_BACKGROUND = UIColor(red: 230/256.0, green: 233/256.0, blue: 234/256.0, alpha: 1.0)
    static let OK = UIColor(red: 102/256.0, green: 182/256.0, blue: 15/256.0, alpha: 1.0)
    static let ERROR = UIColor(red: 174/256.0, green: 53/256.0, blue: 53/256.0, alpha: 1.0)
    static let COMMENT = UIColor(red: 63/256.0, green: 84/256.0, blue: 130/256.0, alpha: 1.0)
    static let BORDER = UIColor(red: 202/256.0, green: 210/256.0, blue: 227/256.0, alpha: 1.0)
    static let CORNER_RADIUS = CGFloat(8)
}

@UIApplicationMain
class AppDelegate: UIResponder {
    static let deviceId = UInt64(abs(UIDevice.current.identifierForVendor!.uuidString.hashValue))
    static let GOOGLE_API_KEY = "AIzaSyA83KOger1DEkxp3h0ItejyGBlEUuE7Bkc"
    @nonobjc static var instance: AppDelegate {
        return (UIApplication.shared.delegate as! AppDelegate)
    }
    
    static func versionName() -> String {
        let system = Bundle.main.infoDictionary!
        return "\(system["CFBundleShortVersionString"]!) build \(system["CFBundleVersion"]!)"
    }
    
    var window: UIWindow?
    
    var tabs: TabsViewController?

    var split: UISplitViewController {
        return tabs!.viewControllers![1] as! UISplitViewController
    }
    
    fileprivate var navigation: UINavigationController {
        get {
            return (split.viewControllers[0] as! UINavigationController)
        }
    }

    var orderView: OrderViewController?
    var expertsView: ExpertsOverviewController?
    var historyView: HistoryViewController?
    let uploader = AttachmentsUploader()
    
    var connectionErrorNotification: UILocalNotification?
    
    func prepareBackground(_ application: UIApplication) {
        DataController.shared().xmppQueue.async {
            if(DataController.shared().activeProfile?.busy ?? false) {
                application.setMinimumBackgroundFetchInterval(UIApplicationBackgroundFetchIntervalMinimum)
            }
            else {
                application.setMinimumBackgroundFetchInterval(UIApplicationBackgroundFetchIntervalNever)
            }
        }
    }
    
    func start() {
        let storyboard = UIStoryboard(name: "Main", bundle: nil)
        tabs = storyboard.instantiateViewController(withIdentifier: "tabs") as? TabsViewController
        self.window?.rootViewController = tabs
        GMSServices.provideAPIKey(AppDelegate.GOOGLE_API_KEY)
        GMSPlacesClient.provideAPIKey(AppDelegate.GOOGLE_API_KEY)
        
        if #available(iOS 10.0, *) {
            INPreferences.requestSiriAuthorization() { (status: INSiriAuthorizationStatus) -> Void in
                print(status)
            }
            INVocabulary.shared().setVocabularyStrings(["эксперт", "экспертов", "эксперта", "экспертам", "эксперту"], of: .contactGroupName)
        }
        let application = UIApplication.shared
        application.registerForRemoteNotifications()
        let settings = UIUserNotificationSettings(types: [.alert, .sound, .badge], categories: [])
        application.registerUserNotificationSettings(settings)
        application.isIdleTimerDisabled = false
        DataController.shared().start()
    }
    
    func onProfileChanged() {
        let application = UIApplication.shared
        application.applicationIconBadgeNumber = Int(ExpLeagueProfile.active.unread)
        tabs?.viewControllers?[1].tabBarItem.badgeValue = ExpLeagueProfile.active.unread > 0 ? "\(ExpLeagueProfile.active.unread)" : nil;
        QObject.track(ExpLeagueProfile.active, #selector(ExpLeagueProfile.unreadChanged), tracker: {
            let unread = Int(ExpLeagueProfile.active.unread)
            DispatchQueue.main.async() {
                application.applicationIconBadgeNumber = unread
                self.tabs?.viewControllers?[1].tabBarItem.badgeValue = unread > 0 ? "\(unread)" : nil;
            }
            return true
        })
    }
}

extension AppDelegate: UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplicationLaunchOptionsKey: Any]?) -> Bool {
        EVURLCache.LOGGING = false
        EVURLCache.MAX_FILE_SIZE = 26
        EVURLCache.MAX_CACHE_SIZE = 30
        EVURLCache.MAX_AGE = "\(3.0 * 365 * 24 * 60 * 60 * 1000)"
        EVURLCache.FORCE_LOWERCASE = true // is already the default. You also have to put all files int he PreCache using lowercase names
        EVURLCache.activate()
        
        let controller = DataController.shared()
        controller.version = "unSearch \(AppDelegate.versionName()) @iOS \(ProcessInfo.processInfo.operatingSystemVersionString)"
        QObject.connect(controller, signal: #selector(DataController.profileChanged), receiver: self, slot: #selector(self.onProfileChanged))
        NSSetUncaughtExceptionHandler({(e: NSException) -> () in
            print("Stack trace: \(e.callStackSymbols)")
        })
        window = UIWindow(frame: UIScreen.main.bounds)
        let onboard = UIStoryboard(name: "Onboard", bundle: nil)
        if (!controller.initialized()) {
            let pageControl = UIPageControl.appearance()
            pageControl.pageIndicatorTintColor = Palette.CHAT_BACKGROUND
            pageControl.backgroundColor = UIColor.white
            pageControl.currentPageIndicatorTintColor = Palette.CONTROL
            let page1 = (onboard.instantiateViewController(withIdentifier: "onboardPage") as! OnboardPageViewController)
                .build(text: "Нет времени на поиск? Необходимо разобраться в сложной теме? Оперативно решить проблему?", image: UIImage(named: "onBoarding_img1")!)
            let page2 = (onboard.instantiateViewController(withIdentifier: "onboardPage") as! OnboardPageViewController)
                .build(text: "Не тратьте время и нервы — для этого есть unSearch! Просто поручите поиск нам!", image: UIImage(named: "onBoarding_img2")!)
            let page3 = (onboard.instantiateViewController(withIdentifier: "onboardPage") as! OnboardPageViewController)
                .build(text: "Изучим сложный вопрос, обзвоним кого нужно, сравним и выберем лучшее, проверим наличие.", image: UIImage(named: "onBoarding_img3")!)
            let page4 = (onboard.instantiateViewController(withIdentifier: "onboardPage") as! OnboardPageViewController)
                .build(text: "Получите готовое решение: пошаговая инструкция, проверенная информация, отзывы и рейтинги.", image: UIImage(named: "onBoarding_img4")!)
            let lastPage = (onboard.instantiateViewController(withIdentifier: "onboardPage") as! OnboardPageViewController)
                .build(text: "Мы помогли вам?\nУгостите эксперта чашечкой кофе!", image: UIImage(named: "onBoarding_img5")!, final: true) {
                    controller.setupDefaultProfiles(UIDevice.current.identifierForVendor!.uuidString.hashValue)
                    self.start()
                }
    
            let pages = [page1, page2, page3, page4, lastPage]
            let onboardingVC = OnboardViewController(pages: pages)
            for i in 0...pages.count - 1 {
                let page = pages[i]
                if (page.callback == nil) {
                    page.callback = {
                        onboardingVC.index = i + 1
                        onboardingVC.setViewControllers([pages[i+1]], direction: .forward, animated: true, completion: nil)
                    }
                }
            }
            window?.rootViewController = onboardingVC
        }
        else {
            start()
        }
        window?.makeKeyAndVisible()
        
        return true
    }
    
    func applicationDidEnterBackground(_ application: UIApplication) {
        prepareBackground(application)
        DataController.shared().suspend()
    }
    
    func applicationDidBecomeActive(_ application: UIApplication) {
        FBSDKAppEvents.activateApp()
        if (connectionErrorNotification != nil) {
            application.cancelLocalNotification(connectionErrorNotification!)
        }
        DataController.shared().resume()
    }
    
    func application(_ application: UIApplication, performFetchWithCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        if (connectionErrorNotification != nil) {
            application.cancelLocalNotification(connectionErrorNotification!)
        }
        guard DataController.shared().activeProfile != nil && application.applicationState != .active else {
            completionHandler(.newData)
            return
        }
        if (!ExpLeagueProfile.active.busy) {
            completionHandler(.noData)
        }
        else if (DataController.shared().reachability?.isReachable ?? true) {
            QObject.track(ExpLeagueProfile.active, #selector(ExpLeagueProfile.busyChanged)) {
                guard !ExpLeagueProfile.active.busy else {
                    return true
                }
                self.prepareBackground(application)
                ExpLeagueProfile.active.suspend()
                usleep(10000000) // let notifications pass
                DispatchQueue.main.async {
                    completionHandler(.newData)
                }
                return false
            }
            ExpLeagueProfile.active.resume()
        }
        else {
            self.prepareBackground(application)
            DispatchQueue.main.async {
                completionHandler(.failed)
            }
        }
    }
    
    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any], fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        guard DataController.shared().activeProfile != nil else {
            completionHandler(.newData)
            return
        }
        print("Received remote notification: \(userInfo)")
        if let messageId = userInfo["id"] as? String {
            ExpLeagueProfile.active.expect(messageId)
        }
        else if let aowId = userInfo["aow"] as? String {
            ExpLeagueProfile.active.aow(aowId, title: userInfo["title"] as? String)
        }
        self.application(application, performFetchWithCompletionHandler: completionHandler)
    }

    func application(_ application: UIApplication, didReceive notification: UILocalNotification) {
        if let orderId = notification.userInfo?["order"] as? String, let order = ExpLeagueProfile.active.order(name: orderId) {
            ExpLeagueProfile.active.selectedOrder = order
            tabs?.selectedIndex = 1
        }
    }
    
    func applicationWillTerminate(_ application: UIApplication) {
        ExpLeagueProfile.active.suspend()
    }
    
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let chars = (deviceToken as NSData).bytes.assumingMemoryBound(to: Swift.CChar)
        var token = ""
        
        for i in 0..<deviceToken.count {
            token += String(format: "%02.2hhx", arguments: [chars[i]])
        }
        DataController.shared().token = token
        print(token)
    }
}

class TabsViewController: UITabBarController {
    override func viewDidLoad() {
        AppDelegate.instance.tabs = self
        for b in tabBar.items! {
            b.image = b.image?.withRenderingMode(.alwaysOriginal)
            b.selectedImage = b.selectedImage?.withRenderingMode(.alwaysOriginal)
            b.setTitleTextAttributes([NSForegroundColorAttributeName: Palette.CONTROL], for: .selected)
        }
    }
}
