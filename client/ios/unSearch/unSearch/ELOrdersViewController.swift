//
// Created by Igor Kuralenok on 12.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class ELHistoryViewController: UITableViewController {
    private func order(index: Int) -> ELOrder {
        let keys : [(String, ELOrder)] = ELConnection.instance.orders.sort({$1.1.started.compare($0.1.started) == NSComparisonResult.OrderedAscending})
        return keys[index].1
    }
    
    override func viewDidLoad() {
        ELConnection.instance.onOrderCreate({
            (self.view as! UITableView).reloadData()
        })
    }
    
    override func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        let orderView = tableView.dequeueReusableCellWithIdentifier("orderCell", forIndexPath: indexPath)
        let order = self.order(indexPath.item);
        orderView.textLabel?.text = order.topic
        return orderView
    }
    
    override func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        return CGFloat(100);
    }
    
    override func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return section == 0 ? ELConnection.instance.orders.count : 0;
    }
    
    override func tableView(tableView: UITableView, didSelectRowAtIndexPath indexPath: NSIndexPath) {
        ELConnection.instance.orderSelected = order(indexPath.item)
        splitViewController!.showDetailViewController(ELMessagesVeiwController(), sender: nil)
    }
}
