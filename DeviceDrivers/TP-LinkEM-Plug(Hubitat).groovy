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
===== 2019 History =====
10.01	4.5.01	Combined HS110 and HS300 drivers to single driver.
10.05	4.5.02	Corrected power level extraction.  Increased error count for retry.
10.10	4.5.10	Updated to create individual types for the devices to alleviate confusion and errors.
12-05	4.5.12	Update to incorporate common changes and eliminate events where state has not changed.
12-18	4.5.13	New preference - emFuncion to enable em attributes.  Added sunset to debug log.  Updated
12-29	4.5.14	Interim Changed year function due to error.  Added method thisYear and moded 214, 277, and 311. 
				logInfo for one line per external action or refresh.
12-29	4.5.15	Final.  Update day, month, year processing to account for changes in Groovy Date.
===== GitHub Repository =====
	https://github.com/DaveGut/Hubitat-TP-Link-Integration
=======================================================================================================*/
	def driverVer() { return "4.5.15" }
	def type() { return "Engr Mon Plug" }
//	def type() { return "Engr Mon Multi-Plug" }
	def gitHubName() {
		if (type() == "Engr Mon Plug") { return "EM-Plug" }
		else { return "EM-Multi-Plug" }
	}
metadata {
	definition (name: "TP-Link ${type()}",
				namespace: "davegut",
                author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/Hubitat-TP-Link-Integration/master/DeviceDrivers/TP-Link${gitHubName()}(Hubitat).groovy"
			   ) {
		capability "Switch"
		capability "Actuator"
		capability "Refresh"
		capability "Power Meter"
		capability "Energy Meter"
		attribute "currMonthTotal", "number"
		attribute "currMonthAvg", "number"
		attribute "lastMonthTotal", "number"
		attribute "lastMonthAvg", "number"
		attribute "commsError", "bool"
	}
    preferences {
		if (!getDataValue("applicationVersion")) {
			input ("device_IP", "text", title: "Device IP (Current = ${getDataValue("deviceIP")})")
			input ("multiPlug", "bool", title: "Device is part of a Multi-Plug)")
			input ("plug_No", "text",
				   title: "For multiPlug, the number of the plug (00, 01, 02, etc.)")
		}
		input ("emFunction", "bool", title: "Enable Energy Monitor Functions", defaultValue: true)
		input ("refresh_Rate", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"], defaultValue: "30")
		input ("shortPoll", "number",title: "Fast Power Polling Interval ('0' = disabled)",
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
		} else if (multiPlug == true && !plug_No) {
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
	if (emFunction) {
		schedule("0 01 0 * * ?", updateStats)
		updateStats()
	}
	pauseExecution(1000)

	if (debug == true) { runIn(1800, debugLogOff) }

	logInfo("Debug logging is: ${debug}.")
	logInfo("Description text logging is ${descriptionText}.")
	logInfo("Refresh set for every ${refresh_Rate} minute(s).")
	logInfo("ShortPoll set for ${shortPoll}")
	logInfo("Scheduled nightly energy statistics update.")

	if (nameSync == "device" || nameSync == "hub") { syncName() }
	refresh()
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
	//	Usage is for updating parameters on driver change to clean up as much as possible.
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
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},"system":{"set_relay_state":{"state": 1}},""" +
				""""system" :{"get_sysinfo" :{}}}""", "commandResponse")
	} else {
		sendCmd("""{"system" :{"set_relay_state" :{"state" : 1}},"system" :{"get_sysinfo" :{}}}""", "commandResponse")
	}
}

def off() {
	logDebug("off")
	if(getDataValue("plugId")) {
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},"system":{"set_relay_state":{"state": 0}},""" +
				""""system" :{"get_sysinfo" :{}}}""", "commandResponse")
	} else {
		sendCmd("""{"system" :{"set_relay_state" :{"state" : 0}},"system" :{"get_sysinfo" :{}}}""", "commandResponse")
	}
}

def refresh() {
	logDebug("refresh")
	sendCmd("""{"system" :{"get_sysinfo" :{}}}""", "commandResponse")
}

