//
//  ExpertsOverview.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 13/03/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

import unSearchCore

class ExpertsOverviewController: UITableViewController {
    var experts: [ExpLeagueMember] {
        return ExpLeagueProfile.active.experts
    }
    
    var table: UITableView {
        return (self.view as! UITableView)
    }
        
    var top: [ExpLeagueMember] = []
    var my: [ExpLeagueMember] = []
    
    func update() {
        top.removeAll()
        my.removeAll()
        for exp in experts {
            if (exp.myTasks > 0) {
                my.append(exp)
            }
            else {
                top.append(exp)
            }
        }
        my.sort() {
            return $0.myTasks > $1.myTasks
        }
        top.sort() {
            return $0.tasks > $1.tasks
        }
        table.reloadData()
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        self.navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.white]
        AppDelegate.instance.expertsView = self
        (view as! UITableView).register(UITableViewCell.self, forCellReuseIdentifier: "Empty")
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)

        table.isEditing = false
        if (navigationController != nil) {
            navigationController!.navigationBar.setBackgroundImage(UIImage(named: "experts_background"), for: .default)
        }
        update()
    }
    
    override func viewDidAppear(_ animated: Bool) {
        AppDelegate.instance.tabs.tabBar.isHidden = false
    }
    
    override func tableView(_ tableView: UITableView, canEditRowAt indexPath: IndexPath) -> Bool {
        return false
    }
    
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell: ExpertCell
        let exp: ExpLeagueMember
        switch((indexPath as NSIndexPath).section) {
        case 0 where my.isEmpty && top.isEmpty:
            let cell = tableView.dequeueReusableCell(withIdentifier: "Empty", for: indexPath)
            cell.textLabel!.text = "Нет избранных"
            cell.textLabel!.textAlignment = .center
            cell.textLabel!.textColor = UIColor.lightGray
            return cell
        case 0 where !my.isEmpty:
            exp = my[(indexPath as NSIndexPath).row]
            cell = tableView.dequeueReusableCell(withIdentifier: "FavoriteExpert", for: indexPath) as! ExpertCell
        case 0 where my.isEmpty:
            exp = top[(indexPath as NSIndexPath).row]
            cell = tableView.dequeueReusableCell(withIdentifier: "TopExpert", for: indexPath) as! ExpertCell
        case 1:
            exp = top[(indexPath as NSIndexPath).row]
            cell = tableView.dequeueReusableCell(withIdentifier: "TopExpert", for: indexPath) as! ExpertCell
        default:
            return UITableViewCell()
        }
        cell.update(exp)
        return cell
    }
    
    override func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return 77;
    }
    
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch(section) {
        case 0 where my.isEmpty && top.isEmpty:
            return 1
        case 0:
            return my.count > 0 ? my.count : top.count
        case 1:
            return top.count
        default:
            return 0
        }
    }
    
    override func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        return 38
    }
    
    override func tableView(_ tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        let label = UILabel()
        label.textColor = UIColor.lightGray
        label.font = UIFont(name: "Helvetica", size: 14)
        label.backgroundColor = UIColor.white
        label.frame = CGRect(x: 15, y: 0, width: tableView.frame.width - 15, height: 38)
        let view = UIView()
        view.addSubview(label)
        switch(section) {
        case 0 where my.isEmpty && top.isEmpty:
            label.text = "ИЗБРАННЫЕ"
        case 0:
            if (my.count > 0) {
                label.text = "ИЗБРАННЫЕ"
            }
            else {
                label.text = "ЛУЧШИЕ"
            }
        case 1:
            label.text = "ЛУЧШИЕ"
        default:
            label.text = ""
        }
        view.frame = CGRect(x: 0, y: 0, width: tableView.frame.width, height: 38)
        view.backgroundColor = UIColor.white
        return view
    }
    
    override func numberOfSections(in tableView: UITableView) -> Int {
        return my.isEmpty || top.isEmpty ? 1 : 2
    }
    
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        let exp: ExpLeagueMember
        switch((indexPath as NSIndexPath).section) {
        case 0 where my.isEmpty && top.isEmpty:
            return;
        case 0 where !my.isEmpty:
            exp = my[(indexPath as NSIndexPath).row]
        case 0 where my.isEmpty:
            exp = top[(indexPath as NSIndexPath).row]
        case 1:
            exp = top[(indexPath as NSIndexPath).row]
        default:
            return
        }
        AppDelegate.instance.tabs.tabBar.isHidden = true;
        let expertView = ExpertViewController(expert: exp)
        splitViewController!.showDetailViewController(expertView, sender: nil)
    }
    
    override func tableView(_ tableView: UITableView, canMoveRowAt indexPath: IndexPath) -> Bool {
        return false
    }
    
    override func tableView(_ tableView: UITableView, shouldHighlightRowAt indexPath: IndexPath) -> Bool {
        if ((indexPath as NSIndexPath).section == 0 && (indexPath as NSIndexPath).row == 0) {
            return !(my.isEmpty && top.isEmpty)
        }
        return true
    }
}

