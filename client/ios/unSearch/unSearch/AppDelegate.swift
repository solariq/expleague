//
//  AppDelegate.swift
//  unSearch
//
//  Created by Игорь Кураленок on 09.01.16.
//  Copyright (c) 2016 Experts League Inc. All rights reserved.
//


import UIKit
import XMPPFramework

@UIApplicationMain
class AppDelegate: UIResponder {
    var window: UIWindow?
    
    let connection = ELConnection.instance
    let ordersViewController: ELOrdersViewController? = nil
    
    func showOrder(order: ELOrder) {
        let tabs = window!.rootViewController as! UITabBarController
        tabs.selectedIndex = 1
        let history = tabs.viewControllers![1] as! UINavigationController
        let selector = Selector(order.id())
//        history.navigationController!.performSelector(selector)
    }
}

extension AppDelegate: UIApplicationDelegate {

    func application(application: UIApplication, didFinishLaunchingWithOptions launchOptions: [NSObject: AnyObject]?) -> Bool {
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
        connection.start()
    }


    func applicationWillTerminate(application: UIApplication) {
        connection.stop()
    // Called when the application is about to terminate. Save data if appropriate. See also applicationDidEnterBackground:.
    }
}
