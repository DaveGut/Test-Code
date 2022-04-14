/*	===== HUBITAT Samsung Dryer Using SmartThings ==========================================
		Copyright 2022 Dave Gutheinz
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file
except in compliance with the License. You may obtain a copy of the License at:
		http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the
License is distributed on an  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. See the License for the specific language governing permissions
and limitations under the  License.
===== HISTORY =============================================================================
0.1		Test 1 version.
0.4		Assigned initial attributes.  Encapsulated parsing in TRY statements.
		Defined test commands (some require instructions).
===========================================================================================*/
def driverVer() { return "0.04" }
metadata {
	definition (name: "Samsung Dryer (ST) Test",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
		capability "Refresh"
		command "stDeviceList"
		attribute "wrinklePrevent", "string"
		attribute "dryingTemperature", "string"
		attribute "dryLevel", "string"
		attribute "kidsLock", "string"
		attribute "remainingTime", "number"
		attribute "machineState", "string"
		attribute "jobState", "string"
		attribute "remoteControl", "string"
//	Still Under Test		
		capability "Switch"
		command "testRefresh"
		command "setMachineState", [[
			name: "Dryer State", type: "ENUM",
			constraints: ["pause", "run", "stop"]]]
	}
	preferences {
		input ("debugLog", "bool",  
			   title: "Enable debug logging for 30 minutes", defaultValue: false)
		input ("infoLog", "bool",  
			   title: "Enable description text logging", defaultValue: true)
		input ("stApiKey", "text", title: "SmartThings API Key", defaultValue: "")
		input ("stDeviceId", "text", title: "SmartThings TV Device ID", defaultValue: "")
	}
}

def installed() {
	runIn(1, updated)
}

def updated() {
	logInfo("updated: [stApiKey: ${stApiKey}, stDeviceId: ${stDeviceId}]")
	unschedule()
	def updateData = [:]
	def status = "OK"
	def statusReason = ""
	def deviceData
	if (!stApiKey || stApiKey == "") {
		status = "failed"
		statusReason = "No stApiKey"
	} else if (!stDeviceId || stDeviceId == "") {
		status = "failed"
		statusReason = "No stDeviceId"
	} else {
		if (debug) { runIn(1800, debugOff) }
		updateData << [debugLog: debugLog, infoLog: infoLog]
		runEvery5Minutes(refresh)
		updateData << [pollInterval: "5 min"]
//		updateData << [driver: versionUpdate()]
	}
	def updateStatus = [:]
	updateStatus << [status: status]
	if (statusReason != "") {
		updateStatus << [statusReason: statusReason]
	}
	updateStatus << [updateData: updateData]
	refresh()
	logInfo("updated: ${updateStatus}")
}

def refresh() {
	def cmdUri = "https://api.smartthings.com/v1/devices/${stDeviceId.trim()}/components/main/capabilities/switch/status"
	def stData = sendGet(cmdUri, stApiKey.trim())
	if (stData.status != "OK") {
		logWarn("refresh: Error from ST Cloud: ${stData}")
		return
	}
	def onOff = stData.data.switch.value
	sendEvent(name: "switch", value: onOff)
	if (device.currentValue("switch") != onOff) {
		if (onOff == "off") {
			runEvery5Minutes(refresh)
			getDeviceStatus()
		} else {
			runEvery1Minute(refresh)
		}
		logDebug("refresh: [switch: ${onOff}]")
	}
	if (onOff == "on") { getDeviceStatus() }
}

def getDeviceStatus() {
	def logData = [:]
	cmdUri = "https://api.smartthings.com/v1/devices/${stDeviceId.trim()}/components/main/status"
	def stData = sendGet(cmdUri, stApiKey.trim())
	if (stData.status != "OK") {
		logWarn("getDeviceStatus: Error from ST Cloud: ${stData}")
		return
	}
	def resp = stData.data
	
/*	try {
		def inputSource = resp.mediaInputSource.inputSource.value
		logData << [inputSource: setEvent("inputSource", inputSource)]
	} catch (error) {
		logWarn("inputSource: ${error}")
	}*/
	
	try {
		def wrinklePrevent = resp["custom.dryerWrinklePrevent"].dryerWrinklePrevent.value
		logData << [wrinklePrevent: setEvent("wrinklePrevent", wrinklePrevent)]
	} catch (error) {
		logWarn("wrinklePrevent: ${error}")
	}
	
	try {
		def dryingTemperature = resp["samsungce.dryerDryingTemperature"].dryingTemperature.value
		logData << [dryingTemperature: setEvent("dryingTemperature", dryingTemperature)]
	} catch (error) {
		logWarn("dryingTemperature: ${error}")
	}
	
	try {
		def dryLevel = resp["custom.dryerDryLevel"].dryerDryLevel.value
		logData << [dryLevel: setEvent("dryLevel", dryLevel)]
	} catch (error) {
		logWarn("dryLevel: ${error}")
	}
	
	try {
		def kidsLock = resp["samsungce.kidsLock"].lockState.value
		logData << [kidsLock: setEvent("kidsLock", kidsLock)]
	} catch (error) {
		logWarn("kidsLock: ${error}")
	}
	
	try {
		def remainingTime = resp["samsungce.dryerDelayEnd"].remainingTime.value
		logData << [remainingTime: setEvent("remainingTime", remainingTime)]
	} catch (error) {
		logWarn("remainingTime: ${error}")
	}
	
	try {
		def machineState = resp["dryerOperatingState"].machineState.value
		logData << [machineState: setEvent("machineState", machineState)]
	} catch (error) {
		logWarn("machineState: ${error}")
	}
	
	try {
		def jobState = resp["dryerOperatingState"].dryerJobState.value
		logData << [jobState: setEvent("jobState", jobState)]
	} catch (error) {
		logWarn("jobState: ${error}")
	}
	
	try {
		def remoteControl = resp.remoteControlStatus.remoteControlEnabled.value
		logData << [remoteControl: setEvent("remoteControl", remoteControl)]
	} catch (error) {
		logWarn("remoteControl: ${error}")
	}

	if (logData != [:]) {
		logDebug("getDeviceStatus: ${logData}")
	}
}

