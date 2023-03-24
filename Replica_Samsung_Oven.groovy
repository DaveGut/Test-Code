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
def driverVer() { return "1.0T3" }
def appliance() { return "Samsung Oven" }

metadata {
	definition (name: "Replica ${appliance()}",
				namespace: "replica",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/HubiThingsReplica%20Drivers/Replica_Samsung_Oven.groovy"
			   ){
		capability "Configuration"
		capability "Refresh"
		attribute "healthStatus", "enum", ["offline", "online"]
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

//	===== Installation, setup and update =====
def installed() {
	updateDataValue("componentId", "main")
	runIn(1, updated)
}

def updated() {
	unschedule()
//	configure()
	pauseExecution(2000)
	def updStatus = [:]
	if (!getDataValue("driverVersion") || getDataValue("driverVersion") != driverVer()) {
		updateDataValue("driverVersion", driverVer())
		updStatus << [driverVer: driverVer()]
	}
	if (logEnable) { runIn(1800, debugLogOff) }
	if (traceLog) { runIn(600, traceLogOff) }
	updStatus << [logEnable: logEnable, infoLog: infoLog, traceLog: traceLog]
	runIn(3, configure)
//	refresh()
//	runIn(5, listAttributes,[data:true])
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
	def deviceId = new JSONObject(getDataValue("description")).deviceId
	def status = parent.setSmartDeviceCommand(deviceId, component, capability, command, arguments)
	return [component, capability, command, arguments, status]
}

//	===== Device Commands =====
//	Common parent/child Oven commands are in library replica.samsungReplicaOvenCommon
def setProbeSetpoint(temperature) {
	if (device.currentValue("probeStatus") != "disconnected") {
		temperature = temperature.toInteger()
		if (temperature > 0) {
			def status = sendRawCommand(getDataValue("componentId"), "samsungce.meatProbe", "setTemperatureSetpoint", [temperature])
			logInfo("setProbeSetpoint: ${status}")
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
		def status = sendRawCommand(getDataValue("componentId"), "samsungce.lamp", "setBrightnessLevel", [lightLevel])
		logInfo("setOvenLight: ${status}")
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

//	===== Common Capabilities, Commands, and Attributes ===== // library marker replica.samsungOvenCommon, line 11
command "setOvenSetpoint", [[name: "oven temperature", type: "NUMBER"]] // library marker replica.samsungOvenCommon, line 12
attribute "ovenSetpoint", "number" // library marker replica.samsungOvenCommon, line 13
attribute "ovenTemperature", "number"	//	attr.temperature // library marker replica.samsungOvenCommon, line 14
command "setOvenMode", [[name: "from state.supported OvenModes", type:"STRING"]] // library marker replica.samsungOvenCommon, line 15
attribute "ovenMode", "string" // library marker replica.samsungOvenCommon, line 16
command "stop" // library marker replica.samsungOvenCommon, line 17
command "start", [[name: "mode", type: "STRING"], // library marker replica.samsungOvenCommon, line 18
				  [name: "time (hh:mm:ss OR secs)", type: "STRING"], // library marker replica.samsungOvenCommon, line 19
				  [name: "setpoint", type: "NUMBER"]] // library marker replica.samsungOvenCommon, line 20
attribute "completionTime", "string"	//	time string // library marker replica.samsungOvenCommon, line 21
attribute "progress", "number"			//	percent // library marker replica.samsungOvenCommon, line 22
attribute "operatingState", "string"	//	attr.machineState // library marker replica.samsungOvenCommon, line 23
attribute "ovenJobState", "string" // library marker replica.samsungOvenCommon, line 24
attribute "operationTime", "string" // library marker replica.samsungOvenCommon, line 25
command "setOperationTime", [[name: "time (hh:mm:ss OR secs)", type: "STRING"]] // library marker replica.samsungOvenCommon, line 26

def parseEvent(event) { // library marker replica.samsungOvenCommon, line 28
	logDebug("parseEvent: <b>${event}</b>") // library marker replica.samsungOvenCommon, line 29
	if (state.deviceCapabilities.contains(event.capability)) { // library marker replica.samsungOvenCommon, line 30
		logTrace("parseEvent: <b>${event}</b>") // library marker replica.samsungOvenCommon, line 31
		if (event.value != null) { // library marker replica.samsungOvenCommon, line 32
			switch(event.attribute) { // library marker replica.samsungOvenCommon, line 33
				case "machineState": // library marker replica.samsungOvenCommon, line 34
					if (!state.deviceCapabilities.contains("samsungce.ovenOperatingState")) { // library marker replica.samsungOvenCommon, line 35
						event.attribute = "operatingState" // library marker replica.samsungOvenCommon, line 36
						setEvent(event) // library marker replica.samsungOvenCommon, line 37
					} // library marker replica.samsungOvenCommon, line 38
					break // library marker replica.samsungOvenCommon, line 39
				case "operationTime": // library marker replica.samsungOvenCommon, line 40
					def opTime = formatTime(event.value, "hhmmss", "parseEvent") // library marker replica.samsungOvenCommon, line 41
					event.value = opTime // library marker replica.samsungOvenCommon, line 42
				case "completionTime": // library marker replica.samsungOvenCommon, line 43
				case "progress": // library marker replica.samsungOvenCommon, line 44
				case "ovenJobState": // library marker replica.samsungOvenCommon, line 45
				case "operationTime": // library marker replica.samsungOvenCommon, line 46
					if (state.deviceCapabilities.contains("samsungce.ovenOperatingState")) { // library marker replica.samsungOvenCommon, line 47
						if (event.capability == "samsungce.ovenOperatingState") { // library marker replica.samsungOvenCommon, line 48
							setEvent(event) // library marker replica.samsungOvenCommon, line 49
						} // library marker replica.samsungOvenCommon, line 50
					} else { // library marker replica.samsungOvenCommon, line 51
						setEvent(event) // library marker replica.samsungOvenCommon, line 52
					} // library marker replica.samsungOvenCommon, line 53
					break // library marker replica.samsungOvenCommon, line 54
				case "temperature": // library marker replica.samsungOvenCommon, line 55
					def attr = "ovenTemperature" // library marker replica.samsungOvenCommon, line 56
					if (event.capability == "samsungce.meatProbe") { // library marker replica.samsungOvenCommon, line 57
						attr = "probeTemperature" // library marker replica.samsungOvenCommon, line 58
					} // library marker replica.samsungOvenCommon, line 59
					event["attribute"] = attr // library marker replica.samsungOvenCommon, line 60
					setEvent(event) // library marker replica.samsungOvenCommon, line 61
					break // library marker replica.samsungOvenCommon, line 62
				case "temperatureSetpoint": // library marker replica.samsungOvenCommon, line 63
					event["attribute"] = "probeSetpoint" // library marker replica.samsungOvenCommon, line 64
					setEvent(event) // library marker replica.samsungOvenCommon, line 65
					break // library marker replica.samsungOvenCommon, line 66
				case "status": // library marker replica.samsungOvenCommon, line 67
					event["attribute"] = "probeStatus" // library marker replica.samsungOvenCommon, line 68
					setEvent(event) // library marker replica.samsungOvenCommon, line 69
					break // library marker replica.samsungOvenCommon, line 70
				case "ovenMode": // library marker replica.samsungOvenCommon, line 71
					if (state.deviceCapabilities.contains("samsungce.ovenMode")) { // library marker replica.samsungOvenCommon, line 72
						if (event.capability == "samsungce.ovenMode") { // library marker replica.samsungOvenCommon, line 73
							setEvent(event) // library marker replica.samsungOvenCommon, line 74
						} // library marker replica.samsungOvenCommon, line 75
					} else { // library marker replica.samsungOvenCommon, line 76
						setEvent(event) // library marker replica.samsungOvenCommon, line 77
					} // library marker replica.samsungOvenCommon, line 78
					break // library marker replica.samsungOvenCommon, line 79
				case "supportedOvenModes": // library marker replica.samsungOvenCommon, line 80
				//	if samsungce.ovenMode, use that, otherwise use // library marker replica.samsungOvenCommon, line 81
				//	ovenMode.  Format always hh:mm:ss. // library marker replica.samsungOvenCommon, line 82
					if (state.deviceCapabilities.contains("samsungce.ovenMode")) { // library marker replica.samsungOvenCommon, line 83
						if (event.capability == "samsungce.ovenMode") { // library marker replica.samsungOvenCommon, line 84
							setState(event) // library marker replica.samsungOvenCommon, line 85
						} // library marker replica.samsungOvenCommon, line 86
					} else { // library marker replica.samsungOvenCommon, line 87
						setState(event) // library marker replica.samsungOvenCommon, line 88
					} // library marker replica.samsungOvenCommon, line 89
					break // library marker replica.samsungOvenCommon, line 90
				case "supportedBrightnessLevel": // library marker replica.samsungOvenCommon, line 91
					setState(event) // library marker replica.samsungOvenCommon, line 92
					break // library marker replica.samsungOvenCommon, line 93
				case "supportedCooktopOperatingState": // library marker replica.samsungOvenCommon, line 94
					break // library marker replica.samsungOvenCommon, line 95
				default: // library marker replica.samsungOvenCommon, line 96
					setEvent(event) // library marker replica.samsungOvenCommon, line 97
					break // library marker replica.samsungOvenCommon, line 98
			} // library marker replica.samsungOvenCommon, line 99
		} // library marker replica.samsungOvenCommon, line 100
	} // library marker replica.samsungOvenCommon, line 101
} // library marker replica.samsungOvenCommon, line 102

def setState(event) { // library marker replica.samsungOvenCommon, line 104
	def attribute = event.attribute // library marker replica.samsungOvenCommon, line 105
	if (state."${attribute}" != event.value) { // library marker replica.samsungOvenCommon, line 106
		state."${event.attribute}" = event.value // library marker replica.samsungOvenCommon, line 107
		logInfo("setState: [event: ${event}]") // library marker replica.samsungOvenCommon, line 108
	} // library marker replica.samsungOvenCommon, line 109
} // library marker replica.samsungOvenCommon, line 110





def setEvent(event) { // library marker replica.samsungOvenCommon, line 116
	logTrace("<b>setEvent</b>: ${event}") // library marker replica.samsungOvenCommon, line 117






	if (device.currentValue(event.attribute).toString() != event.value.toString()) { // library marker replica.samsungOvenCommon, line 124
log.trace "<b>setEvent</b>: ${event}" // library marker replica.samsungOvenCommon, line 125
		sendEvent(name: event.attribute, value: event.value, unit: event.unit) // library marker replica.samsungOvenCommon, line 126
		logInfo("setEvent: [event: ${event}]") // library marker replica.samsungOvenCommon, line 127
	} // library marker replica.samsungOvenCommon, line 128
} // library marker replica.samsungOvenCommon, line 129





//	===== Device Commands ===== // library marker replica.samsungOvenCommon, line 135
def setOvenMode(mode) { // library marker replica.samsungOvenCommon, line 136
	//	mode: string, from supportedOvenModes // library marker replica.samsungOvenCommon, line 137
	def ovenMode = checkMode(mode) // library marker replica.samsungOvenCommon, line 138
	def status = [mode: mode, ovenMode: ovenMode] // library marker replica.samsungOvenCommon, line 139
	if (ovenMode == "notSupported") { // library marker replica.samsungOvenCommon, line 140
		status << [error: "modeNotSupported"] // library marker replica.samsungOvenCommon, line 141
		logWarn(setOvenMode: status) // library marker replica.samsungOvenCommon, line 142
	} else { // library marker replica.samsungOvenCommon, line 143
		status << sendRawCommand(getDataValue("componentId"), "ovenMode", "setOvenMode", [ovenMode]) // library marker replica.samsungOvenCommon, line 144
		logInfo("setOvenMode: ${status}") // library marker replica.samsungOvenCommon, line 145
	} // library marker replica.samsungOvenCommon, line 146
} // library marker replica.samsungOvenCommon, line 147

def checkMode(mode) { // library marker replica.samsungOvenCommon, line 149
	mode = state.supportedOvenModes.find { it.toLowerCase() == mode.toLowerCase() } // library marker replica.samsungOvenCommon, line 150
	if (mode == null) { // library marker replica.samsungOvenCommon, line 151
		mode = "notSupported" // library marker replica.samsungOvenCommon, line 152
	} // library marker replica.samsungOvenCommon, line 153
	return mode // library marker replica.samsungOvenCommon, line 154
} // library marker replica.samsungOvenCommon, line 155

def setOvenSetpoint(setpoint) { // library marker replica.samsungOvenCommon, line 157
	setpoint = setpoint.toInteger() // library marker replica.samsungOvenCommon, line 158
	def status = [setpoint: setpoint] // library marker replica.samsungOvenCommon, line 159
	if (setpoint >= 0) { // library marker replica.samsungOvenCommon, line 160
		status << sendRawCommand(getDataValue("componentId"), "ovenSetpoint", "setOvenSetpoint", [setpoint]) // library marker replica.samsungOvenCommon, line 161
		logInfo("setOvenSetpoint: ${status}") // library marker replica.samsungOvenCommon, line 162
	} else { // library marker replica.samsungOvenCommon, line 163
		status << [error: "invalidSetpoint"] // library marker replica.samsungOvenCommon, line 164
		logWarn("setOvenSetpoint: ${status}") // library marker replica.samsungOvenCommon, line 165
	} // library marker replica.samsungOvenCommon, line 166
} // library marker replica.samsungOvenCommon, line 167

def setOperationTime(opTime) { // library marker replica.samsungOvenCommon, line 169
	opTime = formatTime(opTime, "seconds", "setOperationTime") // library marker replica.samsungOvenCommon, line 170
	def status = [opTime: opTime] // library marker replica.samsungOvenCommon, line 171
	if (opTime != "invalidEntry") { // library marker replica.samsungOvenCommon, line 172
		status << sendRawCommand(getDataValue("componentId"), "ovenOperatingState", "start", [time: opTime]) // library marker replica.samsungOvenCommon, line 173
		logInfo("setOperationTime: ${status}") // library marker replica.samsungOvenCommon, line 174
	} else { // library marker replica.samsungOvenCommon, line 175
		status << [error: "invalidOpTime"] // library marker replica.samsungOvenCommon, line 176
		logWarn("setOperationTime: ${status}") // library marker replica.samsungOvenCommon, line 177
	} // library marker replica.samsungOvenCommon, line 178
} // library marker replica.samsungOvenCommon, line 179

def start(mode = null, opTime = null, setpoint = null) { // library marker replica.samsungOvenCommon, line 181
	def status = [input: [mode: mode, opTime: opTime, setpoint: setpoint]] // library marker replica.samsungOvenCommon, line 182
	if (mode == null && opTime == null && setpoint == null) { // library marker replica.samsungOvenCommon, line 183
		status << sendRawCommand(getDataValue("componentId"), "ovenOperatingState", "start", []) // library marker replica.samsungOvenCommon, line 184
	} else { // library marker replica.samsungOvenCommon, line 185
		if (mode == null) {  // library marker replica.samsungOvenCommon, line 186
			mode = device.currentValue("ovenMode") // library marker replica.samsungOvenCommon, line 187
		} // library marker replica.samsungOvenCommon, line 188
		if (opTime == null) {  // library marker replica.samsungOvenCommon, line 189
			opTime = device.currentValue("operationTime") // library marker replica.samsungOvenCommon, line 190
		} // library marker replica.samsungOvenCommon, line 191
		if (setpoint == null) { // library marker replica.samsungOvenCommon, line 192
			setpoint = device.currentValue("ovenSetpoint") // library marker replica.samsungOvenCommon, line 193
		} // library marker replica.samsungOvenCommon, line 194
		opTime = formatTime(opTime, "seconds", "start").toInteger() // library marker replica.samsungOvenCommon, line 195
		def opMode = checkMode(mode) // library marker replica.samsungOvenCommon, line 196
		status << [opTime: opTime, mode: opMode, setpoint: setpoint] // library marker replica.samsungOvenCommon, line 197
		if (setpoint >= 0 && opMode != "notSupported" && opTime != "invalidEntry") { // library marker replica.samsungOvenCommon, line 198
			status << sendRawCommand(getDataValue("componentId"), "ovenOperatingState",  // library marker replica.samsungOvenCommon, line 199
										"start",[mode: opMode, time: opTime, setpoint: setpoint]) // library marker replica.samsungOvenCommon, line 200
		} else { // library marker replica.samsungOvenCommon, line 201
			status << [Error: "argument out of range"] // library marker replica.samsungOvenCommon, line 202
		} // library marker replica.samsungOvenCommon, line 203
	} // library marker replica.samsungOvenCommon, line 204
	logInfo("start: ${status}") // library marker replica.samsungOvenCommon, line 205
} // library marker replica.samsungOvenCommon, line 206

def checkValues(checkData) { // library marker replica.samsungOvenCommon, line 208
	//	Validate current machine states match called-for states then start. // library marker replica.samsungOvenCommon, line 209
	def respData = [:] // library marker replica.samsungOvenCommon, line 210
	def checkStatus = "OK" // library marker replica.samsungOvenCommon, line 211
	if (checkData.mode.toLowerCase() != device.currentValue("ovenMode").toLowerCase()) { // library marker replica.samsungOvenCommon, line 212
		respData << [mode: [check: checkData.mode, attribute: device.currentValue("ovenMode")]] // library marker replica.samsungOvenCommon, line 213
		checkStatus = "MISMATCH" // library marker replica.samsungOvenCommon, line 214
	} // library marker replica.samsungOvenCommon, line 215

	//	Change opTime and operation time to seconds (as string) (if not already) to simplify comparison. // library marker replica.samsungOvenCommon, line 217
	def checkOpTime = formatTime(checkData.opTime, "seconds", "checkValues") // library marker replica.samsungOvenCommon, line 218
	def checkOperationTime = formatTime(device.currentValue("operationTime"), "seconds", "checkValues") // library marker replica.samsungOvenCommon, line 219
	if (checkOpTime != checkOperationTime) { // library marker replica.samsungOvenCommon, line 220
		respData << [opTime: [check: checkData.opTime, attribute: device.currentValue("operationTime")]] // library marker replica.samsungOvenCommon, line 221
		checkStatus = "MISMATCH" // library marker replica.samsungOvenCommon, line 222
	} // library marker replica.samsungOvenCommon, line 223

	if (checkData.setpoint != device.currentValue("ovenSetpoint")) { // library marker replica.samsungOvenCommon, line 225
		respData << [setpoint: [check: checkData.setpoint, attribute: device.currentValue("ovenSetpoint")]] // library marker replica.samsungOvenCommon, line 226
		checkStatus = "MISMATCH" // library marker replica.samsungOvenCommon, line 227
	} // library marker replica.samsungOvenCommon, line 228
	if (checkStatus == "MISMATCH") { // library marker replica.samsungOvenCommon, line 229
		logWarn("checkValues: [status: ${checkStatus}, error: Oven not started due to mismatch]") // library marker replica.samsungOvenCommon, line 230
	} // library marker replica.samsungOvenCommon, line 231
	return [status: checkStatus, data: respData] // library marker replica.samsungOvenCommon, line 232
} // library marker replica.samsungOvenCommon, line 233

def stop() { // library marker replica.samsungOvenCommon, line 235
	def status = sendRawCommand(getDataValue("componentId"), "ovenOperatingState", "stop") // library marker replica.samsungOvenCommon, line 236
	logInfo("stop: ${status}") // library marker replica.samsungOvenCommon, line 237
	return status // library marker replica.samsungOvenCommon, line 238
} // library marker replica.samsungOvenCommon, line 239

def formatTime(timeValue, desiredFormat, callMethod) { // library marker replica.samsungOvenCommon, line 241
	timeValue = timeValue.toString() // library marker replica.samsungOvenCommon, line 242
	def currentFormat = "seconds" // library marker replica.samsungOvenCommon, line 243
	if (timeValue.contains(":")) { // library marker replica.samsungOvenCommon, line 244
		currentFormat = "hhmmss" // library marker replica.samsungOvenCommon, line 245
	} // library marker replica.samsungOvenCommon, line 246
	def formatedTime // library marker replica.samsungOvenCommon, line 247
	if (currentFormat == "hhmmss") { // library marker replica.samsungOvenCommon, line 248
		formatedTime = formatHhmmss(timeValue) // library marker replica.samsungOvenCommon, line 249
		if (desiredFormat == "seconds") { // library marker replica.samsungOvenCommon, line 250
			formatedTime = convertHhMmSsToInt(formatedTime) // library marker replica.samsungOvenCommon, line 251
		} // library marker replica.samsungOvenCommon, line 252
	} else { // library marker replica.samsungOvenCommon, line 253
		formatedTime = timeValue // library marker replica.samsungOvenCommon, line 254
		if (desiredFormat == "hhmmss") { // library marker replica.samsungOvenCommon, line 255
			formatedTime = convertIntToHhMmSs(timeValue) // library marker replica.samsungOvenCommon, line 256
		} // library marker replica.samsungOvenCommon, line 257
	} // library marker replica.samsungOvenCommon, line 258
	if (formatedTime == "invalidEntry") { // library marker replica.samsungOvenCommon, line 259
		Map errorData = [callMethod: callMethod, timeValue: timeValue, // library marker replica.samsungOvenCommon, line 260
						 desiredFormat: desiredFormat] // library marker replica.samsungOvenCommon, line 261
		logWarn("formatTime: [error: ${formatedTime}, data: ${errorData}") // library marker replica.samsungOvenCommon, line 262
	} // library marker replica.samsungOvenCommon, line 263
	return formatedTime // library marker replica.samsungOvenCommon, line 264
} // library marker replica.samsungOvenCommon, line 265

def formatHhmmss(timeValue) { // library marker replica.samsungOvenCommon, line 267
	def timeArray = timeValue.split(":") // library marker replica.samsungOvenCommon, line 268
	def hours = 0 // library marker replica.samsungOvenCommon, line 269
	def minutes = 0 // library marker replica.samsungOvenCommon, line 270
	def seconds = 0 // library marker replica.samsungOvenCommon, line 271
	if (timeArray.size() != timeValue.count(":") + 1) { // library marker replica.samsungOvenCommon, line 272
		return "invalidEntry" // library marker replica.samsungOvenCommon, line 273
	} else { // library marker replica.samsungOvenCommon, line 274
		try { // library marker replica.samsungOvenCommon, line 275
			if (timeArray.size() == 3) { // library marker replica.samsungOvenCommon, line 276
				hours = timeArray[0].toInteger() // library marker replica.samsungOvenCommon, line 277
				minutes = timeArray[1].toInteger() // library marker replica.samsungOvenCommon, line 278
				seconds = timeArray[2].toInteger() // library marker replica.samsungOvenCommon, line 279
			} else if (timeArray.size() == 2) { // library marker replica.samsungOvenCommon, line 280
				minutes = timeArray[0].toInteger() // library marker replica.samsungOvenCommon, line 281
				seconds = timeArray[1].toInteger() // library marker replica.samsungOvenCommon, line 282
			} // library marker replica.samsungOvenCommon, line 283
		} catch (error) { // library marker replica.samsungOvenCommon, line 284
			return "invalidEntry" // library marker replica.samsungOvenCommon, line 285
		} // library marker replica.samsungOvenCommon, line 286
	} // library marker replica.samsungOvenCommon, line 287
	if (hours < 10) { hours = "0${hours}" } // library marker replica.samsungOvenCommon, line 288
	if (minutes < 10) { minutes = "0${minutes}" } // library marker replica.samsungOvenCommon, line 289
	if (seconds < 10) { seconds = "0${seconds}" } // library marker replica.samsungOvenCommon, line 290
	return "${hours}:${minutes}:${seconds}" // library marker replica.samsungOvenCommon, line 291
} // library marker replica.samsungOvenCommon, line 292

def convertIntToHhMmSs(timeSeconds) { // library marker replica.samsungOvenCommon, line 294
	def hhmmss // library marker replica.samsungOvenCommon, line 295
	try { // library marker replica.samsungOvenCommon, line 296
		hhmmss = new GregorianCalendar( 0, 0, 0, 0, 0, timeSeconds.toInteger(), 0 ).time.format( 'HH:mm:ss' ) // library marker replica.samsungOvenCommon, line 297
	} catch (error) { // library marker replica.samsungOvenCommon, line 298
		hhmmss = "invalidEntry" // library marker replica.samsungOvenCommon, line 299
	} // library marker replica.samsungOvenCommon, line 300
	return hhmmss // library marker replica.samsungOvenCommon, line 301
} // library marker replica.samsungOvenCommon, line 302

def convertHhMmSsToInt(timeValue) { // library marker replica.samsungOvenCommon, line 304
	def timeArray = timeValue.split(":") // library marker replica.samsungOvenCommon, line 305
	def seconds = 0 // library marker replica.samsungOvenCommon, line 306
	if (timeArray.size() != timeValue.count(":") + 1) { // library marker replica.samsungOvenCommon, line 307
		return "invalidEntry" // library marker replica.samsungOvenCommon, line 308
	} else { // library marker replica.samsungOvenCommon, line 309
		try { // library marker replica.samsungOvenCommon, line 310
			if (timeArray.size() == 3) { // library marker replica.samsungOvenCommon, line 311
				seconds = timeArray[0].toInteger() * 3600 + // library marker replica.samsungOvenCommon, line 312
				timeArray[1].toInteger() * 60 + timeArray[2].toInteger() // library marker replica.samsungOvenCommon, line 313
			} else if (timeArray.size() == 2) { // library marker replica.samsungOvenCommon, line 314
				seconds = timeArray[0].toInteger() * 60 + timeArray[1].toInteger() // library marker replica.samsungOvenCommon, line 315
			} // library marker replica.samsungOvenCommon, line 316
		} catch (error) { // library marker replica.samsungOvenCommon, line 317
			seconds = "invalidEntry" // library marker replica.samsungOvenCommon, line 318
		} // library marker replica.samsungOvenCommon, line 319
	} // library marker replica.samsungOvenCommon, line 320
	return seconds // library marker replica.samsungOvenCommon, line 321
} // library marker replica.samsungOvenCommon, line 322

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
	updateDataValue("rules", getReplicaRules()) // library marker replica.samsungReplicaCommon, line 19
//	setReplicaRules() // library marker replica.samsungReplicaCommon, line 20
	logData << [triggers: "initialized", commands: "initialized", rules: "initialized"] // library marker replica.samsungReplicaCommon, line 21
	logData << [replicaRules: "initialized"] // library marker replica.samsungReplicaCommon, line 22
	state.checkCapabilities = true // library marker replica.samsungReplicaCommon, line 23
	sendCommand("configure") // library marker replica.samsungReplicaCommon, line 24
	logData: [device: "configuring HubiThings"] // library marker replica.samsungReplicaCommon, line 25
//	refresh() // library marker replica.samsungReplicaCommon, line 26
	runIn(5, listAttributes,[data:true]) // library marker replica.samsungReplicaCommon, line 27
	logInfo("configure: ${logData}") // library marker replica.samsungReplicaCommon, line 28
} // library marker replica.samsungReplicaCommon, line 29

Map getReplicaCommands() { // library marker replica.samsungReplicaCommon, line 31
    return (["replicaEvent":[[name:"parent*",type:"OBJECT"],[name:"event*",type:"JSON_OBJECT"]],  // library marker replica.samsungReplicaCommon, line 32
			 "replicaStatus":[[name:"parent*",type:"OBJECT"],[name:"event*",type:"JSON_OBJECT"]],  // library marker replica.samsungReplicaCommon, line 33
			 "replicaHealth":[[name:"parent*",type:"OBJECT"],[name:"health*",type:"JSON_OBJECT"]], // library marker replica.samsungReplicaCommon, line 34
			 "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]]) // library marker replica.samsungReplicaCommon, line 35
} // library marker replica.samsungReplicaCommon, line 36

Map getReplicaTriggers() { // library marker replica.samsungReplicaCommon, line 38
	return [refresh:[], deviceRefresh: []] // library marker replica.samsungReplicaCommon, line 39
} // library marker replica.samsungReplicaCommon, line 40

String getReplicaRules() { // library marker replica.samsungReplicaCommon, line 42
	return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"},{"trigger":{"name":"deviceRefresh","label":"command: deviceRefresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"}]}""" // library marker replica.samsungReplicaCommon, line 43
} // library marker replica.samsungReplicaCommon, line 44

//	===== Event Parse Interface s===== // library marker replica.samsungReplicaCommon, line 46
void replicaStatus(def parent=null, Map status=null) { // library marker replica.samsungReplicaCommon, line 47
	def logData = [parent: parent, status: status] // library marker replica.samsungReplicaCommon, line 48
	if (state.checkCapabilities) { // library marker replica.samsungReplicaCommon, line 49
		runIn(4, checkCapabilities, [data: status.components]) // library marker replica.samsungReplicaCommon, line 50
	} else if (state.refreshAttributes) { // library marker replica.samsungReplicaCommon, line 51
		refreshAttributes(status.components) // library marker replica.samsungReplicaCommon, line 52
	} // library marker replica.samsungReplicaCommon, line 53
	logDebug("replicaStatus: ${logData}") // library marker replica.samsungReplicaCommon, line 54
} // library marker replica.samsungReplicaCommon, line 55

def checkCapabilities(components) { // library marker replica.samsungReplicaCommon, line 57
	state.checkCapabilities = false // library marker replica.samsungReplicaCommon, line 58
	def componentId = getDataValue("componentId") // library marker replica.samsungReplicaCommon, line 59
	def disabledCapabilities = [] // library marker replica.samsungReplicaCommon, line 60
	try { // library marker replica.samsungReplicaCommon, line 61
		disabledCapabilities << components[componentId]["custom.disabledCapabilities"].disabledCapabilities.value // library marker replica.samsungReplicaCommon, line 62
	} catch (e) { } // library marker replica.samsungReplicaCommon, line 63

	def enabledCapabilities = [] // library marker replica.samsungReplicaCommon, line 65
	Map description // library marker replica.samsungReplicaCommon, line 66
	try { // library marker replica.samsungReplicaCommon, line 67
		description = new JsonSlurper().parseText(getDataValue("description")) // library marker replica.samsungReplicaCommon, line 68
	} catch (error) { // library marker replica.samsungReplicaCommon, line 69
		logWarn("checkCapabilities.  Data element Description not loaded. Run Configure") // library marker replica.samsungReplicaCommon, line 70
	} // library marker replica.samsungReplicaCommon, line 71
	def thisComponent = description.components.find { it.id == componentId } // library marker replica.samsungReplicaCommon, line 72
	thisComponent.capabilities.each { capability -> // library marker replica.samsungReplicaCommon, line 73
		if (designCapabilities().contains(capability.id) && // library marker replica.samsungReplicaCommon, line 74
			!disabledCapabilities.contains(capability.id)) { // library marker replica.samsungReplicaCommon, line 75
			enabledCapabilities << capability.id // library marker replica.samsungReplicaCommon, line 76
		} // library marker replica.samsungReplicaCommon, line 77
	} // library marker replica.samsungReplicaCommon, line 78
	state.deviceCapabilities = enabledCapabilities // library marker replica.samsungReplicaCommon, line 79
	runIn(1, configureChildren, [data: components]) // library marker replica.samsungReplicaCommon, line 80
	runIn(5, refreshAttributes, [data: components]) // library marker replica.samsungReplicaCommon, line 81
	logInfo("checkCapabilities: [design: ${designCapabilities()}, disabled: ${disabledCapabilities}, enabled: ${enabledCapabilities}]") // library marker replica.samsungReplicaCommon, line 82
} // library marker replica.samsungReplicaCommon, line 83

//	===== Child Configure / Install ===== // library marker replica.samsungReplicaCommon, line 85
def configureChildren(components) { // library marker replica.samsungReplicaCommon, line 86
	def logData = [:] // library marker replica.samsungReplicaCommon, line 87
	def componentId = getDataValue("componentId") // library marker replica.samsungReplicaCommon, line 88
	def disabledComponents = [] // library marker replica.samsungReplicaCommon, line 89
	try { // library marker replica.samsungReplicaCommon, line 90
		disabledComponents << components[componentId]["custom.disabledComponents"].disabledComponents.value // library marker replica.samsungReplicaCommon, line 91
	} catch (e) { } // library marker replica.samsungReplicaCommon, line 92
	designChildren().each { designChild -> // library marker replica.samsungReplicaCommon, line 93
		if (disabledComponents.contains(designChild.key)) { // library marker replica.samsungReplicaCommon, line 94
			logData << ["${designChild.key}": [status: "SmartThingsDisabled"]] // library marker replica.samsungReplicaCommon, line 95
		} else { // library marker replica.samsungReplicaCommon, line 96
			def dni = device.getDeviceNetworkId() // library marker replica.samsungReplicaCommon, line 97
			def childDni = "dni-${designChild.key}" // library marker replica.samsungReplicaCommon, line 98
			def child = getChildDevice(childDni) // library marker replica.samsungReplicaCommon, line 99
			if (child == null) { // library marker replica.samsungReplicaCommon, line 100
				def type = "Replica ${appliance()} ${designChild.value}" // library marker replica.samsungReplicaCommon, line 101
				def name = "${device.displayName} ${designChild.key}" // library marker replica.samsungReplicaCommon, line 102
				try { // library marker replica.samsungReplicaCommon, line 103
					addChildDevice("replicaChild", "${type}", "${childDni}", [ // library marker replica.samsungReplicaCommon, line 104
						name: type,  // library marker replica.samsungReplicaCommon, line 105
						label: name, // library marker replica.samsungReplicaCommon, line 106
						componentId: designChild.key // library marker replica.samsungReplicaCommon, line 107
					]) // library marker replica.samsungReplicaCommon, line 108
					logData << ["${name}": [status: "installed"]] // library marker replica.samsungReplicaCommon, line 109
				} catch (error) { // library marker replica.samsungReplicaCommon, line 110
					logData << ["${name}": [status: "FAILED", reason: error]] // library marker replica.samsungReplicaCommon, line 111
				} // library marker replica.samsungReplicaCommon, line 112
			} else { // library marker replica.samsungReplicaCommon, line 113
				child.checkCapabilities(components) // library marker replica.samsungReplicaCommon, line 114
				logData << ["${name}": [status: "already installed"]] // library marker replica.samsungReplicaCommon, line 115
			} // library marker replica.samsungReplicaCommon, line 116
		} // library marker replica.samsungReplicaCommon, line 117
	} // library marker replica.samsungReplicaCommon, line 118
	runIn(1, checkChildren, [data: components]) // library marker replica.samsungReplicaCommon, line 119
	runIn(3, refreshAttributes, [data: components]) // library marker replica.samsungReplicaCommon, line 120
	logInfo("configureChildren: ${logData}") // library marker replica.samsungReplicaCommon, line 121
} // library marker replica.samsungReplicaCommon, line 122

def checkChildren(components) { // library marker replica.samsungReplicaCommon, line 124
	getChildDevices().each { // library marker replica.samsungReplicaCommon, line 125
		it.checkCapabilities(components) // library marker replica.samsungReplicaCommon, line 126
	} // library marker replica.samsungReplicaCommon, line 127
} // library marker replica.samsungReplicaCommon, line 128

//	===== Attributes // library marker replica.samsungReplicaCommon, line 130
def refreshAttributes(components) { // library marker replica.samsungReplicaCommon, line 131
	state.refreshAttributes = false // library marker replica.samsungReplicaCommon, line 132
	def component = components."${getDataValue("componentId")}" // library marker replica.samsungReplicaCommon, line 133
	logDebug("refreshAttributes: ${component}") // library marker replica.samsungReplicaCommon, line 134
	component.each { capability -> // library marker replica.samsungReplicaCommon, line 135
		capability.value.each { attribute -> // library marker replica.samsungReplicaCommon, line 136
			parseEvent([capability: capability.key, // library marker replica.samsungReplicaCommon, line 137
						attribute: attribute.key, // library marker replica.samsungReplicaCommon, line 138
						value: attribute.value.value, // library marker replica.samsungReplicaCommon, line 139
						unit: attribute.value.unit]) // library marker replica.samsungReplicaCommon, line 140
			pauseExecution(50) // library marker replica.samsungReplicaCommon, line 141
		} // library marker replica.samsungReplicaCommon, line 142
	} // library marker replica.samsungReplicaCommon, line 143
	getChildDevices().each { // library marker replica.samsungReplicaCommon, line 144
		it.refreshAttributes(components) // library marker replica.samsungReplicaCommon, line 145
	} // library marker replica.samsungReplicaCommon, line 146
} // library marker replica.samsungReplicaCommon, line 147

void replicaHealth(def parent=null, Map health=null) { // library marker replica.samsungReplicaCommon, line 149
	if(parent) { logInfo("replicaHealth: ${parent?.getLabel()}") } // library marker replica.samsungReplicaCommon, line 150
	if(health) { logInfo("replicaHealth: ${health}") } // library marker replica.samsungReplicaCommon, line 151
} // library marker replica.samsungReplicaCommon, line 152

def setHealthStatusValue(value) {     // library marker replica.samsungReplicaCommon, line 154
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value") // library marker replica.samsungReplicaCommon, line 155
} // library marker replica.samsungReplicaCommon, line 156

void replicaEvent(def parent=null, Map event=null) { // library marker replica.samsungReplicaCommon, line 158
//	if (event.deviceEvent.componentId == getDataValue("componentId")) { // library marker replica.samsungReplicaCommon, line 159
	if (event && event.deviceEvent.componentId == getDataValue("componentId")) { // library marker replica.samsungReplicaCommon, line 160
		try { // library marker replica.samsungReplicaCommon, line 161
			parseEvent(event.deviceEvent) // library marker replica.samsungReplicaCommon, line 162
		} catch (err) { // library marker replica.samsungReplicaCommon, line 163
			logWarn("replicaEvent: [event = ${event}, error: ${err}") // library marker replica.samsungReplicaCommon, line 164
		} // library marker replica.samsungReplicaCommon, line 165
	} else { // library marker replica.samsungReplicaCommon, line 166
		getChildDevices().each {  // library marker replica.samsungReplicaCommon, line 167
			it.parentEvent(event) // library marker replica.samsungReplicaCommon, line 168
		} // library marker replica.samsungReplicaCommon, line 169
	} // library marker replica.samsungReplicaCommon, line 170
} // library marker replica.samsungReplicaCommon, line 171

def sendCommand(String name, def value=null, String unit=null, data=[:]) { // library marker replica.samsungReplicaCommon, line 173
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now]) // library marker replica.samsungReplicaCommon, line 174
} // library marker replica.samsungReplicaCommon, line 175

//	===== Refresh Commands ===== // library marker replica.samsungReplicaCommon, line 177
def refresh() { // library marker replica.samsungReplicaCommon, line 178
	logDebug("refresh") // library marker replica.samsungReplicaCommon, line 179
	state.refreshAttributes = true // library marker replica.samsungReplicaCommon, line 180
	sendCommand("deviceRefresh") // library marker replica.samsungReplicaCommon, line 181
	runIn(1, sendCommand, [data: ["refresh"]]) // library marker replica.samsungReplicaCommon, line 182
} // library marker replica.samsungReplicaCommon, line 183

def deviceRefresh() { // library marker replica.samsungReplicaCommon, line 185
	sendCommand("deviceRefresh") // library marker replica.samsungReplicaCommon, line 186
} // library marker replica.samsungReplicaCommon, line 187

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

// ~~~~~ start include (1257) replica.samsungOvenTest ~~~~~
library ( // library marker replica.samsungOvenTest, line 1
	name: "samsungOvenTest", // library marker replica.samsungOvenTest, line 2
	namespace: "replica", // library marker replica.samsungOvenTest, line 3
	author: "Dave Gutheinz", // library marker replica.samsungOvenTest, line 4
	description: "Test Methods for replica Samsung Oven parent/children", // library marker replica.samsungOvenTest, line 5
	category: "utilities", // library marker replica.samsungOvenTest, line 6
	documentationLink: "" // library marker replica.samsungOvenTest, line 7
) // library marker replica.samsungOvenTest, line 8
//	Version 1.0 // library marker replica.samsungOvenTest, line 9


//	===== Test Commands ===== // library marker replica.samsungOvenTest, line 12
command "aTest1" // library marker replica.samsungOvenTest, line 13
command "aTest2" // library marker replica.samsungOvenTest, line 14
command "aReady" // library marker replica.samsungOvenTest, line 15
command "aPaused" // library marker replica.samsungOvenTest, line 16
command "aRunning" // library marker replica.samsungOvenTest, line 17

def aReady() { // library marker replica.samsungOvenTest, line 19
	setEvent([attribute: "operatingState", value: "ready", unit: null]) // library marker replica.samsungOvenTest, line 20
} // library marker replica.samsungOvenTest, line 21
def aPaused() { // library marker replica.samsungOvenTest, line 22
	setEvent([attribute: "operatingState", value: "paused", unit: null]) // library marker replica.samsungOvenTest, line 23
} // library marker replica.samsungOvenTest, line 24
def aRunning() { // library marker replica.samsungOvenTest, line 25
	setEvent([attribute: "operatingState", value: "running", unit: null]) // library marker replica.samsungOvenTest, line 26
} // library marker replica.samsungOvenTest, line 27

def aTest1() { // library marker replica.samsungOvenTest, line 29
//	use samsungce.XX capabilities where available // library marker replica.samsungOvenTest, line 30
//	Stop, mode: bake, setpoint: 333, operatingTime: 01:01:01 // library marker replica.samsungOvenTest, line 31
	log.trace "===== start samsungce ControlTest: ${getTestAttrs()} =====" // library marker replica.samsungOvenTest, line 32
	if (device.currentValue("operatingState") != "ready") { // library marker replica.samsungOvenTest, line 33
		sendRawCommand("main", "samsungce.ovenOperatingState", "stop") // library marker replica.samsungOvenTest, line 34
	} // library marker replica.samsungOvenTest, line 35
	runIn(10, samsungce_start) // library marker replica.samsungOvenTest, line 36
} // library marker replica.samsungOvenTest, line 37

def samsungce_start() { // library marker replica.samsungOvenTest, line 39
	if (device.currentValue("operatingState") != "ready") { // library marker replica.samsungOvenTest, line 40
		logWarn "samsungce STOP Failed.  Test Aborted" // library marker replica.samsungOvenTest, line 41
		samsungce_completeTest() // library marker replica.samsungOvenTest, line 42
	} else { // library marker replica.samsungOvenTest, line 43
		sendRawCommand("main", "samsungce.ovenMode", "setOvenMode", ["Bake"]) // library marker replica.samsungOvenTest, line 44
		pauseExecution(9000) // library marker replica.samsungOvenTest, line 45
		sendRawCommand("main", "ovenSetpoint", "setOvenSetpoint", [333]) // library marker replica.samsungOvenTest, line 46
		pauseExecution(9000) // library marker replica.samsungOvenTest, line 47
		sendRawCommand("main", "samsungce.ovenOperatingState", "setOperationTime", ["01:01:01"]) // library marker replica.samsungOvenTest, line 48
		pauseExecution(9000) // library marker replica.samsungOvenTest, line 49
		log.trace "samsungceSetParams: [priorToStart: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 50
		sendRawCommand("main", "samsungce.ovenOperatingState", "start") // library marker replica.samsungOvenTest, line 51
		runIn(30, samsungce_modifyParams) // library marker replica.samsungOvenTest, line 52
	} // library marker replica.samsungOvenTest, line 53
} // library marker replica.samsungOvenTest, line 54

def samsungce_modifyParams() { // library marker replica.samsungOvenTest, line 56
	log.trace "samsungceStart: [FinishStartTest: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 57
	if (device.currentValue("operatingState") != "running") { // library marker replica.samsungOvenTest, line 58
		log.warn "samsungce START Failed.  TEST Aborted." // library marker replica.samsungOvenTest, line 59
		samsungce_completeTest() // library marker replica.samsungOvenTest, line 60
	} else { // library marker replica.samsungOvenTest, line 61
//	set mode to convectionBake, setpoint to 388, and operation time to 00:35:00 // library marker replica.samsungOvenTest, line 62
		sendRawCommand("main", "samsungce.ovenMode", "setOvenMode", ["ConvectionBake"]) // library marker replica.samsungOvenTest, line 63
		pauseExecution(9000) // library marker replica.samsungOvenTest, line 64
		sendRawCommand("main", "ovenSetpoint", "setOvenSetpoint", [380]) // library marker replica.samsungOvenTest, line 65
		pauseExecution(9000) // library marker replica.samsungOvenTest, line 66
		sendRawCommand("main", "samsungce.ovenOperatingState", "setOperationTime", ["00:35:00"]) // library marker replica.samsungOvenTest, line 67
		runIn(30, samsungce_pauseTest) // library marker replica.samsungOvenTest, line 68
	} // library marker replica.samsungOvenTest, line 69
} // library marker replica.samsungOvenTest, line 70

def samsungce_pauseTest() { // library marker replica.samsungOvenTest, line 72
	log.trace "samsungceModify: [FinishModifyTest: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 73
	if (device.currentValue("operatingState") != "running") { // library marker replica.samsungOvenTest, line 74
		log.warn "MODIFY PARAMS CAUSED FAILURE.  TEST Aborted." // library marker replica.samsungOvenTest, line 75
		samsungce_completeTest() // library marker replica.samsungOvenTest, line 76
	} else { // library marker replica.samsungOvenTest, line 77
//	set mode to convectionBake, setpoint to 388, and operation time to 00:35:00 // library marker replica.samsungOvenTest, line 78
		sendRawCommand("main", "samsungce.ovenOperatingState", "pause") // library marker replica.samsungOvenTest, line 79
		runIn(30, samsungce_restartTest) // library marker replica.samsungOvenTest, line 80
	} // library marker replica.samsungOvenTest, line 81
} // library marker replica.samsungOvenTest, line 82

def samsungce_restartTest() { // library marker replica.samsungOvenTest, line 84
	log.trace "samsungcePause: [FinishPauseTest: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 85
	if (device.currentValue("operatingState") != "paused") { // library marker replica.samsungOvenTest, line 86
		log.warn "MODIFY PARAMS CAUSED FAILURE.  TEST Aborted." // library marker replica.samsungOvenTest, line 87
		samsungce_completeTest() // library marker replica.samsungOvenTest, line 88
	} else { // library marker replica.samsungOvenTest, line 89
//	set mode to convectionBake, setpoint to 388, and operation time to 00:35:00 // library marker replica.samsungOvenTest, line 90
		sendRawCommand("main", "samsungce.ovenOperatingState", "start") // library marker replica.samsungOvenTest, line 91
		runIn(30, samsungce_completeTest) // library marker replica.samsungOvenTest, line 92
	} // library marker replica.samsungOvenTest, line 93
} // library marker replica.samsungOvenTest, line 94

def samsungce_completeTest() { // library marker replica.samsungOvenTest, line 96
	log.trace "samsungceFinalAttrs: ${getTestAttrs()}" // library marker replica.samsungOvenTest, line 97
	if (device.currentValue("operatingState") != "ready") { // library marker replica.samsungOvenTest, line 98
		sendRawCommand("main", "samsungce.ovenOperatingState", "stop") // library marker replica.samsungOvenTest, line 99
	} // library marker replica.samsungOvenTest, line 100
	log.trace "===================== END samsungce Control Test =========================" // library marker replica.samsungOvenTest, line 101
} // library marker replica.samsungOvenTest, line 102


def aTest2() { // library marker replica.samsungOvenTest, line 105
//	use production capabilities and custom capabilities only // library marker replica.samsungOvenTest, line 106
//	Stop, mode: ConvectionBake, setpoint: 355, operatingTime: 3600 // library marker replica.samsungOvenTest, line 107
	log.trace "===== start basic ControlTest: ${getTestAttrs()} =====" // library marker replica.samsungOvenTest, line 108
	if (device.currentValue("operatingState") != "ready") { // library marker replica.samsungOvenTest, line 109
		sendRawCommand("main", "ovenOperatingState", "stop") // library marker replica.samsungOvenTest, line 110
	} // library marker replica.samsungOvenTest, line 111
	runIn(10, basic_start) // library marker replica.samsungOvenTest, line 112
} // library marker replica.samsungOvenTest, line 113

def basic_start() { // library marker replica.samsungOvenTest, line 115
	if (device.currentValue("operatingState") != "ready") { // library marker replica.samsungOvenTest, line 116
		logWarn "basic STOP Failed.  Test Aborted" // library marker replica.samsungOvenTest, line 117
		basic_completeTest() // library marker replica.samsungOvenTest, line 118
	} else { // library marker replica.samsungOvenTest, line 119
		sendRawCommand("main", "ovenMode", "setOvenMode", ["ConvectionBake"]) // library marker replica.samsungOvenTest, line 120
		pauseExecution(9000) // library marker replica.samsungOvenTest, line 121
		sendRawCommand("main", "ovenSetpoint", "setOvenSetpoint", [355]) // library marker replica.samsungOvenTest, line 122
		pauseExecution(9000) // library marker replica.samsungOvenTest, line 123
		log.trace "basicSetParams: [priorToStart: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 124
		//	Test setting opTime with start command.  (may fail???) // library marker replica.samsungOvenTest, line 125
		sendRawCommand("main", "ovenOperatingState", "start", [time: 3600]) // library marker replica.samsungOvenTest, line 126
		runIn(30, basic_validateStart) // library marker replica.samsungOvenTest, line 127
	} // library marker replica.samsungOvenTest, line 128
} // library marker replica.samsungOvenTest, line 129

def basic_validateStart() { // library marker replica.samsungOvenTest, line 131
	log.trace "basicStart: [FinishStartTest: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 132
	if (device.currentValue("operatingState") != "running") { // library marker replica.samsungOvenTest, line 133
		log.warn "basic START with opTime Failed.  Try full start command." // library marker replica.samsungOvenTest, line 134
		sendRawCommand("main", "ovenOperatingState", "start", [mode: "ConvectionBake", time: 3600, setpoint: 355]) // library marker replica.samsungOvenTest, line 135
		runIn(30, basic_modifyParams) // library marker replica.samsungOvenTest, line 136
	} else { // library marker replica.samsungOvenTest, line 137
		basic_modifyParams() // library marker replica.samsungOvenTest, line 138
	} // library marker replica.samsungOvenTest, line 139
} // library marker replica.samsungOvenTest, line 140

def basic_modifyParams() { // library marker replica.samsungOvenTest, line 142
	log.trace "basicStart: [FinishStartTest: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 143
	if (device.currentValue("operatingState") != "running") { // library marker replica.samsungOvenTest, line 144
		log.warn "basic START Failed.  TEST Aborted." // library marker replica.samsungOvenTest, line 145
		basic_completeTest() // library marker replica.samsungOvenTest, line 146
	} else { // library marker replica.samsungOvenTest, line 147
//	set mode to Bake, setpoint to 315, and operation time to 1800 // library marker replica.samsungOvenTest, line 148
		sendRawCommand("main", "ovenMode", "setOvenMode", ["Bake"]) // library marker replica.samsungOvenTest, line 149
		pauseExecution(9000) // library marker replica.samsungOvenTest, line 150
		sendRawCommand("main", "ovenSetpoint", "setOvenSetpoint", [325]) // library marker replica.samsungOvenTest, line 151
		pauseExecution(9000) // library marker replica.samsungOvenTest, line 152
		sendRawCommand("main", "ovenOperatingState", "start", [time: 1800]) // library marker replica.samsungOvenTest, line 153
		runIn(30, basic_pauseTest) // library marker replica.samsungOvenTest, line 154
	} // library marker replica.samsungOvenTest, line 155
} // library marker replica.samsungOvenTest, line 156

def basic_pauseTest() { // library marker replica.samsungOvenTest, line 158
	log.trace "basicModify: [FinishModifyTest: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 159
	if (device.currentValue("operatingState") != "running") { // library marker replica.samsungOvenTest, line 160
		log.warn "MODIFY PARAMS CAUSED FAILURE.  TEST Aborted." // library marker replica.samsungOvenTest, line 161
		basic_completeTest() // library marker replica.samsungOvenTest, line 162
	} else { // library marker replica.samsungOvenTest, line 163
//	set mode to convectionBake, setpoint to 388, and operation time to 00:35:00 // library marker replica.samsungOvenTest, line 164
		sendRawCommand("main", "ovenOperatingState", "setMachineState", ["paused"]) // library marker replica.samsungOvenTest, line 165
		runIn(30, basic_restartTest) // library marker replica.samsungOvenTest, line 166
	} // library marker replica.samsungOvenTest, line 167
} // library marker replica.samsungOvenTest, line 168

def basic_restartTest() { // library marker replica.samsungOvenTest, line 170
	log.trace "basicPause: [FinishPauseTest: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 171
	if (device.currentValue("operatingState") != "paused") { // library marker replica.samsungOvenTest, line 172
		log.warn "PAUSE CAUSED FAILURE.  TEST Aborted." // library marker replica.samsungOvenTest, line 173
		basic_completeTest() // library marker replica.samsungOvenTest, line 174
	} else { // library marker replica.samsungOvenTest, line 175
//	set mode to convectionBake, setpoint to 388, and operation time to 00:35:00 // library marker replica.samsungOvenTest, line 176
		sendRawCommand("main", "ovenOperatingState", "setMachineState", ["running"]) // library marker replica.samsungOvenTest, line 177
		runIn(30, basic_completeTest) // library marker replica.samsungOvenTest, line 178
	} // library marker replica.samsungOvenTest, line 179
} // library marker replica.samsungOvenTest, line 180

def basic_completeTest() { // library marker replica.samsungOvenTest, line 182
	log.trace "basicFinalAttrs: ${getTestAttrs()}" // library marker replica.samsungOvenTest, line 183
	if (device.currentValue("operatingState") != "ready") { // library marker replica.samsungOvenTest, line 184
		sendRawCommand("main", "ovenOperatingState", "stop") // library marker replica.samsungOvenTest, line 185
	} // library marker replica.samsungOvenTest, line 186
	log.trace "===================== END basic Control Test =========================" // library marker replica.samsungOvenTest, line 187
} // library marker replica.samsungOvenTest, line 188

//	Test Utilities // library marker replica.samsungOvenTest, line 190
def getTestAttrs() { // library marker replica.samsungOvenTest, line 191
	def attrs = [ // library marker replica.samsungOvenTest, line 192
		mode: device.currentValue("ovenMode"), // library marker replica.samsungOvenTest, line 193
		setpoint: device.currentValue("ovenSetpoint"), // library marker replica.samsungOvenTest, line 194
		opTime: device.currentValue("operationTime"), // library marker replica.samsungOvenTest, line 195
		opState: device.currentValue("operatingState"), // library marker replica.samsungOvenTest, line 196
		jobState: device.currentValue("ovenJobState"), // library marker replica.samsungOvenTest, line 197
		remoteControl: device.currentValue("remoteControlEnabled"), // library marker replica.samsungOvenTest, line 198
		kidsLock: device.currentValue("lockState"), // library marker replica.samsungOvenTest, line 199
		door: device.currentValue("doorState") // library marker replica.samsungOvenTest, line 200
		] // library marker replica.samsungOvenTest, line 201
	return attrs // library marker replica.samsungOvenTest, line 202
} // library marker replica.samsungOvenTest, line 203

// ~~~~~ end include (1257) replica.samsungOvenTest ~~~~~
