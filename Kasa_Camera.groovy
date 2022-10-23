/*	Kasa CAM Development Test
===================================================================================================*/
import groovy.json.JsonOutput
metadata {
	definition (name: "Kasa Camera",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/DeviceDrivers/camera.groovy"
			   ) {
		capability "Switch"
		command "on", [[name: "(CLOUD only)"]]
		command "off", [[name: "(CLOUD only)"]]
		capability "Configuration"
		capability "Refresh"
		command "refresh", [[name: "(CLOUD only)"]]
		command "setPollInterval", [[
			name: "Poll Interval in seconds",
			constraints: ["default", "5 seconds", "10 seconds", "15 seconds",
						  "30 seconds", "1 minute"],
			type: "ENUM"]]
		capability "Sensor"
        capability "Motion Sensor"
	}
	preferences {
		input ("deviceIp", "text", title: "KasaCam Device Ip")
		if (deviceIp) {
			input ("motionTimeout", "number",
				   title: "Motion Active Timeout (seconds)",
				   defaultValue: 30)
			input("contFns", "bool", title: "Enable CAM Settings Functions",
				  defaultValue: false)
		}
		if (contFns) {
			input("userName", "email", title: "Kasa Account User Name")
			input("userPassword", "password", title: "Kasa Account Password")
			if (getDataValue("kasaToken")) {
				input ("motionDetect", "enum", title: "Motion Detection",
				   	options: ["on", "off"], defaultValue: "off")
				input ("motionSens", "enum", title: "Motion Detect Sensitivity",
					   options: ["low", "medium", "high"], defaultValue: "low")
				input ("nightTrigger", "number", title: "Night Motion Detect Trigger Time (msec)",
					   defaultValue: 600)
				input ("dayTrigger", "number", title: "Day Motion Detect Trigger Time (msec)",
					   defaultValue: 600)
				input ("dayNight", "enum", title: "Day-night mode",
					   options: ["day", "night", "auto"], defaultValue: "auto")
				input ("bcDetect", "enum", title: "Baby Cry Detect",
					   options: ["on", "off"], defaultValue: "off")
				input ("pDetect", "enum", title: "Person Detect",
					   options: ["on", "off"], defaultValue: "off")
				input ("soundDetect", "enum", title: "Sound Detection (device dependent)",
					   options: ["on", "off"], defaultValue: "off")
				input ("resolution", "enum", title: "Video Resolution",
					   options: ["1080P", "720P", "360P"],
					   defaultValue: "360P")
				input ("ledOnOff", "enum", title: "LED On / Off",
					   options: ["on", "off"], defaultValue: "on")
			}
		}
		input ("textEnable", "bool",
			   title: "Enable descriptionText logging",
			   defaultValue: true)
		input ("logEnable", "bool",
			   title: "Enable debug logging",
			   defaultValue: false)
	}
}
def installed() {
	if (!state.pollInterval) {
		state.pollInterval = "30 seconds"
		state.lastActiveTime = 0
	}
	state.updateCreds = true
	sendEvent(name: "motion", value: "inactive")
	runIn(2, updated)
	logInfo("installed: [pollInterval: ${state.pollInterval}")
}

def updated() {
	unschedule()
	def updStatus = [:]
	if (!deviceIp) {
		logWarn("Updated: Enter the device IP and Save Preferences.")
	} else {
		if (!getDataValue("deviceIP")) {
			updStatus << [deviceIPPort: updateDeviceData()]
		}
	}
	updStatus << [contFns: contFns]

	if (contFns) {
		if (!getDataValue("kasaToken")) {
			if (!userName || !userPassword || username == "" || userPassword == "") {
				logWarn("updated: No Kasa Credentials.  Update the Kasa Creentials and Save Preferences")
				return
			}
			def tokenData = getToken()
			if (tokenData == "Error" || tokenData == "") {
				logWarn("updated: getToken failed.  Verify the Kasa Crendentials and Save Preferences again.")
				return
			} else {
				updateDataValue("kasaToken", tokenData)
				updStatus << [kasaToken: tokenData]
				tokenAvail = true
			}
			def cloudData = getCloudUrl(tokenData)
			if (cloudData == "Error" || cloudData == "") {
				logWarn("updated: getCloudUrl failed.  Verify the Kasa Crendentials and Save Preferences again.")
				return
			} else {
				updateDataValue("kasaCloudUrl", cloudData)
				updStatus << [kasaCloudUrl: cloudData]
				urlAvail = true
			}
		}
	}

	updStatus << updateCommon()
	logInfo("updated: ${updStatus}")
	if (contFns && getDataValue("kasaToken")) {
		runEvery30Minutes(getPrefs)
		schedule("0 30 2 ? * MON,WED,SAT", "getToken")
		updStatus << [updatePrefs: updatePrefs()]
		refresh()
	}
	pauseExecution(1000)
}

