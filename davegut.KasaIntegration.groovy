/*	Kasa Integration Application
	Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Link to Documentation =====
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Documentation.pdf

==	Version 2.4.2c
Issues resolved:
a.	Comms error.  Attribute "commsError" not resetting. 
	== Resolved: Added internal UDP timeout (since Hub function appears not to work).
b.	Cloud control. Cloud control not working (Kasa has migrated server to Tapo server).
	== Resolved:  Removed Cloud access from integration. Modified device data update
				  triggered on Hub reboot.
c.	User confusion on which intergration to use given new API in some devices.
	== Resolved:  Provide list of new user API devices on Add Devices page.
d.	LAN Issues. Kasa devices not discovered. Usually caused by either LAN issue,
	device issues, or device busy when polling.
	== Resolved:  Existing try again function on discovery page. Added note to
				  exercise device before trying again.
Continued Issue: LAN issues due to factors outside of Hubitat implementation.
1.	User LAN topology / security isolating device from Hub.
2.	Older routers temporarily "drop" LAN devices (usually when total devices
	exceed 20 or so).
3.	Interference from other connections to physical devices. (Kasa devices
	appear to ignore incoming UDP messages when a message is being processed.
	The more connections to the device, the higher probability.
===================================================================================================*/
//	=====	NAMESPACE	============
def nameSpace() { return "davegut" }
//	================================
#include davegut.Logging
import groovy.json.JsonSlurper
def getVer() { return "" }

definition(
	name: "Kasa Integration",
	namespace: nameSpace(),
	author: "Dave Gutheinz",
	description: "Application to install TP-Link bulbs, plugs, and switches.",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	installOnOpen: true,
	singleInstance: true,
	documentationLink: "https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/README.md",
	importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/Application/KasaIntegrationApp.groovy"
)

preferences {
	page(name: "initInstance")
	page(name: "startPage")
	page(name: "addDevicesPage")
	page(name: "addDevStatus")
	page(name: "removeDevicesPage")
}

void hubStartupHandler() {
	startPage()
	findDevices(30)
}

def installed() { updated() }

def updated() {
	logInfo("updated: Updating device configurations and (if cloud enabled) Kasa Token")
	app.updateSetting("logEnable", [type:"bool", value: false])
	app?.updateSetting("appSetup", [type:"bool", value: false])
	state.remove("addedDevices")
	state.remove("failedAdds")
	scheduleChecks()
}

def scheduleChecks() {
	unschedule()
	configureEnable()
}

def uninstalled() {
    getAllChildDevices().each { 
        deleteChildDevice(it.deviceNetworkId)
    }
}

def initInstance() {
	logDebug("initInstance: Getting external data for the app.")
	unschedule()
	runIn(900, scheduleChecks)
	app.updateSetting("infoLog", true)
	app.updateSetting("logEnable", false)
	runIn(900, debugLogOff)
	if (!state.devices) { state.devices = [:] }
	cleanData()
	if (!lanSegment) {
		def hub = location.hub
		def hubIpArray = hub.localIP.split('\\.')
		def segments = [hubIpArray[0],hubIpArray[1],hubIpArray[2]].join(".")
		app?.updateSetting("lanSegment", [type:"string", value: segments])
	}
	if (!ports) {
		app?.updateSetting("ports", [type:"string", value: "9999"])
	}
	if (!hostLimits) {
		app?.updateSetting("hostLimits", [type:"string", value: "1, 254"])
	}
	startPage()
}

def cleanData() {
	state.tapoDevices = []
	Map newDevices = [:]
	state.devices.each {
		def isChild = getChildDevice(it.value.dni)
		if (isChild) {
			newDevices << it
		}
	}
	state.devices = newDevices
}

