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
	definition (name: "Samsung Soundbar",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungTvRemote/SamsungSoundbar.groovy"
			   ){
		capability "AudioVolume"
		capability "MediaTransport"
		capability "Switch"
		attribute "trackTime", "NUMBER"
		attribute "elapsedTime", "Number"
		attribute "trackData", "JSON"
		attribute "soundFrom", "JSON"
		capability "AudioNotification"
		capability "Refresh"
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
			input ("notificationLevel", "num", title: "Notification volume increase in percent", defaultValue: 5)
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
}

def deviceSetup() { getDeviceStatus("deviceSetupParse") }
def deviceSetupParse(resp, data) {
	def setupData = [:]
	def respData = validateResp(resp, "deviceStatusParse")
	def components = []
	respData.components.each{
		components << it.key
	}
	state.components = components
	if (respData == "error") { return }
	def mainData = respData.components.main
	
	def supportedInputs =  mainData.mediaInputSource.supportedInputSources.value
	sendEvent(name: "supportedInputs", value: supportedInputs)	
	state.supportedInputs = supportedInputs
	setupData << [supportedInputs: supportedInputs]
	
	def supportedPictureModes = mainData["custom.picturemode"].supportedPictureModes.value
	state.supportedPictureModes = supportedPictureModes
	setupData << [supportedPictureModes:  supportedPictureModes]
	
	def supportedSoundModes =  mainData["custom.soundmode"].supportedSoundModes.value
	state.supportedSoundModes =  supportedSoundModes
	setupData << [supportedSoundModes:  supportedSoundModes]
	
	return setupData
}

