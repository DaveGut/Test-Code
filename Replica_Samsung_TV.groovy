/*	HubiThings Replica Soundbar Driver
	HubiThings Replica Applications Copyright 2023 by Bloodtick
	Replica Color Bulb Copyright 2023 by Dave Gutheinz

	Licensed under the Apache License, Version 2.0 (the "License"); 
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at:
	      http://www.apache.org/licenses/LICENSE-2.0
	Unless required by applicable law or agreed to in writing, software 
	distributed under the License is distributed on an "AS IS" BASIS, 
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
	implied. See the License for the specific language governing 
	permissions and limitations under the License.

Sample Audio Notifications Streams are at the bottom of this driver.

Ver 1.1 Changes
a.	Check if attribute has changed before sending event or completing
	follow-on methods.
b.	Move websocket, TV app to library (shared with non-replica driver).
c.	Update refreshAttributes for clarity and commonality.
d.	Removed test code for audio notify.  Too many issues.

Issues with this driver: Contact davegut via Private Message on the
Hubitat Community site: https://community.hubitat.com/


Add parse to Replica Samsung Soundbar due to change in AudioNotification.
==========================================================================*/
import groovy.json.JsonOutput
def driverVer() { return "1.0" }
def platform() { return "HubiThings" }

metadata {
	definition (name: "Replica Samsung TV",
				namespace: "replica",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/HubiThingsReplica%20Drivers/Replica_Samsung_TV.groovy"
			   ){
		capability "Actuator"
		capability "Configuration"
		capability "Refresh"
		attribute "healthStatus", "enum", ["offline", "online"]
		capability "Switch"
		capability "SamsungTV"
			command "showMessage", [[name: "Not Implemented"]]
			command "toggleSoundMode"
			command "togglePictureMode"
		command "setLevel", ["number"]
			attribute "level", "NUMBER"
		capability "MediaInputSource"	//	new
			command "hdmi"
			attribute "inputSource", "string"
		capability "MediaTransport"
			command "fastForward"
			command "fastBack"
		capability "Configuration"
		//	TV Channel/data
		command "setTvChannel", ["number"]
			attribute "tvChannel", "string"
			attribute "tvChannelName", "string"
			attribute "trackDescription", "string"
			command "channelUp"
			command "channelDown"
			command "channelList"
		//	Websocket Keys
		command "close"
		attribute "wsStatus", "string"
		command "sendKey", ["string"]
		//	Art / Ambient	
		command "artMode"
			attribute "artModeStatus", "string"
		command "ambientMode"
		//	Cursor and Entry Control
		command "arrowLeft"
		command "arrowRight"
		command "arrowUp"
		command "arrowDown"
		command "enter"
		command "numericKeyPad"
		//	Menu Access
		command "home"
		command "menu"
		command "guide"
		command "info"
		//	Navigation Commands
		command "exit"
		command "Return"
		//	Application Functions
		command "appOpenByName", ["string"]
		command "appOpenByCode", ["string"]
			attribute "currentApp", "string"
		command "appClose"
		//	Dashboard Support
		capability "PushableButton"
		capability "Variable"
		command "eventHandler", [[name: "For App Use Only"]]
	}
	preferences {
		input ("deviceIp", "string", title: "Device IP. Required!")
		if (deviceIp) {
			input ("tvPwrOnMode", "enum", title: "TV Startup Display", 
				   options: ["ART_MODE", "Ambient", "none"], defaultValue: "none")
			input ("findAppCodes", "bool", title: "Scan for App Codes (use rarely)", defaultValue: false)
			input ("resetAppCodes", "bool", title: "Delete and Rescan for App Codes (use rarely)", defaultValue: false)
			input ("logEnable", "bool",  title: "Enable debug logging for 30 minutes", defaultValue: false)
			input ("infoLog", "bool", title: "Enable information logging",defaultValue: true)
			input ("traceLog", "bool", title: "Enable trace logging as directed by developer", defaultValue: false)
		}
	}
}

//	===== Installation, setup and update =====
def installed() {
	state.token = "12345678"
	def tokenSupport = "false"
	sendEvent(name: "wsStatus", value: "closed")
	sendEvent(name: "numberOfButtone", value: 45)
	sendEvent(name: "currentApp", value: " ")
	state.appData = [:]
	runIn(1, updated)
}

def updated() {
	unschedule()
	close()
	def updStatus = [:]
	initialize()
	if (!deviceIp) {
		logWarn("\n\n\t\t<b>Enter the deviceIp and Save Preferences</b>\n\n")
		updStatus << [status: "ERROR", data: "Device IP not set."]
	} else {
		updStatus << [getDeviceData: configureLan()]
		if (!getDataValue("driverVersion") || getDataValue("driverVersion") != driverVer()) {
			updateDataValue("driverVersion", driverVer())
			updStatus << [driverVer: driverVer()]
		}
		if (resetAppCodes) {
			state.appData = [:]
			runIn(5, updateAppCodes)
		} else if (findAppCodes) {
			runIn(5, updateAppCodes)
		}
	}
	if (logEnable) { runIn(1800, debugLogOff) }
	if (traceLog) { runIn(600, traceLogOff) }
	updStatus << [logEnable: logEnable, infoLog: infoLog, traceLog: traceLog]
	
	runIn(10, refresh)
	pauseExecution(5000)
	listAttributes(true)
	logInfo("updated: ${updStatus}")
}

def initialize() {
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers()))
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands()))
	logInfo("initialize: initialize device-specific data")
}

