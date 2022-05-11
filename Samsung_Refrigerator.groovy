/*	===== HUBITAT Samsung Refrigerator Using SmartThings
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
	Commands Implement: setRapidCooling, setRapidFreezing
	Commands not implemented: coolerSetpoint, FreezerSetpoint, setDefrost, OnOFf
	Attributes not Implemented: coolerSetpoing, freezerSetpoing, icemaker
	Ambiguities to resolve: ContactSensor (there are four reporting, cooler
		freezer, main, and cvroom (meatdrawer).  May be the main reports any other contact
		open.

TODO
a.	create componentId in child drivers install routine
d.	Button Implementation
===========================================================================================*/
import groovy.json.JsonSlurper
def driverVer() { return "0.5" }
//def simulate() { return true }

metadata {
	definition (name: "Samsung Refrigerator",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
		capability "Refresh"
		capability "Contact Sensor"
		attribute "defrost", "string"
		attribute "rapidCooling", "string"
		attribute "rapidFreezing", "string"
		attribute "filterReplace", "string"
		command "getDeviceList"
		command "setRapidCooling", [[
			name: "Rapid Cooling",
			constraints: ["on", "off"],
			type: "ENUM"]]
		command "setRapidFreezing", [[
			name: "Rapid Freezing",
			constraints: ["on", "off"],
			type: "ENUM"]]
	}
	preferences {
		input ("stApiKey", "password", title: "SmartThings API Key", defaultValue: "")
		input ("stDeviceId", "text", title: "SmartThings Device ID", defaultValue: "")
		input ("childInstall", "bool",
			   title: "Install child components", defaultValue: false)
		input ("refreshInterval", "enum", 
			   title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"], 
			   defaultValue: "5")
		input ("debugLog", "bool",  
			   title: "Enable debug logging for 30 minutes", defaultValue: false)
		input ("infoLog", "bool",  
			   title: "Enable description text logging", defaultValue: true)
	}
}

def installed() {
	runIn(1, updated)
}

def updated() {
	if (childInstall) {
		installChildren()
		pauseExecution(5000)
	}
	def commonStatus = commonUpdate()
	switch(refreshInterval) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		case "30" : runEvery30Minutes(refresh); break
		default: runEvery5Minutes(refresh)
	}
	
	logInfo("updated: [refreshInterval: ${refreshInterval}, ${commonStatus}]")
	def children = getChildDevices()
	children.each {
		it.updated()
	}
}

def setRapidCooling(onOff) {
	logDebug("setRapidCooling: ${onOff}")
	def cmdData = [
		component: "main",
		capability: "refrigeration",
		command: "setRapidCooling",
		arguments: [onOff]]
	cmdRespParse(syncPost(cmdData))
}

def setRapidFreezing(onOff) {
	logDebug("setRapidFreezing: ${onOff}")
	def cmdData = [
		component: "main",
		capability: "refrigeration",
		command: "setRapidFreezing",
		arguments: [onOff]]
	cmdRespParse(syncPost(cmdData))
}

def refresh() {
//	if (simulate() == true) {
//		deviceStatusParse(testData(), "simulation")
//	} else {
		commonRefresh()
//	}
}