//	===== Basic (LAN) Functions==
def updateDeviceData() {
	def respData = [:]
	updateDataValue("deviceIP", deviceIp)
	updateDataValue("devicePort", "9999")
	def cmd = """{"system":{"get_sysinfo":{}}}"""
	def execResp = sendLanCmd(cmd, "updDevData")
	if (execResp != "OK") {
		logWarn("updateDeviceData: ${execResp}")
	}
	pauseExecution(200)
	return "${deviceIp}:9999"
}
def updDevData(resp) {
	def respData = [:]
	resp = lanPrepResp(resp)
	if (resp.lanError) {
		respData << [error: resp.lanError, data: resp]
	} else {
		updateDataValue("feature", "ipCamera")
		def model = resp.system.get_sysinfo.system.model.substring(0,5)
		def deviceId = resp.system.get_sysinfo.system.deviceId
		updateDataValue("model", model)
		updateDataValue("deviceId", deviceId)
		respData << [feature: "ipCamera", model: model,
					 deviceId: deviceId]
	}
	logInfo("updDevData: ${respData}")
}

def motionPoll() {
	def cmd = """{"system":{"get_sysinfo":{}}}"""
	def execResp = sendLanCmd(cmd, "motionParse")
	if (execResp != "OK") {
		logWarn("motionPoll: ${execResp}")
	}
}
def motionParse(resp) {
	resp = lanPrepResp(resp)
	if (resp.lanError) {
		logError("motionParse: ${resp}")
	} else {
		def status = resp.system.get_sysinfo.system
		def lastActTime = status.last_activity_timestamp
		def sysTime = status.system_time
		def deltaTime = sysTime - lastActTime
logDebug("motionParse: [lastAct: $lastActTime, sysTime: $sysTime, deltatime: $deltaTime, motionTo: $motionTimeout]")
		def a_type = status.a_type
		if (deltaTime < 300 && lastActTime > state.lastActiveTime) {
	   	 	sendEvent(name: "motion", value: "active")
			state.lastActiveTime = lastActTime
			logInfo("motionParse: [motion: active, motionTime: ${lastActTime}, type: ${a_type}]")
		} else if (device.currentValue("motion") == "active" &&
				   motionTimeout.toInteger() < sysTime - lastActTime) {
			sendEvent(name: "motion", value: "inactive")
			logInfo("motionInactive: [motion: inactive]")
		}
	}
}

//	===== Cloud Setting Functions =====
def updatePrefs() {
	def data = [
		"smartlife.cam.ipcamera.motionDetect":[
			set_is_enable:[value:"${motionDetect}"],
			set_sensitivity:[value:"${motionSens}"],
			set_min_trigger_time:[
				day_mode_value: dayTrigger,
				night_mode_value: nightTrigger
				]
		],
		"smartlife.cam.ipcamera.led":[
			set_status:[value:"${ledOnOff}"]
		],
		"smartlife.cam.ipcamera.videoControl":[
			set_resolution:[
				value:[
					[channel:1, resolution:"${resolution}"]]]
		],		
		"smartlife.cam.ipcamera.soundDetect":[
			set_is_enable:[value:"${soundDetect}"]
		],
		"smartlife.cam.ipcamera.dayNight":[
			set_mode:[value:"${dayNight}"]
		],
		"smartlife.cam.ipcamera.intelligence":[
			set_bcd_enable:[value:"${bcDetect}"],
			set_pd_enable:[value:"${pDetect}"]
		]]
	def cmd = JsonOutput.toJson(data)
	def execResp = sendKasaCmd(cmd, "parseUpdatePrefs")
	return execResp
}
def parseUpdatePrefs(resp, data) {
	def updData = [:]
	resp = cloudPrepResp(resp)
	if (resp.cloudError) {
		updData << resp
	} else {
		updData << [status: "prefsUpdated"]
	}
	logInfo("parseUpdatePrefs: ${updData}")
}

