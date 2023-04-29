/*	===== HUBITAT INTEGRATION VERSION =====================================================
Hubitat - Smart Things Data Collection for Hubitat Environment driver development
		Copyright 2022 Dave Gutheinz
Version 1.2.	Added getCapability (gets capability definition (for custom capabilities).
===========================================================================================*/
def driverVer() { return "1.2" }
import groovy.json.JsonOutput
import org.json.JSONObject
metadata {
	definition (name: "ST Data Collect",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
		command "getCapability", [
			[name: "Capability", type: "STRING"],
			[name: "Version", type: "NUMBER"]]
	}
	preferences {
		input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "")
		if (stApiKey) {
			input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "")
		}
	}
}

def installed() { updated() }

def updated() {
	def status = "OK"
	def reason = "Success"
	def updateData = [:]
	if (!stApiKey || stApiKey == "") {
		updateData << [status: "FAILED", reason: "No stApiKey"]
	} else if (!stDeviceId || stDeviceId == "") {
		getDeviceList()
		updateData << [status: "FAILED", reason: "No stDeviceId"]
	} else {
		unschedule()
		updateData << [status: "OK"]
		updateData << [stDeviceId: stDeviceId]
		if (!getDataValue("driverVersion") || 
			getDataValue("driverVersion") != driverVer()) {
			updateDataValue("driverVersion", driverVer())
			updateData << [driverVer: driverVer()]
		}
		getDevDesc()
		runIn(3, getDeviceStatus)
	}
	if (updateData.status == "FAILED") {
		logWarn("updated: ${updateData}")
	} else {
		logInfo("updated: ${updateData}")
	}
}

def getCapability(cap, ver) {
	def sendData = [
		path: "/capabilities/${cap}/${ver}",
		parse: "capabilityParse"
		]
	asyncGet(sendData)
}

def capabilityParse(resp, data) {
	if (resp.status != 200) {
		logWarn("capabilityParse: [error: InvalidResponse, respStatus: ${resp.status}]")
	} else {
		try {
			log.trace resp.data
			def slurped = new JsonSlurper().parseText(resp.data)
			log.trace slurped
			def prettied = groovy.json.JsonOutput.toJson(slurped)
//			log.trace prettied
//			log.trace new JSONObject(prettied)
		} catch (err) {
			logWarn("capabilityParse: [noDataError: ${err}]")
		}
	}
}
		
def getDevDesc() {
	def sendData = [
		path: "/devices/${stDeviceId.trim()}",
		parse: "devDescParse"
		]
	asyncGet(sendData)
}

def devDescParse(resp, data) {
	if (resp.status != 200) {
		logWarn("devDescParse: [error: InvalidResponse, respStatus: ${resp.status}]")
	} else {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
			log.trace "deviceDescription: ${respData}"
		} catch (err) {
			logWarn("devDescParse(: [noDataError: ${err}]")
		}
	}
}

def getDeviceStatus() {
	def sendData = [
		path: "/devices/${stDeviceId.trim()}/status",
		parse: "devParse"
	]
	asyncGet(sendData)
}

def devParse(resp, data) {
	if (resp.status != 200) {
		logWarn("devParse: [error: InvalidResponse, respStatus: ${resp.status}]")
	} else {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
			log.trace "deviceStatus: ${respData}"
		} catch (err) {
			logWarn("devParse: [noDataError: ${err}]")
		}
	}
}

def commonUpdate() {
	if (!stApiKey || stApiKey == "") {
		return [status: "FAILED", reason: "No stApiKey"]
	}
	if (!stDeviceId || stDeviceId == "") {
		getDeviceList()
		return [status: "FAILED", reason: "No stDeviceId"]
	}

	unschedule()
	def updateData = [:]
	updateData << [status: "OK"]
	if (debugLog) { runIn(1800, debugLogOff) }
	updateData << [stDeviceId: stDeviceId]
	updateData << [textEnable: textEnable, logEnable: logEnable]
	if (!getDataValue("driverVersion") || 
		getDataValue("driverVersion") != driverVer()) {
		updateDataValue("driverVersion", driverVer())
		updateData << [driverVer: driverVer()]
	}
	setPollInterval(pollInterval)
	updateData << [pollInterval: pollInterval]

	runIn(5, refresh)
	return updateData
}

def setPollInterval(pollInterval) {
	logDebug("setPollInterval: ${pollInterval}")
	state.pollInterval = pollInterval
	switch(pollInterval) {
		case "10sec": 
			schedule("*/10 * * * * ?", "poll")		
			break
		case "20sec":
			schedule("*/20 * * * * ?", "poll")		
			break
		case "30sec":
			schedule("*/30 * * * * ?", "poll")		
			break
		case "1" :
		case "1min": runEvery1Minute(poll); break
		case "5" : runEvery5Minutes(poll); break
		case "5min" : runEvery5Minutes(poll); break
		case "10" : runEvery10Minutes(poll); break
		case "30" : runEvery30Minutes(poll); break
		default: runEvery10Minutes(poll)
	}
}

def deviceCommand(cmdData) {
	def respData = [:]
	if (simulate() == true) {
		respData = testResp(cmdData)
	} else if (!stDeviceId || stDeviceId.trim() == "") {
		respData << [status: "FAILED", data: "no stDeviceId"]
	} else {
		def sendData = [
			path: "/devices/${stDeviceId.trim()}/commands",
			cmdData: cmdData
		]
		respData = syncPost(sendData)
	}
	if (respData.results.status[0] != "FAILED") {
		if (cmdData.capability && cmdData.capability != "refresh") {
			refresh()
		} else {
			poll()
		}
	}
	return respData
}

