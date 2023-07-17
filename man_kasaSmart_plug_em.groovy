/*	TP-Link SMART API / PROTOCOL DRIVER SERIES for plugs, switches, bulbs, hubs and Hub-connected devices.
		Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md

This driver is part of a set of drivers for TP-LINK SMART API plugs, switches, hubs,
sensors and thermostats.  The set encompasses the following:
a.	ALL TAPO devices except cameras and Robot Vacuum Cleaners.
b.	NEWER KASA devices using the SMART API except cameras.
	1.	MATTER devices
	2.	Kasa Hub
	3.	Kasa TRV
=================================================================================================*/
def type() {return "man_kasaSmart_plug_em" }

metadata {
	definition (name: type(), namespace: "davegut", author: "Dave Gutheinz", 
				importUrl: "")
	{
		capability "Switch"
		capability "Refresh"
		attribute "commsError", "string"
		capability "EnergyMeter"
		capability "PowerMeter"
		attribute "energyThisMonth", "number"
	}
	preferences {
		manPreferences()
		commonPreferences()
		plugSwitchPreferences()
		input ("powerProtect", "bool", title: "Enable Power Protection", defaultValue: false)
		input ("pwrProtectWatts", "NUMBER", title: "Power Protection Watts (Max 1660)", 
			   defaultValue: 1000)
		securityPreferences()
	}
}

def installed() { 
	runIn(1, updated)
}
def updated() {
	Map logData = [manInstUpd: manInstUpd()]
	if (logData.manInstUpd.status == "OK") {
		logData = [common: commonUpdated()]
		runEvery10Minutes(energyPoll)
		logData << [energyPoll: "10 mins"]
		runIn(4, delayedUpdates)
	} else {
		logData << [status: "ERROR"]
	}
	if (logData.status == "ERROR") {
		logError("updated: ${logData}")
	} else {
		logInfo("updated: ${logData}")
	}
	pauseExecution(10000)
}

def delayedUpdates() {
	def logData = [:]
	logData << [setAutoOff: setAutoOff()]
	logData << [setDefaultState: setDefaultState()]
	logData << [setPowerProtect: setPowerProtect()]
	logData << [ syncName: syncName()]
	energyPoll()
	logInfo("delayedUpdates: ${logData}")
}

def setPowerProtect() {
	Map logData = [:]
	if (pwrProtectWatts > 1660) {
		logWarn("setPowerProtect: entered watts exceed 1660.  Aborting Power Protect Setup.")
		device.updateSetting("pwrProtectWatts", [type: "number", value: 1000])
		logData << [FAILED: "Invalid User Entry"]
	} else {
		def cmdStr= [method: "multipleRequest",
					params: [requests: [
						[method: "set_protection_power",
						 params: [protection_power: pwrProtectWatts,
								  enabled: powerProtect]],
						[method: "get_protection_power"]
						]]]
		def devData = securePassthrough(cmdStr, false)
		def data = devData.result.responses.find { it.method == "get_protection_power" }.result
		device.updateSetting("pwrProtectWatts", [type: "number", value: data.protection_power])
		device.updateSetting("powerProtect", [type: "bool", value: data.enabled])
		logData << [powerProtect: data.enabled, pwrProtectWatts: data.protection_power]
	}
	return logData
}

def deviceParse(resp, data=null) {
	def cmdResp = parseData(resp)
	if (cmdResp.status == "OK") {
		def devData = cmdResp.cmdResp.result
		if (devData.responses) {
			devData = devData.responses.find{it.method == "get_device_info"}.result
		}
		logDebug("deviceParse: ${devData}")
		def onOff = "off"
		if (devData.device_on == true) { onOff = "on" }
		updateAttr("switch", onOff)
	}
}

def energyPoll() {
	securePassthrough([method: "get_energy_usage"], true, "energyPoll", "parseEnergyPoll")
}
def parseEnergyPoll(resp, data=null) {
	def devData = parseData(resp)
	if(devData.status == "OK") {
		devData = devData.cmdResp.result
		logDebug("parseEnergyPoll: [devData: ${devData}, data: ${data}]")
		updateAttr("power", devData.current_power)
		updateAttr("energy", devData.today_energy/1000)
		updateAttr("energyThisMonth", (devData.month_energy/1000).toInteger())
	}
}








// ~~~~~ start include (1352) davegut.lib_tpLink_manInstall ~~~~~
library ( // library marker davegut.lib_tpLink_manInstall, line 1
	name: "lib_tpLink_manInstall", // library marker davegut.lib_tpLink_manInstall, line 2
	namespace: "davegut", // library marker davegut.lib_tpLink_manInstall, line 3
	author: "Dave Gutheinz", // library marker davegut.lib_tpLink_manInstall, line 4
	description: "Library for manual install of a tpLink device", // library marker davegut.lib_tpLink_manInstall, line 5
	category: "utilities", // library marker davegut.lib_tpLink_manInstall, line 6
	documentationLink: "" // library marker davegut.lib_tpLink_manInstall, line 7
) // library marker davegut.lib_tpLink_manInstall, line 8

import java.security.MessageDigest // library marker davegut.lib_tpLink_manInstall, line 10

def manPreferences() { // library marker davegut.lib_tpLink_manInstall, line 12
	input ("devIp", "string", title: "Device IP", // library marker davegut.lib_tpLink_manInstall, line 13
		   defaultValue: "notEntered") // library marker davegut.lib_tpLink_manInstall, line 14
	input ("username", "string", title: "Kasa Username", // library marker davegut.lib_tpLink_manInstall, line 15
		   defaultValue: "notEntered") // library marker davegut.lib_tpLink_manInstall, line 16
	input ("password", "password", title: "Kasa Password", // library marker davegut.lib_tpLink_manInstall, line 17
		   defaultValue: "notEntered") // library marker davegut.lib_tpLink_manInstall, line 18
	input ("encUsername", "password", title: "Storage for the tpLink credentials", // library marker davegut.lib_tpLink_manInstall, line 19
		   defaultValue:"notEntered") // library marker davegut.lib_tpLink_manInstall, line 20
	input ("encPassword", "password", title: "Storage for the tpLink credentials", // library marker davegut.lib_tpLink_manInstall, line 21
		   defaultValue:"notEntered") // library marker davegut.lib_tpLink_manInstall, line 22
} // library marker davegut.lib_tpLink_manInstall, line 23

def manInstUpd() { // library marker davegut.lib_tpLink_manInstall, line 25
	def logData = [deviceIp: devIp, userName: username, password: password] // library marker davegut.lib_tpLink_manInstall, line 26
	if (devIp != "notEntered" && username != "notEntered" && // library marker davegut.lib_tpLink_manInstall, line 27
		password != "notEntered") { // library marker davegut.lib_tpLink_manInstall, line 28
		//	trim and update user-entered data // library marker davegut.lib_tpLink_manInstall, line 29
		devIp = devIp.trim() // library marker davegut.lib_tpLink_manInstall, line 30
		device.updateSetting("devIp", [type:"string", value: devIp]) // library marker davegut.lib_tpLink_manInstall, line 31
		username = username.trim() // library marker davegut.lib_tpLink_manInstall, line 32
		device.updateSetting("username", [type:"string", value: username]) // library marker davegut.lib_tpLink_manInstall, line 33
		password = password.trim() // library marker davegut.lib_tpLink_manInstall, line 34
		device.updateSetting("password", [type:"password", value: password]) // library marker davegut.lib_tpLink_manInstall, line 35

		updateDataValue("deviceIp", devIp) // library marker davegut.lib_tpLink_manInstall, line 37
		String encUsername = mdEncode(username).bytes.encodeBase64().toString() // library marker davegut.lib_tpLink_manInstall, line 38
		String encPassword = password.bytes.encodeBase64().toString() // library marker davegut.lib_tpLink_manInstall, line 39
		device.updateSetting("encUsername", [type:"password", value: encUsername]) // library marker davegut.lib_tpLink_manInstall, line 40
		device.updateSetting("encPassword", [type:"password", value: encPassword]) // library marker davegut.lib_tpLink_manInstall, line 41
		logData << [encUsername: encUsername, encPassword: encPassword] // library marker davegut.lib_tpLink_manInstall, line 42
		logData << [status: "OK"] // library marker davegut.lib_tpLink_manInstall, line 43
	} else { // library marker davegut.lib_tpLink_manInstall, line 44
		logData << [status: "ERROR", reason: "missingData"] // library marker davegut.lib_tpLink_manInstall, line 45
	} // library marker davegut.lib_tpLink_manInstall, line 46
	return logData // library marker davegut.lib_tpLink_manInstall, line 47
} // library marker davegut.lib_tpLink_manInstall, line 48

private String mdEncode(String message) { // library marker davegut.lib_tpLink_manInstall, line 50
	MessageDigest md = MessageDigest.getInstance("SHA-1") // library marker davegut.lib_tpLink_manInstall, line 51
	md.update(message.getBytes()) // library marker davegut.lib_tpLink_manInstall, line 52
	byte[] digest = md.digest() // library marker davegut.lib_tpLink_manInstall, line 53
	return digest.encodeHex() // library marker davegut.lib_tpLink_manInstall, line 54
} // library marker davegut.lib_tpLink_manInstall, line 55


// ~~~~~ end include (1352) davegut.lib_tpLink_manInstall ~~~~~

