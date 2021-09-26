/*	Kasa Device Driver Series

		Copyright Dave Gutheinz

License Information:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md

===== Version 6.4.0.5 =====
1.  Switched to Library-based development.  Groovy file will have a lot of comments
	related to importing the library methods into the driver for publication.
===================================================================================================*/
def driverVer() { return "6.4.0.5" }
def type() { return "Light Strip" }

metadata {
	definition (name: "Kasa Light Strip",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/DeviceDrivers/LightStrip.groovy"
			   ) {
        capability "Light"
		capability "Switch"
		capability "Switch Level"
		capability "Change Level"
 		capability "Refresh"
		capability "Actuator"
		capability "Color Temperature"
		capability "Color Mode"
		capability "Color Control"
		capability "Light Effects"
		command "effectSet", [[
			name: "Name for effect.", 
			type: "STRING"]]
		command "effectCreate"
		command "effectDelete", [[
			name: "Name for effect to delete.", 
			type: "STRING"]]
		//	Poll Interval Function
		command "setPollInterval", [[
			name: "Poll Interval in seconds",
			constraints: ["default", "5 seconds", "10 seconds", "15 seconds",
						  "30 seconds", "1 minute", "5 minutes",  "10 minutes",
						  "30 minutes"],
			type: "ENUM"]]
		//	Energy Monitor
		capability "Power Meter"
		capability "Energy Meter"
		//	Psuedo Capability Energy Statistics
		attribute "currMonthTotal", "number"
		attribute "currMonthAvg", "number"
		attribute "lastMonthTotal", "number"
		attribute "lastMonthAvg", "number"
		//	SCommunications Attributes
		attribute "connection", "string"
		attribute "commsError", "string"
		//	Psuedo capability Light Presets
		command "bulbPresetCreate", [[
			name: "Name for preset.", 
			type: "STRING"]]
		command "bulbPresetDelete", [[
			name: "Name for preset.", 
			type: "STRING"]]
		command "bulbPresetSet", [[
			name: "Name for preset.", 
			type: "STRING"],[
			name: "Transition Time (seconds).", 
			type: "STRING"]]
		attribute "bulbPreset", "string"
	}

	preferences {
		input ("emFunction", "bool", 
				   title: "Enable Energy Monitor", 
				   defaultValue: false)
		input ("transition_Time", "num",
			   title: "Default Transition time in sec.",
			   defaultValue: 0)
		input ("syncEffects", "bool",
			   title: "Sync Effect Preset Data",
			   defaultValue: false)
		input ("syncBulbs", "bool",
			   title: "Sync Bulb Preset Data",
			   defaultValue: false)
		input ("debug", "bool",
			   title: "Debug logging, 30 min.", 
			   defaultValue: false)
		input ("descriptionText", "bool", 
			   title: "Description Text Logging", 
			   defaultValue: true)
		input ("bind", "bool",
			   title: "Kasa Cloud Binding",
			   defalutValue: true)
		if (bind && parent.useKasaCloud) {
			input ("useCloud", "bool",
				   title: "Use Kasa Cloud for device control",
				   defaultValue: false)
		}
		input ("rebootDev", "bool",
			   title: "Reboot Device",
			   defaultValue: false)
	}
}

def installed() {
	def msg = "installed: "
	if (parent.useKasaCloud) {
		msg += "Installing as CLOUD device. "
		msg += "<b>\n\t\t\tif device is not bound to the cloud, the device may not "
		msg += "work! SEt Preferences 'Use Kasa Cloud for device control'.</b>"
		device.updateSetting("useCloud", [type:"bool", value: true])
		sendEvent(name: "connection", value: "CLOUD")
	} else {
		msg += "Installing as LAN device. "
		sendEvent(name: "connection", value: "LAN")
		device.updateSetting("useCloud", [type:"bool", value: false])
	}
	sendEvent(name: "commsError", value: "false")
	state.errorCount = 0
	state.pollInterval = "30 minutes"
	state.bulbPresets = [:]
	sendEvent(name: "lightEffects", value: [])
	updateDataValue("driverVersion", driverVer())
	runIn(2, updated)
	logInfo(msg)
}

def updated() {
	if (rebootDev) {
		logWarn("updated: ${rebootDevice()}")
		return
	}
	if (syncEffects) {
		logDebug("updated: ${syncEffectPresets()}")
		return
	}
	if (syncBulbs) {
		logDebug("updated: ${syncBulbPresets()}")
		return
	}
	unschedule()
	def updStatus = [:]
	if (debug) { runIn(1800, debugOff) }
	updStatus << [debug: debug]
	updStatus << [descriptionText: descriptionText]
	updStatus << [transition_Time: "${transition_Time} seconds"]
	state.errorCount = 0
	sendEvent(name: "commsError", value: "false")
	updStatus << [bind: bindUnbind()]
	updStatus << [emFunction: setupEmFunction()]
	updStatus << [pollInterval: setPollInterval()]
	updStatus << [driverVersion: updateDriverData()]
	log.info "[${type()}, ${driverVer()}, ${device.label}]  updated: ${updStatus}"
	runIn(3, refresh)
}

def updateDriverData() {
	def drvVer = getDataValue("driverVersion")
	if (drvVer == !driverVer()) {
		state.remove("lastLanCmd")
		state.remove("commsErrorText")
		if (!state.bulbPresets) { state.bulbPresets = [:] }
		def commsType = "LAN"
		if (useCloud == true) { commsType = "CLOUD" }
		setCommsData(comType)
		if (!state.bulbPresets) { state.bulbPresets = [:] }
		updateDataValue("driverVersion", driverVer())
	}
	return driverVer()
}

//	===== Command Methods =====
def on() {
	logDebug("on: transition time = ${transition_Time}")
	def transTime = 1000 * transition_Time.toInteger()
	sendCmd("""{"smartlife.iot.lightStrip":""" +
			"""{"set_light_state":{"on_off":1,"transition_period":${transTime}}}}""")
}

def off() {
	logDebug("off: transition time = ${transition_Time}")
	def transTime = 1000 * transition_Time.toInteger()
	sendCmd("""{"smartlife.iot.lightStrip":""" +
			"""{"set_light_state":{"on_off":0,"transition_period":${transTime}}}}""")
}

def setLevel(level, transTime = transition_Time.toInteger()) {
	if (level < 0) { level = 0 }
	else if (level > 100) { level = 100 }
	logDebug("setLevel: ${level} // ${transTime}")
	transTime = 1000*transTime
	sendCmd("""{"smartlife.iot.lightStrip":{"set_light_state":{"ignore_default":1,"on_off":1,""" +
			""""brightness":${level},"transition_period":${transTime}}}}""")
}

def setColorTemperature(colorTemp, level = device.currentValue("level"), transTime = transition_Time.toInteger()) {
	logDebug("setColorTemperature: ${colorTemp} // ${level} // ${transTime}")
	transTime = 1000 * transTime
	if (colorTemp < 1000) { colorTemp = 1000 }
	else if (colorTemp > 12000) { colorTemp = 12000 }
	def hsvData = getCtHslValue(colorTemp)
	state.currentCT = colorTemp
	sendCmd("""{"smartlife.iot.lightStrip":{"set_light_state":{"ignore_default":1,""" +
			""""on_off":1,"brightness":${level},"color_temp":0,"hue":${hsvData.hue},""" +
			""""saturation":${hsvData.saturation},"transition_period":${transTime}}}}""")
}

def setHue(hue) {
	logDebug("setHue:  hue = ${hue}")
	setColor([hue: hue])
}

def setSaturation(saturation) {
	logDebug("setSaturation: saturation = ${saturation}")
	setColor([saturation: saturation])
}

def setColor(Map color) {
	logDebug("setColor:  ${color} // ${transition_Time}")
	def transTime = 1000 * transition_Time.toInteger()
	if (color == null) {
		LogWarn("setColor: Color map is null. Command not executed.")
		return
	}
	def level = device.currentValue("level")
	if (color.level) { level = color.level }
	def hue = device.currentValue("hue")
	if (color.hue || color.hue == 0) { hue = color.hue.toInteger() }
	def saturation = device.currentValue("saturation")
	if (color.saturation || color.saturation == 0) { saturation = color.saturation }
	hue = Math.round(0.49 + hue * 3.6).toInteger()
	if (hue < 0 || hue > 360 || saturation < 0 || saturation > 100 || level < 0 || level > 100) {
		logWarn("setColor: Entered hue, saturation, or level out of range! (H:${hue}, S:${saturation}, L:${level}")
        return
    }
	state.currentCT = 0
	sendCmd("""{"smartlife.iot.lightStrip":{"set_light_state":{"ignore_default":1,"on_off":1,"brightness":${level},""" +
			""""color_temp":0,"hue":${hue},"saturation":${saturation},"transition_period":${transTime}}}}""")
}

def refresh() {
	logDebug("refresh")
	sendCmd("""{"system":{"get_sysinfo":{}}}""")
}

def poll() {
	sendCmd("""{"system":{"get_sysinfo":{}}}""")
}

//	===== Capability Light Effect =====
def setEffect(index) {
	logDebug("setEffect: effNo = ${index}")
	index = index.toInteger()
	def effectPresets = state.effectPresets
	if (effectPresets == []) {
		logWarn("setEffect: effectPresets database is empty.")
		return
	}
	def effData = effectPresets[index]
	sendEffect(effData)						 
}

def setPreviousEffect() {
	def effectPresets = state.effectPresets
	if (device.currentValue("colorMode") != "EFFECTS" || effectPresets == []) {
		logWarn("setPreviousEffect: Not available. Either not in Effects or data is empty.")
		return
	}
	def effName = device.currentValue("effectName").trim()
	def index = effectPresets.findIndexOf { it.name == effName }
	if (index == -1) {
		logWarn("setPreviousEffect: ${effName} not found in effectPresets.")
	} else {
		def size = effectPresets.size()
		if (index == 0) { index = size - 1 }
		else { index = index-1 }
		def effData = effectPresets[index]
		sendEffect(effData)						 
	}
}