def commandResponse(response) {
	def cmdResponse = parseInput(response)
	def status = cmdResponse.system.get_sysinfo
	def relayState = status.relay_state
	if (getDataValue("plugNo")) {
		status = status.children.find { it.id == getDataValue("plugNo") }
		relayState = status.state
	}

	logDebug("refreshResponse: status = ${status}")
	def onOff = "off"
	if (relayState == 1) { onOff = "on" }
	sendEvent(name: "switch", value: onOff)
	//	Get current energy data.  If emFunction disabled, ignore.
	if (emFunction) {
		if(getDataValue("plugId")) {
			sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},"emeter":{"get_realtime":{}}}""", 
					"powerResponse")
		} else {
			sendCmd("""{"emeter":{"get_realtime":{}}}""", "powerResponse")
		}
	} else {
		logInfo("Status: [switch:${onOff}]")
	}
}


//	Update Today's power data.  Called from refreshResponse.
def powerResponse(response) {
	def cmdResponse = parseInput(response)
	logDebug("powerResponse: cmdResponse = ${cmdResponse}")
	def realtime = cmdResponse.emeter.get_realtime

	def power = realtime.power
	if (power == null) { power = realtime.power_mw / 1000 }
	power = (0.5 + Math.round(100*power)/100).toInteger()
	sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W")
	//	get total energy today
	if(getDataValue("plugId")) {
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},"emeter":{"get_monthstat":{"year": ${thisYear()}}}}""",
				"setEngrToday")
	} else {
		sendCmd("""{"emeter":{"get_monthstat":{"year": ${thisYear()}}}}""",
				"setEngrToday")
	}
}

def setEngrToday(response) {
	def cmdResponse = parseInput(response)
	logDebug("setEngrToday: ${cmdResponse}")
	def data = cmdResponse.emeter.get_monthstat.month_list.find { it.month == thisMonth() }
	def energyData = data.energy
	if (energyData == null) { energyData = data.energy_wh/1000 }
	energyData -= device.currentValue("currMonthTotal")
	energyData = Math.round(100*energyData)/100
	sendEvent(name: "energy", value: energyData, descriptionText: "KiloWatt Hours", unit: "KWH")
	//	Log the current status.
	def deviceStatus = [:]
	deviceStatus << ["switch" : device.currentValue("switch")]
	deviceStatus << ["power" : device.currentValue("power")]
	deviceStatus << ["energy" : device.currentValue("energy")]
	logInfo("Status: ${deviceStatus}")
	//	Short Power Polling function.
	if (shortPoll.toInteger() > 0) { runIn(shortPoll.toInteger(), powerPoll) }
}

//	Power Polling Methods
def powerPoll() {
	if(getDataValue("plugId")) {
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},"emeter":{"get_realtime":{}}}""", 
				"powerPollResponse")
	} else {
		sendCmd("""{"emeter":{"get_realtime":{}}}""", "powerPollResponse")
	}
}

def powerPollResponse(response) {
	def cmdResponse = parseInput(response)
	logDebug("powerPollResponse cmdResponse: ${cmdResponse}")
	def realtime = cmdResponse.emeter.get_realtime
	def scale = "energy"
	if (realtime.power == null) { scale = "power_mw" }
	def power = realtime."${scale}"
	if(power == null) { power = 0 }
	else if (scale == "power_mw") { power = power / 1000 }
	power = (0.5 + Math.round(100*power)/100).toInteger()
	if (device.currentValue("power") != power) {
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W")
	}
	
	if (shortPoll.toInteger() > 0) { runIn(shortPoll.toInteger(), powerPoll) }
}


//	Update this and last month's stats (at 00:01 AM).  Called from updated.
def updateStats() {
	logDebug("updateStats")
	if(getDataValue("plugId")) {
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},"emeter":{"get_monthstat":{"year": ${thisYear()}}}}""",
				"setThisMonth")
	} else {
		sendCmd("""{"emeter":{"get_monthstat":{"year": ${thisYear()}}}}""",
				"setThisMonth")
	}
}

