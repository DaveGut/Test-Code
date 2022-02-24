/*	===== HUBITAT INTEGRATION VERSION =====================================================
Hubitat - ST Integration Template
		Copyright 2022 Dave Gutheinz
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file
except in compliance with the License. You may obtain a copy of the License at:
		http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the
License is distributed on an  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. See the License for the specific language governing permissions
and limitations under the  License.
===== DISCLAIMERS =========================================================================
	THE AUTHOR OF THIS INTEGRATION IS NOT ASSOCIATED WITH SAMSUNG.
===== Narrative ===========================================================================
This driver is developed as a template for programmers to access and use the SmartThings
interface for those items that just do not have Hubitat interfaces.  Using this does NOT
require an active ST Hub (I do not have one), but does require a ST account and phone app
(to add devices and helps in testing).

There are several sections with appropriate instructions embedded in the code.  The main
sections are metadata, update, commands, parsing (attributes) (using Samsung TV as an example), and 
ST API interface code (converts user code to Samsung commands.

Note: I am at best an average programmer.  I have attempted to make this code as clean as
possible - but allow me my errors.
Installation Instructions (after creating device)
a.	Get ST Token from site https://account.smartthings.com/login?redirect=https%3A%2F%2Faccount.smartthings.com%2Ftokens
b.	Enter deviceIp and stApiKey into Preferences.
c.	Save preferences will indicate failed (no stDeviceId). This is expected until you get the id.
d.	Select command listStDevices, find your device on the list and enter the deviceID into preferences.
	Note: The list also contains the SmartThings Capabilities for your device.  That information is used at
	at st Capability web page to get the device commands and attributes for your integration. Site:
	https://developer-preview.smartthings.com/docs/devices/capabilities/capabilities-reference/.
e.	The final save preferences should update your device attributes.


===========================================================================================*/
import groovy.json.JsonOutput

metadata {
	definition (name: "ST Integration Template",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: ""
			   ){
/*
Capabilities are Hubitat Capabilities. A cross mapping of desired ST Capability/Commands
is required.
For this exercise, I will use Hubitat Capabilities Switch, Refresh, and AudioVolume vs a
Samsung TV (2020 model).
*/
		capability "Switch"				//	On/Off
		capability "Refresh"
		capability "Audio Volume"		//	setVolume, mute, unmute, volumeUp, volumeDown
		command "testCode"				//	A play area for testing code prior to making formal method.
		command "getStDeviceList"			//	Generate a list of ST Devices with deviceId and capabilities
		command "getStDeviceStatus"		//	Gets the current status of the set device (deviceId).
										//	Sends the data the parse method (as designed).
		
	}
	preferences {
		input ("deviceIp", "text", title: "Samsung TV Ip", defaultValue: "")
		input ("stApiKey", "text", title: "SmartThings API Key", defaultValue: "")
		input ("stDeviceId", "text", title: "SmartThings TV Device ID", defaultValue: "")
	}
}

//	===== Installation, setup and update =====
def installed() {
	sendEvent(name: "volume", value: 0)
	runIn(1, updated)
}

def updated() {
	sendEvent(name: "volume", value: 0)
	logInfo("updated")
	unschedule()
	def updateData = [:]
	def status = "OK"
	def statusReason = ""
	def deviceData
	if (deviceIp) {
		//	Get onOff status for use in setup
		updateData << [deviceIp: deviceIp]
		if (deviceIp != deviceIp.trim()) {
			deviceIp = deviceIp.trim()
			device.updateSetting("deviceIp", [type:"text", value: deviceIp])	
		}
		if (stDeviceId != "") {
			updateData << [STData: updateST()]
		} else {
			status = "failed"
			statusReason = "No stDeviceId"
		}
	} else {
		status = "failed"
		statusReason = "No device IP"
	}
	def updateStatus = [:]
	updateStatus << [status: status]
	if (statusReason != "") {
		updateStatus << [statusReason: statusReason]
	}
	updateStatus << [updateData: updateData]
	sendEvent(name: "volume", value: 0)
	logInfo("updated: ${updateStatus}")
/*	EXAMPLE LOGINFO after full success.
[status:OK, updateData:[deviceIp:192.168.50.239, STData:[status:OK, switch:on, volume:18, mute:unmuted]]]

*/
}

def updateST() {
//	completes an initial parse of the ST Attributes and update
//	Hubitat Attributes accordingly.
	def stData = getStDeviceStatus()
	return stData
}

