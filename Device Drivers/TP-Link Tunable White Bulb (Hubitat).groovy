/*
TP-Link Device Driver, Version 4.1

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

*/
def driverVer() { return "4.1.01" }
metadata {
	definition (name: "TP-Link Tunable White Bulb", 
    			namespace: "davegut", 
                author: "Dave Gutheinz") {
        capability "Light"
		capability "Switch"
		capability "Switch Level"
 		capability "Refresh"
		capability "Actuator"
		capability "Color Temperature"
		command "setCircadian"
		attribute "circadianState", "string"
		attribute "commsError", "string"
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
		input ("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate)
        input ("transition_Time", "num", title: "Default Transition time (seconds)")
    	input name: "infoLog", type: "bool", title: "Display information messages?", required: false
    	input name: "traceLog", type: "bool", title: "Display trace messages?", required: false
	}
}

def installed() {
	log.info "Installing ${device.label}..."
	sendEvent(name: "commsError", value: "none")
	device.updateSetting("refresh_Rate", [type: "enum", value: "30"])
	device.updateSetting("transition_Time", [type: "num", value: 0])
	device.updateSetting("infoLog", [type:"bool", value: true])
	device.updateSetting("traceLog", [type:"bool", value: true])
	runIn(1800, stopTraceLogging)
	runIn(2, updated)
}

def updated() {
	log.info "Updating ${device.label}..."
	if (state.currentError) { state.currentError = null }
	if(device_IP) {
		updateDataValue("deviceIP", device_IP)
	}

	unschedule()
	if (traceLog == true) {
		device.updateSetting("traceLog", [type:"bool", value: true])
		runIn(1800, stopTraceLogging)
	} else { stopTraceLogging() }
	if (infoLog == true) {
		device.updateSetting("infoLog", [type:"bool", value: true])
	} else {
		device.updateSetting("infoLog", [type:"bool", value: false])
	}
	state.commsErrorCount = 0
	sendEvent(name: "commsError", value: "none")
	updateDataValue("driverVersion", driverVer())
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
		case "15" :
			runEvery15Minutes(refresh)
			break
		default:
			runEvery30Minutes(refresh)
	}
	if (getDataValue("deviceIP")) { refresh() }
}

void uninstalled() {
	try {
		def alias = device.label
		log.info "Removing device ${alias} with DNI = ${device.deviceNetworkId}"
		parent.removeChildDevice(alias, device.deviceNetworkId)
	} catch (ex) {
		log.info "${device.name} ${device.label}: Either the device was manually installed or there was an error."
	}
}

def stopTraceLogging() {
	log.trace "stopTraceLogging: Trace Logging is off."
	device.updateSetting("traceLog", [type:"bool", value: false])
}

def on() {
	logTrace("On: transition_Time = ${transition_Time}")
	def transTime = 1000*transition_Time.toInteger()
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"on_off":1,"transition_period":${transTime}}}}""")
}

def off() {
	logTrace("off: transition_Time = ${transition_Time}")
	def transTime = 1000*transition_Time.toInteger()
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"on_off":0,"transition_period":${transTime}}}}""")
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
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"ignore_default":1,"on_off":1,"brightness":${percentage},"transition_period":${rate}}}}""")
}

def setColorTemperature(kelvin) {
	logTrace("setColorTemperature: colorTemp = ${kelvin}")
	if (kelvin == null) kelvin = state.lastColorTemp
	if (kelvin < 2700) kelvin = 2700
	if (kelvin > 6500) kelvin = 6500
	kelvin = kelvin as int
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"ignore_default":1,"on_off":1,"color_temp": ${kelvin},"hue":0,"saturation":0}}}""")
}

def setCircadian() {
	logTrace("setCircadian")
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"mode":"circadian"}}}""")
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

def parse(response) {
	unschedule(createCommsError)
	sendEvent(name: "commsError", value: "none")
	state.commsErrorCount = 0
	def encrResponse = parseLanMessage(response).payload
	def cmdResponse
	try {
		cmdResponse = parseJson(inputXOR(encrResponse))
		logTrace("parseInput: response = ${cmdResponse}")
	} catch (error) {
		log.error "${device.label} parseInput fragmented return from device.  In Kasa App reduce device name to less that 18 characters!"
		sendEvent(name: "commsError", value: "parseInput failed.  See logs.")
	}
	def status
	if (cmdResponse.system) {
		logTrace("parse: Refresh Response")
		status = cmdResponse.system.get_sysinfo.light_state
	} else {
		logTrace("parse: Command Response")
		status = cmdResponse["smartlife.iot.smartbulb.lightingservice"].transition_light_state
	}
	logTrace("parseBulbState: status = ${status}")
	if (status.on_off == 0) {
		sendEvent(name: "switch", value: "off")
		logInfo "${device.label}: Power: off"
		sendEvent(name: "circadianState", value: "normal")
	} else {
		sendEvent(name: "switch", value: "on")
		sendEvent(name: "level", value: status.brightness)
		sendEvent(name: "circadianState", value: status.mode)
		sendEvent(name: "colorTemperature", value: status.color_temp)
		logInfo "${device.label}: Power: on / Brightness: ${status.brightness}% / " +
				 "Circadian State: ${status.mode} / Color Temp: ${status.color_temp}K"
		setColorTempData(status.color_temp)
	}
}

//	Code common to all Hubitat TP-Link drivers
private sendCmd(command) {
	logTrace("sendCmd: command = ${command} // device IP = ${getDataValue("deviceIP")}")
	if (!getDataValue("deviceIP")) {
		sendEvent(name: "commsError", value: "No device IP. Update Preferences.")
		log.error "No device IP. Update Preferences."
		return
	}
	runIn(2, createCommsError)	//	Starts 3 second timer for error.
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command), 
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${getDataValue("deviceIP")}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING])
	sendHubCommand(myHubAction)
}

def createCommsError() {
	sendEvent(name: "commsError", value: "Comms Error. Device offline. See logs")
	log.error "${device.label}: Comms Error. Device offline. Check the device and run the TP-Lnk application to update IP."
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
	if (infoLog == true) { log.info msg }
}

def logTrace(msg){
	if(traceLog == true) { log.trace msg }
}

//	end-of-file