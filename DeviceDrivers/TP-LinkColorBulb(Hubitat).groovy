/*
TP-Link Device Driver, Version 4.5

	Copyright 2018, 2019 Dave Gutheinz

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

DISCLAIMER:  This Applicaion and the associated Device Drivers are in no way sanctioned or supported by TP-Link.  
All  development is based upon open-source data on the TP-Link devices; primarily various users on GitHub.com.

===== History =====
2.04.19	4.1.01.	Final code for Hubitat without reference to deviceType and enhancement of logging functions.
3.28.19	4.2.01	a.	Added capability Change Level implementation.
				c.	Added user command to synchronize the Kasa App name with the Hubitat device label.
				d.	Added method updateInstallData called from app on initial update only.
7.01.19	4.3.01	a.	Updated communications architecture, reducing required logic (and error potentials).
				b.	Added import ability for driver from the HE editor.
				c.	Added preference for synching name between hub and device.  Deleted command syncKasaName.
8.25.19	4.3.02	Added comms re-transmit on FIRST time a communications doesn't succeed.  Device will
				attempt up to 5 retransmits.
9.21.19	4.4.01	Added link to Application that will check/update IPs if the communications fail.
================================================================================================*/
def driverVer() { return "4.5.01" }
	def bulbType() { return "Color Bulb" }
//	def bulbType() { return "Tunable White Bulb" }
//	def bulbType() { return "Soft White Bulb" }

metadata {
	definition (name: "TP-Link ${bulbType()}",
    			namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/Hubitat-TP-Link-Integration/master/DeviceDrivers/TP-LinkColorBulb(Hubitat).groovy"
//				importUrl: "https://raw.githubusercontent.com/DaveGut/Hubitat-TP-Link-Integration/master/DeviceDrivers/TP-LinkCTBulb(Hubitat).groovy"
//				importUrl: "https://raw.githubusercontent.com/DaveGut/Hubitat-TP-Link-Integration/master/DeviceDrivers/TP-LinkWhiteBulb(Hubitat).groovy"
			   ) {
        capability "Light"
		capability "Switch"
		capability "Switch Level"
		capability "Change Level"
 		capability "Refresh"
		capability "Actuator"
		attribute "commsError", "bool"
		if (bulbType() == "Color Bulb" || bulbType() == "Tunable White Bulb") {
			capability "Color Temperature"
			command "setCircadian"
			attribute "circadianState", "string"
			capability "Color Mode"
		}
		if (bulbType() == "Color Bulb") {
			capability "Color Control"
		}
	}
	preferences {
		if (!getDataValue("applicationVersion")) {
			input ("device_IP", "text", title: "Device IP (Current = ${getDataValue("deviceIP")})")
		}
		input ("transition_Time", "num", title: "Default Transition time (seconds)", defaultValue: 0)
		if (type == "Color Bulb") {
			input ("highRes", "bool", title: "High Resolution Hue Scale", defaultValue: false)
		}
		input ("refresh_Rate", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"], defaultValue: "30")
		input ("nameSync", "enum", title: "Synchronize Names", defaultValue: "none",
			   options: ["none": "Don't synchronize",
						 "device" : "Kasa device name master", 
						 "hub" : "Hubitat label master"])
		input ("debug", "bool", title: "Enable debug logging", defaultValue: false)
		input ("descriptionText", "bool", title: "Enable description text logging", defaultValue: true)
	}
}

def installed() {
	log.info "Installing .."
	runIn(2, updated)
}

def updated() {
	log.info "Updating .."
	unschedule()

	if (!getDataValue("applicationVersion")) {
		if (!device_IP) {
			logWarn("updated:  deviceIP  is not set.")
			return
		}
		updateDataValue("deviceIP", device_IP)
		logInfo("Device IP set to ${getDataValue("deviceIP")}")
	}
	if (getDataValue("driverVersion") != driverVer()) {
		updateInstallData()
		updateDataValue("driverVersion", driverVer())
	}

	switch(refresh_Rate) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		default: runEvery30Minutes(refresh)
	}
	state.transTime = 1000*transition_Time.toInteger()
	state.errorCount = 0

	logInfo("Debug logging is: ${debug}.")
	logInfo("Description text logging is ${descriptionText}.")
	logInfo("Refresh set for every ${refresh_Rate} minute(s).")
	logInfo("ShortPoll set for ${shortPoll}")

	if (nameSync == "device" || nameSync == "hub") { runIn(5, syncName) }
	refresh()
}

