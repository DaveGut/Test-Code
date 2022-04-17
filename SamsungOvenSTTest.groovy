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
===========================================================================================*/
import groovy.json.JsonSlurper
def driverVer() { return "0.05" }
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
		attribute "cooktopOperatingState", "string"
		//	Probe Attribute
		attribute "probeStatus", "string"
		attribute "probeTemperature", "string"
		attribute "probeSetPoint", "string"
		//	Main Oven Attributes
		attribute "setpoint", "string"
		attribute "temperature", "string"
		attribute "machineState", "string"
		attribute "progress", "string"
		attribute "ovenJobState", "string"
		attribute "operationTime", "string"
		attribute "ovenMode", "string"
		//	Cavity Attributes
		attribute "cavitySetpoint", "string"
		attribute "cavityTemperature", "string"
		attribute "cavityMachineState", "string"
		attribute "cavityProgress", "string"
		attribute "cavityOvenJobState", "string"
		attribute "cavityOperationTime", "string"
		attribute "cavityOvenMode", "string"
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

def refresh() {
	stRefresh()
}

def deviceStatusParse(resp, data) {
	def respData = validateResp(resp, "deviceStatusParse")
	if (respData == "error") { return }
	def logData = [:]
	def mainData = respData.components.main
	def cavityData
	try {
		cavityData = respData.components.cavity-01
	} catch (error) {}
	
	def testLog = [:]
/*
	try {
		testLog << [inputSource: mainData.mediaInputSource.inputSource.value]
	} catch (error) {}
	try {
		testLog << [mediaInputSource: mainData["samsungvd.mediaInputSource"].inputSource.value]
	} catch (error) {}
	try {
		testLog << [soundMode: mainData["custom.soundmode"].soundMode.value]
	} catch (error) {}
//	=============================================
*/

	try {
		def kidsLock = mainData["samsungce.kidsLock"].lockState.value
		logData << [kidsLock: setEvent("kidsLock", kidsLock)]
	} catch (error) {
		testLog << [kidsLock: mainData["samsungce.kidsLock"]]
	}

	try {
		def setpoint = mainData.ovenSetpoint.ovenSetpoint.value
		logData << [setpoint: setEvent("setpoint", setpoint)]
	} catch (error) {
		testLog << [setpoint: mainData.ovenSetpoint]
	}

	try {
		def temperature = mainData.temperatureMeasurement.temperature.value
		logData << [temperature: setEvent("temperature", temperature)]
	} catch (error) {
			testLog << [temperature: mainData.temperatureMeasurement]
	}
	
	try {
		def machineState = mainData.ovenOperatingState.machineState.value
		logData << [machineState: setEvent("machineState", machineState)]
		def progress = mainData.ovenOperatingState.progress.value
		logData << [progress: setEvent("progress", progress)]
		def ovenJobState = mainData.ovenOperatingState.ovenJobState.value
		logData << [ovenJobState: setEvent("ovenJobState", ovenJobState)]
	} catch (error) {
		testLog << [ovenOperatingState: mainData.ovenOperatingState]
	}
	
	try {
		def operationTime = mainData.ovenOperatingState.operationTime.value
		logData << [operationTime: setEvent("operationTime", operationTime)]
	} catch (error) {
		testLog << [operationTime: mainData.ovenOperatingState]
	}
	
	try {
		def ovenMode = mainData["samsungce.ovenMode"].ovenMode.value
		logData << [ovenMode: setEvent("ovenMode", ovenMode)]
	} catch (error) {
		testLog << [ovenMode: mainData["samsungce.ovenMode"]]
	}
	
	try {
		def probeStatus = mainData["samsungce.meatProbe"].status.value
		logData << [probeStatus: setEvent("probeStatus", probeStatus)]
		def probeTemperature = mainData["samsungce.meatProbe"].temperature.value
		logData << [probeTemperature: setEvent("probeTemperature", probeTemperature)]
		def probeSetPoint = mainData["samsungce.meatProbe"].temperatureSetpoint.value
		logData << [probeSetPoint: setEvent("probeSetPoint", probeSetPoint)]
	} catch (error) {
		testLog << [meatProbe: mainData["samsungce.meatProbe"]]
	}
	
	try {
		def doorState = mainData["samsungce.doorState"].doorState.value
		logData << [doorState: setEvent("doorState", doorState)]
	} catch (error) {
		testLog << [doorState: mainData["samsungce.doorState"]]
	}
	
	try {
		def cooktopOperatingState = mainData["custom.cooktopOperatingState"].cooktopOperatingState.value
		logData << [cooktopOperatingState: setEvent("cooktopOperatingState", cooktopOperatingState)]
	} catch (error) {
		testLog << [setpoint: mainData.ovenSetpoint]
	}
	
	if (cavityData != null) {
		try {
			def cavityStatus = cavityData["custom.ovenCavityStatus"].ovenCavityStatus.value
			logData << [cavityStatus: setEvent("cavityStatus", cavityStatus)]
		} catch (error) {
			testLog << [cavityStatus: cavityData["custom.ovenCavityStatus"]]
		}
			
		try {
			def cavitySetpoint = cavityData.ovenSetpoint.ovenSetpoint.value
			logData << [cavitySetpoint: setEvent("cavitySetpoint", cavitySetpoint)]
		} catch (error) {
			testLog << [cavitySetpoint: cavityData.ovenSetpoint]
		}

		try {
			def cavityTemperature = cavityData.temperatureMeasurement.temperature.value
			logData << [cavityTemperature: setEvent("cavityTemperature", cavityTemperature)]
		} catch (error) {
			testLog << [cavityTemperature: cavityData.temperatureMeasurement]
		}

		try {
			def cavityMachineState = cavityData.ovenOperatingState.machineState.value
			logData << [cavityMachineState: setEvent("cavityMachineState", cavityMachineState)]
			def cavityProgress = cavityData.ovenOperatingState.progress.value
			logData << [cavityProgress: setEvent("cavityProgress", cavityProgress)]
			def cavityOvenJobState = cavityData.ovenOperatingState.ovenJobState.value
			logData << [cavityOvenJobState: setEvent("cavityOvenJobState", cavityOvenJobState)]
		} catch (error) {
			testLog << [ovenOperatingState: cavityData.ovenOperatingState]
		}

		try {
			def cavityOperationTime = cavityData.ovenOperatingState.operationTime.value
			logData << [cavityOperationTime: setEvent("cavityOperationTime", cavityOperationTime)]
		} catch (error) {
			testLog << [cavityOperationTime: cavityData.ovenOperatingState]
		}

		try {
			def cavityOvenMode = cavityData["samsungce.ovenMode"].ovenMode.value
			logData << [cavityOvenMode: setEvent("cavityOvenMode", cavityOvenMode)]
		} catch (error) {
			testLog << [cavityOvenMode: cavityData["samsungce.ovenMode"]]
		}
	} else {
		testLog << [cavityData: "Null Cavity Data"]
	}

	if (logData != [:]) {
		logDebug("getDeviceStatus: ${logData}")
	}
//	Temp Test Code
	log.trace "testLog: ${testLog}"
	runIn(1, testCode)
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
def pauseMain() {
	logDebug("pauseMain")
	def sendData = [
		path: "/devices/${stDeviceId.trim()}/commands",
		comp: "main",
		cap: "ovenOperatingState",
		cmd: "setMachineState",
		params: ["pause"],
		parse: "pauseMain"
	]
	def respData = syncPost(sendData)
	if (respData == "error") { return }
	if (respData.status == "OK") {
		refresh()
	} else {
		logWarn("pauseMain: ${respData}")
	}
}

def pauseCavity() {
	if (device.currentValue("cavityStatus") == "on") {
		logDebug("pauseCavity")
		def sendData = [
			path: "/devices/${stDeviceId.trim()}/commands",
			comp: "cavity-01",
			cap: "ovenOperatingState",
			cmd: "setMachineState",
			params: ["pause"],
			parse: "pauseCavity"
		]
		def respData = syncPost(sendData)
		if (respData == "error") { return }
		if (respData.status == "OK") {
			refresh()
		} else {
			logWarn("pauseCavity: ${respData}")
		}
	} else {
		logDebug("pauseCavity: not executed. Cavity is off")
	}
}

def stopMain() {
	logDebug("stopMain")
	def sendData = [
		path: "/devices/${stDeviceId.trim()}/commands",
		comp: "main",
		cap: "ovenOperatingState",
		cmd: "stop",
		params: [],
		parse: "stopMain()"
	]
	def respData = syncPost(sendData)
	if (respData == "error") { return }
	if (respData.status == "OK") {
		refresh()
	} else {
		logWarn("stopMain: ${respData}")
	}
}

def stopCavity() {
	if (device.currentValue("cavityStatus") == "on") {
		logDebug("stopCavity")
		def sendData = [
			path: "/devices/${stDeviceId.trim()}/commands",
			comp: "cavity-01",
			cap: "ovenOperatingState",
			cmd: "stop",
			params: [],
			parse: "stopCavity"
		]
		def respData = syncPost(sendData)
		if (respData == "error") { return }
		if (respData.status == "OK") {
			refresh()
		} else {
			logWarn("stopCavity: ${respData}")
		}
	} else {
		logDebug("stopCavity: not executed. Cavity is off")
	}
}

//	===== Library Integration =====




// ~~~~~ start include (387) davegut.ST-Samsung-Comms ~~~~~
library ( // library marker davegut.ST-Samsung-Comms, line 1
	name: "ST-Samsung-Comms", // library marker davegut.ST-Samsung-Comms, line 2
	namespace: "davegut", // library marker davegut.ST-Samsung-Comms, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Samsung-Comms, line 4
	description: "ST Communications Methods", // library marker davegut.ST-Samsung-Comms, line 5
	category: "utilities", // library marker davegut.ST-Samsung-Comms, line 6
	documentationLink: "" // library marker davegut.ST-Samsung-Comms, line 7
) // library marker davegut.ST-Samsung-Comms, line 8

def validateResp(resp, method) { // library marker davegut.ST-Samsung-Comms, line 10
	if (resp.status != 200) { // library marker davegut.ST-Samsung-Comms, line 11

		def errorResp = [status: "error", // library marker davegut.ST-Samsung-Comms, line 13
						 httpCode: resp.status, // library marker davegut.ST-Samsung-Comms, line 14
						 errorMsg: resp.errorMessage] // library marker davegut.ST-Samsung-Comms, line 15
		logWarn("${method}: ${errorResp}") // library marker davegut.ST-Samsung-Comms, line 16
		return "error" // library marker davegut.ST-Samsung-Comms, line 17
	} else { // library marker davegut.ST-Samsung-Comms, line 18
		try { // library marker davegut.ST-Samsung-Comms, line 19
			return new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Samsung-Comms, line 20
		} catch (err) { // library marker davegut.ST-Samsung-Comms, line 21
			logWarn("${method}: [noDataError: ${err}]") // library marker davegut.ST-Samsung-Comms, line 22
		} // library marker davegut.ST-Samsung-Comms, line 23
	} // library marker davegut.ST-Samsung-Comms, line 24
} // library marker davegut.ST-Samsung-Comms, line 25

//	Asynchronous send commands // library marker davegut.ST-Samsung-Comms, line 27
private asyncGet(sendData) { // library marker davegut.ST-Samsung-Comms, line 28
//	sendData Spec: [path, parse] // library marker davegut.ST-Samsung-Comms, line 29
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Samsung-Comms, line 30
		logWarn("asyncGet: [status: ${error}, statusReason: no stApiKey]") // library marker davegut.ST-Samsung-Comms, line 31
		return // library marker davegut.ST-Samsung-Comms, line 32
	} // library marker davegut.ST-Samsung-Comms, line 33
//	logDebug("asyncGet: [apiKey: ${stApiKey}, sendData: ${sendData}]") // library marker davegut.ST-Samsung-Comms, line 34
	def sendCmdParams = [ // library marker davegut.ST-Samsung-Comms, line 35
		uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Samsung-Comms, line 36
		path: sendData.path, // library marker davegut.ST-Samsung-Comms, line 37
		headers: ['Authorization': 'Bearer ' + stApiKey.trim()]] // library marker davegut.ST-Samsung-Comms, line 38
	try { // library marker davegut.ST-Samsung-Comms, line 39
		asynchttpGet(sendData.parse, sendCmdParams) // library marker davegut.ST-Samsung-Comms, line 40
	} catch (error) { // library marker davegut.ST-Samsung-Comms, line 41
		logWarn("asyncGet: [status: error, statusReason: ${error}]") // library marker davegut.ST-Samsung-Comms, line 42
	} // library marker davegut.ST-Samsung-Comms, line 43
} // library marker davegut.ST-Samsung-Comms, line 44

