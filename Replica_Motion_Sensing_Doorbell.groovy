/**
*  Copyright 2022 bthrock
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*
*/
import org.json.JSONObject
def driverVer() {return "1.0"}

metadata {
    definition(name: "Replica Motion-Sensing Doorbell", namespace: "replica", author: "bthrock", importUrl:"https://raw.githubusercontent.com/TheMegamind/Replica-Drivers/main/replicaMotionSensingDoorbell.groovy")
    {
        capability "Actuator"
        capability "Configuration"
        capability "MotionSensor"
		attribute "doorbell", "string"
        capability "Refresh"
		capability "Battery"
		command "testRules"
		attribute "lastMotion", "string"
		attribute "lastRing", "string"
        attribute "healthStatus", "enum", ["offline", "online"]
    }
    preferences {   
		input ("textEnable", "bool", 
			   title: "Enable descriptionText logging",
			   defaultValue: true)
		input ("logEnable", "bool",
			   title: "Enable debug logging",
			   defaultValue: false)
    }
}

def installed() {
	initialize()
}

def updated() {
	initialize()
}

def initialize() {
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers()))
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands()))
}

def configure() {
	logInfo("configure: configured default rules")
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
Map getReplicaCommands() {
    return ([
//		"push":[],	//	Causes error.  Actual return is a value "button".
		"setRing":[[name:"button", type: "ENUM"]],
		"setBattery":[[name:"battery", type:"integer"]],
		"setMotionValue":[[name:"motion*",type:"ENUM"]], 
//		"setMotionActive":[],"setMotionInactive":[],		//	not necessary
		"setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]
	])
}

//	Test will allow testing ring and motion in rules / audio notification.
//	It does not test the functions of the doorbell itself.
def testRules() {
	setMotionValue("active")
	pauseExecution(10000)
	setRing("pushed")
	setMotionValue("inactive")
}

def setRing(button) {
	if (device.currentValue("doorbell") != button) {
		sendEvent(name: "doorbell", value: button)
		if (button == "pushed") {
			def ringTime = new Date()
			sendEvent(name: "lastRing", value: ringTime)
		}
	}
	if (button == "pushed") {
		runIn(5, deviceRefresh)
		runIn(15, checkRing)
	}
	logInfo("setRing: [doorbell: ${button}]")
}

def checkRing() {
	if (device.currentValue("doorbell") == "pushed") {
		sendEvent(name: "doorbell", value: "up")
		logInfo("checkRing: [doorbell: up]")
	}
}

def setBattery(battery) {
	sendEvent(name: "battery", value: battery, unit: "%")
	logDebug("setBattery: [battery: ${battery}]")
}

def setMotionValue(value) {
	sendEvent(name: "motion", value: value)
	def motionTime = new Date()
	if (value == "active") {
		sendEvent(name: "lastMotion", value: motionTime)
	}
	logInfo("setMotionValue: [motion: ${value}]")
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value)
}

def deviceRefresh() {
	sendCommand("deviceRefresh")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "refresh":[], "deviceRefresh": [] ])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
	return """{"version":1,"components":[{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"command":{"name":"setBattery","label":"command: setBattery(battery)","type":"command","parameters":[{"name":"battery","type":"integer"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"ActivityState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"motionSensor","attribute":"motion","label":"attribute: motion.*"},"command":{"name":"setMotionValue","label":"command: setMotionValue(motion*)","type":"command","parameters":[{"name":"motion*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.*"},"command":{"name":"setRing","label":"command: setRing(button)","type":"command","parameters":[{"name":"button","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"name":"deviceRefresh","label":"command: deviceRefresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger","disableStatus":true}]}"""
}

def logInfo(msg) { 
	if (textEnable) {
		log.info "${device.displayName}-${driverVer()}: ${msg}"
	}
}

def debugLogOff() {
	if (logEnable) {
		device.updateSetting("logEnable", [type:"bool", value: false])
	}
	logInfo("debugLogOff")
}

def logDebug(msg) {
	if (logEnable) {
		log.debug "${device.displayName}-${driverVer()}: ${msg}"
	}
}

def logWarn(msg) { log.warn "${device.displayName}-${driverVer()}: ${msg}" }
