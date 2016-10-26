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
import FBSDKCoreKit

class DataController: NSObject {
    let managedObjectContext = NSManagedObjectContext(concurrencyType: .privateQueueConcurrencyType)
    let group = DispatchGroup()
    init(app: AppDelegate) {
        super.init()
        // This resource is the same name as your xcdatamodeld contained in your project.
        guard let modelURL = Bundle.main.url(forResource: "ProfileModel", withExtension: "momd") else {
            fatalError("Error loading model from bundle")
        }
        // The managed object model for the application. It is a fatal error for the application not to be able to find and load its model.
        guard let mom = NSManagedObjectModel(contentsOf: modelURL) else {
            fatalError("Error initializing mom from: \(modelURL)")
        }
        let psc = NSPersistentStoreCoordinator(managedObjectModel: mom)
        self.managedObjectContext.persistentStoreCoordinator = psc
        let urls = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        let docURL = urls[urls.endIndex-1]
        /* The directory the application uses to store the Core Data store file.
         This code uses a file named "DataModel.sqlite" in the application's documents directory.
        */
        let storeURL = docURL.appendingPathComponent("ExpLeagueProfiles.sqlite")
        do {
            try psc.addPersistentStore(
                ofType: NSSQLiteStoreType,
                configurationName: nil,
                at: storeURL,
                options: [
                    NSMigratePersistentStoresAutomaticallyOption: true,
                    NSInferMappingModelAutomaticallyOption: true
            ])
        } catch {
            fatalError("Error migrating store: \(error)")
        }
        let profilesFetch = NSFetchRequest<ExpLeagueProfile>(entityName: "Profile")
            
        do {
            let profiles = try self.managedObjectContext.fetch(profilesFetch)
            app.profiles = profiles
            if (profiles.count > 3) {
                app.profiles = Array(profiles[0..<3])
            }
            if (profiles.count > 0) {
                AppDelegate.instance.activate(profiles.filter({$0.active.boolValue}).first ?? profiles[0])
            }
        } catch {
            fatalError("Failed to fetch employees: \(error)")
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
    let xmppQueue = DispatchQueue(label: "ExpLeague XMPP stream", attributes: [])
    
    var tabs: TabsViewController!

    var split: UISplitViewController {
        return tabs.viewControllers![1] as! UISplitViewController
    }
    
    fileprivate var navigation: UINavigationController {
        get {
            return (split.viewControllers[0] as! UINavigationController)
        }
    }

    var orderView: OrderViewController?
    var expertsView: ExpertsOverviewController?
    var historyView: HistoryViewController?
    var dataController: DataController!
    let uploader = AttachmentsUploader()
    var token: String?
    
    var activeProfile: ExpLeagueProfile?
    
    var profiles : [ExpLeagueProfile]?;
    
    func randString(_ len: Int, seed: Int? = nil) -> String {
        let seedX = seed != nil ? seed! : Int(arc4random())
        let chars = "0123456789ABCDEF".characters
        var result = ""
        
        srand48(seedX)
        for _ in 0..<len {
            let idx = Int(drand48()*Double(chars.count))
            result.append(chars[chars.index(chars.startIndex, offsetBy: idx)])
        }
        return result
    }
    
    func setupDefaultProfiles(_ code: Int?) {
        profiles = [];
        let randString = UUID().uuidString
        let userName = self.randString(8, seed: code)
        let login = userName + "-" + randString.substring(to: randString.characters.index(randString.startIndex, offsetBy: 8))
        let passwd = UUID().uuidString

        let production = ExpLeagueProfile("Production", domain: "expleague.com", login: login, passwd: passwd, port: 5222, context: dataController.managedObjectContext)
        if (profiles!.count == 0) {
            profiles!.append(production)
        }
        if (profiles!.count == 1) {
            profiles!.append(ExpLeagueProfile("Test", domain: "test.expleague.com", login: login, passwd: passwd, port: 5222, context: dataController.managedObjectContext))
        }
        if (profiles!.count == 2) {
            profiles!.append(ExpLeagueProfile("Local", domain: "172.21.211.153", login: login, passwd: passwd, port: 5222, context: dataController.managedObjectContext))
        }
        if (activeProfile == nil) {
            activate(profiles![0]);
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
    func activate(_ profile: ExpLeagueProfile) {
        activeProfile?.disconnect();
        print ("\(profile.domain): \(profile.orders.count)")
        profile.connect()
        activeProfile = profile
        reachability = Reachability(hostname: profile.domain)
        QObject.notify(#selector(activate), self)
    }
    
    func prepareBackground(_ application: UIApplication) {
        if(activeProfile?.busy ?? false) {
            application.setMinimumBackgroundFetchInterval(UIApplicationBackgroundFetchIntervalMinimum)
        }
        else {
            application.setMinimumBackgroundFetchInterval(UIApplicationBackgroundFetchIntervalNever)
        }
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
        
        GMSServices.provideAPIKey(AppDelegate.GOOGLE_API_KEY)
        GMSPlacesClient.provideAPIKey(AppDelegate.GOOGLE_API_KEY)
        FBSDKAppEvents.activateApp()
        
        window = UIWindow(frame: UIScreen.main.bounds)
        let storyboard = UIStoryboard(name: "Main", bundle: nil)
        dataController = DataController(app: self)
        window?.rootViewController = activeProfile != nil ? storyboard.instantiateViewController(withIdentifier: "tabs") : storyboard.instantiateInitialViewController()
        window?.makeKeyAndVisible()
                
//        application.statusBarStyle = .LightContent
        application.registerForRemoteNotifications()
        let settings = UIUserNotificationSettings(types: [.alert, .sound], categories: [])
        application.registerUserNotificationSettings(settings)
        application.isIdleTimerDisabled = false
        return true
    }
    
    func applicationDidEnterBackground(_ application: UIApplication) {
        prepareBackground(application)
        activeProfile?.suspend()
    }
    
    func applicationDidBecomeActive(_ application: UIApplication) {
        if (connectionErrorNotification != nil) {
            application.cancelLocalNotification(connectionErrorNotification!)
        }
        _ = dataController.group.wait(timeout: DispatchTime.distantFuture)
        activeProfile?.resume()
    }
    
    func application(_ application: UIApplication, performFetchWithCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        if (connectionErrorNotification != nil) {
            application.cancelLocalNotification(connectionErrorNotification!)
        }
        guard activeProfile != nil && application.applicationState != .active else {
            completionHandler(.newData)
            return
        }
        if (!activeProfile!.busy) {
            completionHandler(.noData)
        }
        else if (reachability == nil || reachability!.isReachable) {
            QObject.track(activeProfile!, #selector(ExpLeagueProfile.busyChanged)) {
                guard !self.activeProfile!.busy else {
                    return true
                }
                self.prepareBackground(application)
                self.activeProfile!.suspend()
                DispatchQueue.main.async {
                    completionHandler(.newData)
                }
                return false
            }
            activeProfile?.resume()
        }
        else {
            self.prepareBackground(application)
            DispatchQueue.main.async {
                completionHandler(.failed)
            }
        }
    }
    
    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any], fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        print("Received remote notification: \(userInfo)")
        if let messageId = userInfo["id"] as? String {
            activeProfile!.expect(messageId)
        }
        else if let aowId = userInfo["aow"] as? String, aowId != activeProfile!.aowId {
            activeProfile!.aow(aowId, title: userInfo["title"])
        }
        self.application(application, performFetchWithCompletionHandler: completionHandler)
    }

    func application(_ application: UIApplication, didReceive notification: UILocalNotification) {
        if let orderId = notification.userInfo?["order"] as? String, let order = activeProfile?.order(name: orderId) {
            historyView?.selected = order
            tabs.selectedIndex = 1
        }
    }
    
    func applicationWillTerminate(_ application: UIApplication) {
        activeProfile?.suspend()
    }
    
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let chars = (deviceToken as NSData).bytes.assumingMemoryBound(to: Swift.CChar)
        var token = ""
        
        for i in 0..<deviceToken.count {
            token += String(format: "%02.2hhx", arguments: [chars[i]])
        }
        self.token = token
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