private syncPost(sendData){ // library marker davegut.ST-Samsung-Comms, line 46
//	sendData Spec: [path, comp, cap, cmd, params, parse] // library marker davegut.ST-Samsung-Comms, line 47
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Samsung-Comms, line 48
		logWarn("asyncPost: [status: ${error}, statusReason: no stApiKey]") // library marker davegut.ST-Samsung-Comms, line 49
		return // library marker davegut.ST-Samsung-Comms, line 50
	} // library marker davegut.ST-Samsung-Comms, line 51
//	logDebug("syncPost: [apiKey: ${stApiKey}, sendData: ${sendData}]") // library marker davegut.ST-Samsung-Comms, line 52
	def cmdData = [ // library marker davegut.ST-Samsung-Comms, line 53
		component: sendData.comp, // library marker davegut.ST-Samsung-Comms, line 54
		capability: sendData.cap, // library marker davegut.ST-Samsung-Comms, line 55
		command: sendData.cmd, // library marker davegut.ST-Samsung-Comms, line 56
		arguments: sendData.params // library marker davegut.ST-Samsung-Comms, line 57
		] // library marker davegut.ST-Samsung-Comms, line 58
	def cmdBody = [commands: [cmdData]] // library marker davegut.ST-Samsung-Comms, line 59
	def sendCmdParams = [ // library marker davegut.ST-Samsung-Comms, line 60
		uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Samsung-Comms, line 61
		path: sendData.path, // library marker davegut.ST-Samsung-Comms, line 62
		headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Samsung-Comms, line 63
		body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Samsung-Comms, line 64
	] // library marker davegut.ST-Samsung-Comms, line 65
	def respData = "error" // library marker davegut.ST-Samsung-Comms, line 66
	try { // library marker davegut.ST-Samsung-Comms, line 67
		httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Samsung-Comms, line 68
			if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Samsung-Comms, line 69
				respData = [status: "OK", results: resp.data.results] // library marker davegut.ST-Samsung-Comms, line 70
			} else { // library marker davegut.ST-Samsung-Comms, line 71
				def errorResp = [status: "error", // library marker davegut.ST-Samsung-Comms, line 72
								 reqMethod: sendData.parse, // library marker davegut.ST-Samsung-Comms, line 73
								 httpCode: resp.status, // library marker davegut.ST-Samsung-Comms, line 74
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Samsung-Comms, line 75
				logDebug("syncPost error from ST: ${errorResp}") // library marker davegut.ST-Samsung-Comms, line 76
			} // library marker davegut.ST-Samsung-Comms, line 77
		} // library marker davegut.ST-Samsung-Comms, line 78
	} catch (error) { // library marker davegut.ST-Samsung-Comms, line 79
		def errorResp = [status: "error", // library marker davegut.ST-Samsung-Comms, line 80
						 reqMethod: sendData.parse, // library marker davegut.ST-Samsung-Comms, line 81
						 errorMsg: error] // library marker davegut.ST-Samsung-Comms, line 82
		logWarn("syncPost: ${errorResp}") // library marker davegut.ST-Samsung-Comms, line 83
	} // library marker davegut.ST-Samsung-Comms, line 84
	return respData // library marker davegut.ST-Samsung-Comms, line 85
} // library marker davegut.ST-Samsung-Comms, line 86

