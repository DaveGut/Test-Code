/*	===== HUBITAT Samsung Washer Using SmartThings ==========================================
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
===========================================================================================*/
import groovy.json.JsonSlurper
def driverVer() { return "0.1" }
metadata {
	definition (name: "Samsung Washer via ST",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
		capability "Refresh"
		attribute "switch", "string"
		command "setMachineState", [[
			name: "Washer State", type: "ENUM",
			constraints: ["pause", "run", "stop"]]]
//		attribute "completionTime", "string"
		attribute "dryerJobState", "string"
		attribute "machineState", "string"
		attribute "kidsLock", "string"
		attribute "remoteControlEnabled", "string"
		command "getDeviceList"
		attribute "timeRemaining", "integer"
		capability "PushableButton"
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
	logInfo("updated: ${commonStatus}")
}

def refresh() { commonRefresh() }

def deviceStatusParse(resp, data) {
	def respData = validateResp(resp, "deviceStatusParse")
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
		def machineState = mainData.washerOperatingState.machineState.value
		if (device.currentValue("machineState") != machineState) {
			sendEvent(name: "machineState", value: machineState)
			logData << [machineState: machineState]
		}
	} catch (e) { logWarn("deviceStatusParse: machineState") }
	
	try {
		def jobState = mainData.washerOperatingState.dryerJobState.value
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
	} catch (e) { logWarn("deviceStatusParse: remoteControlEnabledh") }
	
	try {
		def compTime = mainData.washerOperatingState.completionTime.value
		if (compTime != null) {
			def timeRemaining = calcTimeRemaining(compTime)
			if (device.currentValue("timeRemaining") != timeRemaining) {
				sendEvent(name: "timeRemaining", value: timeRemaining)
				logData << [timeRemaining: timeRemaining]
			}
		}
	} catch (e) { logWarn("deviceStatusParse: timeRemaining") }

	if (logData != [:]) {
		logInfo("getDeviceStatus: ${logData}")
	}
//	Temp Test Code
	runIn(1, listAttributes)
}

//	===== Command Tests =====
def setMachineState(machState) {
	logDebug("setMachineState: ${machState}")
	def logData = [:]
	def remoteControlEnabled = device.currentValue("remoteControlEnabled")
	if (remoteControlEnabled == "true") {
		def sendData = [
			path: "/devices/${stDeviceId.trim()}/commands",
			comp: "main",
			cap: "washerOperatingState",
			cmd: "setMachineState",
			params: [machState],
			parse: "setMachineState"]
		def respData = syncPost(sendData)
		if (respData == "error") {
			logData << [error: "error from setMachineState"]
		} else if (respData.status == "OK") {
			refresh()
		} else {
			logData << [error: respData]
		}
	} else {
		logData << [error: "failed", remoteControlEnabled: "false"]
	}
	logInfo("[setMachineState: ${logData}]")
}

def toggleRunPause() {
	def pauseRun = "run"
	if (device.currentValue("machineState") == "run") {
		pauseRun = "pause"
	}
	setMachineState(pauseRun)
}

//	===== button interface =====
def push(pushed) {
	logDebug("push: button = ${pushed}")
	if (pushed == null) {
		logWarn("push: pushed is null.  Input ignored")
		return
	}
	pushed = pushed.toInteger()
	switch(pushed) {
		//	===== Physical Remote Commands =====
		case 1 : toggleRunPause(); break
		case 2 : setMachineState("stop"); break
		default:
			logDebug("push: Invalid Button Number!")
			break
	}
}

//	===== Library Integration =====



// ~~~~~ start include (387) davegut.ST-Comms ~~~~~
library ( // library marker davegut.ST-Comms, line 1
	name: "ST-Comms", // library marker davegut.ST-Comms, line 2
	namespace: "davegut", // library marker davegut.ST-Comms, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Comms, line 4
	description: "ST Communications Methods", // library marker davegut.ST-Comms, line 5
	category: "utilities", // library marker davegut.ST-Comms, line 6
	documentationLink: "" // library marker davegut.ST-Comms, line 7
) // library marker davegut.ST-Comms, line 8

