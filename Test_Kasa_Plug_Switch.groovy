/*	Kasa Device Driver Series

		Copyright Dave Gutheinz

License Information:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md

===== Version 6.5.0 =====
1.	Update to comms using variable vice hard-coded device port
2.	Updated comms type processing to account for deviceIP = CLOUD
===================================================================================================*/
def type() { return "Plug Switch" }
//def type() { return "Dimming Switch" }
//def type() { return "EM Plug" }
def file() {
	def filename = type().replaceAll(" ", "-")
	if (type() == "a Dimming Switch") {
		filename = "DimmingSwitch"
	}
	return filename
}

metadata {
//	definition (name: "Kasa ${type()}",
	definition (name: "Test Kasa ${type()}",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/DeviceDrivers/${file()}.groovy"
			   ) {
		capability "Switch"
		if (type() == "Dimming Switch") {
			capability "Switch Level"
			capability "Level Preset"
			capability "Change Level"
		}
		capability "Actuator"
		capability "Refresh"
		command "setPollInterval", [[
			name: "Poll Interval in seconds",
			constraints: ["default", "5 seconds", "10 seconds", "15 seconds",
						  "30 seconds", "1 minute", "5 minutes",  "10 minutes",
						  "30 minutes"],
			type: "ENUM"]]
		if (type().contains("EM")) {
			capability "Power Meter"
			capability "Energy Meter"
			attribute "currMonthTotal", "number"
			attribute "currMonthAvg", "number"
			attribute "lastMonthTotal", "number"
			attribute "lastMonthAvg", "number"
		}
		command "ledOn"
		command "ledOff"
		attribute "led", "string"
		attribute "connection", "string"
		attribute "commsError", "string"
	}

	preferences {
		if (type().contains("EM")) {
			input ("emFunction", "bool", 
				   title: "Enable Energy Monitor", 
				   defaultValue: false)
		}
		input ("debug", "bool",
			   title: "30 minutes of debug logging", 
			   defaultValue: false)
		input ("descriptionText", "bool", 
			   title: "Enable information logging", 
			   defaultValue: true)
		input ("nameSync", "enum", title: "Synchronize Names",
			   defaultValue: "none",
			   options: ["none": "Don't synchronize",
						 "device" : "Kasa device name master", 
						 "Hubitat" : "Hubitat label master"])
		input ("bind", "bool",
			   title: "Kasa Cloud Binding",
			   defalutValue: true)
//	update for useKasaCloud
		if (bind && parent.kasaToken) {
			input ("useCloud", "bool",
				   title: "Use Kasa Cloud for device control",
				   defaultValue: false)
		}
/////////////////////////////
		if (getDataValue("model") == "HS200" && getDataValue("deviceIP") != "CLOUD") {
			input ("altLan", "bool",
				   title: "Alternate LAN Comms (for comms problems only)",
				   defaultValue: false)
		}
		input ("rebootDev", "bool",
			   title: "Reboot device <b>[Caution]</b>",
			   defaultValue: false)
	}
}

def installed() {
	def msg = "installed: "
	def commInst = instCommon()
	message += commInst
	runIn(2, updated)
	logInfo(msg)
}

def updated() {
	def updStatus = updCommon()
	log.info "[${type()}, ${driverVer()}, ${device.label}]  updated: ${updStatus}"
	runIn(3, refresh)
}

def updateDriverData() {
	def drvVer = getDataValue("driverVersion")
	if (drvVer == !driverVer()) {
		state.remove("lastLanCmd")
		state.remove("commsErrorText")
		if (!state.pollInterval) { state.pollInterval = "30 minutes" }
		if (!state.bulbPresets) { state.bulbPresets = [:] }
		updateDataValue("driverVersion", driverVer())
	}
	return driverVer()
}

def on() {
	logDebug("on")
	if (!emFunction) {
		if (type() != "Dimming Switch") {
			sendCmd("""{"system":{"set_relay_state":{"state":1},""" +
					""""get_sysinfo":{}}}""")
		} else{
			sendCmd("""{"system":{"set_relay_state":{"state":1}}}""")
		}
	} else {
		sendCmd("""{"system":{"set_relay_state":{"state":1},""" +
				""""get_sysinfo":{}},"emeter":{"get_realtime":{}}}""")
	}
}

def off() {
	logDebug("off")
	if (!emFunction) {
		if (type() != "Dimming Switch") {
			sendCmd("""{"system":{"set_relay_state":{"state":0},""" +
					""""get_sysinfo":{}}}""")
		} else{
			sendCmd("""{"system":{"set_relay_state":{"state":0}}}""")
		}
	} else {
		sendCmd("""{"system":{"set_relay_state":{"state":0},""" +
				""""get_sysinfo":{}},"emeter":{"get_realtime":{}}}""")
	}
}

def setLevel(percentage, transition = null) {
	logDebug("setLevel: level = ${percentage}")
	percentage = percentage.toInteger()
	if (percentage < 0) { percentage = 0 }
	if (percentage > 100) { percentage = 100 }
	percentage = percentage.toInteger()
	sendCmd("""{"smartlife.iot.dimmer":{"set_brightness":{"brightness":${percentage}}},""" +
			""""system":{"set_relay_state":{"state":1},"get_sysinfo":{}}}""")
}

def presetLevel(percentage) {
	logDebug("presetLevel: level = ${percentage}")
	percentage = percentage.toInteger()
	if (percentage < 0) { percentage = 0 }
	if (percentage > 100) { percentage = 100 }
	percentage = percentage.toInteger()
	sendCmd("""{"smartlife.iot.dimmer":{"set_brightness":{"brightness":${percentage}}},""" +
			""""system" :{"get_sysinfo" :{}}}""")
}

def refresh() {
	logDebug("refresh")
	poll()
}

def poll() {
	if (!emFunction) {
		sendCmd("""{"system":{"get_sysinfo":{}}}""")
	} else {
		sendCmd("""{"system":{"get_sysinfo":{}},""" +
				""""emeter":{"get_realtime":{}}}""")
	}
}

def distResp(response) {
	if (response.system) {
		if (response.system.get_sysinfo) {
			if (nameSync == "device") {
				device.setLabel(response.system.get_sysinfo.alias)
				device.updateSetting("nameSync",[type:"enum", value:"none"])
			}
			setSysInfo(response)
		} else if (response.system.set_relay_state) {
			poll()
		} else if (response.system.reboot) {
			logWarn("distResp: Rebooting device.")
		} else if (response.system.set_dev_alias) {
			if (response.system.set_dev_alias.err_code != 0) {
				logWarn("distResp: Name Sync from Hubitat to Device returned an error.")
			} else {
				device.updateSetting("nameSync",[type:"enum", value:"none"])
			}
		} else {
			logWarn("distResp: Unhandled response = ${response}")
		}
	} else if (response.emeter) {
		def month = new Date().format("M").toInteger()
		if (response.emeter.get_realtime) {
			setPower(response.emeter.get_realtime)
		} else if (response.emeter.get_monthstat.month_list.find { it.month == month }) {
			setEnergyToday(response.emeter.get_monthstat)
		} else if (response.emeter.get_monthstat.month_list.find { it.month == month - 1 }) {
			setLastMonth(response.emeter.get_monthstat)
		} else {
			logWarn("distResp: Unhandled response = ${response}")
		}
	} else if (response.cnCloud) {
		setBindUnbind(response.cnCloud)
	} else {
		logWarn("distResp: Unhandled response = ${response}")
	}
	resetCommsError()
}

