/*
TP-Link Bulb Device Handler, Version 4.0

	Copyright 2019 Dave Gutheinz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this  file except in compliance with the
License. You may obtain a copy of the License at:

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, 
software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
either express or implied. See the License for the specific 
language governing permissions and limitations under the 
License.

Discalimer:  This Applicaion and the associated Device 
Drivers are in no way sanctioned or supported by TP-Link.  
All  development is based upon open-source data on the 
TP-Link devices; primarily various users on GitHub.com.

===== History ================================================
01.01.19	Version 4.0 device driver created.  Does not
			require Node Applet nor Kasa Account to control
			devices.
01.04.19	4.0.02. Updated command response processing to
			eliminate unnecessary refresh command and reduce
			communications loads.
01.05.19	4.0.03. Added 30 minute refresh option.  Added
			Preference for logTrace (default is false.  Added
			error handling sequence in device return.

//	===== Device Type Identifier ===========================*/
	def driverVer() { return "4.0.03" }
//	def deviceType() { return "Soft White Bulb" }
//	def deviceType() { return "Tunable White Bulb" }
	def deviceType() { return "Color Bulb" }
//	==========================================================
metadata {
	definition (name: "TP-Link ${deviceType()}", 
    			namespace: "davegut", 
                author: "Dave Gutheinz") {
        capability "Light"
		capability "Switch"
		capability "Switch Level"
        capability "Actuator"
		capability "Refresh"
		if (deviceType() != "Soft White Bulb") {
			capability "Color Temperature"
			command "setCircadian"
			attribute "circadianState", "string"
		}
		if (deviceType() == "Color Bulb") {
			capability "Color Control"
			capability "Color Mode"
		}
	}

    preferences {
        input ("transition_Time", "num", title: "Default Transition time (seconds)")
		
		def refreshRate = [:]
		refreshRate << ["1" : "Refresh every minute"]
		refreshRate << ["5" : "Refresh every 5 minutes"]
		refreshRate << ["10" : "Refresh every 10 minutes"]
		refreshRate << ["15" : "Refresh every 15 minutes"]
		refreshRate << ["30" : "Refresh every 30 minutes"]
		input ("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate)

		if (deviceType() == "Color Bulb") {
		    def hueScale = [:]
		    hueScale << ["highRez": "High Resolution (0 - 360)"]
		    hueScale << ["lowRez": "Low Resolution (0 - 100)"]
	        input ("hue_Scale", "enum", title: "High or Low Res Hue", options: hueScale)
        }
		
		input ("device_IP", "text", title: "Device IP (Current = ${getDataValue("deviceIP")})")
    	input name: "traceLog", type: "bool", title: "Display trace messages?", required: false
	}
}

//	===== Update when installed or setting changed =====
def installed() {
	log.info "Installing ${device.label}..."
	state.currentError = null

	device.updateSetting("refresh_Rate",[type:"enum", value:"15"])
	device.updateSetting("transition_Time",[type:"num", value:0])
	if (deviceType() == "Color Bulb") {
		device.updateSetting("hue_Scale",[type:"enum", value:"lowRez"])
	}

	updated()
}

def ping() {
	refresh()
}

def updated() {
	log.info "Updating ${device.label}..."
	unschedule()

	updateDataValue("driverVersion", driverVer())
	if(device_IP) {
		updateDataValue("deviceIP", device_IP)
	}
	if(deviceIP) {
		updateDataValue("deviceIP", deviceIP)
	}


	switch(refresh_Rate) {
		case "1" :
			runEvery1Minute(refresh)
			break
		case "5" :
			runEvery5Minutes(refresh)
			break
		case "10" :
			runEvery10Minutes(refresh)
			break
		case "30" :
			runEvery30Minutes(refresh)
			break
		default:
			runEvery15Minutes(refresh)
	}

	if (getDataValue("deviceIP")) { refresh() }
}

//	===== Basic Bulb Control/Status =====
def on() {
	logTrace("On: transition_Time = ${transition_Time}")
	def transTime = 1000*transition_Time.toInteger()
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"on_off":1,"transition_period":${transTime}}}}""", "commandResponse")
}

def off() {
	logTrace("off: transition_Time = ${transition_Time}")
	def transTime = 1000*transition_Time.toInteger()
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"on_off":0,"transition_period":${transTime}}}}""", "commandResponse")
}