// ~~~~~ start include (1354) davegut.lib_tpLink_plugSwitch ~~~~~
library ( // library marker davegut.lib_tpLink_plugSwitch, line 1
	name: "lib_tpLink_plugSwitch", // library marker davegut.lib_tpLink_plugSwitch, line 2
	namespace: "davegut", // library marker davegut.lib_tpLink_plugSwitch, line 3
	author: "Compied by Dave Gutheinz", // library marker davegut.lib_tpLink_plugSwitch, line 4
	description: "Method common to tpLink plugs and switches", // library marker davegut.lib_tpLink_plugSwitch, line 5
	category: "utilities", // library marker davegut.lib_tpLink_plugSwitch, line 6
	documentationLink: "" // library marker davegut.lib_tpLink_plugSwitch, line 7
) // library marker davegut.lib_tpLink_plugSwitch, line 8

def on() { // library marker davegut.lib_tpLink_plugSwitch, line 10
	setPower(true) // library marker davegut.lib_tpLink_plugSwitch, line 11
	if (autoOffEnable) { // library marker davegut.lib_tpLink_plugSwitch, line 12
		runIn(5 + 60 * autoOffTime.toInteger(), refresh) // library marker davegut.lib_tpLink_plugSwitch, line 13
	} // library marker davegut.lib_tpLink_plugSwitch, line 14
} // library marker davegut.lib_tpLink_plugSwitch, line 15
def off() { // library marker davegut.lib_tpLink_plugSwitch, line 16
	setPower(false) // library marker davegut.lib_tpLink_plugSwitch, line 17
	unschedule(off) // library marker davegut.lib_tpLink_plugSwitch, line 18
} // library marker davegut.lib_tpLink_plugSwitch, line 19
def setPower(onOff) { // library marker davegut.lib_tpLink_plugSwitch, line 20
	logDebug("setPower: [device_on: ${onOff}]") // library marker davegut.lib_tpLink_plugSwitch, line 21
	List requests = [[ // library marker davegut.lib_tpLink_plugSwitch, line 22
		method: "set_device_info", // library marker davegut.lib_tpLink_plugSwitch, line 23
		params: [device_on: onOff]]] // library marker davegut.lib_tpLink_plugSwitch, line 24
	sendMultiCmd(requests, true, "setPower") // library marker davegut.lib_tpLink_plugSwitch, line 25
} // library marker davegut.lib_tpLink_plugSwitch, line 26

def plugSwitchPreferences() { // library marker davegut.lib_tpLink_plugSwitch, line 28
	input ("autoOffEnable", "bool", title: "Enable Auto Off", defaultValue: false) // library marker davegut.lib_tpLink_plugSwitch, line 29
	input ("autoOffTime", "NUMBER", title: "Auto Off Time (minutes)", defaultValue: 120) // library marker davegut.lib_tpLink_plugSwitch, line 30
	input ("defState", "enum", title: "Power Loss Default State", // library marker davegut.lib_tpLink_plugSwitch, line 31
		   options: ["lastState", "on", "off"], defaultValue: "lastState") // library marker davegut.lib_tpLink_plugSwitch, line 32
} // library marker davegut.lib_tpLink_plugSwitch, line 33

def setAutoOff() { // library marker davegut.lib_tpLink_plugSwitch, line 35
	def cmdStr= [method: "multipleRequest", // library marker davegut.lib_tpLink_plugSwitch, line 36
					params: [requests: [ // library marker davegut.lib_tpLink_plugSwitch, line 37
						[method: "set_auto_off_config", // library marker davegut.lib_tpLink_plugSwitch, line 38
						 params: [enable:autoOffEnable,  // library marker davegut.lib_tpLink_plugSwitch, line 39
								  delay_min: autoOffTime.toInteger()]], // library marker davegut.lib_tpLink_plugSwitch, line 40
						[method: "get_auto_off_config"] // library marker davegut.lib_tpLink_plugSwitch, line 41
						]]] // library marker davegut.lib_tpLink_plugSwitch, line 42
	def devData = securePassthrough(cmdStr, false) // library marker davegut.lib_tpLink_plugSwitch, line 43
	def data = devData.result.responses.find { it.method == "get_auto_off_config" } // library marker davegut.lib_tpLink_plugSwitch, line 44
	device.updateSetting("autoOffTime", [type: "number", value: data.result.delay_min]) // library marker davegut.lib_tpLink_plugSwitch, line 45
	device.updateSetting("autoOffEnable", [type: "bool", value: data.result.enable]) // library marker davegut.lib_tpLink_plugSwitch, line 46
	return [enable: data.result.enable, time: data.result.delay_min] // library marker davegut.lib_tpLink_plugSwitch, line 47
} // library marker davegut.lib_tpLink_plugSwitch, line 48

def setDefaultState() { // library marker davegut.lib_tpLink_plugSwitch, line 50
	def type = "last_states" // library marker davegut.lib_tpLink_plugSwitch, line 51
	def state = [] // library marker davegut.lib_tpLink_plugSwitch, line 52
	if (defState == "on") { // library marker davegut.lib_tpLink_plugSwitch, line 53
		type = "custom" // library marker davegut.lib_tpLink_plugSwitch, line 54
		state = [on: true] // library marker davegut.lib_tpLink_plugSwitch, line 55
	} else if (defState == "off") { // library marker davegut.lib_tpLink_plugSwitch, line 56
		type = "custom" // library marker davegut.lib_tpLink_plugSwitch, line 57
		state = [on: false] // library marker davegut.lib_tpLink_plugSwitch, line 58
	} // library marker davegut.lib_tpLink_plugSwitch, line 59
	def cmdStr= [method: "multipleRequest", // library marker davegut.lib_tpLink_plugSwitch, line 60
					params: [requests: [ // library marker davegut.lib_tpLink_plugSwitch, line 61
						[method: "set_device_info", // library marker davegut.lib_tpLink_plugSwitch, line 62
						 params: [default_states: [type: type, state: state]]], // library marker davegut.lib_tpLink_plugSwitch, line 63
						[method: "get_device_info"] // library marker davegut.lib_tpLink_plugSwitch, line 64
						]]] // library marker davegut.lib_tpLink_plugSwitch, line 65
	def devData = securePassthrough(cmdStr, false) // library marker davegut.lib_tpLink_plugSwitch, line 66
	def data = devData.result.responses.find { it.method == "get_device_info" } // library marker davegut.lib_tpLink_plugSwitch, line 67
	def defaultStates = data.result.default_states // library marker davegut.lib_tpLink_plugSwitch, line 68
	def newState = "lastState" // library marker davegut.lib_tpLink_plugSwitch, line 69
	if (defaultStates.type == "custom"){ // library marker davegut.lib_tpLink_plugSwitch, line 70
		newState = "off" // library marker davegut.lib_tpLink_plugSwitch, line 71
		if (defaultStates.state.on == true) { // library marker davegut.lib_tpLink_plugSwitch, line 72
			newState = "on" // library marker davegut.lib_tpLink_plugSwitch, line 73
		} // library marker davegut.lib_tpLink_plugSwitch, line 74
	} // library marker davegut.lib_tpLink_plugSwitch, line 75
	device.updateSetting("defState", [type: "enum", value: newState]) // library marker davegut.lib_tpLink_plugSwitch, line 76
	return [defState: newState] // library marker davegut.lib_tpLink_plugSwitch, line 77
} // library marker davegut.lib_tpLink_plugSwitch, line 78

// ~~~~~ end include (1354) davegut.lib_tpLink_plugSwitch ~~~~~

// ~~~~~ start include (1335) davegut.lib_tpLink_common ~~~~~
library ( // library marker davegut.lib_tpLink_common, line 1
	name: "lib_tpLink_common", // library marker davegut.lib_tpLink_common, line 2
	namespace: "davegut", // library marker davegut.lib_tpLink_common, line 3
	author: "Compied by Dave Gutheinz", // library marker davegut.lib_tpLink_common, line 4
	description: "Method common to tpLink device DRIVERS", // library marker davegut.lib_tpLink_common, line 5
	category: "utilities", // library marker davegut.lib_tpLink_common, line 6
	documentationLink: "" // library marker davegut.lib_tpLink_common, line 7
) // library marker davegut.lib_tpLink_common, line 8
def driverVer() { return "B.1.0" } // library marker davegut.lib_tpLink_common, line 9

def commonPreferences() { // library marker davegut.lib_tpLink_common, line 11
	input ("nameSync", "enum", title: "Synchronize Names", // library marker davegut.lib_tpLink_common, line 12
		   options: ["none": "Don't synchronize", // library marker davegut.lib_tpLink_common, line 13
					 "device" : "TP-Link device name master", // library marker davegut.lib_tpLink_common, line 14
					 "Hubitat" : "Hubitat label master"], // library marker davegut.lib_tpLink_common, line 15
		   defaultValue: "none") // library marker davegut.lib_tpLink_common, line 16
	input ("developerData", "bool", title: "Get Data for Developer", defaultValue: false) // library marker davegut.lib_tpLink_common, line 17
} // library marker davegut.lib_tpLink_common, line 18

def commonUpdated() { // library marker davegut.lib_tpLink_common, line 20
	unschedule() // library marker davegut.lib_tpLink_common, line 21
	if (developerData) { // library marker davegut.lib_tpLink_common, line 22
		runIn(10, getDeveloperData) // library marker davegut.lib_tpLink_common, line 23
		device.updateSetting("developerData",[type:"bool", value: false]) // library marker davegut.lib_tpLink_common, line 24
	} // library marker davegut.lib_tpLink_common, line 25
	Map logData = [:] // library marker davegut.lib_tpLink_common, line 26
	updateAttr("commsError", false) // library marker davegut.lib_tpLink_common, line 27
	state.errorCount = 0 // library marker davegut.lib_tpLink_common, line 28
	state.lastCmd = "" // library marker davegut.lib_tpLink_common, line 29
	logData << [login: setLoginInterval()] // library marker davegut.lib_tpLink_common, line 30
	logData << [refresh: setRefreshInterval()] // library marker davegut.lib_tpLink_common, line 31
	logData << setLogsOff() // library marker davegut.lib_tpLink_common, line 32
	runIn(1, deviceLogin) // library marker davegut.lib_tpLink_common, line 33
	pauseExecution(2000) // library marker davegut.lib_tpLink_common, line 34
	return logData // library marker davegut.lib_tpLink_common, line 35
} // library marker davegut.lib_tpLink_common, line 36