def setSysInfo(response) {
	logDebug("setSysInfo: ${response}")
	def status = response.system.get_sysinfo
	def onOff = "on"
	if (status.relay_state == 0) { onOff = "off" }
	if (onOff != device.currentValue("switch")) {
		sendEvent(name: "switch", value: onOff, type: "digital")
		logInfo("setSysInfo: switch: ${onOff}")
	}
	if (type() == "Dimming Switch") {
		if (status.brightness != device.currentValue("level")) {
			sendEvent(name: "level", value: status.brightness, type: "digital")
			logInfo("setSysInfo: level: ${status.brightness}")
		}
	}
	def ledOnOff = "on"
	if (status.led_off == 1) { ledOnOff = "off" }
	if (ledOnOff != device.currentValue("led")) {
		sendEvent(name: "led", value: ledOnOff)
		logInfo("setSysInfo: Led On/Off = ${ledOnOff}")
	}
	if (response.emeter) { setPower(response.emeter.get_realtime) }
}

//	===== includes =====



// ~~~~~ start include (166) davegut.kasaCommunications ~~~~~
import groovy.json.JsonSlurper // library marker davegut.kasaCommunications, line 1
library ( // library marker davegut.kasaCommunications, line 2
	name: "kasaCommunications", // library marker davegut.kasaCommunications, line 3
	namespace: "davegut", // library marker davegut.kasaCommunications, line 4
	author: "Dave Gutheinz", // library marker davegut.kasaCommunications, line 5
	description: "Kasa Communications Methods", // library marker davegut.kasaCommunications, line 6
	category: "communications", // library marker davegut.kasaCommunications, line 7
	documentationLink: "" // library marker davegut.kasaCommunications, line 8
) // library marker davegut.kasaCommunications, line 9

def sendCmd(command) { // library marker davegut.kasaCommunications, line 11
	if (device.currentValue("connection") == "LAN") { // library marker davegut.kasaCommunications, line 12
		sendLanCmd(command) // library marker davegut.kasaCommunications, line 13
	} else if (device.currentValue("connection") == "CLOUD"){ // library marker davegut.kasaCommunications, line 14
		sendKasaCmd(command) // library marker davegut.kasaCommunications, line 15
	} else if (device.currentValue("connection") == "AltLAN") { // library marker davegut.kasaCommunications, line 16
		sendTcpCmd(command) // library marker davegut.kasaCommunications, line 17
	} else { // library marker davegut.kasaCommunications, line 18
		logWarn("sendCmd: attribute connection is not set.") // library marker davegut.kasaCommunications, line 19
	} // library marker davegut.kasaCommunications, line 20
} // library marker davegut.kasaCommunications, line 21

def sendLanCmd(command) { // library marker davegut.kasaCommunications, line 23
	logDebug("sendLanCmd: command = ${command}") // library marker davegut.kasaCommunications, line 24
	if (!command.contains("password")) { // library marker davegut.kasaCommunications, line 25
		state.lastCommand = command // library marker davegut.kasaCommunications, line 26
	} // library marker davegut.kasaCommunications, line 27
	def myHubAction = new hubitat.device.HubAction( // library marker davegut.kasaCommunications, line 28
		outputXOR(command), // library marker davegut.kasaCommunications, line 29
		hubitat.device.Protocol.LAN, // library marker davegut.kasaCommunications, line 30
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, // library marker davegut.kasaCommunications, line 31
		 destinationAddress: "${getDataValue("deviceIP")}:${getDataValue("devicePort")}", // library marker davegut.kasaCommunications, line 32
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING, // library marker davegut.kasaCommunications, line 33
		 parseWarning: true, // library marker davegut.kasaCommunications, line 34
		 timeout: 10, // library marker davegut.kasaCommunications, line 35
		 callback: parseUdp]) // library marker davegut.kasaCommunications, line 36
	try { // library marker davegut.kasaCommunications, line 37
		sendHubCommand(myHubAction) // library marker davegut.kasaCommunications, line 38
	} catch (e) { // library marker davegut.kasaCommunications, line 39
		logWarn("sendLanCmd: LAN Error = ${e}") // library marker davegut.kasaCommunications, line 40
		handleCommsError() // library marker davegut.kasaCommunications, line 41
	} // library marker davegut.kasaCommunications, line 42
} // library marker davegut.kasaCommunications, line 43

def parseUdp(message) { // library marker davegut.kasaCommunications, line 45
	def resp = parseLanMessage(message) // library marker davegut.kasaCommunications, line 46
	if (resp.type == "LAN_TYPE_UDPCLIENT") { // library marker davegut.kasaCommunications, line 47
		def clearResp = inputXOR(resp.payload) // library marker davegut.kasaCommunications, line 48
		if (clearResp.length() > 1022) { // library marker davegut.kasaCommunications, line 49
			if (clearResp.contains("preferred")) { // library marker davegut.kasaCommunications, line 50
				clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}" // library marker davegut.kasaCommunications, line 51
			} else { // library marker davegut.kasaCommunications, line 52
				def msg = "parseUdp: Response is too long for Hubitat UDP implementation." // library marker davegut.kasaCommunications, line 53
				msg += "\n\t<b>Device attributes have not been updated.</b>" // library marker davegut.kasaCommunications, line 54
				if(device.getName().contains("Multi")) { // library marker davegut.kasaCommunications, line 55
					msg += "\n\t<b>HS300:</b>\tCheck your device names. The total Kasa App names of all " // library marker davegut.kasaCommunications, line 56
					msg += "\n\t\t\tdevice names can't exceed 96 charactrs (16 per device).\n\r" // library marker davegut.kasaCommunications, line 57
				} // library marker davegut.kasaCommunications, line 58
				logWarn(msg) // library marker davegut.kasaCommunications, line 59
				return // library marker davegut.kasaCommunications, line 60
			} // library marker davegut.kasaCommunications, line 61
		} // library marker davegut.kasaCommunications, line 62
		def cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.kasaCommunications, line 63
		distResp(cmdResp) // library marker davegut.kasaCommunications, line 64
	} else { // library marker davegut.kasaCommunications, line 65
		logDebug("parse: LAN Error = ${resp.type}") // library marker davegut.kasaCommunications, line 66
		handleCommsError() // library marker davegut.kasaCommunications, line 67
	} // library marker davegut.kasaCommunications, line 68
} // library marker davegut.kasaCommunications, line 69

