/*
blebox tempSensorPro information driver.
*/
//	===== Definitions, Installation and Updates =====
metadata {
	definition (name: "data dump",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/bleBoxDevices/Drivers/tempSensor.groovy"
			   ) {
		capability "Refresh"
	}
	preferences {
		input ("device_IP", "text", title: "Device IP")
	}
}

def installed() {
	runIn(2, updated)
}

def updated() {
	if (!device_IP) {
		logWarn("updated:  device_IP  is not set.")
		return
	}

	refresh()
}

def refresh() {
	logInfo("STARTING SEQUENCE")
	logInfo("CurrentStatus")
	sendGetCmd("/api/settings/state", "commandParse")
	pauseExecution(2000)
	
	logInfo("SET ALL 10, 20, 30, 40")
	def cmdText = """{"settings":{"multiSensor":[""" +
		"""{"id":0, "settings":{"userTempOffset":10}},""" +
		"""{"id":1, "settings":{"userTempOffset":20}},""" +
		"""{"id":2, "settings":{"userTempOffset":30}},""" +
		"""{"id":3, "settings":{"userTempOffset":40}}]}}"""
	sendPostCmd("/api/settings/set", cmdText,"commandParse")
	pauseExecution(2000)

	logInfo("SET sensor 2 to 50, name to first test")
	cmdText = """{"settings":{"multiSensor":[""" +
		"""{"id":2, "settings":{"userTempOffset":50, "name": "first test"}}]}}"""
	sendPostCmd("/api/settings/set", cmdText,"commandParse")
	pauseExecution(2000)

	logInfo("SET ALL 10, 20, 30, 40 using tempSensor format")
	cmdTxt = """{"settings": {"tempSensor": {""" +
		"""userTempOffset": {"0": 10, "1": 20, "2": 30, "3": 40}}}}"""
	sendPostCmd("/api/settings/set", cmdText,"commandParse")
	pauseExecution(2000)

	logInfo("SET #2 to 60 using tempSensor format")
	cmdTxt = """{"settings": {"tempSensor": {""" +
		"""userTempOffset": {"2": 50}}}}"""
	sendPostCmd("/api/settings/set", cmdText,"commandParse")
	pauseExecution(2000)

	logInfo("SET sensor 2 name using  tempSensor method (not documented")
	cmdTxt = """{"settings": {"tempSensor": {""" +
		"""name": {"2": "Test Name}}}}"""
	sendPostCmd("/api/settings/set", cmdText,"commandParse")
	

/*	
	logInfo("INFO")
	sendGetCmd("/api/device/state", "commandParse")
	pauseExecution(2000)
	logInfo("STATE")
	sendGetCmd("/api/shutter/state", "commandParse")
	pauseExecution(2000)
	logInfo("SETTINGS")
	sendGetCmd("/api/settings/state", "commandParse")
*/
}

def commandParse(response) {
	def cmdResponse = parseInput(response)
	logDebug("commandParse: ${cmdResponse}")
	if (cmdResponse == null) {
		if (state.nullResp == true) { return }
		state.nullResp = true
		pauseExecution(1000)
		sendGetCmd("/api/settings/state", "commandParse")
		return
	}
	state.nullResp = false
}

//	===== Communications =====
private sendGetCmd(command, action){
	logDebug("sendGetCmd: ${command} / ${action} / ${device_IP}")
	sendHubCommand(new hubitat.device.HubAction("GET ${command} HTTP/1.1\r\nHost: ${device_IP}\r\n\r\n",
				   hubitat.device.Protocol.LAN, null,[callback: action]))
}

private sendPostCmd(command, body, action){
	logDebug("sendPostCmd: ${command} / ${body} / ${action})}")
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
	try {
		def jsonSlurper = new groovy.json.JsonSlurper()
		return jsonSlurper.parseText(response.body)
	} catch (error) {
		logWarn("CommsError: ${error}.")
		logWarn("")
	}
}

//	===== Utility Methods =====
def logInfo(msg) { log.info "${msg}" }

def logDebug(msg){ log.debug "${msg}" }

def logWarn(msg){ log.warn "${msg}" }

//	end-of-file


