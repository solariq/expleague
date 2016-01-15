//
// Created by Igor Kuralenok on 12.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class ELHistoryViewController: UITableViewController {
    private func order(index: Int) -> ExpLeagueOrder {
        let orders = AppDelegate.instance.activeProfile!.orders
        let keys = orders.sortedArrayUsingComparator({
            let lhs = $0 as! ExpLeagueOrder
            let rhs = $1 as! ExpLeagueOrder
            
            return lhs.started < rhs.started ? .OrderedAscending : (lhs.started > rhs.started  ? .OrderedDescending : .OrderedSame);
            })
        return keys[index] as! ExpLeagueOrder;
    }
    
    override func viewDidLoad() {
        AppDelegate.instance.activeProfile
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
        return section == 0 ? AppDelegate.instance.activeProfile!.orders.count : 0;
    }
    
    override func tableView(tableView: UITableView, didSelectRowAtIndexPath indexPath: NSIndexPath) {
        let o = order(indexPath.item)
        AppDelegate.instance.activeProfile!.selected = o
        AppDelegate.instance.messagesView.order = o
        splitViewController!.showDetailViewController(AppDelegate.instance.messagesView, sender: nil)
    }
}
