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
0.05	a.	Temp: Modify logInfo and logDebug to always log.
		b.	Fixed noted errors from test analysis.
		c.	Changed polling to add deviceRefresh after detecting switch = on, 
			then get the device status.
		d.	Temp: added routine to get all attributes after each stGetDeviceData.
		e.	Changed comms to async.
0.06	Next iteration.  Added simulation capability for developer.
===========================================================================================*/
import groovy.json.JsonSlurper
def driverVer() { return "0.06T" }

def simulate() { return false }
def testData() {
	def kidsLock = "unlocked"
	def doorState = "closed"
	def cavityStatus = "on"
	def cooktopState = "ready"
	def remoteControl = "false"
	
	def setpoint = "225"
	def temp = "300"
	def endTime = "2022-04-28T21:03:35.698Z"
	def ovenState = "running"
	def ovenJobState = "cooking"
	def ovenMode = "ConvectionBake"
	def ovenModeZ = "Bake"
	def ovenJobStateZ = "broiling"
	
	def probeStatus = "disconnected"
	def probeTemp = "200"
	def probeSetpoint = "225"
	
	def cavitySetpoint = "325"
	def cavityTemp = "300"
	def cavityEndTime = "2022-04-28T21:03:35.698Z"
	def cavityState = "running"
	def cavityJobState = "cooking"
	def cavityMode = "Bake"
	def cavityModeZ = "Convection"
	def cavityJobStateZ = "broiling"

	return  [components:[
		"cavity-01":[
			ovenSetpoint:[ovenSetpoint:[value: cavitySetpoint]],
			temperatureMeasurement:[temperature:[value: cavityTemp]],
			"custom.ovenCavityStatus":[ovenCavityStatus:[value: cavityStatus]],
			"samsungce.ovenMode":[ovenMode:[value: cavityMode]],
			ovenOperatingState:[
				completionTime:[value: cavityEndTime], 
				machineState:[value: cavityState], 
				ovenJobState:[value: cavityJobState]],
			"samsungce.ovenOperatingState":[ovenJobState:[value: cavityJobStateZ]],
			ovenMode:[ovenMode:[value: cavityModeZ]]],
		main:[
			ovenSetpoint:[ovenSetpoint:[value: setpoint]],
			"samsungce.meatProbe":[
				temperatureSetpoint:[value: probeSetpoint],
				temperature:[value: probeTemp], 
				status:[value:probeStatus]],
			"samsungce.doorState":[doorState:[value: doorState]],
			remoteControlStatus:[remoteControlEnabled:[value: remoteControl]],
			"custom.cooktopOperatingState":[cooktopOperatingState:[value: cooktopState]],
			temperatureMeasurement:[temp:[value:ovenTemp]],
			ovenOperatingState:[
				completionTime:[value: endTime], 
				machineState:[value: ovenState], 
				ovenJobState:[value: ovenJobState]],
			"samsungce.ovenMode":[ovenMode:[value: ovenMode]],
			"samsungce.kidsLock":[lockState:[value:kidsLock]],
			"samsungce.ovenOperatingState":[ovenJobState:[value: ovenJobStateZ]], 
			ovenMode:[ovenMode:[value: ovenModeZ]],
		]
	]]
}

