/*	Kasa Local Integration
Copyright Dave Gutheinz
=======================================================================================================*/
import groovy.json.JsonSlurper

definition(
	name: "Find Kasa Device",
	namespace: "davegut",
	author: "Dave Gutheinz",
	description: "Test application to see if a device is at a specific IP address.",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	singleInstance: true
)

preferences {
	page(name: "startPage")
	page(name: "checkForDevicePage")
}

def installed() { initialize() }

def updated() { initialize() }

def initialize() {
	logInfo("initialize")
	unschedule()
	state.device = [:]
}

def startPage() {
	logInfo("starting Find Kasa Device")
	return dynamicPage(name:"startPage",
					   title:"<b>Find a Kasa Device at an entered IP.</b>",
					   uninstall: true,
					   install: true) {
		section() {
			input "ip", "string",
				title: "<b>IP Address to check</b>",
				description: "Enter the IP address",
				submitOnChange: true
			href "checkForDevicePage",
				title: "<b>Check for the Kasa Device at IP = ${ip}</b>",
				description: "Checks for the device. It will take 30 seconds for the next page to appear."
		}
	}
}

//	Add Devices
def checkForDevicePage() { 
	findDevice()
	pauseExecution(10000)
	
	def device = state.device
	def listing = "Information on Device at IP = ${device.ip}"
	listing += "\n\t\tDNI = ${dni}"
	listing += "\n\t\tType = ${type}"
	listing += "\n\t\tMype = ${model}"
	listing += "\n\t\tAlisa = ${alias}"
	
	return dynamicPage(name:"checkForDevicePage",
					   title: "Press next to return to start page",
					   nextPage: startPage,
					   install: false) {
	}
}

//	Get Device Data Methods
def findDevice() {
	sendLanCmd("""{"system":{"get_sysinfo":{}}}""", "parseLanData")
}

//def parseDeviceData(cmdResp, appServerUrl = null, ip = null) {
private sendLanCmd(command, action) {
	ip = ip.trim()
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command),
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${ip}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
//		 parseWarning: true,
		 timeout: 3,
		 callback: action])
	sendHubCommand(myHubAction)
}

def parseLanData(response) {
	def resp = parseLanMessage(response.description)
	def message = ""
	if (resp.type != "LAN_TYPE_UDPCLIENT") {
		logWarn("parseLanData: ${resp}")
		return
	}
	def clearResp = inputXOR(resp.payload)
	if (clearResp.length() > 1022) {
		clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}"
	}
	def ip = convertHexToIP(resp.ip)
	def cmdResp = new JsonSlurper().parseText(clearResp).system.get_sysinfo
	message += "\n\t\t\tDevice found at IP ${ip}"
	def dni
	if (cmdResp.mic_mac) {
		dni = cmdResp.mic_mac
	} else {
		dni = cmdResp.mac.replace(/:/, "")
	}
	message += "\n\t\t\tDNI = ${dni}"
	def kasaType
	if (cmdResp.mic_type) {
		kasaType = cmdResp.mic_type
	} else {
		kasaType = cmdResp.type
	}
	def type
	def feature = cmdResp.feature
	if (kasaType == "IOT.SMARTPLUGSWITCH") {
		type = "Kasa Plug Switch"
		if (feature == "TIM:ENE") {
			type = "Kasa EM Plug"
		}
		if (cmdResp.brightness) {
			type = "Kasa Dimming Switch"
		} else if (cmdResp.children) {
			type = "Kasa Multi Plug"
			if (feature == "TIM:ENE") {
				type = "Kasa EM Multi Plug"
			}
		}
	} else if (kasaType == "IOT.SMARTBULB") {
		if (cmdResp.lighting_effect_state) {
			feature = "lightStrip"
			type = "Kasa Color Bulb"
		} else if (cmdResp.is_color == 1) {
			type = "Kasa Color Bulb"
		} else if (cmdResp.is_variable_color_temp == 1) {
			type = "Kasa CT Bulb"
		} else {
			type = "Kasa Mono Bulb"
		}
	}
	message += "\n\t\t\tType: ${type}"
	message += "\n\t\t\tModel: ${cmdResp.model}"
	message += "\n\t\t\tAlias: ${cmdResp.alias}"
	logInfo("<b>${message}</b>")
}

//	Utility Methods
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
	for(int i = 0; i < strBytes.length-1; i++) {
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative
		XORtemp = nextKey ^ key
		key = nextKey
		cmdResponse += new String(XORtemp)
	}
	return cmdResponse
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private Integer convertHexToInt(hex) { Integer.parseInt(hex,16) }

def debugOff() { app.updateSetting("debugLog", false) }

def logTrace(msg){ log.trace "Find Device: ${msg}" }

def logDebug(msg){
	if(debugLog == true) { log.debug "Find Device: ${msg}" }
}

def logInfo(msg){ log.info "Find Device: ${msg}" }

def logWarn(msg) { log.warn "Find Device: ${msg}" }

//	end-of-file