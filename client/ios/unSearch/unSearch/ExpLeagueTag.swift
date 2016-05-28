//
//  ExpLeagueTag.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 12/04/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData
import UIKit


class ExpLeagueTag: NSManagedObject {
    var type: ExpLeagueTagType {
        return ExpLeagueTagType(rawValue: self.typeInt.shortValue)!
    }
    
    private dynamic var _icon: UIImage?
    var icon: UIImage {
        if (_icon != nil) {
            return _icon!
        }
        if iconStr.hasPrefix("named://") {
            return UIImage(named: iconStr.substringFromIndex("named://".endIndex))!
        }
        let request = NSURLRequest(URL: NSURL(string: iconStr)!)
        
        do {
            let imageData = try NSURLConnection.sendSynchronousRequest(request, returningResponse: nil)
            
            if let image = UIImage(data: imageData) {
                _icon = image
                return image
            }
        }
        catch {
            parent.log("Unable to load icon \(iconStr): \(error)");
        }
        return UIImage(named: "search_icon")!
    }
    
    func updateIcon(icon: String) {
        iconStr = icon
        save()
    }
    
    init(name: String, icon: String, type: ExpLeagueTagType, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entityForName("Tag", inManagedObjectContext: context)!, insertIntoManagedObjectContext: context)
        self.iconStr = icon
        self.name = name
        self.typeInt = NSNumber(short: type.rawValue)
        save()
    }
    
    override init(entity: NSEntityDescription, insertIntoManagedObjectContext context: NSManagedObjectContext?) {
        super.init(entity: entity, insertIntoManagedObjectContext: context)
    }
}

enum ExpLeagueTagType: Int16 {
    case Tag = 0
    case Pattern = 1
}
