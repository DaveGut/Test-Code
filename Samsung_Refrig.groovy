/*	Samsung Refrigerator using SmartThings Interface
		Copyright Dave Gutheinz
License Information:
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Description
This driver is for SmartThings-installed Samsung Refrigerators for import of control
and status of defined functions into Hubitat Environment.
=====	Library Use
This driver uses libraries for the functions common to SmartThings devices. 
Library code is at the bottom of the distributed single-file driver.
===== Installation Instructions Link =====
https://github.com/DaveGut/HubitatActive/blob/master/SamsungAppliances/Install_Samsung_Appliance.pdf
=====	Version B0.5
Updated to differentiate between a standard wifi and a DONGLE-Based Wifi
DONGLE based system has very limted functionality within the components.
==============================================================================*/
def driverVer() { return "B0.5" }

metadata {
	definition (name: "Samsung Refrig",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungAppliances/Samsung_Refrig.groovy"
			   ){
		capability "Refresh"
		capability "Contact Sensor"
		capability "Temperature Measurement"
		capability "Thermostat Cooling Setpoint"
		capability "Filter Status"
		command "setRapidCooling", [[
			name: "Rapid Cooling",
			constraints: ["on", "off"],
			type: "ENUM"]]
		attribute "rapidCooling", "string"
		command "setRapidFreezing", [[
			name: "Rapid Freezing",
			constraints: ["on", "off"],
			type: "ENUM"]]
		attribute "rapidFreezing", "string"
		command "defrost", [[
			name: "Defrost",
			constraints: ["on", "off"],
			type: "ENUM"]]
		attribute "defrost", "string"
	}
	
	preferences {
		input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "")
		if (stApiKey) {
			input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "")
		}
		if (stDeviceId) {
			input ("pollInterval", "enum", title: "Poll Interval (minutes)",
				   options: ["1", "5", "10", "30"], defaultValue: "5")
			input ("debugLog", "bool",  
				   title: "Enable debug logging for 30 minutes", defaultValue: false)
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
		deviceSetup()
	}
}

