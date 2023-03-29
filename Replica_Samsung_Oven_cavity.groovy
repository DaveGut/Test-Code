/*	HubiThings Replica RangeOven cavity Driver
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
	definition (name: "Replica ${appliance()} cavity",
				namespace: "replicaChild",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/HubiThingsReplica%20Drivers/Replica_Samsung_RangeOven_cavity.groovy"
			   ){
		capability "Refresh"
		attribute "ovenCavityStatus", "string"
	}
	preferences {
		input ("logEnable", "bool",  title: "Enable debug logging for 30 minutes", defaultValue: false)
		input ("infoLog", "bool", title: "Enable information logging",defaultValue: true)
		input ("traceLog", "bool", title: "Enable trace logging as directed by developer", defaultValue: false)
	}
}

//	===== Installation, setup and update =====
def installed() {
	runIn(1, updated)
}

def updated() {
	unschedule()
	def updStatus = [:]
	if (!getDataValue("driverVersion") || getDataValue("driverVersion") != driverVer()) {
		updateDataValue("driverVersion", driverVer())
		updStatus << [driverVer: driverVer()]
	}
	if (logEnable) { runIn(1800, debugLogOff) }
	if (traceLog) { runIn(600, traceLogOff) }
	updStatus << [logEnable: logEnable, infoLog: infoLog, traceLog: traceLog]
	listAttributes(true)
	logInfo("updated: ${updStatus}")
}

//	===== Event Parse Interface s=====
def designCapabilities() {
	return ["ovenSetpoint", "ovenMode", "ovenOperatingState", "temperatureMeasurement",
			"samsungce.ovenMode", "samsungce.ovenOperatingState", "custom.ovenCavityStatus"]
}

def sendRawCommand(component, capability, command, arguments = []) {
	Map status = [:]
	def cavityInst = device.currentValue("ovenCavityStatus") == "on"
	if (cavityInst == "on") {
		status << parent.sendRawCommand(component, capability, command, arguments)
	} else {
		status << [FAILED: [cavityInst: cavityInst]]
	}
	return status
}

//	===== Device Commands =====
//	Common parent/child Oven commands are in library replica.samsungReplicaOvenCommon

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

// ~~~~~ start include (1252) replica.samsungReplicaChildCommon ~~~~~
library ( // library marker replica.samsungReplicaChildCommon, line 1
	name: "samsungReplicaChildCommon", // library marker replica.samsungReplicaChildCommon, line 2
	namespace: "replica", // library marker replica.samsungReplicaChildCommon, line 3
	author: "Dave Gutheinz", // library marker replica.samsungReplicaChildCommon, line 4
	description: "Common Methods for replica Samsung Appliances children", // library marker replica.samsungReplicaChildCommon, line 5
	category: "utilities", // library marker replica.samsungReplicaChildCommon, line 6
	documentationLink: "" // library marker replica.samsungReplicaChildCommon, line 7
) // library marker replica.samsungReplicaChildCommon, line 8
//	Version 1.0 // library marker replica.samsungReplicaChildCommon, line 9
import groovy.json.JsonSlurper // library marker replica.samsungReplicaChildCommon, line 10

def checkCapabilities(components) { // library marker replica.samsungReplicaChildCommon, line 12
	def componentId = getDataValue("componentId") // library marker replica.samsungReplicaChildCommon, line 13
	def disabledCapabilities = [] // library marker replica.samsungReplicaChildCommon, line 14
	try { // library marker replica.samsungReplicaChildCommon, line 15
		disabledCapabilities << components[componentId]["custom.disabledCapabilities"].disabledCapabilities.value // library marker replica.samsungReplicaChildCommon, line 16
	} catch (e) { } // library marker replica.samsungReplicaChildCommon, line 17
	def enabledCapabilities = [] // library marker replica.samsungReplicaChildCommon, line 18
	Map description = new JsonSlurper().parseText(parent.getDataValue("description")) // library marker replica.samsungReplicaChildCommon, line 19
	def descComponent = description.components.find { it.id == componentId } // library marker replica.samsungReplicaChildCommon, line 20
	descComponent.capabilities.each { capability -> // library marker replica.samsungReplicaChildCommon, line 21
		if (designCapabilities().contains(capability.id) && // library marker replica.samsungReplicaChildCommon, line 22
			!disabledCapabilities.contains(capability.id)) { // library marker replica.samsungReplicaChildCommon, line 23
			enabledCapabilities << capability.id // library marker replica.samsungReplicaChildCommon, line 24
		} // library marker replica.samsungReplicaChildCommon, line 25
	} // library marker replica.samsungReplicaChildCommon, line 26
	state.deviceCapabilities = enabledCapabilities // library marker replica.samsungReplicaChildCommon, line 27
	runIn(1, refreshAttributes, [data: components]) // library marker replica.samsungReplicaChildCommon, line 28
	logInfo("checkCapabilities: [disabled: ${disabledCapabilities}, enabled: ${enabledCapabilities}]") // library marker replica.samsungReplicaChildCommon, line 29
} // library marker replica.samsungReplicaChildCommon, line 30

def refreshAttributes(components) { // library marker replica.samsungReplicaChildCommon, line 32
	logDebug("refreshAttributes: ${component}") // library marker replica.samsungReplicaChildCommon, line 33
	def component = components."${getDataValue("componentId")}" // library marker replica.samsungReplicaChildCommon, line 34
	component.each { capability -> // library marker replica.samsungReplicaChildCommon, line 35
		capability.value.each { attribute -> // library marker replica.samsungReplicaChildCommon, line 36
			parseEvent([capability: capability.key, // library marker replica.samsungReplicaChildCommon, line 37
						attribute: attribute.key, // library marker replica.samsungReplicaChildCommon, line 38
						value: attribute.value.value, // library marker replica.samsungReplicaChildCommon, line 39
						unit: attribute.value.unit]) // library marker replica.samsungReplicaChildCommon, line 40
			pauseExecution(100) // library marker replica.samsungReplicaChildCommon, line 41
		} // library marker replica.samsungReplicaChildCommon, line 42
	} // library marker replica.samsungReplicaChildCommon, line 43
	listAttributes(false) // library marker replica.samsungReplicaChildCommon, line 44
} // library marker replica.samsungReplicaChildCommon, line 45

void parentEvent(Map event) { // library marker replica.samsungReplicaChildCommon, line 47
	if (event.deviceEvent.componentId == getDataValue("componentId")) { // library marker replica.samsungReplicaChildCommon, line 48
		try { // library marker replica.samsungReplicaChildCommon, line 49
			parseEvent(event.deviceEvent) // library marker replica.samsungReplicaChildCommon, line 50
		} catch (err) { // library marker replica.samsungReplicaChildCommon, line 51
			logWarn("replicaEvent: [event = ${event}, error: ${err}") // library marker replica.samsungReplicaChildCommon, line 52
		} // library marker replica.samsungReplicaChildCommon, line 53
	} // library marker replica.samsungReplicaChildCommon, line 54
} // library marker replica.samsungReplicaChildCommon, line 55

//	===== Device Commands ===== // library marker replica.samsungReplicaChildCommon, line 57
def refresh() { parent.refresh() } // library marker replica.samsungReplicaChildCommon, line 58

// ~~~~~ end include (1252) replica.samsungReplicaChildCommon ~~~~~

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
