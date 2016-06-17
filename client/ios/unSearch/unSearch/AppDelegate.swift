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
import ReachabilitySwift

class DataController: NSObject {
    let managedObjectContext = NSManagedObjectContext(concurrencyType: .PrivateQueueConcurrencyType)
    let group = dispatch_group_create()
    init(app: AppDelegate) {
        super.init()
        // This resource is the same name as your xcdatamodeld contained in your project.
        guard let modelURL = NSBundle.mainBundle().URLForResource("ProfileModel", withExtension: "momd") else {
            fatalError("Error loading model from bundle")
        }
        // The managed object model for the application. It is a fatal error for the application not to be able to find and load its model.
        guard let mom = NSManagedObjectModel(contentsOfURL: modelURL) else {
            fatalError("Error initializing mom from: \(modelURL)")
        }
        let psc = NSPersistentStoreCoordinator(managedObjectModel: mom)
        self.managedObjectContext.persistentStoreCoordinator = psc
        dispatch_group_async(group, dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_BACKGROUND, 0)) {
            let urls = NSFileManager.defaultManager().URLsForDirectory(.DocumentDirectory, inDomains: .UserDomainMask)
            let docURL = urls[urls.endIndex-1]
            /* The directory the application uses to store the Core Data store file.
            This code uses a file named "DataModel.sqlite" in the application's documents directory.
            */
            let storeURL = docURL.URLByAppendingPathComponent("ExpLeagueProfiles.sqlite")
            do {
                try psc.addPersistentStoreWithType(
                    NSSQLiteStoreType,
                    configuration: nil,
                    URL: storeURL,
                    options: [
                        NSMigratePersistentStoresAutomaticallyOption: true,
                        NSInferMappingModelAutomaticallyOption: true
                ])
            } catch {
                fatalError("Error migrating store: \(error)")
            }
            let profilesFetch = NSFetchRequest(entityName: "Profile")
            
            do {
                let profiles = try self.managedObjectContext.executeFetchRequest(profilesFetch) as! [ExpLeagueProfile]
                app.profiles = profiles
                if (profiles.count > 3) {
                    app.profiles = Array(profiles[0..<3])
                }
//                let localOrders = app.profiles![2].orders.mutableCopy() as! NSMutableOrderedSet
//                localOrders.removeAllObjects()
//                app.profiles![2].orders = localOrders.copy() as! NSOrderedSet
//                try self.managedObjectContext.save()
                if (profiles.count > 0) {
                    AppDelegate.instance.activate(profiles.filter({$0.active.boolValue}).first ?? profiles[0])
                }
            } catch {
                fatalError("Failed to fetch employees: \(error)")
            }
        }
    }
}

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
    @nonobjc static var instance: AppDelegate {
        return (UIApplication.sharedApplication().delegate as! AppDelegate)
    }
    
    static func versionName() -> String {
        let system = NSBundle.mainBundle().infoDictionary!
        return "\(system["CFBundleShortVersionString"]!) build \(system["CFBundleVersion"]!)"
    }
    
    var window: UIWindow?
    let xmppQueue = dispatch_queue_create("ExpLeague XMPP stream", nil)
    
    var tabs: TabsViewController!

    var split: UISplitViewController {
        return tabs.viewControllers![1] as! UISplitViewController
    }
    
    private var navigation: UINavigationController {
        get {
            return (split.viewControllers[0] as! UINavigationController)
        }
    }

    var orderView: OrderViewController?
    var expertsView: ExpertsOverviewController?
    var historyView: HistoryViewController?
    var dataController: DataController!
    var token: String?
    
    var activeProfile: ExpLeagueProfile?
    
    var profiles : [ExpLeagueProfile]?;
    
    
    func setupDefaultProfiles() {
        profiles = [];
        let randString = NSUUID().UUIDString
        let login = randString.substringToIndex(randString.startIndex.advancedBy(8))
        let passwd = NSUUID().UUIDString

        let production = ExpLeagueProfile("Production", domain: "expleague.com", login: login, passwd: passwd, port: 5222, context: dataController.managedObjectContext)
        if (profiles!.count == 0) {
            profiles!.append(production)
        }
        if (profiles!.count == 1) {
            profiles!.append(ExpLeagueProfile("Test", domain: "test.expleague.com", login: login, passwd: passwd, port: 5222, context: dataController.managedObjectContext))
        }
        if (profiles!.count == 2) {
            profiles!.append(ExpLeagueProfile("Local", domain: "localhost", login: login, passwd: passwd, port: 5222, context: dataController.managedObjectContext))
        }
        if (activeProfile == nil) {
            activate(production);
        }

        do {
            try dataController.managedObjectContext.save()
        }
        catch {
            activeProfile!.log("\(error)")
        }
    }
    
    var reachability: Reachability?
    var connectionErrorNotification: UILocalNotification?
    func activate(profile: ExpLeagueProfile) {
        activeProfile?.disconnect();
        print ("\(profile.domain): \(profile.orders.count)")
        profile.connect()
        activeProfile = profile
        do {
            reachability = try Reachability(hostname: profile.domain)
        }
        catch {
            activeProfile?.log("Unable to start reachability: \(error)")
        }
        QObject.notify(#selector(activate), self)
    }
    
    func prepareBackground(application: UIApplication) {
        if(activeProfile?.busy ?? false) {
            activeProfile?.log("Setting up local communication error notification because of \(activeProfile!.incoming) incoming and \(activeProfile!.outgoing) outgoing messages")
            connectionErrorNotification = Notifications.unableToCommunicate(activeProfile!.incoming, outgoing: activeProfile!.outgoing)
            application.setMinimumBackgroundFetchInterval(UIApplicationBackgroundFetchIntervalMinimum)
        }
        else {
            application.setMinimumBackgroundFetchInterval(UIApplicationBackgroundFetchIntervalNever)
        }
    }
}