//	===== Audio Volume / Audio Mute =====
//	Implemented as capability AudioVolume
def setVolume(volume) {
	if (volume == null) { volume = device.currentValue("volume") }
	def cmdData = [
		component: "main",
		capability: "audioVolume",
		command: "setVolume",
		arguments: [volume]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setVolume: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}
def volumeUp() { 
	def curVol = device.currentValue("volume")
	def newVol = curVol + 2
	if (newVol > 50) { newVol = 50 }
	setVolume(newVol)
}
def volumeDown() {
	def curVol = device.currentValue("volume")
	def newVol = curVol - 2
	if (newVol > 50) { newVol = 0 }
	setVolume(newVol)
}

def mute() { setMute("mute") }
def unmute() { setMute("unmute") } 
def setMute(muteValue) {
	def cmdData = [
		component: "main",
		capability: "audioMute",
		command: muteValue,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setMute: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

//	===== Media Playback =====
//	Implemented as capability MediaTransport
def play() { setMediaPlayback("play") }
def pause() { setMediaPlayback("pause") }
def stop() { setMediaPlayback("stop") }
def setMediaPlayback(pbMode) {
	def cmdData = [
		component: "main",
		capability: "mediaPlayback",
		command: pbMode,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setMediaPlayback: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

//	===== Media Input Source =====
//	Implemented as Media Input Source
def setInputSource(inputSource) {
	def cmdData = [
		component: "main",
		capability: "mediaInputSource",
		command: "setInputSource",
		arguments: [inputSource]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setInputSource: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

//	===== Switch =====
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

//	===== Audio Notification =====
//	Implemented as Audio Notification
def playText(text, level=null) {
	def trackData = textToSpeech(text)
	logInfo("playText: [text: ${text}]")
	playTrack(trackData.uri, level)
}
def playTextAndRestore(text, level=null) {
	def trackData = textToSpeech(text)
	logInfo("playTextAndRestore: [text: ${text}]")
	playTrackAndRestore(trackData.uri, level)
}
def playTextAndResume(text, level=null) {
	def trackData = textToSpeech(text)
	logInfo("playTextAndResume: [text: ${text}]")
	playTrackAndResume(trackData.uri, level)
}

def convertToTrack(text) {
	def ttsApiKey = "2cef725970f14ee6b8cb843495f6f08e"
	def ttsLang = "en-au"
	def uriText = URLEncoder.encode(text, "UTF-8").replaceAll(/\+/, "%20")
	def trackUri = "http://api.voicerss.org/?" +
		"key=${ttsApiKey.trim()}" +
		"&f=48khz_16bit_mono" +
		"&c=MP3" +
		"&hl=${ttsLang}" +
		"&src=${uriText}"
		def duration = (1 + text.length() / 10).toInteger()
		return [uri: trackUri, duration: duration]
}

def playTrack(trackData, level = null) {
	def volume = setNotificationVolume(level)
	def cmdData = [
		component: "main",
		capability: "audioNotification",
		command: "playTrack",
		arguments: [trackData, volume]]
//		arguments: ["https://streams.radio.co/se1a320b47/listen", volume]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("playTrack: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}
def playTrackAndRestore(trackData, level=null) {
	def volume = setNotificationVolume(level)
	def cmdData = [
		component: "main",
		capability: "audioNotification",
		command: "playTrackAndRestore",
		arguments: [trackData, volume]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("playTrackAndRestore: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}
def playTrackAndResume(trackData, level=null) {
	logDebug("playTrackAndResume: Volume = ${volume}, trackData = ${trackData}")
	def volume = setNotificationVolume(level)
	def cmdData = [
		component: "main",
		capability: "audioNotification",
		command: "playTrackAndResume",
		arguments: [trackData, volume]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("playTrackAndResume: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

def setNotificationVolume(level) {
	if (level == null) { level = device.currentValue("volume") }
	return level + notificationLevel.toInteger()
}

//	Status Parse
def validateResp(resp, data) {
	def respLog = [:]
	if (resp.status == 200) {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
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
		logWarn("validateResp: ${respLog}")
	}
}

def statusParse(mainData) {
	def logData = [:]
	def onOff = mainData.switch.switch.value
	def stData = [:]
	try {
	if (device.currentValue("switch") != onOff) {
//		if (onOff == "off") {
//			setPollInterval("10")
//		} else {
//			setPollInterval("1")
//		}
		sendEvent(name: "switch", value: onOff)
		stData << [switch: onOff]
	}
	} catch (e) { logWarn("switch") }
	
	try {
	def volume = mainData.audioVolume.volume.value.toInteger()
	if (device.currentValue("volume").toInteger() != volume) {
		sendEvent(name: "volume", value: volume)
		stData << [volume: volume]
	}
	} catch (e) { logWarn("volume") }
	
	try {
	def mute = mainData.audioMute.mute.value
	if (device.currentValue("mute") != mute) {
		sendEvent(name: "mute", value: mute)
		stData << [mute: mute]
	}
	} catch (e) { logWarn("mute") }
	
	try {
	def transportStatus = mainData.mediaPlayback.playbackStatus.value
	if (device.currentValue("transportStatus") != transportStatus) {
		sendEvent(name: "transportStatus", value: transportStatus)
		stData << [transportStatus: transportStatus]
	}
	} catch (e) { logWarn("transportStatus") }
	
	try {
	def mediaInputSource = mainData.mediaInputSource.inputSource.value
	if (device.currentValue("mediaInputSource") != mediaInputSource) {
		sendEvent(name: "mediaInputSource", value: mediaInputSource)		
		stData << [mediaInputSource: mediaInputSource]
	}
	} catch (e) { logWarn("mediaInputSource") }
	
	try {
	def soundFrom = mainData["samsungvd.soundFrom"]
	def soundFromData = [mode: soundFrom.mode.value,
						 name: soundFrom.detailName.value]
	if (device.currentValue("soundFrom") != soundFromData) {
		sendEvent(name: "soundFrom", value: soundFromData)		
		stData << [soundFrom: soundFromData]
	}
	} catch (e) { logWarn("soundFrom") }
	
	try {
	def trackTime = mainData.audioTrackData.totalTime.value
	if (device.currentValue("trackTime") != trackTime) {
		sendEvent(name: "trackTime", value: trackTime)		
		stData << [trackTime: trackTime]
	}
	} catch (e) { logWarn("trackTime") }
		
	try {
	def elapsedTime = mainData.audioTrackData.elapsedTime.value
	if (device.currentValue("elapsedTime") != elapsedTime) {
		sendEvent(name: "elapsedTime", value: elapsedTime)		
		stData << [elapsedTime: elapsedTime]
	}
	} catch (e) { logWarn("elapsedTime") }
		
	try {
	def trackData = mainData.audioTrackData.audioTrackData.value
	if (device.currentValue("trackData") != trackData) {
		sendEvent(name: "trackData", value: trackData)		
		stData << [trackData: trackData]
	}
	} catch (e) { logWarn("trackData") }
		
	if (stData != [:]) {
		logInfo("statusParse: ${stData}")
	}	
	listAttributes(true)
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

//	Logging during development // library marker davegut.Logging, line 10
def listAttributes(trace = false) { // library marker davegut.Logging, line 11
	def attrs = device.getSupportedAttributes() // library marker davegut.Logging, line 12
	def attrList = [:] // library marker davegut.Logging, line 13
	attrs.each { // library marker davegut.Logging, line 14
		def val = device.currentValue("${it}") // library marker davegut.Logging, line 15
		attrList << ["${it}": val] // library marker davegut.Logging, line 16
	} // library marker davegut.Logging, line 17
	if (trace == true) { // library marker davegut.Logging, line 18
		logTrace("Attributes: ${attrList}") // library marker davegut.Logging, line 19
	} else { // library marker davegut.Logging, line 20
		logDebug("Attributes: ${attrList}") // library marker davegut.Logging, line 21
	} // library marker davegut.Logging, line 22
} // library marker davegut.Logging, line 23

def logTrace(msg){ // library marker davegut.Logging, line 25
	log.trace "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" // library marker davegut.Logging, line 26
} // library marker davegut.Logging, line 27

def logInfo(msg) {  // library marker davegut.Logging, line 29
	if (infoLog == true) { // library marker davegut.Logging, line 30
		log.info "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" // library marker davegut.Logging, line 31
	} // library marker davegut.Logging, line 32
} // library marker davegut.Logging, line 33

def debugLogOff() { // library marker davegut.Logging, line 35
	if (debug == true) { // library marker davegut.Logging, line 36
		device.updateSetting("debug", [type:"bool", value: false]) // library marker davegut.Logging, line 37
	} else if (debugLog == true) { // library marker davegut.Logging, line 38
		device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.Logging, line 39
	} // library marker davegut.Logging, line 40
	logInfo("Debug logging is false.") // library marker davegut.Logging, line 41
} // library marker davegut.Logging, line 42

def logDebug(msg) { // library marker davegut.Logging, line 44
	if (debug == true || debugLog == true) { // library marker davegut.Logging, line 45
		log.debug "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" // library marker davegut.Logging, line 46
	} // library marker davegut.Logging, line 47
} // library marker davegut.Logging, line 48

def logWarn(msg) { log.warn "${device.displayName} ${getDataValue("driverVersion")}: ${msg}" } // library marker davegut.Logging, line 50

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

private asyncGet(sendData) { // library marker davegut.ST-Communications, line 11
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 12
		logWarn("asyncGet: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 13
	} else { // library marker davegut.ST-Communications, line 14
		logDebug("asyncGet: ${sendData}") // library marker davegut.ST-Communications, line 15
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 16
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 17
			path: sendData.path, // library marker davegut.ST-Communications, line 18
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()]] // library marker davegut.ST-Communications, line 19
		try { // library marker davegut.ST-Communications, line 20
			asynchttpGet(sendData.parse, sendCmdParams) // library marker davegut.ST-Communications, line 21
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

private syncPost(sendData){ // library marker davegut.ST-Communications, line 65
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
			httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 80
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
	def respData // library marker davegut.ST-Common, line 50
	if (simulate == true) { // library marker davegut.ST-Common, line 51
		respData = testResp(cmdData) // library marker davegut.ST-Common, line 52
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 53
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 54
		logWarn("deviceCommand: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 55
	} else { // library marker davegut.ST-Common, line 56
		def sendData = [ // library marker davegut.ST-Common, line 57
			path: "/devices/${stDeviceId.trim()}/commands", // library marker davegut.ST-Common, line 58
			cmdData: cmdData // library marker davegut.ST-Common, line 59
		] // library marker davegut.ST-Common, line 60
		respData = syncPost(sendData) // library marker davegut.ST-Common, line 61
		if(respData.status == "OK") { // library marker davegut.ST-Common, line 62
			respData = [status: "OK"] // library marker davegut.ST-Common, line 63
		} // library marker davegut.ST-Common, line 64
	} // library marker davegut.ST-Common, line 65
	runIn(1, poll) // library marker davegut.ST-Common, line 66
	return respData // library marker davegut.ST-Common, line 67
} // library marker davegut.ST-Common, line 68

def refresh() {  // library marker davegut.ST-Common, line 70
	def cmdData = [ // library marker davegut.ST-Common, line 71
		component: "main", // library marker davegut.ST-Common, line 72
		capability: "refresh", // library marker davegut.ST-Common, line 73
		command: "refresh", // library marker davegut.ST-Common, line 74
		arguments: []] // library marker davegut.ST-Common, line 75
	def cmdStatus = deviceCommand(cmdData) // library marker davegut.ST-Common, line 76
	logInfo("refresh: ${cmdStatus}") // library marker davegut.ST-Common, line 77
} // library marker davegut.ST-Common, line 78

def poll() { // library marker davegut.ST-Common, line 80
	if (simulate == true) { // library marker davegut.ST-Common, line 81
		def children = getChildDevices() // library marker davegut.ST-Common, line 82
		if (children) { // library marker davegut.ST-Common, line 83
			children.each { // library marker davegut.ST-Common, line 84
				it.statusParse(testData()) // library marker davegut.ST-Common, line 85
			} // library marker davegut.ST-Common, line 86
		} // library marker davegut.ST-Common, line 87
		statusParse(testData()) // library marker davegut.ST-Common, line 88
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 89
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 90
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 91
	} else { // library marker davegut.ST-Common, line 92
		def sendData = [ // library marker davegut.ST-Common, line 93
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 94
			parse: "validateResp" // library marker davegut.ST-Common, line 95
			] // library marker davegut.ST-Common, line 96
		asyncGet(sendData) // library marker davegut.ST-Common, line 97
	} // library marker davegut.ST-Common, line 98
} // library marker davegut.ST-Common, line 99

def getDeviceList() { // library marker davegut.ST-Common, line 101
	def sendData = [ // library marker davegut.ST-Common, line 102
		path: "/devices", // library marker davegut.ST-Common, line 103
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 104
		] // library marker davegut.ST-Common, line 105
	asyncGet(sendData) // library marker davegut.ST-Common, line 106
} // library marker davegut.ST-Common, line 107

def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 109
	def respData // library marker davegut.ST-Common, line 110
	if (resp.status != 200) { // library marker davegut.ST-Common, line 111
		respData = [status: "ERROR", // library marker davegut.ST-Common, line 112
					httpCode: resp.status, // library marker davegut.ST-Common, line 113
					errorMsg: resp.errorMessage] // library marker davegut.ST-Common, line 114
	} else { // library marker davegut.ST-Common, line 115
		try { // library marker davegut.ST-Common, line 116
			respData = new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Common, line 117
		} catch (err) { // library marker davegut.ST-Common, line 118
			respData = [status: "ERROR", // library marker davegut.ST-Common, line 119
						errorMsg: err, // library marker davegut.ST-Common, line 120
						respData: resp.data] // library marker davegut.ST-Common, line 121
		} // library marker davegut.ST-Common, line 122
	} // library marker davegut.ST-Common, line 123
	if (respData.status == "ERROR") { // library marker davegut.ST-Common, line 124
		logWarn("getDeviceListParse: ${respData}") // library marker davegut.ST-Common, line 125
	} else { // library marker davegut.ST-Common, line 126
		log.info "" // library marker davegut.ST-Common, line 127
		respData.items.each { // library marker davegut.ST-Common, line 128
			log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 129
		} // library marker davegut.ST-Common, line 130
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 131
	} // library marker davegut.ST-Common, line 132
} // library marker davegut.ST-Common, line 133

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 135
	Integer currTime = now() // library marker davegut.ST-Common, line 136
	Integer compTime // library marker davegut.ST-Common, line 137
	try { // library marker davegut.ST-Common, line 138
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 139
	} catch (e) { // library marker davegut.ST-Common, line 140
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 141
	} // library marker davegut.ST-Common, line 142
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 143
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 144
	return timeRemaining // library marker davegut.ST-Common, line 145
} // library marker davegut.ST-Common, line 146

// ~~~~~ end include (642) davegut.ST-Common ~~~~~

// ~~~~~ start include (737) davegut.Samsung-Soundbar-Sim ~~~~~
library ( // library marker davegut.Samsung-Soundbar-Sim, line 1
	name: "Samsung-Soundbar-Sim", // library marker davegut.Samsung-Soundbar-Sim, line 2
	namespace: "davegut", // library marker davegut.Samsung-Soundbar-Sim, line 3
	author: "Dave Gutheinz", // library marker davegut.Samsung-Soundbar-Sim, line 4
	description: "Simulator - Samsung Soundbar", // library marker davegut.Samsung-Soundbar-Sim, line 5
	category: "utilities", // library marker davegut.Samsung-Soundbar-Sim, line 6
	documentationLink: "" // library marker davegut.Samsung-Soundbar-Sim, line 7
) // library marker davegut.Samsung-Soundbar-Sim, line 8

def testData() { // library marker davegut.Samsung-Soundbar-Sim, line 10
	def pbStatus = "playing" // library marker davegut.Samsung-Soundbar-Sim, line 11
	def sfMode = 3 // library marker davegut.Samsung-Soundbar-Sim, line 12
	def sfDetail = "External Device" // library marker davegut.Samsung-Soundbar-Sim, line 13
	def volume = 15 // library marker davegut.Samsung-Soundbar-Sim, line 14
	def inputSource = "HDMI2" // library marker davegut.Samsung-Soundbar-Sim, line 15
	def supportedSources = ["digital", "HDMI1", "bluetooth", "HDMI2", "wifi"] // library marker davegut.Samsung-Soundbar-Sim, line 16
	def mute = "unmuted" // library marker davegut.Samsung-Soundbar-Sim, line 17
	def onOff = "on" // library marker davegut.Samsung-Soundbar-Sim, line 18
	def trackTime = 280 // library marker davegut.Samsung-Soundbar-Sim, line 19
	def trackRemain = 122 // library marker davegut.Samsung-Soundbar-Sim, line 20
	def trackData = [title: "a", artist: "b", album: "c"] // library marker davegut.Samsung-Soundbar-Sim, line 21


	return [ // library marker davegut.Samsung-Soundbar-Sim, line 24
		mediaPlayback:[ // library marker davegut.Samsung-Soundbar-Sim, line 25
			playbackStatus: [value: pbStatus], // library marker davegut.Samsung-Soundbar-Sim, line 26
			supportedPlaybackCommands:[value:[play, pause, stop]]], // library marker davegut.Samsung-Soundbar-Sim, line 27
		"samsungvd.soundFrom":[ // library marker davegut.Samsung-Soundbar-Sim, line 28
			mode:[value: sfMode], // library marker davegut.Samsung-Soundbar-Sim, line 29
			detailName:[value: sfDetail]], // library marker davegut.Samsung-Soundbar-Sim, line 30
		audioVolume:[volume:[value: volume]], // library marker davegut.Samsung-Soundbar-Sim, line 31
		mediaInputSource:[ // library marker davegut.Samsung-Soundbar-Sim, line 32
			supportedInputSources:[value: supportedSources],  // library marker davegut.Samsung-Soundbar-Sim, line 33
			inputSource:[value: inputSource]], // library marker davegut.Samsung-Soundbar-Sim, line 34
		audioMute:[mute:[value: mute]], // library marker davegut.Samsung-Soundbar-Sim, line 35
		switch:[switch:[value: onOff]], // library marker davegut.Samsung-Soundbar-Sim, line 36
		audioTrackData:[ // library marker davegut.Samsung-Soundbar-Sim, line 37
			totalTime:[value: trackTime], // library marker davegut.Samsung-Soundbar-Sim, line 38
			audioTrackData:[value: trackData], // library marker davegut.Samsung-Soundbar-Sim, line 39
			elapsedTime:[value: trackRemain]]] // library marker davegut.Samsung-Soundbar-Sim, line 40
} // library marker davegut.Samsung-Soundbar-Sim, line 41

def testResp(cmdData) { // library marker davegut.Samsung-Soundbar-Sim, line 43
	return [ // library marker davegut.Samsung-Soundbar-Sim, line 44
		cmdData: cmdData, // library marker davegut.Samsung-Soundbar-Sim, line 45
		status: [status: "OK", // library marker davegut.Samsung-Soundbar-Sim, line 46
				 results:[[id: "e9585885-3848-4fea-b0db-ece30ff1701e", status: "ACCEPTED"]]]] // library marker davegut.Samsung-Soundbar-Sim, line 47
} // library marker davegut.Samsung-Soundbar-Sim, line 48

// ~~~~~ end include (737) davegut.Samsung-Soundbar-Sim ~~~~~