def setNextEffect() {
	def effectPresets = state.effectPresets
	if (device.currentValue("colorMode") != "EFFECTS" || effectPresets == []) {
		logWarn("setNextEffect: Not available. Either not in Effects or data is empty.")
		return
	}
	def effName = device.currentValue("effectName").trim()
	def index = effectPresets.findIndexOf { it.name == effName }
	if (index == -1) {
		logWarn("setNextEffect: ${effName} not found in effectPresets.")
	} else {
		def size = effectPresets.size()
		if (index == size - 1) { index = 0 }
		else { index = index + 1 }
		def effData = effectPresets[index]
		sendEffect(effData)						 
	}
}

def effectSet(effName) {
	if (state.effectPresets == []) {
		logWarn("effectSet: effectPresets database is empty.")
		return
	}
	effName = effName.trim()
	logDebug("effectSet: ${effName}.")
	def effData = state.effectPresets.find { it.name == effName }
	if (effData == null) {
		logWarn("effectSet: ${effName} not found.")
		return
	}
	sendEffect(effData)
}

def sendEffect(effData) {
	effData = new groovy.json.JsonBuilder(effData).toString()
	sendCmd("""{"smartlife.iot.lighting_effect":{"set_lighting_effect":""" +
			"""${effData}},"context":{"source":"<id>"}}""")
}

def effectCreate() {
	state.createEffect = true
	sendCmd("""{"smartlife.iot.lighting_effect":{"get_lighting_effect":{}}}""")
}

def effectDelete(effName) {
	sendEvent(name: "lightEffects", value: [])
	effName = effName.trim()
	def index = state.effectPresets.findIndexOf { it.name == effName }
	if (index == -1 || nameIndex == -1) {
		logWarn("effectDelete: ${effName} not in effectPresets!")
	} else {
		state.effectPresets.remove(index)
		resetLightEffects()
	}
	logDebug("effectDelete: deleted effect ${effName}")
}

def resetLightEffects() {
	if (state.effectsPresets != []) {
		def lightEffects = []
		state.effectPresets.each{
			def name = """ "${it.name}" """
			lightEffects << name
		}
		sendEvent(name: "lightEffects", value: lightEffects)
	}
	return "Updated lightEffects list"
}

def parseEffect(resp) {
	logDebug("parseEffect: ${resp}")
	if (resp.get_lighting_effect) {
		def effData = resp.get_lighting_effect
		def effName = effData.name
		if (state.createEffect == true) {
			def existngEffect = state.effectPresets.find { it.name == effName }
			if (existngEffect == null) {
				state.effectPresets << effData
				resetLightEffects()
				logDebug("parseEffece: ${effName} added to effectPresets")
			} else {
				logWarn("parseEffect: ${effName} already exists.")
			}
			state.remove("createEffect")
		}
		refresh()
	} else {
		if (resp.set_lighting_effect.err_code != 0) {
			logWarn("parseEffect: Error setting effect.")
		}
		sendCmd("""{"smartlife.iot.lighting_effect":{"get_lighting_effect":{}}}""")
	}
}

def syncEffectPresets() {
	device.updateSetting("syncEffects", [type:"bool", value: false])
	parent.resetStates(device.deviceNetworkId)
	state.effectPresets.each{
		def effData = it
		parent.syncEffectPreset(effData, device.deviceNetworkId)
		pauseExecution(1000)
	}
	return "Synching Event Presets with all Kasa Light Strips."
}

def resetStates() { state.effectPresets = [] }

def updateEffectPreset(effData) {
	logDebug("updateEffectPreset: ${effData.name}")
	state.effectPresets << effData
	runIn(5, resetLightEffects)
}

//	===== Capability Bulb Presets =====
def bulbPresetCreate(psName) {
	if (!state.bulbPresets) { state.bulbPresets = [:] }
	psName = psName.trim()
	def psData = [:]
	psData["hue"] = device.currentValue("hue")
	psData["saturation"] = device.currentValue("saturation")
	psData["level"] = device.currentValue("level")
	def colorTemp = device.currentValue("colorTemperature")
	if (colorTemp == null) { colorTemp = 0 }
	psData["colTemp"] = colorTemp
	logDebug("bulbPresetCreate: ${psName}, ${psData}")
	state.bulbPresets << ["${psName}": psData]
}

def bulbPresetDelete(psName) {
	psName = psName.trim()
	logDebug("bulbPresetDelete: ${psName}")
	def presets = state.bulbPresets
	if (presets.toString().contains(psName)) {
		presets.remove(psName)
	} else {
		logWarn("bulbPresetDelete: ${psName} is not a valid name.")
	}
}

def bulbPresetSet(psName, transTime = transition_Time) {
	psName = psName.trim()
	transTime = 1000 * transTime.toInteger()
	if (state.bulbPresets."${psName}") {
		def psData = state.bulbPresets."${psName}"
		logDebug("bulbPresetSet: ${psData}, transTime = ${transTime}")
		if (psData.colTemp.toInteger() != 0) {
			setColorTemperature(psData.colTemp, psData.level, transTime = transition_Time.toInteger())
		} else {
			def hue = psData.hue
			hue = Math.round(0.49 + hue * 3.6).toInteger()
			sendCmd("""{"smartlife.iot.lightStrip":{"set_light_state":{"ignore_default":1,"on_off":1,"brightness":${psData.level},""" +
					""""hue":${hue},"color_temp":0,"saturation":${psData.saturation},"transition_period":${transTime}}}}""")
		}
	} else {
		logWarn("bulbPresetSet: ${psName} is not a valid name.")
	}
}

def syncBulbPresets() {
	device.updateSetting("syncBulbs", [type:"bool", value: false])
	parent.syncBulbPresets(state.bulbPresets, type())
	return "Synching Bulb Presets with all Kasa Bulbs."
}

def updatePresets(bulbPresets) {
	logDebug("updatePresets: Preset Bulb Data: ${bulbPresets}.")
	state.bulbPresets = bulbPresets
}

//	===== Update Data =====
def distResp(response) {
	if (response["smartlife.iot.lightStrip"]) {
		sendCmd("""{"system":{"get_sysinfo":{}}}""")
	} else if (response.system) {
		updateBulbData(response.system.get_sysinfo)
		if(emFunction) { getPower() }
	} else if (response["smartlife.iot.lighting_effect"]) {
		parseEffect(response["smartlife.iot.lighting_effect"])
	} else if (emFunction && response["smartlife.iot.common.emeter"]) {
		def month = new Date().format("M").toInteger()
		def emeterResp = response["smartlife.iot.common.emeter"]
		if (emeterResp.get_realtime) {
			setPower(emeterResp.get_realtime)
		} else if (emeterResp.get_monthstat.month_list.find { it.month == month }) {
			setEnergyToday(emeterResp.get_monthstat)
		} else if (emeterResp.get_monthstat.month_list.find { it.month == month - 1 }) {
			setLastMonth(emeterResp.get_monthstat)
		} else {
			logWarn("distResp: Unhandled response = ${response}")
		}
	} else if (response["smartlife.iot.common.cloud"]) {
		setBindUnbind(response["smartlife.iot.common.cloud"])
	} else if (response["smartlife.iot.common.system"]) {
		logWarn("distResp: Rebooting device")
	} else {
		logWarn("distResp: Unhandled response = ${response}")
	}
	resetCommsError()
}

def updateBulbData(status) {
	logDebug("updateBulbData: ${status}")
	if (status.err_code && status.err_code != 0) {
		logWarn("updateBulbData: ${status.err_msg}")
		return
	}
	def effect = status.lighting_effect_state
	status = status.light_state
	def deviceStatus = [:]
	def onOff = "on"
	if (status.on_off == 0) { onOff = "off" }
	deviceStatus << ["power" : onOff]
	def isChange = false
	if (device.currentValue("switch") != onOff) {
		sendEvent(name: "switch", value: onOff, type: "digital")
		isChange = true
	}
	if (onOff == "on") {
		def colorMode = "RGB"
		if (effect.enable == 1) { colorMode = "EFFECTS" }
		else if (state.currentCT > 0) { colorMode = "CT" }
		def hue = status.hue
		def hubHue = (hue / 3.6).toInteger()
		def saturation = status.saturation
		def level = status.brightness
		if (status.groups) {
			hue = status.groups[0][2]
			saturation = status.groups[0][3]
			level = status.groups[0][4]
		}
		def colorTemp = state.currentCT
		def color = " "
		def colorName = " "
		def effectName = " "
		if (colorMode == "EFFECTS") {
			effectName = effect.name
			level = effect.brightness
			hubHue = 0
			saturation = 0
			colorTemp = 0
		} else if (colorMode == "CT") {
			colorName = getCtName(colorTemp)
			hubHue = 0
			saturation = 0
		} else if (colorMode == "RGB") {
			colorName = getColorName(hue)
			color = "{hue: ${hubHue},saturation:${saturation},level: ${level}}"
		}
		if (level != device.currentValue("level")) {
			deviceStatus << ["level" : level]
			sendEvent(name: "level", value: level, unit: "%")
			isChange = true
		}
		if (effectName != device.currentValue("effectName")) {
			deviceStatus << ["effectName" : effectName]
			sendEvent(name: "effectName", value: effectName)
			isChange = true
		}
		if (device.currentValue("colorTemperature") != colorTemp) {
			isChange = true
			deviceStatus << ["colorTemp" : colorTemp]
			sendEvent(name: "colorTemperature", value: colorTemp)
		}
		if (color != device.currentValue("color")) {
			isChange = true
			deviceStatus << ["color" : color]
			sendEvent(name: "hue", value: hubHue)
			sendEvent(name: "saturation", value: saturation)
			sendEvent(name: "color", value: color)
		}
		if (device.currentValue("colorName") != colorName) {
			deviceStatus << ["colorName" : colorName]
			deviceStatus << ["colorMode" : colorMode]
			sendEvent(name: "colorMode", value: colorMode)
		    sendEvent(name: "colorName", value: colorName)
		}
	}
	if (isChange == true) {
		logInfo("updateBulbData: Status = ${deviceStatus}")
	}
}

