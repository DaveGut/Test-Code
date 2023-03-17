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
command "pause" // library marker replica.samsungOvenCommon, line 26
command "setOperationTime", [[name: "time (hh:mm:ss OR secs)", type: "STRING"]] // library marker replica.samsungOvenCommon, line 27

def parseEvent(event) { // library marker replica.samsungOvenCommon, line 29
	logDebug("parseEvent: <b>${event}</b>") // library marker replica.samsungOvenCommon, line 30
	if (state.deviceCapabilities.contains(event.capability)) { // library marker replica.samsungOvenCommon, line 31
		logTrace("parseEvent: <b>${event}</b>") // library marker replica.samsungOvenCommon, line 32
		if (event.value != null) { // library marker replica.samsungOvenCommon, line 33
			switch(event.attribute) { // library marker replica.samsungOvenCommon, line 34
				case "machineState": // library marker replica.samsungOvenCommon, line 35
					if (!state.deviceCapabilities.contains("samsungce.ovenOperatingState")) { // library marker replica.samsungOvenCommon, line 36
						event.attribute = "operatingState" // library marker replica.samsungOvenCommon, line 37
						setEvent(event) // library marker replica.samsungOvenCommon, line 38
					} // library marker replica.samsungOvenCommon, line 39
					break // library marker replica.samsungOvenCommon, line 40
				case "operationTime": // library marker replica.samsungOvenCommon, line 41
					def opTime = formatTime(event.value, "hhmmss", "parseEvent") // library marker replica.samsungOvenCommon, line 42
					event.value = opTime // library marker replica.samsungOvenCommon, line 43
				case "completionTime": // library marker replica.samsungOvenCommon, line 44
				case "progress": // library marker replica.samsungOvenCommon, line 45
				case "ovenJobState": // library marker replica.samsungOvenCommon, line 46
				case "operationTime": // library marker replica.samsungOvenCommon, line 47
					if (state.deviceCapabilities.contains("samsungce.ovenOperatingState")) { // library marker replica.samsungOvenCommon, line 48
						if (event.capability == "samsungce.ovenOperatingState") { // library marker replica.samsungOvenCommon, line 49
							setEvent(event) // library marker replica.samsungOvenCommon, line 50
						} // library marker replica.samsungOvenCommon, line 51
					} else { // library marker replica.samsungOvenCommon, line 52
						setEvent(event) // library marker replica.samsungOvenCommon, line 53
					} // library marker replica.samsungOvenCommon, line 54
					break // library marker replica.samsungOvenCommon, line 55
				case "temperature": // library marker replica.samsungOvenCommon, line 56
					def attr = "ovenTemperature" // library marker replica.samsungOvenCommon, line 57
					if (event.capability == "samsungce.meatProbe") { // library marker replica.samsungOvenCommon, line 58
						attr = "probeTemperature" // library marker replica.samsungOvenCommon, line 59
					} // library marker replica.samsungOvenCommon, line 60
					event["attribute"] = attr // library marker replica.samsungOvenCommon, line 61
					setEvent(event) // library marker replica.samsungOvenCommon, line 62
					break // library marker replica.samsungOvenCommon, line 63
				case "temperatureSetpoint": // library marker replica.samsungOvenCommon, line 64
					event["attribute"] = "probeSetpoint" // library marker replica.samsungOvenCommon, line 65
					setEvent(event) // library marker replica.samsungOvenCommon, line 66
					break // library marker replica.samsungOvenCommon, line 67
				case "status": // library marker replica.samsungOvenCommon, line 68
					event["attribute"] = "probeStatus" // library marker replica.samsungOvenCommon, line 69
					setEvent(event) // library marker replica.samsungOvenCommon, line 70
					break // library marker replica.samsungOvenCommon, line 71
				case "ovenMode": // library marker replica.samsungOvenCommon, line 72
					if (state.deviceCapabilities.contains("samsungce.ovenMode")) { // library marker replica.samsungOvenCommon, line 73
						if (event.capability == "samsungce.ovenMode") { // library marker replica.samsungOvenCommon, line 74
							setEvent(event) // library marker replica.samsungOvenCommon, line 75
						} // library marker replica.samsungOvenCommon, line 76
					} else { // library marker replica.samsungOvenCommon, line 77
						setEvent(event) // library marker replica.samsungOvenCommon, line 78
					} // library marker replica.samsungOvenCommon, line 79
					break // library marker replica.samsungOvenCommon, line 80
				case "supportedOvenModes": // library marker replica.samsungOvenCommon, line 81
				//	if samsungce.ovenMode, use that, otherwise use // library marker replica.samsungOvenCommon, line 82
				//	ovenMode.  Format always hh:mm:ss. // library marker replica.samsungOvenCommon, line 83
					if (state.deviceCapabilities.contains("samsungce.ovenMode")) { // library marker replica.samsungOvenCommon, line 84
						if (event.capability == "samsungce.ovenMode") { // library marker replica.samsungOvenCommon, line 85
							setState(event) // library marker replica.samsungOvenCommon, line 86
						} // library marker replica.samsungOvenCommon, line 87
					} else { // library marker replica.samsungOvenCommon, line 88
						setState(event) // library marker replica.samsungOvenCommon, line 89
					} // library marker replica.samsungOvenCommon, line 90
					break // library marker replica.samsungOvenCommon, line 91
				case "supportedBrightnessLevel": // library marker replica.samsungOvenCommon, line 92
					setState(event) // library marker replica.samsungOvenCommon, line 93
					break // library marker replica.samsungOvenCommon, line 94
				case "supportedCooktopOperatingState": // library marker replica.samsungOvenCommon, line 95
					break // library marker replica.samsungOvenCommon, line 96
				default: // library marker replica.samsungOvenCommon, line 97
					setEvent(event) // library marker replica.samsungOvenCommon, line 98
					break // library marker replica.samsungOvenCommon, line 99
			} // library marker replica.samsungOvenCommon, line 100
		} // library marker replica.samsungOvenCommon, line 101
	} // library marker replica.samsungOvenCommon, line 102
} // library marker replica.samsungOvenCommon, line 103