def startPage() {
	logInfo("starting Kasa Integration")
	if (selectedRemoveDevices) { removeDevices() }
	if (selectedAddDevices) { addDevices() }
	try {
		state.segArray = lanSegment.split('\\,')
		state.portArray = ports.split('\\,')
		def rangeArray = hostLimits.split('\\,')
		def array0 = rangeArray[0].toInteger()
		def array1 = array0 + 2
		if (rangeArray.size() > 1) {
			array1 = rangeArray[1].toInteger()
		}
		state.hostArray = [array0, array1]
	} catch (e) {
		logWarn("startPage: Invalid entry for Lan Segements, Host Array Range, or Ports. Resetting to default!")
		def hub = location.hubs[0]
		def hubIpArray = hub.localIP.split('\\.')
		def segments = [hubIpArray[0],hubIpArray[1],hubIpArray[2]].join(".")
		app?.updateSetting("lanSegment", [type:"string", value: segments])
		app?.updateSetting("ports", [type:"string", value: "9999"])
		app?.updateSetting("hostLimits", [type:"string", value: "1, 254"])
	}
	
	def notes = "<b>IMPORTANT NOTES:</b>\r"
	notes += "\t<b>HS300 Multiplug</b>.  Requires special handling.  Read install instructions.\r"
	
	return dynamicPage(name:"startPage",
					   title:"<b>Kasa Hubitat Integration</b>",
					   uninstall: true,
					   install: true) {
		section() {
			paragraph notes
			input "appSetup", "bool",
				title: "<b>Modify LAN Configuration</b>",
				submitOnChange: true,
				defaultalue: false
			if (appSetup) {
				input "lanSegment", "string",
					title: "<b>Lan Segments</b> (ex: 192.168.50, 192,168.01)",
					submitOnChange: true
				input "hostLimits", "string",
					title: "<b>Host Address Range</b> (ex: 5, 100)",
					submitOnChange: true
				input "ports", "string",
					title: "<b>Ports for Port Forwarding</b> (ex: 9999, 8000)",
					submitOnChange: true
			}
			paragraph "<b>LAN Configuration</b>:  [LanSegments: ${state.segArray},  " +
				"Ports ${state.portArray},  hostRange: ${state.hostArray}]"

			href "addDevicesPage",
				title: "<b>Scan LAN for Kasa devices and add</b>",
				description: "Discover and add Kasa IOT devices."
			
			href "removeDevicesPage",
				title: "<b>Remove Kasa Devices</b>",
				description: "Select to remove selected Kasa Device from Hubitat."
			paragraph " "
			input "logEnable", "bool",
				   title: "<b>Debug logging</b>",
				   submitOnChange: true
		}
	}
}

def addDevicesPage() {
	logDebug("addDevicesPage")
	def action = findDevices(10, true)
	
	def devices = state.devices
	def uninstalledDevices = [:]
	def requiredDrivers = [:]
	devices.each {
		def isChild = getChildDevice(it.value.dni)
		if (!isChild) {
			uninstalledDevices["${it.value.dni}"] = "${it.value.alias}, ${it.value.type}"
			requiredDrivers["${it.value.type}"] = "${it.value.type}"
		}
	}
	uninstalledDevices.sort()
	def tapoDevices = state.tapoDevices
	def tapoDevList = ""
	tapoDevices.each { tapoDevList += "${it}\n" }
	return dynamicPage(name:"addDevicesPage",
					   title: "Add Kasa Devices to Hubitat",
					   nextPage: addDevStatus,
					   install: false) {
	 	section() {
			input ("selectedAddDevices", "enum",
				   required: false,
				   multiple: true,
				   title: "Devices to add (${uninstalledDevices.size() ?: 0} available).\n\t" +
				   "Total Devices: ${devices.size()}",
				   description: "Use the dropdown to select devices.  Then select 'Done'.",
				   options: uninstalledDevices)
			if (tapoDevices && tapoDevices.size() > 0) {
				def tapoNote = "<b>Found Tapo Protocol Devices.</b>.  The below Kasa devices "
				tapoNote += "can't be installed via this integration.  The community "
				tapoNote += "TP-LInk Tapo Integration will likely support these devices. "
				tapoNote += "\n<p style='font-size:14px'>${tapoDevList}</p>"
				paragraph(tapoNote)
			}
			def desTxt = "Recommend you exercise missing devices through Kasa/Tapo phone "
			desTxt += "app and then retry discovery of devices not on list."
			href "addDevicesPage",
				title: "<b>Rescan for Additional Kasa Devices</b>",
				description: desTxt
		}
	}
}

