/*	Kasa Device Driver Series
		Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Link to list of changes =====
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Changes.pdf
===== Link to Documentation =====
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Documentation.pdf
	Driver Change:
	A:	(Ease of MX) lib KasaCommon. Add methods nameSpace() and  prodVer(). Modify drivers to utilize.
	B:	(Cleanup) All Drivers: Remove driver version as no longer necessary.  Remove data element 
		driver version(avoid user confusion).  Update library Logging remove ref to driver version.
	C:	(Correct error) Plug-Switch: If device is HS300, use AltLan comms for get_sysinfo automatically. 
		Rename Hubitat device to plug alias on installation. Also modifies librarieskasaCommon and 
		kasaCommunications.
	D:	(Cleanup) All code: Review logging to reduce InfoLogging, assure Warnings are sufficient.
	E.	(Error) SendLanCmd with UDP no longer returns timeout response (used in message error
		handling.  Fix SendUdpCmd and parseUdp to reinstate this capability.
===================================================================================================*/
//	6.7.2 Change B.  Remove driverVer()
//def driverVer() { return "6.7.1" }
def type() { return "Plug Switch" }
//def type() { return "EM Plug" }
//def type() { return "Multi Plug" }
//def type() { return "EM Multi Plug" }
def file() { return type().replaceAll(" ", "-") }

metadata {
	definition (name: "Kasa ${type()}",
//	6.7.2 Change A
				namespace: nameSpace(),
//				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/DeviceDrivers/${file()}.groovy"
			   ) {
		capability "Switch"
		capability "Actuator"
		capability "Configuration"
		capability "Refresh"
		command "ledOn"
		command "ledOff"
		attribute "led", "string"
		command "setPollInterval", [[
			name: "Poll Interval in seconds",
			constraints: ["default", "5 seconds", "10 seconds", "15 seconds",
						  "30 seconds", "1 minute", "5 minutes",  "10 minutes",
						  "30 minutes"],
			type: "ENUM"]]
		capability "Power Meter"
		capability "Energy Meter"
		attribute "currMonthTotal", "number"
		attribute "currMonthAvg", "number"
		attribute "lastMonthTotal", "number"
		attribute "lastMonthAvg", "number"
		attribute "connection", "string"
		attribute "commsError", "string"
	}
	preferences {
		input ("infoLog", "bool", 
			   title: "Enable information logging " + helpLogo(),
			   defaultValue: true)
		input ("debug", "bool",
			   title: "30 minutes of debug logging", 
			   defaultValue: false)
		if (getDataValue("feature") == "TIM:ENE") {
			input ("emFunction", "bool", 
				   title: "Enable Energy Monitor", 
				   defaultValue: false)
		}
		if (getDataValue("deviceIP") != "CLOUD" && getDataValue("model") == "HS200") {
			input ("altLan", "bool",
			   	title: "Alternate LAN Comms (for comms problems only)",
			   	defaultValue: false)
		}
		input ("bind", "bool",
			   title: "Kasa Cloud Binding",
			   defalutValue: true)
		input ("useCloud", "bool",
		 	  title: "Use Kasa Cloud for device control",
		 	  defaultValue: false)
//	6.7.2 Change C: set nameSync to default "device" to force update on install.
		input ("nameSync", "enum", title: "Synchronize Names",
			   options: ["none": "Don't synchronize",
						 "device" : "Kasa device name master", 
						 "Hubitat" : "Hubitat label master"],
			   defaultValue: "none")
		input ("rebootDev", "bool",
			   title: "Reboot device <b>[Caution]</b>",
			   defaultValue: false)
	}
}

def installed() {
	if (getDataValue("altComms") == "true") {
		device.updateSetting("nameSync",[type:"enum", value:"device"])
	}
	def instStatus = installCommon()
	logInfo("installed: ${instStatus}")
}

def updated() {
	def updStatus = updateCommon()
	if (getDataValue("feature") == "TIM:ENE") {
		updStatus << [emFunction: setupEmFunction()]
	}
	logInfo("updated: ${updStatus}")
	
	refresh()
}

def setSysInfo(status) {
//	runIn(3, getPower)	// DUP
	def switchStatus = status.relay_state
	def ledStatus = status.led_off
//	6.7.2 Change C.  Cloud and altLan use the same child id.  Lan uses plugNo.
	if (getDataValue("plugNo") != null) {
//		if (device.currentValue("connection") != "CLOUD") {
//			status = status.children.find { it.id == getDataValue("plugNo") }
//		} else {
//			status = status.children.find { it.id == getDataValue("plugId") }
//		}
		def childStatus = status.children.find { it.id == getDataValue("plugNo") }
		if (childStatus == null) {
			childStatus = status.children.find { it.id == getDataValue("plugId") }
		}
		status = childStatus
		switchStatus = status.state
	}
	def onOff = "on"
	if (switchStatus == 0) { onOff = "off" }
	sendEvent(name: "switch", value: onOff, type: "digital")	//	5.2.3
	def ledOnOff = "on"
	if (ledStatus == 1) { ledOnOff = "off" }
	sendEvent(name: "led", value: ledOnOff)
	logDebug("setSysinfo: [switch: ${switchStatus}, ledStatus: ${ledStatus}]")
	
	if (nameSync == "device" || nameSync == "Hubitat") {
		updateName(status)
	}
	runIn(2, getPower)
}

def coordUpdate(cType, coordData) {
	def msg = "coordinateUpdate: "
	if (cType == "commsData") {
		device.updateSetting("bind", [type:"bool", value: coordData.bind])
		device.updateSetting("useCloud", [type:"bool", value: coordData.useCloud])
		sendEvent(name: "connection", value: coordData.connection)
		device.updateSetting("altLan", [type:"bool", value: coordData.altLan])
		msg += "[commsData: ${coordData}]"
	} else {
		msg += "Not updated."
	}
	logInfo(msg)
}







// ~~~~~ start include (1147) davegut.kasaCommon ~~~~~
library ( // library marker davegut.kasaCommon, line 1
	name: "kasaCommon", // library marker davegut.kasaCommon, line 2
	namespace: "davegut", // library marker davegut.kasaCommon, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaCommon, line 4
	description: "Kasa Device Common Methods", // library marker davegut.kasaCommon, line 5
	category: "utilities", // library marker davegut.kasaCommon, line 6
	documentationLink: "" // library marker davegut.kasaCommon, line 7
) // library marker davegut.kasaCommon, line 8

//	6.7.2 Change A:	Add methods nameSpace() // library marker davegut.kasaCommon, line 10
def nameSpace() { return "davegut" } // library marker davegut.kasaCommon, line 11

String helpLogo() { // library marker davegut.kasaCommon, line 13
	return """<a href="https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/README.md">""" + // library marker davegut.kasaCommon, line 14
		"""<div style="position: absolute; top: 10px; right: 10px; height: 80px; font-size: 20px;">Kasa Help</div></a>""" // library marker davegut.kasaCommon, line 15
} // library marker davegut.kasaCommon, line 16

