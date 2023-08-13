/*	Kasa Device Driver Series
		Copyright Dave Gutheinz
License Information:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
6.5.1	Hot fix for loop in EM Month Stat Processing due to month = 1
6.5.2	a.	Updated Energy Monitor function (final fix for January process loop issue)
			*	Schedule getThisMonth and getLastMonth to preclude looping
			*	Change get energy today to getDayStat and using raw lan protocol
			*	Will no longer poll for power if Kasa device is off
		b.	Added capability Configuration (command: configure)
			*	Configure command will call app.configurationsUpdate.
				**	Updates configurations on ALL devices
				**	Schedules app.configurationsUpdate to run nightly
			*	Updates device data to current driver data (i.e., new capability data)
			*	Set states for updates available and driver version not matching app version.
		c.	Color Bulbs and Light Strips.
			*	Added support for attribute RGB from capability Color
			*	Added command setRgb to allow direct entry of RGB for device control.
		d.	Dimming Switch.  Changed preferences fadeOn and fadeOff to values instant, slow, medium
			and fast to reflect Kasa Phone App setting values.
		e.	Code Update Process: After update, go to ANY SINGLE device and select the "Configure"
			Command.  This will update ALL installed Kasa devices.
===================================================================================================*/
def driverVer() { return "6.5.2" }
def type() { return "Dimming Switch" }

metadata {
	definition (name: "Kasa ${type()}",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/DeviceDrivers/DimmingSwitch.groovy"
			   ) {
		capability "Configuration"
		capability "Switch"
		capability "Actuator"
		capability "Refresh"
		capability "Switch Level"
		capability "Level Preset"
		capability "Change Level"
		command "ledOn"
		command "ledOff"
		attribute "led", "string"
		command "setPollInterval", [[
			name: "Poll Interval in seconds",
			constraints: ["default", "5 seconds", "10 seconds", "15 seconds",
						  "30 seconds", "1 minute", "5 minutes",  "10 minutes",
						  "30 minutes"],
			type: "ENUM"]]
		attribute "connection", "string"
		attribute "commsError", "string"
	}
	preferences {
		input ("descriptionText", "bool", 
			   title: "Enable information logging", 
			   defaultValue: true)
		input ("debug", "bool",
			   title: "30 minutes of debug logging", 
			   defaultValue: false)
		input ("gentleOn", "number",
			   title: "Gentle On (max 7000 msec)",
			   defaultValue:5000,
			   range: 0 .. 7100)
		input ("gentleOff", "number",
			   title: "Gentle On (max 7000 msec)",
			   defaultValue:5000,
			   range: 0 .. 7100)
		def fadeOpts = [0: "Instant",  1000: "Fast",
						2000: "Medium", 3000: "Slow"]
		input ("fadeOn", "enum",
			   title: "Fade On",
			   defaultValue:"Fast",
			   options: fadeOpts)
		input ("fadeOff", "enum",
			   title: "Fade Off",
			   defaultValue:"Fast",
			   options: fadeOpts)
		def pressOpts = ["none",  "instant_on_off", "gentle_on_off",
						 "Preset 0", "Preset 1", "Preset 2", "Preset 3"]
		input ("longPress", "enum", title: "Long Press Action",
			   defaultValue: "gentle_on_off",
			   options: pressOpts)
		input ("doubleClick", "enum", title: "Double Tap Action",
			   defaultValue: "Preset 1",
			   options: pressOpts)
		input ("bind", "bool",
			   title: "Kasa Cloud Binding",
			   defalutValue: true)
		if (bind) {
			input ("useCloud", "bool",
				   title: "Use Kasa Cloud for device control",
				   defaultValue: false)
		}
		input ("nameSync", "enum", title: "Synchronize Names",
			   defaultValue: "none",
			   options: ["none": "Don't synchronize",
						 "device" : "Kasa device name master", 
						 "Hubitat" : "Hubitat label master"])
		input ("rebootDev", "bool",
			   title: "Reboot device <b>[Caution]</b>",
			   defaultValue: false)
	}
}

def installed() {
	def instStatus = installCommon()
	pauseExecution(3000)
	getDimmerConfiguration()
	logInfo("installed: ${instStatus}")
}

def updated() {
	def updStatus = updateCommon()
	configureDimmer()
	logInfo("updated: ${updStatus}")
}

def deviceConfigure() {
}

def configureDimmer() {
	logDebug("configureDimmer")
	if (longPress == null || doubleClick == null || gentleOn == null
	    || gentleOff == null || fadeOff == null || fadeOn == null) {
		def dimmerSet = getDimmerConfiguration()
		pauseExecution(2000)
	}
	sendCmd("""{"smartlife.iot.dimmer":{"set_gentle_on_time":{"duration": ${gentleOn}}, """ +
			""""set_gentle_off_time":{"duration": ${gentleOff}}, """ +
			""""set_fade_on_time":{"fadeTime": ${fadeOn}}, """ +
			""""set_fade_off_time":{"fadeTime": ${fadeOff}}}}""")
	pauseExecution(2000)

	def action1 = """{"mode":"${longPress}"}"""
	if (longPress.contains("Preset")) {
		action1 = """{"mode":"customize_preset","index":${longPress[-1].toInteger()}}"""
	}
	def action2 = """{"mode":"${doubleClick}"}"""
	if (doubleClick.contains("Preset")) {
		action2 = """{"mode":"customize_preset","index":${doubleClick[-1].toInteger()}}"""
	}
	sendCmd("""{"smartlife.iot.dimmer":{"set_double_click_action":${action2}, """ +
			""""set_long_press_action":${action1}}}""")

	runIn(1, getDimmerConfiguration)
}

def setDimmerConfig(response) {
	logDebug("setDimmerConfiguration: ${response}")
	def params
	def dimmerConfig = [:]
	if (response["get_dimmer_parameters"]) {
		params = response["get_dimmer_parameters"]
		if (params.err_code == "0") {
			logWarn("setDimmerConfig: Error in getDimmerParams: ${params}")
		} else {
			device.updateSetting("fadeOn", [type:"integer", value: params.fadeOnTime])
			device.updateSetting("fadeOff", [type:"integer", value: params.fadeOffTime])
			device.updateSetting("gentleOn", [type:"integer", value: params.gentleOnTime])
			device.updateSetting("gentleOff", [type:"integer", value: params.gentleOffTime])
			dimmerConfig << [fadeOn: fadeOn, fadeOff: fadeOff,
							 genleOn: gentleOn, gentleOff: gentleOff]
		}
	}
	if (response["get_default_behavior"]) {
		params = response["get_default_behavior"]
		if (params.err_code == "0") {
			logWarn("setDimmerConfig: Error in getDefaultBehavior: ${params}")
		} else {
			def longPress = params.long_press.mode
			if (params.long_press.index != null) { longPress = "Preset ${params.long_press.index}" }
			device.updateSetting("longPress", [type:"enum", value: longPress])
			def doubleClick = params.double_click.mode
			if (params.double_click.index != null) { doubleClick = "Preset ${params.double_click.index}" }
			device.updateSetting("doubleClick", [type:"enum", value: doubleClick])
			dimmerConfig << [longPress: longPress, doubleClick: doubleClick]
		}
	}
	logInfo("setDimmerConfig: ${dimmerConfig}")
}

