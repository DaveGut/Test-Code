/*	===== HUBITAT INTEGRATION VERSION =====================================================
Hubitat - Smart Things Data Collection for Hubitat Environment driver development
		Copyright 2022 Dave Gutheinz
===========================================================================================*/
def driverVer() { return "1.0.0" }
metadata {
	definition (name: "ST Data Collect",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
	}
	preferences {
		input ("stApiKey", "text", title: "SmartThings API Key", defaultValue: "")
		input ("stDeviceId", "text", title: "SmartThings Device ID", defaultValue: "")
		input ("debugLog", "bool",  
			   title: "Enable debug logging for 30 minutes", defaultValue: false)
		input ("infoLog", "bool",  
			   title: "Enable description text logging", defaultValue: true)
	}
}

def installed() { updated() }
def updated() { 
	unschedule()
	def updateData = [:]
	def deviceData
	def keyStatus = "OK"
	def devIdStatus = "OK"
	if (!stApiKey || stApiKey == "") {
		keyStatus = "No stApiKey"
	}
	if (!stDeviceId || stDeviceId == "") {
		devIdStatus = "No stDeviceId"
	}
	updateData << [status: [keyStatus: keyStatus, devIdStatus: devIdStatus]]
	updateData << [stApiKey: stApiKey, stDeviceId: stDeviceId]
	updateData << [debugLog: debugLog, infoLog: infoLog]
	if (!getDataValue("driverVersion") || 
		getDataValue("driverVersion") != driverVer()) {
		updateDataValue("driverVersion", driverVer())
		updateData << [driverVer: driverVer()]
	}
	logInfo updateData
		if (keyStatus == "OK") {
		if (devIdStatus != "OK") {
			runIn(2, getDeviceList)
			pauseExecution(5000)
			logWarn("updated: Enter the device ID into Preference to continue.")
		} else {
			getDeviceStatus("developmentParse")
		}
	} else {
		logWarn("updated: Enter the SmartThings API Key into Preference to continue.")
	}
}

def developmentParse(resp, data) {
	def respData = validateResp(resp, "developmentParse")
	if (respData == "error") { return }
	log.trace "[devStatus: ${respData}]"
	getDevDesc()
}

def getDevDesc() {
	def sendData = [
		path: "/devices/${stDeviceId.trim()}",
		parse: "getDevDescParse"
		]
	asyncGet(sendData)
}
def getDevDescParse(resp, data) {
	def respData = validateResp(resp, "test1Parse")
	if (respData == "error") { return }
	log.trace "[devDescription: ${respData}]"
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
//		if (!state.components) { // library marker davegut.ST-Common, line 31
//			updateData << [deviceStates: deviceSetup()] // library marker davegut.ST-Common, line 32
//		} // library marker davegut.ST-Common, line 33
	} // library marker davegut.ST-Common, line 34
	def updateStatus = [:] // library marker davegut.ST-Common, line 35
	updateStatus << [status: status] // library marker davegut.ST-Common, line 36
	if (statusReason != "") { // library marker davegut.ST-Common, line 37
		updateStatus << [statusReason: statusReason] // library marker davegut.ST-Common, line 38
	} // library marker davegut.ST-Common, line 39
	updateStatus << [updateData: updateData] // library marker davegut.ST-Common, line 40
	refresh() // library marker davegut.ST-Common, line 41
	return updateStatus // library marker davegut.ST-Common, line 42
} // library marker davegut.ST-Common, line 43

def cmdRespParse(respData) { // library marker davegut.ST-Common, line 45
	if (respData == "error") { // library marker davegut.ST-Common, line 46
		logData << [error: "error from setMachineState"] // library marker davegut.ST-Common, line 47
	} else if (respData.status == "OK") { // library marker davegut.ST-Common, line 48
		runIn(2, refresh) // library marker davegut.ST-Common, line 49
	} else { // library marker davegut.ST-Common, line 50
		logData << [error: respData] // library marker davegut.ST-Common, line 51
	} // library marker davegut.ST-Common, line 52
} // library marker davegut.ST-Common, line 53

def commonRefresh() { // library marker davegut.ST-Common, line 55
/*	Design // library marker davegut.ST-Common, line 56
	a.	Complete a deviceRefresh // library marker davegut.ST-Common, line 57
		1.	Ignore response. Will refresh data in ST for device. // library marker davegut.ST-Common, line 58
	b.	run getDeviceStatus // library marker davegut.ST-Common, line 59
		1.	Capture switch attributes // library marker davegut.ST-Common, line 60
			a) if on, set runEvery1Minute(refresh) // library marker davegut.ST-Common, line 61
			b)	if off, set runEvery10Minutes(refresh) // library marker davegut.ST-Common, line 62
		2.	Capture other attributes // library marker davegut.ST-Common, line 63
		3.	Log a list of current attribute states.  (Temporary) // library marker davegut.ST-Common, line 64
*/ // library marker davegut.ST-Common, line 65
	def cmdData = [ // library marker davegut.ST-Common, line 66
		component: "main", // library marker davegut.ST-Common, line 67
		capability: "refresh", // library marker davegut.ST-Common, line 68
		command: "refresh", // library marker davegut.ST-Common, line 69
		arguments: []] // library marker davegut.ST-Common, line 70
	syncPost(cmdData) // library marker davegut.ST-Common, line 71
	def respData = syncPost(sendData) // library marker davegut.ST-Common, line 72
	getDeviceStatus() // library marker davegut.ST-Common, line 73
} // library marker davegut.ST-Common, line 74