def validateResp(resp, method) { // library marker davegut.ST-Comms, line 10
	if (resp.status != 200) { // library marker davegut.ST-Comms, line 11

		def errorResp = [status: "error", // library marker davegut.ST-Comms, line 13
						 httpCode: resp.status, // library marker davegut.ST-Comms, line 14
						 errorMsg: resp.errorMessage] // library marker davegut.ST-Comms, line 15
		logWarn("${method}: ${errorResp}") // library marker davegut.ST-Comms, line 16
		return "error" // library marker davegut.ST-Comms, line 17
	} else { // library marker davegut.ST-Comms, line 18
		try { // library marker davegut.ST-Comms, line 19
			return new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Comms, line 20
		} catch (err) { // library marker davegut.ST-Comms, line 21
			logWarn("${method}: [noDataError: ${err}]") // library marker davegut.ST-Comms, line 22
		} // library marker davegut.ST-Comms, line 23
	} // library marker davegut.ST-Comms, line 24
} // library marker davegut.ST-Comms, line 25

//	Asynchronous send commands // library marker davegut.ST-Comms, line 27
private asyncGet(sendData) { // library marker davegut.ST-Comms, line 28
//	sendData Spec: [path, parse] // library marker davegut.ST-Comms, line 29
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Comms, line 30
		logWarn("asyncGet: [status: ${error}, statusReason: no stApiKey]") // library marker davegut.ST-Comms, line 31
		return // library marker davegut.ST-Comms, line 32
	} // library marker davegut.ST-Comms, line 33
	logDebug("asyncGet: [apiKey: ${stApiKey}, sendData: ${sendData}]") // library marker davegut.ST-Comms, line 34
	def sendCmdParams = [ // library marker davegut.ST-Comms, line 35
		uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Comms, line 36
		path: sendData.path, // library marker davegut.ST-Comms, line 37
		headers: ['Authorization': 'Bearer ' + stApiKey.trim()]] // library marker davegut.ST-Comms, line 38
	try { // library marker davegut.ST-Comms, line 39
		asynchttpGet(sendData.parse, sendCmdParams) // library marker davegut.ST-Comms, line 40
	} catch (error) { // library marker davegut.ST-Comms, line 41
		logWarn("asyncGet: [status: error, statusReason: ${error}]") // library marker davegut.ST-Comms, line 42
	} // library marker davegut.ST-Comms, line 43
} // library marker davegut.ST-Comms, line 44

private syncPost(sendData){ // library marker davegut.ST-Comms, line 46
//	sendData Spec: [path, comp, cap, cmd, params, parse] // library marker davegut.ST-Comms, line 47
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Comms, line 48
		logWarn("asyncPost: [status: ${error}, statusReason: no stApiKey]") // library marker davegut.ST-Comms, line 49
		return // library marker davegut.ST-Comms, line 50
	} // library marker davegut.ST-Comms, line 51
	logDebug("syncPost: [apiKey: ${stApiKey}, sendData: ${sendData}]") // library marker davegut.ST-Comms, line 52
	def cmdData = [ // library marker davegut.ST-Comms, line 53
		component: sendData.comp, // library marker davegut.ST-Comms, line 54
		capability: sendData.cap, // library marker davegut.ST-Comms, line 55
		command: sendData.cmd, // library marker davegut.ST-Comms, line 56
		arguments: sendData.params // library marker davegut.ST-Comms, line 57
		] // library marker davegut.ST-Comms, line 58
	def cmdBody = [commands: [cmdData]] // library marker davegut.ST-Comms, line 59
	def sendCmdParams = [ // library marker davegut.ST-Comms, line 60
		uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Comms, line 61
		path: sendData.path, // library marker davegut.ST-Comms, line 62
		headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Comms, line 63
		body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Comms, line 64
	] // library marker davegut.ST-Comms, line 65
	def respData = "error" // library marker davegut.ST-Comms, line 66
	try { // library marker davegut.ST-Comms, line 67
		httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Comms, line 68
			if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Comms, line 69
				respData = [status: "OK", results: resp.data.results] // library marker davegut.ST-Comms, line 70
			} else { // library marker davegut.ST-Comms, line 71
				def errorResp = [status: "error", // library marker davegut.ST-Comms, line 72
								 reqMethod: sendData.parse, // library marker davegut.ST-Comms, line 73
								 httpCode: resp.status, // library marker davegut.ST-Comms, line 74
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Comms, line 75
				logDebug("syncPost error from ST: ${errorResp}") // library marker davegut.ST-Comms, line 76
			} // library marker davegut.ST-Comms, line 77
		} // library marker davegut.ST-Comms, line 78
	} catch (error) { // library marker davegut.ST-Comms, line 79
		def errorResp = [status: "error", // library marker davegut.ST-Comms, line 80
						 reqMethod: sendData.parse, // library marker davegut.ST-Comms, line 81
						 errorMsg: error] // library marker davegut.ST-Comms, line 82
		logDebug("syncPost: ${errorResp}") // library marker davegut.ST-Comms, line 83
	} // library marker davegut.ST-Comms, line 84
	return respData // library marker davegut.ST-Comms, line 85
} // library marker davegut.ST-Comms, line 86

