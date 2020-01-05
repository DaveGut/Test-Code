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
10.01	4.5.01	a.	Converted to single file for dimming switch, multi-plug, and switch-plug.
				b.	Two drivers created from the single file (with 2 edits at beginning)
					1.	TP-Link Dimming Switch
					2.	TP-Link Plug-Switch (incorporates old TP-Link MultiPlug driver).
10.05	4.5.02	Increased retries before polling.
10.10	4.5.10	Updated to create individual types for the devices to alleviate confusion and errors.
10.11	4.5.11	Updated Plug-Switch to handle delay in HS210 updating internal status.
12.04	4.5.12	Updated to not send event if state is same as that of device.  Info logging will still
				state of on/off for each poll.
12.18	4.5.13	Updated for separate fast poll parse method and stop debug logging after 30 minutes.
===== GitHub Repository =====
	https://github.com/DaveGut/Hubitat-TP-Link-Integration
=======================================================================================================*/
	def driverVer() { return "4.5.13" }
//	def type() { return "Plug-Switch" }
	def type() { return "Multi-Plug" }
//	def type() { return "Dimming Switch" }
	def gitHubName() { return type().replaceAll("\\s","") }

metadata {
	definition (name: "TP-Link ${type()}",
    			namespace: "davegut",
                author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/Hubitat-TP-Link-Integration/master/DeviceDrivers/TP-Link${gitHubName()}(Hubitat).groovy"
			   ) {
		capability "Switch"
        capability "Actuator"
		capability "Refresh"
		if (type() == "Dimming Switch") {
			capability "Switch Level"
		}
		attribute "commsError", "bool"
	}
	preferences {
		if (!getDataValue("applicationVersion")) {
			input ("device_IP", "text", title: "Device IP (Current = ${getDataValue("deviceIP")})")
			if (type() == "Plug-Switch") {
				input ("multiPlug", "bool", title: "Device is part of a Multi-Plug)")
				input ("plug_No", "text",
					   title: "For multiPlug, the number of the plug (00, 01, 02, etc.)")
			}
		}
		input ("refresh_Rate", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"], defaultValue: "30")
		input ("shortPoll", "number",title: "Fast Polling Interval ('0' = disabled)",
			   defaultValue: 0)
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
	state.errorCount = 0

	if (!getDataValue("applicationVersion")) {
		if (!device_IP) {
			logWarn("updated: Device IP must be set to continue.")
			return
		} else if (type() == "Plug-Switch" && multiPlug == true && !plug_No) {
			logWarn("updated: Plug Number must be set to continue.")
			return
		}
		updateDataValue("deviceIP", device_IP.trim())
		logInfo("Device IP set to ${getDataValue("deviceIP")}")
		if (multiPlug == true && (!getDataValue("plugNo") || getDataValue("plugNo")==null)) {
			sendCmd("""{"system" :{"get_sysinfo" :{}}}""", "getMultiPlugData")
		}
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
	if (shortPoll == null) { device.updateSetting("shortPoll",[type:"number", value:0]) }
	if (debug == true) { runIn(1800, debugLogOff) }

	logInfo("Debug logging is: ${debug}.")
	logInfo("Description text logging is ${descriptionText}.")
	logInfo("Refresh set for every ${refresh_Rate} minute(s).")
	logInfo("ShortPoll set for ${shortPoll}")

	if (nameSync == "device" || nameSync == "hub") { runIn(5, syncName) }
	runIn(5, refresh)
}

def debugLogOff() {
	device.updateSetting("debug", [type:"bool", value: false])
	pauseExecution(5000)
	logInfo("Debug logging is false.")
}

def getMultiPlugData(response) {
	logDebug("getMultiPlugData: plugNo = ${plug_No}")
	def cmdResponse = parseInput(response)
	def plugId = "${cmdResponse.system.get_sysinfo.deviceId}${plug_No}"
	updateDataValue("plugNo", plug_No)
	updateDataValue("plugId", plugId)
	logInfo("Plug ID = ${plugId} / Plug Number = ${plug_No}")
}

def updateInstallData() {
	logInfo("updateInstallData: Updating installation to driverVersion ${driverVer()}")
	updateDataValue("driverVersion", driverVer())
	state.remove("multiPlugInstalled")
	pauseExecution(1000)
	state.remove("currentError")
	pauseExecution(1000)
	state.remove("commsErrorCount")
	pauseExecution(1000)
	state.remove("updated")
}


//	Device Commands
def on() {
	logDebug("on")
	if(getDataValue("plugId")) {
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" +
				""""system":{"set_relay_state":{"state":1}},""" +
				""""system":{"get_sysinfo":{}}}""", "commandResponse")
	} else if (device.name == "HS210") {
		sendCmd("""{"system":{"set_relay_state":{"state":1}}}""", "specialResponse")
	} else {
		sendCmd("""{"system":{"set_relay_state":{"state":1}},""" +
				""""system":{"get_sysinfo":{}}}""", "commandResponse")
	}
}

def off() {
	logDebug("off")
	if(getDataValue("plugId")) {
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" +
				""""system":{"set_relay_state":{"state":0}},""" +
				""""system":{"get_sysinfo":{}}}""", "commandResponse")
	} else if (device.name == "HS210") {
		sendCmd("""{"system":{"set_relay_state":{"state":0}}}""", "specialResponse")
	} else {
		sendCmd("""{"system":{"set_relay_state":{"state":0}},""" +
				""""system":{"get_sysinfo":{}}}""", "commandResponse")
	}
}

def specialResponse(response) {
	//	Created to handle delay in HS210 completing internal status update.
	pauseExecution(1000)
	sendCmd("""{"system":{"get_sysinfo":{}}}""", "commandResponse")
}

def setLevel(percentage, transition = null) {
	//	Accessible only if the type = dimmer, as defined at beginning of driver
	logDebug("setLevel: level = ${percentage}")
	if (percentage < 0 || percentage > 100) {
		logWarn("$device.name $device.label: Entered brightness is not from 0...100")
		return
	}
	percentage = percentage.toInteger()
	sendCmd("""{"system":{"set_relay_state":{"state":1}},""" +
			""""smartlife.iot.dimmer":{"set_brightness":{"brightness":${percentage}}},""" +
			""""system":{"get_sysinfo":{}}}""", "commandResponse")
}

def refresh() {
	logDebug("refresh")
	sendCmd("""{"system":{"get_sysinfo":{}}}""", "commandResponse")
}


def runShortPoll() {
	logDebug("runShortPoll")
	sendCmd("""{"system":{"get_sysinfo":{}}}""", "shortPollResponse")
}

//	Device command parsing methods
def commandResponse(response) {
	//	Will update events to system which will flow if a change.
	//	Single log line per run.
	def cmdResponse = parseInput(response)
	logDebug("commandResponse: status = ${cmdResponse}")
	def status = cmdResponse.system.get_sysinfo
	def relayState = status.relay_state
	if (getDataValue("plugNo")) {
		status = status.children.find { it.id == getDataValue("plugNo") }
		relayState = status.state
	}
	def onOff = "off"
	if (relayState == 1) { onOff = "on"}
	sendEvent(name: "switch", value: onOff)
	def deviceStatus = [:]
	deviceStatus << ["power" : onOff]
	if (type() == "Dimming Switch") {
		sendEvent(name: "level", value: status.brightness)
		deviceStatus << ["level" : status.brightness]
	}
	logInfo("Status: ${deviceStatus}")
	if (shortPoll.toInteger() > 0) { runIn(shortPoll.toInteger(), runShortPoll) }
}


//	Device command parsing methods
def shortPollResponse(response) {
	//	Unless debug logging, short poll does not log.  It will send events for
	//	changes in onOff and Level.
	def cmdResponse = parseInput(response)
	logDebug("shortPollResponse: status = ${cmdResponse}")
	def status = cmdResponse.system.get_sysinfo
	def relayState = status.relay_state
	if (getDataValue("plugNo")) {
		status = status.children.find { it.id == getDataValue("plugNo") }
		relayState = status.state
	}
	def onOff = "off"
	if (relayState == 1) { onOff = "on"}
	if (device.currentValue("switch") != onOff) {
		sendEvent(name: "switch", value: onOff)
	}
	if (type() == "Dimming Switch") {
		if (device.currentValue("level") != status.brightness) {
			sendEvent(name: "level", value: status.brightness)
		}
	}
	if (shortPoll.toInteger() > 0) { runIn(shortPoll.toInteger(), runShortPoll) }
}

//	Synchronize Names between Device and Hubitat
def syncName() {
	logDebug("syncName. Synchronizing device name and label with master = ${nameSync}")
	if (nameSync == "hub") {
		if(getDataValue("plugId")) {
			sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" +
					""""system":{"set_dev_alias":{"alias":"${device.label}"}}}""",
					"nameSyncHub")
		} else {
			sendCmd("""{"system":{"set_dev_alias":{"alias":"${device.label}"}}}""", "nameSyncHub")
		}
	} else if (nameSync == "device") {
		sendCmd("""{"system":{"get_sysinfo":{}}}""", "nameSyncDevice")
	}
}
def nameSyncHub(response) {
	def cmdResponse = parseInput(response)
	logInfo("Kasa name for device changed.")
}
def nameSyncDevice(response) {
	def cmdResponse = parseInput(response)
	def status = cmdResponse.system.get_sysinfo
	if(getDataValue("plugId")) {
		status = status.children.find { it.id == getDataValue("plugNo") }
	}
	device.setLabel(status.alias)
	logInfo("Hubit name for device changed to ${status.alias}.")
}


//	Communications and initial common parsing
private sendCmd(command, action) {
	logDebug("sendCmd: command = ${command} // device IP = ${getDataValue("deviceIP")}, action = ${action}")
	state.lastCommand = [command: "${command}", action: "${action}"]
	runIn(5, setCommsError)
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command),
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${getDataValue("deviceIP")}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 timeout: 5,
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
	if (state.errorCount < 5) {
		state.errorCount+= 1
		repeatCommand()
		logWarn("Attempt ${state.errorCount} to recover communications")
	} else if (state.errorCount == 5) {
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