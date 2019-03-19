/*
TP-Link Device Driver, Version 4.2

	Copyright 2018, 2019 Dave Gutheinz

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

Discalimer:  This Applicaion and the associated Device Drivers are in no way sanctioned or supported by TP-Link.  
All  development is based upon open-source data on the TP-Link devices; primarily various users on GitHub.com.

===== History =====
2.04.19	4.1.01.	Final code for Hubitat without reference to deviceType and enhancement of logging functions. Added
				code to delete the device as a child from the application when deleted via the devices page.  Note:
				The device is also deleted whenever the application is deleted.
3.28.19	4.2.02	a.	Added capability Change Level implementation.
				b.	Removed info log preference.  Will always log these messages (one per response)
				c.	Added user command to synchronize the Kasa App name with the Hubitat device label
					(using Hubitat as the master name).
				d.	Added method updateInstallData called from app on initial update only.
3.28.19	4.2.02	Fixed Energy Stat and Month Stat responses to use emeter vices smartlife....
*/
def driverVer() { return "4.2.02" }
metadata {
	definition (name: "TP-Link Engr Mon Plug",
    			namespace: "davegut",
                author: "Dave Gutheinz") {
		capability "Switch"
        capability "Actuator"
		capability "Refresh"
		capability "Power Meter"
		capability "Energy Meter"
		command: "sendThisMonth"
		command: "sendThisYear"
		command: "sendLastYear"
		attribute "commsError", "string"
		command "syncKasaName"
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
			input ("device_IP", "text", title: "Device IP (Current = ${getDataValue("deviceIP")})")
		}
    	input name: "traceLog", type: "bool", title: "Display trace messages?", required: false
	}
}

def installed() {
	log.info "Installing ............"
	if(!state.todayStartEnergy) { state.todayStartEnergy = 0 }
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
	if (getDataValue("deviceIP")) { 
		setCurrentDate()
		schedule("0 05 0 * * ?", setCurrentDate)
		schedule("0 10 0 * * ?", getEnergyStats)
		runIn(2, refresh)
		runIn(5, getEnergyStats)
	}
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
	logTrace("on")
	sendCmd("""{"system" :{"set_relay_state" :{"state" : 1}}}""", "commandResponse")
}

def off() {
	logTrace("off")
	sendCmd("""{"system" :{"set_relay_state" :{"state" : 0}}}""", "commandResponse")
}

def refresh(){
	sendCmd('{"system" :{"get_sysinfo" :{}}}', "refreshResponse")
}

def syncKasaName() {
	logTrace("syncKasaName.  Updating Kasa App Name to ${device.label}")
	sendCmd("""{"system":{"set_dev_alias":{"alias":"${device.label}"}}}""", "syncNameResponse")
}
	
def parseInput(response) {
	unschedule(createCommsError)
	sendEvent(name: "commsError", value: "none")
	def encrResponse = parseLanMessage(response).payload
	try {
		def cmdResponse = parseJson(inputXOR(encrResponse))
		logTrace("parseInput: response = ${cmdResponse}")
		return cmdResponse
	} catch (error) {
		def errMsg = "Unable to process return from the device due to fragmented return from device.\n" +
					 "Change device Name in the Kasa Application to less than 18 characters."
		log.error "${device.label} ${driverVer()} CommsError: Device Name too long!\n${errMsg}"
		sendEvent(name: "commsError", value: "Device Name too long", description: "See log for corrective action.")
	}
}

def commandResponse(response) {
	logTrace("commandResponse: response = ${response}")
	unschedule(createCommsError)
	state.currentError = null
	refresh()
}

def syncNameResponse(response) {
	logTrace("syncNameResponse: response = ${response}")
	unschedule(createCommsError)
	state.currentError = null
	logInfo("Updated Kasa Name for device to ${device.label}.")
}