def sendKasaCmd(command) { // library marker davegut.kasaCommunications, line 71
	logDebug("sendKasaCmd: ${command}") // library marker davegut.kasaCommunications, line 72
	state.lastCommand = command // library marker davegut.kasaCommunications, line 73
	runIn(5, handleCommsError) // library marker davegut.kasaCommunications, line 74
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 75
	def cmdBody = [ // library marker davegut.kasaCommunications, line 76
		method: "passthrough", // library marker davegut.kasaCommunications, line 77
		params: [ // library marker davegut.kasaCommunications, line 78
			deviceId: getDataValue("deviceId"), // library marker davegut.kasaCommunications, line 79
			requestData: "${command}" // library marker davegut.kasaCommunications, line 80
		] // library marker davegut.kasaCommunications, line 81
	] // library marker davegut.kasaCommunications, line 82
	def sendCloudCmdParams = [ // library marker davegut.kasaCommunications, line 83
		uri: "${parent.kasaCloudUrl}/?token=${parent.kasaToken}", // library marker davegut.kasaCommunications, line 84
		requestContentType: 'application/json', // library marker davegut.kasaCommunications, line 85
		contentType: 'application/json', // library marker davegut.kasaCommunications, line 86
		headers: ['Accept':'application/json; version=1, */*; q=0.01'], // library marker davegut.kasaCommunications, line 87
		timeout: 5, // library marker davegut.kasaCommunications, line 88
		body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.kasaCommunications, line 89
	] // library marker davegut.kasaCommunications, line 90
	try { // library marker davegut.kasaCommunications, line 91
		httpPostJson(sendCloudCmdParams) {resp -> // library marker davegut.kasaCommunications, line 92
			if (resp.status == 200 && resp.data.error_code == 0) { // library marker davegut.kasaCommunications, line 93
				def jsonSlurper = new groovy.json.JsonSlurper() // library marker davegut.kasaCommunications, line 94
				distResp(jsonSlurper.parseText(resp.data.result.responseData)) // library marker davegut.kasaCommunications, line 95
			} else { // library marker davegut.kasaCommunications, line 96
				def msg = "sendKasaCmd:\n<b>Error from the Kasa Cloud.</b> Most common cause is " // library marker davegut.kasaCommunications, line 97
				msg += "your Kasa Token has expired.  Run Kasa Login and Token update and try again." // library marker davegut.kasaCommunications, line 98
				msg += "\nAdditional Data: Error ${resp.data.error_code} = ${resp.data.msg}\n\n" // library marker davegut.kasaCommunications, line 99
				logWarn(msg) // library marker davegut.kasaCommunications, line 100
			} // library marker davegut.kasaCommunications, line 101
		} // library marker davegut.kasaCommunications, line 102
	} catch (e) { // library marker davegut.kasaCommunications, line 103
		def msg = "sendKasaCmd:\n<b>Error in Cloud Communications.</b> The Kasa Cloud is unreachable." // library marker davegut.kasaCommunications, line 104
		msg += "\nAdditional Data: Error = ${e}\n\n" // library marker davegut.kasaCommunications, line 105
		logWarn(msg) // library marker davegut.kasaCommunications, line 106
	} // library marker davegut.kasaCommunications, line 107
} // library marker davegut.kasaCommunications, line 108

private sendTcpCmd(command) { // library marker davegut.kasaCommunications, line 110
	logDebug("sendTcpCmd: ${command}") // library marker davegut.kasaCommunications, line 111
	try { // library marker davegut.kasaCommunications, line 112
		interfaces.rawSocket.connect("${getDataValue("deviceIP")}", // library marker davegut.kasaCommunications, line 113
									 "${getDataValue("devicePort")}", byteInterface: true) // library marker davegut.kasaCommunications, line 114
	} catch (error) { // library marker davegut.kasaCommunications, line 115
		logDebug("SendTcpCmd: Unable to connect to device at ${getDataValue("deviceIP")}. " + // library marker davegut.kasaCommunications, line 116
				 "Error = ${error}") // library marker davegut.kasaCommunications, line 117
	} // library marker davegut.kasaCommunications, line 118
	runIn(5, handleCommsError) // library marker davegut.kasaCommunications, line 119
	state.lastCommand = command // library marker davegut.kasaCommunications, line 120
	interfaces.rawSocket.sendMessage(outputXorTcp(command)) // library marker davegut.kasaCommunications, line 121
} // library marker davegut.kasaCommunications, line 122

def socketStatus(message) { // library marker davegut.kasaCommunications, line 124
	if (message != "receive error: Stream closed.") { // library marker davegut.kasaCommunications, line 125
		logDebug("socketStatus: Socket Established") // library marker davegut.kasaCommunications, line 126
	} else { // library marker davegut.kasaCommunications, line 127
		logWarn("socketStatus = ${message}") // library marker davegut.kasaCommunications, line 128
	} // library marker davegut.kasaCommunications, line 129
} // library marker davegut.kasaCommunications, line 130

def parse(message) { // library marker davegut.kasaCommunications, line 132
	def respLength // library marker davegut.kasaCommunications, line 133
	if (message.length() > 8 && message.substring(0,4) == "0000") { // library marker davegut.kasaCommunications, line 134
		def hexBytes = message.substring(0,8) // library marker davegut.kasaCommunications, line 135
		respLength = 8 + 2 * hubitat.helper.HexUtils.hexStringToInt(hexBytes) // library marker davegut.kasaCommunications, line 136
		if (message.length() == respLength) { // library marker davegut.kasaCommunications, line 137
			extractResp(message) // library marker davegut.kasaCommunications, line 138
		} else { // library marker davegut.kasaCommunications, line 139
			state.response = message // library marker davegut.kasaCommunications, line 140
			state.respLength = respLength // library marker davegut.kasaCommunications, line 141
		} // library marker davegut.kasaCommunications, line 142
	} else if (message.length() == 0 || message == null) { // library marker davegut.kasaCommunications, line 143
		return // library marker davegut.kasaCommunications, line 144
	} else { // library marker davegut.kasaCommunications, line 145
		def resp = state.response // library marker davegut.kasaCommunications, line 146
		resp = resp.concat(message) // library marker davegut.kasaCommunications, line 147
		if (resp.length() == state.respLength) { // library marker davegut.kasaCommunications, line 148
			state.response = "" // library marker davegut.kasaCommunications, line 149
			state.respLength = 0 // library marker davegut.kasaCommunications, line 150
			extractResp(message) // library marker davegut.kasaCommunications, line 151
		} else { // library marker davegut.kasaCommunications, line 152
			state.response = resp // library marker davegut.kasaCommunications, line 153
		} // library marker davegut.kasaCommunications, line 154
	} // library marker davegut.kasaCommunications, line 155
} // library marker davegut.kasaCommunications, line 156

def extractResp(message) { // library marker davegut.kasaCommunications, line 158
	if (message.length() == null) { // library marker davegut.kasaCommunications, line 159
		logDebug("extractResp: null return rejected.") // library marker davegut.kasaCommunications, line 160
		return  // library marker davegut.kasaCommunications, line 161
	} // library marker davegut.kasaCommunications, line 162
	logDebug("extractResp: ${message}") // library marker davegut.kasaCommunications, line 163
	try { // library marker davegut.kasaCommunications, line 164
		distResp(parseJson(inputXorTcp(message))) // library marker davegut.kasaCommunications, line 165
	} catch (e) { // library marker davegut.kasaCommunications, line 166
		logWarn("extractResp: Invalid or incomplete return.\nerror = ${e}") // library marker davegut.kasaCommunications, line 167
		handleCommsError() // library marker davegut.kasaCommunications, line 168
	} // library marker davegut.kasaCommunications, line 169
} // library marker davegut.kasaCommunications, line 170

def handleCommsError() { // library marker davegut.kasaCommunications, line 172
	def count = state.errorCount + 1 // library marker davegut.kasaCommunications, line 173
	state.errorCount = count // library marker davegut.kasaCommunications, line 174
	def message = "handleCommsError: Count: ${count}." // library marker davegut.kasaCommunications, line 175
	if (count <= 3) { // library marker davegut.kasaCommunications, line 176
		message += "\n\t\t\t Retransmitting command, try = ${count}" // library marker davegut.kasaCommunications, line 177
		runIn(1, sendCmd, [data: state.lastCommand]) // library marker davegut.kasaCommunications, line 178
	} else if (count == 4) { // library marker davegut.kasaCommunications, line 179
		setCommsError() // library marker davegut.kasaCommunications, line 180
		message += "\n\t\t\t Setting Comms Error." // library marker davegut.kasaCommunications, line 181
	} // library marker davegut.kasaCommunications, line 182
	logDebug(message) // library marker davegut.kasaCommunications, line 183
} // library marker davegut.kasaCommunications, line 184