def setEvent(event) { // library marker replica.samsungOvenCommon, line 105
	logTrace("<b>setEvent</b>: ${event}") // library marker replica.samsungOvenCommon, line 106
	if (device.currentValue(event.attribute).toString() != event.value.toString()) { // library marker replica.samsungOvenCommon, line 107
		sendEvent(name: event.attribute, value: event.value, unit: event.unit) // library marker replica.samsungOvenCommon, line 108
		logInfo("setEvent: [event: ${event}]") // library marker replica.samsungOvenCommon, line 109
	} // library marker replica.samsungOvenCommon, line 110
} // library marker replica.samsungOvenCommon, line 111

//	===== Device Commands ===== // library marker replica.samsungOvenCommon, line 113
def setOvenMode(mode) { // library marker replica.samsungOvenCommon, line 114
	//	mode: string, from supportedOvenModes // library marker replica.samsungOvenCommon, line 115
	def ovenMode = checkMode(mode) // library marker replica.samsungOvenCommon, line 116
	if (ovenMode == "notSupported") { // library marker replica.samsungOvenCommon, line 117
		logWarn("setOvenMode: [error: Mode ${mode} not supported]") // library marker replica.samsungOvenCommon, line 118
	} else { // library marker replica.samsungOvenCommon, line 119
		def status = sendRawCommand(getDataValue("componentId"), "ovenMode", "ovenMode", [ovenMode]) // library marker replica.samsungOvenCommon, line 120
		logInfo("setOvenMode: ${status}") // library marker replica.samsungOvenCommon, line 121
	} // library marker replica.samsungOvenCommon, line 122
	return ovenMode // library marker replica.samsungOvenCommon, line 123
} // library marker replica.samsungOvenCommon, line 124

def checkMode(mode) { // library marker replica.samsungOvenCommon, line 126
	mode = state.supportedOvenModes.find { it.toLowerCase() == mode.toLowerCase() } // library marker replica.samsungOvenCommon, line 127
	if (mode == null) { // library marker replica.samsungOvenCommon, line 128
		mode = "notSupported" // library marker replica.samsungOvenCommon, line 129
	} // library marker replica.samsungOvenCommon, line 130
	return mode // library marker replica.samsungOvenCommon, line 131
} // library marker replica.samsungOvenCommon, line 132

def setOvenSetpoint(setpoint) { // library marker replica.samsungOvenCommon, line 134
	setpoint = setpoint.toInteger() // library marker replica.samsungOvenCommon, line 135
	if (setpoint >= 0) { // library marker replica.samsungOvenCommon, line 136
		def status = sendRawCommand(getDataValue("componentId"), "ovenSetpoint", "setOvenSetpoint", [setpoint]) // library marker replica.samsungOvenCommon, line 137
		logInfo("setOvenSetpoint: ${status}") // library marker replica.samsungOvenCommon, line 138
	} else { // library marker replica.samsungOvenCommon, line 139
		logWarn("setOvenSetpoint: [error: negative value, setpoint: ${setpoint}]") // library marker replica.samsungOvenCommon, line 140
	} // library marker replica.samsungOvenCommon, line 141
} // library marker replica.samsungOvenCommon, line 142

