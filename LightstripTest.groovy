/*	Kasa Device Driver Series
Color Light Strip Test Code
===================================================================================================*/
def driverVer() { return "Test1.0.0" }
metadata {
	definition (name: "Kasa Light Strip",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/DeviceDrivers/ColorBulb.groovy"
			   ) {
        capability "Light"
		capability "Switch"
		capability "Switch Level"
		capability "Change Level"
 		capability "Refresh"
		capability "Actuator"
		capability "Color Temperature"
		command "setCircadian"
		attribute "circadianState", "string"
		capability "Color Mode"
		capability "Color Control"
	}
	preferences {
		if (!getDataValue("applicationVersion")) {
			input ("device_IP", "text", 
				   title: "Device IP", 
				   defaultValue: getDataValue("deviceIP"))
		}
		input ("transition_Time", "num", 
			   title: "Default Transition time (seconds)", 
			   defaultValue: 0)
		input ("highRes", "bool", 
			   title: "(Color Bulb) High Resolution Hue Scale", 
			   defaultValue: false)
	}
}
def installed() {
	logInfo("Installing Device....")
	runIn(2, updated)
}

//	===== Updated and associated methods =====
def updated() {
	logInfo("Updating device preferences....")
	unschedule()
	state.respLength = 0
	state.response = ""
	state.lastConnect = 0
	state.errorCount = 0
	
	//	Manual installation support.  Get IP and Plug Number
	if (!getDataValue("applicationVersion")) {
		if (!device_IP) {
			logWarn("updated: Device IP is not set.")
			return
		}
		if (getDataValue("deviceIP") != device_IP.trim()) {
			updateDataValue("deviceIP", device_IP.trim())
			logInfo("updated: Device IP set to ${device_IP.trim()}")
		}
	}

	//	Update various preferences.
	state.transTime = 1000*transition_Time.toInteger()
	logInfo("updated: Light transition time set to ${transition_Time} seconds.")

	runIn(5, refresh)
}

//	===== Command Methods =====
def on() {
	logDebug("on: transition time = ${state.transTime}")
	def command = """{"smartlife.iot.lightStrip":""" +
		"""{"set_light_state":{"on_off":1,"transition_period":${state.transTime}}}}"""
	sendCmd(command)
}
def off() {
	logDebug("off: transition time = ${state.transTime}")
	def command = """{"smartlife.iot.lightStrip":""" +
		"""{"set_light_state":{"on_off":0,"transition_period":${state.transTime}}}}"""
	sendCmd(command)
}
def setLevel(percentage, rate = null) {
	logDebug("setLevel(x,x): rate = ${rate} // percentage = ${percentage}")
	if (percentage < 0) { percentage = 0 }
	else if (percentage > 100) { percentage = 100 }
	if (rate == null) {
		rate = state.transTime.toInteger()
	} else {
		rate = 1000*rate.toInteger()
	}
	def command = """{"smartlife.iot.lightStrip":""" +
		"""{"set_light_state":{"ignore_default":1,"on_off":1,""" +
		""""brightness":${percentage},"transition_period":${rate}}}}"""
	sendCmd(command)
}
def startLevelChange(direction) {
	logDebug("startLevelChange: direction = ${direction}")
	if (direction == "up") { levelUp() }
	else { levelDown() }
}
def stopLevelChange() {
	logDebug("stopLevelChange")
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
	if (curLevel == 0) { return }
	def newLevel = curLevel - 4
	if (newLevel < 0) { newLevel = 0 }
	setLevel(newLevel, 0)
	if (newLevel == 0) { off() }
	runIn(1, levelDown)
}
def refresh(){
	logDebug("refresh")
	def command = "0000001dd0f281f88bff9af7d5ef94b6d1b4c09fec95e68" +
		"fe187e8caf08bf68bf6"
	sendCmd(command)
}

