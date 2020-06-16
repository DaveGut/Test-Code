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
06.20	5.3.0	a.	Updated comms to use us rawSocket for all communications.
				b.	Limited quickPoll interval to 5 to 40 seconds (based on persistance
					of the Kasa rawSocket interface
NOTES:  This version uses only rawSocket.  I want to expand to other devices to see if there
is some issue with UDP that can be solved.  Particularly looking at the Multi-Plugs and processing
that data
====================================================================================================*/
def driverVer() { return "B 5.2.1" }
metadata {
	definition (name: "B Kasa Dimming Switch",
    			namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/DeviceDrivers/DimmingSwitch.groovy"
			   ) {
		capability "Switch"
		capability "Actuator"
		capability "Refresh"
		capability "Switch Level"
		command "setPollFreq", ["NUMBER"]
		command "presetLevel",  ["NUMBER"]
	}
	//	Preferences are set up to request the Device IP if a person chooses to do a manual install.
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
	//	On manual installation, the IP is not present until
	//	entered by user.  notifies user.  The next clause
	//	sets the entered deviceIP
	if (device.currentValue("driverVersion") != driverVer()) {
		updateDataValue("driverVersion", driverVer())
	}
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
	//	set state.errorsSinceReset
	resetErrorsSinceReset()
	//	log then set to zero state.errorsSinceReset to track times
	//	the app goes to error processing.  This will remain in
	//	place in the first production version (for troubleshooting
	//	information.
	schedule("0 01 0 * * ?", resetErrorsSinceReset)
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

def resetErrorsSinceReset() {
	log.info "resetErrorsSinceReset: total errors = state.errorsSinceReset"
	state.errorsSinceReset = 0
}

def on() {
	unschedule(quickPoll)
	//	Where feasible, commands are pre-encrypted to reduce processing.
	sendCmd("00000046d0f281f88bff9af7d5ef94b6c5a0d48bf99cf091e8" +
			"b7c4b0d1a5c0e2d8a381f286e793f6d4eedfa2dff3d1a2dba8" +
			"dcb9d4f6ccb795f297e3bccfb6c5acc2a4cbe9d3a8d5a8d5",
		"on")
}

def off() {
	unschedule(quickPoll)
	sendCmd("00000046d0f281f88bff9af7d5ef94b6c5a0d48bf99cf091e8" +
			"b7c4b0d1a5c0e2d8a381f286e793f6d4eedea3def2d0a3daa9" +
			"ddb8d5f7cdb694f396e2bdceb7c4adc3a5cae8d2a9d4a9d4",
		"off")
}

def refresh() {
	//	Unschedule quickPoll before sending refresh.  The next will be scheduled in
	//	parse.
	//	STRATEGY:	Use the refresh rate to kick-start the quickPolling every minute.
	//				This will allow for automatic error recovery if your wifi has
	//				intermittantissues.
	unschedule(quickPoll)
	sendCmd("0000001dd0f281f88bff9af7d5ef94b6d1b4c09fec95e68fe187e8caf08bf68bf6",
		"refresh")
}

def quickPoll() {
	//	Assumes socket is already established and still open.  If not, then we will
	//	end up in the timeout (error processing).
	def command = "0000001dd0f281f88bff9af7d5ef94b6d1b4c09fec95e68fe187e8caf08bf68bf6"
	state.lastCommand = [command: "${command}", method: "quickPoll"]
	runIn(2, rawSocketTimeout)
	interfaces.rawSocket.sendMessage(command)
}

def setPollFreq(interval = 0) {
	//	Will run refresh after setting.  Refresh will kick-start the polling if it is
	//	enabled.  Otherwise, it will refresh the device for continued operations.
	interval = interval.toInteger()
	if (interval !=0 && interval < 5) { interval = 5 }
	else if (interval > 40) { interval = 40 }
	if (interval != state.pollFreq) {
		state.pollFreq = interval
	}
	logInfo("setPollFreq: Polling interval set to ${interval} seconds")
	refresh()
}

def setLevel(percentage, transition = null) {
	logDebug("setLevel: level = ${percentage}")
	percentage = percentage.toInteger()
	if (percentage < 0) { percentage = 0 }
	if (percentage > 100) { percentage = 100 }
	percentage = percentage.toInteger()
	unschedule(quickPoll)
	def command = """{"system":{"set_relay_state":{"state":1}},""" +
			      """"smartlife.iot.dimmer":{"set_brightness":{"brightness":${percentage}}},""" +
			      """"system" :{"get_sysinfo" :{}}}"""
	def encrCmd = "000000" + Integer.toHexString(command.length()) + outputXOR(command)
	sendCmd(encrCmd, "setLevel")
	
}

def presetLevel(percentage) {
	logDebug("presetLevel: level = ${percentage}")
	percentage = percentage.toInteger()
	if (percentage < 0) { percentage = 0 }
	if (percentage > 100) { percentage = 100 }
	percentage = percentage.toInteger()
	unschedule(quickPoll)
	def command = """{"smartlife.iot.dimmer":{"set_brightness":{"brightness":${percentage}}},""" +
				  """"system" :{"get_sysinfo" :{}}}"""
	def encrCmd = "000000" + Integer.toHexString(command.length()) + outputXOR(command)
	sendCmd(encrCmd, "presetLevel")
}

private sendCmd(command, method) {
	//	Used only for all user commands and reset.
	//	Connects the interface and sends the command.  If already connected,
	//	will have no impact (aside from HE processing)
	//	The state.lastCommand is for message repeating on timeout.
	logDebug("sendCmd: source method = ${method}")
	state.lastCommand = [command: "${command}", method: "${method}"]
	runIn(2, rawSocketTimeout)
	interfaces.rawSocket.connect("${getDataValue("deviceIP")}", 9999, byteInterface: true)
	interfaces.rawSocket.sendMessage(command)
}

def parse(resp) {
	//	If a return is received, error count is set to 0 and rawSocketTimeout will not run.
	unschedule(rawSocketTimeout)
	state.errorCount = 0
	def status
	try { status = parseJson(inputXOR(resp.substring(8))).system.get_sysinfo }
	catch (e) { runIn(2, refresh) }
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
	//	The HS220 has a persistent rawSocket interface.  An if statement
	//	is required for plugs and switches since some of them
	//	(HS200) do not have a persistent interface.  For those, a
	//	connect command will be required for each poll.  I may change
	//	this function back to UDP messaging for the HS200 (less Hubitat
	//	processing.
	if (state.pollFreq > 0) {
		if (device.name == "HS200") { runIn(state.pollFreq, refresh) }
		else { runIn(state.pollFreq, quickPoll) }
	} else { interfaces.rawSocket.close()}
}

def socketStatus(message) {
	//	The Kasa devices do not properly follow the rawSocket reporting
	//	paradigm.  On the first message, they always send back a socket
	//	closed message (this is legacy code from the early devices like
	//	the HS220).  Therefore, I can not rely on a socket close message
	//	to trigger a reconnect.  I use the timeout instead.
	if (message == "receive error: Stream closed.") {
		logDebug("socketStatus: Socket Established")
//	} else if (message == "send error: Broken pipe (Write failed)") {
//
//		logInfo("socketStats: Write Error.  Attempting recovery.")
//		unschedule(rawSocketTimeout)
//		sendCmd(state.lastCommand.command, state.lastCommand.method)
	} else {
		//	Log any other error.  Handle any comms failure in timeout.
		logWarn("socketStatus = ${message}")
	}
}

def rawSocketTimeout() {
	//	Increments error count on each visit.  If error count >= 3, logs a warning
	//	message.  Three failures indicate a device or wifi problem, not a problem
	//	with this logic.
	state.errorCount += 1
	//	Provide a running total of visits to this method.  Used to determine
	//	severity of comms error processing.  Will probably go away in final version.
	state.errorsSinceReset += 1
	logDebug("rawSocketTimeout:  If persistant, notify developer")
	if (state.errorCount < 3) {
		//	Will make three retries on any communication.
		runIn(2, rawSocketTimeout)
		interfaces.rawSocket.connect("${getDataValue("deviceIP")}", 9999, byteInterface: true)
		interfaces.rawSocket.sendMessage(state.lastCommand.command)
	} else if (state.errorCount >=3 ) {
		//	Tells user on all subsequent failures.
		logWarn("rawSocketTimeout:  Repeat limit of 3 reached.  Command not sent.  Check your IP Address")
	}
}

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