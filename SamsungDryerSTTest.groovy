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
0.2		Fixed timeRemaining
		Adjusted final attributes.
0.3	a.	Remove attribute dryLevel
	b.	stApiKey: Set preference type to password. Removed logging.
	c.	Removed error encapsulation of attribute processing in method deviceStatusParse
	d.	Removed logging all attributes on each refresh (done for testing)
	e.	Removed simulation code methods and steps in refresh method.
===========================================================================================*/
import groovy.json.JsonSlurper
def driverVer() { return "0.3T" }

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
		attribute "jobState", "string"
		attribute "machineState", "string"
		attribute "kidsLock", "string"
		attribute "remoteControlEnabled", "string"
		command "getDeviceList"
		capability "PushableButton"
		//	Attributes under test.
		attribute "completionTime", "string"
		attribute "timeRemaining", "integer"
		attribute "dryingTemperature", "string"
		attribute "wrinklePrevent", "string"
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

def refresh() { commonRefresh() }

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
	
	def kidsLock = mainData["samsungce.kidsLock"].lockState.value
	if (device.currentValue("kidsLock") != kidsLock) {
		sendEvent(name: "kidsLock", value: kidsLock)
		logData << [kidsLock: kidsLock]
	}

	def machineState = mainData.dryerOperatingState.machineState.value
	if (device.currentValue("machineState") != machineState) {
		sendEvent(name: "machineState", value: machineState)
		logData << [machineState: machineState]
	}
	
	def jobState = mainData.dryerOperatingState.dryerJobState.value
	if (device.currentValue("jobState") != jobState) {
		sendEvent(name: "jobState", value: jobState)
		logData << [jobState: jobState]
	}

	def remoteControlEnabled = mainData.remoteControlStatus.remoteControlEnabled.value
	if (device.currentValue("remoteControlEnabled") != remoteControlEnabled) {
		sendEvent(name: "remoteControlEnabled", value: remoteControlEnabled)
		logData << [remoteControlEnabled: remoteControlEnabled]
	}

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

	def wrinklePrevent = mainData["custom.dryerWrinklePrevent"].dryerWrinklePrevent.value
	if (device.currentValue("wrinklePrevent") != wrinklePrevent) {
		sendEvent(name: "wrinklePrevent", value: wrinklePrevent)
		logData << [wrinklePrevent: wrinklePrevent]
	}

	def dryingTemperature = mainData["samsungce.dryerDryingTemperature"].dryingTemperature.value
	if (device.currentValue("dryingTemperature") != dryingTemperature) {
		sendEvent(name: "dryingTemperature", value: dryingTemperature)
		logData << [dryingTemperature: dryingTemperature]
	}
	
	if (logData != [:]) {
		logInfo("getDeviceStatus: ${logData}")
	}
//	runIn(1, listAttributes)
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
	logDebug("asyncGet: [sendData: ${sendData}]") // library marker davegut.ST-Communications, line 35
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
	logDebug("syncPost: [cmdBody: ${cmdData}, path: ${path}]") // library marker davegut.ST-Communications, line 52
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
		updateData << [stDeviceId: stDeviceId] // library marker davegut.ST-Common, line 24
		updateData << [debugLog: debugLog, infoLog: infoLog] // library marker davegut.ST-Common, line 25
		if (!getDataValue("driverVersion") ||  // library marker davegut.ST-Common, line 26
			getDataValue("driverVersion") != driverVer()) { // library marker davegut.ST-Common, line 27
			updateDataValue("driverVersion", driverVer()) // library marker davegut.ST-Common, line 28
			updateData << [driverVer: driverVer()] // library marker davegut.ST-Common, line 29
		} // library marker davegut.ST-Common, line 30
		refresh() // library marker davegut.ST-Common, line 31
	} // library marker davegut.ST-Common, line 32
	def updateStatus = [:] // library marker davegut.ST-Common, line 33
	updateStatus << [status: status] // library marker davegut.ST-Common, line 34
	if (statusReason != "") { // library marker davegut.ST-Common, line 35
		updateStatus << [statusReason: statusReason] // library marker davegut.ST-Common, line 36
	} // library marker davegut.ST-Common, line 37
	updateStatus << [updateData: updateData] // library marker davegut.ST-Common, line 38
	return updateStatus // library marker davegut.ST-Common, line 39
} // library marker davegut.ST-Common, line 40