def getPrefs() {
	def data = [
		"smartlife.cam.ipcamera.motionDetect":[
			get_sensitivity:[],
			get_is_enable:[],
			get_min_trigger_time:[]
		],
		"smartlife.cam.ipcamera.switch":[
			get_is_enable:[]],
		"smartlife.cam.ipcamera.led":[
			get_status:[]],
		"smartlife.cam.ipcamera.videoControl":[
			get_resolution:[]],
		"smartlife.cam.ipcamera.soundDetect":[
			get_sensitivity:[],
			get_is_enable:[]],
		"smartlife.cam.ipcamera.dayNight":[
			get_mode:[]],
		"smartlife.cam.ipcamera.intelligence":[
			get_bcd_enable:[],
			get_pd_enable:[]],
		"smartlife.cam.ipcamera.relay":[
			get_preview_snapshot:[]]
	]
	
	def cmd = JsonOutput.toJson(data)
	def execResp = sendKasaCmd(cmd, "parseGetPrefs")
	if (execResp == "OK") {
		logDebug("getPrefs")
	} else {
		logWarn("getPrefs: ${execResp}")
	}
	return
}
def parseGetPrefs(resp, data) {
	def updData = [:]
	resp = cloudPrepResp(resp)
	if (resp.cloudError) {
		updData << resp
	} else {
		if (resp["smartlife.cam.ipcamera.led"]) {
			def ledOnOff = resp["smartlife.cam.ipcamera.led"].get_status.value
			device.updateSetting("ledOnOff", [type:"enum", value: ledOnOff])
			updData << [ledOnOff: ledOnOff]
		}
		if (resp["smartlife.cam.ipcamera.switch"]) {
			def sw = resp["smartlife.cam.ipcamera.switch"].get_is_enable.value
			sendEvent(name: "switch", value: sw, type: digital)
			updData << [switch: sw]
		}
		if (resp["smartlife.cam.ipcamera.videoControl"]) {
			def resolution = resp["smartlife.cam.ipcamera.videoControl"].get_resolution.value[0].resolution
			device.updateSetting("resolution", [type:"enum", value: resolution])
			updData << [resolution: resolution]
		}
		if (resp["smartlife.cam.ipcamera.soundDetect"]) {
			def sdEnable = resp["smartlife.cam.ipcamera.soundDetect"].get_is_enable.value
			device.updateSetting("soundDetect", [type:"enum", value: sdEnable])
			updData << [soundDetect: sdEnable]
		}
		if (resp["smartlife.cam.ipcamera.motionDetect"]) {
			def motDet = resp["smartlife.cam.ipcamera.motionDetect"].get_is_enable.value
			def sens = resp["smartlife.cam.ipcamera.motionDetect"].get_sensitivity.value
			def dayTrig = resp["smartlife.cam.ipcamera.motionDetect"].get_min_trigger_time.day_mode_value
			def nightTrig = resp["smartlife.cam.ipcamera.motionDetect"].get_min_trigger_time.night_mode_value
			device.updateSetting("motionDetect", [type:"enum", value: motDet])
			device.updateSetting("motionSens", [type:"enum", value: sens])
			device.updateSetting("dayTrigger", [type:"number", value: dayTrigger])
			device.updateSetting("nightTrigger", [type:"number", value: nightTrigger])
			updData << [motionDetect: motDet, motionSens: sens,
						dayTrigger: dayTrigger, nightTrigger: nightTrigger]
		}
		if (resp["smartlife.cam.ipcamera.dayNight"]) {
			def dayNight = resp["smartlife.cam.ipcamera.dayNight"].get_mode.value
			device.updateSetting("dayNight", [type:"enum", value: dayNight])
			updData << [dayNight: dayNight]
		}
		if (resp["smartlife.cam.ipcamera.intelligence"]) {
			def bcDet = resp["smartlife.cam.ipcamera.intelligence"].get_bcd_enable.value
			def pDet = resp["smartlife.cam.ipcamera.intelligence"].get_pd_enable.value
			device.updateSetting("bcDetect", [type:"enum", value: bcDet])
			device.updateSetting("pDetect", [type:"enum", value: pDet])
			updData << [bcDetect: bcDet, pDetect: pDet]
		}
	}
	logInfo("parseGetPrefs: ${updData}")
}

