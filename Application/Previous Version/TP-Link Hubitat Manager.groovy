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
2018
2.04	4.1.01.	Eliminated perodic IP polling. Users will update IP by running application 
				when needed.
3.28	4.2.01.	a.	Removed any method that will automatically poll for devices.  Polling
					is now limited to when the user starts the application.
				b.	Added automatic Update Device Date and Update Preferences on polling.
					Method will check the app version.  If not current, will update data
					based on the app version and update the app version.
*/
	def appVersion() { return "4.2.01" }
//	def debugLog() { return false }
	def debugLog() { return true }
	import groovy.json.JsonSlurper
definition(
	name: "TP-Link App V4.2",
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
	page(name: "addDevicesPage")
}

def mainPage() {
	logDebug("mainPage")
	setInitialStates()
	app?.removeSetting("selectedDevices")
	
	def updateStatus = updateInstallData()
	logDebug("mainPage: updateStatus (failures) = ${updateStatus}")
	discoverDevices()

	return dynamicPage(name:"mainPage",
		title:"TP-Link/Kasa Device Manager",
		uninstall: true,
		install: true) {
		section() {
			paragraph "Application Version ${appVersion()}"
			if (updateStatus != "") {
				paragraph "The following devices had the incorrect driver version: ${updateStatus}."
				paragraph "Update the drivers and rerun the application."
			}
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
		"d0f281f88bff9af7d5f5cfb496f194e0bfccb5c6afc1a7c8eacaf08bf68bf6", 	//	Encrypted command
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
		isChild.updateDataValue("deviceIP", ip)
	}
}

def updateInstallData() {
	children = getChildDevices()
	if (!children) { return "No Children" }
	def failures = ""
	children.each { child ->
		def driverVersion = child.driverVer()
		if (driverVersion != null) { driverVersion = driverVersion.substring(0,3) }
		def instAppVer = child.getDataValue("applicationVersion")
		if (instAppVer != null) { instAppVer = instAppVer.substring(0,3) }
		logDebug("updateInstallData ${child.label}: driver = ${driverVersion}, appVer = ${instAppVer}")
		if (instAppVer == "4.2") { return failures }
		if (driverVersion != "4.2") {
			log.error "Incompatible Driver Version for ${child.label}"
			failures += "${child.label} //  "
		} else {
			switch(instAppVer) {
				case "4.1":
				case "4.0":
					break
				case "3.7":
				case "3.6":
				case "3.5":
					def dni = child.getDeviceNetworkId()
					def newDni = dni.replace("_","")
					child.setDeviceNetworkId(newDni)
					child.updateDataValue("deviceId", null)
					child.updateDataValue("appServerUrl", null)
					child.updateDataValue("installType", null)
					break
				default:
					child.updateDataValue("deviceId", null)
					child.updateDataValue("appServerUrl", null)
					break
			}
			child.updateDataValue("applicationVersion", appVersion())
			child.updateInstallData()
			log.info "Successfully updated installation data for ${child.label}"
		}
	}
	return failures
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
	tpLinkModel << ["RE270" : "TP-Link Smart RE Plug"]
	tpLinkModel << ["RE370" : "TP-Link Smart RE Plug"]
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
			log.info "Installed TP-Link $deviceModel with alias ${device.value.alias}"
		}
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
	if (selectedDevices) {
		addDevices()
	}
}

def uninstalled() {
    	getAllChildDevices().each { 
        deleteChildDevice(it.deviceNetworkId)
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
	if(debugLog() == true) { log.debug "${appVersion()} ${msg}" }
}

//	end-of-file