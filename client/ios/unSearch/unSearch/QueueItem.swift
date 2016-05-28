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

    override init(entity: NSEntityDescription, insertIntoManagedObjectContext context: NSManagedObjectContext?) {
        super.init(entity: entity, insertIntoManagedObjectContext: context)
    }
    
    // Insert code here to add functionality to your managed object subclass
    init(message: XMPPMessage, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entityForName("QueueItem", inManagedObjectContext: context)!, insertIntoManagedObjectContext: context)
        self.body = message.XMLString()
        self.receipt = message.elementID()
        save()
    }
}