def setRefreshInterval() { // library marker davegut.lib_tpLink_common, line 38
	runEvery10Minutes(refresh) // library marker davegut.lib_tpLink_common, line 39
	return "10 mins" // library marker davegut.lib_tpLink_common, line 40
} // library marker davegut.lib_tpLink_common, line 41

def setLoginInterval() { // library marker davegut.lib_tpLink_common, line 43
	runEvery3Hours(deviceLogin) // library marker davegut.lib_tpLink_common, line 44
	return "3 hrs" // library marker davegut.lib_tpLink_common, line 45
} // library marker davegut.lib_tpLink_common, line 46

def syncName() { // library marker davegut.lib_tpLink_common, line 48
	def logData = [syncName: nameSync] // library marker davegut.lib_tpLink_common, line 49
	List requests = [] // library marker davegut.lib_tpLink_common, line 50
	if (nameSync == "none") { // library marker davegut.lib_tpLink_common, line 51
		logData << [result: "noSyncRequired"] // library marker davegut.lib_tpLink_common, line 52
	} else { // library marker davegut.lib_tpLink_common, line 53
		if (nameSync == "Hubitat") { // library marker davegut.lib_tpLink_common, line 54
			String nickname = device.getLabel().bytes.encodeBase64().toString() // library marker davegut.lib_tpLink_common, line 55
			requests = [ // library marker davegut.lib_tpLink_common, line 56
				[method: "set_device_info", // library marker davegut.lib_tpLink_common, line 57
				 params: [nickname: nickname]]] // library marker davegut.lib_tpLink_common, line 58
		} // library marker davegut.lib_tpLink_common, line 59
		def cmdResp = sendMultiCmd(requests, false) // library marker davegut.lib_tpLink_common, line 60
		cmdResp = cmdResp.result.responses.find { it.method == "get_device_info" } // library marker davegut.lib_tpLink_common, line 61
		byte[] plainBytes = cmdResp.result.nickname.decodeBase64() // library marker davegut.lib_tpLink_common, line 62
		def nickname = new String(plainBytes) // library marker davegut.lib_tpLink_common, line 63
		device.setLabel(nickname) // library marker davegut.lib_tpLink_common, line 64
		logData << [label: nickname, status: "labelUpdated"] // library marker davegut.lib_tpLink_common, line 65
	} // library marker davegut.lib_tpLink_common, line 66
	device.updateSetting("nameSync",[type:"enum", value:"none"]) // library marker davegut.lib_tpLink_common, line 67
	return logData // library marker davegut.lib_tpLink_common, line 68
} // library marker davegut.lib_tpLink_common, line 69

def getDeveloperData() { // library marker davegut.lib_tpLink_common, line 71
	def attrData = device.getCurrentStates() // library marker davegut.lib_tpLink_common, line 72
	Map attrs = [:] // library marker davegut.lib_tpLink_common, line 73
	attrData.each { // library marker davegut.lib_tpLink_common, line 74
		attrs << ["${it.name}": it.value] // library marker davegut.lib_tpLink_common, line 75
	} // library marker davegut.lib_tpLink_common, line 76
	Date date = new Date() // library marker davegut.lib_tpLink_common, line 77
	Map devData = [ // library marker davegut.lib_tpLink_common, line 78
		currentTime: date.toString(), // library marker davegut.lib_tpLink_common, line 79
		lastLogin: state.lastSuccessfulLogin, // library marker davegut.lib_tpLink_common, line 80
		name: device.getName(), // library marker davegut.lib_tpLink_common, line 81
		status: device.getStatus(), // library marker davegut.lib_tpLink_common, line 82
		aesKeyLen: aesKey.length(), // library marker davegut.lib_tpLink_common, line 83
		cookieLen: deviceCookie.length(), // library marker davegut.lib_tpLink_common, line 84
		tokenLen: deviceToken.length(), // library marker davegut.lib_tpLink_common, line 85
		dataValues: device.getData(), // library marker davegut.lib_tpLink_common, line 86
		attributes: attrs, // library marker davegut.lib_tpLink_common, line 87
		cmdResp: securePassthrough([method: "get_device_info"], false), // library marker davegut.lib_tpLink_common, line 88
		childData: getChildDevData() // library marker davegut.lib_tpLink_common, line 89
	] // library marker davegut.lib_tpLink_common, line 90
	logWarn("DEVELOPER DATA: ${devData}") // library marker davegut.lib_tpLink_common, line 91
} // library marker davegut.lib_tpLink_common, line 92

def getChildDevData(){ // library marker davegut.lib_tpLink_common, line 94
	Map cmdBody = [ // library marker davegut.lib_tpLink_common, line 95
		method: "get_child_device_list" // library marker davegut.lib_tpLink_common, line 96
	] // library marker davegut.lib_tpLink_common, line 97
	def childData = securePassthrough(cmdBody, false) // library marker davegut.lib_tpLink_common, line 98
	if (childData.error_code == 0) { // library marker davegut.lib_tpLink_common, line 99
		return childData.result.child_device_list // library marker davegut.lib_tpLink_common, line 100
	} else { // library marker davegut.lib_tpLink_common, line 101
		return "noChildren" // library marker davegut.lib_tpLink_common, line 102
	} // library marker davegut.lib_tpLink_common, line 103
} // library marker davegut.lib_tpLink_common, line 104

//	===== Login Process ===== // library marker davegut.lib_tpLink_common, line 106
def deviceLogin() { // library marker davegut.lib_tpLink_common, line 107
	Map logData = [:] // library marker davegut.lib_tpLink_common, line 108
	def handshakeData = handshake(getDataValue("deviceIp")) // library marker davegut.lib_tpLink_common, line 109

	if (handshakeData.respStatus == "OK") { // library marker davegut.lib_tpLink_common, line 111
		def credentials // library marker davegut.lib_tpLink_common, line 112
		if (device.getParentAppId() == null) { // library marker davegut.lib_tpLink_common, line 113
			credentials = [encUsername: encUsername,encPassword: encPassword] // library marker davegut.lib_tpLink_common, line 114
		} else { // library marker davegut.lib_tpLink_common, line 115
			credentials = parent.getCredentials() // library marker davegut.lib_tpLink_common, line 116
		} // library marker davegut.lib_tpLink_common, line 117
		def tokenData = loginDevice(handshakeData.cookie, handshakeData.aesKey,  // library marker davegut.lib_tpLink_common, line 118
									credentials, getDataValue("deviceIp")) // library marker davegut.lib_tpLink_common, line 119
		if (tokenData.respStatus == "OK") { // library marker davegut.lib_tpLink_common, line 120
			logData << [rsaKeys: handshakeData.rsaKeys, // library marker davegut.lib_tpLink_common, line 121
						cookie: handshakeData.cookie, // library marker davegut.lib_tpLink_common, line 122
						aesKey: handshakeData.aesKey, // library marker davegut.lib_tpLink_common, line 123
						token: tokenData.token] // library marker davegut.lib_tpLink_common, line 124
			device.updateSetting("aesKey", [type:"password", value: handshakeData.aesKey]) // library marker davegut.lib_tpLink_common, line 125
			device.updateSetting("deviceCookie", [type:"password", value: handshakeData.cookie]) // library marker davegut.lib_tpLink_common, line 126
			device.updateSetting("deviceToken", [type:"password", value: tokenData.token]) // library marker davegut.lib_tpLink_common, line 127
			Date date = new Date() // library marker davegut.lib_tpLink_common, line 128
			state.lastSuccessfulLogin = date.toString() // library marker davegut.lib_tpLink_common, line 129
			state.lastLogin = now() // library marker davegut.lib_tpLink_common, line 130
		} else { // library marker davegut.lib_tpLink_common, line 131
			logData << [tokenData: tokenData] // library marker davegut.lib_tpLink_common, line 132
		} // library marker davegut.lib_tpLink_common, line 133
	} else { // library marker davegut.lib_tpLink_common, line 134
		logData << [handshakeData: handshakeData] // library marker davegut.lib_tpLink_common, line 135
	} // library marker davegut.lib_tpLink_common, line 136
	return logData // library marker davegut.lib_tpLink_common, line 137
} // library marker davegut.lib_tpLink_common, line 138

def getAesKey() { // library marker davegut.lib_tpLink_common, line 140
	return new JsonSlurper().parseText(aesKey) // library marker davegut.lib_tpLink_common, line 141
} // library marker davegut.lib_tpLink_common, line 142

def refresh() { // library marker davegut.lib_tpLink_common, line 144
	logDebug("refresh") // library marker davegut.lib_tpLink_common, line 145
	securePassthrough([method: "get_device_info"], true, "refresh") // library marker davegut.lib_tpLink_common, line 146
} // library marker davegut.lib_tpLink_common, line 147

def updateAttr(attr, value) { // library marker davegut.lib_tpLink_common, line 149
	if (device.currentValue(attr) != value) { // library marker davegut.lib_tpLink_common, line 150
		sendEvent(name: attr, value: value) // library marker davegut.lib_tpLink_common, line 151
	} // library marker davegut.lib_tpLink_common, line 152
} // library marker davegut.lib_tpLink_common, line 153