def setEvent(event, value) {
	def status = "error in setEvent"
	if (device.currentValue(event) != value) {
		try {
			sendEvent(name: event, value: value)
			status = value
		} catch (error) {
			logWarn("[${event}: ${value}: ${error}]")
		}
	} 
	return status
}

//	===== Command Tests =====
def on() { setOnOff("on") }
def off() { setOnOff("off") }
def setOnOff(onOff) {
	def respData = sendCommand("main", "switch", onOff)
	if (respData.status == "OK") {
		refresh()
	}
}

def setMachineState(machState) {
	def respData = sendCommand("main", "dryerOperatingState", "setMachineState", [machState])
	logTrace("setMachineState: [${machState}: ${respData}]")
	if (respData.status == "OK") {
		refresh()
	}
}

def testRefresh() {
	def respData = sendCommand("main", "refresh", "refresh")
	log.trace respData
}

//	Updated.  Working on signatures for application.
def stDeviceList() {
	def stData
	if (!stApiKey || stAiKey == "") {
		stData = [status: "error", statusReason: "no stApiKey"]
	} else {
		def cmdUri = "https://api.smartthings.com/v1/devices"
		stData = sendGet(cmdUri, stApiKey.trim())
		if (stData.status == "OK") {
			log.info ""
			stData.data.items.each {
				def deviceData = [label: it.label,
								  manufacturer: it.manufacturerName,
								  presentationId: it.presentationId,
								  deviceId: it.deviceId]
				log.info "Found: ${deviceData}"
				log.info ""
			}
		}
	}
	if (stData.status == "error") {
		logWarn("stDeviceList: ${stData}")
	} else {
		logDebug("stDeviceList: Success")
	}
}


//	===== Communications Methods =====
private sendGet(cmdUri, apiKey) {
	def stData
	def sendCmdParams = [
		uri: cmdUri,
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01',
				 'Authorization': 'Bearer ' + apiKey]
	]
	try {
		httpGet(sendCmdParams) {resp ->
			if (resp.status == 200) {
				stData = [status: "OK", data: resp.data]
			} else {
				stData = [status: "error", statusReason: "HTTP status = ${resp.status}"]
			}
		}
	} catch (error) {
		stData = [status: "error", statusReason: error]
	}
	return stData
}

def sendCommand(component, capability, command, args = []) {
	def stData
	if (!stApiKey || stApiKey == "") {
		stData = [status: "error", statusReason: "no stApiKey"]
	} else {
		def cmdUri = "https://api.smartthings.com/v1/devices/${stDeviceId.trim()}/commands"
		def cmdData = [
			component: component,
			capability: capability,
			command: command,
			arguments: args
		]
		stData = sendPost(cmdUri, cmdData, stApiKey.trim())
	}
	return stData
}

private sendPost(cmdUri, cmdData, apiKey){
	logDebug("sendStDeviceCmd: [apiKey: ${apiKey}, cmdData: ${cmdData}, cmdUri: ${cmdUri}]")
	def stData
	def cmdBody = [commands: [cmdData]]
	def sendCmdParams = [
		uri: cmdUri,
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01',
				  'Authorization': 'Bearer ' + apiKey],
		body : new groovy.json.JsonBuilder(cmdBody).toString()
	]
	try {
		httpPost(sendCmdParams) {resp ->
			if (resp.status == 200 && resp.data != null) {
				stData = [status: "OK", results: resp.data.results]
			} else {
				stData = [status: "error", statusReason: "HTTP Code ${resp.status}"]
			}
	}
	} catch (e) { 
		stData = [status: "error", statusReason: "CommsError = ${e}"]
	}
	return stData
}

//	========================================================
//	===== Logging ==========================================
//	========================================================
def logTrace(msg){
	log.trace "${device.label} V${driverVer()}: ${msg}"
}

def logInfo(msg) { 
	if (infoLog == true) {
		log.info "${device.label} V${driverVer()}: ${msg}"
	}
}

def debugLogOff() {
	device.updateSetting("debugLog", [type:"bool", value: false])
	logInfo("Debug logging is false.")
}

def logDebug(msg) {
	if (debugLog == true) {
		log.debug "${device.label} V${driverVer()}: ${msg}"
	}
}

def logWarn(msg) { log.warn "${device.label} V${driverVer()}: ${msg}" }
