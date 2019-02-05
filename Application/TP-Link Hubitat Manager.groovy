/*
TP-Link Device Application, Version 4.1

	Copyright 2018, 2019 Dave Gutheinz

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

DISCLAIMER:  This Applicaion and the associated Device Drivers are in no way sanctioned or supported by TP-Link.  
All  development is based upon open-source data on the TP-Link devices; primarily various users on GitHub.com.

===== History =====
2.04.19	4.1.01.	Eliminated perodic IP polling. Users will update IP by running application when needed.
*/
	def appVersion() { return "4.1.01" }
//	def debugLog() { return false }
	def debugLog() { return true }
	import groovy.json.JsonSlurper
definition(
	name: "TP-Link Device Manager",
	namespace: "davegut",
	author: "Dave Gutheinz",
	description: "Application to install TP-Link bulbs, plugs, and switches.  Does not require a Kasa Account nor a Node Applet",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	singleInstance: true
	)

preferences {
	page(name: "mainPage")
}

def mainPage() {
	logDebug("mainPage")
	setInitialStates()
	app?.removeSetting("selectedDevices")
	discoverDevices()
    def devices = state.devices
	logDebug("mainPage: devices = ${devices}")
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

	return dynamicPage(name:"mainPage",
		title:"Add TP-Link/Kasa Devices",
		nextPage:"",
		refresh: false,
        multiple: true,
		uninstall: true,
		install: true) {
		
		section("") {
			if (errorMsgDev != null) { paragraph "ERROR:  ${errorMSgDev}." }
			else { paragraph "No errors." }
		}
        
	 	section("Select Devices to Add (${newDevices.size() ?: 0} found)", hideable: false, hidden: false) {
			input ("selectedDevices", "enum",
	               required: false,
				   multiple: true,
				   submitOnChange: false,
				   title: null,
				   description: "Add Devices",
				   options: newDevices)
		}
		section("") {
			paragraph "WARNING:  Uninstalling this application will uninstall all associated child devices"
		}
	}
}

def setInitialStates() {
	logDebug("setInitialStates")
	if (!state.devices) { state.devices = [:] }
}

def installed() { initialize() }

def updated() { initialize() }

def initialize() {
	logDebug("initialize")
	unsubscribe()
	unschedule()
	if (selectedDevices) { addDevices() }
}

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
	logDebug("discoverDevices: IP Segment = ${networkPrefix}")
	
	for(int i = 2; i < 255; i++) {
		def deviceIP = "${networkPrefix}.${i.toString()}"
		sendCmd(deviceIP)
		pauseExecution(50)
	}
	pauseExecution(2000)
}

private sendCmd(ip) {
	def myHubAction = new hubitat.device.HubAction(
		"d0f281f88bff9af7d5f5cfb496f194e0bfccb5c6afc1a7c8eacaf08bf68bf6", 
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${ip}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 callback: parse])
	sendHubCommand(myHubAction)
}

def parse(response) {
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
	tpLinkModel << ["HS110" : "TP-Link Engr Mon Plug"]
	tpLinkModel << ["HS115" : "TP-Link Engr Mon Plug"]
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

def uninstalled() {
    	getAllChildDevices().each { 
        deleteChildDevice(it.deviceNetworkId)
    }
}

def removeChildDevice(alias, deviceNetworkId) {
	try {
		deleteChildDevice(it.deviceNetworkId)
		sendEvent(name: "DeviceDelete", value: "${alias} deleted")
	} catch (Exception e) {
		sendEvent(name: "DeviceDelete", value: "Failed to delete ${alias}")
	}
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

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

def logDebug(msg){
	if(debugLog() == true) { log.debug msg }
}
	
//	end-of-file

/*
def uninstallPage() {
	def page1Text = "This will uninstall the All Child Devices including this Application with all it's user data. \nPlease make sure that any devices created by this app are removed from any routines/rules/smartapps before tapping Remove."
	dynamicPage (name: "uninstallPage", title: "Uninstall Page", install: false, uninstall: true) {
		section("") {
            paragraph title: "", page1Text
		}
		remove("Uninstall this application", "Warning!!!", "Last Chance to Stop! \nThis action is not reversible \n\nThis will remove All Devices including this Application with all it's user data")
	}
}
*/