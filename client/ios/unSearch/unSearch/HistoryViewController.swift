//
// Created by Igor Kuralenok on 12.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class HistoryViewController: UITableViewController {
    var keys: [ExpLeagueOrder] {
        let orders = AppDelegate.instance.activeProfile!.orders
        return orders.sortedArrayUsingComparator({
            let lhs = $0 as! ExpLeagueOrder
            let rhs = $1 as! ExpLeagueOrder
            
            return lhs.started < rhs.started ? .OrderedDescending : (lhs.started > rhs.started  ? .OrderedAscending : .OrderedSame);
        }) as! [ExpLeagueOrder]
    }
    
    private func order(index: Int) -> ExpLeagueOrder {
        return keys[index]
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        splitViewController!.delegate = self
        AppDelegate.instance.historyView = self
    }
    
    override func viewWillAppear(animated: Bool) {
        super.viewWillAppear(animated)
        if let order = AppDelegate.instance.activeProfile!.selected {
            let table = (self.view as! UITableView)
            let path = NSIndexPath(forRow: keys.indexOf(order)!, inSection: 0)
            table.selectRowAtIndexPath(path, animated: false, scrollPosition: .Top)
            self.tableView(table, didSelectRowAtIndexPath: path)
        }
        AppDelegate.instance.tabs.tabBar.hidden = false
    }
    
    override func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        let orderView = tableView.dequeueReusableCellWithIdentifier("orderCell", forIndexPath: indexPath)
        let order = self.order(indexPath.item);
        orderView.textLabel?.text = order.text
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
        AppDelegate.instance.messagesView!.order = o
        splitViewController!.showDetailViewController(AppDelegate.instance.messagesView!, sender: nil)
    }
}


extension HistoryViewController: UISplitViewControllerDelegate {
    func splitViewController(svc: UISplitViewController, willChangeToDisplayMode displayMode: UISplitViewControllerDisplayMode) {
        print(displayMode)
    }
    
    func splitViewController(splitViewController: UISplitViewController, showDetailViewController vc: UIViewController, sender: AnyObject?) -> Bool {
        let mvc = vc as! MessagesVeiwController
        mvc.order = AppDelegate.instance.activeProfile!.selected
        return false
    }
    
    func splitViewController(splitViewController: UISplitViewController, showViewController vc: UIViewController, sender: AnyObject?) -> Bool {
        return true
    }
    
    func splitViewController(splitViewController: UISplitViewController, collapseSecondaryViewController secondaryViewController: UIViewController, ontoPrimaryViewController primaryViewController: UIViewController) -> Bool {
        let mvc = secondaryViewController as! MessagesVeiwController
        mvc.order = AppDelegate.instance.activeProfile!.selected
        mvc.viewWillAppear(false)
        return mvc.order == nil
    }
}