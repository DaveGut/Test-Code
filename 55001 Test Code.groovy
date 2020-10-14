/*
Test only.  Light the programming fire for WS Samsung Remote.
*/
import groovy.json.JsonOutput
import org.json.JSONObject
metadata {
	definition (name: "Ole Fashioned Remote",
				namespace: "davegut",
				author: "David Gutheinz"
			   ){
		command "tvAction", [[name: "Key name (i.e., MUTE)", type: "STRING"]]
	}
	preferences {
		input ("deviceIp", "text", title: "Device Ip")
	}
}
def installed() { }
def updated() {
	//	get initila data
	def data = getData()
	pauseExecution(2000)
	log.info "Updated: Sending Authenticate string"
	tvAction("AUTHENTICATE")
	pauseExecution(2000)
	log.info "Updated: Sending SOURCE key"
	tvAction("SOURCE")
}

def getData() {
	log.info "getData: Get Test Device Data</b>"
	def macAddress64
	httpGet([uri: "http://${deviceIp}:8001/api/v2/", timeout: 5]) { resp ->
		def tknSup = false
		def remAvail = true
		try {
			def isSupport = parseJson(resp.data.isSupport)
			remAvail = isSupport.remote_available
			tknSup = isSupport.tokenAuthSupport
		} catch (e) {}
		state.modelName = resp.data.device.modelName
		def macAddress = resp.data.device.wifiMac.replaceAll(":","")
		macAddress64 = macAddress.encodeAsBase64().toString()
	}
	
	log.info "getData: Creating Authentication Packet"
//	Data about the Hubitat Device
	def app = "Hubitat.SamsungRemote"
	def appLength = app.getBytes().size()
	def remote = "Hubitat".encodeAsBase64().toString()
	def remoteLength = remote.getBytes().size()
//	Data about the TV
	def ipAddress = deviceIp.encodeAsBase64().toString()
	def ipAddressLength = ipAddress.getBytes().size()
	def macAddressLength = macAddress64.getBytes().size()
// The Authentication Messages
	def authMsg = "${(char)0x64}${(char)0x00}${(char)ipAddressLength}${(char)0x00}${ipAddress}${(char)macAddressLength}${(char)0x00}${macAddress64}${(char)remoteLength}${(char)0x00}${remote}"
	def authMsgLength = authMsg.getBytes().size()
	def authPacket = "${(char)0x00}${(char)appLength}${(char)0x00}${app}${(char)authMsgLength}${(char)0x00}${authMsg}"
	state.authPacket = authPacket
}

private tvAction(key) {
log.trace device.deviceNetworkId
	log.info "tvAction ${key}"
	def authPacket = state.authPacket

	if (key == "AUTHENTICATE") {
		log.trace "authenticate"
	//	sendHubCommand(new physicalgraph.device.HubAction(authPacket, physicalgraph.device.Protocol.LAN, "${ipAddressHex}:D6D8"))
		sendHubCommand(new hubitat.device.HubAction(
			authPacket,
			hubitat.device.Protocol.LAN,[
				destinationAddress: "${deviceIp}:55000",
				timeout: 5]
		))
	} else {
		def tvApp = "Hubitat.${state.modelName}"
		def tvAppLength = tvApp.getBytes().size()
		def command = "KEY_${key}".encodeAsBase64().toString()
		def commandLength = command.getBytes().size()
		def actionMsg = "${(char)0x00}${(char)0x00}${(char)0x00}${(char)commandLength}${(char)0x00}${command}"
		def actionMsgLength = actionMsg.getBytes().size()
		def actionPacket = "${(char)0x00}${(char)tvAppLength}${(char)0x00}${tvApp}${(char)actionMsgLength}${(char)0x00}${actionMsg}"
		sendHubCommand(new hubitat.device.HubAction(
			authPacket + actionPacket,
			hubitat.device.Protocol.LAN,[
				destinationAddress: "${deviceIp}:55000",
				timeout: 5]
		))
	}
}

def parse(resp) {
	log.trace "at Parse"
}