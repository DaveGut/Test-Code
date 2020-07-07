/*	Kasa Device Driver Series
Copyright Dave Gutheinz
License Information:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== 2020 History =====
02.28	New version 5.0.  Moved Quick Polling from preferences to a command with number (seconds)
		input value.  A value of blank or 0 is disabled.  A value below 5 is read as 5.
04.20	5.1.0	Update for Hubitat Program Manager
04.23	5.1.1	Update for Hub version 2.2.0, specifically the parseLanMessage = true option.
05.17	5.2.0	a.	Pre-encrypt refresh / quickPoll commands to reduce per-commnand processing
				b.	Integrated method parseInput into responses and deleted
05.21	5.2.1	Administrative version change to support HPM
05.27	5.2.1.1	Fixed type on quick poll switch implementation.
07.01	5.3.0	a.	Updated comms to use us rawSocket for all communications.
				b.	Limited quickPoll interval to 5 to 30 seconds (based on persistance
					of the Kasa rawSocket interface
====================================================================================================*/
def driverVer() { return "Beta-5.3.0" }

metadata {
	definition (name: "Kasa Dimming Switch",
    			namespace: "davegut",
				author: "Dave Gutheinz"	//,
//				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/DeviceDrivers/DimmingSwitch.groovy"
			   ) {
		capability "Switch"
		capability "Actuator"
		capability "Refresh"
		capability "Switch Level"
		command "setPollInterval", [[
			name: "Poll Interval (seconds)", 
			type: "ENUM",
			constraints: ["off", "5", "10", "15", "20", "25", "30"],
		]]
		command "presetLevel",  ["NUMBER"]
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
	state.remove("lastCommand")
	state.errorCount = 0
	if (!getDataValue("applicationVersion")) {
		if (!device_IP) {
			logWarn("updated: Device IP is not set.")
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
}

def on() {
	logDebug("on")
	sendCmd("00000046d0f281f88bff9af7d5ef94b6c5a0d48bf99cf091e8" +
			"b7c4b0d1a5c0e2d8a381f286e793f6d4eedfa2dff3d1a2dba8" +
			"dcb9d4f6ccb795f297e3bccfb6c5acc2a4cbe9d3a8d5a8d5")
}

def off() {
	logDebug("off")
	sendCmd("00000046d0f281f88bff9af7d5ef94b6c5a0d48bf99cf091e8" +
			"b7c4b0d1a5c0e2d8a381f286e793f6d4eedea3def2d0a3daa9" +
			"ddb8d5f7cdb694f396e2bdceb7c4adc3a5cae8d2a9d4a9d4")
}

def refresh() {
	logDebug("refresh")
	sendCmd("0000001dd0f281f88bff9af7d5ef94b6d1b4c09fec95e68fe187e8caf08bf68bf6")
}

def setLevel(percentage, transition = null) {
	logDebug("setLevel: level = ${percentage}")
	percentage = percentage.toInteger()
	if (percentage < 0) { percentage = 0 }
	if (percentage > 100) { percentage = 100 }
	percentage = percentage.toInteger()
	def command = """{"system":{"set_relay_state":{"state":1}},""" +
			      """"smartlife.iot.dimmer":{"set_brightness":{"brightness":${percentage}}},""" +
			      """"system" :{"get_sysinfo" :{}}}"""
	sendCmd(outputXOR(command))
}

def presetLevel(percentage) {
	logDebug("presetLevel: level = ${percentage}")
	percentage = percentage.toInteger()
	if (percentage < 0) { percentage = 0 }
	if (percentage > 100) { percentage = 100 }
	percentage = percentage.toInteger()
	def command = """{"smartlife.iot.dimmer":{"set_brightness":{"brightness":${percentage}}},""" +
				  """"system" :{"get_sysinfo" :{}}}"""
	sendCmd(outputXOR(command))
}

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

def commandResponse(status) {
	logDebug("parse: status = ${status}")
	def onOff = "on"
	if (status.relay_state == 0 || status.state == 0) { onOff = "off" }
	if (onOff != device.currentValue("switch")) {
		sendEvent(name: "switch", value: onOff, type: "physical")
		logInfo("parse: switch: ${onOff}")
	}
	if(status.brightness != device.currentValue("level")) {
		sendEvent(name: "level", value: status.brightness)
		logInfo("parse: level: ${status.brightness}")
	}

	if (state.pollInterval > 0) {
		runIn(state.pollInterval, refresh)
	}
}

//	===== rawSocket Parse =====
def parse(response) {
	logDebug("parse: parsing return")
	unschedule(rawSocketTimeout)
	state.errorCount = 0
	def status
	try { status = parseJson(inputXOR(response)).system.get_sysinfo }
	catch (e) { logWarn("parse: Invalid return from device") }
	commandResponse(status)
}

//	===== Common Kasa Driver code =====
private sendCmd(command) {
	logDebug("sendCmd")
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