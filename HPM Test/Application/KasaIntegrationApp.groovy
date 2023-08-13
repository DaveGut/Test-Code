/*	Kasa Local Integration

		Copyright Dave Gutheinz

License Information:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md

Changes since version 6:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Version%206%20Change%20Log.md

===== Version 6.5.0 =====
1.	Added capability to enter multiple ports in support of Port Forwarding
2.	Fixed internal comms to use variable vs hard-coded port.
3.	Updated device database, addDevices, and updateDevices to add port to device data.
4.	Removed some configuration items to simplify installation.
6.5.1	Updated error handling and range checking.
6.5.2	a.	Added configurationsUpdate method to control update of app and all children configurations.
			1)	Checks and notifies on App not at current version using HPM Manifest Data.
				*	Adds "Update Available" to name if a new version is available.
			2)	Checks and notifies on device version not equal to current version or not equal to App version.
			3)	Configures children and app to support the configuration capability.
			4)	Polls devices for current IP and update children accordingly.
				*	Fixed the update method to properly handle multi-plug devices.
		b.	Update childUpdate to not change IP if the detected version is "CLOUD".
===================================================================================================*/
def appVersion() { return "6.5.2" }
import groovy.json.JsonSlurper

definition(
	name: "Kasa Integration",
	namespace: "davegut",
	author: "Dave Gutheinz",
	description: "Application to install TP-Link bulbs, plugs, and switches.",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	singleInstance: true,
	documentationLink: "https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/README.md",
	importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/Application/KasaIntegrationApp.groovy"
)

preferences {
	page(name: "initInstance")
	page(name: "startPage")
	page(name: "kasaAuthenticationPage")
	page(name: "addDevicesPage")
	page(name: "removeDevicesPage")
	page(name: "listDevicesByIp")
	page(name: "listDevicesByName")
	page(name: "startGetToken")
	page(name: "commsTest")
	page(name: "commsTestDisplay")
	page(name: "dbReset")
	page(name: "addDevStatus")
}

def installed() { updated() }

def updated() {
	logInfo("updated: Updating device configurations and (if cloud enabled) Kasa Token")
	unschedule()
	app?.updateSetting("appSetup", [type:"bool", value: false])
	app?.updateSetting("utilities", [type:"bool", value: false])
	app?.updateSetting("debugLog", [type:"bool", value: false])
	def installedState = app.getInstallationState()
	if (userName && userName != "") {
		schedule("0 30 2 ? * MON,WED,SAT", schedGetToken)
	}
	updateConfigurations(true)
}

def uninstalled() {
    getAllChildDevices().each { 
        deleteChildDevice(it.deviceNetworkId)
    }
}

