//
//  PurchaseHelper.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 09.06.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import StoreKit
import ReachabilitySwift

enum PurchaseResult: Int {
    case Accepted
    case Rejected
    case Error
}

class PurchaseHelper: NSObject {
    static let instance = PurchaseHelper()
    
    var products: [String: SKProduct?] = [:]
    var queue: [String: [(rc: PurchaseResult, transactionId: String?) -> ()]] = [:]
    
    func productsChanged() {
        QObject.notify(#selector(self.productsChanged), self)
    }
    
    func request(id: String, callback: (rc: PurchaseResult, transactionId: String?) -> ()) {
        if let val = products[id] {
            if let product = val {
                if queue[id] == nil {
                    queue[id] = []
                }
                queue[id]?.append(callback)
                SKPaymentQueue.defaultQueue().addPayment(SKPayment(product: product))
            }
            else {
                callback(rc: .Error, transactionId: nil)
            }
        }
        else {
            QObject.track(self, #selector(self.productsChanged)) {
                guard self.products[id] != nil else {
                    return true
                }
                self.request(id, callback: callback)
                return false
            }
            register([id])
        }
    }
    
    func register(ids: [String]) {
        var remaining = ids
        for id in ids {
            if (products[id] != nil) {
                remaining.removeOne(id)
            }
        }
        guard !remaining.isEmpty else {
            return
        }
        let request = SKProductsRequest(productIdentifiers: Set<String>(remaining))
        request.delegate = self
        request.start()
    }
    
    static func visitTransactions(visitor visitor: (name: String, id: String)->(), completion: (NSError?) -> ()) {
        let group = dispatch_group_create()
        let observer = RestoreTransactionsObserver(visitor: visitor, group: group)
        dispatch_group_enter(group)
        SKPaymentQueue.defaultQueue().addTransactionObserver(observer)
        SKPaymentQueue.defaultQueue().restoreCompletedTransactions()
        
        dispatch_group_notify(group, dispatch_get_main_queue()) {
            SKPaymentQueue.defaultQueue().removeTransactionObserver(observer)
            completion(observer.error)
        }
    }
    
    var reachability: Reachability!
    override init() {
        super.init()
        SKPaymentQueue.defaultQueue().addTransactionObserver(self)
        reachability = try! Reachability.reachabilityForInternetConnection()
        reachability.whenReachable = {r in
            self.products.forEach { (id: String, product: SKProduct?) -> () in
                if (product == nil) {
                    self.products.removeValueForKey(id)
                }
            }
        }
        do {
            try reachability.startNotifier()
        }
        catch {
            AppDelegate.instance.activeProfile?.log("Unable to register reachability: \(error)")
        }
    }
}

extension PurchaseHelper: SKPaymentTransactionObserver {
    func paymentQueue(queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        print("\(transactions.count) payment transactions finished")
        for transaction in transactions {
            print("\t\(transaction.payment.productIdentifier) -> \(transaction.transactionState)")
            switch transaction.transactionState {
            case .Purchased, .Restored:
                let callback = self.queue[transaction.payment.productIdentifier]?.removeFirstOrNone()
                callback?(rc: .Accepted, transactionId: transaction.transactionIdentifier)
                SKPaymentQueue.defaultQueue().finishTransaction(transaction)
            case .Failed:
                let callback = self.queue[transaction.payment.productIdentifier]?.removeFirstOrNone()
                callback?(rc: .Rejected, transactionId: nil)
                SKPaymentQueue.defaultQueue().finishTransaction(transaction)
            default:
                break;
            }
        }
    }
}

extension PurchaseHelper: SKProductsRequestDelegate {
    func productsRequest(request: SKProductsRequest, didReceiveResponse response: SKProductsResponse) {
        print("Products request concluded: \(response.products.count) products resolved \(response.invalidProductIdentifiers.count) --- failed")
        response.products.forEach {product in
            self.products[product.productIdentifier] = product
        }
        response.invalidProductIdentifiers.forEach{invalidId in
            self.products[invalidId] = nil
        }
        productsChanged()
    }

}

class RestoreTransactionsObserver: NSObject, SKPaymentTransactionObserver {
    let visitor: (name: String, id: String) -> ()
    @objc func paymentQueue(queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        for transaction in transactions {
            switch transaction.transactionState {
            case .Restored:
                visitor(name: transaction.payment.productIdentifier, id: transaction.originalTransaction!.transactionIdentifier!)
                SKPaymentQueue.defaultQueue().finishTransaction(transaction)
                break;
            default:
                break;
            }
        }
    }
        
    func paymentQueueRestoreCompletedTransactionsFinished(queue: SKPaymentQueue) {
        dispatch_group_leave(group)
    }
        
    func paymentQueue(queue: SKPaymentQueue, restoreCompletedTransactionsFailedWithError error: NSError) {
        self.error = error
        dispatch_group_leave(group)
    }
    
    var error: NSError?
    var group: dispatch_group_t
    init(visitor: (name: String, id: String) -> (), group: dispatch_group_t) {
        self.group = group
        self.visitor = visitor
        super.init()
    }
}
