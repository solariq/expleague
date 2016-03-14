//
//  ExpertsOverview.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 13/03/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class ExpertsOverviewController: UITableViewController {
    var experts: [ExpLeagueMember] {
        return AppDelegate.instance.activeProfile!.experts
    }
        
    var top: [ExpLeagueMember] = []
    var my: [ExpLeagueMember] = []
    
    override func viewDidLoad() {
        super.viewDidLoad()
        self.navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.whiteColor()]
    }
    
    override func viewWillAppear(animated: Bool) {
        super.viewWillAppear(animated)
        let table = (self.view as! UITableView)
        table.reloadData()
        table.editing = false
        (view as! UITableView).registerClass(UITableViewCell.self, forCellReuseIdentifier: "Empty")
        if (navigationController != nil) {
            navigationController!.navigationBar.setBackgroundImage(UIImage(named: "experts_background"), forBarMetrics: .Default)
        }
        top.removeAll()
        my.removeAll()
        for exp in experts {
            if (exp.group == "Top") {
                top.append(exp)
            }
            else {
                my.append(exp)
            }
        }
    }
    
    override func viewDidAppear(animated: Bool) {
        AppDelegate.instance.tabs.tabBar.hidden = false
    }
    
    override func tableView(tableView: UITableView, canEditRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        return false
    }
    
    override func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        switch(indexPath.section) {
        case 0 where my.isEmpty:
            let cell = tableView.dequeueReusableCellWithIdentifier("Empty", forIndexPath: indexPath)
            cell.textLabel!.text = "Нет избранных"
            cell.textLabel!.textAlignment = .Center
            cell.textLabel!.textColor = UIColor.lightGrayColor()
            return cell
        case 0 where !my.isEmpty:
            let exp = my[indexPath.row]
            let cell = tableView.dequeueReusableCellWithIdentifier("FavoriteExpert", forIndexPath: indexPath) as! ExpertCell
            cell.name.text = exp.name
            cell.tags.text = exp.tags.joinWithSeparator(", ")
            cell.avatar.image = exp.avatar
            cell.tasks.text = "заданий: \(exp.myTasks)"
            return cell
        case 1:
            let exp = top[indexPath.row]
            let cell = tableView.dequeueReusableCellWithIdentifier("TopExpert", forIndexPath: indexPath) as! ExpertCell
            cell.name.text = exp.name
            cell.tags.text = exp.tags.joinWithSeparator(", ")
            cell.avatar.image = exp.avatar
            cell.tasks.text = "всего заданий: \(exp.myTasks)"
            return cell
        default:
            return UITableViewCell()
        }
    }
    
    override func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        return 77;
    }
    
    override func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch(section) {
        case 0:
            return max(my.count, 1)
        case 1:
            return top.count
        default:
            return 0
        }
    }
    
    override func tableView(tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        return 38
    }
    override func tableView(tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        let label = UILabel()
        label.textColor = UIColor.lightGrayColor()
        label.font = UIFont(name: "Helvetica", size: 14)
        label.backgroundColor = UIColor.whiteColor()
        label.frame = CGRectMake(15, 0, tableView.frame.width - 15, 38)
        let view = UIView()
        view.addSubview(label)
        switch(section) {
        case 0:
            label.text = "ИЗБРАННЫЕ"
        case 1:
            label.text = "ОНЛАЙН"
        default:
            label.text = ""
        }
        view.frame = CGRectMake(0, 0, tableView.frame.width, 38)
        view.backgroundColor = UIColor.whiteColor()
        return view
    }
    
    override func numberOfSectionsInTableView(tableView: UITableView) -> Int {
        return 2
    }
    override func tableView(tableView: UITableView, didSelectRowAtIndexPath indexPath: NSIndexPath) {
        let exp: ExpLeagueMember
        switch(indexPath.section) {
        case 0 where my.isEmpty:
            return;
        case 0 where !my.isEmpty:
            exp = my[indexPath.row]
        case 1:
            exp = top[indexPath.row]
        default:
            return
        }
        AppDelegate.instance.tabs.tabBar.hidden = true;
        let expertView = ExpertViewController(expert: exp)
        splitViewController!.showDetailViewController(expertView, sender: nil)
    }
    
    override func tableView(tableView: UITableView, canMoveRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        return false
    }
    
    override func tableView(tableView: UITableView, shouldHighlightRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        if (indexPath.section == 0 && indexPath.row == 0) {
            return !my.isEmpty
        }
        return true
    }
}

extension ExpertsOverviewController: UISplitViewControllerDelegate {
    func primaryViewControllerForCollapsingSplitViewController(splitViewController: UISplitViewController) -> UIViewController? {
        AppDelegate.instance.tabs.tabBar.hidden = false
        return navigationController ?? self
    }
    
    func primaryViewControllerForExpandingSplitViewController(splitViewController: UISplitViewController) -> UIViewController? {
        return primaryViewControllerForCollapsingSplitViewController(splitViewController)
    }
    
    func splitViewController(svc: UISplitViewController, willChangeToDisplayMode displayMode: UISplitViewControllerDisplayMode) {
        if (displayMode != .AllVisible) {
            AppDelegate.instance.tabs.tabBar.hidden = false
        }
    }
}

class ExpertCell: UITableViewCell {
    @IBOutlet weak var name: UILabel!
    @IBOutlet weak var tags: UILabel!
    @IBOutlet weak var tasks: UILabel!
    @IBOutlet weak var avatar: AvatarView!
}