def initInstance() {
	logDebug("initInstance: Getting external data for the app.")
	if (!debugLog) { app.updateSetting("debugLog", false) }
	if (!state.devices) { state.devices = [:] }
	if (!lanSegment) {
		def hub = location.hubs[0]
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
	getManifestData()
	startPage()
}

//	===== Page Methods =====
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
		
	if (debugLog) { runIn(1800, debugOff) }

	return dynamicPage(name:"startPage",
					   title:"<b>Kasa Local Hubitat Integration, Version ${appVersion()}</b>" +
					   		 "\n(Instructions available using <b>?</b> at upper right corner.)",
					   uninstall: true,
					   install: true) {
		section() {
			if (state.updateAvailable) {
				paragraph "<b>App update available.</b>  Manifest: ${state.manifestData}"
			}
			if (appSetup) {
				href "kasaAuthenticationPage",
					title: "<b>Kasa Login and Token Update</b>",
					description: "Click to enter credentials and get token"
				def note = "(After running Kasa Login and Token Update, refresh this page.)\n"
				note += "<b>Run install to enable the Cloud interface.</b>"
				paragraph note
				
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
			paragraph "<b>Current Configuration:  token</b> = ${kasaToken},  " +
				"<b>cloudUrl</b> = ${kasaCloudUrl},  <b>LanSegments</b> = ${state.segArray},  " +
				"<b>Ports</b> = ${state.portArray},  <b>hostRange</b> = ${state.hostArray}"
			input "appSetup", "bool",
				title: "<b>Modify Configuration</b>",
				submitOnChange: true,
				defaultalue: false

			href "addDevicesPage",
				title: "<b>Install Kasa Devices</b>",
				description: "Also updates all device IP addresses."

			href "removeDevicesPage",
				title: "<b>Remove Kasa Devices</b>",
				description: "Click to select and delete devices"

			input "utilities", "bool",
				   title: "<b>Application Utilities</b>",
				   submitOnChange: true,
				   defaultalue: false
	
			if (utilities == true) {
				href "listDevicesByIp",
					title: "<b>List All Kasa Devices with IP Address.</b>",
					description: "Click to get List"

				href "listDevicesByName",
					title: "<b>List All Kasa Devices by Name</b>",
					description: "Click to get List"

				href "commsTest", title: "<b>IP Comms Test Tool</b>",
					description: "Click to go to IP Comms Test"
				href "dbReset", title: "<b>Reset the Device Database</b>",
					description: "Click to reset the device database"
			}
			input "debugLog", "bool",
				   title: "<b>Enable debug logging for 30 minutes</b>",
				   submitOnChange: true,
				   defaultValue: false
		}
	}
}

def kasaAuthenticationPage() {
	logInfo("kasaAuthenticationPage")
	return dynamicPage (name: "kasaAuthenticationPage", 
    					title: "Initial Kasa Login Page, Version ${appVersion()}",
						nextPage: startPage,
                        install: false) {
        section("Enter Kasa Account Credentials: ") {
			input ("userName", "email",
            		title: "TP-Link Kasa Email Address", 
                    required: true,
                    submitOnChange: true)
			input ("userPassword", "password",
            		title: "TP-Link Kasa Account Password",
                    required: true,
                    submitOnChange: true)
			if (userName && userPassword && userName != null && userPassword != null) {
				href "startGetToken", title: "Get or Update Kasa Token", 
					description: "Tap to Get Kasa Token"
			}
			paragraph "Select  '<'  at upper left corner to exit."
		}
	}
}

def startGetToken() {
	logInfo("getTokenFromStart: Result = ${getToken()}")
	startPage()
}

def addDevicesPage() { 
	logDebug("addDevicesPage")
	def action = findDevices()
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
	def reqDrivers = "1.\t<b>Ensure the following drivers are installed:</b>"
	requiredDrivers.each {
		reqDrivers += "\n\t\t${it.key}"
	}
	def pageInstructions = "<b>Before Installing New Devices</b>\n"
///	pageInstructions += "1.\t<b>Assure the drivers below are installed.</b>\n"
	pageInstructions += "${reqDrivers}\n"
	pageInstructions += "2.\t<b>Assign Static IP Addresses.</b>\n"
	pageInstructions += "<b>Note</b> If devices are missing, try again (sometimes "
	pageInstructions += "devices are not detected. Also, consider logging into the "
	pageInstructions += "Kasa Cloud. Some devices can not be reached over the LAN."

	return dynamicPage(name:"addDevicesPage",
					   title: "Add Kasa Devices to Hubitat, Version ${appVersion()}",
					   nextPage: addDevStatus,
					   install: false) {
	 	section() {
			paragraph pageInstructions
			input ("selectedAddDevices", "enum",
				   required: false,
				   multiple: true,
				   title: "Devices to add (${uninstalledDevices.size() ?: 0} available).",
				   description: "Use the dropdown to select devices.  Then select 'Done'.",
				   options: uninstalledDevices)
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
	if (state.failedAdds == null) {
		failMsg += "Failed Adds: No devices failed to add."
	} else {
		failMsg += "<b>The following devices were not installed:</b>\n"
		state.failedAdds.each{
			failMsg += "\t${it}\n"
		}
		failMsg += "\t<b>Most common failure cause: Driver not installed.</b>"
	}
			
	return dynamicPage(name:"addDeviceStatus",
					   title: "Installation Status, Version ${appVersion()}",
					   nextPage: listDevicesByName,
					   install: false) {
	 	section() {
			paragraph addMsg
			paragraph failMsg
		}
	}
	app?.removeSetting("selectedAddDevices")
}

def removeDevicesPage() {
	logInfo("removeDevicesPage")
	def devices = state.devices
	def installedDevices = [:]
	devices.each {
		def installed = false
		def isChild = getChildDevice(it.value.dni)
		if (isChild) {
			installedDevices["${it.value.dni}"] = "${it.value.alias}, type = ${it.value.type}, dni = ${it.value.dni}"
		}
	}
	logDebug("removeDevicesPage: newDevices = ${newDevices}")
	return dynamicPage(name:"removedDevicesPage",
					   title:"<b>Remove Kasa Devices from Hubitat</b>",
					   nextPage: startPage,
					   install: false) {
		section("Select Devices to Remove from Hubitat, Version ${appVersion()}") {
			input ("selectedRemoveDevices", "enum",
				   required: false,
				   multiple: true,
				   title: "Devices to remove (${installedDevices.size() ?: 0} available)",
				   description: "Use the dropdown to select devices.  Then select 'Done'.",
				   options: installedDevices)
		}
	}
}

def listDevicesByIp() {
	logInfo("listDevicesByIp")
	def theList = ""
	def devices = state.devices
	if (devices == null) {
		theList += "<b>No devices in the device database.</b>"
	} else {
		theList += "<b>Total Kasa devices: ${devices.size() ?: 0}</b>\n"
		def deviceList = []
		theList +=  "<b>[DeviceIp:Port: [Alias, DeviceType, driverVersion, Installed</b>]\n"
		devices.each{
			def driverVer = "ukn"
			def installed = "No"
			def isChild = getChildDevice(it.key)
			if (isChild) {
				driverVer = isChild.getDataValue("driverVersion")
				installed = "Yes"
			}
			def type = it.value.type.replace("Kasa ","")
			deviceList << "<b>${it.value.ip}:${it.value.port}</b>: [${it.value.alias}, ${type}, ${driverVer}, ${installed}]"
		}
		deviceList.sort()
		deviceList.each {
			theList += "${it}\n"
		}
	}
	return dynamicPage(name:"listDevicesByIp",
					   title: "List Kasa Devices with IP Address, Version ${appVersion()}",
					   nextPage: startPage,
					   install: false) {
	 	section() {
			paragraph theList
		}
	}
}

def listDevicesByName() { 
	logInfo("listDevicesByName")
	state.listDevices = [:]
	def devices = state.devices
	def theList = ""
	if (devices == null) {
		theList += "<b>No devices in the device database.</b>"
	} else {
		theList += "<b>Total Kasa devices:   ${devices.size() ?: 0}</b>\n"
		theList += "<b>Alias: [DeviceType, DeviceIp:Port, DriverVersion, Installed]</b>\n"
		def deviceList = []
		devices.each{
			def driverVer = "ukn"
			def installed = "No"
			def isChild = getChildDevice(it.key)
			if (isChild) {
				driverVer = isChild.getDataValue("driverVersion")
				installed = "Yes"
			}
			def type = it.value.type.replace("Kasa ","")
			deviceList << "<b>${it.value.alias}</b>: [${type}, ${it.value.ip}:${it.value.port}, ${driverVer}, ${installed}]"
		}
		deviceList.sort()
		deviceList.each {
			theList += "${it}\n"
		}
	}
	return dynamicPage(name:"listDevicesByName",
					   title: "List Kasa Devices by DeviceName, Version ${appVersion()}",
					   nextPage: startPage,
					   install: false) {
	 	section() {
			paragraph theList
		}
	}
}

def commsTest() {
	logInfo("commsTest")
	return dynamicPage(name:"commsTest",
					   title: "IP Communications Test, Version ${appVersion()}",
					   nextPage: startPage,
					   install: false) {
	 	section() {
			input "testIp", "string",
				title: "<b>IP Address to Test</b>",
				required: false,
				submitOnChange: true
			input "testPort", "string",
				title: "<b>Port to Test</b>",
				required: false,
				defaultValue: "9999",
				submitOnChange: true
			if (testIp && testIp != null) {
				href "commsTestDisplay", title: "<b>Test IP Address</b>",
					description: "Click to Test IP Comms."
			}
		}
	}
}

def commsTestDisplay() {
	def pingData = sendPing(testIp, 3)
	def success = 100 * (1 - pingData.packetLoss.toInteger()/3)
	def text = "<b>Ping Test</b>\n"
	text += "<b>\t   Time(min):</b>\t\t${pingData.rttMin}\n"
	text += "<b>\t  Time(max):</b>\t\t${pingData.rttMax}\n"
	text += "<b>\tSuccess(%):</b>\t\t${success.toInteger()}%\n\n"
	sendLanCmd(testIp, testPort, """{"system":{"get_sysinfo":{}}}""", "commsTestParse")
	pauseExecution(3000)
	text += "<b>Device Command Test:</b>\t${state.commsTest}"
	return dynamicPage(name:"commsTestDisplay",
					   title: "Communications Testing Results for IP = ${testIp}, Version ${appVersion()}",
					   nextPage: commsTest,
					   install: false) {
		section() {
			paragraph text
		}
	}
}
	
def dbReset() {
	logInfo("dbReset")
	def action = findDevices()
	return dynamicPage(name:"dbReset",
					   title: "Reset the Kasa Device Database, Version ${appVersion()}",
					   nextPage: listDevicesByIp,
					   install: false) {
		def notice = "The device database has been reset and devices rediscovered."
	 	section() {
			paragraph notice
		}
	}
}

//	===== Generate the device database =====
def findDevices() {
	state.devices = [:]
	def start = state.hostArray.min().toInteger()
	def finish = state.hostArray.max().toInteger() +1
	logDebug("findDevices: [hostArray: ${state.hostArray}, portArray: ${state.portArray}, pollSegment: ${state.segArray}]")
	state.portArray.each {
		def port = it.trim()
		state.segArray.each {
			def pollSegment = it.trim()
			logDebug("findDevices: [segment: ${pollSegment}, port: ${port}]")
			for(int i = start; i < finish; i++) {
				def deviceIP = "${pollSegment}.${i.toString()}"
				sendLanCmd(deviceIP, port, """{"system":{"get_sysinfo":{}}}""", "getLanData")
				pauseExecution(100)
			}
		}
	}
	if (kasaToken && userName != "") {
		logDebug("findDevices: ${cloudGetDevices()}")
	} else {
		def msg = "findDevice: \n<b>No Kasa Token available.</b> CLOUD wasn't polled. "
		msg += "If you need to poll the cloud, do the following and try again:"
		msg += "\n\ta.\tGo to Kasa Login and Token Update"
		msg += "\n\tb.\tRefresh your browser."
		msg += "\n\tc.\tVerify Current Configuration token is not null."
		msg += "\nIf you do not need to poll cloud, no action is required.\n\r"
		logWarn(msg)
	}
		
	runIn(20,updateChildren)
	return
}

def getLanData(response) {
	def lanData = parseLanData(response)
	if (lanData.error) { return }
	parseDeviceData(lanData.cmdResp, lanData.ip, lanData.port)
}

def cloudGetDevices() {
	logInfo("cloudGetDevices ${kasaToken}")
	def message = ""
	def cmdData = [uri: "https://wap.tplinkcloud.com?token=${kasaToken}", 
				   cmdBody: [method: "getDeviceList"]]
	def respData = sendKasaCmd(cmdData)
	def cloudDevices
	def cloudUrl
	if (respData.error_code == 0) {
		cloudDevices = respData.result.deviceList
		cloudUrl = ""
	} else {
		message = "Devices not returned from Kasa Cloud."
		logWarn("cloudGetDevices: <b>Devices not returned from Kasa Cloud.</b> Return = ${respData}\n\r")
		return message
	}
	cloudDevices.each {
		if (it.deviceType != "IOT.SMARTPLUGSWITCH" && it.deviceType != "IOT.SMARTBULB") {
			logInfo("<b>cloudGetDevice: Ignore device type ${it.deviceType}.")
		} else if (it.status == 0) {
			logInfo("cloudGetDevice: Device name ${it.alias} is offline and not included.")
			cloudUrl = it.appServerUrl
		} else {
			cloudUrl = it.appServerUrl
			def cmdBody = [
				method: "passthrough",
				params: [
					deviceId: it.deviceId,
					requestData: """{"system":{"get_sysinfo":{}}}"""]]
			cmdData = [uri: "${cloudUrl}/?token=${kasaToken}",
					   cmdBody: cmdBody]
			def cmdResp
			respData = sendKasaCmd(cmdData)
			if (respData.error_code == 0) {
				def jsonSlurper = new groovy.json.JsonSlurper()
				cmdResp = jsonSlurper.parseText(respData.result.responseData)
				parseDeviceData(cmdResp.system.get_sysinfo)
			} else {
				message = "Data for one or more devices not returned from Kasa Cloud.\n\r"
				logWarn("cloudGetDevices: <b>Device datanot returned from Kasa Cloud.</b> Return = ${respData}\n\r")
				return message
			}
		}
	}
	message += "Available device data sent to parse methods.\n\r"
	if (cloudUrl != "" && cloudUrl != kasaCloudUrl) {
		app?.updateSetting("kasaCloudUrl", cloudUrl)
		message += " kasaCloudUrl uptdated to ${cloudUrl}."
	}
	return message
}

def parseDeviceData(cmdResp, ip = "CLOUD", port = "CLOUD") {
	logDebug("parseDeviceData: ${cmdResp} //  ${ip} // ${port}")
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
	}
	def model = cmdResp.model.substring(0,5)
	def alias = cmdResp.alias
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
			def existingDev = devices.find { it.key == childDni }
			if (existingDev) {
				if (ip == "CLOUD" || ip == existingDev.value.ip) {
					logInfo("parseDeviceData: ${existingDev.value.alias} already in devices array.")
					return
				}
			}
			def device = createDevice(childDni, ip, port, type, feature, model, alias, deviceId, plugNo, plugId)
			devices << ["${childDni}" : device]
			logInfo("parseDeviceData: ${type} ${alias} (${ip}) added to devices array.")
		}
	} else {
		def existingDev = devices.find { it.key == dni }
		if (existingDev) {
			if (ip == "CLOUD" || ip == existingDev.value.ip) {
				logInfo("parseDeviceData: ${existingDev.value.alias} already in devices array.")
				return
			}
		}
		def device = createDevice(dni, ip, port, type, feature, model, alias, deviceId, plugNo, plugId)
		devices << ["${dni}" : device]
		logInfo("parseDeviceData: ${type} ${alias} (${ip}) added to devices array.")
	}
}

