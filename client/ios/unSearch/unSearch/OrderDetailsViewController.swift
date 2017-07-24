//
//  OrderDetailsViewController.swift
//  unSearch
//
//  Created by Igor Kuralenok on 12.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import StoreKit
import XMPPFramework
import FBSDKCoreKit
import QuickLook
import MobileCoreServices

import unSearchCore

fileprivate func < <T : Comparable>(lhs: T?, rhs: T?) -> Bool {
  switch (lhs, rhs) {
  case let (l?, r?):
    return l < r
  case (nil, _?):
    return true
  default:
    return false
  }
}


enum OrderDetailsViewControllerState {
    case chat
    case answer
}

class OrderDetailsViewController: UIPageViewController {
    var data: ChatModel! {
        didSet {
            if (isViewLoaded) {
                chat.data = data
                answer.data = data
            }
        }
    }
    
    var chat: ChatViewController!
    var answer: AnswerViewController!
    var state: OrderDetailsViewControllerState {
        get {
            return self.viewControllers![0] == answer ? .answer : .chat
        }
        set (v) {
            guard state != v else {
                return
            }
            switch(v) {
            case .answer:
                self.setViewControllers([answer], direction: .forward, animated: shown, completion: nil)
            case .chat:
                self.setViewControllers([chat], direction: .reverse, animated: shown, completion: nil)
            }
        }
    }
    
    
    func stateChanged() {
        segmentedControl.setEnabled(!answer.text.isEmpty, forSegmentAt: 1)
        if (answer.text.isEmpty) {
            state = .chat
        }
        segmentedControl.selectedSegmentIndex = state == .chat ? 0 : 1
        answer.onStateChanged()
        QObject.notify(#selector(self.stateChanged), self)
    }
    
    func updateState() {
        state = [.chat, .answer][segmentedControl.selectedSegmentIndex]
    }

    fileprivate let segmentedControl = UISegmentedControl(items: ["Диалог", "Ответ"])
    fileprivate var oldNavbarTotleView: UIView? = nil
    fileprivate var shown = false
    
    override func viewDidLoad() {
        super.viewDidLoad()
        PurchaseHelper.instance.register([
            "com.expleague.unSearch.Star30r",
            "com.expleague.unSearch.Star75r",
            "com.expleague.unSearch.Star150r",
            "com.expleague.unSearch.Star300r",
        ])
        chat = storyboard!.instantiateViewController(withIdentifier: "chatVC") as! ChatViewController
        answer = storyboard!.instantiateViewController(withIdentifier: "answerVC") as! AnswerViewController
        chat.data = data
        answer.data = data
        chat.didMove(toParentViewController: self)
        answer.didMove(toParentViewController: self)
        segmentedControl.setTitleTextAttributes([NSFontAttributeName : UIFont.systemFont(ofSize: 16)], for: .normal)
        segmentedControl.sizeToFit()
        dataSource = self
        setViewControllers([chat], direction: .forward, animated: true, completion: nil)
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        oldNavbarTotleView = self.navigationItem.titleView
        segmentedControl.addTarget(self, action: #selector(self.updateState), for: .valueChanged)
        navigationItem.titleView = segmentedControl
        if (!shown) {
            data.controller = self
            data.sync(true) {
                FBSDKAppEvents.logEvent("Task view", parameters: [
                    "order": self.data.order.id
                ])
                self.state = self.data.state == .chat || self.data.state == .save || self.data.answer.isEmpty ? .chat : .answer
                self.stateChanged()
                self.chat.scrollToLastMessage()
            }
        }
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        self.navigationItem.titleView = oldNavbarTotleView
        
        data.markAsRead()
    }
    
    fileprivate var tabBarHeight = CGFloat(49)
    fileprivate var tabBar: UITabBarController?
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        let frame = self.tabBarController?.tabBar.frame
        guard frame?.size.height ?? 0 > 0 && !shown else {
            return
        }
//        tabBarHeight = frame?.size.height ?? tabB
        tabBar = tabBarController
        UIView.animate(withDuration: 0.3) {
            self.tabBarController?.tabBar.frame = frame!.offsetBy(dx: 0, dy: self.tabBarHeight)
        }
        shown = true
        ExpLeagueProfile.active.selectedOrder = nil
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        shown = navigationController?.childViewControllers.contains(self) ?? false
        guard !shown else {
            return
        }
        data.controller = nil
        let frame = AppDelegate.instance.tabs?.tabBar.frame
        tabBar?.tabBar.isHidden = false
        if (frame?.size.height ?? 0 > 0){
            UIView.animate(withDuration: 0.3) {
                self.tabBar?.tabBar.frame = frame!.offsetBy(dx: 0, dy: -self.tabBarHeight)
            }
        }
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
    }
}

extension OrderDetailsViewController:  UIPageViewControllerDataSource {
    func pageViewController(_ pageViewController: UIPageViewController, viewControllerBefore viewController: UIViewController) -> UIViewController? {
        return viewController is AnswerViewController ? chat : nil
    }

    func pageViewController(_ pageViewController: UIPageViewController, viewControllerAfter viewController: UIViewController) -> UIViewController? {
        return !answer.text.isEmpty && viewController is ChatViewController ? answer : nil
    }
}
