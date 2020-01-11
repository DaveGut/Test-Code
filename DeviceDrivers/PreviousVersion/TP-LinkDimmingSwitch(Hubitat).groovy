/*
TP-Link Switch and Plug Device Driver, Version 4.6
	Copyright Dave Gutheinz
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

DISCLAIMER:  This Applicaion and the associated Device Drivers are in no way sanctioned or supported by TP-Link.  
All  development is based upon open-source data on the TP-Link devices; primarily various users on GitHub.com.

===== 2020 History =====
01.03	4.6.01	Update from 4.5 to incorporate enhanced communications error processing.
===== GitHub Repository =====
	https://github.com/DaveGut/Hubitat-TP-Link-Integration
=======================================================================================================*/
	def driverVer() { return "4.6.01" }
//	def type() { return "Plug-Switch" }
//	def type() { return "Multi-Plug" }
	def type() { return "Dimming Switch" }
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
		input ("shortPoll", "number",title: "Fast Polling Interval - <b>Caution</b> ('0' = disabled and preferred)",
			   defaultValue: 0, descriptionText: "TTTTTTT")
		input ("nameSync", "enum", title: "Synchronize Names", defaultValue: "none",
			   options: ["none": "Don't synchronize",
						 "device" : "Kasa device name master", 
						 "hub" : "Hubitat label master"])
		input ("debug", "bool", title: "Enable debug logging", defaultValue: false)
		input ("descriptionText", "bool", title: "Enable description text logging", defaultValue: true)
	}
}

//	Installation and update
def installed() {
	log.info "Installing .."
	runIn(2, updated)
}

def updated() {
	log.info "Updating .."
	unschedule()
	state.errorCount = 0
	sendEvent(name: "commsError", value: false)

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

//	User Commands and response parsing
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
	pauseExecution(1000)
	sendCmd("""{"system":{"get_sysinfo":{}}}""", "commandResponse")
}

def setLevel(percentage, transition = null) {
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
	if (state.errorCount < 1) {
		sendCmd("""{"system":{"get_sysinfo":{}}}""", "commandResponse")
	}
}

def commandResponse(response) {
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
	if (shortPoll > 0) { runIn(shortPoll, runShortPoll) }
}

//	Name Sync with the Kasa Device Name
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

//	Common Communications Methods
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
		 timeout: 2,
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

//	Communications Error Handling
def setCommsError() {
	logDebug("setCommsError")
	state.errorCount += 1
	if (state.errorCount > 6) {
		return
	} else if (state.errorCount < 5) {
		repeatCommand()
		logWarn("Executing attempt ${state.errorCount} to recover communications")
	} else if (state.errorCount == 5) {
		sendEvent(name: "commsError", value: true)
		if (getDataValue("applicationVersion")) {
			logWarn("setCommsError: Parent commanded to poll for devices to correct error.")
			parent.updateDeviceIps()
			runIn(40, repeatCommand)
		} else {
			repeatCommand()
		}
	} else if (state.errorCount == 6) {		
		logWarn "<b>setCommsError</b>: Your device is not reachable at IP ${getDataValue("deviceIP")}.\r" +
				"<b>Corrective Action</b>: \r"+
				"Your action is required to re-enable the device.\r" +
				"a.  If the device was removed, disable the device in Hubitat.\r" +
				"b.  Check the device in the Kasa App and assure it works.\r" +
				"c.  If a manual installation, update your IP and Refresh Rate in the device preferences.\r" +
				"d.  For TP-Link Integration installation:\r" +
				"\t1.  Assure the device is working in the Kasa App.\r" +
				"\t2.  Run the TP-Link Integration app (this will update the IP address).\r" +
				"\t3.  Execute any command except Refresh.  This should reconnect the device.\r"
				"\t4.  A Save Preferences will reset the error data and force a restart the interface."
	}
}

def repeatCommand() { 
	logDebug("repeatCommand: ${state.lastCommand}")
	sendCmd(state.lastCommand.command, state.lastCommand.action)
}

//	Short Polling Methods
def runShortPoll() {
	sendShortPollCmd("""{"system":{"get_sysinfo":{}}}""", "shortPollResponse")
}

private sendShortPollCmd(command, action) {
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

def shortPollResponse(response) {
	logDebug("shortPollResponse")
	def encrResponse = parseLanMessage(response).payload
	def cmdResponse = parseJson(inputXOR(encrResponse))
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
	if (shortPoll > 0) { runIn(shortPoll, runShortPoll) }
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
	if (descriptionText == true) { log.info "${device.label} ${driverVer()} ${msg}" }
}

def logDebug(msg){
	if(debug == true) { log.debug "${device.label} ${driverVer()} ${msg}" }
}

def logWarn(msg){ log.warn "${device.label} ${driverVer()} ${msg}" }

//	end-of-file