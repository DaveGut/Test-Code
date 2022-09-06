/*	Samsung Washer using SmartThings Interface
		Copyright Dave Gutheinz
License Information:
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Description
This driver is for SmartThings-installed Samsung Washers for import of control
and status of defined functions into Hubitat Environment.
=====	Library Use
This driver uses libraries for the functions common to SmartThings devices. 
Library code is at the bottom of the distributed single-file driver.
===== Installation Instructions Link =====
https://github.com/DaveGut/HubitatActive/blob/master/SamsungAppliances/Install_Samsung_Appliance.pdf
=====	Version B0.7
Beta Release of the driver set.  Added child for dryer flex compartment.
==============================================================================*/
def driverVer() { return "B0.7TEST" }
def namespace() { return "davegut" }
metadata {
	definition (name: "Samsung Washer",
				namespace: namespace(),
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungAppliances/Samsung_Washer.groovy"
			   ){
		capability "Refresh"
		attribute "switch", "string"
		command "start"
		command "pause"
		command "stop"
		attribute "machineState", "string"
		attribute "kidsLock", "string"
		attribute "remoteControlEnabled", "string"
		attribute "completionTime", "string"
		attribute "timeRemaining", "number"
		attribute "waterTemperature", "string"
		attribute "jobState", "string"
		attribute "soilLevel", "string"
		attribute "spinLevel", "string"
	}
	preferences {
		input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "")
		if (stApiKey) {
			input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "")
		}
		if (stDeviceId) {
			input ("pollInterval", "enum", title: "Poll Interval (minutes)",
				   options: ["1", "5", "10", "30"], defaultValue: "10")
			input ("debugLog", "bool",  
				   title: "Enable debug logging for 30 minutes", defaultValue: false)
			input("installChildren", "bool", title: "Install child devices",
				  defaultValue: false)
		}
	}
}

def installed() { }

def updated() {
	def commonStatus = commonUpdate()
	if (commonStatus.status == "FAILED") {
		logWarn("updated: ${commonStatus}")
	} else {
		logInfo("updated: ${commonStatus}")
	}
	if (installChildren) {
		deviceSetup()
		device.updateSetting("installChildren", [type:"bool", value: false])
	}
}

def start() { setMachineState("run") }
def pause() { setMachineState("pause") }
def stop() { setMachineState("stop") }
def setMachineState(machState) {
	def cmdData = [
		component: "main",
		capability: "dryerOperatingState",
		command: "setMachineState",
		arguments: [machState]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setMachineState: [cmd: ${machState}, status: ${cmdStatus}]")
}

def distResp(resp, data) {
	def respLog = [:]
	if (resp.status == 200) {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
			if (data.reason == "deviceSetup") {
				deviceSetupParse(respData)
			} else {
				def children = getChildDevices()
				children.each {
						it.statusParse(respData)
				}
				statusParse(respData)
			}
		} catch (err) {
			respLog << [status: "ERROR",
						errorMsg: err,
						respData: resp.data]
		}
	} else {
		respLog << [status: "ERROR",
					httpCode: resp.status,
					errorMsg: resp.errorMessage]
	}
	if (respLog != [:]) {
		logWarn("distResp: ${respLog}")
	}
}

def deviceSetupParse(respData) {
	def respLog = []
	def compData = respData.components
	def dni = device.getDeviceNetworkId()
	compData.each {
		if (it.key != "main") {
			def childDni = dni + "-${it.key}"
			def isChild = getChildDevice(childDni)
			if (!isChild) {
				def disabledComponents = []
				if (disabledComponents == null) {
					disabledComponents = compData.main["custom.disabledComponents"].disabledComponents.value
				}
				if(!disabledComponents.contains(it.key)) {
					respLog << [component: it.key, status: "Installing"]
					addChild(it.key, childDni)
				}
			}
		} else {
			updateDataValue("component", "main")
		}
	}
			
	if (respLog != []) {
		logInfo("deviceSetupParse: ${respLog}")
	}
}
def addChild(component, childDni) {
	def type = "Samsung Washer flex"
	try {
		addChildDevice(namespace(), "${type}", "${childDni}", [
			 "label": "${device.displayName} flex", component: component])
		logInfo("addChild: [status: ADDED, label: ${component}, type: ${type}]")
	} catch (error) {
		logWarn("addChild: [status: FAILED, type: ${type}, dni: ${childDni}, component: ${component}, error: ${error}]")
	}
}

