/*	HubiThings Replica RangeOven Driver
	HubiThings Replica Applications Copyright 2023 by Bloodtick
	Replica RangeOven Copyright 2023 by Dave Gutheinz

	Licensed under the Apache License, Version 2.0 (the "License"); 
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at:
	      http://www.apache.org/licenses/LICENSE-2.0
	Unless required by applicable law or agreed to in writing, software 
	distributed under the License is distributed on an "AS IS" BASIS, 
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
	implied. See the License for the specific language governing 
	permissions and limitations under the License.

Issues with this driver: Contact davegut via Private Message on the
Hubitat Community site: https://community.hubitat.com/
==========================================================================*/
def driverVer() { return "1.0" }
def appliance() { return "Samsung Oven" }

metadata {
	definition (name: "Replica ${appliance()}",
				namespace: "replica",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/HubiThingsReplica%20Drivers/Replica_Samsung_Oven.groovy"
			   ){
		
		command "aTestMe"
		
		capability "Configuration"
		capability "Refresh"
		attribute "healthStatus", "enum", ["offline", "online"]
		command "setOvenSetpoint", [[name: "oven temperature", type: "NUMBER"]]
		attribute "ovenSetpoint", "number"
		attribute "ovenTemperature", "number"	//	attr.temperature
		command "setOvenMode", [[name: "from state.supported OvenModes", type:"STRING"]]
		attribute "ovenMode", "string"
		command "stop"
		command "start", [[name: "mode", type: "STRING"],
						  [name: "time (hh:mm:ss OR secs)", type: "STRING"],
						  [name: "setpoint", type: "NUMBER"]]
		attribute "completionTime", "string"	//	time string
		attribute "progress", "number"			//	percent
		attribute "operatingState", "string"	//	attr.machineState
		attribute "ovenJobState", "string"
		attribute "operationTime", "string"
		command "pause"
		command "setOperationTime", [[name: "time (hh:mm:ss OR secs)", type: "STRING"]]
		attribute "lockState", "string"
		command "setOvenLight", [[name: "from state.supported BrightnessLevels", type: "STRING"]]
		attribute "brightnessLevel", "string"
		attribute "remoteControlEnabled", "boolean"
		attribute "doorState", "string"
		attribute "cooktopOperatingState", "string"
		command "setProbeSetpoint", [[name: "probe alert temperature", type: "NUMBER"]]
		attribute "probeSetpoint", "number"
		attribute "probeStatus", "string"
		attribute "probeTemperature", "number"
	}
	preferences {
		input ("logEnable", "bool",  title: "Enable debug logging for 30 minutes", defaultValue: false)
		input ("infoLog", "bool", title: "Enable information logging",defaultValue: true)
		input ("traceLog", "bool", title: "Enable trace logging as directed by developer", defaultValue: false)
	}
}


def aTestMe() {
	logInfo("<b>TEST: Starting operationTime = ${device.currentValue("operationTime")}</b>")
	logInfo("<b>TEST_1: setting Op Time to 01:00:00 using second format</b>")
	setOperationTime(3600)
	pauseExecution(5000)
	logInfo("<b>TEST_1: operationTime = ${device.currentValue("operationTime")}</b>")
	logInfo("<b>TEST_2: setting Op Time to 00:45:30 using hh:mm:ss format</b>")
	setOperationTime("00:45:30")
	pauseExecution(5000)
	logInfo("<b>TEST_2: operationTime = ${device.currentValue("operationTime")}</b>")
}


//	===== Installation, setup and update =====
def installed() {
	updateDataValue("componentId", "main")
	runIn(1, updated)
}

def updated() {
	unschedule()
	configure()
	pauseExecution(2000)
	def updStatus = [:]
	if (!getDataValue("driverVersion") || getDataValue("driverVersion") != driverVer()) {
		updateDataValue("driverVersion", driverVer())
		updStatus << [driverVer: driverVer()]
	}
	if (logEnable) { runIn(1800, debugLogOff) }
	if (traceLog) { runIn(600, traceLogOff) }
	updStatus << [logEnable: logEnable, infoLog: infoLog, traceLog: traceLog]
	refresh()
	runIn(5, listAttributes,[data:true])
	logInfo("updated: ${updStatus}")
}

def designCapabilities() {
	return ["refresh", "remoteControlStatus", "ovenSetpoint", "ovenMode",
			"ovenOperatingState", "temperatureMeasurement", "samsungce.doorState",
			"samsungce.ovenMode", "samsungce.ovenOperatingState", "samsungce.meatProbe",
			"samsungce.lamp", "samsungce.kidsLock", "custom.cooktopOperatingState"]
}

Map designChildren() {
	return ["cavity-01": "cavity"]
}

def sendRawCommand(component, capability, command, arguments = []) {
//	if (device.currentValue("remoteControlEnabled")) {
		def deviceId = new JSONObject(getDataValue("description")).deviceId
		def status = parent.setSmartDeviceCommand(deviceId, component, capability, command, arguments)
		logInfo("sendRawCommand: [${component}, ${capability}, ${command}, ${arguments}, ${status}]")
		return "[${component}, ${capability}, ${command}, ${arguments}, ${status}]"
//	} else {
//		logWarn("sendRawCommand: Command Aborted.  The Remote Control must be enabled at the stove prior to entering control commands.")
//		return "[Aborted: Remote Control disabled]"
//	}
}

//	===== Device Commands =====
//	Common parent/child Oven commands are in library replica.samsungReplicaOvenCommon

def setProbeSetpoint(temperature) {
	if (device.currentValue("probeStatus") != "disconnected") {
		temperature = temperature.toInteger()
		if (temperature > 0) {
			sendRawCommand(getDataValue("componentId"), "samsungce.meatProbe", "setTemperatureSetpoint", [temperature])
			logInfo("setProbeSetpoint: ${temperature}")
		} else {
			logWarn("setProbeSetpoint: Not set.  Temperature ${temperature} < 0")
		}
	} else {
		logWarn("setProbeSetpoint: Not set.  Probe is disconnected")
	}
}

def setOvenLight(lightLevel) {
	 lightLevel = state.supportedBrightnessLevel.find { it.toLowerCase() == lightLevel.toLowerCase() }
	if (lightLevel == null) {
		logWarn("setOvenLight:  Level ${lightLevel} not supported")
	} else {
		sendRawCommand(getDataValue("componentId"), "samsungce.lamp", "setBrightnessLevel", [lightLevel])
		logInfo("setOvenLight: ${lightLevel}")
	}
}

//	===== Libraries =====




