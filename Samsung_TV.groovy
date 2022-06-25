/*	Samsung HVAC (AC) using SmartThings Interfae
		Copyright Dave Gutheinz
License Information:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
=====	Changes
0.8		Changed statusParse logging to standard.
0.8		Changed trace logging to logDebug in library code.
0.8		Full Test of all commands.

===========================================================================================*/
def driverVer() { return "0.8" }
metadata {
	definition (name: "Samsung TV",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungTvRemote/SamsungTVRemote.groovy"
			   ){
		capability "Samsung TV"
		command "showMessage"
		attribute "pictureModes", "JSON_OBJECT"
		attribute "soundModes", "ENUM"
		capability "MediaInputSource"
		capability "MediaTransport"
		command "fastForward"
		command "rewind"
		capability "Refresh"
		//	ST Capability TV
		command "channelUp"
		command "channelDown"
		command "setTvChannel", ["string"]
		attribute "tvChannel", "string"
		attribute "tvChannelName", "string"
		//	Custom Samsung Cmds
//		command "launchApp", ["string"]
		command "toggleInputSource"
		command "toggleSoundMode"
		command "togglePictureMode"
		command "toggleMute"
		
		//	Device Setup Commands
		command "deviceSetup"	//	Development Tool
		command "getDeviceList"		//	Development Tool.  Move to app if I get one.
	}
	preferences {
		input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "")
		if (stApiKey) {
			input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "")
		}
		if (stDeviceId) {
//			input ("simulate", "bool", title: "Simulation Mode", defalutValue: false)
			input ("pollInterval", "enum", title: "Poll Interval (minutes)",
				   options: ["1", "5", "10", "30"], defaultValue: "5")
			input ("debugLog", "bool",  
				   title: "Enable debug logging for 30 minutes", defaultValue: false)
			input ("infoLog", "bool",  
				   title: "Enable description text logging", defaultValue: true)
		}
	}
}

//	========================================================
//	===== Installation, setup and update ===================
//	========================================================
def installed() {
	sendEvent(name: "volume", value: 0)
	runIn(1, updated)
}

def updated() {
	state.remove("pollInterval")
	def commonStatus = commonUpdate()
	setPollInterval(pollInterval)
	commonStatus << [pollInterval: pollInterval]
	if (commonStatus.status == "OK") {
		logInfo("updated: ${commonStatus}")
	} else {
		logWarn("updated: ${commonStatus}")
	}
	deviceSetup()
}

def showMessage(a=null, b=null, c=null, d=null) { logWarn("showMessage: NOT IMPLEMENTED?") }