def setOperationTime(opTime) { // library marker replica.samsungOvenCommon, line 144
	opTime = formatTime(opTime, "seconds", "setOperationTime") // library marker replica.samsungOvenCommon, line 145
	if (opTime != "invalidEntry") { // library marker replica.samsungOvenCommon, line 146
		def status = sendRawCommand(getDataValue("componentId"), "ovenOperatingState", "start", [time: opTime]) // library marker replica.samsungOvenCommon, line 147
		logInfo("setOperationTime: ${status}") // library marker replica.samsungOvenCommon, line 148
	} else { // library marker replica.samsungOvenCommon, line 149
		logWarn("setOperationTime: [error: ${optime}]") // library marker replica.samsungOvenCommon, line 150
	} // library marker replica.samsungOvenCommon, line 151
} // library marker replica.samsungOvenCommon, line 152

def start(mode = null, opTime = null, setpoint = null) { // library marker replica.samsungOvenCommon, line 154
	def logData = [input: [mode: mode, opTime: opTime, setpoint: setpoint]] // library marker replica.samsungOvenCommon, line 155
	if (mode == null && opTime == null && setpoint == null) { // library marker replica.samsungOvenCommon, line 156
		def status = sendRawCommand(getDataValue("componentId"), "ovenOperatingState", "start", []) // library marker replica.samsungOvenCommon, line 157
		logData << [status: status] // library marker replica.samsungOvenCommon, line 158
	} else { // library marker replica.samsungOvenCommon, line 159
		if (mode == null) {  // library marker replica.samsungOvenCommon, line 160
			mode = device.currentValue("ovenMode") // library marker replica.samsungOvenCommon, line 161
			logData << [mode: mode] // library marker replica.samsungOvenCommon, line 162
		} // library marker replica.samsungOvenCommon, line 163
		if (opTime == null) {  // library marker replica.samsungOvenCommon, line 164
			opTime = device.currentValue("operationTime") // library marker replica.samsungOvenCommon, line 165
			logData << [opTime: opTime] // library marker replica.samsungOvenCommon, line 166
		} // library marker replica.samsungOvenCommon, line 167
		if (setpoint == null) { // library marker replica.samsungOvenCommon, line 168
			setpoint = device.currentValue("ovenSetpoint") // library marker replica.samsungOvenCommon, line 169
			logData << [setpoint: setpoint] // library marker replica.samsungOvenCommon, line 170
		} // library marker replica.samsungOvenCommon, line 171

		logData << [capability: "ovenOperatingState"] // library marker replica.samsungOvenCommon, line 173
		opTime = formatTime(opTime, "seconds", "start").toInteger() // library marker replica.samsungOvenCommon, line 174
		def opMode = checkMode(mode) // library marker replica.samsungOvenCommon, line 175
		logData << [opTime: opTime, mode: opMode, setpoint: setpoint] // library marker replica.samsungOvenCommon, line 176
		if (setpoint >= 0 && opMode != "notSupported" && opTime != "invalidEntry") { // library marker replica.samsungOvenCommon, line 177
			def status = sendRawCommand(getDataValue("componentId"), "ovenOperatingState",  // library marker replica.samsungOvenCommon, line 178
										"start",[mode: opMode, time: opTime, setpoint: setpoint]) // library marker replica.samsungOvenCommon, line 179
			logData << [status: status] // library marker replica.samsungOvenCommon, line 180
		} else { // library marker replica.samsungOvenCommon, line 181
			logData << [start: "failed start", reason: "argument out of range"] // library marker replica.samsungOvenCommon, line 182
		} // library marker replica.samsungOvenCommon, line 183
	} // library marker replica.samsungOvenCommon, line 184
	logInfo("start: ${logData}") // library marker replica.samsungOvenCommon, line 185
} // library marker replica.samsungOvenCommon, line 186

