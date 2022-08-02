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
=====	Version B0.5
Beta release for further user testiong
a.	Update major commands, as follows:
	1.	setThermostatMode now has all Samsung HVAC modes plus mode samsungAuto.  samsungAuto mode
		is different than auto. auto mode emulates standard hubitat auto mode where the mode is auto
		controlled by temperature as it goes above "coolingSetpoint" and below "heatingSetpoint".
		samsungAuto uses the native HVAC auto mode with a single setpoint (samsungAutoSetpoint).
	2.	setCoolingSetpoint and setHeatingSetpoint.  When setting a calculation will be made to assue
		that colling setpoint > heatingSetpoint plus the preference "min Heating/Cooling delta".
		This precludes unsuccessful operation while in auto mode.
	3.	setSamsungAutoSetpoint.  Sets the single setpoint when you set the mode to the HVAC-
		internal auto mode.
	4.	setLight: new command to set the panel light on or off.
b.	Developed standard thermostat auto mode emulation.
c.	Developed methods to track and control the resultant thermostat modes and operating states.
==============================================================================*/
def driverVer() { return "B0.51" }
metadata {
	definition (name: "Samsung HVAC",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungAppliances/Samsung_HVAC.groovy"
			   ){
		capability "Refresh"
		capability "Thermostat"
		//	Augmented Thermostat Mode Control.  Commands added to
		//	maintain capabilty interface paradigm.  Emergency Heat not avail.
		command "setThermostatMode", [[
			name: "Thermostat Mode",
			constraints: ["off", "auto", "cool", , "heat", "dry", "wind", "samsungAuto"],
			type: "ENUM"]]
		command "emergencyHeat", [[name: "NOT IMPLEMENTED"]]
		command "samsungAuto"
		command "wind"
		command "dry"
		//	Augmented Thermostat Fan Mode Control.  Commands added to
		//	matain capability interface paradigm.
		command "setThermostatFanMode", [[
			name: "Thermostat Fan Mode",
			constraints: ["auto", "low", "medium", "high"],
			type: "ENUM"]]
		command "fanLow"
		command "fanMedium"
		command "fanHigh"
		command "setSamsungAutoSetpoint", ["number"]
		attribute "samsungAutoSetpoint", "number"
		//	Set the light on the remote control.
		command "setLight", [[
			name: "Display On/Off",
			constraints: ["Light_On", "Light_Off"],
			type: "ENUM"]]
		attribute "lightStatus", "string"

		command "setTemperature", ["number"]
	}
	preferences {
		input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "")
		if (stApiKey) {
			input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "")
		}
		input ("tempOffset", "number", title: "Min Heat/Cool temperature delta",
				   defaultValue: 4)
		input ("pollInterval", "enum", title: "Poll Interval (minutes)",
			   options: ["1", "5", "10", "30"], defaultValue: "1")
		input ("debugLog", "bool",  
			   title: "Enable debug logging for 30 minutes", defaultValue: false)
	}
}

def installed() {}

def updated() {
	def commonStatus = commonUpdate()
	if (commonStatus.status == "FAILED") {
		logWarn("updated: ${commonStatus}")
	} else {
		logInfo("updated: ${commonStatus}")
	}
	deviceSetup()
}

