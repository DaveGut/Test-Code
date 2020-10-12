/*
Test only.  Light the programming fire for WS Samsung Remote.
*/
import groovy.json.JsonOutput
metadata {
	definition (name: "Samsung Remote WS Test",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
		command "sendKey", ["string"]
		command "getData"
		command "getToken"
		command "volumeUp"
		command "volumeDown"
		command "mute"
		command "source"
		command "tools"
		command "menu"
	}
	preferences {
		input ("deviceIp", "text", title: "Device Ip")
	}
}
def installed() { }
def getData() {
	httpGet([uri: "http://${deviceIp}:8001/api/v2/", timeout: 5]) { resp ->
		logTrace("getData: name = ${resp.data.name}")
		updateDataValue("name64", resp.data.name.encodeAsBase64().toString())
	}
}
def getToken() {
	def name = getDataValue("name64")
	logTrace("getToken: name = ${name}")
	def uri = "https://${deviceIp}:8002/api/v2/channels/samsung.remote.control?name=${name}"
	webSocketConnect(uri)
	runIn(5, close)
}

def volumeUp() { sendKey("KEY_VOLUP") }
def volumeDown() { sendKey("KEY_VOLDOWN") }
def mute() { sendKey("KEY_MUTE") }
def source() { sendKey("KEY_SOURCE") }
def tools() { sendKey("KEY_TOOLS") }
def menu() { sendKey("KEY_MENU") }

def sendKey(String keyPress) {
	def command = [method: "ms.remote.control", params: [
		Cmd: "Click", DataOfCmd: keyPress, Option: "false", TypeOfRemote: "SendRemoteKey"]]
	command = JsonOutput.toJson(command)
	def token = getDataValue("token")
//	def name = getDataValue("name64")
	def name = "LInux UPnP/1.0 Hubitat"
	name = name.encodeAsBase64().toString()
	logTrace "sendKey: key = ${keyPress}, token = ${token}"
	def uri = "https://${deviceIp}:8002/api/v2/channels/samsung.remote.control?token=${token}"
//	def uri = "https://${deviceIp}:8002/api/v2/channels/samsung.remote.control?name=${name}&token=${token}"
	webSocketConnect(uri)
	pauseExecution(200)
	sendMessage(command)
	runIn(3, close)
}

def webSocketConnect(String url) { interfaces.webSocket.connect(url) }
def sendMessage(String message) { interfaces.webSocket.sendMessage(message) }
def close() { interfaces.webSocket.close() }
def webSocketStatus(message) { logInfo "webSocketStatus: $message" }
def parse(message) {
	def respData = parseJson(message)
	if (respData.event == "ms.channel.connect" && respData.token) { 
		logTrace("parse: token = ${respData.token}")
		updateDataValue("token", respData.token)
	} else {
		logInfo("parse: ${message}")
	}
}

def logTrace(msg) { log.trace "TV Test || ${msg}" }
def logInfo(msg) { log.info "TV Test || ${msg}" }
def logDebug(msg) { log.debug "TV Test || ${msg}" }