def checkValues(checkData) { // library marker replica.samsungOvenCommon, line 188
	//	Validate current machine states match called-for states then start. // library marker replica.samsungOvenCommon, line 189
	def respData = [:] // library marker replica.samsungOvenCommon, line 190
	def checkStatus = "OK" // library marker replica.samsungOvenCommon, line 191
	if (checkData.mode.toLowerCase() != device.currentValue("ovenMode").toLowerCase()) { // library marker replica.samsungOvenCommon, line 192
		respData << [mode: [check: checkData.mode, attribute: device.currentValue("ovenMode")]] // library marker replica.samsungOvenCommon, line 193
		checkStatus = "MISMATCH" // library marker replica.samsungOvenCommon, line 194
	} // library marker replica.samsungOvenCommon, line 195

	//	Change opTime and operation time to seconds (as string) (if not already) to simplify comparison. // library marker replica.samsungOvenCommon, line 197
	def checkOpTime = formatTime(checkData.opTime, "seconds", "checkValues") // library marker replica.samsungOvenCommon, line 198
	def checkOperationTime = formatTime(device.currentValue("operationTime"), "seconds", "checkValues") // library marker replica.samsungOvenCommon, line 199
	if (checkOpTime != checkOperationTime) { // library marker replica.samsungOvenCommon, line 200
		respData << [opTime: [check: checkData.opTime, attribute: device.currentValue("operationTime")]] // library marker replica.samsungOvenCommon, line 201
		checkStatus = "MISMATCH" // library marker replica.samsungOvenCommon, line 202
	} // library marker replica.samsungOvenCommon, line 203

	if (checkData.setpoint != device.currentValue("ovenSetpoint")) { // library marker replica.samsungOvenCommon, line 205
		respData << [setpoint: [check: checkData.setpoint, attribute: device.currentValue("ovenSetpoint")]] // library marker replica.samsungOvenCommon, line 206
		checkStatus = "MISMATCH" // library marker replica.samsungOvenCommon, line 207
	} // library marker replica.samsungOvenCommon, line 208
	if (checkStatus == "MISMATCH") { // library marker replica.samsungOvenCommon, line 209
		logWarn("checkValues: [status: ${checkStatus}, error: Oven not started due to mismatch]") // library marker replica.samsungOvenCommon, line 210
	} // library marker replica.samsungOvenCommon, line 211
	return [status: checkStatus, data: respData] // library marker replica.samsungOvenCommon, line 212
} // library marker replica.samsungOvenCommon, line 213

def stop() { // library marker replica.samsungOvenCommon, line 215
	def status = sendRawCommand(getDataValue("componentId"), "ovenOperatingState", "stop") // library marker replica.samsungOvenCommon, line 216
	logInfo("stop: ${status}") // library marker replica.samsungOvenCommon, line 217
} // library marker replica.samsungOvenCommon, line 218

def pause() { // library marker replica.samsungOvenCommon, line 220
	//	Only available on samsungce devices. // library marker replica.samsungOvenCommon, line 221
	if (state.deviceCapabilities.contains("samsungce.ovenOperatingState")) { // library marker replica.samsungOvenCommon, line 222
		def status = sendRawCommand(getDataValue("componentId"), "samsungce.ovenOperatingState", "pause") // library marker replica.samsungOvenCommon, line 223
		logInfo("pause: ${status}") // library marker replica.samsungOvenCommon, line 224
	} else { // library marker replica.samsungOvenCommon, line 225
		logWarn("pause: Not available on your device") // library marker replica.samsungOvenCommon, line 226
	} // library marker replica.samsungOvenCommon, line 227
} // library marker replica.samsungOvenCommon, line 228

def formatTime(timeValue, desiredFormat, callMethod) { // library marker replica.samsungOvenCommon, line 230
	timeValue = timeValue.toString() // library marker replica.samsungOvenCommon, line 231
	def currentFormat = "seconds" // library marker replica.samsungOvenCommon, line 232
	if (timeValue.contains(":")) { // library marker replica.samsungOvenCommon, line 233
		currentFormat = "hhmmss" // library marker replica.samsungOvenCommon, line 234
	} // library marker replica.samsungOvenCommon, line 235
	def formatedTime // library marker replica.samsungOvenCommon, line 236
	if (currentFormat == "hhmmss") { // library marker replica.samsungOvenCommon, line 237
		formatedTime = formatHhmmss(timeValue) // library marker replica.samsungOvenCommon, line 238
		if (desiredFormat == "seconds") { // library marker replica.samsungOvenCommon, line 239
			formatedTime = convertHhMmSsToInt(formatedTime) // library marker replica.samsungOvenCommon, line 240
		} // library marker replica.samsungOvenCommon, line 241
	} else { // library marker replica.samsungOvenCommon, line 242
		formatedTime = timeValue // library marker replica.samsungOvenCommon, line 243
		if (desiredFormat == "hhmmss") { // library marker replica.samsungOvenCommon, line 244
			formatedTime = convertIntToHhMmSs(timeValue) // library marker replica.samsungOvenCommon, line 245
		} // library marker replica.samsungOvenCommon, line 246
	} // library marker replica.samsungOvenCommon, line 247
	if (formatedTime == "invalidEntry") { // library marker replica.samsungOvenCommon, line 248
		Map errorData = [callMethod: callMethod, timeValue: timeValue, // library marker replica.samsungOvenCommon, line 249
						 desiredFormat: desiredFormat] // library marker replica.samsungOvenCommon, line 250
		logWarn("formatTime: [error: ${formatedTime}, data: ${errorData}") // library marker replica.samsungOvenCommon, line 251
	} // library marker replica.samsungOvenCommon, line 252
	return formatedTime // library marker replica.samsungOvenCommon, line 253
} // library marker replica.samsungOvenCommon, line 254