//	===== includes =====



//	End of File
// ~~~~~ start include (33) davegut.bulbTools ~~~~~
/*	bulb tools // library marker davegut.bulbTools, line 1

		Copyright Dave Gutheinz // library marker davegut.bulbTools, line 3

License Information:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md // library marker davegut.bulbTools, line 5

This library contains tools that can be useful to bulb developers in the future. // library marker davegut.bulbTools, line 7
It is designed to be hardware and communications agnostic.  Each method, when  // library marker davegut.bulbTools, line 8
called, returns the data within the specifications below. // library marker davegut.bulbTools, line 9


===================================================================================================*/ // library marker davegut.bulbTools, line 12
library ( // library marker davegut.bulbTools, line 13
	name: "bulbTools", // library marker davegut.bulbTools, line 14
	namespace: "davegut", // library marker davegut.bulbTools, line 15
	author: "Dave Gutheinz", // library marker davegut.bulbTools, line 16
	description: "Color Bulb and Light Strip Tools", // library marker davegut.bulbTools, line 17
	category: "utility", // library marker davegut.bulbTools, line 18
	documentationLink: "" // library marker davegut.bulbTools, line 19
) // library marker davegut.bulbTools, line 20

//	===== Capability Change Level ===== // library marker davegut.bulbTools, line 22
def startLevelChange(direction) { // library marker davegut.bulbTools, line 23
	if (direction == "up") { levelUp() } // library marker davegut.bulbTools, line 24
	else { levelDown() } // library marker davegut.bulbTools, line 25
} // library marker davegut.bulbTools, line 26

def stopLevelChange() { // library marker davegut.bulbTools, line 28
	unschedule(levelUp) // library marker davegut.bulbTools, line 29
	unschedule(levelDown) // library marker davegut.bulbTools, line 30
} // library marker davegut.bulbTools, line 31

def levelUp() { // library marker davegut.bulbTools, line 33
	def curLevel = device.currentValue("level").toInteger() // library marker davegut.bulbTools, line 34
	if (curLevel == 100) { return } // library marker davegut.bulbTools, line 35
	def newLevel = curLevel + 4 // library marker davegut.bulbTools, line 36
	if (newLevel > 100) { newLevel = 100 } // library marker davegut.bulbTools, line 37
	setLevel(newLevel, 0) // library marker davegut.bulbTools, line 38
	runIn(1, levelUp) // library marker davegut.bulbTools, line 39
} // library marker davegut.bulbTools, line 40

def levelDown() { // library marker davegut.bulbTools, line 42
	def curLevel = device.currentValue("level").toInteger() // library marker davegut.bulbTools, line 43
	if (curLevel == 0) { return } // library marker davegut.bulbTools, line 44
	def newLevel = curLevel - 4 // library marker davegut.bulbTools, line 45
	if (newLevel < 0) { newLevel = 0 } // library marker davegut.bulbTools, line 46
	setLevel(newLevel, 0) // library marker davegut.bulbTools, line 47
	if (newLevel == 0) { off() } // library marker davegut.bulbTools, line 48
	runIn(1, levelDown) // library marker davegut.bulbTools, line 49
} // library marker davegut.bulbTools, line 50

//	===== Capability Color Control ===== // library marker davegut.bulbTools, line 52
def getCtName(temp){ // library marker davegut.bulbTools, line 53
    def value = temp.toInteger() // library marker davegut.bulbTools, line 54
    def colorName // library marker davegut.bulbTools, line 55
	if (value <= 2800) { colorName = "Incandescent" } // library marker davegut.bulbTools, line 56
	else if (value <= 3300) { colorName = "Soft White" } // library marker davegut.bulbTools, line 57
	else if (value <= 3500) { colorName = "Warm White" } // library marker davegut.bulbTools, line 58
	else if (value <= 4150) { colorName = "Moonlight" } // library marker davegut.bulbTools, line 59
	else if (value <= 5000) { colorName = "Horizon" } // library marker davegut.bulbTools, line 60
	else if (value <= 5500) { colorName = "Daylight" } // library marker davegut.bulbTools, line 61
	else if (value <= 6000) { colorName = "Electronic" } // library marker davegut.bulbTools, line 62
	else if (value <= 6500) { colorName = "Skylight" } // library marker davegut.bulbTools, line 63
	else { colorName = "Polar" } // library marker davegut.bulbTools, line 64
	return colorName // library marker davegut.bulbTools, line 65
} // library marker davegut.bulbTools, line 66

def getColorName(hue){ // library marker davegut.bulbTools, line 68
    def colorName // library marker davegut.bulbTools, line 69
	switch (hue){ // library marker davegut.bulbTools, line 70
		case 0..15: colorName = "Red" // library marker davegut.bulbTools, line 71
            break // library marker davegut.bulbTools, line 72
		case 16..45: colorName = "Orange" // library marker davegut.bulbTools, line 73
            break // library marker davegut.bulbTools, line 74
		case 46..75: colorName = "Yellow" // library marker davegut.bulbTools, line 75
            break // library marker davegut.bulbTools, line 76
		case 76..105: colorName = "Chartreuse" // library marker davegut.bulbTools, line 77
            break // library marker davegut.bulbTools, line 78
		case 106..135: colorName = "Green" // library marker davegut.bulbTools, line 79
            break // library marker davegut.bulbTools, line 80
		case 136..165: colorName = "Spring" // library marker davegut.bulbTools, line 81
            break // library marker davegut.bulbTools, line 82
		case 166..195: colorName = "Cyan" // library marker davegut.bulbTools, line 83
            break // library marker davegut.bulbTools, line 84
		case 196..225: colorName = "Azure" // library marker davegut.bulbTools, line 85
            break // library marker davegut.bulbTools, line 86
		case 226..255: colorName = "Blue" // library marker davegut.bulbTools, line 87
            break // library marker davegut.bulbTools, line 88
		case 256..285: colorName = "Violet" // library marker davegut.bulbTools, line 89
            break // library marker davegut.bulbTools, line 90
		case 286..315: colorName = "Magenta" // library marker davegut.bulbTools, line 91
            break // library marker davegut.bulbTools, line 92
		case 316..345: colorName = "Rose" // library marker davegut.bulbTools, line 93
            break // library marker davegut.bulbTools, line 94
		case 346..360: colorName = "Red" // library marker davegut.bulbTools, line 95
            break // library marker davegut.bulbTools, line 96
		default: // library marker davegut.bulbTools, line 97
			logWarn("setRgbData: Unknown.") // library marker davegut.bulbTools, line 98
			colorName = "Unknown" // library marker davegut.bulbTools, line 99
    } // library marker davegut.bulbTools, line 100
	return colorName // library marker davegut.bulbTools, line 101
} // library marker davegut.bulbTools, line 102

//	Capability Color Temperature for RGB Devices ===== // library marker davegut.bulbTools, line 104
def getCtHslValue(kelvin) { // library marker davegut.bulbTools, line 105
	def temp = kelvin/100 // library marker davegut.bulbTools, line 106
	def red // library marker davegut.bulbTools, line 107
	def green // library marker davegut.bulbTools, line 108
	def blue // library marker davegut.bulbTools, line 109
	if( temp <= 66 ){  // library marker davegut.bulbTools, line 110
        red = 255 // library marker davegut.bulbTools, line 111
 		green = temp // library marker davegut.bulbTools, line 112
		green = 99.4708025861 * Math.log(green) - 161.1195681661; // library marker davegut.bulbTools, line 113
		if( temp <= 19){ // library marker davegut.bulbTools, line 114
			blue = 0 // library marker davegut.bulbTools, line 115
		} else { // library marker davegut.bulbTools, line 116
			blue = temp-10; // library marker davegut.bulbTools, line 117
			blue = 138.5177312231 * Math.log(blue) - 305.0447927307 // library marker davegut.bulbTools, line 118
		} // library marker davegut.bulbTools, line 119
	} else { // library marker davegut.bulbTools, line 120
		red = temp - 60 // library marker davegut.bulbTools, line 121
		red = 329.698727446 * Math.pow(red, -0.1332047592) // library marker davegut.bulbTools, line 122
		green = temp - 60 // library marker davegut.bulbTools, line 123
		green = 288.1221695283 * Math.pow(green, -0.0755148492 ) // library marker davegut.bulbTools, line 124
		blue = 255 // library marker davegut.bulbTools, line 125
	} // library marker davegut.bulbTools, line 126
	red = limitValue(red) // library marker davegut.bulbTools, line 127
	green = limitValue(green) // library marker davegut.bulbTools, line 128
	blue = limitValue(blue) // library marker davegut.bulbTools, line 129

	def hsvData = hubitat.helper.ColorUtils.rgbToHSV([red, green, blue]) // library marker davegut.bulbTools, line 131
	def hue = (0.5 + hsvData[0]).toInteger() // library marker davegut.bulbTools, line 132
	def saturation = (0.5 + hsvData[1]).toInteger() // library marker davegut.bulbTools, line 133
	def level = (0.5 + hsvData[2]).toInteger() // library marker davegut.bulbTools, line 134
	def hslData = [ // library marker davegut.bulbTools, line 135
		hue: hue, // library marker davegut.bulbTools, line 136
		saturation: saturation, // library marker davegut.bulbTools, line 137
		level: level // library marker davegut.bulbTools, line 138
		] // library marker davegut.bulbTools, line 139
	return hslData // library marker davegut.bulbTools, line 140
} // library marker davegut.bulbTools, line 141