def addDevStatus() {
	addDevices()
	logInfo("addDevStatus")
	def addMsg = ""
	if (state.addedDevices == null) {
		addMsg += "Added Devices: No devices added."
	} else {
		addMsg += "<b>The following devices were installed:</b>\n"
		state.addedDevices.each{
			addMsg += "\t${it}\n"
		}
	}
	def failMsg = ""
	if (state.failedAdds) {
		failMsg += "<b>The following devices were not installed:</b>\n"
		state.failedAdds.each{
			failMsg += "\t${it}\n"
		}
	}
	return dynamicPage(name:"addDeviceStatus",
					   title: "Installation Status",
					   nextPage: startPage,
					   install: false) {
	 	section() {
			paragraph addMsg
			paragraph failMsg
		}
	}
	app?.removeSetting("selectedAddDevices")
}

def addDevices() {
	logInfo("addDevices: [selectedDevices: ${selectedAddDevices}]")
	def hub = location.hubs[0]
	state.addedDevices = []
	state.failedAdds = []
	selectedAddDevices.each { dni ->
		def isChild = getChildDevice(dni)
		if (!isChild) {
			def device = state.devices.find { it.value.dni == dni }
			def deviceData = [:]
			deviceData["deviceIP"] = device.value.ip
			deviceData["devicePort"] = device.value.port
			deviceData["deviceId"] = device.value.deviceId
			deviceData["model"] = device.value.model
			deviceData["feature"] = device.value.feature
			if (device.value.plugNo) {
				deviceData["plugNo"] = device.value.plugNo
				deviceData["plugId"] = device.value.plugId
			}
			try {
				addChildDevice(
					nameSpace(),
					device.value.type,
					device.value.dni,
					[
						"label": device.value.alias.replaceAll("[\u201C\u201D]", "\"").replaceAll("[\u2018\u2019]", "'").replaceAll("[^\\p{ASCII}]", ""),
						"data" : deviceData
					]
				)
				state.addedDevices << [label: device.value.alias, ip: device.value.ip]
				logDebug("Installed ${device.value.alias}.")
			} catch (error) {
				state.failedAdds << [label: device.value.alias, driver: device.value.type, ip: device.value.ip]
				def msgData = [status: "failedToAdd", label: device.value.alias, driver: device.value.type, ip: device.value.ip]
				msgData << [errorMsg: error]
				logWarn("addDevice: ${msgData}")
			}
		}
		pauseExecution(3000)
	}
	app?.removeSetting("selectedAddDevices")
}

def findDevices(timeout = 10, findTapo = false) {
	def start = state.hostArray.min().toInteger()
	def finish = state.hostArray.max().toInteger() + 1
	def await
	logDebug([method: "findDevices", hostArray: state.hostArray, portArray: state.portArray, 
			 pollSegment: state.segArray, timeout: timeout, findTapo: findTapo])
	state.portArray.each {
		def port = it.trim()
		List deviceIPs = []
		state.segArray.each {
			def pollSegment = it.trim()
			logDebug("findDevices: Searching for LAN deivces on IP Segment = ${pollSegment}, port = ${port}")
            for(int i = start; i < finish; i++) {
				deviceIPs.add("${pollSegment}.${i.toString()}")
			}
			logInfo([method: "findDevices", activity: "sendLanCmd", segment: pollSegment, port: port])
			def xorCmd = outputXOR("""{"system":{"get_sysinfo":{}}}""")
			await = sendLanCmd(deviceIPs.join(','), port, xorCmd, "getLanData", timeout)
			pauseExecution(1000*(timeout + 1))
			if (findTapo == true) {
				//	Note this currently only finds non-battery powered Tapo devices (bat devices use port 20004).
				def cmdData = "0200000101e51100095c11706d6f58577b22706172616d73223a7b227273615f6b6579223a222d2d2d2d2d424547494e205055424c4943204b45592d2d2d2d2d5c6e4d494942496a414e42676b71686b6947397730424151454641414f43415138414d49494243674b43415145416d684655445279687367797073467936576c4d385c6e54646154397a61586133586a3042712f4d6f484971696d586e2b736b4e48584d525a6550564134627532416257386d79744a5033445073665173795679536e355c6e6f425841674d303149674d4f46736350316258367679784d523871614b33746e466361665a4653684d79536e31752f564f2f47474f795436507459716f384e315c6e44714d77373563334b5a4952387a4c71516f744657747239543337536e50754a7051555a7055376679574b676377716e7338785a657a78734e6a6465534171765c6e3167574e75436a5356686d437931564d49514942576d616a37414c47544971596a5442376d645348562f2b614a32564467424c6d7770344c7131664c4f6a466f5c6e33737241683144744a6b537376376a624f584d51695666453873764b6877586177717661546b5658382f7a4f44592b2f64684f5374694a4e6c466556636c35585c6e4a514944415141425c6e2d2d2d2d2d454e44205055424c4943204b45592d2d2d2d2d5c6e227d7d"
				await = sendLanCmd(deviceIPs.join(','), "20002", cmdData, "getTapoLanData", 5)
				pauseExecution(10000)
			}
		}
	}
	runIn(10, updateChildren)
	return
}

