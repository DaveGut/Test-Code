/*	===== HUBITAT Samsung Dryer Using SmartThings ==========================================
		Copyright 2022 Dave Gutheinz
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file
except in compliance with the License. You may obtain a copy of the License at:
		http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the
License is distributed on an  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. See the License for the specific language governing permissions
and limitations under the  License.
===== HISTORY =============================================================================
0.1	Alpha version
	a.	Finalize Refresh Design
	b.	Created attribute "timeRemaing" with associated processing.
	c.	Added button interface (push)
		1: toggles machineState between run and pause.
		2: sets machine state to stop.
0.11	Alpha Version 2
	a.	Added logInfo to calcTimeRemaining(). Reason: try to
		understand completion time and calculation of timeRemaing better.
===========================================================================================*/
import groovy.json.JsonSlurper
def driverVer() { return "0.11T" }

def simulate() { return false }
def testData() {
	def wrinklePrevent = "off"
	def dryingTemp = "medium"
	def dryLevel = "normal"
	def completionTime = "2022-04-28T19:56:26Z"
	def machineState = "stop"
	def jobState = "none"
	def onOff = "off"
	def kidsLock = "unlocked"
	def soilLevel = "normal"
	def remoteControl = "false"
	def dryingTime = "22"
	
	return  [components:[
		main:[
			"custom.dryerWrinklePrevent":[dryerWrinklePrevent:[value:wrinklePrevent]], 
			"samsungce.dryerDryingTemperature":[dryingTemperature:[value:dryingTemp]], 
			switch:[switch:[value:onOff]],
			"custom.dryerDryLevel":[dryerDryLevel:[value:dryLevel]], 
			"samsungce.kidsLock":[lockState:[value:kidsLock]], 
			dryerOperatingState:[
				completionTime:[value:completionTime], 
				machineState:[value:machineState], 
				dryerJobState:[value:jobState]], 
			remoteControlStatus:[remoteControlEnabled:[value:remoteControl]], 
			"samsungce.dryerDryingTime":[dryingTime:[value:dryingTime]]
		]]]
}

metadata {
	definition (name: "Samsung Dryer via ST",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
		capability "Refresh"
		attribute "switch", "string"
		command "start"
		command "pause"
		command "stop"
		attribute "dryerJobState", "string"
		attribute "machineState", "string"
		attribute "kidsLock", "string"
		attribute "remoteControlEnabled", "string"
		command "getDeviceList"
		attribute "timeRemaining", "integer"
		capability "PushableButton"
		//	Attributes under test.
		attribute "completionTime", "string"
		attribute "timeRemaining", "integer"
		attribute "dryingTemperature", "string"
		attribute "wrinklePrevent", "string"
		attribute "dryingTime", "string"
		attribute "dryLevel", "string"
	}
	preferences {
		input ("debugLog", "bool",  
			   title: "Enable debug logging for 30 minutes", defaultValue: false)
		input ("infoLog", "bool",  
			   title: "Enable description text logging", defaultValue: true)
		input ("stApiKey", "text", title: "SmartThings API Key", defaultValue: "")
		input ("stDeviceId", "text", title: "SmartThings TV Device ID", defaultValue: "")
	}
}

def installed() {
	runIn(1, updated)
}

def updated() {
	def commonStatus = commonUpdate()
	state.pushDefinitions = [1: "start", 2: "pause", 3: "stop"]
	logInfo("updated: ${commonStatus}")
}

def refresh() {
	if (simulate() == true) {
		deviceStatusParse(testData(), "simulation")
	} else {
		commonRefresh()
	}
}