def limitValue(value) { // library marker davegut.bulbTools, line 143
	value = value + 0.5 // library marker davegut.bulbTools, line 144
	if (value > 255) { value = 255 } // library marker davegut.bulbTools, line 145
	else if (value < 0) { value = 0 } // library marker davegut.bulbTools, line 146
	return value.toInteger() // library marker davegut.bulbTools, line 147
} // library marker davegut.bulbTools, line 148

def xxgetCtHslValue(kelvin) { // library marker davegut.bulbTools, line 150
	kelvin = 100 * Math.round(kelvin / 100) // library marker davegut.bulbTools, line 151
	switch(kelvin) { // library marker davegut.bulbTools, line 152
		case 1000: rgb= [255, 56, 0]; break // library marker davegut.bulbTools, line 153
		case 1100: rgb= [255, 71, 0]; break // library marker davegut.bulbTools, line 154
		case 1200: rgb= [255, 83, 0]; break // library marker davegut.bulbTools, line 155
		case 1300: rgb= [255, 93, 0]; break // library marker davegut.bulbTools, line 156
		case 1400: rgb= [255, 101, 0]; break // library marker davegut.bulbTools, line 157
		case 1500: rgb= [255, 109, 0]; break // library marker davegut.bulbTools, line 158
		case 1600: rgb= [255, 115, 0]; break // library marker davegut.bulbTools, line 159
		case 1700: rgb= [255, 121, 0]; break // library marker davegut.bulbTools, line 160
		case 1800: rgb= [255, 126, 0]; break // library marker davegut.bulbTools, line 161
		case 1900: rgb= [255, 131, 0]; break // library marker davegut.bulbTools, line 162
		case 2000: rgb= [255, 138, 18]; break // library marker davegut.bulbTools, line 163
		case 2100: rgb= [255, 142, 33]; break // library marker davegut.bulbTools, line 164
		case 2200: rgb= [255, 147, 44]; break // library marker davegut.bulbTools, line 165
		case 2300: rgb= [255, 152, 54]; break // library marker davegut.bulbTools, line 166
		case 2400: rgb= [255, 157, 63]; break // library marker davegut.bulbTools, line 167
		case 2500: rgb= [255, 161, 72]; break // library marker davegut.bulbTools, line 168
		case 2600: rgb= [255, 165, 79]; break // library marker davegut.bulbTools, line 169
		case 2700: rgb= [255, 169, 87]; break // library marker davegut.bulbTools, line 170
		case 2800: rgb= [255, 173, 94]; break // library marker davegut.bulbTools, line 171
		case 2900: rgb= [255, 177, 101]; break // library marker davegut.bulbTools, line 172
		case 3000: rgb= [255, 180, 107]; break // library marker davegut.bulbTools, line 173
		case 3100: rgb= [255, 184, 114]; break // library marker davegut.bulbTools, line 174
		case 3200: rgb= [255, 187, 120]; break // library marker davegut.bulbTools, line 175
		case 3300: rgb= [255, 190, 126]; break // library marker davegut.bulbTools, line 176
		case 3400: rgb= [255, 193, 132]; break // library marker davegut.bulbTools, line 177
		case 3500: rgb= [255, 196, 137]; break // library marker davegut.bulbTools, line 178
		case 3600: rgb= [255, 199, 143]; break // library marker davegut.bulbTools, line 179
		case 3700: rgb= [255, 201, 148]; break // library marker davegut.bulbTools, line 180
		case 3800: rgb= [255, 204, 153]; break // library marker davegut.bulbTools, line 181
		case 3900: rgb= [255, 206, 159]; break // library marker davegut.bulbTools, line 182
		case 4000: rgb= [100, 209, 200]; break // library marker davegut.bulbTools, line 183
		case 4100: rgb= [255, 211, 168]; break // library marker davegut.bulbTools, line 184
		case 4200: rgb= [255, 213, 173]; break // library marker davegut.bulbTools, line 185
		case 4300: rgb= [255, 215, 177]; break // library marker davegut.bulbTools, line 186
		case 4400: rgb= [255, 217, 182]; break // library marker davegut.bulbTools, line 187
		case 4500: rgb= [255, 219, 186]; break // library marker davegut.bulbTools, line 188
		case 4600: rgb= [255, 221, 190]; break // library marker davegut.bulbTools, line 189
		case 4700: rgb= [255, 223, 194]; break // library marker davegut.bulbTools, line 190
		case 4800: rgb= [255, 225, 198]; break // library marker davegut.bulbTools, line 191
		case 4900: rgb= [255, 227, 202]; break // library marker davegut.bulbTools, line 192
		case 5000: rgb= [255, 228, 206]; break // library marker davegut.bulbTools, line 193
		case 5100: rgb= [255, 230, 210]; break // library marker davegut.bulbTools, line 194
		case 5200: rgb= [255, 232, 213]; break // library marker davegut.bulbTools, line 195
		case 5300: rgb= [255, 233, 217]; break // library marker davegut.bulbTools, line 196
		case 5400: rgb= [255, 235, 220]; break // library marker davegut.bulbTools, line 197
		case 5500: rgb= [255, 236, 224]; break // library marker davegut.bulbTools, line 198
		case 5600: rgb= [255, 238, 227]; break // library marker davegut.bulbTools, line 199
		case 5700: rgb= [255, 239, 230]; break // library marker davegut.bulbTools, line 200
		case 5800: rgb= [255, 240, 233]; break // library marker davegut.bulbTools, line 201
		case 5900: rgb= [255, 242, 236]; break // library marker davegut.bulbTools, line 202
		case 6000: rgb= [255, 243, 239]; break // library marker davegut.bulbTools, line 203
		case 6100: rgb= [255, 244, 242]; break // library marker davegut.bulbTools, line 204
		case 6200: rgb= [255, 245, 245]; break // library marker davegut.bulbTools, line 205
		case 6300: rgb= [255, 246, 247]; break // library marker davegut.bulbTools, line 206
		case 6400: rgb= [255, 248, 251]; break // library marker davegut.bulbTools, line 207
		case 6500: rgb= [255, 249, 253]; break // library marker davegut.bulbTools, line 208
		case 6600: rgb= [254, 249, 255]; break // library marker davegut.bulbTools, line 209
		case 6700: rgb= [252, 247, 255]; break // library marker davegut.bulbTools, line 210
		case 6800: rgb= [249, 246, 255]; break // library marker davegut.bulbTools, line 211
		case 6900: rgb= [247, 245, 255]; break // library marker davegut.bulbTools, line 212
		case 7000: rgb= [245, 243, 255]; break // library marker davegut.bulbTools, line 213
		case 7100: rgb= [243, 242, 255]; break // library marker davegut.bulbTools, line 214
		case 7200: rgb= [240, 241, 255]; break // library marker davegut.bulbTools, line 215
		case 7300: rgb= [239, 240, 255]; break // library marker davegut.bulbTools, line 216
		case 7400: rgb= [237, 239, 255]; break // library marker davegut.bulbTools, line 217
		case 7500: rgb= [235, 238, 255]; break // library marker davegut.bulbTools, line 218
		case 7600: rgb= [233, 237, 255]; break // library marker davegut.bulbTools, line 219
		case 7700: rgb= [231, 236, 255]; break // library marker davegut.bulbTools, line 220
		case 7800: rgb= [230, 235, 255]; break // library marker davegut.bulbTools, line 221
		case 7900: rgb= [228, 234, 255]; break // library marker davegut.bulbTools, line 222
		case 8000: rgb= [227, 233, 255]; break // library marker davegut.bulbTools, line 223
		case 8100: rgb= [225, 232, 255]; break // library marker davegut.bulbTools, line 224
		case 8200: rgb= [224, 231, 255]; break // library marker davegut.bulbTools, line 225
		case 8300: rgb= [222, 230, 255]; break // library marker davegut.bulbTools, line 226
		case 8400: rgb= [221, 230, 255]; break // library marker davegut.bulbTools, line 227
		case 8500: rgb= [220, 229, 255]; break // library marker davegut.bulbTools, line 228
		case 8600: rgb= [218, 229, 255]; break // library marker davegut.bulbTools, line 229
		case 8700: rgb= [217, 227, 255]; break // library marker davegut.bulbTools, line 230
		case 8800: rgb= [216, 227, 255]; break // library marker davegut.bulbTools, line 231
		case 8900: rgb= [215, 226, 255]; break // library marker davegut.bulbTools, line 232
		case 9000: rgb= [214, 225, 255]; break // library marker davegut.bulbTools, line 233
		case 9100: rgb= [212, 225, 255]; break // library marker davegut.bulbTools, line 234
		case 9200: rgb= [211, 224, 255]; break // library marker davegut.bulbTools, line 235
		case 9300: rgb= [210, 223, 255]; break // library marker davegut.bulbTools, line 236
		case 9400: rgb= [209, 223, 255]; break // library marker davegut.bulbTools, line 237
		case 9500: rgb= [208, 222, 255]; break // library marker davegut.bulbTools, line 238
		case 9600: rgb= [207, 221, 255]; break // library marker davegut.bulbTools, line 239
		case 9700: rgb= [207, 221, 255]; break // library marker davegut.bulbTools, line 240
		case 9800: rgb= [206, 220, 255]; break // library marker davegut.bulbTools, line 241
		case 9900: rgb= [205, 220, 255]; break // library marker davegut.bulbTools, line 242
		case 10000: rgb= [207, 218, 255]; break // library marker davegut.bulbTools, line 243
		case 10100: rgb= [207, 218, 255]; break // library marker davegut.bulbTools, line 244
		case 10200: rgb= [206, 217, 255]; break // library marker davegut.bulbTools, line 245
		case 10300: rgb= [205, 217, 255]; break // library marker davegut.bulbTools, line 246
		case 10400: rgb= [204, 216, 255]; break // library marker davegut.bulbTools, line 247
		case 10500: rgb= [204, 216, 255]; break // library marker davegut.bulbTools, line 248
		case 10600: rgb= [203, 215, 255]; break // library marker davegut.bulbTools, line 249
		case 10700: rgb= [202, 215, 255]; break // library marker davegut.bulbTools, line 250
		case 10800: rgb= [202, 214, 255]; break // library marker davegut.bulbTools, line 251
		case 10900: rgb= [201, 214, 255]; break // library marker davegut.bulbTools, line 252
		case 11000: rgb= [200, 213, 255]; break // library marker davegut.bulbTools, line 253
		case 11100: rgb= [200, 213, 255]; break // library marker davegut.bulbTools, line 254
		case 11200: rgb= [199, 212, 255]; break // library marker davegut.bulbTools, line 255
		case 11300: rgb= [198, 212, 255]; break // library marker davegut.bulbTools, line 256
		case 11400: rgb= [198, 212, 255]; break // library marker davegut.bulbTools, line 257
		case 11500: rgb= [197, 211, 255]; break // library marker davegut.bulbTools, line 258
		case 11600: rgb= [197, 211, 255]; break // library marker davegut.bulbTools, line 259
		case 11700: rgb= [197, 210, 255]; break // library marker davegut.bulbTools, line 260
		case 11800: rgb= [196, 210, 255]; break // library marker davegut.bulbTools, line 261
		case 11900: rgb= [195, 210, 255]; break // library marker davegut.bulbTools, line 262
		case 12000: rgb= [195, 209, 255]; break // library marker davegut.bulbTools, line 263
		default: // library marker davegut.bulbTools, line 264
			logWarn("setRgbData: Unknown.") // library marker davegut.bulbTools, line 265
			colorName = "Unknown" // library marker davegut.bulbTools, line 266
	} // library marker davegut.bulbTools, line 267
	def hsvData = hubitat.helper.ColorUtils.rgbToHSV([rgb[0].toInteger(), rgb[1].toInteger(), rgb[2].toInteger()]) // library marker davegut.bulbTools, line 268
	def hue = (0.5 + hsvData[0]).toInteger() // library marker davegut.bulbTools, line 269
	def saturation = (0.5 + hsvData[1]).toInteger() // library marker davegut.bulbTools, line 270
	def level = (0.5 + hsvData[2]).toInteger() // library marker davegut.bulbTools, line 271
	def hslData = [ // library marker davegut.bulbTools, line 272
		hue: hue, // library marker davegut.bulbTools, line 273
		saturation: saturation, // library marker davegut.bulbTools, line 274
		level: level // library marker davegut.bulbTools, line 275
		] // library marker davegut.bulbTools, line 276
	return hslData // library marker davegut.bulbTools, line 277
} // library marker davegut.bulbTools, line 278