def deviceStatusParse(resp, data) {
	def respData
	if (data == "simulation") {
		respData = resp
	} else {
		respData = validateResp(resp, "deviceStatusParse")
	}
	if (respData == "error") { return }

	def children = getChildDevices()
	children.each {
		it.statusParse(respData)
	}

	def parseData = respData.components.main
	def logData = [:]
	def contact = parseData.contactSensor.contact.value
	if (device.currentValue("contact") != contact) {
		sendEvent(name: "contact", value: contact)
		logData << [contact: contact]
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

	def filterReplace = parseData["custom.waterFilter"].waterFilterStatus.value
	if (device.currentValue("filterReplace") != filterReplace) {
		sendEvent(name: "filterReplace", value: filterReplace)
		logData << [filterReplace: filterReplace]
	}

	if (logData != [:]) {
		logInfo("getDeviceStatus: ${logData}")
	}
//	Temp Test Code
	runIn(1, listAttributes)
}

def xxxxxxxpush(pushed) {
	logDebug("push: button = ${pushed}")
	if (pushed == null) {
		logWarn("push: pushed is null.  Input ignored")
	} else {
		pushed = pushed.toInteger()
		switch(pushed) {
			//	===== Physical Remote Commands =====
			case 1 : setRapidCooling("on"); break
			case 2 : setRapidCooling("off"); break
			case 3 : setRapidFreezing("on"); break
			case 4 : setRapidFreezing("off"); break
			default:
				logWarn("push: Invalid Button Number!")
		}
	}
}

def installChildren() {
//	def respData
//	if (simulate() == true) {
//		respData = testData()
//	} else {
		def resp = syncGet("/devices/${stDeviceId.trim()}/status")
		if (resp.status == "OK") {
			respData = resp.results
		} else {
			logWarn("installChildren: Invalid return from SmartThings")
		}
//	}
	def disabledComponents = respData.components.main["custom.disabledComponents"].disabledComponents.value
	def components = respData.components
	def dni = device.getDeviceNetworkId()
	components.each {
		if (disabledComponents.contains(it.key) == false && it.key != "main") {
			def childDni = dni + "-${it.key}"
			def isChild = getChildDevice(childDni)
			if (isChild) {
				logInfo("installChildren: [component: ${it.key}, status: already installed]")
			} else {
				addChild(it.key, childDni)
			}
		} else if (it.key != "main") {
			logInfo("installChildren: [component: ${it.key}, status: disabled]")
		}
	}
	device.updateSetting("childInstall", [type:"bool", value: false])
}

def addChild(component, childDni) {
	def type
	switch(component) {
		case "freezer":
		case "cooler":
		case "onedoor":
			type = "Samsung Refrigerator-cavity"
			break
		case "icemaker":
		case "icemaker-02":
			type = "Samsung Refrigerator-icemaker"
			break
		case "cvroom":
			type = "Samsung Refrigerator-cvroom"
			break
		default:
			logWarn("addChild: [component: ${component}, error: not on components list.")
	}
//	def label = "${device.displayName}-${component}"
	try {
		addChildDevice("davegut", "${type}", "${childDni}", [
			"name": type, "label": component, component: component])
		logInfo("addChild: [status: ADDED, label: ${component}, type: ${type}]")
	} catch (error) {
		logWarn("addChild: [status: FAILED, type: ${type}, dni: ${childDni}, component: ${component}, error: ${error}]")
	}
}

//	===== Library Integration =====





// ~~~~~ start include (611) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8

//	Logging during development // library marker davegut.Logging, line 10
def listAttributes() { // library marker davegut.Logging, line 11
	def attrs = device.getSupportedAttributes() // library marker davegut.Logging, line 12
	def attrList = [:] // library marker davegut.Logging, line 13
	attrs.each { // library marker davegut.Logging, line 14
		def val = device.currentValue("${it}") // library marker davegut.Logging, line 15
		attrList << ["${it}": val] // library marker davegut.Logging, line 16
	} // library marker davegut.Logging, line 17
	logTrace("Attributes: ${attrList}") // library marker davegut.Logging, line 18
} // library marker davegut.Logging, line 19

def logTrace(msg){ // library marker davegut.Logging, line 21
	log.trace "${device.displayName} ${driverVer()}: ${msg}" // library marker davegut.Logging, line 22
} // library marker davegut.Logging, line 23

def logInfo(msg) {  // library marker davegut.Logging, line 25
	if (infoLog == true) { // library marker davegut.Logging, line 26
		log.info "${device.displayName} ${driverVer()}: ${msg}" // library marker davegut.Logging, line 27
	} // library marker davegut.Logging, line 28
} // library marker davegut.Logging, line 29

def debugLogOff() { // library marker davegut.Logging, line 31
	device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.Logging, line 32
	logInfo("Debug logging is false.") // library marker davegut.Logging, line 33
} // library marker davegut.Logging, line 34

def logDebug(msg) { // library marker davegut.Logging, line 36
	if (debugLog == true) { // library marker davegut.Logging, line 37
		log.debug "${device.displayName} ${driverVer()}: ${msg}" // library marker davegut.Logging, line 38
	} // library marker davegut.Logging, line 39
} // library marker davegut.Logging, line 40

def logWarn(msg) { log.warn "${device.displayName} ${driverVer()}: ${msg}" } // library marker davegut.Logging, line 42


// ~~~~~ end include (611) davegut.Logging ~~~~~

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
		def errorResp = [status: "error", // library marker davegut.ST-Communications, line 13
						 httpCode: resp.status, // library marker davegut.ST-Communications, line 14
						 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 15
		logWarn("${method}: ${errorResp}") // library marker davegut.ST-Communications, line 16
		return "error" // library marker davegut.ST-Communications, line 17
	} else { // library marker davegut.ST-Communications, line 18
		try { // library marker davegut.ST-Communications, line 19
			return new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Communications, line 20
		} catch (err) { // library marker davegut.ST-Communications, line 21
			logWarn("${method}: [noDataError: ${err}]") // library marker davegut.ST-Communications, line 22
		} // library marker davegut.ST-Communications, line 23
	} // library marker davegut.ST-Communications, line 24
} // library marker davegut.ST-Communications, line 25