def statusParse(respData) {
	def parseData
	try {
		parseData = respData.components.main
	} catch (error) {
		logWarn("statusParse: [parseData: ${respData}, error: ${error}]")
		return
	}

	def onOff = parseData.switch.switch.value
	sendEvent(name: "switch", value: onOff)
	if (device.currentValue("switch") != onOff) {
		if (onOff == "off") {
			setPollInterval(state.pollInterval)
		} else {
			runEvery1Minute(poll)
		}
	}
	
	if (parseData["samsungce.kidsLock"]) {
		def kidsLock = parseData["samsungce.kidsLock"].lockState.value
		sendEvent(name: "kidsLock", value: kidsLock)
	}
	
	def machineState = parseData.washerOperatingState.machineState.value
	sendEvent(name: "machineState", value: machineState)
	
	if (parseData["samsungce.kidsLock"]) {
		def kidsLock = parseData["samsungce.kidsLock"].lockState.value
		sendEvent(name: "kidsLock", value: kidsLock)
	}

	def jobState = parseData.washerOperatingState.washerJobState.value
	sendEvent(name: "jobState", value: jobState)
	
	def remoteControlEnabled = parseData.remoteControlStatus.remoteControlEnabled.value
	sendEvent(name: "remoteControlEnabled", value: remoteControlEnabled)
	
	def completionTime = parseData.washerOperatingState.completionTime.value
	if (completionTime != null) {
		def timeRemaining = calcTimeRemaining(completionTime)
		sendEvent(name: "completionTime", value: completionTime)
		sendEvent(name: "timeRemaining", value: timeRemaining)
	}

	def waterTemperature = parseData["custom.washerWaterTemperature"].washerWaterTemperature.value
	sendEvent(name: "waterTemperature", value: waterTemperature)

	def soilLevel = parseData["custom.washerSoilLevel"].washerSoilLevel.value
	sendEvent(name: "soilLevel", value: soilLevel)

	def spinLevel = parseData["custom.washerSpinLevel"].washerSpinLevel.value
	sendEvent(name: "spinLevel", value: spinLevel)
	
	runIn(1, listAttributes, [data: true])
//	runIn(1, listAttributes)
}

//	===== Library Integration =====



def simulate() { return true }


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
		logTrace("Attributes: ${attrList}") // library marker davegut.Logging, line 19
	} else { // library marker davegut.Logging, line 20
		logDebug("Attributes: ${attrList}") // library marker davegut.Logging, line 21
	} // library marker davegut.Logging, line 22
} // library marker davegut.Logging, line 23

def logTrace(msg){ // library marker davegut.Logging, line 25
	log.trace "${device.displayName} ${driverVer()}: ${msg}" // library marker davegut.Logging, line 26
} // library marker davegut.Logging, line 27

def logInfo(msg) {  // library marker davegut.Logging, line 29
	log.info "${device.displayName} ${driverVer()}: ${msg}" // library marker davegut.Logging, line 30
} // library marker davegut.Logging, line 31

def debugLogOff() { // library marker davegut.Logging, line 33
	if (debug == true) { // library marker davegut.Logging, line 34
		device.updateSetting("debug", [type:"bool", value: false]) // library marker davegut.Logging, line 35
	} else if (debugLog == true) { // library marker davegut.Logging, line 36
		device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.Logging, line 37
	} // library marker davegut.Logging, line 38
	logInfo("Debug logging is false.") // library marker davegut.Logging, line 39
} // library marker davegut.Logging, line 40

def logDebug(msg) { // library marker davegut.Logging, line 42
	if (debug == true || debugLog == true) { // library marker davegut.Logging, line 43
		log.debug "${device.displayName} ${driverVer()}: ${msg}" // library marker davegut.Logging, line 44
	} // library marker davegut.Logging, line 45
} // library marker davegut.Logging, line 46

def logWarn(msg) { log.warn "${device.displayName} ${driverVer()}: ${msg}" } // library marker davegut.Logging, line 48

// ~~~~~ end include (1072) davegut.Logging ~~~~~