def auto() { setThermostatMode("auto") }
def cool() { setThermostatMode("cool") }
def heat() { setThermostatMode("heat") }
def wind() { setThermostatMode("wind") }
def dry() { setThermostatMode("dry") }
def samsungAuto() { setThermostatMode("samsungAuto") }
def emergencyHeat() { logInfo("emergencyHeat: Not Available on this device") }
def off() {
	def cmdData = [
		component: "main",
		capability: "switch",
		command: "off",
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("off: [cmd: ${onOff}, ${cmdStatus}]")
}
def setThermostatMode(thermostatMode) {
	def cmdStatus
	def prevMode = device.currentValue("thermostatMode")
	if (thermostatMode == "auto") {
		state.autoMode = true
		cmdStatus = [status: "OK", mode: "Auto Emulation"]
		poll()
	} else {
		state.autoMode = false
		if (thermostatMode == "samsungAuto") {
			thermostatMode = "auto"
		}
		cmdStatus = sendModeCommand(thermostatMode)
	}
	logInfo("setThermostatMode: [cmd: ${thermostatMode}, ${cmdStatus}]")
}
def sendModeCommand(thermostatMode) {
	def cmdData = [
		component: "main",
		capability: "airConditionerMode",
		command: "setAirConditionerMode",
		arguments: [thermostatMode]]
	cmdStatus = deviceCommand(cmdData)
	return cmdStatus
}

def fanAuto() { setThermostatFanMode("auto") }
def fanCirculate() { setThermostatFanMode("medium") }
def fanOn() { setThermostatFanMode("medium") }
def fanLow() { setThermostatFanMode("low") }
def fanMedium() { setThermostatFanMode("medium") }
def fanHigh() { setThermostatFanMode("high") }
def setThermostatFanMode(fanMode) {
	def cmdData = [
		component: "main",
		capability: "airConditionerFanMode",
		command: "setFanMode",
		arguments: [fanMode]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setThermostatFanMode: [cmd: ${fanMode}, ${cmdStatus}]")
}

def setHeatingSetpoint(setpoint) {
	if (setpoint < state.minSetpoint || setpoint > state.maxSetpoint) {
		logWarn("setHeatingSetpoint: Setpoint out of range")
		return
	}
	def logData = [:]
	def offset = tempOffset
	if (offset < 0) { offset = -offset }
	if (state.tempUnit == "°F") {
		setpoint = setpoint.toInteger()
		offset = offset.toInteger()		
	}

	if (device.currentValue("heatingSetpoint") != setpoint) {
		sendEvent(name: "heatingSetpoint", value: setpoint, unit: state.tempUnit)
		logData << [heatingSetpoint: setpoint]
	}

	def minSetpoint = setpoint + offset
	if (minSetpoint > device.currentValue("coolingSetpoint")) {
		sendEvent(name: "coolingSetpoint", value: minSetpoint, unit: state.tempUnit)
		logData << [coolingSetpoint: minSetpoint]
	}
	
	runIn(1, updateOperation)
	if (logData != [:]) {
		logInfo("setHeatingSetpoint: ${logData}")
	}
}
def setCoolingSetpoint(setpoint) {
	if (setpoint < state.minSetpoint || setpoint > state.maxSetpoint) {
		logWarn("setCoolingSetpoint: Setpoint out of range")
		return
	}
	def logData = [:]
	def offset = tempOffset
	if (offset < 0) { offset = -offset }
	if (state.tempUnit == "°F") {
		setpoint = setpoint.toInteger()
		offset = offset.toInteger()		
	}

	if (device.currentValue("coolingSetpoint") != setpoint) {
		sendEvent(name: "coolingSetpoint", value: setpoint, unit: state.tempUnit)
		logData << [coolingSetpoint: setpoint]
	}

	def maxSetpoint = setpoint - 4
	if (maxSetpoint < device.currentValue("heatingSetpoint")) {
		sendEvent(name: "heatingSetpoint", value: maxSetpoint, unit: state.tempUnit)
		logData << [heatingSetpoint: maxSetpoint]
	}
	
	runIn(1, updateOperation)
	if (logData != [:]) {
		logInfo("setCoolingSetpoint: ${logData}")
	}
}
def setSamsungAutoSetpoint(setpoint) {
	if (setpoint < state.minSetpoint || setpoint > state.maxSetpoint) {
		logWarn("setSamsungAutoSetpoint: Setpoint out of range")
		return
	}
	if (state.tempUnit == "°F") {
		setpoint = setpoint.toInteger()
	}
	def logData = [:]
	if (device.currentValue("samsungAutoSetpoint") != setpoint) {
		sendEvent(name: "samsungAutoSetpoint", value: setpoint, unit: state.tempUnit)
		logData << [samsungAutoSetpoint: setpoint]
		if (samsungAuto && device.currentValue("thermostatMode") == "auto") {
			setThermostatSetpoint(setpoint)
		}
	}
	
	runIn(1, updateOperation)
	if (logData != [:]) {
		logInfo("setSamsungAutoSetpoint: ${logData}")
	}
}
def setThermostatSetpoint(setpoint) {
	def cmdData = [
		component: "main",
		capability: "thermostatCoolingSetpoint",
		command: "setCoolingSetpoint",
		arguments: [setpoint]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setThermostatSetpoint: [cmd: ${setpoint}, ${cmdStatus}]")
}

def setLight(onOff) {
	def cmdData = [
		component: "main",
		capability: "execute",
		command: "execute",
		arguments: ["mode/vs/0":[
			"x.com.samsung.da.options":[
				onOff
			]]]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setLight [cmd: ${onOff}, ${cmdStatus}]")

/*{
  "commands": [
    {
      "component": "main",
      "capability": "execute",
      "command": "execute",
      "arguments": [
        "mode/vs/0",
          {
             "x.com.samsung.da.options":[
                "Light_On"
             ]
          }
       ]
    }
  ]
}*/

}

def distResp(resp, data) {
	def respLog = [:]
	if (resp.status == 200) {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
			if (data.reason == "deviceSetup") {
				deviceSetupParse(respData.components.main)
			}
			statusParse(respData.components.main)
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

def deviceSetupParse(parseData) {
	def logData = [:]

	tempUnit = parseData.temperatureMeasurement.temperature.unit
	state.tempUnit = "°${tempUnit}"
	logData << [tempUnit: tempUnit]

	def supportedThermostatModes = parseData.airConditionerMode.supportedAcModes.value
	supportedThermostatModes << "samsungAuto"
	supportedThermostatModes << "off"
	sendEvent(name: "supportedThermostatModes", value: supportedThermostatModes)
	logData << [supportedThermostatModes: supportedThermostatModes]

	def supportedThermostatFanModes = parseData.airConditionerFanMode.supportedAcFanModes.value
	sendEvent(name: "supportedThermostatFanModes", value: supportedThermostatFanModes)
	logData << [supportedThermostatFanModes: supportedThermostatFanModes]

	state.minSetpoint = parseData["custom.thermostatSetpointControl"].minimumSetpoint.value
	state.maxSetpoint = parseData["custom.thermostatSetpointControl"].maximumSetpoint.value 
	
	//	Initialize setpoints if required.
	def coolSetpoint = 76
	def heatSetpoint = 68
	def samsungAutoSetpoint = 72
	if (state.tempUnit == "°C") {
		coolSetpoint = 24
		heatSetpoint = 20
		samsungAutoSetpoint = 22
	}
	if (!device.currentValue("coolingSetpoint")) {
		sendEvent(name: "coolingSetpoint", value: coolSetpoint, unit: state.tempUnit)
	}
	if (!device.currentValue("heatingSetpoint")) {
		sendEvent(name: "heatingSetpoint", value: heatSetpoint, unit: state.tempUnit)
	}
	if (!device.currentValue("samsungAutoSetpoint")) {
		sendEvent(name: "samsungAutoSetpoint", value: samsungAutoSetpoint, unit: state.tempUnit)
	}
	logInfo("deviceSetupParse: ${logData}")
}

def statusParse(parseData) {
	def logData = [:]

	def temperature = parseData.temperatureMeasurement.temperature.value
	if (device.currentValue("temperature") != temperature) {
		sendEvent(name: "temperature", value: temperature, unit: tempUnit)
		logData << [temperature: temperature]
	}

	def thermostatSetpoint = parseData.thermostatCoolingSetpoint.coolingSetpoint.value
	if (device.currentValue("thermostatSetpoint") != thermostatSetpoint) {
		sendEvent(name: "thermostatSetpoint", value: thermostatSetpoint, unit: tempUnit)
		logData << [thermostatSetpoint: thermostatSetpoint]
	}

	def thermostatMode = parseData.airConditionerMode.airConditionerMode.value
	state.rawMode = thermostatMode
	if (state.autoMode) {
		thermostatMode = "auto"
	} else if (thermostatMode != "cool" && thermostatMode != "heat" &&
			   thermostatMode != "wind" && thermostatMode != "dry" &&
			   thermostatMode != "off") {
		thermostatMode = "samsungAuto"
	}
	if (device.currentValue("thermostatMode") != thermostatMode) {
		sendEvent(name: "thermostatMode", value: thermostatMode)
		logData << [thermostatMode: thermostatMode]
	}

	def thermostatFanMode = parseData.airConditionerFanMode.fanMode.value
	if (device.currentValue("thermostatFanMode") != thermostatFanMode) {
		sendEvent(name: "thermostatFanMode", value: thermostatFanMode)
		logData << [thermostatFanMode: thermostatFanMode]
	}
	
	try{
		def execStatus = parseData.execute.data.value.payload["x.com.samsung.da.options"]
		def lightStatus = "Light_On"
		if (execStatus.contains("Light_Off")) { lightStatus = "Light_Off" }
		if (device.currentValue("lightStatus") != lightStatus) {
			sendEvent(name: "lightStatus", value: lightStatus)
			logData << [lightStatus: lightStatus]
		}
	} catch (e) {
		logWarn("statusParse: setLight does not work on this device")
	}
	
	runIn(2, updateOperation)
	if (logData != [:]) { logInfo("statusParse: ${logData}") }
	runIn(4, listAttributes)
}

def updateOperation() {
	def respData = [:]
	def setpoint = device.currentValue("thermostatSetpoint")
	def temperature = device.currentValue("temperature")
	def heatPoint = device.currentValue("heatingSetpoint")
	def coolPoint = device.currentValue("coolingSetpoint")
	def samsungPoint = device.currentValue("samsungAutoSetpoint")
	def mode = device.currentValue("thermostatMode")
	def rawMode = state.rawMode
	def autoMode = state.autoMode

	if (state.autoMode) {
		def opMode
		if (temperature <= heatPoint) {
			opMode = "heat"
		} else if (temperature >= coolSetpoint) {
			opMode = "cool"
		}
		if (rawMode != opMode) {
			def cmdStatus = sendModeCommand(opMode)
			respData << [sendModeCommand: opMode]
			logInfo("updateOperation: ${respData}")
			return
		}
	}

	def newSetpoint = setpoint
	if (rawMode == "cool") {
		newSetpoint = coolPoint
	} else if (rawMode == "heat") {
		newSetpoint = heatPoint
	} else if (mode == "samsungAuto") {
		newSetpoint = samsungPoint
	}
	if (newSetpoint != setpoint) {
		setThermostatSetpoint(newSetpoint)
		respData << [thermostatSetpoint: newSetpoint]
		logInfo("updateOperation: ${respData}")
		return
	}
	
	def opState = "idle"
	if (mode == "off" || mode == "wind" || mode == "dry") {
		opState = mode
	} else if (mode == "samsungAuto") {
		if (temperature - setpoint > 1.5) {
			opState = "cooling"
		} else if (setpoint - temperature > 1.5) {
			opState = "heating"
		}
	} else if (rawMode == "cool") {
		if (temperature - setpoint > 0) {
			opState = "cooling"
		}
	} else if (rawMode == "heat") {
		if (setpoint - temperature > 0) {
			opState = "heating"
		}
	}
	if (device.currentValue("thermostatOperatingState") != opState) {
		sendEvent(name: "thermostatOperatingState", value: opState)
		respData << [thermostatOperatingState: opState]
		logInfo("updateOperation: ${respData}")
	}
}

//	===== Library Integration =====



def simulate() { return false }
//#include davegut.Samsung-AC-Sim

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
			logWarn("asyncGet: [status: error, statusReason: ${error}]") // library marker davegut.ST-Communications, line 23
		} // library marker davegut.ST-Communications, line 24
	} // library marker davegut.ST-Communications, line 25
} // library marker davegut.ST-Communications, line 26

private syncGet(path){ // library marker davegut.ST-Communications, line 28
	def respData = [:] // library marker davegut.ST-Communications, line 29
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 30
		respData << [status: "ERROR", errorMsg: "no stApiKey"] // library marker davegut.ST-Communications, line 31
		logWarn("syncGet: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 32
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
					respData << [status: "FAILED", errorMsg: "httpCode: ${resp.status}"] // library marker davegut.ST-Communications, line 45
					def warnData = [status:"ERROR", // library marker davegut.ST-Communications, line 46
									cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 47
									httpCode: resp.status, // library marker davegut.ST-Communications, line 48
									errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 49
					logWarn("syncGet: ${warnData}") // library marker davegut.ST-Communications, line 50
				} // library marker davegut.ST-Communications, line 51
			} // library marker davegut.ST-Communications, line 52
		} catch (error) { // library marker davegut.ST-Communications, line 53
			respData << [status: "FAILED", errorMsg: "non-HTTP Error"] // library marker davegut.ST-Communications, line 54
			def warnData = [status: "ERROR", // library marker davegut.ST-Communications, line 55
							cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 56
							httpCode: "No Response", // library marker davegut.ST-Communications, line 57
							errorMsg: error] // library marker davegut.ST-Communications, line 58
			logWarn("syncGet: ${warnData}") // library marker davegut.ST-Communications, line 59
		} // library marker davegut.ST-Communications, line 60
	} // library marker davegut.ST-Communications, line 61
	return respData // library marker davegut.ST-Communications, line 62
} // library marker davegut.ST-Communications, line 63

private xxasyncPost(sendData){ // library marker davegut.ST-Communications, line 65
	def respData = [:] // library marker davegut.ST-Communications, line 66
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 67
		respData << [status: "ERROR", errorMsg: "no stApiKey"] // library marker davegut.ST-Communications, line 68
		logWarn("syncPost: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 69
	} else { // library marker davegut.ST-Communications, line 70
		logDebug("syncPost: ${sendData}") // library marker davegut.ST-Communications, line 71
		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 72
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 73
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 74
			path: sendData.path, // library marker davegut.ST-Communications, line 75
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 76
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 77
		] // library marker davegut.ST-Communications, line 78
		try { // library marker davegut.ST-Communications, line 79
			asynchttpPost(sendData.parse, sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 80
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 81
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 82
				} else { // library marker davegut.ST-Communications, line 83
					respData << [status: "FAILED", errorMsg: "httpCode: ${resp.status}"] // library marker davegut.ST-Communications, line 84
					def warnData = [status:"ERROR", // library marker davegut.ST-Communications, line 85
									cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 86
									httpCode: resp.status, // library marker davegut.ST-Communications, line 87
									errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 88
					logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 89
				} // library marker davegut.ST-Communications, line 90
			} // library marker davegut.ST-Communications, line 91
		} catch (error) { // library marker davegut.ST-Communications, line 92
			respData << [status: "FAILED", errorMsg: "non-HTTP Error"] // library marker davegut.ST-Communications, line 93
			def warnData = [status: "ERROR", // library marker davegut.ST-Communications, line 94
							cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 95
							httpCode: "No Response", // library marker davegut.ST-Communications, line 96
							errorMsg: error] // library marker davegut.ST-Communications, line 97
			logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 98
		} // library marker davegut.ST-Communications, line 99
	} // library marker davegut.ST-Communications, line 100
	return respData // library marker davegut.ST-Communications, line 101
} // library marker davegut.ST-Communications, line 102

