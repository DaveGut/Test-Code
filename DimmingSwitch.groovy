/*	Kasa Device Driver Series
Copyright Dave Gutheinz
License Information:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== 2020 History =====
02.28	New version 5.0.  Deprecated with this version
04.20	5.1.0	Update for Hubitat Program Manager
05,17	5.2.0	UDP Comms Update.  Deprecated with this version.
08.01	5.3.0	Major rewrite of LAN communications using rawSocket.  Other edit improvements.
				a.	implemented rawSocket for communications to address UPD errors and
					the issue that Hubitat UDP not supporting Kasa return lengths > 1024.
				b.	Use encrypted version of refresh / quickPoll commands
====================================================================================================*/
def driverVer() { return "5.3.0" }
def devVer() { return true }
def minPollInterval() { return 2 }

metadata {
	definition (name: "Kasa Dimming Switch",
    			namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/DeviceDrivers/DimmingSwitch.groovy"
			   ) {
		capability "Switch"
		capability "Actuator"
		capability "Refresh"
		capability "Switch Level"
		command "presetLevel",  ["NUMBER"]
		command "setPollFreq", [[
			name: "Poll Interval 0 = off, 5s - 30s range", 
			type: "NUMBER"]]
	}
	preferences {
		if (!getDataValue("applicationVersion")) {
			input ("device_IP", "text",
				   title: "Device IP",
				   defaultValue: getDataValue("deviceIP"))
		}
		input ("refresh_Rate", "enum",  
			   title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "10", "15", "30", "60", "180"], 
			   defaultValue: "60")
		input ("debug", "bool", 
			   title: "Enable debug logging", 
			   defaultValue: false)
		input ("descriptionText", "bool", 
			   title: "Enable description text logging", 
			   defaultValue: true)
		input ("pollTest", "bool", 
			   title: "Enable 5 minute quick poll trace logging", 
			   defaultValue: false)
		input ("device_IP", "text", 
			   title: "Device IP for TESTING COMMS", 
			   defaultValue: getDataValue("deviceIP"))
		if (devVer() == true) {
			input ("collectCommsStats", "bool", 
				   title: "Collect Comms Stats (developer request)", 
				   defaultValue: false)
		}
	}
}

def installed() {
	logInfo("Installing Device....")
	runIn(2, updated)
}

//	===== Updated and associated methods =====
def updated() {
	logInfo("Updating device preferences....")
	state.respLength = 0
	state.response = ""
	state.lastConnect = 0
	state.errorCount = 0
	if (!state.pollInterval) { state.pollInterval = "off" }

	//	Manual installation support.  Get IP and Plug Number
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

	//	Update various preferences.
	logInfo("updated:  ${setRefresh()}")
	if (debug == true) { 
		runIn(1800, debugLogOff)
		logInfo("updated: Debug logging enabled for 30 minutes.")
	} else {
		unschedule(debugLogOff)
		logInfo("updated: Debug logging is off.")
	}
	if (pollTest) { 
		runIn(300, pollTestOff)
		logInfo("updated: Poll Testing enabled for 5 minutes")
	} else {
		unschedule(pollTestOff)
		logInfo("updated: Poll Testing is off")
	}
	logInfo("updated: Description text logging is ${descriptionText}.")
	if (devVer() == true) {
		logInfo("updated:  ${setCommsStats()}")
	}

	//	Update driver data to current version.
	logInfo("updated: ${updateDriverData()}")

	runIn(5, refresh)
}

def updateDriverData() {
	//	Version 5.2 to 5.3 updates
	if (getDataValue("driverVersion") != driverVer()) {
		updateDataValue("driverVersion", driverVer())
		//	Change state.pollFreq to state.pollInterval
		if (state.pollFreq) {
			def interval = state.pollFreq
			state.remove("pollFreq")
			if (interval == 0) { interval = "off" }
			setPollFreq(interval)
		}
		//	No longer use state.lastCommand
		state.remove("lastCommand")
		return "Driver data updated to latest values."
	} else {
		return "Driver version and data already correct."
	}
}