def getLanData(response) {
	if (response instanceof Map) {
		def lanData = parseLanData(response)
		if (lanData.error) { return }
		def cmdResp = lanData.cmdResp
		if (cmdResp.system) {
			cmdResp = cmdResp.system
		}
		def await = parseDeviceData(cmdResp, lanData.ip, lanData.port)
	} else {
		devices = state.devices
		response.each {
			def lanData = parseLanData(it)
			if (lanData.error) { return }
			def cmdResp = lanData.cmdResp
			if (cmdResp.system) {
				cmdResp = cmdResp.system
			}
			def await = parseDeviceData(cmdResp, lanData.ip, lanData.port)
		}
	}
}

def parseDeviceData(cmdResp, ip, port) {
	def logData = [method: "parseDeviceData"]
	def dni
	if (cmdResp.mic_mac) {
		dni = cmdResp.mic_mac
	} else {
		dni = cmdResp.mac.replace(/:/, "")
	}
	def devices = state.devices
	def kasaType
	if (cmdResp.mic_type) {
		kasaType = cmdResp.mic_type
	} else {
		kasaType = cmdResp.type
	}
	def type = "Kasa Plug Switch"
	def feature = cmdResp.feature
	if (kasaType == "IOT.SMARTPLUGSWITCH") {
		if (cmdResp.dev_name && cmdResp.dev_name.contains("Dimmer")) {
			feature = "dimmingSwitch"
			type = "Kasa Dimming Switch"
		}
	} else if (kasaType == "IOT.SMARTBULB") {
		if (cmdResp.lighting_effect_state) {
			feature = "lightStrip"
			type = "Kasa Light Strip"
		} else if (cmdResp.is_color == 1) {
			feature = "colorBulb"
			type = "Kasa Color Bulb"
		} else if (cmdResp.is_variable_color_temp == 1) {
			feature = "colorTempBulb"
			type = "Kasa CT Bulb"
		} else {
			feature = "monoBulb"
			type = "Kasa Mono Bulb"
		}
	} else if (kasaType == "IOT.IPCAMERA") {
		feature = "ipCamera"
		type = "NOT AVAILABLE"
	}
	def model = cmdResp.model.substring(0,5)
	def alias = cmdResp.alias
	def rssi = cmdResp.rssi
	def deviceId = cmdResp.deviceId
	def plugNo
	def plugId
	if (cmdResp.children) {
		def childPlugs = cmdResp.children
		childPlugs.each {
			plugNo = it.id
			plugNo = it.id.substring(it.id.length() - 2)
			def childDni = "${dni}${plugNo}"
			plugId = "${deviceId}${plugNo}"
			alias = it.alias
			def device = createDevice(childDni, ip, port, rssi, type, feature, model, alias, deviceId, plugNo, plugId)
			devices["${childDni}"] = device
		}
	} else if (model == "HS300") {
		def parentAlias = alias
		for(int i = 0; i < 6; i++) {
			plugNo = "0${i.toString()}"
			def childDni = "${dni}${plugNo}"
			plugId = "${deviceId}${plugNo}"
			def child = getChildDevice(childDni)
			if (child) {
				alias = child.device.getLabel()
			} else {
				alias = "${parentAlias}_${plugNo}_TEMP"
			}
			def device = createDevice(childDni, ip, port, rssi, type, feature, model, alias, deviceId, plugNo, plugId)
			devices["${childDni}"] = device
		}
	} else {
		def device = createDevice(dni, ip, port, rssi, type, feature, model, alias, deviceId, plugNo, plugId)
		devices["${dni}"] = device
	}
	logData << [alias: "<b>${alias}</b>", type: type, ip: ip, port: port, status: "added to array"]
	logDebug(logData)
	return
}