private syncPost(sendData){ // library marker davegut.ST-Communications, line 104
	def respData = [:] // library marker davegut.ST-Communications, line 105
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 106
		respData << [status: "ERROR", errorMsg: "no stApiKey"] // library marker davegut.ST-Communications, line 107
		logWarn("syncPost: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 108
	} else { // library marker davegut.ST-Communications, line 109
		logDebug("syncPost: ${sendData}") // library marker davegut.ST-Communications, line 110

		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 112
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 113
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 114
			path: sendData.path, // library marker davegut.ST-Communications, line 115
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 116
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 117
		] // library marker davegut.ST-Communications, line 118
		try { // library marker davegut.ST-Communications, line 119
			httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 120
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 121
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 122
				} else { // library marker davegut.ST-Communications, line 123
					respData << [status: "FAILED", errorMsg: "httpCode: ${resp.status}"] // library marker davegut.ST-Communications, line 124
					def warnData = [status:"ERROR", // library marker davegut.ST-Communications, line 125
									cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 126
									httpCode: resp.status, // library marker davegut.ST-Communications, line 127
									errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 128
					logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 129
				} // library marker davegut.ST-Communications, line 130
			} // library marker davegut.ST-Communications, line 131
		} catch (error) { // library marker davegut.ST-Communications, line 132
			respData << [status: "FAILED", errorMsg: "non-HTTP Error"] // library marker davegut.ST-Communications, line 133
			def warnData = [status: "ERROR", // library marker davegut.ST-Communications, line 134
							cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 135
							httpCode: "No Response", // library marker davegut.ST-Communications, line 136
							errorMsg: error] // library marker davegut.ST-Communications, line 137
			logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 138
		} // library marker davegut.ST-Communications, line 139
	} // library marker davegut.ST-Communications, line 140
	return respData // library marker davegut.ST-Communications, line 141
} // library marker davegut.ST-Communications, line 142

