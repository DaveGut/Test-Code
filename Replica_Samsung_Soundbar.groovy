/*	HubiThings Replica Color Bulb Driver
	HubiThings Replica Applications Copyright 2023 by Bloodtick
	Replica Color Bulb Copyright 2023 by Dave Gutheinz

	Licensed under the Apache License, Version 2.0 (the "License"); 
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at:
	      http://www.apache.org/licenses/LICENSE-2.0
	Unless required by applicable law or agreed to in writing, software 
	distributed under the License is distributed on an "AS IS" BASIS, 
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
	implied. See the License for the specific language governing 
	permissions and limitations under the License.

Sample Audio Notifications Streams are at the bottom of this driver.

Issues with this driver: Contact davegut via Private Message on the
Hubitat Community site: https://community.hubitat.com/
==========================================================================*/
import org.json.JSONObject
import groovy.json.JsonOutput
def driverVer() { return "1.0" }

metadata {
	definition (name: "Replica Samsung Soundbar",
				namespace: "replica",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/HubiThingsReplica%20Drivers/Replica_Samsung_Soundbar.groovy"
			   ){
		capability "Switch"
		capability "MediaInputSource"
		command "toggleInputSource"
		capability "MediaTransport"
		capability "AudioVolume"
		capability "AudioNotification"
		attribute "audioTrackData", "JSON_OBJECT"
		capability "Refresh"
		capability "Configuration"
		attribute "healthStatus", "enum", ["offline", "online"]
		command "testAudioNotify"
	}
	preferences {
		//	If !deviceIp - assume ST Notify
		//	If deviceIp - assume Local Notify
		//	Alt TTS Available under all conditions
		input ("altTts", "bool", title: "Use Alternate TTS Method", defalutValue: false)
		if (altTts) {
			def ttsLanguages = ["en-au":"English (Australia)","en-ca":"English (Canada)", "en-gb":"English (Great Britain)",
								"en-us":"English (United States)", "en-in":"English (India)","ca-es":"Catalan",
								"zh-cn":"Chinese (China)", "zh-hk":"Chinese (Hong Kong)","zh-tw":"Chinese (Taiwan)",
								"da-dk":"Danish", "nl-nl":"Dutch","fi-fi":"Finnish","fr-ca":"French (Canada)",
								"fr-fr":"French (France)","de-de":"German","it-it":"Italian","ja-jp":"Japanese",
								"ko-kr":"Korean","nb-no":"Norwegian","pl-pl":"Polish","pt-br":"Portuguese (Brazil)",
								"pt-pt":"Portuguese (Portugal)","ru-ru":"Russian","es-mx":"Spanish (Mexico)",
								"es-es":"Spanish (Spain)","sv-se":"Swedish (Sweden)"]
			input ("ttsApiKey", "string", title: "TTS Site Key", defaultValue: null,
				   description: "From http://www.voicerss.org/registration.aspx")
			input ("ttsLang", "enum", title: "TTS Language", options: ttsLanguages, defaultValue: "en-us")
		}
		input ("deviceIp", "string", title: "Device IP. For Local Notification.")
		input ("logEnable", "bool",  title: "Enable debug logging for 30 minutes", defaultValue: false)
		input ("infoLog", "bool", title: "Enable information logging",defaultValue: true)
		input ("traceLog", "bool", title: "Enable trace logging as directed by developer", defaultValue: false)
	}
}

def installed() {
	runIn(1, updated)
}

def updated() {
	unschedule()
	def updStatus = [:]
	initialize()
	if (!getDataValue("driverVersion") || getDataValue("driverVersion") != driverVer()) {
		updateDataValue("driverVersion", driverVer())
		updStatus << [driverVer: driverVer()]
	}
	if (logEnable) { runIn(1800, debugLogOff) }
	if (traceLog) { runIn(600, traceLogOff) }
	updStatus << [logEnable: logEnable, infoLog: infoLog, traceLog: traceLog]
//	runEvery5Minutes(deviceRefresh)
	updStatus << [pollInterval: "5 Minutes"]
	clearQueue()

	runIn(10, refresh)
	pauseExecution(5000)
	listAttributes(true)
	logInfo("updated: ${updStatus}")
}

def initialize() {
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers()))
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands()))
	logInfo("initialize: initialize device-specific data")
}

