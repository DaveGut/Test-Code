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
1.2.7 = MX release
a.	Use Hubitat built-in methods for converting temperatures.
b.	When setting one of the Hubitat setpoints, run updateOperations vice refresh.
	Note that this called is delayed to avoid multiple commanding as use changes temp on
	interface.
c.	update method updateOperations to streamline and use runIn to slow down process 
	slightly and avoid excessive comms..
d.	Method deviceCommand: if Failed response, do not refresh nor poll the device.
e.	Preference setPollInterval: Change to add 5 minutes.  Default 1 minute.

==============================================================================*/
def driverVer() { return "1.2.9" }

metadata {
	definition (name: "Samsung HVAC",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungAppliances/Samsung_HVAC.groovy"
			   ){
		capability "Refresh"
		capability "Thermostat"
		attribute "switch", "string"
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
//		command "testTemp", ["NUMBER"]
//		command "aSetThermostat", ["NUMBER"]	//	Test conversion algorithms.
//		command "aSetHVACScale", [
//			[name: "Test Scale", constraints: ["C", "F"],
//			 type: "ENUM"]]
//		command "aSetTemp", ["NUMBER"]	//	Simulate house temp
//		command "aSetHVACSetpoint", ["NUMBER"]	//	Simulate change in HVAC setpoint from remote.

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
			input ("pollInterval", "enum", title: "Poll Interval",
				   options: ["10sec", "20sec", "30sec", "1 min", "5min"], defaultValue: "1 min")
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
def modSetpointAttr(attr, toScale) {
	def attrData = device.currentState(attr)
	def setpoint = attrData.value.toFloat()
	if (attrData.unit != toScale) {
		setpoint = convertTemp(setpoint, attrData.unit, toScale)
		sendEvent(name: attr, value: setpoint, unit: toScale)
	} else {
		setpoint = "noChange"
	}
	return setpoint
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
	logInfo("setModeCommand: [cmd: ${thermostatMode}, ${cmdStatus}]")
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
	def hubMin = getDataValue("minSetpoint").toFloat()
	def hubMax = getDataValue("maxSetpoint").toFloat()
	if (tempUnit != tempScale) {
		hubMin = convertTemp(hubMin, tempUnit, tempScale)
		hubMax = convertTemp(hubMax, tempUnit, tempScale)
	}
	if (spType == "heat") {
		hubMax = hubMax - tempOffset.toInteger()
	} else if (spType == "cool") {
		hubMin = hubMin + tempOffset.toInteger()
	}
	if (setpoint < hubMin || setpoint > hubMax) {
		def errData = [error: "setpoint out-of-range", tempUnit: tempUnit,
					   setpoint: setpoint, hubMin: hubMin, hubMax: hubMax]
		return errData
	} else {
		return "OK"
	}
}
def setHeatingSetpoint(setpoint) {
	def logData = [:]
	if (tempScale == "°F") {
		setpoint = (0.5 + setpoint).toInteger()
	}
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
	runIn(10, updateOperation)
	logInfo("setHeatingSetpoint: ${logData}")
}
def setCoolingSetpoint(setpoint) {
	def logData = [:]
	if (tempScale == "°F") {
		setpoint = (0.5 + setpoint).toInteger()
	}
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
	runIn(10, updateOperation)
	logInfo("setCoolingSetpoint: ${logData}")
}
def setSamsungAutoSetpoint(setpoint) {
	def logData = [:]
	if (tempScale == "°F") {
		setpoint = (0.5 + setpoint).toInteger()
	}
	def check = checkSetpoint(setpoint, "samsungAuto")
	if (check == "OK") {
		sendEvent(name: "samsungAutoSetpoint", value: setpoint, unit: tempScale)
		sendEvent(name: "level", value: setpoint, unit: tempScale)
		logData << [samsungAutoSetpoint: setpoint]
	} else {
		logData << [checkSetpoint: check]
	}
	sendEvent(name: "thermostatSetpoint", value: thermostatSetpoint, unit: tempScale)
	sendEvent(name: "level", value: thermostatSetpoint, unit: tempScale)
	runIn(10, updateOperation)
	logInfo("setSamsungAutoSetpoint: ${logData}")
}
def setLevel(setpoint) { 
	if (tempScale == "°F") {
		setpoint = (0.5 + setpoint).toInteger()
	}
	setSamsungAutoSetpoint(setpoint) 
}

//	===== Control Device Setpoint =====
def setThermostatSetpoint(setpoint) {
	def logData = [:]
	def tempUnit = getDataValue("tempUnit")
	if (tempScale != tempUnit) {
		setpoint = convertTemp(setpoint, tempScale, tempUnit)
	}
	def cmdData = [
		component: "main",
		capability: "thermostatCoolingSetpoint",
		command: "setCoolingSetpoint",
		arguments: [setpoint]]
	def cmdStatus = deviceCommand(cmdData)
	logData << [setpoint: setpoint, cmdStatus: cmdStatus]
	logInfo("setThermostatSetpoint: ${logData}")
}

//	===== Thermostat Operational Control =====
def updateOperation() {
	def respData = [:]
	def setpoint = device.currentValue("thermostatSetpoint")
	def temperature = device.currentValue("temperature")
	def heatPoint = device.currentValue("heatingSetpoint")
	def coolPoint = device.currentValue("coolingSetpoint")
	def mode = device.currentValue("thermostatMode")

	def newHvacSetpoint = setpoint
	def opState = "idle"
	switch(mode) {
		//	In each case, set newHvacSetpoint for later check/update as req
		case "samsungAuto":
		//	set opState based on 3 deg total "comfortZone" (pure guess)
			if (temperature - setpoint > 1.5) {
				opState = "cooling"
			} else if (setpoint - temperature > 1.5) {
				opState = "heating"
			}
			newHvacSetpoint = device.currentValue("samsungAutoSetpoint")
			break
		case "cool":
			if (temperature > setpoint) {
				opState = "cooling"
			}
			newHvacSetpoint = coolPoint
			break
		case "heat":
			if (temperature < setpoint) {
				opState = "heating"
			}
			newHvacSetpoint = heatPoint
			break
		case "auto":
			def opMode
			if (temperature < heatPoint) {
				opMode = "heat"
				opState = "heating"
				newHvacSetpoint = heatPoint
			} else if (temperature > coolPoint) {
				opMode = "cool"
				opState = "cooling"
				newHvacSetpoint = coolPoint
			}
			if (state.hvac_mode != opMode) {
				//	set hvac to hub-determined opMode
				def hvacMode = sendModeCommand(opMode)
				respData << [sendModeCommand: opMode, cmdStatus: hvacMode]
			}
			break
		default:
			opState = mode
	}
	if (device.currentValue("thermostatOperatingState") != opState) {
		sendEvent(name: "thermostatOperatingState", value: opState)
		respData << [thermostatOperatingState: opState]
	}
	if (setpoint != newHvacSetpoint) {
		respData << [newHvacSetpoint: newHvacSetpoint]
		runIn(10, setThermostatSetpoint, [data: newHvacSetpoint])
	}
	if (respData != [:]) { logInfo("updateOperations: ${respData}") }
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
						errorMsg: err]
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
	def tempUnit = parseData.temperatureMeasurement.temperature.unit
	tempUnit = "°${tempUnit}"
	if (tempUnit != getDataValue("tempUnit")) {
		logWarn("statusParse: updating tempUnit from ${getDataValue("tempUnit")} to ${tempUnit}")
		updateDataValue("tempUnit", tempUnit)
		logData << [tempUnit: tempUnit]
		def minSetpoint = parseData["custom.thermostatSetpointControl"].minimumSetpoint.value.toInteger()
		updateDataValue("minSetpoint", minSetpoint.toString())
		logData << [minSetpoint: minSetpoint]
		def maxSetpoint = parseData["custom.thermostatSetpointControl"].maximumSetpoint.value.toInteger()
		updateDataValue("maxSetpoint", maxSetpoint.toString())
		logData << [maxSetpoint: maxSetpoint]
	}

	def temperature = parseData.temperatureMeasurement.temperature.value
	def thermostatSetpoint = parseData.thermostatCoolingSetpoint.coolingSetpoint.value
	if (tempUnit != tempScale) {
		temperature = convertTemp(temperature, tempUnit, tempScale)
		thermostatSetpoint = convertTemp(thermostatSetpoint, tempUnit, tempScale)
	}
	if (device.currentValue("temperature") != temperature) {
		sendEvent(name: "temperature", value: temperature, unit: tempScale)
		logData << [temperature: temperature]
	}
	def onOff = parseData.switch.switch.value
	def thermostatMode = parseData.airConditionerMode.airConditionerMode.value
	sendEvent(name: "switch", value: onOff)
	state.hvac_mode = thermostatMode
	if (state.autoMode) {
		thermostatMode = "auto"
	} else if (onOff == "off") {
		thermostatMode = "off"
	} else if (thermostatMode != "cool" && thermostatMode != "heat" &&
			   thermostatMode != "wind" && thermostatMode != "dry" &&
			   thermostatMode != "off") {
		thermostatMode = "samsungAuto"
	}
	def humidity = parseData.relativeHumidityMeasurement.humidity.value

//	===== detect thermostat setpoint change, then update attributes =====
	if (device.currentValue("thermostatSetpoint") != thermostatSetpoint) {
		//	Update hub-setpoints because if thermostat setpoint changes
		logData << [updateSetpoints: updateSetpoints(thermostatMode, thermostatSetpoint)]
		sendEvent(name: "thermostatSetpoint", value: thermostatSetpoint, unit: tempScale)
		sendEvent(name: "level", value: thermostatSetpoint, unit: tempScale)
		logData << [thermostatSetpoint: thermostatSetpoint, level: thermostatSetpoint]
	}

	if (device.currentValue("humidity") != humidity) {
		sendEvent(name: "humidity", value: humidity, unit: "%")
		logData << [humidity: humidity]
	}

//	===== Mode changes =====
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

	def fanOscillationMode = parseData.fanOscillationMode.fanOscillationMode.value
	if (device.currentValue("fanOscillationMode") != fanOscillationMode) {
		sendEvent(name: "fanOscillationMode", value: fanOscillationMode)
		logData << [fanOscillationMode: fanOscillationMode]
	}

	def dustFilterStatus = parseData["custom.dustFilter"].dustFilterStatus.value
	if (device.currentValue("dustFilterStatus") != dustFilterStatus) {
		sendEvent(name: "dustFilterStatus", value: dustFilterStatus)
		logData << [dustFilterStatus: dustFilterStatus]
	}

	if (logData != [:]) {
		logInfo("statusParse: ${logData}")
	}

	updateOperation()
	if (simulate()) {
		runIn(2, listAttributes, [data: true])
	}
}

def updateSetpoints(mode, setpoint) {
	def coolSp = device.currentValue("coolingSetpoint")
	def heatSp = device.currentValue("heatingSetpoint")
	def samsungSp = device.currentValue("samsungAutoSetpoint")
	def hvac_mode = state.hvac_mode
	//	If a compressor mode, Set based on hubitat-mode or HVAC (raw) mode.
	if (mode == "cool" || hvac_mode == "cool") {
		if (coolSp != setpoint) {
		setCoolingSetpoint(setpoint)
		}
	} else if (mode == "heat" || hvac_mode == "heat") {
		if (heatSp != setpoint) {
			setHeatingSetpoint(setpoint)
		}
	} else if (mode == "samsungAuto" || hvac_mode == "auto") {
		if (samsungSp != setpoint) {
			setSamsungAutoSetpoint(setpoint)
		}
	} else {
		//	If not a compressor mode reset nearest setpoint.
		def midPoint = (coolSp - heatSp)/2
		if (setpoint > midPoint) {
			setCoolingSetpoint(setpoint)
		} else {
			setHeatingSetpoint(setpoint)
		}
	}
}

def convertTemp(temperature, fromScale, toScale) {
	def newTemp = temperature
	if (fromScale == "°C" && toScale == "°F") {
		newTemp = Math.round(celsiusToFahrenheit(temperature))
	} else if (fromScale == "°F" && toScale == "°C") {
		newTemp = Math.round(2 * fahrenheitToCelsius(temperature))/2
	}
	logDebug("convertTemp: [origTemp: ${temperature} ${fromScale}, newTemp, ${newTemp} ${toScale}]")
	return newTemp
}

//	===== Library Integration =====



def simulate() { return false}
//def simulate() { return true}
//#include davegut.Samsung-HVAC-Sim

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
	log.trace "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.commonLogging, line 27
} // library marker davegut.commonLogging, line 28