def setCommsError() { // library marker davegut.kasaCommunications, line 186
	def message = "setCommsError: Four consecutive errors.  Setting commsError to true." // library marker davegut.kasaCommunications, line 187
	if (device.currentValue("commsError") == "false") { // library marker davegut.kasaCommunications, line 188
		sendEvent(name: "commsError", value: "true") // library marker davegut.kasaCommunications, line 189
		message += "\n\t\tFix attempt ${parent.fixConnection(device.currentValue("connection"))}" // library marker davegut.kasaCommunications, line 190
		logWarn message // library marker davegut.kasaCommunications, line 191
	} // library marker davegut.kasaCommunications, line 192
} // library marker davegut.kasaCommunications, line 193

//	Update // library marker davegut.kasaCommunications, line 195
def resetCommsError() { // library marker davegut.kasaCommunications, line 196
	unschedule(handleCommsError) // library marker davegut.kasaCommunications, line 197
	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommunications, line 198
	state.errorCount = 0 // library marker davegut.kasaCommunications, line 199
} // library marker davegut.kasaCommunications, line 200

private outputXOR(command) { // library marker davegut.kasaCommunications, line 202
	def str = "" // library marker davegut.kasaCommunications, line 203
	def encrCmd = "" // library marker davegut.kasaCommunications, line 204
 	def key = 0xAB // library marker davegut.kasaCommunications, line 205
	for (int i = 0; i < command.length(); i++) { // library marker davegut.kasaCommunications, line 206
		str = (command.charAt(i) as byte) ^ key // library marker davegut.kasaCommunications, line 207
		key = str // library marker davegut.kasaCommunications, line 208
		encrCmd += Integer.toHexString(str) // library marker davegut.kasaCommunications, line 209
	} // library marker davegut.kasaCommunications, line 210
   	return encrCmd // library marker davegut.kasaCommunications, line 211
} // library marker davegut.kasaCommunications, line 212

private inputXOR(encrResponse) { // library marker davegut.kasaCommunications, line 214
	String[] strBytes = encrResponse.split("(?<=\\G.{2})") // library marker davegut.kasaCommunications, line 215
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 216
	def key = 0xAB // library marker davegut.kasaCommunications, line 217
	def nextKey // library marker davegut.kasaCommunications, line 218
	byte[] XORtemp // library marker davegut.kasaCommunications, line 219
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommunications, line 220
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.kasaCommunications, line 221
		XORtemp = nextKey ^ key // library marker davegut.kasaCommunications, line 222
		key = nextKey // library marker davegut.kasaCommunications, line 223
		cmdResponse += new String(XORtemp) // library marker davegut.kasaCommunications, line 224
	} // library marker davegut.kasaCommunications, line 225
	return cmdResponse // library marker davegut.kasaCommunications, line 226
} // library marker davegut.kasaCommunications, line 227

private outputXorTcp(command) { // library marker davegut.kasaCommunications, line 229
	def str = "" // library marker davegut.kasaCommunications, line 230
	def encrCmd = "000000" + Integer.toHexString(command.length())  // library marker davegut.kasaCommunications, line 231
 	def key = 0xAB // library marker davegut.kasaCommunications, line 232
	for (int i = 0; i < command.length(); i++) { // library marker davegut.kasaCommunications, line 233
		str = (command.charAt(i) as byte) ^ key // library marker davegut.kasaCommunications, line 234
		key = str // library marker davegut.kasaCommunications, line 235
		encrCmd += Integer.toHexString(str) // library marker davegut.kasaCommunications, line 236
	} // library marker davegut.kasaCommunications, line 237
   	return encrCmd // library marker davegut.kasaCommunications, line 238
} // library marker davegut.kasaCommunications, line 239

private inputXorTcp(resp) { // library marker davegut.kasaCommunications, line 241
	String[] strBytes = resp.substring(8).split("(?<=\\G.{2})") // library marker davegut.kasaCommunications, line 242
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 243
	def key = 0xAB // library marker davegut.kasaCommunications, line 244
	def nextKey // library marker davegut.kasaCommunications, line 245
	byte[] XORtemp // library marker davegut.kasaCommunications, line 246
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommunications, line 247
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.kasaCommunications, line 248
		XORtemp = nextKey ^ key // library marker davegut.kasaCommunications, line 249
		key = nextKey // library marker davegut.kasaCommunications, line 250
		cmdResponse += new String(XORtemp) // library marker davegut.kasaCommunications, line 251
	} // library marker davegut.kasaCommunications, line 252
	return cmdResponse // library marker davegut.kasaCommunications, line 253
} // library marker davegut.kasaCommunications, line 254

def logTrace(msg){ // library marker davegut.kasaCommunications, line 256
	log.trace "[${type()} / ${driverVer()}-r${rel()} / ${device.label}]| ${msg}" // library marker davegut.kasaCommunications, line 257
} // library marker davegut.kasaCommunications, line 258

def logInfo(msg) { // library marker davegut.kasaCommunications, line 260
	if (descriptionText == true) {  // library marker davegut.kasaCommunications, line 261
		log.info "[${type()} / ${driverVer()}-r${rel()} / ${device.label}]| ${msg}" // library marker davegut.kasaCommunications, line 262
	} // library marker davegut.kasaCommunications, line 263
} // library marker davegut.kasaCommunications, line 264

def logDebug(msg){ // library marker davegut.kasaCommunications, line 266
	if(debug == true) { // library marker davegut.kasaCommunications, line 267
		log.debug "[${type()} / ${driverVer()}-r${rel()} / ${device.label}]| ${msg}" // library marker davegut.kasaCommunications, line 268
	} // library marker davegut.kasaCommunications, line 269
} // library marker davegut.kasaCommunications, line 270

def debugOff() { // library marker davegut.kasaCommunications, line 272
	device.updateSetting("debug", [type:"bool", value: false]) // library marker davegut.kasaCommunications, line 273
	logInfo("debugLogOff: Debug logging is off.") // library marker davegut.kasaCommunications, line 274
} // library marker davegut.kasaCommunications, line 275

def logWarn(msg){ // library marker davegut.kasaCommunications, line 277
	log.warn "[${type()} / ${driverVer()}-r${rel()} / ${device.label}]| ${msg}" // library marker davegut.kasaCommunications, line 278
} // library marker davegut.kasaCommunications, line 279

// ~~~~~ end include (166) davegut.kasaCommunications ~~~~~

// ~~~~~ start include (167) davegut.kasaEnergyMonitor ~~~~~
library ( // library marker davegut.kasaEnergyMonitor, line 1
	name: "kasaEnergyMonitor", // library marker davegut.kasaEnergyMonitor, line 2
	namespace: "davegut", // library marker davegut.kasaEnergyMonitor, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaEnergyMonitor, line 4
	description: "Kasa energy monitor routines", // library marker davegut.kasaEnergyMonitor, line 5
	category: "energyMonitor", // library marker davegut.kasaEnergyMonitor, line 6
	documentationLink: "" // library marker davegut.kasaEnergyMonitor, line 7
) // library marker davegut.kasaEnergyMonitor, line 8

