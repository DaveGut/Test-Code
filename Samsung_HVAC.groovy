/*	===== HUBITAT Samsung HVAC Using SmartThings ==========================================
Example drivers and apps can be found at:
	https://github.com/hubitat/HubitatPublic

The design will be based upon the SmartThings interface defined at:
	https://developer-preview.smartthings.com/docs/api/public
specifcally using the category (left of page) devices -> devices using
	listDevices to generate a device list for selection of deviceId
	executeCommandsOnADevice for execution of commands
	getTheFullStatusOfADevice
Additionally, from the status data, I get a list of capabilities.  I then can deteermine
available commands / attributes for the ST Capabilities from
	production: https://developer-preview.smartthings.com/docs/devices/capabilities/capabilities-reference
	proposed: https://developer-preview.smartthings.com/docs/devices/capabilities/proposed

Hubitat design will use Hubitat capabilities as well as custom commands and attributes.  For
capabilities, the commands and attributes are defined in:
	https://docs.hubitat.com/index.php?title=Driver_Capability_List

My contribution is to define the best capability set for the device.  Capabilities are the
interface between the Hubitat devices and the various support apps (thermostat scheduler,
rule machine, alexa/google integration) and selecting the right ones will enable better
integration within the Hubiverse.  Below is my set of capabilities for this design.

//	Capability Refresh
	//	Command: refresh - will send refresh to SmartThings then request device status.
//	Capability Switch
	//	Commands: on, off
	//	Attribute: switch
//	Capability Thermostat
	//	Commands:
		//	fanAuto, fanCirculate(medium), fanOn, setThermostatFanMode
		//	setCoolingSetpoint, setHeatingSetpoint
		//	auto, cool, heat, off, setThermostatMode
			//	Note that the ST design does not have a mode of off.  They use the switch
			//	capability.  My design will do the same.  Design will be to send an
			//	off command when the off is selected.  When any other mode is selected,
			//	we will send an ON followed by the Mode Command.  This will make the
			//	interface consistent with the Hubiverse.
	//	Attributes:
		//	supportedThermostatFanModes, thermostatFanMode
		//	temperature
		//	coolingSetpoint, heating setpoint
		//	supportedThermostateModes, thermostatMode
	//	Implementation Notes:
		//	SmartThings Driver is for Air Conditioner.  It therefore misses any control of
		//	Heating setPoint.
			//	Test coolingSetpoint functions to determine if they act as heatingSetpoint
			//	when the device is in heat mode.  May have to revert to NOT using Thermostat.
		//	Will have to design interface for cooling/heating setpoint and use in auto mode.
		//	MIA CMDS: emergencyHeat (just set to heat)
	//	Custom commands/attributes
		//	cmd: autoClean, attribute: autoCleanMode
		//	custom attribute dustFilterStatus

===========================================================================================*/
import groovy.json.JsonSlurper
def driverVer() { return "0.5" }