def setLevel(percentage) {
	logTrace("setLevel(x): transition_Time = ${transition_Time}")
	setLevel(percentage, transition_Time)
}

def setLevel(percentage, rate) {
	logTrace("setLevel(x,x): rate = ${rate} // percentage = ${percentage}")
	if (percentage < 0 || percentage > 100) {
		log.error "$device.name $device.label: Entered brightness is not from 0...100"
		percentage = 50
	}
	percentage = percentage as int
	rate = 1000*rate.toInteger()
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"ignore_default":1,"on_off":1,"brightness":${percentage},"transition_period":${rate}}}}""", "commandResponse")
}

def setColorTemperature(kelvin) {
	logTrace("setColorTemperature: colorTemp = ${kelvin}")
	if (kelvin == null) kelvin = state.lastColorTemp
	switch(deviceType()) {
		case "Tunable White Bulb" :
			if (kelvin < 2700) kelvin = 2700
			if (kelvin > 6500) kelvin = 6500
			break
		defalut:
			if (kelvin < 2500) kelvin = 2500
			if (kelvin > 9000) kelvin = 9000
	}
	kelvin = kelvin as int
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"ignore_default":1,"on_off":1,"color_temp": ${kelvin},"hue":0,"saturation":0}}}""", "commandResponse")
}

def setCircadian() {
	logTrace("setCircadian")
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"mode":"circadian"}}}""", "commandResponse")
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
    if (hue_Scale == "lowRez") { 
		hue = Math.round(0.5 + hue * 3.6).toInteger()
	}
	def saturation = color.saturation as int
    if (hue < 0 || hue > 360 || saturation < 0 || saturation > 100) {
		log.error "${device.label}: Entered hue or saturation out of range!"
        return
    }
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"ignore_default":1,"on_off":1,"brightness":${percentage},"color_temp":0,"hue":${hue},"saturation":${saturation}}}}""", "commandResponse")
}

def refresh(){
	sendCmd('{"system":{"get_sysinfo":{}}}', "refreshResponse")
}

def setColorTempData(temp){
	logTrace("setColorTempData: color temperature = {temp}")
    def value = temp.toInteger()
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
	log.info "${device.getDisplayName()} Color Mode is CT.  Color is ${genericName}."
 	sendEvent(name: "colorMode", value: "CT")
    sendEvent(name: "colorName", value: genericName)
}

def setRgbData(hue){
	logTrace("setRgbData: hue = ${hue} // hueScale = ${hue_Scale}")
    def colorName
    hue = hue.toInteger()
	if (hue_Scale == "lowRez") { hue = (hue * 3.6).toInteger() }
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
	log.info "${device.getDisplayName()} Color Mode is RGB.  Color is ${colorName}."
 	sendEvent(name: "colorMode", value: "RGB")
    sendEvent(name: "colorName", value: colorName)
}

//	===== Send the Command =====
private sendCmd(command, action) {
	logTrace("sendCmd: command = ${command} // action = ${action} // device IP = ${getDataValue("deviceIP")}")
	if (!getDataValue("deviceIP")) {
		state.currentError = "No device IP. Update Preferences."
		log.error "No device IP. Update Preferences."
		return
	}
	runIn(5, createCommsError)	//	Starts 3 second timer for error.
	
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command), 
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${getDataValue("deviceIP")}:9999",
		encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		callback: action])
	sendHubCommand(myHubAction)
}

def createCommsError() {
	state.currentError = "Comms Error. Device offline or IP has changed. Check and run Preferences."
	log.error "Comms Error. Device offline or IP has changed. Check and run Preferences."
}

