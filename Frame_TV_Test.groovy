/*	===== HUBITAT INTEGRATION VERSION =====================================================
Hubitat - Samsung TV Remote Driver
		Copyright 2022 Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== 2022 Version 3.2 ==================================================================*/
def driverVer() { return "Frame_Test_1" }
import groovy.json.JsonOutput

metadata {
	definition (name: "Frame TV Test",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungTvRemote/SamsungTVRemote.groovy"
			   ){
		capability "Switch"
		command "tvToArtMode"
		command "artModeToMediaHome"
//		command "artModeOn"
//		command "artModeOff"
		
		command "home"
		command "menu"
		command "channelUp"
		command "channelDown"
		command "ambientMode"
		command "ambientModeExit"

		
	}
	preferences {
		input ("deviceIp", "text", title: "Samsung TV Ip", defaultValue: "")
		if (deviceIp) {
			input ("tvPwrOnMode", "enum", title: "TV Startup Display", 
				   options: ["ART_MODE", "Ambient", "none"], defaultValue: "ART_MODE")
		}
	}
}

//	===== Installation, setup and update =====
def installed() {
	state.token = "12345678"
	def tokenSupport = "false"
	runIn(1, updated)
}

def updated() {
	unschedule()
	def updStatus = [:]
	if (!deviceIp) {
		logWarn("\n\n\t\t<b>Enter the deviceIp and Save Preferences</b>\n\n")
		updStatus << [status: "ERROR", data: "Device IP not set."]
	} else {
		updStatus << [getDeviceData: getDeviceData()]
		state.offCount = 0
	}

	if (updStatus.toString().contains("ERROR")) {
		logWarn("updated: ${updStatus}")
	} else {
		logInfo("updated: ${updStatus}")
	}
}
						
def getDeviceData() {
	def respData = [:]
//	if (getDataValue("uuid")) {
//		respData << [status: "already run"]
//	} else {
		try{
			httpGet([uri: "http://${deviceIp}:8001/api/v2/", timeout: 5]) { resp ->
				def wifiMac = resp.data.device.wifiMac
				updateDataValue("deviceMac", wifiMac)
				def alternateWolMac = wifiMac.replaceAll(":", "").toUpperCase()
				updateDataValue("alternateWolMac", alternateWolMac)
				device.setDeviceNetworkId(alternateWolMac)
				def modelYear = "20" + resp.data.device.model[0..1]
				updateDataValue("modelYear", modelYear)
				def frameTv = "false"
				if (resp.data.device.FrameTVSupport) {
					frameTv = resp.data.device.FrameTVSupport
					sendEvent(name: "artModeStatus", value: "notFrameTV")
					respData << [artModeStatus: "notFrameTV"]
				}
				updateDataValue("frameTv", frameTv)
				if (resp.data.device.TokenAuthSupport) {
					tokenSupport = resp.data.device.TokenAuthSupport
					updateDataValue("tokenSupport", tokenSupport)
				}
				def os = "${resp.data.device.OS} V${resp.data.version}"
				updateDataValue("osVersion", os)
				
				def uuid = resp.data.device.duid.substring(5)
				updateDataValue("uuid", uuid)
				respData << [status: "OK", dni: alternateWolMac, modelYear: modelYear,
							 frameTv: frameTv, tokenSupport: tokenSupport]
			}
		} catch (error) {
			respData << [status: "ERROR", reason: error]
		}
//	}
	return respData
}

def on() {
	logInfo("on")
	onCmd()
	pauseExecution(3000)
	onCmd()
	setPowerOnMode()
}
def onCmd() {
	def wolMac = getDataValue("alternateWolMac")
	def cmd = "FFFFFFFFFFFF$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac$wolMac"
	wol = new hubitat.device.HubAction(cmd,
									   hubitat.device.Protocol.LAN,
									   [type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
										destinationAddress: "255.255.255.255:7",
										encoding: hubitat.device.HubAction.Encoding.HEX_STRING])
	sendHubCommand(wol)
}
def setPowerOnMode() {
	connect("remote")
	if(tvPwrOnMode == "ART_MODE" && getDataValue("frameTv") == "true") {
		sendKey("TV")
		tvToArtMode()
	} else if (tvPwrOnMode == "Ambient") {
		sendKey("TV")
		pauseExecution(1000)
		ambientMode()
	}
}
	  
