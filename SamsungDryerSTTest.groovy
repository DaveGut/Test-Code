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
===========================================================================================*/
def driverVer() { return "0.03" }
metadata {
	definition (name: "Samsung Dryer (ST) Test",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
//		capability "Switch"
//		capability "Polling"
		capability "Refresh"
		attribute "kidsLock", "string"
		command "stDeviceList"
	}
	preferences {
//		input ("pollInterval","enum", title: "Poll Interval to SmartThings (minutes)",
//			   options: ["1", "5", "10", "15", "30", "60"], defaultValue: "30")
		input ("traceLog", "bool",  
			   title: "TRACE LOGGING", defaultValue: false)
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
		updateData << [stData: getDeviceStatus()]
		
		device.updateSetting("infoLog", [type:"bool", value: true])

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
	refresh
	logInfo("updated: ${updateStatus}")
}

def refresh() { 
	getDeviceStatus()
}
def xxpoll() {
//	def respData = sendCmdData("main", "refresh", "refresh")
//	if (respData.status == "OK") { getDeviceStatus() }
}

def on() { setOnOff("on") }
def off() { setOnOff("off") }
def setOnOff(onOff) {
	def respData = sendCmdData("main", "switch", onOff)
	if (respData.status == "OK" && device.currentValue("switch") != onOff) {
		sendEvent(name: "switch", value: onOff)
	}
}

def stDeviceList() {
	def stData
	if (!stApiKey || stAiKey == "") {
		stData = [status: "error", statusReason: "no stApiKey"]
	} else {
		def cmdUri = "https://api.smartthings.com/v1/devices"
		stData = sendStGet(cmdUri, stApiKey.trim())
		if (stData.status == "OK") {
			logInfo{""}
			stData.data.items.each {
				def deviceData = [stLabel: it.label, 
								  deviceId: it.deviceId, 
								  deviceName: it.name]
				def components = it.components
				components.each {
					def compData = [:]
					def capabilities = it.capabilities
					def capData = []
					capabilities.each {
						capData << it.id
					}
					compData << ["${it.id}": capData]
					deviceData << compData
				}
				logInfo("<b>${deviceData.stLabel}</b>: ${deviceData}")
				logInfo{""}
			}
		}
	}
	if (stData.status == "error") {
		logWarn("stDeviceList: ${stData}")
	} else {
		logDebug("stDeviceList: Success")
	}
}

def sendCmdData(component, capability, command, args = []) {
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
		stData = sendStPost(cmdUri, cmdData, stApiKey.trim())
	}
	if (stData.status == "error") {
		logWarn("getDevDesc: [command: ${command}, response: ${stData}]")
	} else {
		logDebug("sendCmdData: [command: ${command}, status: ${stData}]")
	}
	return stData
}

def getDeviceStatus() {
	def stData = [:]
	if (!stApiKey || stAiKey == "") {
		stData = [status: "error", statusReason: "no stApiKey"]
	} else if (!stDeviceId || stDeviceId == "") {
		stData = [status: "error", statusReason: "no stDeviceId"]
	} else {
		def cmdUri = "https://api.smartthings.com/v1/devices/${stDeviceId.trim()}/status"
		stData = sendStGet(cmdUri, stApiKey.trim())
		if (stData.status == "OK") {
			def respData = deviceStatus(stData.data.components.main)
			return respData
		}
	}
	if (stData.status == "error") {
		logWarn("getDevices: ${stData}")
	}
}