//	===== Samsung TV =======================================
def on() { setSwitch("on") }
def off() { setSwitch("off") }
def setSwitch(onOff) {
	def cmdData = [
		component: "main",
		capability: "switch",
		command: onOff,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setSwitch: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

def setVolume(volume) {
	def cmdData = [
		component: "main",
		capability: "audioVolume",
		command: "setVolume",
		arguments: [volume]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setVolume: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

def volumeUp() { setVolUpDown("volumeUp") }
def volumeDown() { setVolUpDown("volumeDown") }
def setVolUpDown(direction) {
	def cmdData = [
		component: "main",
		capability: "audioVolume",
		command: direction,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setVolUpDown: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}
	
def mute() { setMute("mute") }
def unmute() { setMute("unmute") } 
def toggleMute() {
	def newMute = "mute"
	if (device.currentValue("mute") == "muted") {
		newMute = "unmute"
	}
	setMute(newMute)
}
def setMute(muteValue) {
	def cmdData = [
		component: "main",
		capability: "audioMute",
		command: muteValue,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setMute: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

def setPictureMode(pictureMode) {
	def cmdData = [
		component: "main",
		capability: "custom.picturemode",
		command: "setPictureMode",
		arguments: [pictureMode]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setPictureMode: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}
def togglePictureMode() {
	def pictureModes = state.pictureModes
	def totalModes = pictureModes.size()
	def currentMode = device.currentValue("pictureMode")
	def modeNo = pictureModes.indexOf(currentMode)
	def newModeNo = modeNo + 1
	if (newModeNo == totalModes) { newModeNo = 0 }
	def newPictureMode = pictureModes[newModeNo]
	setPictureMode(newPictureMode)
}

def setSoundMode(soundMode) { 
	def cmdData = [
		component: "main",
		capability: "custom.soundmode",
		command: "setSoundMode",
		arguments: [soundMode]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setSoundMode: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}
def toggleSoundMode() {
	def soundModes = state.soundModes
	def totalModes = soundModes.size()
	def currentMode = device.currentValue("soundMode")
	def modeNo = soundModes.indexOf(currentMode)
	def newModeNo = modeNo + 1
	if (newModeNo == totalModes) { newModeNo = 0 }
	def soundMode = soundModes[newModeNo]
	setSoundMode(soundMode)
}

//	===== Media Input Source ===============================
def setInputSource(inputSource) {
	def cmdData = [
		component: "main",
		capability: "mediaInputSource",
		command: "setInputSource",
		arguments: [inputSource]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setInputSource: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}
def toggleInputSource() {
	def inputSources = state.supportedInputs
	def totalSources = inputSources.size()
	def currentSource = device.currentValue("mediaInputSource")
	def sourceNo = inputSources.indexOf(currentSource)
	def newSourceNo = sourceNo + 1
	if (newSourceNo == totalSources) { newSourceNo = 0 }
	def inputSource = inputSources[newSourceNo]
	setInputSource(inputSource)
}

//	===== Media Transport ==================================
def play() { setMediaPlayback("play") }
def pause() { setMediaPlayback("pause") }
def stop() { setMediaPlayback("stop") }
def fastForward() { setMediaPlayback("fastForward") }
def rewind() { setMediaPlayback("rewind") }
def setMediaPlayback(pbMode) {
	def cmdData = [
		component: "main",
		capability: "mediaPlayback",
		command: pbMode,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setMediaPlayback: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

//	===== SmartThings TV ===============================================
def setChannel(newChannel) {
	def cmdData = [
		component: "main",
		capability: "tvChannel",
		command: "setTvChannel",
		arguments: [newChannel]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setChannel: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}	

def channelUp() { setChannelUpDown("channelUp") }
def channelDown() { setChannelUpDown("channelDown") }
def setChannelUpDown(direction) {
	def cmdData = [
		component: "main",
		capability: "tvChannel",
		command: direction,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setChannelUpDown: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

//	===== Launch App =====
def launchApp(appId) {
	def cmdData = [
		component: "main",
		capability: "custom.launchapp",
		command: "launchApp",
		arguments: ["$appId"]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("launchApp: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

def distResp(resp, data) {
	def respLog = [:]
	if (resp.status == 200) {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
			if (data.reason == "deviceSetup") {
				deviceSetupParse(respData.components.main)
			}
			statusParse(respData.components.main)
		} catch (err) {
			respLog << [status: "ERROR",
						errorMsg: err,
						respData: resp.data]
		}
	} else {
		respLog << [status: "ERROR",
					httpCode: resp.status,
					errorMsg: resp.errorMessage]
	}
	if (respLog != [:]) {
		logWarn("distResp: ${respLog}")
	}
}

def deviceSetupParse(mainData) {
	def setupData = [:]
	def supportedInputs =  mainData.mediaInputSource.supportedInputSources.value
	sendEvent(name: "supportedInputs", value: supportedInputs)	
	state.supportedInputs = supportedInputs
	setupData << [supportedInputs: supportedInputs]
	
	def pictureModes = mainData["custom.picturemode"].supportedPictureModes.value
	sendEvent(name: "pictureModes",value: pictureModes)
	state.pictureModes = pictureModes
	setupData << [pictureModes: pictureModes]
	
	def soundModes =  mainData["custom.soundmode"].supportedSoundModes.value
	sendEvent(name: "soundModes",value: soundModes)
	state.soundModes = soundModes
	setupData << [soundModes: soundModes]
	
	logInfo("deviceSetupParse: ${setupData}")
}

def statusParse(mainData) {
	def stData = [:]
	def onOff = mainData.switch.switch.value
	if (device.currentValue("switch") != onOff) {
		if (onOff == "off") {
			setPollInterval("10")
		} else {
			setPollInterval("1")
		}
		sendEvent(name: "switch", value: onOff)
		stData << [switch: onOff]
	}
	
	def volume = mainData.audioVolume.volume.value.toInteger()
	if (device.currentValue("volume").toInteger() != volume) {
		sendEvent(name: "volume", value: volume)
		stData << [volume: volume]
	}
	
	def mute = mainData.audioMute.mute.value
	if (device.currentValue("mute") != mute) {
		sendEvent(name: "mute", value: mute)
		stData << [mute: mute]
	}
	
	def mediaInputSource = mainData.mediaInputSource.inputSource.value
	if (device.currentValue("mediaInputSource") != mediaInputSource) {
		sendEvent(name: "mediaInputSource", value: mediaInputSource)		
		stData << [mediaInputSource: mediaInputSource]
	}
	
	def tvChannel = mainData.tvChannel.tvChannel.value
	def tvChannelName = mainData.tvChannel.tvChannelName.value
	if (device.currentValue("tvChannel") != tvChannel) {
		sendEvent(name: "tvChannel", value: tvChannel)	
		sendEvent(name: "tvChannelName", value: tvChannelName)			
		stData << [tvChannel: tvChannel, tvChannelName: tvChannelName]
	}
	
	def pictureMode = mainData["custom.picturemode"].pictureMode.value
	if (device.currentValue("pictureMode") != pictureMode) {
		sendEvent(name: "pictureMode",value: pictureMode)
		stData << [pictureMode: pictureMode]
	}
	
	def soundMode = mainData["custom.soundmode"].soundMode.value
	if (device.currentValue("soundMode") != soundMode) {
		sendEvent(name: "soundMode",value: soundMode)
		stData << [soundMode: soundMode]
	}
	
	def transportStatus = mainData.mediaPlayback.playbackStatus.value
	if (transportStatus == null || transportStatus == "") {
		transportStatus = "stopped"
	}
	if (device.currentValue("transportStatus") != transportStatus) {
		sendEvent(name: "transportStatus", value: transportStatus)
		stData << [transportStatus: transportStatus]
	}
	
	if (stData != [:]) {
		logInfo("statusParse: ${stData}")
	}				   
}

//	===== Library Integration =====




// ~~~~~ start include (611) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8

def listAttributes(trace = false) { // library marker davegut.Logging, line 10
	def attrs = device.getSupportedAttributes() // library marker davegut.Logging, line 11
	def attrList = [:] // library marker davegut.Logging, line 12
	attrs.each { // library marker davegut.Logging, line 13
		def val = device.currentValue("${it}") // library marker davegut.Logging, line 14
		attrList << ["${it}": val] // library marker davegut.Logging, line 15
	} // library marker davegut.Logging, line 16
	if (trace == true) { // library marker davegut.Logging, line 17
		logTrace("Attributes: ${attrList}") // library marker davegut.Logging, line 18
	} else { // library marker davegut.Logging, line 19
		logDebug("Attributes: ${attrList}") // library marker davegut.Logging, line 20
	} // library marker davegut.Logging, line 21
} // library marker davegut.Logging, line 22

def logTrace(msg){ // library marker davegut.Logging, line 24
	log.trace "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" // library marker davegut.Logging, line 25
} // library marker davegut.Logging, line 26

def logInfo(msg) {  // library marker davegut.Logging, line 28
	if (infoLog == true) { // library marker davegut.Logging, line 29
		log.info "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" // library marker davegut.Logging, line 30
	} // library marker davegut.Logging, line 31
} // library marker davegut.Logging, line 32

def debugLogOff() { // library marker davegut.Logging, line 34
	if (debug == true) { // library marker davegut.Logging, line 35
		device.updateSetting("debug", [type:"bool", value: false]) // library marker davegut.Logging, line 36
	} else if (debugLog == true) { // library marker davegut.Logging, line 37
		device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.Logging, line 38
	} // library marker davegut.Logging, line 39
	logInfo("Debug logging is false.") // library marker davegut.Logging, line 40
} // library marker davegut.Logging, line 41

def logDebug(msg) { // library marker davegut.Logging, line 43
	if (debug == true || debugLog == true) { // library marker davegut.Logging, line 44
		log.debug "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" // library marker davegut.Logging, line 45
	} // library marker davegut.Logging, line 46
} // library marker davegut.Logging, line 47

def logWarn(msg) { log.warn "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" } // library marker davegut.Logging, line 49

// ~~~~~ end include (611) davegut.Logging ~~~~~

// ~~~~~ start include (387) davegut.ST-Communications ~~~~~
library ( // library marker davegut.ST-Communications, line 1
	name: "ST-Communications", // library marker davegut.ST-Communications, line 2
	namespace: "davegut", // library marker davegut.ST-Communications, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Communications, line 4
	description: "ST Communications Methods", // library marker davegut.ST-Communications, line 5
	category: "utilities", // library marker davegut.ST-Communications, line 6
	documentationLink: "" // library marker davegut.ST-Communications, line 7
) // library marker davegut.ST-Communications, line 8
import groovy.json.JsonSlurper // library marker davegut.ST-Communications, line 9

private asyncGet(sendData, passData = "none") { // library marker davegut.ST-Communications, line 11
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 12
		logWarn("asyncGet: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 13
	} else { // library marker davegut.ST-Communications, line 14
		logDebug("asyncGet: ${sendData}, ${passData}") // library marker davegut.ST-Communications, line 15
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 16
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 17
			path: sendData.path, // library marker davegut.ST-Communications, line 18
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()]] // library marker davegut.ST-Communications, line 19
		try { // library marker davegut.ST-Communications, line 20
			asynchttpGet(sendData.parse, sendCmdParams, [reason: passData]) // library marker davegut.ST-Communications, line 21
		} catch (error) { // library marker davegut.ST-Communications, line 22
			logWarn("asyncGet: [status: error, statusReason: ${error}]") // library marker davegut.ST-Communications, line 23
		} // library marker davegut.ST-Communications, line 24
	} // library marker davegut.ST-Communications, line 25
} // library marker davegut.ST-Communications, line 26

private syncGet(path){ // library marker davegut.ST-Communications, line 28
	def respData = [:] // library marker davegut.ST-Communications, line 29
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 30
		respData << [status: "ERROR", errorMsg: "no stApiKey"] // library marker davegut.ST-Communications, line 31
		logWarn("syncGet: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 32
	} else { // library marker davegut.ST-Communications, line 33
		logDebug("syncGet: ${sendData}") // library marker davegut.ST-Communications, line 34
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 35
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 36
			path: path, // library marker davegut.ST-Communications, line 37
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()] // library marker davegut.ST-Communications, line 38
		] // library marker davegut.ST-Communications, line 39
		try { // library marker davegut.ST-Communications, line 40
			httpGet(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 41
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 42
					respData << [status: "OK", results: resp.data] // library marker davegut.ST-Communications, line 43
				} else { // library marker davegut.ST-Communications, line 44
					respData << [status: "FAILED", errorMsg: "httpCode: ${resp.status}"] // library marker davegut.ST-Communications, line 45
					def warnData = [status:"ERROR", // library marker davegut.ST-Communications, line 46
									cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 47
									httpCode: resp.status, // library marker davegut.ST-Communications, line 48
									errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 49
					logWarn("syncGet: ${warnData}") // library marker davegut.ST-Communications, line 50
				} // library marker davegut.ST-Communications, line 51
			} // library marker davegut.ST-Communications, line 52
		} catch (error) { // library marker davegut.ST-Communications, line 53
			respData << [status: "FAILED", errorMsg: "non-HTTP Error"] // library marker davegut.ST-Communications, line 54
			def warnData = [status: "ERROR", // library marker davegut.ST-Communications, line 55
							cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 56
							httpCode: "No Response", // library marker davegut.ST-Communications, line 57
							errorMsg: error] // library marker davegut.ST-Communications, line 58
			logWarn("syncGet: ${warnData}") // library marker davegut.ST-Communications, line 59
		} // library marker davegut.ST-Communications, line 60
	} // library marker davegut.ST-Communications, line 61
	return respData // library marker davegut.ST-Communications, line 62
} // library marker davegut.ST-Communications, line 63

private xxasyncPost(sendData){ // library marker davegut.ST-Communications, line 65
	def respData = [:] // library marker davegut.ST-Communications, line 66
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 67
		respData << [status: "ERROR", errorMsg: "no stApiKey"] // library marker davegut.ST-Communications, line 68
		logWarn("syncPost: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 69
	} else { // library marker davegut.ST-Communications, line 70
		logDebug("syncPost: ${sendData}") // library marker davegut.ST-Communications, line 71
		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 72
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 73
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 74
			path: sendData.path, // library marker davegut.ST-Communications, line 75
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 76
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 77
		] // library marker davegut.ST-Communications, line 78
		try { // library marker davegut.ST-Communications, line 79
			asynchttpPost(sendData.parse, sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 80
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 81
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 82
				} else { // library marker davegut.ST-Communications, line 83
					respData << [status: "FAILED", errorMsg: "httpCode: ${resp.status}"] // library marker davegut.ST-Communications, line 84
					def warnData = [status:"ERROR", // library marker davegut.ST-Communications, line 85
									cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 86
									httpCode: resp.status, // library marker davegut.ST-Communications, line 87
									errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 88
					logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 89
				} // library marker davegut.ST-Communications, line 90
			} // library marker davegut.ST-Communications, line 91
		} catch (error) { // library marker davegut.ST-Communications, line 92
			respData << [status: "FAILED", errorMsg: "non-HTTP Error"] // library marker davegut.ST-Communications, line 93
			def warnData = [status: "ERROR", // library marker davegut.ST-Communications, line 94
							cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 95
							httpCode: "No Response", // library marker davegut.ST-Communications, line 96
							errorMsg: error] // library marker davegut.ST-Communications, line 97
			logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 98
		} // library marker davegut.ST-Communications, line 99
	} // library marker davegut.ST-Communications, line 100
	return respData // library marker davegut.ST-Communications, line 101
} // library marker davegut.ST-Communications, line 102

private syncPost(sendData){ // library marker davegut.ST-Communications, line 104
	def respData = [:] // library marker davegut.ST-Communications, line 105
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 106
		respData << [status: "ERROR", errorMsg: "no stApiKey"] // library marker davegut.ST-Communications, line 107
		logWarn("syncPost: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 108
	} else { // library marker davegut.ST-Communications, line 109
		logDebug("syncPost: ${sendData}") // library marker davegut.ST-Communications, line 110
		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 111
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 112
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 113
			path: sendData.path, // library marker davegut.ST-Communications, line 114
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 115
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 116
		] // library marker davegut.ST-Communications, line 117
		try { // library marker davegut.ST-Communications, line 118
			httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 119
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 120
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 121
				} else { // library marker davegut.ST-Communications, line 122
					respData << [status: "FAILED", errorMsg: "httpCode: ${resp.status}"] // library marker davegut.ST-Communications, line 123
					def warnData = [status:"ERROR", // library marker davegut.ST-Communications, line 124
									cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 125
									httpCode: resp.status, // library marker davegut.ST-Communications, line 126
									errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 127
					logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 128
				} // library marker davegut.ST-Communications, line 129
			} // library marker davegut.ST-Communications, line 130
		} catch (error) { // library marker davegut.ST-Communications, line 131
			respData << [status: "FAILED", errorMsg: "non-HTTP Error"] // library marker davegut.ST-Communications, line 132
			def warnData = [status: "ERROR", // library marker davegut.ST-Communications, line 133
							cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 134
							httpCode: "No Response", // library marker davegut.ST-Communications, line 135
							errorMsg: error] // library marker davegut.ST-Communications, line 136
			logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 137
		} // library marker davegut.ST-Communications, line 138
	} // library marker davegut.ST-Communications, line 139
	return respData // library marker davegut.ST-Communications, line 140
} // library marker davegut.ST-Communications, line 141

// ~~~~~ end include (387) davegut.ST-Communications ~~~~~

// ~~~~~ start include (642) davegut.ST-Common ~~~~~
library ( // library marker davegut.ST-Common, line 1
	name: "ST-Common", // library marker davegut.ST-Common, line 2
	namespace: "davegut", // library marker davegut.ST-Common, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Common, line 4
	description: "ST Wash/Dryer Common Methods", // library marker davegut.ST-Common, line 5
	category: "utilities", // library marker davegut.ST-Common, line 6
	documentationLink: "" // library marker davegut.ST-Common, line 7
) // library marker davegut.ST-Common, line 8

def commonUpdate() { // library marker davegut.ST-Common, line 10
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Common, line 11
		return [status: "FAILED", reason: "No stApiKey"] // library marker davegut.ST-Common, line 12
	} // library marker davegut.ST-Common, line 13
	if (!stDeviceId || stDeviceId == "") { // library marker davegut.ST-Common, line 14
		getDeviceList() // library marker davegut.ST-Common, line 15
		return [status: "FAILED", reason: "No stDeviceId"] // library marker davegut.ST-Common, line 16
	} // library marker davegut.ST-Common, line 17

	unschedule() // library marker davegut.ST-Common, line 19
	def updateData = [:] // library marker davegut.ST-Common, line 20
	updateData << [status: "OK"] // library marker davegut.ST-Common, line 21
	if (debugLog) { runIn(1800, debugLogOff) } // library marker davegut.ST-Common, line 22
	updateData << [stDeviceId: stDeviceId] // library marker davegut.ST-Common, line 23
	updateData << [debugLog: debugLog, infoLog: infoLog] // library marker davegut.ST-Common, line 24
	if (!getDataValue("driverVersion") ||  // library marker davegut.ST-Common, line 25
		getDataValue("driverVersion") != driverVer()) { // library marker davegut.ST-Common, line 26
		updateDataValue("driverVersion", driverVer()) // library marker davegut.ST-Common, line 27
		updateData << [driverVer: driverVer()] // library marker davegut.ST-Common, line 28
	} // library marker davegut.ST-Common, line 29
	setPollInterval(pollInterval) // library marker davegut.ST-Common, line 30
	updateData << [pollInterval: pollInterval] // library marker davegut.ST-Common, line 31

	runIn(5, refresh) // library marker davegut.ST-Common, line 33
	return updateData // library marker davegut.ST-Common, line 34
} // library marker davegut.ST-Common, line 35

def setPollInterval(pollInterval) { // library marker davegut.ST-Common, line 37
	logDebug("setPollInterval: ${pollInterval}") // library marker davegut.ST-Common, line 38
	state.pollInterval = pollInterval // library marker davegut.ST-Common, line 39
	switch(pollInterval) { // library marker davegut.ST-Common, line 40
		case "1" : runEvery1Minute(poll); break // library marker davegut.ST-Common, line 41
		case "5" : runEvery5Minutes(poll); break // library marker davegut.ST-Common, line 42
		case "10" : runEvery10Minutes(poll); break // library marker davegut.ST-Common, line 43
		case "30" : runEvery30Minutes(poll); break // library marker davegut.ST-Common, line 44
		default: runEvery10Minutes(poll) // library marker davegut.ST-Common, line 45
	} // library marker davegut.ST-Common, line 46
} // library marker davegut.ST-Common, line 47

def deviceCommand(cmdData) { // library marker davegut.ST-Common, line 49
	def respData = [cmdData: cmdData] // library marker davegut.ST-Common, line 50
	if (simulate == true) { // library marker davegut.ST-Common, line 51
		respData = testResp(cmdData) // library marker davegut.ST-Common, line 52
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 53
		respData << "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 54
	} else { // library marker davegut.ST-Common, line 55
		def sendData = [ // library marker davegut.ST-Common, line 56
			path: "/devices/${stDeviceId.trim()}/commands", // library marker davegut.ST-Common, line 57
			cmdData: cmdData // library marker davegut.ST-Common, line 58
		] // library marker davegut.ST-Common, line 59
		respData = syncPost(sendData) // library marker davegut.ST-Common, line 60
		if(respData.status == "OK") { // library marker davegut.ST-Common, line 61
			respData << [status: "OK"] // library marker davegut.ST-Common, line 62
		} // library marker davegut.ST-Common, line 63
	} // library marker davegut.ST-Common, line 64
	if (cmdData.capability != "refresh") { // library marker davegut.ST-Common, line 65
		refresh() // library marker davegut.ST-Common, line 66
	} // library marker davegut.ST-Common, line 67
	runIn(3, poll) // library marker davegut.ST-Common, line 68
	if (respData.status == "FAILED") { // library marker davegut.ST-Common, line 69
		logWarn("deviceCommand: ${respData}") // library marker davegut.ST-Common, line 70
	} else { // library marker davegut.ST-Common, line 71
		logDebug("deviceCommand: ${respData}") // library marker davegut.ST-Common, line 72
	} // library marker davegut.ST-Common, line 73
} // library marker davegut.ST-Common, line 74

def refresh() {  // library marker davegut.ST-Common, line 76
	def cmdData = [ // library marker davegut.ST-Common, line 77
		component: "main", // library marker davegut.ST-Common, line 78
		capability: "refresh", // library marker davegut.ST-Common, line 79
		command: "refresh", // library marker davegut.ST-Common, line 80
		arguments: []] // library marker davegut.ST-Common, line 81
	def cmdStatus = deviceCommand(cmdData) // library marker davegut.ST-Common, line 82
	logInfo("refresh: ${cmdStatus}") // library marker davegut.ST-Common, line 83
} // library marker davegut.ST-Common, line 84

def poll() { // library marker davegut.ST-Common, line 86
	if (simulate == true) { // library marker davegut.ST-Common, line 87
		def children = getChildDevices() // library marker davegut.ST-Common, line 88
		if (children) { // library marker davegut.ST-Common, line 89
			children.each { // library marker davegut.ST-Common, line 90
				it.statusParse(testData()) // library marker davegut.ST-Common, line 91
			} // library marker davegut.ST-Common, line 92
		} // library marker davegut.ST-Common, line 93
		statusParse(testData()) // library marker davegut.ST-Common, line 94
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 95
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 96
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 97
	} else { // library marker davegut.ST-Common, line 98
		def sendData = [ // library marker davegut.ST-Common, line 99
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 100
			parse: "distResp" // library marker davegut.ST-Common, line 101
			] // library marker davegut.ST-Common, line 102
		asyncGet(sendData, "statusParse") // library marker davegut.ST-Common, line 103
	} // library marker davegut.ST-Common, line 104
} // library marker davegut.ST-Common, line 105

def deviceSetup() { // library marker davegut.ST-Common, line 107
	if (simulate == true) { // library marker davegut.ST-Common, line 108
		def children = getChildDevices() // library marker davegut.ST-Common, line 109
		if (children) { // library marker davegut.ST-Common, line 110
			children.each { // library marker davegut.ST-Common, line 111
				it.deviceSetupParse(testData()) // library marker davegut.ST-Common, line 112
			} // library marker davegut.ST-Common, line 113
		} // library marker davegut.ST-Common, line 114
		deviceSetupParse(testData()) // library marker davegut.ST-Common, line 115
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 116
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 117
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 118
	} else { // library marker davegut.ST-Common, line 119
		def sendData = [ // library marker davegut.ST-Common, line 120
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 121
			parse: "distResp" // library marker davegut.ST-Common, line 122
			] // library marker davegut.ST-Common, line 123
		asyncGet(sendData, "deviceSetup") // library marker davegut.ST-Common, line 124
	} // library marker davegut.ST-Common, line 125
} // library marker davegut.ST-Common, line 126

def getDeviceList() { // library marker davegut.ST-Common, line 128
	def sendData = [ // library marker davegut.ST-Common, line 129
		path: "/devices", // library marker davegut.ST-Common, line 130
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 131
		] // library marker davegut.ST-Common, line 132
	asyncGet(sendData) // library marker davegut.ST-Common, line 133
} // library marker davegut.ST-Common, line 134

def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 136
	def respData // library marker davegut.ST-Common, line 137
	if (resp.status != 200) { // library marker davegut.ST-Common, line 138
		respData = [status: "ERROR", // library marker davegut.ST-Common, line 139
					httpCode: resp.status, // library marker davegut.ST-Common, line 140
					errorMsg: resp.errorMessage] // library marker davegut.ST-Common, line 141
	} else { // library marker davegut.ST-Common, line 142
		try { // library marker davegut.ST-Common, line 143
			respData = new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Common, line 144
		} catch (err) { // library marker davegut.ST-Common, line 145
			respData = [status: "ERROR", // library marker davegut.ST-Common, line 146
						errorMsg: err, // library marker davegut.ST-Common, line 147
						respData: resp.data] // library marker davegut.ST-Common, line 148
		} // library marker davegut.ST-Common, line 149
	} // library marker davegut.ST-Common, line 150
	if (respData.status == "ERROR") { // library marker davegut.ST-Common, line 151
		logWarn("getDeviceListParse: ${respData}") // library marker davegut.ST-Common, line 152
	} else { // library marker davegut.ST-Common, line 153
		log.info "" // library marker davegut.ST-Common, line 154
		respData.items.each { // library marker davegut.ST-Common, line 155
			log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 156
		} // library marker davegut.ST-Common, line 157
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 158
	} // library marker davegut.ST-Common, line 159
} // library marker davegut.ST-Common, line 160

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 162
	Integer currTime = now() // library marker davegut.ST-Common, line 163
	Integer compTime // library marker davegut.ST-Common, line 164
	try { // library marker davegut.ST-Common, line 165
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 166
	} catch (e) { // library marker davegut.ST-Common, line 167
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 168
	} // library marker davegut.ST-Common, line 169
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 170
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 171
	return timeRemaining // library marker davegut.ST-Common, line 172
} // library marker davegut.ST-Common, line 173

// ~~~~~ end include (642) davegut.ST-Common ~~~~~