def installCommon() { // library marker davegut.kasaCommon, line 18
	pauseExecution(3000) // library marker davegut.kasaCommon, line 19
	def instStatus = [:] // library marker davegut.kasaCommon, line 20
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 21
		sendEvent(name: "connection", value: "CLOUD") // library marker davegut.kasaCommon, line 22
		device.updateSetting("useCloud", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 23
		instStatus << [useCloud: true, connection: "CLOUD"] // library marker davegut.kasaCommon, line 24
	} else { // library marker davegut.kasaCommon, line 25
		sendEvent(name: "connection", value: "LAN") // library marker davegut.kasaCommon, line 26
		device.updateSetting("useCloud", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 27
		instStatus << [useCloud: false, connection: "LAN"] // library marker davegut.kasaCommon, line 28
	} // library marker davegut.kasaCommon, line 29

	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 31
	state.errorCount = 0 // library marker davegut.kasaCommon, line 32
	state.pollInterval = "30 minutes" // library marker davegut.kasaCommon, line 33
//	6.7.2 Change B.  Remove driverVer() // library marker davegut.kasaCommon, line 34
//	instStatus << [driverVersion: driverVer()] // library marker davegut.kasaCommon, line 35
	runIn(1, updated) // library marker davegut.kasaCommon, line 36
	return instStatus // library marker davegut.kasaCommon, line 37
} // library marker davegut.kasaCommon, line 38

def updateCommon() { // library marker davegut.kasaCommon, line 40
	def updStatus = [:] // library marker davegut.kasaCommon, line 41
	if (rebootDev) { // library marker davegut.kasaCommon, line 42
		updStatus << [rebootDev: rebootDevice()] // library marker davegut.kasaCommon, line 43
		return updStatus // library marker davegut.kasaCommon, line 44
	} // library marker davegut.kasaCommon, line 45
	unschedule() // library marker davegut.kasaCommon, line 46
	updStatus << [bind: bindUnbind()] // library marker davegut.kasaCommon, line 47
	if (nameSync != "none") { // library marker davegut.kasaCommon, line 48
		updStatus << [nameSync: syncName()] // library marker davegut.kasaCommon, line 49
	} // library marker davegut.kasaCommon, line 50
	if (debug) { runIn(1800, debugLogOff) } // library marker davegut.kasaCommon, line 51
	updStatus << [debug: debug] // library marker davegut.kasaCommon, line 52
	state.errorCount = 0 // library marker davegut.kasaCommon, line 53
	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 54
	def pollInterval = state.pollInterval // library marker davegut.kasaCommon, line 55
	if (pollInterval == null) { pollInterval = "30 minutes" } // library marker davegut.kasaCommon, line 56
	updStatus << [pollInterval: setPollInterval(pollInterval)] // library marker davegut.kasaCommon, line 57
//	6.7.2 Change B.  Remove driverVer() // library marker davegut.kasaCommon, line 58
//	if(getDataValue("driverVersion") != driverVer()) { // library marker davegut.kasaCommon, line 59
	state.remove("UPDATE_AVAILABLE") // library marker davegut.kasaCommon, line 60
	state.remove("releaseNotes") // library marker davegut.kasaCommon, line 61
	removeDataValue("driverVersion") // library marker davegut.kasaCommon, line 62
//		updateDataValue("driverVersion", driverVer()) // library marker davegut.kasaCommon, line 63
//		updStatus << [driverVersion: driverVer()] // library marker davegut.kasaCommon, line 64
//	} // library marker davegut.kasaCommon, line 65
	if (emFunction) { // library marker davegut.kasaCommon, line 66
		schedule("10 0 0 * * ?", getEnergyThisMonth) // library marker davegut.kasaCommon, line 67
		schedule("15 2 0 1 * ?", getEnergyLastMonth) // library marker davegut.kasaCommon, line 68
		state.getEnergy = "This Month" // library marker davegut.kasaCommon, line 69
		updStatus << [emFunction: "scheduled"] // library marker davegut.kasaCommon, line 70
	} // library marker davegut.kasaCommon, line 71
	runIn(5, listAttributes) // library marker davegut.kasaCommon, line 72
	return updStatus // library marker davegut.kasaCommon, line 73
} // library marker davegut.kasaCommon, line 74

def configure() { // library marker davegut.kasaCommon, line 76
	if (parent == null) { // library marker davegut.kasaCommon, line 77
		logWarn("configure: No Parent Detected.  Configure function ABORTED.  Use Save Preferences instead.") // library marker davegut.kasaCommon, line 78
	} else { // library marker davegut.kasaCommon, line 79
		def confStatus = parent.updateConfigurations() // library marker davegut.kasaCommon, line 80
		logInfo("configure: ${confStatus}") // library marker davegut.kasaCommon, line 81
	} // library marker davegut.kasaCommon, line 82
} // library marker davegut.kasaCommon, line 83

def refresh() { poll() } // library marker davegut.kasaCommon, line 85

def poll() { getSysinfo() } // library marker davegut.kasaCommon, line 87

def setPollInterval(interval = state.pollInterval) { // library marker davegut.kasaCommon, line 89
	if (interval == "default" || interval == "off" || interval == null) { // library marker davegut.kasaCommon, line 90
		interval = "30 minutes" // library marker davegut.kasaCommon, line 91
	} else if (useCloud && interval.contains("sec")) { // library marker davegut.kasaCommon, line 92
		interval = "1 minute" // library marker davegut.kasaCommon, line 93
	} // library marker davegut.kasaCommon, line 94
	state.pollInterval = interval // library marker davegut.kasaCommon, line 95
	def pollInterval = interval.substring(0,2).toInteger() // library marker davegut.kasaCommon, line 96
	if (interval.contains("sec")) { // library marker davegut.kasaCommon, line 97
		def start = Math.round((pollInterval-1) * Math.random()).toInteger() // library marker davegut.kasaCommon, line 98
		schedule("${start}/${pollInterval} * * * * ?", "poll") // library marker davegut.kasaCommon, line 99
		logWarn("setPollInterval: Polling intervals of less than one minute " + // library marker davegut.kasaCommon, line 100
				"can take high resources and may impact hub performance.") // library marker davegut.kasaCommon, line 101
	} else { // library marker davegut.kasaCommon, line 102
		def start = Math.round(59 * Math.random()).toInteger() // library marker davegut.kasaCommon, line 103
		schedule("${start} */${pollInterval} * * * ?", "poll") // library marker davegut.kasaCommon, line 104
	} // library marker davegut.kasaCommon, line 105
	logDebug("setPollInterval: interval = ${interval}.") // library marker davegut.kasaCommon, line 106
	return interval // library marker davegut.kasaCommon, line 107
} // library marker davegut.kasaCommon, line 108

def rebootDevice() { // library marker davegut.kasaCommon, line 110
	device.updateSetting("rebootDev", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 111
	reboot() // library marker davegut.kasaCommon, line 112
	pauseExecution(10000) // library marker davegut.kasaCommon, line 113
	return "REBOOTING DEVICE" // library marker davegut.kasaCommon, line 114
} // library marker davegut.kasaCommon, line 115

def bindUnbind() { // library marker davegut.kasaCommon, line 117
	def message // library marker davegut.kasaCommon, line 118
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 119
		device.updateSetting("bind", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 120
		device.updateSetting("useCloud", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 121
		message = "No deviceIp.  Bind not modified." // library marker davegut.kasaCommon, line 122
	} else if (bind == null || // library marker davegut.kasaCommon, line 123
	    type() == "Light Strip") { // library marker davegut.kasaCommon, line 124
		message = "Getting current bind state" // library marker davegut.kasaCommon, line 125
		getBind() // library marker davegut.kasaCommon, line 126
	} else if (bind == true) { // library marker davegut.kasaCommon, line 127
		if (!parent.kasaToken || parent.userName == null || parent.userPassword == null) { // library marker davegut.kasaCommon, line 128
			message = "Username/pwd not set." // library marker davegut.kasaCommon, line 129
			getBind() // library marker davegut.kasaCommon, line 130
		} else { // library marker davegut.kasaCommon, line 131
			message = "Binding device to the Kasa Cloud." // library marker davegut.kasaCommon, line 132
			setBind(parent.userName, parent.userPassword) // library marker davegut.kasaCommon, line 133
		} // library marker davegut.kasaCommon, line 134
	} else if (bind == false) { // library marker davegut.kasaCommon, line 135
		message = "Unbinding device from the Kasa Cloud." // library marker davegut.kasaCommon, line 136
		setUnbind() // library marker davegut.kasaCommon, line 137
	} // library marker davegut.kasaCommon, line 138
	pauseExecution(5000) // library marker davegut.kasaCommon, line 139
	return message // library marker davegut.kasaCommon, line 140
} // library marker davegut.kasaCommon, line 141

def setBindUnbind(cmdResp) { // library marker davegut.kasaCommon, line 143
	def bindState = true // library marker davegut.kasaCommon, line 144
	if (cmdResp.get_info) { // library marker davegut.kasaCommon, line 145
		if (cmdResp.get_info.binded == 0) { bindState = false } // library marker davegut.kasaCommon, line 146
		logInfo("setBindUnbind: Bind status set to ${bindState}") // library marker davegut.kasaCommon, line 147
		setCommsType(bindState) // library marker davegut.kasaCommon, line 148
	} else if (cmdResp.bind.err_code == 0){ // library marker davegut.kasaCommon, line 149
		getBind() // library marker davegut.kasaCommon, line 150
	} else { // library marker davegut.kasaCommon, line 151
		logWarn("setBindUnbind: Unhandled response: ${cmdResp}") // library marker davegut.kasaCommon, line 152
	} // library marker davegut.kasaCommon, line 153
} // library marker davegut.kasaCommon, line 154

def setCommsType(bindState) { // library marker davegut.kasaCommon, line 156
	def commsType = "LAN" // library marker davegut.kasaCommon, line 157
	def cloudCtrl = false // library marker davegut.kasaCommon, line 158
	if (bindState == false && useCloud == true) { // library marker davegut.kasaCommon, line 159
		logWarn("setCommsType: Can not use cloud.  Device is not bound to Kasa cloud.") // library marker davegut.kasaCommon, line 160
	} else if (bindState == true && useCloud == true && parent.kasaToken) { // library marker davegut.kasaCommon, line 161
		commsType = "CLOUD" // library marker davegut.kasaCommon, line 162
		cloudCtrl = true // library marker davegut.kasaCommon, line 163
	} else if (altLan == true) { // library marker davegut.kasaCommon, line 164
		commsType = "AltLAN" // library marker davegut.kasaCommon, line 165
		state.response = "" // library marker davegut.kasaCommon, line 166
	} // library marker davegut.kasaCommon, line 167
	def commsSettings = [bind: bindState, useCloud: cloudCtrl, commsType: commsType] // library marker davegut.kasaCommon, line 168
	device.updateSetting("bind", [type:"bool", value: bindState]) // library marker davegut.kasaCommon, line 169
	device.updateSetting("useCloud", [type:"bool", value: cloudCtrl]) // library marker davegut.kasaCommon, line 170
	sendEvent(name: "connection", value: "${commsType}") // library marker davegut.kasaCommon, line 171
	logInfo("setCommsType: ${commsSettings}") // library marker davegut.kasaCommon, line 172
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaCommon, line 173
		def coordData = [:] // library marker davegut.kasaCommon, line 174
		coordData << [bind: bindState] // library marker davegut.kasaCommon, line 175
		coordData << [useCloud: cloudCtrl] // library marker davegut.kasaCommon, line 176
		coordData << [connection: commsType] // library marker davegut.kasaCommon, line 177
		coordData << [altLan: altLan] // library marker davegut.kasaCommon, line 178
		parent.coordinate("commsData", coordData, getDataValue("deviceId"), getDataValue("plugNo")) // library marker davegut.kasaCommon, line 179
	} // library marker davegut.kasaCommon, line 180
	pauseExecution(1000) // library marker davegut.kasaCommon, line 181
} // library marker davegut.kasaCommon, line 182

def syncName() { // library marker davegut.kasaCommon, line 184
	def message // library marker davegut.kasaCommon, line 185
	if (nameSync == "Hubitat") { // library marker davegut.kasaCommon, line 186
		message = "Hubitat Label Sync" // library marker davegut.kasaCommon, line 187
		setDeviceAlias(device.getLabel()) // library marker davegut.kasaCommon, line 188
	} else if (nameSync == "device") { // library marker davegut.kasaCommon, line 189
		message = "Device Alias Sync" // library marker davegut.kasaCommon, line 190
	} else { // library marker davegut.kasaCommon, line 191
		message = "Not Syncing" // library marker davegut.kasaCommon, line 192
	} // library marker davegut.kasaCommon, line 193
	return message // library marker davegut.kasaCommon, line 194
} // library marker davegut.kasaCommon, line 195

def updateName(response) { // library marker davegut.kasaCommon, line 197
	device.updateSetting("nameSync",[type:"enum", value:"none"]) // library marker davegut.kasaCommon, line 198
	def name = device.getLabel() // library marker davegut.kasaCommon, line 199
	if (response.alias) { // library marker davegut.kasaCommon, line 200
		name = response.alias // library marker davegut.kasaCommon, line 201
		device.setLabel(name) // library marker davegut.kasaCommon, line 202
//	6.7.2 Change C. Update the app state.devices to new alias. // library marker davegut.kasaCommon, line 203
		parent.updateAlias(device.deviceNetworkId, name) // library marker davegut.kasaCommon, line 204
	} else if (response.err_code != 0) { // library marker davegut.kasaCommon, line 205
		def msg = "updateName: Name Sync from Hubitat to Device returned an error." // library marker davegut.kasaCommon, line 206
		msg+= "\n\rNote: <b>Some devices do not support syncing name from the hub.</b>\n\r" // library marker davegut.kasaCommon, line 207
		logWarn(msg) // library marker davegut.kasaCommon, line 208
		return // library marker davegut.kasaCommon, line 209
	} // library marker davegut.kasaCommon, line 210
	logInfo("updateName: Hubitat and Kasa device name synchronized to ${name}") // library marker davegut.kasaCommon, line 211
} // library marker davegut.kasaCommon, line 212

//	6.7.2 Change 6. Added altComms method for sysinfo message. // library marker davegut.kasaCommon, line 214
def getSysinfo() { // library marker davegut.kasaCommon, line 215
//	sendCmd("""{"system":{"get_sysinfo":{}}}""") // library marker davegut.kasaCommon, line 216
	if (getDataValue("altComms") == "true") { // library marker davegut.kasaCommon, line 217
		sendTcpCmd("""{"system":{"get_sysinfo":{}}}""") // library marker davegut.kasaCommon, line 218
	} else { // library marker davegut.kasaCommon, line 219
		sendCmd("""{"system":{"get_sysinfo":{}}}""") // library marker davegut.kasaCommon, line 220
	} // library marker davegut.kasaCommon, line 221
} // library marker davegut.kasaCommon, line 222

def reboot() { // library marker davegut.kasaCommon, line 224
	def method = "system" // library marker davegut.kasaCommon, line 225
	if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 226
		method = "smartlife.iot.common.system" // library marker davegut.kasaCommon, line 227
	} // library marker davegut.kasaCommon, line 228
	sendCmd("""{"${method}":{"reboot":{"delay":1}}}""") // library marker davegut.kasaCommon, line 229
} // library marker davegut.kasaCommon, line 230

def bindService() { // library marker davegut.kasaCommon, line 232
	def service = "cnCloud" // library marker davegut.kasaCommon, line 233
	if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 234
		service = "smartlife.iot.common.cloud" // library marker davegut.kasaCommon, line 235
	} // library marker davegut.kasaCommon, line 236
	return service // library marker davegut.kasaCommon, line 237
} // library marker davegut.kasaCommon, line 238

