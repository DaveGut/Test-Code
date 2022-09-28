/*	Kasa Device Driver Series
		Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Link to list of changes =====
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Changes.pdf
===== Link to Documentation =====
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Documentation.pdf
===================================================================================================*/
//	6.7.2 Change B.  Remove driverVer()
//def driverVer() { return "6.7.1" }
def type() { return "Dimming Switch" }

metadata {
	definition (name: "Kasa ${type()}",
//	6.7.2 Change A:	Add methods nameSpace()
				namespace: nameSpace(),
//				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/DeviceDrivers/DimmingSwitch.groovy"
			   ) {
		capability "Switch"
		capability "Actuator"
		capability "Refresh"
		capability "Configuration"
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
		input ("infoLog", "bool", 
			   title: "Enable information logging " + helpLogo(),
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
		input ("useCloud", "bool",
		 	  title: "Use Kasa Cloud for device control",
		 	  defaultValue: false)
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
	refresh()
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
			def fadeOn = getFade(params.fadeOnTime.toInteger())
			def fadeOff = getFade(params.fadeOffTime.toInteger())
			device.updateSetting("fadeOn", [type:"integer", value: fadeOn])
			device.updateSetting("fadeOff", [type:"integer", value: fadeOff])
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

def getFade(fadeTime) {
	def fadeSpeed = "Instant"
	if (fadeTime == 1000) {
		fadeSpeed = "Fast"
	} else if (fadeTime == 2000) {
		fadeSpeed = "Medium"
	} else if (fadeTime == 3000) {
		fadeSpeed = "Slow"
	}
	return fadeSpeed
}

def setLevel(level, transTime = gentleOn) {
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

def setSysInfo(status) {
	def updates = [:]
	def switchStatus = status.relay_state
	def onOff = "on"
	if (switchStatus == 0) { onOff = "off" }
	sendEvent(name: "switch", value: onOff, type: "digital")

	sendEvent(name: "level", value: status.brightness, type: "digital")
	def ledStatus = status.led_off
	def ledOnOff = "on"
	if (ledStatus == 1) { ledOnOff = "off" }
	sendEvent(name: "led", value: ledOnOff)

	runIn(3, listAttributes)
	if (nameSync == "device") {
		updateName(status)
	}
}

def checkTransTime(transTime) {
	if (transTime == null || transTime < 0) { transTime = 0 }
	transTime = 1000 * transTime.toInteger()
	if (transTime > 8000) { transTime = 8000 }
	return transTime
}

def checkLevel(level) {
	if (level == null || level < 0) {
		level = device.currentValue("level")
		logWarn("checkLevel: Entered level null or negative. Level set to ${level}")
	} else if (level > 100) {
		level = 100
		logWarn("checkLevel: Entered level > 100.  Level set to ${level}")
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






// ~~~~~ start include (1147) davegut.kasaCommon ~~~~~
library ( // library marker davegut.kasaCommon, line 1
	name: "kasaCommon", // library marker davegut.kasaCommon, line 2
	namespace: "davegut", // library marker davegut.kasaCommon, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaCommon, line 4
	description: "Kasa Device Common Methods", // library marker davegut.kasaCommon, line 5
	category: "utilities", // library marker davegut.kasaCommon, line 6
	documentationLink: "" // library marker davegut.kasaCommon, line 7
) // library marker davegut.kasaCommon, line 8

//	6.7.2 Change A:	Add methods nameSpace() // library marker davegut.kasaCommon, line 10
def nameSpace() { return "davegut" } // library marker davegut.kasaCommon, line 11

String helpLogo() { // library marker davegut.kasaCommon, line 13
	return """<a href="https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/README.md">""" + // library marker davegut.kasaCommon, line 14
		"""<div style="position: absolute; top: 10px; right: 10px; height: 80px; font-size: 20px;">Kasa Help</div></a>""" // library marker davegut.kasaCommon, line 15
} // library marker davegut.kasaCommon, line 16

def installCommon() { // library marker davegut.kasaCommon, line 18
	pauseExecution(3000) // library marker davegut.kasaCommon, line 19
	def instStatus = [:] // library marker davegut.kasaCommon, line 20
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 21
		sendEvent(name: "connection", value: "CLOUD") // library marker davegut.kasaCommon, line 22
		device.updateSetting("useCloud", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 23
		instStatus << [useCloud: true, connection: "CLOUD"] // library marker davegut.kasaCommon, line 24
	} else { // library marker davegut.kasaCommon, line 25
		sendEvent(name: "connection", value: "LAN") // library marker davegut.kasaCommon, line 26
		device.updateSetting("useCloud", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 27
		instStatus << [useCloud: false, connection: "LAN"] // library marker davegut.kasaCommon, line 28
	} // library marker davegut.kasaCommon, line 29

	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 31
	state.errorCount = 0 // library marker davegut.kasaCommon, line 32
	state.pollInterval = "30 minutes" // library marker davegut.kasaCommon, line 33
//	6.7.2 Change B.  Remove driverVer() // library marker davegut.kasaCommon, line 34
//	instStatus << [driverVersion: driverVer()] // library marker davegut.kasaCommon, line 35
	runIn(1, updated) // library marker davegut.kasaCommon, line 36
	return instStatus // library marker davegut.kasaCommon, line 37
} // library marker davegut.kasaCommon, line 38

def updateCommon() { // library marker davegut.kasaCommon, line 40
	def updStatus = [:] // library marker davegut.kasaCommon, line 41
	if (rebootDev) { // library marker davegut.kasaCommon, line 42
		updStatus << [rebootDev: rebootDevice()] // library marker davegut.kasaCommon, line 43
		return updStatus // library marker davegut.kasaCommon, line 44
	} // library marker davegut.kasaCommon, line 45
	unschedule() // library marker davegut.kasaCommon, line 46
	updStatus << [bind: bindUnbind()] // library marker davegut.kasaCommon, line 47
	if (nameSync != "none") { // library marker davegut.kasaCommon, line 48
		updStatus << [nameSync: syncName()] // library marker davegut.kasaCommon, line 49
	} // library marker davegut.kasaCommon, line 50
	if (debug) { runIn(1800, debugLogOff) } // library marker davegut.kasaCommon, line 51
	updStatus << [debug: debug] // library marker davegut.kasaCommon, line 52
	state.errorCount = 0 // library marker davegut.kasaCommon, line 53
	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 54
	def pollInterval = state.pollInterval // library marker davegut.kasaCommon, line 55
	if (pollInterval == null) { pollInterval = "30 minutes" } // library marker davegut.kasaCommon, line 56
	updStatus << [pollInterval: setPollInterval(pollInterval)] // library marker davegut.kasaCommon, line 57
//	6.7.2 Change B.  Remove driverVer() // library marker davegut.kasaCommon, line 58
//	if(getDataValue("driverVersion") != driverVer()) { // library marker davegut.kasaCommon, line 59
	state.remove("UPDATE_AVAILABLE") // library marker davegut.kasaCommon, line 60
	state.remove("releaseNotes") // library marker davegut.kasaCommon, line 61
	removeDataValue("driverVersion") // library marker davegut.kasaCommon, line 62
//		updateDataValue("driverVersion", driverVer()) // library marker davegut.kasaCommon, line 63
//		updStatus << [driverVersion: driverVer()] // library marker davegut.kasaCommon, line 64
//	} // library marker davegut.kasaCommon, line 65
	if (emFunction) { // library marker davegut.kasaCommon, line 66
		schedule("10 0 0 * * ?", getEnergyThisMonth) // library marker davegut.kasaCommon, line 67
		schedule("15 2 0 1 * ?", getEnergyLastMonth) // library marker davegut.kasaCommon, line 68
		state.getEnergy = "This Month" // library marker davegut.kasaCommon, line 69
		updStatus << [emFunction: "scheduled"] // library marker davegut.kasaCommon, line 70
	} // library marker davegut.kasaCommon, line 71
	runIn(5, listAttributes) // library marker davegut.kasaCommon, line 72
	return updStatus // library marker davegut.kasaCommon, line 73
} // library marker davegut.kasaCommon, line 74

def configure() { // library marker davegut.kasaCommon, line 76
	if (parent == null) { // library marker davegut.kasaCommon, line 77
		logWarn("configure: No Parent Detected.  Configure function ABORTED.  Use Save Preferences instead.") // library marker davegut.kasaCommon, line 78
	} else { // library marker davegut.kasaCommon, line 79
		def confStatus = parent.updateConfigurations() // library marker davegut.kasaCommon, line 80
		logInfo("configure: ${confStatus}") // library marker davegut.kasaCommon, line 81
	} // library marker davegut.kasaCommon, line 82
} // library marker davegut.kasaCommon, line 83

def refresh() { poll() } // library marker davegut.kasaCommon, line 85

def poll() { getSysinfo() } // library marker davegut.kasaCommon, line 87

def setPollInterval(interval = state.pollInterval) { // library marker davegut.kasaCommon, line 89
	if (interval == "default" || interval == "off" || interval == null) { // library marker davegut.kasaCommon, line 90
		interval = "30 minutes" // library marker davegut.kasaCommon, line 91
	} else if (useCloud && interval.contains("sec")) { // library marker davegut.kasaCommon, line 92
		interval = "1 minute" // library marker davegut.kasaCommon, line 93
	} // library marker davegut.kasaCommon, line 94
	state.pollInterval = interval // library marker davegut.kasaCommon, line 95
	def pollInterval = interval.substring(0,2).toInteger() // library marker davegut.kasaCommon, line 96
	if (interval.contains("sec")) { // library marker davegut.kasaCommon, line 97
		def start = Math.round((pollInterval-1) * Math.random()).toInteger() // library marker davegut.kasaCommon, line 98
		schedule("${start}/${pollInterval} * * * * ?", "poll") // library marker davegut.kasaCommon, line 99
		logWarn("setPollInterval: Polling intervals of less than one minute " + // library marker davegut.kasaCommon, line 100
				"can take high resources and may impact hub performance.") // library marker davegut.kasaCommon, line 101
	} else { // library marker davegut.kasaCommon, line 102
		def start = Math.round(59 * Math.random()).toInteger() // library marker davegut.kasaCommon, line 103
		schedule("${start} */${pollInterval} * * * ?", "poll") // library marker davegut.kasaCommon, line 104
	} // library marker davegut.kasaCommon, line 105
	logDebug("setPollInterval: interval = ${interval}.") // library marker davegut.kasaCommon, line 106
	return interval // library marker davegut.kasaCommon, line 107
} // library marker davegut.kasaCommon, line 108

def rebootDevice() { // library marker davegut.kasaCommon, line 110
	device.updateSetting("rebootDev", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 111
	reboot() // library marker davegut.kasaCommon, line 112
	pauseExecution(10000) // library marker davegut.kasaCommon, line 113
	return "REBOOTING DEVICE" // library marker davegut.kasaCommon, line 114
} // library marker davegut.kasaCommon, line 115

def bindUnbind() { // library marker davegut.kasaCommon, line 117
	def message // library marker davegut.kasaCommon, line 118
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 119
		device.updateSetting("bind", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 120
		device.updateSetting("useCloud", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 121
		message = "No deviceIp.  Bind not modified." // library marker davegut.kasaCommon, line 122
	} else if (bind == null || // library marker davegut.kasaCommon, line 123
	    type() == "Light Strip") { // library marker davegut.kasaCommon, line 124
		message = "Getting current bind state" // library marker davegut.kasaCommon, line 125
		getBind() // library marker davegut.kasaCommon, line 126
	} else if (bind == true) { // library marker davegut.kasaCommon, line 127
		if (!parent.kasaToken || parent.userName == null || parent.userPassword == null) { // library marker davegut.kasaCommon, line 128
			message = "Username/pwd not set." // library marker davegut.kasaCommon, line 129
			getBind() // library marker davegut.kasaCommon, line 130
		} else { // library marker davegut.kasaCommon, line 131
			message = "Binding device to the Kasa Cloud." // library marker davegut.kasaCommon, line 132
			setBind(parent.userName, parent.userPassword) // library marker davegut.kasaCommon, line 133
		} // library marker davegut.kasaCommon, line 134
	} else if (bind == false) { // library marker davegut.kasaCommon, line 135
		message = "Unbinding device from the Kasa Cloud." // library marker davegut.kasaCommon, line 136
		setUnbind() // library marker davegut.kasaCommon, line 137
	} // library marker davegut.kasaCommon, line 138
	pauseExecution(5000) // library marker davegut.kasaCommon, line 139
	return message // library marker davegut.kasaCommon, line 140
} // library marker davegut.kasaCommon, line 141

def setBindUnbind(cmdResp) { // library marker davegut.kasaCommon, line 143
	def bindState = true // library marker davegut.kasaCommon, line 144
	if (cmdResp.get_info) { // library marker davegut.kasaCommon, line 145
		if (cmdResp.get_info.binded == 0) { bindState = false } // library marker davegut.kasaCommon, line 146
		logInfo("setBindUnbind: Bind status set to ${bindState}") // library marker davegut.kasaCommon, line 147
		setCommsType(bindState) // library marker davegut.kasaCommon, line 148
	} else if (cmdResp.bind.err_code == 0){ // library marker davegut.kasaCommon, line 149
		getBind() // library marker davegut.kasaCommon, line 150
	} else { // library marker davegut.kasaCommon, line 151
		logWarn("setBindUnbind: Unhandled response: ${cmdResp}") // library marker davegut.kasaCommon, line 152
	} // library marker davegut.kasaCommon, line 153
} // library marker davegut.kasaCommon, line 154

def setCommsType(bindState) { // library marker davegut.kasaCommon, line 156
	def commsType = "LAN" // library marker davegut.kasaCommon, line 157
	def cloudCtrl = false // library marker davegut.kasaCommon, line 158
	if (bindState == false && useCloud == true) { // library marker davegut.kasaCommon, line 159
		logWarn("setCommsType: Can not use cloud.  Device is not bound to Kasa cloud.") // library marker davegut.kasaCommon, line 160
	} else if (bindState == true && useCloud == true && parent.kasaToken) { // library marker davegut.kasaCommon, line 161
		commsType = "CLOUD" // library marker davegut.kasaCommon, line 162
		cloudCtrl = true // library marker davegut.kasaCommon, line 163
	} else if (altLan == true) { // library marker davegut.kasaCommon, line 164
		commsType = "AltLAN" // library marker davegut.kasaCommon, line 165
		state.response = "" // library marker davegut.kasaCommon, line 166
	} // library marker davegut.kasaCommon, line 167
	def commsSettings = [bind: bindState, useCloud: cloudCtrl, commsType: commsType] // library marker davegut.kasaCommon, line 168
	device.updateSetting("bind", [type:"bool", value: bindState]) // library marker davegut.kasaCommon, line 169
	device.updateSetting("useCloud", [type:"bool", value: cloudCtrl]) // library marker davegut.kasaCommon, line 170
	sendEvent(name: "connection", value: "${commsType}") // library marker davegut.kasaCommon, line 171
	logInfo("setCommsType: ${commsSettings}") // library marker davegut.kasaCommon, line 172
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaCommon, line 173
		def coordData = [:] // library marker davegut.kasaCommon, line 174
		coordData << [bind: bindState] // library marker davegut.kasaCommon, line 175
		coordData << [useCloud: cloudCtrl] // library marker davegut.kasaCommon, line 176
		coordData << [connection: commsType] // library marker davegut.kasaCommon, line 177
		coordData << [altLan: altLan] // library marker davegut.kasaCommon, line 178
		parent.coordinate("commsData", coordData, getDataValue("deviceId"), getDataValue("plugNo")) // library marker davegut.kasaCommon, line 179
	} // library marker davegut.kasaCommon, line 180
	pauseExecution(1000) // library marker davegut.kasaCommon, line 181
} // library marker davegut.kasaCommon, line 182

def syncName() { // library marker davegut.kasaCommon, line 184
	def message // library marker davegut.kasaCommon, line 185
	if (nameSync == "Hubitat") { // library marker davegut.kasaCommon, line 186
		message = "Hubitat Label Sync" // library marker davegut.kasaCommon, line 187
		setDeviceAlias(device.getLabel()) // library marker davegut.kasaCommon, line 188
	} else if (nameSync == "device") { // library marker davegut.kasaCommon, line 189
		message = "Device Alias Sync" // library marker davegut.kasaCommon, line 190
	} else { // library marker davegut.kasaCommon, line 191
		message = "Not Syncing" // library marker davegut.kasaCommon, line 192
	} // library marker davegut.kasaCommon, line 193
	return message // library marker davegut.kasaCommon, line 194
} // library marker davegut.kasaCommon, line 195

def updateName(response) { // library marker davegut.kasaCommon, line 197
	device.updateSetting("nameSync",[type:"enum", value:"none"]) // library marker davegut.kasaCommon, line 198
	def name = device.getLabel() // library marker davegut.kasaCommon, line 199
	if (response.alias) { // library marker davegut.kasaCommon, line 200
		name = response.alias // library marker davegut.kasaCommon, line 201
		device.setLabel(name) // library marker davegut.kasaCommon, line 202
//	6.7.2 Change C. Update the app state.devices to new alias. // library marker davegut.kasaCommon, line 203
		parent.updateAlias(device.deviceNetworkId, name) // library marker davegut.kasaCommon, line 204
	} else if (response.err_code != 0) { // library marker davegut.kasaCommon, line 205
		def msg = "updateName: Name Sync from Hubitat to Device returned an error." // library marker davegut.kasaCommon, line 206
		msg+= "\n\rNote: <b>Some devices do not support syncing name from the hub.</b>\n\r" // library marker davegut.kasaCommon, line 207
		logWarn(msg) // library marker davegut.kasaCommon, line 208
		return // library marker davegut.kasaCommon, line 209
	} // library marker davegut.kasaCommon, line 210
	logInfo("updateName: Hubitat and Kasa device name synchronized to ${name}") // library marker davegut.kasaCommon, line 211
} // library marker davegut.kasaCommon, line 212

//	6.7.2 Change 6. Added altComms method for sysinfo message. // library marker davegut.kasaCommon, line 214
def getSysinfo() { // library marker davegut.kasaCommon, line 215
//	sendCmd("""{"system":{"get_sysinfo":{}}}""") // library marker davegut.kasaCommon, line 216
	if (getDataValue("altComms") == "true") { // library marker davegut.kasaCommon, line 217
		sendTcpCmd("""{"system":{"get_sysinfo":{}}}""") // library marker davegut.kasaCommon, line 218
	} else { // library marker davegut.kasaCommon, line 219
		sendCmd("""{"system":{"get_sysinfo":{}}}""") // library marker davegut.kasaCommon, line 220
	} // library marker davegut.kasaCommon, line 221
} // library marker davegut.kasaCommon, line 222

def reboot() { // library marker davegut.kasaCommon, line 224
	def method = "system" // library marker davegut.kasaCommon, line 225
	if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 226
		method = "smartlife.iot.common.system" // library marker davegut.kasaCommon, line 227
	} // library marker davegut.kasaCommon, line 228
	sendCmd("""{"${method}":{"reboot":{"delay":1}}}""") // library marker davegut.kasaCommon, line 229
} // library marker davegut.kasaCommon, line 230

def bindService() { // library marker davegut.kasaCommon, line 232
	def service = "cnCloud" // library marker davegut.kasaCommon, line 233
	if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 234
		service = "smartlife.iot.common.cloud" // library marker davegut.kasaCommon, line 235
	} // library marker davegut.kasaCommon, line 236
	return service // library marker davegut.kasaCommon, line 237
} // library marker davegut.kasaCommon, line 238

def getBind() { // library marker davegut.kasaCommon, line 240
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 241
		logDebug("getBind: [status: notRun, reason: [deviceIP: CLOUD]]") // library marker davegut.kasaCommon, line 242
	} else { // library marker davegut.kasaCommon, line 243
		sendLanCmd("""{"${bindService()}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 244
	} // library marker davegut.kasaCommon, line 245
} // library marker davegut.kasaCommon, line 246

def setBind(userName, password) { // library marker davegut.kasaCommon, line 248
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 249
		logDebug("setBind: [status: notRun, reason: [deviceIP: CLOUD]]") // library marker davegut.kasaCommon, line 250
	} else { // library marker davegut.kasaCommon, line 251
		sendLanCmd("""{"${bindService()}":{"bind":{"username":"${userName}",""" + // library marker davegut.kasaCommon, line 252
				   """"password":"${password}"}},""" + // library marker davegut.kasaCommon, line 253
				   """"${bindService()}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 254
	} // library marker davegut.kasaCommon, line 255
} // library marker davegut.kasaCommon, line 256

def setUnbind() { // library marker davegut.kasaCommon, line 258
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 259
		logDebug("setUnbind: [status: notRun, reason: [deviceIP: CLOUD]]") // library marker davegut.kasaCommon, line 260
	} else { // library marker davegut.kasaCommon, line 261
		sendLanCmd("""{"${bindService()}":{"unbind":""},""" + // library marker davegut.kasaCommon, line 262
				   """"${bindService()}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 263
	} // library marker davegut.kasaCommon, line 264
} // library marker davegut.kasaCommon, line 265

def setDeviceAlias(newAlias) { // library marker davegut.kasaCommon, line 267
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaCommon, line 268
	sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaCommon, line 269
				""""system":{"set_dev_alias":{"alias":"${device.getLabel()}"}}}""") // library marker davegut.kasaCommon, line 270
	} else { // library marker davegut.kasaCommon, line 271
		sendCmd("""{"system":{"set_dev_alias":{"alias":"${device.getLabel()}"}}}""") // library marker davegut.kasaCommon, line 272
	} // library marker davegut.kasaCommon, line 273
} // library marker davegut.kasaCommon, line 274

// ~~~~~ end include (1147) davegut.kasaCommon ~~~~~

// ~~~~~ start include (1148) davegut.kasaCommunications ~~~~~
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
	def connection = device.currentValue("connection") // library marker davegut.kasaCommunications, line 21
	state.lastCommand = command // library marker davegut.kasaCommunications, line 22
	if (connection == "LAN") { // library marker davegut.kasaCommunications, line 23
		sendLanCmd(command) // library marker davegut.kasaCommunications, line 24
	} else if (connection == "CLOUD"){ // library marker davegut.kasaCommunications, line 25
		sendKasaCmd(command) // library marker davegut.kasaCommunications, line 26
	} else if (connection == "AltLAN") { // library marker davegut.kasaCommunications, line 27
		sendTcpCmd(command) // library marker davegut.kasaCommunications, line 28
	} else { // library marker davegut.kasaCommunications, line 29
		logWarn("sendCmd: attribute connection is not set.") // library marker davegut.kasaCommunications, line 30
	} // library marker davegut.kasaCommunications, line 31
} // library marker davegut.kasaCommunications, line 32

def sendLanCmd(command) { // library marker davegut.kasaCommunications, line 34
	logDebug("sendLanCmd: [ip: ${getDataValue("deviceIP")}, cmd: ${command}]") // library marker davegut.kasaCommunications, line 35
	runIn(10, handleCommsError) // library marker davegut.kasaCommunications, line 36
	def myHubAction = new hubitat.device.HubAction( // library marker davegut.kasaCommunications, line 37
		outputXOR(command), // library marker davegut.kasaCommunications, line 38
		hubitat.device.Protocol.LAN, // library marker davegut.kasaCommunications, line 39
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, // library marker davegut.kasaCommunications, line 40
		 destinationAddress: "${getDataValue("deviceIP")}:${getPort()}", // library marker davegut.kasaCommunications, line 41
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING, // library marker davegut.kasaCommunications, line 42
		 parseWarning: true, // library marker davegut.kasaCommunications, line 43
		 timeout: 9, // library marker davegut.kasaCommunications, line 44
		 ignoreResponse: false, // library marker davegut.kasaCommunications, line 45
		 callback: "parseUdp"]) // library marker davegut.kasaCommunications, line 46
	try { // library marker davegut.kasaCommunications, line 47
		sendHubCommand(myHubAction) // library marker davegut.kasaCommunications, line 48
	} catch (e) { // library marker davegut.kasaCommunications, line 49
		logWarn("sendLanCmd: LAN Error = ${e}.\n\rNo retry on this error.") // library marker davegut.kasaCommunications, line 50
	} // library marker davegut.kasaCommunications, line 51
} // library marker davegut.kasaCommunications, line 52

//	6.7.2 Change C: Changes to handle HS300 Message too long to parse. // library marker davegut.kasaCommunications, line 54
//	If unhandled (for any device, sets altComms true (forever) and  // library marker davegut.kasaCommunications, line 55
//	retries the command.  Code clean-up. // library marker davegut.kasaCommunications, line 56
def parseUdp(message) { // library marker davegut.kasaCommunications, line 57
	def resp = parseLanMessage(message) // library marker davegut.kasaCommunications, line 58
	if (resp.type == "LAN_TYPE_UDPCLIENT") { // library marker davegut.kasaCommunications, line 59
		def clearResp = inputXOR(resp.payload) // library marker davegut.kasaCommunications, line 60
		if (clearResp.length() > 1022) { // library marker davegut.kasaCommunications, line 61
			if (clearResp.contains("preferred")) { // library marker davegut.kasaCommunications, line 62
				clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}" // library marker davegut.kasaCommunications, line 63
			} else { // library marker davegut.kasaCommunications, line 64
				logWarn("parseUdp: [error: msg too long, data: ${clearResp}]") // library marker davegut.kasaCommunications, line 65
				updateDataValue("altComms", "true") // library marker davegut.kasaCommunications, line 66
				unschedule("handleCommsError") // library marker davegut.kasaCommunications, line 67
				sendTcpCmd(state.lastCommand) // library marker davegut.kasaCommunications, line 68
				return // library marker davegut.kasaCommunications, line 69
			} // library marker davegut.kasaCommunications, line 70
		} // library marker davegut.kasaCommunications, line 71
		def cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.kasaCommunications, line 72
		logDebug("parseUdp: ${cmdResp}") // library marker davegut.kasaCommunications, line 73
		distResp(cmdResp) // library marker davegut.kasaCommunications, line 74
		state.lastCommand = "" // library marker davegut.kasaCommunications, line 75
		resetCommsError() // library marker davegut.kasaCommunications, line 76
	} else { // library marker davegut.kasaCommunications, line 77
		logDebug("parseUdp: [error: error, reason: not LAN_TYPE_UDPCLIENT, respType: ${resp.type}]") // library marker davegut.kasaCommunications, line 78
		handleCommsError() // library marker davegut.kasaCommunications, line 79
	} // library marker davegut.kasaCommunications, line 80
} // library marker davegut.kasaCommunications, line 81

def sendKasaCmd(command) { // library marker davegut.kasaCommunications, line 83
	logDebug("sendKasaCmd: ${command}") // library marker davegut.kasaCommunications, line 84
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 85
	def cmdBody = [ // library marker davegut.kasaCommunications, line 86
		method: "passthrough", // library marker davegut.kasaCommunications, line 87
		params: [ // library marker davegut.kasaCommunications, line 88
			deviceId: getDataValue("deviceId"), // library marker davegut.kasaCommunications, line 89
			requestData: "${command}" // library marker davegut.kasaCommunications, line 90
		] // library marker davegut.kasaCommunications, line 91
	] // library marker davegut.kasaCommunications, line 92
	if (!parent.kasaCloudUrl || !parent.kasaToken) { // library marker davegut.kasaCommunications, line 93
		logWarn("sendKasaCmd: Cloud interface not properly set up.") // library marker davegut.kasaCommunications, line 94
		return // library marker davegut.kasaCommunications, line 95
	} // library marker davegut.kasaCommunications, line 96
	def sendCloudCmdParams = [ // library marker davegut.kasaCommunications, line 97
		uri: "${parent.kasaCloudUrl}/?token=${parent.kasaToken}", // library marker davegut.kasaCommunications, line 98
		requestContentType: 'application/json', // library marker davegut.kasaCommunications, line 99
		contentType: 'application/json', // library marker davegut.kasaCommunications, line 100
		headers: ['Accept':'application/json; version=1, */*; q=0.01'], // library marker davegut.kasaCommunications, line 101
		timeout: 10, // library marker davegut.kasaCommunications, line 102
		body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.kasaCommunications, line 103
	] // library marker davegut.kasaCommunications, line 104
	try { // library marker davegut.kasaCommunications, line 105
		asynchttpPost("cloudParse", sendCloudCmdParams) // library marker davegut.kasaCommunications, line 106
	} catch (e) { // library marker davegut.kasaCommunications, line 107
		def msg = "sendKasaCmd: <b>Error in Cloud Communications.</b> The Kasa Cloud is unreachable." // library marker davegut.kasaCommunications, line 108
		msg += "\nAdditional Data: Error = ${e}\n\n" // library marker davegut.kasaCommunications, line 109
		logWarn(msg) // library marker davegut.kasaCommunications, line 110
	} // library marker davegut.kasaCommunications, line 111
} // library marker davegut.kasaCommunications, line 112

def cloudParse(resp, data = null) { // library marker davegut.kasaCommunications, line 114
	try { // library marker davegut.kasaCommunications, line 115
		response = new JsonSlurper().parseText(resp.data) // library marker davegut.kasaCommunications, line 116
	} catch (e) { // library marker davegut.kasaCommunications, line 117
		response = [error_code: 9999, data: e] // library marker davegut.kasaCommunications, line 118
	} // library marker davegut.kasaCommunications, line 119
	if (resp.status == 200 && response.error_code == 0 && resp != []) { // library marker davegut.kasaCommunications, line 120
		def cmdResp = new JsonSlurper().parseText(response.result.responseData) // library marker davegut.kasaCommunications, line 121
		logDebug("cloudParse: ${cmdResp}") // library marker davegut.kasaCommunications, line 122
		distResp(cmdResp) // library marker davegut.kasaCommunications, line 123
		state.lastCommand = "" // library marker davegut.kasaCommunications, line 124
		resetCommsError() // library marker davegut.kasaCommunications, line 125
	} else { // library marker davegut.kasaCommunications, line 126
		def msg = "cloudParse:\n<b>Error from the Kasa Cloud.</b> Most common cause is " // library marker davegut.kasaCommunications, line 127
		msg += "your Kasa Token has expired.  Run Kasa Login and Token update and try again." // library marker davegut.kasaCommunications, line 128
		msg += "\nAdditional Data: Error = ${resp.data}\n\n" // library marker davegut.kasaCommunications, line 129
		logDebug(msg) // library marker davegut.kasaCommunications, line 130
		handleCommsError() // library marker davegut.kasaCommunications, line 131
	} // library marker davegut.kasaCommunications, line 132
} // library marker davegut.kasaCommunications, line 133

def sendTcpCmd(command) { // library marker davegut.kasaCommunications, line 135
	logDebug("sendTcpCmd: ${command}") // library marker davegut.kasaCommunications, line 136
	try { // library marker davegut.kasaCommunications, line 137
		interfaces.rawSocket.connect("${getDataValue("deviceIP")}", // library marker davegut.kasaCommunications, line 138
									 getPort().toInteger(), byteInterface: true) // library marker davegut.kasaCommunications, line 139
	} catch (error) { // library marker davegut.kasaCommunications, line 140
		logDebug("SendTcpCmd: Unable to connect to device at ${getDataValue("deviceIP")}:${getDataValue("devicePort")}. " + // library marker davegut.kasaCommunications, line 141
				 "Error = ${error}") // library marker davegut.kasaCommunications, line 142
	} // library marker davegut.kasaCommunications, line 143
	state.response = "" // library marker davegut.kasaCommunications, line 144
	interfaces.rawSocket.sendMessage(outputXorTcp(command)) // library marker davegut.kasaCommunications, line 145
//	runIn(2, close) // library marker davegut.kasaCommunications, line 146
	runIn(30, close) // library marker davegut.kasaCommunications, line 147
} // library marker davegut.kasaCommunications, line 148

def close() { interfaces.rawSocket.close() } // library marker davegut.kasaCommunications, line 150

def socketStatus(message) { // library marker davegut.kasaCommunications, line 152
	if (message != "receive error: Stream closed.") { // library marker davegut.kasaCommunications, line 153
		logDebug("socketStatus: Socket Established") // library marker davegut.kasaCommunications, line 154
	} else { // library marker davegut.kasaCommunications, line 155
		logWarn("socketStatus = ${message}") // library marker davegut.kasaCommunications, line 156
	} // library marker davegut.kasaCommunications, line 157
} // library marker davegut.kasaCommunications, line 158

def parse(message) { // library marker davegut.kasaCommunications, line 160
	def response = state.response.concat(message) // library marker davegut.kasaCommunications, line 161
	state.response = response // library marker davegut.kasaCommunications, line 162
	runInMillis(50, extractTcpResp, [data: response]) // library marker davegut.kasaCommunications, line 163
} // library marker davegut.kasaCommunications, line 164

def extractTcpResp(response) { // library marker davegut.kasaCommunications, line 166
	if (response.length() == null) { // library marker davegut.kasaCommunications, line 167
		logDebug("extractTcpResp: null return rejected.") // library marker davegut.kasaCommunications, line 168
		return  // library marker davegut.kasaCommunications, line 169
	} // library marker davegut.kasaCommunications, line 170
	logDebug("extractTcpResp: ${response}") // library marker davegut.kasaCommunications, line 171
	def cmdResp // library marker davegut.kasaCommunications, line 172
	try { // library marker davegut.kasaCommunications, line 173
		cmdResp = parseJson(inputXorTcp(response)) // library marker davegut.kasaCommunications, line 174
	} catch (e) { // library marker davegut.kasaCommunications, line 175
		logWarn("extractTcpResponse: comms error = ${e}") // library marker davegut.kasaCommunications, line 176
		cmdResp = "error" // library marker davegut.kasaCommunications, line 177
		handleCommsError() // library marker davegut.kasaCommunications, line 178
	} // library marker davegut.kasaCommunications, line 179
	if (cmdResp != "error") { // library marker davegut.kasaCommunications, line 180
		logDebug("extractTcpResp: ${cmdResp}") // library marker davegut.kasaCommunications, line 181
		distResp(cmdResp) // library marker davegut.kasaCommunications, line 182
		state.lastCommand = "" // library marker davegut.kasaCommunications, line 183
		resetCommsError() // library marker davegut.kasaCommunications, line 184
	} // library marker davegut.kasaCommunications, line 185
} // library marker davegut.kasaCommunications, line 186

def handleCommsError() { // library marker davegut.kasaCommunications, line 188
	if (state.lastCommand == "") { return } // library marker davegut.kasaCommunications, line 189
	def count = state.errorCount + 1 // library marker davegut.kasaCommunications, line 190
	state.errorCount = count // library marker davegut.kasaCommunications, line 191
	def retry = true // library marker davegut.kasaCommunications, line 192
	def status = [count: count, command: state.lastCommand] // library marker davegut.kasaCommunications, line 193
	if (count == 3) { // library marker davegut.kasaCommunications, line 194
		def attemptFix = parent.fixConnection() // library marker davegut.kasaCommunications, line 195
		status << [attemptFixResult: [attemptFix]] // library marker davegut.kasaCommunications, line 196
	} else if (count >= 4) { // library marker davegut.kasaCommunications, line 197
		retry = false // library marker davegut.kasaCommunications, line 198
	} // library marker davegut.kasaCommunications, line 199
	if (retry == true) { // library marker davegut.kasaCommunications, line 200
		if (state.lastCommand != null) { sendCmd(state.lastCommand) } // library marker davegut.kasaCommunications, line 201
		if (count > 1) { // library marker davegut.kasaCommunications, line 202
			logDebug("handleCommsError: [count: ${count}]") // library marker davegut.kasaCommunications, line 203
		} // library marker davegut.kasaCommunications, line 204
	} else { // library marker davegut.kasaCommunications, line 205
		setCommsError() // library marker davegut.kasaCommunications, line 206
	} // library marker davegut.kasaCommunications, line 207
	status << [retry: retry] // library marker davegut.kasaCommunications, line 208
	if (status.count > 2) { // library marker davegut.kasaCommunications, line 209
		logWarn("handleCommsError: ${status}") // library marker davegut.kasaCommunications, line 210
	} else { // library marker davegut.kasaCommunications, line 211
		logDebug("handleCommsError: ${status}") // library marker davegut.kasaCommunications, line 212
	} // library marker davegut.kasaCommunications, line 213
} // library marker davegut.kasaCommunications, line 214

def setCommsError() { // library marker davegut.kasaCommunications, line 216
	if (device.currentValue("commsError") == "false") { // library marker davegut.kasaCommunications, line 217
		def message = "Can't connect to your device at ${getDataValue("deviceIP")}:${getPort()}. " // library marker davegut.kasaCommunications, line 218
		message += "Refer to troubleshooting guide commsError section." // library marker davegut.kasaCommunications, line 219
		sendEvent(name: "commsError", value: "true") // library marker davegut.kasaCommunications, line 220
		state.COMMS_ERROR = message			 // library marker davegut.kasaCommunications, line 221
		logWarn("setCommsError: <b>${message}</b>") // library marker davegut.kasaCommunications, line 222
		runIn(15, limitPollInterval) // library marker davegut.kasaCommunications, line 223
	} // library marker davegut.kasaCommunications, line 224
} // library marker davegut.kasaCommunications, line 225

def limitPollInterval() { // library marker davegut.kasaCommunications, line 227
	state.nonErrorPollInterval = state.pollInterval // library marker davegut.kasaCommunications, line 228
	setPollInterval("30 minutes") // library marker davegut.kasaCommunications, line 229
} // library marker davegut.kasaCommunications, line 230

def resetCommsError() { // library marker davegut.kasaCommunications, line 232
	state.errorCount = 0 // library marker davegut.kasaCommunications, line 233
	if (device.currentValue("commsError") == "true") { // library marker davegut.kasaCommunications, line 234
		sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommunications, line 235
		setPollInterval(state.nonErrorPollInterval) // library marker davegut.kasaCommunications, line 236
		state.remove("nonErrorPollInterval") // library marker davegut.kasaCommunications, line 237
		state.remove("COMMS_ERROR") // library marker davegut.kasaCommunications, line 238
		logInfo("resetCommsError: Comms error cleared!") // library marker davegut.kasaCommunications, line 239
	} // library marker davegut.kasaCommunications, line 240
} // library marker davegut.kasaCommunications, line 241

private outputXOR(command) { // library marker davegut.kasaCommunications, line 243
	def str = "" // library marker davegut.kasaCommunications, line 244
	def encrCmd = "" // library marker davegut.kasaCommunications, line 245
 	def key = 0xAB // library marker davegut.kasaCommunications, line 246
	for (int i = 0; i < command.length(); i++) { // library marker davegut.kasaCommunications, line 247
		str = (command.charAt(i) as byte) ^ key // library marker davegut.kasaCommunications, line 248
		key = str // library marker davegut.kasaCommunications, line 249
		encrCmd += Integer.toHexString(str) // library marker davegut.kasaCommunications, line 250
	} // library marker davegut.kasaCommunications, line 251
   	return encrCmd // library marker davegut.kasaCommunications, line 252
} // library marker davegut.kasaCommunications, line 253

private inputXOR(encrResponse) { // library marker davegut.kasaCommunications, line 255
	String[] strBytes = encrResponse.split("(?<=\\G.{2})") // library marker davegut.kasaCommunications, line 256
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 257
	def key = 0xAB // library marker davegut.kasaCommunications, line 258
	def nextKey // library marker davegut.kasaCommunications, line 259
	byte[] XORtemp // library marker davegut.kasaCommunications, line 260
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommunications, line 261
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.kasaCommunications, line 262
		XORtemp = nextKey ^ key // library marker davegut.kasaCommunications, line 263
		key = nextKey // library marker davegut.kasaCommunications, line 264
		cmdResponse += new String(XORtemp) // library marker davegut.kasaCommunications, line 265
	} // library marker davegut.kasaCommunications, line 266
	return cmdResponse // library marker davegut.kasaCommunications, line 267
} // library marker davegut.kasaCommunications, line 268

private outputXorTcp(command) { // library marker davegut.kasaCommunications, line 270
	def str = "" // library marker davegut.kasaCommunications, line 271
	def encrCmd = "000000" + Integer.toHexString(command.length())  // library marker davegut.kasaCommunications, line 272
 	def key = 0xAB // library marker davegut.kasaCommunications, line 273
	for (int i = 0; i < command.length(); i++) { // library marker davegut.kasaCommunications, line 274
		str = (command.charAt(i) as byte) ^ key // library marker davegut.kasaCommunications, line 275
		key = str // library marker davegut.kasaCommunications, line 276
		encrCmd += Integer.toHexString(str) // library marker davegut.kasaCommunications, line 277
	} // library marker davegut.kasaCommunications, line 278
   	return encrCmd // library marker davegut.kasaCommunications, line 279
} // library marker davegut.kasaCommunications, line 280

private inputXorTcp(resp) { // library marker davegut.kasaCommunications, line 282
	String[] strBytes = resp.substring(8).split("(?<=\\G.{2})") // library marker davegut.kasaCommunications, line 283
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 284
	def key = 0xAB // library marker davegut.kasaCommunications, line 285
	def nextKey // library marker davegut.kasaCommunications, line 286
	byte[] XORtemp // library marker davegut.kasaCommunications, line 287
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommunications, line 288
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.kasaCommunications, line 289
		XORtemp = nextKey ^ key // library marker davegut.kasaCommunications, line 290
		key = nextKey // library marker davegut.kasaCommunications, line 291
		cmdResponse += new String(XORtemp) // library marker davegut.kasaCommunications, line 292
	} // library marker davegut.kasaCommunications, line 293
	return cmdResponse // library marker davegut.kasaCommunications, line 294
} // library marker davegut.kasaCommunications, line 295

// ~~~~~ end include (1148) davegut.kasaCommunications ~~~~~

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

// ~~~~~ start include (1151) davegut.kasaPlugs ~~~~~
library ( // library marker davegut.kasaPlugs, line 1
	name: "kasaPlugs", // library marker davegut.kasaPlugs, line 2
	namespace: "davegut", // library marker davegut.kasaPlugs, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaPlugs, line 4
	description: "Kasa Plug and Switches Common Methods", // library marker davegut.kasaPlugs, line 5
	category: "utilities", // library marker davegut.kasaPlugs, line 6
	documentationLink: "" // library marker davegut.kasaPlugs, line 7
) // library marker davegut.kasaPlugs, line 8

def on() { setRelayState(1) } // library marker davegut.kasaPlugs, line 10

def off() { setRelayState(0) } // library marker davegut.kasaPlugs, line 12

def ledOn() { setLedOff(0) } // library marker davegut.kasaPlugs, line 14

def ledOff() { setLedOff(1) } // library marker davegut.kasaPlugs, line 16

def distResp(response) { // library marker davegut.kasaPlugs, line 18
	if (response.system) { // library marker davegut.kasaPlugs, line 19
		if (response.system.get_sysinfo) { // library marker davegut.kasaPlugs, line 20
			setSysInfo(response.system.get_sysinfo) // library marker davegut.kasaPlugs, line 21
//	6.7.2 Change 6. Use distribute response to send getSysinfo // library marker davegut.kasaPlugs, line 22
//	on error=0 response.system.set_relay_state. // library marker davegut.kasaPlugs, line 23
		} else if (response.system.set_relay_state || // library marker davegut.kasaPlugs, line 24
				   response.system.set_led_off) { // library marker davegut.kasaPlugs, line 25
			getSysinfo() // library marker davegut.kasaPlugs, line 26
		} else if (response.system.reboot) { // library marker davegut.kasaPlugs, line 27
			logWarn("distResp: Rebooting device.") // library marker davegut.kasaPlugs, line 28
		} else if (response.system.set_dev_alias) { // library marker davegut.kasaPlugs, line 29
			updateName(response.system.set_dev_alias) // library marker davegut.kasaPlugs, line 30
		} else { // library marker davegut.kasaPlugs, line 31
			logDebug("distResp: Unhandled response = ${response}") // library marker davegut.kasaPlugs, line 32
		} // library marker davegut.kasaPlugs, line 33
	} else if (response["smartlife.iot.dimmer"]) { // library marker davegut.kasaPlugs, line 34
		if (response["smartlife.iot.dimmer"].get_dimmer_parameters) { // library marker davegut.kasaPlugs, line 35
			setDimmerConfig(response["smartlife.iot.dimmer"]) // library marker davegut.kasaPlugs, line 36
		} else { // library marker davegut.kasaPlugs, line 37
			logDebug("distResp: Unhandled response: ${response["smartlife.iot.dimmer"]}") // library marker davegut.kasaPlugs, line 38
		} // library marker davegut.kasaPlugs, line 39
	} else if (response.emeter) { // library marker davegut.kasaPlugs, line 40
		distEmeter(response.emeter) // library marker davegut.kasaPlugs, line 41
	} else if (response.cnCloud) { // library marker davegut.kasaPlugs, line 42
		setBindUnbind(response.cnCloud) // library marker davegut.kasaPlugs, line 43
	} else { // library marker davegut.kasaPlugs, line 44
		logDebug("distResp: Unhandled response = ${response}") // library marker davegut.kasaPlugs, line 45
	} // library marker davegut.kasaPlugs, line 46
} // library marker davegut.kasaPlugs, line 47

//	6.7.2 Change C. Break up combined setRelayState and getSysinfo. // library marker davegut.kasaPlugs, line 49
//	Then use distribute response to send getSysinfo on valid response. // library marker davegut.kasaPlugs, line 50
def setRelayState(onOff) { // library marker davegut.kasaPlugs, line 51
	logDebug("setRelayState: [switch: ${onOff}]") // library marker davegut.kasaPlugs, line 52
	if (getDataValue("plugNo") == null) { // library marker davegut.kasaPlugs, line 53
		sendCmd("""{"system":{"set_relay_state":{"state":${onOff}}}}""") // library marker davegut.kasaPlugs, line 54
	} else { // library marker davegut.kasaPlugs, line 55
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaPlugs, line 56
				""""system":{"set_relay_state":{"state":${onOff}}}}""") // library marker davegut.kasaPlugs, line 57
	} // library marker davegut.kasaPlugs, line 58
	runInMillis(100, getSysinfo) // library marker davegut.kasaPlugs, line 59
} // library marker davegut.kasaPlugs, line 60

//	6.7.2 Change C. Split command and status request. Status request in distResp. // library marker davegut.kasaPlugs, line 62
def setLedOff(onOff) { // library marker davegut.kasaPlugs, line 63
	logDebug("setLedOff: [ledOff: ${onOff}]") // library marker davegut.kasaPlugs, line 64
		sendCmd("""{"system":{"set_led_off":{"off":${onOff}}}}""") // library marker davegut.kasaPlugs, line 65
//		sendCmd("""{"system":{"set_led_off":{"off":${onOff}},"get_sysinfo":{}}}""") // library marker davegut.kasaPlugs, line 66
} // library marker davegut.kasaPlugs, line 67

// ~~~~~ end include (1151) davegut.kasaPlugs ~~~~~