private xxsyncPost(sendData){ // library marker davegut.ST-Communications, line 144
	def respData = [:] // library marker davegut.ST-Communications, line 145
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 146
		respData << [status: "ERROR", errorMsg: "no stApiKey"] // library marker davegut.ST-Communications, line 147
		logWarn("syncPost: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 148
	} else { // library marker davegut.ST-Communications, line 149
		logDebug("syncPost: ${sendData}") // library marker davegut.ST-Communications, line 150
		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 151
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 152
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 153
			path: sendData.path, // library marker davegut.ST-Communications, line 154
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 155
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 156
		] // library marker davegut.ST-Communications, line 157
		try { // library marker davegut.ST-Communications, line 158
			httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 159
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 160
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 161
				} else { // library marker davegut.ST-Communications, line 162
					respData << [status: "FAILED", errorMsg: "httpCode: ${resp.status}"] // library marker davegut.ST-Communications, line 163
					def warnData = [status:"ERROR", // library marker davegut.ST-Communications, line 164
									cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 165
									httpCode: resp.status, // library marker davegut.ST-Communications, line 166
									errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 167
					logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 168
				} // library marker davegut.ST-Communications, line 169
			} // library marker davegut.ST-Communications, line 170
		} catch (error) { // library marker davegut.ST-Communications, line 171
			respData << [status: "FAILED", errorMsg: "non-HTTP Error"] // library marker davegut.ST-Communications, line 172
			def warnData = [status: "ERROR", // library marker davegut.ST-Communications, line 173
							cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 174
							httpCode: "No Response", // library marker davegut.ST-Communications, line 175
							errorMsg: error] // library marker davegut.ST-Communications, line 176
			logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 177
		} // library marker davegut.ST-Communications, line 178
	} // library marker davegut.ST-Communications, line 179
	return respData // library marker davegut.ST-Communications, line 180
} // library marker davegut.ST-Communications, line 181

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
	def cmdResp = [cmdData: cmdData] // library marker davegut.ST-Common, line 50
	def respData // library marker davegut.ST-Common, line 51
	if (simulate() == true) { // library marker davegut.ST-Common, line 52
		respData = testResp(cmdData) // library marker davegut.ST-Common, line 53
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 54
		respData << "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 55
	} else { // library marker davegut.ST-Common, line 56
		def sendData = [ // library marker davegut.ST-Common, line 57
			path: "/devices/${stDeviceId.trim()}/commands", // library marker davegut.ST-Common, line 58
			cmdData: cmdData // library marker davegut.ST-Common, line 59
		] // library marker davegut.ST-Common, line 60
		respData = syncPost(sendData) // library marker davegut.ST-Common, line 61
	} // library marker davegut.ST-Common, line 62
	cmdResp << [respData: respData] // library marker davegut.ST-Common, line 63
	if (respData.status == "FAILED") { // library marker davegut.ST-Common, line 64
		logWarn("deviceCommand: ${cmdResp}") // library marker davegut.ST-Common, line 65
	} else { // library marker davegut.ST-Common, line 66
		logDebug("deviceCommand: ${cmdResp}") // library marker davegut.ST-Common, line 67
		if (cmdData.capability != "refresh") { // library marker davegut.ST-Common, line 68
			refresh() // library marker davegut.ST-Common, line 69
		} else { // library marker davegut.ST-Common, line 70
			poll() // library marker davegut.ST-Common, line 71
		} // library marker davegut.ST-Common, line 72
	} // library marker davegut.ST-Common, line 73
	return respData // library marker davegut.ST-Common, line 74
} // library marker davegut.ST-Common, line 75