def on() { setSwitch("on") }
def off() { setSwitch("off") }
def setSwitch(onOff) {
	if (getDataValue("kasaToken")) {
		def cmd = """{"smartlife.cam.ipcamera.switch":{"set_is_enable":{"value":"${onOff}"},"get_is_enable":{}}}"""
		def execResp = sendKasaCmd(cmd, "setSwitchStatus")
		if (execResp == "OK") {
			logDebug("setSwitch")
			pauseExecution(2000)
		} else {
			logWarn("setSwitch: ${execResp}")
		}
	} else {
		logWarn("setSwitch: Not available.  Not kasaToken.")
	}
}
def setSwitchStatus(resp, data) {
	resp = cloudPrepResp(resp)
	if (resp.cloudError) {
		logError("setSwitchStatus: ${resp}")
	} else {
		def status = resp["smartlife.cam.ipcamera.switch"].get_is_enable.value
		if (device.currentValue("switch") != status) {
			sendEvent(name: "switch", value: status)
			logInfo("setSwitchStatus: [switch: ${status}]")
		}
	}
}





// ~~~~~ start include (1195) davegut.camCommon ~~~~~
library ( // library marker davegut.camCommon, line 1
	name: "camCommon", // library marker davegut.camCommon, line 2
	namespace: "davegut", // library marker davegut.camCommon, line 3
	author: "Dave Gutheinz", // library marker davegut.camCommon, line 4
	description: "camera Common Methods", // library marker davegut.camCommon, line 5
	category: "utilities", // library marker davegut.camCommon, line 6
	documentationLink: "" // library marker davegut.camCommon, line 7
) // library marker davegut.camCommon, line 8

def nameSpace() { return "davegut" } // library marker davegut.camCommon, line 10

def updateCommon() { // library marker davegut.camCommon, line 12
	def updStatus = [:] // library marker davegut.camCommon, line 13
	if (rebootDev) { // library marker davegut.camCommon, line 14
		updStatus << [rebootDev: rebootDevice()] // library marker davegut.camCommon, line 15
		return updStatus // library marker davegut.camCommon, line 16
	} // library marker davegut.camCommon, line 17
	unschedule() // library marker davegut.camCommon, line 18
	if (logEnable) { runIn(1800, debugLogOff) } // library marker davegut.camCommon, line 19
	updStatus << [textEnable: textEnable, logEnable: logEnable] // library marker davegut.camCommon, line 20
	def pollInterval = state.pollInterval // library marker davegut.camCommon, line 21
	if (pollInterval == null) { pollInterval = "30 minutes" } // library marker davegut.camCommon, line 22
	updStatus << [pollInterval: setPollInterval(pollInterval)] // library marker davegut.camCommon, line 23
//	runIn(5, listAttributes) // library marker davegut.camCommon, line 24
	return updStatus // library marker davegut.camCommon, line 25
} // library marker davegut.camCommon, line 26

def configure() { installed() } // library marker davegut.camCommon, line 28

def refresh() { getPrefs() } // library marker davegut.camCommon, line 30

def setPollInterval(interval = state.pollInterval) { // library marker davegut.camCommon, line 32
	state.pollInterval = interval // library marker davegut.camCommon, line 33
	def pollInterval = interval.substring(0,2).toInteger() // library marker davegut.camCommon, line 34
	if (interval.contains("sec")) { // library marker davegut.camCommon, line 35
		def start = Math.round((pollInterval-1) * Math.random()).toInteger() // library marker davegut.camCommon, line 36
		schedule("${start}/${pollInterval} * * * * ?", "motionPoll") // library marker davegut.camCommon, line 37
	} else { // library marker davegut.camCommon, line 38
		def start = Math.round(59 * Math.random()).toInteger() // library marker davegut.camCommon, line 39
		schedule("${start} */${pollInterval} * * * ?", "motionPoll") // library marker davegut.camCommon, line 40
	} // library marker davegut.camCommon, line 41
	logDebug("setPollInterval: interval = ${interval}.") // library marker davegut.camCommon, line 42
	return interval // library marker davegut.camCommon, line 43
} // library marker davegut.camCommon, line 44