//	======================================================== // library marker davegut.ST-Comms, line 88
//	===== Logging ========================================== // library marker davegut.ST-Comms, line 89
//	======================================================== // library marker davegut.ST-Comms, line 90
def logTrace(msg){ // library marker davegut.ST-Comms, line 91
	log.trace "${device.label} V${driverVer()}: ${msg}" // library marker davegut.ST-Comms, line 92
} // library marker davegut.ST-Comms, line 93

def logInfo(msg) {  // library marker davegut.ST-Comms, line 95
	if (infoLog == true) { // library marker davegut.ST-Comms, line 96
		log.info "${device.label} V${driverVer()}: ${msg}" // library marker davegut.ST-Comms, line 97
	} // library marker davegut.ST-Comms, line 98
} // library marker davegut.ST-Comms, line 99

def debugLogOff() { // library marker davegut.ST-Comms, line 101
	device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.ST-Comms, line 102
	logInfo("Debug logging is false.") // library marker davegut.ST-Comms, line 103
} // library marker davegut.ST-Comms, line 104

def logDebug(msg) { // library marker davegut.ST-Comms, line 106
	if (debugLog == true) { // library marker davegut.ST-Comms, line 107
		log.debug "${device.label} V${driverVer()}: ${msg}" // library marker davegut.ST-Comms, line 108
	} // library marker davegut.ST-Comms, line 109
} // library marker davegut.ST-Comms, line 110

def logWarn(msg) { log.warn "${device.label} V${driverVer()}: ${msg}" } // library marker davegut.ST-Comms, line 112

// ~~~~~ end include (387) davegut.ST-Comms ~~~~~

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
	} // library marker davegut.ST-Common, line 22
	if (debugLog) { runIn(1800, debugLogOff) } // library marker davegut.ST-Common, line 23
	updateData << [stApiKey: stApiKey, stDeviceId: stDeviceId] // library marker davegut.ST-Common, line 24
	updateData << [debugLog: debugLog, infoLog: infoLog] // library marker davegut.ST-Common, line 25
	def updateStatus = [:] // library marker davegut.ST-Common, line 26
	updateStatus << [status: status] // library marker davegut.ST-Common, line 27
	if (statusReason != "") { // library marker davegut.ST-Common, line 28
		updateStatus << [statusReason: statusReason] // library marker davegut.ST-Common, line 29
	} // library marker davegut.ST-Common, line 30
	updateStatus << [updateData: updateData] // library marker davegut.ST-Common, line 31
	refresh() // library marker davegut.ST-Common, line 32
	return updateStatus // library marker davegut.ST-Common, line 33
} // library marker davegut.ST-Common, line 34