private asyncGet(sendData) { // library marker davegut.ST-Communications, line 27
//	sendData Spec: [path, parse] // library marker davegut.ST-Communications, line 28
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Communications, line 29
		logWarn("asyncGet: [status: ${error}, statusReason: no stApiKey]") // library marker davegut.ST-Communications, line 30
		return // library marker davegut.ST-Communications, line 31
	} // library marker davegut.ST-Communications, line 32
	logDebug("asyncGet: [sendData: ${sendData}]") // library marker davegut.ST-Communications, line 33
	def sendCmdParams = [ // library marker davegut.ST-Communications, line 34
		uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 35
		path: sendData.path, // library marker davegut.ST-Communications, line 36
		headers: ['Authorization': 'Bearer ' + stApiKey.trim()]] // library marker davegut.ST-Communications, line 37
	try { // library marker davegut.ST-Communications, line 38
		asynchttpGet(sendData.parse, sendCmdParams) // library marker davegut.ST-Communications, line 39
	} catch (error) { // library marker davegut.ST-Communications, line 40
		logWarn("asyncGet: [status: error, statusReason: ${error}]") // library marker davegut.ST-Communications, line 41
	} // library marker davegut.ST-Communications, line 42
} // library marker davegut.ST-Communications, line 43

private syncGet(path = "/devices/${stDeviceId.trim()}/commands"){ // library marker davegut.ST-Communications, line 45
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Communications, line 46
		logWarn("asyncPost: [status: ${error}, statusReason: no stApiKey]") // library marker davegut.ST-Communications, line 47
		return // library marker davegut.ST-Communications, line 48
	} // library marker davegut.ST-Communications, line 49
	logDebug("syncGet: [cmdBody: ${cmdData}, path: ${path}]") // library marker davegut.ST-Communications, line 50
	def cmdBody = [commands: [cmdData]] // library marker davegut.ST-Communications, line 51
	def sendCmdParams = [ // library marker davegut.ST-Communications, line 52
		uri: "https://api.smartthings.com/v1${path}", // library marker davegut.ST-Communications, line 53
		headers: ['Authorization': 'Bearer ' + stApiKey.trim()] // library marker davegut.ST-Communications, line 54
	] // library marker davegut.ST-Communications, line 55
	def respData = "error" // library marker davegut.ST-Communications, line 56
	try { // library marker davegut.ST-Communications, line 57
		httpGet(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 58
			if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 59
				respData = [status: "OK", results: resp.data] // library marker davegut.ST-Communications, line 60
			} else { // library marker davegut.ST-Communications, line 61
				def errorResp = [status: "error", // library marker davegut.ST-Communications, line 62
								 cmdBody: cmdBody, // library marker davegut.ST-Communications, line 63
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 64
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 65
				logDebug("syncGet error from ST: ${errorResp}") // library marker davegut.ST-Communications, line 66
			} // library marker davegut.ST-Communications, line 67
		} // library marker davegut.ST-Communications, line 68
	} catch (error) { // library marker davegut.ST-Communications, line 69
		def errorResp = [status: "error", // library marker davegut.ST-Communications, line 70
						 cmdBody: cmdBody, // library marker davegut.ST-Communications, line 71
						 errorMsg: error] // library marker davegut.ST-Communications, line 72
		logDebug("syncGet: ${errorResp}") // library marker davegut.ST-Communications, line 73
	} // library marker davegut.ST-Communications, line 74
	return respData // library marker davegut.ST-Communications, line 75
} // library marker davegut.ST-Communications, line 76