def setRefresh() {
	if (state.pollInterval != "off") {
		return "Preference Refresh is disabled.  Using quickPoll"
	} else {
		switch(refresh_Rate) {
			case "1" : runEvery1Minute(refresh); break
			case "5" : runEvery5Minutes(refresh); break
			case "10" : runEvery10Minutes(refresh); break
			case "15" : runEvery15Minutes(refresh); break
			case "30" : runEvery30Minutes(refresh); break
			case "60" : runEvery1Hour(refresh); break
			case "180": runEvery3Hours(refresh); break
			default:
				runEvery1Hour(refresh); break
				return "refresh set to default of every 60 minute(s)."
		}
		return "Preference Refresh set for every ${refresh_Rate} minute(s)."
	}
}

def setCommsStats() {
	logDebug("setCommsStats")
	if (collectCommsStats) {
		def ratio
		if (!state.totalSendCmds || state.totalSendCmds == 0) {
			ratio = 0
		} else {
			ratio = state.totalRawSocketTimeouts.toInteger() / state.totalSendCmds.toInteger()
		}
		ratio = (100 * ratio).toInteger()
		updateDataValue("lastErrorRatio", "${ratio}")
		logInfo("resetCommsStats: lastErrorRatio = ${ratio}")
		state.totalParses = 0
		state.totalSendCmds = 0
		state.totalRawSocketTimeouts = 0
		schedule("0 03 0 * * ?", setCommsStats)
		return "Communications statistics collection enabled."
	} else {
		unschedule(resetCommsStats)
		state.remove("totalParses")
		state.remove("totalSendCmds")
		state.remove("totalRawSocketTimeouts")
		removeDataValue("lastErrorRatio")
		return "Communications statistics collection disabled."
	}
}

def debugLogOff() {
	device.updateSetting("debug", [type:"bool", value: false])
	logInfo("Debug logging is false.")
}

def pollTestOff() {
	device.updateSetting("pollTest", [type:"bool", value: false])
	logInfo("pollTestOff")
}


//	===== Device Command Methods =====
def on() {
	logDebug("on")
	sendOnOff(1)
}

def off() {
	logDebug("off")
	sendOnOff(0)
}

def sendOnOff(onOff) {
	def command = """{"system":{"set_relay_state":{"state":${onOff}},""" +
		""""get_sysinfo":{}}}"""
	sendCmd(outputXOR(command))
}	
	