// ~~~~~ start include (1253) replica.samsungOvenCommon ~~~~~
library ( // library marker replica.samsungOvenCommon, line 1
	name: "samsungOvenCommon", // library marker replica.samsungOvenCommon, line 2
	namespace: "replica", // library marker replica.samsungOvenCommon, line 3
	author: "Dave Gutheinz", // library marker replica.samsungOvenCommon, line 4
	description: "Common Methods for replica Samsung Oven parent/children", // library marker replica.samsungOvenCommon, line 5
	category: "utilities", // library marker replica.samsungOvenCommon, line 6
	documentationLink: "" // library marker replica.samsungOvenCommon, line 7
) // library marker replica.samsungOvenCommon, line 8
//	Version 1.0 // library marker replica.samsungOvenCommon, line 9

def parseEvent(event) { // library marker replica.samsungOvenCommon, line 11
//	logDebug("parseEvent: <b>${event}</b>") // library marker replica.samsungOvenCommon, line 12
	logInfo("parseEvent: <b>${event}</b>") // library marker replica.samsungOvenCommon, line 13
	if (state.deviceCapabilities.contains(event.capability)) { // library marker replica.samsungOvenCommon, line 14
		logTrace("parseEvent: <b>${event}</b>") // library marker replica.samsungOvenCommon, line 15
		if (event.value != null) { // library marker replica.samsungOvenCommon, line 16
			switch(event.attribute) { // library marker replica.samsungOvenCommon, line 17
				case "machineState": // library marker replica.samsungOvenCommon, line 18
					if (!state.deviceCapabilities.contains("samsungce.ovenOperatingState")) { // library marker replica.samsungOvenCommon, line 19
						event.attribute = "operatingState" // library marker replica.samsungOvenCommon, line 20
						setEvent(event) // library marker replica.samsungOvenCommon, line 21
					} // library marker replica.samsungOvenCommon, line 22
					break // library marker replica.samsungOvenCommon, line 23
				//	use samsungce.ovenOperatingState if available, otherwise, base capability. // library marker replica.samsungOvenCommon, line 24
				case "operationTime": // library marker replica.samsungOvenCommon, line 25
					def opTime = formatTime(event.value, "hhmmss", "parseEvent") // library marker replica.samsungOvenCommon, line 26
					event.value = opTime // library marker replica.samsungOvenCommon, line 27
				case "completionTime": // library marker replica.samsungOvenCommon, line 28
				case "progress": // library marker replica.samsungOvenCommon, line 29
				case "ovenJobState": // library marker replica.samsungOvenCommon, line 30
				case "operationTime": // library marker replica.samsungOvenCommon, line 31
					if (state.deviceCapabilities.contains("samsungce.ovenOperatingState")) { // library marker replica.samsungOvenCommon, line 32
						if (event.capability == "samsungce.ovenOperatingState") { // library marker replica.samsungOvenCommon, line 33
							setEvent(event) // library marker replica.samsungOvenCommon, line 34
						} // library marker replica.samsungOvenCommon, line 35
					} else { // library marker replica.samsungOvenCommon, line 36
						setEvent(event) // library marker replica.samsungOvenCommon, line 37
					} // library marker replica.samsungOvenCommon, line 38
					break // library marker replica.samsungOvenCommon, line 39
				case "temperature": // library marker replica.samsungOvenCommon, line 40
					def attr = "ovenTemperature" // library marker replica.samsungOvenCommon, line 41
					if (event.capability == "samsungce.meatProbe") { // library marker replica.samsungOvenCommon, line 42
						attr = "probeTemperature" // library marker replica.samsungOvenCommon, line 43
					} // library marker replica.samsungOvenCommon, line 44
					event["attribute"] = attr // library marker replica.samsungOvenCommon, line 45
					setEvent(event) // library marker replica.samsungOvenCommon, line 46
					break // library marker replica.samsungOvenCommon, line 47
				case "temperatureSetpoint": // library marker replica.samsungOvenCommon, line 48
					event["attribute"] = "probeSetpoint" // library marker replica.samsungOvenCommon, line 49
					setEvent(event) // library marker replica.samsungOvenCommon, line 50
					break // library marker replica.samsungOvenCommon, line 51
				case "status": // library marker replica.samsungOvenCommon, line 52
					event["attribute"] = "probeStatus" // library marker replica.samsungOvenCommon, line 53
					setEvent(event) // library marker replica.samsungOvenCommon, line 54
					break // library marker replica.samsungOvenCommon, line 55
				case "ovenMode": // library marker replica.samsungOvenCommon, line 56
				//	if samsungce.ovenMode, use that, otherwise use // library marker replica.samsungOvenCommon, line 57
				//	ovenMode.  Format always hh:mm:ss. // library marker replica.samsungOvenCommon, line 58
					if (state.deviceCapabilities.contains("samsungce.ovenMode")) { // library marker replica.samsungOvenCommon, line 59
						if (event.capability == "samsungce.ovenMode") { // library marker replica.samsungOvenCommon, line 60
							setEvent(event) // library marker replica.samsungOvenCommon, line 61
						} // library marker replica.samsungOvenCommon, line 62
					} else { // library marker replica.samsungOvenCommon, line 63
						setEvent(event) // library marker replica.samsungOvenCommon, line 64
					} // library marker replica.samsungOvenCommon, line 65
					break // library marker replica.samsungOvenCommon, line 66
				case "supportedOvenModes": // library marker replica.samsungOvenCommon, line 67
				//	if samsungce.ovenMode, use that, otherwise use // library marker replica.samsungOvenCommon, line 68
				//	ovenMode.  Format always hh:mm:ss. // library marker replica.samsungOvenCommon, line 69
					if (state.deviceCapabilities.contains("samsungce.ovenMode")) { // library marker replica.samsungOvenCommon, line 70
						if (event.capability == "samsungce.ovenMode") { // library marker replica.samsungOvenCommon, line 71
							setState(event) // library marker replica.samsungOvenCommon, line 72
						} // library marker replica.samsungOvenCommon, line 73
					} else { // library marker replica.samsungOvenCommon, line 74
						setState(event) // library marker replica.samsungOvenCommon, line 75
					} // library marker replica.samsungOvenCommon, line 76
					break // library marker replica.samsungOvenCommon, line 77
				case "supportedBrightnessLevel": // library marker replica.samsungOvenCommon, line 78
					setState(event) // library marker replica.samsungOvenCommon, line 79
					break // library marker replica.samsungOvenCommon, line 80
				case "supportedCooktopOperatingState": // library marker replica.samsungOvenCommon, line 81
					break // library marker replica.samsungOvenCommon, line 82
				default: // library marker replica.samsungOvenCommon, line 83
					setEvent(event) // library marker replica.samsungOvenCommon, line 84
					break // library marker replica.samsungOvenCommon, line 85
			} // library marker replica.samsungOvenCommon, line 86
		} // library marker replica.samsungOvenCommon, line 87
	} // library marker replica.samsungOvenCommon, line 88
} // library marker replica.samsungOvenCommon, line 89