def deviceStatusParse(resp, data) {
	def respData
	if (data == "simulation") {
		respData = resp
		log.trace resp
	} else {
		respData = validateResp(resp, "deviceStatusParse")
	}
	if (respData == "error") { return }
	def mainData = respData.components.main
	def logData = [:]

	try {
		def onOff = mainData.switch.switch.value
		if (device.currentValue("switch") != onOff) {
			if (onOff == "off") {
				runEvery10Minutes(refresh)
			} else {
				runEvery1Minute(refresh)
			}
			sendEvent(name: "switch", value: onOff)
			logData << [switch: onOff]
		}
	} catch (e) { logWarn("deviceStatusParse: switch") }
	
	try {
		def kidsLock = mainData["samsungce.kidsLock"].lockState.value
		if (device.currentValue("kidsLock") != kidsLock) {
			sendEvent(name: "kidsLock", value: kidsLock)
			logData << [kidsLock: kidsLock]
		}
	} catch (e) { logWarn("deviceStatusParse: kidsLock") }
	
	try {
		def machineState = mainData.dryerOperatingState.machineState.value
		if (device.currentValue("machineState") != machineState) {
			sendEvent(name: "machineState", value: machineState)
			logData << [machineState: machineState]
		}
	} catch (e) { logWarn("deviceStatusParse: machineState") }
	
	try {
		def jobState = mainData.dryerOperatingState.dryerJobState.value
		if (device.currentValue("jobState") != jobState) {
			sendEvent(name: "jobState", value: jobState)
			logData << [jobState: jobState]
		}
	} catch (e) { logWarn("deviceStatusParse: jobState") }
	
	try {
		def remoteControlEnabled = mainData.remoteControlStatus.remoteControlEnabled.value
		if (device.currentValue("remoteControlEnabled") != remoteControlEnabled) {
			sendEvent(name: "remoteControlEnabled", value: remoteControlEnabled)
			logData << [remoteControlEnabled: remoteControlEnabled]
		}
	} catch (e) { logWarn("deviceStatusParse: remoteControlEnabled") }
	
	try {
		def completionTime = mainData.dryerOperatingState.completionTime.value
		if (completionTime != null) {
			sendEvent(name: "completionTime", value: completionTime)
			logData << [completionTime: completionTime]
			def timeRemaining = calcTimeRemaining(completionTime)
			if (device.currentValue("timeRemaining") != timeRemaining) {
				sendEvent(name: "timeRemaining", value: timeRemaining)
				logData << [timeRemaining: timeRemaining]
			}
		}
	} catch (e) { logWarn("deviceStatusParse: timeRemaining") }

	try {
		def wrinklePrevent = mainData["custom.dryerWrinklePrevent"].dryerWrinklePrevent.value
		if (device.currentValue("wrinklePrevent") != wrinklePrevent) {
			sendEvent(name: "wrinklePrevent", value: wrinklePrevent)
			logData << [wrinklePrevent: wrinklePrevent]
		}
	} catch (e) { logWarn("deviceStatusParse: wrinklePrevent") }
	
	try {
		def dryingTemperature = mainData["samsungce.dryerDryingTemperature"].dryingTemperature.value
		if (device.currentValue("dryingTemperature") != dryingTemperature) {
			sendEvent(name: "dryingTemperature", value: dryingTemperature)
			logData << [dryingTemperature: dryingTemperature]
		}
	} catch (e) { logWarn("deviceStatusParse: dryingTemperature") }
	
	try {
		def dryingTime = mainData["samsungce.dryerDryingTime"].dryingTime.value
		if (device.currentValue("dryingTime") != dryingTime) {
			sendEvent(name: "dryingTime", value: dryingTime)
			logData << [dryingTime: dryingTime]
		}
	} catch (e) { logWarn("deviceStatusParse: dryingTime") }
	
	try {
		def dryLevel = mainData["custom.dryerDryLevel"].dryerDryLevel.value
		if (device.currentValue("dryLevel") != dryLevel) {
			sendEvent(name: "dryLevel", value: dryLevel)
			logData << [dryLevel: dryLevel]
		}
	} catch (e) { logWarn("deviceStatusParse: dryLevel") }
	
	if (logData != [:]) {
		logInfo("getDeviceStatus: ${logData}")
	}
//	Temp Test Code
	runIn(1, listAttributes)
}

//	===== Commands =====
def start() { setMachineState("run") }
def pause() { setMachineState("pause") }
def stop() { setMachineState("stop") }
def setMachineState(machState) {
	def remoteControlEnabled = device.currentValue("remoteControlEnabled")
	logDebug("setMachineState: ${machState}, ${remotControlEnabled}")
	if (remoteControlEnabled == "true") {
		def cmdData = [
			component: "main",
			capability: "dryerOperatingState",
			command: "setMachineState",
			arguments: [machState]]
		cmdRespParse(syncPost(cmdData))
	} else {
		logDebug("setMachineState: [status: failed, remoteControlEnabled: false]")
	}
}