def off() {
	logInfo("off")
//	if (tvPwrOnMode = "ART_MODE" || tvPwrOnMode == "Ambient") {
//		sendKey("TV")
//	}
	if (getDataValue("frameTv") == "false") { sendKey("POWER") }
	else {
		sendKey("POWER", "Press")
		pauseExecution(3000)
		sendKey("POWER", "Release")
	}
}

def tvToArtMode() {
	logInfo("TV_TO_ART_MODE")
	sendKey("POWER")
	runIn(3, getArtModeStatus)
}

def artModeToMediaHome() {
	logInfo("ART_MODE_TO_MEDIA_HOME")
	sendKey("POWER")
	runIn(3, getArtModeStatus)
}

def artModeOn() {
	logInfo("ART_MODE_ON")
	artMode("on")
	runIn(3, getArtModeStatus)
}

def artModeOff() {
	logInfo("aART_MODE_OFF")
	artMode("off")
	runIn(3, getArtModeStatus)
}

def artMode(onOff) {
	def data = [value:"${onOff}",
				request:"set_artmode_status",
				id: "${getDataValue("uuid")}"]
	data = JsonOutput.toJson(data)
	def cmdData = [method:"ms.channel.emit",
				   params:[data:"${data}",
						   to:"host",
						   event:"art_app_request"]]
	cmdData = JsonOutput.toJson(cmdData)
	sendMessage("frameArt", cmdData)
}
def getArtModeStatus() {
	def data = [request:"get_artmode_status",
				id: "${getDataValue("uuid")}"]
	data = JsonOutput.toJson(data)
	def cmdData = [method:"ms.channel.emit",
				   params:[data:"${data}",
						   to:"host",
						   event:"art_app_request"]]
	cmdData = JsonOutput.toJson(cmdData)
	sendMessage("frameArt", cmdData)
}

def ambientMode() {
	logInfo("AMBIENT_MODE")
	sendKey("AMBIENT")
}
def ambientModeExit() {
	sendKey("HOME")
	pauseExecution(2000)
	sendKey("HOME")
}

def home() { sendKey("HOME") }
def menu() { sendKey("MENU") }
def channelUp() { sendKey("CHUP") }
def channelDown() { sendKey("CHDOWN") }

def sendKey(key, cmd = "Click") {
	key = "KEY_${key.toUpperCase()}"
	def data = [method:"ms.remote.control",
				params:[Cmd:"${cmd}",
						DataOfCmd:"${key}",
						TypeOfRemote:"SendRemoteKey"]]
	sendMessage("remote", JsonOutput.toJson(data) )
}

def connect(funct) {
	logInfo("CONNECT: [FUNCTION: ${funct}]")
	def url
	def name = "SHViaXRhdCBTYW1zdW5nIFJlbW90ZQ=="
	if (getDataValue("tokenSupport") == "true") {
		if (funct == "remote") {
			url = "wss://${deviceIp}:8002/api/v2/channels/samsung.remote.control?name=${name}&token=${state.token}"
		} else if (funct == "frameArt") {
			url = "wss://${deviceIp}:8002/api/v2/channels/com.samsung.art-app?name=${name}&token=${state.token}"
		} else if (funct == "application") {
			url = "ws://${deviceIp}:8001/api/v2/applications?name=${name}"
		} else {
			logWarn("connect: Invalid Function = ${funct}, tokenSupport = true")
		}
	} else {
		if (funct == "remote") {
			url = "ws://${deviceIp}:8001/api/v2/channels/samsung.remote.control?name=${name}"
		} else if (funct == "frameArt") {
			url = "ws://${deviceIp}:8001/api/v2/channels/com.samsung.art-app?name=${name}"
		} else if (funct == "application") {
			url = "ws://${deviceIp}:8001/api/v2?name=${name}"
		} else {
			logWarn("connect: Invalid Function = ${funct}, tokenSupport = false")
		}
	}
	state.currentFunction = funct
	interfaces.webSocket.connect(url, ignoreSSLIssues: true)
}

def sendMessage(funct, data) {
	logDebug("sendMessage: [function = ${funct}, data = ${data}, connectType = ${state.currentFunction}]")
	if (state.wsDeviceStatus != "open" || state.currentFunction != funct) {
		connect(funct)
		pauseExecution(300)
	}
	interfaces.webSocket.sendMessage(data)
}