def updateInstallData() {
	logInfo("updateInstallData: Updating installation to driverVersion ${driverVer()}")
	updateDataValue("driverVersion", driverVer())
	if (getDataValue("plugId")) { updateDataValue("plugId", null) }
	if (getDataValue("plugNo")) { updateDataValue("plugNo", null) }
	if (getDataValue("hueScale")) { updateDataValue("hueScale", null) }
	state.remove("currentError")
	pauseExecution(1000)
	state.remove("commsErrorCount")
	pauseExecution(1000)
	state.remove("updated")
}


//	Device Commands
def on() {
	logDebug("On: transition time = ${state.transTime}")
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"on_off":1,"transition_period":${state.transTime}}}}""", "commandResponse")
}

def off() {
	logDebug("Off: transition time = ${state.transTime}")
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"on_off":0,"transition_period":${state.transTime}}}}""", "commandResponse")
}

def setLevel(percentage, rate = null) {
	logDebug("setLevel(x,x): rate = ${rate} // percentage = ${percentage}")
	if (percentage < 0 || percentage > 100) {
		logWarn("$device.name $device.label: Entered brightness is not from 0...100")
		return
	}
	if (rate == null) {
		rate = state.transTime.toInteger()
	} else {
		rate = 1000*rate.toInteger()
	}
	percentage = percentage.toInteger()
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"ignore_default":1,"on_off":1,"brightness":${percentage},"transition_period":${rate}}}}""", "commandResponse")
}

def startLevelChange(direction) {
	logDebug("startLevelChange: direction = ${direction}")
	if (direction == "up") {
		levelUp()
	} else {
		levelDown()
	}
}

def stopLevelChange() {
	logDebug("stopLevelChange")
	unschedule(levelUp)
	unschedule(levelDown)
}

def levelUp() {
	def newLevel = device.currentValue("level").toInteger() + 2
	if (newLevel > 101) { return }
	if (newLevel > 100) { newLevel = 100 }
	setLevel(newLevel, 0)
	runInMillis(500, levelUp)
}

def levelDown() {
	def newLevel = device.currentValue("level").toInteger() - 2
	if (newLevel < -1) { return }
	else if (newLevel <= 0) { off() }
	else {
		setLevel(newLevel, 0)
		runInMillis(500, levelDown)
	}
}

def setColorTemperature(kelvin) {
	//	type = Color Bulb or Tunable White Bulb
	logDebug("setColorTemperature: colorTemp = ${kelvin}")
	Integer lowK = 2500
	Integer highK = 9000
	if (bulbType() == "Tunable White Bulb") {
		lowK = 2700
		highK = 6500
	}
	if (kelvin < lowK) kelvin = lowK
	if (kelvin > highK) kelvin = highK
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"ignore_default":1,"on_off":1,"color_temp": ${kelvin},"hue":0,"saturation":0}}}""", "commandResponse")
}