def setCoolingSetpoint(setpoint) {
	def cmdData = [
		component: getDataValue("component"),
		capability: "thermostatCoolingSetpoint",
		command: "setCoolingSetpoint",
		arguments: [setpoint]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setCoolingSetpoint: [cmd: ${setpoint}, ${cmdStatus}]")
}

def setRapidCooling(onOff) {
	setRefrigeration("setRapidCooling", onOff)
}
def setRapidFreezing(onOff) {
	setRefrigeration("setRapidFreezing", onOff)
}
def defrost(onOff) {
	setRefrigeration("setDefrost", onOff)
}
def setRefrigeration(command, onOff) {
	def cmdData = [
		component: getDataValue("component"),
		capability: "refrigeration",
		command: command,
		arguments: [onOff]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setRefrigeration: [cmd ${command}, onOff: ${onOff}, status: ${cmdStatus}]")
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
	if (!getDataValue("dongle")) {
		def dongle = "false"
		def mnmo = respData.components.main.ocf.mnmo
		if (mnmo != null && mnmo.value.contains("DONGLE")) {
			dongle = "true"
		}
		updateDataValue("dongle", dongle)
		respLog << [dongle: dongle]
	}
	//	Install Children
	def compData = respData.components
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
	def type
	switch(component) {
		case "freezer":
		case "cooler":
		case "onedoor":
			type = "Samsung Refrig cavity"
			break
		case "icemaker":
		case "icemaker-02":
			type = "Samsung Refrig icemaker"
			break
		case "cvroom":
			type = "Samsung Refrig cvroom"
			break
		default:
			logWarn("addChild: [component: ${component}, error: not on components list.")
	}
	try {
		addChildDevice("davegut", "${type}", "${childDni}", [
			"name": type, "label": component, component: component])
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
		logWarn("statusParse: [respData: ${respData}, error: ${error}]")
		return
	}
	def logData = [:]
	def contact = parseData.contactSensor.contact.value
	if (device.currentValue("contact") != contact) {
		sendEvent(name: "contact", value: contact)
		logData << [contact: contact]
	}

	def tempUnit = parseData.thermostatCoolingSetpoint.coolingSetpoint.unit
	def coolingSetpoint = parseData.thermostatCoolingSetpoint.coolingSetpoint.value
	if (device.currentValue("coolingSetpoint") != coolingSetpoint) {
		sendEvent(name: "coolingSetpoint", value: coolingSetpoint, unit: tempUnit)
		logData << [coolingSetpoint: coolingSetpoint, unit: tempUnit]
	}

	if (getDataValue("dongle") == "false") {
		def temperature = parseData.temperatureMeasurement.temperature.value
		if (device.currentValue("temperature") != temperature) {
			sendEvent(name: "temperature", value: temperature, unit: tempUnit)
			logData << [temperature: temperature]
		}
	}
	
	def defrost = parseData.refrigeration.defrost.value
	if (device.currentValue("defrost") != defrost) {
		sendEvent(name: "defrost", value: defrost)
		logData << [defrost: defrost]
	}

	def rapidCooling = parseData.refrigeration.rapidCooling.value
	if (device.currentValue("rapidCooling") != rapidCooling) {
		sendEvent(name: "rapidCooling", value: rapidCooling)
		logData << [rapidCooling: rapidCooling]
	}

	def rapidFreezing = parseData.refrigeration.rapidFreezing.value
	if (device.currentValue("rapidFreezing") != rapidFreezing) {
		sendEvent(name: "rapidFreezing", value: rapidFreezing)
		logData << [rapidFreezing: rapidFreezing]
	}

	def filterStatus = parseData["custom.waterFilter"].waterFilterStatus.value
	if (device.currentValue("filterStatus") != filterStatus) {
		sendEvent(name: "filterStatus", value: filterStatus)
		logData << [filterStatus: filterStatus]
	}

	if (logData != [:]) {
		logInfo("getDeviceStatus: ${logData}")
	}

	runIn(1, listAttributes, [data: true])
//	runIn(1, listAttributes)
}

//	===== Library Integration =====



def simulate() { return false }
//#include davegut.Samsung-Refrig-Sim
//#include davegut.Samsung-Refrig-Sim-DONGLE

// ~~~~~ start include (993) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8

def listAttributes(trace = false) { // library marker davegut.Logging, line 10
	def attrs = device.getSupportedAttributes() // library marker davegut.Logging, line 11
	def attrList = [:] // library marker davegut.Logging, line 12
	attrs.each { // library marker davegut.Logging, line 13
		def val = device.currentValue("${it}") // library marker davegut.Logging, line 14
		attrList << ["${it}": val] // library marker davegut.Logging, line 15
	} // library marker davegut.Logging, line 16
	if (trace == true) { // library marker davegut.Logging, line 17
		logTrace("Attributes: ${attrList}") // library marker davegut.Logging, line 18
	} else { // library marker davegut.Logging, line 19
		logDebug("Attributes: ${attrList}") // library marker davegut.Logging, line 20
	} // library marker davegut.Logging, line 21
} // library marker davegut.Logging, line 22

def logTrace(msg){ // library marker davegut.Logging, line 24
	log.trace "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" // library marker davegut.Logging, line 25
} // library marker davegut.Logging, line 26

def logInfo(msg) {  // library marker davegut.Logging, line 28
	log.info "${device.displayName} ${getDataValue("driverVersion")}: ${msg}"  // library marker davegut.Logging, line 29
} // library marker davegut.Logging, line 30

def debugLogOff() { // library marker davegut.Logging, line 32
	if (debug == true) { // library marker davegut.Logging, line 33
		device.updateSetting("debug", [type:"bool", value: false]) // library marker davegut.Logging, line 34
	} else if (debugLog == true) { // library marker davegut.Logging, line 35
		device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.Logging, line 36
	} // library marker davegut.Logging, line 37
	logInfo("Debug logging is false.") // library marker davegut.Logging, line 38
} // library marker davegut.Logging, line 39

def logDebug(msg) { // library marker davegut.Logging, line 41
	if (debug == true || debugLog == true) { // library marker davegut.Logging, line 42
		log.debug "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" // library marker davegut.Logging, line 43
	} // library marker davegut.Logging, line 44
} // library marker davegut.Logging, line 45

def logWarn(msg) { log.warn "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" } // library marker davegut.Logging, line 47

// ~~~~~ end include (993) davegut.Logging ~~~~~

// ~~~~~ start include (1001) davegut.ST-Communications ~~~~~
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

// ~~~~~ end include (1001) davegut.ST-Communications ~~~~~

// ~~~~~ start include (1000) davegut.ST-Common ~~~~~
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
		respData << "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 54
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

def refresh() {  // library marker davegut.ST-Common, line 70
	def cmdData = [ // library marker davegut.ST-Common, line 71
		component: "main", // library marker davegut.ST-Common, line 72
		capability: "refresh", // library marker davegut.ST-Common, line 73
		command: "refresh", // library marker davegut.ST-Common, line 74
		arguments: []] // library marker davegut.ST-Common, line 75
	deviceCommand(cmdData) // library marker davegut.ST-Common, line 76
} // library marker davegut.ST-Common, line 77

def poll() { // library marker davegut.ST-Common, line 79
	if (simulate() == true) { // library marker davegut.ST-Common, line 80
		def children = getChildDevices() // library marker davegut.ST-Common, line 81
		if (children) { // library marker davegut.ST-Common, line 82
			children.each { // library marker davegut.ST-Common, line 83
				it.statusParse(testData()) // library marker davegut.ST-Common, line 84
			} // library marker davegut.ST-Common, line 85
		} // library marker davegut.ST-Common, line 86
		statusParse(testData()) // library marker davegut.ST-Common, line 87
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 88
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 89
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 90
	} else { // library marker davegut.ST-Common, line 91
		def sendData = [ // library marker davegut.ST-Common, line 92
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 93
			parse: "distResp" // library marker davegut.ST-Common, line 94
			] // library marker davegut.ST-Common, line 95
		asyncGet(sendData, "statusParse") // library marker davegut.ST-Common, line 96
	} // library marker davegut.ST-Common, line 97
} // library marker davegut.ST-Common, line 98

def deviceSetup() { // library marker davegut.ST-Common, line 100
	if (simulate() == true) { // library marker davegut.ST-Common, line 101
		def children = getChildDevices() // library marker davegut.ST-Common, line 102
//		if (children) { // library marker davegut.ST-Common, line 103
//			children.each { // library marker davegut.ST-Common, line 104
//				it.deviceSetupParse(testData()) // library marker davegut.ST-Common, line 105
//			} // library marker davegut.ST-Common, line 106
//		} // library marker davegut.ST-Common, line 107
		deviceSetupParse(testData()) // library marker davegut.ST-Common, line 108
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 109
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 110
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 111
	} else { // library marker davegut.ST-Common, line 112
		def sendData = [ // library marker davegut.ST-Common, line 113
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 114
			parse: "distResp" // library marker davegut.ST-Common, line 115
			] // library marker davegut.ST-Common, line 116
		asyncGet(sendData, "deviceSetup") // library marker davegut.ST-Common, line 117
	} // library marker davegut.ST-Common, line 118
} // library marker davegut.ST-Common, line 119

def getDeviceList() { // library marker davegut.ST-Common, line 121
	def sendData = [ // library marker davegut.ST-Common, line 122
		path: "/devices", // library marker davegut.ST-Common, line 123
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 124
		] // library marker davegut.ST-Common, line 125
	asyncGet(sendData) // library marker davegut.ST-Common, line 126
} // library marker davegut.ST-Common, line 127

def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 129
	def respData // library marker davegut.ST-Common, line 130
	if (resp.status != 200) { // library marker davegut.ST-Common, line 131
		respData = [status: "ERROR", // library marker davegut.ST-Common, line 132
					httpCode: resp.status, // library marker davegut.ST-Common, line 133
					errorMsg: resp.errorMessage] // library marker davegut.ST-Common, line 134
	} else { // library marker davegut.ST-Common, line 135
		try { // library marker davegut.ST-Common, line 136
			respData = new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Common, line 137
		} catch (err) { // library marker davegut.ST-Common, line 138
			respData = [status: "ERROR", // library marker davegut.ST-Common, line 139
						errorMsg: err, // library marker davegut.ST-Common, line 140
						respData: resp.data] // library marker davegut.ST-Common, line 141
		} // library marker davegut.ST-Common, line 142
	} // library marker davegut.ST-Common, line 143
	if (respData.status == "ERROR") { // library marker davegut.ST-Common, line 144
		logWarn("getDeviceListParse: ${respData}") // library marker davegut.ST-Common, line 145
	} else { // library marker davegut.ST-Common, line 146
		log.info "" // library marker davegut.ST-Common, line 147
		respData.items.each { // library marker davegut.ST-Common, line 148
			log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 149
		} // library marker davegut.ST-Common, line 150
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 151
	} // library marker davegut.ST-Common, line 152
} // library marker davegut.ST-Common, line 153

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 155
	Integer currTime = now() // library marker davegut.ST-Common, line 156
	Integer compTime // library marker davegut.ST-Common, line 157
	try { // library marker davegut.ST-Common, line 158
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 159
	} catch (e) { // library marker davegut.ST-Common, line 160
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 161
	} // library marker davegut.ST-Common, line 162
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 163
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 164
	return timeRemaining // library marker davegut.ST-Common, line 165
} // library marker davegut.ST-Common, line 166

// ~~~~~ end include (1000) davegut.ST-Common ~~~~~
