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

import unSearchCore

enum PurchaseResult: Int {
    case accepted
    case rejected
    case error
}

class PurchaseHelper: NSObject {
    static let instance = PurchaseHelper()
    
    var products: [String: SKProduct?] = [:]
    var queue: [String: [(_ rc: PurchaseResult, _ transactionId: String?) -> ()]] = [:]
    
    func productsChanged() {
        QObject.notify(#selector(self.productsChanged), self)
    }
    
    func request(_ id: String, callback: @escaping (_ rc: PurchaseResult, _ transactionId: String?) -> ()) {
        if let val = products[id] {
            if let product = val {
                if queue[id] == nil {
                    queue[id] = []
                }
                queue[id]?.append(callback)
                SKPaymentQueue.default().add(SKPayment(product: product))
            }
            else {
                callback(.error, nil)
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
    
    func register(_ ids: [String]) {
        var remaining = ids
        for id in ids {
            if (products[id] != nil) {
                _ = remaining.removeOne(id)
            }
        }
        guard !remaining.isEmpty else {
            return
        }
        let request = SKProductsRequest(productIdentifiers: Set<String>(remaining))
        request.delegate = self
        request.start()
    }
    
    static func visitTransactions(visitor: @escaping (_ name: String, _ id: String)->(), completion: @escaping (NSError?) -> ()) {
        let group = DispatchGroup()
        let observer = RestoreTransactionsObserver(visitor: visitor, group: group)
        group.enter()
        SKPaymentQueue.default().add(observer)
        SKPaymentQueue.default().restoreCompletedTransactions()
        
        group.notify(queue: DispatchQueue.main) {
            SKPaymentQueue.default().remove(observer)
            completion(observer.error)
        }
    }
    
    var reachability: Reachability!
    override init() {
        super.init()
        SKPaymentQueue.default().add(self)
        reachability = Reachability()
        reachability.whenReachable = {r in
            self.products.forEach { (id: String, product: SKProduct?) -> () in
                if (product == nil) {
                    self.products.removeValue(forKey: id)
                }
            }
        }
        do {
            try reachability.startNotifier()
        }
        catch {
            DataController.shared().activeProfile?.log("Unable to register reachability: \(error)")
        }
    }
}

extension PurchaseHelper: SKPaymentTransactionObserver {
    func paymentQueue(_ queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        print("\(transactions.count) payment transactions finished")
        for transaction in transactions {
            print("\t\(transaction.payment.productIdentifier) -> \(transaction.transactionState)")
            switch transaction.transactionState {
            case .purchased, .restored:
                let callback = self.queue[transaction.payment.productIdentifier]?.removeFirstOrNone()
                callback?(.accepted, transaction.transactionIdentifier)
                SKPaymentQueue.default().finishTransaction(transaction)
            case .failed:
                let callback = self.queue[transaction.payment.productIdentifier]?.removeFirstOrNone()
                callback?(.rejected, nil)
                SKPaymentQueue.default().finishTransaction(transaction)
            default:
                break;
            }
        }
    }
}

extension PurchaseHelper: SKProductsRequestDelegate {
    func productsRequest(_ request: SKProductsRequest, didReceive response: SKProductsResponse) {
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
    let visitor: (_ name: String, _ id: String) -> ()
    @objc func paymentQueue(_ queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        for transaction in transactions {
            switch transaction.transactionState {
            case .restored:
                visitor(transaction.payment.productIdentifier, transaction.original!.transactionIdentifier!)
                SKPaymentQueue.default().finishTransaction(transaction)
                break;
            default:
                break;
            }
        }
    }
        
    func paymentQueueRestoreCompletedTransactionsFinished(_ queue: SKPaymentQueue) {
        group.leave()
    }
        
    func paymentQueue(_ queue: SKPaymentQueue, restoreCompletedTransactionsFailedWithError error: Error) {
        self.error = error as NSError?
        group.leave()
    }
    
    var error: NSError?
    var group: DispatchGroup
    init(visitor: @escaping (_ name: String, _ id: String) -> (), group: DispatchGroup) {
        self.group = group
        self.visitor = visitor
        super.init()
    }
}