extension AppDelegate: UIApplicationDelegate {
    func application(application: UIApplication, didFinishLaunchingWithOptions launchOptions: [NSObject: AnyObject]?) -> Bool {
        EVURLCache.LOGGING = false
        EVURLCache.MAX_FILE_SIZE = 26
        EVURLCache.MAX_CACHE_SIZE = 30
        EVURLCache.MAX_AGE = "\(3.0 * 365 * 24 * 60 * 60 * 1000)"
        EVURLCache.FORCE_LOWERCASE = true // is already the default. You also have to put all files int he PreCache using lowercase names
        EVURLCache.activate()
        
        dataController = DataController(app: self)
        
//        application.statusBarStyle = .LightContent
        application.registerForRemoteNotifications()
        let settings = UIUserNotificationSettings(forTypes: [.Alert, .Sound], categories: [])
        application.registerUserNotificationSettings(settings)
        application.idleTimerDisabled = false
        return true
    }
    
    func applicationDidEnterBackground(application: UIApplication) {
        prepareBackground(application)
        activeProfile?.suspend()
    }
    
    func applicationDidBecomeActive(application: UIApplication) {
        if (connectionErrorNotification != nil) {
            application.cancelLocalNotification(connectionErrorNotification!)
        }
        dispatch_group_wait(dataController.group, DISPATCH_TIME_FOREVER)
        activeProfile?.resume()
    }
    
    func application(application: UIApplication, performFetchWithCompletionHandler completionHandler: (UIBackgroundFetchResult) -> Void) {
        if (connectionErrorNotification != nil) {
            application.cancelLocalNotification(connectionErrorNotification!)
        }
        guard activeProfile != nil && application.applicationState != .Active else {
            completionHandler(.NewData)
            return
        }
        if (!activeProfile!.busy) {
            completionHandler(.NoData)
        }
        else if (reachability == nil || reachability!.isReachable()) {
            QObject.track(activeProfile!, #selector(ExpLeagueProfile.busyChanged)) {
                guard !self.activeProfile!.busy else {
                    return true
                }
                self.prepareBackground(application)
                self.activeProfile!.suspend()
                dispatch_async(dispatch_get_main_queue()) {
                    completionHandler(.NewData)
                }
                return false
            }
            activeProfile?.resume()
        }
        else {
            self.prepareBackground(application)
            dispatch_async(dispatch_get_main_queue()) {
                completionHandler(.Failed)
            }
        }
    }
    
    func application(application: UIApplication, didReceiveRemoteNotification userInfo: [NSObject : AnyObject], fetchCompletionHandler completionHandler: (UIBackgroundFetchResult) -> Void) {
        print("Received remote notification: \(userInfo)")
        if let messageId = userInfo["id"] as? String {
            activeProfile!.expect(messageId)
        }
        else if let aow = userInfo["aow"] as? String {
            activeProfile!.aow(aow)
        }
        self.application(application, performFetchWithCompletionHandler: completionHandler)
    }

    func application(application: UIApplication, didReceiveLocalNotification notification: UILocalNotification) {
        if let orderId = notification.userInfo?["order"] as? String, let order = activeProfile?.order(name: orderId) {
            historyView?.selected = order
            tabs.selectedIndex = 1
        }
    }
    
    func applicationWillTerminate(application: UIApplication) {
        activeProfile?.suspend()
    }
    
    func application(application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: NSData) {
        token = String(deviceToken)
    }
}

class TabsViewController: UITabBarController {
    override func viewDidLoad() {
        AppDelegate.instance.tabs = self
        for b in tabBar.items! {
            b.image = b.image?.imageWithRenderingMode(.AlwaysOriginal)
            b.selectedImage = b.selectedImage?.imageWithRenderingMode(.AlwaysOriginal)
            b.setTitleTextAttributes([NSForegroundColorAttributeName: Palette.CONTROL], forState: .Selected)
        }
    }
}
