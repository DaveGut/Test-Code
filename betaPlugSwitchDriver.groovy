/*	Kasa Device Driver Series
Copyright Dave Gutheinz
License Information:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== 2020 History =====
02.28	New version 5.0.  Moved Quick Polling from preferences to a command with number (seconds)
		input value.  A value of blank or 0 is disabled.  A value below 5 is read as 5.
04.20	5.1.0	Update for Hubitat Program Manager
04.23	5.1.1	Update for Hub version 2.2.0, specifically the parseLanMessage = true option.
05,17	5.2.0	a.	Pre-encrypt refresh / quickPoll commands to reduce per-commnand processing
				b.	Integrated method parseInput into responses and deleted
05.21	5.2.1	Administrative version change to support HPM
05.27	5.2.1.1	Fixed type on quick poll switch implementation.
07.01	5.3.0	a.	Updated comms to use us rawSocket for all communications.
				b.	Limited quickPoll interval to 5 to 30 seconds (based on persistance
					of the Kasa rawSocket interface
====================================================================================================*/
def driverVer() { return "Beta-5.3.0" }

metadata {
	definition (name: "Kasa Plug Switch",
    			namespace: "davegut",
				author: "Dave Gutheinz"	//,
//				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/DeviceDrivers/EM-Multi-Plug.groovy"
			   ) {
		capability "Switch"
		capability "Actuator"
		capability "Refresh"
		command "setPollInterval", [[
			name: "Poll Interval (seconds)", 
			type: "ENUM",
			constraints: ["off", "5", "10", "15", "20", "25", "30"],
		]]
		capability "Power Meter"
		capability "Energy Meter"
		attribute "currMonthTotal", "number"
		attribute "currMonthAvg", "number"
		attribute "lastMonthTotal", "number"
		attribute "lastMonthAvg", "number"
	}
	
	preferences {
		if (!getDataValue("applicationVersion")) {
			input ("device_IP", "text", title: "Device IP", defaultValue: getDataValue("deviceIP"))
		}
		input ("refresh_Rate", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "10", "15", "30", "60", "180"], defaultValue: "60")
		input ("debug", "bool", title: "Enable debug logging", defaultValue: false)
		input ("descriptionText", "bool", title: "Enable description text logging", defaultValue: true)
	}
}

def installed() {
	log.info "Installing .."
	state.pollFreq = 0
	updated()
}

def updated() {
	log.info "Updating .."
	unschedule()
	state.errorCount = 0
	if (!state.concat) { state.concat = 0 }
	if (device.currentValue("driverVersion") != driverVer()) {
		updateDataValue("driverVersion", driverVer())
	}
	
	if (!getDataValue("applicationVersion")) {
		if (!device_IP) {
			logWarn("updated: Device IP is not set.\n" +
					"<b>The Device NAME (in Device Information) must be set to HS110 for Energy Monitor functions to work.</b>")
			return
		}
		if (getDataValue("deviceIP") != device_IP.trim()) {
			updateDataValue("deviceIP", device_IP.trim())
			logInfo("updated: Device IP set to ${device_IP.trim()}")
		}
	}
	
	switch(refresh_Rate) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "10" : runEvery10Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		case "30" : runEvery30Minutes(refresh); break
		case "180": runEvery3Hours(refresh); break
		default: runEvery1Hour(refresh)
	}
	
	logInfo("updated: Refresh set for every ${refresh_Rate} minute(s).")
	if (debug == true) { runIn(1800, debugLogOff) }
	logInfo("updated: Debug logging is: ${debug} for 30 minutes.")
	logInfo("updated: Description text logging is ${descriptionText}.")
	refresh()
	
	//	===== EM Function Kick-start =====
	if (device.name == "HS110") {
		if (!device.currentValue("power")) {
			sendEvent(name: "power", value: 0, descriptionText: "Watts", unit: "W")
		}
		schedule("0 01 0 * * ?", updateStats)
		updateStats()
		logInfo("updated: Scheduled nightly energy statistics update.")
	}
}

//	===== Command Methods =====
def on() {
	logDebug("on")
	unschedule(quickPoll)
	sendCmd("on", "00000046d0f281f88bff9af7d5ef94b6c5a0d48bf99cf091e8" +
			"b7c4b0d1a5c0e2d8a381f286e793f6d4eedfa2dff3d1a2dba8" +
			"dcb9d4f6ccb795f297e3bccfb6c5acc2a4cbe9d3a8d5a8d5")
}