//	======================================================== // library marker davegut.ST-Samsung-Comms, line 88
//	===== Logging ========================================== // library marker davegut.ST-Samsung-Comms, line 89
//	======================================================== // library marker davegut.ST-Samsung-Comms, line 90
def logTrace(msg){ // library marker davegut.ST-Samsung-Comms, line 91
	log.trace "${device.label} V${driverVer()}: ${msg}" // library marker davegut.ST-Samsung-Comms, line 92
} // library marker davegut.ST-Samsung-Comms, line 93

def logInfo(msg) {  // library marker davegut.ST-Samsung-Comms, line 95
//	if (infoLog == true) { // library marker davegut.ST-Samsung-Comms, line 96
		log.info "${device.label} V${driverVer()}: ${msg}" // library marker davegut.ST-Samsung-Comms, line 97
//	} // library marker davegut.ST-Samsung-Comms, line 98
} // library marker davegut.ST-Samsung-Comms, line 99

def debugLogOff() { // library marker davegut.ST-Samsung-Comms, line 101
	device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.ST-Samsung-Comms, line 102
	logInfo("Debug logging is false.") // library marker davegut.ST-Samsung-Comms, line 103
} // library marker davegut.ST-Samsung-Comms, line 104

def logDebug(msg) { // library marker davegut.ST-Samsung-Comms, line 106
//	if (debugLog == true) { // library marker davegut.ST-Samsung-Comms, line 107
		log.debug "${device.label} V${driverVer()}: ${msg}" // library marker davegut.ST-Samsung-Comms, line 108
//	} // library marker davegut.ST-Samsung-Comms, line 109
} // library marker davegut.ST-Samsung-Comms, line 110

