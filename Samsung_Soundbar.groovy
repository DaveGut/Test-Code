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
	definition (name: "ST Samsung Soundbar Test",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
		capability "Switch"
		capability "MediaInputSource"
		capability "MediaTransport"
		capability "AudioVolume"
		
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
			input ("simulate", "bool", title: "Simulation Mode", defalutValue: false)
			input ("pollInterval", "enum", title: "Poll Interval (minutes)",
				   options: ["1", "5", "10", "30"], defaultValue: "5")
			input ("debugLog", "bool",  
				   title: "Enable debug logging for 30 minutes", defaultValue: false)
			input ("infoLog", "bool",  
				   title: "Enable information logging", defaultValue: true)
		}
	}
}

//	========================================================
//	===== Installation, setup and update ===================
//	========================================================
def installed() {
	runIn(1, updated)
}

def updated() {
	def commonStatus = commonUpdate()
	if (commonStatus.status == "OK") {
		logInfo("updated: ${commonStatus}")
	} else {
		logWarn("updated: ${commonStatus}")
	}
	deviceSetup()
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

//	===== Media Input Source =====
def setInputSource(inputSource) {
	def cmdData = [
		component: "main",
		capability: "mediaInputSource",
		command: "setInputSource",
		arguments: [inputSource]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setInputSource: [cmdData: ${cmdData}, status: ${cmdStatus}]")
}

//	===== Media Transport =====
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

//	===== Audio Volume =====
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
	def newVol = curVol + 1
	if (newVol > 50) { newVol = 50 }
	setVolume(newVol)
}
def volumeDown() {
	def curVol = device.currentValue("volume")
	def newVol = curVol - 1
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

def distResp(resp, data) {
	def respLog = [:]
	if (resp.status == 200) {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
			if (data == "deviceSetup") {
				deviceSetupParse(resp.components.main)
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
	try {
		def supportedInputs =  mainData.mediaInputSource.supportedInputSources.value
		sendEvent(name: "supportedInputs", value: supportedInputs)	
		setupData << [supportedInputs: supportedInputs]
	} catch (e) { logWarn("deviceSetupParse: supportedInputs") }
	if (setupData != [:]) {
		logInfo("statusParse: ${stData}")
	}	
}

def statusParse(mainData) {
	def stData = [:]
	try {
		def onOff = mainData.switch.switch.value
		if (device.currentValue("switch") != onOff) {
			sendEvent(name: "switch", value: onOff)
			stData << [switch: onOff]
		}
	} catch (e) { logWarn("switch") }
	try {
		def volume = mainData.audioVolume.volume.value.toInteger()
		if (!device.currentValue("volume") || device.currentValue("volume").toInteger() != volume) {
			sendEvent(name: "volume", value: volume)
			stData << [volume: volume]
		}
	} catch (e) { logWarn("volume: ${e}") }

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
	} catch (e) { logWarn("mediaInputSource: ${e}") }
		
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

private asyncGet(sendData, passData = "none") { // library marker davegut.ST-Communications, line 11
log.trace "======================== asyncGet =====" // library marker davegut.ST-Communications, line 12
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 13
		logWarn("asyncGet: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 14
	} else { // library marker davegut.ST-Communications, line 15
		logDebug("asyncGet: ${sendData}, ${passData}") // library marker davegut.ST-Communications, line 16
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 17
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 18
			path: sendData.path, // library marker davegut.ST-Communications, line 19
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()]] // library marker davegut.ST-Communications, line 20
		try { // library marker davegut.ST-Communications, line 21
			asynchttpGet(sendData.parse, sendCmdParams, [reason: passData]) // library marker davegut.ST-Communications, line 22
//			asynchttpGet(sendData.parse, sendCmdParams) // library marker davegut.ST-Communications, line 23
		} catch (error) { // library marker davegut.ST-Communications, line 24
			logWarn("asyncGet: [status: error, statusReason: ${error}]") // library marker davegut.ST-Communications, line 25
		} // library marker davegut.ST-Communications, line 26
	} // library marker davegut.ST-Communications, line 27
} // library marker davegut.ST-Communications, line 28

private syncGet(path){ // library marker davegut.ST-Communications, line 30
	def respData = [:] // library marker davegut.ST-Communications, line 31
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 32
		respData << [status: "ERROR", errorMsg: "no stApiKey"] // library marker davegut.ST-Communications, line 33
		logWarn("syncGet: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 34
	} else { // library marker davegut.ST-Communications, line 35
		logDebug("syncGet: ${sendData}") // library marker davegut.ST-Communications, line 36
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 37
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 38
			path: path, // library marker davegut.ST-Communications, line 39
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()] // library marker davegut.ST-Communications, line 40
		] // library marker davegut.ST-Communications, line 41
		try { // library marker davegut.ST-Communications, line 42
			httpGet(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 43
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 44
					respData << [status: "OK", results: resp.data] // library marker davegut.ST-Communications, line 45
				} else { // library marker davegut.ST-Communications, line 46
					respData << [status: "FAILED", errorMsg: "httpCode: ${resp.status}"] // library marker davegut.ST-Communications, line 47
					def warnData = [status:"ERROR", // library marker davegut.ST-Communications, line 48
									cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 49
									httpCode: resp.status, // library marker davegut.ST-Communications, line 50
									errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 51
					logWarn("syncGet: ${warnData}") // library marker davegut.ST-Communications, line 52
				} // library marker davegut.ST-Communications, line 53
			} // library marker davegut.ST-Communications, line 54
		} catch (error) { // library marker davegut.ST-Communications, line 55
			respData << [status: "FAILED", errorMsg: "non-HTTP Error"] // library marker davegut.ST-Communications, line 56
			def warnData = [status: "ERROR", // library marker davegut.ST-Communications, line 57
							cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 58
							httpCode: "No Response", // library marker davegut.ST-Communications, line 59
							errorMsg: error] // library marker davegut.ST-Communications, line 60
			logWarn("syncGet: ${warnData}") // library marker davegut.ST-Communications, line 61
		} // library marker davegut.ST-Communications, line 62
	} // library marker davegut.ST-Communications, line 63
	return respData // library marker davegut.ST-Communications, line 64
} // library marker davegut.ST-Communications, line 65

