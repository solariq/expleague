//
//  Notifications.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 26/05/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import NotificationCenter

class Notifications {
    static func unableToCommunicate(incoming: Int, outgoing: Int) -> UILocalNotification {
        let notification = UILocalNotification()
//        notification.fireDate = NSDate(timeIntervalSinceNow: 10)
        notification.fireDate = NSDate(timeIntervalSinceNow: 5 * 60)
        notification.alertBody = "Не удалось доставить \(incoming > 0 ? "\(incoming) входящих" : "")\(incoming > 0 && outgoing > 0 ? " и " : "")\(outgoing > 0 ? "\(outgoing) исходящих" : ""). Войдите в приложение, и мы попробуем еще раз"
        notification.alertAction = "Cейчас"
        notification.soundName = "owl.wav"
        UIApplication.sharedApplication().scheduleLocalNotification(notification)
        return notification
    }
    
    static func notifyBestAnswer(aow: ExpLeagueOrder, title: String) {
        guard UIApplication.sharedApplication().applicationState != .Active else {
            return
        }
        let notification = UILocalNotification()
        notification.alertBody = "Новый ответ недели на тему '\(title)'\", "
        notification.soundName = "owl.wav"
        notification.userInfo = ["order" : aow.id]
        UIApplication.sharedApplication().scheduleLocalNotification(notification)
    }
    
    static func notifyExpertFound(order: ExpLeagueOrder) {
        guard UIApplication.sharedApplication().applicationState != .Active else {
            return
        }
        let notification = UILocalNotification()
        notification.alertBody = "Эксперт найден! Для Вас работает \(order.expert!.name)"
        notification.soundName = "owl.wav"
        notification.userInfo = ["order" : order.id]
        UIApplication.sharedApplication().scheduleLocalNotification(notification)
    }
    
    static func notifyAnswerReceived(order: ExpLeagueOrder) {
        guard UIApplication.sharedApplication().applicationState != .Active else {
            return
        }
        let notification = UILocalNotification()
        notification.alertBody = "Получен ответ на задание от \(order.experts.last?.name ?? "unknown"): \(order.shortAnswer)"
        notification.soundName = "owl.wav"
        notification.userInfo = ["order" : order.id]
        UIApplication.sharedApplication().scheduleLocalNotification(notification)
    }
    
    static func notifyMessageReceived(order: ExpLeagueOrder, message: ExpLeagueMessage) {
        guard UIApplication.sharedApplication().applicationState != .Active else {
            return
        }
        let notification = UILocalNotification()
        notification.alertBody = "Получено сообщение от \(order.expert!.name): '\(message.body!)'"
        notification.soundName = "owl.wav"
        notification.userInfo = ["order" : order.id]
        UIApplication.sharedApplication().scheduleLocalNotification(notification)
    }
}