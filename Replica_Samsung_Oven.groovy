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
def driverVer() { return "1.0T4" }
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
	Map status = [:]
	def rcEnabled = device.currentValue("remoteControlEnabled")
	if (rcEnabled) {
		def deviceId = new JSONObject(getDataValue("description")).deviceId
		def cmdStatus = parent.setSmartDeviceCommand(deviceId, component, capability, command, arguments)
		def cmdData = [component, capability, command, arguments, cmdStatus]
		status << [cmdData: cmdData]
	} else {
		status << [FAILED: [rcEnabled: rcEnabled]]
	}
	return status
}

//	===== Device Commands =====
//	Common parent/child Oven commands are in library replica.samsungReplicaOvenCommon
def setProbeSetpoint(temperature) {
	temperature = temperature.toInteger()
	def isCapability =  state.deviceCapabilities.contains("samsungce.meatProbe")
	Map cmdStatus = [temperature: temperature, isCapability: isCapability]
	def probeStatus = device.currentValue("probeStatus")
	if (isCapability && probeStatus == "connected") {
		if (temperature > 0) {
			cmdStatus << sendRawCommand(getDataValue("componentId"), "samsungce.meatProbe", "setTemperatureSetpoint", [temperature])
		} else {
			cmdStatus << [FAILED: "invalidTemperature"]
		}
	} else {
		cmdStatus << [FAILED: [probeStatus: probeStatus]]
	}
	logInfo("setProbeSetpoint: ${cmdStatus}")
}

def setOvenLight(lightLevel) {
	lightLevel = state.supportedBrightnessLevel.find { it.toLowerCase() == lightLevel.toLowerCase() }
	def isCapability =  state.deviceCapabilities.contains("samsungce.lamp")
	Map cmdStatus = [lightLevel: lightLevel, isCapability: isCapability]
	if (lightLevel != null && isCapability) {
		cmdStatus << sendRawCommand(getDataValue("componentId"), "samsungce.lamp", "setBrightnessLevel", [lightLevel])
	} else {
		cmdStatus << [FAILED: "invalidLightLevel"]
	}
	logInfo("setOvenLight: ${cmdStatus}")
}

//	===== Libraries =====





//==========================================

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
command "pause" // library marker replica.samsungOvenCommon, line 18
command "start", [[name: "mode", type: "STRING"], // library marker replica.samsungOvenCommon, line 19
				  [name: "time (hh:mm:ss OR secs)", type: "STRING"], // library marker replica.samsungOvenCommon, line 20
				  [name: "setpoint", type: "NUMBER"]] // library marker replica.samsungOvenCommon, line 21
attribute "completionTime", "string"	//	time string // library marker replica.samsungOvenCommon, line 22
attribute "progress", "number"			//	percent // library marker replica.samsungOvenCommon, line 23
attribute "operatingState", "string"	//	attr.machineState // library marker replica.samsungOvenCommon, line 24
attribute "ovenJobState", "string" // library marker replica.samsungOvenCommon, line 25
attribute "operationTime", "string" // library marker replica.samsungOvenCommon, line 26
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

def setState(event) { // library marker replica.samsungOvenCommon, line 105
	def attribute = event.attribute // library marker replica.samsungOvenCommon, line 106
	if (state."${attribute}" != event.value) { // library marker replica.samsungOvenCommon, line 107
		state."${event.attribute}" = event.value // library marker replica.samsungOvenCommon, line 108
		logInfo("setState: [event: ${event}]") // library marker replica.samsungOvenCommon, line 109
	} // library marker replica.samsungOvenCommon, line 110
} // library marker replica.samsungOvenCommon, line 111





def setEvent(event) { // library marker replica.samsungOvenCommon, line 117
	logTrace("<b>setEvent</b>: ${event}") // library marker replica.samsungOvenCommon, line 118






	if (device.currentValue(event.attribute).toString() != event.value.toString()) { // library marker replica.samsungOvenCommon, line 125
log.trace "<b>setEvent</b>: ${event}" // library marker replica.samsungOvenCommon, line 126
		sendEvent(name: event.attribute, value: event.value, unit: event.unit) // library marker replica.samsungOvenCommon, line 127
		logInfo("setEvent: [event: ${event}]") // library marker replica.samsungOvenCommon, line 128
	} // library marker replica.samsungOvenCommon, line 129
} // library marker replica.samsungOvenCommon, line 130





//	===== Device Commands ===== // library marker replica.samsungOvenCommon, line 136
def setOvenMode(mode) { // library marker replica.samsungOvenCommon, line 137
	def ovenMode = checkMode(mode) // library marker replica.samsungOvenCommon, line 138
	def hasAdvCap =  state.deviceCapabilities.contains("samsungce.ovenOperatingState") // library marker replica.samsungOvenCommon, line 139
	Map cmdStatus = [mode: mode, ovenMode: ovenMode, hasAdvCap: hasAdvCap] // library marker replica.samsungOvenCommon, line 140
	if (ovenMode == "notSupported") { // library marker replica.samsungOvenCommon, line 141
		cmdStatus << [FAILED: ovenMode] // library marker replica.samsungOvenCommon, line 142
	} else if (hasAdvCap) { // library marker replica.samsungOvenCommon, line 143
		cmdStatus << sendRawCommand(getDataValue("componentId"),  // library marker replica.samsungOvenCommon, line 144
									"samsungce.ovenMode", "setOvenMode", [ovenMode]) // library marker replica.samsungOvenCommon, line 145
	} else { // library marker replica.samsungOvenCommon, line 146
		cmdStatus << sendRawCommand(getDataValue("componentId"),  // library marker replica.samsungOvenCommon, line 147
									"ovenMode", "setOvenMode", [ovenMode]) // library marker replica.samsungOvenCommon, line 148
	} // library marker replica.samsungOvenCommon, line 149
	logInfo("setOvenMode: ${cmdStatus}") // library marker replica.samsungOvenCommon, line 150
} // library marker replica.samsungOvenCommon, line 151

def checkMode(mode) { // library marker replica.samsungOvenCommon, line 153
	mode = state.supportedOvenModes.find { it.toLowerCase() == mode.toLowerCase() } // library marker replica.samsungOvenCommon, line 154
	if (mode == null) { // library marker replica.samsungOvenCommon, line 155
		mode = "notSupported" // library marker replica.samsungOvenCommon, line 156
	} // library marker replica.samsungOvenCommon, line 157
	return mode // library marker replica.samsungOvenCommon, line 158
} // library marker replica.samsungOvenCommon, line 159

