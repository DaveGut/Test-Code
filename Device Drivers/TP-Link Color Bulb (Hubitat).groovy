/*
TP-Link Device Driver, Version 4.2

	Copyright 2018, 2019 Dave Gutheinz

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

DISCLAIMER:  This Applicaion and the associated Device Drivers are in no way sanctioned or supported by TP-Link.  
All  development is based upon open-source data on the TP-Link devices; primarily various users on GitHub.com.

===== History =====
2.04.19	4.1.01.	Final code for Hubitat without reference to deviceType and enhancement of logging functions. Added
				code to delete the device as a child from the application when deleted via the devices page.  Note:
				The device is also deleted whenever the application is deleted.
3.28.19	4.2.01	a.	Added capability Change Level implementation.
				b.	Removed info log preference.  Will always log these messages (one per response)
				c.	Added user command to synchronize the Kasa App name with the Hubitat device label
					(using Hubitat as the master name).
Methods to update data structure during installation from smart app.
*/
def driverVer() { return "4.2.01" }
metadata {
	definition (name: "TP-Link Color Bulb", 
    			namespace: "davegut", 
                author: "Dave Gutheinz") {
        capability "Light"
		capability "Switch"
		capability "Switch Level"
		capability "Change Level"
 		capability "Refresh"
		capability "Actuator"
		capability "Color Temperature"
		command "setCircadian"
		attribute "circadianState", "string"
		capability "Color Control"
		capability "Color Mode"
		attribute "commsError", "string"
		command "syncKasaName"
	}
	preferences {
		if (!getDataValue("applicationVersion")) {
			input ("device_IP", "text", title: "Device IP (Current = ${getDataValue("deviceIP")})")
		}
		def refreshRate = [:]
		refreshRate << ["1" : "Refresh every minute"]
		refreshRate << ["5" : "Refresh every 5 minutes"]
		refreshRate << ["10" : "Refresh every 10 minutes"]
		refreshRate << ["15" : "Refresh every 15 minutes"]
		refreshRate << ["30" : "Refresh every 30 minutes"]
		input ("refresh_Rate", "enum", title: "Device Refresh Interval", options: refreshRate)
        input ("transition_Time", "num", title: "Default Transition time (seconds)")
    	input name: "highRes", type: "bool", title: "High Resolution Hue Scale", required: false
    	input name: "traceLog", type: "bool", title: "Display trace messages?", required: false
	}
}

def installed() {
	log.info "Installing ............"
	runIn(2, updated)
}

def updated() {
	log.info "Updating ............."
	unschedule()
	updateDataValue("driverVersion", driverVer())
	if(device_IP) { updateDataValue("deviceIP", device_IP) }
	if (traceLog == true) { runIn(1800, stopTraceLogging) }
	else { stopTraceLogging() }
	sendEvent(name: "commsError", value: "none")
	switch(refresh_Rate) {
		case 1 :
			runEvery1Minute(refresh)
			break
		case 5 :
			runEvery5Minutes(refresh)
			break
		case 10 :
			runEvery10Minutes(refresh)
			break
		case 15 :
			runEvery15Minutes(refresh)
			break
		default:
			runEvery30Minutes(refresh)
	}
	if (transition_Time) {
		state.transTime = 1000*transition_Time.toInteger()
	} else { state.transTime = 0 }
	if (getDataValue("deviceIP")) { refresh() }
}

def updateInstallData() {
	logInfo "Updating previous installation data"
	if (transition_Time) { state.transTime = 1000*transition_Time.toInteger() }
	else { state.transTime = 0 }
	updateDataValue("hueScale", null)
	updateDataValue("driverVersion", driverVer())
}

void uninstalled() {
	try {
		def alias = device.label
		logInfo("Removing device ...")
		parent.removeChildDevice(alias, device.deviceNetworkId)
	} catch (ex) {
		logInfo("Either the device was manually installed or there was an error.")
	}
}