def testCode() {
	logTrace("testCode")
}

/*	===== Command Definitions
Commands include three inputs sent to senStPost:
a.	Capability
b.	Command
c.	argunemts in format [arg1, arg2]
The command return is "Accepted" (no data).  To get the data
on refresh, I add the if statement followed by getStDeviceStatus().
*/
def refresh() {
	def respData = sendStPost("refresh", "refresh", [])
	if (respData.status == "OK") { getStDeviceStatus() }
}

//	Capability Switch
/*	===== Capability Switch
For most commands, the ST attribute is not immediately set based on 
the update.  Usually whenever the refresh is run in the ST app or
using the method refresh herein.  there are exceptions (i.e., on off).
Therefore, I set the attribute an the command being Accepted by ST.
*/
def on() {
	def respData = sendStPost("switch", "on", [])
	if (respData.status == "OK") {
		sendEvent(name: "switch", value: "on")
	}
}
def off() {
	def respData = sendStPost("switch", "off", [])
	if (respData.status == "OK") { 
		sendEvent(name: "switch", value: "off")
	}
}

//	Capability AudioVolume
def setVolume(volume) {
	def respData = sendStPost("audioVolume", "setVolume", [volume])
	if (respData.status == "OK") { 
		sendEvent(name: "volume", value: volume)
	}
}
def volumeUp() {
	if (device.currentValue("volume").toInteger() < 100) {
		def respData = sendStPost("audioVolume", "volumeUp", [])
		if (respData.status == "OK") {
			def newVolume = device.currentValue("volume").toInteger() + 1
			sendEvent(name: "volume", value: newVolume)
		}
	}
}
def volumeDown() {
	if (device.currentValue("volume").toInteger() > 0) {
		def respData = sendStPost("audioVolume", "volumeDown", [])
		if (respData.status == "OK") {
			def volume = device.currentValue("volume").toInteger() - 1
			sendEvent(name: "volume", value: volume)
		}
	}
}

//	Capability AudioMute
def mute() {
	sendStPost("audioMute", "mute", args = [])
	sendEvent(name: "mute", value: "muted")
}
def unmute() {
	sendStPost("audioMute", "unmute", args = [])
	sendEvent(name: "mute", value: "unmuted")
}

/*	getStDeviceList obtains a logging list of the ST devices and for each device
	provides deviceId (used in Preferences) and ST Capabilities (used in development.
Example LOG INFO for Samsung TV and Ring Doorbell


*/
def getStDeviceList() {
	def stDevices = listStDevices()	//	Calls method 
	if (stDevices.status == "error") {
		logWarn("getStDeviceList: Error: ${stDevices.statusReason}")
		return
	}
	logDebug("getStDeviceList")
	logInfo("=================================================")
	stDevices.each {
		def deviceData = [stLabel: it.label, deviceId: it.deviceId, deviceName: it.name]
		def components = it.components
		components.each {
			def compData = [compName: it.id]
			def capabilities = it.capabilities
			def capData = []
			capabilities.each {
				capData << it.id
			}
			compData << [capabilities: capData]
			deviceData << compData
		}
		logInfo("${deviceData}")
		logInfo("=================================================")
	}
}

/*
listStDevices: Returns a unformatted list of ST device with associated data.
used by getStDeviceList.
Example List: 
RING DOORBELL
[stLabel:Front Doorbell, deviceId:xxxxxxxxxxxxxxxxxxxxxx, 
 deviceName:c2c-ring-doorbell-battery-rtsp, compName:main, 
 capabilities:[refresh, healthCheck, videoStream, motionSensor, button, battery]
]
SAMSUNG TV
[stLabel:DenTV, deviceId:xxxxxxxxxxxxxxxxxxxxxxx,
 deviceName:[TV] DenTV, compName:main, 
 capabilities:[ocf, switch, audioVolume, audioMute, tvChannel, 
			   mediaInputSource, mediaPlayback, mediaTrackControl, 
			   custom.error, custom.picturemode, custom.soundmode, 
			   custom.accessibility, custom.launchapp, custom.recording, 
			   custom.tvsearch, custom.disabledCapabilities, 
			   samsungvd.ambient, samsungvd.ambientContent, 
			   samsungvd.ambient18, samsungvd.mediaInputSource, 
			   refresh, execute, samsungvd.firmwareVersion, 
			   samsungvd.supportsPowerOnByOcf
			  ]
]
*/