// ~~~~~ end include (1335) davegut.lib_tpLink_common ~~~~~

// ~~~~~ start include (1327) davegut.lib_tpLink_comms ~~~~~
library ( // library marker davegut.lib_tpLink_comms, line 1
	name: "lib_tpLink_comms", // library marker davegut.lib_tpLink_comms, line 2
	namespace: "davegut", // library marker davegut.lib_tpLink_comms, line 3
	author: "Dave Gutheinz", // library marker davegut.lib_tpLink_comms, line 4
	description: "Tapo Communications", // library marker davegut.lib_tpLink_comms, line 5
	category: "utilities", // library marker davegut.lib_tpLink_comms, line 6
	documentationLink: "" // library marker davegut.lib_tpLink_comms, line 7
) // library marker davegut.lib_tpLink_comms, line 8
import org.json.JSONObject // library marker davegut.lib_tpLink_comms, line 9
import groovy.json.JsonOutput // library marker davegut.lib_tpLink_comms, line 10
import groovy.json.JsonBuilder // library marker davegut.lib_tpLink_comms, line 11
import groovy.json.JsonSlurper // library marker davegut.lib_tpLink_comms, line 12

def sendMultiCmd(requests, async, method=null, action="deviceParse") { // library marker davegut.lib_tpLink_comms, line 14
	requests << [method: "get_device_info"] // library marker davegut.lib_tpLink_comms, line 15
	Map cmdBody = [ // library marker davegut.lib_tpLink_comms, line 16
		method: "multipleRequest", // library marker davegut.lib_tpLink_comms, line 17
		params: [requests: requests]] // library marker davegut.lib_tpLink_comms, line 18
	securePassthrough(cmdBody, async, method, action) // library marker davegut.lib_tpLink_comms, line 19
} // library marker davegut.lib_tpLink_comms, line 20

def securePassthrough(cmdBody, async=true, method=null, action = "deviceParse") { // library marker davegut.lib_tpLink_comms, line 22
//	return // library marker davegut.lib_tpLink_comms, line 23
	if (devIp == null) { devIp = getDataValue("deviceIp") } // library marker davegut.lib_tpLink_comms, line 24
	Map cmdData = [cmdBody: cmdBody, async: async, method: method, action: action] // library marker davegut.lib_tpLink_comms, line 25
	logDebug("securePassthrough: ${cmdData}") // library marker davegut.lib_tpLink_comms, line 26
	def uri = "http://${getDataValue("deviceIp")}/app?token=${deviceToken}" // library marker davegut.lib_tpLink_comms, line 27
	cmdBody = JsonOutput.toJson(cmdBody).toString() // library marker davegut.lib_tpLink_comms, line 28
	Map reqBody = [method: "securePassthrough", // library marker davegut.lib_tpLink_comms, line 29
				   params: [request: encrypt(cmdBody, getAesKey())]] // library marker davegut.lib_tpLink_comms, line 30
	state.lastCmd = cmdData // library marker davegut.lib_tpLink_comms, line 31
	if (async) { // library marker davegut.lib_tpLink_comms, line 32
		asyncPost(uri, reqBody, action, deviceCookie, method) // library marker davegut.lib_tpLink_comms, line 33
	} else { // library marker davegut.lib_tpLink_comms, line 34
		def respData = syncPost(uri, reqBody, deviceCookie) // library marker davegut.lib_tpLink_comms, line 35
		Map logData = [reqDni: reqDni] // library marker davegut.lib_tpLink_comms, line 36
		def cmdResp = "ERROR" // library marker davegut.lib_tpLink_comms, line 37
		if (respData.respStatus == "OK") { // library marker davegut.lib_tpLink_comms, line 38
			logData << [respStatus: "OK"] // library marker davegut.lib_tpLink_comms, line 39
			respData = respData.data.result.response // library marker davegut.lib_tpLink_comms, line 40
			cmdResp = new JsonSlurper().parseText(decrypt(respData, getAesKey())) // library marker davegut.lib_tpLink_comms, line 41
		} else { // library marker davegut.lib_tpLink_comms, line 42
			logData << respData // library marker davegut.lib_tpLink_comms, line 43
		} // library marker davegut.lib_tpLink_comms, line 44
		if (logData.respStatus == "OK") { // library marker davegut.lib_tpLink_comms, line 45
			logDebug("securePassthrough - SYNC: ${logData}") // library marker davegut.lib_tpLink_comms, line 46
		} else { // library marker davegut.lib_tpLink_comms, line 47
			logWarn("securePassthrough - SYNC: ${logData}") // library marker davegut.lib_tpLink_comms, line 48
		} // library marker davegut.lib_tpLink_comms, line 49
		return cmdResp // library marker davegut.lib_tpLink_comms, line 50
	} // library marker davegut.lib_tpLink_comms, line 51
} // library marker davegut.lib_tpLink_comms, line 52

//	===== Sync comms for device update ===== // library marker davegut.lib_tpLink_comms, line 54
def syncPost(uri, reqBody, cookie=null) { // library marker davegut.lib_tpLink_comms, line 55
	def reqParams = [ // library marker davegut.lib_tpLink_comms, line 56
		uri: uri, // library marker davegut.lib_tpLink_comms, line 57
		headers: [ // library marker davegut.lib_tpLink_comms, line 58
			Cookie: cookie // library marker davegut.lib_tpLink_comms, line 59
		], // library marker davegut.lib_tpLink_comms, line 60
		body : new JsonBuilder(reqBody).toString() // library marker davegut.lib_tpLink_comms, line 61
	] // library marker davegut.lib_tpLink_comms, line 62
	logDebug("syncPost: [cmdParams: ${reqParams}]") // library marker davegut.lib_tpLink_comms, line 63
	Map respData = [:] // library marker davegut.lib_tpLink_comms, line 64
	try { // library marker davegut.lib_tpLink_comms, line 65
		httpPostJson(reqParams) {resp -> // library marker davegut.lib_tpLink_comms, line 66
			respData << [status: resp.status, data: resp.data] // library marker davegut.lib_tpLink_comms, line 67
			if (resp.status == 200) { // library marker davegut.lib_tpLink_comms, line 68
				respData << [respStatus: "OK", headers: resp.headers] // library marker davegut.lib_tpLink_comms, line 69
			} else { // library marker davegut.lib_tpLink_comms, line 70
				respData << [respStatus: "Return Error"] // library marker davegut.lib_tpLink_comms, line 71
			} // library marker davegut.lib_tpLink_comms, line 72
		} // library marker davegut.lib_tpLink_comms, line 73
	} catch (e) { // library marker davegut.lib_tpLink_comms, line 74
		respData << [status: "HTTP Failed", data: e] // library marker davegut.lib_tpLink_comms, line 75
	} // library marker davegut.lib_tpLink_comms, line 76
	return respData // library marker davegut.lib_tpLink_comms, line 77
} // library marker davegut.lib_tpLink_comms, line 78

def asyncPost(uri, reqBody, parseMethod, cookie=null, reqData=null) { // library marker davegut.lib_tpLink_comms, line 80
	Map logData = [:] // library marker davegut.lib_tpLink_comms, line 81
	def reqParams = [ // library marker davegut.lib_tpLink_comms, line 82
		uri: uri, // library marker davegut.lib_tpLink_comms, line 83
		requestContentType: 'application/json', // library marker davegut.lib_tpLink_comms, line 84
		contentType: 'application/json', // library marker davegut.lib_tpLink_comms, line 85
		headers: [ // library marker davegut.lib_tpLink_comms, line 86
			Cookie: cookie // library marker davegut.lib_tpLink_comms, line 87
		], // library marker davegut.lib_tpLink_comms, line 88
		timeout: 5, // library marker davegut.lib_tpLink_comms, line 89
		body : new groovy.json.JsonBuilder(reqBody).toString() // library marker davegut.lib_tpLink_comms, line 90
	] // library marker davegut.lib_tpLink_comms, line 91
	try { // library marker davegut.lib_tpLink_comms, line 92
		asynchttpPost(parseMethod, reqParams, [data: reqData]) // library marker davegut.lib_tpLink_comms, line 93
		logData << [status: "OK"] // library marker davegut.lib_tpLink_comms, line 94
	} catch (e) { // library marker davegut.lib_tpLink_comms, line 95
		logData << [status: e, reqParams: reqParams] // library marker davegut.lib_tpLink_comms, line 96
	} // library marker davegut.lib_tpLink_comms, line 97
	if (logData.status == "OK") { // library marker davegut.lib_tpLink_comms, line 98
		logDebug("asyncPost: ${logData}") // library marker davegut.lib_tpLink_comms, line 99
	} else { // library marker davegut.lib_tpLink_comms, line 100
		logWarn("asyncPost: ${logData}") // library marker davegut.lib_tpLink_comms, line 101
	} // library marker davegut.lib_tpLink_comms, line 102
} // library marker davegut.lib_tpLink_comms, line 103

