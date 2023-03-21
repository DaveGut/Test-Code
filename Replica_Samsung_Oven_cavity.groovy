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
def driverVer() { return "1.0T2" }
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
	if (device.currentValue("ovenCavityStatus") == "on") {
		def status = parent.sendRawCommand(component, capability, command, arguments)
		return status
	} else {
		return "[FAILED: ovenCavityStatus is not on]"
	}
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



log.trace "<b>setEvent</b>: ${event}" // library marker replica.samsungOvenCommon, line 121



	if (device.currentValue(event.attribute).toString() != event.value.toString()) { // library marker replica.samsungOvenCommon, line 125
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




def xx_parseCorrect(event) { // library marker replica.samsungReplicaChildCommon, line 60
	logTrace("parseCorrect: <b>${event}</b>") // library marker replica.samsungReplicaChildCommon, line 61
	if (device.currentValue(event.attribute).toString() != event.value.toString()) { // library marker replica.samsungReplicaChildCommon, line 62
		sendEvent(name: event.attribute, value: event.value, unit: event.unit) // library marker replica.samsungReplicaChildCommon, line 63
		logInfo("parseCorrect: [event: ${event}]") // library marker replica.samsungReplicaChildCommon, line 64
	} // library marker replica.samsungReplicaChildCommon, line 65
} // library marker replica.samsungReplicaChildCommon, line 66




def xx_setState(event) { // library marker replica.samsungReplicaChildCommon, line 71
	def attribute = event.attribute // library marker replica.samsungReplicaChildCommon, line 72
	if (state."${attribute}" != event.value) { // library marker replica.samsungReplicaChildCommon, line 73
		state."${event.attribute}" = event.value // library marker replica.samsungReplicaChildCommon, line 74
		logInfo("setState: [event: ${event}]") // library marker replica.samsungReplicaChildCommon, line 75
	} // library marker replica.samsungReplicaChildCommon, line 76
} // library marker replica.samsungReplicaChildCommon, line 77




//	===== Device Commands ===== // library marker replica.samsungReplicaChildCommon, line 82
def refresh() { parent.refresh() } // library marker replica.samsungReplicaChildCommon, line 83

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
