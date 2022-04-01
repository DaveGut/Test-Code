/*	===== HUBITAT INTEGRATION VERSION ===============
Hubitat - WS Samsung TV Remote
===========================================================================================*/
def driverVer() { return "1.0.0" }
//	Poll Timeout in seconds for user changes
def commsTimeout = 5
import groovy.json.JsonOutput

metadata {
	definition (name: "Samsung TV StbyTest",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
		command "sendKey", ["string"]
		attribute "wsConnected", "BOOL"
		
		command "muteTest"
		command "standbyTest1"
		command "standbyTest2"
		command "standbyTest3"
		command "wakeTest1"
		command "wakeTest2"
		command "wakeTest3"
	}
	preferences {
		input ("deviceIp", "text", title: "Samsung TV Ip", defaultValue: "")
		input ("tvWsToken", "text", 
			   title: "The WS Token for your TV (from previous Installation)",
			   defaultValue: state.token)
		input ("debugLog", "bool",  
			   title: "Enable debug logging for 30 minutes", defaultValue: false)
		input ("infoLog", "bool",  
			   title: "Enable description text logging", defaultValue: true)
	}
}

//	===== Installation, setup and update =====
def installed() {
	runIn(1, updated)
}

def updated() {
	logInfo("updated")
	unschedule()
	def updateData = [:]
	def status = "OK"
	def statusReason
	def deviceData
	if (deviceIp) {
		//	Get onOff status for use in setup
		updateData << [deviceIp: "deviceIp"]
		if (deviceIp != deviceIp.trim()) {
			deviceIp = deviceIp.trim()
			device.updateSetting("deviceIp", [type:"text", value: deviceIp])	
		}
		deviceData = getDeviceData()
	} else {
		logInfo("updated: [status: failed, statusReason: No device IP]")
		return
	}
	
	if (!state.token) {
		state.token = tvWsToken
		updateData << [tvToken: tvWsToken]
	} else {
		updateData << [tvToken: state.token]
	}
	if (debug) { runIn(1800, debugOff) }
	updateData << [debugLog: debugLog, infoLog: infoLog]
	updateData << [driver: versionUpdate()]
	def updateStatus = [:]
	updateStatus << [status: status]
	if (statusReason != "") {
		updateStatus << [statusReason: statusReason]
	}
	updateStatus << [updateData: updateData, deviceData: deviceData]
	logInfo("updated: ${updateStatus}")
}

def getDeviceData() {
	def validDeviceData = false
	try{
		httpGet([uri: "http://${deviceIp}:8001/api/v2/", timeout: 5]) { resp ->
			def wifiMac = resp.data.device.wifiMac
			updateDataValue("deviceMac", wifiMac)
			def alternateWolMac = wifiMac.replaceAll(":", "").toUpperCase()
			updateDataValue("alternateWolMac", alternateWolMac)
			def newDni = getMACFromIP(deviceIp)
			def modelYear = "20" + resp.data.device.model[0..1]
			updateDataValue("modelYear", modelYear)
			def frameTv = "false"
			if (resp.data.device.FrameTVSupport) {
				frameTv = resp.data.device.FrameTVSupport
			}
			updateDataValue("frameTv", frameTv)
			if (resp.data.device.TokenAuthSupport) {
				tokenSupport = resp.data.device.TokenAuthSupport
			}
			def uuid = resp.data.device.duid.substring(5)
			updateDataValue("uuid", uuid)
			updateDataValue("tokenSupport", tokenSupport)
			logInfo("getDeviceData: year = $modelYear, frameTv = $frameTv, tokenSupport = $tokenSupport")
		}
		logInfo("getDeviceData: Updated Device Data.")
		state.remove("driverError")
		validDeviceData = true
	} catch (error) {
		logWarn("getDeviceData: Failed.  Error = ${error}")
		state.driverError = "<b>getDeviceData failed. Rerun Save Preferences.</b>"
	}
	return validDeviceData
}

def versionUpdate() {
	if (!getDataValue("driverVersion") || getDataValue("driverVersion") != driverVer()) {
		updateDataValue("driverVersion", driverVer())
	}
	return driverVer()
}

def muteTest() {
	key = "KEY_MUTE"
	def data = [method:"ms.remote.control",
				params:[Cmd:"Click",
						DataOfCmd:"${key}",
						Option: false,
						TypeOfRemote:"SendRemoteKey"]]
	sendMessage("remote", JsonOutput.toJson(data) )
}