private syncPost(cmdData, path = "/devices/${stDeviceId.trim()}/commands"){ // library marker davegut.ST-Communications, line 78
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Communications, line 79
		logWarn("asyncPost: [status: ${error}, statusReason: no stApiKey]") // library marker davegut.ST-Communications, line 80
		return // library marker davegut.ST-Communications, line 81
	} // library marker davegut.ST-Communications, line 82
	logDebug("syncPost: [cmdBody: ${cmdData}, path: ${path}]") // library marker davegut.ST-Communications, line 83
	def cmdBody = [commands: [cmdData]] // library marker davegut.ST-Communications, line 84
	def sendCmdParams = [ // library marker davegut.ST-Communications, line 85
		uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 86
		path: path, // library marker davegut.ST-Communications, line 87
		headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 88
		body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 89
	] // library marker davegut.ST-Communications, line 90
	def respData = "error" // library marker davegut.ST-Communications, line 91
	try { // library marker davegut.ST-Communications, line 92
		httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 93
			if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 94
				respData = [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 95
			} else { // library marker davegut.ST-Communications, line 96
				def errorResp = [status: "error", // library marker davegut.ST-Communications, line 97
								 cmdBody: cmdBody, // library marker davegut.ST-Communications, line 98
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 99
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 100
				logDebug("syncPost error from ST: ${errorResp}") // library marker davegut.ST-Communications, line 101
			} // library marker davegut.ST-Communications, line 102
		} // library marker davegut.ST-Communications, line 103
	} catch (error) { // library marker davegut.ST-Communications, line 104
		def errorResp = [status: "error", // library marker davegut.ST-Communications, line 105
						 cmdBody: cmdBody, // library marker davegut.ST-Communications, line 106
						 errorMsg: error] // library marker davegut.ST-Communications, line 107
		logDebug("syncPost: ${errorResp}") // library marker davegut.ST-Communications, line 108
	} // library marker davegut.ST-Communications, line 109
	return respData // library marker davegut.ST-Communications, line 110
} // library marker davegut.ST-Communications, line 111

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
	getDeviceStatus("deviceStatusParse") // library marker davegut.ST-Common, line 61
} // library marker davegut.ST-Common, line 62

def getDeviceStatus(parseMethod = "deviceStatusParse") { // library marker davegut.ST-Common, line 64
	def sendData = [ // library marker davegut.ST-Common, line 65
		path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 66
		parse: parseMethod // library marker davegut.ST-Common, line 67
		] // library marker davegut.ST-Common, line 68
	asyncGet(sendData) // library marker davegut.ST-Common, line 69
} // library marker davegut.ST-Common, line 70

def getDeviceList() { // library marker davegut.ST-Common, line 72
	def sendData = [ // library marker davegut.ST-Common, line 73
		path: "/devices", // library marker davegut.ST-Common, line 74
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 75
		] // library marker davegut.ST-Common, line 76
	asyncGet(sendData) // library marker davegut.ST-Common, line 77
} // library marker davegut.ST-Common, line 78
def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 79
	def respData = validateResp(resp, "getDeviceListParse") // library marker davegut.ST-Common, line 80
	if (respData == "error") { return } // library marker davegut.ST-Common, line 81
	log.info "" // library marker davegut.ST-Common, line 82
	respData.items.each { // library marker davegut.ST-Common, line 83
		log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 84
	} // library marker davegut.ST-Common, line 85
	log.warn "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 86
} // library marker davegut.ST-Common, line 87

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 89
	Integer currTime = now() // library marker davegut.ST-Common, line 90
	Integer compTime // library marker davegut.ST-Common, line 91
	try { // library marker davegut.ST-Common, line 92
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 93
	} catch (e) { // library marker davegut.ST-Common, line 94
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 95
	} // library marker davegut.ST-Common, line 96
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 97
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 98
	return timeRemaining // library marker davegut.ST-Common, line 99
} // library marker davegut.ST-Common, line 100