def setEvent(event) { // library marker replica.samsungOvenCommon, line 91
	logTrace("<b>setEvent</b>: ${event}") // library marker replica.samsungOvenCommon, line 92
	if (device.currentValue(event.attribute).toString() != event.value.toString()) { // library marker replica.samsungOvenCommon, line 93
		sendEvent(name: event.attribute, value: event.value, unit: event.unit) // library marker replica.samsungOvenCommon, line 94
		logInfo("setEvent: [event: ${event}]") // library marker replica.samsungOvenCommon, line 95
	} // library marker replica.samsungOvenCommon, line 96
} // library marker replica.samsungOvenCommon, line 97

//	===== Device Commands ===== // library marker replica.samsungOvenCommon, line 99
def setOvenMode(mode) { // library marker replica.samsungOvenCommon, line 100
	//	mode: string, from supportedOvenModes // library marker replica.samsungOvenCommon, line 101
	def ovenMode = checkMode(mode) // library marker replica.samsungOvenCommon, line 102
	if (ovenMode == "notSupported") { // library marker replica.samsungOvenCommon, line 103
		logWarn("setOvenMode: [error: Mode ${mode} not supported]") // library marker replica.samsungOvenCommon, line 104
	} else { // library marker replica.samsungOvenCommon, line 105
		def capability = "samsungce.ovenMode" // library marker replica.samsungOvenCommon, line 106
		if (!state.deviceCapabilities.contains(capability)) { // library marker replica.samsungOvenCommon, line 107
			capability = "ovenMode" // library marker replica.samsungOvenCommon, line 108
		} // library marker replica.samsungOvenCommon, line 109
		sendRawCommand(getDataValue("componentId"), capability, "setOvenMode", [ovenMode]) // library marker replica.samsungOvenCommon, line 110
		logInfo("setOvenMode: ${ovenMode}") // library marker replica.samsungOvenCommon, line 111
	} // library marker replica.samsungOvenCommon, line 112
	return ovenMode // library marker replica.samsungOvenCommon, line 113
} // library marker replica.samsungOvenCommon, line 114

def checkMode(mode) { // library marker replica.samsungOvenCommon, line 116
	mode = state.supportedOvenModes.find { it.toLowerCase() == mode.toLowerCase() } // library marker replica.samsungOvenCommon, line 117
	if (mode == null) { // library marker replica.samsungOvenCommon, line 118
		mode = "notSupported" // library marker replica.samsungOvenCommon, line 119
	} // library marker replica.samsungOvenCommon, line 120
	return mode // library marker replica.samsungOvenCommon, line 121
} // library marker replica.samsungOvenCommon, line 122

def setOvenSetpoint(setpoint) { // library marker replica.samsungOvenCommon, line 124
//	setpoint: number. >= 0 // library marker replica.samsungOvenCommon, line 125
	setpoint = setpoint.toInteger() // library marker replica.samsungOvenCommon, line 126
	if (setpoint >= 0) { // library marker replica.samsungOvenCommon, line 127
		sendRawCommand(getDataValue("componentId"), "ovenSetpoint", "setOvenSetpoint", [setpoint]) // library marker replica.samsungOvenCommon, line 128
		logInfo("setOvenSetpoint: ${setpoint}") // library marker replica.samsungOvenCommon, line 129
	} else { // library marker replica.samsungOvenCommon, line 130
		logWarn("setOvenSetpoint: [error: negative value, setpoint: ${setpoint}]") // library marker replica.samsungOvenCommon, line 131
	} // library marker replica.samsungOvenCommon, line 132
	return setpoint // library marker replica.samsungOvenCommon, line 133
} // library marker replica.samsungOvenCommon, line 134

def setOperationTime(opTime) { // library marker replica.samsungOvenCommon, line 136
	//	opTime: string/number.  interger > 0 or hh:mm:ss or mm:ss // library marker replica.samsungOvenCommon, line 137
	if (state.deviceCapabilities.contains("samsungce.ovenOperatingState")) { // library marker replica.samsungOvenCommon, line 138
		opTime = formatTime(opTime, "hhmmss", "setOperationTime") // library marker replica.samsungOvenCommon, line 139
		if (opTime != "invalidEntry") { // library marker replica.samsungOvenCommon, line 140
			sendRawCommand(getDataValue("componentId"), "samsungce.ovenOperatingState", "setOperationTime", [opTime]) // library marker replica.samsungOvenCommon, line 141
			logInfo("setOperationTime: ${opTime}") // library marker replica.samsungOvenCommon, line 142
		} // library marker replica.samsungOvenCommon, line 143
	} else { // library marker replica.samsungOvenCommon, line 144
		//	Safety: Uses start command.  For safety, update only if the device is already running. // library marker replica.samsungOvenCommon, line 145
		//	Otherwise, user may start oven without intention. // library marker replica.samsungOvenCommon, line 146
//		if (device.currentValue("operatingState") != "running") { // library marker replica.samsungOvenCommon, line 147
//			logWarn("setOperationTime: Not available on device unless the device is running. <b>Use start command</b>.") // library marker replica.samsungOvenCommon, line 148
//		} else { // library marker replica.samsungOvenCommon, line 149
			opTime = formatTime(opTime, "seconds", "setOperationTime") // library marker replica.samsungOvenCommon, line 150
			if (opTime != "invalidEntry") { // library marker replica.samsungOvenCommon, line 151
				sendRawCommand(getDataValue("componentId"), "ovenOperatingState", "start", [time: opTime]) // library marker replica.samsungOvenCommon, line 152
				logInfo("setOperationTime: ${opTime}") // library marker replica.samsungOvenCommon, line 153
//			} // library marker replica.samsungOvenCommon, line 154
		} // library marker replica.samsungOvenCommon, line 155
	} // library marker replica.samsungOvenCommon, line 156
	return opTime // library marker replica.samsungOvenCommon, line 157
} // library marker replica.samsungOvenCommon, line 158