def createDevice(dni, ip, port, rssi, type, feature, model, alias, deviceId, plugNo, plugId) {
	Map logData = [method: "createDevice"]
	def device = [:]
	device["dni"] = dni
	device["ip"] = ip
	device["port"] = port
	device["type"] = type
	device["rssi"] = rssi
	device["feature"] = feature
	device["model"] = model
	device["alias"] = alias
	device["deviceId"] = deviceId
	if (plugNo != null) {
		device["plugNo"] = plugNo
		device["plugId"] = plugId
		logData << [plugNo: plugNo]
	}
	logData << [device: device]
	logDebug(logData)
	return device
}

def getTapoLanData(response) {
	List tapoDevices = []
	if (response instanceof Map) {
		def lanData = parseTapoLanData(response)
		if (!lanData.status) {
			tapoDevices << lanData
		}
	} else {
		devices = state.devices
		response.each {
			def lanData = parseTapoLanData(it)
			if (!lanData.status) {
				tapoDevices << lanData
			}
		}
	}
	state.tapoDevices = tapoDevices
}

def parseTapoLanData(response) {
	Map devData = [:]
	def respData = parseLanMessage(response.description)
	if (respData.type == "LAN_TYPE_UDPCLIENT") {
		byte[] payloadByte = hubitat.helper.HexUtils.hexStringToByteArray(respData.payload.drop(32)) 
		String payloadString = new String(payloadByte)
		if (payloadString.length() > 1007) {
			payloadString = payloadString + """"}}}"""
		}
		Map payload = new JsonSlurper().parseText(payloadString).result
		List supported = ["SMART.KASAHUB", "SMART.KASAPLUG", "SMART.KASASWITCH", "SMART.KASAENERGY"]
		String devType = payload.device_type
		String model = payload.device_model
		String devIp = payload.ip
		String dni = payload.mac.replaceAll("-", "")
		if (supported.contains(devType)) {
			devData = [ip: devIp, type: devType, model: model, dni: dni]
		} else {
			devData = [status: "Tapo Device", ip: devIp, model: model]
		}
	} else {
		devData = [status: "LAN Data Error"]
		logDebug(devData)
	}
	return devData
}

def removeDevicesPage() {
	Map logData = [method: "removeDevicesPage"]
	def installedDevices = [:]
	getChildDevices().each {
		installedDevices << ["${it.device.deviceNetworkId}": it.device.label]
	}
	logData << [installedDevices: installedDevices]
	logData << [childDevices: installedDevices]
	logInfo(logData)
	return dynamicPage(name:"removedDevicesPage",
					   title:"<b>Remove Kasa Devices from Hubitat</b>",
					   nextPage: startPage,
					   install: false) {
		section() {
			input ("selectedRemoveDevices", "enum",
				   required: false,
				   multiple: true,
				   title: "Devices to remove (${installedDevices.size() ?: 0} available)",
				   description: "Use the dropdown to select devices.  Then select 'Done'.",
				   options: installedDevices)
		}
	}
}

def removeDevices() {
	Map logData = [method: "removeDevices", selectedRemoveDevices: selectedRemoveDevices]
	selectedRemoveDevices.each { dni ->
		try {
			deleteChildDevice(dni)
			logData << ["${dni}": "removed"]
		} catch (error) {
			logData << ["${dni}": "FAILED"]
			logWarn("Failed to delet ${device.value.alias}.")
		}
	}
	app?.removeSetting("selectedRemoveDevices")
	logInfo(logData)
}

def updateConfigurations() {
	Map logData = [method: "updateConfiguration", configureEnabled: configureEnabled]
	if (configureEnabled) {
		app?.updateSetting("configureEnabled", [type:"bool", value: false])
		configureChildren()
		runIn(600, configureEnable)
		logData << [status: "executing configureChildren"]
	} else {
		logData << [status: "notRun", data: "method rn with past 10 minutes"]
	}
	logInfo(logData)
	return logData
}

def configureEnable() {
	logDebug("configureEnable: Enabling configureDevices")
	app?.updateSetting("configureEnabled", [type:"bool", value: true])
}

def configureChildren() {
	state.devices = [:]
	def await = findDevices(5)
	runIn(2, updateChildren)
}