def logWarn(msg) { log.warn "${device.label} V${driverVer()}: ${msg}" } // library marker davegut.ST-Samsung-Comms, line 112

// ~~~~~ end include (387) davegut.ST-Samsung-Comms ~~~~~

// ~~~~~ start include (449) davegut.ST-Samsung-Refresh ~~~~~
/* // library marker davegut.ST-Samsung-Refresh, line 1
For use with devices that can't be refreshed when power is off // library marker davegut.ST-Samsung-Refresh, line 2
Example: Samsung Wifi Devices // library marker davegut.ST-Samsung-Refresh, line 3
	Checks / updates switch state // library marker davegut.ST-Samsung-Refresh, line 4
	if Switch is on, refreshes the stDevice states, updates states on Hubitat. // library marker davegut.ST-Samsung-Refresh, line 5
Parse method (deviceStatusParse) is in the device driver base code. // library marker davegut.ST-Samsung-Refresh, line 6
*/ // library marker davegut.ST-Samsung-Refresh, line 7
library ( // library marker davegut.ST-Samsung-Refresh, line 8
	name: "ST-Samsung-Refresh", // library marker davegut.ST-Samsung-Refresh, line 9
	namespace: "davegut", // library marker davegut.ST-Samsung-Refresh, line 10
	author: "Dave Gutheinz", // library marker davegut.ST-Samsung-Refresh, line 11
	description: "ST Communications Methods", // library marker davegut.ST-Samsung-Refresh, line 12
	category: "utilities", // library marker davegut.ST-Samsung-Refresh, line 13
	documentationLink: "" // library marker davegut.ST-Samsung-Refresh, line 14
) // library marker davegut.ST-Samsung-Refresh, line 15