//	===== button interface =====
def push(pushed) {
	logDebug("push: button = ${pushed}")
	if (pushed == null) {
		logWarn("push: pushed is null.  Input ignored")
	} else {
		pushed = pushed.toInteger()
		switch(pushed) {
			//	===== Physical Remote Commands =====
			case 1 : start(); break
			case 2 : pause(); break
			case 3 : stop(); break
			default:
				logWarn("push: Invalid Button Number!")
		}
	}
}

//	===== Library Integration =====



// ~~~~~ start include (387) davegut.ST-Communications ~~~~~
library ( // library marker davegut.ST-Communications, line 1
	name: "ST-Communications", // library marker davegut.ST-Communications, line 2
	namespace: "davegut", // library marker davegut.ST-Communications, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Communications, line 4
	description: "ST Communications Methods", // library marker davegut.ST-Communications, line 5
	category: "utilities", // library marker davegut.ST-Communications, line 6
	documentationLink: "" // library marker davegut.ST-Communications, line 7
) // library marker davegut.ST-Communications, line 8
import groovy.json.JsonSlurper // library marker davegut.ST-Communications, line 9

def validateResp(resp, method) { // library marker davegut.ST-Communications, line 11
	if (resp.status != 200) { // library marker davegut.ST-Communications, line 12

		def errorResp = [status: "error", // library marker davegut.ST-Communications, line 14
						 httpCode: resp.status, // library marker davegut.ST-Communications, line 15
						 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 16
		logWarn("${method}: ${errorResp}") // library marker davegut.ST-Communications, line 17
		return "error" // library marker davegut.ST-Communications, line 18
	} else { // library marker davegut.ST-Communications, line 19
		try { // library marker davegut.ST-Communications, line 20
			return new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Communications, line 21
		} catch (err) { // library marker davegut.ST-Communications, line 22
			logWarn("${method}: [noDataError: ${err}]") // library marker davegut.ST-Communications, line 23
		} // library marker davegut.ST-Communications, line 24
	} // library marker davegut.ST-Communications, line 25
} // library marker davegut.ST-Communications, line 26

//	Asynchronous send commands // library marker davegut.ST-Communications, line 28
private asyncGet(sendData) { // library marker davegut.ST-Communications, line 29
//	sendData Spec: [path, parse] // library marker davegut.ST-Communications, line 30
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Communications, line 31
		logWarn("asyncGet: [status: ${error}, statusReason: no stApiKey]") // library marker davegut.ST-Communications, line 32
		return // library marker davegut.ST-Communications, line 33
	} // library marker davegut.ST-Communications, line 34
	logDebug("asyncGet: [apiKey: ${stApiKey}, sendData: ${sendData}]") // library marker davegut.ST-Communications, line 35
	def sendCmdParams = [ // library marker davegut.ST-Communications, line 36
		uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 37
		path: sendData.path, // library marker davegut.ST-Communications, line 38
		headers: ['Authorization': 'Bearer ' + stApiKey.trim()]] // library marker davegut.ST-Communications, line 39
	try { // library marker davegut.ST-Communications, line 40
		asynchttpGet(sendData.parse, sendCmdParams) // library marker davegut.ST-Communications, line 41
	} catch (error) { // library marker davegut.ST-Communications, line 42
		logWarn("asyncGet: [status: error, statusReason: ${error}]") // library marker davegut.ST-Communications, line 43
	} // library marker davegut.ST-Communications, line 44
} // library marker davegut.ST-Communications, line 45

