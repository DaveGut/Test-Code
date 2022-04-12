/*	===== HUBITAT Samsung Washer Using SmartThings ==========================================
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
def driverVer() { return "0.3" }
metadata {
	definition (name: "Samsung Washer (ST) Test",
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

def refresh() { getDeviceStatus() }
def xxpoll() {
	def respData = sendCmdData("main", "refresh", "refresh")
	if (respData.status == "OK") { getDeviceStatus() }
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
		logDebug("sendCmdData: [command: ${command}, status: success]")
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
//	washerOperatingState:[
//		completionTime:[value:2022-04-09T17:56:26Z, timestamp:2022-04-09T17:55:26.575Z],
//		machineState:[value:stop, timestamp:2022-04-09T17:55:26.555Z],
//		washerJobState:[value:none, timestamp:2022-04-09T17:55:26.566Z],
//		supportedMachineStates:[value:null]], 
		if (data.washerOperatingState != null) {
			def completeTime = data.washerOperatingState.completionTime.value
			def machineState = data.washerOperatingState.machineState.value
			def jobState = data.washerOperatingState.washerJobState.value
			state.opState = [completionTime: completionTime, machineState: machineState, jobState: jobState]
			stData << [opState: [completionTime: completionTime, machineState: machineState, jobState: jobState]]
		}
//	samsungce.washerDelayEnd:[remainingTime:[value:null], minimumReservableTime:[value:null]], 
		if (data["samsungce.washerDelayEnd"] != null) {
			def delayEnd = data["samsungce.washerDelayEnd"]
			state.delayEnd = delayEnd
			stData << [delayEnd: delayEnd]
		}
//	custom.jobBeginningStatus:[jobBeginningStatus:[value:null]], 
		if (data["custom.jobBeginningStatus"] != null) {
			def jobBeginStatus = data["custom.jobBeginningStatus"].jobBeginningStatus.value
			state.jobBeginStatus = jobBeginStatus
			stData << [jobBeginStatus: jobBeginStatus]
		}
//	samsungce.waterConsumptionReport:[waterConsumption:[value:null]], 
		if (data["samsungce.waterConsumptionReport"] != null) {
			def wrinklePrevent = data["samsungce.waterConsumptionReport"]
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
				stData = [status: "OK", results: resp.data]
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


//	========================================================
//	===== Installation, setup and update ===================
//	========================================================
/*
[status:OK, data:[components:[main:[samsungce.washerDelayEnd:[remainingTime:[value:null], minimumReservableTime:[value:null]], samsungce.washerWaterLevel:[supportedWaterLevel:[value:null], waterLevel:[value:null]], samsungce.welcomeMessage:[welcomeMessage:[value:null]], custom.washerWaterTemperature:[supportedWasherWaterTemperature:[value:[none, tapCold, cold, warm, hot, extraHot], timestamp:2022-03-28T19:35:57.777Z], washerWaterTemperature:[value:warm, timestamp:2022-04-09T13:01:33.658Z]], samsungce.autoDispenseSoftener:[remainingAmount:[value:null], amount:[value:null], supportedDensity:[value:null], density:[value:null], supportedAmount:[value:null]], samsungce.autoDispenseDetergent:[remainingAmount:[value:null], amount:[value:null], supportedDensity:[value:null], density:[value:null], supportedAmount:[value:null]], samsungce.deviceIdentification:[micomAssayCode:[value:20185441, timestamp:2022-03-28T19:35:57.206Z], modelName:[value:null], serialNumber:[value:null], serialNumberExtra:[value:null], modelClassificationCode:[value:20010101001031070100000000000000, timestamp:2022-03-28T19:35:57.206Z], description:[value:DA_WM_A51_20_COMMON_WF7500, timestamp:2022-03-28T19:35:57.206Z], binaryId:[value:DA_WM_A51_20_COMMON, timestamp:2022-03-28T19:35:57.206Z]], samsungce.washerWaterValve:[waterValve:[value:null], supportedWaterValve:[value:null]], washerOperatingState:[completionTime:[value:2022-04-09T17:56:26Z, timestamp:2022-04-09T17:55:26.575Z], machineState:[value:stop, timestamp:2022-04-09T17:55:26.555Z], washerJobState:[value:none, timestamp:2022-04-09T17:55:26.566Z], supportedMachineStates:[value:null]], switch:[switch:[value:off, timestamp:2022-04-09T17:55:26.399Z]], custom.washerAutoSoftener:[washerAutoSoftener:[value:null]], samsungce.washerFreezePrevent:[operatingState:[value:null]], samsungce.washerCycle:[supportedCycles:[value:[[cycle:01, supportedOptions:[soilLevel:[raw:C33E, default:normal, options:[light, down, normal, up, heavy]], spinLevel:[raw:A43F, default:high, options:[rinseHold, noSpin, low, medium, high, extraHigh]], waterTemperature:[raw:833E, default:warm, options:[tapCold, cold, warm, hot, extraHot]]]], [cycle:70, supportedOptions:[soilLevel:[raw:C53E, default:heavy, options:[light, down, normal, up, heavy]], spinLevel:[raw:A53F, default:extraHigh, options:[rinseHold, noSpin, low, medium, high, extraHigh]], waterTemperature:[raw:843E, default:hot, options:[tapCold, cold, warm, hot, extraHot]]]], [cycle:71, supportedOptions:[soilLevel:[raw:C33E, default:normal, options:[light, down, normal, up, heavy]], spinLevel:[raw:A20F, default:low, options:[rinseHold, noSpin, low, medium]], waterTemperature:[raw:830E, default:warm, options:[tapCold, cold, warm]]]], [cycle:55, supportedOptions:[soilLevel:[raw:C33E, default:normal, options:[light, down, normal, up, heavy]], spinLevel:[raw:A43F, default:high, options:[rinseHold, noSpin, low, medium, high, extraHigh]], waterTemperature:[raw:831E, default:warm, options:[tapCold, cold, warm, hot]]]], [cycle:72, supportedOptions:[soilLevel:[raw:C33E, default:normal, options:[light, down, normal, up, heavy]], spinLevel:[raw:A43F, default:high, options:[rinseHold, noSpin, low, medium, high, extraHigh]], waterTemperature:[raw:8520, default:extraHot, options:[extraHot]]]], [cycle:54, supportedOptions:[soilLevel:[raw:C33E, default:normal, options:[light, down, normal, up, heavy]], spinLevel:[raw:A43F, default:high, options:[rinseHold, noSpin, low, medium, high, extraHigh]], waterTemperature:[raw:8410, default:hot, options:[hot]]]], [cycle:73, supportedOptions:[soilLevel:[raw:C000, options:[]], spinLevel:[raw:A43F, default:high, options:[rinseHold, noSpin, low, medium, high, extraHigh]], waterTemperature:[raw:8000, options:[]]]], [cycle:74, supportedOptions:[soilLevel:[raw:C33E, default:normal, options:[light, down, normal, up, heavy]], spinLevel:[raw:A207, default:low, options:[rinseHold, noSpin, low]], waterTemperature:[raw:830E, default:warm, options:[tapCold, cold, warm]]]], [cycle:75, supportedOptions:[soilLevel:[raw:C33E, default:normal, options:[light, down, normal, up, heavy]], spinLevel:[raw:A207, default:low, options:[rinseHold, noSpin, low]], waterTemperature:[raw:810E, default:tapCold, options:[tapCold, cold, warm]]]], [cycle:76, supportedOptions:[soilLevel:[raw:C308, default:normal, options:[normal]], spinLevel:[raw:A207, default:low, options:[rinseHold, noSpin, low]], waterTemperature:[raw:830E, default:warm, options:[tapCold, cold, warm]]]], [cycle:77, supportedOptions:[soilLevel:[raw:C33E, default:normal, options:[light, down, normal, up, heavy]], spinLevel:[raw:A21F, default:low, options:[rinseHold, noSpin, low, medium, high]], waterTemperature:[raw:830E, default:warm, options:[tapCold, cold, warm]]]], [cycle:56, supportedOptions:[soilLevel:[raw:C33E, default:normal, options:[light, down, normal, up, heavy]], spinLevel:[raw:A43F, default:high, options:[rinseHold, noSpin, low, medium, high, extraHigh]], waterTemperature:[raw:8106, default:tapCold, options:[tapCold, cold]]]], [cycle:78, supportedOptions:[soilLevel:[raw:C13E, default:light, options:[light, down, normal, up, heavy]], spinLevel:[raw:A43F, default:high, options:[rinseHold, noSpin, low, medium, high, extraHigh]], waterTemperature:[raw:831E, default:warm, options:[tapCold, cold, warm, hot]]]], [cycle:7A, supportedOptions:[soilLevel:[raw:C520, default:heavy, options:[heavy]], spinLevel:[raw:A43F, default:high, options:[rinseHold, noSpin, low, medium, high, extraHigh]], waterTemperature:[raw:8410, default:hot, options:[hot]]]], [cycle:57, supportedOptions:[soilLevel:[raw:C000, options:[]], spinLevel:[raw:A520, default:extraHigh, options:[extraHigh]], waterTemperature:[raw:8520, default:extraHot, options:[extraHot]]]]], timestamp:2022-03-28T19:35:58.044Z], washerCycle:[value:Table_00_Course_77, timestamp:2022-04-09T16:57:38.040Z], referenceTable:[value:[id:Table_00], timestamp:2022-03-28T19:35:57.931Z]], samsungce.waterConsumptionReport:[waterConsumption:[value:null]], ocf:[st:[value:null], mndt:[value:null], mnfv:[value:DA_WM_A51_20_COMMON_30210227, timestamp:2022-03-28T19:35:56.530Z], mnhw:[value:ARTIK051, timestamp:2022-03-28T19:35:56.530Z], di:[value:38937b11-c1fb-b219-d3e8-3bc1500db94a, timestamp:2022-03-28T19:35:56.530Z], mnsl:[value:http://www.samsung.com, timestamp:2022-03-28T19:35:56.530Z], dmv:[value:res.1.1.0,sh.1.1.0, timestamp:2022-03-28T19:35:56.530Z], n:[value:[washer] Samsung, timestamp:2022-03-28T19:35:56.530Z], mnmo:[value:DA_WM_A51_20_COMMON|20185441|20010101001031070100000000000000, timestamp:2022-03-28T19:35:56.530Z], vid:[value:DA-WM-WM-000001, timestamp:2022-03-28T19:35:56.530Z], mnmn:[value:Samsung Electronics, timestamp:2022-03-28T19:35:56.530Z], mnml:[value:http://www.samsung.com, timestamp:2022-03-28T19:35:56.530Z], mnpv:[value:DAWIT 2.0, timestamp:2022-03-28T19:35:56.530Z], mnos:[value:TizenRT 1.0 + IPv6, timestamp:2022-03-28T19:35:56.530Z], pi:[value:38937b11-c1fb-b219-d3e8-3bc1500db94a, timestamp:2022-03-28T19:35:56.530Z], icv:[value:core.1.1.0, timestamp:2022-03-28T19:35:56.530Z]], custom.dryerDryLevel:[dryerDryLevel:[value:null], supportedDryerDryLevel:[value:null]], custom.disabledCapabilities:[disabledCapabilities:[value:[samsungce.washerCyclePreset, samsungce.waterConsumptionReport, samsungce.autoDispenseDetergent, samsungce.autoDispenseSoftener, samsungce.detergentOrder, samsungce.detergentState, samsungce.softenerOrder, samsungce.softenerState, samsungce.washerBubbleSoak, samsungce.washerFreezePrevent, custom.washerRinseCycles, custom.dryerWrinklePrevent, samsungce.washerWaterLevel, samsungce.washerWaterValve, samsungce.washerWashingTime, custom.washerAutoDetergent, custom.washerAutoSoftener, samsungce.welcomeMessage], timestamp:2022-03-28T19:35:59.670Z]], custom.washerRinseCycles:[supportedWasherRinseCycles:[value:null], washerRinseCycles:[value:null]], samsungce.driverVersion:[versionNumber:[value:21082401, timestamp:2022-03-28T19:35:56.591Z]], samsungce.kidsLock:[lockState:[value:unlocked, timestamp:2022-03-28T19:35:57.523Z]], samsungce.detergentOrder:[alarmEnabled:[value:null], orderThreshold:[value:null]], powerConsumptionReport:[powerConsumption:[value:[energy:39800, deltaEnergy:0, power:0, powerEnergy:0.0, persistedEnergy:0, energySaved:0, start:2022-04-09T17:42:42Z, end:2022-04-09T17:54:58Z], timestamp:2022-04-09T17:54:58.541Z]], samsungce.softenerOrder:[alarmEnabled:[value:null], orderThreshold:[value:null]], custom.washerSoilLevel:[supportedWasherSoilLevel:[value:[none, light, down, normal, up, heavy], timestamp:2022-03-28T19:35:57.777Z], washerSoilLevel:[value:normal, timestamp:2022-03-28T19:35:57.777Z]], samsungce.washerBubbleSoak:[status:[value:null]], samsungce.washerCyclePreset:[maxNumberOfPresets:[value:10, timestamp:2022-03-28T19:35:59.550Z], presets:[value:null]], samsungce.detergentState:[remainingAmount:[value:null], dosage:[value:null], initialAmount:[value:null], detergentType:[value:null]], refresh:[:], custom.jobBeginningStatus:[jobBeginningStatus:[value:null]], execute:[data:[value:[payload:[rt:[x.com.samsung.da.mode], if:[oic.if.baseline, oic.if.a], x.com.samsung.da.options:[AddWashIndicator_On]]], data:[href:/course/vs/0], timestamp:2022-04-09T17:55:27.046Z]], samsungce.softenerState:[remainingAmount:[value:null], dosage:[value:null], softenerType:[value:null], initialAmount:[value:null]], remoteControlStatus:[remoteControlEnabled:[value:false, timestamp:2022-03-29T19:43:22.494Z]], custom.supportedOptions:[referenceTable:[value:[id:Table_00], timestamp:2022-03-28T19:35:57.931Z], supportedCourses:[value:[01, 70, 71, 55, 72, 54, 73, 74, 75, 76, 77, 56, 78, 7A, 57], timestamp:2022-03-28T19:35:57.601Z]], samsungce.washerWashingTime:[supportedWashingTimes:[value:null], washingTime:[value:null]], custom.washerAutoDetergent:[washerAutoDetergent:[value:null]], custom.washerSpinLevel:[washerSpinLevel:[value:low, timestamp:2022-04-09T16:57:37.941Z], supportedWasherSpinLevel:[value:[rinseHold, noSpin, low, medium, high, extraHigh], timestamp:2022-03-28T19:35:57.777Z]]]]]]]


WASHER
[status:OK, data:
[components:[main:[
EXP	samsungce.washerDelayEnd:[remainingTime:[value:null], minimumReservableTime:[value:null]], 
no	samsungce.washerWaterLevel:[supportedWaterLevel:[value:null], waterLevel:[value:null]], 
no	samsungce.welcomeMessage:[welcomeMessage:[value:null]], 
no	custom.washerWaterTemperature:[
		supportedWasherWaterTemperature:[value:[none, tapCold, cold, warm, hot, extraHot], timestamp:2022-03-28T19:35:57.777Z],
		washerWaterTemperature:[value:warm, timestamp:2022-04-09T13:01:33.658Z]], 
no	samsungce.autoDispenseSoftener:[remainingAmount:[value:null],amount:[value:null], supportedDensity:[value:null], density:[value:null], supportedAmount:[value:null]], 
no	samsungce.autoDispenseDetergent:[remainingAmount:[value:null],amount:[value:null],supportedDensity:[value:null],density:[value:null],supportedAmount:[value:null]], 
no	samsungce.deviceIdentification:[DEVICE DATA], 
no	samsungce.washerWaterValve:[waterValve:[value:null], supportedWaterValve:[value:null]],
EXP	washerOperatingState:[
		completionTime:[value:2022-04-09T17:56:26Z, timestamp:2022-04-09T17:55:26.575Z],
		machineState:[value:stop, timestamp:2022-04-09T17:55:26.555Z],
		washerJobState:[value:none, timestamp:2022-04-09T17:55:26.566Z],
		supportedMachineStates:[value:null]], 
yes	switch:[switch:[value:off, timestamp:2022-04-09T17:55:26.399Z]], 
no	custom.washerAutoSoftener:[MULTIPLE CYCLES], 
EXP	samsungce.waterConsumptionReport:[waterConsumption:[value:null]], 
no	ocf:[MULTIPLE DEFINITIONS],
no	custom.dryerDryLevel:[dryerDryLevel:[value:null], supportedDryerDryLevel:[value:null]], 
no	custom.disabledCapabilities:[
no	disabledCapabilities:[value:[VARIOUS VALUE], 
no	custom.washerRinseCycles:[supportedWasherRinseCycles:[value:null],washerRinseCycles:[value:null]], 
no	samsungce.driverVersion:[versionNumber:[value:21082401, timestamp:2022-03-28T19:35:56.591Z]], 
yes	samsungce.kidsLock:[lockState:[value:unlocked, timestamp:2022-03-28T19:35:57.523Z]], 
no	samsungce.detergentOrder:[alarmEnabled:[value:null], orderThreshold:[value:null]], 
EXP	powerConsumptionReport:[		EXPERIMENT. LOG FULL RET
		powerConsumption:[value:[energy:39800, power:0, powerEnergy:0.0,
								 persistedEnergy:0, energySaved:0,
								 start:2022-04-09T17:42:42Z,end:2022-04-09T17:54:58Z],
						  timestamp:2022-04-09T17:54:58.541Z]], 
no	samsungce.softenerOrder:[alarmEnabled:[value:null], orderThreshold:[value:null]],
no	custom.washerSoilLevel:[supportedWasherSoilLevel:[value:[none, light, down, normal, up, heavy], timestamp:2022-03-28T19:35:57.777Z], washerSoilLevel:[value:normal, timestamp:2022-03-28T19:35:57.777Z]], 
no	samsungce.washerBubbleSoak:[status:[value:null]], 
no	samsungce.washerCyclePreset:[maxNumberOfPresets:[value:10, timestamp:2022-03-28T19:35:59.550Z], presets:[value:null]], 
no	samsungce.detergentState:[remainingAmount:[value:null], dosage:[value:null], initialAmount:[value:null], detergentType:[value:null]], 
yes	refresh:[:], 
EXP	custom.jobBeginningStatus:[jobBeginningStatus:[value:null]], 
NO	execute:[data:[value:[payload:[rt:[x.com.samsung.da.mode], if:[oic.if.baseline, oic.if.a], x.com.samsung.da.options:[AddWashIndicator_On]]], data:[href:/course/vs/0], timestamp:2022-04-09T17:55:27.046Z]], 
NO	samsungce.softenerState:[remainingAmount:[value:null], dosage:[value:null], softenerType:[value:null], initialAmount:[value:null]], 
NO	remoteControlStatus:[remoteControlEnabled:[value:false, timestamp:2022-03-29T19:43:22.494Z]], 
NO	custom.supportedOptions:[referenceTable:[value:[id:Table_00], timestamp:2022-03-28T19:35:57.931Z], supportedCourses:[value:[01, 70, 71, 55, 72, 54, 73, 74, 75, 76, 77, 56, 78, 7A, 57], timestamp:2022-03-28T19:35:57.601Z]], 
NO	samsungce.washerWashingTime:[supportedWashingTimes:[value:null], washingTime:[value:null]], 
NO	custom.washerAutoDetergent:[washerAutoDetergent:[value:null]], 
NO	custom.washerSpinLevel:[washerSpinLevel:[value:low, timestamp:2022-04-09T16:57:37.941Z], supportedWasherSpinLevel:[value:[rinseHold, noSpin, low, medium, high, extraHigh], timestamp:2022-03-28T19:35:57.777Z]]]]]

Washer: [stLabel:Washer, deviceId:38937b11-c1fb-b219-d3e8-3bc1500db94a, deviceName:[washer] Samsung, main:[execute, ocf, powerConsumptionReport, refresh, remoteControlStatus, switch, washerOperatingState, custom.disabledCapabilities, custom.dryerDryLevel, custom.jobBeginningStatus, custom.supportedOptions, custom.washerAutoDetergent, custom.washerAutoSoftener, custom.washerRinseCycles, custom.washerSoilLevel, custom.washerSpinLevel, custom.washerWaterTemperature, samsungce.autoDispenseDetergent, samsungce.autoDispenseSoftener, samsungce.detergentOrder, samsungce.detergentState, samsungce.deviceIdentification, samsungce.driverVersion, samsungce.kidsLock, samsungce.softenerOrder, samsungce.softenerState, samsungce.washerBubbleSoak, samsungce.washerCycle, samsungce.washerCyclePreset, samsungce.washerDelayEnd, samsungce.washerFreezePrevent, samsungce.washerWashingTime, samsungce.washerWaterLevel, samsungce.washerWaterValve, samsungce.welcomeMessage, samsungce.waterConsumptionReport]]
*/