def on() {
	logTrace("On: transition time = ${state.transTime}")
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"on_off":1,"transition_period":${state.transTime}}}}""")
}

def off() {
	logTrace("Off: transition time = ${state.transTime}")
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"on_off":0,"transition_period":${state.transTime}}}}""")
}

def setLevel(percentage) {
	logTrace("setLevel(x): transition time = ${state.transTime}")
	setLevel(percentage, state.transTime)
}

def setLevel(percentage, rate) {
	logTrace("setLevel(x,x): rate = ${rate} // percentage = ${percentage}")
	if (percentage < 0 || percentage > 100) {
		log.error "$device.name $device.label: Entered brightness is not from 0...100"
		percentage = 50
	}
	percentage = percentage as int
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"ignore_default":1,"on_off":1,"brightness":${percentage},"transition_period":${rate}}}}""")
}

def startLevelChange(direction) {
	logTrace("startLevelChange: direction = ${direction}")
	if (direction == "up") {
		levelUp()
	} else {
		levelDown()
	}
}

def stopLevelChange() {
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
	logTrace("setColorTemperature: colorTemp = ${kelvin}")
	if (kelvin == null) kelvin = state.lastColorTemp
	if (kelvin < 2500) kelvin = 2500
	if (kelvin > 9000) kelvin = 9000
	kelvin = kelvin as int
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"ignore_default":1,"on_off":1,"color_temp": ${kelvin},"hue":0,"saturation":0}}}""")
}

def setCircadian() {
	logTrace("setCircadian")
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"mode":"circadian"}}}""")
}

def setHue(hue) {
	logTrace("setHue:  hue = ${hue} // saturation = ${state.lastSaturation}")
	saturation = state.lastSaturation
	setColor([hue: hue, saturation: saturation])
}

def setSaturation(saturation) {
	logTrace("setSaturation: saturation = ${saturation} // hue = {state.lastHue}")
	hue = state.lastHue
	setColor([hue: hue, saturation: saturation])
}

def setColor(Map color) {
	logTrace("setColor:  color = ${color}")
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
		log.error "${device.label}: Entered hue or saturation out of range!"
        return
    }
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"ignore_default":1,"on_off":1,"brightness":${percentage},"color_temp":0,"hue":${hue},"saturation":${saturation}}}}""")
}

def refresh(){
	sendCmd('{"system":{"get_sysinfo":{}}}')
}

def setColorTempData(temp){
	logTrace("setColorTempData: color temperature = ${temp}")
    def value = temp.toInteger()
	state.lastColorTemp = value
    def genericName
    if (value < 2400) genericName = "Sunrise"
    else if (value < 2800) genericName = "Incandescent"
    else if (value < 3300) genericName = "Soft White"
    else if (value < 3500) genericName = "Warm White"
    else if (value < 4150) genericName = "Moonlight"
    else if (value <= 5000) genericName = "Horizon"
    else if (value < 5500) genericName = "Daylight"
    else if (value < 6000) genericName = "Electronic"
    else if (value <= 6500) genericName = "Skylight"
    else if (value < 20000) genericName = "Polar"
	logInfo "${device.getDisplayName()} Color Mode is CT.  Color is ${genericName}."
 	sendEvent(name: "colorMode", value: "CT")
    sendEvent(name: "colorName", value: genericName)
}