def standbyTest1() {
	logTrace("<b>standbyTest1</b>: Setting standby using value = on and request = go_to_standby\n\n")
	def data = [value:"on",
				request:"go_to_standby",
				id: "${getDataValue("uuid")}"]
	data = JsonOutput.toJson(data)
	artModeCmd(data)
}
	
def standbyTest2() {
	logTrace("<b>standbyTest2</b>: Setting standby using value = NULL and request = go_to_standby\n\n")
	def data = [value: "",
				request:"go_to_standby",
				id: "${getDataValue("uuid")}"]
	data = JsonOutput.toJson(data)
	artModeCmd(data)
}

def standbyTest3() {
	logTrace("<b>standbyTest3</b>: Setting standby WITHOUT value and request = go_to_standby\n\n")
	def data = [request:"go_to_standby",
				id: "${getDataValue("uuid")}"]
	data = JsonOutput.toJson(data)
	artModeCmd(data)
}

def wakeTest1() {
	logTrace("<b>wakeTest1</b>: Setting standby using value = on and request = wakeup\n\n")
	def data = [value:"on",
				request:"wakeup",
				id: "${getDataValue("uuid")}"]
	data = JsonOutput.toJson(data)
	artModeCmd(data)
}
	
def wakeTest2() {
	logTrace("<b>wakeTest2</b>: Setting standby using value = NULL and request = wakeup\n\n")
	def data = [value: "",
				request:"wakeup",
				id: "${getDataValue("uuid")}"]
	data = JsonOutput.toJson(data)
	artModeCmd(data)
}
	
def wakeTest3() {
	logTrace("<b>wakeTest3</b>: Setting standby WITHOUT value and request = wakeup\n\n")
	def data = [request:"wakeup",
				id: "${getDataValue("uuid")}"]
	data = JsonOutput.toJson(data)
	artModeCmd(data)
}

def artModeCmd(data) {
	def cmdData = [method:"ms.channel.emit",
				   params:[data:"${data}",
						   to:"host",
						   event:"art_app_request"]]
	cmdData = JsonOutput.toJson(cmdData)
	sendMessage("frameArt", cmdData)	//	send command, connect is automatic.
}

// ===============================================
//	Communications Interfaces
//	==============================================
//	===== WebSocket Interace
def connect(funct) {
	def samsungMeth = "samsung.remote.control"
	if (funct == "frameArt") {
		samsungMeth = "com.samsung.art-app"
	}
	logTrace("connect: function = ${funct}")
	def url
	def name = "SHViaXRhdCBTYW1zdW5nIFJlbW90ZQ=="
	if (getDataValue("tokenSupport") == "true") {
		url = "wss://${deviceIp}:8002/api/v2/channels/${samsungMeth}?name=${name}&token=${state.token}"
	} else {
		url = "ws://${deviceIp}:8001/api/v2/channels/${samsungMeth}?name=${name}"
	}
log.trace url
	state.currentFunction = funct
	interfaces.webSocket.connect(url, ignoreSSLIssues: true)
}

def sendMessage(funct, data) {
	connect(funct)
	pauseExecution(300)
	logTrace("sendMessage: [function: ${funct}, data: ${data}]")
	interfaces.webSocket.sendMessage(data)
	runIn(2, close)
}

def close() { interfaces.webSocket.close() }

def webSocketStatus(message) {
	logTrace("webSocketStatus: ${message}")
}

def parse(resp) {
	resp = parseJson(resp)
	logTrace("parse: ${resp}")
}

//	===== Logging=====
def logTrace(msg){
	log.trace "[${device.label}, ${driverVer()}]:: ${msg}"
}

def logInfo(msg) { 
	if (infoLog == true) {
		log.info "[${device.label}, ${driverVer()}]:: ${msg}"
	}
}

def debugOff() {
	device.updateSetting("debugLog", [type:"bool", value: false])
	logInfo("Debug logging is false.")
}

def logDebug(msg) {
	if (debugLog == true) {
		log.debug "[${device.label}, ${driverVer()}]:: ${msg}"
	}
}

def logWarn(msg) { log.warn "[${device.label}, ${driverVer()}]:: ${msg}" }

//	End-of-File