def deviceStatus(data) {
	if (traceLog) {
		log.trace data
	}
	def stData = [:]
//	switch:[switch:[value:off, timestamp:2022-04-09T20:02:05.188Z]],
	def onOff = data.switch.switch.value
	if (device.currentValue("switch") != onOff) {
//		sendEvent(name: "switch", value: onOff)
//		stData << [switch: onOff]
		if (onOff == "on") {
//			runEvery1Minute(refresh)
			runEvery5Minutes(refresh)
		} else {
			runEvery5Minutes(refresh)
		}
	}
	
	
	
sendEvent(name: "switch", value: onOff)
stData << [switch: onOff]
	
	
	
//	if (onOff = "on") {
//	samsungce.kidsLock:[lockState:[value:unlocked, timestamp:2022-03-28T19:41:15.099Z]],
		if (data["samsungce.kidsLock"] != null) {
			def kidsLock = data["samsungce.kidsLock"].lockState.value
			sendEvent(name: "kidsLock", value: kidsLock)
			stData << [kidsLock: kidsLock]
		}
//	powerConsumptionReport:[powerConsumption:[value:[energy:626400, deltaEnergy:200, power:0, powerEnergy:0.0, persistedEnergy:0, energySaved:0, start:2022-04-09T19:52:23Z, end:2022-04-09T19:57:57Z], timestamp:2022-04-09T19:57:57.648Z]], 
		if (data.powerConsumptionReport != null) {
			def pwrConRep = data.powerConsumptionReport.powerConsumption.value
			state.pwrConRep = pwrConRep
			stData << [pwrConRep: pwrConRep]
		}
//	dryerOperatingState:[completionTime:[value:2022-04-09T21:17:05Z, timestamp:2022-04-09T20:02:05.880Z], 
//						  machineState:[value:stop, timestamp:2022-04-09T20:02:05.267Z], 
//						  supportedMachineStates:[value:null], 
//						  dryerJobState:[value:none, timestamp:2022-04-09T20:02:05.458Z]], - may want to use for notification.
		if (data.dryerOperatingState != null) {
			def completeTime = data.dryerOperatingState.completionTime.value
			def machineState = data.dryerOperatingState.machineState.value
			def jobState = data.dryerOperatingState.dryerJobState.value
			state.opState = [completionTime: completionTime, machineState: machineState, jobState: jobState]
			stData << [opState: [completionTime: completionTime, machineState: machineState, jobState: jobState]]
		}
//	samsungce.dryerDelayEnd:[remainingTime:[value:null]], 
		if (data["samsungce.dryerDelayEnd"] != null) {
			def delayEnd = data["samsungce.dryerDelayEnd"]
			state.delayEnd = delayEnd
			stData << [delayEnd: delayEnd]
		}
//	custom.jobBeginningStatus:[jobBeginningStatus:[value:null]], 
		if (data["custom.jobBeginningStatus"] != null) {
			def jobBeginStatus = data["custom.jobBeginningStatus"].jobBeginningStatus.value
			state.jobBeginStatus = jobBeginStatus
			stData << [jobBeginStatus: jobBeginStatus]
		}
//	custom.dryerWrinklePrevent:[operatingState:[value:null], dryerWrinklePrevent:[value:off, timestamp:2022-03-28T19:41:13.634Z]], 
		if (data["custom.dryerWrinklePrevent"] != null) {
			def wrinklePrevent = data["custom.dryerWrinklePrevent"]
			state.wrinklePrevent = wrinklePrevent
			stData << [wrinklePrevent: wrinklePrevent]
		}
//	}
	if (stData != [:]) {
		logInfo("stDeviceStatus: ${stData}")
	}
	return stData
}