def stRefresh() { // library marker davegut.ST-Samsung-Refresh, line 17
	def sendData = [ // library marker davegut.ST-Samsung-Refresh, line 18
		path: "/devices/${stDeviceId.trim()}/commands", // library marker davegut.ST-Samsung-Refresh, line 19
		comp: "main", // library marker davegut.ST-Samsung-Refresh, line 20
		cap: "refresh", // library marker davegut.ST-Samsung-Refresh, line 21
		cmd: "refresh", // library marker davegut.ST-Samsung-Refresh, line 22
		params: [], // library marker davegut.ST-Samsung-Refresh, line 23
		parse: "stRefresh" // library marker davegut.ST-Samsung-Refresh, line 24
	] // library marker davegut.ST-Samsung-Refresh, line 25
	def respData = syncPost(sendData) // library marker davegut.ST-Samsung-Refresh, line 26
	if (respData == "error") { return } // library marker davegut.ST-Samsung-Refresh, line 27
	if (respData.status == "OK" && respData.results[0].status == "ACCEPTED") { // library marker davegut.ST-Samsung-Refresh, line 28
		runIn(2, getDeviceStatus) // library marker davegut.ST-Samsung-Refresh, line 29
	} else { // library marker davegut.ST-Samsung-Refresh, line 30
		logWarn("stRefresh: ${respData}") // library marker davegut.ST-Samsung-Refresh, line 31
	} // library marker davegut.ST-Samsung-Refresh, line 32
} // library marker davegut.ST-Samsung-Refresh, line 33

