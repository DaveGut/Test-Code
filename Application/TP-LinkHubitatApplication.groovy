/*
TP-Link Device Application, Version 4.2
		Copyright 2018, 2019 Dave Gutheinz
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file 
except in compliance with the License. You may obtain a copy of the License at: 
		http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the 
License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
either express or implied. See the License for the specific language governing permissions 
and limitations under the License.

DISCLAIMER:  This Applicaion and the associated Device Drivers are in no way sanctioned or 
supported by TP-Link. All  development is based upon open-source data on the TP-Link 
devices; primarily various users on GitHub.com.

===== History =====
2019
2.04	4.1.01.	Eliminated perodic IP polling. Users will update IP by running application 
				when needed.
3.28	4.2.01.	a.	Removed any method that will automatically poll for devices.  Polling
					is now limited to when the user starts the application.
				b.	Added automatic Update Device Date and Update Preferences on polling.
					Method will check the app version.  If not current, will update data
					based on the app version and update the app version.
7.15	4.3.01	Minor changes for compatibility with new drivers.
				a.	Added parameter for user to set autoIpUpdate to poll for IPs every hour.
				b.	Added code to check for comms error before the IP update.  If no errors
					the IP update will not run.
=============================================================================================*/
def debugLog() { return false }
def appVersion() { return "4.3.01" }
import groovy.json.JsonSlurper

definition(
	name: "TP-Link Integration",
	namespace: "davegut",
	author: "Dave Gutheinz",
	description: "Application to install TP-Link bulbs, plugs, and switches.  Does not require a Kasa Account nor a Node Applet",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	singleInstance: true,
	importUrl: "https://github.com/DaveGut/Hubitat-TP-Link-Integration/blob/master/Application/TP-LinkHubitatApplication.groovy"
	)

preferences {
	page(name: "mainPage")
	page(name: "addDevicesPage")
}

//	Page definitions
def mainPage() {
	logDebug("mainPage")
	setInitialStates()
	app?.removeSetting("selectedDevices")
	discoverDevices()
	return dynamicPage(name:"mainPage",
		title:"TP-Link/Kasa Device Manager",
		uninstall: true,
		install: true) {
		section() {
			"The app will first poll for TP-Link Devices.  This can up to 2 minutes.  During this " +
			"time, the Hubitat resources for UDP communications are stressed.  Hubitat performance " +
			"may be impacted for up to 5 minutes (while the UDP resources are released).\n\n"
			"Notes:\n"
			"a. After running, Hubitat resources will be fully released.  There should be no performance impact./n"
			"b. This application does NOT run unless initiated by user./n"
			"c. To update IPs, run this application at any time then press DONE after polling completes./b"
			"d. It is STRONGLY recommended that you set up STATIC IP addresses for the TP-Link devices in your router."
		}
		section() {
        	href "addDevicesPage", title: "Install Kasa Devices", description: "Go to Install Devices"
        }
	}		
}
def addDevicesPage() {
    def devices = state.devices
	def newDevices = [:]
	devices.each {
		def isChild = getChildDevice(it.value.DNI)
		if (!isChild) {
			newDevices["${it.value.DNI}"] = "${it.value.model} ${it.value.alias}"
		}
	}
	logDebug("addDevicesPage: newDevices = ${newDevices}")
	return dynamicPage(name:"addDevicesPage",
		title:"Add TP-Link/Kasa Devices to Hubitat",
		install: true) {
	 	section() {
			input ("selectedDevices", "enum",
				   required: true,
				   multiple: true,
				   title: "Devices to add (${newDevices.size() ?: 0} available)",
				   description: "Add Devices",
				   options: newDevices)
		}
	}
}


