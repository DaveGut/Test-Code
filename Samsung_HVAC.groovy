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
1.2.1 = MX release to fix US version.
==============================================================================*/
def driverVer() { return "1.2.6" }

metadata {
	definition (name: "Samsung HVAC",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungAppliances/Samsung_HVAC.groovy"
			   ){
		capability "Refresh"
		capability "Thermostat"
		command "setThermostatMode", [[
			name: "Thermostat Mode",
			constraints: ["off", "auto", "cool", "heat", 
						  "dry", "wind", "samsungAuto"],
			type: "ENUM"]]
		command "emergencyHeat", [[name: "NOT IMPLEMENTED"]]
		command "samsungAuto"
		command "wind"
		command "dry"
		command "setSamsungAutoSetpoint", ["number"]
		attribute "samsungAutoSetpoint", "number"
		command "setLevel", ["number"]		//	To set samsungAutoSetpoint via slider
		attribute "level", "number"			//	Reflects samsungAutoSetpoint
		command "setThermostatFanMode", [[
			name: "Thermostat Fan Mode",
			constraints: ["auto", "low", "medium", "high"],
			type: "ENUM"]]
		command "fanLow"
		command "fanMedium"
		command "fanHigh"
		command "setOptionalMode", [[
			name: "Thermostat Optional Mode",
			constraints: ["off", "sleep", "quiet", "smart", 
						  "speed", "windFree", "windFreeSleep"],
			type: "ENUM"]]
		attribute "acOptionalMode", "string"
		command "setOscillationMode", [[
			name: "Fan Oscillation Mode",
			constraints: ["fixed", "all", "vertical", "horizontal"],
			type: "ENUM"]]
		attribute "fanOscillationMode", "string"

		command "setPanelLight", [[
			name: "Panel Light On/Off",
			constraints: ["on", "off"],
			type: "ENUM"]]
		attribute "lightStatus", "string"
		capability "RelativeHumidityMeasurement"
		attribute "dustFilterStatus", "string"

		//		TEST ONLY
		command "aSetThermostat", ["NUMBER"]	//	Test conversion algorithms.
		command "aSetHVACScale", [
			[name: "Test Scale", constraints: ["C", "F"],
			 type: "ENUM"]]
		command "aSetTemp", ["NUMBER"]	//	Simulate house temp

	}
	preferences {
		input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "")
		if (stApiKey) {
			input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "")
		}
		if (stDeviceId) {
			input ("tempScale", "enum", options: ["°C", "°F"],
				   title: "Hub Temperature Scale", defaultValue: "°C")
			input ("tempOffset", "number", title: "Min Heat/Cool temperature delta",
					   defaultValue: 4)
			input ("pollInterval", "enum", title: "Poll Interval (minutes)",
				   options: ["1", "5", "10", "30"], defaultValue: "1")
			input ("textEnable", "bool", 
				   title: "Enable descriptionText logging",
				   defaultValue: true)
			input ("logEnable", "bool",
			   title: "Enable debug logging",
			   defaultValue: false)
			input ("config", "bool", title: "Reset device configuration", defaultValue: false)
		}
	}
}

def installed() {
	device.updateSetting("tempScale", [type:"enum", value: "°C"])
	device.updateSetting("tempOffset", [type:"number", value: 4])
	device.updateSetting("config", [type: "bool", value: true])
}
def updated() {
	def commonStatus = commonUpdate()
	if (commonStatus.status == "FAILED") {
		log.warn "updated: ${commonStatus}"
	} else {
		log.info "updated: ${commonStatus}"
		if (config) { deviceSetup()	}
		runIn(2, changeScale)
	}
	pauseExecution(5000)
}
def changeScale() {
	def logData = [tempScale: tempScale]
	logData << [coolingSetpoint: modSetpointAttr("coolingSetpoint", tempScale)]
	logData << [heatingSetpoint: modSetpointAttr("heatingSetpoint", tempScale)]
	logData << [samsungAutoSetpoint: modSetpointAttr("samsungAutoSetpoint", tempScale)]

	if (simulate()) {
		setSimStates()
	}
	logInfo("changeScale: ${logData}")
}