def start(mode = null, opTime = null, setpoint = null) { // library marker replica.samsungOvenCommon, line 160
	//	Mode: string, from supportedOvenModes or null. // library marker replica.samsungOvenCommon, line 161
	//	Optime: string/number.  interger > 0 or hh:mm:ss or mm:ss or null. // library marker replica.samsungOvenCommon, line 162
	//	setpoint: number. >= 0 // library marker replica.samsungOvenCommon, line 163
	def logData = [input: [mode: mode, opTime: opTime, setpoint: setpoint]] // library marker replica.samsungOvenCommon, line 164
	if (mode == null && opTime == null && setpoint == null) { // library marker replica.samsungOvenCommon, line 165
		def capability = "samsungce.ovenOperatingState" // library marker replica.samsungOvenCommon, line 166
		if (!state.deviceCapabilities.contains(capability)) { // library marker replica.samsungOvenCommon, line 167
			capability = "ovenOperatingState" // library marker replica.samsungOvenCommon, line 168
		} // library marker replica.samsungOvenCommon, line 169
		sendRawCommand(getDataValue("componentId"), capability, "start", []) // library marker replica.samsungOvenCommon, line 170
		logData << [capability: "start only sent"] // library marker replica.samsungOvenCommon, line 171
	} else { // library marker replica.samsungOvenCommon, line 172
		if (mode == null) { mode = device.currentValue("ovenMode") } // library marker replica.samsungOvenCommon, line 173
		if (opTime == null) { opTime = device.currentValue("operationTime") } // library marker replica.samsungOvenCommon, line 174
		if (setpoint == null) { setpoint = device.currentValue("ovenSetpoint") } // library marker replica.samsungOvenCommon, line 175
		if (state.deviceCapabilities.contains("samsungce.ovenOperatingState")) { // library marker replica.samsungOvenCommon, line 176
			logData << [capability: "samsungce.ovenOperatingState"] // library marker replica.samsungOvenCommon, line 177
			//	Send individual commands for the samsungce device // library marker replica.samsungOvenCommon, line 178
			logData << [mode: setOvenMode(mode)] // library marker replica.samsungOvenCommon, line 179
			pauseExecution(1000) // library marker replica.samsungOvenCommon, line 180
			logData << [opTime: setOperationTime(opTime)] // library marker replica.samsungOvenCommon, line 181
			pauseExecution(1000) // library marker replica.samsungOvenCommon, line 182
			logData << [setpoint: setOvenSetpoint(setpoint)] // library marker replica.samsungOvenCommon, line 183
			pauseExecution(1000) // library marker replica.samsungOvenCommon, line 184
			//	Check attributes are updated properly.  If so, send start. // library marker replica.samsungOvenCommon, line 185
			def checkData = logData // library marker replica.samsungOvenCommon, line 186
			def checkStatus = checkValues(checkData) // library marker replica.samsungOvenCommon, line 187
			if (checkStatus.status != "MISMATCH") { // library marker replica.samsungOvenCommon, line 188
				sendRawCommand(getDataValue("componentId"), "samsungce.ovenOperatingState", "start", []) // library marker replica.samsungOvenCommon, line 189
				logData << [start: "sent"] // library marker replica.samsungOvenCommon, line 190
			} else { // library marker replica.samsungOvenCommon, line 191
				logData << [start: "failed start", data: checkStatus] // library marker replica.samsungOvenCommon, line 192
			} // library marker replica.samsungOvenCommon, line 193
		} else { // library marker replica.samsungOvenCommon, line 194
			logData << [capability: "ovenOperatingState"] // library marker replica.samsungOvenCommon, line 195
			//	Legacy capability control // library marker replica.samsungOvenCommon, line 196
			opTime = formatTime(opTime, "seconds", "start").toInteger() // library marker replica.samsungOvenCommon, line 197
			def opMode = checkMode(mode) // library marker replica.samsungOvenCommon, line 198
			logData << [opTime: opTime, mode: opMode, setpoint: setpoint] // library marker replica.samsungOvenCommon, line 199
			if (setpoint >= 0 && opMode != "notSupported" && opTime != "invalidEntry") { // library marker replica.samsungOvenCommon, line 200
				sendRawCommand(getDataValue("componentId"), "ovenOperatingState", "start",  // library marker replica.samsungOvenCommon, line 201
							   [mode: opMode, time: opTime, setpoint: setpoint]) // library marker replica.samsungOvenCommon, line 202
				logData << [start: "start with parameters"] // library marker replica.samsungOvenCommon, line 203
			} else { // library marker replica.samsungOvenCommon, line 204
				logData << [start: "failed start", reason: "argument out of range"] // library marker replica.samsungOvenCommon, line 205
			} // library marker replica.samsungOvenCommon, line 206
		} // library marker replica.samsungOvenCommon, line 207
	} // library marker replica.samsungOvenCommon, line 208
	logInfo("start: ${logData}") // library marker replica.samsungOvenCommon, line 209
} // library marker replica.samsungOvenCommon, line 210

def checkValues(checkData) { // library marker replica.samsungOvenCommon, line 212
	//	Validate current machine states match called-for states then start. // library marker replica.samsungOvenCommon, line 213
	def respData = [:] // library marker replica.samsungOvenCommon, line 214
	def checkStatus = "OK" // library marker replica.samsungOvenCommon, line 215
	if (checkData.mode.toLowerCase() != device.currentValue("ovenMode").toLowerCase()) { // library marker replica.samsungOvenCommon, line 216
		respData << [mode: [check: checkData.mode, attribute: device.currentValue("ovenMode")]] // library marker replica.samsungOvenCommon, line 217
		checkStatus = "MISMATCH" // library marker replica.samsungOvenCommon, line 218
	} // library marker replica.samsungOvenCommon, line 219

	//	Change opTime and operation time to seconds (as string) (if not already) to simplify comparison. // library marker replica.samsungOvenCommon, line 221
	def checkOpTime = formatTime(checkData.opTime, "seconds", "checkValues") // library marker replica.samsungOvenCommon, line 222
	def checkOperationTime = formatTime(device.currentValue("operationTime"), "seconds", "checkValues") // library marker replica.samsungOvenCommon, line 223
	if (checkOpTime != checkOperationTime) { // library marker replica.samsungOvenCommon, line 224
		respData << [opTime: [check: checkData.opTime, attribute: device.currentValue("operationTime")]] // library marker replica.samsungOvenCommon, line 225
		checkStatus = "MISMATCH" // library marker replica.samsungOvenCommon, line 226
	} // library marker replica.samsungOvenCommon, line 227

	if (checkData.setpoint != device.currentValue("ovenSetpoint")) { // library marker replica.samsungOvenCommon, line 229
		respData << [setpoint: [check: checkData.setpoint, attribute: device.currentValue("ovenSetpoint")]] // library marker replica.samsungOvenCommon, line 230
		checkStatus = "MISMATCH" // library marker replica.samsungOvenCommon, line 231
	} // library marker replica.samsungOvenCommon, line 232
	if (checkStatus == "MISMATCH") { // library marker replica.samsungOvenCommon, line 233
		logWarn("checkValues: [status: ${checkStatus}, error: Oven not started due to mismatch]") // library marker replica.samsungOvenCommon, line 234
	} // library marker replica.samsungOvenCommon, line 235
	return [status: checkStatus, data: respData] // library marker replica.samsungOvenCommon, line 236
} // library marker replica.samsungOvenCommon, line 237