//	Poll for devices, collect device data
def discoverDevices() {
	def hub
	try { hub = location.hubs[0] }
	catch (error) { 
		logWarn "Hub not detected.  You must have a hub to install this app."
		return
	}
	def hubIpArray = hub.localIP.split('\\.')
	def networkPrefix = [hubIpArray[0],hubIpArray[1],hubIpArray[2]].join(".")
	logInfo("discoverDevices: IP Segment = ${networkPrefix}, poll interal = ${pause}")
	for(int i = 2; i < 255; i++) {
		def deviceIP = "${networkPrefix}.${i.toString()}"
		sendCmd(deviceIP, "parseDevices")
		pauseExecution(100)
	}
	pauseExecution(2000)
}
private sendCmd(ip, action) {
	def myHubAction = new hubitat.device.HubAction(
		"d0f281f88bff9af7d5f5cfb496f194e0bfccb5c6afc1a7c8eacaf08bf68bf6", 	//	Encrypted command
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${ip}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 timeout: 1,
		 callback: action])
	sendHubCommand(myHubAction)
}
def parseDevices(response) {
	def resp = parseLanMessage(response.description)
	def parser = new JsonSlurper()
	def cmdResp = parser.parseText(inputXOR(resp.payload)).system.get_sysinfo
	logDebug("parseDevices: cmdResp = ${cmdResp}")
	if (cmdResp.system) {
		def dni = cmdResp.system.ethernet_mac.replace(/:/, "")
		updateDevices(dni, cmdResp.system.model.substring(0,5), convertHexToIP(resp.ip), cmdResp.system.alias)
	} else if (cmdResp.mic_mac) {
		model = cmdResp.model.substring(0,5)
		updateDevices(cmdResp.mic_mac, cmdResp.model.substring(0,5), convertHexToIP(resp.ip), cmdResp.alias)
	} else {
		def dni = cmdResp.mac.replace(/:/, "")
		if (cmdResp.children) {
			def totPlugs = cmdResp.child_num
			def children = cmdResp.children
			for (def i = 0; i < totPlugs; i++) {
				plugNo = children[i].id
				def plugDNI = "${dni}${plugNo}"
				plugId = "${cmdResp.deviceId}${plugNo}"
				updateDevices(plugDNI, cmdResp.model.substring(0,5), convertHexToIP(resp.ip), children[i].alias, plugNo, plugId)
			}
		} else {
			updateDevices(dni, cmdResp.model.substring(0,5), convertHexToIP(resp.ip), cmdResp.alias)
		}
	}
}
def updateDevices(dni, model, ip, alias, plugNo = null, plugId = null) {
	logInfo("updateDevices: DNI = ${dni}, model = ${model}, ip = ${ip}, alias = ${alias}, plugNo = ${plugNo}, plugId = ${plugId}")
	if (model == "RE270" || model == "RE370") { return }
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
	//	WiFi Range Extender with smart plug.
//	tpLinkModel << ["RE270" : "TP-Link RE Plug"]
//	tpLinkModel << ["RE370" : "TP-Link RE Plug"]
	//	Miltiple Outlet Plug
	tpLinkModel << ["HS107" : "TP-Link Multi-Plug"]
	tpLinkModel << ["KP200" : "TP-Link Multi-Plug"]
	tpLinkModel << ["KP400" : "TP-Link Multi-Plug"]
	//	Dimming Switch Devices
	tpLinkModel << ["HS220" : "TP-Link Dimming Switch"]
	//	Energy Monitor Multi Plugs
	tpLinkModel << ["HS300" : "TP-Link Engr Mon Multi-Plug"]
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
		logWarn("Hub not detected.  You must have a hub to install this app.")
		return
	}
	def hubId = hub.id
	selectedDevices.each { dni ->
		def isChild = getChildDevice(dni)
		if (!isChild) {
			def device = state.devices.find { it.value.DNI == dni }
			def deviceModel = device.value.model
			def deviceData = [:]
			deviceData["applicationVersion"] = appVersion()
			deviceData["deviceIP"] = device.value.IP
			if (device.value.plugNo != null) {
				deviceData["plugNo"] = device.value.plugNo
				deviceData["plugId"] = device.value.plugId
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
			logInfo("Installed TP-Link $deviceModel with alias ${device.value.alias}")
		}
	}
}


//	Install and Initialization methods
def setInitialStates() {
	logDebug("setInitialStates")
	if (!state.devices) { state.devices = [:] }
	if (!state.commsError) { state.commsError = false }
	if (!state.updateRequired) { state.updateRequired = false }
}
def installed() { initialize() }
def updated() { initialize() }
def initialize() {
	logDebug("initialize")
	unsubscribe()
	unschedule()
	if (selectedDevices) {
		addDevices()
	}
}
def uninstalled() {
    getAllChildDevices().each { 
        deleteChildDevice(it.deviceNetworkId)
    }
}


//	Utility methods
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
def logDebug(msg){
	if(debugLog() == true) { log.debug "${appVersion()} ${msg}" }
}
def logInfo(msg) { log.info "${appVersion()} ${msg}" }
def logWarn(msg) { log.warn "${appVersion()} ${msg}" }

//	end-of-file