def createDevice(dni, ip, port, type, feature, model, alias, deviceId, plugNo, plugId) {
	logDebug("createDevice: dni = ${dni}")
	def device = [:]
	device["dni"] = dni
	device["ip"] = ip
	device["port"] = port
	device["type"] = type
	device["feature"] = feature
	device["model"] = model
	device["alias"] = alias
	device["deviceId"] = deviceId
	if (plugNo != null) {
		device["plugNo"] = plugNo
		device["plugId"] = plugId
	}
	return device
}

def updateChildren() {
	def devices = state.devices
	devices.each {
		def child = getChildDevice(it.key)
		if (child) {
			if (it.value.ip != null || it.value.ip != "" || it.value.ip != "CLOUD") {
				child.updateDataValue("deviceIP", it.value.ip)
				child.updateDataValue("devicePort", it.value.port.toString())
				child.updateDataValue("feature", it.value.feature)
				def logData = [deviceIP: it.value.ip,port: it.value.port, 
							   feature: it.value.feature]
				logDebug("updateChildDeviceData: [${it.value.alias}: ${logData}]")
			}
		}
	}
}

//	===== Application Utility Methods =====
def addDevices() {
	logInfo("addDevices: ${selectedAddDevices}")
	def hub = location.hubs[0]
	state.addedDevices = []
	state.failedAdds = []
	selectedAddDevices.each { dni ->
		//	See if any installing devices are IP = CLOUD. 
		//	If so, set useKasaCloud to true so device can be controlled.
		def isChild = getChildDevice(dni)
		if (!isChild) {
			def device = state.devices.find { it.value.dni == dni }
			def deviceData = [:]
			deviceData["deviceIP"] = device.value.ip
			deviceData["devicePort"] = device.value.port
			deviceData["plugNo"] = device.value.plugNo
			deviceData["plugId"] = device.value.plugId
			deviceData["deviceId"] = device.value.deviceId
			deviceData["model"] = device.value.model
			deviceData["feature"] = device.value.feature
			try {
				addChildDevice(
					"davegut",
					device.value.type,
					device.value.dni,
					hub.id, [
						"label": device.value.alias,
						"name" : device.value.type,
						"data" : deviceData
					]
				)
				state.addedDevices << [label: device.value.alias, ip: device.value.ip]
				logInfo("Installed ${device.value.alias}.")
			} catch (error) {
				state.failedAdds << [label: device.value.alias, driver: device.value.type, ip: device.value.ip]
				def msg = "addDevice: \n<b>Failed to install device.</b> Most likely "
				msg += "could not find driver <b>${device.value.type}</b> in the "
				msg += "Hubitat Drivers Code page.  Check that page."
				msg += "\nAdditional data: Device Data = ${device}.\n\r"
				logWarn(msg)
			}
		}
		pauseExecution(3000)
	}
	app?.removeSetting("selectedAddDevices")
}