def off() {
	logDebug("off")
	unschedule(quickPoll)
	sendCmd("off", "00000046d0f281f88bff9af7d5ef94b6c5a0d48bf99cf091e8" +
			"b7c4b0d1a5c0e2d8a381f286e793f6d4eedea3def2d0a3daa9" +
			"ddb8d5f7cdb694f396e2bdceb7c4adc3a5cae8d2a9d4a9d4")
}

def refresh() {
	logDebug("refresh")
	unschedule(quickPoll)
	sendCmd("refresh", "0000001dd0f281f88bff9af7d5ef94b6d1b4c09fec95e68fe187e8caf08bf68bf6")
}

def commandResponse(status) {
	logDebug("commandResponse: status = ${status}")
	def onOff = "on"
	if (status.relay_state == 0) { onOff = "off" }
	if (onOff != device.currentValue("switch")) {
		sendEvent(name: "switch", value: onOff, type: "digital")
		logInfo("parse: switch: ${onOff}")
	}

	if (state.pollInterval > 0) {
		runIn(state.pollInterval, quickPoll)
	}
	if (device.name == "HS110") {
		sendCmd("getPower", "0000001ed0f297fa9feb8efcdee49fbddabfcb94e683e28efa93fe9bb983f885f885")
	}
}

//	===== Energy Monitor Methods =====
def powerResponse(status) {
	logDebug("powerResponse: status = ${status}")
	def power = status.power
	if (power == null) { power = status.power_mw / 1000 }
	power = (0.5 + Math.round(100*power)/100).toInteger()
	def curPwr = device.currentValue("power").toInteger()
	if (power > curPwr + 3 || power < curPwr - 3) { 
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W")
		logInfo("powerResponse: power = ${power}")
	}

	if (state.pollFreq > 0) {
		runIn(state.pollFreq, quickPoll)
	}
	def year = new Date().format("yyyy").toInteger()
	sendCmd("setEnergyToday", outputXOR("""{"emeter":{"get_daystat":{"month": ${month}, "year": ${year}}}}"""))
}

def powerPollResponse(status) {
	def power = status.power
	if (power == null) { power = realtime.power_mw / 1000 }
	power = (0.5 + Math.round(100*power)/100).toInteger()
	def curPwr = device.currentValue("power").toInteger()
	if (power > curPwr + 3 || power < curPwr - 3) { 
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W")
		logInfo("powerPollResponse: power = ${power}")
	}

	if (state.pollInterval > 0) {
		runIn(state.pollInterval, quickPoll)
	}
}

def setEnergyToday(status) {
	logDebug("setEnergyToday: ${status}")
	def day = new Date().format("d").toInteger()
	def data = status.day_list.find { it.day == day }
	def energyData = data.energy
	if (energyData == null) { energyData = data.energy_wh/1000 }
	energyData = Math.round(10*energyData)/10
	if (energyData != device.currentValue("energy")) {
		sendEvent(name: "energy", value: energyData, descriptionText: "KiloWatt Hours", unit: "KWH")
		logInfo("setEngrToday: [energy: ${energyData}]")
	}
}

def updateStats() {
	logDebug("updateStats")
//	state.statsUpdated = false
	def year = new Date().format("yyyy").toInteger()
	sendCmd("getThisMonth", outputXOR("""{"emeter":{"get_monthstat":{"year":${year}}}}"""))
}

def setThisMonth(status) {
	logDebug("setThisMonth: ${status}")
	def year = new Date().format("yyyy").toInteger()
	def month = new Date().format("M").toInteger()
	def day = new Date().format("d").toInteger()
	def data = status.month_list.find { it.month == month }
	def scale = "energy"
	def energyData
	if (data == null) { energyData = 0 }
	else {
		if (data.energy == null) { scale = "energy_wh" }
		energyData = data."${scale}"
	}
	def avgEnergy = 0
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
	if (month != 1) {
		setLastMonth(status)
	} else {
		sendCmd("getLastMonth", outputXOR("""{"emeter":{"get_monthstat":{"year":${year-1}}}}"""))
	}
}

def setLastMonth(status) {
	logDebug("setLastMonth: cmdResponse = ${status}")
	def year = new Date().format("yyyy").toInteger()
	def month = new Date().format("M").toInteger()
	def data = status.month_list.find { it.month == month-1 }
	def lastMonth = month - 1
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
			if (year == 2020 || year == 2024 || year == 2028) { monthLength = 29 }
			break
		default:
			monthLength = 31
	}
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
	refresh()
}