def getDeviceStatus() { // library marker davegut.ST-Samsung-Refresh, line 35
	def sendData = [ // library marker davegut.ST-Samsung-Refresh, line 36
		path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Samsung-Refresh, line 37
		parse: "deviceStatusParse" // library marker davegut.ST-Samsung-Refresh, line 38
		] // library marker davegut.ST-Samsung-Refresh, line 39
	asyncGet(sendData) // library marker davegut.ST-Samsung-Refresh, line 40
} // library marker davegut.ST-Samsung-Refresh, line 41

// ~~~~~ end include (449) davegut.ST-Samsung-Refresh ~~~~~

// ~~~~~ start include (450) davegut.ST-Samsung-Common ~~~~~
library ( // library marker davegut.ST-Samsung-Common, line 1
	name: "ST-Samsung-Common", // library marker davegut.ST-Samsung-Common, line 2
	namespace: "davegut", // library marker davegut.ST-Samsung-Common, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Samsung-Common, line 4
	description: "ST Communications Methods", // library marker davegut.ST-Samsung-Common, line 5
	category: "utilities", // library marker davegut.ST-Samsung-Common, line 6
	documentationLink: "" // library marker davegut.ST-Samsung-Common, line 7
) // library marker davegut.ST-Samsung-Common, line 8

def commonUpdate() { // library marker davegut.ST-Samsung-Common, line 10
	unschedule() // library marker davegut.ST-Samsung-Common, line 11
	def updateData = [:] // library marker davegut.ST-Samsung-Common, line 12
	def status = "OK" // library marker davegut.ST-Samsung-Common, line 13
	def statusReason = "" // library marker davegut.ST-Samsung-Common, line 14
	def deviceData // library marker davegut.ST-Samsung-Common, line 15
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Samsung-Common, line 16
		status = "failed" // library marker davegut.ST-Samsung-Common, line 17
		statusReason = "No stApiKey" // library marker davegut.ST-Samsung-Common, line 18
	} else if (!stDeviceId || stDeviceId == "") { // library marker davegut.ST-Samsung-Common, line 19
		status = "failed" // library marker davegut.ST-Samsung-Common, line 20
		statusReason = "No stDeviceId" // library marker davegut.ST-Samsung-Common, line 21
	} else { // library marker davegut.ST-Samsung-Common, line 22
		if (debug) { runIn(1800, debugOff) } // library marker davegut.ST-Samsung-Common, line 23
	} // library marker davegut.ST-Samsung-Common, line 24
	updateData << [stApiKey: stApiKey, stDeviceId: stDeviceId] // library marker davegut.ST-Samsung-Common, line 25
	updateData << [debugLog: debugLog, infoLog: infoLog] // library marker davegut.ST-Samsung-Common, line 26
	def updateStatus = [:] // library marker davegut.ST-Samsung-Common, line 27
	updateStatus << [status: status] // library marker davegut.ST-Samsung-Common, line 28
	if (statusReason != "") { // library marker davegut.ST-Samsung-Common, line 29
		updateStatus << [statusReason: statusReason] // library marker davegut.ST-Samsung-Common, line 30
	} // library marker davegut.ST-Samsung-Common, line 31
	updateStatus << [updateData: updateData] // library marker davegut.ST-Samsung-Common, line 32
	runEvery5Minutes(refresh) // library marker davegut.ST-Samsung-Common, line 33
	refresh() // library marker davegut.ST-Samsung-Common, line 34
	return updateStatus // library marker davegut.ST-Samsung-Common, line 35
} // library marker davegut.ST-Samsung-Common, line 36