//	===== Thermostat Mode Control =====
def auto() { setThermostatMode("auto") }
def cool() { setThermostatMode("cool") }
def heat() { setThermostatMode("heat") }
def wind() { setThermostatMode("wind") }
def dry() { setThermostatMode("dry") }
def samsungAuto() { setThermostatMode("samsungAuto") }
def emergencyHeat() { logInfo("emergencyHeat: Not Available on this device") }
def on() { setOn() }
def off() { setThermostatMode("off") }
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
def setThermostatMode(thermostatMode) {
	def cmdStatus
	def prevMode = device.currentValue("thermostatMode")
	if (thermostatMode == "auto") {
		state.autoMode = true
		cmdStatus = [status: "OK", mode: "Auto Emulation"]
		poll()
	} else if (thermostatMode == "off") {
		state.autoMode = false
		cmdStatus = setOff()
	} else {
		state.autoMode = false
		if (thermostatMode == "samsungAuto") {
			thermostatMode = "auto"
		}
		cmdStatus = setOn()
		logInfo("setOn: [cmd: on, ${cmdStatus}]")
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

//	===== Thermostat Fan Control =====
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

def setOptionalMode(opMode) {
	def cmdData = [
		component: "main",
		capability: "custom.airConditionerOptionalMode",
		command: "setAcOptionalMode",
		arguments: [opMode]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setOptionalMode: [cmd: ${opMode}, ${cmdStatus}]")
}

def setOscillationMode(oscMode) {
	def cmdData = [
		component: "main",
		capability: "fanOscillationMode",
		command: "setFanOscillationMode",
		arguments: [oscMode]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setOscillationMode: [cmd: ${oscMode}, ${cmdStatus}]")
}

//	===== Hubitat/Thermostat Setpoint Control
def checkSetpoint(setpoint, spType) {
	def tempUnit = getDataValue("tempUnit")
	def hubMin = convertTemp(getDataValue("minSetpoint").toInteger(), tempUnit, tempScale)
	def hubMax = convertTemp(getDataValue("maxSetpoint").toInteger(), tempUnit, tempScale)
	if (spType == "heat") {
		hubMax = hubMax - tempOffset.toInteger()
	} else if (spType == "cool") {
		hubMin = hubMin + tempOffset.toInteger()
	}
	if (setpoint < hubMin || setpoint > hubMax) {
		return "setpoint out-of-range"
	} else {
		return "OK"
	}
}
def setHeatingSetpoint(setpoint) {
	def logData = [:]
	def check = checkSetpoint(setpoint, "heat")
	if (check == "OK") {
		sendEvent(name: "heatingSetpoint", value: setpoint, unit: tempScale)
		logData << [heatingSetpoint: setpoint]
		def minCoolSetpoint = setpoint + tempOffset.toInteger()
		if (device.currentValue("coolingSetpoint") < minCoolSetpoint) {
			sendEvent(name: "coolingSetpoint", value: minCoolSetpoint, unit: tempScale)
			logData << [coolingSetpoint: minCoolSetpoint]
		}
	} else {
		logData << [checkSetpoint: check]
	}
	runIn(1, updateOperation)
	logInfo("setHeatingSetpoint: ${logData}")
}
def setCoolingSetpoint(setpoint) {
	def logData = [:]
	def check = checkSetpoint(setpoint, "cool")
	if (check == "OK") {
		sendEvent(name: "coolingSetpoint", value: setpoint, unit: tempScale)
		logData << [coolingSetpoint: setpoint]
		def maxHeatSetpoint = setpoint - tempOffset.toInteger()
		if (device.currentValue("heatingSetpoint") > maxHeatSetpoint) {
			sendEvent(name: "heatingSetpoint", value: maxHeatSetpoint, unit: tempScale)
			logData << [heatingSetpoint: maxHeatSetpoint]
		}
	} else {
		logData << [checkSetpoint: check]
	}
	runIn(1, updateOperation)
	logInfo("setHeatingSetpoint: ${logData}")
}
def setSamsungAutoSetpoint(setpoint) {
	def logData = [:]
	def check = checkSetpoint(setpoint, "")
	if (check == "OK") {
		sendEvent(name: "samsungAutoSetpoint", value: setpoint, unit: tempScale)
		sendEvent(name: "level", value: setpoint, unit: tempScale)
		logData << [samsungAutoSetpoint: setpoint]
		if (samsungAuto && device.currentValue("thermostatMode") == "auto") {
			setThermostatSetpoint(setpoint)
		}
	} else {
		logData << [checkSetpoint: check]
	}
	runIn(1, updateOperation)
	logInfo("setSamsungAutoSetpoint: ${logData}")
}
def setLevel(setpoint) { 
	def logData = [:]
	def hubMin = convertTemp(getDataValue("minSetpoint").toInteger(), tempUnit, tempScale)
	def hubMax = convertTemp(getDataValue("maxSetpoint").toInteger(), tempUnit, tempScale)
	if (setpoint < hubMin) {
		setpoint = hubMin
		logData << [error: "setpoint below range", 
					action: "setpoint set to ${hubMin}"]
	} else if (setpoint > hubMax) {
		setpoint = hubMax
		logData << [error: "setpoint below range", 
					action: "setpoint set to ${hubMax}"]
	} else { 
		logData << [setpoint: setpoint] 
	}
	logInfo("setLevel: ${logData}")
	setSamsungAutoSetpoint(setpoint) 
}

//	===== Control Device Setpoint =====
def setThermostatSetpoint(setpoint) {
	def logData = [:]
	def tempUnit = getDataValue("tempUnit")
	def adjSetpoint = convertTemp(setpoint, tempScale, tempUnit)
	if (adjSetpoint < getDataValue("minSetpoint").toFloat() || 
		adjSetpoint > getDataValue("maxSetpoint").toFloat()) {
		logData << [setpoint: setpoint, adjSetpoint: adjSetpoint, error: "adjSetpoint out-of-range."]
	} else {
		def cmdData = [
			component: "main",
			capability: "thermostatCoolingSetpoint",
			command: "setCoolingSetpoint",
			arguments: [adjSetpoint]]
		def cmdStatus = deviceCommand(cmdData)
		logData << [setpoint: setpoint, adjSetpoint: adjSetpoint, status: cmdStatus]
	}
	logInfo("setThermostatSetpoint: ${logData}")
}

//	===== Thermostat Operational Control =====
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

//	===== Panel Light =====
def setPanelLight(onOff) {
	def lightCmd = "Light_On"
	if (onOff == "on") {
		lightCmd = "Light_Off"
	}
	def args = ["mode/vs/0",["x.com.samsung.da.options":["${lightCmd}"]]]
	def cmdData = [
		component: "main",
		capability: "execute",
		command: "execute",
		arguments: args]
	def cmdStatus = deviceCommand(cmdData)
	sendEvent(name: "lightStatus", value: onOff)
	logInfo("togglePanelLight: [onOff: ${onOff}, cmd: ${lightCmd}, ${cmdStatus}]")
}

//	===== Response Data Handling =====
def distResp(resp, data) {
	def respLog = [:]
	if (resp.status == 200) {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
			if (data.reason == "deviceSetup") {
				deviceSetupParse(respData.components.main)
			} else {
				statusParse(respData.components.main)
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

def deviceSetupParse(parseData) {
	def logData = [:]

	def supportedThermostatModes = parseData.airConditionerMode.supportedAcModes.value
	supportedThermostatModes << "samsungAuto"
	supportedThermostatModes << "off"
	sendEvent(name: "supportedThermostatModes", value: supportedThermostatModes)
	logData << [supportedThermostatModes: supportedThermostatModes]

	def supportedThermostatFanModes = parseData.airConditionerFanMode.supportedAcFanModes.value
	sendEvent(name: "supportedThermostatFanModes", value: supportedThermostatFanModes)
	logData << [supportedThermostatFanModes: supportedThermostatFanModes]

	def supportedOpModes = parseData["custom.airConditionerOptionalMode"].supportedAcOptionalMode.value
	sendEvent(name: "supportedOpModes", value: supportedOpModes)
	logData << [supportedOpModes: supportedOpModes]

	def supportedOscillationModes = parseData.fanOscillationMode.supportedFanOscillationModes.value
	sendEvent(name: "supportedOscillationModes", value: supportedOscillationModes)
	logData << [supportedOscillationModes: supportedOscillationModes]


	def tempUnit = "°${parseData["custom.thermostatSetpointControl"].minimumSetpoint.unit}"

	def minSetpoint = parseData["custom.thermostatSetpointControl"].minimumSetpoint.value.toInteger()
	updateDataValue("minSetpoint", minSetpoint.toString())
	logData << [minSetpoint: minSetpoint]
	
	def maxSetpoint = parseData["custom.thermostatSetpointControl"].maximumSetpoint.value.toInteger()
	updateDataValue("maxSetpoint", maxSetpoint.toString())
	logData << [maxSetpoint: maxSetpoint]

	def coolingSetpoint = 74
	def heatingSetpoint = 70
	def samsungAutoSetpoint = 72
	if (tempScale == "°C") {
		coolingSetpoint = 24
		heatingSetpoint = 20
		samsungAutoSetpoint = 22
	}
	sendEvent(name: "coolingSetpoint", value: coolingSetpoint, unit: tempScale)
	sendEvent(name: "heatingSetpoint", value: heatingSetpoint, unit: tempScale)
	sendEvent(name: "samsungAutoSetpoint", value: samsungAutoSetpoint, unit: tempScale)
	
	if (simulate()) {
		setSimStates()
	}
	device.updateSetting("config", [type: "bool", value: false])
	if (logData != [:]) {
		logInfo("deviceSetupParse: ${logData}")
	}
}

def statusParse(parseData) {
	def logData = [:]
	def tempUnit = parseData["custom.thermostatSetpointControl"].minimumSetpoint.unit
	tempUnit = "°${tempUnit}"
	if (tempUnit != getDataValue("tempUnit")) {
		//	devices tempUnit has changed.  Will need to update min/max setpoint data and states.
		updateDataValue("tempUnit", tempUnit)
		logData << [tempUnit: tempUnit]

		def minSetpoint = parseData["custom.thermostatSetpointControl"].minimumSetpoint.value.toInteger()
		updateDataValue("minSetpoint", minSetpoint.toString())
		logData << [minSetpoint: minSetpoint]
	
		def maxSetpoint = parseData["custom.thermostatSetpointControl"].maximumSetpoint.value.toInteger()
		updateDataValue("maxSetpoint", maxSetpoint.toString())
		logData << [maxSetpoint: maxSetpoint]
	}
	
	tempUnit = "°${parseData.temperatureMeasurement.temperature.unit}"
	def temperature = parseData.temperatureMeasurement.temperature.value
	temperature = convertTemp(temperature, tempUnit, tempScale)
	if (device.currentValue("temperature") != temperature) {
		sendEvent(name: "temperature", value: temperature, unit: tempScale)
		logData << [temperature: temperature]
	}

	tempUnit = "°${parseData.thermostatCoolingSetpoint.coolingSetpoint.unit}"
	def thermostatSetpoint = parseData.thermostatCoolingSetpoint.coolingSetpoint.value
	thermostatSetpoint = convertTemp(thermostatSetpoint, tempUnit, tempScale)
	if (device.currentValue("thermostatSetpoint") != thermostatSetpoint) {
		sendEvent(name: "thermostatSetpoint", value: thermostatSetpoint, unit: tempScale)
		sendEvent(name: "level", value: thermostatSetpoint, unit: tempScale)
		logData << [thermostatSetpoint: thermostatSetpoint, level: thermostatSetpoint]
	}

	def onOff = parseData.switch.switch.value
	def thermostatMode = parseData.airConditionerMode.airConditionerMode.value
	sendEvent(name: "switch", value: onOff)
	state.rawMode = thermostatMode
	if (state.autoMode) {
		thermostatMode = "auto"
	} else if (onOff == "off") {
		thermostatMode = "off"
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

	def acOptionalMode = parseData["custom.airConditionerOptionalMode"].acOptionalMode.value
	if (device.currentValue("acOptionalMode") != acOptionalMode) {
		sendEvent(name: "acOptionalMode", value: acOptionalMode)
		logData << [acOptionalMode: acOptionalMode]
	}

	def dustFilterStatus = parseData["custom.dustFilter"].dustFilterStatus.value
	if (device.currentValue("dustFilterStatus") != dustFilterStatus) {
		sendEvent(name: "dustFilterStatus", value: dustFilterStatus)
		logData << [dustFilterStatus: dustFilterStatus]
	}

	def humidity = parseData.relativeHumidityMeasurement.humidity.value
	if (device.currentValue("humidity") != humidity) {
		sendEvent(name: "humidity", value: humidity, unit: "%")
		logData << [humidity: humidity]
	}

	def fanOscillationMode = parseData.fanOscillationMode.fanOscillationMode.value
	if (device.currentValue("fanOscillationMode") != fanOscillationMode) {
		sendEvent(name: "fanOscillationMode", value: fanOscillationMode)
		logData << [fanOscillationMode: fanOscillationMode]
	}

	if (logData != [:]) {
		logInfo("statusParse: ${logData}")
	}
	
	runIn(2, updateOperation)
	if (simulate()) {
		runIn(4, listAttributes, [data: true])
	}
}

//	===== Driver Utilities =====
def modSetpointAttr(attr, toScale) {
	def attrData = device.currentState(attr)

	if (attrData.unit != toScale) {
		setpoint = convertTemp(setpoint, attrData.unit, toScale)
		sendEvent(name: attr, value: setpoint, unit: toScale)
	} else {
		setpoint = "noChange"
	}
	return setpoint
}
def convertTemp(temperature, fromScale, toScale) {
	def newTemp = temperature
	if (fromScale == "°C" && toScale == "°F") {
		newTemp = convertCtoF(temperature)
	} else if (fromScale == "°F" && toScale == "°C") {
		newTemp = convertFtoC(temperature)
	}
log.trace "convertTemp: [$temperature, $fromScale, $toScale, $newTemp]"
	return newTemp
}
def convertCtoF(temperature) {
	return (temperature * (9.0 / 5.0) + 32.0).toInteger()
}
def convertFtoC(temperature) {
	return (0.9 + 2 * (temperature - 32.0) * (5.0 / 9.0)).toInteger() / 2
}

//	===== Library Integration =====



//def simulate() { return false}
def simulate() { return true}


// ~~~~~ start include (1170) davegut.commonLogging ~~~~~
library ( // library marker davegut.commonLogging, line 1
	name: "commonLogging", // library marker davegut.commonLogging, line 2
	namespace: "davegut", // library marker davegut.commonLogging, line 3
	author: "Dave Gutheinz", // library marker davegut.commonLogging, line 4
	description: "Common Logging Methods", // library marker davegut.commonLogging, line 5
	category: "utilities", // library marker davegut.commonLogging, line 6
	documentationLink: "" // library marker davegut.commonLogging, line 7
) // library marker davegut.commonLogging, line 8

//	Logging during development // library marker davegut.commonLogging, line 10
def listAttributes(trace = false) { // library marker davegut.commonLogging, line 11
	def attrs = device.getSupportedAttributes() // library marker davegut.commonLogging, line 12
	def attrList = [:] // library marker davegut.commonLogging, line 13
	attrs.each { // library marker davegut.commonLogging, line 14
		def val = device.currentValue("${it}") // library marker davegut.commonLogging, line 15
		attrList << ["${it}": val] // library marker davegut.commonLogging, line 16
	} // library marker davegut.commonLogging, line 17
	if (trace == true) { // library marker davegut.commonLogging, line 18
		logInfo("Attributes: ${attrList}") // library marker davegut.commonLogging, line 19
	} else { // library marker davegut.commonLogging, line 20
		logDebug("Attributes: ${attrList}") // library marker davegut.commonLogging, line 21
	} // library marker davegut.commonLogging, line 22
} // library marker davegut.commonLogging, line 23

//	6.7.2 Change B.  Remove driverVer() // library marker davegut.commonLogging, line 25
def logTrace(msg){ // library marker davegut.commonLogging, line 26
	log.trace "${device.displayName}: ${msg}" // library marker davegut.commonLogging, line 27
} // library marker davegut.commonLogging, line 28

def logInfo(msg) {  // library marker davegut.commonLogging, line 30
	if (textEnable || infoLog) { // library marker davegut.commonLogging, line 31
		log.info "${device.displayName}: ${msg}" // library marker davegut.commonLogging, line 32
	} // library marker davegut.commonLogging, line 33
} // library marker davegut.commonLogging, line 34

def debugLogOff() { // library marker davegut.commonLogging, line 36
	if (logEnable) { // library marker davegut.commonLogging, line 37
		device.updateSetting("logEnable", [type:"bool", value: false]) // library marker davegut.commonLogging, line 38
	} // library marker davegut.commonLogging, line 39
	logInfo("debugLogOff") // library marker davegut.commonLogging, line 40
} // library marker davegut.commonLogging, line 41

def logDebug(msg) { // library marker davegut.commonLogging, line 43
	if (logEnable || debugLog) { // library marker davegut.commonLogging, line 44
		log.debug "${device.displayName}: ${msg}" // library marker davegut.commonLogging, line 45
	} // library marker davegut.commonLogging, line 46
} // library marker davegut.commonLogging, line 47

def logWarn(msg) { log.warn "${device.displayName}: ${msg}" } // library marker davegut.commonLogging, line 49

// ~~~~~ end include (1170) davegut.commonLogging ~~~~~

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
		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 66
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 67
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 68
			path: sendData.path, // library marker davegut.ST-Communications, line 69
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 70
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 71
		] // library marker davegut.ST-Communications, line 72
		try { // library marker davegut.ST-Communications, line 73
			httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 74
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 75
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 76
				} else { // library marker davegut.ST-Communications, line 77
					respData << [status: "FAILED", // library marker davegut.ST-Communications, line 78
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 79
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 80
				} // library marker davegut.ST-Communications, line 81
			} // library marker davegut.ST-Communications, line 82
		} catch (error) { // library marker davegut.ST-Communications, line 83
			respData << [status: "FAILED", // library marker davegut.ST-Communications, line 84
						 httpCode: "Timeout", // library marker davegut.ST-Communications, line 85
						 errorMsg: error] // library marker davegut.ST-Communications, line 86
		} // library marker davegut.ST-Communications, line 87
	} // library marker davegut.ST-Communications, line 88
	return respData // library marker davegut.ST-Communications, line 89
} // library marker davegut.ST-Communications, line 90

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
//	updateData << [debugLog: debugLog, infoLog: infoLog] // library marker davegut.ST-Common, line 24
	updateData << [textEnable: textEnable, logEnable: logEnable] // library marker davegut.ST-Common, line 25
	if (!getDataValue("driverVersion") ||  // library marker davegut.ST-Common, line 26
		getDataValue("driverVersion") != driverVer()) { // library marker davegut.ST-Common, line 27
		updateDataValue("driverVersion", driverVer()) // library marker davegut.ST-Common, line 28
		updateData << [driverVer: driverVer()] // library marker davegut.ST-Common, line 29
	} // library marker davegut.ST-Common, line 30
	setPollInterval(pollInterval) // library marker davegut.ST-Common, line 31
	updateData << [pollInterval: pollInterval] // library marker davegut.ST-Common, line 32

	runIn(5, refresh) // library marker davegut.ST-Common, line 34
	return updateData // library marker davegut.ST-Common, line 35
} // library marker davegut.ST-Common, line 36

def setPollInterval(pollInterval) { // library marker davegut.ST-Common, line 38
	logDebug("setPollInterval: ${pollInterval}") // library marker davegut.ST-Common, line 39
	state.pollInterval = pollInterval // library marker davegut.ST-Common, line 40
	switch(pollInterval) { // library marker davegut.ST-Common, line 41
		case "10sec":  // library marker davegut.ST-Common, line 42
			schedule("*/10 * * * * ?", "poll")		 // library marker davegut.ST-Common, line 43
			break // library marker davegut.ST-Common, line 44
		case "20sec": // library marker davegut.ST-Common, line 45
			schedule("*/20 * * * * ?", "poll")		 // library marker davegut.ST-Common, line 46
			break // library marker davegut.ST-Common, line 47
		case "30sec": // library marker davegut.ST-Common, line 48
			schedule("*/30 * * * * ?", "poll")		 // library marker davegut.ST-Common, line 49
			break // library marker davegut.ST-Common, line 50
		case "1" : runEvery1Minute(poll); break // library marker davegut.ST-Common, line 51
		case "5" : runEvery5Minutes(poll); break // library marker davegut.ST-Common, line 52
		case "10" : runEvery10Minutes(poll); break // library marker davegut.ST-Common, line 53
		case "30" : runEvery30Minutes(poll); break // library marker davegut.ST-Common, line 54
		default: runEvery10Minutes(poll) // library marker davegut.ST-Common, line 55
	} // library marker davegut.ST-Common, line 56
} // library marker davegut.ST-Common, line 57

def deviceCommand(cmdData) { // library marker davegut.ST-Common, line 59
	def respData = [:] // library marker davegut.ST-Common, line 60
	if (simulate() == true) { // library marker davegut.ST-Common, line 61
		respData = testResp(cmdData) // library marker davegut.ST-Common, line 62
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 63
		respData << [status: "FAILED", data: "no stDeviceId"] // library marker davegut.ST-Common, line 64
	} else { // library marker davegut.ST-Common, line 65
		def sendData = [ // library marker davegut.ST-Common, line 66
			path: "/devices/${stDeviceId.trim()}/commands", // library marker davegut.ST-Common, line 67
			cmdData: cmdData // library marker davegut.ST-Common, line 68
		] // library marker davegut.ST-Common, line 69
		respData = syncPost(sendData) // library marker davegut.ST-Common, line 70
	} // library marker davegut.ST-Common, line 71
	if (cmdData.capability && cmdData.capability != "refresh") { // library marker davegut.ST-Common, line 72
		refresh() // library marker davegut.ST-Common, line 73
	} else { // library marker davegut.ST-Common, line 74
		poll() // library marker davegut.ST-Common, line 75
		} // library marker davegut.ST-Common, line 76
	return respData // library marker davegut.ST-Common, line 77
} // library marker davegut.ST-Common, line 78

def refresh() { // library marker davegut.ST-Common, line 80
	if (stApiKey!= null) { // library marker davegut.ST-Common, line 81
		def cmdData = [ // library marker davegut.ST-Common, line 82
			component: "main", // library marker davegut.ST-Common, line 83
			capability: "refresh", // library marker davegut.ST-Common, line 84
			command: "refresh", // library marker davegut.ST-Common, line 85
			arguments: []] // library marker davegut.ST-Common, line 86
		deviceCommand(cmdData) // library marker davegut.ST-Common, line 87
	} // library marker davegut.ST-Common, line 88
} // library marker davegut.ST-Common, line 89

def poll() { // library marker davegut.ST-Common, line 91
	if (simulate() == true) { // library marker davegut.ST-Common, line 92
		def children = getChildDevices() // library marker davegut.ST-Common, line 93
		if (children) { // library marker davegut.ST-Common, line 94
			children.each { // library marker davegut.ST-Common, line 95
				it.statusParse(testData()) // library marker davegut.ST-Common, line 96
			} // library marker davegut.ST-Common, line 97
		} // library marker davegut.ST-Common, line 98
		statusParse(testData()) // library marker davegut.ST-Common, line 99
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 100
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 101
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 102
	} else { // library marker davegut.ST-Common, line 103
		def sendData = [ // library marker davegut.ST-Common, line 104
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 105
			parse: "distResp" // library marker davegut.ST-Common, line 106
			] // library marker davegut.ST-Common, line 107
		asyncGet(sendData, "statusParse") // library marker davegut.ST-Common, line 108
	} // library marker davegut.ST-Common, line 109
} // library marker davegut.ST-Common, line 110

def deviceSetup() { // library marker davegut.ST-Common, line 112
	if (simulate() == true) { // library marker davegut.ST-Common, line 113
		def children = getChildDevices() // library marker davegut.ST-Common, line 114
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

// ~~~~~ end include (1090) davegut.ST-Common ~~~~~

// ~~~~~ start include (1197) davegut.Samsung-HVAC-Sim ~~~~~
library ( // library marker davegut.Samsung-HVAC-Sim, line 1
	name: "Samsung-HVAC-Sim", // library marker davegut.Samsung-HVAC-Sim, line 2
	namespace: "davegut", // library marker davegut.Samsung-HVAC-Sim, line 3
	author: "Dave Gutheinz", // library marker davegut.Samsung-HVAC-Sim, line 4
	description: "ST Samsung AC Simulator", // library marker davegut.Samsung-HVAC-Sim, line 5
	category: "utilities", // library marker davegut.Samsung-HVAC-Sim, line 6
	documentationLink: "" // library marker davegut.Samsung-HVAC-Sim, line 7
) // library marker davegut.Samsung-HVAC-Sim, line 8

def aSetThermostat(setpoint) { // library marker davegut.Samsung-HVAC-Sim, line 10
	setThermostatSetpoint(setpoint) // library marker davegut.Samsung-HVAC-Sim, line 11
} // library marker davegut.Samsung-HVAC-Sim, line 12

def aSetHVACScale(unit) { // library marker davegut.Samsung-HVAC-Sim, line 14
	//	Modify thermostatSetpoint // library marker davegut.Samsung-HVAC-Sim, line 15
	def thermSp = device.currentState("thermostatSetpoint") // library marker davegut.Samsung-HVAC-Sim, line 16
	def unitVal = "°${unit}" // library marker davegut.Samsung-HVAC-Sim, line 17
	def sp = state.setpoint // library marker davegut.Samsung-HVAC-Sim, line 18
	def temp = state.temperature // library marker davegut.Samsung-HVAC-Sim, line 19
	if (getDataValue("tempUnit") != unitVal) { // library marker davegut.Samsung-HVAC-Sim, line 20
		state.setpoint = convertTemp(sp, getDataValue("tempUnit"), unitVal) // library marker davegut.Samsung-HVAC-Sim, line 21
		state.temperature = convertTemp(temp, getDataValue("tempUnit"), unitVal) // library marker davegut.Samsung-HVAC-Sim, line 22
	} // library marker davegut.Samsung-HVAC-Sim, line 23
	state.scale = unit // library marker davegut.Samsung-HVAC-Sim, line 24
	runIn(1, poll) // library marker davegut.Samsung-HVAC-Sim, line 25
} // library marker davegut.Samsung-HVAC-Sim, line 26

def aSetTemp(temperature) { // library marker davegut.Samsung-HVAC-Sim, line 28
	state.temperature = temperature.toInteger() // library marker davegut.Samsung-HVAC-Sim, line 29
	runIn(1, poll) // library marker davegut.Samsung-HVAC-Sim, line 30
} // library marker davegut.Samsung-HVAC-Sim, line 31

def setSimStates() { // library marker davegut.Samsung-HVAC-Sim, line 33
		setpoint = 25 // library marker davegut.Samsung-HVAC-Sim, line 34
		temperature = 15 // library marker davegut.Samsung-HVAC-Sim, line 35
		if (tempUnit == "°F") { // library marker davegut.Samsung-HVAC-Sim, line 36
			setpoint = 77 // library marker davegut.Samsung-HVAC-Sim, line 37
			temperature = 59 // library marker davegut.Samsung-HVAC-Sim, line 38
		} // library marker davegut.Samsung-HVAC-Sim, line 39
		state.setpoint = setpoint // library marker davegut.Samsung-HVAC-Sim, line 40
		state.temperature = temperature // library marker davegut.Samsung-HVAC-Sim, line 41
	logSimStates() // library marker davegut.Samsung-HVAC-Sim, line 42
} // library marker davegut.Samsung-HVAC-Sim, line 43

def logSimStates() { // library marker davegut.Samsung-HVAC-Sim, line 45
	def stateData = [tempUnit: getDataValue("tempUnit"), // library marker davegut.Samsung-HVAC-Sim, line 46
					 tempScale: tempScale, // library marker davegut.Samsung-HVAC-Sim, line 47
					 setpoint: state.setpoint, // library marker davegut.Samsung-HVAC-Sim, line 48
					 temperature: state.temperature] // library marker davegut.Samsung-HVAC-Sim, line 49
	log.trace "logSimStates: $stateData" // library marker davegut.Samsung-HVAC-Sim, line 50
}	 // library marker davegut.Samsung-HVAC-Sim, line 51

def testData() { // library marker davegut.Samsung-HVAC-Sim, line 53
	if (!state.fanMode) {  // library marker davegut.Samsung-HVAC-Sim, line 54
		state.switch = "off" // library marker davegut.Samsung-HVAC-Sim, line 55
		state.fanMode = "auto" // library marker davegut.Samsung-HVAC-Sim, line 56
		state.temperature = 15 // library marker davegut.Samsung-HVAC-Sim, line 57
		state.setpoint = 20 // library marker davegut.Samsung-HVAC-Sim, line 58
		state.mode = "auto" // library marker davegut.Samsung-HVAC-Sim, line 59
		state.scale = "C" // library marker davegut.Samsung-HVAC-Sim, line 60
		state.acOptionalMode = "windFree" // library marker davegut.Samsung-HVAC-Sim, line 61
		state.oscilMode = "fixed" // library marker davegut.Samsung-HVAC-Sim, line 62
	} // library marker davegut.Samsung-HVAC-Sim, line 63
	def minSetpoint = 16 // library marker davegut.Samsung-HVAC-Sim, line 64
	def maxSetpoint = 30 // library marker davegut.Samsung-HVAC-Sim, line 65
	if (state.scale == "F") { // library marker davegut.Samsung-HVAC-Sim, line 66
		minSetpoint = 60 // library marker davegut.Samsung-HVAC-Sim, line 67
		maxSetpoint = 86 // library marker davegut.Samsung-HVAC-Sim, line 68
	} // library marker davegut.Samsung-HVAC-Sim, line 69

	return [ // library marker davegut.Samsung-HVAC-Sim, line 71
		switch:[switch:[value: state.switch]], // library marker davegut.Samsung-HVAC-Sim, line 72
		relativeHumidityMeasurement:[humidity:[value:67]], // library marker davegut.Samsung-HVAC-Sim, line 73
		"custom.dustFilter":[dustFilterStatus:[value:"normal"]], // library marker davegut.Samsung-HVAC-Sim, line 74
		"custom.thermostatSetpointControl":[ // library marker davegut.Samsung-HVAC-Sim, line 75
			minimumSetpoint:[value: minSetpoint, unit: state.scale], // library marker davegut.Samsung-HVAC-Sim, line 76
			maximumSetpoint:[value: maxSetpoint, unit: state.scale]], // library marker davegut.Samsung-HVAC-Sim, line 77
		airConditionerFanMode:[ // library marker davegut.Samsung-HVAC-Sim, line 78
			supportedAcFanModes:[value:["auto", "low", "medium", "high"]], // library marker davegut.Samsung-HVAC-Sim, line 79
			fanMode:[value: state.fanMode]], // library marker davegut.Samsung-HVAC-Sim, line 80
		temperatureMeasurement:[temperature:[value: state.temperature, unit: state.scale]], // library marker davegut.Samsung-HVAC-Sim, line 81
		thermostatCoolingSetpoint:[coolingSetpoint:[value: state.setpoint, unit: state.scale]], // library marker davegut.Samsung-HVAC-Sim, line 82
		"custom.airConditionerOptionalMode":[ // library marker davegut.Samsung-HVAC-Sim, line 83
			supportedAcOptionalMode:[value:["off", "sleep", "quiet", "smart", "speed", "windFree", "windFreeSleep"]], // library marker davegut.Samsung-HVAC-Sim, line 84
			acOptionalMode:[value: state.acOptionalMode]], // library marker davegut.Samsung-HVAC-Sim, line 85
		fanOscillationMode:[ // library marker davegut.Samsung-HVAC-Sim, line 86
			supportedFanOscillationModes:[value:["fixed", "all", "vertical", "horizontal"]],  // library marker davegut.Samsung-HVAC-Sim, line 87
			fanOscillationMode:[value: state.oscilMode]], // library marker davegut.Samsung-HVAC-Sim, line 88
		airConditionerMode:[ // library marker davegut.Samsung-HVAC-Sim, line 89
			supportedAcModes:[value:["auto", "cool", "dry", "wind", "heat"]], // library marker davegut.Samsung-HVAC-Sim, line 90
			airConditionerMode:[value: state.mode]] // library marker davegut.Samsung-HVAC-Sim, line 91
		] // library marker davegut.Samsung-HVAC-Sim, line 92
} // library marker davegut.Samsung-HVAC-Sim, line 93

def testResp(cmdData) { // library marker davegut.Samsung-HVAC-Sim, line 95
	def cmd = cmdData.command // library marker davegut.Samsung-HVAC-Sim, line 96
	def args = cmdData.arguments // library marker davegut.Samsung-HVAC-Sim, line 97
	switch(cmd) { // library marker davegut.Samsung-HVAC-Sim, line 98
		case "off": // library marker davegut.Samsung-HVAC-Sim, line 99
			state.switch = "off" // library marker davegut.Samsung-HVAC-Sim, line 100
			state.mode = "off" // library marker davegut.Samsung-HVAC-Sim, line 101
			break // library marker davegut.Samsung-HVAC-Sim, line 102
		case "on": // library marker davegut.Samsung-HVAC-Sim, line 103
			state.switch = "on" // library marker davegut.Samsung-HVAC-Sim, line 104
			state.mode = "samsungAuto" // library marker davegut.Samsung-HVAC-Sim, line 105
			break // library marker davegut.Samsung-HVAC-Sim, line 106
		case "setAirConditionerMode": // library marker davegut.Samsung-HVAC-Sim, line 107
			state.switch = "on" // library marker davegut.Samsung-HVAC-Sim, line 108
			state.mode = args[0] // library marker davegut.Samsung-HVAC-Sim, line 109
			break // library marker davegut.Samsung-HVAC-Sim, line 110
		case "setFanMode": // library marker davegut.Samsung-HVAC-Sim, line 111
			state.fanMode = args[0] // library marker davegut.Samsung-HVAC-Sim, line 112
			break // library marker davegut.Samsung-HVAC-Sim, line 113
		case "setCoolingSetpoint": // library marker davegut.Samsung-HVAC-Sim, line 114
			state.setpoint = args[0] // library marker davegut.Samsung-HVAC-Sim, line 115
			break // library marker davegut.Samsung-HVAC-Sim, line 116
		case "execute": // library marker davegut.Samsung-HVAC-Sim, line 117
			def onOff = args[1]["x.com.samsung.da.options"][0] // library marker davegut.Samsung-HVAC-Sim, line 118
			state.light = ["Sleep_0", onOff, "Volume_Mute"] // library marker davegut.Samsung-HVAC-Sim, line 119
			break // library marker davegut.Samsung-HVAC-Sim, line 120
		case "setAcOptionalMode": // library marker davegut.Samsung-HVAC-Sim, line 121
			state.acOptionalMode = args[0] // library marker davegut.Samsung-HVAC-Sim, line 122
			break // library marker davegut.Samsung-HVAC-Sim, line 123
		case "setFanOscillationMode": // library marker davegut.Samsung-HVAC-Sim, line 124
			state.oscilMode = args[0] // library marker davegut.Samsung-HVAC-Sim, line 125
			break // library marker davegut.Samsung-HVAC-Sim, line 126
		case "refresh": // library marker davegut.Samsung-HVAC-Sim, line 127
			break // library marker davegut.Samsung-HVAC-Sim, line 128
		default: // library marker davegut.Samsung-HVAC-Sim, line 129
			logWarn("testResp: [unhandled: ${cmdData}]") // library marker davegut.Samsung-HVAC-Sim, line 130
	} // library marker davegut.Samsung-HVAC-Sim, line 131

	return [ // library marker davegut.Samsung-HVAC-Sim, line 133
		cmdData: cmdData, // library marker davegut.Samsung-HVAC-Sim, line 134
		status: [status: "OK", // library marker davegut.Samsung-HVAC-Sim, line 135
				 results:[[id: "e9585885-3848-4fea-b0db-ece30ff1701e", status: "ACCEPTED"]]]] // library marker davegut.Samsung-HVAC-Sim, line 136
} // library marker davegut.Samsung-HVAC-Sim, line 137

// ~~~~~ end include (1197) davegut.Samsung-HVAC-Sim ~~~~~