def commonRefresh() { // library marker davegut.ST-Common, line 36
/*	Design // library marker davegut.ST-Common, line 37
	a.	Complete a deviceRefresh // library marker davegut.ST-Common, line 38
		1.	Ignore response. Will refresh data in ST for device. // library marker davegut.ST-Common, line 39
	b.	run getDeviceStatus // library marker davegut.ST-Common, line 40
		1.	Capture switch attributes // library marker davegut.ST-Common, line 41
			a) if on, set runEvery1Minute(refresh) // library marker davegut.ST-Common, line 42
			b)	if off, set runEvery10Minutes(refresh) // library marker davegut.ST-Common, line 43
		2.	Capture other attributes // library marker davegut.ST-Common, line 44
		3.	Log a list of current attribute states.  (Temporary) // library marker davegut.ST-Common, line 45
*/ // library marker davegut.ST-Common, line 46
	def sendData = [ // library marker davegut.ST-Common, line 47
		path: "/devices/${stDeviceId.trim()}/commands", // library marker davegut.ST-Common, line 48
		comp: "main", // library marker davegut.ST-Common, line 49
		cap: "refresh", // library marker davegut.ST-Common, line 50
		cmd: "refresh", // library marker davegut.ST-Common, line 51
		params: [], // library marker davegut.ST-Common, line 52
		parse: "stRefresh" // library marker davegut.ST-Common, line 53
	] // library marker davegut.ST-Common, line 54
	def respData = syncPost(sendData) // library marker davegut.ST-Common, line 55
	getDeviceStatus() // library marker davegut.ST-Common, line 56
} // library marker davegut.ST-Common, line 57
def getDeviceStatus() { // library marker davegut.ST-Common, line 58
	def sendData = [ // library marker davegut.ST-Common, line 59
		path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 60
		parse: "deviceStatusParse" // library marker davegut.ST-Common, line 61
		] // library marker davegut.ST-Common, line 62
	asyncGet(sendData) // library marker davegut.ST-Common, line 63
} // library marker davegut.ST-Common, line 64
def listAttributes() { // library marker davegut.ST-Common, line 65
	def attrs = device.getSupportedAttributes() // library marker davegut.ST-Common, line 66
	def attrList = [:] // library marker davegut.ST-Common, line 67
	attrs.each { // library marker davegut.ST-Common, line 68
		def val = device.currentValue("${it}") // library marker davegut.ST-Common, line 69
		attrList << ["${it}": val] // library marker davegut.ST-Common, line 70
	} // library marker davegut.ST-Common, line 71
//	logDebug("Attributes: ${attrList}") // library marker davegut.ST-Common, line 72
	logTrace("Attributes: ${attrList}") // library marker davegut.ST-Common, line 73
} // library marker davegut.ST-Common, line 74

def getDeviceList() { // library marker davegut.ST-Common, line 76
	def sendData = [ // library marker davegut.ST-Common, line 77
		path: "/devices", // library marker davegut.ST-Common, line 78
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 79
		] // library marker davegut.ST-Common, line 80
	asyncGet(sendData) // library marker davegut.ST-Common, line 81
} // library marker davegut.ST-Common, line 82
def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 83
	def respData = validateResp(resp, "getDeviceListParse") // library marker davegut.ST-Common, line 84
	if (respData == "error") { return } // library marker davegut.ST-Common, line 85
	log.info "" // library marker davegut.ST-Common, line 86
	respData.items.each { // library marker davegut.ST-Common, line 87
		def deviceData = [ // library marker davegut.ST-Common, line 88
			label: it.label, // library marker davegut.ST-Common, line 89
			manufacturer: it.manufacturerName, // library marker davegut.ST-Common, line 90
			presentationId: it.presentationId, // library marker davegut.ST-Common, line 91
			deviceId: it.deviceId // library marker davegut.ST-Common, line 92
		] // library marker davegut.ST-Common, line 93
		log.info "Found: ${deviceData}" // library marker davegut.ST-Common, line 94
		log.info "" // library marker davegut.ST-Common, line 95
	} // library marker davegut.ST-Common, line 96
} // library marker davegut.ST-Common, line 97

def calcTimeRemaining(compTime) { // library marker davegut.ST-Common, line 99
	Integer currentTime = new Date().getTime() // library marker davegut.ST-Common, line 100
	Integer finishTime = Date.parse("yyyy'-'MM'-'dd'T'HH':'mm':'ss'Z'", compTime).getTime() // library marker davegut.ST-Common, line 101
	finishTime = finishTime + location.timeZone.rawOffset // library marker davegut.ST-Common, line 102
	def timeRemaining = ((finishTime - currentTime)/1000).toInteger() // library marker davegut.ST-Common, line 103
	return timeRemaining // library marker davegut.ST-Common, line 104
} // library marker davegut.ST-Common, line 105

// ~~~~~ end include (450) davegut.ST-Common ~~~~~
