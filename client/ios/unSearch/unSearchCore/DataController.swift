//
//  DataController.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 30.10.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData
import ReachabilitySwift
import MapKit

public class DataController: NSObject {
    fileprivate static let instance = DataController()
    
    public static func shared() -> DataController {
        return instance
    }
    
    public var activeProfile: ExpLeagueProfile?
    public var version: String?
    public let xmppQueue = ExpLeagueCommunicator.xmppQueue
    public var reachability: Reachability?
    public var token: String?

    let managedObjectContext = NSManagedObjectContext(concurrencyType: .privateQueueConcurrencyType)
    var locationProvider: LocationProvider?
    var profiles: [ExpLeagueProfile] = []

    public func setupDefaultProfiles(_ code: Int?) {
        let randString = UUID().uuidString
        let userName = Utils.randString(8, seed: code)
        let login = userName + "-" + randString.substring(to: randString.characters.index(randString.startIndex, offsetBy: 8))
        let passwd = UUID().uuidString
        
        if (profiles.count == 0) {
            profiles.append(ExpLeagueProfile("Production", domain: "expleague.com", login: login, passwd: passwd, port: 5222, context: managedObjectContext))
        }
        if (profiles.count == 1) {
            profiles.append(ExpLeagueProfile("Test", domain: "test.expleague.com", login: login, passwd: passwd, port: 5222, context: managedObjectContext))
        }
        if (profiles.count == 2) {
            profiles.append(ExpLeagueProfile("Local", domain: "172.21.211.153", login: login, passwd: passwd, port: 5222, context: managedObjectContext))
        }
        if (activeProfile == nil) {
            activate(profiles[0]);
        }
        
        do {
            try managedObjectContext.save()
        }
        catch {
            activeProfile!.log("\(error)")
        }
    }

    func activate(_ profile: ExpLeagueProfile) {
        activeProfile?.disconnect();
        print ("\(profile.domain): \(profile.orders.count)")
        profile.connect()
        activeProfile = profile
        reachability = Reachability(hostname: profile.domain)
        profileChanged()
    }
    
    public func profileChanged() { QObject.notify(#selector(profileChanged), self) }
    
    public func currentLocation() -> CLLocationCoordinate2D? {
        return locationProvider?.deviceLocation
    }
    
    public func suspend() {
        locationProvider?.stopTracking()
        activeProfile?.suspend()
    }
    
    public func resume() {
        locationProvider?.startTracking()
        activeProfile?.resume()
    }

    override init() {
        super.init()
        
        DispatchQueue.main.async {
            self.locationProvider = LocationProvider()
            self.locationProvider?.setUpLocationProvider()
        }
        // This resource is the same name as your xcdatamodeld contained in your project.
        let bundle = Bundle(identifier: "com.expleague.unSearchCore")
        guard let modelURL = bundle?.url(forResource: "ProfileModel", withExtension: "momd") else {
            fatalError("Error loading model from bundle")
        }
        // The managed object model for the application. It is a fatal error for the application not to be able to find and load its model.
        guard let mom = NSManagedObjectModel(contentsOf: modelURL) else {
            fatalError("Error initializing mom from: \(modelURL)")
        }
        let psc = NSPersistentStoreCoordinator(managedObjectModel: mom)
        self.managedObjectContext.persistentStoreCoordinator = psc
        
        let urls = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        
        let docURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.expleague.unSearch")
        /* The directory the application uses to store the Core Data store file.
         This code uses a file named "DataModel.sqlite" in the application's documents directory.
         */
        guard let storeURL = docURL?.appendingPathComponent("ExpLeagueProfiles.sqlite") else {
            fatalError("Unable to find CoreData storage")
        }
        let oldStoreUrl = urls[urls.count - 1].appendingPathComponent("ExpLeagueProfiles.sqlite")
        if FileManager.default.fileExists(atPath: oldStoreUrl.path) {
            do {
                try FileManager.default.moveItem(at: oldStoreUrl, to: storeURL)
            }
            catch {
                print("Unable to move old storage (\(oldStoreUrl)) to new location (\(storeURL)): \(error)")
            }
        }
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
            profiles = try self.managedObjectContext.fetch(profilesFetch)
            if (profiles.count > 3) {
                self.profiles = Array(profiles[0..<3])
            }
            if (profiles.count > 0) {
                activate(profiles.filter({$0.active.boolValue}).first ?? profiles[0])
            }
        } catch {
            fatalError("Failed to fetch employees: \(error)")
        }
    }
}