def refresh() {  // library marker davegut.ST-Common, line 77
	def cmdData = [ // library marker davegut.ST-Common, line 78
		component: "main", // library marker davegut.ST-Common, line 79
		capability: "refresh", // library marker davegut.ST-Common, line 80
		command: "refresh", // library marker davegut.ST-Common, line 81
		arguments: []] // library marker davegut.ST-Common, line 82
	deviceCommand(cmdData) // library marker davegut.ST-Common, line 83
} // library marker davegut.ST-Common, line 84

def poll() { // library marker davegut.ST-Common, line 86
	if (simulate() == true) { // library marker davegut.ST-Common, line 87
		def children = getChildDevices() // library marker davegut.ST-Common, line 88
		if (children) { // library marker davegut.ST-Common, line 89
			children.each { // library marker davegut.ST-Common, line 90
				it.statusParse(testData()) // library marker davegut.ST-Common, line 91
			} // library marker davegut.ST-Common, line 92
		} // library marker davegut.ST-Common, line 93
		statusParse(testData()) // library marker davegut.ST-Common, line 94
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 95
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 96
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 97
	} else { // library marker davegut.ST-Common, line 98
		def sendData = [ // library marker davegut.ST-Common, line 99
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 100
			parse: "distResp" // library marker davegut.ST-Common, line 101
			] // library marker davegut.ST-Common, line 102
		asyncGet(sendData, "statusParse") // library marker davegut.ST-Common, line 103
	} // library marker davegut.ST-Common, line 104
} // library marker davegut.ST-Common, line 105