def refreshResponse(response){
	logTrace("refreshResponse: response = ${response}")
	def cmdResponse = parseInput(response)
	getPower()
	def onOffState = cmdResponse.system.get_sysinfo.relay_state
	if (onOffState == 1) {
		sendEvent(name: "switch", value: "on")
		logInfo "${device.label}: Power: on"
	} else {
		sendEvent(name: "switch", value: "off")
		logInfo "${device.label}: Power: off"
	}
}

def getPower(){
	logTrace("getPower")
	sendCmd("""{"emeter":{"get_realtime":{}}}""", "powerResponse")
}

def powerResponse(response) {
	def cmdResponse = parseInput(response)
	logTrace("powerResponse: cmdResponse = ${cmdResponse}")
	def realtime = cmdResponse["emeter"]["get_realtime"]
	if (realtime.power == null) {
		state.powerScale = "power_mw"
		state.energyScale = "energy_wh"
	} else {
		state.powerScale = "power"
		state.energyScale = "energy"
	}
	def power = realtime."${state.powerScale}"
	if (state.powerScale == "power_mw") { power = power / 1000 }
	sendEvent(name: "power", value: power)
	logInfo "${device.label}: Power is ${power} Watts."
	getEnergyThisMonth()
}

def getEnergyThisMonth(){
	logTrace("getEnergyThisMonth: month = ${state.monthToday} / year = ${state.yearToday}")
	sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""", "energyThisMonthResponse")
}

def energyThisMonthResponse(response) {
	def cmdResponse = parseInput(response)
	logTrace("energyThisMonthResponse: cmdResponse = ${cmdResponse}")
	def energy
	def monthList = cmdResponse["emeter"]["get_monthstat"].month_list
	def thisMonth = monthList.find { it.month == state.monthToday }
	if (state.energyScale == "energy_wh") {
		state.energyThisMonth = (thisMonth.energy_wh)/1000
		energy = state.energyThisMonth - state.todayStartEnergy
	} else {
		state.energyThisMonth = thisMonth.energy
		energy = state.energyThisMonth - state.todayStartEnergy
	}
	sendEvent(name: "energy", value: energy)
	logInfo("${device.label}: Energy Today is ${energy} kWh")
}

def getEnergyStats() {
	def year = state.yearToday
	logTrace("getEnergyStats: year = ${year}")
	sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""", "energyStatResponse")
}

def getPrevYear() {
	def year = state.yearToday - 1
	logTrace("getPrevYear: year = ${year}")
	sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""", "energyStatResponse")
}

def energyStatResponse(response) {
	def cmdResponse = parseInput(response)
	logTrace("energyStatResponse: cmdResponse = ${cmdResponse}")
	def monthList = cmdResponse["emeter"]["get_monthstat"].month_list
	def year = monthList[0].year
	if (year == state.yearToday) {
		state.energyThisYear = monthList
		getPrevYear()
		def thisMonth = monthList.find { it.month == state.monthToday }
		if (state.energyScale == "energy_wh") {
			state.todayStartEnergy = (thisMonth.energy_wh)/1000
		} else {
			state.todayStartEnergy = thisMonth.energy
		}
	} else {
		state.energyLastYear = monthList
	}
}

def setCurrentDate() {
	sendCmd('{"time":{"get_time":null}}', "currentDateResponse")
}

def currentDateResponse(response) {
	def cmdResponse = parseInput(response)
	logTrace("currentDateResponse: cmdResponse = ${cmdResponse}")
	def currDate =  cmdResponse["time"]["get_time"]
	state.dayToday = currDate.mday.toInteger()
	state.monthToday = currDate.month.toInteger()
	state.yearToday = currDate.year.toInteger()
}

def sendThisMonth() { return state.energyThisMonth }

def sendThisYear() { return state.energyThisYear }

def sendLastYear() { return state.energyLastYear }

//	===== Send the Command =====
private sendCmd(command, action) {
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
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 callback: action])
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