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
    @NSManaged var id: String
    @NSManaged var started: NSTimeInterval
    @NSManaged var topic: String
    @NSManaged var flags: Int16
    @NSManaged var messagesRaw: NSOrderedSet
}