//	End of File // library marker davegut.bulbTools, line 280

// ~~~~~ end include (33) davegut.bulbTools ~~~~~

// ~~~~~ start include (1) davegut.kasaCommon ~~~~~
import groovy.json.JsonSlurper // library marker davegut.kasaCommon, line 1

library ( // library marker davegut.kasaCommon, line 3
	name: "kasaCommon", // library marker davegut.kasaCommon, line 4
	namespace: "davegut", // library marker davegut.kasaCommon, line 5
	author: "Dave Gutheinz", // library marker davegut.kasaCommon, line 6
	description: "Kasa common routines", // library marker davegut.kasaCommon, line 7
	category: "communications", // library marker davegut.kasaCommon, line 8
	documentationLink: "" // library marker davegut.kasaCommon, line 9
) // library marker davegut.kasaCommon, line 10

//	===== Communications ===== // library marker davegut.kasaCommon, line 12
def sendCmd(command, timeout = 3) { // library marker davegut.kasaCommon, line 13
	if (device.currentValue("connection") == "LAN") { // library marker davegut.kasaCommon, line 14
		sendLanCmd(command, timeout) // library marker davegut.kasaCommon, line 15
	} else if (device.currentValue("connection") == "CLOUD"){ // library marker davegut.kasaCommon, line 16
		sendKasaCmd(command) // library marker davegut.kasaCommon, line 17
	} else { // library marker davegut.kasaCommon, line 18
		logWarn("sendCmd: attribute connection not set.") // library marker davegut.kasaCommon, line 19
	} // library marker davegut.kasaCommon, line 20
} // library marker davegut.kasaCommon, line 21

def sendLanCmd(command, timeout = 3) { // library marker davegut.kasaCommon, line 23
	logDebug("sendLanCmd: command = ${command}") // library marker davegut.kasaCommon, line 24
	state.lastCommand = command // library marker davegut.kasaCommon, line 25
	runIn(timeout + 1, handleCommsError) // library marker davegut.kasaCommon, line 26
	def myHubAction = new hubitat.device.HubAction( // library marker davegut.kasaCommon, line 27
		outputXOR(command), // library marker davegut.kasaCommon, line 28
		hubitat.device.Protocol.LAN, // library marker davegut.kasaCommon, line 29
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, // library marker davegut.kasaCommon, line 30
		 destinationAddress: "${getDataValue("deviceIP")}:9999", // library marker davegut.kasaCommon, line 31
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING, // library marker davegut.kasaCommon, line 32
		 parseWarning: true, // library marker davegut.kasaCommon, line 33
		 timeout: timeout]) // library marker davegut.kasaCommon, line 34
	try { // library marker davegut.kasaCommon, line 35
		sendHubCommand(myHubAction) // library marker davegut.kasaCommon, line 36
	} catch (e) { // library marker davegut.kasaCommon, line 37
		def errMsg = "LAN Error = ${e}" // library marker davegut.kasaCommon, line 38
		logWarn("sendLanCmd: ${errMsg}]") // library marker davegut.kasaCommon, line 39
	} // library marker davegut.kasaCommon, line 40
} // library marker davegut.kasaCommon, line 41

def parse(message) { // library marker davegut.kasaCommon, line 43
	def resp = parseLanMessage(message) // library marker davegut.kasaCommon, line 44
	if (resp.type != "LAN_TYPE_UDPCLIENT") { // library marker davegut.kasaCommon, line 45
		def errMsg = "LAN Error = ${resp.type}" // library marker davegut.kasaCommon, line 46
		logWarn("parse: ${errMsg}]") // library marker davegut.kasaCommon, line 47
		return // library marker davegut.kasaCommon, line 48
	} // library marker davegut.kasaCommon, line 49
	def clearResp = inputXOR(resp.payload) // library marker davegut.kasaCommon, line 50
	if (clearResp.length() > 1022) { // library marker davegut.kasaCommon, line 51
		clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}" // library marker davegut.kasaCommon, line 52
	} // library marker davegut.kasaCommon, line 53
	def cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.kasaCommon, line 54
	distResp(cmdResp) // library marker davegut.kasaCommon, line 55
} // library marker davegut.kasaCommon, line 56

def sendKasaCmd(command) { // library marker davegut.kasaCommon, line 58
	logDebug("sendKasaCmd: ${command}") // library marker davegut.kasaCommon, line 59
	state.lastCommand = command // library marker davegut.kasaCommon, line 60
	runIn(4, handleCommsError) // library marker davegut.kasaCommon, line 61
	def cmdResponse = "" // library marker davegut.kasaCommon, line 62
	def cmdBody = [ // library marker davegut.kasaCommon, line 63
		method: "passthrough", // library marker davegut.kasaCommon, line 64
		params: [ // library marker davegut.kasaCommon, line 65
			deviceId: getDataValue("deviceId"), // library marker davegut.kasaCommon, line 66
			requestData: "${command}" // library marker davegut.kasaCommon, line 67
		] // library marker davegut.kasaCommon, line 68
	] // library marker davegut.kasaCommon, line 69
	def sendCloudCmdParams = [ // library marker davegut.kasaCommon, line 70
		uri: "${parent.kasaCloudUrl}/?token=${parent.kasaToken}", // library marker davegut.kasaCommon, line 71
		requestContentType: 'application/json', // library marker davegut.kasaCommon, line 72
		contentType: 'application/json', // library marker davegut.kasaCommon, line 73
		headers: ['Accept':'application/json; version=1, */*; q=0.01'], // library marker davegut.kasaCommon, line 74
		timeout: 5, // library marker davegut.kasaCommon, line 75
		body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.kasaCommon, line 76
	] // library marker davegut.kasaCommon, line 77
	try { // library marker davegut.kasaCommon, line 78
		httpPostJson(sendCloudCmdParams) {resp -> // library marker davegut.kasaCommon, line 79
			if (resp.status == 200 && resp.data.error_code == 0) { // library marker davegut.kasaCommon, line 80
				def jsonSlurper = new groovy.json.JsonSlurper() // library marker davegut.kasaCommon, line 81
				distResp(jsonSlurper.parseText(resp.data.result.responseData)) // library marker davegut.kasaCommon, line 82
			} else { // library marker davegut.kasaCommon, line 83
				def errMsg = "CLOUD Error = ${resp.data}" // library marker davegut.kasaCommon, line 84
				logWarn("sendKasaCmd: ${errMsg}]") // library marker davegut.kasaCommon, line 85
			} // library marker davegut.kasaCommon, line 86
		} // library marker davegut.kasaCommon, line 87
	} catch (e) { // library marker davegut.kasaCommon, line 88
		def errMsg = "CLOUD Error = ${e}" // library marker davegut.kasaCommon, line 89
		logWarn("sendKasaCmd: ${errMsg}]") // library marker davegut.kasaCommon, line 90
	} // library marker davegut.kasaCommon, line 91
} // library marker davegut.kasaCommon, line 92

