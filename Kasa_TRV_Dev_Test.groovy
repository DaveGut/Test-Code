/*	Kasa Dev Test
		Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===================================================================================================*/
import groovy.json.JsonSlurper
def driverVer() { return "TEST" }

def type() { return "TRV Test" }
def file() { return type().replaceAll(" ", "-") }

metadata {
	definition (name: "Kasa ${type()}",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: ""
			   ) {
	}
	command "getGeneralData"
	command "getDeviceData"
	command "getToken"
	preferences {
		input ("deviceIp", "string", title: "Test device LAN IP Address", defaultValue: "notSet")
		input ("userName", "email", title: "Kasa Email Address", defaultValue: "notSet")
		input ("userPwd", "password", title: "Kasa Account Password", defaultValue: "notSet")
		input ("deviceId", "string", title: "Device ID Copied from LOGS", defaultValue: "notSet")
	}
}

def getInstrs() {
	def instructions = "<b>Instructions:</b> In the Preferences section:  "
	instructions += "<b>a.</b>  Enter the devices Local Area Network IP (i.e., 192.168.50.22).  "
	instructions += "<b>b.</b>  DISABLE two factor authentication in the Kasa App (if enabled).  "
	instructions += "<b>c.</b>  Enter you Kasa userName (E-Mail address)."
	Instructions += "<b>d.</b>  Enter your Kasa account password."
	Instructions += "<b>e.</b>  Do a Save Preferences."
	Instructions += "<b>f.</b>  Run the command getGeneralData"
	Instructions += "<b>g.</b>  Send logs from this command to developer"
	return instructions
}

def getDevInstrs() {
	def ins =  "<b>Instructions for Device ID:</b>  "
	ins += "<b>a.</b>  Copy the deviceId for desired device from the log page.  "
	ins += "<b>b.</b>  Past into Preferene deviceId.  "
	ins += "<b>c.</b>  Do a Save Preferences.  "
	ins += "<b>d.</b>  run the command getDeviceData  "
	ins += "<b>e.</b>  Send logs from this command to developer."
	return ins
}

def installed() { runIn(1, updated) }

def updated() {
	if (deviceIp == "notSet" || userName  == "notSet" || userPwd == "notSet") {
		logWarn("updated: [prefNotSet: [deviceIp: ${deviceIp}, userName: ${userName}, userPwd: ${userPwd}]")
		logWarn(getInstrs())
	} else {
		if (deviceId == "notSet") {
			logWarn("updated: Need to set device Id")
			logWarn(getDevInstrs())
		}
	}
	logInfo("updated complete")
}

def sysinfo() { return """{"system":{"get_sysinfo":{}}}""" }

//	=====	General Data	=====
def getGeneralData() {
	if (!getDataValue("token")) {
		getToken()
		runIn(10, getCloudDevices)
	} else {
		getCloudDevices()
	}
}

def getToken() {
	def message = [user: userName]
	def termId = java.util.UUID.randomUUID()
	def cmdBody = [
		method: "login",
		params: [
			appType: "Kasa_Android",
			cloudUserName: "${userName}",
			cloudPassword: "${userPwd.replaceAll('&gt;', '>').replaceAll('&lt;','<')}",
			terminalUUID: "${termId}"]]
	cmdData = [uri: "https://wap.tplinkcloud.com",
			   cmdBody: cmdBody]
	def respData = sendKasaCmd(cmdData)
	if (respData.error_code == 0) {
		updateDataValue("token", respData.result.token)
		message << [newToken: respData.result.token]
	} else {
		message << [updateFailed: respData]
	}
	logTrace("getToken: ${message}]")
}

def getCloudDevices(type = "allDevices") {
	def message = [token: getDataValue("token")]
	def cmdData = [uri: "https://wap.tplinkcloud.com?token=${getDataValue("token")}", 
				   cmdBody: [method: "getDeviceList"]]
	def respData = sendKasaCmd(cmdData)
	if (respData.error_code == 0) {
		def cloudDevices = respData.result.deviceList
		if (!getDataValue("cloudUrl")) {
			def cloudUrl = cloudDevices[0].appServerUrl
			updateDataValue("cloudUrl", cloudUrl)
			message << [cloudUrl: cloudUrl]
		}
		message << [cloudDevices: "getting devices", type: type]
		if (type == "allDevices") {
			runIn(5, getGeneralDeviceData, [data: cloudDevices])
		} else {
			runIn(5, getSpecificDeviceData, [data: cloudDevices])
		}
	} else {
		message << [getCloudDevices: "Devices not returned from Kasa Cloud"]
		logWarn("getCloudDevices: <b>Devices not returned from Kasa Cloud.</b> Return = ${respData}\n\r")
	}
	logTrace("getCloudDevices: ${message}")
}