private xxasyncPost(sendData){ // library marker davegut.ST-Communications, line 67
	def respData = [:] // library marker davegut.ST-Communications, line 68
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 69
		respData << [status: "ERROR", errorMsg: "no stApiKey"] // library marker davegut.ST-Communications, line 70
		logWarn("syncPost: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 71
	} else { // library marker davegut.ST-Communications, line 72
		logDebug("syncPost: ${sendData}") // library marker davegut.ST-Communications, line 73
		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 74
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 75
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 76
			path: sendData.path, // library marker davegut.ST-Communications, line 77
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 78
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 79
		] // library marker davegut.ST-Communications, line 80
		try { // library marker davegut.ST-Communications, line 81
			asynchttpPost(sendData.parse, sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 82
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 83
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 84
				} else { // library marker davegut.ST-Communications, line 85
					respData << [status: "FAILED", errorMsg: "httpCode: ${resp.status}"] // library marker davegut.ST-Communications, line 86
					def warnData = [status:"ERROR", // library marker davegut.ST-Communications, line 87
									cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 88
									httpCode: resp.status, // library marker davegut.ST-Communications, line 89
									errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 90
					logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 91
				} // library marker davegut.ST-Communications, line 92
			} // library marker davegut.ST-Communications, line 93
		} catch (error) { // library marker davegut.ST-Communications, line 94
			respData << [status: "FAILED", errorMsg: "non-HTTP Error"] // library marker davegut.ST-Communications, line 95
			def warnData = [status: "ERROR", // library marker davegut.ST-Communications, line 96
							cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 97
							httpCode: "No Response", // library marker davegut.ST-Communications, line 98
							errorMsg: error] // library marker davegut.ST-Communications, line 99
			logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 100
		} // library marker davegut.ST-Communications, line 101
	} // library marker davegut.ST-Communications, line 102
	return respData // library marker davegut.ST-Communications, line 103
} // library marker davegut.ST-Communications, line 104

