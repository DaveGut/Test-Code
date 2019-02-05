/*
TP-Link Device Driver, Version 4.1

	Copyright 2018, 2019 Dave Gutheinz

Discalimer:  This Applicaion and the associated Device Drivers are in no way sanctioned or supported by TP-Link.  
All  development is based upon open-source data on the TP-Link devices; primarily various users on GitHub.com.

===== History =====
2.04.19	4.1.01.	Final code for Hubitat without reference to deviceType and enhancement of logging functions. Added
				code to delete the device as a child from the application when deleted via the devices page.  Note:
				The device is also deleted whenever the application is deleted.

*/
def driverVer() { return "4.1.01" }
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
    	input name: "infoLog", type: "bool", title: "Display information messages?", required: false
    	input name: "traceLog", type: "bool", title: "Display trace messages?", required: false
	}
}

def installed() {
	log.info "Installing ${device.label}..."
	if(!state.todayStartEnergy) { state.todayStartEnergy = 0 }
	state.installed = false
	sendEvent(name: "commsError", value: "none")
	device.updateSetting("refresh_Rate",[type:"enum", value:"30"])
	device.updateSetting("infoLog", [type:"bool", value: true])
	device.updateSetting("traceLog", [type:"bool", value: true])
	runIn(900, stopTraceLogging)
	runIn(2, updated)
}

def updated() {
	log.info "Updating ${device.label}..."
	unschedule()
	if (traceLog == true) {
		device.updateSetting("traceLog", [type:"bool", value: true])
		runIn(900, stopTraceLogging)
	} else { stopTraceLogging() }
	if (!infoLog || infoLog == true) {
		device.updateSetting("infoLog", [type:"bool", value: true])
	} else {
		device.updateSetting("infoLog", [type:"bool", value: false])
	}
	state.commsErrorCount = 0
	sendEvent(name: "commsError", value: "none")
	updateDataValue("driverVersion", driverVer())
	if(device_IP) {
		updateDataValue("deviceIP", device_IP)
	}
	if (state.currentError) { state.currentError = null }
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
		if (!state.installed || state.installed == false) {
			runIn(5, getEnergyStats)
			state.installed = true
		}
	}
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
	sendCmd("""{"system" :{"set_relay_state" :{"state" : 1}}}""", "commandResponse")
}

def off() {
	logTrace("off")
	sendCmd("""{"system" :{"set_relay_state" :{"state" : 0}}}""", "commandResponse")
}

def refresh(){
	sendCmd('{"system" :{"get_sysinfo" :{}}}', "refreshResponse")
}

def parseInput(response) {
	unschedule(createCommsError)
	sendEvent(name: "commsError", value: "none")
	state.commsErrorCount = 0
	def encrResponse = parseLanMessage(response).payload
	try {
		def cmdResponse = parseJson(inputXOR(encrResponse))
		logTrace("parseInput: response = ${cmdResponse}")
		return cmdResponse
	} catch (error) {
		log.error "${device.label} parseInput fragmented return from device.  In Kasa App reduce device name to less that 18 characters!"
		sendEvent(name: "commsError", value: "parseInput failed.  See logs.")
	}
}

def commandResponse(response) {
	logTrace("commandResponse: response = ${response}")
	unschedule(createCommsError)
	state.currentError = null
	refresh()
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
	def monthList = cmdResponse["smartlife.iot.common.emeter"]["get_monthstat"].month_list
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
	def monthList = cmdResponse["smartlife.iot.common.emeter"]["get_monthstat"].month_list
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
	logTrace("sendCmd: command = ${command} // action = ${action} // device IP = ${getDataValue("deviceIP")}")
	if (!getDataValue("deviceIP")) {
		sendEvent(name: "commsError", value: "No device IP. Update Preferences.")
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