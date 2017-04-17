//
//  OnboardPageViewController.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 24.03.17.
//  Copyright © 2017 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class OnboardPageViewController: UIViewController {
    @IBOutlet weak var image: UIImageView!
    @IBOutlet weak var text: UITextView!
    @IBOutlet weak var button: UIButton!
    @IBAction func fire(_ sender: Any) {
        callback?()
    }
    @IBOutlet weak var topSpacing: NSLayoutConstraint!
    @IBOutlet weak var midSpacing: NSLayoutConstraint!
    fileprivate var txt: String? {
        didSet {
            self.text?.text = txt
        }

    }
    fileprivate var img: UIImage? {
        didSet {
            self.image?.image = img
        }
    }
    
    var callback: (()->Void)? {
        didSet {
            self.button?.isHidden = callback == nil
        }
    }
    
    func build(text: String, image: UIImage, callback: (()->Void)? = nil) -> OnboardPageViewController {
        self.txt = text
        self.img = image
        self.callback = callback
        return self
    }
    
    override func viewDidLoad() {
        let diff = UIScreen.main.bounds.height - 568.0
        topSpacing.constant = max(diff / 4, 0)
        midSpacing.constant = max(diff / 4, 0)
        button!.layer.cornerRadius = button!.frame.height / 2
        button!.layer.borderColor = Palette.CONTROL.cgColor
        button!.layer.borderWidth = 2
        button!.clipsToBounds = true
        button!.isHidden = callback == nil
        image!.image = img
        text!.text = txt
        
    }
}

class OnboardViewController: UIPageViewController, UIPageViewControllerDataSource {
    let pages: [OnboardPageViewController]
    var index = 0
    func pageViewController(_ pageViewController: UIPageViewController, viewControllerAfter viewController: UIViewController) -> UIViewController? {
        let index = pages.index(of: viewController as! OnboardPageViewController)!
        return index < pages.count - 1 ? pages[index + 1] : nil
    }

    func pageViewController(_ pageViewController: UIPageViewController, viewControllerBefore viewController: UIViewController) -> UIViewController? {
        let index = pages.index(of: viewController as! OnboardPageViewController)!
        return index > 0 ? pages[index - 1] : nil
    }
    
    func presentationCount(for pageViewController: UIPageViewController) -> Int {
        return pages.count
    }
    
    func presentationIndex(for pageViewController: UIPageViewController) -> Int {
        return index
    }
    
    init(pages: [OnboardPageViewController]) {
        self.pages = pages
        super.init(transitionStyle: .scroll, navigationOrientation: .horizontal, options: nil)
        self.dataSource = self
        
        setViewControllers([pages[0]], direction: .forward, animated: true, completion: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}
