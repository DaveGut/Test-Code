/*	===== HUBITAT Samsung HVAC Using SmartThings ==========================================
Changes in rev 0.6
Adjusted design.  I am adjusting the design to work explicitly as your AC unit works.  This
will work with hubitat; however, there may be some limitations.  We can address these after
getting the base control working. (The thermostat mode was leading to a complex design that
would be hard to verify/test).
1.	Remove capability "Thermostat".
2.	Add capabilities TemperatureMeasurement, ThremostatFanMode, ThermostatMode, 
	ThermostatCoolingSetpoint (Note: ThermostatCoolingSetpoint matches the ST design for 
	the device. The attribute thermostatCoolingSetpoint will be used for temperature control
	in all thermostat modes.)
3.	Modify command setThermostatFanMode to add high, medium, and low to selection to
	match the device's capabilities.
4.	Modify command setThermostatMode to add dry and wind to selection to
	match the device's capabilities.

Testing:
Open Log Window
Run command Z Test
	This command will collect current device state, change the states to explicit values,
	(three runs), then restore to the current state.  Logs are how I will determine operation.
Send logs.

===========================================================================================*/
import groovy.json.JsonSlurper
def driverVer() { return "0.5" }

def testData() {
	return [components:[
		main:[
			switch:[switch:[value: "on"]],
			airConditionerFanMode:[
				fanMode:[value: "auto"],
				supportedAcFanModes:[value:["auto", "low", "medium", "high"]]],
			temperatureMeasurement:[temperature:[value:"22", unit: "C"]],
			thermostatCoolingSetpoint:[coolingSetpoint:[value: "22.0", unit: "C"]],
			airConditionerMode:[
				supportedAcModes:[value:["auto", "cool", "dry", "wind", "heat"]],
				airConditionerMode:[value: "heat"]],
			"custom.autoCleaningMode":[autoCleaningMode:[value: "on"]],
			"custom.dustFilter":[dustFilterStatus:[value: "normal"]]
		]]]
}
def testResp(cmdData) {
	return [
		cmdData: cmdData,
		status: [status: "OK",
				 results:[[id: "e9585885-3848-4fea-b0db-ece30ff1701e", status: "ACCEPTED"]]]]
}

metadata {
	definition (name: "Samsung HVAC",
				namespace: "davegut",
				author: "David Gutheinz",
				//	import URL will be added later.  Provides short-cut for downloading
				//	updates/new code using the editors IMPORT function (at top).
				importUrl: ""
			   ){
		capability "Refresh"
		capability "Switch"
		capability "TemperatureMeasurement"
		capability "ThermostatCoolingSetpoint"
		capability "ThermostatFanMode"
		command "setThermostatFanMode", [[
			name: "Fan Mode",
			constraints: ["on", "circulate", "auto", "high", "medium", "low"],
			type: "ENUM"]]
		capability "ThermostatMode"
		command "setThermostatMode", [[
			name: "Thermostat Mode",
			constraints: ["auto", "off", "heat", "emergency heat", "cool", "dry", "wind"],
			type: "ENUM"]]
		attribute "autoCleaningMode", "string"
		attribute "dustFilterStatus", "string"
		command "getDeviceList"
		//	Test commands for data collection
		command "zTest"
	}
	preferences {
		input ("simulate", "bool", title: "Simulation Mode", defalutValue: false)
		input ("circSpeed", "enum", title: "Fan Circulate Speed",
			   defaultValue: "medium", options: ["low", "medium", "high"])
		input ("debugLog", "bool",  
			   title: "Enable debug logging for 30 minutes", defaultValue: false)
		input ("infoLog", "bool",  
			   title: "Enable description text logging", defaultValue: true)
		input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "")
		input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "")
	}
}

//	Prior to running test, open a log page.  Wnen complete, send log.
def zTest() {
	//	Disable info and debug logging
	//	Note.  For setting updates (like below), the settings will not
	//	take effect while running the current method (or chained methods).
	//	I use a runIn to execute the command.  This allows the new
	//	setting to be in effect.
	device.updateSetting("infoLog", [type:"bool", value: false])
	device.updateSetting("debugLog", [type:"bool", value: false])
	runIn(2, execTest)
}

