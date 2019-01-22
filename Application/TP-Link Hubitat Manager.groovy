/*
TP-Link Smart Home Device Manager, 2019 Version 4.0

	Copyright 2018, 2019 Dave Gutheinz

Discalimer: This Service Manager and the associated Device
Handlers are in no way sanctioned or supported by TP-Link. All
development is based upon open-source data on the TP-Link Kasa
Devices; primarily various users on GitHub.com.

====== History ================================================
01.01.19	4.0.01.	Initial release of the UDP version not requiring a Kasa Account nor a Node Applet to install and operate the devices.
01.13.19	4.0.04. Skip versions to sync numbers with drivers.
			a.	Added error logic for no-hub error.
			b.	Increase time interval for polling all addresses when doing periodic polling.
			c.	Moved installation polling to add devices only.
01.22.19	4.0.05.	Various
			a.	Removed periodic device discovery.
			b.	Added checkIp method (called from children to update IP).
			c.	Changed method to update data from app to devices.

========== Application Information ==========================*/
	def appVersion() { return "4.0.05" }
	def driverVersion() { return "4.0" }
//	def debugLog() { return false }
	def debugLog() { return true }
	import groovy.json.JsonSlurper
//	===========================================================

definition(
	name: "TP-Link Smart Home Device Manager",
	namespace: "davegut",
	author: "Dave Gutheinz",
	description: "Application to install TP-Link bulbs, plugs, and switches.  Does not require a Kasa Account nor a Node Applet",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "")
	singleInstance: true

preferences {
	page(name: "mainPage")
	page(name: "addDevicesPage")
	page(name: "listDevicesPage")
}

//	============================
//	===== Page Definitions =====
//	============================
def mainPage() {
	logDebug("mainPage")
	setInitialStates()

	return dynamicPage (name: "mainPage",
		title: "Main Page",
		uninstall: true) {
        errorSection()
        
		section("Available Device Management Functions") {
			href "addDevicesPage", 
                title: "Install Kasa Devices", 
                description: "Go to Install Devices"
			
			href "listDevicesPage", 
            	title: "List Drivers and Programs", 
                description: "List Drivers and Programs"
		}
	}
}

def addDevicesPage() {
	app?.removeSetting("selectedDevices")
	state.addDevices = true
	discoverDevices()
	state.addDevices = false
    def devices = state.devices
	logDebug("addDevicesPage: devices = ${devices}")
	def errorMsgDev = null
	def newDevices = [:]
	devices.each {
		def isChild = getChildDevice(it.value.DNI)
		if (!isChild) {
			newDevices["${it.value.DNI}"] = "${it.value.model} ${it.value.alias}"
		}
	}
	
	if (devices == [:]) {
		errorMsgDev = "Looking for devices.  If this message persists, we have been unable to find " +
        "TP-Link devices on your wifi."
	} else if (newDevices == [:]) {
		errorMsgDev = "No new devices to add. Check: 1) Device installed to Kasa properly, " +
        "2) The Hubitat Devices Tab (in case already installed)."
	}

	return dynamicPage(name:"addDevicesPage",
		title:"Add TP-Link/Kasa Devices",
		nextPage:"",
		refresh: false,
        multiple: true,
		install: true) {
        errorSection()
        
 		section("Select Devices to Add (${newDevices.size() ?: 0} found)", hideable: true, hidden: false) {
			input ("selectedDevices", "enum", 
                   required: false, 
                   multiple: true, 
                   submitOnChange: false,
                   title: null,
                   description: "Add Devices",
                   options: newDevices)
		}
	}
}

def listDevicesPage() {
	def devices = state.devices
	logDebug("listDevicesPage: devices = ${devices}")
	def errorMsgDev = null
	def kasaDevices = "Label : Model : deviceIP : Installed : Driver\n"
	devices.each {
    	def installed = "No"
		def devDriverVer = ""
		def isChild = getChildDevice(it.value.DNI)
		if (isChild) {
        	installed = true
            devDriverVer = isChild.driverVer()
		}
		kasaDevices += "${it.value.alias} : ${it.value.model} : ${it.value.IP} : ${it.value.DNI} : ${installed} : ${devDriverVer}\n"
	}
	if (devices == [:]) {
		errorMsgDev = "Devices database was cleared in-error.  Run Device Installer Page to correct " +
        "then try again.  You can also remove devices using the Environment app."
	}
	if (kasaDevices == [:]) {
		errorMsgDev = "There are no devices to remove from the SmartThings app at this time.  This " +
        "implies no devices are installed."
	}
        
	return dynamicPage (name: "listDevicesPage", 
    	title: "List of Kasa Devices and Handlers") { 
        errorSection()
		
		section("Kasa Devices and Device Handlers", hideable: true) {
			paragraph "Recommended Minimum Driver: ${driverVersion()}\n${kasaDevices}"
		}
	}
}