def stop() { // library marker replica.samsungOvenCommon, line 239
	//	Same format for both capabilities. // library marker replica.samsungOvenCommon, line 240
	def capability = "samsungce.ovenOperatingState" // library marker replica.samsungOvenCommon, line 241
	if (!state.deviceCapabilities.contains(capability)) { // library marker replica.samsungOvenCommon, line 242
		capability = "ovenOperatingState" // library marker replica.samsungOvenCommon, line 243
	} // library marker replica.samsungOvenCommon, line 244
	sendRawCommand(getDataValue("componentId"), capability, "stop") // library marker replica.samsungOvenCommon, line 245
	logInfo("stop") // library marker replica.samsungOvenCommon, line 246
} // library marker replica.samsungOvenCommon, line 247

def pause() { // library marker replica.samsungOvenCommon, line 249
	//	Only available on samsungce devices. // library marker replica.samsungOvenCommon, line 250
	if (state.deviceCapabilities.contains("samsungce.ovenOperatingState")) { // library marker replica.samsungOvenCommon, line 251
		sendRawCommand(getDataValue("componentId"), "samsungce.ovenOperatingState", "pause") // library marker replica.samsungOvenCommon, line 252
		logInfo("pause") // library marker replica.samsungOvenCommon, line 253
	} else { // library marker replica.samsungOvenCommon, line 254
		logWarn("pause: Not available on your device") // library marker replica.samsungOvenCommon, line 255
	} // library marker replica.samsungOvenCommon, line 256
} // library marker replica.samsungOvenCommon, line 257

def formatTime(timeValue, desiredFormat, callMethod) { // library marker replica.samsungOvenCommon, line 259
	timeValue = timeValue.toString() // library marker replica.samsungOvenCommon, line 260
	def currentFormat = "seconds" // library marker replica.samsungOvenCommon, line 261
	if (timeValue.contains(":")) { // library marker replica.samsungOvenCommon, line 262
		currentFormat = "hhmmss" // library marker replica.samsungOvenCommon, line 263
	} // library marker replica.samsungOvenCommon, line 264
	def formatedTime // library marker replica.samsungOvenCommon, line 265
	if (currentFormat == "hhmmss") { // library marker replica.samsungOvenCommon, line 266
		formatedTime = formatHhmmss(timeValue) // library marker replica.samsungOvenCommon, line 267
		if (desiredFormat == "seconds") { // library marker replica.samsungOvenCommon, line 268
			formatedTime = convertHhMmSsToInt(formatedTime) // library marker replica.samsungOvenCommon, line 269
		} // library marker replica.samsungOvenCommon, line 270
	} else { // library marker replica.samsungOvenCommon, line 271
		formatedTime = timeValue // library marker replica.samsungOvenCommon, line 272
		if (desiredFormat == "hhmmss") { // library marker replica.samsungOvenCommon, line 273
			formatedTime = convertIntToHhMmSs(timeValue) // library marker replica.samsungOvenCommon, line 274
		} // library marker replica.samsungOvenCommon, line 275
	} // library marker replica.samsungOvenCommon, line 276
	if (formatedTime == "invalidEntry") { // library marker replica.samsungOvenCommon, line 277
		Map errorData = [callMethod: callMethod, timeValue: timeValue, // library marker replica.samsungOvenCommon, line 278
						 desiredFormat: desiredFormat] // library marker replica.samsungOvenCommon, line 279
		logWarn("formatTime: [error: ${formatedTime}, data: ${errorData}") // library marker replica.samsungOvenCommon, line 280
	} // library marker replica.samsungOvenCommon, line 281
	return formatedTime // library marker replica.samsungOvenCommon, line 282
} // library marker replica.samsungOvenCommon, line 283

def formatHhmmss(timeValue) { // library marker replica.samsungOvenCommon, line 285
	def timeArray = timeValue.split(":") // library marker replica.samsungOvenCommon, line 286
	def hours = 0 // library marker replica.samsungOvenCommon, line 287
	def minutes = 0 // library marker replica.samsungOvenCommon, line 288
	def seconds = 0 // library marker replica.samsungOvenCommon, line 289
	if (timeArray.size() != timeValue.count(":") + 1) { // library marker replica.samsungOvenCommon, line 290
		return "invalidEntry" // library marker replica.samsungOvenCommon, line 291
	} else { // library marker replica.samsungOvenCommon, line 292
		try { // library marker replica.samsungOvenCommon, line 293
			if (timeArray.size() == 3) { // library marker replica.samsungOvenCommon, line 294
				hours = timeArray[0].toInteger() // library marker replica.samsungOvenCommon, line 295
				minutes = timeArray[1].toInteger() // library marker replica.samsungOvenCommon, line 296
				seconds = timeArray[2].toInteger() // library marker replica.samsungOvenCommon, line 297
			} else if (timeArray.size() == 2) { // library marker replica.samsungOvenCommon, line 298
				minutes = timeArray[0].toInteger() // library marker replica.samsungOvenCommon, line 299
				seconds = timeArray[1].toInteger() // library marker replica.samsungOvenCommon, line 300
			} // library marker replica.samsungOvenCommon, line 301
		} catch (error) { // library marker replica.samsungOvenCommon, line 302
			return "invalidEntry" // library marker replica.samsungOvenCommon, line 303
		} // library marker replica.samsungOvenCommon, line 304
	} // library marker replica.samsungOvenCommon, line 305
	if (hours < 10) { hours = "0${hours}" } // library marker replica.samsungOvenCommon, line 306
	if (minutes < 10) { minutes = "0${minutes}" } // library marker replica.samsungOvenCommon, line 307
	if (seconds < 10) { seconds = "0${seconds}" } // library marker replica.samsungOvenCommon, line 308
	return "${hours}:${minutes}:${seconds}" // library marker replica.samsungOvenCommon, line 309
} // library marker replica.samsungOvenCommon, line 310

def convertIntToHhMmSs(timeSeconds) { // library marker replica.samsungOvenCommon, line 312
	def hhmmss // library marker replica.samsungOvenCommon, line 313
	try { // library marker replica.samsungOvenCommon, line 314
		hhmmss = new GregorianCalendar( 0, 0, 0, 0, 0, timeSeconds.toInteger(), 0 ).time.format( 'HH:mm:ss' ) // library marker replica.samsungOvenCommon, line 315
	} catch (error) { // library marker replica.samsungOvenCommon, line 316
		hhmmss = "invalidEntry" // library marker replica.samsungOvenCommon, line 317
	} // library marker replica.samsungOvenCommon, line 318
	return hhmmss // library marker replica.samsungOvenCommon, line 319
} // library marker replica.samsungOvenCommon, line 320

