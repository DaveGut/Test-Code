/*
TP-Link Device Driver, Version 4.4

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
				d.	Initial release of Engr Mon Multi-Plug driver
8.21.19	4.4.01	a.	Added retry logic on command failure
================================================================================================*/
def driverVer() { return "4.4.01" }
metadata {
	definition (name: "TP-Link Tunable White Bulb", 
    			namespace: "davegut", 
				author: "Dave Gutheinz",
				importUrl: "https://github.com/DaveGut/Hubitat-TP-Link-Integration/blob/master/DeviceDrivers/TP-LinkCTBulb(Hubitat).groovy"
			   ) {
        capability "Light"
		capability "Switch"
		capability "Switch Level"
		capability "Change Level"
 		capability "Refresh"
		capability "Actuator"
		capability "Color Temperature"
		command "setCircadian"
		attribute "circadianState", "string"
		attribute "colorMode", "string"
	}
	preferences {
		def refreshRate = [:]
		refreshRate << ["1" : "Refresh every minute"]
		refreshRate << ["5" : "Refresh every 5 minutes"]
		refreshRate << ["15" : "Refresh every 15 minutes"]
		refreshRate << ["30" : "Refresh every 30 minutes"]
		def nameMaster  = [:]
		nameMaster << ["none": "Don't synchronize"]
		nameMaster << ["device" : "Kasa (device) alias master"]
		nameMaster << ["hub" : "Hubitat label master"]
		if (!getDataValue("applicationVersion")) {
			input ("device_IP", "text", title: "Device IP (Current = ${getDataValue("deviceIP")})")
		}
		input ("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate, defaultValue: 30)
		input ("transition_Time", "num", title: "Default Transition time (seconds)", defaultValue: 0)
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
	setRefreshInterval(refresh_Rate)
	state.transTime = 1000*transition_Time.toInteger()
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
def setRefreshInterval(interval) {
	logDebug("setRefreshInterval: interval = ${interval}")
	unschedule(refresh)
    interval = (interval ?: 30).toString()
	switch(interval) {
		case "1" :
			runEvery1Minute(refresh)
			break
		case "5" :
			runEvery5Minutes(refresh)
			break
		case "15" :
			runEvery15Minutes(refresh)
			break
		default :
			runEvery30Minutes(refresh)
			break
	}
	logInfo("Refresh set for every ${interval} minute(s).")
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
def syncName() {
	logDebug("syncName. Synchronizing device name and label with master = ${nameSync}")
	if (nameSync == "hub") {
		sendCmd("""{"smartlife.iot.common.system":{"set_dev_alias":{"alias":"${device.label}"}}}""", "nameSyncHub")
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
	logDebug("On: transition time = ${state.transTime}")
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"on_off":1,"transition_period":${state.transTime}}}}""", "commandResponse")
}
def off() {
	logDebug("Off: transition time = ${state.transTime}")
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"on_off":0,"transition_period":${state.transTime}}}}""", "commandResponse")
}
def setLevel(percentage) {
	logDebug("setLevel(x): transition time = ${state.transTime}")
	setLevel(percentage, state.transTime)
}
def setLevel(percentage, rate) {
	logDebug("setLevel(x,x): rate = ${rate} // percentage = ${percentage}")
	if (percentage < 0 || percentage > 100) {
		logWarn("$device.name $device.label: Entered brightness is not from 0...100")
		return
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
//	Color Temp Bulb Commands
def setColorTemperature(kelvin) {
	logDebug("setColorTemperature: colorTemp = ${kelvin}")
	if (kelvin == null) kelvin = state.lastColorTemp
	if (kelvin < 2700) kelvin = 2700
	if (kelvin > 6500) kelvin = 6500
	kelvin = kelvin as int
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"ignore_default":1,"on_off":1,"color_temp": ${kelvin},"hue":0,"saturation":0}}}""", "commandResponse")
}
def setCircadian() {
	logDebug("setCircadian")
	sendCmd("""{"smartlife.iot.smartbulb.lightingservice":{"transition_light_state":{"mode":"circadian"}}}""", "commandResponse")
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
	if (state.previousStatus == status) { return }
	state.previousStatus = status
	logDebug("parseBulbState: status = ${status}")
	if (status.on_off == 0) {
		sendEvent(name: "switch", value: "off")
		logInfo("Power: off")
		sendEvent(name: "circadianState", value: "normal")
	} else {
		sendEvent(name: "switch", value: "on")
		sendEvent(name: "level", value: status.brightness, unit: "%")
		sendEvent(name: "circadianState", value: status.mode)
		sendEvent(name: "colorTemperature", value: status.color_temp, unit: "K")
		logInfo("Power: on / Brightness: ${status.brightness}% / " +
				 "Circadian State: ${status.mode} / Color Temp: " +
				 "${status.color_temp}K")
		setColorTempData(status.color_temp)
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

//	Communications and initial common parsing
private sendCmd(command, action) {
	logDebug("sendCmd: command = ${command} // device IP = ${getDataValue("deviceIP")}, action = ${action}")
	retrySendCmd([retryCount: 0, command: outputXOR(command), action: action])
}

def retrySendCmd(data) {
	if (data.retryCount >= 5) {
        logWarn("Message retry count exceeded")
		setCommsError()
		return
	}
	
	if (data.retryCount > 0) {
		logWarn("sendCmd failed, starting retry #${data.retryCount}")
	}

	data.retryCount++
	runIn(2, retrySendCmd, [data: data])
	
	def myHubAction = new hubitat.device.HubAction(
		data.command,
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${getDataValue("deviceIP")}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 timeout: 1,
		 callback: data.action])
	sendHubCommand(myHubAction)
}
def parseInput(response) {
	unschedule(retrySendCmd)
	try {
		def encrResponse = parseLanMessage(response).payload
		def cmdResponse = parseJson(inputXOR(encrResponse))
		logDebug("parseInput: cmdResponse = ${cmdResponse}")
		return cmdResponse
	} catch (error) {
		logWarn "CommsError: Fragmented message returned from device: ${error}"
	}
}
def setCommsError() {
	sendEvent(name: "switch", value: "OFFLINE",descriptionText: "No response from device.")
	state.previousStatus = "OFFLINE"
	logWarn "CommsError: No response from device.  Device set to offline.  Refresh.  If off line " +
			"persists, check IP address of device."
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