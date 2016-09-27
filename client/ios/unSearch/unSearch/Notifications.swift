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
    static func notifyBestAnswer(_ aow: ExpLeagueOrder, title: String) {
        guard UIApplication.shared.applicationState != .active else {
            return
        }
        let notification = UILocalNotification()
        notification.alertBody = "Пятница — время для ответа недели: '\(title)'"
        notification.soundName = "owl.wav"
        notification.userInfo = ["order" : aow.id]
        UIApplication.shared.scheduleLocalNotification(notification)
    }
    
    static func notifyExpertFound(_ order: ExpLeagueOrder) {
        guard UIApplication.shared.applicationState != .active else {
            return
        }
        let notification = UILocalNotification()
        notification.alertBody = "Эксперт найден! Для Вас работает \(order.activeExpert?.name ?? "эксперт")"
        notification.soundName = "owl.wav"
        notification.userInfo = ["order" : order.id]
        UIApplication.shared.scheduleLocalNotification(notification)
    }
    
    static func notifyAnswerReceived(_ order: ExpLeagueOrder, answer: ExpLeagueMessage) {
        guard UIApplication.shared.applicationState != .active else {
            return
        }
        let notification = UILocalNotification()
        notification.alertBody = "Получен ответ на задание от \(answer.expert?.name ?? "эксперта"): \(order.shortAnswer)"
        notification.soundName = "owl.wav"
        notification.userInfo = ["order" : order.id]
        UIApplication.shared.scheduleLocalNotification(notification)
    }
    
    static func notifyMessageReceived(_ order: ExpLeagueOrder, message: ExpLeagueMessage) {
        guard UIApplication.shared.applicationState != .active else {
            return
        }
        let notification = UILocalNotification()
        notification.alertBody = "Получено сообщение от \(message.expert?.name ?? "эксперта"): '\(message.body!)'"
        notification.soundName = "owl.wav"
        notification.userInfo = ["order" : order.id]
        UIApplication.shared.scheduleLocalNotification(notification)
        print("Message notification sent")
    }
}
