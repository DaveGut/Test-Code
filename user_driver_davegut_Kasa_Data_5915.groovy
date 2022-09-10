/*	Kasa Comms Test
		Copyright Dave Gutheinz
*/
metadata {
	definition (name: "Kasa Data",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: ""
			   ) {
		capability "Refresh"
	}
	preferences {
		input ("deviceIp", "text", title: "Kasa Device IP", defaultValue: "")
	}
}
def installed() {
}
def updated() {
	if (!deviceIp) {
		log.warn "Enter the Device Ip in preferences and Save Preferences"
	} else {
		refresh()
	}
}

def refresh() {
	sendUdpCmd()
	runIn(5, sendRsCmd)
}

//	=====	UDP Test	=====
def sendUdpCmd() {
	def myHubAction = new hubitat.device.HubAction(
		outputXOR("""{"system":{"get_sysinfo":{}}}"""),
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${deviceIp}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 parseWarning: true,
		 timeout: 5,
		 ignoreResponse: false,
		 callback: parseUdp])
	try {
		sendHubCommand(myHubAction)
	} catch (e) {
		log.warn "sendUDPCmd:  [deviceIp: ${deviceIp}, error: ${e}]"
	}
}
def parseUdp(message) {
	def resp = parseLanMessage(message)
	if (resp.type == "LAN_TYPE_UDPCLIENT") {
		def clearResp = inputXOR(resp.payload)
		log.info "parseUdp: [deviceIp: ${deviceIp}, resp: ${clearResp}]"
	} else {
		log.warn "parseUdp: [deviceIp: ${deviceIp}, status: failed, data: ${resp}]"
	}
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
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)
		XORtemp = nextKey ^ key
		key = nextKey
		cmdResponse += new String(XORtemp)
	}
	return cmdResponse
}

//	=====	RS Test	=====
def sendRsCmd() {
	state.response = ""
	try {
		interfaces.rawSocket.connect(deviceIp, 9999, byteInterface: true)
	} catch (error) {
		log.warn "sendRsCmd: [deviceIp: ${deviceIp}, error: Unable to connect]"
	}
	interfaces.rawSocket.sendMessage(outputXorTcp("""{"system":{"get_sysinfo":{}}}"""))
	runIn(2, close)
}
def close() { interfaces.rawSocket.close() }
def socketStatus(message) {
log.trace message
	if (message != "receive error: Stream closed.") {
		log.info "socketStatus: Socket Established"
	} else {
		log.warn "socketStatus = ${message}"
	}
}
def parse(message) {
	def response = state.response.concat(message)
	state.response = response
	runInMillis(50, extractRsResp, [data: response])
}
def extractRsResp(response) {
	state.response = ""
	if (response.length() == null) { return }
	try {
		def cmdResp = parseJson(inputXorTcp(response))
		log.info "extractRsResp: [deviceIp: ${deviceIp}, resp: ${cmdResp}]"
	} catch (e) {
		log.warn "extractRsResp: [deviceIp: ${deviceIp}, error: ${e}]"
	}
}


//	=====	WS and TCP Encoding	=====
private outputXorTcp(command) {
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
private inputXorTcp(resp) {
	String[] strBytes = resp.substring(8).split("(?<=\\G.{2})")
	def cmdResponse = ""
	def key = 0xAB
	def nextKey
	byte[] XORtemp
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommunications, line 280
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)
		XORtemp = nextKey ^ key
		key = nextKey
		cmdResponse += new String(XORtemp)
	}
	return cmdResponse
}