def formatHhmmss(timeValue) { // library marker replica.samsungOvenCommon, line 256
	def timeArray = timeValue.split(":") // library marker replica.samsungOvenCommon, line 257
	def hours = 0 // library marker replica.samsungOvenCommon, line 258
	def minutes = 0 // library marker replica.samsungOvenCommon, line 259
	def seconds = 0 // library marker replica.samsungOvenCommon, line 260
	if (timeArray.size() != timeValue.count(":") + 1) { // library marker replica.samsungOvenCommon, line 261
		return "invalidEntry" // library marker replica.samsungOvenCommon, line 262
	} else { // library marker replica.samsungOvenCommon, line 263
		try { // library marker replica.samsungOvenCommon, line 264
			if (timeArray.size() == 3) { // library marker replica.samsungOvenCommon, line 265
				hours = timeArray[0].toInteger() // library marker replica.samsungOvenCommon, line 266
				minutes = timeArray[1].toInteger() // library marker replica.samsungOvenCommon, line 267
				seconds = timeArray[2].toInteger() // library marker replica.samsungOvenCommon, line 268
			} else if (timeArray.size() == 2) { // library marker replica.samsungOvenCommon, line 269
				minutes = timeArray[0].toInteger() // library marker replica.samsungOvenCommon, line 270
				seconds = timeArray[1].toInteger() // library marker replica.samsungOvenCommon, line 271
			} // library marker replica.samsungOvenCommon, line 272
		} catch (error) { // library marker replica.samsungOvenCommon, line 273
			return "invalidEntry" // library marker replica.samsungOvenCommon, line 274
		} // library marker replica.samsungOvenCommon, line 275
	} // library marker replica.samsungOvenCommon, line 276
	if (hours < 10) { hours = "0${hours}" } // library marker replica.samsungOvenCommon, line 277
	if (minutes < 10) { minutes = "0${minutes}" } // library marker replica.samsungOvenCommon, line 278
	if (seconds < 10) { seconds = "0${seconds}" } // library marker replica.samsungOvenCommon, line 279
	return "${hours}:${minutes}:${seconds}" // library marker replica.samsungOvenCommon, line 280
} // library marker replica.samsungOvenCommon, line 281

def convertIntToHhMmSs(timeSeconds) { // library marker replica.samsungOvenCommon, line 283
	def hhmmss // library marker replica.samsungOvenCommon, line 284
	try { // library marker replica.samsungOvenCommon, line 285
		hhmmss = new GregorianCalendar( 0, 0, 0, 0, 0, timeSeconds.toInteger(), 0 ).time.format( 'HH:mm:ss' ) // library marker replica.samsungOvenCommon, line 286
	} catch (error) { // library marker replica.samsungOvenCommon, line 287
		hhmmss = "invalidEntry" // library marker replica.samsungOvenCommon, line 288
	} // library marker replica.samsungOvenCommon, line 289
	return hhmmss // library marker replica.samsungOvenCommon, line 290
} // library marker replica.samsungOvenCommon, line 291

def convertHhMmSsToInt(timeValue) { // library marker replica.samsungOvenCommon, line 293
	def timeArray = timeValue.split(":") // library marker replica.samsungOvenCommon, line 294
	def seconds = 0 // library marker replica.samsungOvenCommon, line 295
	if (timeArray.size() != timeValue.count(":") + 1) { // library marker replica.samsungOvenCommon, line 296
		return "invalidEntry" // library marker replica.samsungOvenCommon, line 297
	} else { // library marker replica.samsungOvenCommon, line 298
		try { // library marker replica.samsungOvenCommon, line 299
			if (timeArray.size() == 3) { // library marker replica.samsungOvenCommon, line 300
				seconds = timeArray[0].toInteger() * 3600 + // library marker replica.samsungOvenCommon, line 301
				timeArray[1].toInteger() * 60 + timeArray[2].toInteger() // library marker replica.samsungOvenCommon, line 302
			} else if (timeArray.size() == 2) { // library marker replica.samsungOvenCommon, line 303
				seconds = timeArray[0].toInteger() * 60 + timeArray[1].toInteger() // library marker replica.samsungOvenCommon, line 304
			} // library marker replica.samsungOvenCommon, line 305
		} catch (error) { // library marker replica.samsungOvenCommon, line 306
			seconds = "invalidEntry" // library marker replica.samsungOvenCommon, line 307
		} // library marker replica.samsungOvenCommon, line 308
	} // library marker replica.samsungOvenCommon, line 309
	return seconds // library marker replica.samsungOvenCommon, line 310
} // library marker replica.samsungOvenCommon, line 311

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
log.warn event // library marker replica.samsungReplicaCommon, line 159
	if (event.deviceEvent.componentId == getDataValue("componentId")) { // library marker replica.samsungReplicaCommon, line 160
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

def parseCorrect(event) { // library marker replica.samsungReplicaCommon, line 173
	logTrace("parseCorrect: <b>${event}</b>") // library marker replica.samsungReplicaCommon, line 174
	if (device.currentValue(event.attribute).toString() != event.value.toString()) { // library marker replica.samsungReplicaCommon, line 175
		sendEvent(name: event.attribute, value: event.value, unit: event.unit) // library marker replica.samsungReplicaCommon, line 176
		logInfo("parseCorrect: [event: ${event}]") // library marker replica.samsungReplicaCommon, line 177
	} // library marker replica.samsungReplicaCommon, line 178
} // library marker replica.samsungReplicaCommon, line 179

def setState(event) { // library marker replica.samsungReplicaCommon, line 181
	def attribute = event.attribute // library marker replica.samsungReplicaCommon, line 182
	if (state."${attribute}" != event.value) { // library marker replica.samsungReplicaCommon, line 183
		state."${event.attribute}" = event.value // library marker replica.samsungReplicaCommon, line 184
		logInfo("setState: [event: ${event}]") // library marker replica.samsungReplicaCommon, line 185
	} // library marker replica.samsungReplicaCommon, line 186
} // library marker replica.samsungReplicaCommon, line 187

def sendCommand(String name, def value=null, String unit=null, data=[:]) { // library marker replica.samsungReplicaCommon, line 189
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now]) // library marker replica.samsungReplicaCommon, line 190
} // library marker replica.samsungReplicaCommon, line 191

