def driverVer() { return "RS-TEST" }

metadata {
	definition (name: "A rawSocket Kasa",
    			namespace: "davegut",
				author: "Dave Gutheinz"
			   ) {
		capability "Switch"
		capability "Actuator"
		capability "Refresh"
	}
	preferences {
		input ("device_IP", "text",
				   title: "Device IP",
				   defaultValue: getDataValue("deviceIP"))
	}
}

def installed() {
	logInfo("Installing Device....")
	runIn(2, updated)
}

//	===== Updated and associated methods =====
def updated() {
	if (!device_IP) {
		logWarn("updated: Device IP is not set.")
		return
	}
	if (getDataValue("deviceIP") != device_IP.trim()) {
		updateDataValue("deviceIP", device_IP.trim())
		logInfo("updated: Device IP set to ${device_IP.trim()}")
	}
}

//	===== Device Command Methods =====
def on() {
	logDebug("on")
	def command = outputXOR("""{"system":{"set_relay_state":{"state":1},""" +
							""""get_sysinfo":{}}}""")
	sendCmd(command)
}

def off() {
	logDebug("off")
	def command = outputXOR("""{"system":{"set_relay_state":{"state":0},""" +
							""""get_sysinfo":{}}}""")
	sendCmd(command)
}

def refresh() {
	logDebug("refresh")
	if (pollTest) { logTrace("Poll Test.  Time = ${now()}") }
	def command = "0000001dd0f281f88bff9af7d5ef94b6d1b4c09" +
		"fec95e68fe187e8caf08bf68bf6"
	sendCmd(command)
}

def setSysInfo(resp) {
	def status = resp.system.get_sysinfo
	logDebug("setSysInfo: status = ${status}")
	def onOff = "on"
	if (status.relay_state == 0) { onOff = "off" }
	if (onOff != device.currentValue("switch")) {
		sendEvent(name: "switch", value: onOff, type: "digital")
		logInfo("setSysInfo: switch: ${onOff}")
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
	setSysInfo(resp)
}

//	===== Common Kasa Driver code =====
private sendCmd(command) {
	logDebug("sendCmd: ${command}")
	try {
		interfaces.rawSocket.connect("${getDataValue("deviceIP")}", 
									 9999, byteInterface: true)
	} catch (error) {
		logDebug("SendCmd: Unable to connect to device at ${getDataValue("deviceIP")}. " +
				 "Error = ${error}")
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

def parse(message) {
	def respLength
	if (message.length() > 8 && message.substring(0,4) == "0000") {
		def hexBytes = message.substring(0,8)
		respLength = 8 + 2 * hubitat.helper.HexUtils.hexStringToInt(hexBytes)
		if (message.length() == respLength) {
			distResp(message)
		} else {
			state.response = message
			state.respLength = respLength
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