def execTest() {
	def curMode = device.currentValue("thermostatMode")
	def fanMode = device.currentValue("thermostatFanMode")
	def curTempSetting = device.currentValue("coolingSetpoint")
	//	Run 1
	poll()
	pauseExecution(2000)
	log.trace "<b>test:[mode: auto, fanMode: auto, temp: 22]</b>"
	auto()
	pauseExecution(2000)
	setThermostatFanMode("auto")
	pauseExecution(2000)
	setCoolingSetpoint(22)
	pauseExecution(5000)
	//	Run 2
	poll()
	pauseExecution(2000)
	log.trace "<b>test:[mode: heat, fanMode: medium, temp: 17]</b>"
	heat()
	pauseExecution(2000)
	setThermostatFanMode("medium")
	pauseExecution(2000)
	setCoolingSetpoint(17)
	pauseExecution(5000)
	//	Run 3
	poll()
	pauseExecution(2000)
	log.trace "<b>test:[mode: cool, fanMode: low, temp: 22]</b>"
	cool()
	pauseExecution(2000)
	setThermostatFanMode("low")
	pauseExecution(2000)
	setCoolingSetpoint(22)
	pauseExecution(5000)
	//	Restore original settings
	poll()
	pauseExecution(2000)
	log.trace "<b>resetDefault:[mode: ${curMode}, fanMode: ${fanMode}, temp: ${curTempSetting}]</b>"
	setThermostatMode(curMode)
	pauseExecution(2000)
	setThermostatFanMode(fanMode)
	pauseExecution(2000)
	setCoolingSetpoint(curTempSetting)
	pauseExecution(5000)
	poll()
}

def installed() {
}

def updated() {
	def commonStatus = commonUpdate()
	logInfo("updated: ${commonStatus}")
	if (simulate == true) {
		updateDataValue("driverVersion", "SIMULATE")
	}
}

def commonUpdate() {
	unschedule()
	def updateData = [:]
	def status = "OK"
	def statusReason = ""
	def deviceData
	if (!stApiKey || stApiKey == "") {
		status = "failed"
		statusReason = "No stApiKey"
	} else if (!stDeviceId || stDeviceId == "") {
		status = "failed"
		statusReason = "No stDeviceId"
	} else {
		if (debugLog) { runIn(1800, debugLogOff) }
		updateData << [stDeviceId: stDeviceId]
		updateData << [debugLog: debugLog, infoLog: infoLog]
		if (!getDataValue("driverVersion") || 
			getDataValue("driverVersion") != driverVer()) {
			updateDataValue("driverVersion", driverVer())
			updateData << [driverVer: driverVer()]
		}
		runIn(10, refresh)
	}
	def updateStatus = [:]
	updateStatus << [status: status]
	if (statusReason != "") {
		updateStatus << [statusReason: statusReason]
	}
	updateStatus << [updateData: updateData]
	return updateStatus
}