//	==================================================
//	Basic Commands
//	==================================================
def on() { setRelayState(1) }

def off() { setRelayState(0) }

def ledOn() { setLedOff(0) }

def ledOff() { setLedOff(1) }

def setLevel(level, transTime = fadeOn/1000) {
	setDimmerTransition(level, transTime)
	def updates = [:]
	updates << [switch: "on", level: level]
	sendEvent(name: "switch", value: "on", type: "digital")
	sendEvent(name: "level", value: level, type: "digital")
	logInfo("setLevel: ${updates}")
	runIn(9, getSysinfo)
}

def presetLevel(level) {
	presetBrightness(level)
}

def startLevelChange(direction) {
	logDebug("startLevelChange: [level: ${device.currentValue("level")}, direction: ${direction}]")
	if (device.currentValue("switch") == "off") {
		setRelayState(1)
		pauseExecution(1000)
	}
	if (direction == "up") { levelUp() }
	else { levelDown() }
}

def stopLevelChange() {
	logDebug("startLevelChange: [level: ${device.currentValue("level")}]")
	unschedule(levelUp)
	unschedule(levelDown)
}

def levelUp() {
	def curLevel = device.currentValue("level").toInteger()
	if (curLevel == 100) { return }
	def newLevel = curLevel + 4
	if (newLevel > 100) { newLevel = 100 }
	presetBrightness(newLevel)
	runIn(1, levelUp)
}

def levelDown() {
	def curLevel = device.currentValue("level").toInteger()
	if (curLevel == 0 || device.currentValue("switch") == "off") { return }
	def newLevel = curLevel - 4
	if (newLevel <= 0) { off() }
	else {
		presetBrightness(newLevel)
		runIn(1, levelDown)
	}
}

//	==================================================
//	Handle responses from Device
//	==================================================
def distResp(response) {
	if (response.system) {
		if (response.system.get_sysinfo) {
			setSysInfo(response.system.get_sysinfo)
			if (nameSync == "device") {
				updateName(response.system.get_sysinfo)
			}
		} else if (response.system.reboot) {
			logWarn("distResp: Rebooting device.")
		} else if (response.system.set_dev_alias) {
			updateName(response.system.set_dev_alias)
		} else {
			logDebug("distResp: Unhandled response = ${response}")
		}
	} else if (response["smartlife.iot.dimmer"]) {
		if (response["smartlife.iot.dimmer"].get_dimmer_parameters) {
			setDimmerConfig(response["smartlife.iot.dimmer"])
		} else {
			logDebug("distResp: Unhandled response: ${response["smartlife.iot.dimmer"]}")
		}
	} else if (response.cnCloud) {
		setBindUnbind(response.cnCloud)
	} else {
		logDebug("distResp: Unhandled response = ${response}")
	}
}

def setSysInfo(status) {
	logDebug("setSysInfo: ${status}")
	def updates = [:]
	def switchStatus = status.relay_state
	def ledStatus = status.led_off
	def onOff = "on"
	if (switchStatus == 0) { onOff = "off" }
	if (onOff != device.currentValue("switch")) {
		updates << [switch: onOff]
		sendEvent(name: "switch", value: onOff, type: "digital")
	}
	if (status.brightness != device.currentValue("level")) {
		updates << [level: status.brightness]
		sendEvent(name: "level", value: status.brightness, type: "digital")
	}
	def ledOnOff = "on"
	if (ledStatus == 1) { ledOnOff = "off" }
	if (ledOnOff != device.currentValue("led")) {
		updates << [led: ledOnOff]
		sendEvent(name: "led", value: ledOnOff)
	}
	if (updates != [:]) { logInfo("setSysinfo: ${updates}") }
}

//	==================================================
//	Kasa API Methods
//	==================================================
def setRelayState(onOff) {
	logDebug("setRelayState: [switch: ${onOff}]")
	sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" +
			""""system":{"set_relay_state":{"state":${onOff}},"get_sysinfo":{}}}""")
}

def setLedOff(onOff) {
	logDebug("setLedOff: [ledOff: ${onOff}]")
	sendCmd("""{"system":{"set_led_off":{"off":${onOff}},""" +
			""""get_sysinfo":{}}}""")
}

def checkTransTime(transTime) {
	if (transTime == null || transTime < 1) { transTime = 1 }
	else if (transTime > 8) { transTime = 8 }
	transTime = (1000 * transTime).toInteger()
	return transTime
}

def checkLevel(level) {
	if (level == null || level < 0) {
		level = 0
	} else if (level > 100) {
		level = 100
	}
	return level
}

def setDimmerTransition(level, transTime) {
	level = checkLevel(level)
	transTime = checkTransTime(transTime)
	logDebug("setDimmerTransition: [level: ${level}, transTime: ${transTime}]")
	if (level == 0) {
		setRelayState(0)
	} else {
		sendCmd("""{"smartlife.iot.dimmer":{"set_dimmer_transition":{"brightness":${level},""" +
				""""duration":${transTime}}}}""")
	}
}

def presetBrightness(level) {
	level = checkLevel(level)
	logDebug("presetLevel: [level: ${level}]")
	sendCmd("""{"smartlife.iot.dimmer":{"set_brightness":{"brightness":${level}}},""" +
			""""system" :{"get_sysinfo" :{}}}""")
}

def getDimmerConfiguration() {
	logDebug("getDimmerConfiguration")
	sendCmd("""{"smartlife.iot.dimmer":{"get_dimmer_parameters":{}, """ +
			""""get_default_behavior":{}}}""")
}

//	==================================================
//	Includes
//	==================================================



// ~~~~~ start include (322) davegut.kasaCommon ~~~~~
library ( // library marker davegut.kasaCommon, line 1
	name: "kasaCommon", // library marker davegut.kasaCommon, line 2
	namespace: "davegut", // library marker davegut.kasaCommon, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaCommon, line 4
	description: "Kasa Device Common Methods", // library marker davegut.kasaCommon, line 5
	category: "utilities", // library marker davegut.kasaCommon, line 6
	documentationLink: "" // library marker davegut.kasaCommon, line 7
) // library marker davegut.kasaCommon, line 8

