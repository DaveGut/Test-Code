/*
bleBox Device Integration Application, Version 0/1
		Copyright 2018, 2019 Dave Gutheinz
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file 
except in compliance with the License. You may obtain a copy of the License at: 
		http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the 
License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
either express or implied. See the License for the specific language governing permissions 
and limitations under the License.
V2.0.0
Initial updates to accommodate multiple API Levels.  Currently uses deprecated
command to capture all devices.
Growth:
a.	Add driver name to metadata for each device.  If no driver, All caps NO DRIVER.
=============================================================================================*/
def appVersion() { return "TEST2.0.0" }
import groovy.json.JsonSlurper
definition(
	name: "Test bleBox Integration",
	namespace: "davegut",
	author: "Dave Gutheinz",
	description: "Application to install bleBox devices.",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	singleInstance: true,
	importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/bleBoxDevices/Application/bleboxApplication.groovy"
	)

preferences {
	page(name: "mainPage")
	page(name: "addDevicesPage")
	page(name: "listDevicesPage")
}

def installed() {
	if (!state.devices) { state.devices = [:] }
	initialize()
}

def updated() { initialize() }

def initialize() {
	logDebug("initialize")
	unschedule()
	app?.updateSetting("pollEnabled", [type:"bool", value: true])
	if (state.deviceIps) { state.remove("deviceIps") }
	if (selectedAddDevices) { addDevices() }
}

//	=====	Main Page	=====
def mainPage() {
	logDebug("mainPage")
	initialize()
	return dynamicPage(name:"mainPage",
		title:"<b>bleBox Device Manager</b>",
		uninstall: true,
		install: true) {
		section() {
			href "addDevicesPage",
				title: "<b>Install bleBox Devices</b>",
				description: "Gets device information. Then offers new devices for install.\n" +
							 "(It may take several minutes for the next page to load.)"
			href "listDevicesPage",
					title: "<b>List all available bleBox devices and update the IP address for installed devices.</b>",
					description: "Lists available devices.\n" +
								 "(It may take several minutes for the next page to load.)"
			input ("infoLog", "bool",
				   defaultValue: true,
				   required: false,
				   submitOnChange: true,
				   title: "Enable Application Info Logging")
			input ("debugLog", "bool",
				   defaultValue: false,
				   required: false,
				   submitOnChange: true,
				   title: "Enable Application Debug Logging")
			paragraph "<b>Recommendation:  Set Static IP Address in your WiFi router for all bleBox Devices."
		}
	}
}

//	=====	Add Devices	=====
def addDevicesPage() {
	state.devices = [:]
	findDevices(25, "parseDeviceData")
	def devices = state.devices
	def newDevices = [:]
	devices.each {
		def isChild = getChildDevice(it.value.dni)
		if (!isChild) {
			newDevices["${it.value.dni}"] = "${it.value.type} ${it.value.label} // ${it.value.driver}"
		}
	}
	logDebug("addDevicesPage: newDevices = ${newDevices}")
	return dynamicPage(name:"addDevicesPage",
		title:"<b>Add bleBox Devices to Hubitat</b>",
		install: true) {
	 	section() {
			input ("selectedAddDevices", "enum",
				   required: false,
				   multiple: true,
				   title: "Devices to add (${newDevices.size() ?: 0} available)",
				   description: "Use the dropdown to select devices to add.  Then select 'Done'.",
				   options: newDevices)
		}
	}
}

def parseDeviceData(response) {
	def cmdResponse = parseResponse(response)
	logDebug("parseDeviceData: <b>${cmdResponse}")
	if (cmdResponse == "error") { return }
	if (cmdResponse.device) { cmdResponse = cmdResponse.device }
	else { logWarn("parseDeviceData: invalid return data") }
	def label = cmdResponse.deviceName
	def dni = cmdResponse.id.toUpperCase()
	def type = cmdResponse.type
	def ip = cmdResponse.ip
	def typeData
	def apiLevel = 20000000
	if (cmdResponse.apiLevel) { apiLevel = cmdResponse.apiLevel.toInteger() }
	def devData = [:]
	devData["dni"] = dni
	devData["ip"] = ip
	devData["label"] = label
	devData["apiLevel"] = apiLevel
	devData["type"] = type
	devData["driver"] = getDriverName(type)
	state.devices << ["${dni}" : devData]
	def isChild = getChildDevice(dni)
	if (isChild) {
		isChild.updateDataValue("deviceIP", ip)
	}
	if (type == "switchBoxD") {
		switchBoxDCmd(ip, "parseswitchBoxDData", apiLevel)
	}
}