def webSocketStatus(message) {
	def status
	if (message == "status: open") {
		state.wsDeviceStatus = "open"
		status = "open"
	} else if (message == "status: closing") {
		state.wsDeviceStatus = "closed"
		state.currentFunction = "close"
		status = "closed"
	} else if (message.substring(0,7) == "failure") {
		status = "closed-failure"
		state.wsDeviceStatus = "closed"
		state.currentFunction = "close"
		interfaces.webSocket.close()
	}
	logDebug("webSocketStatus: [status: ${status}, message: ${message}]")
}

def parse(resp) {
//	Special test logging.
	def logData = [:]
	try {
		resp = parseJson(resp)
		def event = resp.event
		logData << [EVENT: event]
		if (event == "ms.channel.connect") {
			def newToken = resp.data.token
			if (newToken != null && newToken != state.token) {
				state.token = newToken
				logData << [TOKEN: "updated"]
			} else {
				logData << [TOKEN: "noChange"]
			}
		} else if (event == "d2d_service_message") {
			def data = parseJson(resp.data)
			logData << [SUB_EVENT: data.event, DATA: data]
			if (data.event == "artmode_status" ||
				data.event == "art_mode_changed") {
				def status = data.value
				if (status == null) { status = data.status }
				sendEvent(name: "artModeStatus", value: status)
				logData << [ART_MODE_STATUS: data.value]
				logTrace("parse: [ART_MODE_STATUS: ${data.value}]")
			}
		} else if (event == "ms.channel.ready") {
			logData << [DATA: resp.data, STATUS: "WS Connected"]
		} else if (event == "ms.error") {
			logData << [DATA: resp.data, STATUS: "Error, Closing WS"]
			close{}
		} else {
			logData << [DATA: resp.data, STATUS: "Not Parsed"]
		}
		logTrace("parse: ${logData}")
	} catch (e) {
		logWarn("parse: [STATUS: unhandled, ERROR: ${e}]")
	}
}

//	===== Log Interface =====
def logTrace(msg){
	log.trace "${device.displayName} ${driverVer()}: ${msg}"
}
def logInfo(msg) {
	log.info "${device.displayName} ${driverVer()}: ${msg}"
}
def logDebug(msg) {
//	log.debug "${device.displayName} ${driverVer()}: ${msg}"
}
def logWarn(msg) { log.warn "${device.displayName} ${driverVer()}: ${msg}" }

/*{"device":{
	"FrameTVSupport":"true",
		"GamePadSupport":"true","ImeSyncedSupport":"true","Language":"en_US",
			"OS":"Tizen",
				"PowerState":"on","TokenAuthSupport":"true","VoiceSupport":"true",
"WallScreenRatio":"-1","WallService":"false","countryCode":"US","description":"Samsung DTV RCR",
"developerIP":"0.0.0.0","developerMode":"0","duid":"uuid:be2f6712-6642-4688-9f5b-89910bae667b",
"firmwareVersion":"Unknown","id":"uuid:be2f6712-6642-4688-9f5b-89910bae667b","ip":"192.168.1.10",
"model":"22_PONTUSM_FTV","modelName":"QN50LS03BAFXZA","name":"Master Bedroom TV",
"networkType":"wireless","resolution":"3840x2160","smartHubAgreement":"true","ssid":"78:d2:94:30:4c:ee",
"type":"Samsung SmartTV","udn":"uuid:be2f6712-6642-4688-9f5b-89910bae667b",
"wifiMac":"80:8A:BD:5A:1D:06"},"id":"uuid:be2f6712-6642-4688-9f5b-89910bae667b",
"isSupport":"{"DMP_DRM_PLAYREADY":"false","DMP_DRM_WIDEVINE":"false","DMP_available":"true",
"EDEN_available":"true","FrameTVSupport":"true","ImeSyncedSupport":"true","TokenAuthSupport":"true",
"remote_available":"true","remote_fourDirections":"true","remote_touchPad":"true","remote_voiceControl":"true"}\n"
,"name":"Master Bedroom TV","remote":"1.0","type":"Samsung SmartTV",
"uri":"http://192.168.1.10:8001/api/v2/","version":"2.0.25"}*/