def logInfo(msg) {  // library marker davegut.commonLogging, line 30
	if (textEnable || infoLog) { // library marker davegut.commonLogging, line 31
		log.info "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.commonLogging, line 32
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
		log.debug "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.commonLogging, line 45
	} // library marker davegut.commonLogging, line 46
} // library marker davegut.commonLogging, line 47

def logWarn(msg) { log.warn "${device.displayName}-${driverVer()}: ${msg}" } // library marker davegut.commonLogging, line 49

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
						 errorMsg: error] // library marker davegut.ST-Communications, line 52
		} // library marker davegut.ST-Communications, line 53
	} // library marker davegut.ST-Communications, line 54
	return respData // library marker davegut.ST-Communications, line 55
} // library marker davegut.ST-Communications, line 56

private syncPost(sendData){ // library marker davegut.ST-Communications, line 58
	def respData = [:] // library marker davegut.ST-Communications, line 59
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 60
		respData << [status: "FAILED", // library marker davegut.ST-Communications, line 61
					 errorMsg: "No stApiKey"] // library marker davegut.ST-Communications, line 62
	} else { // library marker davegut.ST-Communications, line 63
		logDebug("syncPost: ${sendData}") // library marker davegut.ST-Communications, line 64
		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 65
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 66
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 67
			path: sendData.path, // library marker davegut.ST-Communications, line 68
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 69
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 70
		] // library marker davegut.ST-Communications, line 71
		try { // library marker davegut.ST-Communications, line 72
			httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 73
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 74
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 75
				} else { // library marker davegut.ST-Communications, line 76
					respData << [status: "FAILED", // library marker davegut.ST-Communications, line 77
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 78
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 79
				} // library marker davegut.ST-Communications, line 80
			} // library marker davegut.ST-Communications, line 81
		} catch (error) { // library marker davegut.ST-Communications, line 82
			respData << [status: "FAILED", // library marker davegut.ST-Communications, line 83
						 errorMsg: error] // library marker davegut.ST-Communications, line 84
		} // library marker davegut.ST-Communications, line 85
	} // library marker davegut.ST-Communications, line 86
	return respData // library marker davegut.ST-Communications, line 87
} // library marker davegut.ST-Communications, line 88

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
	updateData << [textEnable: textEnable, logEnable: logEnable] // library marker davegut.ST-Common, line 24
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
		case "1" : // library marker davegut.ST-Common, line 50
		case "1min": runEvery1Minute(poll); break // library marker davegut.ST-Common, line 51
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
	if (respData.results.status[0] != "FAILED") { // library marker davegut.ST-Common, line 72
		if (cmdData.capability && cmdData.capability != "refresh") { // library marker davegut.ST-Common, line 73
			refresh() // library marker davegut.ST-Common, line 74
		} else { // library marker davegut.ST-Common, line 75
			poll() // library marker davegut.ST-Common, line 76
		} // library marker davegut.ST-Common, line 77
	} // library marker davegut.ST-Common, line 78
	return respData // library marker davegut.ST-Common, line 79
} // library marker davegut.ST-Common, line 80

