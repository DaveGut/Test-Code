/**
*  Copyright 2023 Bloodtick
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
@SuppressWarnings('unused')
public static String version() {return "1.2.1"}

metadata 
{
    definition(name: "Replica Raw Developement", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaRawDevelopement.groovy")
    {
		//	===== Required for all Raw Development Drivers
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        attribute "healthStatus", "enum", ["offline", "online"]
		//	===== End required definitions
    }
    preferences {   
		input ("deviceIp", "text", title: "Samsung TV Ip", defaultValue: "")
    }
}

def installed() {
	initialize()
}

def updated() {
	initialize()
	setAutoAttributes()
}

//	===== BOILERPLATE. Required for all raw developments.  Use caution with any changes.=====
def initialize() {
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers()))
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands()))
}

def configure() {
    log.info "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
//	This can be added to if there are some direct (non-raw) commands implemented
Map getReplicaCommands() {
    return (["replicaEvent":[[name:"parent*",type:"OBJECT"],[name:"event*",type:"JSON_OBJECT"]], 
			 "replicaStatus":[[name:"parent*",type:"OBJECT"],[name:"event*",type:"JSON_OBJECT"]], 
			 "replicaHealth":[[name:"parent*",type:"OBJECT"],[name:"health*",type:"JSON_OBJECT"]],
			 "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

void replicaEvent(def parent=null, Map event=null) {
//    log.info "replicaEvent"
    if(parent) log.info parent?.getLabel()
//    if(event) log.info event
	def eventData = event.deviceEvent
	"parse_${event.deviceEvent.componentId}"(event.deviceEvent)
}

void replicaStatus(def parent=null, Map status=null) {
    log.info "replicaStatus"
    if(parent) log.info parent?.getLabel()
    if(status) log.info status   
}

void replicaHealth(def parent=null, Map health=null) {
    log.info "replicaHealth"
    if(parent) log.info parent?.getLabel()
    if(health) log.info health   
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
//	This can be added to if there are some direct (non-raw) commands implemented
Map getReplicaTriggers() {
    return ([ "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
}

void refresh() {
    sendCommand("refresh")
}

//	This can be added to if there are some direct (non-raw) commands implemented
String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true}]}"""
}
//	===== END BOILERPLATE =====
def setAutoAttributes() {
	//	All auto populated attributes should be listed here.  Then in the parse method,
	//	it is simple matter to send the event.  Events that are duplicated in the component
	//	or require special handling should NOT be included here.  They will be manually
	//	handled in the parse_component method.  Example below based on partial Samsung TV.
	//	If you have more than one component/capability with the same attribute names, these
	//	will have to be handled manually or within child device drivers.
	state.autoAttributes = ["switch", "volume", "mute", "playbackStatus"]
}
//	For each Component, create a method (parse{componentName} to update attributes and states from the event.
//	For child devices you could send the event for handling there instead.
def parse_main(event) {
	//  You only have to parse relevant events.  The others are ignored in the default of the switch.
	log.info "$event.componentId: $event"
	if (event.stateChange == true) {
		if (state.autoAttributes.contains(event.attribute)) {
			sendEvent(name: event.attribute, value: event.value, unit: event.unit)
		} else {
			switch(event.attribute) {
				case "supportedInputDevices":
				if (event.capability == "samsungvd.mediaInputSource") {
					log.trace "samsungvd.mediaInputSource.supportedInputDevices: $event"
				} else {
					state.supportInputDevices = event.value
				}
				break
				case "inputSource":
				if (event.capability == "samsungvd.inputSource") {
					log.trace "samsungvd.inputSource.inputSource: $event"
				} else {
					state.supportInputDevices = event.value
				}
				break
				default:
					log.info "unhandledEvent: $event"
				break
			}
		}
	}
}