def getGeneralDeviceData(cloudDevices) {
	cloudDevices.each {
		def devData = [alias: it.alias, name: it.deviceName, deviceId: "<b>${it.deviceId}</b>"]
		logDebug(devData)
	}
}

def sendKasaCmd(cmdData) {
	def logData = [:]
	def commandParams = [
		uri: cmdData.uri,
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01'],
		body : new groovy.json.JsonBuilder(cmdData.cmdBody).toString()
	]
	logData << [commandParams: commandParams]
	def respData
	try {
		httpPostJson(commandParams) {resp ->
			logData << [respStatus: resp.status]
			if (resp.status == 200) {
				respData = resp.data
			} else {
				logData << [error: "HTTP Protocol Error"]
				respData = [error_code: resp.status, msg: "HTTP Protocol Error"]
			}
		}
	} catch (e) {
		logData << [error: "cloudUnreachable", data: e]
		respData = [error_code: 9999, msg: e]
	}
	logTrace("sendKasaCmd: ${logData}")
	return respData
}

//	=====	Selected Device Data	=====
def getDeviceData() {
	sendLanCmd()
	runIn(10, getCloudDevices, [data: deviceId])
	runIn(20, getDeviceCloudData)
}

def sendLanCmd() {
	def message  = [ip: deviceIp, cmd: sysinfo()]
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(sysinfo()),
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${deviceIp}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 parseWarning: true,
		 timeout: 9,
		 ignoreResponse: false,
		 callback: "parseUdp"])
	try {
		sendHubCommand(myHubAction)
		message << [sendHubCommand: "success"]
	} catch (e) {
		message << [sendHubCommand: "error", lanError: e]
		logWarn("sendLanCmd: LAN Error = ${e}.\n\rNo retry on this error.")
	}
	logTrace("sendLanCmd: ${message}")
}

def parseUdp(message) {
	def logData = [:]
	def resp = parseLanMessage(message)
	if (resp.type == "LAN_TYPE_UDPCLIENT") {
		def clearResp = inputXOR(resp.payload)
		logData << [clearResp: clearResp]
	} else {
		logData << [error: "notLAN_TYPE_UDPCLIENT", respType: resp.type]
	}
	logDebug("deviceLanData: ${logData}")
}

private outputXOR(command) {
	def str = ""
	def encrCmd = ""
 	def key = 0xAB
	for (int i = 0; i < command.length(); i++) {
		str = (command.charAt(i) as byte) ^ key
		key = str
		encrCmd += Integer.toHexString(str)
	}
   	return encrCmd
}

private inputXOR(encrResponse) {
	String[] strBytes = encrResponse.split("(?<=\\G.{2})")
	def cmdResponse = ""
	def key = 0xAB
	def nextKey
	byte[] XORtemp
	for(int i = 0; i < strBytes.length; i++) {
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative
		XORtemp = nextKey ^ key
		key = nextKey
		cmdResponse += new String(XORtemp)
	}
	return cmdResponse
}

def getSpecificDeviceData(cloudDevices) {
	cloudDevices.each {
		if (it.deviceId == deviceId) {
			logDebug("deviceKasaData: ${it}")
		}
	}
}

def getDeviceCloudData() {
	def logData = [:]
	def cmdBody = [
		method: "passthrough",
		params: [
			deviceId: deviceId,
			requestData: "${sysinfo()}"
		]
	]
	if (!getDataValue("cloudUrl") || !getDataValue("token")) {
		logWarn("sendKasaCmd: Cloud interface not properly set up.")
		return
	}
	def sendCloudCmdParams = [
		uri: "${getDataValue("cloudUrl")}/?token=${getDataValue("token")}",
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01'],
		timeout: 10,
		body : new groovy.json.JsonBuilder(cmdBody).toString()
	]
	logData << [commandParams: sendCloudCmdParams]
	try {
		asynchttpPost("cloudParse", sendCloudCmdParams)
	} catch (e) {
		logData << [error: "cloudUnreachable", data: e]
	}
	logTrace("getDeviceCloudData: ${logData}")
}

def cloudParse(resp, data = null) {
	def logData = [:]
	try {
		response = new JsonSlurper().parseText(resp.data)
	} catch (e) {
		response = [error_code: 9999, data: e]
	}
	if (resp.status == 200 && response.error_code == 0 && resp != []) {
		def cmdResp = new JsonSlurper().parseText(response.result.responseData)
		logData << [cmdResp: cmdResp]
	} else {
		logData << [error: "Error from the Kasa Cloud", data: resp.data]
	}
	logDebug("deviceCloudData: ${logData}")
}

//	logging
def logTrace(msg){ log.trace "${device.displayName}: ${msg}" }
def logInfo(msg) { log.info "${device.displayName}: ${msg}" }
def logDebug(msg) { log.debug "${device.displayName}: ${msg}" }
def logWarn(msg) { log.warn "${device.displayName}: ${msg}" }