//	====== Common Install / Update Elements ===== // library marker davegut.kasaCommon, line 10
def installCommon() { // library marker davegut.kasaCommon, line 11
	pauseExecution(3000) // library marker davegut.kasaCommon, line 12
	def instStatus = [:] // library marker davegut.kasaCommon, line 13
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 14
		sendEvent(name: "connection", value: "CLOUD") // library marker davegut.kasaCommon, line 15
		device.updateSetting("useCloud", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 16
		instStatus << [useCloud: true, connection: "CLOUD"] // library marker davegut.kasaCommon, line 17
	} else { // library marker davegut.kasaCommon, line 18
		sendEvent(name: "connection", value: "LAN") // library marker davegut.kasaCommon, line 19
		device.updateSetting("useCloud", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 20
		instStatus << [useCloud: false, connection: "LAN"] // library marker davegut.kasaCommon, line 21
	} // library marker davegut.kasaCommon, line 22
	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 23
	state.errorCount = 0 // library marker davegut.kasaCommon, line 24
	state.pollInterval = "30 minutes" // library marker davegut.kasaCommon, line 25
	instStatus << [driverVersion: driverVer()] // library marker davegut.kasaCommon, line 26
	runIn(2, updated) // library marker davegut.kasaCommon, line 27
	return instStatus // library marker davegut.kasaCommon, line 28
} // library marker davegut.kasaCommon, line 29

def updateCommon() { // library marker davegut.kasaCommon, line 31
	unschedule() // library marker davegut.kasaCommon, line 32
	def updStatus = [:] // library marker davegut.kasaCommon, line 33
	if (rebootDev) { // library marker davegut.kasaCommon, line 34
		updStatus << [rebootDev: rebootDevice()] // library marker davegut.kasaCommon, line 35
		return updStatus // library marker davegut.kasaCommon, line 36
	} // library marker davegut.kasaCommon, line 37
	updStatus << [bind: bindUnbind()] // library marker davegut.kasaCommon, line 38
	if (nameSync != "none") { // library marker davegut.kasaCommon, line 39
		updStatus << [nameSync: syncName()] // library marker davegut.kasaCommon, line 40
	} // library marker davegut.kasaCommon, line 41
	if (debug) { runIn(1800, debugOff) } // library marker davegut.kasaCommon, line 42
	updStatus << [debug: debug] // library marker davegut.kasaCommon, line 43
	state.errorCount = 0 // library marker davegut.kasaCommon, line 44
	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 45
	updStatus << [pollInterval: setPollInterval()] // library marker davegut.kasaCommon, line 46
	getSysinfo() // library marker davegut.kasaCommon, line 47
	configure() // library marker davegut.kasaCommon, line 48
	pauseExecution(3000) // library marker davegut.kasaCommon, line 49
	return updStatus // library marker davegut.kasaCommon, line 50
} // library marker davegut.kasaCommon, line 51

def configure() { // library marker davegut.kasaCommon, line 53
	//	Capability Configuration command // library marker davegut.kasaCommon, line 54
	//	calls parentUpdateConfigurations, which configures App and ALL Kasa Devices // library marker davegut.kasaCommon, line 55
	//	Only needs to be executed from one device to update all devices. // library marker davegut.kasaCommon, line 56
	def config = parent.updateConfigurations() // library marker davegut.kasaCommon, line 57
	logInfo("configure: ${config}") // library marker davegut.kasaCommon, line 58
} // library marker davegut.kasaCommon, line 59

def childConfigure(updateData) { // library marker davegut.kasaCommon, line 61
	//	called from parent..updateConfigurations(). // library marker davegut.kasaCommon, line 62
	//	Uses provided data to determine and inform user of current driver status. // library marker davegut.kasaCommon, line 63
	//	Checks driver vs device driverVersion, if different, updates and sets // library marker davegut.kasaCommon, line 64
	//	data for use in new capabilities (if any). // library marker davegut.kasaCommon, line 65
	def message = "configure: Configuring ${device.getLabel()}." // library marker davegut.kasaCommon, line 66
	message += "\n\t\t\t  *\tUpdated IP for all devices." // library marker davegut.kasaCommon, line 67
	logDebug("childConfigure: ${updateData}") // library marker davegut.kasaCommon, line 68
	if (driverVer().trim() != updateData.appVersion) { // library marker davegut.kasaCommon, line 69
		state.DRIVER_MISMATCH = "Driver version (${driverVer()}) not the same as App version (${updateData.appVersion})" // library marker davegut.kasaCommon, line 70
		message += "\n\t\t\t  *\t<b>Driver/App Versions: Don't match!  Update!</b>" // library marker davegut.kasaCommon, line 71
		logWarn("configure: Current driver does not match with App Version.  Update to assure proper operation.") // library marker davegut.kasaCommon, line 72
	} else { // library marker davegut.kasaCommon, line 73
		state.remove("DRIVER_MISMATCH") // library marker davegut.kasaCommon, line 74
		message += "\n\t\t\t  *\tDriver/App Versions: OK, same for each." // library marker davegut.kasaCommon, line 75
	} // library marker davegut.kasaCommon, line 76
	if (updateData.updateAvailable) { // library marker davegut.kasaCommon, line 77
		state.releaseNotes = "${updateData.releaseNotes}" // library marker davegut.kasaCommon, line 78
		if (updateData.releaseNotes.contains("CRITICAL")) { // library marker davegut.kasaCommon, line 79
			state.UPDATE_AVAILABLE = "A CRITICAL UPDATE TO APP AND DRIVER ARE AVAILABLE to version  ${updateData.currVersion}." // library marker davegut.kasaCommon, line 80
			message += "\n\t\t\t  *\t<b>Driver/App Updates: CRITICAL UPDATES AVAILABLE.</b>" // library marker davegut.kasaCommon, line 81
			logWarn("<b>A CRITICAL</b> Applications and Drivers update is available for the Kasa Integration") // library marker davegut.kasaCommon, line 82
		} else { // library marker davegut.kasaCommon, line 83
			state.UPDATE_AVAILABLE = "App and driver updates are available to version ${updateData.currVersion}.  Consider updating." // library marker davegut.kasaCommon, line 84
			message += "\n\t\t\t  *\t<b>Driver/App Updates: Available.</b>" // library marker davegut.kasaCommon, line 85
		} // library marker davegut.kasaCommon, line 86
	} else { // library marker davegut.kasaCommon, line 87
		message += "\n\t\t\t  *\tDriver/App Updates: No updates available." // library marker davegut.kasaCommon, line 88
		state.remove("UPDATE_AVAILABLE") // library marker davegut.kasaCommon, line 89
		state.remove("releaseNotes") // library marker davegut.kasaCommon, line 90
	} // library marker davegut.kasaCommon, line 91
	//	Added/updated preference and state values to avoid null errors. // library marker davegut.kasaCommon, line 92
	if(getDataValue("driverVersion") != driverVer()){ // library marker davegut.kasaCommon, line 93
		updateDataValue("driverVersion", driverVer()) // library marker davegut.kasaCommon, line 94
		if (!descriptionText || descriptionText == null) { // library marker davegut.kasaCommon, line 95
			device.updateSetting("descriptionText", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 96
		} // library marker davegut.kasaCommon, line 97
		if (!state.pollInterval) { state.pollInterval = "30 minutes" } // library marker davegut.kasaCommon, line 98
		deviceConfigure() // library marker davegut.kasaCommon, line 99
	} // library marker davegut.kasaCommon, line 100
	logInfo(message) // library marker davegut.kasaCommon, line 101
} // library marker davegut.kasaCommon, line 102

//	===== Poll/Refresh ===== // library marker davegut.kasaCommon, line 104
def refresh() { getSysinfo() } // library marker davegut.kasaCommon, line 105

def poll() { getSysinfo() } // library marker davegut.kasaCommon, line 107

//	===== Preference Methods ===== // library marker davegut.kasaCommon, line 109
def setPollInterval(interval = state.pollInterval) { // library marker davegut.kasaCommon, line 110
	if (interval == "default" || interval == "off" || interval == null) { // library marker davegut.kasaCommon, line 111
		interval = "30 minutes" // library marker davegut.kasaCommon, line 112
	} else if (useCloud && interval.contains("sec")) { // library marker davegut.kasaCommon, line 113
		interval = "1 minute" // library marker davegut.kasaCommon, line 114
	} // library marker davegut.kasaCommon, line 115
	state.pollInterval = interval // library marker davegut.kasaCommon, line 116
	def pollInterval = interval.substring(0,2).toInteger() // library marker davegut.kasaCommon, line 117
	if (interval.contains("sec")) { // library marker davegut.kasaCommon, line 118
		def start = Math.round((pollInterval-1) * Math.random()).toInteger() // library marker davegut.kasaCommon, line 119
		schedule("${start}/${pollInterval} * * * * ?", "getSysinfo") // library marker davegut.kasaCommon, line 120
		state.pollNote = "Polling intervals of less than one minute can take high " + // library marker davegut.kasaCommon, line 121
			"resources and may impact hub performance." // library marker davegut.kasaCommon, line 122
	} else { // library marker davegut.kasaCommon, line 123
		def start = Math.round(59 * Math.random()).toInteger() // library marker davegut.kasaCommon, line 124
		schedule("${start} */${pollInterval} * * * ?", "getSysinfo") // library marker davegut.kasaCommon, line 125
		state.remove("pollNote") // library marker davegut.kasaCommon, line 126
	} // library marker davegut.kasaCommon, line 127
	logDebug("setPollInterval: interval = ${interval}.") // library marker davegut.kasaCommon, line 128
	return interval // library marker davegut.kasaCommon, line 129
} // library marker davegut.kasaCommon, line 130

def rebootDevice() { // library marker davegut.kasaCommon, line 132
	device.updateSetting("rebootDev", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 133
	reboot() // library marker davegut.kasaCommon, line 134
	pauseExecution(10000) // library marker davegut.kasaCommon, line 135
	return "REBOOTING DEVICE" // library marker davegut.kasaCommon, line 136
} // library marker davegut.kasaCommon, line 137

def bindUnbind() { // library marker davegut.kasaCommon, line 139
	def message // library marker davegut.kasaCommon, line 140
	if (bind == null || // library marker davegut.kasaCommon, line 141
	    getDataValue("deviceIP") == "CLOUD" || // library marker davegut.kasaCommon, line 142
	    type() == "Light Strip") { // library marker davegut.kasaCommon, line 143
		message = "Getting current bind state" // library marker davegut.kasaCommon, line 144
		getBind() // library marker davegut.kasaCommon, line 145
	} else if (bind == true) { // library marker davegut.kasaCommon, line 146
		if (!parent.kasaToken || parent.userName == null || parent.userPassword == null) { // library marker davegut.kasaCommon, line 147
			message = "Username/pwd not set." // library marker davegut.kasaCommon, line 148
			getBind() // library marker davegut.kasaCommon, line 149
		} else { // library marker davegut.kasaCommon, line 150
			message = "Binding device to the Kasa Cloud." // library marker davegut.kasaCommon, line 151
			setBind(parent.userName, parent.userPassword) // library marker davegut.kasaCommon, line 152
		} // library marker davegut.kasaCommon, line 153
	} else if (bind == false) { // library marker davegut.kasaCommon, line 154
		message = "Unbinding device from the Kasa Cloud." // library marker davegut.kasaCommon, line 155
		setUnbind() // library marker davegut.kasaCommon, line 156
	} // library marker davegut.kasaCommon, line 157
	pauseExecution(5000) // library marker davegut.kasaCommon, line 158
	return message // library marker davegut.kasaCommon, line 159
} // library marker davegut.kasaCommon, line 160

def setBindUnbind(cmdResp) { // library marker davegut.kasaCommon, line 162
	def bindState = true // library marker davegut.kasaCommon, line 163
	if (cmdResp.get_info) { // library marker davegut.kasaCommon, line 164
		if (cmdResp.get_info.binded == 0) { bindState = false } // library marker davegut.kasaCommon, line 165
		logInfo("setBindUnbind: Bind status set to ${bindState}") // library marker davegut.kasaCommon, line 166
		setCommsType(bindState) // library marker davegut.kasaCommon, line 167
	} else if (cmdResp.bind.err_code == 0){ // library marker davegut.kasaCommon, line 168
		getBind() // library marker davegut.kasaCommon, line 169
	} else { // library marker davegut.kasaCommon, line 170
		logWarn("setBindUnbind: Unhandled response: ${cmdResp}") // library marker davegut.kasaCommon, line 171
	} // library marker davegut.kasaCommon, line 172
} // library marker davegut.kasaCommon, line 173

def setCommsType(bindState) { // library marker davegut.kasaCommon, line 175
	def commsType = "LAN" // library marker davegut.kasaCommon, line 176
	def cloudCtrl = false // library marker davegut.kasaCommon, line 177
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 178
		commsType = "CLOUD" // library marker davegut.kasaCommon, line 179
		cloudCtrl = true // library marker davegut.kasaCommon, line 180
	} else if (bindState == false && useCloud == true) { // library marker davegut.kasaCommon, line 181
		logWarn("setCommsType: Can not use cloud.  Device is not bound to Kasa cloud.") // library marker davegut.kasaCommon, line 182
	} else if (bindState == true && useCloud == true && parent.kasaToken) { // library marker davegut.kasaCommon, line 183
		commsType = "CLOUD" // library marker davegut.kasaCommon, line 184
		cloudCtrl = true // library marker davegut.kasaCommon, line 185
	} else if (altLan == true) { // library marker davegut.kasaCommon, line 186
		commsType = "AltLAN" // library marker davegut.kasaCommon, line 187
		state.response = "" // library marker davegut.kasaCommon, line 188
	} // library marker davegut.kasaCommon, line 189
	def commsSettings = [bind: bindState, useCloud: cloudCtrl, commsType: commsType] // library marker davegut.kasaCommon, line 190
	device.updateSetting("bind", [type:"bool", value: bindState]) // library marker davegut.kasaCommon, line 191
	device.updateSetting("useCloud", [type:"bool", value: cloudCtrl]) // library marker davegut.kasaCommon, line 192
	sendEvent(name: "connection", value: "${commsType}") // library marker davegut.kasaCommon, line 193
	logInfo("setCommsType: ${commsSettings}") // library marker davegut.kasaCommon, line 194
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaCommon, line 195
		def coordData = [:] // library marker davegut.kasaCommon, line 196
		coordData << [bind: bindState] // library marker davegut.kasaCommon, line 197
		coordData << [useCloud: cloudCtrl] // library marker davegut.kasaCommon, line 198
		coordData << [connection: commsType] // library marker davegut.kasaCommon, line 199
		parent.coordinate("commsData", coordData, getDataValue("deviceId"), getDataValue("plugNo")) // library marker davegut.kasaCommon, line 200
	} // library marker davegut.kasaCommon, line 201
	pauseExecution(1000) // library marker davegut.kasaCommon, line 202
} // library marker davegut.kasaCommon, line 203

def syncName() { // library marker davegut.kasaCommon, line 205
	def message // library marker davegut.kasaCommon, line 206
	if (nameSync == "Hubitat") { // library marker davegut.kasaCommon, line 207
		message = "Hubitat Label Sync" // library marker davegut.kasaCommon, line 208
		setDeviceAlias(device.getLabel()) // library marker davegut.kasaCommon, line 209
	} else if (nameSync == "device") { // library marker davegut.kasaCommon, line 210
		message = "Device Alias Sync" // library marker davegut.kasaCommon, line 211
	} else { // library marker davegut.kasaCommon, line 212
		message = "Not Syncing" // library marker davegut.kasaCommon, line 213
	} // library marker davegut.kasaCommon, line 214
	return message // library marker davegut.kasaCommon, line 215
} // library marker davegut.kasaCommon, line 216

def updateName(response) { // library marker davegut.kasaCommon, line 218
	device.updateSetting("nameSync",[type:"enum", value:"none"]) // library marker davegut.kasaCommon, line 219
	def name = device.getLabel() // library marker davegut.kasaCommon, line 220
	if (response.alias) { // library marker davegut.kasaCommon, line 221
		name = response.alias // library marker davegut.kasaCommon, line 222
		device.setLabel(name) // library marker davegut.kasaCommon, line 223
	} else if (response.err_code != 0) { // library marker davegut.kasaCommon, line 224
		def msg = "updateName: Name Sync from Hubitat to Device returned an error." // library marker davegut.kasaCommon, line 225
		msg+= "Note: <b>some devices do not support syncing name from the hub.</b>\n\r" // library marker davegut.kasaCommon, line 226
		logWarn(msg) // library marker davegut.kasaCommon, line 227
		return // library marker davegut.kasaCommon, line 228
	} // library marker davegut.kasaCommon, line 229
	logInfo("updateName: Hubitat and Kasa device name synchronized to ${name}") // library marker davegut.kasaCommon, line 230
} // library marker davegut.kasaCommon, line 231

//	===== Kasa API Commands ===== // library marker davegut.kasaCommon, line 233
def getSysinfo() { // library marker davegut.kasaCommon, line 234
	sendCmd("""{"system":{"get_sysinfo":{}}}""") // library marker davegut.kasaCommon, line 235
} // library marker davegut.kasaCommon, line 236

def reboot() { // library marker davegut.kasaCommon, line 238
	def method = "system" // library marker davegut.kasaCommon, line 239
	if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 240
		method = "smartlife.iot.common.system" // library marker davegut.kasaCommon, line 241
	} // library marker davegut.kasaCommon, line 242
	sendCmd("""{"${method}":{"reboot":{"delay":1}}}""") // library marker davegut.kasaCommon, line 243
} // library marker davegut.kasaCommon, line 244