def deviceSetup() { // library marker davegut.ST-Common, line 107
	if (simulate() == true) { // library marker davegut.ST-Common, line 108
		def children = getChildDevices() // library marker davegut.ST-Common, line 109
		if (children) { // library marker davegut.ST-Common, line 110
			children.each { // library marker davegut.ST-Common, line 111
				it.deviceSetupParse(testData()) // library marker davegut.ST-Common, line 112
			} // library marker davegut.ST-Common, line 113
		} // library marker davegut.ST-Common, line 114
		deviceSetupParse(testData()) // library marker davegut.ST-Common, line 115
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 116
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 117
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 118
	} else { // library marker davegut.ST-Common, line 119
		def sendData = [ // library marker davegut.ST-Common, line 120
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 121
			parse: "distResp" // library marker davegut.ST-Common, line 122
			] // library marker davegut.ST-Common, line 123
		asyncGet(sendData, "deviceSetup") // library marker davegut.ST-Common, line 124
	} // library marker davegut.ST-Common, line 125
} // library marker davegut.ST-Common, line 126

def getDeviceList() { // library marker davegut.ST-Common, line 128
	def sendData = [ // library marker davegut.ST-Common, line 129
		path: "/devices", // library marker davegut.ST-Common, line 130
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 131
		] // library marker davegut.ST-Common, line 132
	asyncGet(sendData) // library marker davegut.ST-Common, line 133
} // library marker davegut.ST-Common, line 134