/*	Since I do not have an actual device, I set up a data simulator from the data you provided
me.  This allows me to test the status parsing.  I can also develop temporary routines to test
certain commands - but mostly I rely on the user to test commands.*/
def testData() {
/*	This data defines the ST component (main) and capabilities (switch, airConditionerFanMode, etc.
	Each capability then has aattributes (i.e., switch) as well current values.  In the design,
	I go to the ST Developers page capabilities to get the available commands for formal
	capabilities (switch is ON and OFF).  for custom capabilities (i.e., custom.autoCleaningMode)
	I have to try to guess the command name and sometimes the values.
*/
	return [components:[
		main:[
			switch:[switch:[value: "on"]],
			airConditionerFanMode:[
				fanMode:[value: "auto"],
				supportedAcFanModes:[value:["auto", "low", "medium", "high"]]],
			temperatureMeasurement:[temperature:[value:"22", unit: "C"]],
			"custom.thermostatSetpointControl":[
				minimumSetpoint:[value: "16.0", unit: "C"],
				maximumSetpoint:[value: "21.0", unit: "C"]],
			thermostatCoolingSetpoint:[coolingSetpoint:[value: "22.0", unit: "C"]],
			airConditionerMode:[
				supportedAcModes:[value:["auto", "cool", "dry", "wind", "heat"]],
				airConditionerMode:[value: "heat"]],
			"custom.autoCleaningMode":[autoCleaningMode:[value: "on"]],
			"custom.dustFilter":[dustFilterStatus:[value: "normal"]]
		]]]
}
def testResp() {
	return [results:
			[
				[
					id: "testResponse",
					status: "ACCEPTED"
				]
			]
		   ]
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
		capability "Thermostat"
		attribute "autoCleaningMode", "string"
		attribute "dustFilterStatus", "string"
		command "getDeviceList"		//	Used as part of the installation process.
//	Per the user document, there is a custom command "custom.thermostatSetpointControl".
//	It has a minimum and maximum temperature range.  On other thermostats, this defines
//	the range for house temperature (low - high) in auto mode.  Not positive here.
//	The below are EXPERIMENTAL COMMANDS.  The experiment will be detailed in the method section.
//	Note, these will probably be moved to the preferences section after testing.
		command "setMinimumSetpoint", ["NUMBER"]
		command "setMaximumSetpoint", ["NUMBER"]
		attribute "setpointRange", "JSON_OBJECT"
	}
	preferences {
		/*	The SmartThings Driver does not support a fan mode of circulate.  Here,
			I will allow the user to define the fan speed for when circulate is selected. */
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

def installed() {
/*	Runs only when device is installed.  Usually contains a hook to updated;
however, since I need a key to continue, nothing here.*/
}

def updated() {
//	Runs every time "savePreferences" is selected from device's edit page.
	def commonStatus = commonUpdate()
	logInfo("updated: ${commonStatus}")
	if (simulate == true) {
		updateDataValue("driverVersion", "SIMULATE")
	}
}

//	Will place in library on next version
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

//	Methods.  Used for exposed commands and for internal processing.
//	I usually place the control interface first followed by the status
//	interface, and then finally the communications interface.
//	===== Control Interface =====
//	Capability Refresh
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
//	Not available.  Set mode to heat if not already.
	logWarn("emergencyHeat: not available. Setting Thermostat to heat")
	setThermostatMode("heat")
}
def setThermostatMode(thermostatMode) {
/*	From the ST Capabilities list, capability is "airConditionerMode", the command
	is "setAirConditionerMode(mode) and the argument is the mode to be set.
	From a use perspective, this is called from heat, cool, auto, or emergencyHeat.
	TESTING:  If the unit is set to off and a mode is selected here, does the unit
	then turn on automatically??????  If not, I will add a switch(on) command prior
	to setting the mode.
	Available modes include those listed in the attribute 
		supportedAcModes:[value:["auto", "cool", "dry", "wind", "heat"]]
*/
	def cmdData = [
		component: "main",
		capability: "airConditionerMode",
		command: "setAirConditionerMode",
		arguments: [thermostatMode]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setThermostatMode: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

//	Fan modes
def fanAuto() { setThermostatFanMode("auto") }
def fanCirculate() {
//	uses the preference value circSpeed.
	setThermostatFanMode(circSpeed)
}
def fanOn() {
//	Not supported on ST Interface.  For simplicity, I will use the same
//	speed as the command above.  We could set up a separage preference - your thoughts.
	setThermostatFanMode(circSpeed)
}
def setThermostatFanMode(fanMode) {
//	See notes on setThermostatMode.  Same general issue.
//	modes: supportedAcFanModes:[value:["auto", "low", "medium", "high"]]
	def cmdData = [
		component: "main",
		capability: "airConditionerFanMode",
		command: "setFanMode",
		arguments: [fanMode]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setThermostatFanMode: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

//	Temperature Control
def setCoolingSetpoint(setpoint) {
//	setCoolingSetpoint Experiment.
//	Seeing if colling set point is used to set mode temperature
//	per one of the Samsung HVAC user manuals.
//	a.	auto() then setCoolingSetpoint(20).  Send log data
//	b.	cool() then setCoolingSetpoint(22).  Send log data
//	c.	heat() then setCoolingSetpoint(15).  Send log data
//	d.	auto(), wait 30s, cool(), wait 30s, heat().  Send log data
	if (device.currentValue("thermostatMode") == "heat") {
		def cmdData = [
			component: "main",
			capability: "thermostatCoolingSetpoint",
			command: "setCoolingSetpoint",
			arguments: [setpoint]]
		def cmdStatus = deviceCommand(cmdData)
		logInfo("setCoolingSetpoint: [cmdData: ${cmdData}, status: ${cmdStatus}]")
	} else {
		logWarn("setCoolingSetpoint: AC Mode must be cool.")
	}
}

def setHeatingSetpoint(setpoint) {
	if (device.currentValue("thermostatMode") == "heat") {
		def cmdData = [
			component: "main",
			capability: "thermostatCoolingSetpoint",
			command: "setCoolingSetpoint",
			arguments: [setpoint]]
		def cmdStatus = deviceCommand(cmdData)
		logInfo("setHeatingSetpoint: [cmdData: ${cmdData}, status: ${cmdStatus}]")
	} else {
		logWarn("setHeatingSetpoint: AC Mode must be heat.")
	}
}

//	Experimental setpoint control. No SmartThings api defined for custom command,
//	so, I am guessing here.
//	Min/Max Setpoint Experiment, phase 1 - command works???
//	a.	maxSetpoint = {current temperature} - 5
//	b.	minSetpoint = maxSetpoint - 5
//	c.	Run: auto(), fanAuto(), setMinimumSetpoint(minSetpoint), setMaximumSetpoint(maxSetpoint). send log.
//	d.	Observe:  The unit should start cooling the living space.
//	e.	maxSetpoint: Increase by 15
//	f.	Run: setMaximumSetpoint(maxSetpoint). send log.
//	g.	Observe:  The unit should stop cooling or heating the living Space.
//	h.	minSetpoint: Increase by 15.
//	i.	Run: setMinimumSetpoint(minSetpoint). send log.
//	j.	Observe: The unit should start heating the unit.
//	k.	Reset your unit to desired setting using the unit's remote control.
def setMinimumSetpoint(setpoint) {
	def cmdData = [
		component: "main",
		capability: "custom.thermostatSetpointControl",
		command: "setMinimumSetpoint",
		arguments: [setpoint]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setMinimumSetpoint: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}
def setMaximumSetpoint(setpoint) {
	def cmdData = [
		component: "main",
		capability: "custom.thermostatSetpointControl",
		command: "setMaximumSetpoint",
		arguments: [setpoint]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setMaximumSetpoint: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

//	Compose the command data before sending
def deviceCommand(cmdData) {
	def respData
	if (simulate == true) {
		respData = testResp()
	} else if (!stDeviceId || stDeviceId.trim() == "") {
		respData = "[status: FAILED, data: no stDeviceId]"
		logWarn("deviceCommand: [status: ERROR, errorMsg: no stDeviceId]")
	} else {
		def sendData = [
			path: "/devices/${stDeviceId.trim()}/commands",
			cmdData: cmdData
		]
		respData = syncPost(sendData)
//		poll()
	}
	poll()
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
	def respLog
	if (resp.status == 200) {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
			statusParse(respData.components.main)
		} catch (err) {
			respLog = [status: "ERROR",
						errorMsg: err,
						respData: resp.data]
		}
	} else {
		respLog = [status: "ERROR",
					httpCode: resp.status,
					errorMsg: resp.errorMessage]
	}
	if (respLog != [:]) {
		logWarn("deviceStatusParse: ${respLog}")
	}
}
def statusParse(parseData) {
	def logData = [:]
	
	setAttribute("switch", parseData.switch.switch.value)

	//	Try loops will eventually go away.  Needed to detect errors against actual device.
	def tempUnit
	try {
		tempUnit = parseData.temperatureMeasurement.temperature.unit
		setAttribute("temperature", 
					 parseData.temperatureMeasurement.temperature.value, tempUnit)
	} catch (e) {
		logWarn("statusParse: [temperature: ${e}]")
	}

	try {
		def thermostatMode = parseData.airConditionerMode.airConditionerMode.value
		setAttribute("thermostatMode", thermostatMode)
		if (thermostatMode == "heat") {
			setAttribute("thermostatSetpoint", device.currentValue("heatingSetpoint"), tempUnit)
		} else if (thermostatMode == "cool") {
			setAttribute("thermostatSetpoint", device.currentValue("coolingSetpoint"), tempUnit)
		} else if (thermostatMode == "auto") {
			setAttribute("thermostatSetpoint", device.currentValue("setpointRange"), tempUnit)
		}
	} catch (e) {
		logWarn("statusParse: [thermostatMode: ${e}]")
	}

	try {
		if (thermostatMode == "heat") {
			setAttribute("heatingSetpoint", 
						 parseData.thermostatCoolingSetpoint.coolingSetpoint.value, tempUnit)
		} else if (thermostatMode == "cool") {
			setAttribute("coolingSetpoint", 
						 parseData.thermostatCoolingSetpoint.coolingSetpoint.value, tempUnit)
		}
	} catch (e) {
		logWarn("statusParse: [heatingSetpoint: ${e}]")
	}

	try {
		setAttribute("thermostatFanMode", 
					 parseData.airConditionerFanMode.fanMode.value)
	} catch (e) {
		logWarn("statusParse: [thermostatFanMode: ${e}]")
	}
	
	
	try {
		def minSetpoint = parseData["custom.thermostatSetpointControl"].minimumSetpoint.value
		def maxSetpoint = parseData["custom.thermostatSetpointControl"].maximumSetpoint.value
		def setpointRange = [min: minSetpoint, max: maxSetpoint]
		setAttribute("setpointRange", setpointRange, tempUnit)
	} catch (e) {
		logWarn("statusParse: [minimumSetpoint: ${e}]")
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
	
//	if (logData != [:]) {
//		logInfo("deviceStatusParse: ${logData}")
//	}
//	Temp Test Code
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
