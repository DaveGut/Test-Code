/*
TP-Link Device Driver, Version 4.3

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
7.22.19	4.3.02	Modified on/off methods to include get_sysinfo, reducing messages by 1.
8.25.19	4.3.02	Added comms re-transmit on FIRST time a communications doesn't succeed.  Device will
				attempt up to 5 retransmits.
================================================================================================*/
def driverVer() { return "4.3.02" }
metadata {
	definition (name: "TP-Link Dimming Switch",
    			namespace: "davegut",
                author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/Hubitat-TP-Link-Integration/master/DeviceDrivers/TP-LinkDimmingSwitch(Hubitat).groovy"
			   ) {
		capability "Switch"
        capability "Actuator"
		capability "Refresh"
		capability "Switch Level"
		attribute "commsError", "bool"
	}
    preferences {
		def refreshRate = [:]
		refreshRate << ["1 min" : "Refresh every minute"]
		refreshRate << ["5 min" : "Refresh every 5 minutes"]
		refreshRate << ["15 min" : "Refresh every 15 minutes"]
		refreshRate << ["30 min" : "Refresh every 30 minutes"]
		def nameMaster  = [:]
		nameMaster << ["none": "Don't synchronize"]
		nameMaster << ["device" : "Kasa (device) alias master"]
		nameMaster << ["hub" : "Hubitat label master"]
		if (!getDataValue("applicationVersion")) {
			input ("device_IP", "text", title: "Device IP (Current = ${getDataValue("deviceIP")})", defaultValue: "")
		}
		input ("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate, defaultValue: "30 min")
		input ("debug", "bool", title: "Enable debug logging", defaultValue: false)
		input ("descriptionText", "bool", title: "Enable description text logging", defaultValue: true)
		input ("nameSync", "enum", title: "Synchronize Names", options: nameMaster, defaultValue: "none")
	}
}

def installed() {
	log.info "Installing .."
	runIn(2, updated)
}
def updated() {
	log.info "Updating .."
	unschedule()
	logInfo("Debug logging is: ${debug}.")
	logInfo("Description text logging is ${descriptionText}.")
	switch(refresh_Rate) {
		case "1 min" :
			runEvery1Minute(refresh)
			break
		case "5 min" :
			runEvery5Minutes(refresh)
			break
		case "15 min" :
			runEvery15Minutes(refresh)
			break
		default:
			runEvery30Minutes(refresh)
	}
	logInfo("Refresh set for every ${refresh_Rate} minute(s).")
	if (!getDataValue("applicationVersion")) {
		logInfo("Setting deviceIP for program.")
		updateDataValue("deviceIP", device_IP)
	}
	if (getDataValue("driverVersion") != driverVer()) { updateInstallData() }
	if (getDataValue("deviceIP")) {
		if (nameSync != "none") { syncName() }
		runIn(2, refresh)
	}
}
//	Update methods called in updated
def updateInstallData() {
	logInfo("updateInstallData: Updating installation to driverVersion ${driverVer()}")
	updateDataValue("driverVersion", driverVer())
	if (getDataValue("plugId")) { updateDataValue("plugId", null) }
	if (getDataValue("plugNo")) { updateDataValue("plugNo", null) }
	state.remove("multiPlugInstalled")
	pauseExecution(1000)
	state.remove("currentError")
	pauseExecution(1000)
	state.remove("commsErrorCount")
	pauseExecution(1000)
	state.remove("updated")
}
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
	logInfo("Hubit name for device changed to ${status.alias}.")
}


//	Device Commands
def on() {
	logDebug("on")
	sendCmd("""{"system" :{"set_relay_state" :{"state" : 1}},"system" :{"get_sysinfo" :{}}}""", "commandResponse")
}
def off() {
	logDebug("off")
	sendCmd("""{"system" :{"set_relay_state" :{"state" : 0}},"system" :{"get_sysinfo" :{}}}""", "commandResponse")
}
def setLevel(percentage) {
	logTrace("setLevel: level = ${percentage}")
	sendCmd('{"system":{"set_relay_state":{"state": 1}}}')
	if (percentage < 0 || percentage > 100) {
		logWarn("$device.name $device.label: Entered brightness is not from 0...100")
		return
	}
	percentage = percentage.toInteger()
	sendCmd("""{"smartlife.iot.dimmer" :{"set_brightness" :{"brightness" :${percentage}}},"system" :{"get_sysinfo" :{}}}""", "commandResponse")
}
def refresh() {
	logDebug("refresh")
	sendCmd("""{"system" :{"get_sysinfo" :{}}}""", "commandResponse")
}
//	Device command parsing methods
def commandResponse(response) {
	def cmdResponse = parseInput(response)
	logDebug("commandResponse: status = ${cmdResponse}")
	def status = cmdResponse.system.get_sysinfo
	def pwrState = "off"
	if (status.relay_state == 1) { pwrState = "on"}
	if (device.currentValue("switch") != pwrState || device.currentValue("level") != status.brightness) {
		sendEvent(name: "switch", value: "${pwrState}")
		sendEvent(name: "level", value: status.brightness)
		logInfo("Power: ${pwrState}")
	}
}


//	Communications and initial common parsing
private sendCmd(command, action) {
	logDebug("sendCmd: command = ${command} // device IP = ${getDataValue("deviceIP")}, action = ${action}")
	state.lastCommand = command
	state.lastAction = action
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
	if (state.errorCount < 5) {
		state.errorCount+= 1
		sendCmd(state.lastCommand, state.lastAction)
		logWarn("Attempt ${state.errorCount} to recover communications")
	} else {
		sendEvent(name: "commsError", value: true)
		logWarn "CommsError: No response from device.  Refresh.  If off line " +
				"persists, check IP address of device."
	}
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