def setOvenSetpoint(setpoint) { // library marker replica.samsungOvenCommon, line 161
	setpoint = setpoint.toInteger() // library marker replica.samsungOvenCommon, line 162
	Map cmdStatus = [setpoint: setpoint] // library marker replica.samsungOvenCommon, line 163
	if (setpoint >= 0) { // library marker replica.samsungOvenCommon, line 164
		cmdStatus << sendRawCommand(getDataValue("componentId"), "ovenSetpoint", "setOvenSetpoint", [setpoint]) // library marker replica.samsungOvenCommon, line 165
		logInfo("setOvenSetpoint: ${setpoint}") // library marker replica.samsungOvenCommon, line 166
	} else { // library marker replica.samsungOvenCommon, line 167
		cmdStatus << [FAILED: "invalidSetpoint"] // library marker replica.samsungOvenCommon, line 168
	} // library marker replica.samsungOvenCommon, line 169
	logInfo("setOvenSetpoint: ${cmdStatus}") // library marker replica.samsungOvenCommon, line 170
} // library marker replica.samsungOvenCommon, line 171

def setOperationTime(opTime) { // library marker replica.samsungOvenCommon, line 173
	def hasAdvCap =  state.deviceCapabilities.contains("samsungce.ovenOperatingState") // library marker replica.samsungOvenCommon, line 174
	Map cmdStatus = [opTime: opTime, hasAdvCap: hasAdvCap] // library marker replica.samsungOvenCommon, line 175
	if (hasAdvCap) { // library marker replica.samsungOvenCommon, line 176
		opTime = formatTime(opTime, "hhmmss", "setOperationTime") // library marker replica.samsungOvenCommon, line 177
		cmdStatus << [formatedOpTime: opTime] // library marker replica.samsungOvenCommon, line 178
		if (opTime == "invalidEntry") { // library marker replica.samsungOvenCommon, line 179
			cmdStatus << [FAILED: opTime] // library marker replica.samsungOvenCommon, line 180
		} else { // library marker replica.samsungOvenCommon, line 181
			cmdStatus << sendRawCommand(getDataValue("componentId"),  // library marker replica.samsungOvenCommon, line 182
										"samsungce.ovenOperatingState",  // library marker replica.samsungOvenCommon, line 183
										"setOperationTime", [opTime]) // library marker replica.samsungOvenCommon, line 184
		} // library marker replica.samsungOvenCommon, line 185
	} else { // library marker replica.samsungOvenCommon, line 186
		opTime = formatTime(opTime, "seconds", "setOperationTime") // library marker replica.samsungOvenCommon, line 187
		cmdStatus << [formatedOpTime: opTime] // library marker replica.samsungOvenCommon, line 188
		if (opTime == "invalidEntry") { // library marker replica.samsungOvenCommon, line 189
			cmdStatus << [FAILED: opTime] // library marker replica.samsungOvenCommon, line 190
		} else { // library marker replica.samsungOvenCommon, line 191
//			Map opCmd = [time: opTime] // library marker replica.samsungOvenCommon, line 192
//			cmdStatus << sendRawCommand(getDataValue("componentId"),  // library marker replica.samsungOvenCommon, line 193
//										"ovenOperatingState",  // library marker replica.samsungOvenCommon, line 194
//										"start", [opCmd]) // library marker replica.samsungOvenCommon, line 195
			start(null, null, opTime) // library marker replica.samsungOvenCommon, line 196
		} // library marker replica.samsungOvenCommon, line 197
	} // library marker replica.samsungOvenCommon, line 198
	logInfo("setOperationTime: ${cmdStatus}") // library marker replica.samsungOvenCommon, line 199
} // library marker replica.samsungOvenCommon, line 200

def stop() { // library marker replica.samsungOvenCommon, line 202
	def hasAdvCap =  state.deviceCapabilities.contains("samsungce.ovenOperatingState") // library marker replica.samsungOvenCommon, line 203
	Map cmdStatus = [hasAdvCap: hasAdvCap] // library marker replica.samsungOvenCommon, line 204
	if (hasAdvCap) { // library marker replica.samsungOvenCommon, line 205
		cmdStatus << sendRawCommand(getDataValue("componentId"),  // library marker replica.samsungOvenCommon, line 206
									"samsungce.ovenOperatingState", "stop") // library marker replica.samsungOvenCommon, line 207
	} else { // library marker replica.samsungOvenCommon, line 208
		cmdStatus << sendRawCommand(getDataValue("componentId"),  // library marker replica.samsungOvenCommon, line 209
									"ovenOperatingState", "stop") // library marker replica.samsungOvenCommon, line 210
	} // library marker replica.samsungOvenCommon, line 211
	logInfo("stop: ${cmdStatus}") // library marker replica.samsungOvenCommon, line 212
} // library marker replica.samsungOvenCommon, line 213

def pause() { // library marker replica.samsungOvenCommon, line 215
	def hasAdvCap =  state.deviceCapabilities.contains("samsungce.ovenOperatingState") // library marker replica.samsungOvenCommon, line 216
	Map cmdStatus = [hasAdvCap: hasAdvCap] // library marker replica.samsungOvenCommon, line 217
	if (hasAdvCap) { // library marker replica.samsungOvenCommon, line 218
		cmdStatus << sendRawCommand(getDataValue("componentId"),  // library marker replica.samsungOvenCommon, line 219
									"samsungce.ovenOperatingState", "pause") // library marker replica.samsungOvenCommon, line 220
	} else { // library marker replica.samsungOvenCommon, line 221
		cmdStatus << [FAILED: "pause not available on device"] // library marker replica.samsungOvenCommon, line 222
	} // library marker replica.samsungOvenCommon, line 223
	logInfo("pause: ${cmdStatus}") // library marker replica.samsungOvenCommon, line 224
} // library marker replica.samsungOvenCommon, line 225