private sendStGet(cmdUri, apiKey) {
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

private sendStPost(cmdUri, cmdData, apiKey){
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


/*
[status:OK, data:[components:[main:[custom.dryerWrinklePrevent:[operatingState:[value:null], dryerWrinklePrevent:[value:off, timestamp:2022-03-28T19:41:13.634Z]], samsungce.dryerDryingTemperature:[dryingTemperature:[value:medium, timestamp:2022-04-09T20:02:05.594Z], supportedDryingTemperature:[value:[none, extraLow, low, mediumLow, medium, high], timestamp:2022-03-28T19:41:13.634Z]], samsungce.welcomeMessage:[welcomeMessage:[value:null]], samsungce.dryerCyclePreset:[maxNumberOfPresets:[value:10, timestamp:2022-04-03T16:00:38.127Z], presets:[value:null]], samsungce.deviceIdentification:[micomAssayCode:[value:20185441, timestamp:2022-03-28T19:41:13.603Z], modelName:[value:null], serialNumber:[value:null], serialNumberExtra:[value:null], modelClassificationCode:[value:30000001001031000100000000000000, timestamp:2022-03-28T19:41:13.603Z], description:[value:DA_WM_A51_20_COMMON_DV45K6500Ex0, timestamp:2022-04-03T15:53:57.781Z], binaryId:[value:DA_WM_A51_20_COMMON, timestamp:2022-04-03T15:53:57.781Z]], switch:[switch:[value:off, timestamp:2022-04-09T20:02:05.188Z]], samsungce.dryerFreezePrevent:[operatingState:[value:null]], ocf:[st:[value:null], mndt:[value:null], mnfv:[value:DA_WM_A51_20_COMMON_30210227, timestamp:2022-04-03T16:00:36.424Z], mnhw:[value:ARTIK051, timestamp:2022-04-03T15:53:56.204Z], di:[value:c98c12e1-b822-e22f-c98c-b14ff889c897, timestamp:2022-03-28T19:39:25.368Z], mnsl:[value:null], dmv:[value:1.2.1, timestamp:2022-03-28T19:41:15.224Z], n:[value:[dryer] Samsung, timestamp:2022-03-28T19:41:15.224Z], mnmo:[value:DA_WM_A51_20_COMMON|20185441|30000001001031000100000000000000, timestamp:2022-04-03T15:53:56.204Z], vid:[value:DA-WM-WD-000001, timestamp:2022-03-28T19:39:25.368Z], mnmn:[value:Samsung Electronics, timestamp:2022-03-28T19:39:25.368Z], mnml:[value:http://www.samsung.com, timestamp:2022-03-28T19:39:25.368Z], mnpv:[value:DAWIT 2.0, timestamp:2022-04-03T15:53:56.204Z], mnos:[value:TizenRT 1.0 + IPv6, timestamp:2022-04-03T15:53:56.204Z], pi:[value:c98c12e1-b822-e22f-c98c-b14ff889c897, timestamp:2022-03-28T19:39:25.368Z], icv:[value:core.1.1.0, timestamp:2022-03-28T19:39:25.368Z]], custom.dryerDryLevel:[dryerDryLevel:[value:normal, timestamp:2022-04-09T20:02:05.744Z], supportedDryerDryLevel:[value:[none, damp, less, normal, more, very], timestamp:2022-03-28T19:41:13.634Z]], samsungce.dryerAutoCycleLink:[dryerAutoCycleLink:[value:null]], samsungce.dryerCycle:[dryerCycle:[value:Table_00_Course_01, timestamp:2022-04-09T20:02:05.165Z], supportedCycles:[value:[[cycle:01, supportedOptions:[dryingLevel:[raw:D33E, default:normal, options:[damp, less, normal, more, very]], dryingTemperature:[raw:8410, default:medium, options:[medium]]]], [cycle:70, supportedOptions:[dryingLevel:[raw:D33E, default:normal, options:[damp, less, normal, more, very]], dryingTemperature:[raw:8520, default:high, options:[high]]]], [cycle:71, supportedOptions:[dryingLevel:[raw:D33E, default:normal, options:[damp, less, normal, more, very]], dryingTemperature:[raw:8520, default:high, options:[high]]]], [cycle:77, supportedOptions:[dryingLevel:[raw:D33E, default:normal, options:[damp, less, normal, more, very]], dryingTemperature:[raw:8410, default:medium, options:[medium]]]], [cycle:76, supportedOptions:[dryingLevel:[raw:D308, default:normal, options:[normal]], dryingTemperature:[raw:8204, default:low, options:[low]]]], [cycle:75, supportedOptions:[dryingLevel:[raw:D33E, default:normal, options:[damp, less, normal, more, very]], dryingTemperature:[raw:8102, default:extraLow, options:[extraLow]]]], [cycle:74, supportedOptions:[dryingLevel:[raw:D308, default:normal, options:[normal]], dryingTemperature:[raw:8410, default:medium, options:[medium]]]], [cycle:6F, supportedOptions:[dryingLevel:[raw:D520, default:very, options:[very]], dryingTemperature:[raw:8520, default:high, options:[high]]]], [cycle:7C, supportedOptions:[dryingLevel:[raw:D000, options:[]], dryingTemperature:[raw:8520, default:high, options:[high]]]], [cycle:7D, supportedOptions:[dryingLevel:[raw:D000, options:[]], dryingTemperature:[raw:8520, default:high, options:[high]]]], [cycle:7E, supportedOptions:[dryingLevel:[raw:D000, options:[]], dryingTemperature:[raw:8000, options:[]]]], [cycle:7F, supportedOptions:[dryingLevel:[raw:D000, options:[]], dryingTemperature:[raw:853E, default:high, options:[extraLow, low, mediumLow, medium, high]]]], [cycle:80, supportedOptions:[dryingLevel:[raw:D000, options:[]], dryingTemperature:[raw:8520, default:high, options:[high]]]], [cycle:81, supportedOptions:[dryingLevel:[raw:D33E, default:normal, options:[damp, less, normal, more, very]], dryingTemperature:[raw:8102, default:extraLow, options:[extraLow]]]]], timestamp:2022-03-28T19:41:15.122Z], referenceTable:[value:[id:Table_00], timestamp:2022-03-28T19:41:15.219Z]], custom.disabledCapabilities:[disabledCapabilities:[value:[samsungce.dryerCyclePreset, samsungce.detergentOrder, samsungce.detergentState, samsungce.dryerFreezePrevent, samsungce.welcomeMessage], timestamp:2022-04-03T15:53:59.721Z]], samsungce.driverVersion:[versionNumber:[value:21082401, timestamp:2022-03-28T19:39:25.388Z]], samsungce.kidsLock:[lockState:[value:unlocked, timestamp:2022-03-28T19:41:15.099Z]], samsungce.detergentOrder:[alarmEnabled:[value:null], orderThreshold:[value:null]], powerConsumptionReport:[powerConsumption:[value:[energy:626400, deltaEnergy:200, power:0, powerEnergy:0.0, persistedEnergy:0, energySaved:0, start:2022-04-09T19:52:23Z, end:2022-04-09T19:57:57Z], timestamp:2022-04-09T19:57:57.648Z]], dryerOperatingState:[completionTime:[value:2022-04-09T21:17:05Z, timestamp:2022-04-09T20:02:05.880Z], machineState:[value:stop, timestamp:2022-04-09T20:02:05.267Z], supportedMachineStates:[value:null], dryerJobState:[value:none, timestamp:2022-04-09T20:02:05.458Z]], samsungce.detergentState:[remainingAmount:[value:null], dosage:[value:null], initialAmount:[value:null], detergentType:[value:null]], samsungce.dryerDelayEnd:[remainingTime:[value:null]], refresh:[:], custom.jobBeginningStatus:[jobBeginningStatus:[value:null]],execute:[data:[value:[payload:[rt:[x.com.samsung.da.operation], if:[oic.if.baseline, oic.if.a], x.com.samsung.da.state:Ready, x.com.samsung.da.remainingTime:01:15:00, x.com.samsung.da.progressPercentage:1, x.com.samsung.da.progress:None, x.com.samsung.da.supportedProgress:[None, Drying, Cooling, Finish]]], data:[href:/operational/state/vs/0], timestamp:2022-04-09T20:02:05.880Z]], remoteControlStatus:[remoteControlEnabled:[value:false, timestamp:2022-03-29T19:45:26.777Z]], custom.supportedOptions:[referenceTable:[value:[id:Table_00], timestamp:2022-03-28T19:41:15.219Z], supportedCourses:[value:[01, 70, 71, 77, 76, 75, 74, 6F, 7C, 7D, 7E, 7F, 80, 81], timestamp:2022-03-28T19:41:15.122Z]], samsungce.dryerDryingTime:[supportedDryingTime:[value:[0, 20, 30, 40, 50, 60], timestamp:2022-03-28T19:41:13.634Z], dryingTime:[value:0, unit:min, timestamp:2022-04-09T20:02:05.740Z]]]]]]

custom.disabledCapabilities:[
	disabledCapabilities:[
		value:[
			samsungce.dryerCyclePreset, 
			samsungce.detergentOrder, 
			samsungce.detergentState, s
			amsungce.dryerFreezePrevent, 
			samsungce.welcomeMessage], timestamp:2022-04-03T15:53:59.721Z]]


execute:[
	data:[
		value:[
			payload:[
				rt:[x.com.samsung.da.operation],
				if:[oic.if.baseline, oic.if.a],
				x.com.samsung.da.state:Ready,
				x.com.samsung.da.remainingTime:01:15:00,
				x.com.samsung.da.progressPercentage:1,
				x.com.samsung.da.progress:None,
				x.com.samsung.da.supportedProgress:[None, Drying, Cooling, Finish]
			]
		], 
		data:[href:/operational/state/vs/0], timestamp:2022-04-09T20:02:05.880Z]], 



DRYER Status Message
[status:OK, data:
 [components:[main:[
NO	 custom.dryerWrinklePrevent:[operatingState:[value:null], dryerWrinklePrevent:[value:off, timestamp:2022-03-28T19:41:13.634Z]], 
NO	 samsungce.dryerDryingTemperature:[dryingTemperature:[value:medium, timestamp:2022-04-09T20:02:05.594Z], 
									   supportedDryingTemperature:[value:[none, extraLow, low, mediumLow, medium, high], 
																   timestamp:2022-03-28T19:41:13.634Z]], 
NO	 samsungce.welcomeMessage:[welcomeMessage:[value:null]], 
NO	 samsungce.dryerCyclePreset:[maxNumberOfPresets:[value:10, timestamp:2022-04-03T16:00:38.127Z], presets:[value:null]], 
NO	 samsungce.deviceIdentification:[VARIOUS VALUES], 
YES	 switch:[switch:[value:off, timestamp:2022-04-09T20:02:05.188Z]], 
NO	 samsungce.dryerFreezePrevent:[operatingState:[value:null]], 
NO	 ocf:[VARIOUS VALUES], 
NO	 custom.dryerDryLevel:[dryerDryLevel:[value:normal, timestamp:2022-04-09T20:02:05.744Z], 
						   supportedDryerDryLevel:[value:[none, damp, less, normal, more, very], timestamp:2022-03-28T19:41:13.634Z]], 
NO	 samsungce.dryerAutoCycleLink:[dryerAutoCycleLink:[value:null]], 
NO	 samsungce.dryerCycle:[VARIOUS VALUES], 
NO	 custom.disabledCapabilities:[VARIOUS VALUES], 
NO	 samsungce.driverVersion:[versionNumber:[value:21082401, timestamp:2022-03-28T19:39:25.388Z]], 
YES	 samsungce.kidsLock:[lockState:[value:unlocked, timestamp:2022-03-28T19:41:15.099Z]], 
NO	 samsungce.detergentOrder:[alarmEnabled:[value:null], orderThreshold:[value:null]], 
EXP	 powerConsumptionReport:[powerConsumption:[value:[energy:626400, deltaEnergy:200, power:0, powerEnergy:0.0, persistedEnergy:0, energySaved:0, start:2022-04-09T19:52:23Z, end:2022-04-09T19:57:57Z], timestamp:2022-04-09T19:57:57.648Z]], 
EXP	 dryerOperatingState:[completionTime:[value:2022-04-09T21:17:05Z, timestamp:2022-04-09T20:02:05.880Z], 
						  machineState:[value:stop, timestamp:2022-04-09T20:02:05.267Z], 
						  supportedMachineStates:[value:null], 
						  dryerJobState:[value:none, timestamp:2022-04-09T20:02:05.458Z]], 
NO	 samsungce.detergentState:[remainingAmount:[value:null], dosage:[value:null], initialAmount:[value:null], detergentType:[value:null]], 
EXP	 samsungce.dryerDelayEnd:[remainingTime:[value:null]], 
YES	 refresh:[:], 
EXP	 custom.jobBeginningStatus:[jobBeginningStatus:[value:null]], 
NO	 execute:[VARIOUS], 
NO	 remoteControlStatus:[remoteControlEnabled:[value:false, timestamp:2022-03-29T19:45:26.777Z]], 
NO	 custom.supportedOptions:[referenceTable:[value:[id:Table_00], timestamp:2022-03-28T19:41:15.219Z], supportedCourses:[value:[01, 70, 71, 77, 76, 75, 74, 6F, 7C, 7D, 7E, 7F, 80, 81], timestamp:2022-03-28T19:41:15.122Z]], 
NO	 samsungce.dryerDryingTime:[supportedDryingTime:[value:[0, 20, 30, 40, 50, 60], timestamp:2022-03-28T19:41:13.634Z], dryingTime:[value:0, unit:min, timestamp:2022-04-09T20:02:05.740Z]]]]]]

Dryer: [stLabel:Dryer, deviceId:c98c12e1-b822-e22f-c98c-b14ff889c897, deviceName:[dryer] Samsung, main:[ocf, execute, refresh, switch, remoteControlStatus, dryerOperatingState, powerConsumptionReport, custom.disabledCapabilities, custom.dryerDryLevel, custom.dryerWrinklePrevent, custom.jobBeginningStatus, custom.supportedOptions, samsungce.detergentOrder, samsungce.detergentState, samsungce.deviceIdentification, samsungce.driverVersion, samsungce.dryerAutoCycleLink, samsungce.dryerCycle, samsungce.dryerCyclePreset, samsungce.dryerDelayEnd, samsungce.dryerDryingTemperature, samsungce.dryerDryingTime, samsungce.dryerFreezePrevent, samsungce.kidsLock, samsungce.welcomeMessage]]



*/
