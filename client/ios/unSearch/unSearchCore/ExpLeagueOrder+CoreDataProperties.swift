//
//  ExpLeagueOrder+CoreDataProperties.swift
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

extension ExpLeagueOrder {
    @NSManaged public var id: String
    @NSManaged public var started: TimeInterval
    @NSManaged public var topic: String
    @NSManaged public var parent: ExpLeagueProfile

    @NSManaged var flags: Int16
    @NSManaged var messagesRaw: NSOrderedSet
}
