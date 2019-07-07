/*
*/
metadata {
	definition (name: "ZZ TCP Socket Test",
    			namespace: "davegut",
                author: "Dave Gutheinz",
				importUrl: ""
			   ) {
		capability "Refresh"
	}
    preferences {
		input ("deviceIP", "text", title: "Device IP")
	}
}

def installed() {
	runIn(2, updated)
}
def updated() {
}
//	Commands being sent to the device
def refresh() {
	//	Truth code for UDP comms
	log.info "Sending control UDP Command"
	sendUdpCmd("""{"system":{"get_sysinfo":{}}}""")
	pauseExecution(3000)
	log.warn "Sending TCP Test Command"
	sendTcpCmd("""{"system":{"get_sysinfo":{}}}""")
}


//	=====	TCP TEST CODE	===================================================
private sendTcpCmd(command) {
	def message = tcpEncrypt(command)			//	See note in method tcpEncrypt
	
	interfaces.rawSocket.connect(deviceIP, 9999, 
								 byteInterface: true,
								 readDelay: 150)
	
//	pauseExecution(200)							//	Just in case timing is an issue
	interfaces.rawSocket.sendMessage(message)
	runIn(5, closeSocket)
}
def parse(response) {
	log.warn "TCP Response is ${response}"
	def encrResponse = parseLanMessage(response).payload
	def cmdResponse = parseJson(inputXOR(encrResponse))
	log.warn "TCP Return message is ${cmdResponse}"
}
def closeSocket() {
	interfaces.rawSocket.close()
}
def socketStatus(response) {
	log.warn "TCP Socket is closed"
}
//	===========================================================================


//	Baseline UDP Code
private sendUdpCmd(command) {
	def myHubAction = new hubitat.device.HubAction(
		udpEncrypt(command),
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${deviceIP}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 timeout: 2,
		 callback: udpParse])
	sendHubCommand(myHubAction)
}
def udpParse(response) {
	log.info "UDP Response is: ${response}"
	def encrResponse = parseLanMessage(response).payload
	def cmdResponse = parseJson(inputXOR(encrResponse))
	log.info "udpParse: cmdResponse = ${cmdResponse}"
}

//	Utility Methods
private tcpEncrypt(command) {
	//	The difference in the message formats (TCP vice UDP) is the TCP has
	//	a 4 hex character header.  This is total message length, so for all
	//	of these commands, the message is three hex nulls plus the message
	//	length in hex.  My uncertainity here is how the null characters are
	//	handled.  The node.js algorithm that works is:
/*
module.exports.encryptWithHeader = function (input, firstKey) {
  if (typeof firstKey === 'undefined') firstKey = 0xAB;
  var bufMsg = module.exports.encrypt(input, firstKey);
  var bufLength = new Buffer(4); // node v6: Buffer.alloc(4)
  bufLength.writeUInt32BE(input.length, 0);
  return Buffer.concat([bufLength, bufMsg], input.length + 4);
};
*/
//	The var bufMsg is the node.js equivalent of the udpEncrypt herein (which works).
	def str = ""
	def encrCmd = "00000000" + Integer.toHexString(command.length())
 	def key = 0xAB
	for (int i = 0; i < command.length(); i++) {
		str = (command.charAt(i) as byte) ^ key
		key = str
		encrCmd += Integer.toHexString(str)
	}
	return encrCmd
}
private udpEncrypt(command) {
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