def commsTestParse(response) {
	def resp = parseLanMessage(response.description)
	if (resp.type == "LAN_TYPE_UDPCLIENT") {
		state.commsTest = "PASS"
	} else {
		state.commsTest = "FAIL"
	}
}
	
def getToken() {
	logInfo("getToken ${userName}")
	app?.removeSetting("kasaToken")
	def message = ""
	def hub = location.hubs[0]
	def cmdBody = [
		method: "login",
		params: [
			appType: "Kasa_Android",
			cloudUserName: "${userName}",
			cloudPassword: "${userPassword}",
			terminalUUID: "${hub.id}"]]
	cmdData = [uri: "https://wap.tplinkcloud.com",
			   cmdBody: cmdBody]
	def respData = sendKasaCmd(cmdData)
	if (respData.error_code == 0) {
		app?.updateSetting("kasaToken", respData.result.token)
		message = "Token updated to ${respData.result.token}"
	} else {
		message = "Token not updated.  See WARN message in Log."
		logWarn("getToken: <b>Token not updated.</b> Return = ${respData}\n\r")
	}
	return message
}

def removeDevices() {
	logDebug("removeDevices: ${selectedRemoveDevices}")
	def devices = state.devices
	selectedRemoveDevices.each { dni ->
		def device = state.devices.find { it.value.dni == dni }
		def isChild = getChildDevice(dni)
		if (isChild) {
			try {
				deleteChildDevice(dni)
				logInfo("Deleted ${device.value.alias}")
			} catch (error) {
				logWarn("Failed to delet ${device.value.alias}.")
			}
		}
	}
	app?.removeSetting("selectedRemoveDevices")
}

