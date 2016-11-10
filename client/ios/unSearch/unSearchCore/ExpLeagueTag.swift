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


public class ExpLeagueTag: NSManagedObject {
    public var type: ExpLeagueTagType {
        return ExpLeagueTagType(rawValue: self.typeInt.int16Value)!
    }
    
    fileprivate dynamic var _icon: UIImage?
    public var icon: UIImage {
        if (_icon != nil) {
            return _icon!
        }
        if iconStr.hasPrefix("named://") {
            return UIImage(named: iconStr.substring(from: "named://".endIndex))!
        }
        let request = URLRequest(url: URL(string: iconStr)!)
        
        do {
            let imageData = try NSURLConnection.sendSynchronousRequest(request, returning: nil)
            
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
    
    func updateIcon(_ icon: String) {
        iconStr = icon
        save()
    }
    
    init(name: String, icon: String, type: ExpLeagueTagType, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entity(forEntityName: "Tag", in: context)!, insertInto: context)
        self.iconStr = icon
        self.name = name
        self.typeInt = NSNumber(value: type.rawValue as Int16)
        save()
    }
    
    override init(entity: NSEntityDescription, insertInto context: NSManagedObjectContext?) {
        super.init(entity: entity, insertInto: context)
    }
}

public enum ExpLeagueTagType: Int16 {
    case tag = 0
    case pattern = 1
}