// ~~~~~ start include (1091) davegut.ST-Communications ~~~~~
library ( // library marker davegut.ST-Communications, line 1
	name: "ST-Communications", // library marker davegut.ST-Communications, line 2
	namespace: "davegut", // library marker davegut.ST-Communications, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Communications, line 4
	description: "ST Communications Methods", // library marker davegut.ST-Communications, line 5
	category: "utilities", // library marker davegut.ST-Communications, line 6
	documentationLink: "" // library marker davegut.ST-Communications, line 7
) // library marker davegut.ST-Communications, line 8
import groovy.json.JsonSlurper // library marker davegut.ST-Communications, line 9

private asyncGet(sendData, passData = "none") { // library marker davegut.ST-Communications, line 11
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 12
		logWarn("asyncGet: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 13
	} else { // library marker davegut.ST-Communications, line 14
		logDebug("asyncGet: ${sendData}, ${passData}") // library marker davegut.ST-Communications, line 15
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 16
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 17
			path: sendData.path, // library marker davegut.ST-Communications, line 18
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()]] // library marker davegut.ST-Communications, line 19
		try { // library marker davegut.ST-Communications, line 20
			asynchttpGet(sendData.parse, sendCmdParams, [reason: passData]) // library marker davegut.ST-Communications, line 21
		} catch (error) { // library marker davegut.ST-Communications, line 22
			logWarn("asyncGet: [status: FAILED, errorMsg: ${error}]") // library marker davegut.ST-Communications, line 23
		} // library marker davegut.ST-Communications, line 24
	} // library marker davegut.ST-Communications, line 25
} // library marker davegut.ST-Communications, line 26

private syncGet(path){ // library marker davegut.ST-Communications, line 28
	def respData = [:] // library marker davegut.ST-Communications, line 29
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 30
		respData << [status: "FAILED", // library marker davegut.ST-Communications, line 31
					 errorMsg: "No stApiKey"] // library marker davegut.ST-Communications, line 32
	} else { // library marker davegut.ST-Communications, line 33
		logDebug("syncGet: ${sendData}") // library marker davegut.ST-Communications, line 34
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 35
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 36
			path: path, // library marker davegut.ST-Communications, line 37
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()] // library marker davegut.ST-Communications, line 38
		] // library marker davegut.ST-Communications, line 39
		try { // library marker davegut.ST-Communications, line 40
			httpGet(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 41
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 42
					respData << [status: "OK", results: resp.data] // library marker davegut.ST-Communications, line 43
				} else { // library marker davegut.ST-Communications, line 44
					respData << [status: "FAILED", // library marker davegut.ST-Communications, line 45
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 46
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 47
				} // library marker davegut.ST-Communications, line 48
			} // library marker davegut.ST-Communications, line 49
		} catch (error) { // library marker davegut.ST-Communications, line 50
			respData << [status: "FAILED", // library marker davegut.ST-Communications, line 51
						 httpCode: "Timeout", // library marker davegut.ST-Communications, line 52
						 errorMsg: error] // library marker davegut.ST-Communications, line 53
		} // library marker davegut.ST-Communications, line 54
	} // library marker davegut.ST-Communications, line 55
	return respData // library marker davegut.ST-Communications, line 56
} // library marker davegut.ST-Communications, line 57

private syncPost(sendData){ // library marker davegut.ST-Communications, line 59
	def respData = [:] // library marker davegut.ST-Communications, line 60
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 61
		respData << [status: "FAILED", // library marker davegut.ST-Communications, line 62
					 errorMsg: "No stApiKey"] // library marker davegut.ST-Communications, line 63
	} else { // library marker davegut.ST-Communications, line 64
		logDebug("syncPost: ${sendData}") // library marker davegut.ST-Communications, line 65

		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 67
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 68
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 69
			path: sendData.path, // library marker davegut.ST-Communications, line 70
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 71
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 72
		] // library marker davegut.ST-Communications, line 73
		try { // library marker davegut.ST-Communications, line 74
			httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 75
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 76
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 77
				} else { // library marker davegut.ST-Communications, line 78
					respData << [status: "FAILED", // library marker davegut.ST-Communications, line 79
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 80
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 81
				} // library marker davegut.ST-Communications, line 82
			} // library marker davegut.ST-Communications, line 83
		} catch (error) { // library marker davegut.ST-Communications, line 84
			respData << [status: "FAILED", // library marker davegut.ST-Communications, line 85
						 httpCode: "Timeout", // library marker davegut.ST-Communications, line 86
						 errorMsg: error] // library marker davegut.ST-Communications, line 87
		} // library marker davegut.ST-Communications, line 88
	} // library marker davegut.ST-Communications, line 89
	return respData // library marker davegut.ST-Communications, line 90
} // library marker davegut.ST-Communications, line 91