private listStDevices() {
	def stDevices = "[status: error, statusReason: undefined error]"
	if (!stApiKey) {
		stDevices = "[status: error, statusReason: no stAPIKey]"
	}
	def cmdUri = "https://api.smartthings.com/v1/devices"
	def sendCmdParams = [
		uri: cmdUri,
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01',
				 'Authorization': 'Bearer ' + stApiKey.trim()]
	]
	httpGet(sendCmdParams) {resp ->
		stDevices = resp.data.items
	}
	return stDevices
}

/*	Get ST Device Status.  Grabs and parses the ST Device Status.  Usually called
	from Refresh(after doing a refresh).  Can be used at any time.*/
private getStDeviceStatus() {
	def stData = [:]
	if (connectST == false) {
		stData << [status: "error", statusReason: "connectST is false"]
		return stData
	} else if (!stDeviceId || stDeviceId == "" || !stApiKey || stApiKey == "") {
		stData << [status: "error", statusReason: "stDeviceId or stApiKey null"]
		return stData
	}
	def cmdUri = "https://api.smartthings.com/v1/devices/${stDeviceId.trim()}/status"
	def sendCmdParams = [
		uri: cmdUri,
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01',
				 'Authorization': 'Bearer ' + stApiKey.trim()]
	]
	httpGet(sendCmdParams) {resp ->
//	Here, I assume the device has only one component, main.  Is not always true.
		def data = resp.data.components.main
log.trace data
		if (resp.status == 200) {
			stData << [status: "OK"]
/*
The format of the data access is (from resp.data.components.main)
	data.{capability}.{attribute}.value 
*/
			def onOff = data.switch.switch.value
			if (device.currentValue("switch") != onOff) {
				sendEvent(name: "switch", value: onOff)
			}
			def volume = data.audioVolume.volume.value.toInteger()
			if (device.currentValue("volume").toInteger() != volume) {
				sendEvent(name: "volume", value: volume)
			}
			def mute = data.audioMute.mute.value
			if (device.currentValue("mute") != mute) {
				sendEvent(name: "mute", value: mute)
			}
			stData << [switch: onOff, volume: volume, mute: mute]
		} else {
			stData << [status: "error", statusReason: "http status = ${resp.status}"]
		}
	}
	if (stData.status == "error") {
		logWarn("getStDeviceData: ${stData}")
	} else {
		logDebug("getStDeviceData: ${stData}")
	}
	return stData
/* EXAMPLE FULL OUTPUT (to blow your mind)

*/
}



private sendStPost(capability, command, arguments){
	def stData = [status: "OK"]
	if (!stDeviceId || !stApiKey) {
		stData = [status: "error", statusReason: "stApiKey or stDeviceId missing"]
		logWarn("sendStPost: ${stData}")
		return stData
	}
	logDebug("sendStGet: ${capability}/ ${command}/ ${arguments}")
	def cmdUri =  "https://api.smartthings.com/v1/devices/${stDeviceId.trim()}/commands"
	def cmdData = [
		component: "main",
		capability: capability,
		command: command,
		arguments: arguments
	]
//	It may be possible to send multiple commands in same message.  Future research.
	def cmdBody = [commands: [cmdData]]
	def sendCmdParams = [
		uri: cmdUri,
		requestContentType: 'application/json',
		contentType: 'application/json',
		headers: ['Accept':'application/json; version=1, */*; q=0.01',
				 'Authorization': 'Bearer ' + stApiKey.trim()],
		body : new groovy.json.JsonBuilder(cmdBody).toString()
	]
	try {
		httpPost(sendCmdParams) {resp ->
			if (resp.status == 200 && resp.data != null) {
				stData << [results: resp.data]
			} else {
				stData = [status: "error", statusReason: "HTTP Code ${resp.status}"]
			}
		}
	} catch (e) { 
		stData = [status: "error", statusReason: "CommsError = ${e}"]
	}
	return stData
}

//	===== Logging=====
def logTrace(msg){
	log.trace "[${device.label}]:: ${msg}"
}

def logInfo(msg) { 
	log.info "[${device.label}]:: ${msg}"
}

def logDebug(msg) {
	log.debug "[${device.label}]:: ${msg}"
}

def logWarn(msg) {
	log.warn "[${device.label}]:: ${msg}"
}
