//
//  UIGestureRecognizerClosure.swift
//
//  Adds closures to gesture setup. Just an example of using an extension.
//
//  Usage:
//
//  self.view.addGestureRecognizer(UITapGestureRecognizer(){
//      print("UITapGestureRecognizer")
//  })
//
//  let longpressGesture = UILongPressGestureRecognizer() {
//    print("UILongPressGestureRecognizer")
//  }
//  self.view.addGestureRecognizer(longpressGesture)
//
//  Michael L. Collard
//  collard@uakron.edu

import UIKit

// Global array of targets, as extensions cannot have non-computed properties
private var target = [Target]()

extension UIGestureRecognizer {    
    convenience init(trailingClosure closure: (() -> ())) {
        // let UIGestureRecognizer do its thing
        self.init()
        
        target.append(Target(closure))
        self.addTarget(target.last!, action: #selector(Target.invoke))
    }
}

private class Target {
    // store closure
    private var trailingClosure: (() -> ())
    
    init(_ closure:(() -> ())) {
        trailingClosure = closure
    }
    
    // function that gesture calls, which then
    // calls closure
    /* Note: Note sure why @IBAction is needed here */
    @objc
    @IBAction func invoke() {
        trailingClosure()
    }
}