// ~~~~~ end include (1091) davegut.ST-Communications ~~~~~

// ~~~~~ start include (1090) davegut.ST-Common ~~~~~
library ( // library marker davegut.ST-Common, line 1
	name: "ST-Common", // library marker davegut.ST-Common, line 2
	namespace: "davegut", // library marker davegut.ST-Common, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Common, line 4
	description: "ST Wash/Dryer Common Methods", // library marker davegut.ST-Common, line 5
	category: "utilities", // library marker davegut.ST-Common, line 6
	documentationLink: "" // library marker davegut.ST-Common, line 7
) // library marker davegut.ST-Common, line 8

def commonUpdate() { // library marker davegut.ST-Common, line 10
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Common, line 11
		return [status: "FAILED", reason: "No stApiKey"] // library marker davegut.ST-Common, line 12
	} // library marker davegut.ST-Common, line 13
	if (!stDeviceId || stDeviceId == "") { // library marker davegut.ST-Common, line 14
		getDeviceList() // library marker davegut.ST-Common, line 15
		return [status: "FAILED", reason: "No stDeviceId"] // library marker davegut.ST-Common, line 16
	} // library marker davegut.ST-Common, line 17

	unschedule() // library marker davegut.ST-Common, line 19
	def updateData = [:] // library marker davegut.ST-Common, line 20
	updateData << [status: "OK"] // library marker davegut.ST-Common, line 21
	if (debugLog) { runIn(1800, debugLogOff) } // library marker davegut.ST-Common, line 22
	updateData << [stDeviceId: stDeviceId] // library marker davegut.ST-Common, line 23
	updateData << [debugLog: debugLog, infoLog: infoLog] // library marker davegut.ST-Common, line 24
	if (!getDataValue("driverVersion") ||  // library marker davegut.ST-Common, line 25
		getDataValue("driverVersion") != driverVer()) { // library marker davegut.ST-Common, line 26
		updateDataValue("driverVersion", driverVer()) // library marker davegut.ST-Common, line 27
		updateData << [driverVer: driverVer()] // library marker davegut.ST-Common, line 28
	} // library marker davegut.ST-Common, line 29
	setPollInterval(pollInterval) // library marker davegut.ST-Common, line 30
	updateData << [pollInterval: pollInterval] // library marker davegut.ST-Common, line 31

	runIn(5, refresh) // library marker davegut.ST-Common, line 33
	return updateData // library marker davegut.ST-Common, line 34
} // library marker davegut.ST-Common, line 35

def setPollInterval(pollInterval) { // library marker davegut.ST-Common, line 37
	logDebug("setPollInterval: ${pollInterval}") // library marker davegut.ST-Common, line 38
	state.pollInterval = pollInterval // library marker davegut.ST-Common, line 39
	switch(pollInterval) { // library marker davegut.ST-Common, line 40
		case "1" : runEvery1Minute(poll); break // library marker davegut.ST-Common, line 41
		case "5" : runEvery5Minutes(poll); break // library marker davegut.ST-Common, line 42
		case "10" : runEvery10Minutes(poll); break // library marker davegut.ST-Common, line 43
		case "30" : runEvery30Minutes(poll); break // library marker davegut.ST-Common, line 44
		default: runEvery10Minutes(poll) // library marker davegut.ST-Common, line 45
	} // library marker davegut.ST-Common, line 46
} // library marker davegut.ST-Common, line 47

def deviceCommand(cmdData) { // library marker davegut.ST-Common, line 49
	def respData = [:] // library marker davegut.ST-Common, line 50
	if (simulate() == true) { // library marker davegut.ST-Common, line 51
		respData = testResp(cmdData) // library marker davegut.ST-Common, line 52
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 53
		respData << [status: "FAILED", data: "no stDeviceId"] // library marker davegut.ST-Common, line 54
	} else { // library marker davegut.ST-Common, line 55
		def sendData = [ // library marker davegut.ST-Common, line 56
			path: "/devices/${stDeviceId.trim()}/commands", // library marker davegut.ST-Common, line 57
			cmdData: cmdData // library marker davegut.ST-Common, line 58
		] // library marker davegut.ST-Common, line 59
		respData = syncPost(sendData) // library marker davegut.ST-Common, line 60
	} // library marker davegut.ST-Common, line 61
	if (cmdData.capability && cmdData.capability != "refresh") { // library marker davegut.ST-Common, line 62
		refresh() // library marker davegut.ST-Common, line 63
	} else { // library marker davegut.ST-Common, line 64
		poll() // library marker davegut.ST-Common, line 65
	} // library marker davegut.ST-Common, line 66
	return respData // library marker davegut.ST-Common, line 67
} // library marker davegut.ST-Common, line 68