private syncPost(cmdData, path = "/devices/${stDeviceId.trim()}/commands"){ // library marker davegut.ST-Communications, line 47
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Communications, line 48
		logWarn("asyncPost: [status: ${error}, statusReason: no stApiKey]") // library marker davegut.ST-Communications, line 49
		return // library marker davegut.ST-Communications, line 50
	} // library marker davegut.ST-Communications, line 51
	logDebug("syncPost: [apiKey: ${stApiKey}, cmdBody: ${cmdData}, path: ${path}]") // library marker davegut.ST-Communications, line 52
	def cmdBody = [commands: [cmdData]] // library marker davegut.ST-Communications, line 53
	def sendCmdParams = [ // library marker davegut.ST-Communications, line 54
		uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 55
		path: path, // library marker davegut.ST-Communications, line 56
		headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 57
		body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 58
	] // library marker davegut.ST-Communications, line 59
	def respData = "error" // library marker davegut.ST-Communications, line 60
	try { // library marker davegut.ST-Communications, line 61
		httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 62
			if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 63
				respData = [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 64
			} else { // library marker davegut.ST-Communications, line 65
				def errorResp = [status: "error", // library marker davegut.ST-Communications, line 66
								 cmdBody: cmdBody, // library marker davegut.ST-Communications, line 67
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 68
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 69
				logDebug("syncPost error from ST: ${errorResp}") // library marker davegut.ST-Communications, line 70
			} // library marker davegut.ST-Communications, line 71
		} // library marker davegut.ST-Communications, line 72
	} catch (error) { // library marker davegut.ST-Communications, line 73
		def errorResp = [status: "error", // library marker davegut.ST-Communications, line 74
						 cmdBody: cmdBody, // library marker davegut.ST-Communications, line 75
						 errorMsg: error] // library marker davegut.ST-Communications, line 76
		logDebug("syncPost: ${errorResp}") // library marker davegut.ST-Communications, line 77
	} // library marker davegut.ST-Communications, line 78
	return respData // library marker davegut.ST-Communications, line 79
} // library marker davegut.ST-Communications, line 80

//	======================================================== // library marker davegut.ST-Communications, line 82
//	===== Logging ========================================== // library marker davegut.ST-Communications, line 83
//	======================================================== // library marker davegut.ST-Communications, line 84
def logTrace(msg){ // library marker davegut.ST-Communications, line 85
	log.trace "${device.label} V${driverVer()}: ${msg}" // library marker davegut.ST-Communications, line 86
} // library marker davegut.ST-Communications, line 87

def logInfo(msg) {  // library marker davegut.ST-Communications, line 89
	if (infoLog == true) { // library marker davegut.ST-Communications, line 90
		log.info "${device.label} V${driverVer()}: ${msg}" // library marker davegut.ST-Communications, line 91
	} // library marker davegut.ST-Communications, line 92
} // library marker davegut.ST-Communications, line 93

def debugLogOff() { // library marker davegut.ST-Communications, line 95
	device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.ST-Communications, line 96
	logInfo("Debug logging is false.") // library marker davegut.ST-Communications, line 97
} // library marker davegut.ST-Communications, line 98

def logDebug(msg) { // library marker davegut.ST-Communications, line 100
	if (debugLog == true) { // library marker davegut.ST-Communications, line 101
		log.debug "${device.label} V${driverVer()}: ${msg}" // library marker davegut.ST-Communications, line 102
	} // library marker davegut.ST-Communications, line 103
} // library marker davegut.ST-Communications, line 104

def logWarn(msg) { log.warn "${device.label} V${driverVer()}: ${msg}" } // library marker davegut.ST-Communications, line 106

// ~~~~~ end include (387) davegut.ST-Communications ~~~~~

// ~~~~~ start include (450) davegut.ST-Common ~~~~~
library ( // library marker davegut.ST-Common, line 1
	name: "ST-Common", // library marker davegut.ST-Common, line 2
	namespace: "davegut", // library marker davegut.ST-Common, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Common, line 4
	description: "ST Wash/Dryer Common Methods", // library marker davegut.ST-Common, line 5
	category: "utilities", // library marker davegut.ST-Common, line 6
	documentationLink: "" // library marker davegut.ST-Common, line 7
) // library marker davegut.ST-Common, line 8

