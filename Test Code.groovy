/*
KEY_VOLUP; KEY_VOLDOWN; KEY_MUTE; KEY_SOURCE; KEY_TOOLS; KEY_MENU
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
	}
	preferences {
		input ("deviceIp", "text", title: "Device Ip")
		input ("devicePort", "text", title: "Capability Port", defaultValue: "8001")
	}
}
def installed() { updated() }

def updated() {
	updateDataValue("token", "12345678") 
}

def getData() {
	//	Gets the name and uri, update device data.
	def uri = "http://${deviceIp}:8001/api/v2/"
	httpGet([uri: uri, timeout: 5]) { resp ->
		logTrace("getData: name = ${resp.data.name} // uri ${resp.data.uri}")
		updateDataValue("name", resp.data.name)
		updateDataValue("name64", resp.data.name.encodeAsBase64().toString())
	}
}

def getToken() {
	logTrace("getToken")
	//	Gets the token and sets.
	def name = getDataValue("name64")
	def uri = "http://${deviceIp}:${devicePort}/api/v2/channels/samsung.remote.control?name=${name}"
	webSocketConnect(uri)
	runIn(5, close)
}

def sendKey(String keyPress) {
	log.trace "test: $keyPress"
	def command = [
		method: "ms.remote.control",
		params: [
			Cmd: "Click",
			DataOfCmd: keyPress,
			Option: "false",
			TypeOfRemote: "SendRemoteKey"
			]
		]
	command = JsonOutput.toJson(command)
	def cmdUri = getDataValue("cmdUri")
	def token = getDataValue("token")
	def uri = "http://${deviceIp}:${devicePort}/api/v2/channels/samsung.remote.control?token=${token}"
	webSocketConnect(url)
	pauseExecution(200)
	sendMessage(command)
	runIn(3, close)
}

def webSocketConnect(String url) { interfaces.webSocket.connect(url) }

def sendMessage(String message) { interfaces.webSocket.sendMessage(message) }

def close() { interfaces.webSocket.close() }

def webSocketStatus(message) { log.debug "webSocketStatus: $message" }

def parse(message) {
	def respData = parseJson(message)
	if (respData.event == "ms.channel.connect" && respData.token) { 
		log.trace respData.token
		updateDataValue("token", respData.token)
	} else {
		logInfo("parse: ${message}")
	}
}

def logTrace(msg) { log.trace "${msg}" }

def logInfo(msg) { log.info "${msg}" }