//	===== Temporary cloud comms for stand-alone test version ===== // library marker davegut.camCommon, line 46
def getToken() { // library marker davegut.camCommon, line 47
	def message // library marker davegut.camCommon, line 48
	def termId = java.util.UUID.randomUUID() // library marker davegut.camCommon, line 49
	def cmdBody = [ // library marker davegut.camCommon, line 50
		method: "login", // library marker davegut.camCommon, line 51
		params: [ // library marker davegut.camCommon, line 52
			appType: "Kasa_Android", // library marker davegut.camCommon, line 53
			cloudUserName: "${userName}", // library marker davegut.camCommon, line 54
			cloudPassword: "${userPassword.replaceAll('&gt;', '>').replaceAll('&lt;','<')}", // library marker davegut.camCommon, line 55
			terminalUUID: "${termId}"]] // library marker davegut.camCommon, line 56
	cmdData = [uri: "https://wap.tplinkcloud.com", // library marker davegut.camCommon, line 57
			   cmdBody: cmdBody] // library marker davegut.camCommon, line 58
	def respData = sendCloudCmd(cmdData) // library marker davegut.camCommon, line 59
	if (respData.error_code == 0) { // library marker davegut.camCommon, line 60
		message = respData.result.token // library marker davegut.camCommon, line 61
	} else { // library marker davegut.camCommon, line 62
		message = "Error" // library marker davegut.camCommon, line 63
		runIn(600, getToken) // library marker davegut.camCommon, line 64
	} // library marker davegut.camCommon, line 65
	return message // library marker davegut.camCommon, line 66
} // library marker davegut.camCommon, line 67

def getCloudUrl(kasaToken) { // library marker davegut.camCommon, line 69
	def message // library marker davegut.camCommon, line 70
	def cmdData = [uri: "https://wap.tplinkcloud.com?token=${kasaToken}",  // library marker davegut.camCommon, line 71
				   cmdBody: [method: "getDeviceList"]] // library marker davegut.camCommon, line 72
	def respData = sendCloudCmd(cmdData) // library marker davegut.camCommon, line 73
	if (respData.error_code == 0) { // library marker davegut.camCommon, line 74
		def cloudDevices = respData.result.deviceList // library marker davegut.camCommon, line 75
		message = cloudDevices[0].appServerUrl // library marker davegut.camCommon, line 76
	} else { // library marker davegut.camCommon, line 77
		message << "[getCloudUrl: Devices not returned from Kasa Cloud]" // library marker davegut.camCommon, line 78
		logWarn("getCloudUrl: <b>Devices not returned from Kasa Cloud.</b> Return = ${respData}\n\r") // library marker davegut.camCommon, line 79
	} // library marker davegut.camCommon, line 80
	return message // library marker davegut.camCommon, line 81
} // library marker davegut.camCommon, line 82

def sendCloudCmd(cmdData) { // library marker davegut.camCommon, line 84
	def commandParams = [ // library marker davegut.camCommon, line 85
		uri: cmdData.uri, // library marker davegut.camCommon, line 86
		requestContentType: 'application/json', // library marker davegut.camCommon, line 87
		contentType: 'application/json', // library marker davegut.camCommon, line 88
		headers: ['Accept':'application/json; version=1, */*; q=0.01'], // library marker davegut.camCommon, line 89
		body : new groovy.json.JsonBuilder(cmdData.cmdBody).toString() // library marker davegut.camCommon, line 90
	] // library marker davegut.camCommon, line 91
	def respData // library marker davegut.camCommon, line 92
	try { // library marker davegut.camCommon, line 93
		httpPostJson(commandParams) {resp -> // library marker davegut.camCommon, line 94
			if (resp.status == 200) { // library marker davegut.camCommon, line 95
				respData = resp.data // library marker davegut.camCommon, line 96
			} else { // library marker davegut.camCommon, line 97
				def msg = "sendKasaCmd: <b>HTTP Status not equal to 200.  Protocol error.  " // library marker davegut.camCommon, line 98
				msg += "HTTP Protocol Status = ${resp.status}" // library marker davegut.camCommon, line 99
				logWarn(msg) // library marker davegut.camCommon, line 100
				respData = [error_code: resp.status, msg: "HTTP Protocol Error"] // library marker davegut.camCommon, line 101
			} // library marker davegut.camCommon, line 102
		} // library marker davegut.camCommon, line 103
	} catch (e) { // library marker davegut.camCommon, line 104
		def msg = "sendKasaCmd: <b>Error in Cloud Communications.</b> The Kasa Cloud is unreachable." // library marker davegut.camCommon, line 105
		msg += "\nAdditional Data: Error = ${e}\n\n" // library marker davegut.camCommon, line 106
		logWarn(msg) // library marker davegut.camCommon, line 107
		respData = [error_code: 9999, msg: e] // library marker davegut.camCommon, line 108
	} // library marker davegut.camCommon, line 109
	return respData // library marker davegut.camCommon, line 110
} // library marker davegut.camCommon, line 111

