/*	Kasa Device Communications Testing
		Copyright Dave Gutheinz
This driver provides testing of user-selected communications for Kasa Plugs, Switches, and Bulbs.
License Information:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===================================================================================================*/
import groovy.json.JsonSlurper
def driverVer() { return "1.0" }
metadata {
	definition (name: "A Kasa Comms Test",
				namespace: "davegut",
				author: "Dave Gutheinz") {
		capability "Polling"
		capability "Switch"
	}
	preferences {
		input ("deviceIp", "string",
			   title: "Device IP")
		def opts = ["LAN": "UDP on LAN", "CLOUD": "Kasa CLOUD", "rawSOCKET": "raw socket on LAN"]
		input ("commsType", "enum",
			   title: "Communications Type",
			   options: ["LAN": "UDP on LAN", "CLOUD": "Kasa CLOUD", "rawSOCKET": "raw socket on LAN"],
			   defaultValue: "LAN")
		if (commsType == "CLOUD") {
			input ("deviceId", "string",
				   title: "Device ID from App Installed Device")
			input ("token", "string",
				   title: "Kasa Token from Kasa Integration App")
		}
		input ("debug", "bool",
			   title: "5 minutes of debug logging", 
			   defaultValue: false)
	}
		   
}
		
def installed() { updated() }
def updated() {
	if (!deviceIp) {
		logWarn("updated: Device IP is not set.")
		return
	} else if (commsType == "CLOUD") {
		if (deviceId == null || token == null) {
			lohWarn("updated: Cloud Comms: Device Id or Token not set.")
		}
	}
	logInfo(deviceIp)
	logInfo(commsType)
	logInfo(debug)
	if (debug) { runIn(300, debugOff) }
	state.tests = 0
	state.successes = 0
}

def on() {
	unschedule()
	state.tests = 0
	state.successes = 0
	logInfo("Starting Comms Test on IP ${deviceIp} using ${commsType}")
	poll()
	runEvery1Minute(poll)
}
def off() {
	unschedule()
	interfaces.rawSocket.close()
	logInfo("Stoping Comms Test. Tests = ${state.tests}, Successes = ${state.successes}")
}
def poll() {
	state.tests += 1
	sendCmd("""{"system":{"get_sysinfo":{}}}""")
}

def sendCmd(command) {
	if (commsType == "LAN") {
		sendLanCmd(command)
	} else if (commsType == "CLOUD"){
		sendKasaCmd(command)
	} else if (commsType == "rawSOCKET") {
		sendTcpCmd(command)
	} else {
		logWarn("sendCmd: attribute connection not set.")
	}
}

//	===== LAN Communications =====
def sendLanCmd(command) {
	logDebug("sendLanCmd: ${deviceIp}")
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command),
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${deviceIp}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 parseWarning: true,
		 timeout: 5,
		 callback: parseUdp])
	try {
		sendHubCommand(myHubAction)
	} catch (e) {
		logWarn("sendLanCmd: LAN Error = ${e}")
	}
}
def parseUdp(message) {
	def resp = parseLanMessage(message)
	if (resp.type == "LAN_TYPE_UDPCLIENT") {
		logDebug("parseUdp: Success")
		state.successes += 1
	} else {
		logDebug("parse: LAN Error = ${resp.type}")
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

//	===== CLOUD Communications=====
def sendKasaCmd(command) {
	logDebug("sendKasaCmd: ${deviceId}, ${token}")
	def cmdResponse = ""
	def cmdBody = [
		method: "passthrough",
		params: [
			deviceId: deviceId,
			requestData: "${command}"
		]
	]
	def sendCloudCmdParams = [
		uri: "${parent.kasaCloudUrl}/?token=${token}",
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01'],
		timeout: 5,
		body : new groovy.json.JsonBuilder(cmdBody).toString()
	]
	try {
		httpPostJson(sendCloudCmdParams) {resp ->
			if (resp.status == 200 && resp.data.error_code == 0) {
				logDebug("sendKasaCmd: Success")
				state.successes += 1
			} else {
				def errMsg = ""
				logWarn("sendKasaCmd: CLOUD Error = ${resp.data}")
			}
		}
	} catch (e) {
		def errMsg = "CLOUD Error = ${e}"
		logWarn("sendKasaCmd: CLOUD Error = ${e}")
	}
}

//	===== Raw Socket Communications
private sendTcpCmd(command) {
//	return
	logDebug("sendTcpCmd: ${deviceIp}")
	try {
		interfaces.rawSocket.connect("${deviceIp}", 
									 9999, byteInterface: true)
	} catch (error) {
		logWarn("SendTcpCmd: Unable to connect to device at ${deviceIp}. " +
				 "Error = ${error}")
	}
	interfaces.rawSocket.sendMessage(outputXorTcp(command))
}
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
def socketStatus(message) {
	logDebug("socketStatus: ${message}")
}
def parse(message) {
//	return
	def respLength
	if (message.length() > 8 && message.substring(0,4) == "0000") {
		logDebug("parse: Success")
		state.successes += 1
	}
}

//	===== Utility Methods =====
def logInfo(msg) {
	log.info "[${device.label}]| ${msg}"
}
def logDebug(msg){
	if(debug == true) {
		log.debug "[${device.label}]| ${msg}"
	}
}
def debugOff() {
	device.updateSetting("debug", [type:"bool", value: false])
	logInfo("debugLogOff: Debug logging is off.")
}
def logWarn(msg){
	log.warn "[${device.label}]| ${msg}"
}