def schedGetToken() {
	logInfo("schedGetToken: Result = ${getToken()}")
}

//	===== Device Service Methods =====
def updateConfigurations(force = false) {
	def msg = ""
	if (configureEnabled == true || configureEnabled == null || force) {
		app?.updateSetting("configureEnabled", [type:"bool", value: false])
		runIn(900, configureEnable)
		runIn(3, configureChildren)
		msg += "Updating App and all device configurations"
	} else {
		msg += "<b>Not executed</b>.  Ran method witin last 15 minutes."
	}
	logInfo("updateConfigurations: ${msg}")
	return msg
}

def configureEnable() {
	logDebug("configureEnable: Enabling configureDevices")
	app?.updateSetting("configureEnabled", [type:"bool", value: true])
}

def configureChildren() {
	schedule("15 05 1 * * ?", updateConfigurations)
	runIn(3, execFixConnection, [data: "LAN"])
	def manifestData = getManifestData()
	def children = getChildDevices()
	children.each {
		it.childConfigure(manifestData)
		pauseExecution(1000)
	}
}




////////////////////////////////////////////////////////////////
def getManifestData() {
	logDebug("getManifestData: updateManifest = ${state.updateManifest}")
	def msg = "getManifestData: "
	def manifestData = [currVersion: appVersion().trim(), releaseNotes: "Default",
						updateAvailable: false, appVersion: appVersion().trim()]

	

////////////////////////////////////testCode	
//	def params = [uri: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/KasaDevices/packageManifest.json", 
//				  contentType: "text/plain; charset=UTF-8"]
	def params = [uri: "https://raw.githubusercontent.com/DaveGut/Test-Code/master/packageManifest.json", 
				  contentType: "text/plain; charset=UTF-8"]

	
	def manifest
	try {
		httpGet(params) { resp ->
			manifest = new JsonSlurper().parseText(resp.data.text)
		}
	} catch (e) {
		logWarn("getManifestData: ${e}")
	}

	def updateAvailable = false
	if (manifest.version.trim() != appVersion().trim()) {
		updateAvailable = true
		app.updateLabel("Kasa Integration <span style='color:green'>Update Available</span>")
		msg += "\n\t\t\t* App update available."
	} else if (app.getLabel() != "Kasa Integration"){
		app.updateLabel("Kasa Integration")
		msg += "\n\t\t\t* App is up to date and name set to default."
	}
	manifestData = [currVersion: manifest.version.trim(),
					releaseNotes: manifest.releaseNotes,
					updateAvailable: updateAvailable,
					appVersion: appVersion().trim()]
	state.manifestData = manifestData
	state.updateAvailable = updateAvailable
	msg += "\n\t\t\t* ${manifestData}"
	logInfo(msg)
	return manifestData
}