def refresh() { // library marker davegut.ST-Common, line 70
	if (stApiKey!= null) { // library marker davegut.ST-Common, line 71
		def cmdData = [ // library marker davegut.ST-Common, line 72
			component: "main", // library marker davegut.ST-Common, line 73
			capability: "refresh", // library marker davegut.ST-Common, line 74
			command: "refresh", // library marker davegut.ST-Common, line 75
			arguments: []] // library marker davegut.ST-Common, line 76
		deviceCommand(cmdData) // library marker davegut.ST-Common, line 77
	} // library marker davegut.ST-Common, line 78
} // library marker davegut.ST-Common, line 79

def poll() { // library marker davegut.ST-Common, line 81
	if (simulate() == true) { // library marker davegut.ST-Common, line 82
		def children = getChildDevices() // library marker davegut.ST-Common, line 83
		if (children) { // library marker davegut.ST-Common, line 84
			children.each { // library marker davegut.ST-Common, line 85
				it.statusParse(testData()) // library marker davegut.ST-Common, line 86
			} // library marker davegut.ST-Common, line 87
		} // library marker davegut.ST-Common, line 88
		statusParse(testData()) // library marker davegut.ST-Common, line 89
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 90
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 91
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 92
	} else { // library marker davegut.ST-Common, line 93
		def sendData = [ // library marker davegut.ST-Common, line 94
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 95
			parse: "distResp" // library marker davegut.ST-Common, line 96
			] // library marker davegut.ST-Common, line 97
		asyncGet(sendData, "statusParse") // library marker davegut.ST-Common, line 98
	} // library marker davegut.ST-Common, line 99
} // library marker davegut.ST-Common, line 100

def deviceSetup() { // library marker davegut.ST-Common, line 102
	if (simulate() == true) { // library marker davegut.ST-Common, line 103
		def children = getChildDevices() // library marker davegut.ST-Common, line 104
		deviceSetupParse(testData()) // library marker davegut.ST-Common, line 105
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 106
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 107
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 108
	} else { // library marker davegut.ST-Common, line 109
		def sendData = [ // library marker davegut.ST-Common, line 110
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 111
			parse: "distResp" // library marker davegut.ST-Common, line 112
			] // library marker davegut.ST-Common, line 113
		asyncGet(sendData, "deviceSetup") // library marker davegut.ST-Common, line 114
	} // library marker davegut.ST-Common, line 115
} // library marker davegut.ST-Common, line 116

def getDeviceList() { // library marker davegut.ST-Common, line 118
	def sendData = [ // library marker davegut.ST-Common, line 119
		path: "/devices", // library marker davegut.ST-Common, line 120
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 121
		] // library marker davegut.ST-Common, line 122
	asyncGet(sendData) // library marker davegut.ST-Common, line 123
} // library marker davegut.ST-Common, line 124

def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 126
	def respData // library marker davegut.ST-Common, line 127
	if (resp.status != 200) { // library marker davegut.ST-Common, line 128
		respData = [status: "ERROR", // library marker davegut.ST-Common, line 129
					httpCode: resp.status, // library marker davegut.ST-Common, line 130
					errorMsg: resp.errorMessage] // library marker davegut.ST-Common, line 131
	} else { // library marker davegut.ST-Common, line 132
		try { // library marker davegut.ST-Common, line 133
			respData = new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Common, line 134
		} catch (err) { // library marker davegut.ST-Common, line 135
			respData = [status: "ERROR", // library marker davegut.ST-Common, line 136
						errorMsg: err, // library marker davegut.ST-Common, line 137
						respData: resp.data] // library marker davegut.ST-Common, line 138
		} // library marker davegut.ST-Common, line 139
	} // library marker davegut.ST-Common, line 140
	if (respData.status == "ERROR") { // library marker davegut.ST-Common, line 141
		logWarn("getDeviceListParse: ${respData}") // library marker davegut.ST-Common, line 142
	} else { // library marker davegut.ST-Common, line 143
		log.info "" // library marker davegut.ST-Common, line 144
		respData.items.each { // library marker davegut.ST-Common, line 145
			log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 146
		} // library marker davegut.ST-Common, line 147
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 148
	} // library marker davegut.ST-Common, line 149
} // library marker davegut.ST-Common, line 150

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 152
	Integer currTime = now() // library marker davegut.ST-Common, line 153
	Integer compTime // library marker davegut.ST-Common, line 154
	try { // library marker davegut.ST-Common, line 155
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 156
	} catch (e) { // library marker davegut.ST-Common, line 157
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 158
	} // library marker davegut.ST-Common, line 159
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 160
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 161
	return timeRemaining // library marker davegut.ST-Common, line 162
} // library marker davegut.ST-Common, line 163

