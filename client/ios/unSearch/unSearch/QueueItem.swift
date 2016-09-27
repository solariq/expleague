//
//  QueueItem.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 11/04/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData
import XMPPFramework

class QueueItem: NSManagedObject {

    override init(entity: NSEntityDescription, insertInto context: NSManagedObjectContext?) {
        super.init(entity: entity, insertInto: context)
    }
    
    // Insert code here to add functionality to your managed object subclass
    init(message: XMPPMessage, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entity(forEntityName: "QueueItem", in: context)!, insertInto: context)
        self.body = message.xmlString
        self.receipt = message.elementID()
        save()
    }
}