def bindService() { // library marker davegut.kasaCommon, line 246
	def service = "cnCloud" // library marker davegut.kasaCommon, line 247
	if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 248
		service = "smartlife.iot.common.cloud" // library marker davegut.kasaCommon, line 249
	} // library marker davegut.kasaCommon, line 250
	return service // library marker davegut.kasaCommon, line 251
} // library marker davegut.kasaCommon, line 252

def getBind() {	 // library marker davegut.kasaCommon, line 254
	sendLanCmd("""{"${bindService()}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 255
} // library marker davegut.kasaCommon, line 256

def setBind(userName, password) { // library marker davegut.kasaCommon, line 258
	sendLanCmd("""{"${bindService()}":{"bind":{"username":"${userName}",""" + // library marker davegut.kasaCommon, line 259
			   """"password":"${password}"}},""" + // library marker davegut.kasaCommon, line 260
			   """"${bindService()}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 261
} // library marker davegut.kasaCommon, line 262

def setUnbind() { // library marker davegut.kasaCommon, line 264
	sendLanCmd("""{"${bindService()}":{"unbind":""},""" + // library marker davegut.kasaCommon, line 265
			   """"${bindService()}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 266
} // library marker davegut.kasaCommon, line 267

def setDeviceAlias(newAlias) { // library marker davegut.kasaCommon, line 269
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaCommon, line 270
	sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaCommon, line 271
				""""system":{"set_dev_alias":{"alias":"${device.getLabel()}"}}}""") // library marker davegut.kasaCommon, line 272
	} else { // library marker davegut.kasaCommon, line 273
		sendCmd("""{"system":{"set_dev_alias":{"alias":"${device.getLabel()}"}}}""") // library marker davegut.kasaCommon, line 274
	} // library marker davegut.kasaCommon, line 275
} // library marker davegut.kasaCommon, line 276

// ~~~~~ end include (322) davegut.kasaCommon ~~~~~

// ~~~~~ start include (323) davegut.kasaCommunications ~~~~~
library ( // library marker davegut.kasaCommunications, line 1
	name: "kasaCommunications", // library marker davegut.kasaCommunications, line 2
	namespace: "davegut", // library marker davegut.kasaCommunications, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaCommunications, line 4
	description: "Kasa Communications Methods", // library marker davegut.kasaCommunications, line 5
	category: "communications", // library marker davegut.kasaCommunications, line 6
	documentationLink: "" // library marker davegut.kasaCommunications, line 7
) // library marker davegut.kasaCommunications, line 8

import groovy.json.JsonSlurper // library marker davegut.kasaCommunications, line 10

def getPort() { // library marker davegut.kasaCommunications, line 12
	def port = 9999 // library marker davegut.kasaCommunications, line 13
	if (getDataValue("devicePort")) { // library marker davegut.kasaCommunications, line 14
		port = getDataValue("devicePort") // library marker davegut.kasaCommunications, line 15
	} // library marker davegut.kasaCommunications, line 16
	return port // library marker davegut.kasaCommunications, line 17
} // library marker davegut.kasaCommunications, line 18

def sendCmd(command) { // library marker davegut.kasaCommunications, line 20
	if (!command.contains("password")) { // library marker davegut.kasaCommunications, line 21
		state.lastCommand = command // library marker davegut.kasaCommunications, line 22
	} // library marker davegut.kasaCommunications, line 23
	if (device.currentValue("connection") == "LAN") { // library marker davegut.kasaCommunications, line 24
		sendLanCmd(command) // library marker davegut.kasaCommunications, line 25
	} else if (device.currentValue("connection") == "CLOUD"){ // library marker davegut.kasaCommunications, line 26
		sendKasaCmd(command) // library marker davegut.kasaCommunications, line 27
	} else if (device.currentValue("connection") == "AltLAN") { // library marker davegut.kasaCommunications, line 28
		sendTcpCmd(command) // library marker davegut.kasaCommunications, line 29
	} else { // library marker davegut.kasaCommunications, line 30
		logWarn("sendCmd: attribute connection is not set.") // library marker davegut.kasaCommunications, line 31
	} // library marker davegut.kasaCommunications, line 32
} // library marker davegut.kasaCommunications, line 33

def sendLanCmd(command) { // library marker davegut.kasaCommunications, line 35
	logDebug("sendLanCmd: ${command}") // library marker davegut.kasaCommunications, line 36
	def myHubAction = new hubitat.device.HubAction( // library marker davegut.kasaCommunications, line 37
		outputXOR(command), // library marker davegut.kasaCommunications, line 38
		hubitat.device.Protocol.LAN, // library marker davegut.kasaCommunications, line 39
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, // library marker davegut.kasaCommunications, line 40
		 destinationAddress: "${getDataValue("deviceIP")}:${getPort()}", // library marker davegut.kasaCommunications, line 41
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING, // library marker davegut.kasaCommunications, line 42
		 parseWarning: true, // library marker davegut.kasaCommunications, line 43
		 timeout: 10, // library marker davegut.kasaCommunications, line 44
		 callback: parseUdp]) // library marker davegut.kasaCommunications, line 45
	try { // library marker davegut.kasaCommunications, line 46
		sendHubCommand(myHubAction) // library marker davegut.kasaCommunications, line 47
	} catch (e) { // library marker davegut.kasaCommunications, line 48
		logWarn("sendLanCmd: LAN Error = ${e}.\n\rNo retry on this error.") // library marker davegut.kasaCommunications, line 49
	} // library marker davegut.kasaCommunications, line 50
} // library marker davegut.kasaCommunications, line 51

def parseUdp(message) { // library marker davegut.kasaCommunications, line 53
	def resp = parseLanMessage(message) // library marker davegut.kasaCommunications, line 54
	if (resp.type == "LAN_TYPE_UDPCLIENT") { // library marker davegut.kasaCommunications, line 55
		def clearResp = inputXOR(resp.payload) // library marker davegut.kasaCommunications, line 56
		if (clearResp.length() > 1022) { // library marker davegut.kasaCommunications, line 57
			if (clearResp.contains("preferred")) { // library marker davegut.kasaCommunications, line 58
				clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}" // library marker davegut.kasaCommunications, line 59
			} else { // library marker davegut.kasaCommunications, line 60
				def msg = "parseUdp: Response is too long for Hubitat UDP implementation." // library marker davegut.kasaCommunications, line 61
				msg += "\n\t<b>Device attributes have not been updated.</b>" // library marker davegut.kasaCommunications, line 62
				if(device.getName().contains("Multi")) { // library marker davegut.kasaCommunications, line 63
					msg += "\n\t<b>HS300:</b>\tCheck your device names. The total Kasa App names of all " // library marker davegut.kasaCommunications, line 64
					msg += "\n\t\t\tdevice names can't exceed 96 charactrs (16 per device).\n\r" // library marker davegut.kasaCommunications, line 65
				} // library marker davegut.kasaCommunications, line 66
				logWarn(msg) // library marker davegut.kasaCommunications, line 67
				return // library marker davegut.kasaCommunications, line 68
			} // library marker davegut.kasaCommunications, line 69
		} // library marker davegut.kasaCommunications, line 70
		def cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.kasaCommunications, line 71
		distResp(cmdResp) // library marker davegut.kasaCommunications, line 72
		resetCommsError() // library marker davegut.kasaCommunications, line 73
	} else { // library marker davegut.kasaCommunications, line 74
		logDebug("parse: LAN Error = ${resp.type}") // library marker davegut.kasaCommunications, line 75
		handleCommsError() // library marker davegut.kasaCommunications, line 76
	} // library marker davegut.kasaCommunications, line 77
} // library marker davegut.kasaCommunications, line 78

def sendKasaCmd(command) { // library marker davegut.kasaCommunications, line 80
	logDebug("sendKasaCmd: ${command}") // library marker davegut.kasaCommunications, line 81
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 82
	def cmdBody = [ // library marker davegut.kasaCommunications, line 83
		method: "passthrough", // library marker davegut.kasaCommunications, line 84
		params: [ // library marker davegut.kasaCommunications, line 85
			deviceId: getDataValue("deviceId"), // library marker davegut.kasaCommunications, line 86
			requestData: "${command}" // library marker davegut.kasaCommunications, line 87
		] // library marker davegut.kasaCommunications, line 88
	] // library marker davegut.kasaCommunications, line 89
	if (!parent.kasaCloudUrl || !parent.kasaToken) { // library marker davegut.kasaCommunications, line 90
		logWarn("sendKasaCmd: Cloud interface not properly set up.") // library marker davegut.kasaCommunications, line 91
		return // library marker davegut.kasaCommunications, line 92
	} // library marker davegut.kasaCommunications, line 93
	def sendCloudCmdParams = [ // library marker davegut.kasaCommunications, line 94
		uri: "${parent.kasaCloudUrl}/?token=${parent.kasaToken}", // library marker davegut.kasaCommunications, line 95
		requestContentType: 'application/json', // library marker davegut.kasaCommunications, line 96
		contentType: 'application/json', // library marker davegut.kasaCommunications, line 97
		headers: ['Accept':'application/json; version=1, */*; q=0.01'], // library marker davegut.kasaCommunications, line 98
		timeout: 10, // library marker davegut.kasaCommunications, line 99
		body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.kasaCommunications, line 100
	] // library marker davegut.kasaCommunications, line 101
	try { // library marker davegut.kasaCommunications, line 102
		asynchttpPost("cloudParse", sendCloudCmdParams) // library marker davegut.kasaCommunications, line 103
	} catch (e) { // library marker davegut.kasaCommunications, line 104
		def msg = "sendKasaCmd: <b>Error in Cloud Communications.</b> The Kasa Cloud is unreachable." // library marker davegut.kasaCommunications, line 105
		msg += "\nAdditional Data: Error = ${e}\n\n" // library marker davegut.kasaCommunications, line 106
		logWarn(msg) // library marker davegut.kasaCommunications, line 107
	} // library marker davegut.kasaCommunications, line 108
} // library marker davegut.kasaCommunications, line 109

def cloudParse(resp, data = null) { // library marker davegut.kasaCommunications, line 111
	def jsonSlurper = new groovy.json.JsonSlurper() // library marker davegut.kasaCommunications, line 112
	def response = jsonSlurper.parseText(resp.data) // library marker davegut.kasaCommunications, line 113
	if (resp.status == 200 && response.error_code == 0) { // library marker davegut.kasaCommunications, line 114
		distResp(jsonSlurper.parseText(response.result.responseData)) // library marker davegut.kasaCommunications, line 115
		resetCommsError() // library marker davegut.kasaCommunications, line 116
	} else { // library marker davegut.kasaCommunications, line 117
		def msg = "sendKasaCmd:\n<b>Error from the Kasa Cloud.</b> Most common cause is " // library marker davegut.kasaCommunications, line 118
		msg += "your Kasa Token has expired.  Run Kasa Login and Token update and try again." // library marker davegut.kasaCommunications, line 119
		msg += "\nAdditional Data: Error = ${resp.data}\n\n" // library marker davegut.kasaCommunications, line 120
		logDebug(msg) // library marker davegut.kasaCommunications, line 121
		handleCommsError() // library marker davegut.kasaCommunications, line 122
	} // library marker davegut.kasaCommunications, line 123
} // library marker davegut.kasaCommunications, line 124

private sendTcpCmd(command) { // library marker davegut.kasaCommunications, line 126
	logDebug("sendTcpCmd: ${command}") // library marker davegut.kasaCommunications, line 127
	try { // library marker davegut.kasaCommunications, line 128
		interfaces.rawSocket.connect("${getDataValue("deviceIP")}", // library marker davegut.kasaCommunications, line 129
									 getPort().toInteger(), byteInterface: true) // library marker davegut.kasaCommunications, line 130
	} catch (error) { // library marker davegut.kasaCommunications, line 131
		logDebug("SendTcpCmd: Unable to connect to device at ${getDataValue("deviceIP")}:${getDataValue("devicePort")}. " + // library marker davegut.kasaCommunications, line 132
				 "Error = ${error}") // library marker davegut.kasaCommunications, line 133
	} // library marker davegut.kasaCommunications, line 134
	state.lastCommand = command // library marker davegut.kasaCommunications, line 135
	interfaces.rawSocket.sendMessage(outputXorTcp(command)) // library marker davegut.kasaCommunications, line 136
	runIn(2, close) // library marker davegut.kasaCommunications, line 137
} // library marker davegut.kasaCommunications, line 138

def close() { interfaces.rawSocket.close() } // library marker davegut.kasaCommunications, line 140

def socketStatus(message) { // library marker davegut.kasaCommunications, line 142
	if (message != "receive error: Stream closed.") { // library marker davegut.kasaCommunications, line 143
		logDebug("socketStatus: Socket Established") // library marker davegut.kasaCommunications, line 144
	} else { // library marker davegut.kasaCommunications, line 145
		logWarn("socketStatus = ${message}") // library marker davegut.kasaCommunications, line 146
	} // library marker davegut.kasaCommunications, line 147
} // library marker davegut.kasaCommunications, line 148

def parse(message) { // library marker davegut.kasaCommunications, line 150
	def response = state.response.concat(message) // library marker davegut.kasaCommunications, line 151
	state.response = response // library marker davegut.kasaCommunications, line 152
	runInMillis(50, extractTcpResp, [data: response]) // library marker davegut.kasaCommunications, line 153
} // library marker davegut.kasaCommunications, line 154

def extractTcpResp(response) { // library marker davegut.kasaCommunications, line 156
	state.response = "" // library marker davegut.kasaCommunications, line 157
	if (response.length() == null) { // library marker davegut.kasaCommunications, line 158
		logDebug("extractTcpResp: null return rejected.") // library marker davegut.kasaCommunications, line 159
		return  // library marker davegut.kasaCommunications, line 160
	} // library marker davegut.kasaCommunications, line 161
	logDebug("extractTcpResp: ${response}") // library marker davegut.kasaCommunications, line 162
	try { // library marker davegut.kasaCommunications, line 163
		distResp(parseJson(inputXorTcp(response))) // library marker davegut.kasaCommunications, line 164
		resetCommsError() // library marker davegut.kasaCommunications, line 165
	} catch (e) { // library marker davegut.kasaCommunications, line 166
		handleCommsError() // library marker davegut.kasaCommunications, line 167
	} // library marker davegut.kasaCommunications, line 168
} // library marker davegut.kasaCommunications, line 169

def handleCommsError() { // library marker davegut.kasaCommunications, line 171
	def count = state.errorCount + 1 // library marker davegut.kasaCommunications, line 172
	state.errorCount = count // library marker davegut.kasaCommunications, line 173
	def message = "handleCommsError: Count: ${count}." // library marker davegut.kasaCommunications, line 174
	if (count <= 2) { // library marker davegut.kasaCommunications, line 175
		message += "\n\t\t\t Retrying LAN command, try = ${count}" // library marker davegut.kasaCommunications, line 176
		runIn(1, sendCmd, [data: state.lastCommand]) // library marker davegut.kasaCommunications, line 177
	} else if (count == 3) { // library marker davegut.kasaCommunications, line 178
		def attemptFix = parent.fixConnection(device.currentValue("connection")) // library marker davegut.kasaCommunications, line 179
		message += "\n\t\t\t Attempt to update IP or token: ${attemptFix}" // library marker davegut.kasaCommunications, line 180
		message += "\n\t\t\t Retrying LAN command, try = ${count}" // library marker davegut.kasaCommunications, line 181
		runIn(5, sendCmd, [data: state.lastCommand]) // library marker davegut.kasaCommunications, line 182
	} else if (count > 6) { // library marker davegut.kasaCommunications, line 183
		setCommsError() // library marker davegut.kasaCommunications, line 184
		message += "No retry. Retry limit exceeded." // library marker davegut.kasaCommunications, line 185
	} // library marker davegut.kasaCommunications, line 186
	logWarn(message) // library marker davegut.kasaCommunications, line 187
} // library marker davegut.kasaCommunications, line 188

def setCommsError() { // library marker davegut.kasaCommunications, line 190
	if (device.currentValue("commsError") == "false") { // library marker davegut.kasaCommunications, line 191
		def message = "Can't connect to your device at ${getDataValue("deviceIP")}:${getPort()}. " // library marker davegut.kasaCommunications, line 192
		message += "Refer to troubleshooting guide commsError section." // library marker davegut.kasaCommunications, line 193
		sendEvent(name: "commsError", value: "true") // library marker davegut.kasaCommunications, line 194
		state.COMMS_ERROR = message			 // library marker davegut.kasaCommunications, line 195
		logWarn("setCommsError: <b>${message}</b>") // library marker davegut.kasaCommunications, line 196
		runIn(15, limitPollInterval) // library marker davegut.kasaCommunications, line 197
	} // library marker davegut.kasaCommunications, line 198
} // library marker davegut.kasaCommunications, line 199

def limitPollInterval() { // library marker davegut.kasaCommunications, line 201
	state.nonErrorPollInterval = state.pollInterval // library marker davegut.kasaCommunications, line 202
	setPollInterval("30 minutes") // library marker davegut.kasaCommunications, line 203
} // library marker davegut.kasaCommunications, line 204

def resetCommsError() { // library marker davegut.kasaCommunications, line 206
	state.errorCount = 0 // library marker davegut.kasaCommunications, line 207
	if (device.currentValue("commsError") == "true") { // library marker davegut.kasaCommunications, line 208
		sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommunications, line 209
		setPollInterval(state.nonErrorPollInterval) // library marker davegut.kasaCommunications, line 210
		state.remove("nonErrorPollInterval") // library marker davegut.kasaCommunications, line 211
		state.remove("COMMS_ERROR") // library marker davegut.kasaCommunications, line 212
		logInfo("resetCommsError: Comms error cleared!") // library marker davegut.kasaCommunications, line 213
	} // library marker davegut.kasaCommunications, line 214
} // library marker davegut.kasaCommunications, line 215

private outputXOR(command) { // library marker davegut.kasaCommunications, line 217
	def str = "" // library marker davegut.kasaCommunications, line 218
	def encrCmd = "" // library marker davegut.kasaCommunications, line 219
 	def key = 0xAB // library marker davegut.kasaCommunications, line 220
	for (int i = 0; i < command.length(); i++) { // library marker davegut.kasaCommunications, line 221
		str = (command.charAt(i) as byte) ^ key // library marker davegut.kasaCommunications, line 222
		key = str // library marker davegut.kasaCommunications, line 223
		encrCmd += Integer.toHexString(str) // library marker davegut.kasaCommunications, line 224
	} // library marker davegut.kasaCommunications, line 225
   	return encrCmd // library marker davegut.kasaCommunications, line 226
} // library marker davegut.kasaCommunications, line 227

private inputXOR(encrResponse) { // library marker davegut.kasaCommunications, line 229
	String[] strBytes = encrResponse.split("(?<=\\G.{2})") // library marker davegut.kasaCommunications, line 230
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 231
	def key = 0xAB // library marker davegut.kasaCommunications, line 232
	def nextKey // library marker davegut.kasaCommunications, line 233
	byte[] XORtemp // library marker davegut.kasaCommunications, line 234
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommunications, line 235
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.kasaCommunications, line 236
		XORtemp = nextKey ^ key // library marker davegut.kasaCommunications, line 237
		key = nextKey // library marker davegut.kasaCommunications, line 238
		cmdResponse += new String(XORtemp) // library marker davegut.kasaCommunications, line 239
	} // library marker davegut.kasaCommunications, line 240
	return cmdResponse // library marker davegut.kasaCommunications, line 241
} // library marker davegut.kasaCommunications, line 242

private outputXorTcp(command) { // library marker davegut.kasaCommunications, line 244
	def str = "" // library marker davegut.kasaCommunications, line 245
	def encrCmd = "000000" + Integer.toHexString(command.length())  // library marker davegut.kasaCommunications, line 246
 	def key = 0xAB // library marker davegut.kasaCommunications, line 247
	for (int i = 0; i < command.length(); i++) { // library marker davegut.kasaCommunications, line 248
		str = (command.charAt(i) as byte) ^ key // library marker davegut.kasaCommunications, line 249
		key = str // library marker davegut.kasaCommunications, line 250
		encrCmd += Integer.toHexString(str) // library marker davegut.kasaCommunications, line 251
	} // library marker davegut.kasaCommunications, line 252
   	return encrCmd // library marker davegut.kasaCommunications, line 253
} // library marker davegut.kasaCommunications, line 254

private inputXorTcp(resp) { // library marker davegut.kasaCommunications, line 256
	String[] strBytes = resp.substring(8).split("(?<=\\G.{2})") // library marker davegut.kasaCommunications, line 257
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 258
	def key = 0xAB // library marker davegut.kasaCommunications, line 259
	def nextKey // library marker davegut.kasaCommunications, line 260
	byte[] XORtemp // library marker davegut.kasaCommunications, line 261
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommunications, line 262
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.kasaCommunications, line 263
		XORtemp = nextKey ^ key // library marker davegut.kasaCommunications, line 264
		key = nextKey // library marker davegut.kasaCommunications, line 265
		cmdResponse += new String(XORtemp) // library marker davegut.kasaCommunications, line 266
	} // library marker davegut.kasaCommunications, line 267
	return cmdResponse // library marker davegut.kasaCommunications, line 268
} // library marker davegut.kasaCommunications, line 269

def logTrace(msg){ // library marker davegut.kasaCommunications, line 271
	log.trace "[${driverVer()} / ${device.getLabel()}]| ${msg}" // library marker davegut.kasaCommunications, line 272
} // library marker davegut.kasaCommunications, line 273

def logInfo(msg) { // library marker davegut.kasaCommunications, line 275
	if(descriptionText == true) { // library marker davegut.kasaCommunications, line 276
		log.info "[${driverVer()} / ${device.getLabel()}]| ${msg}" // library marker davegut.kasaCommunications, line 277
	} // library marker davegut.kasaCommunications, line 278
} // library marker davegut.kasaCommunications, line 279

def logDebug(msg){ // library marker davegut.kasaCommunications, line 281
	if(debug == true) { // library marker davegut.kasaCommunications, line 282
		log.debug "[${driverVer()} / ${device.getLabel()}]| ${msg}" // library marker davegut.kasaCommunications, line 283
	} // library marker davegut.kasaCommunications, line 284
} // library marker davegut.kasaCommunications, line 285

def debugOff() { // library marker davegut.kasaCommunications, line 287
	device.updateSetting("debug", [type:"bool", value: false]) // library marker davegut.kasaCommunications, line 288
	logInfo("debugLogOff: Debug logging is off.") // library marker davegut.kasaCommunications, line 289
} // library marker davegut.kasaCommunications, line 290

def logWarn(msg) { // library marker davegut.kasaCommunications, line 292
	log.warn "[${driverVer()} / ${device.getLabel()}]| ${msg}" // library marker davegut.kasaCommunications, line 293
} // library marker davegut.kasaCommunications, line 294

// ~~~~~ end include (323) davegut.kasaCommunications ~~~~~