def parseData(resp) { // library marker davegut.lib_tpLink_comms, line 105
	//	B.1.1	Added setCommsError(false) to assure error reset on clear comms. // library marker davegut.lib_tpLink_comms, line 106
	def logData = [:] // library marker davegut.lib_tpLink_comms, line 107
	if (resp.data && resp.data != null) { // library marker davegut.lib_tpLink_comms, line 108
		def respData = new JsonSlurper().parseText(resp.data) // library marker davegut.lib_tpLink_comms, line 109
		if (respData.error_code == 0) { // library marker davegut.lib_tpLink_comms, line 110
			def cmdResp // library marker davegut.lib_tpLink_comms, line 111
			try { // library marker davegut.lib_tpLink_comms, line 112
				cmdResp = new JsonSlurper().parseText(decrypt(respData.result.response, getAesKey())) // library marker davegut.lib_tpLink_comms, line 113
			} catch (err) { // library marker davegut.lib_tpLink_comms, line 114
				logData << [ status: "FAILED", error: "Error decrypting response", data: err] // library marker davegut.lib_tpLink_comms, line 115
			} // library marker davegut.lib_tpLink_comms, line 116
			//	if here, then a valid encrypted response was received. // library marker davegut.lib_tpLink_comms, line 117
			setCommsError(false) // library marker davegut.lib_tpLink_comms, line 118
			logData << [cmdResp : cmdResp] // library marker davegut.lib_tpLink_comms, line 119
			if (cmdResp.error_code == 0) { // library marker davegut.lib_tpLink_comms, line 120
				logData << [status: "OK"] // library marker davegut.lib_tpLink_comms, line 121
			} else { // library marker davegut.lib_tpLink_comms, line 122
				//	The response indicates an error from the device's control code // library marker davegut.lib_tpLink_comms, line 123
				logData << [status: "FAILED", error: "Error in device control response"] // library marker davegut.lib_tpLink_comms, line 124
			} // library marker davegut.lib_tpLink_comms, line 125
		} else { // library marker davegut.lib_tpLink_comms, line 126
			logData << [status: "FAILED", error: "Error in resp.data", respData: respData] // library marker davegut.lib_tpLink_comms, line 127
		} // library marker davegut.lib_tpLink_comms, line 128
	} else { // library marker davegut.lib_tpLink_comms, line 129
		logData << [status: "FAILED", error: "no response data", httpStatus: resp.status] // library marker davegut.lib_tpLink_comms, line 130
	} // library marker davegut.lib_tpLink_comms, line 131
	if (logData.status == "OK") { // library marker davegut.lib_tpLink_comms, line 132
		logDebug("parseData: ${logData}") // library marker davegut.lib_tpLink_comms, line 133
	} else { // library marker davegut.lib_tpLink_comms, line 134
		logWarn("parseData: ${logData}") // library marker davegut.lib_tpLink_comms, line 135
		handleCommsError() // library marker davegut.lib_tpLink_comms, line 136
	} // library marker davegut.lib_tpLink_comms, line 137
	return logData // library marker davegut.lib_tpLink_comms, line 138
} // library marker davegut.lib_tpLink_comms, line 139

def handleCommsError() { // library marker davegut.lib_tpLink_comms, line 141
	Map logData = [:] // library marker davegut.lib_tpLink_comms, line 142
	if (state.lastCommand != "") { // library marker davegut.lib_tpLink_comms, line 143
		def count = state.errorCount + 1 // library marker davegut.lib_tpLink_comms, line 144
		state.errorCount = count // library marker davegut.lib_tpLink_comms, line 145
		def retry = true // library marker davegut.lib_tpLink_comms, line 146
		def cmdData = new JSONObject(state.lastCmd) // library marker davegut.lib_tpLink_comms, line 147
		def cmdBody = parseJson(cmdData.cmdBody.toString()) // library marker davegut.lib_tpLink_comms, line 148
		logData << [count: count, command: cmdData] // library marker davegut.lib_tpLink_comms, line 149
		switch (count) { // library marker davegut.lib_tpLink_comms, line 150
			case 1: // library marker davegut.lib_tpLink_comms, line 151
				securePassthrough(cmdBody, cmdData.async, cmdData.method, cmdData.action) // library marker davegut.lib_tpLink_comms, line 152
				logData << [status: "commandRetry"] // library marker davegut.lib_tpLink_comms, line 153
				logDebug("handleCommsError: ${logData}") // library marker davegut.lib_tpLink_comms, line 154
				break // library marker davegut.lib_tpLink_comms, line 155
			case 2: // library marker davegut.lib_tpLink_comms, line 156
				logData << [deviceLogin: deviceLogin()] // library marker davegut.lib_tpLink_comms, line 157
				Map data = [cmdBody: cmdBody, async: cmdData.async,  // library marker davegut.lib_tpLink_comms, line 158
							method: cmdData.method, action:cmdData.action] // library marker davegut.lib_tpLink_comms, line 159
				runIn(2, delayedPassThrough, [data:data]) // library marker davegut.lib_tpLink_comms, line 160
				logData << [status: "newLogin and commandRetry"] // library marker davegut.lib_tpLink_comms, line 161
				logWarn("handleCommsError: ${logData}") // library marker davegut.lib_tpLink_comms, line 162
				break // library marker davegut.lib_tpLink_comms, line 163
			case 3: // library marker davegut.lib_tpLink_comms, line 164
				logData << [setCommsError: setCommsError(true), status: "retriesDisabled"] // library marker davegut.lib_tpLink_comms, line 165
				logError("handleCommsError: ${logData}") // library marker davegut.lib_tpLink_comms, line 166
				break // library marker davegut.lib_tpLink_comms, line 167
			default: // library marker davegut.lib_tpLink_comms, line 168
				break // library marker davegut.lib_tpLink_comms, line 169
		} // library marker davegut.lib_tpLink_comms, line 170
	} // library marker davegut.lib_tpLink_comms, line 171
} // library marker davegut.lib_tpLink_comms, line 172

def delayedPassThrough(data) { // library marker davegut.lib_tpLink_comms, line 174
	securePassthrough(data.cmdBody, data.async, data.method, data.action) // library marker davegut.lib_tpLink_comms, line 175
} // library marker davegut.lib_tpLink_comms, line 176

def setCommsError(status) { // library marker davegut.lib_tpLink_comms, line 178
	if (!status) { // library marker davegut.lib_tpLink_comms, line 179
		updateAttr("commsError", false) // library marker davegut.lib_tpLink_comms, line 180
		state.errorCount = 0 // library marker davegut.lib_tpLink_comms, line 181
	} else { // library marker davegut.lib_tpLink_comms, line 182
		updateAttr("commsError", true) // library marker davegut.lib_tpLink_comms, line 183
		return "commsErrorSet" // library marker davegut.lib_tpLink_comms, line 184
	} // library marker davegut.lib_tpLink_comms, line 185
} // library marker davegut.lib_tpLink_comms, line 186

// ~~~~~ end include (1327) davegut.lib_tpLink_comms ~~~~~

// ~~~~~ start include (1337) davegut.lib_tpLink_security ~~~~~
library ( // library marker davegut.lib_tpLink_security, line 1
	name: "lib_tpLink_security", // library marker davegut.lib_tpLink_security, line 2
	namespace: "davegut", // library marker davegut.lib_tpLink_security, line 3
	author: "Dave Gutheinz", // library marker davegut.lib_tpLink_security, line 4
	description: "tpLink RSA and AES security measures", // library marker davegut.lib_tpLink_security, line 5
	category: "utilities", // library marker davegut.lib_tpLink_security, line 6
	documentationLink: "" // library marker davegut.lib_tpLink_security, line 7
) // library marker davegut.lib_tpLink_security, line 8
import groovy.json.JsonSlurper // library marker davegut.lib_tpLink_security, line 9
import java.security.spec.PKCS8EncodedKeySpec // library marker davegut.lib_tpLink_security, line 10
import javax.crypto.spec.SecretKeySpec // library marker davegut.lib_tpLink_security, line 11
import javax.crypto.spec.IvParameterSpec // library marker davegut.lib_tpLink_security, line 12
import javax.crypto.Cipher // library marker davegut.lib_tpLink_security, line 13
import java.security.KeyFactory // library marker davegut.lib_tpLink_security, line 14

def securityPreferences() { // library marker davegut.lib_tpLink_security, line 16
	input ("aesKey", "password", title: "Storage for the AES Key") // library marker davegut.lib_tpLink_security, line 17
	input ("deviceCookie", "password", title: "Storage for the cookie") // library marker davegut.lib_tpLink_security, line 18
	input ("deviceToken", "password", title: "Storage for the token") // library marker davegut.lib_tpLink_security, line 19
} // library marker davegut.lib_tpLink_security, line 20