def start(mode = null, opTime = null, setpoint = null) { // library marker replica.samsungOvenCommon, line 227
	def hasAdvCap =  state.deviceCapabilities.contains("samsungce.ovenOperatingState") // library marker replica.samsungOvenCommon, line 228
	Map cmdStatus = [hasAdvCap: hasAdvCap, input:  // library marker replica.samsungOvenCommon, line 229
					 [mode: mode, opTime: opTime, setpoint: setpoint]] // library marker replica.samsungOvenCommon, line 230
	if (hasAdvCap) { // library marker replica.samsungOvenCommon, line 231
		if (mode != null) { // library marker replica.samsungOvenCommon, line 232
			setOvenMode(mode) // library marker replica.samsungOvenCommon, line 233
			pauseExecution(2000) // library marker replica.samsungOvenCommon, line 234
		} // library marker replica.samsungOvenCommon, line 235
		if (setpoint != null) { // library marker replica.samsungOvenCommon, line 236
			setOvenSetpoint(setpoint) // library marker replica.samsungOvenCommon, line 237
			pauseExecution(2000) // library marker replica.samsungOvenCommon, line 238
		} // library marker replica.samsungOvenCommon, line 239
		if (opTime != null) { // library marker replica.samsungOvenCommon, line 240
			setOperationTime(opTime) // library marker replica.samsungOvenCommon, line 241
			pauseExecution(2000) // library marker replica.samsungOvenCommon, line 242
		} // library marker replica.samsungOvenCommon, line 243
		cmdStatus << sendRawCommand(getDataValue("componentId"), // library marker replica.samsungOvenCommon, line 244
									"samsungce.ovenOperatingState", "start", []) // library marker replica.samsungOvenCommon, line 245
	} else { // library marker replica.samsungOvenCommon, line 246
		Map opCmd = [:] // library marker replica.samsungOvenCommon, line 247
		def failed = false // library marker replica.samsungOvenCommon, line 248
		if (mode != null) { // library marker replica.samsungOvenCommon, line 249
			def ovenMode = checkMode(mode) // library marker replica.samsungOvenCommon, line 250
			cmdStatus << [cmdMode: ovenMode] // library marker replica.samsungOvenCommon, line 251
			opCmd << [mode: ovenMode] // library marker replica.samsungOvenCommon, line 252
			if (ovenMode == "notSupported") { // library marker replica.samsungOvenCommon, line 253
				failed = true // library marker replica.samsungOvenCommon, line 254
			} // library marker replica.samsungOvenCommon, line 255
		} // library marker replica.samsungOvenCommon, line 256
		if (opTime != null) { // library marker replica.samsungOvenCommon, line 257
			opTime = formatTime(opTime, "seconds", "setOperationTime") // library marker replica.samsungOvenCommon, line 258
			cmdStatus << [cmdOpTime: opTime] // library marker replica.samsungOvenCommon, line 259
			opCmd << [time: opTime] // library marker replica.samsungOvenCommon, line 260
			if (opTime == "invalidEntry") { // library marker replica.samsungOvenCommon, line 261
				failed = true // library marker replica.samsungOvenCommon, line 262
			} // library marker replica.samsungOvenCommon, line 263
		} // library marker replica.samsungOvenCommon, line 264
		if (setpoint != null) { // library marker replica.samsungOvenCommon, line 265
			setpoint = setpoint.toInteger() // library marker replica.samsungOvenCommon, line 266
			cmdStatus << [cmdSetpoint: setpoint] // library marker replica.samsungOvenCommon, line 267
			opCmd << [setpoint: setpoint] // library marker replica.samsungOvenCommon, line 268
			if (setpoint < 0) { // library marker replica.samsungOvenCommon, line 269
				failed = true // library marker replica.samsungOvenCommon, line 270
			} // library marker replica.samsungOvenCommon, line 271
		} // library marker replica.samsungOvenCommon, line 272
		if (failed == false) { // library marker replica.samsungOvenCommon, line 273
			cmdStatus << sendRawCommand(getDataValue("componentId"), // library marker replica.samsungOvenCommon, line 274
										"ovenOperatingState", "start", [opCmd]) // library marker replica.samsungOvenCommon, line 275
		} else { // library marker replica.samsungOvenCommon, line 276
			cmdStatus << [FAILED: "invalidInput"] // library marker replica.samsungOvenCommon, line 277
		} // library marker replica.samsungOvenCommon, line 278
	} // library marker replica.samsungOvenCommon, line 279
	logInfo("start: ${cmdStatus}") // library marker replica.samsungOvenCommon, line 280
} // library marker replica.samsungOvenCommon, line 281

def formatTime(timeValue, desiredFormat, callMethod) { // library marker replica.samsungOvenCommon, line 283
	timeValue = timeValue.toString() // library marker replica.samsungOvenCommon, line 284
	def currentFormat = "seconds" // library marker replica.samsungOvenCommon, line 285
	if (timeValue.contains(":")) { // library marker replica.samsungOvenCommon, line 286
		currentFormat = "hhmmss" // library marker replica.samsungOvenCommon, line 287
	} // library marker replica.samsungOvenCommon, line 288
	def formatedTime // library marker replica.samsungOvenCommon, line 289
	if (currentFormat == "hhmmss") { // library marker replica.samsungOvenCommon, line 290
		formatedTime = formatHhmmss(timeValue) // library marker replica.samsungOvenCommon, line 291
		if (desiredFormat == "seconds") { // library marker replica.samsungOvenCommon, line 292
			formatedTime = convertHhMmSsToInt(formatedTime) // library marker replica.samsungOvenCommon, line 293
		} // library marker replica.samsungOvenCommon, line 294
	} else { // library marker replica.samsungOvenCommon, line 295
		formatedTime = timeValue // library marker replica.samsungOvenCommon, line 296
		if (desiredFormat == "hhmmss") { // library marker replica.samsungOvenCommon, line 297
			formatedTime = convertIntToHhMmSs(timeValue) // library marker replica.samsungOvenCommon, line 298
		} // library marker replica.samsungOvenCommon, line 299
	} // library marker replica.samsungOvenCommon, line 300
	if (formatedTime == "invalidEntry") { // library marker replica.samsungOvenCommon, line 301
		Map errorData = [callMethod: callMethod, timeValue: timeValue, // library marker replica.samsungOvenCommon, line 302
						 desiredFormat: desiredFormat] // library marker replica.samsungOvenCommon, line 303
		logWarn("formatTime: [error: ${formatedTime}, data: ${errorData}") // library marker replica.samsungOvenCommon, line 304
	} // library marker replica.samsungOvenCommon, line 305
	return formatedTime // library marker replica.samsungOvenCommon, line 306
} // library marker replica.samsungOvenCommon, line 307