def refresh() {
	if (stApiKey!= null) {
		def cmdData = [
			component: "main",
			capability: "refresh",
			command: "refresh",
			arguments: []]
		deviceCommand(cmdData)
	}
}

def poll() {
	if (simulate() == true) {
		pauseExecution(200)
		def children = getChildDevices()
		if (children) {
			children.each {
				it.statusParse(testData())
			}
		}
		statusParse(testData())
	} else if (!stDeviceId || stDeviceId.trim() == "") {
		respData = "[status: FAILED, data: no stDeviceId]"
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]")
	} else {
		def sendData = [
			path: "/devices/${stDeviceId.trim()}/status",
			parse: "distResp"
			]
		asyncGet(sendData, "statusParse")
	}
}

def deviceSetup() {
	if (simulate() == true) {
		def children = getChildDevices()
		deviceSetupParse(testData())
	} else if (!stDeviceId || stDeviceId.trim() == "") {
		respData = "[status: FAILED, data: no stDeviceId]"
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]")
	} else {
		def sendData = [
			path: "/devices/${stDeviceId.trim()}/status",
			parse: "distResp"
			]
		asyncGet(sendData, "deviceSetup")
	}
}

def getDeviceList() {
	def sendData = [
		path: "/devices",
		parse: "getDeviceListParse"
		]
	asyncGet(sendData)
}

def getDeviceListParse(resp, data) {
	def respData
	if (resp.status != 200) {
		respData = [status: "ERROR",
					httpCode: resp.status,
					errorMsg: resp.errorMessage]
	} else {
		try {
			respData = new JsonSlurper().parseText(resp.data)
		} catch (err) {
			respData = [status: "ERROR",
						errorMsg: err,
						respData: resp.data]
		}
	}
	if (respData.status == "ERROR") {
		logWarn("getDeviceListParse: ${respData}")
	} else {
		log.info ""
		respData.items.each {
			log.trace "${it.label}:   ${it.deviceId}"
		}
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>"
	}
}

def calcTimeRemaining(completionTime) {
	Integer currTime = now()
	Integer compTime
	try {
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime()
	} catch (e) {
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime()
	}
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger()
	if (timeRemaining < 0) { timeRemaining = 0 }
	return timeRemaining
}

import groovy.json.JsonSlurper

private asyncGet(sendData, passData = "none") {
	if (!stApiKey || stApiKey.trim() == "") {
		logWarn("asyncGet: [status: ERROR, errorMsg: no stApiKey]")
	} else {
		logDebug("asyncGet: ${sendData}, ${passData}")
		def sendCmdParams = [
			uri: "https://api.smartthings.com/v1",
			path: sendData.path,
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()]]
		try {
			asynchttpGet(sendData.parse, sendCmdParams, [reason: passData])
		} catch (error) {
			logWarn("asyncGet: [status: FAILED, errorMsg: ${error}]")
		}
	}
}

private syncGet(path){
	def respData = [:]
	if (!stApiKey || stApiKey.trim() == "") {
		respData << [status: "FAILED",
					 errorMsg: "No stApiKey"]
	} else {
		logDebug("syncGet: ${sendData}")
		def sendCmdParams = [
			uri: "https://api.smartthings.com/v1",
			path: path,
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()]
		]
		try {
			httpGet(sendCmdParams) {resp ->
				if (resp.status == 200 && resp.data != null) {
					respData << [status: "OK", results: resp.data]
				} else {
					respData << [status: "FAILED",
								 httpCode: resp.status,
								 errorMsg: resp.errorMessage]
				}
			}
		} catch (error) {
			respData << [status: "FAILED",
						 errorMsg: error]
		}
	}
	return respData
}

private syncPost(sendData){
	def respData = [:]
	if (!stApiKey || stApiKey.trim() == "") {
		respData << [status: "FAILED",
					 errorMsg: "No stApiKey"]
	} else {
		logDebug("syncPost: ${sendData}")
		def cmdBody = [commands: [sendData.cmdData]]
		def sendCmdParams = [
			uri: "https://api.smartthings.com/v1",
			path: sendData.path,
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()],
			body : new groovy.json.JsonBuilder(cmdBody).toString()
		]
		try {
			httpPost(sendCmdParams) {resp ->
				if (resp.status == 200 && resp.data != null) {
					respData << [status: "OK", results: resp.data.results]
				} else {
					respData << [status: "FAILED",
								 httpCode: resp.status,
								 errorMsg: resp.errorMessage]
				}
			}
		} catch (error) {
			respData << [status: "FAILED",
						 errorMsg: error]
		}
	}
	return respData
}

//	Logging during development
def listAttributes(trace = false) {
	def attrs = device.getSupportedAttributes()
	def attrList = [:]
	attrs.each {
		def val = device.currentValue("${it}")
		attrList << ["${it}": val]
	}
	if (trace == true) {
		logInfo("Attributes: ${attrList}")
	} else {
		logDebug("Attributes: ${attrList}")
	}
}

def logTrace(msg){
	if (traceLog == true) {
		log.trace "${device.displayName}-${driverVer()}: ${msg}"
	}
}

def traceLogOff() {
	device.updateSetting("traceLog", [type:"bool", value: false])
	logInfo("traceLogOff")
}

def logInfo(msg) { 
	if (textEnable || infoLog) {
		log.info "${device.displayName}-${driverVer()}: ${msg}"
	}
}

def debugLogOff() {
	device.updateSetting("logEnable", [type:"bool", value: false])
	logInfo("debugLogOff")
}

def logDebug(msg) {
	if (logEnable || debugLog) {
		log.debug "${device.displayName}-${driverVer()}: ${msg}"
	}
}

def logWarn(msg) { log.warn "${device.displayName}-${driverVer()}: ${msg}" }