Map getReplicaCommands() {
    return (["replicaEvent":[[name:"parent*",type:"OBJECT"],[name:"event*",type:"JSON_OBJECT"]], 
			 "replicaStatus":[[name:"parent*",type:"OBJECT"],[name:"event*",type:"JSON_OBJECT"]], 
			 "replicaHealth":[[name:"parent*",type:"OBJECT"],[name:"health*",type:"JSON_OBJECT"]],
			 "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

Map getReplicaTriggers() {
	Map triggers = [ 
		refresh:[], deviceRefresh: [],
		setVolume: [[name:"volume*", type: "NUMBER"]], 
		setMute: [[name:"state*", type: "string"]],
		setPictureMode:[[name:"mode*", type:"string"]], 
		setInputSource:[[name:"inputName*", type: "string"]],
		setSoundMode:[[name:"mode*", type:"string"]],
		setTvChannel: [[name:"tvChannel*", type: "number"]]
	]
	return triggers
}

def configure() {
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
	logInfo "configure (default device data)"
}

String getReplicaRules() {
	return """{"version":1,"components":[
{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},
{"trigger":{"name":"setVolume","label":"command: setVolume(volume*)","type":"command","parameters":[{"name":"volume*","type":"NUMBER"}]},"command":{"name":"setVolume","arguments":[{"name":"volume","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"audioVolume","label":"command: setVolume(volume*)"},"type":"hubitatTrigger"},
{"trigger":{"name":"setMute","label":"command: setMute(state*)","type":"command","parameters":[{"name":"state*","type":"string"}]},"command":{"name":"setMute","arguments":[{"name":"state","optional":false,"schema":{"title":"MuteState","type":"string","enum":["muted","unmuted"]}}],"type":"command","capability":"audioMute","label":"command: setMute(state*)"},"type":"hubitatTrigger"},
{"trigger":{"name":"setPictureMode","label":"command: setPictureMode(mode*)","type":"command","parameters":[{"name":"mode*","type":"string"}]},"command":{"name":"setPictureMode","arguments":[{"name":"mode","optional":false,"schema":{"type":"string"}}],"type":"command","capability":"custom.picturemode","label":"command: setPictureMode(mode*)"},"type":"hubitatTrigger"},
{"trigger":{"name":"setSoundMode","label":"command: setSoundMode(mode*)","type":"command","parameters":[{"name":"mode*","type":"string"}]},"command":{"name":"setSoundMode","arguments":[{"name":"mode","optional":false,"schema":{"type":"string"}}],"type":"command","capability":"custom.soundmode","label":"command: setSoundMode(mode*)"},"type":"hubitatTrigger"},
{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"},
{"trigger":{"name":"setInputSource","label":"command: setInputSource(inputName*)","type":"command","parameters":[{"name":"inputName*","type":"string"}]},"command":{"name":"setInputSource","arguments":[{"name":"id","optional":false,"schema":{"type":"string"}}],"type":"command","capability":"samsungvd.mediaInputSource","label":"command: setInputSource(id*)"},"type":"hubitatTrigger"},
{"trigger":{"name":"setTvChannel","label":"command: setTvChannel(tvChannel*)","type":"command","parameters":[{"name":"tvChannel*","type":"number"}]},"command":{"name":"setTvChannel","arguments":[{"name":"tvChannel","optional":false,"schema":{"title":"String","type":"string","maxLength":255}}],"type":"command","capability":"tvChannel","label":"command: setTvChannel(tvChannel*)"},"type":"hubitatTrigger"},
{"trigger":{"name":"deviceRefresh","label":"command: deviceRefresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"}]}"""
}

def configureLan() {
	def respData = [:]
	def tvData = [:]
	try{
		httpGet([uri: "http://${deviceIp}:8001/api/v2/", timeout: 5]) { resp ->
			tvData = resp.data
		}
	} catch (error) {
		tvData << [status: "error", data: error]
	}
	if (!tvData.status) {
		def wolMac = tvData.device.wifiMac.replaceAll(":", "").toUpperCase()
		updateDataValue("wolMac", wolMac)
		def frameTv = "false"
		if (tvData.device.FrameTVSupport) {
			frameTv = tvData.device.FrameTVSupport
		}
		updateDataValue("frameTv", frameTv)
		if (tvData.device.TokenAuthSupport) {
			tokenSupport = tvData.device.TokenAuthSupport
			updateDataValue("tokenSupport", tokenSupport)
		}
		def uuid = tvData.device.duid.substring(5)
		updateDataValue("uuid", uuid)
		respData << [status: "OK", wolMac: wolMac, frameTv: frameTv, 
					 tokenSupport: tokenSupport, uuid: uuid]
		sendEvent(name: "artModeStatus", value: "none")
		if (frameTv == "true") {
			def data = [request:"get_artmode_status",
						id: uuid]
			data = JsonOutput.toJson(data)
			artModeCmd(data)
		}
	} else {
		respData << tvData
	}
	return respData
}

//	===== Event Parse Interface s=====
void replicaStatus(def parent=null, Map status=null) {
	def logData = [parent: parent, status: status]
	if (state.refreshAttributes) {
		refreshAttributes(status.components.main)
	}
	logTrace("replicaStatus: ${logData}")
}

def refreshAttributes(mainData) {
	logDebug("refreshAttributes: ${mainData}")
	def designCapabilities = [
		"mediaPlayback", "mediaInputSource", "switch", "audioVolume",
		"tvChannel", "custom.picturemode", "custom.soundmode", "audioMute"]
	designCapabilities.each { capability ->
		def attributes = mainData[capability]
		attributes.each { attribute ->
			parse_main([capability: capability,
						attribute: attribute.key,
						value: attribute.value.value,
						unit: attribute.value.unit])
		}
	}
}

void replicaHealth(def parent=null, Map health=null) {
	if(parent) { logInfo("replicaHealth: ${parent?.getLabel()}") }
	if(health) { logInfo("replicaHealth: ${health}") }
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

void replicaEvent(def parent=null, Map event=null) {
	def eventData = event.deviceEvent
	try {
		"parse_${event.deviceEvent.componentId}"(event.deviceEvent)
	} catch (err) {
		logWarn("replicaEvent: [event = ${event}, error: ${err}")
	}
}

def parse_main(event) {
	switch(event.attribute) {
		case "mute":
		case "pictureMode":
		case "soundMode":
		case "tvChannel":
		case "switch":
			createEvent(event.attribute, event.value)
			break
			createEvent(event.attribute, event.value)
			break
		case "volume":
			createEvent(event.attribute, event.value)
			createEvent("level", event.value)
			break
		case "inputSource":
			if (event.capability == "mediaInputSource") {
				createEvent("mediaInputSource", event.value)
			}
			break
		case "tvChannelName":
			createEvent(event.attribute, event.value)
			createEvent("trackDescription", event.value)
			break
		case "playbackStatus":
			createEvent("transportStatus", event.value)
			break
		case "supportedSoundModes":
			state.soundModes = event.value
			break
		case "supportedInputSources":
			state.inputSources = event.value
			break
		case "supportedPictureModes":
			state.pictureModes = event.value
			break
		default:
			logTrace("parse_main: [unhandledEvent: ${event}]")
			break
	}
	logTrace("parse_main: [event: ${event}]")
}

def createEvent(attribute, value, unit=null) {
	if (device.currentValue(attribute).toString() != value.toString()) {
		if (unit == null) {
			sendEvent(name: attribute, value: value)
		} else {
			sendEvent(name: attribute, value: value, unit: unit)
		}
		if (attribute == "switch") {
			if (value == "on") {
				getArtModeStatus()
				runIn(2, setPowerOnMode)
			} else {
				close()
			}
			runIn(5, deviceRefresh)
		} else if (attribute == "tvChannelName" && value.contains(".")) {
			getAppData(event.value)
		}
		logInfo("createEvent: [attribute: ${attribute}, value: ${value}, unit: ${umit}]")
	}
}

//	Used for any rule-based commands
private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
}

//	===== Samsung TV Commands =====
def refresh() {
	state.refreshAttributes = true
	sendCommand("deviceRefresh")
	pauseExecution(500)
	sendCommand("refresh")
}

def deviceRefresh() {
	sendCommand("deviceRefresh")
}

def on() {
	def wolMac = getDataValue("wolMac")
	def cmd = "FFFFFFFFFFFF$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac"
	wol = new hubitat.device.HubAction(
		cmd,
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "255.255.255.255:7",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING])
	sendHubCommand(wol)
}

def off() {
	logInfo("off: [frameTv: ${getDataValue("frameTv")}]")
	if (getDataValue("frameTv") == "true") {
		sendKey("POWER", "Press")
		pauseExecution(4000)
		sendKey("POWER", "Release")
	} else {
		sendKey("POWER")
	}
}

def setPowerOnMode() {
	logDebug("setPowerOnMode: [tvPwrOnMode: ${tvPwrOnMode}]")
	getArtModeStatus()
	pauseExecution(1000)
	switch (tvPwrOnMode) {
		case "ART_MODE":
			artMode()
			break
		case "Ambient":
			ambientMode()
			break
		default:
			break
	}
}

//	===== capability "SamsungTV"===== 
def setLevel(level) { setVolume(level) }

def setVolume(volume) {
	if (volume == null) { volume = device.currentValue("volume").toInteger() }
	if (volume < 0) { volume = 0 }
	else if (volume > 100) { volume = 100 }
	sendCommand("setVolume", volume)
	runIn(5, deviceRefresh)
}

def togglePictureMode() {
	def pictureModes = state.pictureModes
	if (pictureModes != null) {
		def totalModes = pictureModes.size()
		def currentMode = device.currentValue("pictureMode")
		def modeNo = pictureModes.indexOf(currentMode)
		def newModeNo = modeNo + 1
		if (newModeNo == totalModes) { newModeNo = 0 }
		def newPictureMode = pictureModes[newModeNo]
		setPictureMode(newPictureMode)
	}
}

def setPictureMode(pictureMode) {
	sendCommand("setPictureMode", pictureMode)
	runIn(5, deviceRefresh)
}

def toggleSoundMode() {
	def soundModes = state.soundModes
	if (soundModes != null) {
		def totalModes = soundModes.size()
		def currentMode = device.currentValue("soundMode")
		def modeNo = soundModes.indexOf(currentMode)
		def newModeNo = modeNo + 1
		if (newModeNo == totalModes) { newModeNo = 0 }
		def soundMode = soundModes[newModeNo]
		setSoundMode(soundMode)
	}
}

def setSoundMode(soundMode) { 
	sendCommand("setSoundMode", soundMode)
	runIn(5, deviceRefresh)
}

//	===== capability "MediaInputSource" =====
def setInputSource(inputSource) {
	sendCommand("setInputSource", inputSource)
}

//	===== TV Channel =====
def setTvChannel(tvChannel) {
	sendCommand("setTvChannel", tvChannel.toString())
	runIn(5, deviceRefresh)
}

//	===== DASHBOARD SUPPORT INTERFACE =====
def setVariable(appName) {
	sendEvent(name: "variable", value: appName)
	appOpenByName(appName)
}

def push(pushed) {
	logDebug("push: button = ${pushed}, trigger = ${state.triggered}")
	if (pushed == null) {
		logWarn("push: pushed is null.  Input ignored")
		return
	}
	pushed = pushed.toInteger()
	switch(pushed) {
		//	===== Physical Remote Commands =====
		case 2 : mute(); break
		case 3 : numericKeyPad(); break
		case 4 : Return(); break
		case 6 : artMode(); break
		case 7 : ambientMode(); break
		case 45: ambientmodeExit(); break
		case 8 : arrowLeft(); break
		case 9 : arrowRight(); break
		case 10: arrowUp(); break
		case 11: arrowDown(); break
		case 12: enter(); break
		case 13: exit(); break
		case 14: home(); break
		case 18: channelUp(); break
		case 19: channelDown(); break
		case 20: guide(); break
		case 21: volumeUp(); break
		case 22: volumeDown(); break
		//	===== Direct Access Functions
		case 23: menu(); break
		case 24: source(); break
		case 25: info(); break
		case 26: channelList(); break
		//	===== Other Commands =====
		case 35: hdmi(); break
		case 36: fastBack(); break
		case 37: fastForward(); break
		//	===== Application Interface =====
		case 38: appOpenByName("Browser"); break
		case 39: appOpenByName("YouTube"); break
		case 40: appOpenByName("RunNetflix"); break
		case 41: close()
		case 42: toggleSoundMode(); break
		case 43: togglePictureMode(); break
		case 44: appOpenByName(device.currentValue("variable")); break
		default:
			logDebug("push: Invalid Button Number!")
			break
	}
}

def parse(resp) {
	if (resp.toString().contains("mac:")) {
		upnpParse(resp)
	} else {
		parseWs(resp)
	}
}

//	===== Libraries =====




// ~~~~~ start include (1241) davegut.samsungTvWebsocket ~~~~~
library ( // library marker davegut.samsungTvWebsocket, line 1
	name: "samsungTvWebsocket", // library marker davegut.samsungTvWebsocket, line 2
	namespace: "davegut", // library marker davegut.samsungTvWebsocket, line 3
	author: "Dave Gutheinz", // library marker davegut.samsungTvWebsocket, line 4
	description: "Common Samsung TV Websocket Commands", // library marker davegut.samsungTvWebsocket, line 5
	category: "utilities", // library marker davegut.samsungTvWebsocket, line 6
	documentationLink: "" // library marker davegut.samsungTvWebsocket, line 7
) // library marker davegut.samsungTvWebsocket, line 8

import groovy.json.JsonOutput // library marker davegut.samsungTvWebsocket, line 10

//	== ART/Ambient Mode // library marker davegut.samsungTvWebsocket, line 12
def artMode() { // library marker davegut.samsungTvWebsocket, line 13
	def artModeStatus = device.currentValue("artModeStatus") // library marker davegut.samsungTvWebsocket, line 14
	def logData = [artModeStatus: artModeStatus, artModeWs: state.artModeWs] // library marker davegut.samsungTvWebsocket, line 15
	if (getDataValue("frameTv") != "true") { // library marker davegut.samsungTvWebsocket, line 16
		logData << [status: "Not a Frame TV"] // library marker davegut.samsungTvWebsocket, line 17
	} else if (artModeStatus == "on") { // library marker davegut.samsungTvWebsocket, line 18
		logData << [status: "artMode already set"] // library marker davegut.samsungTvWebsocket, line 19
	} else { // library marker davegut.samsungTvWebsocket, line 20
		if (state.artModeWs) { // library marker davegut.samsungTvWebsocket, line 21
			def data = [value:"on", // library marker davegut.samsungTvWebsocket, line 22
						request:"set_artmode_status", // library marker davegut.samsungTvWebsocket, line 23
						id: "${getDataValue("uuid")}"] // library marker davegut.samsungTvWebsocket, line 24
			data = JsonOutput.toJson(data) // library marker davegut.samsungTvWebsocket, line 25
			artModeCmd(data) // library marker davegut.samsungTvWebsocket, line 26
			logData << [status: "Sending artMode WS Command"] // library marker davegut.samsungTvWebsocket, line 27
		} else { // library marker davegut.samsungTvWebsocket, line 28
			sendKey("POWER") // library marker davegut.samsungTvWebsocket, line 29
			logData << [status: "Sending Power WS Command"] // library marker davegut.samsungTvWebsocket, line 30
			if (artModeStatus == "none") { // library marker davegut.samsungTvWebsocket, line 31
				logData << [NOTE: "SENT BLIND. Enable SmartThings interface!"] // library marker davegut.samsungTvWebsocket, line 32
			} // library marker davegut.samsungTvWebsocket, line 33
		} // library marker davegut.samsungTvWebsocket, line 34
		runIn(10, getArtModeStatus) // library marker davegut.samsungTvWebsocket, line 35
	} // library marker davegut.samsungTvWebsocket, line 36
	logInfo("artMode: ${logData}") // library marker davegut.samsungTvWebsocket, line 37
} // library marker davegut.samsungTvWebsocket, line 38

def getArtModeStatus() { // library marker davegut.samsungTvWebsocket, line 40
	if (getDataValue("frameTv") == "true") { // library marker davegut.samsungTvWebsocket, line 41
		if (state.artModeWs) { // library marker davegut.samsungTvWebsocket, line 42
			def data = [request:"get_artmode_status", // library marker davegut.samsungTvWebsocket, line 43
						id: "${getDataValue("uuid")}"] // library marker davegut.samsungTvWebsocket, line 44
			data = JsonOutput.toJson(data) // library marker davegut.samsungTvWebsocket, line 45
			artModeCmd(data) // library marker davegut.samsungTvWebsocket, line 46
		} else { // library marker davegut.samsungTvWebsocket, line 47
			refresh() // library marker davegut.samsungTvWebsocket, line 48
		} // library marker davegut.samsungTvWebsocket, line 49
	} // library marker davegut.samsungTvWebsocket, line 50
} // library marker davegut.samsungTvWebsocket, line 51

def artModeCmd(data) { // library marker davegut.samsungTvWebsocket, line 53
	def cmdData = [method:"ms.channel.emit", // library marker davegut.samsungTvWebsocket, line 54
				   params:[data:"${data}", // library marker davegut.samsungTvWebsocket, line 55
						   to:"host", // library marker davegut.samsungTvWebsocket, line 56
						   event:"art_app_request"]] // library marker davegut.samsungTvWebsocket, line 57
	cmdData = JsonOutput.toJson(cmdData) // library marker davegut.samsungTvWebsocket, line 58
	sendMessage("frameArt", cmdData) // library marker davegut.samsungTvWebsocket, line 59
} // library marker davegut.samsungTvWebsocket, line 60

def ambientMode() { // library marker davegut.samsungTvWebsocket, line 62
	sendKey("AMBIENT") // library marker davegut.samsungTvWebsocket, line 63
	runIn(10, refresh) // library marker davegut.samsungTvWebsocket, line 64
} // library marker davegut.samsungTvWebsocket, line 65

//	== Remote Commands // library marker davegut.samsungTvWebsocket, line 67
def mute() { // library marker davegut.samsungTvWebsocket, line 68
	sendKey("MUTE") // library marker davegut.samsungTvWebsocket, line 69
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 70
} // library marker davegut.samsungTvWebsocket, line 71

def unmute() { // library marker davegut.samsungTvWebsocket, line 73
	sendKey("MUTE") // library marker davegut.samsungTvWebsocket, line 74
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 75
} // library marker davegut.samsungTvWebsocket, line 76

def volumeUp() {  // library marker davegut.samsungTvWebsocket, line 78
	sendKey("VOLUP")  // library marker davegut.samsungTvWebsocket, line 79
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 80
} // library marker davegut.samsungTvWebsocket, line 81

def volumeDown() {  // library marker davegut.samsungTvWebsocket, line 83
	sendKey("VOLDOWN") // library marker davegut.samsungTvWebsocket, line 84
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 85
} // library marker davegut.samsungTvWebsocket, line 86

def play() { // library marker davegut.samsungTvWebsocket, line 88
	sendKey("PLAY") // library marker davegut.samsungTvWebsocket, line 89
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 90
} // library marker davegut.samsungTvWebsocket, line 91

def pause() { // library marker davegut.samsungTvWebsocket, line 93
	sendKey("PAUSE") // library marker davegut.samsungTvWebsocket, line 94
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 95
} // library marker davegut.samsungTvWebsocket, line 96

def stop() { // library marker davegut.samsungTvWebsocket, line 98
	sendKey("STOP") // library marker davegut.samsungTvWebsocket, line 99
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 100
} // library marker davegut.samsungTvWebsocket, line 101

def exit() { // library marker davegut.samsungTvWebsocket, line 103
	sendKey("EXIT") // library marker davegut.samsungTvWebsocket, line 104
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 105
} // library marker davegut.samsungTvWebsocket, line 106

def Return() { sendKey("RETURN") } // library marker davegut.samsungTvWebsocket, line 108

def fastBack() { // library marker davegut.samsungTvWebsocket, line 110
	sendKey("LEFT", "Press") // library marker davegut.samsungTvWebsocket, line 111
	pauseExecution(1000) // library marker davegut.samsungTvWebsocket, line 112
	sendKey("LEFT", "Release") // library marker davegut.samsungTvWebsocket, line 113
} // library marker davegut.samsungTvWebsocket, line 114

def fastForward() { // library marker davegut.samsungTvWebsocket, line 116
	sendKey("RIGHT", "Press") // library marker davegut.samsungTvWebsocket, line 117
	pauseExecution(1000) // library marker davegut.samsungTvWebsocket, line 118
	sendKey("RIGHT", "Release") // library marker davegut.samsungTvWebsocket, line 119
} // library marker davegut.samsungTvWebsocket, line 120

def arrowLeft() { sendKey("LEFT") } // library marker davegut.samsungTvWebsocket, line 122

def arrowRight() { sendKey("RIGHT") } // library marker davegut.samsungTvWebsocket, line 124

def arrowUp() { sendKey("UP") } // library marker davegut.samsungTvWebsocket, line 126

def arrowDown() { sendKey("DOWN") } // library marker davegut.samsungTvWebsocket, line 128

def enter() { sendKey("ENTER") } // library marker davegut.samsungTvWebsocket, line 130

def numericKeyPad() { sendKey("MORE") } // library marker davegut.samsungTvWebsocket, line 132

def home() { sendKey("HOME") } // library marker davegut.samsungTvWebsocket, line 134

def menu() { sendKey("MENU") } // library marker davegut.samsungTvWebsocket, line 136

def guide() { sendKey("GUIDE") } // library marker davegut.samsungTvWebsocket, line 138

def info() { sendKey("INFO") } // library marker davegut.samsungTvWebsocket, line 140

def source() {  // library marker davegut.samsungTvWebsocket, line 142
	sendKey("SOURCE") // library marker davegut.samsungTvWebsocket, line 143
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 144
} // library marker davegut.samsungTvWebsocket, line 145

def hdmi() { // library marker davegut.samsungTvWebsocket, line 147
	sendKey("HDMI") // library marker davegut.samsungTvWebsocket, line 148
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 149
} // library marker davegut.samsungTvWebsocket, line 150

def channelList() { sendKey("CH_LIST") } // library marker davegut.samsungTvWebsocket, line 152

def channelUp() {  // library marker davegut.samsungTvWebsocket, line 154
	sendKey("CHUP")  // library marker davegut.samsungTvWebsocket, line 155
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 156
} // library marker davegut.samsungTvWebsocket, line 157

def nextTrack() { channelUp() } // library marker davegut.samsungTvWebsocket, line 159

def channelDown() {  // library marker davegut.samsungTvWebsocket, line 161
	sendKey("CHDOWN")  // library marker davegut.samsungTvWebsocket, line 162
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 163
} // library marker davegut.samsungTvWebsocket, line 164

def previousTrack() { channelDown() } // library marker davegut.samsungTvWebsocket, line 166

def previousChannel() {  // library marker davegut.samsungTvWebsocket, line 168
	sendKey("PRECH")  // library marker davegut.samsungTvWebsocket, line 169
	runIn(5, deviceRefresh) // library marker davegut.samsungTvWebsocket, line 170
} // library marker davegut.samsungTvWebsocket, line 171

def showMessage() { logWarn("showMessage: not implemented") } // library marker davegut.samsungTvWebsocket, line 173

//	== WebSocket Communications / Parse // library marker davegut.samsungTvWebsocket, line 175
def sendKey(key, cmd = "Click") { // library marker davegut.samsungTvWebsocket, line 176
	key = "KEY_${key.toUpperCase()}" // library marker davegut.samsungTvWebsocket, line 177
	def data = [method:"ms.remote.control", // library marker davegut.samsungTvWebsocket, line 178
				params:[Cmd:"${cmd}", // library marker davegut.samsungTvWebsocket, line 179
						DataOfCmd:"${key}", // library marker davegut.samsungTvWebsocket, line 180
						TypeOfRemote:"SendRemoteKey"]] // library marker davegut.samsungTvWebsocket, line 181
	sendMessage("remote", JsonOutput.toJson(data) ) // library marker davegut.samsungTvWebsocket, line 182
} // library marker davegut.samsungTvWebsocket, line 183

def sendMessage(funct, data) { // library marker davegut.samsungTvWebsocket, line 185
	def wsStat = device.currentValue("wsStatus") // library marker davegut.samsungTvWebsocket, line 186
	logDebug("sendMessage: [wsStatus: ${wsStat}, function: ${funct}, data: ${data}, connectType: ${state.currentFunction}") // library marker davegut.samsungTvWebsocket, line 187
	logTrace("sendMessage: [wsStatus: ${wsStat}, function: ${funct}, data: ${data}, connectType: ${state.currentFunction}") // library marker davegut.samsungTvWebsocket, line 188
	if (wsStat != "open" || state.currentFunction != funct) { // library marker davegut.samsungTvWebsocket, line 189
		connect(funct) // library marker davegut.samsungTvWebsocket, line 190
		pauseExecution(600) // library marker davegut.samsungTvWebsocket, line 191
	} // library marker davegut.samsungTvWebsocket, line 192
	interfaces.webSocket.sendMessage(data) // library marker davegut.samsungTvWebsocket, line 193
	runIn(60, close) // library marker davegut.samsungTvWebsocket, line 194
} // library marker davegut.samsungTvWebsocket, line 195

def connect(funct) { // library marker davegut.samsungTvWebsocket, line 197
	logDebug("connect: function = ${funct}") // library marker davegut.samsungTvWebsocket, line 198
	def url // library marker davegut.samsungTvWebsocket, line 199
	def name = "SHViaXRhdCBTYW1zdW5nIFJlbW90ZQ==" // library marker davegut.samsungTvWebsocket, line 200
	if (getDataValue("tokenSupport") == "true") { // library marker davegut.samsungTvWebsocket, line 201
		if (funct == "remote") { // library marker davegut.samsungTvWebsocket, line 202
			url = "wss://${deviceIp}:8002/api/v2/channels/samsung.remote.control?name=${name}&token=${state.token}" // library marker davegut.samsungTvWebsocket, line 203
		} else if (funct == "frameArt") { // library marker davegut.samsungTvWebsocket, line 204
			url = "wss://${deviceIp}:8002/api/v2/channels/com.samsung.art-app?name=${name}&token=${state.token}" // library marker davegut.samsungTvWebsocket, line 205
		} else { // library marker davegut.samsungTvWebsocket, line 206
			logWarn("sendMessage: Invalid Function = ${funct}, tokenSupport = true") // library marker davegut.samsungTvWebsocket, line 207
		} // library marker davegut.samsungTvWebsocket, line 208
	} else { // library marker davegut.samsungTvWebsocket, line 209
		if (funct == "remote") { // library marker davegut.samsungTvWebsocket, line 210
			url = "ws://${deviceIp}:8001/api/v2/channels/samsung.remote.control?name=${name}" // library marker davegut.samsungTvWebsocket, line 211
		} else if (funct == "frameArt") { // library marker davegut.samsungTvWebsocket, line 212
			url = "ws://${deviceIp}:8001/api/v2/channels/com.samsung.art-app?name=${name}" // library marker davegut.samsungTvWebsocket, line 213
		} else { // library marker davegut.samsungTvWebsocket, line 214
			logWarn("sendMessage: Invalid Function = ${funct}, tokenSupport = false") // library marker davegut.samsungTvWebsocket, line 215
		} // library marker davegut.samsungTvWebsocket, line 216
	} // library marker davegut.samsungTvWebsocket, line 217
	state.currentFunction = funct // library marker davegut.samsungTvWebsocket, line 218
	interfaces.webSocket.connect(url, ignoreSSLIssues: true) // library marker davegut.samsungTvWebsocket, line 219
} // library marker davegut.samsungTvWebsocket, line 220

def close() { // library marker davegut.samsungTvWebsocket, line 222
	logDebug("close") // library marker davegut.samsungTvWebsocket, line 223
	interfaces.webSocket.close() // library marker davegut.samsungTvWebsocket, line 224
	sendEvent(name: "wsStatus", value: "closed") // library marker davegut.samsungTvWebsocket, line 225
} // library marker davegut.samsungTvWebsocket, line 226

def webSocketStatus(message) { // library marker davegut.samsungTvWebsocket, line 228
	def status // library marker davegut.samsungTvWebsocket, line 229
	if (message == "status: open") { // library marker davegut.samsungTvWebsocket, line 230
		status = "open" // library marker davegut.samsungTvWebsocket, line 231
	} else if (message == "status: closing") { // library marker davegut.samsungTvWebsocket, line 232
		status = "closed" // library marker davegut.samsungTvWebsocket, line 233
		state.currentFunction = "close" // library marker davegut.samsungTvWebsocket, line 234
	} else if (message.substring(0,7) == "failure") { // library marker davegut.samsungTvWebsocket, line 235
		status = "closed-failure" // library marker davegut.samsungTvWebsocket, line 236
		state.currentFunction = "close" // library marker davegut.samsungTvWebsocket, line 237
		close() // library marker davegut.samsungTvWebsocket, line 238
	} // library marker davegut.samsungTvWebsocket, line 239
	sendEvent(name: "wsStatus", value: status) // library marker davegut.samsungTvWebsocket, line 240
	logDebug("webSocketStatus: [status: ${status}, message: ${message}]") // library marker davegut.samsungTvWebsocket, line 241
} // library marker davegut.samsungTvWebsocket, line 242

def parseWs(resp) { // library marker davegut.samsungTvWebsocket, line 244
	def logData = [:] // library marker davegut.samsungTvWebsocket, line 245
	try { // library marker davegut.samsungTvWebsocket, line 246
		resp = parseJson(resp) // library marker davegut.samsungTvWebsocket, line 247
		def event = resp.event // library marker davegut.samsungTvWebsocket, line 248
		logData << [EVENT: event] // library marker davegut.samsungTvWebsocket, line 249
		switch(event) { // library marker davegut.samsungTvWebsocket, line 250
			case "ms.channel.connect": // library marker davegut.samsungTvWebsocket, line 251
				def newToken = resp.data.token // library marker davegut.samsungTvWebsocket, line 252
				if (newToken != null && newToken != state.token) { // library marker davegut.samsungTvWebsocket, line 253
					state.token = newToken // library marker davegut.samsungTvWebsocket, line 254
					logData << [TOKEN: "updated"] // library marker davegut.samsungTvWebsocket, line 255
				} else { // library marker davegut.samsungTvWebsocket, line 256
					logData << [TOKEN: "noChange"] // library marker davegut.samsungTvWebsocket, line 257
				} // library marker davegut.samsungTvWebsocket, line 258
				break // library marker davegut.samsungTvWebsocket, line 259
			case "d2d_service_message": // library marker davegut.samsungTvWebsocket, line 260
				def data = parseJson(resp.data) // library marker davegut.samsungTvWebsocket, line 261
				if (data.event == "artmode_status" || // library marker davegut.samsungTvWebsocket, line 262
					data.event == "art_mode_changed") { // library marker davegut.samsungTvWebsocket, line 263
					def status = data.value // library marker davegut.samsungTvWebsocket, line 264
					if (status == null) { status = data.status } // library marker davegut.samsungTvWebsocket, line 265
					sendEvent(name: "artModeStatus", value: status) // library marker davegut.samsungTvWebsocket, line 266
					logData << [artModeStatus: status] // library marker davegut.samsungTvWebsocket, line 267
					state.artModeWs = true // library marker davegut.samsungTvWebsocket, line 268
				} // library marker davegut.samsungTvWebsocket, line 269
				break // library marker davegut.samsungTvWebsocket, line 270
			case "ms.error": // library marker davegut.samsungTvWebsocket, line 271
				logData << [STATUS: "Error, Closing WS",DATA: resp.data] // library marker davegut.samsungTvWebsocket, line 272
				close() // library marker davegut.samsungTvWebsocket, line 273
				break // library marker davegut.samsungTvWebsocket, line 274
			case "ms.channel.ready": // library marker davegut.samsungTvWebsocket, line 275
			case "ms.channel.clientConnect": // library marker davegut.samsungTvWebsocket, line 276
			case "ms.channel.clientDisconnect": // library marker davegut.samsungTvWebsocket, line 277
			case "ms.remote.touchEnable": // library marker davegut.samsungTvWebsocket, line 278
			case "ms.remote.touchDisable": // library marker davegut.samsungTvWebsocket, line 279
				break // library marker davegut.samsungTvWebsocket, line 280
			default: // library marker davegut.samsungTvWebsocket, line 281
				logData << [STATUS: "Not Parsed", DATA: resp.data] // library marker davegut.samsungTvWebsocket, line 282
				break // library marker davegut.samsungTvWebsocket, line 283
		} // library marker davegut.samsungTvWebsocket, line 284
		logDebug("parse: ${logData}") // library marker davegut.samsungTvWebsocket, line 285
	} catch (e) { // library marker davegut.samsungTvWebsocket, line 286
		logData << [STATUS: "unhandled", ERROR: e] // library marker davegut.samsungTvWebsocket, line 287
		logWarn("parse: ${logData}") // library marker davegut.samsungTvWebsocket, line 288
	} // library marker davegut.samsungTvWebsocket, line 289
} // library marker davegut.samsungTvWebsocket, line 290

// ~~~~~ end include (1241) davegut.samsungTvWebsocket ~~~~~

// ~~~~~ start include (1242) davegut.samsungTvApps ~~~~~
library ( // library marker davegut.samsungTvApps, line 1
	name: "samsungTvApps", // library marker davegut.samsungTvApps, line 2
	namespace: "davegut", // library marker davegut.samsungTvApps, line 3
	author: "Dave Gutheinz", // library marker davegut.samsungTvApps, line 4
	description: "Samsung TV Applications", // library marker davegut.samsungTvApps, line 5
	category: "utilities", // library marker davegut.samsungTvApps, line 6
	documentationLink: "" // library marker davegut.samsungTvApps, line 7
) // library marker davegut.samsungTvApps, line 8

import groovy.json.JsonSlurper // library marker davegut.samsungTvApps, line 10

def appOpenByName(appName) { // library marker davegut.samsungTvApps, line 12
	def thisApp = findThisApp(appName) // library marker davegut.samsungTvApps, line 13
	def logData = [appName: thisApp[0], appId: thisApp[1]] // library marker davegut.samsungTvApps, line 14
	if (thisApp[1] != "none") { // library marker davegut.samsungTvApps, line 15
		[status: "execute appOpenByCode"] // library marker davegut.samsungTvApps, line 16
		appOpenByCode(thisApp[1]) // library marker davegut.samsungTvApps, line 17
	} else { // library marker davegut.samsungTvApps, line 18
		def url = "http://${deviceIp}:8080/ws/apps/${appName}" // library marker davegut.samsungTvApps, line 19
		try { // library marker davegut.samsungTvApps, line 20
			httpPost(url, "") { resp -> // library marker davegut.samsungTvApps, line 21
				sendEvent(name: "currentApp", value: respData.name) // library marker davegut.samsungTvApps, line 22
				logData << [status: "OK", currentApp: respData.name] // library marker davegut.samsungTvApps, line 23
			} // library marker davegut.samsungTvApps, line 24
			runIn(5, refresh) // library marker davegut.samsungTvApps, line 25
		} catch (err) { // library marker davegut.samsungTvApps, line 26
			logData << [status: "appName Not Found", data: err] // library marker davegut.samsungTvApps, line 27
			logWarn("appOpenByName: ${logData}") // library marker davegut.samsungTvApps, line 28
		} // library marker davegut.samsungTvApps, line 29
	} // library marker davegut.samsungTvApps, line 30
	logDebug("appOpenByName: ${logData}") // library marker davegut.samsungTvApps, line 31
} // library marker davegut.samsungTvApps, line 32

def appOpenByCode(appId) { // library marker davegut.samsungTvApps, line 34
	def appName = state.appData.find { it.value == appId } // library marker davegut.samsungTvApps, line 35
	if (appName != null) { // library marker davegut.samsungTvApps, line 36
		appName = appName.key // library marker davegut.samsungTvApps, line 37
	} // library marker davegut.samsungTvApps, line 38
	def logData = [appId: appId, appName: appName] // library marker davegut.samsungTvApps, line 39
	def uri = "http://${deviceIp}:8001/api/v2/applications/${appId}" // library marker davegut.samsungTvApps, line 40
	try { // library marker davegut.samsungTvApps, line 41
		httpPost(uri, body) { resp -> // library marker davegut.samsungTvApps, line 42
			if (appName == null) { // library marker davegut.samsungTvApps, line 43
				runIn(3, getAppData, [data: appId]) // library marker davegut.samsungTvApps, line 44
			} else { // library marker davegut.samsungTvApps, line 45
				sendEvent(name: "currentApp", value: appName) // library marker davegut.samsungTvApps, line 46
				logData << [currentApp: appName] // library marker davegut.samsungTvApps, line 47
			} // library marker davegut.samsungTvApps, line 48
			runIn(5, refresh) // library marker davegut.samsungTvApps, line 49
			logData << [status: "OK", data: resp.data] // library marker davegut.samsungTvApps, line 50
		} // library marker davegut.samsungTvApps, line 51
	} catch (err) { // library marker davegut.samsungTvApps, line 52
		logData << [status: "appId Not Found", data: err] // library marker davegut.samsungTvApps, line 53
		logWarn("appOpenByCode: ${logData}") // library marker davegut.samsungTvApps, line 54
	} // library marker davegut.samsungTvApps, line 55
	logDebug("appOpenByCode: ${logData}") // library marker davegut.samsungTvApps, line 56
} // library marker davegut.samsungTvApps, line 57

def appClose() { // library marker davegut.samsungTvApps, line 59
	def appId // library marker davegut.samsungTvApps, line 60
	def appName = device.currentValue("currentApp") // library marker davegut.samsungTvApps, line 61
	if (appName == " " || appName == null) { // library marker davegut.samsungTvApps, line 62
		logWarn("appClose: [status: FAILED, reason: appName not set.]") // library marker davegut.samsungTvApps, line 63
		return // library marker davegut.samsungTvApps, line 64
	} // library marker davegut.samsungTvApps, line 65
	def thisApp = findThisApp(appName) // library marker davegut.samsungTvApps, line 66
	appId = thisApp[1] // library marker davegut.samsungTvApps, line 67
	def logData = [appName: appName, appId: appId] // library marker davegut.samsungTvApps, line 68
	Map params = [uri: "http://${deviceIp}:8001/api/v2/applications/${appId}", // library marker davegut.samsungTvApps, line 69
				  timeout: 3] // library marker davegut.samsungTvApps, line 70
	try { // library marker davegut.samsungTvApps, line 71
		asynchttpDelete("appCloseParse", params, [appId: appId]) // library marker davegut.samsungTvApps, line 72
		logData: [status: "OK"] // library marker davegut.samsungTvApps, line 73
		exit() // library marker davegut.samsungTvApps, line 74
	} catch (err) { // library marker davegut.samsungTvApps, line 75
		logData: [status: "FAILED", data: err] // library marker davegut.samsungTvApps, line 76
		logWarn("appClose: ${logData}") // library marker davegut.samsungTvApps, line 77
	} // library marker davegut.samsungTvApps, line 78
	logDebug("appClose: ${logData}") // library marker davegut.samsungTvApps, line 79
} // library marker davegut.samsungTvApps, line 80

def appCloseParse(resp, data) { // library marker davegut.samsungTvApps, line 82
	def logData = [appId: data.appId] // library marker davegut.samsungTvApps, line 83
	if (resp.status == 200) { // library marker davegut.samsungTvApps, line 84
		sendEvent(name: "currentApp", value: " ") // library marker davegut.samsungTvApps, line 85
		logData << [status: "OK"] // library marker davegut.samsungTvApps, line 86
	} else { // library marker davegut.samsungTvApps, line 87
		logData << [status: "FAILED", status: resp.status] // library marker davegut.samsungTvApps, line 88
		logWarn("appCloseParse: ${logData}") // library marker davegut.samsungTvApps, line 89
	} // library marker davegut.samsungTvApps, line 90
	logDebug("appCloseParse: ${logData}") // library marker davegut.samsungTvApps, line 91
} // library marker davegut.samsungTvApps, line 92

def findThisApp(appName) { // library marker davegut.samsungTvApps, line 94
	def thisApp = state.appData.find { it.key.toLowerCase().contains(appName.toLowerCase()) } // library marker davegut.samsungTvApps, line 95
	def appId = "none" // library marker davegut.samsungTvApps, line 96
	if (thisApp != null) { // library marker davegut.samsungTvApps, line 97
		appName = thisApp.key // library marker davegut.samsungTvApps, line 98
		appId = thisApp.value // library marker davegut.samsungTvApps, line 99
	} else { // library marker davegut.samsungTvApps, line 100
		//	Handle special case for browser (using switch to add other cases. // library marker davegut.samsungTvApps, line 101
		switch(appName.toLowerCase()) { // library marker davegut.samsungTvApps, line 102
			case "browser": // library marker davegut.samsungTvApps, line 103
				appId = "org.tizen.browser" // library marker davegut.samsungTvApps, line 104
				appName = "Browser" // library marker davegut.samsungTvApps, line 105
				break // library marker davegut.samsungTvApps, line 106
			case "youtubetv": // library marker davegut.samsungTvApps, line 107
				appId = "PvWgqxV3Xa.YouTubeTV" // library marker davegut.samsungTvApps, line 108
				appName = "YouTube TV" // library marker davegut.samsungTvApps, line 109
				break // library marker davegut.samsungTvApps, line 110
			case "netflix": // library marker davegut.samsungTvApps, line 111
				appId = "3201907018807" // library marker davegut.samsungTvApps, line 112
				appName = "Netflix" // library marker davegut.samsungTvApps, line 113
				break // library marker davegut.samsungTvApps, line 114
			case "youtube": // library marker davegut.samsungTvApps, line 115
				appId = "9Ur5IzDKqV.TizenYouTube" // library marker davegut.samsungTvApps, line 116
				appName = "YouTube" // library marker davegut.samsungTvApps, line 117
				break // library marker davegut.samsungTvApps, line 118
			case "amazoninstantvideo": // library marker davegut.samsungTvApps, line 119
				appId = "3201910019365" // library marker davegut.samsungTvApps, line 120
				appName = "Prime Video" // library marker davegut.samsungTvApps, line 121
				break // library marker davegut.samsungTvApps, line 122
			default: // library marker davegut.samsungTvApps, line 123
				logWarn("findThisApp: ${appName} not found in appData") // library marker davegut.samsungTvApps, line 124
		} // library marker davegut.samsungTvApps, line 125
	} // library marker davegut.samsungTvApps, line 126
	return [appName, appId] // library marker davegut.samsungTvApps, line 127
} // library marker davegut.samsungTvApps, line 128

def getAppData(appId) { // library marker davegut.samsungTvApps, line 130
	def logData = [appId: appId] // library marker davegut.samsungTvApps, line 131
	def thisApp = state.appData.find { it.value == appId } // library marker davegut.samsungTvApps, line 132
	if (thisApp && !state.appIdIndex) { // library marker davegut.samsungTvApps, line 133
		sendEvent(name: "currentApp", value: thisApp.key) // library marker davegut.samsungTvApps, line 134
		logData << [currentApp: thisApp.key] // library marker davegut.samsungTvApps, line 135
	} else { // library marker davegut.samsungTvApps, line 136
		Map params = [uri: "http://${deviceIp}:8001/api/v2/applications/${appId}", // library marker davegut.samsungTvApps, line 137
					  timeout: 3] // library marker davegut.samsungTvApps, line 138
		try { // library marker davegut.samsungTvApps, line 139
			asynchttpGet("getAppDataParse", params, [appId: appId]) // library marker davegut.samsungTvApps, line 140
		} catch (err) { // library marker davegut.samsungTvApps, line 141
			logData: [status: "FAILED", data: err] // library marker davegut.samsungTvApps, line 142
		} // library marker davegut.samsungTvApps, line 143
	} // library marker davegut.samsungTvApps, line 144
	logDebug("getAppData: ${logData}") // library marker davegut.samsungTvApps, line 145
} // library marker davegut.samsungTvApps, line 146

def getAppDataParse(resp, data) { // library marker davegut.samsungTvApps, line 148
	def logData = [appId: data.appId] // library marker davegut.samsungTvApps, line 149
	if (resp.status == 200) { // library marker davegut.samsungTvApps, line 150
		def respData = new JsonSlurper().parseText(resp.data) // library marker davegut.samsungTvApps, line 151
		logData << [resp: respData] // library marker davegut.samsungTvApps, line 152
		state.appData << ["${respData.name}": respData.id] // library marker davegut.samsungTvApps, line 153
		if(!state.appIdIndex && device.currentValue("currentApp") != currApp) { // library marker davegut.samsungTvApps, line 154
			sendEvent(name: "currentApp", value: respData.name) // library marker davegut.samsungTvApps, line 155
			logData << [currentApp: respData.name] // library marker davegut.samsungTvApps, line 156
		} // library marker davegut.samsungTvApps, line 157
	} else { // library marker davegut.samsungTvApps, line 158
		logData << [status: "FAILED", reason: "${resp.status} response from TV"] // library marker davegut.samsungTvApps, line 159
	} // library marker davegut.samsungTvApps, line 160
	logDebug("getAppDataParse: ${logData}") // library marker davegut.samsungTvApps, line 161
} // library marker davegut.samsungTvApps, line 162

def updateAppCodes() { // library marker davegut.samsungTvApps, line 164
	if (!state.appData) { state.appData = [:] } // library marker davegut.samsungTvApps, line 165
	if (device.currentValue("switch") == "on") { // library marker davegut.samsungTvApps, line 166
		logInfo("updateAppCodes: [currentDbSize: ${state.appData.size()}, availableCodes: ${appIdList().size()}]") // library marker davegut.samsungTvApps, line 167
		if (pollInterval != null) { // library marker davegut.samsungTvApps, line 168
			unschedule("onPoll") // library marker davegut.samsungTvApps, line 169
			runIn(900, setOnPollInterval) // library marker davegut.samsungTvApps, line 170
		} // library marker davegut.samsungTvApps, line 171
		state.appIdIndex = 0 // library marker davegut.samsungTvApps, line 172
		findNextApp() // library marker davegut.samsungTvApps, line 173
	} else { // library marker davegut.samsungTvApps, line 174
		logWarn("getAppList: [status: FAILED, reason: tvOff]") // library marker davegut.samsungTvApps, line 175
	} // library marker davegut.samsungTvApps, line 176
	device.updateSetting("resetAppCodes", [type:"bool", value: false]) // library marker davegut.samsungTvApps, line 177
	device.updateSetting("findAppCodes", [type:"bool", value: false]) // library marker davegut.samsungTvApps, line 178
} // library marker davegut.samsungTvApps, line 179

def findNextApp() { // library marker davegut.samsungTvApps, line 181
	def appIds = appIdList() // library marker davegut.samsungTvApps, line 182
	def logData = [:] // library marker davegut.samsungTvApps, line 183
	if (state.appIdIndex < appIds.size()) { // library marker davegut.samsungTvApps, line 184
		def nextApp = appIds[state.appIdIndex] // library marker davegut.samsungTvApps, line 185
		state.appIdIndex += 1 // library marker davegut.samsungTvApps, line 186
		getAppData(nextApp) // library marker davegut.samsungTvApps, line 187
		runIn(6, findNextApp) // library marker davegut.samsungTvApps, line 188
	} else { // library marker davegut.samsungTvApps, line 189
		if (pollInterval != null) { // library marker davegut.samsungTvApps, line 190
			runIn(20, setOnPollInterval) // library marker davegut.samsungTvApps, line 191
		} // library marker davegut.samsungTvApps, line 192
		logData << [status: "Complete", appIdsScanned: state.appIdIndex] // library marker davegut.samsungTvApps, line 193
		logData << [totalApps: state.appData.size(), appData: state.appData] // library marker davegut.samsungTvApps, line 194
		state.remove("appIdIndex") // library marker davegut.samsungTvApps, line 195
		logInfo("findNextApp: ${logData}") // library marker davegut.samsungTvApps, line 196
	} // library marker davegut.samsungTvApps, line 197
} // library marker davegut.samsungTvApps, line 198

def appIdList() { // library marker davegut.samsungTvApps, line 200
	def appList = [ // library marker davegut.samsungTvApps, line 201
		"kk8MbItQ0H.VUDU", "vYmY3ACVaa.emby", "ZmmGjO6VKO.slingtv", "MCmYXNxgcu.DisneyPlus", // library marker davegut.samsungTvApps, line 202
		"PvWgqxV3Xa.YouTubeTV", "LBUAQX1exg.Hulu", "AQKO41xyKP.AmazonAlexa", "3KA0pm7a7V.TubiTV", // library marker davegut.samsungTvApps, line 203
		"cj37Ni3qXM.HBONow", "gzcc4LRFBF.Peacock", "9Ur5IzDKqV.TizenYouTube", "BjyffU0l9h.Stream", // library marker davegut.samsungTvApps, line 204
		"3202203026841", "3202103023232", "3202103023185", "3202012022468", "3202012022421", // library marker davegut.samsungTvApps, line 205
		"3202011022316", "3202011022131", "3202010022098", "3202009021877", "3202008021577", // library marker davegut.samsungTvApps, line 206
		"3202008021462", "3202008021439", "3202007021336", "3202004020674", "3202004020626", // library marker davegut.samsungTvApps, line 207
		"3202003020365", "3201910019457", "3201910019449", "3201910019420", "3201910019378", // library marker davegut.samsungTvApps, line 208
		"3201910019365", "3201910019354", "3201909019271", "3201909019175", "3201908019041", // library marker davegut.samsungTvApps, line 209
		"3201908019022", "3201907018807", "3201907018786", "3201907018784", "3201906018693", // library marker davegut.samsungTvApps, line 210
		"3201901017768", "3201901017640", "3201812017479", "3201810017091", "3201810017074", // library marker davegut.samsungTvApps, line 211
		"3201807016597", "3201806016432", "3201806016390", "3201806016381", "3201805016367", // library marker davegut.samsungTvApps, line 212
		"3201803015944", "3201803015934", "3201803015869", "3201711015226", "3201710015067", // library marker davegut.samsungTvApps, line 213
		"3201710015037", "3201710015016", "3201710014874", "3201710014866", "3201707014489", // library marker davegut.samsungTvApps, line 214
		"3201706014250", "3201706012478", "3201704012212", "3201704012147", "3201703012079", // library marker davegut.samsungTvApps, line 215
		"3201703012065", "3201703012029", "3201702011851", "3201612011418", "3201611011210", // library marker davegut.samsungTvApps, line 216
		"3201611011005", "3201611010983", "3201608010385", "3201608010191", "3201607010031", // library marker davegut.samsungTvApps, line 217
		"3201606009910", "3201606009798", "3201606009684", "3201604009182", "3201603008746", // library marker davegut.samsungTvApps, line 218
		"3201603008210", "3201602007865", "3201601007670", "3201601007625", "3201601007230", // library marker davegut.samsungTvApps, line 219
		"3201512006963", "3201512006785", "3201511006428", "3201510005981", "3201506003488", // library marker davegut.samsungTvApps, line 220
		"3201506003486", "3201506003175", "3201504001965", "121299000612", "121299000101", // library marker davegut.samsungTvApps, line 221
		"121299000089", "111399002220", "111399002034", "111399000741", "111299002148", // library marker davegut.samsungTvApps, line 222
		"111299001912", "111299000769", "111012010001", "11101200001", "11101000407", // library marker davegut.samsungTvApps, line 223
		"11091000000" // library marker davegut.samsungTvApps, line 224
	] // library marker davegut.samsungTvApps, line 225
	return appList // library marker davegut.samsungTvApps, line 226
} // library marker davegut.samsungTvApps, line 227

def appRunBrowser() { appOpenByName("Browser") } // library marker davegut.samsungTvApps, line 229

def appRunYouTube() { appOpenByName("YouTube") } // library marker davegut.samsungTvApps, line 231

def appRunNetflix() { appOpenByName("Netflix") } // library marker davegut.samsungTvApps, line 233

def appRunPrimeVideo() { appOpenByName("Prime Video") } // library marker davegut.samsungTvApps, line 235

def appRunYouTubeTV() { appOpenByName("YouTubeTV") } // library marker davegut.samsungTvApps, line 237

def appRunHulu() { appOpenByName("Hulu") } // library marker davegut.samsungTvApps, line 239

// ~~~~~ end include (1242) davegut.samsungTvApps ~~~~~

// ~~~~~ start include (1072) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8

//	Logging during development // library marker davegut.Logging, line 10
def listAttributes(trace = false) { // library marker davegut.Logging, line 11
	def attrs = device.getSupportedAttributes() // library marker davegut.Logging, line 12
	def attrList = [:] // library marker davegut.Logging, line 13
	attrs.each { // library marker davegut.Logging, line 14
		def val = device.currentValue("${it}") // library marker davegut.Logging, line 15
		attrList << ["${it}": val] // library marker davegut.Logging, line 16
	} // library marker davegut.Logging, line 17
	if (trace == true) { // library marker davegut.Logging, line 18
		logInfo("Attributes: ${attrList}") // library marker davegut.Logging, line 19
	} else { // library marker davegut.Logging, line 20
		logDebug("Attributes: ${attrList}") // library marker davegut.Logging, line 21
	} // library marker davegut.Logging, line 22
} // library marker davegut.Logging, line 23

def logTrace(msg){ // library marker davegut.Logging, line 25
	if (traceLog == true) { // library marker davegut.Logging, line 26
		log.trace "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.Logging, line 27
	} // library marker davegut.Logging, line 28
} // library marker davegut.Logging, line 29

def traceLogOff() { // library marker davegut.Logging, line 31
	device.updateSetting("traceLog", [type:"bool", value: false]) // library marker davegut.Logging, line 32
	logInfo("traceLogOff") // library marker davegut.Logging, line 33
} // library marker davegut.Logging, line 34

def logInfo(msg) {  // library marker davegut.Logging, line 36
	if (textEnable || infoLog) { // library marker davegut.Logging, line 37
		log.info "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.Logging, line 38
	} // library marker davegut.Logging, line 39
} // library marker davegut.Logging, line 40

def debugLogOff() { // library marker davegut.Logging, line 42
	device.updateSetting("logEnable", [type:"bool", value: false]) // library marker davegut.Logging, line 43
	logInfo("debugLogOff") // library marker davegut.Logging, line 44
} // library marker davegut.Logging, line 45

def logDebug(msg) { // library marker davegut.Logging, line 47
	if (logEnable || debugLog) { // library marker davegut.Logging, line 48
		log.debug "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.Logging, line 49
	} // library marker davegut.Logging, line 50
} // library marker davegut.Logging, line 51

def logWarn(msg) { log.warn "${device.displayName}-${driverVer()}: ${msg}" } // library marker davegut.Logging, line 53

// ~~~~~ end include (1072) davegut.Logging ~~~~~