def fixConnection(type) {
	def msg = "fixConnection: "
	if (pollEnabled == true || pollEnabled == null) {
		def execFix = execFixConnection(type)
		msg += "Checking and updating all device IPs."
	} else {
		msg += "Not executed.  Ran method witin last 15 minutes"
	}
	logInfo(msg)
	return msg
}

def pollEnable() {
	logDebug("pollEnable: Enabling IP check from device error.")
	app?.updateSetting("pollEnabled", [type:"bool", value: true])
}

def execFixConnection(type) {
	def message = ""
	app?.updateSetting("pollEnabled", [type:"bool", value: false])
	runIn(900, pollEnable)
	if (type == "LAN") {
		def pollSegment
		state.portArray.each {
			def port = it.trim()
			state.segArray.each {
				pollSegment = it.trim()
				for(int i = 1; i < 255; i++) {
					def deviceIP = "${pollSegment}.${i.toString()}"
					sendLanCmd(deviceIP, port, """{"system":{"get_sysinfo":{}}}""", "updateDeviceIp")
					pauseExecution(100)
				}
			}
		}
		message = "updated IPs on segments ${state.segArray}"
	} else if (type == "CLOUD") {
		message = "updated token: ${getToken()}."
	} else {
		message = "FAILED.  Control type neither LAN nor CLOUD."
	}
	logInfo("execFixConnection: ${message}")
	return message
}

