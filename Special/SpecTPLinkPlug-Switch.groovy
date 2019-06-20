/*
TP-Link Device Driver, Special Fast Polling Version 4.3

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
				d.	Added method updateInstallData called from app on initial update only.
6.19.19	4.3.01	Special fast polling version.  For test only.
*/
def driverVer() { return "4.3.01" }
metadata {
	definition (name: "Special TP-Link Plug-Switch",
    			namespace: "davegut",
                author: "Dave Gutheinz",
				importUrl: "https://github.com/DaveGut/Hubitat-TP-Link-Integration/blob/master/Device%20Drivers/TP-Link%20Plug-Switch%20(Hubitat).groovy"
			   ) {
		capability "Switch"
        capability "Actuator"
		capability "Refresh"
		command "syncKasaName"
	}
    preferences {
		def refreshRate = [:]
		refreshRate << ["1 min" : "Refresh every minute"]
		refreshRate << ["5 min" : "Refresh every 5 minutes"]
		refreshRate << ["10 min" : "Refresh every 10 minutes"]
		refreshRate << ["15 min" : "Refresh every 15 minutes"]
		refreshRate << ["30 min" : "Refresh every 30 minutes"]
		refreshRate << ["5 sec" : "Refresh every 5 seconds - NOT RECOMMENDED"]
		input ("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate)
    	input ("debugLog", "bool", title: "Display debug messages?", defaultValue: false)
	}
}

def installed() {
	logInfo "Installing ............"
	runIn(2, updated)
}

def updated() {
	logInfo "Updating ............."
	unschedule()
	state.fastPolling = false
	updateDataValue("driverVersion", driverVer())
	if (debugLog == true) { runIn(1800, stopDebugeLogging) }	
	else { stopDebugLogging() }
	switch(refresh_Rate) {
		case "5 sec" :
			runEvery10Minutes(refresh)
			state.fastPolling = true
			break
		case "1 min" :
			runEvery1Minute(refresh)
			break
		case "5 min" :
			runEvery5Minutes(refresh)
			break
		case "10 min" :
			runEvery10Minutes(refresh)
			break
		case "15 min" :
			runEvery15Minutes(refresh)
			break
		default:
			runEvery30Minutes(refresh)
	}
	if (getDataValue("deviceIP")) { runIn(5, refresh) }
}

def updateInstallData() {
	logInfo "Updating previous installation data"
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
	logDebug("on")
	sendCmd("""{"system" :{"set_relay_state" :{"state" : 1}}}""", "commandResponse")
}

def off() {
	logDebug("off")
	sendCmd("""{"system" :{"set_relay_state" :{"state" : 0}}}""", "commandResponse")
}

def refresh() {
	logDebug("refresh")
	sendCmd("""{"system" :{"get_sysinfo" :{}}}""", "refreshResponse")
}

def syncKasaName() {
	logDebug("syncKasaName.  Updating Kasa App Name to ${device.label}")
	sendCmd("""{"system":{"set_dev_alias":{"alias":"${device.label}"}}}""", "syncNameResponse")
}

private sendCmd(command, action) {
	logDebug("sendCmd: command = ${command} // device IP = ${getDataValue("deviceIP")}")
	if (!getDataValue("deviceIP")) {
		logWarn "No device IP in a manual installation. Update Preferences."
		return
	}
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command), 
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${getDataValue("deviceIP")}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 callback: action])
	sendHubCommand(myHubAction)
}

def commandResponse(response) {
	def encrResponse = parseLanMessage(response).payload
	def cmdResponse = parseJson(inputXOR(encrResponse)).system.set_relay_state
	logDebug("commandResponse: Error Code = ${cmdResponse}")
	if (cmdResponse.err_code != 0) {
		logWarn("commandResponse: Error code received in from device")
	}
	refresh()
}

def refreshResponse(response) {
	def encrResponse = parseLanMessage(response).payload
	def cmdResponse = parseJson(inputXOR(encrResponse)).system.get_sysinfo
	if (cmdResponse.err_code != 0) {
		logWarn("refreshResponse: Error code received from device")
		return
	}
	def pwrState = "off"
	if (cmdResponse.relay_state == 1) { pwrState = "on" }
	if (device.currentValue("switch") != pwrState) {
		sendEvent(name: "switch", value: "${pwrState}")
		logInfo "${device.label}: Power: ${pwrState}"
	}
	if (state.fastPolling == true) { runIn(5, refresh) }
}

def syncNameResponse(response) {
	def encrResponse = parseLanMessage(response).payload
	def cmdResponse = parseJson(inputXOR(encrResponse)).system.set_dev_alias
	logDebug("syncNameResponse: ${cmdResponse}")
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
	return cmdResponse
}

def logInfo(msg) { log.info "${device.label} ${driverVer()} ${msg}" }

def logDebug(msg){
	if(debugLog == true) { log.debug "${device.label} ${driverVer()} ${msg}" }
}

def logWarn(msg){ log.warn "${device.label} ${driverVer()} ${msg}" }

def stopDebugLogging() {
	device.updateSetting("debugLog", [type:"bool", value: false])
	logInfo "stopDebugLogging: Debug Logging is off."
}

//	end-of-file
