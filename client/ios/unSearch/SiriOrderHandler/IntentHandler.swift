//
//  IntentHandler.swift
//  SiriOrderHandler
//
//  Created by Igor E. Kuralenok on 28.10.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Intents
import MapKit

import unSearchCore

class IntentHandler: INExtension, INSendMessageIntentHandling {
    override func handler(for intent: INIntent) -> Any {
        return self
    }
    
    // MARK: - INSendMessageIntentHandling
    
    func resolveRecipients(forSendMessage intent: INSendMessageIntent, with completion: @escaping ([INPersonResolutionResult]) -> Void) {
        if let recipients = intent.recipients {
            // If no recipients were provided we'll need to prompt for a value.
            var resolutionResults = [INPersonResolutionResult]()
            for recipient in recipients {
                let matchingContacts = [recipient] // Implement your contact matching logic here to create an array of matching contacts
                switch matchingContacts.count {
                case 2...Int.max:
                    // We need Siri's help to ask user to pick one from the matches.
                    resolutionResults += [INPersonResolutionResult.disambiguation(with: matchingContacts)]
                    
                case 1:
                    // We have exactly one matching contact
                    resolutionResults += [INPersonResolutionResult.success(with: recipient)]
                    
                case 0:
                    // We have no contacts matching the description provided
                    resolutionResults += [INPersonResolutionResult.unsupported()]
                    
                default:
                    break
                }
            }
            completion(resolutionResults)
        }
    }
    
    func resolveContent(forSendMessage intent: INSendMessageIntent, with completion: @escaping (INStringResolutionResult) -> Void) {
        if let text = intent.content, !text.isEmpty {
            completion(INStringResolutionResult.success(with: text))
        } else {
            completion(INStringResolutionResult.needsValue())
        }
    }
    
    // Once resolution is completed, perform validation on the intent and provide confirmation (optional).
    
    func confirm(sendMessage intent: INSendMessageIntent, completion: @escaping (INSendMessageIntentResponse) -> Void) {
        // Verify user is authenticated and your app is ready to send a message.
        let userActivity = NSUserActivity(activityType: NSStringFromClass(INSendMessageIntent.self))
        let response: INSendMessageIntentResponse
        if DataController.shared().activeProfile == nil {
            response = INSendMessageIntentResponse(code: .failure, userActivity: userActivity)
        }
        else {
            response = INSendMessageIntentResponse(code: .ready, userActivity: userActivity)
        }
        
        completion(response)
    }
    
    // Handle the completed intent (required).
    
    func handle(sendMessage intent: INSendMessageIntent, completion: @escaping (INSendMessageIntentResponse) -> Void) {
        // Implement your application logic to send a message here.
        let userActivity = NSUserActivity(activityType: NSStringFromClass(INSendMessageIntent.self))
        let response: INSendMessageIntentResponse
        if (intent.content != nil) {
            ExpLeagueProfile.active.placeOrder(topic: "Запрос из Siri: " + intent.content!, urgency: "day", local: false, location: DataController.shared().currentLocation(), experts: [], images: [])
            response = INSendMessageIntentResponse(code: .success, userActivity: userActivity)
        }
        else {
            response = INSendMessageIntentResponse(code: .failure, userActivity: userActivity)
        }
        completion(response)
    }
    
    override init() {
        super.init()
        DataController.shared().resume()
    }
    
    deinit {
        DataController.shared().suspend()
    }
}