// ~~~~~ end include (1195) davegut.camCommon ~~~~~

// ~~~~~ start include (1196) davegut.camCommunications ~~~~~
library ( // library marker davegut.camCommunications, line 1
	name: "camCommunications", // library marker davegut.camCommunications, line 2
	namespace: "davegut", // library marker davegut.camCommunications, line 3
	author: "Dave Gutheinz", // library marker davegut.camCommunications, line 4
	description: "Camera Communications Methods", // library marker davegut.camCommunications, line 5
	category: "communications", // library marker davegut.camCommunications, line 6
	documentationLink: "" // library marker davegut.camCommunications, line 7
) // library marker davegut.camCommunications, line 8

import groovy.json.JsonSlurper // library marker davegut.camCommunications, line 10

def getPort() { // library marker davegut.camCommunications, line 12
	def port = 9999 // library marker davegut.camCommunications, line 13
	if (getDataValue("devicePort")) { // library marker davegut.camCommunications, line 14
		port = getDataValue("devicePort") // library marker davegut.camCommunications, line 15
	} // library marker davegut.camCommunications, line 16
	return port // library marker davegut.camCommunications, line 17
} // library marker davegut.camCommunications, line 18

def sendLanCmd(command, action) { // library marker davegut.camCommunications, line 20
	def errorData = "OK" // library marker davegut.camCommunications, line 21
	def myHubAction = new hubitat.device.HubAction( // library marker davegut.camCommunications, line 22
		outputXOR(command), // library marker davegut.camCommunications, line 23
		hubitat.device.Protocol.LAN, // library marker davegut.camCommunications, line 24
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, // library marker davegut.camCommunications, line 25
		 destinationAddress: "${getDataValue("deviceIP")}:${getPort()}", // library marker davegut.camCommunications, line 26
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING, // library marker davegut.camCommunications, line 27
		 parseWarning: true, // library marker davegut.camCommunications, line 28
		 timeout: 9, // library marker davegut.camCommunications, line 29
		 ignoreResponse: false, // library marker davegut.camCommunications, line 30
		 callback: action]) // library marker davegut.camCommunications, line 31
	try { // library marker davegut.camCommunications, line 32
		sendHubCommand(myHubAction) // library marker davegut.camCommunications, line 33
	} catch (e) { // library marker davegut.camCommunications, line 34
		errorData = [lanError: "lan_03", error: e] // library marker davegut.camCommunications, line 35
	} // library marker davegut.camCommunications, line 36
	return errorData // library marker davegut.camCommunications, line 37
} // library marker davegut.camCommunications, line 38
def lanPrepResp(message) { // library marker davegut.camCommunications, line 39
	def respData // library marker davegut.camCommunications, line 40
	try { // library marker davegut.camCommunications, line 41
		def resp = parseLanMessage(message) // library marker davegut.camCommunications, line 42
		if (resp.type == "LAN_TYPE_UDPCLIENT") { // library marker davegut.camCommunications, line 43
			def clearResp = inputXOR(resp.payload) // library marker davegut.camCommunications, line 44
			respData = new JsonSlurper().parseText(clearResp) // library marker davegut.camCommunications, line 45
		} else { // library marker davegut.camCommunications, line 46
			respData = [lanError: "lan_01", error: "invalid response type", respType: resp.type] // library marker davegut.camCommunications, line 47
			logDebug("parseUdp: [error: error, reason: not LAN_TYPE_UDPCLIENT, respType: ${resp.type}]") // library marker davegut.camCommunications, line 48
		} // library marker davegut.camCommunications, line 49
	} catch (err) { // library marker davegut.camCommunications, line 50
		respData = [lanError: "lan_02", error: err] // library marker davegut.camCommunications, line 51
	} // library marker davegut.camCommunications, line 52
	return respData // library marker davegut.camCommunications, line 53
} // library marker davegut.camCommunications, line 54