//	===== HubiThings Device Settings =====
Map getReplicaCommands() {
    return ([
		"replicaEvent":[[name:"parent*",type:"OBJECT"],[name:"event*",type:"JSON_OBJECT"]],
		"replicaStatus":[[name:"parent*",type:"OBJECT"],[name:"event*",type:"JSON_OBJECT"]],
//		"replicaHealth":[[name:"parent*",type:"OBJECT"],[name:"health*",type:"JSON_OBJECT"]],
		"setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

def testItem(args) {
	log.warn args
}

Map getReplicaTriggers() {
	def replicaTriggers = [
		off:[],
		on:[],
		setInputSource: [[name:"inputName*", type: "STRING"]],
		play:[],
		pause:[],
		stop:[],
		volumeUp:[],
		volumeDown:[],
		setVolume: [[name:"volumelevel*", type: "NUMBER"]],
		mute:[],
		unmute:[],
		playTrack:[
			[name:"trackuri*", type: "STRING"],
			[name:"volumelevel", type:"NUMBER", data:"volumelevel"]],
		playTrackAndRestore:[
			[name:"trackuri*", type: "STRING"],
			[name:"volumelevel", type:"NUMBER", data:"volumelevel"]],
		playTrackAndResume:[
			[name:"trackuri*", type: "STRING"],
			[name:"volumelevel", type:"NUMBER", data:"volumelevel"]],
		refresh:[],
		deviceRefresh:[]
	]
	return replicaTriggers
}

def configure() {
    initialize()
	setReplicaRules()
	sendCommand("configure")
	logInfo("configure: configuring default rules")
}

String setReplicaRules() {
	def rules = """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger"},
{"trigger":{"name":"deviceRefresh","label":"command: deviceRefresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"},
{"trigger":{"name":"mute","label":"command: mute()","type":"command"},"command":{"name":"mute","type":"command","capability":"audioMute","label":"command: mute()"},"type":"hubitatTrigger"},
{"trigger":{"name":"off","label":"command: off()","type":"command"},"command":{"name":"off","type":"command","capability":"switch","label":"command: off()"},"type":"hubitatTrigger"},
{"trigger":{"name":"on","label":"command: on()","type":"command"},"command":{"name":"on","type":"command","capability":"switch","label":"command: on()"},"type":"hubitatTrigger"},
{"trigger":{"name":"pause","label":"command: pause()","type":"command"},"command":{"name":"pause","type":"command","capability":"mediaPlayback","label":"command: pause()"},"type":"hubitatTrigger"},
{"trigger":{"name":"play","label":"command: play()","type":"command"},"command":{"name":"play","type":"command","capability":"mediaPlayback","label":"command: play()"},"type":"hubitatTrigger"},
{"trigger":{"name":"playTrack","label":"command: playTrack(trackuri*, volumelevel)","type":"command","parameters":[{"name":"trackuri*","type":"STRING"},{"name":"volumelevel","type":"NUMBER","data":"volumelevel"}]},"command":{"name":"playTrack","arguments":[{"name":"uri","optional":false,"schema":{"title":"URI","type":"string","format":"uri"}},{"name":"level","optional":true,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"audioNotification","label":"command: playTrack(uri*, level)"},"type":"hubitatTrigger"},
{"trigger":{"name":"playTrackAndRestore","label":"command: playTrackAndRestore(trackuri*, volumelevel)","type":"command","parameters":[{"name":"trackuri*","type":"STRING"},{"name":"volumelevel","type":"NUMBER","data":"volumelevel"}]},"command":{"name":"playTrackAndRestore","arguments":[{"name":"uri","optional":false,"schema":{"title":"URI","type":"string","format":"uri"}},{"name":"level","optional":true,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"audioNotification","label":"command: playTrackAndRestore(uri*, level)"},"type":"hubitatTrigger"},
{"trigger":{"name":"playTrackAndResume","label":"command: playTrackAndResume(trackuri*, volumelevel)","type":"command","parameters":[{"name":"trackuri*","type":"STRING"},{"name":"volumelevel","type":"NUMBER","data":"volumelevel"}]},"command":{"name":"playTrackAndResume","arguments":[{"name":"uri","optional":false,"schema":{"title":"URI","type":"string","format":"uri"}},{"name":"level","optional":true,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"audioNotification","label":"command: playTrackAndResume(uri*, level)"},"type":"hubitatTrigger"},
{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"},
{"trigger":{"name":"setVolume","label":"command: setVolume(volumelevel*)","type":"command","parameters":[{"name":"volumelevel*","type":"NUMBER"}]},"command":{"name":"setVolume","arguments":[{"name":"volume","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"audioVolume","label":"command: setVolume(volume*)"},"type":"hubitatTrigger"},
{"trigger":{"name":"setInputSource","label":"command: setInputSource(inputSource*)","type":"command","parameters":[{"name":"inputName*","type":"string"}]},"command":{"name":"setInputSource","arguments":[{"name":"mode","optional":false,"schema":{"title":"MediaSource","enum":["AM","CD","FM","HDMI","HDMI1","HDMI2","HDMI3","HDMI4","HDMI5","HDMI6","digitalTv","USB","YouTube","aux","bluetooth","digital","melon","wifi"],"type":"string"}}],"type":"command","capability":"mediaInputSource","label":"command: setInputSource(mode*)"},"type":"hubitatTrigger"},
{"trigger":{"name":"stop","label":"command: stop()","type":"command"},"command":{"name":"stop","type":"command","capability":"mediaPlayback","label":"command: stop()"},"type":"hubitatTrigger"},
{"trigger":{"name":"unmute","label":"command: unmute()","type":"command"},"command":{"name":"unmute","type":"command","capability":"audioMute","label":"command: unmute()"},"type":"hubitatTrigger"},
{"trigger":{"name":"volumeDown","label":"command: volumeDown()","type":"command"},"command":{"name":"volumeDown","type":"command","capability":"audioVolume","label":"command: volumeDown()"},"type":"hubitatTrigger"},
{"trigger":{"name":"volumeUp","label":"command: volumeUp()","type":"command"},"command":{"name":"volumeUp","type":"command","capability":"audioVolume","label":"command: volumeUp()"},"type":"hubitatTrigger"}]}"""

	updateDataValue("rules", rules)
}

//	===== Event Parse Interface s=====
void replicaStatus(def parent=null, Map status=null) {
	def logData = [parent: parent, status: status]
	if (state.refreshAttributes) {
		refreshAttributes(status.components.main)
	}
	logTrace("replicaStatus: ${logData}")
}

def refreshAttributes(mainData) {
	logDebug("setInitialAttributes: ${mainData}")
	def value
	try {
		value = mainData.mediaInputSource.supportedInputSources.value
	} catch(e) {
		value = ["n/a"]
		pauseExecution(200)
	}
	parse_main([attribute: "supportedInputSources", value: value])
	pauseExecution(200)
	
	parse_main([attribute: "switch", value: mainData.switch.switch.value])
	pauseExecution(200)

	parse_main([attribute: "volume", value: mainData.audioVolume.volume.value.toInteger(), unit: "%"])
	pauseExecution(200)

	parse_main([attribute: "mute", value: mainData.audioMute.mute.value])
	pauseExecution(200)

	parse_main([attribute: "playbackStatus", value: mainData.mediaPlayback.playbackStatus.value])
	pauseExecution(200)

	try {
		value = mainData.mediaInputSource.inputSource.value
	} catch(e) {
		value = "n/a"
	}
	parse_main([attribute: "inputSource", value: value])
	pauseExecution(200)

	try {
		value = mainData.audioTrackData.audioTrackData.value
	} catch(e) {
		value = "n/a"
	}
	parse_main([attribute: "audioTrackData", value: value])
	
	state.refreshAttributes	= false
}

void replicaHealth(def parent=null, Map health=null) {
	if(parent) { logInfo("replicaHealth: ${parent?.getLabel()}") }
	if(health) { logInfo("replicaHealth: ${health}") }
}

void replicaEvent(def parent=null, Map event=null) {
	logDebug("replicaEvent: [parent: ${parent}, event: ${event}]")
	def eventData = event.deviceEvent
	try {
		"parse_${event.deviceEvent.componentId}"(event.deviceEvent)
	} catch (err) {
		logWarn("replicaEvent: [event = ${event}, error: ${err}")
	}
}

def parse_main(event) {
	logInfo("parse_main: <b>[attribute: ${event.attribute}, value: ${event.value}, unit: ${event.unit}]</b>")
	switch(event.attribute) {
		case "switch":
		case "volume":
		case "mute":
		case "audioTrackData":
			sendEvent(name: event.attribute, value: event.value)
			break
		case "inputSource":
			if (event.capability == "mediaInputSource") {
				sendEvent(name: "mediaInputSource", value: event.value)
			}
			break
		case "playbackStatus":
			sendEvent(name: "transportStatus", value: event.value)
			break
		case "supportedInputSources":
			state.inputSources = event.value
			break
			break
		default:
			logDebug("parse_main: [unhandledEvent: ${event}]")
		break
	}
	logTrace("parse_main: [event: ${event}]")
}

//	===== HubiThings Send Command and Device Health =====
def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value)
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
	parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
}

def refresh() {
	state.refreshAttributes = true
	sendCommand("deviceRefresh")
	pauseExecution(500)
	sendCommand("refresh")
}

def deviceRefresh() {
	sendCommand("deviceRefresh")
}

//	===== Samsung TV Commands =====
def on() {
	sendCommand("on")
}

def off() {
	sendCommand("off")
}

def setAttrSwitch(onOff) {
	sendEvent(name: "switch", value: onOff)
}

//	===== Media Input Source =====
def toggleInputSource() {
	if (state.inputSources) {
		def inputSources = state.inputSources
		def totalSources = inputSources.size()
		def currentSource = device.currentValue("mediaInputSource")
		def sourceNo = inputSources.indexOf(currentSource)
		def newSourceNo = sourceNo + 1
		if (newSourceNo == totalSources) { newSourceNo = 0 }
		def inputSource = inputSources[newSourceNo]
		setInputSource(inputSource)
	} else { 
		logWarn("toggleInputSource: [status: FAILED, reason: no state.inputSources, <b>correction: try running refresh</b>]")
	}
}

def setInputSource(inputSource) {
	if (inputSource == "n/a") {
		logWarn("setInputSource: [status: FAILED, reason: ST Device does not support input source]")
	} else {
		def inputSources = state.inputSources
		if (inputSources == null) {
			logWarn("setInputSource: [status: FAILED, reason: no state.inputSources, <b>correction: try running refresh</b>]")
		} else if (state.inputSources.contains(inputSource)) {
			sendCommand("setInputSource", inputSource)
		} else {
			logWarn("setInputSource: [status: FAILED, inputSource: ${inputSource}, inputSources: ${inputSources}]")
		}
	}
}

//	===== Media Transport =====
def play() {
	sendCommand("play")
	runIn(5, deviceRefresh)
}

def pause() { 
	sendCommand("pause") 
	runIn(5, deviceRefresh)
}

def stop() {
	sendCommand("stop")
	runIn(5, deviceRefresh)
}

//	===== Audio Volume =====
def volumeUp() { sendCommand("volumeUp") }

def volumeDown() {sendCommand("volumeDown") }

def setVolume(volume) {
	if (volume == null) { volume = device.currentValue("volume").toInteger() }
	if (volume < 0) { volume = 0 }
	else if (volume > 100) { volume = 100 }
	sendCommand("setVolume", volume)
}

def mute() { sendCommand("mute") }

def unmute() { sendCommand("unmute") }

//	===== Audio Notification / URL-URI Playback functions
def testAudioNotify() {
	runIn(3, testTextNotify)
	playTrack("""http://s3.amazonaws.com/smartapp-media/sonos/dogs.mp3""", 8)
}
def testTextNotify() {
log.warn "testTextNotify"
	playText("This is a test of the Text-to-speech function", 9)
}


def playText(text, volume = null) {
log.warn "playText: $text"
//	def track = convertToTrack(text)
//	addToQueue(track.uri, track.duration, volume, true)
//	logDebug("playText: [text: ${text}, vloume: ${volume}, track: ${track}]")
	createTextData(text, volume, true)
}

def playTextAndRestore(text, volume = null) {
//	def track = convertToTrack(text)
//	addToQueue(track.uri, track.duration, volume, false)
//	logDebug("playTextAndRestore: [text: ${text}, vloume: ${volume}, track: ${track}]")
	createTextData(text, volume, false)
}

def playTextAndResume(text, volume = null) {
//	def track = convertToTrack(text)
//	addToQueue(track.uri, track.duration, volume, false)
//	logDebug("playTextAndResume: [text: ${text}, vloume: ${volume}, track: ${track}]")
	createTextData(text, volume, true)
}

def createTextData(text, volume, resume) {
log.warn "createTextData"
	if (volume == null) { volume = device.currentValue("volume") }
	def logData = [text: text, volume: volume, resume: resume]
	def trackUri
	def duration
	if (altTts) {
		if (ttsApiKey == null) {
			logWarn("convertToTrack: [FAILED: No ttsApiKey]")
		} else {
			def uriText = URLEncoder.encode(text, "UTF-8").replaceAll(/\+/, "%20")
			trackUri = "http://api.voicerss.org/?" +
				"key=${ttsApiKey.trim()}" +
				"&f=48khz_16bit_mono" +
				"&c=MP3" +
				"&hl=${ttsLang}" +
				"&src=${uriText}"
			duration = (1 + text.length() / 10).toInteger()
			track =  [uri: trackUri, duration: duration]
		}
	} else {
		def track = textToSpeech(text, voice)
		trackUri = track.uri
		duration = track.duration
	}
	logData << [trackUri: trackUri, duration: duration]
	addToQueue(trackUri, duration, volume, resume)
	logDebug("createTextData: ${logData}")
	logWarn("createTextData: ${logData}")
}

def playTrack(trackData, volume = null) {
//	if (trackData.toString()[0] != "[") {
//		trackData = [url: trackData, name: trackData]
//	}
//	trackData = new JSONObject(trackData)
	createPlayData(trackData, volume, true)
}

def playTrackAndRestore(trackData, volume=null) {
	createPlayData(trackData, volume, false)
}

def playTrackAndResume(trackData, volume=null) {
	createPlayData(trackData, volume, true)
}

def createPlayData(trackData, volume, resume) {
	if (volume == null) { volume = device.currentValue("volume") }
	def logData = [trackData: text, volume: volume, resume: resume]
	def trackUri
	def duration
	if (trackData[0] == "[") {
		logData << [status: "aborted", reason: "trackData not formated as {uri: , duration: }"]
	} else {
		if (trackData[0] == "{") {
			trackData = new JSONObject(trackData)
			trackUri = trackData.uri
			duration = trackData.duration
		} else {
			trackUri = trackData
			duration = 15
		}
		logData << [status: "addToQueue", trackData: trackData, volume: volume, resume: resume]
		addToQueue(trackUri, duration, volume, resume)
	}
	logDebug("createPlayData: ${logData}")
}

//	========== Play Queue Execution ==========
def addToQueue(trackUri, duration, volume, resume){
	def connected = device.currentValue("healthStatus")
	def logData = [connected: connected]
	if (connected == "online") {
		duration = duration + 3
		playData = ["trackUri": trackUri, 
					"duration": duration,
					"requestVolume": volume]
		state.playQueue.add(playData)	
		logData << [addedToQueue: [uri: trackUri, duration: duration, volume: volume], resume: resume]

		if (state.playingNotification == false) {
			runInMillis(100, startPlayViaQueue, [data: resume])
		}
	} else {
		logData << [status: "aborted"]
		logWarn("addToQueue: ${logData}")
	}
	logDebug("addToQueue: ${logData}")
}

def startPlayViaQueue(resume) {
	logDebug("startPlayViaQueue: [queueSize: ${state.playQueue.size()}, resume: ${resume}]")
	if (state.playQueue.size() == 0) { return }
	state.recoveryVolume = device.currentValue("volume")
	state.recoverySource = device.currentValue("mediaInputSource")
	if (getDataValue("hwType") == "Speaker") {
		def blankTrack = convertToTrack("     ")
		execPlay(blankTrack.uri, true)
	}
	state.playingNotification = true
	runInMillis(100, playViaQueue, [data: resume])
}

def playViaQueue(resume) {
	def logData = [:]
	if (state.playQueue.size() == 0) {
		resumePlayer(resume)
		logData << [status: "resumingPlayer", reason: "Zero Queue", resume: resume]
	} else {
		def playData = state.playQueue.get(0)
		state.playQueue.remove(0)

		execPlay(playData.trackUri, playData.requestVolume, resume)
		runIn(playData.duration, resumePlayer, [data: resume])
		runIn(30, kickStartQueue, [data: resume])
		logData << [playData: playData, recoveryVolume: recVolume]
	}
	logDebug("playViaQueue: ${logData}")
}

def execPlay(trackUri, volume, resume) {
	//	Can hood for STPlay vs LocalPlay here if necessary.
	setVolume(volume)
	pauseExecution(200)
	if (deviceIp) {
		sendUpnpCmd("SetAVTransportURI",
					 [InstanceID: 0,
					  CurrentURI: trackUri,
					  CurrentURIMetaData: ""])
		pauseExecution(1000)
		sendUpnpCmd("Play",
					 ["InstanceID" :0,
					  "Speed": "1"])
	} else {
		sendCommand("playTrack", trackUri)
	}		
}

def resumePlayer(resume) {
	//	should be able to recover data here.  At least, recover the current value of the inputSource
	def logData = [resume: resume]
	if (state.playQueue.size() > 0) {
		logData << [status: "aborted", reason: "playQueue not 0"]
		playViaQueue(resume)
	} else {
		state.playingNotification = false
		setVolume(state.recoveryVolume)
		def source = state.recoverySource
		if (source != "n/a") {
			setInputSource(source)
		}
	}
	logDebug("resumePlayer: ${logData}")
}

def kickStartQueue(resume = true) {
	logInfo("kickStartQueue: [resume: ${resume}, size: ${state.playQueue.size()}]")
	if (state.playQueue.size() > 0) {
		resumePlayer(resume)
	} else {
		state.playingNotification = false
	}
}

def clearQueue() {
	logDebug("clearQueue")
	state.playQueue = []
	state.playingNotification = false
}

//	===== UPNP Play Commands =====
private sendUpnpCmd(String action, Map body){
	logDebug("sendSpeakCmd: upnpAction = ${action}, upnpBody = ${body}")
	def host = "${deviceIp}:9197"
	def hubCmd = new hubitat.device.HubSoapAction(
		path:	"/upnp/control/AVTransport1",
		urn:	 "urn:schemas-upnp-org:service:AVTransport:1",
		action:  action,
		body:	body,
		headers: [Host: host,
				  CONNECTION: "close"]
	)
	sendHubCommand(hubCmd)
}

def parse(resp) {
	resp = parseLanMessage(resp)
	logDebug("parse: [upnpResponse: ${resp}]")
}

//	===== Data Logging =====
def listAttributes(trace = false) {
	def attrs = device.getSupportedAttributes()
	def attrList = [:]
	attrs.each {
		def val = device.currentValue("${it}")
		attrList << ["${it}": val]
	}
	if (trace == true) {
		logInfo("Attributes: ${attrList}")
	} else {
		logDebug("Attributes: ${attrList}")
	}
}

def logTrace(msg){
	if (traceLog) {
		log.trace "${device.displayName}-${driverVer()}: ${msg}"
	}
}

def traceLogOff() {
	device.updateSetting("traceLog", [type:"bool", value: false])
	logInfo("traceLogOff")
}

def logInfo(msg) { 
	if (infoLog) {
		log.info "${device.displayName}-${driverVer()}: ${msg}"
	}
}

def debugLogOff() {
	device.updateSetting("logEnable", [type:"bool", value: false])
	logInfo("debugLogOff")
}

def logDebug(msg) {
	if (logEnable) {
		log.debug "${device.displayName}-${driverVer()}: ${msg}"
	}
}

def logWarn(msg) { log.warn "${device.displayName}-${driverVer()}: ${msg}" }

/*	===== Sample Audio Notification URIs =====
	[title: "Bell 1", uri: "http://s3.amazonaws.com/smartapp-media/sonos/bell1.mp3", duration: "10"]
	[title: "Dogs Barking", uri: "http://s3.amazonaws.com/smartapp-media/sonos/dogs.mp3", duration: "10"]
	[title: "Fire Alarm", uri: "http://s3.amazonaws.com/smartapp-media/sonos/alarm.mp3", duration: "17"]
	[title: "The mail has arrived",uri: "http://s3.amazonaws.com/smartapp-media/sonos/the+mail+has+arrived.mp3", duration: "1"]
	[title: "A door opened", uri: "http://s3.amazonaws.com/smartapp-media/sonos/a+door+opened.mp3", duration: "1"]
	[title: "There is motion", uri: "http://s3.amazonaws.com/smartapp-media/sonos/there+is+motion.mp3", duration: "1"]
	[title: "Someone is arriving", uri: "http://s3.amazonaws.com/smartapp-media/sonos/someone+is+arriving.mp3", duration: "1"]
	=====	Some working Streaming Stations =====
	[title:"Cafe del Mar", uri:"https://streams.radio.co/se1a320b47/listen", duration: 0]
	[title:"UT-KUTX", uri: "https://kut.streamguys1.com/kutx-web", duration: 0]
	[title:"89.7 FM Perth", uri: "https://ice8.securenetsystems.net/897FM", duration: 0]
	[title:"Euro1", uri:"https://streams.radio.co/se1a320b47/listen", duration: 0]
	[title:"Easy Hits Florida", uri:"http://airspectrum.cdnstream1.com:8114/1648_128", duration: 0]
	[title:"Austin Blues", uri:"http://158.69.131.71:8036/stream/1/", duration: 0]
*/