def setRgbData(hue, saturation){
	logTrace("setRgbData: hue = ${hue} // highRes = ${highRes}")
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

def syncKasaName() {
	logTrace("syncKasaName.  Updating Kasa App Name to ${device.label}")
	sendCmd("""{"smartlife.iot.common.system":{"set_dev_alias":{"alias":"${device.label}"}}}""")
}
	
def parse(response) {
	unschedule(createCommsError)
	sendEvent(name: "commsError", value: "none")
	def encrResponse = parseLanMessage(response).payload
	def cmdResponse
	try {
		cmdResponse = parseJson(inputXOR(encrResponse))
		logTrace("parseInput: response = ${cmdResponse}")
	} catch (error) {
		def errMsg = "Unable to process return from the device due to fragmented return from device.\n" +
					 "Change device Name in the Kasa Application to less than 18 characters."
		log.error "${device.label} ${driverVer()} CommsError: Device Name too long!\n${errMsg}"
		sendEvent(name: "commsError", value: "Device Name too long", description: "See log for corrective action.")
	}
	def status
	if (cmdResponse.system) {
		logTrace("parse: Refresh Response")
		status = cmdResponse.system.get_sysinfo.light_state
	} else if (cmdResponse["smartlife.iot.smartbulb.lightingservice"]){
		logTrace("parse: Command Response")
		status = cmdResponse["smartlife.iot.smartbulb.lightingservice"].transition_light_state
	} else if (cmdResponse["smartlife.iot.common.system"].set_dev_alias) {
		logInfo("Updated Kasa Name for device to ${device.label}.")
		return
	} else {
		log.error "Unprogrammed return in parse.  cmdResponse = ${cmdResponse}"
		sendEvent(name: "commsError", value: "Unprogrammed return in parse", description: "See log for details.")
		return
	}

	logTrace("parseBulbState: status = ${status}")
	if (status.on_off == 0) {
		sendEvent(name: "switch", value: "off")
		logInfo("Power: off")						//	UPDATED
		sendEvent(name: "circadianState", value: "normal")
	} else {
		sendEvent(name: "switch", value: "on")
		sendEvent(name: "level", value: status.brightness)
		def color = [:]
		def hue = status.hue.toInteger()
		if (highRes != true) { hue = (hue / 3.6).toInteger() }
		color << ["hue" : hue]
		color << ["saturation" : status.saturation]
		sendEvent(name: "circadianState", value: status.mode)
		sendEvent(name: "colorTemperature", value: status.color_temp)
		sendEvent(name: "hue", value: hue)
		sendEvent(name: "saturation", value: status.saturation)
		sendEvent(name: "color", value: color)
		logInfo ("$Power: on / Brightness: ${status.brightness}% / " +											//	UPDATED
				 "Circadian State: ${status.mode} / Color Temp: ${status.color_temp}K / Color: ${color}")		//	UPDATED
		if (status.color_temp.toInteger() == 0) { setRgbData(hue, status.saturation) }
		else { setColorTempData(status.color_temp) }
	}
}

private sendCmd(command) {
	logTrace("sendCmd: command = ${command} // device IP = ${getDataValue("deviceIP")}")
	if (!getDataValue("deviceIP")) {
		log.error "No device IP in a manual installation. Update Preferences."
		return
	}
	runIn(10, createCommsError)
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command), 
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${getDataValue("deviceIP")}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING])
	sendHubCommand(myHubAction)
}

def createCommsError() {
	def errMsg = "The device is not found at the current IP address. Caused by either the IP changed " +
				 "or the device not having power.  Check device for power.\n\n" +
				 "To update IP either run the Hubitat TP-Link App or updated preferences for a manual installation"
	sendEvent(name: "commsError", value: "Device Offline",descriptionText: "See log for corrective action.")
	log.error "${device.label} ${driverVer()} CommsError: Device Offline.\n${errMsg}"
}

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
	logTrace("inputXOR: cmdResponse = ${cmdResponse}")
	return cmdResponse
}

def logInfo(msg) {	
	log.info "${device.label} ${driverVer()} ${msg}"
}

def logTrace(msg){
	if(traceLog == true) { log.trace "${device.label} ${driverVer()} ${msg}" }
}

def stopTraceLogging() {
	log.trace "stopTraceLogging: Trace Logging is off."
	try { device.updateSetting("traceLog", [type:"bool", value: false]) }
	catch (e) {}
}

//	end-of-file