def handleCommsError() { // library marker davegut.kasaCommon, line 94
	def count = state.errorCount + 1 // library marker davegut.kasaCommon, line 95
	state.errorCount = count // library marker davegut.kasaCommon, line 96
	def message = "handleCommsError: Count: ${count}." // library marker davegut.kasaCommon, line 97
	if (count <= 3) { // library marker davegut.kasaCommon, line 98
		message += "\n\t\t\t Retransmitting command, try = ${count}" // library marker davegut.kasaCommon, line 99
		runIn(1, sendCmd, [data: state.lastCommand]) // library marker davegut.kasaCommon, line 100
	} else if (count == 4) { // library marker davegut.kasaCommon, line 101
		setCommsError() // library marker davegut.kasaCommon, line 102
		message += "\n\t\t\t Setting Comms Error." // library marker davegut.kasaCommon, line 103
	} // library marker davegut.kasaCommon, line 104
	logDebug(message) // library marker davegut.kasaCommon, line 105
} // library marker davegut.kasaCommon, line 106

def setCommsError() { // library marker davegut.kasaCommon, line 108
	def message = "setCommsError: Four consecutive errors.  Setting commsError to true." // library marker davegut.kasaCommon, line 109
	message += "\n\t\t<b>ErrorData = ${ErrorData}</b>." // library marker davegut.kasaCommon, line 110
	sendEvent(name: "commsError", value: "true") // library marker davegut.kasaCommon, line 111
	message += "\n\t\t${parent.fixConnection(device.currentValue("connection"))}" // library marker davegut.kasaCommon, line 112
	logWarn message // library marker davegut.kasaCommon, line 113
	runIn(2, refresh) // library marker davegut.kasaCommon, line 114
} // library marker davegut.kasaCommon, line 115

def resetCommsError() { // library marker davegut.kasaCommon, line 117
	unschedule(handleCommsError) // library marker davegut.kasaCommon, line 118
	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 119
	state.errorCount = 0 // library marker davegut.kasaCommon, line 120
} // library marker davegut.kasaCommon, line 121

//	===== Energy Monitor Methods ===== // library marker davegut.kasaCommon, line 123
def setupEmFunction() { // library marker davegut.kasaCommon, line 124
	if (emFunction) { // library marker davegut.kasaCommon, line 125
		sendEvent(name: "power", value: 0) // library marker davegut.kasaCommon, line 126
		sendEvent(name: "energy", value: 0) // library marker davegut.kasaCommon, line 127
		sendEvent(name: "currMonthTotal", value: 0) // library marker davegut.kasaCommon, line 128
		sendEvent(name: "currMonthAvg", value: 0) // library marker davegut.kasaCommon, line 129
		sendEvent(name: "lastMonthTotal", value: 0) // library marker davegut.kasaCommon, line 130
		sendEvent(name: "lastMonthAvg", value: 0) // library marker davegut.kasaCommon, line 131
		def start = Math.round(30 * Math.random()).toInteger() // library marker davegut.kasaCommon, line 132
		schedule("${start} */30 * * * ?", getEnergyToday) // library marker davegut.kasaCommon, line 133
		runIn(1, getEnergyToday) // library marker davegut.kasaCommon, line 134
		return "Initialized" // library marker davegut.kasaCommon, line 135
	} else if (device.currentValue("power") != null) { // library marker davegut.kasaCommon, line 136
		sendEvent(name: "power", value: 0) // library marker davegut.kasaCommon, line 137
		sendEvent(name: "energy", value: 0) // library marker davegut.kasaCommon, line 138
		sendEvent(name: "currMonthTotal", value: 0) // library marker davegut.kasaCommon, line 139
		sendEvent(name: "currMonthAvg", value: 0) // library marker davegut.kasaCommon, line 140
		sendEvent(name: "lastMonthTotal", value: 0) // library marker davegut.kasaCommon, line 141
		sendEvent(name: "lastMonthAvg", value: 0) // library marker davegut.kasaCommon, line 142
		if (type().contains("Multi")) { // library marker davegut.kasaCommon, line 143
			state.remove("powerPollInterval") // library marker davegut.kasaCommon, line 144
		} // library marker davegut.kasaCommon, line 145
		return "Disabled" // library marker davegut.kasaCommon, line 146
	} else { // library marker davegut.kasaCommon, line 147
		return "Disabled" // library marker davegut.kasaCommon, line 148
	} // library marker davegut.kasaCommon, line 149
} // library marker davegut.kasaCommon, line 150

def getPower() { // library marker davegut.kasaCommon, line 152
	if (!emFunction) { return } // library marker davegut.kasaCommon, line 153
	logDebug("getPower") // library marker davegut.kasaCommon, line 154
	if (type().contains("Multi")) { // library marker davegut.kasaCommon, line 155
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaCommon, line 156
				""""emeter":{"get_realtime":{}}}""") // library marker davegut.kasaCommon, line 157
	} else if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 158
		sendCmd("""{"smartlife.iot.common.emeter":{"get_realtime":{}}}""") // library marker davegut.kasaCommon, line 159
	} else { // library marker davegut.kasaCommon, line 160
		sendCmd("""{"emeter":{"get_realtime":{}}}""") // library marker davegut.kasaCommon, line 161
	} // library marker davegut.kasaCommon, line 162
} // library marker davegut.kasaCommon, line 163

def setPower(response) { // library marker davegut.kasaCommon, line 165
	def power = response.power // library marker davegut.kasaCommon, line 166
	if (power == null) { power = response.power_mw / 1000 } // library marker davegut.kasaCommon, line 167
	power = Math.round(10*(power))/10 // library marker davegut.kasaCommon, line 168
	def curPwr = device.currentValue("power") // library marker davegut.kasaCommon, line 169
	if (curPwr < 5 && (power > curPwr + 0.3 || power < curPwr - 0.3)) { // library marker davegut.kasaCommon, line 170
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W", type: "digital") // library marker davegut.kasaCommon, line 171
		logDebug("polResp: power = ${power}") // library marker davegut.kasaCommon, line 172
	} else if (power > curPwr + 5 || power < curPwr - 5) { // library marker davegut.kasaCommon, line 173
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W", type: "digital") // library marker davegut.kasaCommon, line 174
		logDebug("polResp: power = ${power}") // library marker davegut.kasaCommon, line 175
	} // library marker davegut.kasaCommon, line 176
} // library marker davegut.kasaCommon, line 177