def refresh() { // library marker davegut.ST-Common, line 82
	if (stApiKey!= null) { // library marker davegut.ST-Common, line 83
		def cmdData = [ // library marker davegut.ST-Common, line 84
			component: "main", // library marker davegut.ST-Common, line 85
			capability: "refresh", // library marker davegut.ST-Common, line 86
			command: "refresh", // library marker davegut.ST-Common, line 87
			arguments: []] // library marker davegut.ST-Common, line 88
		deviceCommand(cmdData) // library marker davegut.ST-Common, line 89
	} // library marker davegut.ST-Common, line 90
} // library marker davegut.ST-Common, line 91

def poll() { // library marker davegut.ST-Common, line 93
	if (simulate() == true) { // library marker davegut.ST-Common, line 94
		pauseExecution(200) // library marker davegut.ST-Common, line 95
		def children = getChildDevices() // library marker davegut.ST-Common, line 96
		if (children) { // library marker davegut.ST-Common, line 97
			children.each { // library marker davegut.ST-Common, line 98
				it.statusParse(testData()) // library marker davegut.ST-Common, line 99
			} // library marker davegut.ST-Common, line 100
		} // library marker davegut.ST-Common, line 101
		statusParse(testData()) // library marker davegut.ST-Common, line 102
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 103
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 104
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 105
	} else { // library marker davegut.ST-Common, line 106
		def sendData = [ // library marker davegut.ST-Common, line 107
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 108
			parse: "distResp" // library marker davegut.ST-Common, line 109
			] // library marker davegut.ST-Common, line 110
		asyncGet(sendData, "statusParse") // library marker davegut.ST-Common, line 111
	} // library marker davegut.ST-Common, line 112
} // library marker davegut.ST-Common, line 113