def errorSection() {
	section("") {
		if (errorMsgDev != null) {
			paragraph "ERROR:  ${errorMSgDev}."
		} else {
			paragraph "No errors."
		}
	}
}

//	==============================
//	===== Start up Functions =====
//	==============================
def setInitialStates() {
	logDebug("SETINITIALSTATES")
	if (!state.devices) { state.devices = [:] }
	if (!state.addDevices) { state.addDevices = false }
	state.allowDiscovery = true
}

def installed() { initialize() }

def updated() { initialize() }

def initialize() {
	logDebug("INITIALIZE")
	unsubscribe()
	unschedule()
	if (selectedDevices) { addDevices() }
}

//	============================
//	===== Device Discovery =====
//	============================
def discoverDevices() {
	state.devices = [:]
	def hub
	try { hub = location.hubs[0] }
	catch (error) { 
		log.error "Hub not detected.  You must have a hub to install this app."
		return
	}

	def hubIpArray = hub.localIP.split('\\.')
	def networkPrefix = [hubIpArray[0],hubIpArray[1],hubIpArray[2]].join(".")
	logDebug("discoverDevices: IP Segment = ${networkPrefix}, addDevices = ${state.addDevices}")
	
	def delay = 100
	if (state.addDevices == true) { delay = 40 }
	for(int i = 2; i < 255; i++) {
		def deviceIP = "${networkPrefix}.${i.toString()}"
		sendCmd(deviceIP)
		pauseExecution(delay)
	}
}

private sendCmd(ip) {
	def myHubAction = new hubitat.device.HubAction(
		"d0f281f88bff9af7d5f5cfb496f194e0bfccb5c6afc1a7c8eacaf08bf68bf6", 
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${ip}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 callback: parseDevices])
	sendHubCommand(myHubAction)
}

def parseDevices(response) {
	def resp = parseLanMessage(response.description)
	def parser = new JsonSlurper()
	def cmdResp = parser.parseText(inputXOR(resp.payload)).system.get_sysinfo
	def totPlugs = cmdResp.child_num
	def plugNo
	def plugId
	def dni = cmdResp.mic_mac
	if (cmdResp.mic_type != "IOT.SMARTBULB") {
		dni = cmdResp.mac.replace(/:/, "")
	}
	if (totPlugs) {
		def children = cmdResp.children
		for (def i = 0; i < totPlugs; i++) {
			plugNo = children[i].id
			def plugDNI = "${dni}${plugNo}"
			plugId = "${cmdResp.deviceId}${plugNo}"
			updateDevices(plugDNI, cmdResp.model.substring(0,5), convertHexToIP(resp.ip), children[i].alias, plugNo, plugId)
		}
	} else {
		updateDevices(dni, cmdResp.model.substring(0,5), convertHexToIP(resp.ip), cmdResp.alias, plugNo, plugId)
	}
}

def updateDevices(dni, model, ip, alias, plugNo, plugId) {
	logDebug("updateDevices: DNI = ${dni}, model = ${model}, ip = ${ip}, alias = ${alias}, plugNo = ${plugNo}, plugId = ${plugId}")
	def devices = state.devices
	
	def device = [:]
	device["DNI"] = dni
	device["IP"] = ip
	device["alias"] = alias
	device["model"] = model
	if (plugNo) {
		device["plugNo"] = plugNo
		device["plugId"] = plugId
	}
	devices << ["${dni}" : device]
	
	def isChild = getChildDevice(dni)
	if (isChild) { 
		isChild.updateDataValue("applicationVersion", appVersion())
		isChild.updateDataValue("deviceIP", ip)
	}
}