def getEnergyToday() { // library marker davegut.kasaCommon, line 179
	if (!emFunction) { return } // library marker davegut.kasaCommon, line 180
	logDebug("getEnergyToday") // library marker davegut.kasaCommon, line 181
	def year = new Date().format("yyyy").toInteger() // library marker davegut.kasaCommon, line 182
	if (type().contains("Multi")) { // library marker davegut.kasaCommon, line 183
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaCommon, line 184
				""""emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaCommon, line 185
	} else if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 186
		sendCmd("""{"smartlife.iot.common.emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaCommon, line 187
	} else { // library marker davegut.kasaCommon, line 188
		sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaCommon, line 189
	} // library marker davegut.kasaCommon, line 190
} // library marker davegut.kasaCommon, line 191

def setEnergyToday(response) { // library marker davegut.kasaCommon, line 193
	logDebug("setEnergyToday: response = ${response}") // library marker davegut.kasaCommon, line 194
	def month = new Date().format("M").toInteger() // library marker davegut.kasaCommon, line 195
	def data = response.month_list.find { it.month == month } // library marker davegut.kasaCommon, line 196
	def energy = data.energy // library marker davegut.kasaCommon, line 197
	if (energy == null) { energy = data.energy_wh/1000 } // library marker davegut.kasaCommon, line 198
	energy -= device.currentValue("currMonthTotal") // library marker davegut.kasaCommon, line 199
	energy = Math.round(100*energy)/100 // library marker davegut.kasaCommon, line 200
	def currEnergy = device.currentValue("energy") // library marker davegut.kasaCommon, line 201
	if (currEnergy < energy + 0.05) { // library marker davegut.kasaCommon, line 202
		sendEvent(name: "energy", value: energy, descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaCommon, line 203
		logDebug("setEngrToday: [energy: ${energy}]") // library marker davegut.kasaCommon, line 204
	} // library marker davegut.kasaCommon, line 205
	setThisMonth(response) // library marker davegut.kasaCommon, line 206
} // library marker davegut.kasaCommon, line 207

def setThisMonth(response) { // library marker davegut.kasaCommon, line 209
	logDebug("setThisMonth: response = ${response}") // library marker davegut.kasaCommon, line 210
	def month = new Date().format("M").toInteger() // library marker davegut.kasaCommon, line 211
	def day = new Date().format("d").toInteger() // library marker davegut.kasaCommon, line 212
	def data = response.month_list.find { it.month == month } // library marker davegut.kasaCommon, line 213
	def totEnergy = data.energy // library marker davegut.kasaCommon, line 214
	if (totEnergy == null) {  // library marker davegut.kasaCommon, line 215
		totEnergy = data.energy_wh/1000 // library marker davegut.kasaCommon, line 216
	} // library marker davegut.kasaCommon, line 217
	totEnergy = Math.round(100*totEnergy)/100 // library marker davegut.kasaCommon, line 218
	def avgEnergy = 0 // library marker davegut.kasaCommon, line 219
	if (day != 1) {  // library marker davegut.kasaCommon, line 220
		avgEnergy = totEnergy /(day - 1)  // library marker davegut.kasaCommon, line 221
	} // library marker davegut.kasaCommon, line 222
	avgEnergy = Math.round(100*avgEnergy)/100 // library marker davegut.kasaCommon, line 223

	sendEvent(name: "currMonthTotal", value: totEnergy,  // library marker davegut.kasaCommon, line 225
			  descriptionText: "KiloWatt Hours", unit: "kWh") // library marker davegut.kasaCommon, line 226
	sendEvent(name: "currMonthAvg", value: avgEnergy,  // library marker davegut.kasaCommon, line 227
			  descriptionText: "KiloWatt Hours per Day", unit: "kWh/D") // library marker davegut.kasaCommon, line 228
	logDebug("setThisMonth: Energy stats set to ${totEnergy} // ${avgEnergy}") // library marker davegut.kasaCommon, line 229
	if (month != 1) { // library marker davegut.kasaCommon, line 230
		setLastMonth(response) // library marker davegut.kasaCommon, line 231
	} else { // library marker davegut.kasaCommon, line 232
		def year = new Date().format("yyyy").toInteger() // library marker davegut.kasaCommon, line 233
		if (type().contains("Multi")) { // library marker davegut.kasaCommon, line 234
			sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaCommon, line 235
					""""emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaCommon, line 236
		} else if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 237
			sendCmd("""{"smartlife.iot.common.emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaCommon, line 238
		} else { // library marker davegut.kasaCommon, line 239
			sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaCommon, line 240
		} // library marker davegut.kasaCommon, line 241
	} // library marker davegut.kasaCommon, line 242
} // library marker davegut.kasaCommon, line 243

def setLastMonth(response) { // library marker davegut.kasaCommon, line 245
	logDebug("setLastMonth: response = ${response}") // library marker davegut.kasaCommon, line 246
	def year = new Date().format("yyyy").toInteger() // library marker davegut.kasaCommon, line 247
	def month = new Date().format("M").toInteger() // library marker davegut.kasaCommon, line 248
	def day = new Date().format("d").toInteger() // library marker davegut.kasaCommon, line 249
	def lastMonth // library marker davegut.kasaCommon, line 250
	if (month == 1) { // library marker davegut.kasaCommon, line 251
		lastMonth = 12 // library marker davegut.kasaCommon, line 252
	} else { // library marker davegut.kasaCommon, line 253
		lastMonth = month - 1 // library marker davegut.kasaCommon, line 254
	} // library marker davegut.kasaCommon, line 255
	def monthLength // library marker davegut.kasaCommon, line 256
	switch(lastMonth) { // library marker davegut.kasaCommon, line 257
		case 4: // library marker davegut.kasaCommon, line 258
		case 6: // library marker davegut.kasaCommon, line 259
		case 9: // library marker davegut.kasaCommon, line 260
		case 11: // library marker davegut.kasaCommon, line 261
			monthLength = 30 // library marker davegut.kasaCommon, line 262
			break // library marker davegut.kasaCommon, line 263
		case 2: // library marker davegut.kasaCommon, line 264
			monthLength = 28 // library marker davegut.kasaCommon, line 265
			if (year == 2020 || year == 2024 || year == 2028) { monthLength = 29 } // library marker davegut.kasaCommon, line 266
			break // library marker davegut.kasaCommon, line 267
		default: // library marker davegut.kasaCommon, line 268
			monthLength = 31 // library marker davegut.kasaCommon, line 269
	} // library marker davegut.kasaCommon, line 270
	def data = response.month_list.find { it.month == lastMonth } // library marker davegut.kasaCommon, line 271
	def totEnergy // library marker davegut.kasaCommon, line 272
	if (data == null) { // library marker davegut.kasaCommon, line 273
		totEnergy = 0 // library marker davegut.kasaCommon, line 274
	} else { // library marker davegut.kasaCommon, line 275
		totEnergy = data.energy // library marker davegut.kasaCommon, line 276
		if (totEnergy == null) {  // library marker davegut.kasaCommon, line 277
			totEnergy = data.energy_wh/1000 // library marker davegut.kasaCommon, line 278
		} // library marker davegut.kasaCommon, line 279
		totEnergy = Math.round(100*totEnergy)/100 // library marker davegut.kasaCommon, line 280
	} // library marker davegut.kasaCommon, line 281
	def avgEnergy = 0 // library marker davegut.kasaCommon, line 282
	if (day !=1) { // library marker davegut.kasaCommon, line 283
		avgEnergy = totEnergy /(day - 1) // library marker davegut.kasaCommon, line 284
	} // library marker davegut.kasaCommon, line 285
	avgEnergy = Math.round(100*avgEnergy)/100 // library marker davegut.kasaCommon, line 286
	sendEvent(name: "lastMonthTotal", value: totEnergy,  // library marker davegut.kasaCommon, line 287
			  descriptionText: "KiloWatt Hours", unit: "kWh") // library marker davegut.kasaCommon, line 288
	sendEvent(name: "lastMonthAvg", value: avgEnergy,  // library marker davegut.kasaCommon, line 289
			  descriptionText: "KiloWatt Hoursper Day", unit: "kWh/D") // library marker davegut.kasaCommon, line 290
	logDebug("setLastMonth: Energy stats set to ${totEnergy} // ${avgEnergy}") // library marker davegut.kasaCommon, line 291
} // library marker davegut.kasaCommon, line 292

//	===== Preference Methods ===== // library marker davegut.kasaCommon, line 294
def setPollInterval(interval = state.pollInterval) { // library marker davegut.kasaCommon, line 295
	if (interval == "default" || interval == "off") { // library marker davegut.kasaCommon, line 296
		interval = "30 minutes" // library marker davegut.kasaCommon, line 297
	} else if (useCloud && interval.contains("sec")) { // library marker davegut.kasaCommon, line 298
		interval = "1 minute" // library marker davegut.kasaCommon, line 299
	} // library marker davegut.kasaCommon, line 300
	state.pollInterval = interval // library marker davegut.kasaCommon, line 301
	def pollInterval = interval.substring(0,2).toInteger() // library marker davegut.kasaCommon, line 302
	if (interval.contains("sec")) { // library marker davegut.kasaCommon, line 303
		def start = Math.round((pollInterval-1) * Math.random()).toInteger() // library marker davegut.kasaCommon, line 304
		schedule("${start}/${pollInterval} * * * * ?", "poll") // library marker davegut.kasaCommon, line 305
		state.pollWarning = "Polling intervals of less than one minute can take high " + // library marker davegut.kasaCommon, line 306
			"resources and may impact hub performance." // library marker davegut.kasaCommon, line 307
	} else { // library marker davegut.kasaCommon, line 308
		def start = Math.round(59 * Math.random()).toInteger() // library marker davegut.kasaCommon, line 309
		schedule("${start} */${pollInterval} * * * ?", "poll") // library marker davegut.kasaCommon, line 310
		state.remove("pollWarning") // library marker davegut.kasaCommon, line 311
	} // library marker davegut.kasaCommon, line 312
	logDebug("setPollInterval: interval = ${interval}.") // library marker davegut.kasaCommon, line 313
	if(type().contains("Multi")) { // library marker davegut.kasaCommon, line 314
		parent.coordinate("pollInterval", interval, // library marker davegut.kasaCommon, line 315
						  getDataValue("deviceId"), getDataValue("plugNo")) // library marker davegut.kasaCommon, line 316
	} // library marker davegut.kasaCommon, line 317
	return interval // library marker davegut.kasaCommon, line 318
} // library marker davegut.kasaCommon, line 319

def rebootDevice() { // library marker davegut.kasaCommon, line 321
	logWarn("rebootDevice: User Commanded Reboot Device!") // library marker davegut.kasaCommon, line 322
	device.updateSetting("rebootDev", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 323
	if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 324
		sendCmd("""{"smartlife.iot.common.system":{"reboot":{"delay":1}}}""") // library marker davegut.kasaCommon, line 325
	} else { // library marker davegut.kasaCommon, line 326
		sendCmd("""{"system":{"reboot":{"delay":1}}}""") // library marker davegut.kasaCommon, line 327
	} // library marker davegut.kasaCommon, line 328
	pauseExecution(10000) // library marker davegut.kasaCommon, line 329
	return "REBOOTING DEVICE" // library marker davegut.kasaCommon, line 330
} // library marker davegut.kasaCommon, line 331

def bindUnbind() { // library marker davegut.kasaCommon, line 333
	def meth = "cnCloud" // library marker davegut.kasaCommon, line 334
	if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 335
		meth = "smartlife.iot.common.cloud" // library marker davegut.kasaCommon, line 336
	} // library marker davegut.kasaCommon, line 337
	def message // library marker davegut.kasaCommon, line 338
	if (bind == null) { // library marker davegut.kasaCommon, line 339
		sendLanCmd("""{"${meth}":{"get_info":{}}}""", 4) // library marker davegut.kasaCommon, line 340
		message = "Updating to current device value" // library marker davegut.kasaCommon, line 341
	} else if (bind) { // library marker davegut.kasaCommon, line 342
		if (!parent.useKasaCloud || parent.userName == null || parent.userPassword == null) { // library marker davegut.kasaCommon, line 343
			message = "useKasaCtr, userName or userPassword not set" // library marker davegut.kasaCommon, line 344
			sendLanCmd("""{"${meth}":{"get_info":{}}}""", 4) // library marker davegut.kasaCommon, line 345
		} else { // library marker davegut.kasaCommon, line 346
			message = "Sending bind command" // library marker davegut.kasaCommon, line 347
			sendLanCmd("""{"${meth}":{"bind":{"username":"${parent.userName}",""" + // library marker davegut.kasaCommon, line 348
					""""password":"${parent.userPassword}"}},""" + // library marker davegut.kasaCommon, line 349
					""""${meth}":{"get_info":{}}}""", 4) // library marker davegut.kasaCommon, line 350
		} // library marker davegut.kasaCommon, line 351
	} else if (!bind) { // library marker davegut.kasaCommon, line 352
		if (!getDataValue("deviceIP")) { // library marker davegut.kasaCommon, line 353
			message = "Not set. No deviceIP" // library marker davegut.kasaCommon, line 354
			setCommsType(true) // library marker davegut.kasaCommon, line 355
		} else if (type() == "Light Strip") { // library marker davegut.kasaCommon, line 356
			message = "Not set. Light Strip" // library marker davegut.kasaCommon, line 357
			setCommsType(true) // library marker davegut.kasaCommon, line 358
		} else { // library marker davegut.kasaCommon, line 359
			message = "Sending unbind command" // library marker davegut.kasaCommon, line 360
			sendLanCmd("""{"${meth}":{"unbind":""},"${meth}":{"get_info":{}}}""", 4) // library marker davegut.kasaCommon, line 361
		} // library marker davegut.kasaCommon, line 362
	} // library marker davegut.kasaCommon, line 363
	pauseExecution(5000) // library marker davegut.kasaCommon, line 364
	return message // library marker davegut.kasaCommon, line 365
} // library marker davegut.kasaCommon, line 366

def setBindUnbind(cmdResp) { // library marker davegut.kasaCommon, line 368
	def bindState = true // library marker davegut.kasaCommon, line 369
	if (cmdResp.get_info) { // library marker davegut.kasaCommon, line 370
		if (cmdResp.get_info.binded == 0) { bindState = false } // library marker davegut.kasaCommon, line 371
		logInfo("setBindUnbind: Bind status set to ${bindState}") // library marker davegut.kasaCommon, line 372
		setCommsType(bindState) // library marker davegut.kasaCommon, line 373
	} else if (cmdResp.bind.err_code == 0){ // library marker davegut.kasaCommon, line 374
		def meth = "cnCloud" // library marker davegut.kasaCommon, line 375
		if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 376
			meth = "smartlife.iot.common.cloud" // library marker davegut.kasaCommon, line 377
		} // library marker davegut.kasaCommon, line 378
		if (!device.contains("Multi") || getDataValue("plugNo") == "00") { // library marker davegut.kasaCommon, line 379
			sendLanCmd("""{"${meth}":{"get_info":{}}}""", 4) // library marker davegut.kasaCommon, line 380
		} else { // library marker davegut.kasaCommon, line 381
			logWarn("setBindUnbind: Multiplug Plug 00 not installed.") // library marker davegut.kasaCommon, line 382
		} // library marker davegut.kasaCommon, line 383
	} else { // library marker davegut.kasaCommon, line 384
		logWarn("setBindUnbind: Unhandled response: ${cmdResp}") // library marker davegut.kasaCommon, line 385
	} // library marker davegut.kasaCommon, line 386
} // library marker davegut.kasaCommon, line 387

def setCommsType(bindState) { // library marker davegut.kasaCommon, line 389
	def commsType = "LAN" // library marker davegut.kasaCommon, line 390
	def cloudCtrl = false // library marker davegut.kasaCommon, line 391
	state.lastCommand = """{"system":{"get_sysinfo":{}}}""" // library marker davegut.kasaCommon, line 392

	if (bindState == true && useCloud == true && parent.useKasaCloud &&  // library marker davegut.kasaCommon, line 394
		parent.userName && parent.userPassword) { // library marker davegut.kasaCommon, line 395
		state.remove("lastLanCmd") // library marker davegut.kasaCommon, line 396
		commsType = "CLOUD" // library marker davegut.kasaCommon, line 397
		cloudCtrl = true // library marker davegut.kasaCommon, line 398
	} // library marker davegut.kasaCommon, line 399
	def commsSettings = [bind: bindState, useCloud: cloudCtrl, commsType: commsType] // library marker davegut.kasaCommon, line 400
		device.updateSetting("bind", [type:"bool", value: bindState]) // library marker davegut.kasaCommon, line 401
		device.updateSetting("useCloud", [type:"bool", value: cloudCtrl]) // library marker davegut.kasaCommon, line 402
		sendEvent(name: "connection", value: "${commsType}") // library marker davegut.kasaCommon, line 403
	log.info "[${type()}, ${driverVer()}, ${device.label}]  setCommsType: ${commsSettings}" // library marker davegut.kasaCommon, line 404
	if (type().contains("Multi")) { // library marker davegut.kasaCommon, line 405
		def coordData = [:] // library marker davegut.kasaCommon, line 406
		coordData << [bind: bindState] // library marker davegut.kasaCommon, line 407
		coordData << [useCloud: cloudCtrl] // library marker davegut.kasaCommon, line 408
		coordData << [connection: commsType] // library marker davegut.kasaCommon, line 409
		parent.coordinate("commsData", coordData, getDataValue("deviceId"), getDataValue("plugNo")) // library marker davegut.kasaCommon, line 410
	} // library marker davegut.kasaCommon, line 411
	pauseExecution(1000) // library marker davegut.kasaCommon, line 412
} // library marker davegut.kasaCommon, line 413

def ledOn() { // library marker davegut.kasaCommon, line 415
	logDebug("ledOn: Setting LED to on") // library marker davegut.kasaCommon, line 416
	sendCmd("""{"system":{"set_led_off":{"off":0},""" + // library marker davegut.kasaCommon, line 417
			""""get_sysinfo":{}}}""", 4) // library marker davegut.kasaCommon, line 418
} // library marker davegut.kasaCommon, line 419

def ledOff() { // library marker davegut.kasaCommon, line 421
	logDebug("ledOff: Setting LED to off") // library marker davegut.kasaCommon, line 422
	sendCmd("""{"system":{"set_led_off":{"off":1},""" + // library marker davegut.kasaCommon, line 423
			""""get_sysinfo":{}}}""", 4) // library marker davegut.kasaCommon, line 424
} // library marker davegut.kasaCommon, line 425

//	===== Utility Methods ===== // library marker davegut.kasaCommon, line 427
private outputXOR(command) { // library marker davegut.kasaCommon, line 428
	def str = "" // library marker davegut.kasaCommon, line 429
	def encrCmd = "" // library marker davegut.kasaCommon, line 430
 	def key = 0xAB // library marker davegut.kasaCommon, line 431
	for (int i = 0; i < command.length(); i++) { // library marker davegut.kasaCommon, line 432
		str = (command.charAt(i) as byte) ^ key // library marker davegut.kasaCommon, line 433
		key = str // library marker davegut.kasaCommon, line 434
		encrCmd += Integer.toHexString(str) // library marker davegut.kasaCommon, line 435
	} // library marker davegut.kasaCommon, line 436
   	return encrCmd // library marker davegut.kasaCommon, line 437
} // library marker davegut.kasaCommon, line 438

private inputXOR(encrResponse) { // library marker davegut.kasaCommon, line 440
	String[] strBytes = encrResponse.split("(?<=\\G.{2})") // library marker davegut.kasaCommon, line 441
	def cmdResponse = "" // library marker davegut.kasaCommon, line 442
	def key = 0xAB // library marker davegut.kasaCommon, line 443
	def nextKey // library marker davegut.kasaCommon, line 444
	byte[] XORtemp // library marker davegut.kasaCommon, line 445
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommon, line 446
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.kasaCommon, line 447
		XORtemp = nextKey ^ key // library marker davegut.kasaCommon, line 448
		key = nextKey // library marker davegut.kasaCommon, line 449
		cmdResponse += new String(XORtemp) // library marker davegut.kasaCommon, line 450
	} // library marker davegut.kasaCommon, line 451
	return cmdResponse // library marker davegut.kasaCommon, line 452
} // library marker davegut.kasaCommon, line 453