private syncPost(sendData){ // library marker davegut.ST-Communications, line 106
log.trace "======================== syncPost =====" // library marker davegut.ST-Communications, line 107
	def respData = [:] // library marker davegut.ST-Communications, line 108
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 109
		respData << [status: "ERROR", errorMsg: "no stApiKey"] // library marker davegut.ST-Communications, line 110
		logWarn("syncPost: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 111
	} else { // library marker davegut.ST-Communications, line 112
		logDebug("syncPost: ${sendData}") // library marker davegut.ST-Communications, line 113
		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 114
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 115
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 116
			path: sendData.path, // library marker davegut.ST-Communications, line 117
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 118
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 119
		] // library marker davegut.ST-Communications, line 120
		try { // library marker davegut.ST-Communications, line 121
			httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 122
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 123
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 124
				} else { // library marker davegut.ST-Communications, line 125
					respData << [status: "FAILED", errorMsg: "httpCode: ${resp.status}"] // library marker davegut.ST-Communications, line 126
					def warnData = [status:"ERROR", // library marker davegut.ST-Communications, line 127
									cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 128
									httpCode: resp.status, // library marker davegut.ST-Communications, line 129
									errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 130
					logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 131
				} // library marker davegut.ST-Communications, line 132
			} // library marker davegut.ST-Communications, line 133
		} catch (error) { // library marker davegut.ST-Communications, line 134
			respData << [status: "FAILED", errorMsg: "non-HTTP Error"] // library marker davegut.ST-Communications, line 135
			def warnData = [status: "ERROR", // library marker davegut.ST-Communications, line 136
							cmdData: sendData.cmdData, // library marker davegut.ST-Communications, line 137
							httpCode: "No Response", // library marker davegut.ST-Communications, line 138
							errorMsg: error] // library marker davegut.ST-Communications, line 139
			logWarn("syncPost: ${warnData}") // library marker davegut.ST-Communications, line 140
		} // library marker davegut.ST-Communications, line 141
	} // library marker davegut.ST-Communications, line 142
	return respData // library marker davegut.ST-Communications, line 143
} // library marker davegut.ST-Communications, line 144

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
			parse: "distResp" // library marker davegut.ST-Common, line 95
			] // library marker davegut.ST-Common, line 96
		asyncGet(sendData, "statusParse") // library marker davegut.ST-Common, line 97
	} // library marker davegut.ST-Common, line 98
} // library marker davegut.ST-Common, line 99

def deviceSetup() { // library marker davegut.ST-Common, line 101
	if (simulate == true) { // library marker davegut.ST-Common, line 102
		def children = getChildDevices() // library marker davegut.ST-Common, line 103
		if (children) { // library marker davegut.ST-Common, line 104
			children.each { // library marker davegut.ST-Common, line 105
				it.deviceSetupParse(testData()) // library marker davegut.ST-Common, line 106
			} // library marker davegut.ST-Common, line 107
		} // library marker davegut.ST-Common, line 108
		deviceSetupParse(testData()) // library marker davegut.ST-Common, line 109
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 110
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 111
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 112
	} else { // library marker davegut.ST-Common, line 113
		def sendData = [ // library marker davegut.ST-Common, line 114
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 115
			parse: "distResp" // library marker davegut.ST-Common, line 116
			] // library marker davegut.ST-Common, line 117
		asyncGet(sendData, "deviceSetup") // library marker davegut.ST-Common, line 118
	} // library marker davegut.ST-Common, line 119
} // library marker davegut.ST-Common, line 120

def getDeviceList() { // library marker davegut.ST-Common, line 122
	def sendData = [ // library marker davegut.ST-Common, line 123
		path: "/devices", // library marker davegut.ST-Common, line 124
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 125
		] // library marker davegut.ST-Common, line 126
	asyncGet(sendData) // library marker davegut.ST-Common, line 127
} // library marker davegut.ST-Common, line 128

def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 130
	def respData // library marker davegut.ST-Common, line 131
	if (resp.status != 200) { // library marker davegut.ST-Common, line 132
		respData = [status: "ERROR", // library marker davegut.ST-Common, line 133
					httpCode: resp.status, // library marker davegut.ST-Common, line 134
					errorMsg: resp.errorMessage] // library marker davegut.ST-Common, line 135
	} else { // library marker davegut.ST-Common, line 136
		try { // library marker davegut.ST-Common, line 137
			respData = new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Common, line 138
		} catch (err) { // library marker davegut.ST-Common, line 139
			respData = [status: "ERROR", // library marker davegut.ST-Common, line 140
						errorMsg: err, // library marker davegut.ST-Common, line 141
						respData: resp.data] // library marker davegut.ST-Common, line 142
		} // library marker davegut.ST-Common, line 143
	} // library marker davegut.ST-Common, line 144
	if (respData.status == "ERROR") { // library marker davegut.ST-Common, line 145
		logWarn("getDeviceListParse: ${respData}") // library marker davegut.ST-Common, line 146
	} else { // library marker davegut.ST-Common, line 147
		log.info "" // library marker davegut.ST-Common, line 148
		respData.items.each { // library marker davegut.ST-Common, line 149
			log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 150
		} // library marker davegut.ST-Common, line 151
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 152
	} // library marker davegut.ST-Common, line 153
} // library marker davegut.ST-Common, line 154

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 156
	Integer currTime = now() // library marker davegut.ST-Common, line 157
	Integer compTime // library marker davegut.ST-Common, line 158
	try { // library marker davegut.ST-Common, line 159
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 160
	} catch (e) { // library marker davegut.ST-Common, line 161
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 162
	} // library marker davegut.ST-Common, line 163
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 164
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 165
	return timeRemaining // library marker davegut.ST-Common, line 166
} // library marker davegut.ST-Common, line 167

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