def setupEmFunction() { // library marker davegut.kasaEnergyMonitor, line 10
	if (emFunction) { // library marker davegut.kasaEnergyMonitor, line 11
		sendEvent(name: "power", value: 0) // library marker davegut.kasaEnergyMonitor, line 12
		sendEvent(name: "energy", value: 0) // library marker davegut.kasaEnergyMonitor, line 13
		sendEvent(name: "currMonthTotal", value: 0) // library marker davegut.kasaEnergyMonitor, line 14
		sendEvent(name: "currMonthAvg", value: 0) // library marker davegut.kasaEnergyMonitor, line 15
		sendEvent(name: "lastMonthTotal", value: 0) // library marker davegut.kasaEnergyMonitor, line 16
		sendEvent(name: "lastMonthAvg", value: 0) // library marker davegut.kasaEnergyMonitor, line 17
		def start = Math.round(30 * Math.random()).toInteger() // library marker davegut.kasaEnergyMonitor, line 18
		schedule("${start} */30 * * * ?", getEnergyToday) // library marker davegut.kasaEnergyMonitor, line 19
		runIn(1, getEnergyToday) // library marker davegut.kasaEnergyMonitor, line 20
		return "Initialized" // library marker davegut.kasaEnergyMonitor, line 21
	} else if (device.currentValue("power") != null) { // library marker davegut.kasaEnergyMonitor, line 22
		sendEvent(name: "power", value: 0) // library marker davegut.kasaEnergyMonitor, line 23
		sendEvent(name: "energy", value: 0) // library marker davegut.kasaEnergyMonitor, line 24
		sendEvent(name: "currMonthTotal", value: 0) // library marker davegut.kasaEnergyMonitor, line 25
		sendEvent(name: "currMonthAvg", value: 0) // library marker davegut.kasaEnergyMonitor, line 26
		sendEvent(name: "lastMonthTotal", value: 0) // library marker davegut.kasaEnergyMonitor, line 27
		sendEvent(name: "lastMonthAvg", value: 0) // library marker davegut.kasaEnergyMonitor, line 28
		if (type().contains("Multi")) { // library marker davegut.kasaEnergyMonitor, line 29
			state.remove("powerPollInterval") // library marker davegut.kasaEnergyMonitor, line 30
		} // library marker davegut.kasaEnergyMonitor, line 31
		return "Disabled" // library marker davegut.kasaEnergyMonitor, line 32
	} else { // library marker davegut.kasaEnergyMonitor, line 33
		return "Disabled" // library marker davegut.kasaEnergyMonitor, line 34
	} // library marker davegut.kasaEnergyMonitor, line 35
} // library marker davegut.kasaEnergyMonitor, line 36

