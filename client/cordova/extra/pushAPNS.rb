#! /usr/bin/env ruby
require 'rubygems'
require 'pushmeup'


APNS.host = 'gateway.sandbox.push.apple.com' 
APNS.port = 2195 
APNS.pem  = 'apns_development.pem'
APNS.pass = 'B6p4wHWQycOB'

device_token = '833a174edf12d00d723467caa545957f177730d8a4f54f2fb3b05f8a50fcaad9'
# APNS.send_notification(device_token, 'Hello iPhone!' )
APNS.send_notification(device_token, :alert => 'PushPlugin works!!', :badge => 1, :sound => 'beep.wav')
