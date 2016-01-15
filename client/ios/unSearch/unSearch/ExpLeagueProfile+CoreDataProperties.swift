//
//  ExpLeagueProfile+CoreDataProperties.swift
//  unSearch
//
//  Created by Igor Kuralenok on 15.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//
//  Choose "Create NSManagedObject Subclass…" from the Core Data editor menu
//  to delete and recreate this implementation file for your updated model.
//

import Foundation
import CoreData

extension ExpLeagueProfile {

    @NSManaged var active: Bool
    @NSManaged var domain: String
    @NSManaged var port: Int16
    @NSManaged var login: String
    @NSManaged var passwd: String
    @NSManaged var name: String
    @NSManaged var orders: NSOrderedSet
    @NSManaged var orderSelected: Int16
}
