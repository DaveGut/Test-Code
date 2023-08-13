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
def type() { return "Light Strip" }

metadata {
	definition (name: "Kasa ${type()}",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/DeviceDrivers/LightStrip.groovy"
			   ) {
		capability "Configuration"
		capability "Light"
		capability "Switch"
		capability "Switch Level"
		capability "Change Level"
		capability "Refresh"
		capability "Actuator"
		capability "Color Temperature"
		capability "Color Mode"
		capability "Color Control"
		command "setRGB", [[
			name: "red,green,blue", 
			type: "STRING"]]
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
		capability "Light Effects"
		command "effectSet", [[
			name: "Name for effect.", 
			type: "STRING"]]
		command "effectCreate"
		command "effectDelete", [[
			name: "Name for effect to delete.", 
			type: "STRING"]]
		command "setPollInterval", [[
			name: "Poll Interval in seconds",
			constraints: ["default", "5 seconds", "10 seconds", "15 seconds",
						  "30 seconds", "1 minute", "5 minutes",  "10 minutes",
						  "30 minutes"],
			type: "ENUM"]]
		capability "Power Meter"
		capability "Energy Meter"
		attribute "currMonthTotal", "number"
		attribute "currMonthAvg", "number"
		attribute "lastMonthTotal", "number"
		attribute "lastMonthAvg", "number"
		//	Communications
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
		input ("syncBulbs", "bool",
			   title: "Sync Bulb Preset Data",
			   defaultValue: false)
		input ("syncEffects", "bool",
			   title: "Sync Effect Preset Data",
			   defaultValue: false)
		input ("emFunction", "bool", 
			   title: "Enable Energy Monitor", 
			   defaultValue: false)
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
	def instStatus= installCommon()
	state.bulbPresets = [:]
	state.effectPresets = []
		sendEvent(name: "lightEffects", value: [])
	logInfo("installed: ${instStatus}")
}

def updated() {
	def updStatus = updateCommon()
	if (syncBulbs) {
		updStatus << [syncBulbs: syncBulbPresets()]
	}
	if (syncEffects) {
		updStatus << [syncEffects: syncEffectPresets()]
	}
	updStatus << [transition_Time: "${transition_Time} seconds"]
	updStatus << [emFunction: setupEmFunction()]
	logInfo("updated: ${updStatus}")
}

def deviceConfigure() {
	if (!state.bulbPresets) { state.bulbPresets = [:] }
	if (!state.effectPresets) { state.effectPresets = [] }
}

//	==================================================
//	Basic Commands
//	==================================================
def on() { setLightOnOff(1, transition_Time) }

def off() { setLightOnOff(0, transition_Time) }

def setLevel(level, transTime = transition_Time) {
	setLightLevel(level, transTime)
}

def startLevelChange(direction) {
	if (direction == "up") { levelUp() }
	else { levelDown() }
}

def stopLevelChange() {
	unschedule(levelUp)
	unschedule(levelDown)
}

def levelUp() {
	def curLevel = device.currentValue("level").toInteger()
	if (curLevel == 100) { return }
	def newLevel = curLevel + 4
	if (newLevel > 100) { newLevel = 100 }
	setLevel(newLevel, 0)
	runIn(1, levelUp)
}

def levelDown() {
	def curLevel = device.currentValue("level").toInteger()
	if (curLevel == 0 || device.currentValue("switch") == "off") { return }
	def newLevel = curLevel - 4
	if (newLevel < 0) { off() }
	else {
		setLevel(newLevel, 0)
		runIn(1, levelDown)
	}
}

def setColorTemperature(colorTemp, level = device.currentValue("level"), transTime = transition_Time) {
	def lowCt = 1000
	def highCt = 12000
	if (colorTemp < lowCt) { colorTemp = lowCt }
	else if (colorTemp > highCt) { colorTemp = highCt }
	def hsvData = getCtHslValue(colorTemp)
	setLightColor(level, colorTemp, hsvData.hue, hsvData.saturation, 0)
	state.currentCT = colorTemp
}

def setHue(hue) { setColor([hue: hue]) }

def setSaturation(saturation) { setColor([saturation: saturation]) }

def setColor(Map color, transTime = transition_Time) {
	if (color == null) {
		LogWarn("setColor: Color map is null. Command not executed.")
	} else {
		def level = device.currentValue("level")
		if (color.level) { level = color.level }
		def hue = device.currentValue("hue")
		if (color.hue || color.hue == 0) { hue = color.hue.toInteger() }
		def saturation = device.currentValue("saturation")
		if (color.saturation || color.saturation == 0) { saturation = color.saturation }
		hue = Math.round(0.49 + hue * 3.6).toInteger()
		if (hue < 0 || hue > 360 || saturation < 0 || saturation > 100 || level < 0 || level > 100) {
			logWarn("setColor: Entered hue, saturation, or level out of range! (H:${hue}, S:${saturation}, L:${level}")
 		} else {
			setLightColor(level, 0, hue, saturation, transTime)
		}
	}
}

def setRGB(rgb) {
	logDebug("setRGB: ${rgb}") 
	def rgbArray = rgb.split('\\,')
	def hsvData = hubitat.helper.ColorUtils.rgbToHSV([rgbArray[0].toInteger(), rgbArray[1].toInteger(), rgbArray[2].toInteger()])
	def hue = (0.5 + hsvData[0]).toInteger()
	def saturation = (0.5 + hsvData[1]).toInteger()
	def level = (0.5 + hsvData[2]).toInteger()
	def Map hslData = [
		hue: hue,
		saturation: saturation,
		level: level
		]
	setColor(hslData)
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
		} else if (response.system.set_dev_alias) {
			updateName(response.system.set_dev_alias)
		} else {
			logWarn("distResp: Unhandled response = ${response}")
		}
	} else if (response["smartlife.iot.smartbulb.lightingservice"]) {
		setSysInfo([light_state:response["smartlife.iot.smartbulb.lightingservice"].transition_light_state])
	} else if (response["smartlife.iot.lightStrip"]) {
		getSysinfo()
	} else if (response["smartlife.iot.lighting_effect"]) {
		parseEffect(response["smartlife.iot.lighting_effect"])
	} else if (response["smartlife.iot.common.emeter"]) {
		distEmeter(response["smartlife.iot.common.emeter"])
	} else if (response["smartlife.iot.common.cloud"]) {
		setBindUnbind(response["smartlife.iot.common.cloud"])
	} else if (response["smartlife.iot.common.system"]) {
		if (response["smartlife.iot.common.system"].reboot) {
			logWarn("distResp: Rebooting device")
		} else {
			logDebug("distResp: Unhandled reboot response: ${response}")
		}
	} else {
		logWarn("distResp: Unhandled response = ${response}")
	}
}