def sendKasaCmd(command, action = "cloudParse") { // library marker davegut.camCommunications, line 56
	def execResp = "OK" // library marker davegut.camCommunications, line 57
	def cmdBody = [ // library marker davegut.camCommunications, line 58
		method: "passthrough", // library marker davegut.camCommunications, line 59
		params: [ // library marker davegut.camCommunications, line 60
			deviceId: getDataValue("deviceId"), // library marker davegut.camCommunications, line 61
			requestData: "${command}" // library marker davegut.camCommunications, line 62
		] // library marker davegut.camCommunications, line 63
	] // library marker davegut.camCommunications, line 64
	if (!getDataValue("kasaCloudUrl") || !getDataValue("kasaToken")) { // library marker davegut.camCommunications, line 65
		execResp = [cloudError: "cloud_03", reason: "Cloud Comms not set up."] // library marker davegut.camCommunications, line 66
	} else { // library marker davegut.camCommunications, line 67
		def sendCloudCmdParams = [ // library marker davegut.camCommunications, line 68
			uri: "${getDataValue("kasaCloudUrl")}/?token=${getDataValue("kasaToken")}", // library marker davegut.camCommunications, line 69
			requestContentType: 'application/json', // library marker davegut.camCommunications, line 70
			contentType: 'application/json', // library marker davegut.camCommunications, line 71
			headers: ['Accept':'application/json; version=1, */*; q=0.01'], // library marker davegut.camCommunications, line 72
			timeout: 10, // library marker davegut.camCommunications, line 73
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.camCommunications, line 74
		] // library marker davegut.camCommunications, line 75
		try { // library marker davegut.camCommunications, line 76
			asynchttpPost(action, sendCloudCmdParams) // library marker davegut.camCommunications, line 77
		} catch (e) { // library marker davegut.camCommunications, line 78
			execResp = [cloudError: "cloud_04", date: e] // library marker davegut.camCommunications, line 79
		} // library marker davegut.camCommunications, line 80
	} // library marker davegut.camCommunications, line 81
	return execResp // library marker davegut.camCommunications, line 82
} // library marker davegut.camCommunications, line 83
def cloudPrepResp(resp) { // library marker davegut.camCommunications, line 84
	def respData // library marker davegut.camCommunications, line 85
	try { // library marker davegut.camCommunications, line 86
		response = new JsonSlurper().parseText(resp.data) // library marker davegut.camCommunications, line 87
	} catch (e) { // library marker davegut.camCommunications, line 88
		respData = [cloudError: "cloud_01", data: e] // library marker davegut.camCommunications, line 89
	} // library marker davegut.camCommunications, line 90
	if (resp.status == 200 && response.error_code == 0 && resp != []) { // library marker davegut.camCommunications, line 91
		respData = new JsonSlurper().parseText(response.result.responseData) // library marker davegut.camCommunications, line 92
	} else { // library marker davegut.camCommunications, line 93
		respData = [cloudError: "cloud_02", data: resp.data] // library marker davegut.camCommunications, line 94
	} // library marker davegut.camCommunications, line 95
	return respData // library marker davegut.camCommunications, line 96
} // library marker davegut.camCommunications, line 97

private outputXOR(command) { // library marker davegut.camCommunications, line 99
	def str = "" // library marker davegut.camCommunications, line 100
	def encrCmd = "" // library marker davegut.camCommunications, line 101
 	def key = 0xAB // library marker davegut.camCommunications, line 102
	for (int i = 0; i < command.length(); i++) { // library marker davegut.camCommunications, line 103
		str = (command.charAt(i) as byte) ^ key // library marker davegut.camCommunications, line 104
		key = str // library marker davegut.camCommunications, line 105
		encrCmd += Integer.toHexString(str) // library marker davegut.camCommunications, line 106
	} // library marker davegut.camCommunications, line 107
   	return encrCmd // library marker davegut.camCommunications, line 108
} // library marker davegut.camCommunications, line 109