def setCircadian() {
	//	type = Color Bulb or Tunable White Bulb
	logDebug("setCircadian")
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"mode":"circadian"}}}""", "commandResponse")
}

def setHue(hue) {
	//	type = Color Bulb
	logDebug("setHue:  hue = ${hue} // saturation = ${state.lastSaturation}")
	saturation = state.lastSaturation
	setColor([hue: hue, saturation: saturation])
}

def setSaturation(saturation) {
	//	type = Color Bulb
	logDebug("setSaturation: saturation = ${saturation} // hue = {state.lastHue}")
	hue = state.lastHue
	setColor([hue: hue, saturation: saturation])
}

def setColor(Map color) {
	//	type = Color Bulb
	logDebug("setColor:  color = ${color}")
	if (color == null) color = [hue: state.lastHue, saturation: state.lastSaturation, level: device.currentValue("level")]
	def percentage = 100
	if (!color.level) { 
		percentage = device.currentValue("level")
	} else {
		percentage = color.level
	}
    def hue = color.hue.toInteger()
    if (highRes != true) { 
		hue = Math.round(0.5 + hue * 3.6).toInteger()
	}
	def saturation = color.saturation as int
	if (hue < 0 || hue > 360 || saturation < 0 || saturation > 100) {
		logWarn("${device.label}: Entered hue or saturation out of range!")
        return
    }
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"ignore_default":1,"on_off":1,"brightness":${percentage},"color_temp":0,"hue":${hue},"saturation":${saturation}}}}""", "commandResponse")
}

def refresh(){
	logDebug("refresh")
	sendCmd("""{"system":{"get_sysinfo":{}}}""", "refreshResponse")
}


//	Device command parsing methods
def commandResponse(response) {
	def cmdResponse = parseInput(response)
	def status = cmdResponse["smartlife.iot.smartbulb.lightingservice"].transition_light_state
	logDebug("commandResponse: status = ${status}")
	updateBulbData(status)
}

def refreshResponse(response) {
	def cmdResponse = parseInput(response)
	def status = cmdResponse.system.get_sysinfo.light_state
	logDebug("refreshResponse: status = ${status}")
	updateBulbData(status)
}

def updateBulbData(status) {
	logDebug("updateBulbData: ${status}")
	if (status.on_off == 0) {
		sendEvent(name: "switch", value: "off")
		logInfo("Power: off")
		sendEvent(name: "circadianState", value: "normal")
	} else {
		sendEvent(name: "switch", value: "on")
		sendEvent(name: "level", value: status.brightness)
		if (bulbType() == "Soft White Bulb") {
			logInfo("Power: on / Brightness: ${status.brightness}%")
		}
		if (bulbType() == "Tunable White Bulb") {
			sendEvent(name: "circadianState", value: status.mode)
			sendEvent(name: "colorTemperature", value: status.color_temp)
			logInfo("Power: on / Brightness: ${status.brightness}% / " +
					 "Circadian State: ${status.mode} / Color Temp: " +
					 "${status.color_temp}K")
			setColorTempData(status.color_temp)		}
		if (bulbType() == "Color Bulb") {
			sendEvent(name: "circadianState", value: status.mode)
			sendEvent(name: "colorTemperature", value: status.color_temp)
			def color = [:]
			def hue = status.hue.toInteger()
			if (highRes != true) { hue = (hue / 3.6).toInteger() }
			color << ["hue" : hue]
			color << ["saturation" : status.saturation]
			color << ["level" : status.brightness]
			sendEvent(name: "hue", value: hue)
			sendEvent(name: "saturation", value: status.saturation)
			sendEvent(name: "color", value: color)
			logInfo("Power: on / Brightness: ${status.brightness}% / " +
					 "Circadian State: ${status.mode} / Color Temp: " +
					 "${status.color_temp}K / Color: ${color}")
			if (status.color_temp.toInteger() == 0) { setRgbData(hue, status.saturation) }
			else { setColorTempData(status.color_temp) }
		}
	}
}

def setColorTempData(temp){
	logDebug("setColorTempData: color temperature = ${temp}")
    def value = temp.toInteger()
	state.lastColorTemp = value
    def genericName
	if (value <= 2800) { genericName = "Incandescent" }
	else if (value <= 3300) { genericName = "Soft White" }
	else if (value <= 3500) { genericName = "Warm White" }
	else if (value <= 4150) { genericName = "Moonlight" }
	else if (value <= 5000) { genericName = "Horizon" }
	else if (value <= 5500) { genericName = "Daylight" }
	else if (value <= 6000) { genericName = "Electronic" }
	else if (value <= 6500) { genericName = "Skylight" }
	else { genericName = "Polar" }
	logInfo "${device.getDisplayName()} Color Mode is CT.  Color is ${genericName}."
 	sendEvent(name: "colorMode", value: "CT")
    sendEvent(name: "colorName", value: genericName)
}