//	===== Device Login Core ===== // library marker davegut.lib_tpLink_security, line 22
def handshake(String devIp) { // library marker davegut.lib_tpLink_security, line 23
	def rsaKeys = getRsaKeys() // library marker davegut.lib_tpLink_security, line 24
	Map handshakeData = [method: "handshakeData", rsaKeys: rsaKeys.keyNo] // library marker davegut.lib_tpLink_security, line 25
	def pubPem = "-----BEGIN PUBLIC KEY-----\n${rsaKeys.public}-----END PUBLIC KEY-----\n" // library marker davegut.lib_tpLink_security, line 26
	Map cmdBody = [ method: "handshake", params: [ key: pubPem]] // library marker davegut.lib_tpLink_security, line 27
	def uri = "http://${devIp}/app" // library marker davegut.lib_tpLink_security, line 28
	def respData = syncPost(uri, cmdBody) // library marker davegut.lib_tpLink_security, line 29
	if (respData.respStatus == "OK") { // library marker davegut.lib_tpLink_security, line 30
		if (respData.data.error_code == 0) { // library marker davegut.lib_tpLink_security, line 31
			String deviceKey = respData.data.result.key // library marker davegut.lib_tpLink_security, line 32
			try { // library marker davegut.lib_tpLink_security, line 33
				def cookieHeader = respData.headers["set-cookie"].toString() // library marker davegut.lib_tpLink_security, line 34
				def cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker davegut.lib_tpLink_security, line 35
				handshakeData << [cookie: cookie] // library marker davegut.lib_tpLink_security, line 36
			} catch (err) { // library marker davegut.lib_tpLink_security, line 37
				handshakeData << [respStatus: "FAILED", check: "respDat.headers", error: err] // library marker davegut.lib_tpLink_security, line 38
			} // library marker davegut.lib_tpLink_security, line 39
			def aesArray = readDeviceKey(deviceKey, rsaKeys.private) // library marker davegut.lib_tpLink_security, line 40
			handshakeData << [aesKey: aesArray] // library marker davegut.lib_tpLink_security, line 41
			if (aesArray == "ERROR") { // library marker davegut.lib_tpLink_security, line 42
				handshakeData << [respStatus: "FAILED", check: "privateKey"] // library marker davegut.lib_tpLink_security, line 43
			} else { // library marker davegut.lib_tpLink_security, line 44
				handshakeData << [respStatus: "OK"] // library marker davegut.lib_tpLink_security, line 45
			} // library marker davegut.lib_tpLink_security, line 46
		} else { // library marker davegut.lib_tpLink_security, line 47
			handshakeData << [respStatus: "FAILED", data: respData.data] // library marker davegut.lib_tpLink_security, line 48
		} // library marker davegut.lib_tpLink_security, line 49
	} else { // library marker davegut.lib_tpLink_security, line 50
		handshakeData << [respStatus: "FAILED", check: "pubPem. devIp", respData: respData] // library marker davegut.lib_tpLink_security, line 51
	} // library marker davegut.lib_tpLink_security, line 52
	if (handshakeData.respStatus == "OK") { // library marker davegut.lib_tpLink_security, line 53
		logDebug("handshake: ${handshakeData}") // library marker davegut.lib_tpLink_security, line 54
	} else { // library marker davegut.lib_tpLink_security, line 55
		logWarn("handshake: ${handshakeData}") // library marker davegut.lib_tpLink_security, line 56
	} // library marker davegut.lib_tpLink_security, line 57
	return handshakeData // library marker davegut.lib_tpLink_security, line 58
} // library marker davegut.lib_tpLink_security, line 59

def readDeviceKey(deviceKey, privateKey) { // library marker davegut.lib_tpLink_security, line 61
	def response = "ERROR" // library marker davegut.lib_tpLink_security, line 62
	def logData = [:] // library marker davegut.lib_tpLink_security, line 63
	try { // library marker davegut.lib_tpLink_security, line 64
		byte[] privateKeyBytes = privateKey.decodeBase64() // library marker davegut.lib_tpLink_security, line 65
		byte[] deviceKeyBytes = deviceKey.getBytes("UTF-8").decodeBase64() // library marker davegut.lib_tpLink_security, line 66
    	Cipher instance = Cipher.getInstance("RSA/ECB/PKCS1Padding") // library marker davegut.lib_tpLink_security, line 67
		instance.init(2, KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes))) // library marker davegut.lib_tpLink_security, line 68
		byte[] cryptoArray = instance.doFinal(deviceKeyBytes) // library marker davegut.lib_tpLink_security, line 69
		response = cryptoArray // library marker davegut.lib_tpLink_security, line 70
		logData << [cryptoArray: "REDACTED for logs", status: "OK"] // library marker davegut.lib_tpLink_security, line 71
		logDebug("readDeviceKey: ${logData}") // library marker davegut.lib_tpLink_security, line 72
	} catch (err) { // library marker davegut.lib_tpLink_security, line 73
		logData << [status: "READ ERROR", data: err] // library marker davegut.lib_tpLink_security, line 74
		logWarn("readDeviceKey: ${logData}") // library marker davegut.lib_tpLink_security, line 75
	} // library marker davegut.lib_tpLink_security, line 76
	return response // library marker davegut.lib_tpLink_security, line 77
} // library marker davegut.lib_tpLink_security, line 78

def loginDevice(cookie, cryptoArray, credentials, devIp) { // library marker davegut.lib_tpLink_security, line 80
	Map tokenData = [method: "loginDevice"] // library marker davegut.lib_tpLink_security, line 81
	def uri = "http://${devIp}/app" // library marker davegut.lib_tpLink_security, line 82
	String cmdBody = """{"method": "login_device", "params": {"password": "${credentials.encPassword}", "username": "${credentials.encUsername}"}, "requestTimeMils": 0}""" // library marker davegut.lib_tpLink_security, line 83
	Map reqBody = [method: "securePassthrough", params: [request: encrypt(cmdBody, cryptoArray)]] // library marker davegut.lib_tpLink_security, line 84
	def respData = syncPost(uri, reqBody, cookie) // library marker davegut.lib_tpLink_security, line 85
	if (respData.respStatus == "OK") { // library marker davegut.lib_tpLink_security, line 86
		if (respData.data.error_code == 0) { // library marker davegut.lib_tpLink_security, line 87
			try { // library marker davegut.lib_tpLink_security, line 88
				def cmdResp = decrypt(respData.data.result.response, cryptoArray) // library marker davegut.lib_tpLink_security, line 89
				cmdResp = new JsonSlurper().parseText(cmdResp) // library marker davegut.lib_tpLink_security, line 90
				if (cmdResp.error_code == 0) { // library marker davegut.lib_tpLink_security, line 91
					tokenData << [respStatus: "OK", token: cmdResp.result.token] // library marker davegut.lib_tpLink_security, line 92
				} else { // library marker davegut.lib_tpLink_security, line 93
					tokenData << [respStatus: "Error from device",  // library marker davegut.lib_tpLink_security, line 94
								  check: "cryptoArray, credentials", data: cmdResp] // library marker davegut.lib_tpLink_security, line 95
				} // library marker davegut.lib_tpLink_security, line 96
			} catch (err) { // library marker davegut.lib_tpLink_security, line 97
				tokenData << [respStatus: "Error parsing", error: err] // library marker davegut.lib_tpLink_security, line 98
			} // library marker davegut.lib_tpLink_security, line 99
		} else { // library marker davegut.lib_tpLink_security, line 100
			tokenData << [respStatus: "Error in respData.data", data: respData.data] // library marker davegut.lib_tpLink_security, line 101
		} // library marker davegut.lib_tpLink_security, line 102
	} else { // library marker davegut.lib_tpLink_security, line 103
		tokenData << [respStatus: "Error in respData", data: respData] // library marker davegut.lib_tpLink_security, line 104
	} // library marker davegut.lib_tpLink_security, line 105
	if (tokenData.respStatus == "OK") { // library marker davegut.lib_tpLink_security, line 106
		logDebug("handshake: ${tokenData}") // library marker davegut.lib_tpLink_security, line 107
	} else { // library marker davegut.lib_tpLink_security, line 108
		logWarn("handshake: ${tokenData}") // library marker davegut.lib_tpLink_security, line 109
	} // library marker davegut.lib_tpLink_security, line 110
	return tokenData // library marker davegut.lib_tpLink_security, line 111
} // library marker davegut.lib_tpLink_security, line 112

//	===== AES Methods ===== // library marker davegut.lib_tpLink_security, line 114
def encrypt(plainText, keyData) { // library marker davegut.lib_tpLink_security, line 115
	byte[] keyenc = keyData[0..15] // library marker davegut.lib_tpLink_security, line 116
	byte[] ivenc = keyData[16..31] // library marker davegut.lib_tpLink_security, line 117

	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.lib_tpLink_security, line 119
	SecretKeySpec key = new SecretKeySpec(keyenc, "AES") // library marker davegut.lib_tpLink_security, line 120
	IvParameterSpec iv = new IvParameterSpec(ivenc) // library marker davegut.lib_tpLink_security, line 121
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker davegut.lib_tpLink_security, line 122
	String result = cipher.doFinal(plainText.getBytes("UTF-8")).encodeBase64().toString() // library marker davegut.lib_tpLink_security, line 123
	return result.replace("\r\n","") // library marker davegut.lib_tpLink_security, line 124
} // library marker davegut.lib_tpLink_security, line 125

def decrypt(cypherText, keyData) { // library marker davegut.lib_tpLink_security, line 127
	byte[] keyenc = keyData[0..15] // library marker davegut.lib_tpLink_security, line 128
	byte[] ivenc = keyData[16..31] // library marker davegut.lib_tpLink_security, line 129

    byte[] decodedBytes = cypherText.decodeBase64() // library marker davegut.lib_tpLink_security, line 131
    def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker davegut.lib_tpLink_security, line 132
    SecretKeySpec key = new SecretKeySpec(keyenc, "AES") // library marker davegut.lib_tpLink_security, line 133
    cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(ivenc)) // library marker davegut.lib_tpLink_security, line 134
	String result = new String(cipher.doFinal(decodedBytes), "UTF-8") // library marker davegut.lib_tpLink_security, line 135
	return result // library marker davegut.lib_tpLink_security, line 136
} // library marker davegut.lib_tpLink_security, line 137

//	===== RSA Key Methods ===== // library marker davegut.lib_tpLink_security, line 139
def getRsaKeys() { // library marker davegut.lib_tpLink_security, line 140
	def keyNo = Math.round(5 * Math.random()).toInteger() // library marker davegut.lib_tpLink_security, line 141
	def keyData = keyData() // library marker davegut.lib_tpLink_security, line 142
	def RSAKeys = keyData.find { it.keyNo == keyNo } // library marker davegut.lib_tpLink_security, line 143
	return RSAKeys // library marker davegut.lib_tpLink_security, line 144
} // library marker davegut.lib_tpLink_security, line 145