def cmdRespParse(respData) { // library marker davegut.ST-Common, line 42
	def logData = [:] // library marker davegut.ST-Common, line 43
	if (respData == "error") { // library marker davegut.ST-Common, line 44
		logData << [error: "error from setMachineState"] // library marker davegut.ST-Common, line 45
	} else if (respData.status == "OK") { // library marker davegut.ST-Common, line 46
		runIn(2, refresh) // library marker davegut.ST-Common, line 47
	} else { // library marker davegut.ST-Common, line 48
		logData << [error: respData] // library marker davegut.ST-Common, line 49
	} // library marker davegut.ST-Common, line 50
} // library marker davegut.ST-Common, line 51

def commonRefresh() { // library marker davegut.ST-Common, line 53
	def cmdData = [ // library marker davegut.ST-Common, line 54
		component: "main", // library marker davegut.ST-Common, line 55
		capability: "refresh", // library marker davegut.ST-Common, line 56
		command: "refresh", // library marker davegut.ST-Common, line 57
		arguments: []] // library marker davegut.ST-Common, line 58
	syncPost(cmdData) // library marker davegut.ST-Common, line 59
	def respData = syncPost(sendData) // library marker davegut.ST-Common, line 60
	getDeviceStatus() // library marker davegut.ST-Common, line 61
} // library marker davegut.ST-Common, line 62

def getDeviceStatus(parseMethod = "deviceStatusParse") { // library marker davegut.ST-Common, line 64
	def sendData = [ // library marker davegut.ST-Common, line 65
		path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 66
		parse: parseMethod // library marker davegut.ST-Common, line 67
		] // library marker davegut.ST-Common, line 68
	asyncGet(sendData) // library marker davegut.ST-Common, line 69
} // library marker davegut.ST-Common, line 70

def listAttributes() { // library marker davegut.ST-Common, line 72
	def attrs = device.getSupportedAttributes() // library marker davegut.ST-Common, line 73
	def attrList = [:] // library marker davegut.ST-Common, line 74
	attrs.each { // library marker davegut.ST-Common, line 75
		def val = device.currentValue("${it}") // library marker davegut.ST-Common, line 76
		attrList << ["${it}": val] // library marker davegut.ST-Common, line 77
	} // library marker davegut.ST-Common, line 78
//	logDebug("Attributes: ${attrList}") // library marker davegut.ST-Common, line 79
	logTrace("Attributes: ${attrList}") // library marker davegut.ST-Common, line 80
} // library marker davegut.ST-Common, line 81

def on() { setSwitch("on") } // library marker davegut.ST-Common, line 83
def off() { setSwitch("off") } // library marker davegut.ST-Common, line 84
def setSwitch(onOff) { // library marker davegut.ST-Common, line 85
	logDebug("setSwitch: ${onOff}") // library marker davegut.ST-Common, line 86
	def cmdData = [ // library marker davegut.ST-Common, line 87
		component: "main", // library marker davegut.ST-Common, line 88
		capability: "switch", // library marker davegut.ST-Common, line 89
		command: onOff, // library marker davegut.ST-Common, line 90
		arguments: []] // library marker davegut.ST-Common, line 91
	cmdRespParse(syncPost(cmdData)) // library marker davegut.ST-Common, line 92
} // library marker davegut.ST-Common, line 93

def getDeviceList() { // library marker davegut.ST-Common, line 95
	def sendData = [ // library marker davegut.ST-Common, line 96
		path: "/devices", // library marker davegut.ST-Common, line 97
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 98
		] // library marker davegut.ST-Common, line 99
	asyncGet(sendData) // library marker davegut.ST-Common, line 100
} // library marker davegut.ST-Common, line 101
def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 102
	def respData = validateResp(resp, "getDeviceListParse") // library marker davegut.ST-Common, line 103
	if (respData == "error") { return } // library marker davegut.ST-Common, line 104
	log.info "" // library marker davegut.ST-Common, line 105
	respData.items.each { // library marker davegut.ST-Common, line 106
		log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 107
	} // library marker davegut.ST-Common, line 108
	log.warn "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 109
} // library marker davegut.ST-Common, line 110

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 112
	Integer currTime = now() // library marker davegut.ST-Common, line 113
	Integer compTime // library marker davegut.ST-Common, line 114
	try { // library marker davegut.ST-Common, line 115
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 116
	} catch (e) { // library marker davegut.ST-Common, line 117
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 118
	} // library marker davegut.ST-Common, line 119
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 120
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 121
	return timeRemaining // library marker davegut.ST-Common, line 122
} // library marker davegut.ST-Common, line 123

// ~~~~~ end include (450) davegut.ST-Common ~~~~~