def logTrace(msg){ // library marker davegut.kasaCommon, line 455
	log.trace "[${type()} / ${driverVer()} / ${device.label}]| ${msg}" // library marker davegut.kasaCommon, line 456
} // library marker davegut.kasaCommon, line 457

def logInfo(msg) { // library marker davegut.kasaCommon, line 459
	if (descriptionText == true) {  // library marker davegut.kasaCommon, line 460
		log.info "[${type()} / ${driverVer()} / ${device.label}]| ${msg}" // library marker davegut.kasaCommon, line 461
	} // library marker davegut.kasaCommon, line 462
} // library marker davegut.kasaCommon, line 463

def logDebug(msg){ // library marker davegut.kasaCommon, line 465
	if(debug == true) { // library marker davegut.kasaCommon, line 466
		log.debug "[${type()} / ${driverVer()} / ${device.label}]| ${msg}" // library marker davegut.kasaCommon, line 467
	} // library marker davegut.kasaCommon, line 468
} // library marker davegut.kasaCommon, line 469

def debugOff() { // library marker davegut.kasaCommon, line 471
	device.updateSetting("debug", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 472
	logInfo("debugLogOff: Debug logging is off.") // library marker davegut.kasaCommon, line 473
} // library marker davegut.kasaCommon, line 474

def logWarn(msg){ // library marker davegut.kasaCommon, line 476
	log.warn "[${type()} / ${driverVer()} / ${device.label}]| ${msg}" // library marker davegut.kasaCommon, line 477
} // library marker davegut.kasaCommon, line 478

//	End of File // library marker davegut.kasaCommon, line 480

// ~~~~~ end include (1) davegut.kasaCommon ~~~~~