//	=======================================
//	===== Add Devices to Hubitat ==========
//	=======================================
def addDevices() {
	logDebug("addDevices:  Devices = ${state.devices}")
	def tpLinkModel = [:]
	//	Plug-Switch Devices (no energy monitor capability)
	tpLinkModel << ["HS100" : "TP-Link Plug-Switch"]
	tpLinkModel << ["HS103" : "TP-Link Plug-Switch"]
	tpLinkModel << ["HS105" : "TP-Link Plug-Switch"]
	tpLinkModel << ["HS200" : "TP-Link Plug-Switch"]
	tpLinkModel << ["HS210" : "TP-Link Plug-Switch"]
	tpLinkModel << ["KP100" : "TP-Link Plug-Switch"]
	//	Miltiple Outlet Plug
	tpLinkModel << ["HS107" : "TP-Link Multi-Plug"]
	tpLinkModel << ["HS300" : "TP-Link Multi-Plug"]
	tpLinkModel << ["KP200" : "TP-Link Multi-Plug"]
	tpLinkModel << ["KP400" : "TP-Link Multi-Plug"]
	//	Dimming Switch Devices
	tpLinkModel << ["HS220" : "TP-Link Dimming Switch"]
	//	Energy Monitor Plugs
	tpLinkModel << ["HS110" : "TP-Link Plug-Switch"]
	tpLinkModel << ["HS115" : "TP-Link Plug-Switch"]
	//	Soft White Bulbs
	tpLinkModel << ["KB100" : "TP-Link Soft White Bulb"]
	tpLinkModel << ["LB100" : "TP-Link Soft White Bulb"]
	tpLinkModel << ["LB110" : "TP-Link Soft White Bulb"]
	tpLinkModel << ["KL110" : "TP-Link Soft White Bulb"]
	tpLinkModel << ["LB200" : "TP-Link Soft White Bulb"]
	//	Tunable White Bulbs
	tpLinkModel << ["LB120" : "TP-Link Tunable White Bulb"]
	tpLinkModel << ["KL120" : "TP-Link Tunable White Bulb"]
	//	Color Bulbs
	tpLinkModel << ["KB130" : "TP-Link Color Bulb"]
	tpLinkModel << ["LB130" : "TP-Link Color Bulb"]
	tpLinkModel << ["KL130" : "TP-Link Color Bulb"]
	tpLinkModel << ["LB230" : "TP-Link Color Bulb"]

	def hub
	try { hub = location.hubs[0] }
	catch (error) { 
		log.error "Hub not detected.  You must have a hub to install this app."
		return
	}

	def hubId = hub.id
	selectedDevices.each { dni ->
		def isChild = getChildDevice(dni)
		if (!isChild) {
			def device = state.devices.find { it.value.DNI == dni }
			def deviceModel = device.value.model
			
			def deviceData
			if (device.value.plugNo) {
				deviceData = [
					"applicationVersion" : appVersion(),
					"deviceIP" : device.value.IP,
					"plugNo" : device.value.plugNo,
					"plugId" : device.value.plugId
				]
			} else {
				deviceData = [
					"applicationVersion" : appVersion(),
					"deviceIP" : device.value.IP
				]
			}
			
			logDebug("addDevices: ${tpLinkModel["${deviceModel}"]} / ${device.value.DNI} / ${hubId} / ${device.value.alias} / ${deviceModel} / ${deviceData}")
			addChildDevice(
              	"davegut", 
               	tpLinkModel["${deviceModel}"],
				device.value.DNI,
				hubId, [
					"label" : device.value.alias,
                   	"name" : deviceModel,
					"data" : deviceData
                ]
            )
			log.info "Installed TP-Link $deviceModel with alias ${device.value.alias}"
		}
	}
}

def checkIp() {
	if (state.allowDiscovery == false) { return }
	state.allowDiscovery = false
	runIn(600, resetAllowDiscovery)
	discoverDevices()
}

def resetAllowDiscovery() {
	state.allowDiscovery = true
}

//	===== XOR Encode and Decode Device Data =====
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
	def key = 0x2B
	def nextKey
	byte[] XORtemp
	
	for(int i = 0; i < strBytes.length-1; i++) {
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative
		XORtemp = nextKey ^ key
		key = nextKey
		cmdResponse += new String(XORtemp)
	}
	
	//	For some reason, first character not decoding properly.
	cmdResponse = "{" + cmdResponse.drop(1)
	return cmdResponse
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

def logTrace(msg){
	if(debugLog() == true) { log.trace msg }
}

def logDebug(msg){
	if(debugLog() == true) { log.debug msg }
}
	
//	end-of-file