def updateDeviceIp(response) {
	def lanData = parseLanData(response)
	if (lanData.error) { return }
	def ip = lanData.ip
	def cmdResp = lanData.cmdResp
	def dni
	if (cmdResp.mic_mac) { dni = cmdResp.mic_mac }
	else { dni = cmdResp.mac.replace(/:/, "") }
	def alias = cmdResp.alias
		
	if (cmdResp.children) {
		def childPlugs = cmdResp.children
		childPlugs.each {
			plugNo = it.id.substring(it.id.length() - 2)
			def childDni = "${dni}${plugNo}"
			plugId = "${deviceId}${plugNo}"
			alias = it.alias
			updateIp(childDni, ip, alias)
		}
	} else {
		updateIp(dni, ip, alias)
	}
}

def updateIp(dni, ip, alias) {
	def isChild = getChildDevice(dni)
	if (isChild) {
		isChild.updateDataValue("deviceIP", ip)
		logInfo("updateDeviceIp: ${alias} IP set to ${ip}")
	}
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
				pauseExecution(700)
			}
		}
	}
}

//	===== Communications Methods =====
def sendPing(ip, count = 1) {
	hubitat.helper.NetworkUtils.PingData pingData = hubitat.helper.NetworkUtils.ping(ip, count)
	return pingData
}