def convertHhMmSsToInt(timeValue) { // library marker replica.samsungOvenCommon, line 322
	def timeArray = timeValue.split(":") // library marker replica.samsungOvenCommon, line 323
	def seconds = 0 // library marker replica.samsungOvenCommon, line 324
	if (timeArray.size() != timeValue.count(":") + 1) { // library marker replica.samsungOvenCommon, line 325
		return "invalidEntry" // library marker replica.samsungOvenCommon, line 326
	} else { // library marker replica.samsungOvenCommon, line 327
		try { // library marker replica.samsungOvenCommon, line 328
			if (timeArray.size() == 3) { // library marker replica.samsungOvenCommon, line 329
				seconds = timeArray[0].toInteger() * 3600 + // library marker replica.samsungOvenCommon, line 330
				timeArray[1].toInteger() * 60 + timeArray[2].toInteger() // library marker replica.samsungOvenCommon, line 331
			} else if (timeArray.size() == 2) { // library marker replica.samsungOvenCommon, line 332
				seconds = timeArray[0].toInteger() * 60 + timeArray[1].toInteger() // library marker replica.samsungOvenCommon, line 333
			} // library marker replica.samsungOvenCommon, line 334
		} catch (error) { // library marker replica.samsungOvenCommon, line 335
			seconds = "invalidEntry" // library marker replica.samsungOvenCommon, line 336
		} // library marker replica.samsungOvenCommon, line 337
	} // library marker replica.samsungOvenCommon, line 338
	return seconds // library marker replica.samsungOvenCommon, line 339
} // library marker replica.samsungOvenCommon, line 340

// ~~~~~ end include (1253) replica.samsungOvenCommon ~~~~~

// ~~~~~ start include (1251) replica.samsungReplicaCommon ~~~~~
library ( // library marker replica.samsungReplicaCommon, line 1
	name: "samsungReplicaCommon", // library marker replica.samsungReplicaCommon, line 2
	namespace: "replica", // library marker replica.samsungReplicaCommon, line 3
	author: "Dave Gutheinz", // library marker replica.samsungReplicaCommon, line 4
	description: "Common Methods for replica Samsung Appliances", // library marker replica.samsungReplicaCommon, line 5
	category: "utilities", // library marker replica.samsungReplicaCommon, line 6
	documentationLink: "" // library marker replica.samsungReplicaCommon, line 7
) // library marker replica.samsungReplicaCommon, line 8
//	version 1.0 // library marker replica.samsungReplicaCommon, line 9

import org.json.JSONObject // library marker replica.samsungReplicaCommon, line 11
import groovy.json.JsonOutput // library marker replica.samsungReplicaCommon, line 12
import groovy.json.JsonSlurper // library marker replica.samsungReplicaCommon, line 13

def configure() { // library marker replica.samsungReplicaCommon, line 15
	Map logData = [:] // library marker replica.samsungReplicaCommon, line 16
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers())) // library marker replica.samsungReplicaCommon, line 17
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands())) // library marker replica.samsungReplicaCommon, line 18
	logData << [triggers: "initialized", commands: "initialized"] // library marker replica.samsungReplicaCommon, line 19
	setReplicaRules() // library marker replica.samsungReplicaCommon, line 20
	logData << [replicaRules: "initialized"] // library marker replica.samsungReplicaCommon, line 21
	state.checkCapabilities = true // library marker replica.samsungReplicaCommon, line 22
	sendCommand("configure") // library marker replica.samsungReplicaCommon, line 23
	logData: [device: "configuring HubiThings"] // library marker replica.samsungReplicaCommon, line 24
	logInfo("configure: ${logData}") // library marker replica.samsungReplicaCommon, line 25
} // library marker replica.samsungReplicaCommon, line 26