def setSysInfo(status) {
	logDebug("setSysInfo: ${status}")
	def lightStatus = status.light_state
	logDebug("setSysInfo: ${lightStatus}")
	def updates = [:]
	def onOff = "on"
	if (lightStatus.on_off == 0) { onOff = "off" }
	if (device.currentValue("switch") != onOff) {
		updates << [switch : onOff]
		sendEvent(name: "switch", value: onOff, type: "digital")
	}
	if (onOff == "on") {
		int level = lightStatus.brightness
		if (level != device.currentValue("level")) {
			updates << [level : level]
			sendEvent(name: "level", value: level, unit: "%")
		}
		//	Get data for devices, colorMode, and reset data for colorMode = effects
		int colorTemp = lightStatus.color_temp
		int hue = lightStatus.hue
		int hubHue = (hue / 3.6).toInteger()
		int saturation = lightStatus.saturation
		def colorMode
		def colorName = " "
		def effectName = " "
		def color = ""
		def rgb = ""

		if (status.lighting_effect_state.enable == 1) {
			colorMode = "EFFECTS"
			effectName = status.lighting_effect_state.name
			//	Set hue, sat, ct to zero - they mean nothing when in EFFECTS mode
			colorTemp = 0
			hubHue = 0
			saturation = 0
		} else if (colorTemp > 0) {
			colorMode = "CT" 
			colorName = getCtName(colorTemp)
		} else {
			colorMode = "RGB"
			colorName = getColorName(hue)
			color = "[hue: ${hubHue}, saturation: ${saturation}, level: ${level}]"
			rgb = hubitat.helper.ColorUtils.hsvToRGB([hubHue, saturation, level])
		}

		if (device.currentValue("colorTemperature") != colorTemp) {
			updates << [colorTemp : colorTemp]
			sendEvent(name: "colorTemperature", value: colorTemp)
		}
		if (device.currentValue("colorName") != colorName) {
			updates << ["colorName" : colorName]
			sendEvent(name: "colorName", value: colorName)
		}

		if (device.currentValue("hue") != hubHue ||
			device.currentValue("saturation") != saturation ||
			device.currentValue("level") != level) {
			updates << [color : color]
			updates << ["colorMode" : colorMode]
			updates << ["RGB": rgb]
			sendEvent(name: "color", value: color)
			sendEvent(name: "hue", value: hubHue)
			sendEvent(name: "saturation", value: saturation)
			sendEvent(name: "colorMode", value: colorMode)
		    sendEvent(name: "RGB", value: rgb)
		}

		if (effectName != device.currentValue("effectName")) {
			updates << ["effectName" : effectName]
			sendEvent(name: "effectName", value: effectName)
		}
	}
	if (emFunction && onOff == "on") {
		getRealtime()
	} else if (emFunction && 
			   onOff == "off" && 
			   device.currentValue("power") > 0) {
		updates << ["power" : 0]
		sendEvent(name: "power", value: 0, descriptionText: "Watts", unit: "W")
	}
	if (updates != [:]) { logInfo("setSysinfo: ${updates}") }
}

//	==================================================
//	Kasa API Methods
//	==================================================
def service() {
	return "smartlife.iot.lightStrip"
}

def method() {
	return "set_light_state"
}

def checkLevel(level) {
	if (level == null || level < 0) {
		level = 0
		logWarn("checkLevel: level entry error.  Level set to ${level}")
	} else if (level > 100) {
		level = 100
		logWarn("checkLevel: level entry error.  Level set to ${level}")
	}
	return level
}		

def setLightOnOff(onOff, transTime = 0) {
	sendCmd("""{"${service()}":{"${method()}":{"on_off":${onOff},""" +
			""""transition_period":0}}}""")
}

def setLightLevel(level, transTime = 0) {
	level = checkLevel(level)
	if (level == 0) {
		setLightOnOff(0, transTime)
	} else {
		sendCmd("""{"${service()}":{"${method()}":{"ignore_default":1,"on_off":1,""" +
				""""brightness":${level},"transition_period":0}}}""")
	}
}

def setLightColor(level, colorTemp, hue, saturation, transTime = 0) {
	level = checkLevel(level)
	if (level == 0) {
		setLightOnOff(0, transTime)
	} else {
		sendCmd("""{"${service()}":{"${method()}":{"ignore_default":1,"on_off":1,""" +
				""""brightness":${level},"color_temp":${colorTemp},""" +
				""""hue":${hue},"saturation":${saturation},"transition_period":0}}}""")
	}
}