private sendLanCmd(ip, port, command, action) {
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command),
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${ip}:${port}",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 parseWarning: true,
		 timeout: 15,
		 callback: action])
	try {
		sendHubCommand(myHubAction)
	} catch (error) {
		logWarn("sendLanCmd: command to ${ip}:${port} failed. Error = ${error}")
	}
}

def parseLanData(response) {
	def lanData
	def resp = parseLanMessage(response.description)
	if (resp.type == "LAN_TYPE_UDPCLIENT") {
		def clearResp = inputXOR(resp.payload)
		if (clearResp.length() > 1022) {
			clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}"
		}
		def ip = convertHexToIP(resp.ip)
		def port = convertHexToInt(resp.port)
		def cmdResp = new JsonSlurper().parseText(clearResp).system.get_sysinfo
		lanData = [cmdResp: cmdResp, ip: ip, port: port]
	} else {
		lanData = [error: "error"]
	}
	return lanData
}

def sendKasaCmd(cmdData) {
	def commandParams = [
		uri: cmdData.uri,
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01'],
		body : new groovy.json.JsonBuilder(cmdData.cmdBody).toString()
	]
	def respData
	try {
		httpPostJson(commandParams) {resp ->
			if (resp.status == 200) {
				respData = resp.data
			} else {
				def msg = "sendKasaCmd: <b>HTTP Status not equal to 200.  Protocol error.  "
				msg += "HTTP Protocol Status = ${resp.status}"
				logWarn(msg)
				respData = [error_code: resp.status, msg: "HTTP Protocol Error"]
			}
		}
	} catch (e) {
		def msg = "sendKasaCmd: <b>Error in Cloud Communications.</b> The Kasa Cloud is unreachable."
		msg += "\nAdditional Data: Error = ${e}\n\n"
		logWarn(msg)
		respData = [error_code: 9999, msg: e]
	}
	return respData
}

//	===== Utility Methods =====
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

def logTrace(msg){ log.trace "[KasaInt/${appVersion()}] ${msg}" }

def logDebug(msg){
	if(debugLog == true) { log.debug "[KasaInt/${appVersion()}] ${msg}" }
}

def logInfo(msg){ log.info "[KasaInt/${appVersion()}] ${msg}" }

def logWarn(msg) { log.warn "[KasaInt/${appVersion()}] ${msg}" }

//	end-of-file