private inputXOR(encrResponse) { // library marker davegut.camCommunications, line 111
	String[] strBytes = encrResponse.split("(?<=\\G.{2})") // library marker davegut.camCommunications, line 112
	def cmdResponse = "" // library marker davegut.camCommunications, line 113
	def key = 0xAB // library marker davegut.camCommunications, line 114
	def nextKey // library marker davegut.camCommunications, line 115
	byte[] XORtemp // library marker davegut.camCommunications, line 116
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.camCommunications, line 117
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.camCommunications, line 118
		XORtemp = nextKey ^ key // library marker davegut.camCommunications, line 119
		key = nextKey // library marker davegut.camCommunications, line 120
		cmdResponse += new String(XORtemp) // library marker davegut.camCommunications, line 121
	} // library marker davegut.camCommunications, line 122
	return cmdResponse // library marker davegut.camCommunications, line 123
} // library marker davegut.camCommunications, line 124

// ~~~~~ end include (1196) davegut.camCommunications ~~~~~

// ~~~~~ start include (1170) davegut.commonLogging ~~~~~
library ( // library marker davegut.commonLogging, line 1
	name: "commonLogging", // library marker davegut.commonLogging, line 2
	namespace: "davegut", // library marker davegut.commonLogging, line 3
	author: "Dave Gutheinz", // library marker davegut.commonLogging, line 4
	description: "Common Logging Methods", // library marker davegut.commonLogging, line 5
	category: "utilities", // library marker davegut.commonLogging, line 6
	documentationLink: "" // library marker davegut.commonLogging, line 7
) // library marker davegut.commonLogging, line 8

//	Logging during development // library marker davegut.commonLogging, line 10
def listAttributes(trace = false) { // library marker davegut.commonLogging, line 11
	def attrs = device.getSupportedAttributes() // library marker davegut.commonLogging, line 12
	def attrList = [:] // library marker davegut.commonLogging, line 13
	attrs.each { // library marker davegut.commonLogging, line 14
		def val = device.currentValue("${it}") // library marker davegut.commonLogging, line 15
		attrList << ["${it}": val] // library marker davegut.commonLogging, line 16
	} // library marker davegut.commonLogging, line 17
	if (trace == true) { // library marker davegut.commonLogging, line 18
		logInfo("Attributes: ${attrList}") // library marker davegut.commonLogging, line 19
	} else { // library marker davegut.commonLogging, line 20
		logDebug("Attributes: ${attrList}") // library marker davegut.commonLogging, line 21
	} // library marker davegut.commonLogging, line 22
} // library marker davegut.commonLogging, line 23

//	6.7.2 Change B.  Remove driverVer() // library marker davegut.commonLogging, line 25
def logTrace(msg){ // library marker davegut.commonLogging, line 26
	log.trace "${device.displayName}: ${msg}" // library marker davegut.commonLogging, line 27
} // library marker davegut.commonLogging, line 28

def logInfo(msg) {  // library marker davegut.commonLogging, line 30
	if (textEnable || infoLog) { // library marker davegut.commonLogging, line 31
		log.info "${device.displayName}: ${msg}" // library marker davegut.commonLogging, line 32
	} // library marker davegut.commonLogging, line 33
} // library marker davegut.commonLogging, line 34

def debugLogOff() { // library marker davegut.commonLogging, line 36
	if (logEnable) { // library marker davegut.commonLogging, line 37
		device.updateSetting("logEnable", [type:"bool", value: false]) // library marker davegut.commonLogging, line 38
	} // library marker davegut.commonLogging, line 39
	logInfo("debugLogOff") // library marker davegut.commonLogging, line 40
} // library marker davegut.commonLogging, line 41

def logDebug(msg) { // library marker davegut.commonLogging, line 43
	if (logEnable || debugLog) { // library marker davegut.commonLogging, line 44
		log.debug "${device.displayName}: ${msg}" // library marker davegut.commonLogging, line 45
	} // library marker davegut.commonLogging, line 46
} // library marker davegut.commonLogging, line 47

def logWarn(msg) { log.warn "${device.displayName}: ${msg}" } // library marker davegut.commonLogging, line 49

// ~~~~~ end include (1170) davegut.commonLogging ~~~~~