def setColorTemperature(kelvin) {
	logDebug("setColorTemperature: colorTemp = ${kelvin}")
	if (kelvin < 2500) { kelvin = 2500 }
	if (kelvin > 9000) { kelvin = 9000 }
	def command = """{"smartlife.iot.lightStrip":{"set_light_state":""" +
		"""{"ignore_default":1,"on_off":1,"color_temp": ${kelvin},"hue":0,"saturation":0}}}"""
	sendCmd(command)
}
def setCircadian() {
	logDebug("setCircadian")
	def command = """{"smartlife.iot.lightStrip":""" +
		"""{"set_light_state":{"mode":"circadian"}}}"""
	sendCmd(command)
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
	logDebug("setColor:  color = ${color}")
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
	if (highRes != true) {
		hue = Math.round(0.49 + hue * 3.6).toInteger()
	}
	if (hue < 0 || hue > 360 || saturation < 0 || saturation > 100 || level < 0 || level > 100) {
		logWarn("setColor: Entered hue, saturation, or level out of range! (H:${hue}, S:${saturation}, L:${level}")
        return
    }
	def command = """{"smartlife.iot.lightStrip":{"set_light_state":""" +
		"""{"ignore_default":1,"on_off":1,"brightness":${level},"color_temp":0,""" +
		""""hue":${hue},"saturation":${saturation}}}}"""
	sendCmd(command)
}

def updateBulbData(status) {
	logDebug("updateBulbData: ${status}")
	def deviceStatus = [:]
	def type = getDataValue("devType")
	if (status.on_off == 0) { 
		sendEvent(name: "switch", value: "off", type: "digital")
		sendEvent(name: "circadianState", value: "normal")
		deviceStatus << ["power" : "off"]
	} else {
		sendEvent(name: "switch", value: "on", type: "digital")
		deviceStatus << ["power" : "on"]
		sendEvent(name: "level", value: status.brightness, unit: "%")
		deviceStatus << ["level" : status.brightness]
		
		sendEvent(name: "circadianState", value: status.mode)
		deviceStatus << ["mode" : status.mode]
		sendEvent(name: "colorTemperature", value: status.color_temp, unit: " K")
		deviceStatus << ["colorTemp" : status.color_temp]
		
		def hue = status.hue.toInteger()
		if (highRes != true) { hue = (hue / 3.6).toInteger() }
		sendEvent(name: "hue", value: hue)
		deviceStatus << ["hue" : hue]
		sendEvent(name: "saturation", value: status.saturation)
		deviceStatus << ["sat" : status.saturation]
		def color = [:]
		color << ["hue" : hue]
		color << ["saturation" : status.saturation]
		if (status.color_temp.toInteger() > 2000) {
			color << ["level" : 0]
		} else {
			color << ["level" : status.brightness]
		}
		sendEvent(name: "color", value: color)
		if (status.color_temp.toInteger() == 0) { 
			setRgbData(hue, status.saturation) }
		else { 
			setColorTempData(status.color_temp) 
		}
	}
	logInfo("updateBulbData: Status = ${deviceStatus}")
}
def setColorTempData(temp){
	logDebug("setColorTempData: color temperature = ${temp}")
    def value = temp.toInteger()
	state.lastColorTemp = value
    def colorName
	if (value <= 2800) { colorName = "Incandescent" }
	else if (value <= 3300) { colorName = "Soft White" }
	else if (value <= 3500) { colorName = "Warm White" }
	else if (value <= 4150) { colorName = "Moonlight" }
	else if (value <= 5000) { colorName = "Horizon" }
	else if (value <= 5500) { colorName = "Daylight" }
	else if (value <= 6000) { colorName = "Electronic" }
	else if (value <= 6500) { colorName = "Skylight" }
	else { colorName = "Polar" }
	//	Color Bulb Only.
	if (device.currentValue("colorMode") != "CT") {
 		sendEvent(name: "colorMode", value: "CT")
		logInfo("setColorTempData: Color Mode is CT")
	}
	if (device.currentValue("colorName") != colorName) {
	    sendEvent(name: "colorName", value: colorName)
		logInfo("setColorTempData: Color name is ${colorName}.")
	}
}
def setRgbData(hue, saturation){
	logDebug("setRgbData: hue = ${hue} // highRes = ${highRes}")
	state.lastHue = hue
	state.lastSaturation = saturation
	if (highRes != true) { hue = (hue * 3.6).toInteger() }
    def colorName
	switch (hue){
		case 0..15: colorName = "Red"
            break
		case 16..45: colorName = "Orange"
            break
		case 46..75: colorName = "Yellow"
            break
		case 76..105: colorName = "Chartreuse"
            break
		case 106..135: colorName = "Green"
            break
		case 136..165: colorName = "Spring"
            break
		case 166..195: colorName = "Cyan"
            break
		case 196..225: colorName = "Azure"
            break
		case 226..255: colorName = "Blue"
            break
		case 256..285: colorName = "Violet"
            break
		case 286..315: colorName = "Magenta"
            break
		case 316..345: colorName = "Rose"
            break
		case 346..360: colorName = "Red"
            break
		default:
			logWarn("setRgbData: Unknown.")
			colorName = "Unknown"
    }
	if (device.currentValue("colorMode") != "RGB") {
 		sendEvent(name: "colorMode", value: "RGB")
		logInfo("setRgbData: Color Mode is RGB")
	}
	if (device.currentValue("colorName") != colorName) {
	    sendEvent(name: "colorName", value: colorName)
		logInfo("setRgbData: Color name is ${colorName}.")
	}
}