//	===== rawSocket Parse =====
def parse(response) {
	unschedule(rawSocketTimeout)
	state.errorCount = 0
	if (response.length() == 2048) {
		state.concat = 1
		return concat(response)
	} else if (state.concat == 1) {
		state.concat = 2
		return concat(response)
	}
	
	def cmdName = state.cmdName
	def status
	logDebug("parse")
	try { status = parseJson(inputXOR(response)) }
	catch (e) { logWarn("parse: Invalid return from device") }
	switch(cmdName) {
		case "powerPoll" :
			powerPollResponse(status.emeter.get_realtime)
			break
		case "on" :
		case "off" :
		case "refresh" :
			commandResponse(status.system.get_sysinfo)
			break
		case "getPower" :
			powerResponse(status.emeter.get_realtime)
			break
		case "getEnergy" :
			setEnergyToday(status.emeter.get_daystat)
			break
		case "getThisMonth" :
			setThisMonth(status.emeter.get_monthstat)
			break
		case "getLastMonth" :
			setLastMonth(status.emeter.get_monthstat)
			break
		default:
			logDebug("responseDist error in return: ${resp}")
			return
	}
}

def concat(response) {
	if (state.concat == 2) {
		state.concat = 0
		response = state.response + response
		state.response = ""
		parse(response)
	} else {
		state.response = response
	}
}

//	===== quickPoll =====
def setPollInterval(interval) {
	if (interval == "off") {
		logInfo("setPollInterval: polling is off")
		unschedule(quickPoll)
		state.pollInterval = 0
	} else {
		interval = interval.toInteger()
		state.pollInterval = interval
		logInfo("setPollInterval: polling interval set to ${interval} seconds")
		refresh()
	}
}

def quickPoll() {
	def command
	if (device.name == "HS110") {
		state.cmdName == "powerPoll"
		command = "0000001ed0f297fa9feb8efcdee49fbddabfcb94e683e28efa93fe9bb983f885f885"
	} else {
		if (device.name == "HS200") {
			interfaces.rawSocket.connect("${getDataValue("deviceIP")}", 9999, byteInterface: true)
		}
		state.cmdName == "refresh"
		command = "0000001dd0f281f88bff9af7d5ef94b6d1b4c09fec95e68fe187e8caf08bf68bf6"
	}
	runIn(2, rawSocketTimeout, [data: command])
	interfaces.rawSocket.sendMessage(command)
}

//	===== Common Kasa Driver code =====
private sendCmd(cmdName, command) {
	logDebug("sendCmd")
	state.cmdName = cmdName
	runIn(2, rawSocketTimeout, [data: command])
	try {
		interfaces.rawSocket.connect("${getDataValue("deviceIP")}", 9999, byteInterface: true)
		interfaces.rawSocket.sendMessage(command)
	} catch (e) { 
		logDebug("sendCmd: error on rawSocket = ${e}")
	}
}

def socketStatus(message) {
	if (message == "receive error: Stream closed.") {
		logDebug("socketStatus: Socket Established")
	} else {
		logWarn("socketStatus = ${message}")
	}
}

def rawSocketTimeout(command) {
	state.errorCount += 1
logWarn("rawSocketTimeout: ${state.errorCount}")
	if (state.errorCount <= 3) {
		logDebug("rawSocketTimeout: reattempting command, attempt: ${state.errorCount}")
		sendCmd(command)
	} else if (state.errorCount == 4) {
		logDebug("rawSocketTimeout: attempting a refresh to restart comms")
		runIn(60, refresh)		
	} else {
		logWarn("rawSocketTimeout: Retry on error limit exceeded.  Error count = ${state.errorCount}")
	}
}

//	-- Encryption / Decryption
private outputXOR(command) {
	def str = ""
	def encrCmd = "000000" + Integer.toHexString(command.length()) 
 	def key = 0xAB
	for (int i = 0; i < command.length(); i++) {
		str = (command.charAt(i) as byte) ^ key
		key = str
		encrCmd += Integer.toHexString(str)
	}
   	return encrCmd
}

private inputXOR(resp) {
	String[] strBytes = resp.substring(8).split("(?<=\\G.{2})")
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

//	-- Logging
def logInfo(msg) {
	if (descriptionText == true) { log.info "${device.label} ${driverVer()} ${msg}" }
}

def debugLogOff() {
	device.updateSetting("debug", [type:"bool", value: false])
	logInfo("Debug logging is false.")
}

def logDebug(msg){
	if(debug == true) { log.debug "${device.label} ${driverVer()} ${msg}" }
}

def logWarn(msg){ log.warn "${device.label} ${driverVer()} ${msg}" }