Map getReplicaCommands() { // library marker replica.samsungReplicaCommon, line 28
    return (["replicaEvent":[[name:"parent*",type:"OBJECT"],[name:"event*",type:"JSON_OBJECT"]],  // library marker replica.samsungReplicaCommon, line 29
			 "replicaStatus":[[name:"parent*",type:"OBJECT"],[name:"event*",type:"JSON_OBJECT"]],  // library marker replica.samsungReplicaCommon, line 30
			 "replicaHealth":[[name:"parent*",type:"OBJECT"],[name:"health*",type:"JSON_OBJECT"]], // library marker replica.samsungReplicaCommon, line 31
			 "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]]) // library marker replica.samsungReplicaCommon, line 32
} // library marker replica.samsungReplicaCommon, line 33

Map getReplicaTriggers() { // library marker replica.samsungReplicaCommon, line 35
	Map triggers = [  // library marker replica.samsungReplicaCommon, line 36
		refresh:[], deviceRefresh: [] // library marker replica.samsungReplicaCommon, line 37
	] // library marker replica.samsungReplicaCommon, line 38
	return triggers // library marker replica.samsungReplicaCommon, line 39
} // library marker replica.samsungReplicaCommon, line 40

String setReplicaRules() { // library marker replica.samsungReplicaCommon, line 42
	def rules = """{"version":1,"components":[ // library marker replica.samsungReplicaCommon, line 43
{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true}, // library marker replica.samsungReplicaCommon, line 44
{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"}, // library marker replica.samsungReplicaCommon, line 45
{"trigger":{"name":"deviceRefresh","label":"command: deviceRefresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"}]}""" // library marker replica.samsungReplicaCommon, line 46

	updateDataValue("rules", rules) // library marker replica.samsungReplicaCommon, line 48
} // library marker replica.samsungReplicaCommon, line 49

//	===== Event Parse Interface s===== // library marker replica.samsungReplicaCommon, line 51
void replicaStatus(def parent=null, Map status=null) { // library marker replica.samsungReplicaCommon, line 52
	def logData = [parent: parent, status: status] // library marker replica.samsungReplicaCommon, line 53
	if (state.checkCapabilities) { // library marker replica.samsungReplicaCommon, line 54
		runIn(4, checkCapabilities, [data: status.components]) // library marker replica.samsungReplicaCommon, line 55
	} else if (state.refreshAttributes) { // library marker replica.samsungReplicaCommon, line 56
		refreshAttributes(status.components) // library marker replica.samsungReplicaCommon, line 57
	} // library marker replica.samsungReplicaCommon, line 58
	logDebug("replicaStatus: ${logData}") // library marker replica.samsungReplicaCommon, line 59
} // library marker replica.samsungReplicaCommon, line 60

def checkCapabilities(components) { // library marker replica.samsungReplicaCommon, line 62
	state.checkCapabilities = false // library marker replica.samsungReplicaCommon, line 63
	def componentId = getDataValue("componentId") // library marker replica.samsungReplicaCommon, line 64
	def disabledCapabilities = [] // library marker replica.samsungReplicaCommon, line 65
	try { // library marker replica.samsungReplicaCommon, line 66
		disabledCapabilities << components[componentId]["custom.disabledCapabilities"].disabledCapabilities.value // library marker replica.samsungReplicaCommon, line 67
	} catch (e) { } // library marker replica.samsungReplicaCommon, line 68

	def enabledCapabilities = [] // library marker replica.samsungReplicaCommon, line 70
	Map description // library marker replica.samsungReplicaCommon, line 71
	try { // library marker replica.samsungReplicaCommon, line 72
		description = new JsonSlurper().parseText(getDataValue("description")) // library marker replica.samsungReplicaCommon, line 73
	} catch (error) { // library marker replica.samsungReplicaCommon, line 74
		logWarn("checkCapabilities.  Data element Description not loaded. Run Configure") // library marker replica.samsungReplicaCommon, line 75
	} // library marker replica.samsungReplicaCommon, line 76
	def thisComponent = description.components.find { it.id == componentId } // library marker replica.samsungReplicaCommon, line 77
	thisComponent.capabilities.each { capability -> // library marker replica.samsungReplicaCommon, line 78
		if (designCapabilities().contains(capability.id) && // library marker replica.samsungReplicaCommon, line 79
			!disabledCapabilities.contains(capability.id)) { // library marker replica.samsungReplicaCommon, line 80
			enabledCapabilities << capability.id // library marker replica.samsungReplicaCommon, line 81
		} // library marker replica.samsungReplicaCommon, line 82
	} // library marker replica.samsungReplicaCommon, line 83
	state.deviceCapabilities = enabledCapabilities // library marker replica.samsungReplicaCommon, line 84
	runIn(1, configureChildren, [data: components]) // library marker replica.samsungReplicaCommon, line 85
	runIn(5, refreshAttributes, [data: components]) // library marker replica.samsungReplicaCommon, line 86
	logInfo("checkCapabilities: [design: ${designCapabilities()}, disabled: ${disabledCapabilities}, enabled: ${enabledCapabilities}]") // library marker replica.samsungReplicaCommon, line 87
} // library marker replica.samsungReplicaCommon, line 88

//	===== Child Configure / Install ===== // library marker replica.samsungReplicaCommon, line 90
def configureChildren(components) { // library marker replica.samsungReplicaCommon, line 91
	def logData = [:] // library marker replica.samsungReplicaCommon, line 92
	def componentId = getDataValue("componentId") // library marker replica.samsungReplicaCommon, line 93
	def disabledComponents = [] // library marker replica.samsungReplicaCommon, line 94
	try { // library marker replica.samsungReplicaCommon, line 95
		disabledComponents << components[componentId]["custom.disabledComponents"].disabledComponents.value // library marker replica.samsungReplicaCommon, line 96
	} catch (e) { } // library marker replica.samsungReplicaCommon, line 97
	designChildren().each { designChild -> // library marker replica.samsungReplicaCommon, line 98
		if (disabledComponents.contains(designChild.key)) { // library marker replica.samsungReplicaCommon, line 99
			logData << ["${designChild.key}": [status: "SmartThingsDisabled"]] // library marker replica.samsungReplicaCommon, line 100
		} else { // library marker replica.samsungReplicaCommon, line 101
			def dni = device.getDeviceNetworkId() // library marker replica.samsungReplicaCommon, line 102
			def childDni = "dni-${designChild.key}" // library marker replica.samsungReplicaCommon, line 103
			def child = getChildDevice(childDni) // library marker replica.samsungReplicaCommon, line 104
			if (child == null) { // library marker replica.samsungReplicaCommon, line 105
				def type = "Replica ${appliance()} ${designChild.value}" // library marker replica.samsungReplicaCommon, line 106
				def name = "${device.displayName} ${designChild.key}" // library marker replica.samsungReplicaCommon, line 107
				try { // library marker replica.samsungReplicaCommon, line 108
					addChildDevice("replicaChild", "${type}", "${childDni}", [ // library marker replica.samsungReplicaCommon, line 109
						name: type,  // library marker replica.samsungReplicaCommon, line 110
						label: name, // library marker replica.samsungReplicaCommon, line 111
						componentId: designChild.key // library marker replica.samsungReplicaCommon, line 112
					]) // library marker replica.samsungReplicaCommon, line 113
					logData << ["${name}": [status: "installed"]] // library marker replica.samsungReplicaCommon, line 114
				} catch (error) { // library marker replica.samsungReplicaCommon, line 115
					logData << ["${name}": [status: "FAILED", reason: error]] // library marker replica.samsungReplicaCommon, line 116
				} // library marker replica.samsungReplicaCommon, line 117
			} else { // library marker replica.samsungReplicaCommon, line 118
				child.checkCapabilities(components) // library marker replica.samsungReplicaCommon, line 119
				logData << ["${name}": [status: "already installed"]] // library marker replica.samsungReplicaCommon, line 120
			} // library marker replica.samsungReplicaCommon, line 121
		} // library marker replica.samsungReplicaCommon, line 122
	} // library marker replica.samsungReplicaCommon, line 123
	runIn(1, checkChildren, [data: components]) // library marker replica.samsungReplicaCommon, line 124
	runIn(3, refreshAttributes, [data: components]) // library marker replica.samsungReplicaCommon, line 125
	logInfo("configureChildren: ${logData}") // library marker replica.samsungReplicaCommon, line 126
} // library marker replica.samsungReplicaCommon, line 127

def checkChildren(components) { // library marker replica.samsungReplicaCommon, line 129
	getChildDevices().each { // library marker replica.samsungReplicaCommon, line 130
		it.checkCapabilities(components) // library marker replica.samsungReplicaCommon, line 131
	} // library marker replica.samsungReplicaCommon, line 132
} // library marker replica.samsungReplicaCommon, line 133

//	===== Attributes // library marker replica.samsungReplicaCommon, line 135
def refreshAttributes(components) { // library marker replica.samsungReplicaCommon, line 136
	state.refreshAttributes = false // library marker replica.samsungReplicaCommon, line 137
	def component = components."${getDataValue("componentId")}" // library marker replica.samsungReplicaCommon, line 138
	logDebug("refreshAttributes: ${component}") // library marker replica.samsungReplicaCommon, line 139
	component.each { capability -> // library marker replica.samsungReplicaCommon, line 140
		capability.value.each { attribute -> // library marker replica.samsungReplicaCommon, line 141
			parseEvent([capability: capability.key, // library marker replica.samsungReplicaCommon, line 142
						attribute: attribute.key, // library marker replica.samsungReplicaCommon, line 143
						value: attribute.value.value, // library marker replica.samsungReplicaCommon, line 144
						unit: attribute.value.unit]) // library marker replica.samsungReplicaCommon, line 145
			pauseExecution(50) // library marker replica.samsungReplicaCommon, line 146
		} // library marker replica.samsungReplicaCommon, line 147
	} // library marker replica.samsungReplicaCommon, line 148
	getChildDevices().each { // library marker replica.samsungReplicaCommon, line 149
		it.refreshAttributes(components) // library marker replica.samsungReplicaCommon, line 150
	} // library marker replica.samsungReplicaCommon, line 151
} // library marker replica.samsungReplicaCommon, line 152

void replicaHealth(def parent=null, Map health=null) { // library marker replica.samsungReplicaCommon, line 154
	if(parent) { logInfo("replicaHealth: ${parent?.getLabel()}") } // library marker replica.samsungReplicaCommon, line 155
	if(health) { logInfo("replicaHealth: ${health}") } // library marker replica.samsungReplicaCommon, line 156
} // library marker replica.samsungReplicaCommon, line 157

def setHealthStatusValue(value) {     // library marker replica.samsungReplicaCommon, line 159
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value") // library marker replica.samsungReplicaCommon, line 160
} // library marker replica.samsungReplicaCommon, line 161

void replicaEvent(def parent=null, Map event=null) { // library marker replica.samsungReplicaCommon, line 163
	if (event.deviceEvent.componentId == getDataValue("componentId")) { // library marker replica.samsungReplicaCommon, line 164
		try { // library marker replica.samsungReplicaCommon, line 165
			parseEvent(event.deviceEvent) // library marker replica.samsungReplicaCommon, line 166
		} catch (err) { // library marker replica.samsungReplicaCommon, line 167
			logWarn("replicaEvent: [event = ${event}, error: ${err}") // library marker replica.samsungReplicaCommon, line 168
		} // library marker replica.samsungReplicaCommon, line 169
	} else { // library marker replica.samsungReplicaCommon, line 170
		getChildDevices().each {  // library marker replica.samsungReplicaCommon, line 171
			it.parentEvent(event) // library marker replica.samsungReplicaCommon, line 172
		} // library marker replica.samsungReplicaCommon, line 173
	} // library marker replica.samsungReplicaCommon, line 174
} // library marker replica.samsungReplicaCommon, line 175

def parseCorrect(event) { // library marker replica.samsungReplicaCommon, line 177
	logTrace("parseCorrect: <b>${event}</b>") // library marker replica.samsungReplicaCommon, line 178
	if (device.currentValue(event.attribute).toString() != event.value.toString()) { // library marker replica.samsungReplicaCommon, line 179
		sendEvent(name: event.attribute, value: event.value, unit: event.unit) // library marker replica.samsungReplicaCommon, line 180
		logInfo("parseCorrect: [event: ${event}]") // library marker replica.samsungReplicaCommon, line 181
	} // library marker replica.samsungReplicaCommon, line 182
} // library marker replica.samsungReplicaCommon, line 183

def setState(event) { // library marker replica.samsungReplicaCommon, line 185
	def attribute = event.attribute // library marker replica.samsungReplicaCommon, line 186
	if (state."${attribute}" != event.value) { // library marker replica.samsungReplicaCommon, line 187
		state."${event.attribute}" = event.value // library marker replica.samsungReplicaCommon, line 188
		logInfo("setState: [event: ${event}]") // library marker replica.samsungReplicaCommon, line 189
	} // library marker replica.samsungReplicaCommon, line 190
} // library marker replica.samsungReplicaCommon, line 191

def sendCommand(String name, def value=null, String unit=null, data=[:]) { // library marker replica.samsungReplicaCommon, line 193
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now]) // library marker replica.samsungReplicaCommon, line 194
} // library marker replica.samsungReplicaCommon, line 195