//	===== Refresh Commands ===== // library marker replica.samsungReplicaCommon, line 193
def refresh() { // library marker replica.samsungReplicaCommon, line 194
	logDebug("refresh") // library marker replica.samsungReplicaCommon, line 195
	state.refreshAttributes = true // library marker replica.samsungReplicaCommon, line 196
	sendCommand("deviceRefresh") // library marker replica.samsungReplicaCommon, line 197
	runIn(1, sendCommand, [data: ["refresh"]]) // library marker replica.samsungReplicaCommon, line 198
} // library marker replica.samsungReplicaCommon, line 199

def deviceRefresh() { // library marker replica.samsungReplicaCommon, line 201
	sendCommand("deviceRefresh") // library marker replica.samsungReplicaCommon, line 202
} // library marker replica.samsungReplicaCommon, line 203

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

// ~~~~~ start include (1254) replica.samsungOvenTest ~~~~~
library ( // library marker replica.samsungOvenTest, line 1
	name: "samsungOvenTest", // library marker replica.samsungOvenTest, line 2
	namespace: "replica", // library marker replica.samsungOvenTest, line 3
	author: "Dave Gutheinz", // library marker replica.samsungOvenTest, line 4
	description: "Test Methods for replica Samsung Oven parent/children", // library marker replica.samsungOvenTest, line 5
	category: "utilities", // library marker replica.samsungOvenTest, line 6
	documentationLink: "" // library marker replica.samsungOvenTest, line 7
) // library marker replica.samsungOvenTest, line 8
//	Version 1.0 // library marker replica.samsungOvenTest, line 9
/*	===== Tests ===== // library marker replica.samsungOvenTest, line 10
aTest1: Stops oven. Test setOvenMode.  Expected result - light state changes and ovenMode changes. (25 secs) // library marker replica.samsungOvenTest, line 11
aTest2: Set oven setpoint.  Expected result: ovenSetpoint changes. // library marker replica.samsungOvenTest, line 12
aTest3: Set operating time then START.  Expected result: operating time set to 01:01:01 and oven started. // library marker replica.samsungOvenTest, line 13
aTest4: Change Operating time while running.  Expected result: operating time changes to 00:55:55. // library marker replica.samsungOvenTest, line 14
aTest5: Change setpoing while running.  Expected result: ovenSetpoint change to 325 // library marker replica.samsungOvenTest, line 15
aTest6: Change Mode while running.  Expected result: Mode changes to AirFryer. // library marker replica.samsungOvenTest, line 16
aTest7: Pause while running (may not work).  Expected result: operating state changes to "paused"? // library marker replica.samsungOvenTest, line 17
aTest8: Restart after pause.  Expected result (if pause worked): operating state to "running" // library marker replica.samsungOvenTest, line 18
aTest9: Stops the oven operation.  Expected result: oven off. // library marker replica.samsungOvenTest, line 19
*/ // library marker replica.samsungOvenTest, line 20

//	===== Test Commands ===== // library marker replica.samsungOvenTest, line 22
command "aTest1" // library marker replica.samsungOvenTest, line 23
command "aTest2" // library marker replica.samsungOvenTest, line 24
command "aTest3" // library marker replica.samsungOvenTest, line 25
command "aTest4" // library marker replica.samsungOvenTest, line 26
command "aTest5" // library marker replica.samsungOvenTest, line 27
command "aTest6" // library marker replica.samsungOvenTest, line 28
command "aTest7" // library marker replica.samsungOvenTest, line 29
command "aTest8" // library marker replica.samsungOvenTest, line 30
command "aTest9" // library marker replica.samsungOvenTest, line 31

