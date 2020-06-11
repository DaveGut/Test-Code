/*	*/
metadata {
	definition (name: "Alternate Comms Testing",
    			namespace: "davegut",
				author: "Dave Gutheinz"
			   ) {
		command "test1"
		command "test2"
		command "test3"
	}
    preferences {
		input ("device_IP", "text", title: "Device IP", defaultValue: getDataValue("deviceIP"))
	}
}
def installed() {
	log.info "Installing .."
	updated()
}
def updated() {
	log.info "Updating .."
	unschedule()
	if (!device_IP) {
		logWarn("updated: Device IP is not set.")
		return
	}
	if (getDataValue("deviceIP") != device_IP.trim()) {
		updateDataValue("deviceIP", device_IP.trim())
	}
	pauseExecution(2000)
	runTests()
}


//	Tests
def runTests() {
	test1()
	runIn(15, test2)
	runIn(30, test3)
}
def test1() {
	log.debug "<b>Running Raw TCP Test</b>"
	sendTcpCmd("RAW TCP TEST")
	pauseExecution(2000)
	sendTcpCmd("RAW TCP TEST")
}
def test2() {
	log.debug "<b>Running Socket Test, Persistent Port Testing</b>"
	socketOpen()
	sendSocketCmd("Socket Test, Persistent Port")
	pauseExecution(500)
	socketClose()
	pauseExecution(1500)
	sendSocketCmd("Socket Test, Persistent Port")
	pauseExecution(500)
	socketClose()
	pauseExecution(1500)
	sendSocketCmd("Socket Test, Persistent Port")
	pauseExecution(500)
	socketClose()
}
def test3() {
	log.debug "<b>Running Socket Test, Closing Port Testing</b>"
	socketOpen()
	sendSocketCmd("Socket Test, Closing Port")
	pauseExecution(2000)
	socketOpen()
	sendSocketCmd("Socket Test, Closing Port")
	pauseExecution(2000)
	socketOpen()
	sendSocketCmd("Socket Test, Closing Port")
}
	

//	RawSocketTestCode
private socketOpen() {
	interfaces.rawSocket.connect("${getDataValue("deviceIP")}", 9999, byteInterface: true)
}
private socketClose() {
	interfaces.webSocket.close()
}
private sendSocketCmd(test) {
	log.info "<b>sendSocketCmd: test = ${test}</b>"
	interfaces.rawSocket.sendMessage(outputXOR("""{"system" :{"get_sysinfo" :{}}}"""))
}
def parse(message) {
	message = message.substring(0,40)
	log.trace "socketParse: $message"
}
def socketStatus(message) {
	log.trace "socketStatus $message"
}


//	RawLanMessage Comms
private sendTcpCmd(test) {
	log.info "<b>sendTcpCmd: test = ${test}</b>"
	sendHubCommand(new hubitat.device.HubAction(
		outputXOR("""{"system" :{"get_sysinfo" :{}}}"""),
		hubitat.device.Protocol.RAW_LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_RAW,
		 destinationAddress: "${getDataValue("deviceIP")}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 timeout: 5,
		 callback: "tcpParse"]
	))
}
def tcpParse(message) {
	log.trace parseLanMessage(message)
	message = parseLanMessage(message).payload.substring(0,40)
	log.trace "tcpParse: $message"
}


//	Encryption
private outputXOR(command) {
	def str = ""
//	def encrCmd = ""
	def encrCmd = "000000" + Integer.toHexString(command.length())
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