/*
TP-Link Plug and Switch Device Handler, Version 4.0

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
01.05.19	Skipped version 4.0.02 to sync version with bulbs.
01.05.19	4.0.03. Added 30 minute refresh option.  Added
			Preference for logTrace (default is false).  Added
			error handling sequence in device return.
01.13.19	4.0.04. Various enhancements:
			a.	Hide IP preference for non-manual installs.
			b.	Add 10 minute timeout for trace logging. Will
				also run for 10 minutes on set preferences an
				installation.

//	===== Device Type Identifier ===========================*/
	def driverVer() { return "4.0.04" }
//	def deviceType() { return "Plug-Switch" }
	def deviceType() { return "Dimming Switch" }	
//	def deviceType() { return "Multi-Plug" }
//	==========================================================

metadata {
	definition (name: "TP-Link ${deviceType()}",
    			namespace: "davegut",
                author: "Dave Gutheinz") {
		capability "Switch"
        capability "Actuator"
		capability "Refresh"
		if (deviceType() == "Dimming Switch") {
			capability "Switch Level"
		}
	}

    preferences {
		def refreshRate = [:]
		refreshRate << ["1" : "Refresh every minute"]
		refreshRate << ["5" : "Refresh every 5 minutes"]
		refreshRate << ["10" : "Refresh every 10 minutes"]
		refreshRate << ["15" : "Refresh every 15 minutes"]
		refreshRate << ["30" : "Refresh every 30 minutes"]
		input ("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate)

		if (!getDataValue("applicationVersion")) {
			if (deviceType() == "Multi-Plug") {
				input ("plug_No", "text", title: "Number of the plug (00, 01, 02, etc.)")
			}
			input ("device_IP", "text", title: "Device IP (Current = ${getDataValue("deviceIP")})")
		}
		
    	input name: "traceLog", type: "bool", title: "Display trace messages?", required: false
	}
}

//	===== Update when installed or setting changed =====
def installed() {
	log.info "Installing ${device.label}..."
	state.currentError = null
	state.updated = false
	state.multiPLugInstalled == false
	
	device.updateSetting("refresh_Rate",[type:"enum", value:"30"])
	device.updateSetting("traceLog", [type:"bool", value: true])
	runIn(600, stopTraceLogging)

	runIn(2, updated)
}

def updated() {
	log.info "Updating ${device.label}..."
	unschedule()
	
	if (traceLog) {
		device.updateSetting("traceLog", [type:"bool", value: true])
		runIn(600, stopTraceLogging)
	}
	
	updateDataValue("driverVersion", driverVer())
	if(device_IP) {
		updateDataValue("deviceIP", device_IP)
	}

	//	Get data on the plug number if Multi-plug.  Only done once.
	if (!state.multiPlugInstalled) { state.multiPlugInstalled = false }
	if (plug_No && deviceType() == "Multi-Plug" && state.multiPLugInstalled == false) {
		sendCmd('{"system" :{"get_sysinfo" :{}}}', "parsePlugId")
	}
	
	//	Capture legacy deviceIP on initial run of preferences.
	if (!state.updated) { state.updated = false }
	if (state.updated == false) {
		state.updated = true
		if(deviceIP) {
			updateDataValue("deviceIP", deviceIP)
		}
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
		case "15" :
			runEvery30Minutes(refresh)
			break
		default:
			runEvery30Minutes(refresh)
	}

	if (getDataValue("deviceIP")) { refresh() }
}

def stopTraceLogging() {
	logTrace("stopTraceLogging")
	device.updateSetting("traceLog", [type:"bool", value: true])
}

def parsePlugId(response) {
	def encrResponse = parseLanMessage(response).payload
	def cmdResponse
	try {
		cmdResponse = parseJson(inputXOR(encrResponse))
		logTrace("parsePlugId: plug_No = ${plug_No} / cmdResponse = ${cmdResponse}")
	} catch (error) {
		log.error "${device.label} parsePlugId fragmented return from device.  In Kasa App reduce device name to less that 18 characters!"
	}
	def deviceData = cmdResponse.system.get_sysinfo
	def plugId = "${deviceData.deviceId}${plug_No}"
	updateDataValue("plugId", plugId)
	state.multiPlugInstalled = true
	log.info "${device.label}: Plug ID set to ${plugId}"
}

//	===== Basic Plug Control/Status =====
def on() {
	logTrace("on")
	if (deviceType() != "Multi-Plug") {
		sendCmd("""{"system" :{"set_relay_state" :{"state" : 1}}}""", "commandResponse")
	} else {
		def plugId = getDataValue("plugId")
		sendCmd("""{"context":{"child_ids":["${plugId}"]},"system":{"set_relay_state":{"state": 1}}}""",
					"commandResponse")
	}
}

def off() {
	logTrace("off")
	if (deviceType() != "Multi-Plug") {
		sendCmd("""{"system" :{"set_relay_state" :{"state" : 0}}}""", "commandResponse")
	} else {
		def plugId = getDataValue("plugId")
		sendCmd("""{"context":{"child_ids":["${plugId}"]},"system":{"set_relay_state":{"state": 0}}}""",
					"commandResponse")
	}
}

def setLevel(percentage) {
	logTrace("setLevel: level = ${percentage}")
	sendCmd('{"system":{"set_relay_state":{"state": 1}}}', "commandResponse")
	if (percentage < 0 || percentage > 100) {
		log.error "$device.name $device.label: Entered brightness is not from 0...100"
		percentage = 50
	}
	percentage = percentage as int
	sendCmd("""{"smartlife.iot.dimmer" :{"set_brightness" :{"brightness" :${percentage}}}}""", "commandResponse")
}

def refresh(){
	sendCmd('{"system" :{"get_sysinfo" :{}}}', "refreshResponse")
}

def parseInput(response) {
	unschedule(createCommsError)
	state.currentError = null
	def encrResponse = parseLanMessage(response).payload
	try {
		def cmdResponse = parseJson(inputXOR(encrResponse))
		logTrace("parseInput: response = ${cmdResponse}")
		return cmdResponse
	} catch (error) {
		log.error "${device.label} parseInput fragmented return from device.  In Kasa App reduce device name to less that 18 characters!"
		state.currentError = "parseInput failed."
	}
}

def commandResponse(response) {
	logTrace("commandResponse: response = ${response}")
	unschedule(createCommsError)
	state.currentError = null
	refresh()
}

def refreshResponse(response){
	def cmdResponse = parseInput(response)
	def onOff
	if (deviceType() != "Multi-Plug") {
		def onOffState = cmdResponse.system.get_sysinfo.relay_state
		if (onOffState == 1) {
			onOff = "on"
		} else {
			onOff = "off"
		}
	} else {
		def children = cmdResponse.system.get_sysinfo.children
		def plugId = getDataValue("plugNo")
		children.each {
			if (it.id == plugId) {
				if (it.state == 1) {
					onOff = "on"
				} else {
					onOff = "off"
				}
			}
		}
	}
	
	sendEvent(name: "switch", value: onOff)
	if (deviceType() == "Dimming Switch") {
		def level = cmdResponse.system.get_sysinfo.brightness
	 	sendEvent(name: "level", value: level)
		log.info "${device.label}: Power: ${onOff} / Dimmer Level: ${level}%"
	} else {
		log.info "${device.label}: Power: ${onOff}"
	}
}

//	===== Send the Command =====
private sendCmd(command, action) {
	logTrace("sendCmd: command = ${command} // action = ${action} // device IP = ${getDataValue("deviceIP")}")
	if (!getDataValue("deviceIP")) {
		state.currentError = "No device IP. Update Preferences."
		log.error "No device IP. Update Preferences."
		return
	}
	runIn(3, createCommsError)	//	Starts 3 second timer for error.
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
def updateInstallData(ip, appVer) {
	updateDataValue("applicationVersion", appVer)
	updateDataValue("deviceIP", ip)
	log.info "${device.label}: Updated Installation Data."
}

def logTrace(msg){
	if(traceLog == true) { log.trace msg }
}

//	end-of-file