def setEvent(event, value) { // library marker davegut.ST-Samsung-Common, line 38
	def status = "noChange" // library marker davegut.ST-Samsung-Common, line 39
	if (device.currentValue(event) != value) { // library marker davegut.ST-Samsung-Common, line 40
		try { // library marker davegut.ST-Samsung-Common, line 41
			sendEvent(name: event, value: value) // library marker davegut.ST-Samsung-Common, line 42
			status = [event, value] // library marker davegut.ST-Samsung-Common, line 43
		} catch (error) { // library marker davegut.ST-Samsung-Common, line 44
			status = "sendEventError" // library marker davegut.ST-Samsung-Common, line 45
			logWarn("[${event}: ${value}: ${error}]") // library marker davegut.ST-Samsung-Common, line 46
		} // library marker davegut.ST-Samsung-Common, line 47
	} // library marker davegut.ST-Samsung-Common, line 48
	return status // library marker davegut.ST-Samsung-Common, line 49
} // library marker davegut.ST-Samsung-Common, line 50

def getDeviceList() { // library marker davegut.ST-Samsung-Common, line 52
	def sendData = [ // library marker davegut.ST-Samsung-Common, line 53
		path: "/devices", // library marker davegut.ST-Samsung-Common, line 54
		parse: "getDeviceListParse" // library marker davegut.ST-Samsung-Common, line 55
		] // library marker davegut.ST-Samsung-Common, line 56
	asyncGet(sendData) // library marker davegut.ST-Samsung-Common, line 57
} // library marker davegut.ST-Samsung-Common, line 58
def getDeviceListParse(resp, data) { // library marker davegut.ST-Samsung-Common, line 59
	def respData = validateResp(resp, "getDeviceListParse") // library marker davegut.ST-Samsung-Common, line 60
	if (respData == "error") { return } // library marker davegut.ST-Samsung-Common, line 61
	log.info "" // library marker davegut.ST-Samsung-Common, line 62
	respData.items.each { // library marker davegut.ST-Samsung-Common, line 63
		def deviceData = [ // library marker davegut.ST-Samsung-Common, line 64
			label: it.label, // library marker davegut.ST-Samsung-Common, line 65
			manufacturer: it.manufacturerName, // library marker davegut.ST-Samsung-Common, line 66
			presentationId: it.presentationId, // library marker davegut.ST-Samsung-Common, line 67
			deviceId: it.deviceId // library marker davegut.ST-Samsung-Common, line 68
		] // library marker davegut.ST-Samsung-Common, line 69
		log.info "Found: ${deviceData}" // library marker davegut.ST-Samsung-Common, line 70
		log.info "" // library marker davegut.ST-Samsung-Common, line 71
	} // library marker davegut.ST-Samsung-Common, line 72
} // library marker davegut.ST-Samsung-Common, line 73


// ~~~~~ end include (450) davegut.ST-Samsung-Common ~~~~~
