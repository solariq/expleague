//
//  ExpLeagueProfile+CoreDataProperties.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 12/04/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//
//  Choose "Create NSManagedObject Subclass…" from the Core Data editor menu
//  to delete and recreate this implementation file for your updated model.
//

import Foundation
import CoreData

extension ExpLeagueProfile {

    @NSManaged var active: NSNumber
    
    @NSManaged public var domain: String
    @NSManaged public var login: String
    @NSManaged public var name: String
    @NSManaged public var passwd: String
    @NSManaged public var port: NSNumber

    @NSManaged var myUnread: Int32
    @NSManaged var orderSelected: NSNumber
    @NSManaged var receiveAnswerOfTheWeek: NSNumber?
    @NSManaged var aowTitle: String?
    @NSManaged var aowId: String?
    @NSManaged var orders: NSOrderedSet
    @NSManaged var queue: NSOrderedSet?
    @NSManaged var pendingStr: String?
    @NSManaged var expertsSet: NSSet?
    @NSManaged var tagsSet: NSSet?
}