def commonUpdate() { // library marker davegut.ST-Common, line 10
	unschedule() // library marker davegut.ST-Common, line 11
	def updateData = [:] // library marker davegut.ST-Common, line 12
	def status = "OK" // library marker davegut.ST-Common, line 13
	def statusReason = "" // library marker davegut.ST-Common, line 14
	def deviceData // library marker davegut.ST-Common, line 15
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Common, line 16
		status = "failed" // library marker davegut.ST-Common, line 17
		statusReason = "No stApiKey" // library marker davegut.ST-Common, line 18
	} else if (!stDeviceId || stDeviceId == "") { // library marker davegut.ST-Common, line 19
		status = "failed" // library marker davegut.ST-Common, line 20
		statusReason = "No stDeviceId" // library marker davegut.ST-Common, line 21
	} else { // library marker davegut.ST-Common, line 22
		if (debugLog) { runIn(1800, debugLogOff) } // library marker davegut.ST-Common, line 23
		updateData << [stApiKey: stApiKey, stDeviceId: stDeviceId] // library marker davegut.ST-Common, line 24
		updateData << [debugLog: debugLog, infoLog: infoLog] // library marker davegut.ST-Common, line 25
		if (!getDataValue("driverVersion") ||  // library marker davegut.ST-Common, line 26
			getDataValue("driverVersion") != driverVer()) { // library marker davegut.ST-Common, line 27
			updateDataValue("driverVersion", driverVer()) // library marker davegut.ST-Common, line 28
			updateData << [driverVer: driverVer()] // library marker davegut.ST-Common, line 29
		} // library marker davegut.ST-Common, line 30
	} // library marker davegut.ST-Common, line 31
	def updateStatus = [:] // library marker davegut.ST-Common, line 32
	updateStatus << [status: status] // library marker davegut.ST-Common, line 33
	if (statusReason != "") { // library marker davegut.ST-Common, line 34
		updateStatus << [statusReason: statusReason] // library marker davegut.ST-Common, line 35
	} // library marker davegut.ST-Common, line 36
	updateStatus << [updateData: updateData] // library marker davegut.ST-Common, line 37
	refresh() // library marker davegut.ST-Common, line 38
	return updateStatus // library marker davegut.ST-Common, line 39
} // library marker davegut.ST-Common, line 40

def cmdRespParse(respData) { // library marker davegut.ST-Common, line 42
	if (respData == "error") { // library marker davegut.ST-Common, line 43
		logData << [error: "error from setMachineState"] // library marker davegut.ST-Common, line 44
	} else if (respData.status == "OK") { // library marker davegut.ST-Common, line 45
		runIn(2, refresh) // library marker davegut.ST-Common, line 46
	} else { // library marker davegut.ST-Common, line 47
		logData << [error: respData] // library marker davegut.ST-Common, line 48
	} // library marker davegut.ST-Common, line 49
} // library marker davegut.ST-Common, line 50

def commonRefresh() { // library marker davegut.ST-Common, line 52
/*	Design // library marker davegut.ST-Common, line 53
	a.	Complete a deviceRefresh // library marker davegut.ST-Common, line 54
		1.	Ignore response. Will refresh data in ST for device. // library marker davegut.ST-Common, line 55
	b.	run getDeviceStatus // library marker davegut.ST-Common, line 56
		1.	Capture switch attributes // library marker davegut.ST-Common, line 57
			a) if on, set runEvery1Minute(refresh) // library marker davegut.ST-Common, line 58
			b)	if off, set runEvery10Minutes(refresh) // library marker davegut.ST-Common, line 59
		2.	Capture other attributes // library marker davegut.ST-Common, line 60
		3.	Log a list of current attribute states.  (Temporary) // library marker davegut.ST-Common, line 61
*/ // library marker davegut.ST-Common, line 62
	def cmdData = [ // library marker davegut.ST-Common, line 63
		component: "main", // library marker davegut.ST-Common, line 64
		capability: "refresh", // library marker davegut.ST-Common, line 65
		command: "refresh", // library marker davegut.ST-Common, line 66
		arguments: []] // library marker davegut.ST-Common, line 67
	syncPost(cmdData) // library marker davegut.ST-Common, line 68
	def respData = syncPost(sendData) // library marker davegut.ST-Common, line 69
	getDeviceStatus() // library marker davegut.ST-Common, line 70
} // library marker davegut.ST-Common, line 71

def getDeviceStatus(parseMethod = "deviceStatusParse") { // library marker davegut.ST-Common, line 73
	def sendData = [ // library marker davegut.ST-Common, line 74
		path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 75
		parse: parseMethod // library marker davegut.ST-Common, line 76
		] // library marker davegut.ST-Common, line 77
	asyncGet(sendData) // library marker davegut.ST-Common, line 78
} // library marker davegut.ST-Common, line 79