//	===== Refresh Commands ===== // library marker replica.samsungReplicaCommon, line 197
def refresh() { // library marker replica.samsungReplicaCommon, line 198
	logDebug("refresh") // library marker replica.samsungReplicaCommon, line 199
	state.refreshAttributes = true // library marker replica.samsungReplicaCommon, line 200
	sendCommand("deviceRefresh") // library marker replica.samsungReplicaCommon, line 201
	runIn(1, sendCommand, [data: ["refresh"]]) // library marker replica.samsungReplicaCommon, line 202
} // library marker replica.samsungReplicaCommon, line 203

def deviceRefresh() { // library marker replica.samsungReplicaCommon, line 205
	sendCommand("deviceRefresh") // library marker replica.samsungReplicaCommon, line 206
} // library marker replica.samsungReplicaCommon, line 207

// ~~~~~ end include (1251) replica.samsungReplicaCommon ~~~~~

// ~~~~~ start include (1072) davegut.Logging ~~~~~
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
		logInfo("Attributes: ${attrList}") // library marker davegut.Logging, line 19
	} else { // library marker davegut.Logging, line 20
		logDebug("Attributes: ${attrList}") // library marker davegut.Logging, line 21
	} // library marker davegut.Logging, line 22
} // library marker davegut.Logging, line 23

def logTrace(msg){ // library marker davegut.Logging, line 25
	if (traceLog == true) { // library marker davegut.Logging, line 26
		log.trace "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.Logging, line 27
	} // library marker davegut.Logging, line 28
} // library marker davegut.Logging, line 29

def traceLogOff() { // library marker davegut.Logging, line 31
	device.updateSetting("traceLog", [type:"bool", value: false]) // library marker davegut.Logging, line 32
	logInfo("traceLogOff") // library marker davegut.Logging, line 33
} // library marker davegut.Logging, line 34

def logInfo(msg) {  // library marker davegut.Logging, line 36
	if (textEnable || infoLog) { // library marker davegut.Logging, line 37
		log.info "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.Logging, line 38
	} // library marker davegut.Logging, line 39
} // library marker davegut.Logging, line 40

def debugLogOff() { // library marker davegut.Logging, line 42
	device.updateSetting("logEnable", [type:"bool", value: false]) // library marker davegut.Logging, line 43
	logInfo("debugLogOff") // library marker davegut.Logging, line 44
} // library marker davegut.Logging, line 45

def logDebug(msg) { // library marker davegut.Logging, line 47
	if (logEnable || debugLog) { // library marker davegut.Logging, line 48
		log.debug "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.Logging, line 49
	} // library marker davegut.Logging, line 50
} // library marker davegut.Logging, line 51

def logWarn(msg) { log.warn "${device.displayName}-${driverVer()}: ${msg}" } // library marker davegut.Logging, line 53

// ~~~~~ end include (1072) davegut.Logging ~~~~~