def setThisMonth(response) {
	def cmdResponse = parseInput(response)
	logDebug("setThisMonth: energyScale = ${state.energyScale}, cmdResponse = ${cmdResponse}")
	def data = cmdResponse.emeter.get_monthstat.month_list.find { it.month == thisMonth() }
	def scale = "energy"
	def energyData
	if (data == null) { energyData = 0 }
	else {
		if (data.energy == null) { scale = "energy_wh" }
		energyData = data."${scale}"
	}
	def avgEnergy = 0
	def day = today()
	if (day !=1) { avgEnergy = energyData/(day - 1) }
	if (scale == "energy_wh") {
		energyData = energyData/1000
		avgEnergy = avgEnergy/1000
	}
	energyData = Math.round(100*energyData)/100
	avgEnergy = Math.round(100*avgEnergy)/100
	sendEvent(name: "currMonthTotal", value: energyData, descriptionText: "KiloWatt Hours", unit: "KWH")
	sendEvent(name: "currMonthAvg", value: avgEnergy, descriptionText: "KiloWatt Hours per Day", unit: "KWH/D")
	logInfo("This month's energy stats set to ${energyData} // ${avgEnergy}")
	def year = thisYear()
	if (thisMonth() == 1) { year = year -1 }
	if(getDataValue("plugId")) {
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},"emeter":{"get_monthstat":{"year": ${year}}}}""",
				"setLastMonth")
	} else {
		sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""",
				"setLastMonth")
	}
}

def setLastMonth(response) {
	def cmdResponse = parseInput(response)
	logDebug("setThisMonth: energyScale = ${state.energyScale}, cmdResponse = ${cmdResponse}")
	def lastMonth = thisMonth() -1
	if (lastMonth == 0) { lastMonth = 12 }
	def monthLength
	switch(lastMonth) {
		case 4:
		case 6:
		case 9:
		case 11:
			monthLength = 30
			break
		case 2:
			monthLength = 28
			def year = thisYear()
			if (year == 2020 || year == 2024 || year == 2028) { monthLength = 29 }
			break
		default:
			monthLength = 31
	}
	def data = cmdResponse.emeter.get_monthstat.month_list.find { it.month == lastMonth }
	def scale = "energy"
	def energyData
	if (data == null) { energyData = 0 }
	else {
		if (data.energy == null) { scale = "energy_wh" }
		energyData = data."${scale}"
	}
	def avgEnergy = energyData/monthLength
	if (scale == "energy_wh") {
		energyData = energyData/1000
		avgEnergy = avgEnergy/1000
	}
	energyData = Math.round(100*energyData)/100
	avgEnergy = Math.round(100*avgEnergy)/100
	sendEvent(name: "lastMonthTotal", value: energyData, descriptionText: "KiloWatt Hours", unit: "KWH")
	sendEvent(name: "lastMonthAvg", value: avgEnergy, descriptionText: "KiloWatt Hoursper Day", unit: "KWH/D")
	logInfo("Last month's energy stats set to ${energyData} // ${avgEnergy}")
}


//	===== Synchronize naming between the device and Hubitat =====
def syncName() {
	logDebug("syncName. Synchronizing device name and label with master = ${nameSync}")
	if (nameSync == "hub") {
		if(getDataValue("plugId")) {
			sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},"system":{"set_dev_alias":{"alias":"${device.label}"}}}""",
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
def thisYear() {
	def year = new Date().format("yyyy")
	return year.toInteger()
}
def thisMonth() {
	def month = new Date().format("M")
	return month.toInteger()
}
def today() {
	def day = new Date().format("d")
	return day.toInteger()
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
def logInfo(msg) {
	if (descriptionText == true) { log.info "<b>${device.label} ${driverVer()}</b> ${msg}" }
}
def logDebug(msg){
	if(debug == true) { log.debug "<b>${device.label} ${driverVer()}</b> ${msg}" }
}
def logWarn(msg){ log.warn "<b>${device.label} ${driverVer()}</b> ${msg}" }

//	end-of-file