//	===== distribute responses =====
def distResp(response) {
	logDebug("distResp: response length = ${response.length()}")
	if (response.length() == null) {
		logDebug("distResp: null return rejected.")
		return 
	}
	
	def resp
	try {
		resp = parseJson(inputXOR(response))
	} catch (e) {
		logWarn("distResp: Invalid or incomplete return.\nerror = ${e}")
		return
	}
	unschedule(rawSocketTimeout)
	state.errorCount = 0
	
	if (resp["smartlife.iot.lightStrip"]) {
		logDebug("distResp: Returning smartlife.iot.lightStrip")
		updateBulbData(resp["smartlife.iot.lightStrip"].light_state)
	} else {
		logDebug("distResp: Returning system.get_sysinfo")
		updateBulbData(resp.system.get_sysinfo.light_state)
	}
}

//	===== Common Kasa Driver code =====
private sendCmd(command) {
	logDebug("sendCmd:  ${command}")
	command = outputXOR(command)
	if (now() - state.lastConnect > 35000 ||
	   device.name == "HS100" || device.name == "HS200") {
		logDebug("sendCmd: Attempting to connect.....")
		try {
			interfaces.rawSocket.connect("${getDataValue("deviceIP")}", 
										 9999, byteInterface: true)
		} catch (error) {
			logDebug("SendCmd: Unable to connect to device at ${getDataValue("deviceIP")}. " +
					 "Error = ${error}")
		}
	}
	interfaces.rawSocket.sendMessage(command)
}
def socketStatus(message) {
	if (message == "receive error: Stream closed.") {
		logDebug("socketStatus: Socket Established")
	} else {
		logWarn("socketStatus = ${message}")
	}
}
def parse(message) {
	def respLength
	if (message.length() > 8 && message.substring(0,4) == "0000") {
		def hexBytes = message.substring(0,8)
		respLength = 8 + 2 * hubitat.helper.HexUtils.hexStringToInt(hexBytes)
		if (message.length() == respLength) {
			distResp(message)
			state.lastConnect = now()
		} else {
			state.response = message
			state.respLength = respLength
		}
	} else {
		def resp = state.response
		resp = resp.concat(message)
		if (resp.length() == state.respLength) {
			state.response = ""
			state.respLength = 0
			state.lastConnect = now()
			distResp(resp)
		} else {
			state.response = resp
		}
	}
}

//	-- Encryption / Decryption
private outputXOR(command) {
	def str = ""
	def encrCmd = "000000" + Integer.toHexString(command.length()) 
 	def key = 0xAB
	for (int i = 0; i < command.length(); i++) {
		str = (command.charAt(i) as byte) ^ key
		key = str
		encrCmd += Integer.toHexString(str)
	}
   	return encrCmd
}
private inputXOR(resp) {
	String[] strBytes = resp.substring(8).split("(?<=\\G.{2})")
	def cmdResponse = ""
	def key = 0xAB
	def nextKey
	byte[] XORtemp
	for(int i = 0; i < strBytes.length; i++) {
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative
		XORtemp = nextKey ^ key
		key = nextKey
		cmdResponse += new String(XORtemp)
	}
	return cmdResponse
}
//	 ===== Logging =====
def logTrace(msg){ log.trace "${driverVer()} ${device.label} ${msg}" }
def logInfo(msg) { log.info "${driverVer()} ${device.label} ${msg}" }
def logDebug(msg){ log.debug "${driverVer()} ${device.label} ${msg}" }
def logWarn(msg){ log.warn "${driverVer()} ${device.label} ${msg}" }

//	end of file