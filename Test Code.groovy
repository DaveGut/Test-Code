/*
Test only.  Light the programming fire for WS Samsung Remote.
*/
import groovy.json.JsonOutput
metadata {
	definition (name: "Samsung Testing",
				namespace: "davegut",
				author: "David Gutheinz"
			   ){
		command "runTest"
	}
	preferences {
		input ("deviceIp", "text", title: "Device Ip")
	}
}
def installed() { }
def updated() { 
	state.token = "not set"
	state.name = "not set"
	state.name64 = "not set"
	log.warn "==========================================="
	log.info "<b>Get Test Device Data</b>"
	httpGet([uri: "http://${deviceIp}:8001/api/v2/", timeout: 5]) { resp ->
		def tknSup = false
		def remAvail = true
		try {
			def isSupport = parseJson(resp.data.isSupport)
			remAvail = isSupport.remote_available
			tknSup = isSupport.tokenAuthSupport
		} catch (e) {}
		def message = "<b>Device Data:</b> "
		message += "name: <b>${resp.data.name}</b> || "
		message += "model: <b>${resp.data.device.model}</b> || "
		message += "uri: <b>${resp.data.uri}</b> || "
		message += "remoteAvail: <b>${remAvail}</b> || "
		message += "tokenSupport: <b>${tknSup}</b>"
		log.info message
		state.name = resp.data.name
		state.name64 = resp.data.name.encodeAsBase64().toString()
	}
}
			   
def runTest() {
	log.warn "==========================================="
	log.info "<b>Simple Websocket Test, name = tv name</b>"
	def name = state.name64
	def uri = "ws://${deviceIp}:8001/api/v2/channels/samsung.remote.control?name=${name}"
	def keyPress = "KEY_SOURCE"
	def cmd = """{"method":"ms.remote.control","params":{"Cmd":"Click",""" +
		""""DataOfCmd":"${keyPress}","Option":"false","TypeOfRemote":"SendRemoteKey"}}"""
	webSocketConnect(uri)
	pauseExecution(500)
	sendMessage(cmd)
	pauseExecution(500)
	close()
	runIn(3, wsTest2)
}

def wsTest2() {
	log.warn "==========================================="
	log.info "<b>Simple Websocket Test, name = HubitatRemote</b>"
	def name = "HubitatRemote".encodeAsBase64().toString()
	def uri = "ws://${deviceIp}:8001/api/v2/channels/samsung.remote.control?name=${name}"
	def keyPress = "KEY_SOURCE"
	def cmd = """{"method":"ms.remote.control","params":{"Cmd":"Click",""" +
		""""DataOfCmd":"${keyPress}","Option":"false","TypeOfRemote":"SendRemoteKey"}}"""
	webSocketConnect(uri)
	pauseExecution(500)
	sendMessage(cmd)
	pauseExecution(500)
	close()
	runIn(3, wssTest1)
}

def wssTest1() {
	log.warn "==========================================="
	log.info "<b>SSL Websocket Test, name = tv name</b>"
	def name = state.name64
	def uri = "wss://${deviceIp}:8002/api/v2/channels/samsung.remote.control?name=${name}"
	def keyPress = "KEY_SOURCE"
	def cmd = """{"method":"ms.remote.control","params":{"Cmd":"Click",""" +
		""""DataOfCmd":"${keyPress}","Option":"false","TypeOfRemote":"SendRemoteKey"}}"""
	webSocketConnect(uri)
	pauseExecution(500)
	close()
	runIn(3, sendKey)
	runIn(7, wssTest2)
}

def wssTest2() {
	log.warn "==========================================="
	log.info "<b>SSL Websocket Test, name = HubitatRemote</b>"
	def name = "HubitatRemote".encodeAsBase64().toString()
	def uri = "wss://${deviceIp}:8002/api/v2/channels/samsung.remote.control?name=${name}"
	def keyPress = "KEY_SOURCE"
	def cmd = """{"method":"ms.remote.control","params":{"Cmd":"Click",""" +
		""""DataOfCmd":"${keyPress}","Option":"false","TypeOfRemote":"SendRemoteKey"}}"""
	webSocketConnect(uri)
	pauseExecution(500)
	close()
	runIn(3, sendKey)
}

def sendKey() {
	if (state.token == "not set") { return }
	log.warn "==========================================="
	log.info "<b>Token captured.  Attempting to send key./b>"
	def keyPress = "KEY_SOURCE"
	def command = """{"method":"ms.remote.control","params":{"Cmd":"Click",""" +
		""""DataOfCmd":"${keyPress}","Option":"false","TypeOfRemote":"SendRemoteKey"}}"""
	def token = state.token
	def uri = "https://${deviceIp}:8002/api/v2/channels/samsung.remote.control?token=${token}"
	webSocketConnect(uri)
	pauseExecution(500)
	sendMessage(command)
	runIn(3, close)
}

def webSocketConnect(String uri) {
	log.info "wsConnect Uri: <b>${uri}</b>"
	interfaces.webSocket.connect(uri)
}
def sendMessage(String message) {
	log.info "wsSendMessage: <b>${message}</b>"
	interfaces.webSocket.sendMessage(message)
}
def close() { interfaces.webSocket.close() }
def webSocketStatus(message) { }
def parse(message) {
	def respData = parseJson(message)
	log.info "Parse: event = <b>${respData.event}</b> || token = <b>${respData.data.token}</b>"
	log.info "Parse: <b>${respData}</b>"
	if (respData.data.token) { state.token = respData.data.token }
}