def getBind() { // library marker davegut.kasaCommon, line 240
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 241
		logDebug("getBind: [status: notRun, reason: [deviceIP: CLOUD]]") // library marker davegut.kasaCommon, line 242
	} else { // library marker davegut.kasaCommon, line 243
		sendLanCmd("""{"${bindService()}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 244
	} // library marker davegut.kasaCommon, line 245
} // library marker davegut.kasaCommon, line 246

def setBind(userName, password) { // library marker davegut.kasaCommon, line 248
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 249
		logDebug("setBind: [status: notRun, reason: [deviceIP: CLOUD]]") // library marker davegut.kasaCommon, line 250
	} else { // library marker davegut.kasaCommon, line 251
		sendLanCmd("""{"${bindService()}":{"bind":{"username":"${userName}",""" + // library marker davegut.kasaCommon, line 252
				   """"password":"${password}"}},""" + // library marker davegut.kasaCommon, line 253
				   """"${bindService()}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 254
	} // library marker davegut.kasaCommon, line 255
} // library marker davegut.kasaCommon, line 256

def setUnbind() { // library marker davegut.kasaCommon, line 258
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 259
		logDebug("setUnbind: [status: notRun, reason: [deviceIP: CLOUD]]") // library marker davegut.kasaCommon, line 260
	} else { // library marker davegut.kasaCommon, line 261
		sendLanCmd("""{"${bindService()}":{"unbind":""},""" + // library marker davegut.kasaCommon, line 262
				   """"${bindService()}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 263
	} // library marker davegut.kasaCommon, line 264
} // library marker davegut.kasaCommon, line 265

def setDeviceAlias(newAlias) { // library marker davegut.kasaCommon, line 267
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaCommon, line 268
	sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaCommon, line 269
				""""system":{"set_dev_alias":{"alias":"${device.getLabel()}"}}}""") // library marker davegut.kasaCommon, line 270
	} else { // library marker davegut.kasaCommon, line 271
		sendCmd("""{"system":{"set_dev_alias":{"alias":"${device.getLabel()}"}}}""") // library marker davegut.kasaCommon, line 272
	} // library marker davegut.kasaCommon, line 273
} // library marker davegut.kasaCommon, line 274

// ~~~~~ end include (1147) davegut.kasaCommon ~~~~~

// ~~~~~ start include (1148) davegut.kasaCommunications ~~~~~
library ( // library marker davegut.kasaCommunications, line 1
	name: "kasaCommunications", // library marker davegut.kasaCommunications, line 2
	namespace: "davegut", // library marker davegut.kasaCommunications, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaCommunications, line 4
	description: "Kasa Communications Methods", // library marker davegut.kasaCommunications, line 5
	category: "communications", // library marker davegut.kasaCommunications, line 6
	documentationLink: "" // library marker davegut.kasaCommunications, line 7
) // library marker davegut.kasaCommunications, line 8

import groovy.json.JsonSlurper // library marker davegut.kasaCommunications, line 10

def getPort() { // library marker davegut.kasaCommunications, line 12
	def port = 9999 // library marker davegut.kasaCommunications, line 13
	if (getDataValue("devicePort")) { // library marker davegut.kasaCommunications, line 14
		port = getDataValue("devicePort") // library marker davegut.kasaCommunications, line 15
	} // library marker davegut.kasaCommunications, line 16
	return port // library marker davegut.kasaCommunications, line 17
} // library marker davegut.kasaCommunications, line 18

def sendCmd(command) { // library marker davegut.kasaCommunications, line 20
	def connection = device.currentValue("connection") // library marker davegut.kasaCommunications, line 21
	state.lastCommand = command // library marker davegut.kasaCommunications, line 22
	if (connection == "LAN") { // library marker davegut.kasaCommunications, line 23
		sendLanCmd(command) // library marker davegut.kasaCommunications, line 24
	} else if (connection == "CLOUD"){ // library marker davegut.kasaCommunications, line 25
		sendKasaCmd(command) // library marker davegut.kasaCommunications, line 26
	} else if (connection == "AltLAN") { // library marker davegut.kasaCommunications, line 27
		sendTcpCmd(command) // library marker davegut.kasaCommunications, line 28
	} else { // library marker davegut.kasaCommunications, line 29
		logWarn("sendCmd: attribute connection is not set.") // library marker davegut.kasaCommunications, line 30
	} // library marker davegut.kasaCommunications, line 31
} // library marker davegut.kasaCommunications, line 32

def sendLanCmd(command) { // library marker davegut.kasaCommunications, line 34
	logDebug("sendLanCmd: [ip: ${getDataValue("deviceIP")}, cmd: ${command}]") // library marker davegut.kasaCommunications, line 35
	runIn(10, handleCommsError) // library marker davegut.kasaCommunications, line 36
	def myHubAction = new hubitat.device.HubAction( // library marker davegut.kasaCommunications, line 37
		outputXOR(command), // library marker davegut.kasaCommunications, line 38
		hubitat.device.Protocol.LAN, // library marker davegut.kasaCommunications, line 39
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, // library marker davegut.kasaCommunications, line 40
		 destinationAddress: "${getDataValue("deviceIP")}:${getPort()}", // library marker davegut.kasaCommunications, line 41
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING, // library marker davegut.kasaCommunications, line 42
		 parseWarning: true, // library marker davegut.kasaCommunications, line 43
		 timeout: 9, // library marker davegut.kasaCommunications, line 44
		 ignoreResponse: false, // library marker davegut.kasaCommunications, line 45
		 callback: "parseUdp"]) // library marker davegut.kasaCommunications, line 46
	try { // library marker davegut.kasaCommunications, line 47
		sendHubCommand(myHubAction) // library marker davegut.kasaCommunications, line 48
	} catch (e) { // library marker davegut.kasaCommunications, line 49
		logWarn("sendLanCmd: LAN Error = ${e}.\n\rNo retry on this error.") // library marker davegut.kasaCommunications, line 50
	} // library marker davegut.kasaCommunications, line 51
} // library marker davegut.kasaCommunications, line 52

//	6.7.2 Change C: Changes to handle HS300 Message too long to parse. // library marker davegut.kasaCommunications, line 54
//	If unhandled (for any device, sets altComms true (forever) and  // library marker davegut.kasaCommunications, line 55
//	retries the command.  Code clean-up. // library marker davegut.kasaCommunications, line 56
def parseUdp(message) { // library marker davegut.kasaCommunications, line 57
	def resp = parseLanMessage(message) // library marker davegut.kasaCommunications, line 58
	if (resp.type == "LAN_TYPE_UDPCLIENT") { // library marker davegut.kasaCommunications, line 59
		def clearResp = inputXOR(resp.payload) // library marker davegut.kasaCommunications, line 60
		if (clearResp.length() > 1022) { // library marker davegut.kasaCommunications, line 61
			if (clearResp.contains("preferred")) { // library marker davegut.kasaCommunications, line 62
				clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}" // library marker davegut.kasaCommunications, line 63
			} else { // library marker davegut.kasaCommunications, line 64
				logWarn("parseUdp: [error: msg too long, data: ${clearResp}]") // library marker davegut.kasaCommunications, line 65
				updateDataValue("altComms", "true") // library marker davegut.kasaCommunications, line 66
				unschedule("handleCommsError") // library marker davegut.kasaCommunications, line 67
				sendTcpCmd(state.lastCommand) // library marker davegut.kasaCommunications, line 68
				return // library marker davegut.kasaCommunications, line 69
			} // library marker davegut.kasaCommunications, line 70
		} // library marker davegut.kasaCommunications, line 71
		def cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.kasaCommunications, line 72
		logDebug("parseUdp: ${cmdResp}") // library marker davegut.kasaCommunications, line 73
		distResp(cmdResp) // library marker davegut.kasaCommunications, line 74
		state.lastCommand = "" // library marker davegut.kasaCommunications, line 75
		resetCommsError() // library marker davegut.kasaCommunications, line 76
	} else { // library marker davegut.kasaCommunications, line 77
		logDebug("parseUdp: [error: error, reason: not LAN_TYPE_UDPCLIENT, respType: ${resp.type}]") // library marker davegut.kasaCommunications, line 78
		handleCommsError() // library marker davegut.kasaCommunications, line 79
	} // library marker davegut.kasaCommunications, line 80
} // library marker davegut.kasaCommunications, line 81

def sendKasaCmd(command) { // library marker davegut.kasaCommunications, line 83
	logDebug("sendKasaCmd: ${command}") // library marker davegut.kasaCommunications, line 84
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 85
	def cmdBody = [ // library marker davegut.kasaCommunications, line 86
		method: "passthrough", // library marker davegut.kasaCommunications, line 87
		params: [ // library marker davegut.kasaCommunications, line 88
			deviceId: getDataValue("deviceId"), // library marker davegut.kasaCommunications, line 89
			requestData: "${command}" // library marker davegut.kasaCommunications, line 90
		] // library marker davegut.kasaCommunications, line 91
	] // library marker davegut.kasaCommunications, line 92
	if (!parent.kasaCloudUrl || !parent.kasaToken) { // library marker davegut.kasaCommunications, line 93
		logWarn("sendKasaCmd: Cloud interface not properly set up.") // library marker davegut.kasaCommunications, line 94
		return // library marker davegut.kasaCommunications, line 95
	} // library marker davegut.kasaCommunications, line 96
	def sendCloudCmdParams = [ // library marker davegut.kasaCommunications, line 97
		uri: "${parent.kasaCloudUrl}/?token=${parent.kasaToken}", // library marker davegut.kasaCommunications, line 98
		requestContentType: 'application/json', // library marker davegut.kasaCommunications, line 99
		contentType: 'application/json', // library marker davegut.kasaCommunications, line 100
		headers: ['Accept':'application/json; version=1, */*; q=0.01'], // library marker davegut.kasaCommunications, line 101
		timeout: 10, // library marker davegut.kasaCommunications, line 102
		body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.kasaCommunications, line 103
	] // library marker davegut.kasaCommunications, line 104
	try { // library marker davegut.kasaCommunications, line 105
		asynchttpPost("cloudParse", sendCloudCmdParams) // library marker davegut.kasaCommunications, line 106
	} catch (e) { // library marker davegut.kasaCommunications, line 107
		def msg = "sendKasaCmd: <b>Error in Cloud Communications.</b> The Kasa Cloud is unreachable." // library marker davegut.kasaCommunications, line 108
		msg += "\nAdditional Data: Error = ${e}\n\n" // library marker davegut.kasaCommunications, line 109
		logWarn(msg) // library marker davegut.kasaCommunications, line 110
	} // library marker davegut.kasaCommunications, line 111
} // library marker davegut.kasaCommunications, line 112

def cloudParse(resp, data = null) { // library marker davegut.kasaCommunications, line 114
	try { // library marker davegut.kasaCommunications, line 115
		response = new JsonSlurper().parseText(resp.data) // library marker davegut.kasaCommunications, line 116
	} catch (e) { // library marker davegut.kasaCommunications, line 117
		response = [error_code: 9999, data: e] // library marker davegut.kasaCommunications, line 118
	} // library marker davegut.kasaCommunications, line 119
	if (resp.status == 200 && response.error_code == 0 && resp != []) { // library marker davegut.kasaCommunications, line 120
		def cmdResp = new JsonSlurper().parseText(response.result.responseData) // library marker davegut.kasaCommunications, line 121
		logDebug("cloudParse: ${cmdResp}") // library marker davegut.kasaCommunications, line 122
		distResp(cmdResp) // library marker davegut.kasaCommunications, line 123
		state.lastCommand = "" // library marker davegut.kasaCommunications, line 124
		resetCommsError() // library marker davegut.kasaCommunications, line 125
	} else { // library marker davegut.kasaCommunications, line 126
		def msg = "cloudParse:\n<b>Error from the Kasa Cloud.</b> Most common cause is " // library marker davegut.kasaCommunications, line 127
		msg += "your Kasa Token has expired.  Run Kasa Login and Token update and try again." // library marker davegut.kasaCommunications, line 128
		msg += "\nAdditional Data: Error = ${resp.data}\n\n" // library marker davegut.kasaCommunications, line 129
		logDebug(msg) // library marker davegut.kasaCommunications, line 130
		handleCommsError() // library marker davegut.kasaCommunications, line 131
	} // library marker davegut.kasaCommunications, line 132
} // library marker davegut.kasaCommunications, line 133

def sendTcpCmd(command) { // library marker davegut.kasaCommunications, line 135
	logDebug("sendTcpCmd: ${command}") // library marker davegut.kasaCommunications, line 136
	try { // library marker davegut.kasaCommunications, line 137
		interfaces.rawSocket.connect("${getDataValue("deviceIP")}", // library marker davegut.kasaCommunications, line 138
									 getPort().toInteger(), byteInterface: true) // library marker davegut.kasaCommunications, line 139
	} catch (error) { // library marker davegut.kasaCommunications, line 140
		logDebug("SendTcpCmd: Unable to connect to device at ${getDataValue("deviceIP")}:${getDataValue("devicePort")}. " + // library marker davegut.kasaCommunications, line 141
				 "Error = ${error}") // library marker davegut.kasaCommunications, line 142
	} // library marker davegut.kasaCommunications, line 143
	state.response = "" // library marker davegut.kasaCommunications, line 144
	interfaces.rawSocket.sendMessage(outputXorTcp(command)) // library marker davegut.kasaCommunications, line 145
//	runIn(2, close) // library marker davegut.kasaCommunications, line 146
	runIn(30, close) // library marker davegut.kasaCommunications, line 147
} // library marker davegut.kasaCommunications, line 148

def close() { interfaces.rawSocket.close() } // library marker davegut.kasaCommunications, line 150

def socketStatus(message) { // library marker davegut.kasaCommunications, line 152
	if (message != "receive error: Stream closed.") { // library marker davegut.kasaCommunications, line 153
		logDebug("socketStatus: Socket Established") // library marker davegut.kasaCommunications, line 154
	} else { // library marker davegut.kasaCommunications, line 155
		logWarn("socketStatus = ${message}") // library marker davegut.kasaCommunications, line 156
	} // library marker davegut.kasaCommunications, line 157
} // library marker davegut.kasaCommunications, line 158

def parse(message) { // library marker davegut.kasaCommunications, line 160
	def response = state.response.concat(message) // library marker davegut.kasaCommunications, line 161
	state.response = response // library marker davegut.kasaCommunications, line 162
	runInMillis(50, extractTcpResp, [data: response]) // library marker davegut.kasaCommunications, line 163
} // library marker davegut.kasaCommunications, line 164

def extractTcpResp(response) { // library marker davegut.kasaCommunications, line 166
	if (response.length() == null) { // library marker davegut.kasaCommunications, line 167
		logDebug("extractTcpResp: null return rejected.") // library marker davegut.kasaCommunications, line 168
		return  // library marker davegut.kasaCommunications, line 169
	} // library marker davegut.kasaCommunications, line 170
	logDebug("extractTcpResp: ${response}") // library marker davegut.kasaCommunications, line 171
	def cmdResp // library marker davegut.kasaCommunications, line 172
	try { // library marker davegut.kasaCommunications, line 173
		cmdResp = parseJson(inputXorTcp(response)) // library marker davegut.kasaCommunications, line 174
	} catch (e) { // library marker davegut.kasaCommunications, line 175
		logWarn("extractTcpResponse: comms error = ${e}") // library marker davegut.kasaCommunications, line 176
		cmdResp = "error" // library marker davegut.kasaCommunications, line 177
		handleCommsError() // library marker davegut.kasaCommunications, line 178
	} // library marker davegut.kasaCommunications, line 179
	if (cmdResp != "error") { // library marker davegut.kasaCommunications, line 180
		logDebug("extractTcpResp: ${cmdResp}") // library marker davegut.kasaCommunications, line 181
		distResp(cmdResp) // library marker davegut.kasaCommunications, line 182
		state.lastCommand = "" // library marker davegut.kasaCommunications, line 183
		resetCommsError() // library marker davegut.kasaCommunications, line 184
	} // library marker davegut.kasaCommunications, line 185
} // library marker davegut.kasaCommunications, line 186

def handleCommsError() { // library marker davegut.kasaCommunications, line 188
	if (state.lastCommand == "") { return } // library marker davegut.kasaCommunications, line 189
	def count = state.errorCount + 1 // library marker davegut.kasaCommunications, line 190
	state.errorCount = count // library marker davegut.kasaCommunications, line 191
	def retry = true // library marker davegut.kasaCommunications, line 192
	def status = [count: count, command: state.lastCommand] // library marker davegut.kasaCommunications, line 193
	if (count == 3) { // library marker davegut.kasaCommunications, line 194
		def attemptFix = parent.fixConnection() // library marker davegut.kasaCommunications, line 195
		status << [attemptFixResult: [attemptFix]] // library marker davegut.kasaCommunications, line 196
	} else if (count >= 4) { // library marker davegut.kasaCommunications, line 197
		retry = false // library marker davegut.kasaCommunications, line 198
	} // library marker davegut.kasaCommunications, line 199
	if (retry == true) { // library marker davegut.kasaCommunications, line 200
		if (state.lastCommand != null) { sendCmd(state.lastCommand) } // library marker davegut.kasaCommunications, line 201
		if (count > 1) { // library marker davegut.kasaCommunications, line 202
			logDebug("handleCommsError: [count: ${count}]") // library marker davegut.kasaCommunications, line 203
		} // library marker davegut.kasaCommunications, line 204
	} else { // library marker davegut.kasaCommunications, line 205
		setCommsError() // library marker davegut.kasaCommunications, line 206
	} // library marker davegut.kasaCommunications, line 207
	status << [retry: retry] // library marker davegut.kasaCommunications, line 208
	if (status.count > 2) { // library marker davegut.kasaCommunications, line 209
		logWarn("handleCommsError: ${status}") // library marker davegut.kasaCommunications, line 210
	} else { // library marker davegut.kasaCommunications, line 211
		logDebug("handleCommsError: ${status}") // library marker davegut.kasaCommunications, line 212
	} // library marker davegut.kasaCommunications, line 213
} // library marker davegut.kasaCommunications, line 214

def setCommsError() { // library marker davegut.kasaCommunications, line 216
	if (device.currentValue("commsError") == "false") { // library marker davegut.kasaCommunications, line 217
		def message = "Can't connect to your device at ${getDataValue("deviceIP")}:${getPort()}. " // library marker davegut.kasaCommunications, line 218
		message += "Refer to troubleshooting guide commsError section." // library marker davegut.kasaCommunications, line 219
		sendEvent(name: "commsError", value: "true") // library marker davegut.kasaCommunications, line 220
		state.COMMS_ERROR = message			 // library marker davegut.kasaCommunications, line 221
		logWarn("setCommsError: <b>${message}</b>") // library marker davegut.kasaCommunications, line 222
		runIn(15, limitPollInterval) // library marker davegut.kasaCommunications, line 223
	} // library marker davegut.kasaCommunications, line 224
} // library marker davegut.kasaCommunications, line 225

def limitPollInterval() { // library marker davegut.kasaCommunications, line 227
	state.nonErrorPollInterval = state.pollInterval // library marker davegut.kasaCommunications, line 228
	setPollInterval("30 minutes") // library marker davegut.kasaCommunications, line 229
} // library marker davegut.kasaCommunications, line 230

def resetCommsError() { // library marker davegut.kasaCommunications, line 232
	state.errorCount = 0 // library marker davegut.kasaCommunications, line 233
	if (device.currentValue("commsError") == "true") { // library marker davegut.kasaCommunications, line 234
		sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommunications, line 235
		setPollInterval(state.nonErrorPollInterval) // library marker davegut.kasaCommunications, line 236
		state.remove("nonErrorPollInterval") // library marker davegut.kasaCommunications, line 237
		state.remove("COMMS_ERROR") // library marker davegut.kasaCommunications, line 238
		logInfo("resetCommsError: Comms error cleared!") // library marker davegut.kasaCommunications, line 239
	} // library marker davegut.kasaCommunications, line 240
} // library marker davegut.kasaCommunications, line 241

private outputXOR(command) { // library marker davegut.kasaCommunications, line 243
	def str = "" // library marker davegut.kasaCommunications, line 244
	def encrCmd = "" // library marker davegut.kasaCommunications, line 245
 	def key = 0xAB // library marker davegut.kasaCommunications, line 246
	for (int i = 0; i < command.length(); i++) { // library marker davegut.kasaCommunications, line 247
		str = (command.charAt(i) as byte) ^ key // library marker davegut.kasaCommunications, line 248
		key = str // library marker davegut.kasaCommunications, line 249
		encrCmd += Integer.toHexString(str) // library marker davegut.kasaCommunications, line 250
	} // library marker davegut.kasaCommunications, line 251
   	return encrCmd // library marker davegut.kasaCommunications, line 252
} // library marker davegut.kasaCommunications, line 253

private inputXOR(encrResponse) { // library marker davegut.kasaCommunications, line 255
	String[] strBytes = encrResponse.split("(?<=\\G.{2})") // library marker davegut.kasaCommunications, line 256
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 257
	def key = 0xAB // library marker davegut.kasaCommunications, line 258
	def nextKey // library marker davegut.kasaCommunications, line 259
	byte[] XORtemp // library marker davegut.kasaCommunications, line 260
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommunications, line 261
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.kasaCommunications, line 262
		XORtemp = nextKey ^ key // library marker davegut.kasaCommunications, line 263
		key = nextKey // library marker davegut.kasaCommunications, line 264
		cmdResponse += new String(XORtemp) // library marker davegut.kasaCommunications, line 265
	} // library marker davegut.kasaCommunications, line 266
	return cmdResponse // library marker davegut.kasaCommunications, line 267
} // library marker davegut.kasaCommunications, line 268

private outputXorTcp(command) { // library marker davegut.kasaCommunications, line 270
	def str = "" // library marker davegut.kasaCommunications, line 271
	def encrCmd = "000000" + Integer.toHexString(command.length())  // library marker davegut.kasaCommunications, line 272
 	def key = 0xAB // library marker davegut.kasaCommunications, line 273
	for (int i = 0; i < command.length(); i++) { // library marker davegut.kasaCommunications, line 274
		str = (command.charAt(i) as byte) ^ key // library marker davegut.kasaCommunications, line 275
		key = str // library marker davegut.kasaCommunications, line 276
		encrCmd += Integer.toHexString(str) // library marker davegut.kasaCommunications, line 277
	} // library marker davegut.kasaCommunications, line 278
   	return encrCmd // library marker davegut.kasaCommunications, line 279
} // library marker davegut.kasaCommunications, line 280

private inputXorTcp(resp) { // library marker davegut.kasaCommunications, line 282
	String[] strBytes = resp.substring(8).split("(?<=\\G.{2})") // library marker davegut.kasaCommunications, line 283
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 284
	def key = 0xAB // library marker davegut.kasaCommunications, line 285
	def nextKey // library marker davegut.kasaCommunications, line 286
	byte[] XORtemp // library marker davegut.kasaCommunications, line 287
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommunications, line 288
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.kasaCommunications, line 289
		XORtemp = nextKey ^ key // library marker davegut.kasaCommunications, line 290
		key = nextKey // library marker davegut.kasaCommunications, line 291
		cmdResponse += new String(XORtemp) // library marker davegut.kasaCommunications, line 292
	} // library marker davegut.kasaCommunications, line 293
	return cmdResponse // library marker davegut.kasaCommunications, line 294
} // library marker davegut.kasaCommunications, line 295

// ~~~~~ end include (1148) davegut.kasaCommunications ~~~~~

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

//	6.7.2 Change B.  Remove driverVer() // library marker davegut.Logging, line 25
def logTrace(msg){ // library marker davegut.Logging, line 26
	log.trace "${device.displayName}: ${msg}" // library marker davegut.Logging, line 27
} // library marker davegut.Logging, line 28

def logInfo(msg) {  // library marker davegut.Logging, line 30
	if (!infoLog || infoLog == true) { // library marker davegut.Logging, line 31
		log.info "${device.displayName}: ${msg}" // library marker davegut.Logging, line 32
	} // library marker davegut.Logging, line 33
} // library marker davegut.Logging, line 34

def debugLogOff() { // library marker davegut.Logging, line 36
	if (debug == true) { // library marker davegut.Logging, line 37
		device.updateSetting("debug", [type:"bool", value: false]) // library marker davegut.Logging, line 38
	} else if (debugLog == true) { // library marker davegut.Logging, line 39
		device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.Logging, line 40
	} // library marker davegut.Logging, line 41
	logInfo("Debug logging is false.") // library marker davegut.Logging, line 42
} // library marker davegut.Logging, line 43

def logDebug(msg) { // library marker davegut.Logging, line 45
	if (debug == true || debugLog == true) { // library marker davegut.Logging, line 46
		log.debug "${device.displayName}: ${msg}" // library marker davegut.Logging, line 47
	} // library marker davegut.Logging, line 48
} // library marker davegut.Logging, line 49

def logWarn(msg) { log.warn "${device.displayName}: ${msg}" } // library marker davegut.Logging, line 51

// ~~~~~ end include (1072) davegut.Logging ~~~~~

// ~~~~~ start include (1151) davegut.kasaPlugs ~~~~~
library ( // library marker davegut.kasaPlugs, line 1
	name: "kasaPlugs", // library marker davegut.kasaPlugs, line 2
	namespace: "davegut", // library marker davegut.kasaPlugs, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaPlugs, line 4
	description: "Kasa Plug and Switches Common Methods", // library marker davegut.kasaPlugs, line 5
	category: "utilities", // library marker davegut.kasaPlugs, line 6
	documentationLink: "" // library marker davegut.kasaPlugs, line 7
) // library marker davegut.kasaPlugs, line 8

def on() { setRelayState(1) } // library marker davegut.kasaPlugs, line 10

def off() { setRelayState(0) } // library marker davegut.kasaPlugs, line 12

def ledOn() { setLedOff(0) } // library marker davegut.kasaPlugs, line 14

def ledOff() { setLedOff(1) } // library marker davegut.kasaPlugs, line 16

def distResp(response) { // library marker davegut.kasaPlugs, line 18
	if (response.system) { // library marker davegut.kasaPlugs, line 19
		if (response.system.get_sysinfo) { // library marker davegut.kasaPlugs, line 20
			setSysInfo(response.system.get_sysinfo) // library marker davegut.kasaPlugs, line 21
//	6.7.2 Change 6. Use distribute response to send getSysinfo // library marker davegut.kasaPlugs, line 22
//	on error=0 response.system.set_relay_state. // library marker davegut.kasaPlugs, line 23
		} else if (response.system.set_relay_state || // library marker davegut.kasaPlugs, line 24
				   response.system.set_led_off) { // library marker davegut.kasaPlugs, line 25
			getSysinfo() // library marker davegut.kasaPlugs, line 26
		} else if (response.system.reboot) { // library marker davegut.kasaPlugs, line 27
			logWarn("distResp: Rebooting device.") // library marker davegut.kasaPlugs, line 28
		} else if (response.system.set_dev_alias) { // library marker davegut.kasaPlugs, line 29
			updateName(response.system.set_dev_alias) // library marker davegut.kasaPlugs, line 30
		} else { // library marker davegut.kasaPlugs, line 31
			logDebug("distResp: Unhandled response = ${response}") // library marker davegut.kasaPlugs, line 32
		} // library marker davegut.kasaPlugs, line 33
	} else if (response["smartlife.iot.dimmer"]) { // library marker davegut.kasaPlugs, line 34
		if (response["smartlife.iot.dimmer"].get_dimmer_parameters) { // library marker davegut.kasaPlugs, line 35
			setDimmerConfig(response["smartlife.iot.dimmer"]) // library marker davegut.kasaPlugs, line 36
		} else { // library marker davegut.kasaPlugs, line 37
			logDebug("distResp: Unhandled response: ${response["smartlife.iot.dimmer"]}") // library marker davegut.kasaPlugs, line 38
		} // library marker davegut.kasaPlugs, line 39
	} else if (response.emeter) { // library marker davegut.kasaPlugs, line 40
		distEmeter(response.emeter) // library marker davegut.kasaPlugs, line 41
	} else if (response.cnCloud) { // library marker davegut.kasaPlugs, line 42
		setBindUnbind(response.cnCloud) // library marker davegut.kasaPlugs, line 43
	} else { // library marker davegut.kasaPlugs, line 44
		logDebug("distResp: Unhandled response = ${response}") // library marker davegut.kasaPlugs, line 45
	} // library marker davegut.kasaPlugs, line 46
} // library marker davegut.kasaPlugs, line 47

//	6.7.2 Change C. Break up combined setRelayState and getSysinfo. // library marker davegut.kasaPlugs, line 49
//	Then use distribute response to send getSysinfo on valid response. // library marker davegut.kasaPlugs, line 50
def setRelayState(onOff) { // library marker davegut.kasaPlugs, line 51
	logDebug("setRelayState: [switch: ${onOff}]") // library marker davegut.kasaPlugs, line 52
	if (getDataValue("plugNo") == null) { // library marker davegut.kasaPlugs, line 53
		sendCmd("""{"system":{"set_relay_state":{"state":${onOff}}}}""") // library marker davegut.kasaPlugs, line 54
	} else { // library marker davegut.kasaPlugs, line 55
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaPlugs, line 56
				""""system":{"set_relay_state":{"state":${onOff}}}}""") // library marker davegut.kasaPlugs, line 57
	} // library marker davegut.kasaPlugs, line 58
	runInMillis(100, getSysinfo) // library marker davegut.kasaPlugs, line 59
} // library marker davegut.kasaPlugs, line 60

//	6.7.2 Change C. Split command and status request. Status request in distResp. // library marker davegut.kasaPlugs, line 62
def setLedOff(onOff) { // library marker davegut.kasaPlugs, line 63
	logDebug("setLedOff: [ledOff: ${onOff}]") // library marker davegut.kasaPlugs, line 64
		sendCmd("""{"system":{"set_led_off":{"off":${onOff}}}}""") // library marker davegut.kasaPlugs, line 65
//		sendCmd("""{"system":{"set_led_off":{"off":${onOff}},"get_sysinfo":{}}}""") // library marker davegut.kasaPlugs, line 66
} // library marker davegut.kasaPlugs, line 67

// ~~~~~ end include (1151) davegut.kasaPlugs ~~~~~

// ~~~~~ start include (1149) davegut.kasaEnergyMonitor ~~~~~
library ( // library marker davegut.kasaEnergyMonitor, line 1
	name: "kasaEnergyMonitor", // library marker davegut.kasaEnergyMonitor, line 2
	namespace: "davegut", // library marker davegut.kasaEnergyMonitor, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaEnergyMonitor, line 4
	description: "Kasa Device Energy Monitor Methods", // library marker davegut.kasaEnergyMonitor, line 5
	category: "energyMonitor", // library marker davegut.kasaEnergyMonitor, line 6
	documentationLink: "" // library marker davegut.kasaEnergyMonitor, line 7
) // library marker davegut.kasaEnergyMonitor, line 8

def setupEmFunction() { // library marker davegut.kasaEnergyMonitor, line 10
	if (emFunction && device.currentValue("currMonthTotal") > 0) { // library marker davegut.kasaEnergyMonitor, line 11
		runEvery30Minutes(getEnergyToday) // library marker davegut.kasaEnergyMonitor, line 12
		schedule("10 0 0 * * ?", getEnergyThisMonth) // library marker davegut.kasaEnergyMonitor, line 13
		schedule("15 2 0 1 * ?", getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 14
		state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 15
		return "Continuing EM Function" // library marker davegut.kasaEnergyMonitor, line 16
	} else if (emFunction) { // library marker davegut.kasaEnergyMonitor, line 17
		sendEvent(name: "power", value: 0, unit: "W") // library marker davegut.kasaEnergyMonitor, line 18
		sendEvent(name: "energy", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 19
		sendEvent(name: "currMonthTotal", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 20
		sendEvent(name: "currMonthAvg", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 21
		sendEvent(name: "lastMonthTotal", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 22
		sendEvent(name: "lastMonthAvg", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 23
		state.response = "" // library marker davegut.kasaEnergyMonitor, line 24
		runEvery30Minutes(getEnergyToday) // library marker davegut.kasaEnergyMonitor, line 25
		schedule("10 0 0 * * ?", getEnergyThisMonth) // library marker davegut.kasaEnergyMonitor, line 26
		schedule("15 2 0 1 * ?", getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 27
		state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 28
		//	Run order / delay is critical for successful operation. // library marker davegut.kasaEnergyMonitor, line 29
		getEnergyThisMonth() // library marker davegut.kasaEnergyMonitor, line 30
		runIn(10, getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 31
		return "Initialized" // library marker davegut.kasaEnergyMonitor, line 32
	} else if (emFunction && device.currentValue("power") != null) { // library marker davegut.kasaEnergyMonitor, line 33
		//	for power != null, EM had to be enabled at one time.  Set values to 0. // library marker davegut.kasaEnergyMonitor, line 34
		sendEvent(name: "power", value: 0) // library marker davegut.kasaEnergyMonitor, line 35
		sendEvent(name: "energy", value: 0) // library marker davegut.kasaEnergyMonitor, line 36
		sendEvent(name: "currMonthTotal", value: 0) // library marker davegut.kasaEnergyMonitor, line 37
		sendEvent(name: "currMonthAvg", value: 0) // library marker davegut.kasaEnergyMonitor, line 38
		sendEvent(name: "lastMonthTotal", value: 0) // library marker davegut.kasaEnergyMonitor, line 39
		sendEvent(name: "lastMonthAvg", value: 0) // library marker davegut.kasaEnergyMonitor, line 40
		state.remove("getEnergy") // library marker davegut.kasaEnergyMonitor, line 41
		return "Disabled" // library marker davegut.kasaEnergyMonitor, line 42
	} else { // library marker davegut.kasaEnergyMonitor, line 43
		return "Not initialized" // library marker davegut.kasaEnergyMonitor, line 44
	} // library marker davegut.kasaEnergyMonitor, line 45
} // library marker davegut.kasaEnergyMonitor, line 46

def getDate() { // library marker davegut.kasaEnergyMonitor, line 48
	def currDate = new Date() // library marker davegut.kasaEnergyMonitor, line 49
	int year = currDate.format("yyyy").toInteger() // library marker davegut.kasaEnergyMonitor, line 50
	int month = currDate.format("M").toInteger() // library marker davegut.kasaEnergyMonitor, line 51
	int day = currDate.format("d").toInteger() // library marker davegut.kasaEnergyMonitor, line 52
	return [year: year, month: month, day: day] // library marker davegut.kasaEnergyMonitor, line 53
} // library marker davegut.kasaEnergyMonitor, line 54

def distEmeter(emeterResp) { // library marker davegut.kasaEnergyMonitor, line 56
	def date = getDate() // library marker davegut.kasaEnergyMonitor, line 57
	logDebug("distEmeter: ${emeterResp}, ${date}, ${state.getEnergy}") // library marker davegut.kasaEnergyMonitor, line 58
	def lastYear = date.year - 1 // library marker davegut.kasaEnergyMonitor, line 59
	if (emeterResp.get_realtime) { // library marker davegut.kasaEnergyMonitor, line 60
		setPower(emeterResp.get_realtime) // library marker davegut.kasaEnergyMonitor, line 61
	} else if (emeterResp.get_monthstat) { // library marker davegut.kasaEnergyMonitor, line 62
		def monthList = emeterResp.get_monthstat.month_list // library marker davegut.kasaEnergyMonitor, line 63
		if (state.getEnergy == "Today") { // library marker davegut.kasaEnergyMonitor, line 64
			setEnergyToday(monthList, date) // library marker davegut.kasaEnergyMonitor, line 65
		} else if (state.getEnergy == "This Month") { // library marker davegut.kasaEnergyMonitor, line 66
			setThisMonth(monthList, date) // library marker davegut.kasaEnergyMonitor, line 67
		} else if (state.getEnergy == "Last Month") { // library marker davegut.kasaEnergyMonitor, line 68
			setLastMonth(monthList, date) // library marker davegut.kasaEnergyMonitor, line 69
		} else if (monthList == []) { // library marker davegut.kasaEnergyMonitor, line 70
			logDebug("distEmeter: monthList Empty. No data for year.") // library marker davegut.kasaEnergyMonitor, line 71
		} // library marker davegut.kasaEnergyMonitor, line 72
	} else { // library marker davegut.kasaEnergyMonitor, line 73
		logWarn("distEmeter: Unhandled response = ${emeterResp}") // library marker davegut.kasaEnergyMonitor, line 74
	} // library marker davegut.kasaEnergyMonitor, line 75
} // library marker davegut.kasaEnergyMonitor, line 76

def getPower() { // library marker davegut.kasaEnergyMonitor, line 78
	if (emFunction) { // library marker davegut.kasaEnergyMonitor, line 79
		if (device.currentValue("switch") == "on") { // library marker davegut.kasaEnergyMonitor, line 80
			getRealtime() // library marker davegut.kasaEnergyMonitor, line 81
		} else if (device.currentValue("power") != 0) { // library marker davegut.kasaEnergyMonitor, line 82
			sendEvent(name: "power", value: 0, descriptionText: "Watts", unit: "W", type: "digital") // library marker davegut.kasaEnergyMonitor, line 83
		} // library marker davegut.kasaEnergyMonitor, line 84
	} // library marker davegut.kasaEnergyMonitor, line 85
} // library marker davegut.kasaEnergyMonitor, line 86

//	6.7.2 Change D.  Cleanup Logging. // library marker davegut.kasaEnergyMonitor, line 88
def setPower(response) { // library marker davegut.kasaEnergyMonitor, line 89
	logDebug("setPower: ${response}") // library marker davegut.kasaEnergyMonitor, line 90
//	def status = [:] // library marker davegut.kasaEnergyMonitor, line 91
	def power = response.power // library marker davegut.kasaEnergyMonitor, line 92
	if (power == null) { power = response.power_mw / 1000 } // library marker davegut.kasaEnergyMonitor, line 93
	power = (power + 0.5).toInteger() // library marker davegut.kasaEnergyMonitor, line 94
	def curPwr = device.currentValue("power") // library marker davegut.kasaEnergyMonitor, line 95
	def pwrChange = false // library marker davegut.kasaEnergyMonitor, line 96
	if (curPwr != power) { // library marker davegut.kasaEnergyMonitor, line 97
		if (curPwr == null || (curPwr == 0 && power > 0)) { // library marker davegut.kasaEnergyMonitor, line 98
			pwrChange = true // library marker davegut.kasaEnergyMonitor, line 99
		} else { // library marker davegut.kasaEnergyMonitor, line 100
			def changeRatio = Math.abs((power - curPwr) / curPwr) // library marker davegut.kasaEnergyMonitor, line 101
			if (changeRatio > 0.03) { // library marker davegut.kasaEnergyMonitor, line 102
				pwrChange = true // library marker davegut.kasaEnergyMonitor, line 103
			} // library marker davegut.kasaEnergyMonitor, line 104
		} // library marker davegut.kasaEnergyMonitor, line 105
	} // library marker davegut.kasaEnergyMonitor, line 106
	if (pwrChange == true) { // library marker davegut.kasaEnergyMonitor, line 107
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W", type: "digital") // library marker davegut.kasaEnergyMonitor, line 108
//		status << [power: power] // library marker davegut.kasaEnergyMonitor, line 109
	} // library marker davegut.kasaEnergyMonitor, line 110
//	if (status != [:]) { logInfo("setPower: ${status}") } // library marker davegut.kasaEnergyMonitor, line 111
} // library marker davegut.kasaEnergyMonitor, line 112

def getEnergyToday() { // library marker davegut.kasaEnergyMonitor, line 114
	if (device.currentValue("switch") == "on") { // library marker davegut.kasaEnergyMonitor, line 115
		state.getEnergy = "Today" // library marker davegut.kasaEnergyMonitor, line 116
		def year = getDate().year // library marker davegut.kasaEnergyMonitor, line 117
		logDebug("getEnergyToday: ${year}") // library marker davegut.kasaEnergyMonitor, line 118
		runIn(5, getMonthstat, [data: year]) // library marker davegut.kasaEnergyMonitor, line 119
	} // library marker davegut.kasaEnergyMonitor, line 120
} // library marker davegut.kasaEnergyMonitor, line 121

def setEnergyToday(monthList, date) { // library marker davegut.kasaEnergyMonitor, line 123
	logDebug("setEnergyToday: ${date}, ${monthList}") // library marker davegut.kasaEnergyMonitor, line 124
	def data = monthList.find { it.month == date.month && it.year == date.year} // library marker davegut.kasaEnergyMonitor, line 125
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 126
	def energy = 0 // library marker davegut.kasaEnergyMonitor, line 127
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 128
		status << [msgError: "Return Data Null"] // library marker davegut.kasaEnergyMonitor, line 129
	} else { // library marker davegut.kasaEnergyMonitor, line 130
		energy = data.energy // library marker davegut.kasaEnergyMonitor, line 131
		if (energy == null) { energy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 132
		energy = Math.round(100*energy)/100 - device.currentValue("currMonthTotal") // library marker davegut.kasaEnergyMonitor, line 133
	} // library marker davegut.kasaEnergyMonitor, line 134
	if (device.currentValue("energy") != energy) { // library marker davegut.kasaEnergyMonitor, line 135
		sendEvent(name: "energy", value: energy, descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 136
		status << [energy: energy] // library marker davegut.kasaEnergyMonitor, line 137
	} // library marker davegut.kasaEnergyMonitor, line 138
	if (status != [:]) { logInfo("setEnergyToday: ${status}") } // library marker davegut.kasaEnergyMonitor, line 139
	if (!state.getEnergy) { // library marker davegut.kasaEnergyMonitor, line 140
		schedule("10 0 0 * * ?", getEnergyThisMonth) // library marker davegut.kasaEnergyMonitor, line 141
		schedule("15 2 0 1 * ?", getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 142
		state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 143
		getEnergyThisMonth() // library marker davegut.kasaEnergyMonitor, line 144
		runIn(10, getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 145
	} // library marker davegut.kasaEnergyMonitor, line 146
} // library marker davegut.kasaEnergyMonitor, line 147

def getEnergyThisMonth() { // library marker davegut.kasaEnergyMonitor, line 149
	state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 150
	def year = getDate().year // library marker davegut.kasaEnergyMonitor, line 151
	logDebug("getEnergyThisMonth: ${year}") // library marker davegut.kasaEnergyMonitor, line 152
	runIn(5, getMonthstat, [data: year]) // library marker davegut.kasaEnergyMonitor, line 153
} // library marker davegut.kasaEnergyMonitor, line 154

def setThisMonth(monthList, date) { // library marker davegut.kasaEnergyMonitor, line 156
	logDebug("setThisMonth: ${date} // ${monthList}") // library marker davegut.kasaEnergyMonitor, line 157
	def data = monthList.find { it.month == date.month && it.year == date.year} // library marker davegut.kasaEnergyMonitor, line 158
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 159
	def totEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 160
	def avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 161
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 162
		status << [msgError: "Return Data Null"] // library marker davegut.kasaEnergyMonitor, line 163
	} else { // library marker davegut.kasaEnergyMonitor, line 164
		status << [msgError: "OK"] // library marker davegut.kasaEnergyMonitor, line 165
		totEnergy = data.energy // library marker davegut.kasaEnergyMonitor, line 166
		if (totEnergy == null) { totEnergy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 167
		if (date.day == 1) { // library marker davegut.kasaEnergyMonitor, line 168
			avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 169
		} else { // library marker davegut.kasaEnergyMonitor, line 170
			avgEnergy = totEnergy /(date.day - 1) // library marker davegut.kasaEnergyMonitor, line 171
		} // library marker davegut.kasaEnergyMonitor, line 172
	} // library marker davegut.kasaEnergyMonitor, line 173
	totEnergy = Math.round(100*totEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 174
	avgEnergy = Math.round(100*avgEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 175
	sendEvent(name: "currMonthTotal", value: totEnergy,  // library marker davegut.kasaEnergyMonitor, line 176
			  descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 177
	status << [currMonthTotal: totEnergy] // library marker davegut.kasaEnergyMonitor, line 178
	sendEvent(name: "currMonthAvg", value: avgEnergy,  // library marker davegut.kasaEnergyMonitor, line 179
		 	 descriptionText: "KiloWatt Hours per Day", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 180
	status << [currMonthAvg: avgEnergy] // library marker davegut.kasaEnergyMonitor, line 181
	//	Update energy today in sync with energyThisMonth // library marker davegut.kasaEnergyMonitor, line 182
	getEnergyToday() // library marker davegut.kasaEnergyMonitor, line 183
	logInfo("setThisMonth: ${status}") // library marker davegut.kasaEnergyMonitor, line 184
} // library marker davegut.kasaEnergyMonitor, line 185

def getEnergyLastMonth() { // library marker davegut.kasaEnergyMonitor, line 187
	state.getEnergy = "Last Month" // library marker davegut.kasaEnergyMonitor, line 188
	def date = getDate() // library marker davegut.kasaEnergyMonitor, line 189
	def year = date.year // library marker davegut.kasaEnergyMonitor, line 190
	if (date.month == 1) { // library marker davegut.kasaEnergyMonitor, line 191
		year = year - 1 // library marker davegut.kasaEnergyMonitor, line 192
	} // library marker davegut.kasaEnergyMonitor, line 193
	logDebug("getEnergyLastMonth: ${year}") // library marker davegut.kasaEnergyMonitor, line 194
	runIn(5, getMonthstat, [data: year]) // library marker davegut.kasaEnergyMonitor, line 195
} // library marker davegut.kasaEnergyMonitor, line 196

def setLastMonth(monthList, date) { // library marker davegut.kasaEnergyMonitor, line 198
	logDebug("setLastMonth: ${date} // ${monthList}") // library marker davegut.kasaEnergyMonitor, line 199
	def lastMonthYear = date.year // library marker davegut.kasaEnergyMonitor, line 200
	def lastMonth = date.month - 1 // library marker davegut.kasaEnergyMonitor, line 201
	if (date.month == 1) { // library marker davegut.kasaEnergyMonitor, line 202
		lastMonthYear -+ 1 // library marker davegut.kasaEnergyMonitor, line 203
		lastMonth = 12 // library marker davegut.kasaEnergyMonitor, line 204
	} // library marker davegut.kasaEnergyMonitor, line 205
	def data = monthList.find { it.month == lastMonth } // library marker davegut.kasaEnergyMonitor, line 206
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 207
	def totEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 208
	def avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 209
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 210
		status << [msgError: "Return Data Null"] // library marker davegut.kasaEnergyMonitor, line 211
	} else { // library marker davegut.kasaEnergyMonitor, line 212
		status << [msgError: "OK"] // library marker davegut.kasaEnergyMonitor, line 213
		def monthLength // library marker davegut.kasaEnergyMonitor, line 214
		switch(lastMonth) { // library marker davegut.kasaEnergyMonitor, line 215
			case 4: // library marker davegut.kasaEnergyMonitor, line 216
			case 6: // library marker davegut.kasaEnergyMonitor, line 217
			case 9: // library marker davegut.kasaEnergyMonitor, line 218
			case 11: // library marker davegut.kasaEnergyMonitor, line 219
				monthLength = 30 // library marker davegut.kasaEnergyMonitor, line 220
				break // library marker davegut.kasaEnergyMonitor, line 221
			case 2: // library marker davegut.kasaEnergyMonitor, line 222
				monthLength = 28 // library marker davegut.kasaEnergyMonitor, line 223
				if (lastMonthYear == 2020 || lastMonthYear == 2024 || lastMonthYear == 2028) {  // library marker davegut.kasaEnergyMonitor, line 224
					monthLength = 29 // library marker davegut.kasaEnergyMonitor, line 225
				} // library marker davegut.kasaEnergyMonitor, line 226
				break // library marker davegut.kasaEnergyMonitor, line 227
			default: // library marker davegut.kasaEnergyMonitor, line 228
				monthLength = 31 // library marker davegut.kasaEnergyMonitor, line 229
		} // library marker davegut.kasaEnergyMonitor, line 230
		totEnergy = data.energy // library marker davegut.kasaEnergyMonitor, line 231
		if (totEnergy == null) { totEnergy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 232
		avgEnergy = totEnergy / monthLength // library marker davegut.kasaEnergyMonitor, line 233
	} // library marker davegut.kasaEnergyMonitor, line 234
	totEnergy = Math.round(100*totEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 235
	avgEnergy = Math.round(100*avgEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 236
	sendEvent(name: "lastMonthTotal", value: totEnergy,  // library marker davegut.kasaEnergyMonitor, line 237
			  descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 238
	status << [lastMonthTotal: totEnergy] // library marker davegut.kasaEnergyMonitor, line 239
	sendEvent(name: "lastMonthAvg", value: avgEnergy,  // library marker davegut.kasaEnergyMonitor, line 240
			  descriptionText: "KiloWatt Hoursper Day", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 241
	status << [lastMonthAvg: avgEnergy] // library marker davegut.kasaEnergyMonitor, line 242
	logInfo("setLastMonth: ${status}") // library marker davegut.kasaEnergyMonitor, line 243
} // library marker davegut.kasaEnergyMonitor, line 244

def getRealtime() { // library marker davegut.kasaEnergyMonitor, line 246
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaEnergyMonitor, line 247
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaEnergyMonitor, line 248
				""""emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 249
	} else if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaEnergyMonitor, line 250
		sendCmd("""{"smartlife.iot.common.emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 251
	} else { // library marker davegut.kasaEnergyMonitor, line 252
		sendCmd("""{"emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 253
	} // library marker davegut.kasaEnergyMonitor, line 254
} // library marker davegut.kasaEnergyMonitor, line 255

def getMonthstat(year) { // library marker davegut.kasaEnergyMonitor, line 257
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaEnergyMonitor, line 258
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaEnergyMonitor, line 259
				""""emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 260
	} else if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaEnergyMonitor, line 261
		sendCmd("""{"smartlife.iot.common.emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 262
	} else { // library marker davegut.kasaEnergyMonitor, line 263
		sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 264
	} // library marker davegut.kasaEnergyMonitor, line 265
} // library marker davegut.kasaEnergyMonitor, line 266

// ~~~~~ end include (1149) davegut.kasaEnergyMonitor ~~~~~