def parseswitchBoxDData(response) {
	def cmdResponse = parseResponse(response)
	logDebug("parseRelayData: <b>${cmdResponse}")
	if (cmdResponse == "error") { return }
	def relay0Name
	def relay1Name
	if (apiLevel > 20000000) {
		relay0Name = cmdResponse.settings.relays[0].name
		relay1Name = cmdResponse.settings.relays[0].name
	} else {
		relay0Name = cmdResponse.relays[0].name
		relay1Name = cmdResponse.relays[1].name
	}
	def devIp = convertHexToIP(response.ip)
	def device = state.devices.find { it.value.ip == devIp }
	def dni = device.value.dni
	device.value << [dni:"${dni}-0", label:"${relay0Name}", relayNumber:"0"]
	def relay1Data = ["dni": "${dni}-1",
					  "ip": device.value.ip,
					  "type": device.value.type,
					  "driver": device.value.driver,
					  "label": relay1Name,
					  "relayNumber": "1"]
	state.devices << ["${dni}-1" : relay1Data]
}

def getDriverName(type) {
	switch(type) {
		case "airSensor":
			return "bleBox airSensor"
			break
		case "dimmerBox":
			return "bleBox dimmerBox"
			break
		case "gateBox":
			return "bleBox gateBox"
			break
		case "shutterBox":
			return "bleBox shutterBox"
			break
		case "switchBox":
			return "bleBox switchBox"
			break
		case "switchBoxD":
			return "bleBox switchBoxD"
			break
		case "tempSensor":
			return "bleBox tempSensor"
			break
		case "tempSensorPro":
			return "bleBox tempSensorPro"
			break
		case "wLightBox":
			return "bleBox wLightBox"
			break
		case "wLightBoxS":
			return "bleBox wLightBoxS"
			break
		default:
			logWarn("getDriverName: Device not integrated with Hubitat")
			return "NOT INTEGRATED"
	}
}		

//	===== Add Devices =====
def addDevices() {
	logDebug("addDevices:  Devices = ${state.devices}")
	try { hub = location.hubs[0] }
	catch (error) { 
		logWarn("Hub not detected.  You must have a hub to install this app.")
		return
	}
	def hubId = hub.id
	selectedAddDevices.each { dni ->
		def isChild = getChildDevice(dni)
		if (!isChild) {
			def device = state.devices.find { it.value.dni == dni }
			def deviceData = [:]
			deviceData["applicationVersion"] = appVersion()
			deviceData["deviceIP"] = device.value.ip
			deviceData["apiLevel"] = device.value.apiLevel
			if (device.value.relayNumber) { deviceData["relayNumber"] = device.value.relayNumber }
			try {
				addChildDevice(
					"davegut",
					"bleBox ${device.value.type}",
					device.value.dni,
					hubId, [
						"label" : device.value.label,
						"name" : device.value.type,
						"data" : deviceData
					]
				)
			} catch (error) {
				logWarn("Failed to install ${device.value.label}.  Driver bleBox ${device.value.type} most likely not installed.")
			}
		}
	}
}

//	=====	Update Device IPs	=====
def listDevicesPage() {
	logDebug("listDevicesPage")
	state.devices = [:]
	findDevices(25, "parseDeviceData")
	pauseExecution(5000)
	def devices = state.devices
	def foundDevices = "<b>Found Devices (Installed / DNI / IP / Alias):</b>"
	def count = 1
	devices.each {
		def installed = false
		if (getChildDevice(it.value.dni)) { installed = true }
		foundDevices += "\n${count}:\t${installed}\t${it.value.dni}\t${it.value.ip}\t${it.value.label}"
		count += 1
	}
	return dynamicPage(name:"listDevicesPage",
		title:"<b>Available bleBox Devices on your LAN</b>",
		install: false) {
	 	section() {
			paragraph "The appliation has searched and found the below devices. If any are " +
				"missing, there may be a problem with the device.\n\n${foundDevices}\n\n" +
				"<b>RECOMMENDATION: Set Static IP Address in your WiFi router for bleBox Devices.</b>"
		}
	}
}

//	===== Recurring IP Check =====
def updateDeviceIps() {
	logDebug("updateDeviceIps: Updating Device IPs after hub reboot.")
	runIn(5, updateDevices)
}

def updateDevices() {
	if (pollEnabled == true) {
		app?.updateSetting("pollEnabled", [type:"bool", value: false])
		runIn(900, pollEnable)
	} else {
		logWarn("updateDevices: a poll was run within the last 15 minutes.  Exited.")
		return
	}
	def children = getChildDevices()
	logDebug("UpdateDevices: ${children} / ${pollEnabled}")
	app?.updateSetting("missingDevice", [type:"bool", value: false])
	children.each {
		if (it.isDisabled()) {
			logDebug("updateDevices: ${it} is disabled and not checked.")
			return
		}
		def ip = it.getDataValue("deviceIP")
		runIn(2, setMissing)
		sendGetCmd(ip, "/api/device/state", "checkValid")
		pauseExecution(3000)
	}
	runIn(2, pollIfMissing)
}

