//
//  ExpLeagueMember+CoreDataProperties.swift
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

extension ExpLeagueMember {
    @NSManaged public var profile: ExpLeagueProfile

    @NSManaged var xmlStr: String
    @NSManaged var groupInt: NSNumber
}