// ~~~~~ end include (1090) davegut.ST-Common ~~~~~

// ~~~~~ start include (1105) davegut.Samsung-Washer-Sim ~~~~~
library ( // library marker davegut.Samsung-Washer-Sim, line 1
	name: "Samsung-Washer-Sim", // library marker davegut.Samsung-Washer-Sim, line 2
	namespace: "davegut", // library marker davegut.Samsung-Washer-Sim, line 3
	author: "Dave Gutheinz", // library marker davegut.Samsung-Washer-Sim, line 4
	description: "Simulator - Samsung Washer", // library marker davegut.Samsung-Washer-Sim, line 5
	category: "utilities", // library marker davegut.Samsung-Washer-Sim, line 6
	documentationLink: "" // library marker davegut.Samsung-Washer-Sim, line 7
) // library marker davegut.Samsung-Washer-Sim, line 8

def testData() { // library marker davegut.Samsung-Washer-Sim, line 10
	def flex = true // library marker davegut.Samsung-Washer-Sim, line 11
	def altTest = true // library marker davegut.Samsung-Washer-Sim, line 12

	def waterTemp = "hot" // library marker davegut.Samsung-Washer-Sim, line 14
	def completionTime = "2022-09-06T20:00:00Z" // library marker davegut.Samsung-Washer-Sim, line 15
	def machineState = "running" // library marker davegut.Samsung-Washer-Sim, line 16
	def jobState = "spin" // library marker davegut.Samsung-Washer-Sim, line 17
	def onOff = "off" // library marker davegut.Samsung-Washer-Sim, line 18
	def kidsLock = "locked" // library marker davegut.Samsung-Washer-Sim, line 19
	def soilLevel = "low" // library marker davegut.Samsung-Washer-Sim, line 20
	def remoteControlEnabled = "false" // library marker davegut.Samsung-Washer-Sim, line 21
	def spinLevel = "high" // library marker davegut.Samsung-Washer-Sim, line 22
	if (altTest) { // library marker davegut.Samsung-Washer-Sim, line 23
		waterTemp = "cold" // library marker davegut.Samsung-Washer-Sim, line 24
		completionTime = "2022-09-06T20:00:30Z" // library marker davegut.Samsung-Washer-Sim, line 25
		machineState = "stopped" // library marker davegut.Samsung-Washer-Sim, line 26
		jobState = "stop" // library marker davegut.Samsung-Washer-Sim, line 27
		onOff = "on" // library marker davegut.Samsung-Washer-Sim, line 28
		kidsLock = "unlocked" // library marker davegut.Samsung-Washer-Sim, line 29
		soilLevel = "high" // library marker davegut.Samsung-Washer-Sim, line 30
		remoteControlEnabled = "true" // library marker davegut.Samsung-Washer-Sim, line 31
		spinLevel = "slow" // library marker davegut.Samsung-Washer-Sim, line 32
	} // library marker davegut.Samsung-Washer-Sim, line 33

	if (flex) { // library marker davegut.Samsung-Washer-Sim, line 35
		return  [components:[ // library marker davegut.Samsung-Washer-Sim, line 36
			sub:[ // library marker davegut.Samsung-Washer-Sim, line 37
				"custom.washerWaterTemperature":[washerWaterTemperature:[value:waterTemp]],  // library marker davegut.Samsung-Washer-Sim, line 38
				washerOperatingState:[ // library marker davegut.Samsung-Washer-Sim, line 39
					completionTime:[value:completionTime],  // library marker davegut.Samsung-Washer-Sim, line 40
					machineState:[value:machineState],  // library marker davegut.Samsung-Washer-Sim, line 41
					washerJobState:[value:jobState]],  // library marker davegut.Samsung-Washer-Sim, line 42
				switch:[switch:[value:onOff]],  // library marker davegut.Samsung-Washer-Sim, line 43
				"samsungce.kidsLock":[lockState:[value:kidsLock]],  // library marker davegut.Samsung-Washer-Sim, line 44
				remoteControlStatus:[remoteControlEnabled:[value:remoteControlEnabled]] // library marker davegut.Samsung-Washer-Sim, line 45
			], // library marker davegut.Samsung-Washer-Sim, line 46
			main:[ // library marker davegut.Samsung-Washer-Sim, line 47
				"custom.washerWaterTemperature":[washerWaterTemperature:[value:waterTemp]],  // library marker davegut.Samsung-Washer-Sim, line 48
				washerOperatingState:[ // library marker davegut.Samsung-Washer-Sim, line 49
					completionTime:[value:completionTime],  // library marker davegut.Samsung-Washer-Sim, line 50
					machineState:[value:machineState],  // library marker davegut.Samsung-Washer-Sim, line 51
					washerJobState:[value:jobState]],  // library marker davegut.Samsung-Washer-Sim, line 52
				switch:[switch:[value:onOff]],  // library marker davegut.Samsung-Washer-Sim, line 53
				"samsungce.kidsLock":[lockState:[value:kidsLock]],  // library marker davegut.Samsung-Washer-Sim, line 54
				"custom.washerSoilLevel":[washerSoilLevel:[value:soilLevel]],  // library marker davegut.Samsung-Washer-Sim, line 55
				remoteControlStatus:[remoteControlEnabled:[value:remoteControlEnabled]],  // library marker davegut.Samsung-Washer-Sim, line 56
				"custom.washerSpinLevel":[washerSpinLevel:[value:spinLevel]] // library marker davegut.Samsung-Washer-Sim, line 57
			]]] // library marker davegut.Samsung-Washer-Sim, line 58
	} else { // library marker davegut.Samsung-Washer-Sim, line 59
		return  [components:[ // library marker davegut.Samsung-Washer-Sim, line 60
			main:[ // library marker davegut.Samsung-Washer-Sim, line 61
				"custom.washerWaterTemperature":[washerWaterTemperature:[value:waterTemp]],  // library marker davegut.Samsung-Washer-Sim, line 62
				washerOperatingState:[ // library marker davegut.Samsung-Washer-Sim, line 63
					completionTime:[value:completionTime],  // library marker davegut.Samsung-Washer-Sim, line 64
					machineState:[value:machineState],  // library marker davegut.Samsung-Washer-Sim, line 65
					washerJobState:[value:jobState]],  // library marker davegut.Samsung-Washer-Sim, line 66
				switch:[switch:[value:onOff]],  // library marker davegut.Samsung-Washer-Sim, line 67
				"samsungce.kidsLock":[lockState:[value:kidsLock]],  // library marker davegut.Samsung-Washer-Sim, line 68
				"custom.washerSoilLevel":[washerSoilLevel:[value:soilLevel]],  // library marker davegut.Samsung-Washer-Sim, line 69
				remoteControlStatus:[remoteControlEnabled:[value:remoteControlEnabled]],  // library marker davegut.Samsung-Washer-Sim, line 70
				"custom.washerSpinLevel":[washerSpinLevel:[value:spinLevel]] // library marker davegut.Samsung-Washer-Sim, line 71
			]]] // library marker davegut.Samsung-Washer-Sim, line 72
	} // library marker davegut.Samsung-Washer-Sim, line 73
} // library marker davegut.Samsung-Washer-Sim, line 74

def testResp(cmdData) { // library marker davegut.Samsung-Washer-Sim, line 76
	return [ // library marker davegut.Samsung-Washer-Sim, line 77
		cmdData: cmdData, // library marker davegut.Samsung-Washer-Sim, line 78
		status: [status: "OK", // library marker davegut.Samsung-Washer-Sim, line 79
				 results:[[id: "e9585885-3848-4fea-b0db-ece30ff1701e", status: "ACCEPTED"]]]] // library marker davegut.Samsung-Washer-Sim, line 80
} // library marker davegut.Samsung-Washer-Sim, line 81

// ~~~~~ end include (1105) davegut.Samsung-Washer-Sim ~~~~~