def setLevel(percentage, transition = null) {
	logDebug("setLevel: level = ${percentage}")
	percentage = percentage.toInteger()
	if (percentage < 0) { percentage = 0 }
	if (percentage > 100) { percentage = 100 }
	percentage = percentage.toInteger()
//	def command = outputXOR("""{"system":{"set_relay_state":{"state":1}},""" +
//			      """"smartlife.iot.dimmer":{"set_brightness":{"brightness":${percentage}}},""" +
//			      """"system":{"get_sysinfo":{}}}""")
	def command = outputXOR("""{"smartlife.iot.dimmer":{"set_brightness":{"brightness":${percentage}}},""" +
			      """"system":{"set_relay_state":{"state":1}},"get_sysinfo":{}}}""")
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

def refresh() {
	logDebug("refresh")
	if (pollTest) { logTrace("Poll Test.  Time = ${now()}") }
	def command = "0000001dd0f281f88bff9af7d5ef94b6d1b4c09" +
		"fec95e68fe187e8caf08bf68bf6"
	sendCmd(command)
}

def setPollFreq(interval) {
	interval = interval.toInteger()
	if (interval != 0 && interval < minPollInterval()) {
		interval = minPollInterval()
	} else if (interval > 30) {
		interval = 30
	}
	if (interval == 0) {
		logInfo("setPollInterval: polling is off")
		state.pollInterval = "off"
		state.remove("WARNING")
		logInfo("setPollInterval: ${setRefresh()}")
	} else {
		state.pollInterval = interval
		schedule("*/${interval} * * * * ?", refresh)
		logWarn("setPollInterval: polling interval set to ${interval} seconds.\n" +
				"Quick Polling can have negative impact on the Hubitat Hub performance. " +
			    "If you encounter performance problems, try turning off quick polling.")
		state.WARNING = "<b>Quick Polling can have negative impact on the Hubitat " +
						"Hub and network performance.</b>  If you encounter performance " +
				    	"problems, <b>before contacting Hubitat support</b>, turn off quick " +
				    	"polling and check your sysem out."
		refresh()
	}
}

def setSysInfo(resp) {
	def status = resp.system.get_sysinfo
	if (getDataValue("devType") == "multiPlug") {
		status = status.children.find { it.id == getDataValue("plugId") }
	}
	logDebug("setSysInfo: status = ${status}")
	def onOff = "on"
	if (status.state == 0 || status.relay_state == 0) { onOff = "off" }
	if (onOff != device.currentValue("switch")) {
		sendEvent(name: "switch", value: onOff, type: "digital")
		logInfo("setSysInfo: switch: ${onOff}")
	}
	if (status.brightness != device.currentValue("level")) {
		sendEvent(name: "level", value: status.brightness, type: "digital")
		logInfo("setSysInfo: level: ${status.brightness}")
	}
}


//	===== distribute responses =====
def distResp(response) {
	logDebug("distResp: response length = ${response.length()}")
	if (response.length() == null) {
		logDebug("distResp: null return rejected.")
		return 
	}
	
	def resp
	try {
		resp = parseJson(inputXOR(response))
	} catch (e) {
		logWarn("distResp: Invalid or incomplete return.\nerror = ${e}")
		return
	}
	if (collectCommsStats) { state.totalParses += 1 }
	unschedule(rawSocketTimeout)
	state.errorCount = 0
	
	setSysInfo(resp)
}


//	===== Common Kasa Driver code =====
private sendCmd(command) {
	logDebug("sendCmd")
	if (collectCommsStats) { state.totalSendCmds += 1 }
	runIn(2, rawSocketTimeout, [data: command])
	if (now() - state.lastConnect > 35000) {
		logDebug("sendCmd: Connecting.....")
		try {
			interfaces.rawSocket.connect("${getDataValue("deviceIP")}", 
										 9999, byteInterface: true)
		} catch (error) {
			//	Catches no route to host error indicating device not at IP address.
			state.errorCount += 1
			if (state.errorCount == 5) {
				logWarn("SendCmd: IP Address not found on LAN.")
			} else {
				logWarn("SendCmd: IP Address not found on LAN. " +
						"count = ${state.errorCount}.\nRecommend checking " +
						"device IP and updating using the Kasa Integration App.")
			}
			return
		}
	}
	interfaces.rawSocket.sendMessage(command)
}

def socketStatus(message) {
	if (message == "receive error: Stream closed.") {
		logDebug("socketStatus: Socket Established")
	} else {
		logWarn("socketStatus = ${message}")
	}
}

def parse(response) {
	def respLength
	if (response.substring(0,4) == "0000") {
		def hexBytes = response.substring(0,8)
		respLength = 8 + 2 * hubitat.helper.HexUtils.hexStringToInt(hexBytes)
		if (response.length() == respLength) {
			distResp(response)
			state.lastConnect = now()
		} else {
			state.response = response
			state.respLength = respLength
		}
	} else {
		def resp = state.response
		resp = resp.concat(response)
		if (resp.length() == state.respLength) {
			state.response = ""
			state.respLength = 0
			state.lastConnect = now()
			distResp(resp)
		} else {
			state.response = resp
		}
	}
}

def rawSocketTimeout(command) {
	state.errorCount += 1
	if (collectCommsStats) { state.totalRawSocketTimeouts += 1 }
	if (state.errorCount <= 2) {
		logDebug("rawSocketTimeout: attempt = ${state.errorCount}")
		state.lastConnect = 0
		sendCmd(command)
	} else {
		logWarn("rawSocketTimeout: Retry on error limit exceeded. Error " +
				"count = ${state.errorCount}.  If persistant try SavePreferences.")
		if (state.errorCount > 10) {
			unschedule(quickPoll)
			logWarn("rawSocketTimeout: Quick Poll Disabled.")
		}
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


//	 ===== Logging =====
def logTrace(msg){ log.trace "${device.label} ${msg}" }

def logInfo(msg) {
	if (descriptionText == true) { log.info "${device.label} ${msg}" }
}

def logDebug(msg){
	if(debug == true) { log.debug "${device.label} ${msg}" }
}

def logWarn(msg){ log.warn "${device.label} ${msg}" }