def keyData() { // library marker davegut.lib_tpLink_security, line 147
/*	User Note.  You can update these keys at you will using the site: // library marker davegut.lib_tpLink_security, line 148
		https://www.devglan.com/online-tools/rsa-encryption-decryption // library marker davegut.lib_tpLink_security, line 149
	with an RSA Key Size: 1024 bit // library marker davegut.lib_tpLink_security, line 150
	This is at your risk.*/ // library marker davegut.lib_tpLink_security, line 151
	return [ // library marker davegut.lib_tpLink_security, line 152
		[ // library marker davegut.lib_tpLink_security, line 153
			keyNo: 0, // library marker davegut.lib_tpLink_security, line 154
			public: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDGr/mHBK8aqx7UAS+g+TuAvE3J2DdwsqRn9MmAkjPGNon1ZlwM6nLQHfJHebdohyVqkNWaCECGXnftnlC8CM2c/RujvCrStRA0lVD+jixO9QJ9PcYTa07Z1FuEze7Q5OIa6pEoPxomrjxzVlUWLDXt901qCdn3/zRZpBdpXzVZtQIDAQAB", // library marker davegut.lib_tpLink_security, line 155
			private: "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAMav+YcErxqrHtQBL6D5O4C8TcnYN3CypGf0yYCSM8Y2ifVmXAzqctAd8kd5t2iHJWqQ1ZoIQIZed+2eULwIzZz9G6O8KtK1EDSVUP6OLE71An09xhNrTtnUW4TN7tDk4hrqkSg/GiauPHNWVRYsNe33TWoJ2ff/NFmkF2lfNVm1AgMBAAECgYEAocxCHmKBGe2KAEkq+SKdAxvVGO77TsobOhDMWug0Q1C8jduaUGZHsxT/7JbA9d1AagSh/XqE2Sdq8FUBF+7vSFzozBHyGkrX1iKURpQFEQM2j9JgUCucEavnxvCqDYpscyNRAgqz9jdh+BjEMcKAG7o68bOw41ZC+JyYR41xSe0CQQD1os71NcZiMVqYcBud6fTYFHZz3HBNcbzOk+RpIHyi8aF3zIqPKIAh2pO4s7vJgrMZTc2wkIe0ZnUrm0oaC//jAkEAzxIPW1mWd3+KE3gpgyX0cFkZsDmlIbWojUIbyz8NgeUglr+BczARG4ITrTV4fxkGwNI4EZxBT8vXDSIXJ8NDhwJBAIiKndx0rfg7Uw7VkqRvPqk2hrnU2aBTDw8N6rP9WQsCoi0DyCnX65Hl/KN5VXOocYIpW6NAVA8VvSAmTES6Ut0CQQCX20jD13mPfUsHaDIZafZPhiheoofFpvFLVtYHQeBoCF7T7vHCRdfl8oj3l6UcoH/hXMmdsJf9KyI1EXElyf91AkAvLfmAS2UvUnhX4qyFioitjxwWawSnf+CewN8LDbH7m5JVXJEh3hqp+aLHg1EaW4wJtkoKLCF+DeVIgbSvOLJw" // library marker davegut.lib_tpLink_security, line 156
		],[ // library marker davegut.lib_tpLink_security, line 157
			keyNo: 1, // library marker davegut.lib_tpLink_security, line 158
			public: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCshy+qBKbJNefcyJUZ/3i+3KyLji6XaWEWvebUCC2r9/0jE6hc89AufO41a13E3gJ2es732vaxwZ1BZKLy468NnL+tg6vlQXaPkDcdunQwjxbTLNL/yzDZs9HRju2lJnupcksdJWBZmjtztMWQkzBrQVeSKzSTrKYK0s24EEXmtQIDAQAB", // library marker davegut.lib_tpLink_security, line 159
			private: "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAKyHL6oEpsk159zIlRn/eL7crIuOLpdpYRa95tQILav3/SMTqFzz0C587jVrXcTeAnZ6zvfa9rHBnUFkovLjrw2cv62Dq+VBdo+QNx26dDCPFtMs0v/LMNmz0dGO7aUme6lySx0lYFmaO3O0xZCTMGtBV5IrNJOspgrSzbgQRea1AgMBAAECgYBSeiX9H1AkbJK1Z2ZwEUNF6vTJmmUHmScC2jHZNzeuOFVZSXJ5TU0+jBbMjtE65e9DeJ4suw6oF6j3tAZ6GwJ5tHoIy+qHRV6AjA8GEXjhSwwVCyP8jXYZ7UZyHzjLQAK+L0PvwJY1lAtns/Xmk5GH+zpNnhEmKSZAw23f7wpj2QJBANVPQGYT7TsMTDEEl2jq/ZgOX5Djf2VnKpPZYZGsUmg1hMwcpN/4XQ7XOaclR5TO/CJBJl3UCUEVjdrR1zdD8g8CQQDPDoa5Y5UfhLz4Ja2/gs2UKwO4fkTqqR6Ad8fQlaUZ55HINHWFd8FeERBFgNJzszrzd9BBJ7NnZM5nf2OPqU77AkBLuQuScSZ5HL97czbQvwLxVMDmLWyPMdVykOvLC9JhPgZ7cvuwqnlWiF7mEBzeHbBx9JDLJDd4zE8ETBPLgapPAkAHhCR52FaSdVQSwfNjr1DdHw6chODlj8wOp8p2FOiQXyqYlObrOGSpkH8BtuJs1sW+DsxdgR5vE2a2tRYdIe0/AkEAoQ5MzLcETQrmabdVCyB9pQAiHe4yY9e1w7cimsLJOrH7LMM0hqvBqFOIbSPrZyTp7Ie8awn4nTKoZQtvBfwzHw==" // library marker davegut.lib_tpLink_security, line 160
		],[ // library marker davegut.lib_tpLink_security, line 161
			keyNo: 2, // library marker davegut.lib_tpLink_security, line 162
			public: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCBeqRy4zAOs63Sc5yc0DtlFXG1stmdD6sEfUiGjlsy0S8aS8X+Qcjcu5AK3uBBrkVNIa8djXht1bd+pUof5/txzWIMJw9SNtNYqzSdeO7cCtRLzuQnQWP7Am64OBvYkXn2sUqoaqDE50LbSQWbuvZw0Vi9QihfBYGQdlrqjCPUsQIDAQAB", // library marker davegut.lib_tpLink_security, line 163
			private: "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAIF6pHLjMA6zrdJznJzQO2UVcbWy2Z0PqwR9SIaOWzLRLxpLxf5ByNy7kAre4EGuRU0hrx2NeG3Vt36lSh/n+3HNYgwnD1I201irNJ147twK1EvO5CdBY/sCbrg4G9iRefaxSqhqoMTnQttJBZu69nDRWL1CKF8FgZB2WuqMI9SxAgMBAAECgYBBi2wkHI3/Y0Xi+1OUrnTivvBJIri2oW/ZXfKQ6w+PsgU+Mo2QII0l8G0Ck8DCfw3l9d9H/o2wTDgPjGzxqeXHAbxET1dS0QBTjR1zLZlFyfAs7WO8tDKmHVroUgqRkJgoQNQlBSe1E3e7pTgSKElzLuALkRS6p1jhzT2wu9U04QJBAOFr/G36PbQ6NmDYtVyEEr3vWn46JHeZISdJOsordR7Wzbt6xk6/zUDHq0OGM9rYrpBy7PNrbc0JuQrhfbIyaHMCQQCTCvETjXCMkwyUrQT6TpxVzKEVRf1rCitnNQCh1TLnDKcCEAnqZT2RRS3yNXTWFoJrtuEHMGmwUrtog9+ZJBlLAkEA2qxdkPY621XJIIO404mPgM7rMx4F+DsE7U5diHdFw2fO5brBGu13GAtZuUQ7k2W1WY0TDUO+nTN8XPDHdZDuvwJABu7TIwreLaKZS0FFJNAkCt+VEL22Dx/xn/Idz4OP3Nj53t0Guqh/WKQcYHkowxdYmt+KiJ49vXSJJYpiNoQ/NQJAM1HCl8hBznLZLQlxrCTdMvUimG3kJmA0bUNVncgUBq7ptqjk7lp5iNrle5aml99foYnzZeEUW6jrCC7Lj9tg+w==" // library marker davegut.lib_tpLink_security, line 164
		],[ // library marker davegut.lib_tpLink_security, line 165
			keyNo: 3, // library marker davegut.lib_tpLink_security, line 166
			public: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCFYaoMvv5kBxUUbp4PQyd7RoZlPompsupXP2La0qGGxacF98/88W4KNUqLbF4X5BPqxoEA+VeZy75qqyfuYbGQ4fxT6usE/LnzW8zDY/PjhVBht8FBRyAUsoYAt3Ip6sDyjd9YzRzUL1Q/OxCgxz5CNETYxcNr7zfMshBHDmZXMQIDAQAB", // library marker davegut.lib_tpLink_security, line 167
			private: "MIICdQIBADANBgkqhkiG9w0BAQEFAASCAl8wggJbAgEAAoGBAIVhqgy+/mQHFRRung9DJ3tGhmU+iamy6lc/YtrSoYbFpwX3z/zxbgo1SotsXhfkE+rGgQD5V5nLvmqrJ+5hsZDh/FPq6wT8ufNbzMNj8+OFUGG3wUFHIBSyhgC3cinqwPKN31jNHNQvVD87EKDHPkI0RNjFw2vvN8yyEEcOZlcxAgMBAAECgYA3NxjoMeCpk+z8ClbQRqJ/e9CC9QKUB4bPG2RW5b8MRaJA7DdjpKZC/5CeavwAs+Ay3n3k41OKTTfEfJoJKtQQZnCrqnZfq9IVZI26xfYo0cgSYbi8wCie6nqIBdu9k54nqhePPshi22VcFuOh97xxPvY7kiUaRbbKqxn9PFwrYQJBAMsO3uOnYSJxN/FuxksKLqhtNei2GUC/0l7uIE8rbRdtN3QOpcC5suj7id03/IMn2Ks+Vsrmi0lV4VV/c8xyo9UCQQCoKDlObjbYeYYdW7/NvI6cEntgHygENi7b6WFk+dbRhJQgrFH8Z/Idj9a2E3BkfLCTUM1Z/Z3e7D0iqPDKBn/tAkBAHI3bKvnMOhsDq4oIH0rj+rdOplAK1YXCW0TwOjHTd7ROfGFxHDCUxvacVhTwBCCw0JnuriPEH81phTg2kOuRAkAEPR9UrsqLImUTEGEBWqNto7mgbqifko4T1QozdWjI10K0oCNg7W3Y+Os8o7jNj6cTz5GdlxsHp4TS/tczAH7xAkBY6KPIlF1FfiyJAnBC8+jJr2h4TSPQD7sbJJmYw7mvR+f1T4tsWY0aGux69hVm8BoaLStBVPdkaENBMdP+a07u" // library marker davegut.lib_tpLink_security, line 168
		],[ // library marker davegut.lib_tpLink_security, line 169
			keyNo: 4, // library marker davegut.lib_tpLink_security, line 170
			public: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQClF0yuCpo3r1ZpYlGcyI5wy5nnvZdOZmxqz5U2rklt2b8+9uWhmsGdpbTv5+qJXlZmvUKbpoaPxpJluBFDJH2GSpq3I0whh0gNq9Arzpp/TDYaZLb6iIqDMF6wm8yjGOtcSkB7qLQWkXpEN9T2NsEzlfTc+GTKc07QXHnzxoLmwQIDAQAB", // library marker davegut.lib_tpLink_security, line 171
			private: "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBAKUXTK4KmjevVmliUZzIjnDLmee9l05mbGrPlTauSW3Zvz725aGawZ2ltO/n6oleVma9Qpumho/GkmW4EUMkfYZKmrcjTCGHSA2r0CvOmn9MNhpktvqIioMwXrCbzKMY61xKQHuotBaRekQ31PY2wTOV9Nz4ZMpzTtBcefPGgubBAgMBAAECgYB4wCz+05RvDFk45YfqFCtTRyg//0UvO+0qxsBN6Xad2XlvlWjqJeZd53kLTGcYqJ6rsNyKOmgLu2MS8Wn24TbJmPUAwZU+9cvSPxxQ5k6bwjg1RifieIcbTPC5wHDqVy0/Ur7dt+JVMOHFseR/pElDw471LCdwWSuFHAKuiHsaUQJBANHiPdSU3s1bbJYTLaS1tW0UXo7aqgeXuJgqZ2sKsoIEheEAROJ5rW/f2KrFVtvg0ITSM8mgXNlhNBS5OE4nSD0CQQDJXYJxKvdodeRoj+RGTCZGZanAE1naUzSdfcNWx2IMnYUD/3/2eB7ZIyQPBG5fWjc3bGOJKI+gy/14bCwXU7zVAkAdnsE9HBlpf+qOL3y0jxRgpYxGuuNeGPJrPyjDOYpBwSOnwmL2V1e7vyqTxy/f7hVfeU7nuKMB5q7z8cPZe7+9AkEAl7A6aDe+wlE069OhWZdZqeRBmLC7Gi1d0FoBwahW4zvyDM32vltEmbvQGQP0hR33xGeBH7yPXcjtOz75g+UPtQJBAL4gknJ/p+yQm9RJB0oq/g+HriErpIMHwrhNoRY1aOBMJVl4ari1Ch2RQNL9KQW7yrFDv7XiP3z5NwNDKsp/QeU=" // library marker davegut.lib_tpLink_security, line 172
		],[ // library marker davegut.lib_tpLink_security, line 173
			keyNo: 5, // library marker davegut.lib_tpLink_security, line 174
			public: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQChN8Xc+gsSuhcLVM1W1E+e1o+celvKlOmuV6sJEkJecknKFujx9+T4xvyapzyePpTBn0lA9EYbaF7UDYBsDgqSwgt0El3gV+49O56nt1ELbLUJtkYEQPK+6Pu8665UG17leCiaMiFQyoZhD80PXhpjehqDu2900uU/4DzKZ/eywwIDAQAB", // library marker davegut.lib_tpLink_security, line 175
			private: "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAKE3xdz6CxK6FwtUzVbUT57Wj5x6W8qU6a5XqwkSQl5yScoW6PH35PjG/JqnPJ4+lMGfSUD0RhtoXtQNgGwOCpLCC3QSXeBX7j07nqe3UQtstQm2RgRA8r7o+7zrrlQbXuV4KJoyIVDKhmEPzQ9eGmN6GoO7b3TS5T/gPMpn97LDAgMBAAECgYAy+uQCwL8HqPjoiGR2dKTI4aiAHuEv6m8KxoY7VB7QputWkHARNAaf9KykawXsNHXt1GThuV0CBbsW6z4U7UvCJEZEpv7qJiGX8UWgEs1ISatqXmiIMVosIJJvoFw/rAoScadCYyicskjwDFBVNU53EAUD3WzwEq+dRYDn52lqQQJBAMu30FEReAHTAKE/hvjAeBUyWjg7E4/lnYvb/i9Wuc+MTH0q3JxFGGMb3n6APT9+kbGE0rinM/GEXtpny+5y3asCQQDKl7eNq0NdIEBGAdKerX4O+nVDZ7PXz1kQ2ca0r1tXtY/9sBDDoKHP2fQAH/xlOLIhLaH1rabSEJYNUM0ohHdJAkBYZqhwNWtlJ0ITtvSEB0lUsWfzFLe1bseCBHH16uVwygn7GtlmupkNkO9o548seWkRpnimhnAE8xMSJY6aJ6BHAkEAuSFLKrqGJGOEWHTx8u63cxiMb7wkK+HekfdwDUzxO4U+v6RUrW/sbfPNdQ/FpPnaTVdV2RuGhg+CD0j3MT9bgQJARH86hfxp1bkyc7f1iJQT8sofdqqVz5grCV5XeGY77BNmCvTOGLfL5pOJdgALuOoP4t3e94nRYdlW6LqIVugRBQ==" // library marker davegut.lib_tpLink_security, line 176
		] // library marker davegut.lib_tpLink_security, line 177
	] // library marker davegut.lib_tpLink_security, line 178
} // library marker davegut.lib_tpLink_security, line 179

