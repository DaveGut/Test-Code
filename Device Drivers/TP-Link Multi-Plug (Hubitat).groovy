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
	definition (name: "TP-Link Multi-Plug",
    			namespace: "davegut",
                author: "Dave Gutheinz") {
		capability "Switch"
        capability "Actuator"
		capability "Refresh"
		attribute "commsError", "string"
	}
    preferences {
		if (!getDataValue("applicationVersion")) {
			input ("plug_No", "text", title: "Number of the plug (00, 01, 02, etc.)")
			input ("device_IP", "text", title: "Device IP (Current = ${getDataValue("deviceIP")})")
		}
		def refreshRate = [:]
		refreshRate << ["1" : "Refresh every minute"]
		refreshRate << ["5" : "Refresh every 5 minutes"]
		refreshRate << ["10" : "Refresh every 10 minutes"]
		refreshRate << ["15" : "Refresh every 15 minutes"]
		refreshRate << ["30" : "Refresh every 30 minutes"]
		input ("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate)
    	input name: "infoLog", type: "bool", title: "Display information messages?", required: false
    	input name: "traceLog", type: "bool", title: "Display trace messages?", required: false
	}
}

def installed() {
	log.info "Installing ${device.label}..."
	state.multiPLugInstalled == false
	sendEvent(name: "commsError", value: "none")
	device.updateSetting("refresh_Rate",[type:"enum", value:"30"])
	device.updateSetting("infoLog", [type:"bool", value: true])
	device.updateSetting("traceLog", [type:"bool", value: true])
	runIn(1800, stopTraceLogging)
	runIn(2, updated)
}

def updated() {
	log.info "Updating ${device.label}..."
	if (state.currentError) { state.currentError = null }
	if (plug_No && state.multiPLugInstalled == false) {
		sendCmd('{"system" :{"get_sysinfo" :{}}}')
	}
	if(device_IP) { updateDataValue("deviceIP", device_IP) }

	unschedule()
	if (traceLog == true) {
		device.updateSetting("traceLog", [type:"bool", value: true])
		runIn(1800, stopTraceLogging)
	} else {
		stopTraceLogging()
	}
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
	logTrace("on")
	def plugId = getDataValue("plugId")
	sendCmd("""{"context":{"child_ids":["${plugId}"]},"system":{"set_relay_state":{"state": 1}}}""")
}

def off() {
	logTrace("off")
	def plugId = getDataValue("plugId")
	sendCmd("""{"context":{"child_ids":["${plugId}"]},"system":{"set_relay_state":{"state": 0}}}""")
}

def refresh(){
	sendCmd('{"system" :{"get_sysinfo" :{}}}')
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
	if (cmdResponse.system.get_sysinfo) {
		if (state.multiPlugInstalled != false) {
			logTrace("parse: Refresh Response")
			def children = cmdResponse.system.get_sysinfo.children
			def plugId = getDataValue("plugNo")
			plug = children.find { it.id == plugId }
			if (plug.state == 1) {
				sendEvent(name: "switch", value: "on")
				logInfo "${device.label}: Power: on"
			} else {
				sendEvent(name: "switch", value: "off")
				logInfo "${device.label}: Power: off"
			}
		} else {
			logTrace("parse: parse plud ID")
			def plugId = "${cmdResponse.system.get_sysinfo.deviceId}${plug_No}"
			updateDataValue("plugId", plugId)
			state.multiPlugInstalled = true
			logInfo("${device.label}: Plug ID set to ${plugId}")
		}
	} else {
		logTrace("parse: Command Response")
		refresh()
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