def listAttributes() { // library marker davegut.ST-Common, line 81
	def attrs = device.getSupportedAttributes() // library marker davegut.ST-Common, line 82
	def attrList = [:] // library marker davegut.ST-Common, line 83
	attrs.each { // library marker davegut.ST-Common, line 84
		def val = device.currentValue("${it}") // library marker davegut.ST-Common, line 85
		attrList << ["${it}": val] // library marker davegut.ST-Common, line 86
	} // library marker davegut.ST-Common, line 87
//	logDebug("Attributes: ${attrList}") // library marker davegut.ST-Common, line 88
	logTrace("Attributes: ${attrList}") // library marker davegut.ST-Common, line 89
} // library marker davegut.ST-Common, line 90

def on() { setSwitch("on") } // library marker davegut.ST-Common, line 92
def off() { setSwitch("off") } // library marker davegut.ST-Common, line 93
def setSwitch(onOff) { // library marker davegut.ST-Common, line 94
	logDebug("setSwitch: ${onOff}") // library marker davegut.ST-Common, line 95
	def cmdData = [ // library marker davegut.ST-Common, line 96
		component: "main", // library marker davegut.ST-Common, line 97
		capability: "switch", // library marker davegut.ST-Common, line 98
		command: onOff, // library marker davegut.ST-Common, line 99
		arguments: []] // library marker davegut.ST-Common, line 100
	cmdRespParse(syncPost(cmdData)) // library marker davegut.ST-Common, line 101
} // library marker davegut.ST-Common, line 102

def getDeviceList() { // library marker davegut.ST-Common, line 104
	def sendData = [ // library marker davegut.ST-Common, line 105
		path: "/devices", // library marker davegut.ST-Common, line 106
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 107
		] // library marker davegut.ST-Common, line 108
	asyncGet(sendData) // library marker davegut.ST-Common, line 109
} // library marker davegut.ST-Common, line 110
def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 111
	def respData = validateResp(resp, "getDeviceListParse") // library marker davegut.ST-Common, line 112
	if (respData == "error") { return } // library marker davegut.ST-Common, line 113
	log.info "" // library marker davegut.ST-Common, line 114
	respData.items.each { // library marker davegut.ST-Common, line 115
		log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 116
	} // library marker davegut.ST-Common, line 117
	log.warn "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 118
} // library marker davegut.ST-Common, line 119

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 121
	Integer currTime = now() // library marker davegut.ST-Common, line 122
	Integer compTime // library marker davegut.ST-Common, line 123
	try { // library marker davegut.ST-Common, line 124
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 125
	} catch (e) { // library marker davegut.ST-Common, line 126
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 127
	} // library marker davegut.ST-Common, line 128
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 129
//	return [compTime: compTime, currTime: currTime, timeRemaining: "${timeRemaining} secs"] // library marker davegut.ST-Common, line 130
	return timeRemaining // library marker davegut.ST-Common, line 131
} // library marker davegut.ST-Common, line 132
def yyycalcTimeRemaining(compTime) { // library marker davegut.ST-Common, line 133
	Integer currentTime = new Date().getTime() // library marker davegut.ST-Common, line 134
	Integer finishTime = Date.parse("yyyy'-'MM'-'dd'T'HH':'mm':'ss'Z'", compTime).getTime() // library marker davegut.ST-Common, line 135
	finishTime = finishTime + location.timeZone.rawOffset // library marker davegut.ST-Common, line 136
	def timeRemaining = ((finishTime - currentTime)/1000).toInteger() // library marker davegut.ST-Common, line 137
	return timeRemaining // library marker davegut.ST-Common, line 138
} // library marker davegut.ST-Common, line 139
def xxcalcTimeRemaining(compTime) { // library marker davegut.ST-Common, line 140
	Integer currentTime = new Date().getTime() // library marker davegut.ST-Common, line 141
	Integer finishTime = Date.parse("yyyy'-'MM'-'dd'T'HH':'mm':'ss'Z'", compTime).getTime() // library marker davegut.ST-Common, line 142
	finishTime = finishTime + location.timeZone.rawOffset // library marker davegut.ST-Common, line 143
	def timeRemaining = ((finishTime - currentTime)/1000).toInteger() // library marker davegut.ST-Common, line 144
	return timeRemaining // library marker davegut.ST-Common, line 145
} // library marker davegut.ST-Common, line 146

// ~~~~~ end include (450) davegut.ST-Common ~~~~~