// ~~~~~ end include (1337) davegut.lib_tpLink_security ~~~~~

// ~~~~~ start include (1339) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8

preferences { // library marker davegut.Logging, line 10
	input ("logEnable", "bool",  title: "Enable debug logging for 30 minutes", defaultValue: false) // library marker davegut.Logging, line 11
	input ("infoLog", "bool", title: "Enable information logging",defaultValue: true) // library marker davegut.Logging, line 12
	input ("traceLog", "bool", title: "Enable trace logging as directed by developer", defaultValue: false) // library marker davegut.Logging, line 13
} // library marker davegut.Logging, line 14

def setLogsOff() { // library marker davegut.Logging, line 16
	def logData = [logEnagle: logEnable, infoLog: infoLog, traceLog:traceLog] // library marker davegut.Logging, line 17
	if (logEnable) { // library marker davegut.Logging, line 18
		runIn(1800, debugLogOff) // library marker davegut.Logging, line 19
		logData << [debugLogOff: "scheduled"] // library marker davegut.Logging, line 20
	} // library marker davegut.Logging, line 21
	if (traceLog) { // library marker davegut.Logging, line 22
		runIn(1800, traceLogOff) // library marker davegut.Logging, line 23
		logData << [traceLogOff: "scheduled"] // library marker davegut.Logging, line 24
	} // library marker davegut.Logging, line 25
	return logData // library marker davegut.Logging, line 26
} // library marker davegut.Logging, line 27

def logTrace(msg){ // library marker davegut.Logging, line 29
	if (traceLog == true) { // library marker davegut.Logging, line 30
		log.trace "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.Logging, line 31
	} // library marker davegut.Logging, line 32
} // library marker davegut.Logging, line 33

def traceLogOff() { // library marker davegut.Logging, line 35
	device.updateSetting("traceLog", [type:"bool", value: false]) // library marker davegut.Logging, line 36
	logInfo("traceLogOff") // library marker davegut.Logging, line 37
} // library marker davegut.Logging, line 38

def logInfo(msg) {  // library marker davegut.Logging, line 40
	if (textEnable || infoLog) { // library marker davegut.Logging, line 41
		log.info "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.Logging, line 42
	} // library marker davegut.Logging, line 43
} // library marker davegut.Logging, line 44

def debugLogOff() { // library marker davegut.Logging, line 46
	device.updateSetting("logEnable", [type:"bool", value: false]) // library marker davegut.Logging, line 47
	logInfo("debugLogOff") // library marker davegut.Logging, line 48
} // library marker davegut.Logging, line 49

def logDebug(msg) { // library marker davegut.Logging, line 51
	if (logEnable || debugLog) { // library marker davegut.Logging, line 52
		log.debug "${device.displayName}-${driverVer()}: ${msg}" // library marker davegut.Logging, line 53
	} // library marker davegut.Logging, line 54
} // library marker davegut.Logging, line 55

def logWarn(msg) { log.warn "${device.displayName}-${driverVer()}: ${msg}" } // library marker davegut.Logging, line 57

def logError(msg) { log.error "${device.displayName}-${driverVer()}: ${msg}" } // library marker davegut.Logging, line 59

// ~~~~~ end include (1339) davegut.Logging ~~~~~