def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 136
	def respData // library marker davegut.ST-Common, line 137
	if (resp.status != 200) { // library marker davegut.ST-Common, line 138
		respData = [status: "ERROR", // library marker davegut.ST-Common, line 139
					httpCode: resp.status, // library marker davegut.ST-Common, line 140
					errorMsg: resp.errorMessage] // library marker davegut.ST-Common, line 141
	} else { // library marker davegut.ST-Common, line 142
		try { // library marker davegut.ST-Common, line 143
			respData = new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Common, line 144
		} catch (err) { // library marker davegut.ST-Common, line 145
			respData = [status: "ERROR", // library marker davegut.ST-Common, line 146
						errorMsg: err, // library marker davegut.ST-Common, line 147
						respData: resp.data] // library marker davegut.ST-Common, line 148
		} // library marker davegut.ST-Common, line 149
	} // library marker davegut.ST-Common, line 150
	if (respData.status == "ERROR") { // library marker davegut.ST-Common, line 151
		logWarn("getDeviceListParse: ${respData}") // library marker davegut.ST-Common, line 152
	} else { // library marker davegut.ST-Common, line 153
		log.info "" // library marker davegut.ST-Common, line 154
		respData.items.each { // library marker davegut.ST-Common, line 155
			log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 156
		} // library marker davegut.ST-Common, line 157
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 158
	} // library marker davegut.ST-Common, line 159
} // library marker davegut.ST-Common, line 160

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 162
	Integer currTime = now() // library marker davegut.ST-Common, line 163
	Integer compTime // library marker davegut.ST-Common, line 164
	try { // library marker davegut.ST-Common, line 165
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 166
	} catch (e) { // library marker davegut.ST-Common, line 167
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 168
	} // library marker davegut.ST-Common, line 169
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 170
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 171
	return timeRemaining // library marker davegut.ST-Common, line 172
} // library marker davegut.ST-Common, line 173

// ~~~~~ end include (1000) davegut.ST-Common ~~~~~