def getPower() { // library marker davegut.kasaEnergyMonitor, line 38
	logDebug("getPower") // library marker davegut.kasaEnergyMonitor, line 39
	if (type().contains("Multi")) { // library marker davegut.kasaEnergyMonitor, line 40
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaEnergyMonitor, line 41
				""""emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 42
	} else if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaEnergyMonitor, line 43
		sendCmd("""{"smartlife.iot.common.emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 44
	} else { // library marker davegut.kasaEnergyMonitor, line 45
		sendCmd("""{"emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 46
	} // library marker davegut.kasaEnergyMonitor, line 47
} // library marker davegut.kasaEnergyMonitor, line 48

def setPower(response) { // library marker davegut.kasaEnergyMonitor, line 50
	def power = response.power // library marker davegut.kasaEnergyMonitor, line 51
	if (power == null) { power = response.power_mw / 1000 } // library marker davegut.kasaEnergyMonitor, line 52
	power = Math.round(10*(power))/10 // library marker davegut.kasaEnergyMonitor, line 53
	def curPwr = device.currentValue("power") // library marker davegut.kasaEnergyMonitor, line 54
	if (curPwr < 5 && (power > curPwr + 0.3 || power < curPwr - 0.3)) { // library marker davegut.kasaEnergyMonitor, line 55
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W", type: "digital") // library marker davegut.kasaEnergyMonitor, line 56
		logDebug("polResp: power = ${power}") // library marker davegut.kasaEnergyMonitor, line 57
	} else if (power > curPwr + 5 || power < curPwr - 5) { // library marker davegut.kasaEnergyMonitor, line 58
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W", type: "digital") // library marker davegut.kasaEnergyMonitor, line 59
		logDebug("polResp: power = ${power}") // library marker davegut.kasaEnergyMonitor, line 60
	} // library marker davegut.kasaEnergyMonitor, line 61
} // library marker davegut.kasaEnergyMonitor, line 62

def getEnergyToday() { // library marker davegut.kasaEnergyMonitor, line 64
	logDebug("getEnergyToday") // library marker davegut.kasaEnergyMonitor, line 65
	def year = new Date().format("yyyy").toInteger() // library marker davegut.kasaEnergyMonitor, line 66
	if (type().contains("Multi")) { // library marker davegut.kasaEnergyMonitor, line 67
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaEnergyMonitor, line 68
				""""emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 69
	} else if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaEnergyMonitor, line 70
		sendCmd("""{"smartlife.iot.common.emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 71
	} else { // library marker davegut.kasaEnergyMonitor, line 72
		sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 73
	} // library marker davegut.kasaEnergyMonitor, line 74
} // library marker davegut.kasaEnergyMonitor, line 75

def setEnergyToday(response) { // library marker davegut.kasaEnergyMonitor, line 77
	logDebug("setEnergyToday: response = ${response}") // library marker davegut.kasaEnergyMonitor, line 78
	def month = new Date().format("M").toInteger() // library marker davegut.kasaEnergyMonitor, line 79
	def data = response.month_list.find { it.month == month } // library marker davegut.kasaEnergyMonitor, line 80
	def energy = data.energy // library marker davegut.kasaEnergyMonitor, line 81
	if (energy == null) { energy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 82
	energy -= device.currentValue("currMonthTotal") // library marker davegut.kasaEnergyMonitor, line 83
	energy = Math.round(100*energy)/100 // library marker davegut.kasaEnergyMonitor, line 84
	def currEnergy = device.currentValue("energy") // library marker davegut.kasaEnergyMonitor, line 85
	if (currEnergy < energy + 0.05) { // library marker davegut.kasaEnergyMonitor, line 86
		sendEvent(name: "energy", value: energy, descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 87
		logDebug("setEngrToday: [energy: ${energy}]") // library marker davegut.kasaEnergyMonitor, line 88
	} // library marker davegut.kasaEnergyMonitor, line 89
	setThisMonth(response) // library marker davegut.kasaEnergyMonitor, line 90
} // library marker davegut.kasaEnergyMonitor, line 91

def setThisMonth(response) { // library marker davegut.kasaEnergyMonitor, line 93
	logDebug("setThisMonth: response = ${response}") // library marker davegut.kasaEnergyMonitor, line 94
	def month = new Date().format("M").toInteger() // library marker davegut.kasaEnergyMonitor, line 95
	def day = new Date().format("d").toInteger() // library marker davegut.kasaEnergyMonitor, line 96
	def data = response.month_list.find { it.month == month } // library marker davegut.kasaEnergyMonitor, line 97
	def totEnergy = data.energy // library marker davegut.kasaEnergyMonitor, line 98
	if (totEnergy == null) {  // library marker davegut.kasaEnergyMonitor, line 99
		totEnergy = data.energy_wh/1000 // library marker davegut.kasaEnergyMonitor, line 100
	} // library marker davegut.kasaEnergyMonitor, line 101
	totEnergy = Math.round(100*totEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 102
	def avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 103
	if (day != 1) {  // library marker davegut.kasaEnergyMonitor, line 104
		avgEnergy = totEnergy /(day - 1)  // library marker davegut.kasaEnergyMonitor, line 105
	} // library marker davegut.kasaEnergyMonitor, line 106
	avgEnergy = Math.round(100*avgEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 107

	sendEvent(name: "currMonthTotal", value: totEnergy,  // library marker davegut.kasaEnergyMonitor, line 109
			  descriptionText: "KiloWatt Hours", unit: "kWh") // library marker davegut.kasaEnergyMonitor, line 110
	sendEvent(name: "currMonthAvg", value: avgEnergy,  // library marker davegut.kasaEnergyMonitor, line 111
			  descriptionText: "KiloWatt Hours per Day", unit: "kWh/D") // library marker davegut.kasaEnergyMonitor, line 112
	logDebug("setThisMonth: Energy stats set to ${totEnergy} // ${avgEnergy}") // library marker davegut.kasaEnergyMonitor, line 113
	if (month != 1) { // library marker davegut.kasaEnergyMonitor, line 114
		setLastMonth(response) // library marker davegut.kasaEnergyMonitor, line 115
	} else { // library marker davegut.kasaEnergyMonitor, line 116
		def year = new Date().format("yyyy").toInteger() // library marker davegut.kasaEnergyMonitor, line 117
		if (type().contains("Multi")) { // library marker davegut.kasaEnergyMonitor, line 118
			sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaEnergyMonitor, line 119
					""""emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 120
		} else if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaEnergyMonitor, line 121
			sendCmd("""{"smartlife.iot.common.emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 122
		} else { // library marker davegut.kasaEnergyMonitor, line 123
			sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 124
		} // library marker davegut.kasaEnergyMonitor, line 125
	} // library marker davegut.kasaEnergyMonitor, line 126
} // library marker davegut.kasaEnergyMonitor, line 127

def setLastMonth(response) { // library marker davegut.kasaEnergyMonitor, line 129
	logDebug("setLastMonth: response = ${response}") // library marker davegut.kasaEnergyMonitor, line 130
	def year = new Date().format("yyyy").toInteger() // library marker davegut.kasaEnergyMonitor, line 131
	def month = new Date().format("M").toInteger() // library marker davegut.kasaEnergyMonitor, line 132
	def day = new Date().format("d").toInteger() // library marker davegut.kasaEnergyMonitor, line 133
	def lastMonth // library marker davegut.kasaEnergyMonitor, line 134
	if (month == 1) { // library marker davegut.kasaEnergyMonitor, line 135
		lastMonth = 12 // library marker davegut.kasaEnergyMonitor, line 136
	} else { // library marker davegut.kasaEnergyMonitor, line 137
		lastMonth = month - 1 // library marker davegut.kasaEnergyMonitor, line 138
	} // library marker davegut.kasaEnergyMonitor, line 139
	def monthLength // library marker davegut.kasaEnergyMonitor, line 140
	switch(lastMonth) { // library marker davegut.kasaEnergyMonitor, line 141
		case 4: // library marker davegut.kasaEnergyMonitor, line 142
		case 6: // library marker davegut.kasaEnergyMonitor, line 143
		case 9: // library marker davegut.kasaEnergyMonitor, line 144
		case 11: // library marker davegut.kasaEnergyMonitor, line 145
			monthLength = 30 // library marker davegut.kasaEnergyMonitor, line 146
			break // library marker davegut.kasaEnergyMonitor, line 147
		case 2: // library marker davegut.kasaEnergyMonitor, line 148
			monthLength = 28 // library marker davegut.kasaEnergyMonitor, line 149
			if (year == 2020 || year == 2024 || year == 2028) { monthLength = 29 } // library marker davegut.kasaEnergyMonitor, line 150
			break // library marker davegut.kasaEnergyMonitor, line 151
		default: // library marker davegut.kasaEnergyMonitor, line 152
			monthLength = 31 // library marker davegut.kasaEnergyMonitor, line 153
	} // library marker davegut.kasaEnergyMonitor, line 154
	def data = response.month_list.find { it.month == lastMonth } // library marker davegut.kasaEnergyMonitor, line 155
	def totEnergy // library marker davegut.kasaEnergyMonitor, line 156
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 157
		totEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 158
	} else { // library marker davegut.kasaEnergyMonitor, line 159
		totEnergy = data.energy // library marker davegut.kasaEnergyMonitor, line 160
		if (totEnergy == null) {  // library marker davegut.kasaEnergyMonitor, line 161
			totEnergy = data.energy_wh/1000 // library marker davegut.kasaEnergyMonitor, line 162
		} // library marker davegut.kasaEnergyMonitor, line 163
		totEnergy = Math.round(100*totEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 164
	} // library marker davegut.kasaEnergyMonitor, line 165
	def avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 166
	if (day !=1) { // library marker davegut.kasaEnergyMonitor, line 167
		avgEnergy = totEnergy /(day - 1) // library marker davegut.kasaEnergyMonitor, line 168
	} // library marker davegut.kasaEnergyMonitor, line 169
	avgEnergy = Math.round(100*avgEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 170
	sendEvent(name: "lastMonthTotal", value: totEnergy,  // library marker davegut.kasaEnergyMonitor, line 171
			  descriptionText: "KiloWatt Hours", unit: "kWh") // library marker davegut.kasaEnergyMonitor, line 172
	sendEvent(name: "lastMonthAvg", value: avgEnergy,  // library marker davegut.kasaEnergyMonitor, line 173
			  descriptionText: "KiloWatt Hoursper Day", unit: "kWh/D") // library marker davegut.kasaEnergyMonitor, line 174
	logDebug("setLastMonth: Energy stats set to ${totEnergy} // ${avgEnergy}") // library marker davegut.kasaEnergyMonitor, line 175
} // library marker davegut.kasaEnergyMonitor, line 176

// ~~~~~ end include (167) davegut.kasaEnergyMonitor ~~~~~

// ~~~~~ start include (168) davegut.kasaCommon ~~~~~
library ( // library marker davegut.kasaCommon, line 1
	name: "kasaCommon", // library marker davegut.kasaCommon, line 2
	namespace: "davegut", // library marker davegut.kasaCommon, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaCommon, line 4
	description: "Kasa updated and preferences routines", // library marker davegut.kasaCommon, line 5
	category: "energyMonitor", // library marker davegut.kasaCommon, line 6
	documentationLink: "" // library marker davegut.kasaCommon, line 7
) // library marker davegut.kasaCommon, line 8

def driverVer() { return "6.5.0" } // library marker davegut.kasaCommon, line 10
def rel() { return "1" } // library marker davegut.kasaCommon, line 11

//	====== Common Install / Update Elements ===== // library marker davegut.kasaCommon, line 13
def instCommon() { // library marker davegut.kasaCommon, line 14
	def msg = "installed: " // library marker davegut.kasaCommon, line 15
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 16
		msg += "Installing as CLOUD device. Device IP was not found during discovery." // library marker davegut.kasaCommon, line 17
		device.updateSetting("useCloud", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 18
		sendEvent(name: "connection", value: "CLOUD") // library marker davegut.kasaCommon, line 19
	} else { // library marker davegut.kasaCommon, line 20
		msg += "Installing as LAN device. " // library marker davegut.kasaCommon, line 21
		sendEvent(name: "connection", value: "LAN") // library marker davegut.kasaCommon, line 22
		device.updateSetting("useCloud", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 23
	} // library marker davegut.kasaCommon, line 24
	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 25
	state.errorCount = 0 // library marker davegut.kasaCommon, line 26
	state.pollInterval = "30 minutes" // library marker davegut.kasaCommon, line 27
	updateDataValue("driverVersion", driverVer()) // library marker davegut.kasaCommon, line 28
	return msg // library marker davegut.kasaCommon, line 29
} // library marker davegut.kasaCommon, line 30

def updCommon() { // library marker davegut.kasaCommon, line 32
	if (rebootDev) { // library marker davegut.kasaCommon, line 33
		logWarn("updated: ${rebootDevice()}") // library marker davegut.kasaCommon, line 34
		return // library marker davegut.kasaCommon, line 35
	} // library marker davegut.kasaCommon, line 36
	if (syncBulbs) { // library marker davegut.kasaCommon, line 37
		logDebug("updated: ${syncBulbPresets()}") // library marker davegut.kasaCommon, line 38
		return // library marker davegut.kasaCommon, line 39
	} // library marker davegut.kasaCommon, line 40
	if (syncEffects) { // library marker davegut.kasaCommon, line 41
		logDebug("updated: ${syncEffectPresets()}") // library marker davegut.kasaCommon, line 42
		return // library marker davegut.kasaCommon, line 43
	} // library marker davegut.kasaCommon, line 44
	def updStatus = [:] // library marker davegut.kasaCommon, line 45
	if (debug) { runIn(1800, debugOff) } // library marker davegut.kasaCommon, line 46
	updStatus << [debug: debug] // library marker davegut.kasaCommon, line 47
	updStatus << [descriptionText: descriptionText] // library marker davegut.kasaCommon, line 48
	state.errorCount = 0 // library marker davegut.kasaCommon, line 49
	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 50
	if (nameSync != "none") { // library marker davegut.kasaCommon, line 51
		updStatus << [nameSync: syncName()] // library marker davegut.kasaCommon, line 52
	} // library marker davegut.kasaCommon, line 53
	updStatus << [bind: bindUnbind()] // library marker davegut.kasaCommon, line 54
	updStatus << [emFunction: setupEmFunction()] // library marker davegut.kasaCommon, line 55
	updStatus << [pollInterval: setPollInterval()] // library marker davegut.kasaCommon, line 56
	updStatus << [driverVersion: updateDriverData()] // library marker davegut.kasaCommon, line 57
	return updStatus // library marker davegut.kasaCommon, line 58
} // library marker davegut.kasaCommon, line 59

//	===== Preference Methods ===== // library marker davegut.kasaCommon, line 61
def setPollInterval(interval = state.pollInterval) { // library marker davegut.kasaCommon, line 62
	if (interval == "default" || interval == "off") { // library marker davegut.kasaCommon, line 63
		interval = "30 minutes" // library marker davegut.kasaCommon, line 64
	} else if (useCloud && interval.contains("sec")) { // library marker davegut.kasaCommon, line 65
		interval = "1 minute" // library marker davegut.kasaCommon, line 66
	} // library marker davegut.kasaCommon, line 67
	state.pollInterval = interval // library marker davegut.kasaCommon, line 68
	def pollInterval = interval.substring(0,2).toInteger() // library marker davegut.kasaCommon, line 69
	if (interval.contains("sec")) { // library marker davegut.kasaCommon, line 70
		def start = Math.round((pollInterval-1) * Math.random()).toInteger() // library marker davegut.kasaCommon, line 71
		schedule("${start}/${pollInterval} * * * * ?", "poll") // library marker davegut.kasaCommon, line 72
		state.pollWarning = "Polling intervals of less than one minute can take high " + // library marker davegut.kasaCommon, line 73
			"resources and may impact hub performance." // library marker davegut.kasaCommon, line 74
	} else { // library marker davegut.kasaCommon, line 75
		def start = Math.round(59 * Math.random()).toInteger() // library marker davegut.kasaCommon, line 76
		schedule("${start} */${pollInterval} * * * ?", "poll") // library marker davegut.kasaCommon, line 77
		state.remove("pollWarning") // library marker davegut.kasaCommon, line 78
	} // library marker davegut.kasaCommon, line 79
	logDebug("setPollInterval: interval = ${interval}.") // library marker davegut.kasaCommon, line 80
	return interval // library marker davegut.kasaCommon, line 81
} // library marker davegut.kasaCommon, line 82

def rebootDevice() { // library marker davegut.kasaCommon, line 84
	logWarn("rebootDevice: User Commanded Reboot Device!") // library marker davegut.kasaCommon, line 85
	device.updateSetting("rebootDev", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 86
	if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 87
		sendCmd("""{"smartlife.iot.common.system":{"reboot":{"delay":1}}}""") // library marker davegut.kasaCommon, line 88
	} else { // library marker davegut.kasaCommon, line 89
		sendCmd("""{"system":{"reboot":{"delay":1}}}""") // library marker davegut.kasaCommon, line 90
	} // library marker davegut.kasaCommon, line 91
	pauseExecution(10000) // library marker davegut.kasaCommon, line 92
	return "REBOOTING DEVICE" // library marker davegut.kasaCommon, line 93
} // library marker davegut.kasaCommon, line 94

def bindUnbind() { // library marker davegut.kasaCommon, line 96
	def meth = "cnCloud" // library marker davegut.kasaCommon, line 97
	if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 98
		meth = "smartlife.iot.common.cloud" // library marker davegut.kasaCommon, line 99
	} // library marker davegut.kasaCommon, line 100
	def message // library marker davegut.kasaCommon, line 101
log.trace bind // library marker davegut.kasaCommon, line 102
	if (bind == null) { // library marker davegut.kasaCommon, line 103
		sendLanCmd("""{"${meth}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 104
		message = "Updating to current device value" // library marker davegut.kasaCommon, line 105
	} else if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 106
		message = "Device installed as Cloud Only" // library marker davegut.kasaCommon, line 107
		setCommsType(true) // library marker davegut.kasaCommon, line 108
	} else if (type() == "Light Strip") { // library marker davegut.kasaCommon, line 109
		message = "Can't change binding on Light Strip" // library marker davegut.kasaCommon, line 110
		setCommsType(true) // library marker davegut.kasaCommon, line 111
	} else if (bind) { // library marker davegut.kasaCommon, line 112
		if (!parent.kasaToken || parent.userName == null || parent.userPassword == null) { // library marker davegut.kasaCommon, line 113
			message = "useKasaCtr, userName or userPassword not set" // library marker davegut.kasaCommon, line 114
			sendLanCmd("""{"${meth}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 115
		} else { // library marker davegut.kasaCommon, line 116
			message = "Sending bind command" // library marker davegut.kasaCommon, line 117
			sendLanCmd("""{"${meth}":{"bind":{"username":"${parent.userName}",""" + // library marker davegut.kasaCommon, line 118
					""""password":"${parent.userPassword}"}},""" + // library marker davegut.kasaCommon, line 119
					""""${meth}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 120
		} // library marker davegut.kasaCommon, line 121
	} else if (!bind) { // library marker davegut.kasaCommon, line 122
		message = "Sending unbind command" // library marker davegut.kasaCommon, line 123
		sendLanCmd("""{"${meth}":{"unbind":""},"${meth}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 124

	} // library marker davegut.kasaCommon, line 126
	pauseExecution(5000) // library marker davegut.kasaCommon, line 127
	return message // library marker davegut.kasaCommon, line 128
} // library marker davegut.kasaCommon, line 129

def setBindUnbind(cmdResp) { // library marker davegut.kasaCommon, line 131
	def bindState = true // library marker davegut.kasaCommon, line 132
	if (cmdResp.get_info) { // library marker davegut.kasaCommon, line 133
		if (cmdResp.get_info.binded == 0) { bindState = false } // library marker davegut.kasaCommon, line 134
		logInfo("setBindUnbind: Bind status set to ${bindState}") // library marker davegut.kasaCommon, line 135
		setCommsType(bindState) // library marker davegut.kasaCommon, line 136
	} else if (cmdResp.bind.err_code == 0){ // library marker davegut.kasaCommon, line 137
		def meth = "cnCloud" // library marker davegut.kasaCommon, line 138
		if (type().contains("Bulb") || type().contains("Light")) { // library marker davegut.kasaCommon, line 139
			meth = "smartlife.iot.common.cloud" // library marker davegut.kasaCommon, line 140
		} // library marker davegut.kasaCommon, line 141
		if (!device.contains("Multi") || getDataValue("plugNo") == "00") { // library marker davegut.kasaCommon, line 142
			sendLanCmd("""{"${meth}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 143
		} else { // library marker davegut.kasaCommon, line 144
			logWarn("setBindUnbind: Multiplug Plug 00 not installed.") // library marker davegut.kasaCommon, line 145
		} // library marker davegut.kasaCommon, line 146
	} else { // library marker davegut.kasaCommon, line 147
		logWarn("setBindUnbind: Unhandled response: ${cmdResp}") // library marker davegut.kasaCommon, line 148
	} // library marker davegut.kasaCommon, line 149
} // library marker davegut.kasaCommon, line 150

def setCommsType(bindState) { // library marker davegut.kasaCommon, line 152
	def commsType = "LAN" // library marker davegut.kasaCommon, line 153
	def cloudCtrl = false // library marker davegut.kasaCommon, line 154
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 155
		commsType = "CLOUD" // library marker davegut.kasaCommon, line 156
		cloudCtrl = true // library marker davegut.kasaCommon, line 157
	} else if (bindState == true && useCloud == true && parent.useKasaCloud &&  // library marker davegut.kasaCommon, line 158
		parent.userName && parent.userPassword) { // library marker davegut.kasaCommon, line 159
		commsType = "CLOUD" // library marker davegut.kasaCommon, line 160
		cloudCtrl = true // library marker davegut.kasaCommon, line 161
	} else if (altLan == true) { // library marker davegut.kasaCommon, line 162
		commsType = "AltLAN" // library marker davegut.kasaCommon, line 163
	} // library marker davegut.kasaCommon, line 164
	def commsSettings = [bind: bindState, useCloud: cloudCtrl, commsType: commsType] // library marker davegut.kasaCommon, line 165
	device.updateSetting("bind", [type:"bool", value: bindState]) // library marker davegut.kasaCommon, line 166
	device.updateSetting("useCloud", [type:"bool", value: cloudCtrl]) // library marker davegut.kasaCommon, line 167
	sendEvent(name: "connection", value: "${commsType}") // library marker davegut.kasaCommon, line 168
	log.info "[${type()}, ${driverVer()}, ${device.label}]  setCommsType: ${commsSettings}" // library marker davegut.kasaCommon, line 169
	if (type().contains("Multi")) { // library marker davegut.kasaCommon, line 170
		def coordData = [:] // library marker davegut.kasaCommon, line 171
		coordData << [bind: bindState] // library marker davegut.kasaCommon, line 172
		coordData << [useCloud: cloudCtrl] // library marker davegut.kasaCommon, line 173
		coordData << [connection: commsType] // library marker davegut.kasaCommon, line 174
		parent.coordinate("commsData", coordData, getDataValue("deviceId"), getDataValue("plugNo")) // library marker davegut.kasaCommon, line 175
	} // library marker davegut.kasaCommon, line 176
	pauseExecution(1000) // library marker davegut.kasaCommon, line 177
} // library marker davegut.kasaCommon, line 178

def ledOn() { // library marker davegut.kasaCommon, line 180
	logDebug("ledOn: Setting LED to on") // library marker davegut.kasaCommon, line 181
	sendCmd("""{"system":{"set_led_off":{"off":0},""" + // library marker davegut.kasaCommon, line 182
			""""get_sysinfo":{}}}""") // library marker davegut.kasaCommon, line 183
} // library marker davegut.kasaCommon, line 184

def ledOff() { // library marker davegut.kasaCommon, line 186
	logDebug("ledOff: Setting LED to off") // library marker davegut.kasaCommon, line 187
	sendCmd("""{"system":{"set_led_off":{"off":1},""" + // library marker davegut.kasaCommon, line 188
			""""get_sysinfo":{}}}""") // library marker davegut.kasaCommon, line 189
} // library marker davegut.kasaCommon, line 190

def syncName() { // library marker davegut.kasaCommon, line 192
	def message // library marker davegut.kasaCommon, line 193
	if (nameSync == "Hubitat") { // library marker davegut.kasaCommon, line 194
		message = "Syncing device's name from the Hubitat Label/" // library marker davegut.kasaCommon, line 195
		if (type().contains("Multi")) { // library marker davegut.kasaCommon, line 196
			sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaCommon, line 197
					""""system":{"set_dev_alias":{"alias":"${device.label}"}}}""") // library marker davegut.kasaCommon, line 198
		} else { // library marker davegut.kasaCommon, line 199
			sendCmd("""{"system":{"set_dev_alias":{"alias":"${device.label}"}}}""") // library marker davegut.kasaCommon, line 200
		} // library marker davegut.kasaCommon, line 201
	} else if (nameSync == "device") { // library marker davegut.kasaCommon, line 202
		message = "Syncing Hubitat's Label from the device's alias." // library marker davegut.kasaCommon, line 203
		poll() // library marker davegut.kasaCommon, line 204
	} // library marker davegut.kasaCommon, line 205
	return message // library marker davegut.kasaCommon, line 206
} // library marker davegut.kasaCommon, line 207

//	===== Level Up/Down for Bulbs and Dimmers ===== // library marker davegut.kasaCommon, line 209
def startLevelChange(direction) { // library marker davegut.kasaCommon, line 210
	if (direction == "up") { levelUp() } // library marker davegut.kasaCommon, line 211
	else { levelDown() } // library marker davegut.kasaCommon, line 212
} // library marker davegut.kasaCommon, line 213

def stopLevelChange() { // library marker davegut.kasaCommon, line 215
	unschedule(levelUp) // library marker davegut.kasaCommon, line 216
	unschedule(levelDown) // library marker davegut.kasaCommon, line 217
} // library marker davegut.kasaCommon, line 218

def levelUp() { // library marker davegut.kasaCommon, line 220
	def curLevel = device.currentValue("level").toInteger() // library marker davegut.kasaCommon, line 221
	if (curLevel == 100) { return } // library marker davegut.kasaCommon, line 222
	def newLevel = curLevel + 4 // library marker davegut.kasaCommon, line 223
	if (newLevel > 100) { newLevel = 100 } // library marker davegut.kasaCommon, line 224
	setLevel(newLevel, 0) // library marker davegut.kasaCommon, line 225
	runIn(1, levelUp) // library marker davegut.kasaCommon, line 226
} // library marker davegut.kasaCommon, line 227

def levelDown() { // library marker davegut.kasaCommon, line 229
	def curLevel = device.currentValue("level").toInteger() // library marker davegut.kasaCommon, line 230
	if (curLevel == 0) { return } // library marker davegut.kasaCommon, line 231
	def newLevel = curLevel - 4 // library marker davegut.kasaCommon, line 232
	if (newLevel < 0) { newLevel = 0 } // library marker davegut.kasaCommon, line 233
	setLevel(newLevel, 0) // library marker davegut.kasaCommon, line 234
	if (newLevel == 0) { off() } // library marker davegut.kasaCommon, line 235
	runIn(1, levelDown) // library marker davegut.kasaCommon, line 236
} // library marker davegut.kasaCommon, line 237

// ~~~~~ end include (168) davegut.kasaCommon ~~~~~