extension ExpertsOverviewController: UISplitViewControllerDelegate {
    func primaryViewController(forCollapsing splitViewController: UISplitViewController) -> UIViewController? {
        AppDelegate.instance.tabs.tabBar.isHidden = false
        return navigationController ?? self
    }
    
    func primaryViewController(forExpanding splitViewController: UISplitViewController) -> UIViewController? {
        return primaryViewController(forCollapsing: splitViewController)
    }
    
    func splitViewController(_ svc: UISplitViewController, willChangeTo displayMode: UISplitViewControllerDisplayMode) {
        if (displayMode != .allVisible) {
            AppDelegate.instance.tabs.tabBar.isHidden = false
        }
    }
}

class ChooseExpertViewController: UITableViewController {
    var experts: [ExpLeagueMember] = []
    
    let owner: OrderDescriptionViewController
    
    func close() {
        owner.update()
        self.dismiss(animated: true, completion: nil)
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        navigationController!.isNavigationBarHidden = false
        navigationController!.navigationBar.setBackgroundImage(UIImage(named: "experts_background"), for: .default)
        navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.white]
        navigationItem.title = "Выберите эксперта"
    }
    
    override func viewWillAppear(_ animated: Bool) {
        experts = ExpLeagueProfile.active.experts.sorted {
            return $0.myTasks != $1.myTasks ? $0.myTasks > $1.myTasks : $0.tasks > $1.tasks
        }
        (view as! UITableView).reloadData()
    }
    
    override func tableView(_ tableView: UITableView, canEditRowAt indexPath: IndexPath) -> Bool {
        return false
    }
    
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let exp = experts[(indexPath as NSIndexPath).row]
        let cell = tableView.dequeueReusableCell(withIdentifier: "ExpertCell", for: indexPath) as! ExpertCell
        cell.update(exp)
        return cell
    }
    
    override func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return 77;
    }
    
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return experts.count
    }
    
    override func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        return 0
    }
    
    override func numberOfSections(in tableView: UITableView) -> Int {
        return 1
    }
    
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        let expert = experts[(indexPath as NSIndexPath).item]
        if (!owner.experts.contains(expert)) {
            owner.experts.append(expert)
            tableView.cellForRow(at: indexPath)?.accessoryType = .checkmark
        }
        else {
            owner.experts.remove(at: owner.experts.index(of: expert)!)
            tableView.cellForRow(at: indexPath)?.accessoryType = .none
        }
    }
    
    override func tableView(_ tableView: UITableView, willDisplay cell: UITableViewCell, forRowAt indexPath: IndexPath) {
        cell.isHighlighted = owner.experts.contains(experts[(indexPath as NSIndexPath).item])
    }
    
    override func tableView(_ tableView: UITableView, canMoveRowAt indexPath: IndexPath) -> Bool {
        return false
    }
    
    override func tableView(_ tableView: UITableView, shouldHighlightRowAt indexPath: IndexPath) -> Bool {
        return true
    }
    
    override func loadView() {
        let table = UITableView()
        view = table
        table.register(UINib(nibName: "ExpertCell", bundle: Bundle.main), forCellReuseIdentifier: "ExpertCell")
        table.dataSource = self
        table.delegate = self
        table.separatorStyle = .none
    }
    
    init(owner: OrderDescriptionViewController) {
        self.owner = owner
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

class ExpertCell: UITableViewCell {
    @IBOutlet weak var name: UILabel!
    @IBOutlet weak var tags: UILabel!
    @IBOutlet weak var tasks: UILabel!
    @IBOutlet weak var avatar: AvatarView!
    
    var expert: ExpLeagueMember?
    
    func onExpertChanged() {
        update()
    }
    
    func update(_ expert: ExpLeagueMember? = nil) {
        if (expert != nil) {
            if (self.expert != nil) {
                QObject.disconnect(self)
            }
            self.expert = expert!
            QObject.connect(self.expert!, signal: #selector(ExpLeagueMember.changed), receiver: self, slot: #selector(self.onExpertChanged))
        }

        DispatchQueue.main.async {
            guard self.expert != nil else {
                return
            }
            self.name.text = self.expert!.name
            self.tags.text = self.expert!.tags.joined(separator: ", ")
            self.avatar.image = self.expert!.avatar
            self.avatar.online = self.expert!.available
            switch (self.expert!.group) {
            case .favorites:
                self.tasks.text = "заданий: \(self.expert!.tasks), ваших: \(self.expert!.myTasks)"
                break
            case .top:
                self.tasks.text = "заданий: \(self.expert!.tasks)"
                break
            }
            
            self.layoutIfNeeded()
        }
    }
    
    deinit {
        QObject.disconnect(self)
    }
}