def refresh() { 
	def cmdData = [
		component: "main",
		capability: "refresh",
		command: "refresh",
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("refresh: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

//	Capability Switch
def on() { setOnOff("on") }
def off() { setOnOff("off") }
def setOnOff(onOff) {
	def cmdData = [
		component: "main",
		capability: "switch",
		command: onOff,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setOnOff: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

//	Capability Thermostat
//	thermostat modes
def auto() { setThermostatMode("auto") }
def cool() { setThermostatMode("cool") }
def heat() { setThermostatMode("heat") }
def emergencyHeat() {
	logWarn("emergencyHeat: not available. Setting Thermostat to heat")
	setThermostatMode("heat")
}
def setThermostatMode(thermostatMode) {
// Modify to thermostatMode "off".
	if (thermostatMode == "off") {
		off()
	} else {
		def cmdData = [
			component: "main",
			capability: "airConditionerMode",
			command: "setAirConditionerMode",
			arguments: [thermostatMode]]
		def cmdStatus = deviceCommand(cmdData)
		logInfo("setThermostatMode: [cmdData: ${cmdData}, status: ${cmdStatus}]")
	}
}

def fanAuto() { setThermostatFanMode("auto") }
def fanCirculate() { setThermostatFanMode("circulate") }
def fanOn() { setThermostatFanMode("on") }
def setThermostatFanMode(fanMode) {
	//	supportedAcFanModes:[value:["auto", "low", "medium", "high"]]
	//	Modified metadata to add above to default on, auto, circulate.
	//	Handle on and circulate in respect to available modes from device.
	if (fanMode == "circulate") {
		fanMode = circSpeed
	} else if (fanMode == "on") {
		fanMode = "auto"
	}
	def cmdData = [
		component: "main",
		capability: "airConditionerFanMode",
		command: "setFanMode",
		arguments: [fanMode]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setThermostatFanMode: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

//	Cooling Setpoint (used for all setpoints)
def setCoolingSetpoint(setpoint) {
	def cmdData = [
		component: "main",
		capability: "thermostatCoolingSetpoint",
		command: "setCoolingSetpoint",
		arguments: [setpoint]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setCoolingSetpoint: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

def deviceCommand(cmdData) {
	if (simulate == true) {
		respData = testResp(cmdData)
	} else if (!stDeviceId || stDeviceId.trim() == "") {
		respData = "[status: FAILED, data: no stDeviceId]"
		logWarn("deviceCommand: [status: ERROR, errorMsg: no stDeviceId]")
	} else {
		def sendData = [
			path: "/devices/${stDeviceId.trim()}/commands",
			cmdData: cmdData
		]
		respData = syncPost(sendData)
	}
	runIn(1, poll)
	return respData
}

def poll() {
	if (simulate == true) {
		statusParse(testData().components.main)
	} else if (!stDeviceId || stDeviceId.trim() == "") {
		respData = "[status: FAILED, data: no stDeviceId]"
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]")
	} else {
		def sendData = [
			path: "/devices/${stDeviceId.trim()}/status",
			parse: "deviceStatusParse"
			]
		asyncGet(sendData)
	}
}

//	Device Status Parse Method
def deviceStatusParse(resp, data) {
	//	Fixed to generate logWarn as specified in design.
	def respLog = [:]
	if (resp.status == 200) {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
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
		logWarn("deviceStatusParse: ${respLog}")
	}
}
def statusParse(parseData) {
	setAttribute("switch", parseData.switch.switch.value)

	//	Try loops will eventually go away.  Needed to detect errors against actual device.
	def tempUnit
	try {
		tempUnit = parseData.temperatureMeasurement.temperature.unit
		def temp = Double.parseDouble(parseData.temperatureMeasurement.temperature.value)
		setAttribute("temperature", temp, tempUnit)
	} catch (e) {
		logWarn("statusParse: [temperature: ${e}]")
	}

	try {
		setAttribute("thermostatMode", 
					 parseData.airConditionerMode.airConditionerMode.value)
	} catch (e) {
		logWarn("statusParse: [thermostatMode: ${e}]")
	}

	try {
		def temp = Double.parseDouble(parseData.thermostatCoolingSetpoint.coolingSetpoint.value)
		setAttribute("coolingSetpoint", temp,tempUnit)
	} catch (e) {
		logWarn("statusParse: [coolingSetpoint: ${e}]")
	}

	try {
		setAttribute("thermostatFanMode", 
					 parseData.airConditionerFanMode.fanMode.value)
	} catch (e) {
		logWarn("statusParse: [thermostatFanMode: ${e}]")
	}

	try {
		setAttribute("dustFilterStatus", 
					 parseData["custom.dustFilter"].dustFilterStatus.value)
	} catch (e) {
		logWarn("statusParse: [dustFilterStatus: ${e}]")
	}

	try {
		setAttribute("autoCleaningMode", 
					 parseData["custom.autoCleaningMode"].autoCleaningMode.value)
	} catch (e) {
		logWarn("statusParse: [autoCleaningMode: ${e}]")
	}

	runIn(1, listAttributes)
}

def setAttribute(attrName, attrValue, tempUnit = null) {
	if (device.currentValue(attrName) != attrValue) {
		if (tempUnit == null) {
			sendEvent(name: attrName, value: attrValue)
		} else {
			sendEvent(name: attrName, value: attrValue, unit: tempUnit)
		}
		logInfo("setAttribute: [${attrName}: ${attrValue}]")
	}
}
	
//	get the device list
def getDeviceList() {
	def sendData = [
		path: "/devices",
		parse: "getDeviceListParse"
		]
	asyncGet(sendData)
}
def getDeviceListParse(resp, data) {
	def respData
	if (resp.status != 200) {
		respData = [status: "ERROR",
					httpCode: resp.status,
					errorMsg: resp.errorMessage]
	} else {
		try {
			respData = new JsonSlurper().parseText(resp.data)
		} catch (err) {
			respData = [status: "ERROR",
						errorMsg: err,
						respData: resp.data]
		}
	}
	if (respData.status == "ERROR") {
		logWarn("getDeviceListParse: ${respData}")
	} else {
		log.info ""
		respData.items.each {
			log.trace "${it.label}:   ${it.deviceId}"
		}
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>"
	}
}

//	===== Library Integration =====
private asyncGet(sendData) {
	if (!stApiKey || stApiKey.trim() == "") {
		logWarn("asyncGet: [status: ERROR, errorMsg: no stApiKey]")
	} else {
		logDebug("asyncGet: ${sendData}")
		def sendCmdParams = [
			uri: "https://api.smartthings.com/v1",
			path: sendData.path,
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()]]
		try {
			asynchttpGet(sendData.parse, sendCmdParams)
		} catch (error) {
			logWarn("asyncGet: [status: error, statusReason: ${error}]")
		}
	}
}

private syncPost(sendData){
	def respData = [:]
	if (!stApiKey || stApiKey.trim() == "") {
		respData << [status: "ERROR", errorMsg: "no stApiKey"]
		logWarn("syncPost: [status: ERROR, errorMsg: no stApiKey]")
	} else {
		logDebug("syncPost: ${sendData}")
		def cmdBody = [commands: [sendData.cmdData]]
		def sendCmdParams = [
			uri: "https://api.smartthings.com/v1",
			path: sendData.path,
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()],
			body : new groovy.json.JsonBuilder(cmdBody).toString()
		]
		try {
			httpPost(sendCmdParams) {resp ->
				if (resp.status == 200 && resp.data != null) {
					respData << [status: "OK", results: resp.data.results]
				} else {
					respData << [status:"ERROR",
								 cmdBody: cmdBody,
								 httpCode: resp.status,
								 errorMsg: resp.errorMessage]
				}
			}
		} catch (error) {
			respData << [status: "ERROR",
						 cmdBody: cmdBody,
						 httpCode: "No Response",
						 errorMsg: error]
		}
	}
	return respData
}

def listAttributes() {
	def attrs = device.getSupportedAttributes()
	def attrList = [:]
	attrs.each {
		def val = device.currentValue("${it}")
		attrList << ["${it}": val]
	}
	logTrace("Attributes: ${attrList}")
}

def logTrace(msg){
	log.trace "${device.displayName} ${getDataValue("driverVersion")}: ${msg}"
}

def logInfo(msg) { 
	if (infoLog == true) {
		log.info "${device.displayName} ${getDataValue("driverVersion")}: ${msg}"
	}
}

def debugLogOff() {
	device.updateSetting("debugLog", [type:"bool", value: false])
	logInfo("Debug logging is false.")
}

def logDebug(msg) {
	if (debugLog == true) {
		log.debug "${device.displayName} ${getDataValue("driverVersion")}: ${msg}"
	}
}

def logWarn(msg) { log.warn "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" }