// ~~~~~ end include (450) davegut.ST-Common ~~~~~

// ~~~~~ start include (610) davegut.ST-Refrig-Sim ~~~~~
library ( // library marker davegut.ST-Refrig-Sim, line 1
	name: "ST-Refrig-Sim", // library marker davegut.ST-Refrig-Sim, line 2
	namespace: "davegut", // library marker davegut.ST-Refrig-Sim, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Refrig-Sim, line 4
	description: "Simulator - Samsung Refrigerator", // library marker davegut.ST-Refrig-Sim, line 5
	category: "utilities", // library marker davegut.ST-Refrig-Sim, line 6
	documentationLink: "" // library marker davegut.ST-Refrig-Sim, line 7
) // library marker davegut.ST-Refrig-Sim, line 8

def testData() { // library marker davegut.ST-Refrig-Sim, line 10
//* // library marker davegut.ST-Refrig-Sim, line 11
//	main // library marker davegut.ST-Refrig-Sim, line 12
	def mainContact = "closed" // library marker davegut.ST-Refrig-Sim, line 13
	def defrost = "off" // library marker davegut.ST-Refrig-Sim, line 14
	def rapidCooling = "off" // library marker davegut.ST-Refrig-Sim, line 15
	def rapidFreezing = "off" // library marker davegut.ST-Refrig-Sim, line 16
	def filterReplace = "replace" // library marker davegut.ST-Refrig-Sim, line 17

//	cooler // library marker davegut.ST-Refrig-Sim, line 19
	def tempUnit = "C" // library marker davegut.ST-Refrig-Sim, line 20
	def coolerContact = "closed" // library marker davegut.ST-Refrig-Sim, line 21
	def coolerTemperature = 35 // library marker davegut.ST-Refrig-Sim, line 22
	def coolerSetpoint = 30 // library marker davegut.ST-Refrig-Sim, line 23
//	freezer // library marker davegut.ST-Refrig-Sim, line 24
	def freezerContact = "closed" // library marker davegut.ST-Refrig-Sim, line 25
	def freezerTemperature = 36 // library marker davegut.ST-Refrig-Sim, line 26
	def freezerSetpoint = 31 // library marker davegut.ST-Refrig-Sim, line 27
//	cvroom // library marker davegut.ST-Refrig-Sim, line 28
	def drawerMode = "CV_FDR_MEAT" // library marker davegut.ST-Refrig-Sim, line 29
	def drawerContact = "closed" // library marker davegut.ST-Refrig-Sim, line 30
//	icemaker // library marker davegut.ST-Refrig-Sim, line 31
	def onOff = "on" // library marker davegut.ST-Refrig-Sim, line 32
//*/ // library marker davegut.ST-Refrig-Sim, line 33

/* // library marker davegut.ST-Refrig-Sim, line 35
//	main // library marker davegut.ST-Refrig-Sim, line 36
	def mainContact = "open" // library marker davegut.ST-Refrig-Sim, line 37
	def defrost = "on" // library marker davegut.ST-Refrig-Sim, line 38
	def rapidCooling = "on" // library marker davegut.ST-Refrig-Sim, line 39
	def rapidFreezing = "on" // library marker davegut.ST-Refrig-Sim, line 40
	def filterReplace = "ok" // library marker davegut.ST-Refrig-Sim, line 41
//	cooler // library marker davegut.ST-Refrig-Sim, line 42
	def tempUnit = "F" // library marker davegut.ST-Refrig-Sim, line 43
	def coolerContact = "open" // library marker davegut.ST-Refrig-Sim, line 44
	def coolerTemperature = 32 // library marker davegut.ST-Refrig-Sim, line 45
	def coolerSetpoint = 32 // library marker davegut.ST-Refrig-Sim, line 46
//	freezer // library marker davegut.ST-Refrig-Sim, line 47
	def freezerContact = "open" // library marker davegut.ST-Refrig-Sim, line 48
	def freezerTemperature = 33 // library marker davegut.ST-Refrig-Sim, line 49
	def freezerSetpoint = 33 // library marker davegut.ST-Refrig-Sim, line 50
//	cvroom // library marker davegut.ST-Refrig-Sim, line 51
	def drawerMode = "CV_FDR_VEG" // library marker davegut.ST-Refrig-Sim, line 52
	def drawerContact = "open" // library marker davegut.ST-Refrig-Sim, line 53
//	icemaker // library marker davegut.ST-Refrig-Sim, line 54
	def onOff = "off" // library marker davegut.ST-Refrig-Sim, line 55
*/ // library marker davegut.ST-Refrig-Sim, line 56

	return [components:[ // library marker davegut.ST-Refrig-Sim, line 58
		icemaker:[ // library marker davegut.ST-Refrig-Sim, line 59
			switch:[switch:[value: onOff]]], // library marker davegut.ST-Refrig-Sim, line 60
		cooler:[ // library marker davegut.ST-Refrig-Sim, line 61
			contactSensor:[contact:[value: coolerContact]], // library marker davegut.ST-Refrig-Sim, line 62
			temperatureMeasurement:[temperature:[value: coolerTemperature, unit: tempUnit]],  // library marker davegut.ST-Refrig-Sim, line 63
			thermostatCoolingSetpoint:[coolingSetpoint:[value: coolerSetpoint, unit: tempUnit]]], // library marker davegut.ST-Refrig-Sim, line 64
		freezer:[ // library marker davegut.ST-Refrig-Sim, line 65
			contactSensor:[contact:[value: freezerContact]], // library marker davegut.ST-Refrig-Sim, line 66
			temperatureMeasurement:[temperature:[value: freezerTemperature, unit: tempUnit]],  // library marker davegut.ST-Refrig-Sim, line 67
			thermostatCoolingSetpoint:[coolingSetpoint:[value: freezerSetpoint, unit: tempUnit]]], // library marker davegut.ST-Refrig-Sim, line 68
		main:[ // library marker davegut.ST-Refrig-Sim, line 69
			"custom.disabledComponents":[disabledComponents:[value:["onedoor", "icemaker-02", "pantry-01", "pantry-02"]]],  // library marker davegut.ST-Refrig-Sim, line 70
			contactSensor:[contact:[value: mainContact]],  // library marker davegut.ST-Refrig-Sim, line 71
			refrigeration:[ // library marker davegut.ST-Refrig-Sim, line 72
				defrost:[value: defrost],  // library marker davegut.ST-Refrig-Sim, line 73
				rapidCooling:[value: rapidCooling],  // library marker davegut.ST-Refrig-Sim, line 74
				rapidFreezing:[value: rapidFreezing]],  // library marker davegut.ST-Refrig-Sim, line 75
			"custom.waterFilter":[waterFilterStatus:[value: filterReplace]]],  // library marker davegut.ST-Refrig-Sim, line 76
		cvroom:[ // library marker davegut.ST-Refrig-Sim, line 77
			"custom.fridgeMode":[fridgeMode:[value: drawerMode]],  // library marker davegut.ST-Refrig-Sim, line 78
			contactSensor:[contact:[value: drawerContact]]], // library marker davegut.ST-Refrig-Sim, line 79
		onedoor:[],  // library marker davegut.ST-Refrig-Sim, line 80
		"pantry-01":[],  // library marker davegut.ST-Refrig-Sim, line 81
		"pantry-02":[],  // library marker davegut.ST-Refrig-Sim, line 82
		"icemaker-02":[] // library marker davegut.ST-Refrig-Sim, line 83
	]] // library marker davegut.ST-Refrig-Sim, line 84
} // library marker davegut.ST-Refrig-Sim, line 85




// ~~~~~ end include (610) davegut.ST-Refrig-Sim ~~~~~
