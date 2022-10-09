/*	Samsung HVAC using SmartThings Interface
		Copyright Dave Gutheinz
License Information:
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Description
This driver is for SmartThings-installed Samsung HVAC for import of control
and status of defined functions into Hubitat Environment.
=====	Library Use
This driver uses libraries for the functions common to SmartThings devices. 
Library code is at the bottom of the distributed single-file driver.
===== Installation Instructions Link =====
https://github.com/DaveGut/HubitatActive/blob/master/SamsungAppliances/Install_Samsung_Appliance.pdf

1.2.2Test Changes
==============================================================================*/
def driverVer() { return "1.2.2Test" }

metadata {
	definition (name: "Samsung HVAC",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungAppliances/Samsung_HVAC.groovy"
			   ){
		capability "Refresh"
		command "aStep0"
		command "aStep1"
		command "aStep2"
		command "aStep3"
		command "aStep4"
		command "aStep5"
//		command "setSetpoint", ["NUMBER"]
//		command "setHVACSetpoint", ["NUMBER"]
//		command "setHVACScale", ["STRING"]
	}
	preferences {
		input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "")
		if (stApiKey) {
			input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "")
		}
//		if (stDeviceId) {
//			input ("tempScale", "enum", options: ["°C", "°F"], title: "Temperature Scale", defaultValue: "°C")
//		}
	}
}

def aStep0() {
	log.warn "STEP 0: Refreshing device status and displaying test data"
	refresh()
	pauseExecution(3000)
	aStep1()
}
	
def aStep1() {
	log.warn "\n\nSTEP 1: \n\t\t1. On the HVAC panel, verify the device is set to C (if not, tell me).\n\t\t" +
		"2. Set the setpoint up by 1 degree.\nt\t3. Press Refresh.\n\t\t  3. wait 10 seconds then Proceed to A Step 2"
}
def aStep2() {
	log.warn "\n\nSTEP 2: \n\t\t1. On the remote control panel, verify the device is set to C (if not, tell me).\n\t\t" +
		"2. Set the setpoint down by 1 degree.\nt\t3. Press Refresh.\n\t\t  3. wait 10 seconds then Proceed to A Step 2"
}
def aStep3() {
	log.warn "\n\nSTEP 3: \n\t\t1. Using the SmartThings App, verify the device is set to C (if not, tell me).\n\t\t" +
		"2. Set the setpoint up by 1 degree.\nt\t3. Press Refresh.\n\t\t  3. wait 10 seconds then Proceed to A Step 2"
}
def aStep4() {
	log.warn "\n\nSTEP 4: \n\t\t1. Using the SmartThings App, verify the device is set to C (if not, tell me).\n\t\t" +
		"2. Set the setpoint up by 1 degree.\nt\t3. Press Refresh\n\t\t." +
		"3. Verify the unit is set to tempUnit C before proceeding to A Step 5."
}
def aStep5() {
	log.warn "\n\nSTEP 4: \n\t\tThese are automated steps. " +
		"Testing will be around 20C (68F).  There will be a 10 second gap between tests.\n\r" +
		"A final message will appear after completion."
	testSetpointA(20)
	pauseExecution(9000)
	testSetpointA(70)
	pauseExecution(9000)
	testSetpointB(21, "C")
	pauseExecution(9000)
	testSetpointB(71, "F")
	pauseExecution(9000)
	testSetpointB(19, "C")
	pauseExecution(9000)
	testSetpointC(72)
	pauseExecution(9000)
	testSetpointC(18)
	pauseExecution(9000)
	if (state.tempUnit == "°C") {
		setOff()
		pauseExecution(1000)
		setOn()
	}
	log.warn "TESTING COMPLETE"
}

def setHVACSetpoint(setpoint) {
	def newSetpoint = checkSetpoint(setpoint)
	if (newSetpoint.error) {
		logWarn("testSetpointC: ${newSetpoint}")
	} else {
		state.setpoint = newSetpoint.setpoint
		poll()
	}
}
def setHVACScale(unit) {
	state.scale = unit
	poll()
}

