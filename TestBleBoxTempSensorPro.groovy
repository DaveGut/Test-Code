/*
===== Blebox Hubitat Integration Driver 2021 Updates
	Copyright 2021, Dave Gutheinz
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

DISCLAIMER: The author of this integration is not associated with blebox.  This code uses the blebox
open API documentation for development and is intended for integration into the Hubitat Environment.

===== Hiatory =====
7.30.21	Various edits to update to latest bleBox API Levels.
	a.	tempSensorPro:  New driver based on Temp Sensor
		1.	Single parent and four children
		2.	Only support single API level (for now)
		3.	Includes temp Offset for each sensor.
This is the parent driver.  The children drivers are titled bleBox tempSensorChild and are installed
from this driver.
*/
//	===== Definitions, Installation and Updates =====
def driverVer() { return "TEST2.0.0" }
def apiLevel() { return 20210413 }	//	bleBox latest API Level, 7.6.2021

metadata {
	definition (name: "bleBox tempSensorPro",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/bleBoxDevices/Drivers/tempSensor.groovy"
			   ) {
		capability "Refresh"
		attribute "commsError", "bool"
	}
	preferences {
		input ("refreshInterval", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"], defaultValue: "30")
		input ("addChild", "bool", title: "Add New Child tempSensors", defaultValue: false)
		input ("debug", "bool", title: "Enable debug logging", defaultValue: false)
		input ("descriptionText", "bool", title: "Enable description text logging", defaultValue: true)
	}
}

def installed() {
	logInfo("Installing...")
	getChildData()
	runIn(5, updated)
}

def updated() {
	logInfo("Updating...")
	unschedule()

	switch(refreshInterval) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		default: runEvery30Minutes(refresh)
	}
	state.errorCount = 0
	updateDataValue("driverVersion", driverVer())
	if (apiLevel() > getDataValue("apiLevel").toInteger()) {
		state.apiWarn = "<b>Device api software is not the latest available. Consider updating."
	} else {
		state.remove("apiWarn")
	}

	//	update data based on preferences
	if (debug) { runIn(1800, debugOff) }
	logInfo("Debug logging is: ${debug}.")
	logInfo("Description text logging is ${descriptionText}.")
	logInfo("Refresh interval set for every ${refreshInterval} minute(s).")
	
	if (addChild) {
		getChildData()
		pauseExecution(5000)
		device.updateSetting("addChild",[type:"bool", value:false])
	}

	refresh()
}

def getChildData() {
	logInfo("getChildData: Getting data for child devices")
	sendGetCmd("/api/settings/state", "createChildren")
}

def createChildren(response) {
	def cmdResponse = parseInput(response)
	logDebug("createChildren: ${cmdResponse}")
	def settingsArrays = cmdResponse.settings.multiSensor
	
	settingsArrays.each {
		def sensorDni = "${device.getDeviceNetworkId()}-${it.id}"
		def isChild = getChildDevice(sensorDni)
		if (!isChild && it.settings.enabled.toInteger() == 1) {
			try {
				addChildDevice("davegut", "bleBox tempSensorChild", sensorDni, [
					"label":it.settings.name, 
					"name":"tempSensorChild",
					"apiLevel":getDataValue("apiLevel"), 
					"tempOffset":it.settings.userTempOffset, 
					"sensorId":it.id.toString()])
				logInfo("Installed ${it.settings.name}.")
			} catch (error) {
				logWarn("Failed to install device. Device: ${device}, sensorId = ${it.id}.")
				logWarn(error)
			}
		}
	}
}

def updateDeviceSettings(response) {
	def cmdResponse = parseInput(response)
	logDebug("createChildren: ${cmdResponse}")
	def settingsArrays = cmdResponse.settings.multiSensor

	def children = getChildDevices()
	children.each { it.updateDeviceSettings(settingsArrays) }
}

//	===== Commands and Parse Returns =====
def refresh() {
	logDebug("refresh.")
	sendGetCmd("/state", "commandParse")
}

def commandParse(response) {
	def cmdResponse = parseInput(response)
	logDebug("commandParse: cmdResponse = ${cmdResponse}")
	def stateArrays = cmdResponse.multiSensor.sensors

	def children = getChildDevices()
	children.each { it.commandParse(stateArrays) }
}

//	===== Communications =====
private sendGetCmd(command, action){
	logDebug("sendGetCmd: ${command} / ${action} / ${getDataValue("deviceIP")}")
	state.lastCommand = [type: "get", command: "${command}", body: "n/a", action: "${action}"]
	runIn(3, setCommsError)
	sendHubCommand(new hubitat.device.HubAction("GET ${command} HTTP/1.1\r\nHost: ${getDataValue("deviceIP")}\r\n\r\n",
				   hubitat.device.Protocol.LAN, null,[callback: action]))
}

private sendPostCmd(command, body, action){
	logDebug("sendGetCmd: ${command} / ${body} / ${action} / ${getDataValue("deviceIP")}")
	state.lastCommand = [type: "post", command: "${command}", body: "${body}", action: "${action}"]
	runIn(3, setCommsError)
	def parameters = [ method: "POST",
					  path: command,
					  protocol: "hubitat.device.Protocol.LAN",
					  body: body,
					  headers: [
						  Host: getDataValue("deviceIP")
					  ]]
	sendHubCommand(new hubitat.device.HubAction(parameters, null, [callback: action]))
}

def parseInput(response) {
	unschedule(setCommsError)
	state.errorCount = 0
	sendEvent(name: "commsError", value: false)
	try {
		def jsonSlurper = new groovy.json.JsonSlurper()
		return jsonSlurper.parseText(response.body)
	} catch (error) {
		logWarn "CommsError: ${error}."
	}
}

def setCommsError() {
	logDebug("setCommsError")
	if (state.errorCount < 3) {
		state.errorCount+= 1
		repeatCommand()
		logWarn("Attempt ${state.errorCount} to recover communications")
	} else if (state.errorCount == 3) {
		state.errorCount += 1
		if (getDataValue("applicationVersion")) {
			logWarn("setCommsError: Parent commanded to poll for devices to correct error.")
			parent.updateDeviceIps()
			runIn(90, repeatCommand)
		}
	} else {
		sendEvent(name: "commsError", value: true)
		logWarn "setCommsError: No response from device.  Refresh.  If off line " +
				"persists, check IP address of device."
	}
}

def repeatCommand() { 
	logDebug("repeatCommand: ${state.lastCommand}")
	if (state.lastCommand.type == "post") {
		sendPostCmd(state.lastCommand.command, state.lastCommand.body, state.lastCommand.action)
	} else {
		sendGetCmd(state.lastCommand.command, state.lastCommand.action)
	}
}

//	===== Utility Methods =====
def logTrace(msg) { log.trace "${device.label} ${driverVer()} ${msg}" }

def logInfo(msg) {
	if (descriptionText == true) { log.info "${device.label} ${driverVer()} ${msg}" }
}

def logDebug(msg){
	if(debug == true) { log.debug "${device.label} ${driverVer()} ${msg}" }
}

def debugOff() {
	device.updateSetting("debug", [type:"bool", value: false])
	logInfo("debugLogOff: Debug logging is off.")
}

def logWarn(msg){ log.warn "${device.label} ${driverVer()} ${msg}" }

//	end-of-file