def setRgbData(hue, saturation){
	logDebug("setRgbData: hue = ${hue} // highRes = ${highRes}")
    def colorName
    hue = hue.toInteger()
	state.lastHue = hue
	state.lastSaturation = saturation
	if (highRes != true) { hue = (hue * 3.6).toInteger() }
    switch (hue.toInteger()){
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
    }
	logInfo "${device.getDisplayName()} Color Mode is RGB.  Color is ${colorName}."
 	sendEvent(name: "colorMode", value: "RGB")
    sendEvent(name: "colorName", value: colorName)
}


//	Synchronize Names between Device and Hubitat
def syncName() {
	logDebug("syncName. Synchronizing device name and label with master = ${nameSync}")
	if (nameSync == "hub") {
		sendCmd("""{"system":{"set_dev_alias":{"alias":"${device.label}"}}}""", "nameSyncHub")
	} else if (nameSync == "device") {
		sendCmd("""{"system":{"get_sysinfo":{}}}""", "nameSyncDevice")
	}
}
def nameSyncHub(response) {
	def cmdResponse = parseInput(response)
	logInfo("Setting deviceIP for program.")
}
def nameSyncDevice(response) {
	def cmdResponse = parseInput(response)
	def alias = cmdResponse.system.get_sysinfo.alias
	device.setLabel(alias)
	logInfo("Hubit name for device changed to ${alias}.")
}


//	Communications and initial common parsing
private sendCmd(command, action) {
	logDebug("sendCmd: command = ${command} // device IP = ${getDataValue("deviceIP")}, action = ${action}")
	state.lastCommand = [command: "${command}", action: "${action}"]
	runIn(3, setCommsError)
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command),
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${getDataValue("deviceIP")}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 timeout: 3,
		 callback: action])
	sendHubCommand(myHubAction)
}
def parseInput(response) {
	unschedule(setCommsError)
	state.errorCount = 0
	sendEvent(name: "commsError", value: false)
	try {
		def encrResponse = parseLanMessage(response).payload
		def cmdResponse = parseJson(inputXOR(encrResponse))
		return cmdResponse
	} catch (error) {
		logWarn "CommsError: Fragmented message returned from device."
	}
}
def setCommsError() {
	logDebug("setCommsError")
	if (state.errorCount < 3) {
		state.errorCount+= 1
		repeatCommand()
		logWarn("Attempt ${state.errorCount} to recover communications")
	} else if (state.errorCount == 3) {
		state.errorCount += 1
		//	If a child device, update IPs automatically using the application.
		if (getDataValue("applicationVersion")) {
			logWarn("setCommsError: Parent commanded to poll for devices to correct error.")
			parent.updateDevices()
			runIn(90, repeatCommand)
		}
	} else {
		sendEvent(name: "commsError", value: true)
		logWarn "setCommsError: No response from device.  Refresh.  If off line " +
				"persists, check IP address of device."
	}
}
def repeatCommand() { 
	logDebug("repeatCommand: ${state.lastCommand}")
	sendCmd(state.lastCommand.command, state.lastCommand.action)
}


//	Utility Methods
private outputXOR(command) {
	def str = ""
	def encrCmd = ""
 	def key = 0xAB
	for (int i = 0; i < command.length(); i++) {
		str = (command.charAt(i) as byte) ^ key
		key = str
		encrCmd += Integer.toHexString(str)
	}
   	return encrCmd
}
private inputXOR(encrResponse) {
	String[] strBytes = encrResponse.split("(?<=\\G.{2})")
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
def logInfo(msg) {
	if (descriptionText == true) { log.info "<b>${device.label} ${driverVer()}</b> ${msg}" }
}
def logDebug(msg){
	if(debug == true) { log.debug "<b>${device.label} ${driverVer()}</b> ${msg}" }
}
def logWarn(msg){ log.warn "<b>${device.label} ${driverVer()}</b> ${msg}" }

//	end-of-file