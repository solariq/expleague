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
    @NSManaged var domain: String
    @NSManaged var login: String
    @NSManaged var name: String
    @NSManaged var orderSelected: NSNumber?
    @NSManaged var passwd: String
    @NSManaged var port: NSNumber
    @NSManaged var orders: NSOrderedSet
    @NSManaged var queue: NSSet?
    @NSManaged var expertsSet: NSSet?
    @NSManaged var tagsSet: NSSet?

}