def formatHhmmss(timeValue) { // library marker replica.samsungOvenCommon, line 309
	def timeArray = timeValue.split(":") // library marker replica.samsungOvenCommon, line 310
	def hours = 0 // library marker replica.samsungOvenCommon, line 311
	def minutes = 0 // library marker replica.samsungOvenCommon, line 312
	def seconds = 0 // library marker replica.samsungOvenCommon, line 313
	if (timeArray.size() != timeValue.count(":") + 1) { // library marker replica.samsungOvenCommon, line 314
		return "invalidEntry" // library marker replica.samsungOvenCommon, line 315
	} else { // library marker replica.samsungOvenCommon, line 316
		try { // library marker replica.samsungOvenCommon, line 317
			if (timeArray.size() == 3) { // library marker replica.samsungOvenCommon, line 318
				hours = timeArray[0].toInteger() // library marker replica.samsungOvenCommon, line 319
				minutes = timeArray[1].toInteger() // library marker replica.samsungOvenCommon, line 320
				seconds = timeArray[2].toInteger() // library marker replica.samsungOvenCommon, line 321
			} else if (timeArray.size() == 2) { // library marker replica.samsungOvenCommon, line 322
				minutes = timeArray[0].toInteger() // library marker replica.samsungOvenCommon, line 323
				seconds = timeArray[1].toInteger() // library marker replica.samsungOvenCommon, line 324
			} // library marker replica.samsungOvenCommon, line 325
		} catch (error) { // library marker replica.samsungOvenCommon, line 326
			return "invalidEntry" // library marker replica.samsungOvenCommon, line 327
		} // library marker replica.samsungOvenCommon, line 328
	} // library marker replica.samsungOvenCommon, line 329
	if (hours < 10) { hours = "0${hours}" } // library marker replica.samsungOvenCommon, line 330
	if (minutes < 10) { minutes = "0${minutes}" } // library marker replica.samsungOvenCommon, line 331
	if (seconds < 10) { seconds = "0${seconds}" } // library marker replica.samsungOvenCommon, line 332
	return "${hours}:${minutes}:${seconds}" // library marker replica.samsungOvenCommon, line 333
} // library marker replica.samsungOvenCommon, line 334

def convertIntToHhMmSs(timeSeconds) { // library marker replica.samsungOvenCommon, line 336
	def hhmmss // library marker replica.samsungOvenCommon, line 337
	try { // library marker replica.samsungOvenCommon, line 338
		hhmmss = new GregorianCalendar( 0, 0, 0, 0, 0, timeSeconds.toInteger(), 0 ).time.format( 'HH:mm:ss' ) // library marker replica.samsungOvenCommon, line 339
	} catch (error) { // library marker replica.samsungOvenCommon, line 340
		hhmmss = "invalidEntry" // library marker replica.samsungOvenCommon, line 341
	} // library marker replica.samsungOvenCommon, line 342
	return hhmmss // library marker replica.samsungOvenCommon, line 343
} // library marker replica.samsungOvenCommon, line 344

