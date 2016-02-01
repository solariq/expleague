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

class DataController: NSObject {
    let managedObjectContext = NSManagedObjectContext(concurrencyType: .MainQueueConcurrencyType)
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
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_BACKGROUND, 0)) {
            let urls = NSFileManager.defaultManager().URLsForDirectory(.DocumentDirectory, inDomains: .UserDomainMask)
            let docURL = urls[urls.endIndex-1]
            /* The directory the application uses to store the Core Data store file.
            This code uses a file named "DataModel.sqlite" in the application's documents directory.
            */
            let storeURL = docURL.URLByAppendingPathComponent("ExpLeagueProfiles.sqlite")
            do {
                try psc.addPersistentStoreWithType(NSSQLiteStoreType, configuration: nil, URL: storeURL, options: nil)
            } catch {
                fatalError("Error migrating store: \(error)")
            }
            let profilesFetch = NSFetchRequest(entityName: "Profile")
            
            do {
                try {
                    let profiles = try self.managedObjectContext.executeFetchRequest(profilesFetch) as! [ExpLeagueProfile]
                    app.profiles = profiles
                    if (profiles.count < 3 || AppDelegate.instance.activeProfile == nil) {
                        app.setupDefaultProfiles()
                    }
                }()
                let localOrders = app.profiles![2].orders.mutableCopy() as! NSMutableOrderedSet
                localOrders.removeAllObjects()
                app.profiles![2].orders = localOrders.copy() as! NSOrderedSet
                try self.managedObjectContext.save()
                AppDelegate.instance.activate(AppDelegate.instance.activeProfile!)
            } catch {
                fatalError("Failed to fetch employees: \(error)")
            }
        }
    }
}

@UIApplicationMain
class AppDelegate: UIResponder {
    @nonobjc static var instance: AppDelegate {
        return (UIApplication.sharedApplication().delegate as! AppDelegate)
    }
    
    var window: UIWindow?
    var tabs: UITabBarController {
        get {
            return window!.rootViewController as! UITabBarController
        }
    }

    var navigation: UINavigationController {
        get {
            let split = (tabs.viewControllers![1] as! UISplitViewController)
            return (split.viewControllers[0] as! UINavigationController)
        }
    }

    var messagesView: MessagesVeiwController?
    var historyView: HistoryViewController?
    let connectionProgressView = UIStoryboard(name: "Main", bundle: nil).instantiateViewControllerWithIdentifier("progressBar") as! ConnectionProgressController
    
    let stream = XMPPStream()
    var dataController: DataController!
    
    var activeProfile : ExpLeagueProfile? {
        let active = profiles?.filter({return $0.active})
        return active?.count > 0 ? active![0] : nil
    }
    
    var profiles : [ExpLeagueProfile]?;
    
    func connect() {
        if (stream.isConnected() || stream.isConnecting() || activeProfile == nil) {
            return
        }
        do {
            stream.hostName = activeProfile!.domain;
            stream.hostPort = UInt16(activeProfile!.port)
            stream.myJID = activeProfile!.jid;

            try stream.connectWithTimeout(XMPPStreamTimeoutNone)
        }
        catch {
            activeProfile?.log("\(error)")
        }
    }
    
    func disconnect() {
        if (!stream.isDisconnected()) {
            stream.disconnect()
        }
    }
    
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
    
    func activate(profile: ExpLeagueProfile) {
        if (activeProfile != nil) {
            disconnect();
            stream.removeDelegate(activeProfile!)
            activeProfile?.active = false
        }
        profile.active = true
        profile.selected = nil

        stream.addDelegate(profile, delegateQueue: dispatch_get_main_queue())
        do {
            try dataController.managedObjectContext.save()
        }
        catch {
            fatalError("Unable to save profiles")
        }
        connect()
    }
}

extension AppDelegate: UIApplicationDelegate {

    func application(application: UIApplication, didFinishLaunchingWithOptions launchOptions: [NSObject: AnyObject]?) -> Bool {
        dataController = DataController(app: self)
        stream.startTLSPolicy = XMPPStreamStartTLSPolicy.Required
        stream.keepAliveInterval = 30
        stream.enableBackgroundingOnSocket = true
        navigation.navigationBar.barTintColor = UIColor(red: 17.0/256, green: 138.0/256, blue: 222.0/256, alpha: 1.0)
        navigation.navigationBar.tintColor = UIColor.whiteColor()
        navigation.navigationBar.titleTextAttributes = [
            NSForegroundColorAttributeName: UIColor.whiteColor()
        ]
        UIApplication.sharedApplication().statusBarStyle = .LightContent
        
        let action = UIMutableUserNotificationAction()
        action.activationMode = .Background
        action.title = "Перейти"
        action.destructive = false
        action.authenticationRequired = false
        action.identifier = ExpLeagueMessage.GOTO_ORDER
        let notificationCat = UIMutableUserNotificationCategory()
        notificationCat.identifier = ExpLeagueMessage.EXPERT_FOUND_NOTIFICATION
        notificationCat.setActions([action], forContext: .Minimal)
        let settings = UIUserNotificationSettings(forTypes: [.Alert, .Badge, .Sound], categories: [notificationCat])
        application.registerUserNotificationSettings(settings)
        application.registerForRemoteNotifications()
        return true
    }


    func applicationWillResignActive(application: UIApplication) {
    // Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
    // Use this method to pause ongoing tasks, disable timers, and throttle down OpenGL ES frame rates. Games should use this method to pause the game.

    }


    func applicationDidEnterBackground(application: UIApplication) {
    // Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
    // If your application supports background execution, this method is called instead of applicationWillTerminate: when the user quits.

    }


    func applicationWillEnterForeground(application: UIApplication) {
        // Called as part of the transition from the background to the inactive state; here you can undo many of the changes made on entering the background.
    }


    func applicationDidBecomeActive(application: UIApplication) {
        connect()
    }


    func applicationWillTerminate(application: UIApplication) {
        stream.disconnectAfterSending()
    }
    
    func application(application: UIApplication, didReceiveLocalNotification notification: UILocalNotification) {
        let state = application.applicationState
        let order = notification.userInfo!["order"] as! String
        if (state == .Inactive) {
            print(order)
            activeProfile?.selected = activeProfile?.order(name: order)
            tabs.selectedIndex = 1
            // Application was in the background when notification was delivered.
        }
        else {
            print(notification.userInfo!["order"]!)
            
            // App was running in the foreground. Perhaps
            // show a UIAlertView to ask them what they want to do?
        }
    }
    
    func application(application: UIApplication, handleActionWithIdentifier identifier: String?, forLocalNotification notification: UILocalNotification, completionHandler: () -> Void) {
        let state = application.applicationState
        if (state == .Inactive) {
            print(notification.userInfo!["order"]!)
            // Application was in the background when notification was delivered.
        }
        else {
            print(notification.userInfo!["order"]!)

            // App was running in the foreground. Perhaps
            // show a UIAlertView to ask them what they want to do?
        }
    }
    
    func application(application: UIApplication, didReceiveRemoteNotification userInfo: [NSObject : AnyObject]) {
        
    }
    
    func application(application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: NSData) {
        print(deviceToken)
    }
    
    func application(application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: NSError) {
        print(error)
    }
}