def commandResponse(response) {
log.debug "commandResponse:  ${response}"
	unschedule(createCommsError)
	state.currentError = null
	def encrResponse = parseLanMessage(response).payload

	try {
		def cmdResponse = parseJson(inputXOR(encrResponse))
		logTrace("commandResponse: cmdResponse = ${cmdResponse}")
		def status = cmdResponse["smartlife.iot.smartbulb.lightingservice"].transition_light_state
		parseBulbState(status)
	} catch (error) {
		runIn(5, refresh)
		log.error "${device.label} commandResponse fragmented return from device.  In Kasa App reduce device name to less that 18 characters!"
	}
}

def refreshResponse(response){
log.debug "refreshResponse:  ${response}"
	unschedule(createCommsError)
	state.currentError = null
	def encrResponse = parseLanMessage(response).payload
	
	try {
		def cmdResponse = parseJson(inputXOR(encrResponse))
		logTrace("refreshResponse: refreshResponse = ${cmdResponse}")
		def status = cmdResponse.system.get_sysinfo.light_state
		parseBulbState(status)
	} catch (error) {
		log.error "${device.label} refreshResponse fragmented return from device.  In Kasa App reduce device name to less that 18 characters!"
	}
}

def parseBulbState(status) {
	logTrace("parseBulbState: status = ${status}")
	def onOff = status.on_off
	if (onOff == 1) {
		onOff = "on"
	} else {
		onOff = "off"
		status = status.dft_on_state
	}
	sendEvent(name: "switch", value: onOff)
	if (onOff == "off") {
		log.info "${device.label}: Power: ${onOff}"
		return
	}
	
	def level = status.brightness
	sendEvent(name: "level", value: level)
	if (deviceType() == "Soft White Bulb") {
		log.info "${device.label}: Power: ${onOff} / Brightness: ${level}%"
		return
	}
	def circadianState = status.mode
	def color_temp = status.color_temp
	sendEvent(name: "circadianState", value: circadianState)
	sendEvent(name: "colorTemperature", value: color_temp)
	if (deviceType() == "Tunable White Bulb") {
		state.lastColorTemp = color_temp
		setColorTempData(color_temp)
		log.info "${device.label}: Power: ${onOff} / Brightness: ${level}% / Circadian State: ${circadianState} / Color Temp: ${color_temp}K"
		return
	}
	
	def hue = status.hue.toInteger()
	def saturation = status.saturation
	def color = [:]
	if (hue_Scale == "lowRez") { 
		hue = (hue / 3.6).toInteger()
	}
	color << ["hue" : hue]
	color << ["saturation" : status.saturation]
	sendEvent(name: "hue", value: hue)
	sendEvent(name: "saturation", value: saturation)
	sendEvent(name: "color", value: color)
	
	if (color_temp.toInteger() == 0) {
		state.lastHue = hue
		state.lastSaturation = saturation
        setRgbData(hue)
	} else {
		state.lastColorTemp = color_temp
        setColorTempData(color_temp)
	}
	log.info "${device.label}: Power: ${onOff} / Brightness: ${level}% / Circadian State: ${circadianState} / Color Temp: ${color_temp}K / Color: ${color}"
}

//	===== XOR Encode and Decode Device Data =====
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
	def key = 0x2B
	def nextKey
	byte[] XORtemp
	
	for(int i = 0; i < strBytes.length; i++) {
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative
		XORtemp = nextKey ^ key
		key = nextKey
		cmdResponse += new String(XORtemp)
	}
	//	For some reason, first character not decoding properly.
	cmdResponse = "{" + cmdResponse.drop(1)
	logTrace("inputXOR: cmdResponse = ${cmdResponse}")
	return cmdResponse
}

//	===== Other Methods =====
def updateInstallData(ip, appVer, plugNo) {
	updateDataValue("applicationVersion", appVer)
	updateDataValue("deviceIP", ip)
	if(device_IP) {
		device.updateSetting("device_IP",[type:"text", value: "${ip}"])
	}
	updateDataValue("driverVersion", driverVer())
	log.info "${device.label}: Updated Installation Data."
}

def logTrace(msg){
	if(traceLog == true) { log.trace msg }
}

//	end-of-file