def updateChildren() {
	Map logData = [method: "updateChildren"]
	def children = getChildDevices()
	def devices = state.devices
	children.each { childDev ->
		Map childData = [:]
		def device = devices.find { it.value.dni == childDev.getDeviceNetworkId() }
		if (device == null) {
			logData << [error: "Child device not found in local database", 
						action:  "remove device or resolve LAN issue",
						note: "See integration documentation."]
			logWarn(logData)
		} else {
			childDev.updateAttr("commsError", "false")
			childData << [commsError: "false"]
			if (childDev.getDataValue("deviceIP") != device.value.ip ||
				childDev.getDataValue("devicePort") != device.value.port.toString()) {
				childDev.updateDataValue("deviceIP", device.value.ip)
				childDev.updateSetting("manualIp", [type:"string", value: device.value.ip])
				childDev.updateDataValue("devicePort", device.value.port.toString())
				childDev.updateSetting("manualPort", [type:"string", value: device.value.port.toString()])
				childData << [ip: device.value.ip, port: device.value.port]
			}
		}
		logData << ["${childDev}": childData]
	}
	logInfo(logData)
	return
}

def syncBulbPresets(bulbPresets) {
	logDebug("syncBulbPresets")
	def devices = state.devices
	devices.each {
		def type = it.value.type
		if (type == "Kasa Color Bulb" || type == "Kasa Light Strip") {
			def child = getChildDevice(it.value.dni)
			if (child) {
				child.updatePresets(bulbPresets)
			}
		}
	}
}

def resetStates(deviceNetworkId) {
	logDebug("resetStates: ${deviceNetworkId}")
	def devices = state.devices
	devices.each {
		def type = it.value.type
		def dni = it.value.dni
		if (type == "Kasa Light Strip") {
			def child = getChildDevice(dni)
			if (child && dni != deviceNetworkId) {
				child.resetStates()
			}
		}
	}
}

def syncEffectPreset(effData, deviceNetworkId) {
	logDebug("syncEffectPreset: ${effData.name} || ${deviceNetworkId}")
	def devices = state.devices
	devices.each {
		def type = it.value.type
		def dni = it.value.dni
		if (type == "Kasa Light Strip") {
			def child = getChildDevice(dni)
			if (child && dni != deviceNetworkId) {
				child.updateEffectPreset(effData)
			}
		}
	}
}

def coordinate(cType, coordData, deviceId, plugNo) {
	logDebug("coordinate: ${cType}, ${coordData}, ${deviceId}, ${plugNo}")
	def plugs = state.devices.findAll{ it.value.deviceId == deviceId }
	plugs.each {
		if (it.value.plugNo != plugNo) {
			def child = getChildDevice(it.value.dni)
			if (child) {
				child.coordUpdate(cType, coordData)
				pauseExecution(200)
			}
		}
	}
}

private sendLanCmd(ip, port, command, action, commsTo = 5, ignore = false) {
	def myHubAction = new hubitat.device.HubAction(
		command,
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${ip}:${port}",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 ignoreResponse: ignore,
		 parseWarning: true,
		 timeout: commsTo,
		 callback: action])
	try {
		sendHubCommand(myHubAction)
	} catch (error) {
		logWarn("sendLanCmd: command to ${ip}:${port} failed. Error = ${error}")
	}
	return
}

def parseLanData(response) {
	def resp = parseLanMessage(response.description)
	if (resp.type == "LAN_TYPE_UDPCLIENT") {
		def ip = convertHexToIP(resp.ip)
		def port = convertHexToInt(resp.port)
		def clearResp = inputXOR(resp.payload)
		def cmdResp
		try {
			cmdResp = new JsonSlurper().parseText(clearResp).system.get_sysinfo
		} catch (err) {
			if (clearResp.contains("child_num")) {
				clearResp = clearResp.substring(0,clearResp.indexOf("child_num")-2) + "}}}"
			} else if (clearResp.contains("children")) {
				clearResp = clearResp.substring(0,clearResp.indexOf("children")-2) + "}}}"
			} else if (clearResp.contains("preferred")) {
				clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}"
			} else {
				logWarn("parseLanData: [error: msg too long, data: ${clearResp}]")
				return [error: "error", reason: "message to long"]
			}
			cmdResp = new JsonSlurper().parseText(clearResp).system.get_sysinfo
		}
		return [cmdResp: cmdResp, ip: ip, port: port]
	} else {
		return [error: "error", reason: "not LAN_TYPE_UDPCLIENT", respType: resp.type]
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

private Integer convertHexToInt(hex) { Integer.parseInt(hex,16) }