def pollIfMissing() {
	logDebug("pollIfMissing: ${missingDevice}.")
	if (missingDevice == true) {
		state.devices= [:]
		findDevices(25, parseDeviceData)
		app?.updateSetting("missingDevice", [type:"bool", value: false])
	}
}

def checkValid(response) {
	unschedule("setMissing")
	def resp = parseLanMessage(response.description)
	logDebug("checkValid: response received from ${convertHexToIP(resp.ip)}")
}

def setMissing() {
	logWarn("setMissing: Setting missingDevice to true")
	app?.updateSetting("missingDevice", [type:"bool", value: true])
}

def pollEnable() {
	logDebug("pollEnable")
	app?.updateSetting("pollEnabled", [type:"bool", value: true])
}

//	=====	bleBox Specific Communications	=====
def findDevices(pollInterval, action) {
	logDebug("findDevices: ${pollInterval} / ${action}")
	def hub
	try { hub = location.hubs[0] }
	catch (error) { 
		logWarn "Hub not detected.  You must have a hub to install this app."
		return
	}
	def hubIpArray = hub.localIP.split('\\.')
	def networkPrefix = [hubIpArray[0],hubIpArray[1],hubIpArray[2]].join(".")
	logInfo("findDevices: IP Segment = ${networkPrefix}")
	for(int i = 2; i < 254; i++) {
		def deviceIP = "${networkPrefix}.${i.toString()}"
		sendPollCmd(deviceIP, action)
		sendPollCmd("192.168.50.95", action)
		pauseExecution(pollInterval)
	}
	pauseExecution(5000)
	
	for(int i = 2; i < 254; i++) {
		def deviceIP = "${networkPrefix}.${i.toString()}"
		sendPollCmd(deviceIP, action)
		sendOldPollCmd("192.168.50.95", action)
		pauseExecution(pollInterval)
	}
}

private sendPollCmd(ip, action){
	logDebug("sendPollCmd: ${ip} / ${action}")
	try {
		sendHubCommand(new hubitat.device.HubAction("GET /info HTTP/1.1\r\nHost: ${ip}\r\n\r\n",
													hubitat.device.Protocol.LAN, null, [callback: action]))
	} catch (e) {
		logWarn("sendPollCommand: ${e}")
	}
}

private sendOldPollCmd(ip, action){
	logDebug("sendOldPollCmd: ${ip} / ${action}")
	def devices = state.devices
	devices.each {
		if (it.value.ip != ip) {
			try {
				sendHubCommand(new hubitat.device.HubAction("GET /api/device/state HTTP/1.1\r\nHost: ${ip}\r\n\r\n",
															hubitat.device.Protocol.LAN, null, [callback: action]))
			} catch (e) {
				logWarn("sendPollCommand: ${e}")
			}
		}
	}
}

private switchBoxDCmd(ip, action, apiLevel){
	logDebug("switchBoxDCmd: ${ip} / / ${action} / ${apiLevel}")
	if (apiLevel > 20000000) {
		sendHubCommand(new hubitat.device.HubAction("GET /state HTTP/1.1\r\nHost: ${ip}\r\n\r\n",
												hubitat.device.Protocol.LAN, null, [callback: action]))
	} else {
		sendHubCommand(new hubitat.device.HubAction("GET /api/relay/state HTTP/1.1\r\nHost: ${ip}\r\n\r\n",
												hubitat.device.Protocol.LAN, null, [callback: action]))
	}
}

def parseResponse(response) {
	def cmdResponse
	if(response.status != 200) {
		logWarn("parseInput: Error - ${convertHexToIP(response.ip)} // ${response.status}")
		cmdResponse = "error"
	} else if (response.body == null){
		logWarn("parseInput: ${convertHexToIP(response.ip)} // no data in command response.")
		cmdResponse = "error"
	} else {
		def jsonSlurper = new groovy.json.JsonSlurper()
        try {
        	cmdResponse = jsonSlurper.parseText(response.body)
        } catch (error) {
        	cmdResponse = "error"
        	logWarn("parseInput: error parsing body = ${response.body}")
        }
	}
	return cmdResponse
}

//	===== General Utility methods =====
def uninstalled() {
    getAllChildDevices().each { 
        deleteChildDevice(it.deviceNetworkId)
    }
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private Integer convertHexToInt(hex) { Integer.parseInt(hex,16) }

def logDebug(msg){
	if(debugLog == true) { log.debug "<b>${appVersion()}</b> ${msg}" }
}

def logInfo(msg){
	if(infoLog == true) { log.info "<b>${appVersion()}</b> ${msg}" }
}

def logTrace(msg){ log.trace "${msg}" }

def logWarn(msg) { log.warn "<b>${appVersion()}</b> ${msg}" }

//	end-of-file