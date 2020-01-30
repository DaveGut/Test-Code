/*
TP-Link Energy Monitor Plug Device Driver, Version 4.6
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
01.11	4.6.02	Removed Name Sync.  TP-Link has removed command from the devices in latest firmware.
01.20	4.6.03	Corrected error precluding kicking off fast poll.
01.21	4.6.04	Further fixes for fast polling of energy (HS110 not reporting).
01.30	4.6.05	Updated time handling to check time when updating stats and update if not correct.
===== GitHub Repository =====
	https://github.com/DaveGut/Hubitat-TP-Link-Integration
=======================================================================================================*/
	def driverVer() { return "4.6.05" }
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
		input ("shortPoll", "number",title: "Fast Power Polling Interval - <b>Caution</b> ('0' = disabled)",
			   defaultValue: 0)
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
		runIn(5, updateStats)
	}
	if (debug == true) { runIn(1800, debugLogOff) }
	logInfo("Debug logging is: ${debug}.")
	logInfo("Description text logging is ${descriptionText}.")
	logInfo("Refresh set for every ${refresh_Rate} minute(s).")
	logInfo("ShortPoll set for ${shortPoll}")
	logInfo("Scheduled nightly energy statistics update.")
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

	logDebug("commandResponse: status = ${status}")
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
	if (shortPoll > 0) { runIn(shortPoll, powerPoll) }
}

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
	if (shortPoll > 0) { runIn(shortPoll, powerPoll) }
}

def updateStats() {
	logDebug("updateStats")
	checkDate()
}

def checkDate() {
	logInfo("checkDate")
	sendCmd("""{"time":{"get_time":null}}""", "checkDateResponse")
}

def checkDateResponse(response) {
	def data = parseInput(response).time.get_time
	logInfo("checkDateResponse: current date = ${data}")
	def newDate = newDate()
	def year = newDate.format("yyyy").toInteger()
	def month = newDate.format("M").toInteger()
	def day = newDate.format("d").toInteger()
	if(year == data.year.toInteger() && month == data.month.toInteger() && day == data.mday.toInteger()) {
		state.currDate = [data.year, data.month, data.mday]
		pauseExecution(1000)
		logInfo("checkDateResponse: currDate = ${state.currDate}")
		if(getDataValue("plugId")) {
			sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},"emeter":{"get_monthstat":{"year": ${thisYear()}}}}""",
					"setThisMonth")
		} else {
			sendCmd("""{"emeter":{"get_monthstat":{"year": ${thisYear()}}}}""",
					"setThisMonth")
		}
	} else {
		logInfo("checkDateResponse: date is not current.")
		def hour = newDate.format("H").toInteger()
		def min = newDate.format("m").toInteger()
		def sec = newDate.format("s").toInteger()
		changeDate(year, month, day, hour, min, sec)
	}
}

def changeDate(year, month, mday, hour, min, sec) {
	logInfo("changeDate: Updating date to ${year} /${month} /${mday} /${hour} /${min} /${sec}")
	sendCmd("""{"time":{"set_timezone":{"year":${year},"month":${month},"mday":${mday},"hour":${hour},"min":${min},"sec":${sec},"index":55}}}""", 
			"changeDateResponse")
}

def changeDateResponse(response) { 
	logInfo("changeDateResponse: response = ${parseInput(response)}")
	checkDate()
}

def setThisMonth(response) {
	def cmdResponse = parseInput(response)
	logDebug("setThisMonth: cmdResponse = ${cmdResponse}")
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

def thisYear() {
	return state.currDate[0].toInteger()
}

def thisMonth() {
	return state.currDate[1].toInteger()
}

def today() {
	return state.currDate[2].toInteger()
}

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

def powerPoll() {
	if(getDataValue("plugId")) {
		sendPowerPollCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},"emeter":{"get_realtime":{}}}""", 
				"powerPollResponse")
	} else {
		sendPowerPollCmd("""{"emeter":{"get_realtime":{}}}""", "powerPollResponse")
	}
}

private sendPowerPollCmd(command, action) {
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

def powerPollResponse(response) {
	def encrResponse = parseLanMessage(response).payload
	def cmdResponse = parseJson(inputXOR(encrResponse))
	logDebug("powerPollResponse: cmdResponse = ${cmdResponse}")
	def realtime = cmdResponse.emeter.get_realtime
	def power = realtime.power
	if (power == null) { power = realtime.power_mw / 1000 }
	power = (0.5 + Math.round(100*power)/100).toInteger()
	if (device.currentValue("power") != power) {
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W")
		logInfo("powerPollResponse: Power set to ${power} watts")
	}
	if (shortPoll > 0) { runIn(shortPoll, powerPoll) }
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
	if (descriptionText == true) { log.info "${device.label} ${driverVer()} ${msg}" }
}

def logDebug(msg){
	if(debug == true) { log.debug "${device.label} ${driverVer()} ${msg}" }
}

def logWarn(msg){ log.warn "${device.label} ${driverVer()} ${msg}" }

//	end-of-file