def deviceSetup() { // library marker davegut.ST-Common, line 115
	if (simulate() == true) { // library marker davegut.ST-Common, line 116
		def children = getChildDevices() // library marker davegut.ST-Common, line 117
		deviceSetupParse(testData()) // library marker davegut.ST-Common, line 118
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 119
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 120
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 121
	} else { // library marker davegut.ST-Common, line 122
		def sendData = [ // library marker davegut.ST-Common, line 123
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 124
			parse: "distResp" // library marker davegut.ST-Common, line 125
			] // library marker davegut.ST-Common, line 126
		asyncGet(sendData, "deviceSetup") // library marker davegut.ST-Common, line 127
	} // library marker davegut.ST-Common, line 128
} // library marker davegut.ST-Common, line 129

def getDeviceList() { // library marker davegut.ST-Common, line 131
	def sendData = [ // library marker davegut.ST-Common, line 132
		path: "/devices", // library marker davegut.ST-Common, line 133
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 134
		] // library marker davegut.ST-Common, line 135
	asyncGet(sendData) // library marker davegut.ST-Common, line 136
} // library marker davegut.ST-Common, line 137

def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 139
	def respData // library marker davegut.ST-Common, line 140
	if (resp.status != 200) { // library marker davegut.ST-Common, line 141
		respData = [status: "ERROR", // library marker davegut.ST-Common, line 142
					httpCode: resp.status, // library marker davegut.ST-Common, line 143
					errorMsg: resp.errorMessage] // library marker davegut.ST-Common, line 144
	} else { // library marker davegut.ST-Common, line 145
		try { // library marker davegut.ST-Common, line 146
			respData = new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Common, line 147
		} catch (err) { // library marker davegut.ST-Common, line 148
			respData = [status: "ERROR", // library marker davegut.ST-Common, line 149
						errorMsg: err, // library marker davegut.ST-Common, line 150
						respData: resp.data] // library marker davegut.ST-Common, line 151
		} // library marker davegut.ST-Common, line 152
	} // library marker davegut.ST-Common, line 153
	if (respData.status == "ERROR") { // library marker davegut.ST-Common, line 154
		logWarn("getDeviceListParse: ${respData}") // library marker davegut.ST-Common, line 155
	} else { // library marker davegut.ST-Common, line 156
		log.info "" // library marker davegut.ST-Common, line 157
		respData.items.each { // library marker davegut.ST-Common, line 158
			log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 159
		} // library marker davegut.ST-Common, line 160
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 161
	} // library marker davegut.ST-Common, line 162
} // library marker davegut.ST-Common, line 163

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 165
	Integer currTime = now() // library marker davegut.ST-Common, line 166
	Integer compTime // library marker davegut.ST-Common, line 167
	try { // library marker davegut.ST-Common, line 168
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 169
	} catch (e) { // library marker davegut.ST-Common, line 170
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 171
	} // library marker davegut.ST-Common, line 172
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 173
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 174
	return timeRemaining // library marker davegut.ST-Common, line 175
} // library marker davegut.ST-Common, line 176

// ~~~~~ end include (1090) davegut.ST-Common ~~~~~