def getDeviceStatus(parseMethod = "deviceStatusParse") { // library marker davegut.ST-Common, line 76
	def sendData = [ // library marker davegut.ST-Common, line 77
		path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 78
		parse: parseMethod // library marker davegut.ST-Common, line 79
		] // library marker davegut.ST-Common, line 80
	asyncGet(sendData) // library marker davegut.ST-Common, line 81
} // library marker davegut.ST-Common, line 82

def listAttributes() { // library marker davegut.ST-Common, line 84
	def attrs = device.getSupportedAttributes() // library marker davegut.ST-Common, line 85
	def attrList = [:] // library marker davegut.ST-Common, line 86
	attrs.each { // library marker davegut.ST-Common, line 87
		def val = device.currentValue("${it}") // library marker davegut.ST-Common, line 88
		attrList << ["${it}": val] // library marker davegut.ST-Common, line 89
	} // library marker davegut.ST-Common, line 90
//	logDebug("Attributes: ${attrList}") // library marker davegut.ST-Common, line 91
	logTrace("Attributes: ${attrList}") // library marker davegut.ST-Common, line 92
} // library marker davegut.ST-Common, line 93

def on() { setSwitch("on") } // library marker davegut.ST-Common, line 95
def off() { setSwitch("off") } // library marker davegut.ST-Common, line 96
def setSwitch(onOff) { // library marker davegut.ST-Common, line 97
	logDebug("setSwitch: ${onOff}") // library marker davegut.ST-Common, line 98
	def cmdData = [ // library marker davegut.ST-Common, line 99
		component: "main", // library marker davegut.ST-Common, line 100
		capability: "switch", // library marker davegut.ST-Common, line 101
		command: onOff, // library marker davegut.ST-Common, line 102
		arguments: []] // library marker davegut.ST-Common, line 103
	cmdRespParse(syncPost(cmdData)) // library marker davegut.ST-Common, line 104
} // library marker davegut.ST-Common, line 105

def getDeviceList() { // library marker davegut.ST-Common, line 107
	def sendData = [ // library marker davegut.ST-Common, line 108
		path: "/devices", // library marker davegut.ST-Common, line 109
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 110
		] // library marker davegut.ST-Common, line 111
	asyncGet(sendData) // library marker davegut.ST-Common, line 112
} // library marker davegut.ST-Common, line 113
def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 114
	def respData = validateResp(resp, "getDeviceListParse") // library marker davegut.ST-Common, line 115
	if (respData == "error") { return } // library marker davegut.ST-Common, line 116
	log.info "" // library marker davegut.ST-Common, line 117
	respData.items.each { // library marker davegut.ST-Common, line 118
//		def deviceData = [ // library marker davegut.ST-Common, line 119
//			label: it.label, // library marker davegut.ST-Common, line 120
//			deviceId: it.deviceId // library marker davegut.ST-Common, line 121
//		] // library marker davegut.ST-Common, line 122
		log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 123
//		log.info deviceData // library marker davegut.ST-Common, line 124
	} // library marker davegut.ST-Common, line 125
	log.warn "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 126
} // library marker davegut.ST-Common, line 127

def calcTimeRemaining(compTime) { // library marker davegut.ST-Common, line 129
	Integer currentTime = new Date().getTime() // library marker davegut.ST-Common, line 130
	Integer finishTime = Date.parse("yyyy'-'MM'-'dd'T'HH':'mm':'ss'Z'", compTime).getTime() // library marker davegut.ST-Common, line 131
	finishTime = finishTime + location.timeZone.rawOffset // library marker davegut.ST-Common, line 132
	def timeRemaining = ((finishTime - currentTime)/1000).toInteger() // library marker davegut.ST-Common, line 133
	return timeRemaining // library marker davegut.ST-Common, line 134
} // library marker davegut.ST-Common, line 135
def xxcalcTimeRemaining(compTime) { // library marker davegut.ST-Common, line 136
	Integer currentTime = new Date().getTime() // library marker davegut.ST-Common, line 137
	Integer finishTime = Date.parse("yyyy'-'MM'-'dd'T'HH':'mm':'ss'Z'", compTime).getTime() // library marker davegut.ST-Common, line 138
	finishTime = finishTime + location.timeZone.rawOffset // library marker davegut.ST-Common, line 139
	def timeRemaining = ((finishTime - currentTime)/1000).toInteger() // library marker davegut.ST-Common, line 140
	return timeRemaining // library marker davegut.ST-Common, line 141
} // library marker davegut.ST-Common, line 142

// ~~~~~ end include (450) davegut.ST-Common ~~~~~
