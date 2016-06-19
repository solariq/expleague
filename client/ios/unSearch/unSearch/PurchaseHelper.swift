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
    var queue: [String: [(rc: PurchaseResult) -> ()]] = [:]
    
    func productsChanged() {
        QObject.notify(#selector(self.productsChanged), self)
    }
    
    func request(id: String, callback: (rc: PurchaseResult) -> ()) {
        if let val = products[id] {
            if let product = val {
                if queue[id] == nil {
                    queue[id] = []
                }
                queue[id]?.append(callback)
                SKPaymentQueue.defaultQueue().addPayment(SKPayment(product: product))
            }
            else {
                callback(rc: .Error)
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
            switch transaction.transactionState {
            case .Purchased, .Restored:
                let callback = self.queue[transaction.payment.productIdentifier]?.removeFirstOrNone()
                callback?(rc: .Accepted)
                SKPaymentQueue.defaultQueue().finishTransaction(transaction)
                break;
            case .Failed:
                let callback = self.queue[transaction.payment.productIdentifier]?.removeFirstOrNone()
                callback?(rc: .Rejected)
                SKPaymentQueue.defaultQueue().finishTransaction(transaction)
                break;
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