def setSetpoint(setpoint) { testSetpointC(setpoint) }
def testSetpointA(setpoint) {
	def cmdData = [
		component: "main",
		capability: "thermostatCoolingSetpoint",
		command: "setCoolingSetpoint",
		arguments: [setpoint]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("testSetpointA: [tempUnit = ${state.tempUnit},cmd: ${setpoint}, ${cmdStatus}]")
}
def testSetpointB(setpoint, unit) {
	def cmdData = [
		component: "main",
		capability: "thermostatCoolingSetpoint",
		command: "setCoolingSetpoint",
		arguments: [setpoint, "C"]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("testSetpointB: [cmd: ${setpoint}, unit: C, ${cmdStatus}]")
}
def testSetpointC(setpoint) {
	def newSetpoint = checkSetpoint(setpoint)
	if (newSetpoint.error) {
		logWarn("testSetpointC: ${newSetpoint}")
	} else {
		def cmdData = [
			component: "main",
			capability: "thermostatCoolingSetpoint",
			command: "setCoolingSetpoint",
			arguments: [newSetpoint.setpoint]]
		def cmdStatus = deviceCommand(cmdData)
		logInfo("testSetpointA: [tempUnit = ${state.tempUnit}, setpoint: ${setpoint}, cmd: ${newSetpoint.setpoint}, ${cmdStatus}]")
	}
}

def checkSetpoint(setpoint) {
	def newSetpoint
	if (state.tempUnit == "°F") {
		if (setpoint <= 45) {
			newSetpoint = Math.round(32.45 + 9 * setpoint / 5)
			newSetpoint = newSetpoint.toInteger()
		} else {
			newSetpoint = setpoint
		}
	} else if (state.tempUnit == "°C") {
		if (setpoint > 45) {
			newSetpoint = ((setpoint - 32) * 5 / 9)
			newSetpoint = ((0.5 + 2*newSetpoint).toInteger())/2
		} else {
			newSetpoint = setpoint
		}
	}
	if (newSetpoint < state.minSetpoint || newSetpoint > state.maxSetpoint) {
		return [error: "setpoint is outside device min and max setpoints"]
	} else {
		return [setpoint: newSetpoint]
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
}

def statusParse(parseData) {
	def logData = [:]
	def tempUnit = parseData.temperatureMeasurement.temperature.unit
	if (tempUnit != state.tempUnit) {
		state.tempUnit = "°${tempUnit}"
		logData << [tempUnit: tempUnit]	

		def minSetpoint = parseData["custom.thermostatSetpointControl"].minimumSetpoint.value.toInteger()
		state.minSetpoint = minSetpoint
		logData << [minSetpoint: minSetpoint]

		def maxSetpoint = parseData["custom.thermostatSetpointControl"].maximumSetpoint.value.toInteger()
		state.maxSetpoint = maxSetpoint
		logData << [maxSetpoint: maxSetpoint]
	}
	
	def onOff = parseData.switch.switch.value
	state.onOff = onOff
	logData << [onOff: onOff]
	
	def hvacSetpoint = parseData.thermostatCoolingSetpoint.coolingSetpoint.value
	state.hvacSetpoint = hvacSetpoint
	logData << [hvacSetpoint: hvacSetpoint]
	
	logTrace("parseData: ${logData}")
}

def setOn() {
	def cmdData = [
		component: "main",
		capability: "switch",
		command: "on",
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	return cmdStatus
}
def setOff() {
	def cmdData = [
		component: "main",
		capability: "switch",
		command: "off",
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	return cmdStatus
}

//	===== Library Integration =====



def simulate() { return true}


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

//	6.7.2 Change B.  Remove driverVer() // library marker davegut.Logging, line 25
def logTrace(msg){ // library marker davegut.Logging, line 26
	log.trace "${device.displayName}: ${msg}" // library marker davegut.Logging, line 27
} // library marker davegut.Logging, line 28

def logInfo(msg) {  // library marker davegut.Logging, line 30
	if (!infoLog || infoLog == true) { // library marker davegut.Logging, line 31
		log.info "${device.displayName}: ${msg}" // library marker davegut.Logging, line 32
	} // library marker davegut.Logging, line 33
} // library marker davegut.Logging, line 34

def debugLogOff() { // library marker davegut.Logging, line 36
	if (debug == true) { // library marker davegut.Logging, line 37
		device.updateSetting("debug", [type:"bool", value: false]) // library marker davegut.Logging, line 38
	} else if (debugLog == true) { // library marker davegut.Logging, line 39
		device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.Logging, line 40
	} // library marker davegut.Logging, line 41
	logInfo("Debug logging is false.") // library marker davegut.Logging, line 42
} // library marker davegut.Logging, line 43

def logDebug(msg) { // library marker davegut.Logging, line 45
	if (debug == true || debugLog == true) { // library marker davegut.Logging, line 46
		log.debug "${device.displayName}: ${msg}" // library marker davegut.Logging, line 47
	} // library marker davegut.Logging, line 48
} // library marker davegut.Logging, line 49

def logWarn(msg) { log.warn "${device.displayName}: ${msg}" } // library marker davegut.Logging, line 51

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
		case "10sec":  // library marker davegut.ST-Common, line 41
			schedule("*/10 * * * * ?", "poll")		 // library marker davegut.ST-Common, line 42
			break // library marker davegut.ST-Common, line 43
		case "20sec": // library marker davegut.ST-Common, line 44
			schedule("*/20 * * * * ?", "poll")		 // library marker davegut.ST-Common, line 45
			break // library marker davegut.ST-Common, line 46
		case "30sec": // library marker davegut.ST-Common, line 47
			schedule("*/30 * * * * ?", "poll")		 // library marker davegut.ST-Common, line 48
			break // library marker davegut.ST-Common, line 49
		case "1" : runEvery1Minute(poll); break // library marker davegut.ST-Common, line 50
		case "5" : runEvery5Minutes(poll); break // library marker davegut.ST-Common, line 51
		case "10" : runEvery10Minutes(poll); break // library marker davegut.ST-Common, line 52
		case "30" : runEvery30Minutes(poll); break // library marker davegut.ST-Common, line 53
		default: runEvery10Minutes(poll) // library marker davegut.ST-Common, line 54
	} // library marker davegut.ST-Common, line 55
} // library marker davegut.ST-Common, line 56

def deviceCommand(cmdData) { // library marker davegut.ST-Common, line 58
	def respData = [:] // library marker davegut.ST-Common, line 59
	if (simulate() == true) { // library marker davegut.ST-Common, line 60
		respData = testResp(cmdData) // library marker davegut.ST-Common, line 61
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 62
		respData << [status: "FAILED", data: "no stDeviceId"] // library marker davegut.ST-Common, line 63
	} else { // library marker davegut.ST-Common, line 64
		def sendData = [ // library marker davegut.ST-Common, line 65
			path: "/devices/${stDeviceId.trim()}/commands", // library marker davegut.ST-Common, line 66
			cmdData: cmdData // library marker davegut.ST-Common, line 67
		] // library marker davegut.ST-Common, line 68
		respData = syncPost(sendData) // library marker davegut.ST-Common, line 69
	} // library marker davegut.ST-Common, line 70
	if (cmdData.capability && cmdData.capability != "refresh") { // library marker davegut.ST-Common, line 71
		refresh() // library marker davegut.ST-Common, line 72
	} else { // library marker davegut.ST-Common, line 73
		poll() // library marker davegut.ST-Common, line 74
	} // library marker davegut.ST-Common, line 75
	return respData // library marker davegut.ST-Common, line 76
} // library marker davegut.ST-Common, line 77

def refresh() { // library marker davegut.ST-Common, line 79
	if (stApiKey!= null) { // library marker davegut.ST-Common, line 80
		def cmdData = [ // library marker davegut.ST-Common, line 81
			component: "main", // library marker davegut.ST-Common, line 82
			capability: "refresh", // library marker davegut.ST-Common, line 83
			command: "refresh", // library marker davegut.ST-Common, line 84
			arguments: []] // library marker davegut.ST-Common, line 85
		deviceCommand(cmdData) // library marker davegut.ST-Common, line 86
	} // library marker davegut.ST-Common, line 87
} // library marker davegut.ST-Common, line 88

def poll() { // library marker davegut.ST-Common, line 90
	if (simulate() == true) { // library marker davegut.ST-Common, line 91
		def children = getChildDevices() // library marker davegut.ST-Common, line 92
		if (children) { // library marker davegut.ST-Common, line 93
			children.each { // library marker davegut.ST-Common, line 94
				it.statusParse(testData()) // library marker davegut.ST-Common, line 95
			} // library marker davegut.ST-Common, line 96
		} // library marker davegut.ST-Common, line 97
		statusParse(testData()) // library marker davegut.ST-Common, line 98
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 99
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 100
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 101
	} else { // library marker davegut.ST-Common, line 102
		def sendData = [ // library marker davegut.ST-Common, line 103
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 104
			parse: "distResp" // library marker davegut.ST-Common, line 105
			] // library marker davegut.ST-Common, line 106
		asyncGet(sendData, "statusParse") // library marker davegut.ST-Common, line 107
	} // library marker davegut.ST-Common, line 108
} // library marker davegut.ST-Common, line 109

def deviceSetup() { // library marker davegut.ST-Common, line 111
	if (simulate() == true) { // library marker davegut.ST-Common, line 112
		def children = getChildDevices() // library marker davegut.ST-Common, line 113
		deviceSetupParse(testData()) // library marker davegut.ST-Common, line 114
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 115
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 116
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 117
	} else { // library marker davegut.ST-Common, line 118
		def sendData = [ // library marker davegut.ST-Common, line 119
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 120
			parse: "distResp" // library marker davegut.ST-Common, line 121
			] // library marker davegut.ST-Common, line 122
		asyncGet(sendData, "deviceSetup") // library marker davegut.ST-Common, line 123
	} // library marker davegut.ST-Common, line 124
} // library marker davegut.ST-Common, line 125

def getDeviceList() { // library marker davegut.ST-Common, line 127
	def sendData = [ // library marker davegut.ST-Common, line 128
		path: "/devices", // library marker davegut.ST-Common, line 129
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 130
		] // library marker davegut.ST-Common, line 131
	asyncGet(sendData) // library marker davegut.ST-Common, line 132
} // library marker davegut.ST-Common, line 133

def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 135
	def respData // library marker davegut.ST-Common, line 136
	if (resp.status != 200) { // library marker davegut.ST-Common, line 137
		respData = [status: "ERROR", // library marker davegut.ST-Common, line 138
					httpCode: resp.status, // library marker davegut.ST-Common, line 139
					errorMsg: resp.errorMessage] // library marker davegut.ST-Common, line 140
	} else { // library marker davegut.ST-Common, line 141
		try { // library marker davegut.ST-Common, line 142
			respData = new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Common, line 143
		} catch (err) { // library marker davegut.ST-Common, line 144
			respData = [status: "ERROR", // library marker davegut.ST-Common, line 145
						errorMsg: err, // library marker davegut.ST-Common, line 146
						respData: resp.data] // library marker davegut.ST-Common, line 147
		} // library marker davegut.ST-Common, line 148
	} // library marker davegut.ST-Common, line 149
	if (respData.status == "ERROR") { // library marker davegut.ST-Common, line 150
		logWarn("getDeviceListParse: ${respData}") // library marker davegut.ST-Common, line 151
	} else { // library marker davegut.ST-Common, line 152
		log.info "" // library marker davegut.ST-Common, line 153
		respData.items.each { // library marker davegut.ST-Common, line 154
			log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 155
		} // library marker davegut.ST-Common, line 156
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 157
	} // library marker davegut.ST-Common, line 158
} // library marker davegut.ST-Common, line 159

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 161
	Integer currTime = now() // library marker davegut.ST-Common, line 162
	Integer compTime // library marker davegut.ST-Common, line 163
	try { // library marker davegut.ST-Common, line 164
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 165
	} catch (e) { // library marker davegut.ST-Common, line 166
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 167
	} // library marker davegut.ST-Common, line 168
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 169
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 170
	return timeRemaining // library marker davegut.ST-Common, line 171
} // library marker davegut.ST-Common, line 172

// ~~~~~ end include (1090) davegut.ST-Common ~~~~~

// ~~~~~ start include (1168) davegut.Samsung-HVAC-Sim ~~~~~
library ( // library marker davegut.Samsung-HVAC-Sim, line 1
	name: "Samsung-HVAC-Sim", // library marker davegut.Samsung-HVAC-Sim, line 2
	namespace: "davegut", // library marker davegut.Samsung-HVAC-Sim, line 3
	author: "Dave Gutheinz", // library marker davegut.Samsung-HVAC-Sim, line 4
	description: "ST Samsung AC Simulator", // library marker davegut.Samsung-HVAC-Sim, line 5
	category: "utilities", // library marker davegut.Samsung-HVAC-Sim, line 6
	documentationLink: "" // library marker davegut.Samsung-HVAC-Sim, line 7
) // library marker davegut.Samsung-HVAC-Sim, line 8

def setTemperature(temperature) { // library marker davegut.Samsung-HVAC-Sim, line 10
	state.temperature = temperature.toInteger() // library marker davegut.Samsung-HVAC-Sim, line 11
	poll() // library marker davegut.Samsung-HVAC-Sim, line 12
} // library marker davegut.Samsung-HVAC-Sim, line 13

def testData() { // library marker davegut.Samsung-HVAC-Sim, line 15
	if (!state.fanMode) {  // library marker davegut.Samsung-HVAC-Sim, line 16
		state.switch = "off" // library marker davegut.Samsung-HVAC-Sim, line 17
		state.fanMode = "auto" // library marker davegut.Samsung-HVAC-Sim, line 18
		state.temperature = 55 // library marker davegut.Samsung-HVAC-Sim, line 19
		state.setpoint = 60 // library marker davegut.Samsung-HVAC-Sim, line 20
		state.mode = "auto" // library marker davegut.Samsung-HVAC-Sim, line 21
		state.light = ["Sleep_0", "Light_On", "Volume_Mute"] // library marker davegut.Samsung-HVAC-Sim, line 22
		state.scale = "C" // library marker davegut.Samsung-HVAC-Sim, line 23
	} // library marker davegut.Samsung-HVAC-Sim, line 24
	if (state.scale == "C") { // library marker davegut.Samsung-HVAC-Sim, line 25
		state.minSetpoint = 16 // library marker davegut.Samsung-HVAC-Sim, line 26
		state.maxSetpoint = 30 // library marker davegut.Samsung-HVAC-Sim, line 27
	} else { // library marker davegut.Samsung-HVAC-Sim, line 28
		state.minSetpoint = 60 // library marker davegut.Samsung-HVAC-Sim, line 29
		state.maxSetpoint = 86 // library marker davegut.Samsung-HVAC-Sim, line 30
	} // library marker davegut.Samsung-HVAC-Sim, line 31

	return [ // library marker davegut.Samsung-HVAC-Sim, line 33
			switch:[switch:[value: state.switch]], // library marker davegut.Samsung-HVAC-Sim, line 34
			"custom.thermostatSetpointControl":[ // library marker davegut.Samsung-HVAC-Sim, line 35
				minimumSetpoint:[value: state.minSetpoint], // library marker davegut.Samsung-HVAC-Sim, line 36
				maximumSetpoint:[value: state.maxSetpoint]], // library marker davegut.Samsung-HVAC-Sim, line 37
			airConditionerFanMode:[ // library marker davegut.Samsung-HVAC-Sim, line 38
				supportedAcFanModes:[value:["auto", "low", "medium", "high"]], // library marker davegut.Samsung-HVAC-Sim, line 39
				fanMode:[value: state.fanMode]], // library marker davegut.Samsung-HVAC-Sim, line 40
			temperatureMeasurement:[temperature:[value: state.temperature, unit: state.scale]], // library marker davegut.Samsung-HVAC-Sim, line 41
			thermostatCoolingSetpoint:[coolingSetpoint:[value: state.setpoint, unit: state.scale]], // library marker davegut.Samsung-HVAC-Sim, line 42
			airConditionerMode:[ // library marker davegut.Samsung-HVAC-Sim, line 43
				supportedAcModes:[value:["auto", "cool", "dry", "wind", "heat"]], // library marker davegut.Samsung-HVAC-Sim, line 44
				airConditionerMode:[value: state.mode]], // library marker davegut.Samsung-HVAC-Sim, line 45
			execute:[data:[value:[payload:["x.com.samsung.da.options": state.light]]]] // library marker davegut.Samsung-HVAC-Sim, line 46
			] // library marker davegut.Samsung-HVAC-Sim, line 47
} // library marker davegut.Samsung-HVAC-Sim, line 48

def testResp(cmdData) { // library marker davegut.Samsung-HVAC-Sim, line 50
	def cmd = cmdData.command // library marker davegut.Samsung-HVAC-Sim, line 51
	def args = cmdData.arguments // library marker davegut.Samsung-HVAC-Sim, line 52
	switch(cmd) { // library marker davegut.Samsung-HVAC-Sim, line 53
		case "off": // library marker davegut.Samsung-HVAC-Sim, line 54
			state.switch = "off" // library marker davegut.Samsung-HVAC-Sim, line 55
			state.mode = "off" // library marker davegut.Samsung-HVAC-Sim, line 56
			break // library marker davegut.Samsung-HVAC-Sim, line 57
		case "setAirConditionerMode": // library marker davegut.Samsung-HVAC-Sim, line 58
			state.switch = "on" // library marker davegut.Samsung-HVAC-Sim, line 59
			state.mode = args[0] // library marker davegut.Samsung-HVAC-Sim, line 60
			break // library marker davegut.Samsung-HVAC-Sim, line 61
		case "setFanMode": // library marker davegut.Samsung-HVAC-Sim, line 62
			state.fanMode = args[0] // library marker davegut.Samsung-HVAC-Sim, line 63
			break // library marker davegut.Samsung-HVAC-Sim, line 64
		case "setCoolingSetpoint": // library marker davegut.Samsung-HVAC-Sim, line 65
			state.setpoint = args[0] // library marker davegut.Samsung-HVAC-Sim, line 66
			break // library marker davegut.Samsung-HVAC-Sim, line 67
		case "execute": // library marker davegut.Samsung-HVAC-Sim, line 68
			def onOff = args[0]["mode/vs/0"]["x.com.samsung.da.options"][0] // library marker davegut.Samsung-HVAC-Sim, line 69
			state.light = ["Sleep_0", onOff, "Volume_Mute"] // library marker davegut.Samsung-HVAC-Sim, line 70
			break // library marker davegut.Samsung-HVAC-Sim, line 71
		case "refresh": // library marker davegut.Samsung-HVAC-Sim, line 72
			break // library marker davegut.Samsung-HVAC-Sim, line 73
		default: // library marker davegut.Samsung-HVAC-Sim, line 74
			logWarn("testResp: [unhandled: ${cmdData}]") // library marker davegut.Samsung-HVAC-Sim, line 75
	} // library marker davegut.Samsung-HVAC-Sim, line 76

	return [ // library marker davegut.Samsung-HVAC-Sim, line 78
		cmdData: cmdData, // library marker davegut.Samsung-HVAC-Sim, line 79
		status: [status: "OK", // library marker davegut.Samsung-HVAC-Sim, line 80
				 results:[[id: "e9585885-3848-4fea-b0db-ece30ff1701e", status: "ACCEPTED"]]]] // library marker davegut.Samsung-HVAC-Sim, line 81
} // library marker davegut.Samsung-HVAC-Sim, line 82

// ~~~~~ end include (1168) davegut.Samsung-HVAC-Sim ~~~~~