def bulbPresetCreate(psName) {
	if (!state.bulbPresets) { state.bulbPresets = [:] }
	psName = psName.trim().toLowerCase()
	logDebug("bulbPresetCreate: ${psName}")
	def psData = [:]
	psData["hue"] = device.currentValue("hue")
	psData["saturation"] = device.currentValue("saturation")
	psData["level"] = device.currentValue("level")
	def colorTemp = device.currentValue("colorTemperature")
	if (colorTemp == null) { colorTemp = 0 }
	psData["colTemp"] = colorTemp
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

def syncBulbPresets() {
	device.updateSetting("syncBulbs", [type:"bool", value: false])
	parent.syncBulbPresets(state.bulbPresets)
	return "Syncing"
}

def updatePresets(bulbPresets) {
	logInfo("updatePresets: ${bulbPresets}")
	state.bulbPresets = bulbPresets
}

def bulbPresetSet(psName, transTime = transition_Time) {
	psName = psName.trim()
	if (state.bulbPresets."${psName}") {
		def psData = state.bulbPresets."${psName}"
		def hue = Math.round(0.49 + psData.hue.toInteger() * 3.6).toInteger()
		setLightColor(psData.level, psData.colTemp, hue, psData.saturation, transTime)
	} else {
		logWarn("bulbPresetSet: ${psName} is not a valid name.")
	}
}

//	==================================================
//	Light Strip Effects
//	==================================================
def effectCreate() {
	state.createEffect = true
	sendCmd("""{"smartlife.iot.lighting_effect":{"get_lighting_effect":{}}}""")
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
				logDebug("parseEffect: ${effName} added to effectPresets")
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

def resetLightEffects() {
	if (state.effectsPresets != [:]) {
		def lightEffects = []
		state.effectPresets.each{
			def name = """ "${it.name}" """
			lightEffects << name
		}
		sendEvent(name: "lightEffects", value: lightEffects)
	}
	return "Updated lightEffects list"
}

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

def syncEffectPresets() {
	device.updateSetting("syncEffects", [type:"bool", value: false])
	parent.resetStates(device.deviceNetworkId)
	state.effectPresets.each{
		def effData = it
		parent.syncEffectPreset(effData, device.deviceNetworkId)
		pauseExecution(1000)
	}
	return "Synching"
}

def resetStates() { state.effectPresets = [] }

def updateEffectPreset(effData) {
	logDebug("updateEffectPreset: ${effData.name}")
	state.effectPresets << effData
	runIn(5, resetLightEffects)
}

def sendEffect(effData) {
	effData = new groovy.json.JsonBuilder(effData).toString()
	sendCmd("""{"smartlife.iot.lighting_effect":{"set_lighting_effect":""" +
			"""${effData}},"context":{"source":"<id>"}}""")
}

//	==================================================
//	Look Up Methods
//	==================================================
def getColorName(hue){
    def colorName
	switch (hue){
		case 0..15: colorName = "Red"; break
		case 16..45: colorName = "Orange"; break
		case 46..75: colorName = "Yellow"; break
		case 76..105: colorName = "Chartreuse"; break
		case 106..135: colorName = "Green"; break
		case 136..165: colorName = "Spring"; break
		case 166..195: colorName = "Cyan"; break
		case 196..225: colorName = "Azure"; break
		case 226..255: colorName = "Blue"; break
		case 256..285: colorName = "Violet"; break
		case 286..315: colorName = "Magenta"; break
		case 316..345: colorName = "Rose"; break
		case 346..360: colorName = "Red"; break
		default:
			logWarn("setRgbData: Unknown.")
			colorName = "Unknown"
    }
	return colorName
}

def getCtHslValue(kelvin) {
	kelvin = (100 * Math.round(kelvin / 100)).toInteger()
	switch(kelvin) {
		case 1000: rgb= [255, 56, 0]; break
		case 1100: rgb= [255, 71, 0]; break
		case 1200: rgb= [255, 83, 0]; break
		case 1300: rgb= [255, 93, 0]; break
		case 1400: rgb= [255, 101, 0]; break
		case 1500: rgb= [255, 109, 0]; break
		case 1600: rgb= [255, 115, 0]; break
		case 1700: rgb= [255, 121, 0]; break
		case 1800: rgb= [255, 126, 0]; break
		case 1900: rgb= [255, 131, 0]; break
		case 2000: rgb= [255, 138, 18]; break
		case 2100: rgb= [255, 142, 33]; break
		case 2200: rgb= [255, 147, 44]; break
		case 2300: rgb= [255, 152, 54]; break
		case 2400: rgb= [255, 157, 63]; break
		case 2500: rgb= [255, 161, 72]; break
		case 2600: rgb= [255, 165, 79]; break
		case 2700: rgb= [255, 169, 87]; break
		case 2800: rgb= [255, 173, 94]; break
		case 2900: rgb= [255, 177, 101]; break
		case 3000: rgb= [255, 180, 107]; break
		case 3100: rgb= [255, 184, 114]; break
		case 3200: rgb= [255, 187, 120]; break
		case 3300: rgb= [255, 190, 126]; break
		case 3400: rgb= [255, 193, 132]; break
		case 3500: rgb= [255, 196, 137]; break
		case 3600: rgb= [255, 199, 143]; break
		case 3700: rgb= [255, 201, 148]; break
		case 3800: rgb= [255, 204, 153]; break
		case 3900: rgb= [255, 206, 159]; break
		case 4000: rgb= [100, 209, 200]; break
		case 4100: rgb= [255, 211, 168]; break
		case 4200: rgb= [255, 213, 173]; break
		case 4300: rgb= [255, 215, 177]; break
		case 4400: rgb= [255, 217, 182]; break
		case 4500: rgb= [255, 219, 186]; break
		case 4600: rgb= [255, 221, 190]; break
		case 4700: rgb= [255, 223, 194]; break
		case 4800: rgb= [255, 225, 198]; break
		case 4900: rgb= [255, 227, 202]; break
		case 5000: rgb= [255, 228, 206]; break
		case 5100: rgb= [255, 230, 210]; break
		case 5200: rgb= [255, 232, 213]; break
		case 5300: rgb= [255, 233, 217]; break
		case 5400: rgb= [255, 235, 220]; break
		case 5500: rgb= [255, 236, 224]; break
		case 5600: rgb= [255, 238, 227]; break
		case 5700: rgb= [255, 239, 230]; break
		case 5800: rgb= [255, 240, 233]; break
		case 5900: rgb= [255, 242, 236]; break
		case 6000: rgb= [255, 243, 239]; break
		case 6100: rgb= [255, 244, 242]; break
		case 6200: rgb= [255, 245, 245]; break
		case 6300: rgb= [255, 246, 247]; break
		case 6400: rgb= [255, 248, 251]; break
		case 6500: rgb= [255, 249, 253]; break
		case 6600: rgb= [254, 249, 255]; break
		case 6700: rgb= [252, 247, 255]; break
		case 6800: rgb= [249, 246, 255]; break
		case 6900: rgb= [247, 245, 255]; break
		case 7000: rgb= [245, 243, 255]; break
		case 7100: rgb= [243, 242, 255]; break
		case 7200: rgb= [240, 241, 255]; break
		case 7300: rgb= [239, 240, 255]; break
		case 7400: rgb= [237, 239, 255]; break
		case 7500: rgb= [235, 238, 255]; break
		case 7600: rgb= [233, 237, 255]; break
		case 7700: rgb= [231, 236, 255]; break
		case 7800: rgb= [230, 235, 255]; break
		case 7900: rgb= [228, 234, 255]; break
		case 8000: rgb= [227, 233, 255]; break
		case 8100: rgb= [225, 232, 255]; break
		case 8200: rgb= [224, 231, 255]; break
		case 8300: rgb= [222, 230, 255]; break
		case 8400: rgb= [221, 230, 255]; break
		case 8500: rgb= [220, 229, 255]; break
		case 8600: rgb= [218, 229, 255]; break
		case 8700: rgb= [217, 227, 255]; break
		case 8800: rgb= [216, 227, 255]; break
		case 8900: rgb= [215, 226, 255]; break
		case 9000: rgb= [214, 225, 255]; break
		case 9100: rgb= [212, 225, 255]; break
		case 9200: rgb= [211, 224, 255]; break
		case 9300: rgb= [210, 223, 255]; break
		case 9400: rgb= [209, 223, 255]; break
		case 9500: rgb= [208, 222, 255]; break
		case 9600: rgb= [207, 221, 255]; break
		case 9700: rgb= [207, 221, 255]; break
		case 9800: rgb= [206, 220, 255]; break
		case 9900: rgb= [205, 220, 255]; break
		case 10000: rgb= [207, 218, 255]; break
		case 10100: rgb= [207, 218, 255]; break
		case 10200: rgb= [206, 217, 255]; break
		case 10300: rgb= [205, 217, 255]; break
		case 10400: rgb= [204, 216, 255]; break
		case 10500: rgb= [204, 216, 255]; break
		case 10600: rgb= [203, 215, 255]; break
		case 10700: rgb= [202, 215, 255]; break
		case 10800: rgb= [202, 214, 255]; break
		case 10900: rgb= [201, 214, 255]; break
		case 11000: rgb= [200, 213, 255]; break
		case 11100: rgb= [200, 213, 255]; break
		case 11200: rgb= [199, 212, 255]; break
		case 11300: rgb= [198, 212, 255]; break
		case 11400: rgb= [198, 212, 255]; break
		case 11500: rgb= [197, 211, 255]; break
		case 11600: rgb= [197, 211, 255]; break
		case 11700: rgb= [197, 210, 255]; break
		case 11800: rgb= [196, 210, 255]; break
		case 11900: rgb= [195, 210, 255]; break
		case 12000: rgb= [195, 209, 255]; break
		default:
			logWarn("setRgbData: Unknown.")
			colorName = "Unknown"
	}
	def hsvData = hubitat.helper.ColorUtils.rgbToHSV([rgb[0].toInteger(), rgb[1].toInteger(), rgb[2].toInteger()])
	def hue = (0.5 + hsvData[0]).toInteger()
	def saturation = (0.5 + hsvData[1]).toInteger()
	def level = (0.5 + hsvData[2]).toInteger()
	def hslData = [
		hue: hue,
		saturation: saturation,
		level: level
		]
	return hslData
}

def getCtName(colorTemp){
    def colorName
	switch (colorTemp){
		case 0..2000: colorName = "Sodium"; break
		case 2001..2100: colorName = "Starlight"; break
		case 2101..2400: colorName = "Sunrise"; break
		case 2401..2800: colorName = "Incandescent"; break
		case 2801..3300: colorName = "Soft White"; break
		case 3301..3500: colorName = "Warm White"; break
		case 3501..4150: colorName = "Moonlight"; break
		case 4151..5000: colorName = "Horizon"; break
		case 5001..5500: colorName = "Daylight"; break
		case 5501..6000: colorName = "Electronic"; break
		case 6001..6500: colorName = "Skylight"; break
		default:
			colorName = "Polar"
    }
	return colorName
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

// ~~~~~ start include (324) davegut.kasaEnergyMonitor ~~~~~
library ( // library marker davegut.kasaEnergyMonitor, line 1
	name: "kasaEnergyMonitor", // library marker davegut.kasaEnergyMonitor, line 2
	namespace: "davegut", // library marker davegut.kasaEnergyMonitor, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaEnergyMonitor, line 4
	description: "Kasa Device Energy Monitor Methods", // library marker davegut.kasaEnergyMonitor, line 5
	category: "energyMonitor", // library marker davegut.kasaEnergyMonitor, line 6
	documentationLink: "" // library marker davegut.kasaEnergyMonitor, line 7
) // library marker davegut.kasaEnergyMonitor, line 8

def setupEmFunction() { // library marker davegut.kasaEnergyMonitor, line 10
	if (emFunction && device.currentValue("currMonthTotal") > 0) { // library marker davegut.kasaEnergyMonitor, line 11
		runEvery30Minutes(getEnergyToday) // library marker davegut.kasaEnergyMonitor, line 12
		schedule("10 0 0 * * ?", getEnergyThisMonth) // library marker davegut.kasaEnergyMonitor, line 13
		schedule("15 2 0 1 * ?", getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 14
		state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 15
		return "Continuing EM Function" // library marker davegut.kasaEnergyMonitor, line 16
	} else if (emFunction) { // library marker davegut.kasaEnergyMonitor, line 17
		sendEvent(name: "power", value: 0, unit: "W") // library marker davegut.kasaEnergyMonitor, line 18
		sendEvent(name: "energy", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 19
		sendEvent(name: "currMonthTotal", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 20
		sendEvent(name: "currMonthAvg", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 21
		sendEvent(name: "lastMonthTotal", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 22
		sendEvent(name: "lastMonthAvg", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 23
		state.response = "" // library marker davegut.kasaEnergyMonitor, line 24
		runEvery30Minutes(getEnergyToday) // library marker davegut.kasaEnergyMonitor, line 25
		schedule("10 0 0 * * ?", getEnergyThisMonth) // library marker davegut.kasaEnergyMonitor, line 26
		schedule("15 2 0 1 * ?", getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 27
		state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 28
		//	Run order / delay is critical for successful operation. // library marker davegut.kasaEnergyMonitor, line 29
		getEnergyThisMonth() // library marker davegut.kasaEnergyMonitor, line 30
		runIn(10, getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 31
		return "Initialized" // library marker davegut.kasaEnergyMonitor, line 32
	} else if (device.currentValue("power") != null) { // library marker davegut.kasaEnergyMonitor, line 33
		//	for power != null, EM had to be enabled at one time.  Set values to 0. // library marker davegut.kasaEnergyMonitor, line 34
		sendEvent(name: "power", value: 0) // library marker davegut.kasaEnergyMonitor, line 35
		sendEvent(name: "energy", value: 0) // library marker davegut.kasaEnergyMonitor, line 36
		sendEvent(name: "currMonthTotal", value: 0) // library marker davegut.kasaEnergyMonitor, line 37
		sendEvent(name: "currMonthAvg", value: 0) // library marker davegut.kasaEnergyMonitor, line 38
		sendEvent(name: "lastMonthTotal", value: 0) // library marker davegut.kasaEnergyMonitor, line 39
		sendEvent(name: "lastMonthAvg", value: 0) // library marker davegut.kasaEnergyMonitor, line 40
		state.remove("getEnergy") // library marker davegut.kasaEnergyMonitor, line 41
		return "Disabled" // library marker davegut.kasaEnergyMonitor, line 42
	} else { // library marker davegut.kasaEnergyMonitor, line 43
		return "Not initialized" // library marker davegut.kasaEnergyMonitor, line 44
	} // library marker davegut.kasaEnergyMonitor, line 45
} // library marker davegut.kasaEnergyMonitor, line 46

def getDate() { // library marker davegut.kasaEnergyMonitor, line 48
	def currDate = new Date() // library marker davegut.kasaEnergyMonitor, line 49
	int year = currDate.format("yyyy").toInteger() // library marker davegut.kasaEnergyMonitor, line 50
	int month = currDate.format("M").toInteger() // library marker davegut.kasaEnergyMonitor, line 51
	int day = currDate.format("d").toInteger() // library marker davegut.kasaEnergyMonitor, line 52
	return [year: year, month: month, day: day] // library marker davegut.kasaEnergyMonitor, line 53
} // library marker davegut.kasaEnergyMonitor, line 54

def distEmeter(emeterResp) { // library marker davegut.kasaEnergyMonitor, line 56
	def date = getDate() // library marker davegut.kasaEnergyMonitor, line 57
	logDebug("distEmeter: ${emeterResp}, ${date}, ${state.getEnergy}") // library marker davegut.kasaEnergyMonitor, line 58
	def lastYear = date.year - 1 // library marker davegut.kasaEnergyMonitor, line 59
	if (emeterResp.get_realtime) { // library marker davegut.kasaEnergyMonitor, line 60
		setPower(emeterResp.get_realtime) // library marker davegut.kasaEnergyMonitor, line 61
	} else if (emeterResp.get_daystat) { // library marker davegut.kasaEnergyMonitor, line 62
		def dayList = emeterResp.get_daystat.day_list // library marker davegut.kasaEnergyMonitor, line 63
		setEnergyToday(dayList, date) // library marker davegut.kasaEnergyMonitor, line 64
	} else if (emeterResp.get_monthstat) { // library marker davegut.kasaEnergyMonitor, line 65
		def monthList = emeterResp.get_monthstat.month_list // library marker davegut.kasaEnergyMonitor, line 66
		if (state.getEnergy == "This Month") { // library marker davegut.kasaEnergyMonitor, line 67
			setThisMonth(monthList, date) // library marker davegut.kasaEnergyMonitor, line 68
		} else if (state.getEnergy == "Last Month") { // library marker davegut.kasaEnergyMonitor, line 69
			setLastMonth(monthList, date) // library marker davegut.kasaEnergyMonitor, line 70
		} else if (monthList == []) { // library marker davegut.kasaEnergyMonitor, line 71
			logDebug("distEmeter: monthList Empty. No data for year.") // library marker davegut.kasaEnergyMonitor, line 72
		} // library marker davegut.kasaEnergyMonitor, line 73
	} else { // library marker davegut.kasaEnergyMonitor, line 74
		logWarn("distEmeter: Unhandled response = ${emeterResp}") // library marker davegut.kasaEnergyMonitor, line 75
	} // library marker davegut.kasaEnergyMonitor, line 76
} // library marker davegut.kasaEnergyMonitor, line 77

def setPower(response) { // library marker davegut.kasaEnergyMonitor, line 79
	logDebug("setPower: ${response}") // library marker davegut.kasaEnergyMonitor, line 80
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 81
	def power = response.power // library marker davegut.kasaEnergyMonitor, line 82
	if (power == null) { power = response.power_mw / 1000 } // library marker davegut.kasaEnergyMonitor, line 83
	def curPwr = device.currentValue("power") // library marker davegut.kasaEnergyMonitor, line 84
	power = Math.round(100*(power))/100 // library marker davegut.kasaEnergyMonitor, line 85
	def pwrChange = false // library marker davegut.kasaEnergyMonitor, line 86
	if (curPwr == null || (curPwr == 0 && power > 0)) { // library marker davegut.kasaEnergyMonitor, line 87
		pwrChange = true // library marker davegut.kasaEnergyMonitor, line 88
	} else if (curPwr == 0 && power == 0) { // library marker davegut.kasaEnergyMonitor, line 89
		pwrChange = false // library marker davegut.kasaEnergyMonitor, line 90
	} else { // library marker davegut.kasaEnergyMonitor, line 91
		def powerChange = (power - curPwr) / curPwr // library marker davegut.kasaEnergyMonitor, line 92
		powerChange = Math.abs((power - curPwr) / curPwr) // library marker davegut.kasaEnergyMonitor, line 93
		pwrChange = false // library marker davegut.kasaEnergyMonitor, line 94
		if (powerChange > 0.03) { // library marker davegut.kasaEnergyMonitor, line 95
			pwrChange = true // library marker davegut.kasaEnergyMonitor, line 96
		} // library marker davegut.kasaEnergyMonitor, line 97
	} // library marker davegut.kasaEnergyMonitor, line 98
	if (pwrChange == true) { // library marker davegut.kasaEnergyMonitor, line 99
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W", type: "digital") // library marker davegut.kasaEnergyMonitor, line 100
		status << [power: power] // library marker davegut.kasaEnergyMonitor, line 101
	} // library marker davegut.kasaEnergyMonitor, line 102
	if (status != [:]) { logInfo("setPower: ${status}") } // library marker davegut.kasaEnergyMonitor, line 103
} // library marker davegut.kasaEnergyMonitor, line 104

def getEnergyToday() { // library marker davegut.kasaEnergyMonitor, line 106
	def date = getDate() // library marker davegut.kasaEnergyMonitor, line 107
	logDebug("getEnergyToday: ${date}") // library marker davegut.kasaEnergyMonitor, line 108
	getDaystat(date.year, date.month) // library marker davegut.kasaEnergyMonitor, line 109
} // library marker davegut.kasaEnergyMonitor, line 110

def setEnergyToday(dayList, date) { // library marker davegut.kasaEnergyMonitor, line 112
	logDebug("setEnergyToday: ${date}, ${dayList}") // library marker davegut.kasaEnergyMonitor, line 113
	def data = dayList.find { it.day == date.day } // library marker davegut.kasaEnergyMonitor, line 114
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 115
	def energy = 0 // library marker davegut.kasaEnergyMonitor, line 116
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 117
		status << [msgError: "Return Data Null"] // library marker davegut.kasaEnergyMonitor, line 118
	} else { // library marker davegut.kasaEnergyMonitor, line 119
		energy = data.energy // library marker davegut.kasaEnergyMonitor, line 120
		if (energy == null) { energy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 121
		energy = Math.round(100*energy)/100 // library marker davegut.kasaEnergyMonitor, line 122
	} // library marker davegut.kasaEnergyMonitor, line 123
	if (device.currentValue("energy") != energy) { // library marker davegut.kasaEnergyMonitor, line 124
		sendEvent(name: "energy", value: energy, descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 125
		status << [energy: energy] // library marker davegut.kasaEnergyMonitor, line 126
	} // library marker davegut.kasaEnergyMonitor, line 127
	if (status != [:]) { logInfo("setEnergyToday: ${status}") } // library marker davegut.kasaEnergyMonitor, line 128
	//	If the new "state.getEnergy" is not present, set state.getEnergy, then schedule/run // library marker davegut.kasaEnergyMonitor, line 129
	//	 getEnergyThisMonth and getEnergyLastMonth.  Assures that updates have the EM Function // library marker davegut.kasaEnergyMonitor, line 130
	//	updated. // library marker davegut.kasaEnergyMonitor, line 131
	if (!state.getEnergy) { // library marker davegut.kasaEnergyMonitor, line 132
		//	Kick start new version getEnergyThisMonth which adds state.getEnergy // library marker davegut.kasaEnergyMonitor, line 133
		schedule("10 0 0 * * ?", getEnergyThisMonth) // library marker davegut.kasaEnergyMonitor, line 134
		schedule("15 2 0 1 * ?", getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 135
		state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 136
		getEnergyThisMonth() // library marker davegut.kasaEnergyMonitor, line 137
		runIn(10, getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 138
	} // library marker davegut.kasaEnergyMonitor, line 139
} // library marker davegut.kasaEnergyMonitor, line 140

def getEnergyThisMonth() { // library marker davegut.kasaEnergyMonitor, line 142
	state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 143
	def year = getDate().year // library marker davegut.kasaEnergyMonitor, line 144
	logDebug("getEnergyThisMonth: ${year}") // library marker davegut.kasaEnergyMonitor, line 145
	runIn(5, getMonthstat, [data: year]) // library marker davegut.kasaEnergyMonitor, line 146
} // library marker davegut.kasaEnergyMonitor, line 147

def setThisMonth(monthList, date) { // library marker davegut.kasaEnergyMonitor, line 149
	logDebug("setThisMonth: ${date} // ${monthList}") // library marker davegut.kasaEnergyMonitor, line 150
	def data = monthList.find { it.month == date.month && it.year == date.year} // library marker davegut.kasaEnergyMonitor, line 151
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 152
	def totEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 153
	def avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 154
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 155
		status << [msgError: "Return Data Null"] // library marker davegut.kasaEnergyMonitor, line 156
	} else { // library marker davegut.kasaEnergyMonitor, line 157
		status << [msgError: "OK"] // library marker davegut.kasaEnergyMonitor, line 158
		totEnergy = data.energy // library marker davegut.kasaEnergyMonitor, line 159
		if (totEnergy == null) { totEnergy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 160
		if (date.day == 1) { // library marker davegut.kasaEnergyMonitor, line 161
			avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 162
		} else { // library marker davegut.kasaEnergyMonitor, line 163
			avgEnergy = totEnergy /(date.day - 1) // library marker davegut.kasaEnergyMonitor, line 164
		} // library marker davegut.kasaEnergyMonitor, line 165
	} // library marker davegut.kasaEnergyMonitor, line 166
	totEnergy = Math.round(100*totEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 167
	avgEnergy = Math.round(100*avgEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 168
	sendEvent(name: "currMonthTotal", value: totEnergy,  // library marker davegut.kasaEnergyMonitor, line 169
			  descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 170
	status << [currMonthTotal: totEnergy] // library marker davegut.kasaEnergyMonitor, line 171
	sendEvent(name: "currMonthAvg", value: avgEnergy,  // library marker davegut.kasaEnergyMonitor, line 172
		 	 descriptionText: "KiloWatt Hours per Day", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 173
	status << [currMonthAvg: avgEnergy] // library marker davegut.kasaEnergyMonitor, line 174
	//	Update energy today in sync with energyThisMonth // library marker davegut.kasaEnergyMonitor, line 175
	getEnergyToday() // library marker davegut.kasaEnergyMonitor, line 176
	logInfo("setThisMonth: ${status}") // library marker davegut.kasaEnergyMonitor, line 177
} // library marker davegut.kasaEnergyMonitor, line 178

def getEnergyLastMonth() { // library marker davegut.kasaEnergyMonitor, line 180
	state.getEnergy = "Last Month" // library marker davegut.kasaEnergyMonitor, line 181
	def date = getDate() // library marker davegut.kasaEnergyMonitor, line 182
	def year = date.year // library marker davegut.kasaEnergyMonitor, line 183
	if (date.month == 1) { // library marker davegut.kasaEnergyMonitor, line 184
		year = year - 1 // library marker davegut.kasaEnergyMonitor, line 185
	} // library marker davegut.kasaEnergyMonitor, line 186
	logDebug("getEnergyLastMonth: ${year}") // library marker davegut.kasaEnergyMonitor, line 187
	runIn(5, getMonthstat, [data: year]) // library marker davegut.kasaEnergyMonitor, line 188
} // library marker davegut.kasaEnergyMonitor, line 189

def setLastMonth(monthList, date) { // library marker davegut.kasaEnergyMonitor, line 191
	logDebug("setLastMonth: ${date} // ${monthList}") // library marker davegut.kasaEnergyMonitor, line 192
	def lastMonthYear = date.year // library marker davegut.kasaEnergyMonitor, line 193
	def lastMonth = date.month - 1 // library marker davegut.kasaEnergyMonitor, line 194
	if (date.month == 1) { // library marker davegut.kasaEnergyMonitor, line 195
		lastMonthYear -+ 1 // library marker davegut.kasaEnergyMonitor, line 196
		lastMonth = 12 // library marker davegut.kasaEnergyMonitor, line 197
	} // library marker davegut.kasaEnergyMonitor, line 198
	def data = monthList.find { it.month == lastMonth } // library marker davegut.kasaEnergyMonitor, line 199
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 200
	def totEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 201
	def avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 202
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 203
		status << [msgError: "Return Data Null"] // library marker davegut.kasaEnergyMonitor, line 204
	} else { // library marker davegut.kasaEnergyMonitor, line 205
		status << [msgError: "OK"] // library marker davegut.kasaEnergyMonitor, line 206
		def monthLength // library marker davegut.kasaEnergyMonitor, line 207
		switch(lastMonth) { // library marker davegut.kasaEnergyMonitor, line 208
			case 4: // library marker davegut.kasaEnergyMonitor, line 209
			case 6: // library marker davegut.kasaEnergyMonitor, line 210
			case 9: // library marker davegut.kasaEnergyMonitor, line 211
			case 11: // library marker davegut.kasaEnergyMonitor, line 212
				monthLength = 30 // library marker davegut.kasaEnergyMonitor, line 213
				break // library marker davegut.kasaEnergyMonitor, line 214
			case 2: // library marker davegut.kasaEnergyMonitor, line 215
				monthLength = 28 // library marker davegut.kasaEnergyMonitor, line 216
				if (lastMonthYear == 2020 || lastMonthYear == 2024 || lastMonthYear == 2028) {  // library marker davegut.kasaEnergyMonitor, line 217
					monthLength = 29 // library marker davegut.kasaEnergyMonitor, line 218
				} // library marker davegut.kasaEnergyMonitor, line 219
				break // library marker davegut.kasaEnergyMonitor, line 220
			default: // library marker davegut.kasaEnergyMonitor, line 221
				monthLength = 31 // library marker davegut.kasaEnergyMonitor, line 222
		} // library marker davegut.kasaEnergyMonitor, line 223
		totEnergy = data.energy // library marker davegut.kasaEnergyMonitor, line 224
		if (totEnergy == null) { totEnergy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 225
		avgEnergy = totEnergy / monthLength // library marker davegut.kasaEnergyMonitor, line 226
	} // library marker davegut.kasaEnergyMonitor, line 227
	totEnergy = Math.round(100*totEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 228
	avgEnergy = Math.round(100*avgEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 229
	sendEvent(name: "lastMonthTotal", value: totEnergy,  // library marker davegut.kasaEnergyMonitor, line 230
			  descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 231
	status << [lastMonthTotal: totEnergy] // library marker davegut.kasaEnergyMonitor, line 232
	sendEvent(name: "lastMonthAvg", value: avgEnergy,  // library marker davegut.kasaEnergyMonitor, line 233
			  descriptionText: "KiloWatt Hoursper Day", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 234
	status << [lastMonthAvg: avgEnergy] // library marker davegut.kasaEnergyMonitor, line 235
	logInfo("setLastMonth: ${status}") // library marker davegut.kasaEnergyMonitor, line 236
} // library marker davegut.kasaEnergyMonitor, line 237

//	===== API Commands: Energy Monitor ===== // library marker davegut.kasaEnergyMonitor, line 239
def getRealtime() { // library marker davegut.kasaEnergyMonitor, line 240
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaEnergyMonitor, line 241
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaEnergyMonitor, line 242
				""""emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 243
	} else if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaEnergyMonitor, line 244
		sendCmd("""{"smartlife.iot.common.emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 245
	} else { // library marker davegut.kasaEnergyMonitor, line 246
		sendCmd("""{"emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 247
	} // library marker davegut.kasaEnergyMonitor, line 248
} // library marker davegut.kasaEnergyMonitor, line 249

def getDaystat(year, month) { // library marker davegut.kasaEnergyMonitor, line 251
	def cmd = """{"emeter":{"get_daystat":{"month": ${month}, "year": ${year}}}}""" // library marker davegut.kasaEnergyMonitor, line 252
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaEnergyMonitor, line 253
		cmd = """{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaEnergyMonitor, line 254
			  """"emeter":{"get_daystat":{"month": ${month}, "year": ${year}}}}""" // library marker davegut.kasaEnergyMonitor, line 255
	} else if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaEnergyMonitor, line 256
		cmd = """{"smartlife.iot.common.emeter":{"get_daystat":{"month": ${month}, "year": ${year}}}}""" // library marker davegut.kasaEnergyMonitor, line 257
	} // library marker davegut.kasaEnergyMonitor, line 258
	//	If using LAN communications, will use TCP socket (vice sendHubCommand) for sending command. // library marker davegut.kasaEnergyMonitor, line 259
	//	Necessary since the response length will execced 1024 characters late in the month. // library marker davegut.kasaEnergyMonitor, line 260
	if (device.currentValue("connection") == "CLOUD") { // library marker davegut.kasaEnergyMonitor, line 261
		sendKasaCmd(cmd) // library marker davegut.kasaEnergyMonitor, line 262
	} else { // library marker davegut.kasaEnergyMonitor, line 263
		sendTcpCmd(cmd) // library marker davegut.kasaEnergyMonitor, line 264
	} // library marker davegut.kasaEnergyMonitor, line 265
} // library marker davegut.kasaEnergyMonitor, line 266

def getMonthstat(year) { // library marker davegut.kasaEnergyMonitor, line 268
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaEnergyMonitor, line 269
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaEnergyMonitor, line 270
				""""emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 271
	} else if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaEnergyMonitor, line 272
		sendCmd("""{"smartlife.iot.common.emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 273
	} else { // library marker davegut.kasaEnergyMonitor, line 274
		sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 275
	} // library marker davegut.kasaEnergyMonitor, line 276
} // library marker davegut.kasaEnergyMonitor, line 277

// ~~~~~ end include (324) davegut.kasaEnergyMonitor ~~~~~
