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
    static func notifyBestAnswer(aow: ExpLeagueOrder, title: String) {
        guard UIApplication.sharedApplication().applicationState != .Active else {
            return
        }
        let notification = UILocalNotification()
        notification.alertBody = "Пятница — время для ответа недели: '\(title)'"
        notification.soundName = "owl.wav"
        notification.userInfo = ["order" : aow.id]
        UIApplication.sharedApplication().scheduleLocalNotification(notification)
    }
    
    static func notifyExpertFound(order: ExpLeagueOrder) {
        guard UIApplication.sharedApplication().applicationState != .Active else {
            return
        }
        let notification = UILocalNotification()
        notification.alertBody = "Эксперт найден! Для Вас работает \(order.activeExpert?.name ?? "эксперт")"
        notification.soundName = "owl.wav"
        notification.userInfo = ["order" : order.id]
        UIApplication.sharedApplication().scheduleLocalNotification(notification)
    }
    
    static func notifyAnswerReceived(order: ExpLeagueOrder, answer: ExpLeagueMessage) {
        guard UIApplication.sharedApplication().applicationState != .Active else {
            return
        }
        let notification = UILocalNotification()
        notification.alertBody = "Получен ответ на задание от \(answer.expert?.name ?? "эксперта"): \(order.shortAnswer)"
        notification.soundName = "owl.wav"
        notification.userInfo = ["order" : order.id]
        UIApplication.sharedApplication().scheduleLocalNotification(notification)
    }
    
    static func notifyMessageReceived(order: ExpLeagueOrder, message: ExpLeagueMessage) {
        guard UIApplication.sharedApplication().applicationState != .Active else {
            return
        }
        let notification = UILocalNotification()
        notification.alertBody = "Получено сообщение от \(message.expert?.name ?? "эксперта"): '\(message.body!)'"
        notification.soundName = "owl.wav"
        notification.userInfo = ["order" : order.id]
        UIApplication.sharedApplication().scheduleLocalNotification(notification)
        print("Message notification sent")
    }
}