/*	===== HUBITAT INTEGRATION VERSION =====================================================
Hubitat - Samsung TV Remote Driver Switch Only
		Copyright 2020 Dave Gutheinz
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file
except in compliance with the License. You may obtain a copy of the License at:
		http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the
License is distributed on an  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. See the License for the specific language governing permissions
and limitations under the  License.
===== DISCLAIMERS =========================================================================
	THE AUTHOR OF THIS INTEGRATION IS NOT ASSOCIATED WITH SAMSUNG. THIS CODE USES
	TECHNICAL DATA DERIVED FROM GITHUB SOURCES AND AS PERSONAL INVESTIGATION.
===== 2022 History
02.22	Created Development Driver for future ST Device Drivers
===========================================================================================*/
def driverVer() { return "1.0.0" }

metadata {
	definition (name: "ST Development",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
		command "stGetDeviceStatus", ["string"]	//	Development Tool
		command "stDeviceList"		//	Development Tool.  Move to app if I get one.
	}
	preferences {
		input ("stApiKey", "text", title: "SmartThings API Key", defaultValue: "")
	}
}

//	========================================================
//	===== Installation, setup and update ===================
//	========================================================
def installed() { }

def updated() { }

//	========================================================
//	===== Utilities ========================================
//	========================================================
def stGetDeviceStatus(stDeviceId) {
	if (!stApiKey || stAiKey == "") {
		stData = [status: "error", statusReason: "no stApiKey"]
	} else if (!stDeviceId || stDeviceId == "") {
		stData = [status: "error", statusReason: "no stDeviceId"]
	} else {
		def cmdUri = "https://api.smartthings.com/v1/devices/${stDeviceId.trim()}/status"
		stData = sendStGet(cmdUri, stApiKey.trim())
		if (stData.status == "OK") {
			if (type == "status") {
				def respData = stDeviceStatus(stData.data.components.main)
				return respData
			} else if (type == "setup") {
				def respData = stDeviceSetup(stData.data.components.main)
				return respData
			} else {
				logTrace("stGetDeviceStatus: [${stDeviceId}  :  ${stData}]")
			}
		}
	}
	if (stData.status == "error") {
		logWarn("getDevices: ${stData}")
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
	log.trace "[${device.label}, ${driverVer()}]:: ${msg}"
}

def logInfo(msg) { 
	log.info "[${device.label}, ${driverVer()}]:: ${msg}"
}

def logDebug(msg) {
	log.debug "[${device.label}, ${driverVer()}]:: ${msg}"
}

def logWarn(msg) { log.warn "[${device.label}, ${driverVer()}]:: ${msg}" }