def aTest1() { // library marker replica.samsungOvenTest, line 33
//	Stops oven. Test setOvenMode.  Expected result - light state changes and ovenMode changes. (25 secs) // library marker replica.samsungOvenTest, line 34
	log.trace "startTestOvenMode(1): ${getTestAttrs()}" // library marker replica.samsungOvenTest, line 35
	def halt = testStop() // library marker replica.samsungOvenTest, line 36

	def testMode = "Bake" // library marker replica.samsungOvenTest, line 38
	if (device.currentValue("ovenMode") == "Bake") { testMode = "ConvectionRoast" } // library marker replica.samsungOvenTest, line 39
	setOvenMode(testMode) // library marker replica.samsungOvenTest, line 40
	pauseExecution(2000) // library marker replica.samsungOvenTest, line 41
	deviceRefresh() // library marker replica.samsungOvenTest, line 42
	pauseExecution(5000) // library marker replica.samsungOvenTest, line 43
	log.trace "TestOvenMode(1): [mode: ${testMode}, attrs: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 44
} // library marker replica.samsungOvenTest, line 45

def aTest2() { // library marker replica.samsungOvenTest, line 47
//	Set oven setpoint.  Expected result: ovenSetpoint changes. // library marker replica.samsungOvenTest, line 48
	log.trace "startTestSetpoint(2): ${getTestAttrs()}" // library marker replica.samsungOvenTest, line 49
	def testSetpoint = device.currentValue("ovenSetpoint") // library marker replica.samsungOvenTest, line 50
	if (testSetpoint.toInteger() == 350) { testSetpoint = 400 } // library marker replica.samsungOvenTest, line 51
	setOvenSetpoint(testSetpoint) // library marker replica.samsungOvenTest, line 52
	pauseExecution(2000) // library marker replica.samsungOvenTest, line 53
	deviceRefresh() // library marker replica.samsungOvenTest, line 54
	pauseExecution(5000) // library marker replica.samsungOvenTest, line 55
	log.trace "TestSetpoint(2): [setpoint: ${testSetpoint}, attrs: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 56
} // library marker replica.samsungOvenTest, line 57

def aTest3() { // library marker replica.samsungOvenTest, line 59
//	Set operating time then START.  Expected result: operating time set to 01:01:01 and oven started. // library marker replica.samsungOvenTest, line 60
	log.warn "startTestOpTime(3): [warning: Should start the device, attrs: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 61
	def testOpTime = "01:01:01" // library marker replica.samsungOvenTest, line 62
	if (testOpTime == device.currentValue("operationTime")) { testOpTime = "02:02:02" } // library marker replica.samsungOvenTest, line 63
	start(null, testOpTime, null) // library marker replica.samsungOvenTest, line 64
	pauseExecution(2000) // library marker replica.samsungOvenTest, line 65
	deviceRefresh() // library marker replica.samsungOvenTest, line 66
	pauseExecution(5000) // library marker replica.samsungOvenTest, line 67
	log.trace "TestOpTime(3): [opTime: ${testOpTime}, attrs: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 68
} // library marker replica.samsungOvenTest, line 69

def aTest4() { // library marker replica.samsungOvenTest, line 71
//	Change Operating time while running.  Expected result: operating time changes to 00:55:55. // library marker replica.samsungOvenTest, line 72
	log.trace "startTestOpTime_2(4): ${getTestAttrs()}" // library marker replica.samsungOvenTest, line 73
	def testOpTime = "00:55:55" // library marker replica.samsungOvenTest, line 74
	start(null, testOpTime, null) // library marker replica.samsungOvenTest, line 75
	pauseExecution(2000) // library marker replica.samsungOvenTest, line 76
	deviceRefresh() // library marker replica.samsungOvenTest, line 77
	pauseExecution(5000) // library marker replica.samsungOvenTest, line 78
	log.trace "TestOpTime_2(4): [opTime: ${testOpTime}, attrs: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 79
} // library marker replica.samsungOvenTest, line 80

def aTest5() { // library marker replica.samsungOvenTest, line 82
//	Change setpoing while running.  Expected result: ovenSetpoint change to 325 // library marker replica.samsungOvenTest, line 83
	log.trace "startTestSetpoint_2(5): ${getTestAttrs()}" // library marker replica.samsungOvenTest, line 84
	def testSetpoint = 325 // library marker replica.samsungOvenTest, line 85
	setOvenSetpoint(testSetpoint) // library marker replica.samsungOvenTest, line 86
	pauseExecution(2000) // library marker replica.samsungOvenTest, line 87
	deviceRefresh() // library marker replica.samsungOvenTest, line 88
	pauseExecution(5000) // library marker replica.samsungOvenTest, line 89
	log.trace "TestSetpoint_2(5): [setpoint: ${testSetpoint}, attrs: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 90
} // library marker replica.samsungOvenTest, line 91

