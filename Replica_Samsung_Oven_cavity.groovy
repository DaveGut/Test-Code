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
def driverVer() { return "1.0" }
def appliance() { return "Samsung Oven" }

metadata {
	definition (name: "Replica ${appliance()} cavity",
				namespace: "replicaChild",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/HubiThingsReplica%20Drivers/Replica_Samsung_RangeOven_cavity.groovy"
			   ){
		capability "Refresh"
	//	ovenSetpoint
		command "setOvenSetpoint", [[name: "oven temperature", type: "NUMBER"]]
		attribute "ovenSetpoint", "number"
	//	temperatureMeasurement
		attribute "ovenTemperature", "number"	//	attr.temperature
	//	ovenMode and samsungce.ovenMode
		command "setOvenMode", [[name: "from state.supported OvenModes", type:"STRING"]]
		attribute "ovenMode", "string"
	//	ovenOperatingState & samsungce.ovenOperatingState
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
	def status = parent.sendRawCommand(component, capability, command, arguments)
	logDebug("sendRawCommand: ${status}")
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

def parseCorrect(event) { // library marker replica.samsungReplicaChildCommon, line 57
	logTrace("parseCorrect: <b>${event}</b>") // library marker replica.samsungReplicaChildCommon, line 58
	if (device.currentValue(event.attribute).toString() != event.value.toString()) { // library marker replica.samsungReplicaChildCommon, line 59
		sendEvent(name: event.attribute, value: event.value, unit: event.unit) // library marker replica.samsungReplicaChildCommon, line 60
		logInfo("parseCorrect: [event: ${event}]") // library marker replica.samsungReplicaChildCommon, line 61
	} // library marker replica.samsungReplicaChildCommon, line 62
} // library marker replica.samsungReplicaChildCommon, line 63

def setState(event) { // library marker replica.samsungReplicaChildCommon, line 65
	def attribute = event.attribute // library marker replica.samsungReplicaChildCommon, line 66
	if (state."${attribute}" != event.value) { // library marker replica.samsungReplicaChildCommon, line 67
		state."${event.attribute}" = event.value // library marker replica.samsungReplicaChildCommon, line 68
		logInfo("setState: [event: ${event}]") // library marker replica.samsungReplicaChildCommon, line 69
	} // library marker replica.samsungReplicaChildCommon, line 70
} // library marker replica.samsungReplicaChildCommon, line 71

//	===== Device Commands ===== // library marker replica.samsungReplicaChildCommon, line 73
def refresh() { parent.refresh() } // library marker replica.samsungReplicaChildCommon, line 74

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