metadata {
	definition (name: "Samsung Oven via ST",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
		capability "Refresh"
		command "getDeviceList"
		command "pauseMain"
		command "pauseCavity"
		command "stopMain"
		command "stopCavity"
		//	Oven Attributes
		attribute "kidsLock", "string"
		attribute "doorState", "string"
		attribute "cavityStatus", "string"
		attribute "cooktopState", "string"
		attribute "remoteControl", "string"
		//	Main Oven Attributes
		attribute "setpoint", "string"
		attribute "temperature", "string"
		attribute "endTime", "string"
		attribute "timeRemaining", "string"
		attribute "ovenState", "string"
		attribute "ovenJobState", "string"
		attribute "ovenMode", "string"
		attribute "ovenJobStateZ", "string"
		attribute "ovenModeZ", "string"
		//	Probe Attribute
		attribute "probeStatus", "string"
		attribute "probeTemperature", "string"
		attribute "probeSetpoint", "string"
		//	Cavity Attributes
		attribute "cavitySetpoint", "string"
		attribute "cavityTemperature", "string"
		attribute "cavityEndTime", "string"
		attribute "cavityTimeRemaining", "string"
		attribute "cavityState", "string"
		attribute "cavityJobState", "string"
		attribute "cavityMode", "string"
		attribute "cavityJobStateZ", "string"
		attribute "cavityModeZ", "string"
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
//////////////////////////
def updated() {
	def commonStatus = commonUpdate()
//	state.pushDefinitions = [1: "run", 2: "pause", 3: "stop"]
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
	def logData = [:]
	def mainData = respData.components.main
	def cavityData = "none"
	try {
		cavityData = respData.components.cavity-01
	} catch (error) { logData << [cavityData: error] }

	try {
		def kidsLock = mainData["samsungce.kidsLock"].lockState.value
		if (device.currentValue("kidsLock") != kidsLock) {
			sendEvent(name: "kidsLock", value: kidsLock)
			logData << [kidsLock: kidsLock]
		}
	} catch (e) { logWarn("deviceStatusParse: kidsLock") }

	try {
		def doorState = mainData["samsungce.doorState"].doorState.value
		if (device.currentValue("doorState") != doorState) {
			sendEvent(name: "doorState", value: doorState)
			logData << [doorState: doorState]
		}
	} catch (e) { logWarn("deviceStatusParse: doorState") }

	try {
		def cooktopState = mainData["custom.cooktopOperatingState"].cooktopOperatingState.value
		if (device.currentValue("cooktopState") != cooktopState) {
			sendEvent(name: "cooktopState", value: cooktopState)
			logData << [cooktopState: cooktopState]
		}
	} catch (e) { logWarn("deviceStatusParse: cooktopState") }
		
	try {
		def remoteControl = mainData.remoteControlStatus.remoteControlEnabled.value
		if (device.currentValue("remoteControl") != remoteControl) {
			sendEvent(name: "remoteControl", value: remoteControl)
			logData << [remoteControl: remoteControl]
		}
	} catch (e) { logWarn("deviceStatusParse: remoteControl") }
	
	try {
		def setpoint = mainData.ovenSetpoint.ovenSetpoint.value
		if (device.currentValue("setpoint") != setpoint) {
			sendEvent(name: "setpoint", value: setpoint)
			logData << [setpoint: setpoint]
		}
	} catch (e) { logWarn("deviceStatusParse: setpoint") }

	try {
		def temperature = mainData.temperatureMeasurement.temperature.value
		if (device.currentValue("temperature") != temperature) {
			sendEvent(name: "temp", value: temperature)
			logData << [temperature: temperature]
		}
	} catch (e) { logWarn("deviceStatusParse: temperature") }
	
	try {
		def endTime = mainData.ovenOperatingState.completionTime.value
		if (endTime != null) {
			sendEvent(name: "endTime", value: endTime)
			logData << [endTime: endTime]
			def timeRemaining = calcTimeRemaining(endTime)
			if (device.currentValue("timeRemaining") != timeRemaining) {
				sendEvent(name: "timeRemaining", value: timeRemaining)
				logData << [timeRemaining: timeRemaining]
			}
		}
	} catch (e) { logWarn("deviceStatusParse: timeRemaining") }

	try {
		def ovenState = mainData.ovenOperatingState.machineState.value
		if (device.currentValue("ovenState") != ovenState) {
			sendEvent(name: "ovenState", value: ovenState)
			logData << [ovenState: ovenState]
		}
	} catch (e) { logWarn("deviceStatusParse: ovenState") }
	
	try {
		def ovenJobState = mainData.ovenOperatingState.ovenJobState.value
		if (device.currentValue("ovenJobState") != ovenJobState) {
			sendEvent(name: "ovenJobState", value: ovenJobState)
			logData << [ovenJobState: ovenJobState]
		}
	} catch (e) { logWarn("deviceStatusParse: ovenJobState") }
	
	try {
		def ovenMode = mainData["samsungce.ovenMode"].ovenMode.value
		if (device.currentValue("ovenMode") != ovenMode) {
			sendEvent(name: "ovenMode", value: ovenMode)
			logData << [ovenMode: ovenMode]
		}
	} catch (e) { logWarn("deviceStatusParse: ovenMode") }
	
	try {
		def ovenJobStateZ = mainData["samsungce.ovenOperatingState"].ovenJobState.value
		if (device.currentValue("ovenJobStateZ") != ovenJobStateZ) {
			sendEvent(name: "ovenJobStateZ", value: ovenJobStateZ)
			logData << [ovenJobStateZ: ovenJobStateZ]
		}
	} catch (e) { logWarn("deviceStatusParse: ovenJobStateZ") }
	
	try {
		def ovenModeZ = mainData.ovenMode.ovenMode.value
		if (device.currentValue("ovenModeZ") != ovenModeZ) {
			sendEvent(name: "ovenModeZ", value: ovenModeZ)
			logData << [ovenModeZ: ovenModeZ]
		}
	} catch (e) { logWarn("deviceStatusParse: ovenMode") }
	
	try {
		def probeStatus = mainData["samsungce.meatProbe"].status.value
		if (device.currentValue("probeStatus") != probeStatus) {
			sendEvent(name: "probeStatus", value: probeStatus)
			logData << [probeStatus: probeStatus]
		}
	} catch (e) { logWarn("deviceStatusParse: probeStatus") }
	
	try {
		def probeTemperature = mainData["samsungce.meatProbe"].temperature.value
		if (device.currentValue("probeTemperature") != probeTemperature) {
			sendEvent(name: "probeTemperature", value: probeTemperature)
			logData << [probeTemperature: probeTemperature]
		}
	} catch (e) { logWarn("deviceStatusParse: probeTemperature") }
	
	try {
		def probeSetpoint = mainData["samsungce.meatProbe"].temperatureSetpoint.value
		if (device.currentValue("probeSetpoint") != probeSetpoint) {
			sendEvent(name: "probeSetpoint", value: probeSetpoint)
			logData << [probeSetpoint: probeSetpoint]
		}
	} catch (e) { logWarn("deviceStatusParse: probeSetpoint") }
	
	def cavityStatus = "off"
	try {
		cavityStatus = cavityData["custom.ovenCavityStatus"].ovenCavityStatus.value
	} catch (e) { logWarn("deviceStatusParse: cavityStatus") }
	if (device.currentValue("cavityStatus") != cavityStatus) {
		sendEvent(name: "cavityStatus", value: cavityStatus)
		logData << [cavityStatus: cavityStatus]
	}
	
	if (cavityStatus == "on") {
		try {
			def cavitySetpoint = cavityData.ovenSetpoint.ovenSetpoint.value
			if (device.currentValue("cavitySetpoint") != cavitySetpoint) {
				sendEvent(name: "cavitySetpoint", value: cavitySetpoint)
				logData << [cavitySetpoint: cavitySetpoint]
			}
		} catch (e) { logWarn("deviceStatusParse: cavitySetpoint") }

		try {
			def cavityTemperature = cavityData.temperatureMeasurement.temperature.value
			if (device.currentValue("cavityTemperature") != cavityTemperature) {
				sendEvent(name: "cavityTemperature", value: cavityTemperature)
				logData << [cavityTemperature: cavityTemperature]
			}
		} catch (e) { logWarn("deviceStatusParse: cavityTemperature") }

		try {
			def cavityEndTime = cavityData.ovenOperatingState.completionTime.value
			if (cavityEndTime != null) {
				sendEvent(name: "cavityEndTime", value: cavityEndTime)
				logData << [cavityEndTime: cavityEndTime]
				def cavityTimeRemaining = calcTimeRemaining(cavityEndTime)
				if (device.currentValue("cavityTimeRemaining") != cavityTimeRemaining) {
					sendEvent(name: "timeRemaining", value:cavityTimeRemaining)
					logData << [cavityTimeRemaining: cavityTimeRemaining]
				}
			}
		} catch (e) { logWarn("deviceStatusParse: cavityTimeRemaining") }

		try {
			def cavityState = cavityData.ovenOperatingState.machineState.value
			if (device.currentValue("cavityState") != cavityState) {
				sendEvent(name: "cavityState", value: cavityState)
				logData << [cavityState: cavityState]
			}
		} catch (e) { logWarn("deviceStatusParse: cavityState") }
	
		try {
			def cavityJobState = cavityData.ovenOperatingState.ovenJobState.value
			if (device.currentValue("cavityJobState") != cavityJobState) {
				sendEvent(name: "cavityJobState", value: cavityJobState)
				logData << [cavityJobState: cavityJobState]
			}
		} catch (e) { logWarn("deviceStatusParse: cavityJobState") }
	
		try {
			def cavityMode = cavityData["samsungce.ovenMode"].ovenMode.value
			if (device.currentValue("cavityMode") != cavityMode) {
				sendEvent(name: "cavityMode", value: cavityMode)
				logData << [cavityMode: cavityMode]
			}
		} catch (e) { logWarn("deviceStatusParse: cavityMode") }

		try {
			def cavityJobStateZ = cavityData["samsungce.ovenOperatingState"].ovenJobState.value
			if (device.currentValue("cavityJobStateZ") != cavityJobStateZ) {
				sendEvent(name: "cavityJobStateZ", value: cavityJobStateZ)
				logData << [cavityJobStateZ: cavityJobStateZ]
			}
		} catch (e) { logWarn("deviceStatusParse: cavityJobStateZ") }
	
		try {
			def cavityModeZ = cavityData.ovenMode.ovenMode.value
			if (device.currentValue("cavityModeZ") != cavityModeZ) {
				sendEvent(name: "cavityModeZ", value: cavityModeZ)
				logData << [cavityModeZ: cavityModeZ]
			}
		} catch (e) { logWarn("deviceStatusParse: cavityModeZ") }
}
	if (logData != [:]) {
		logDebug("getDeviceStatus: ${logData}")
	}
//	Temp Test Code
	runIn(1, listAttributes)
}
def testCode() {
	def attrs = device.getSupportedAttributes()
	def attrList = [:]
	attrs.each {
		def val = device.currentValue("${it}")
		attrList << ["${it}": val]
	}
	logDebug("Attributes: ${attrList}")
}

//	===== Command Tests =====
def pauseMain() { setMachineState("pause", "main") }
def pauseCavity() { setMachineState("pause", "cavity-01") }
def setMachineState(machState, comp) {
	def remoteControlEnabled = device.currentValue("remoteControlEnabled")
	logDebug("setMachineState: ${machState}, ${comp}, ${remotControlEnabled}")
	if (remoteControlEnabled == "true") {
		def cmdData = [
			component: comp,
			capability: "ovenOperatingState",
			command: "setMachineState",
			arguments: [machState]]
		cmdRespParse(syncPost(cmdData))
	} else {
		logDebug("setMachineState: [status: failed, remoteControlEnabled: false]")
	}
}

def stopMain() { stopMachine("main") }
def stopCavity() { stopMachine("cavity-01") }
def stopMachine(comp) {
	def remoteControlEnabled = device.currentValue("remoteControlEnabled")
	logDebug("stopMachine: ${comp}, ${remotControlEnabled}")
	if (remoteControlEnabled == "true") {
		def cmdData = [
			component: comp,
			capability: "ovenOperatingState",
			command: "stop",
			arguments: []]
		cmdRespParse(syncPost(cmdData))
	} else {
		logDebug("stopMachine: [status: failed, remoteControlEnabled: false]")
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
/*	Design // library marker davegut.ST-Common, line 54
	a.	Complete a deviceRefresh // library marker davegut.ST-Common, line 55
		1.	Ignore response. Will refresh data in ST for device. // library marker davegut.ST-Common, line 56
	b.	run getDeviceStatus // library marker davegut.ST-Common, line 57
		1.	Capture switch attributes // library marker davegut.ST-Common, line 58
			a) if on, set runEvery1Minute(refresh) // library marker davegut.ST-Common, line 59
			b)	if off, set runEvery10Minutes(refresh) // library marker davegut.ST-Common, line 60
		2.	Capture other attributes // library marker davegut.ST-Common, line 61
		3.	Log a list of current attribute states.  (Temporary) // library marker davegut.ST-Common, line 62
*/ // library marker davegut.ST-Common, line 63
	def cmdData = [ // library marker davegut.ST-Common, line 64
		component: "main", // library marker davegut.ST-Common, line 65
		capability: "refresh", // library marker davegut.ST-Common, line 66
		command: "refresh", // library marker davegut.ST-Common, line 67
		arguments: []] // library marker davegut.ST-Common, line 68
	syncPost(cmdData) // library marker davegut.ST-Common, line 69
	def respData = syncPost(sendData) // library marker davegut.ST-Common, line 70
	getDeviceStatus() // library marker davegut.ST-Common, line 71
} // library marker davegut.ST-Common, line 72

def getDeviceStatus(parseMethod = "deviceStatusParse") { // library marker davegut.ST-Common, line 74
	def sendData = [ // library marker davegut.ST-Common, line 75
		path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 76
		parse: parseMethod // library marker davegut.ST-Common, line 77
		] // library marker davegut.ST-Common, line 78
	asyncGet(sendData) // library marker davegut.ST-Common, line 79
} // library marker davegut.ST-Common, line 80

def listAttributes() { // library marker davegut.ST-Common, line 82
	def attrs = device.getSupportedAttributes() // library marker davegut.ST-Common, line 83
	def attrList = [:] // library marker davegut.ST-Common, line 84
	attrs.each { // library marker davegut.ST-Common, line 85
		def val = device.currentValue("${it}") // library marker davegut.ST-Common, line 86
		attrList << ["${it}": val] // library marker davegut.ST-Common, line 87
	} // library marker davegut.ST-Common, line 88
//	logDebug("Attributes: ${attrList}") // library marker davegut.ST-Common, line 89
	logTrace("Attributes: ${attrList}") // library marker davegut.ST-Common, line 90
} // library marker davegut.ST-Common, line 91

def on() { setSwitch("on") } // library marker davegut.ST-Common, line 93
def off() { setSwitch("off") } // library marker davegut.ST-Common, line 94
def setSwitch(onOff) { // library marker davegut.ST-Common, line 95
	logDebug("setSwitch: ${onOff}") // library marker davegut.ST-Common, line 96
	def cmdData = [ // library marker davegut.ST-Common, line 97
		component: "main", // library marker davegut.ST-Common, line 98
		capability: "switch", // library marker davegut.ST-Common, line 99
		command: onOff, // library marker davegut.ST-Common, line 100
		arguments: []] // library marker davegut.ST-Common, line 101
	cmdRespParse(syncPost(cmdData)) // library marker davegut.ST-Common, line 102
} // library marker davegut.ST-Common, line 103

def getDeviceList() { // library marker davegut.ST-Common, line 105
	def sendData = [ // library marker davegut.ST-Common, line 106
		path: "/devices", // library marker davegut.ST-Common, line 107
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 108
		] // library marker davegut.ST-Common, line 109
	asyncGet(sendData) // library marker davegut.ST-Common, line 110
} // library marker davegut.ST-Common, line 111
def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 112
	def respData = validateResp(resp, "getDeviceListParse") // library marker davegut.ST-Common, line 113
	if (respData == "error") { return } // library marker davegut.ST-Common, line 114
	log.info "" // library marker davegut.ST-Common, line 115
	respData.items.each { // library marker davegut.ST-Common, line 116
		log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 117
	} // library marker davegut.ST-Common, line 118
	log.warn "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 119
} // library marker davegut.ST-Common, line 120

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 122
	Integer currTime = now() // library marker davegut.ST-Common, line 123
	Integer compTime // library marker davegut.ST-Common, line 124
	try { // library marker davegut.ST-Common, line 125
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 126
	} catch (e) { // library marker davegut.ST-Common, line 127
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 128
	} // library marker davegut.ST-Common, line 129
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 130
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 131
	return timeRemaining // library marker davegut.ST-Common, line 132
} // library marker davegut.ST-Common, line 133

// ~~~~~ end include (450) davegut.ST-Common ~~~~~