def aTest6() { // library marker replica.samsungOvenTest, line 93
//	Change Mode while running.  Expected result: Mode changes to AirFryer. // library marker replica.samsungOvenTest, line 94
	log.trace "startTestOvenMode_2(6): ${getTestAttrs()}" // library marker replica.samsungOvenTest, line 95
	setOvenMode("AirFryer") // library marker replica.samsungOvenTest, line 96
	pauseExecution(2000) // library marker replica.samsungOvenTest, line 97
	deviceRefresh() // library marker replica.samsungOvenTest, line 98
	pauseExecution(5000) // library marker replica.samsungOvenTest, line 99
	log.trace "TestOvenMode_2(6): [mode: AirFryer, attrs: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 100
} // library marker replica.samsungOvenTest, line 101

def aTest7() { // library marker replica.samsungOvenTest, line 103
//	Pause while running (may not work).  Expected result: operating state changes to "paused"? // library marker replica.samsungOvenTest, line 104
	log.trace "startTestPause(7): ${getTestAttrs()}" // library marker replica.samsungOvenTest, line 105
	pause() // library marker replica.samsungOvenTest, line 106
	pauseExecution(2000) // library marker replica.samsungOvenTest, line 107
	deviceRefresh() // library marker replica.samsungOvenTest, line 108
	pauseExecution(5000) // library marker replica.samsungOvenTest, line 109
	log.trace "TestPause(7): [attrs: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 110
} // library marker replica.samsungOvenTest, line 111

def aTest8() { // library marker replica.samsungOvenTest, line 113
//	Restart after pause.  Expected result (if pause worked): operating state to "running" // library marker replica.samsungOvenTest, line 114
	log.trace "startTestResume(8): ${getTestAttrs()}" // library marker replica.samsungOvenTest, line 115
//	if (device.currentValue("operatingState") == "paused") { // library marker replica.samsungOvenTest, line 116
	if (device.currentValue("operatingState") != "paused") { // library marker replica.samsungOvenTest, line 117
		log.trace "TestResume: [failed: paused didn't work, attrs: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 118
	} else { // library marker replica.samsungOvenTest, line 119
		start() // library marker replica.samsungOvenTest, line 120
		pauseExecution(2000) // library marker replica.samsungOvenTest, line 121
		deviceRefresh() // library marker replica.samsungOvenTest, line 122
		pauseExecution(5000) // library marker replica.samsungOvenTest, line 123
		log.trace "TestResume(8): [attrs: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 124
	} // library marker replica.samsungOvenTest, line 125
} // library marker replica.samsungOvenTest, line 126

def aTest9() { // library marker replica.samsungOvenTest, line 128
//	Stops the oven operation.  Expected result: oven off. // library marker replica.samsungOvenTest, line 129
	def halt = testStop() // library marker replica.samsungOvenTest, line 130
} // library marker replica.samsungOvenTest, line 131

//	Test Utilities // library marker replica.samsungOvenTest, line 133
def getTestAttrs() { // library marker replica.samsungOvenTest, line 134
	def attrs = [ // library marker replica.samsungOvenTest, line 135
		mode: device.currentValue("ovenMode"), // library marker replica.samsungOvenTest, line 136
		setpoint: device.currentValue("ovenSetpoint"), // library marker replica.samsungOvenTest, line 137
		opTime: device.currentValue("operationTime"), // library marker replica.samsungOvenTest, line 138
		opState: device.currentValue("operatingState"), // library marker replica.samsungOvenTest, line 139
		jobState: device.currentValue("ovenJobState") // library marker replica.samsungOvenTest, line 140
		] // library marker replica.samsungOvenTest, line 141
	return attrs // library marker replica.samsungOvenTest, line 142
} // library marker replica.samsungOvenTest, line 143

def testStop() { // library marker replica.samsungOvenTest, line 145
	stop() // library marker replica.samsungOvenTest, line 146
	pauseExecution(2000) // library marker replica.samsungOvenTest, line 147
	deviceRefresh() // library marker replica.samsungOvenTest, line 148
	pauseExecution(5000) // library marker replica.samsungOvenTest, line 149
	log.trace "Stop: [attrs: ${getTestAttrs()}]" // library marker replica.samsungOvenTest, line 150
	return // library marker replica.samsungOvenTest, line 151
} // library marker replica.samsungOvenTest, line 152

// ~~~~~ end include (1254) replica.samsungOvenTest ~~~~~
