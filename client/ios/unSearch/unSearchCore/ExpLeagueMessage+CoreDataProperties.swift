//
//  ExpLeagueMessage+CoreDataProperties.swift
//  unSearch
//
//  Created by Igor Kuralenok on 14.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//
//  Choose "Create NSManagedObject Subclass…" from the Core Data editor menu
//  to delete and recreate this implementation file for your updated model.
//

import Foundation
import CoreData

extension ExpLeagueMessage {
    @NSManaged public var from: String
    @NSManaged public var time: TimeInterval
    
    @NSManaged var parentRaw: NSManagedObject
    @NSManaged var typeRaw: Int16
    @NSManaged var body: String?
    @NSManaged var propertiesRaw: String?
}