def convertHhMmSsToInt(timeValue) { // library marker replica.samsungOvenCommon, line 346
	def timeArray = timeValue.split(":") // library marker replica.samsungOvenCommon, line 347
	def seconds = 0 // library marker replica.samsungOvenCommon, line 348
	if (timeArray.size() != timeValue.count(":") + 1) { // library marker replica.samsungOvenCommon, line 349
		return "invalidEntry" // library marker replica.samsungOvenCommon, line 350
	} else { // library marker replica.samsungOvenCommon, line 351
		try { // library marker replica.samsungOvenCommon, line 352
			if (timeArray.size() == 3) { // library marker replica.samsungOvenCommon, line 353
				seconds = timeArray[0].toInteger() * 3600 + // library marker replica.samsungOvenCommon, line 354
				timeArray[1].toInteger() * 60 + timeArray[2].toInteger() // library marker replica.samsungOvenCommon, line 355
			} else if (timeArray.size() == 2) { // library marker replica.samsungOvenCommon, line 356
				seconds = timeArray[0].toInteger() * 60 + timeArray[1].toInteger() // library marker replica.samsungOvenCommon, line 357
			} // library marker replica.samsungOvenCommon, line 358
		} catch (error) { // library marker replica.samsungOvenCommon, line 359
			seconds = "invalidEntry" // library marker replica.samsungOvenCommon, line 360
		} // library marker replica.samsungOvenCommon, line 361
	} // library marker replica.samsungOvenCommon, line 362
	return seconds // library marker replica.samsungOvenCommon, line 363
} // library marker replica.samsungOvenCommon, line 364

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
			def name = "${device.displayName} ${designChild.key}" // library marker replica.samsungReplicaCommon, line 100
			if (child == null) { // library marker replica.samsungReplicaCommon, line 101
				def type = "Replica ${appliance()} ${designChild.value}" // library marker replica.samsungReplicaCommon, line 102
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

//def developer() { return true } // library marker replica.samsungOvenTest, line 10
def developer() { return false } // library marker replica.samsungOvenTest, line 11
//command "aSetAttribute", [ // library marker replica.samsungOvenTest, line 12
//	[name: "attribute", type: "STRING"], // library marker replica.samsungOvenTest, line 13
//	[name: "value", type: "STRING"], // library marker replica.samsungOvenTest, line 14
//	[name: "dataType", constraints: ["int", "str"], type: "ENUM"]] // library marker replica.samsungOvenTest, line 15
def aSetAttribute(attr, value, dataType = "str") { // library marker replica.samsungOvenTest, line 16
	if (dataType == "int") { // library marker replica.samsungOvenTest, line 17
		value = value.toInteger() // library marker replica.samsungOvenTest, line 18
	} // library marker replica.samsungOvenTest, line 19
	setEvent([attribute: attr, value: value, unit: null]) // library marker replica.samsungOvenTest, line 20
} // library marker replica.samsungOvenTest, line 21

//command "aSetRunning" // library marker replica.samsungOvenTest, line 23
def aSetRunning() { // library marker replica.samsungOvenTest, line 24
	setEvents([ovenMode: "Bake", ovenSetpoint: 400, operationTime: "02:00:00", operatingState: "running"]) // library marker replica.samsungOvenTest, line 25
} // library marker replica.samsungOvenTest, line 26

//command "aSetReady" // library marker replica.samsungOvenTest, line 28
def aSetReady() { // library marker replica.samsungOvenTest, line 29
	setEvents([ovenMode: "NoOperation", ovenSetpoint: 0, operationTime: "00:00:00", operatingState: "ready"]) // library marker replica.samsungOvenTest, line 30
} // library marker replica.samsungOvenTest, line 31

//	Before starting: Oven running with setpoint NOT 366 and optime NOT 1:02:03 // library marker replica.samsungOvenTest, line 33
//	AdvcapChangeOpTime: setOvenSetpoint(366), samsungce.setOperationTime(1:02:03) // library marker replica.samsungOvenTest, line 34
//	Perfect end state: [setpoint: 366, optime: 1:02:03, operatingState: running] // library marker replica.samsungOvenTest, line 35
command "a1AdvcapChangeOpTime" // library marker replica.samsungOvenTest, line 36
def a1AdvcapChangeOpTime() { // library marker replica.samsungOvenTest, line 37
	log.info "\n\r\n\r " // library marker replica.samsungOvenTest, line 38
	log.info "===== startAdvcapChangeOpTime: ${getTestAttrs()}, =====" // library marker replica.samsungOvenTest, line 39
	if (device.currentValue("operatingState") != "running") { // library marker replica.samsungOvenTest, line 40
		log.warn "<b>Oven must be running to run this test.</b>" // library marker replica.samsungOvenTest, line 41
	} else { // library marker replica.samsungOvenTest, line 42
		setOvenSetpoint(366) // library marker replica.samsungOvenTest, line 43
		setEvents([ovenSetpoint: 366]) // library marker replica.samsungOvenTest, line 44
		runIn(10, testAdvcapOperationTime, [data: "01:02:03"]) // library marker replica.samsungOvenTest, line 45
		runIn(30, validate, [data: "AdvcapChangeOpTime"]) // library marker replica.samsungOvenTest, line 46
	} // library marker replica.samsungOvenTest, line 47
} // library marker replica.samsungOvenTest, line 48

//	Before starting: Oven running with setpoint NOT 355 and optime NOT 00:31:01 // library marker replica.samsungOvenTest, line 50
//	BasicChangeOpTime: setOvenSetpoint(355), setOperationTime(1861) // library marker replica.samsungOvenTest, line 51
//	Perfect end state: [setpoint: 333, optime: 00:31:01, operatingState: running] // library marker replica.samsungOvenTest, line 52
command "a2BasicChangeOpTime" // library marker replica.samsungOvenTest, line 53
def a2BasicChangeOpTime() { // library marker replica.samsungOvenTest, line 54
	log.info "\n\r\n\r " // library marker replica.samsungOvenTest, line 55
	log.info "===== startBasicChangeOpTime: ${getTestAttrs()}, =====" // library marker replica.samsungOvenTest, line 56
	if (device.currentValue("operatingState") != "running") { // library marker replica.samsungOvenTest, line 57
		log.warn "<b>Oven must be running to run this test.</b>" // library marker replica.samsungOvenTest, line 58
	} else { // library marker replica.samsungOvenTest, line 59
		setOvenSetpoint(355) // library marker replica.samsungOvenTest, line 60
		setEvents([ovenSetpoint: 355]) // library marker replica.samsungOvenTest, line 61
		runIn(10, testBasicOperationTime, [data: 1861]) // library marker replica.samsungOvenTest, line 62
		runIn(30, validate, [data: "BasicChangeOpTime"]) // library marker replica.samsungOvenTest, line 63
	} // library marker replica.samsungOvenTest, line 64
} // library marker replica.samsungOvenTest, line 65

//	Before starting: Oven running with at least 10 minutes remaining in cycle. // library marker replica.samsungOvenTest, line 67
//	AdvcapPauseStart: samsungce.pause(), samsungce.start(), samsungce.stop() // library marker replica.samsungOvenTest, line 68
command "a3AdvcapPauseStart" // library marker replica.samsungOvenTest, line 69
def a3AdvcapPauseStart() { // library marker replica.samsungOvenTest, line 70
	log.info "\n\r\n\r " // library marker replica.samsungOvenTest, line 71
	log.info "===== startAdvcapPause: ${getTestAttrs()} =====" // library marker replica.samsungOvenTest, line 72
	if (device.currentValue("operatingState") != "running") { // library marker replica.samsungOvenTest, line 73
		log.warn "<b>Oven must be running to run this test.</b>" // library marker replica.samsungOvenTest, line 74
	} else { // library marker replica.samsungOvenTest, line 75
		testAdvcapPause() // library marker replica.samsungOvenTest, line 76
		runIn(20, validate, [data: "AdvcapPause"]) // library marker replica.samsungOvenTest, line 77
		runIn(30, advcapStart) // library marker replica.samsungOvenTest, line 78
	} // library marker replica.samsungOvenTest, line 79
} // library marker replica.samsungOvenTest, line 80
def advcapStart() { // library marker replica.samsungOvenTest, line 81
	log.info "===== startAdvcapStart: ${getTestAttrs()} =====" // library marker replica.samsungOvenTest, line 82
	if (device.currentValue("operatingState") != "paused") { // library marker replica.samsungOvenTest, line 83
		log.warn "<b>Oven must be paused to run this test.</b>" // library marker replica.samsungOvenTest, line 84
	} else { // library marker replica.samsungOvenTest, line 85
		testAdvcapStart() // library marker replica.samsungOvenTest, line 86
		runIn(20, validate, [data: "AdvcapStart"]) // library marker replica.samsungOvenTest, line 87
		runIn(30, advcapStop) // library marker replica.samsungOvenTest, line 88
	} // library marker replica.samsungOvenTest, line 89
} // library marker replica.samsungOvenTest, line 90
def advcapStop() { // library marker replica.samsungOvenTest, line 91
	log.info "===== startAdvcapStop: ${getTestAttrs()} =====" // library marker replica.samsungOvenTest, line 92
	if (device.currentValue("operatingState") != "running") { // library marker replica.samsungOvenTest, line 93
		log.warn "<b>Oven must be running to run this test.</b>" // library marker replica.samsungOvenTest, line 94
	} else { // library marker replica.samsungOvenTest, line 95
		testAdvcapStop() // library marker replica.samsungOvenTest, line 96
		runIn(20, validate, [data: "AdvcapStop"]) // library marker replica.samsungOvenTest, line 97
	} // library marker replica.samsungOvenTest, line 98
} // library marker replica.samsungOvenTest, line 99

//	Before starting: Oven running with at least 10 minutes remaining in cycle. // library marker replica.samsungOvenTest, line 101
//	AdvcapPauseStart: samsungce.pause(), samsungce.start(), samsungce.stop() // library marker replica.samsungOvenTest, line 102
command "a4BasicPauseStart" // library marker replica.samsungOvenTest, line 103
def a4BasicPauseStart() { // library marker replica.samsungOvenTest, line 104
	log.info "\n\r\n\r " // library marker replica.samsungOvenTest, line 105
	log.info "===== startBasicPause: ${getTestAttrs()} =====" // library marker replica.samsungOvenTest, line 106
	if (device.currentValue("operatingState") != "running") { // library marker replica.samsungOvenTest, line 107
		log.warn "<b>Oven must be running to run this test.</b>" // library marker replica.samsungOvenTest, line 108
	} else { // library marker replica.samsungOvenTest, line 109
		testBasicPause() // library marker replica.samsungOvenTest, line 110
		runIn(20, validate, [data: "BasicPause"]) // library marker replica.samsungOvenTest, line 111
		runIn(30, basicStart) // library marker replica.samsungOvenTest, line 112
	} // library marker replica.samsungOvenTest, line 113
} // library marker replica.samsungOvenTest, line 114
def basicStart() { // library marker replica.samsungOvenTest, line 115
	log.info "===== startBasicStart: ${getTestAttrs()} =====" // library marker replica.samsungOvenTest, line 116
	if (device.currentValue("operatingState") != "paused") { // library marker replica.samsungOvenTest, line 117
		log.warn "<b>Oven must be paused to run this test.</b>" // library marker replica.samsungOvenTest, line 118
	} else { // library marker replica.samsungOvenTest, line 119
		testBasicStart() // library marker replica.samsungOvenTest, line 120
		runIn(20, validate, [data: "BasicStart"]) // library marker replica.samsungOvenTest, line 121
		runIn(30, basicStop) // library marker replica.samsungOvenTest, line 122
	} // library marker replica.samsungOvenTest, line 123
} // library marker replica.samsungOvenTest, line 124
def basicStop() { // library marker replica.samsungOvenTest, line 125
	log.info "===== startBasicStop: ${getTestAttrs()} =====" // library marker replica.samsungOvenTest, line 126
	if (device.currentValue("operatingState") != "running") { // library marker replica.samsungOvenTest, line 127
		log.warn "<b>Oven must be running to run this test.</b>" // library marker replica.samsungOvenTest, line 128
	} else { // library marker replica.samsungOvenTest, line 129
		testBasicStop() // library marker replica.samsungOvenTest, line 130
		runIn(20, validate, [data: "BasicStop"]) // library marker replica.samsungOvenTest, line 131
	} // library marker replica.samsungOvenTest, line 132
} // library marker replica.samsungOvenTest, line 133

//	AdvcapQuickStart: samsungce.start("Bake", "00:34:56", 388) // library marker replica.samsungOvenTest, line 135
command "b1AdvcapQuickStart" // library marker replica.samsungOvenTest, line 136
def b1AdvcapQuickStart() { // library marker replica.samsungOvenTest, line 137
	testAdvcapStop() // library marker replica.samsungOvenTest, line 138
	runIn(10, b1Step1) // library marker replica.samsungOvenTest, line 139
} // library marker replica.samsungOvenTest, line 140
def b1Step1() { // library marker replica.samsungOvenTest, line 141
	log.info "\n\r\n\r " // library marker replica.samsungOvenTest, line 142
	log.info "===== AdvcapQuickStart: ${getTestAttrs()} =====" // library marker replica.samsungOvenTest, line 143
	if (device.currentValue("operatingState") != "ready") { // library marker replica.samsungOvenTest, line 144
		log.warn "<b>Oven must be ready to run this test.</b>" // library marker replica.samsungOvenTest, line 145
	} else { // library marker replica.samsungOvenTest, line 146
		testAdvcapStart("Bake", "00:34:56", 388) // library marker replica.samsungOvenTest, line 147
		runIn(20, validate, [data: "AdvcapQuickStart"]) // library marker replica.samsungOvenTest, line 148
	} // library marker replica.samsungOvenTest, line 149
} // library marker replica.samsungOvenTest, line 150

//	AdvcapQuickStart: samsungce.start("Bake", "00:34:56", 388) // library marker replica.samsungOvenTest, line 152
command "b2BasicQuickStart" // library marker replica.samsungOvenTest, line 153
def b2BasicQuickStart() { // library marker replica.samsungOvenTest, line 154
	testBasicStop() // library marker replica.samsungOvenTest, line 155
	runIn(10, b2Step1) // library marker replica.samsungOvenTest, line 156
} // library marker replica.samsungOvenTest, line 157
def b2Step1() { // library marker replica.samsungOvenTest, line 158
	log.info "\n\r\n\r " // library marker replica.samsungOvenTest, line 159
	log.info "===== BasicQuickStart: ${getTestAttrs()} =====" // library marker replica.samsungOvenTest, line 160
	if (device.currentValue("operatingState") != "ready") { // library marker replica.samsungOvenTest, line 161
		log.warn "<b>Oven must be ready to run this test.</b>" // library marker replica.samsungOvenTest, line 162
	} else { // library marker replica.samsungOvenTest, line 163
		testBasicStart("Bake", "00:34:56", 388) // library marker replica.samsungOvenTest, line 164
		runIn(20, validate, [data: "BasicQuickStart"]) // library marker replica.samsungOvenTest, line 165
	} // library marker replica.samsungOvenTest, line 166
} // library marker replica.samsungOvenTest, line 167

def validate(test) { // library marker replica.samsungOvenTest, line 169
	log.info "===== validate ${test}: ${getTestAttrs()} =====" // library marker replica.samsungOvenTest, line 170
} // library marker replica.samsungOvenTest, line 171

//	Test Commands // library marker replica.samsungOvenTest, line 173
def testAdvcapOvenMode(mode) { // library marker replica.samsungOvenTest, line 174
	def ovenMode = checkMode(mode) // library marker replica.samsungOvenTest, line 175
	Map cmdStatus = [mode: mode, ovenMode: ovenMode, hasAdvCap: hasAdvCap] // library marker replica.samsungOvenTest, line 176
	if (ovenMode == "notSupported") { // library marker replica.samsungOvenTest, line 177
		cmdStatus << [FAILED: ovenMode] // library marker replica.samsungOvenTest, line 178
	} else { // library marker replica.samsungOvenTest, line 179
		sendRawCommand("main", "samsungce.ovenMode", "setOvenMode", [ovenMode]) // library marker replica.samsungOvenTest, line 180
		setEvents([ovenMode: ovenMode]) // library marker replica.samsungOvenTest, line 181
	} // library marker replica.samsungOvenTest, line 182
	return cmdStatus // library marker replica.samsungOvenTest, line 183
} // library marker replica.samsungOvenTest, line 184
def testBasicOvenMode(mode) { // library marker replica.samsungOvenTest, line 185
	def ovenMode = checkMode(mode) // library marker replica.samsungOvenTest, line 186
	Map cmdStatus = [mode: mode, ovenMode: ovenMode, hasAdvCap: hasAdvCap] // library marker replica.samsungOvenTest, line 187
	if (ovenMode == "notSupported") { // library marker replica.samsungOvenTest, line 188
		cmdStatus << [FAILED: ovenMode] // library marker replica.samsungOvenTest, line 189
	} else { // library marker replica.samsungOvenTest, line 190
		sendRawCommand("main", "ovenMode", "setOvenMode", [ovenMode]) // library marker replica.samsungOvenTest, line 191
		setEvents([ovenMode: ovenMode]) // library marker replica.samsungOvenTest, line 192
	} // library marker replica.samsungOvenTest, line 193
	return cmdStatus // library marker replica.samsungOvenTest, line 194
} // library marker replica.samsungOvenTest, line 195
def testAdvcapOperationTime(opTime) { // library marker replica.samsungOvenTest, line 196
	Map cmdStatus = [opTime: opTime] // library marker replica.samsungOvenTest, line 197
	opTime = formatTime(opTime, "hhmmss", "setOperationTime") // library marker replica.samsungOvenTest, line 198
	cmdStatus << [formatedOpTime: opTime] // library marker replica.samsungOvenTest, line 199
	if (opTime == "invalidEntry") { // library marker replica.samsungOvenTest, line 200
		cmdStatus << [FAILED: opTime] // library marker replica.samsungOvenTest, line 201
	} else { // library marker replica.samsungOvenTest, line 202
		sendRawCommand("main", "samsungce.ovenOperatingState", // library marker replica.samsungOvenTest, line 203
					   "setOperationTime", [opTime]) // library marker replica.samsungOvenTest, line 204
		setEvents([operationTime: opTime]) // library marker replica.samsungOvenTest, line 205
	} // library marker replica.samsungOvenTest, line 206
	return cmdStatus // library marker replica.samsungOvenTest, line 207
} // library marker replica.samsungOvenTest, line 208
def testBasicOperationTime(opTime) { // library marker replica.samsungOvenTest, line 209
	Map cmdStatus = [opTime: opTime] // library marker replica.samsungOvenTest, line 210
	opTime = formatTime(opTime, "seconds", "setOperationTime") // library marker replica.samsungOvenTest, line 211
	cmdStatus << [formatedOpTime: opTime] // library marker replica.samsungOvenTest, line 212
	if (opTime == "invalidEntry") { // library marker replica.samsungOvenTest, line 213
		cmdStatus << [FAILED: opTime] // library marker replica.samsungOvenTest, line 214
	} else { // library marker replica.samsungOvenTest, line 215
//		sendRawCommand("main", "ovenOperatingState", // library marker replica.samsungOvenTest, line 216
//					   "start", [time: opTime]) // library marker replica.samsungOvenTest, line 217
		testBasicStart(null, opTime, null) // library marker replica.samsungOvenTest, line 218
//		setEvents([operationTime: opTime, operatingState: "running"]) // library marker replica.samsungOvenTest, line 219
	} // library marker replica.samsungOvenTest, line 220
	return cmdStatus // library marker replica.samsungOvenTest, line 221
} // library marker replica.samsungOvenTest, line 222
def testAdvcapStop() { // library marker replica.samsungOvenTest, line 223
	sendRawCommand("main", "samsungce.ovenOperatingState", "stop") // library marker replica.samsungOvenTest, line 224
	setEvents([ovenMode: "NoOperation", ovenSetpoint: 0, operationTime: "00:00:00", operatingState: "ready"]) // library marker replica.samsungOvenTest, line 225
} // library marker replica.samsungOvenTest, line 226
def testBasicStop() { // library marker replica.samsungOvenTest, line 227
	sendRawCommand("main", "ovenOperatingState", "stop") // library marker replica.samsungOvenTest, line 228
	setEvents([ovenMode: "NoOperation", ovenSetpoint: 0, operationTime: "00:00:00", operatingState: "ready"]) // library marker replica.samsungOvenTest, line 229
} // library marker replica.samsungOvenTest, line 230
def testAdvcapPause() { // library marker replica.samsungOvenTest, line 231
	sendRawCommand("main", "samsungce.ovenOperatingState", "pause") // library marker replica.samsungOvenTest, line 232
	setEvents([operatingState: "paused"]) // library marker replica.samsungOvenTest, line 233
} // library marker replica.samsungOvenTest, line 234
def testBasicPause() { // library marker replica.samsungOvenTest, line 235
	sendRawCommand("main", "ovenOperatingState", "setMachineState", ["paused"]) // library marker replica.samsungOvenTest, line 236
	setEvents([operatingState: "paused"]) // library marker replica.samsungOvenTest, line 237
} // library marker replica.samsungOvenTest, line 238
def testAdvcapStart(mode = null, opTime = null, setpoint = null) { // library marker replica.samsungOvenTest, line 239
	Map cmdStatus = [hasAdvCap: hasAdvCap, input:  // library marker replica.samsungOvenTest, line 240
					 [mode: mode, opTime: opTime, setpoint: setpoint]] // library marker replica.samsungOvenTest, line 241
	if (mode != null) { // library marker replica.samsungOvenTest, line 242
		testAdvcapOvenMode(mode) // library marker replica.samsungOvenTest, line 243
		pauseExecution(2000) // library marker replica.samsungOvenTest, line 244
	} // library marker replica.samsungOvenTest, line 245
	if (setpoint != null) { // library marker replica.samsungOvenTest, line 246
		setOvenSetpoint(setpoint) // library marker replica.samsungOvenTest, line 247
		setEvents([ovenSetpoint: setpoint]) // library marker replica.samsungOvenTest, line 248
		pauseExecution(2000) // library marker replica.samsungOvenTest, line 249
	} // library marker replica.samsungOvenTest, line 250
	if (opTime != null) { // library marker replica.samsungOvenTest, line 251
		testAdvcapOperationTime(opTime) // library marker replica.samsungOvenTest, line 252
		pauseExecution(2000) // library marker replica.samsungOvenTest, line 253
	} // library marker replica.samsungOvenTest, line 254
	sendRawCommand("main", "samsungce.ovenOperatingState", "start", []) // library marker replica.samsungOvenTest, line 255
	setEvents([operatingState: "running"]) // library marker replica.samsungOvenTest, line 256
	return cmdStatus // library marker replica.samsungOvenTest, line 257
} // library marker replica.samsungOvenTest, line 258
def testBasicStart(mode = null, opTime = null, setpoint = null) { // library marker replica.samsungOvenTest, line 259
	Map cmdStatus = [hasAdvCap: hasAdvCap, input:  // library marker replica.samsungOvenTest, line 260
					 [mode: mode, opTime: opTime, setpoint: setpoint]] // library marker replica.samsungOvenTest, line 261
	Map opCmd = [:] // library marker replica.samsungOvenTest, line 262
	def failed = false // library marker replica.samsungOvenTest, line 263
	if (mode != null) { // library marker replica.samsungOvenTest, line 264
		def ovenMode = checkMode(mode) // library marker replica.samsungOvenTest, line 265
		cmdStatus << [cmdMode: ovenMode] // library marker replica.samsungOvenTest, line 266
		opCmd << [mode: ovenMode] // library marker replica.samsungOvenTest, line 267
		if (ovenMode == "notSupported") { // library marker replica.samsungOvenTest, line 268
			failed = true // library marker replica.samsungOvenTest, line 269
		} // library marker replica.samsungOvenTest, line 270
	} // library marker replica.samsungOvenTest, line 271
	if (opTime != null) { // library marker replica.samsungOvenTest, line 272
		opTime = formatTime(opTime, "seconds", "setOperationTime").toInteger() // library marker replica.samsungOvenTest, line 273
		cmdStatus << [cmdOpTime: opTime] // library marker replica.samsungOvenTest, line 274
		opCmd << [time: opTime] // library marker replica.samsungOvenTest, line 275
		if (opTime == "invalidEntry") { // library marker replica.samsungOvenTest, line 276
			failed = true // library marker replica.samsungOvenTest, line 277
		} // library marker replica.samsungOvenTest, line 278
	} // library marker replica.samsungOvenTest, line 279
	if (setpoint != null) { // library marker replica.samsungOvenTest, line 280
		setpoint = setpoint.toInteger() // library marker replica.samsungOvenTest, line 281
		cmdStatus << [cmdSetpoint: setpoint] // library marker replica.samsungOvenTest, line 282
		opCmd << [setpoint: setpoint] // library marker replica.samsungOvenTest, line 283
		if (setpoint < 0) { // library marker replica.samsungOvenTest, line 284
			failed = true // library marker replica.samsungOvenTest, line 285
		} // library marker replica.samsungOvenTest, line 286
	} // library marker replica.samsungOvenTest, line 287
	if (failed == false) { // library marker replica.samsungOvenTest, line 288
		cmdStatus << sendRawCommand(getDataValue("componentId"), // library marker replica.samsungOvenTest, line 289
									"ovenOperatingState", "start", [opCmd]) // library marker replica.samsungOvenTest, line 290
		setEvents([ovenMode: opCmd.mode, ovenSetpoint: opCmd.setpoint,  // library marker replica.samsungOvenTest, line 291
				   operationTime: opCmd.time, operatingState: "running"]) // library marker replica.samsungOvenTest, line 292
	} else { // library marker replica.samsungOvenTest, line 293
		cmdStatus << [FAILED: "invalidInput"] // library marker replica.samsungOvenTest, line 294
	} // library marker replica.samsungOvenTest, line 295
	return cmdStatus // library marker replica.samsungOvenTest, line 296
} // library marker replica.samsungOvenTest, line 297

//	Test Utilities // library marker replica.samsungOvenTest, line 299
def getTestAttrs() { // library marker replica.samsungOvenTest, line 300
	def attrs = [ // library marker replica.samsungOvenTest, line 301
		mode: device.currentValue("ovenMode"), // library marker replica.samsungOvenTest, line 302
		setpoint: device.currentValue("ovenSetpoint"), // library marker replica.samsungOvenTest, line 303
		opTime: device.currentValue("operationTime"), // library marker replica.samsungOvenTest, line 304
		opState: device.currentValue("operatingState"), // library marker replica.samsungOvenTest, line 305
		jobState: device.currentValue("ovenJobState"), // library marker replica.samsungOvenTest, line 306
		remoteControl: device.currentValue("remoteControlEnabled"), // library marker replica.samsungOvenTest, line 307
		lockState: device.currentValue("lockState"), // library marker replica.samsungOvenTest, line 308
		door: device.currentValue("doorState") // library marker replica.samsungOvenTest, line 309
		] // library marker replica.samsungOvenTest, line 310
	return attrs // library marker replica.samsungOvenTest, line 311
} // library marker replica.samsungOvenTest, line 312

def getCavityStatus() { // library marker replica.samsungOvenTest, line 314
	Map childrenStatus = [:] // library marker replica.samsungOvenTest, line 315
	getChildDevices().each { child -> // library marker replica.samsungOvenTest, line 316
		childrenStatus << ["${child}_DIVIDER": child.device.currentValue("ovenCavityStatus")] // library marker replica.samsungOvenTest, line 317
	} // library marker replica.samsungOvenTest, line 318
	return childrenStatus // library marker replica.samsungOvenTest, line 319
} // library marker replica.samsungOvenTest, line 320

def setEvents(eventData) { // library marker replica.samsungOvenTest, line 322
	if (developer() == true) { // library marker replica.samsungOvenTest, line 323
		eventData.each { // library marker replica.samsungOvenTest, line 324
			setEvent([attribute: it.key, value: it.value, unit: null]) // library marker replica.samsungOvenTest, line 325
		} // library marker replica.samsungOvenTest, line 326
	} // library marker replica.samsungOvenTest, line 327
} // library marker replica.samsungOvenTest, line 328

// ~~~~~ end include (1257) replica.samsungOvenTest ~~~~~
