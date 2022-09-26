/*	Samsung Soundbar using SmartThings Interface
		Copyright Dave Gutheinz
License Information:
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md

===== Notification Sounds	=====
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

===== Valid Track Entry Examples =====
[title:"Cafe del Mar", uri:"https://streams.radio.co/se1a320b47/listen", duration: 0]
[uri:"https://streams.radio.co/se1a320b47/listen", duration: 0]
[uri:"https://streams.radio.co/se1a320b47/listen"]
https://streams.radio.co/se1a320b47/listen
===== Method Descriptions =====
playText: sends the test to playTextAndResume for processing

playTextAndRestore / Resume: converts the text to an audio stream and sends the trackData to associated playTextAnd(Resume/Restore) methods.

playTrack:  Plays the track immediately.  This track becomes the MASTER used in recoverying from the playTrackAnd(Resume/Restore) methods.

playTrackAnd(Resume/Restore).  Sends the audio track to the play queue with a switch for Resume.  If resume is true, the Master Track (see playTrack) will begin playing when the queue empties.

Queue:  The queue is a firstIn/firstOut function that actually controls the play of Audio Notifications.  Queue can hang.
	a.	kickStartQueue:  forces the queue to start again.  This is also scheduled to run every 30 minutes to keep the queue clear.
	b.	clearQueue: Zeroes out the queue and associated states.
	c.	resumePlayer: When the queue is empty, the system will be reset to the MASTER TRACK, volume is set to the original volume, the input source is set to the one at the start of playing.  If play is set, the MASTER TRACK will play - so if you do not want this, use the playTrackAndRestore.

URI Presets.  There are 8 presets that can be used for quick play used for quick playing of regular channels.  These also work well in dashboards using the capability PushableButton push method.
	a.	Uri Preset Create: This creates a URI from the current Master URI.  To create:
		1.	Enter the track data into the Play Track function and wait for the uri to be playing.
		2.	Enter a number (1-8) and your name for the URI in the command box.
		3.	The preset name will appear in the Attributes field on the Device's edit page (for your reference).
	b.	URI Preset Play:  When selected, it will play the preset and become the MASTER TRACK.


==============================================================================*/
import org.json.JSONObject
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.util.XmlSlurper
import groovy.util.XmlParser
def driverVer() { return "1.2" }

metadata {
	definition (name: "Samsung Soundbar",
				namespace: "davegut",
				author: "David Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/SamsungAppliances/Samsung_Soundbar.groovy"
			   ){
		capability "Switch"
		capability "MediaInputSource"
		capability "AudioNotification"
		capability "AudioVolume"
		capability "MusicPlayer"
		attribute "currentUri", "string"
		capability "Refresh"
		capability "PushableButton"
		attribute "urlPreset_1", "string"
		attribute "urlPreset_2", "string"
		attribute "urlPreset_3", "string"
		attribute "urlPreset_4", "string"
		attribute "urlPreset_5", "string"
		attribute "urlPreset_6", "string"
		attribute "urlPreset_7", "string"
		attribute "urlPreset_8", "string"
		command "urlPresetCreate", [[name: "Url Preset Number", type: "NUMBER"],[name: "URL Preset Name", type: "STRING"]]
		command "urlPresetPlay", [[name: "Url Preset Number", type: "NUMBER"]]
		command "urlPresetDelete", [[name: "Url Preset Number", type: "NUMBER"]]
		command "kickStartQueue"
		command "clearQueue"
		command "setInputSource", [[
			name: "Soundbar Input",
			constraints: ["digital", "HDMI1", "bluetooth", "HDMI2", "wifi"],
			type: "ENUM"]]
		command "toggleInputSource"
	}
	preferences {
		input ("stApiKey", "string", title: "SmartThings API Key", defaultValue: "")
		if (stApiKey) {
			input ("stDeviceId", "string", title: "SmartThings Device ID", defaultValue: "")
		}
		if (stDeviceId) {
			input ("deviceIp", "string", title: "deviceIp")
//			input ("upnpNotify", "bool",
//				   title: "Use UPnP for audio notifications", defaultValue: false)
			input ("volIncrement", "number", title: "Volume Up/Down Increment", defaultValue: 1)
			input ("pollInterval", "enum", title: "Poll Interval (minutes)",
				   options: ["1", "5", "10", "30"], defaultValue: "5")
			input ("useVoicesRss", "bool",  title: "Use Voices RSS for TTS generation", defaultValue: false)
			if (useVoices) {
				def ttsLanguages = ["en-au":"English (Australia)","en-ca":"English (Canada)", "en-gb":"English (Great Britain)",
									"en-us":"English (United States)", "en-in":"English (India)","ca-es":"Catalan",
									"zh-cn":"Chinese (China)", "zh-hk":"Chinese (Hong Kong)","zh-tw":"Chinese (Taiwan)",
									"da-dk":"Danish", "nl-nl":"Dutch","fi-fi":"Finnish","fr-ca":"French (Canada)",
									"fr-fr":"French (France)","de-de":"German","it-it":"Italian","ja-jp":"Japanese",
									"ko-kr":"Korean","nb-no":"Norwegian","pl-pl":"Polish","pt-br":"Portuguese (Brazil)",
									"pt-pt":"Portuguese (Portugal)","ru-ru":"Russian","es-mx":"Spanish (Mexico)",
									"es-es":"Spanish (Spain)","sv-se":"Swedish (Sweden)"]
				input ("ttsApiKey", "string", title: "TTS Site Key", description: "From http://www.voicerss.org/registration.aspx")
				input ("ttsLang", "enum", title: "TTS Language", options: ttsLanguages, defaultValue: "en-us")
			}
			input ("infoLog", "bool",  
				   title: "Info logging", defaultValue: true)
			input ("debugLog", "bool",  
				   title: "Enable debug logging for 30 minutes", defaultValue: false)
		}
	}
}

def installed() {
	sendEvent(name: "switch", value: "on")
	sendEvent(name: "volume", value: 0)
	sendEvent(name: "mute", value: "unmuted")
	sendEvent(name: "status", value: "stopped")
	sendEvent(name: "mediaInputSource", value: "wifi")
	state.urlPresetData = [:]
	runIn(1, updated)
}

def updated() {
	def commonStatus = commonUpdate()
	if (volIncrement == null || !volIncrement) {
		device.updateSetting("volIncrement", [type:"number", value: 1])
	}
	sendEvent(name: "numberOfButtons", value: "29")
	state.remove("playQueue")
	state.playingNotification = false
	state.playQueue = []
	clearQueue()
	state.triggered = false
	runEvery30Minutes(kickStartQueue)
	if (commonStatus.status == "OK") {
		logInfo("updated: ${commonStatus}")
	} else {
		logWarn("updated: ${commonStatus}")
	}
	deviceSetup()
}

//	===== capability "Switch" =====
def on() { setSwitch("on") }
def off() { setSwitch("off") }
def setSwitch(onOff) {
	def cmdData = [
		component: "main",
		capability: "switch",
		command: onOff,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setSwitch: [cmd: ${onOff}, ${cmdStatus}]")
}

//	===== capability "MediaInputSource" =====
def toggleInputSource() {
	if (state.supportedInputs) {
		def inputSources = state.supportedInputs
		def totalSources = inputSources.size()
		def currentSource = device.currentValue("mediaInputSource")
		def sourceNo = inputSources.indexOf(currentSource)
		def newSourceNo = sourceNo + 1
		if (newSourceNo == totalSources) { newSourceNo = 0 }
		def inputSource = inputSources[newSourceNo]
		setInputSource(inputSource)
	} else { logWarn("toggleInputSource: NOT SUPPORTED") }
}
def setInputSource(inputSource) {
	if (state.supportedInputs) {
		def inputSources = state.supportedInputs
		if (inputSources.contains(inputSource)) {
		def cmdData = [
			component: "main",
			capability: "mediaInputSource",
			command: "setInputSource",
			arguments: [inputSource]]
		def cmdStatus = deviceCommand(cmdData)
		logInfo("setInputSource: [cmd: ${inputSource}, ${cmdStatus}]")
		} else {
			logWarn("setInputSource: Invalid input source")
		}
	} else { logWarn("setInputSource: NOT SUPPORTED") } 
}

//	=====	capability "MusicPlayer" =====
def setLevel(level) { setVolume(level) }

//	===== Media Transport =====
def play() { 
	upnpPlay()
//	setMediaPlayback("play")
}
def pause() { 
	upnpPause()
//	setMediaPlayback("pause") 
}
def stop() { 
	upnpStop()
//	setMediaPlayback("stop") 
}
def setMediaPlayback(pbMode) {
	def cmdData = [
		component: "main",
		capability: "mediaPlayback",
		command: pbMode,
		arguments: []]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setMediaPlayback: [cmd: ${pbMode}, ${cmdStatus}]")
}

def mute() { setMute("muted") }
def unmute() { setMute("unmuted") }
def toggleMute() {
	def muteValue = "muted"
	if(device.currentValue("mute") == "muted") {
		muteValue = "unmuted"
	}
	setMute(muteValue)
}
def setMute(muteValue) {
	def cmdData = [
		component: "main",
		capability: "audioMute",
		command: "setMute",
		arguments: [muteValue]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setMute: [cmd: ${muteValue}, ${cmdStatus}]")
}

//	===== UPnP Media Transport Methods =====
def previousTrack() {
	sendUpnpCmd("AVTransport",
				"Previous",
				["InstanceID" :0])
}
def nextTrack() {
	sendUpnpCmd("AVTransport",
				"Next",
				["InstanceID" :0])
}
def setTrack(trackData) {
logTrace("setTrack: $trackData")
	def uri
	def title = "unknown"
	def duration = 0
	if (trackData.class == String) {
		if(trackData.startsWith("htt")) {
			uri = trackData
		} else {
			try{
				trackData = parseJson(trackData)
				uri = trackData.uri
				title = trackData.title
				duration = trackData.duration
			} catch (e) {
				logWarn("setTrack: [error: uri is not properly formatted]")
				return
			}
		}
	} else {
		uri = trackData.uri
		title = trackData.title
		duration = trackData.duration
	}

	def metadata = """<DIDL-Lite"""
	metadata += """ xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """
	metadata += """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" """
	metadata += """xmlns:dc="http://purl.org/dc/elements/1.1/" """
	metadata += """xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/">"""
	metadata += """<item>"""
	metadata += """<title>${title}</title>"""
	metadata += """<duration>${duration}</duration>"""
	metadata += """<upnp:class>object.item.audioItem</upnp:class>"""
	metadata += """<res>${uri}</res>"""
	metadata += """</item></DIDL-Lite>"""

	sendUpnpCmd("AVTransport",
				"SetAVTransportURI",
				 [InstanceID: 0,
				  CurrentURI: uri,
				  CurrentURIMetaData: metadata])
}
def restoreTrack(uri) {
	state.play = false
	setTrack(uri)
}
def resumeTrack(uri) {
	setTrack(uri)
}
//	playText(text) see AudioNotificaton playText
//	playTrack(trackUri) see AudioNotification playTrack

//	===== capability "AudioVolume" =====
//	mute/unmute in capability Music Player
def volumeUp() { 
	def curVol = device.currentValue("volume")
	def newVol = curVol + volIncrement.toInteger()
	setVolume(newVol)
}
def volumeDown() {
	def curVol = device.currentValue("volume")
	def newVol = curVol - volIncrement.toInteger()
	setVolume(newVol)
}
def setVolume(volume) {
	if (volume == null) { volume = device.currentValue("volume") }
	else if (volume < 0) { volume = 0 }
	else if (volume > 100) { volume = 100 }
	def cmdData = [
		component: "main",
		capability: "audioVolume",
		command: "setVolume",
		arguments: [volume]]
	def cmdStatus = deviceCommand(cmdData)
	logInfo("setVolume: [cmd: ${volume}, ${cmdStatus}]")
}

//	===== Play Text Methods =====
def playText(text, volume = null) { playTextAndResume(text) }
def playTextAndRestore(text, volume = null) {
	logDebug("playTextAndRestore: [text: ${text}, Volume: ${volume}]")
logTrace("playTextAndRestore: [text: ${text}, Volume: ${volume}]")
	def trackData = convertToTrack("text")
	playTrackAndRestore(trackData, volume)
}
def playTextAndResume(text, volume = null) {
	logDebug("playTextAndResume: [text: ${text}, Volume: ${volume}]")
logTrace("playTextAndResume: [text: ${text}, Volume: ${volume}]")
	def trackData = convertToTrack("text")
	playTrackAndResume(trackData, volume)
}
def convertToTrack(text) {
	def track
	if (!useVoices) {
		track = textToSpeech(text)
	} else {										//	Soundbar
		def uriText = URLEncoder.encode(text, "UTF-8").replaceAll(/\+/, "%20")
		uri = "http://api.voicerss.org/?" +
			"key=${ttsApiKey.trim()}" +
			"&f=48khz_16bit_mono" +
			"&c=MP3" +
			"&hl=${ttsLang}" +
			"&src=${uriText}"
		def duration = (1 + text.length() / 10).toInteger()
		track =  [uri: uri, name: "TTS", duration: duration]
	}
	return track
}

//	===== Play Track Methods =====
def playTrack(trackData, volume = null) {
	//	Immediate command.  Will stop all other playing, and empty the playQueue.
	logDebug("playTrack: [trackData: ${trackData}, volume: ${volume}]")
logTrace("playTrack: [trackData: ${trackData}, volume: ${volume}]")
	try {
		duration = trackData.duration.toInteger()
		uri = trackData.uri
	} catch (e) {
logTrace("playTrack: alternate track data method")
		trackData = genTrackData(trackData, "playTrackAndRestore")
			duration = trackData.duration.toInteger()
			uri = trackData.uri
	}
		
	if (trackData != "error") {
		state.masterTrack = true
		state.play = true
		stop()
		if (volume == null) {
			volume = device.currentValue("volume")
		}
		setVolume(volume.toInteger())
		setTrack(trackData)
	}
}
def playTrackAndRestore(trackData, volume= null) {
	logDebug("playTrackAndRestore: [trackData: ${trackData}, Volume: ${volume}]")
logTrace("playTrackAndRestore: [trackData: ${trackData}, volume: ${volume}]")
	def duration
	def uri
	try {
		duration = trackData.duration.toInteger()
		uri = trackData.uri
	} catch (e) {
logTrace("playTrackAndRestore: alternate track data method")
		trackData = genTrackData(trackData, "playTrackAndRestore")
			duration = trackData.duration.toInteger()
			uri = trackData.uri
	}
		
	if (trackData != "error") {
		if (volume == null) {
			volume = device.currentValue("volume").toInteger()
		}
		def addQueueData = [uri: uri,
							duration: duration,
							volume: volume,
							resume: false]
		addToQueue(addQueueData)
	}
}
def playTrackAndResume(trackData, volume=null) {
	logDebug("playTrackAndResume: [trackData: ${trackData}, Volume: ${volume}]")
logTrace("playTrackAndResume: [trackData: ${trackData}, Volume: ${volume}]")
	try {
		duration = trackData.duration.toInteger()
		uri = trackData.uri
	} catch (e) {
logTrace("playTrackAndRestore: alternate track data method")
		trackData = genTrackData(trackData, "playTrackAndRestore")
			duration = trackData.duration.toInteger()
			uri = trackData.uri
	}
		
	if (trackData != "error") {
		if (volume == null) {
			volume = device.currentValue("volume").toInteger()
		}
		def addQueueData = [uri: uri,
							duration: duration,
							volume: volume,
							resume: false]
		addToQueue(addQueueData)
	}
}
def genTrackData(trackData, meth) {
	if (trackData.class == String) {
		if (trackData.startsWith("http")) {
			trackData = """{duration:0,title:"${trackData}",uri:"${trackData}"}"""
		}
		try {
			trackData = trackData.replace("[","{").replaceAll("]","}")
			trackData = new JSONObject(trackData)
		} catch (error) {
			logWarn("genTrackData: [method: ${meth}, trackData: ${trackData}, errorCode: 101, error: trackData malformed]")
			return "error"
		}
		return trackData
	} else {
		logWarn("genTrackData: [method: ${meth}, trackData: ${trackData}, errorCode: 101, error: trackData malformed]")
		return "error"
	}
}

//	===== Custom Implemenation "PlayQueue" =====
def addToQueue(addData){
	logDebug("addToQueue: ${addData}")
logTrace("addToQueue: ${addData}")
	def playData = ["uri": addData.uri,
					"duration": addData.duration,
					"requestVolume": addData.volume]
	state.playQueue.add(playData)

	if (state.playingNotification == false) {
		state.playingNotification = true
		runIn(1, startPlayViaQueue, [data: addData])
	} else {
		runIn(30, kickStartQueue)
	}
}
def startPlayViaQueue(addData) {
	logDebug("startPlayViaQueue: ${addData}")
logTrace("startPlayViaQueue: ${addData}")
	if (state.playQueue.size() == 0) { return }
	state.resetData = [volume: device.currentValue("volume"),
					   inputSource: device.currentValue("inputSource"),
					   resume: addData.resume]
	runIn(1, playViaQueue)
}
def playViaQueue() {
	logDebug("playViaQueue: queueSize = ${state.playQueue.size()}")
logTrace("playViaQueue: queueSize = ${state.playQueue.size()}")
	if (state.playQueue.size() == 0) {
		resumePlayer()
	} else {
		if (device.currentValue("status") != "stopped") {
			stop()
		}
		def playData = state.playQueue.get(0)
		state.play = true
		setVolume(playData.requestVolume.toInteger())
		setTrack(playData.uri)
		
		state.playQueue.remove(0)
		def duration = playData.duration.toInteger()
		if (duration > 0) {
			runIn(duration + 4, playViaQueue)
		}
		runIn(30, kickStartQueue)
	}
}
def resumePlayer() {
	if (state.playQueue.size() > 0) {
		playViaQueue()
	} else {
		state.playingNotification = false
		def resetData = state.resetData
		state.resetData = []
		logDebug("resumePlayer: resetData = ${resetData}")
logTrace("resumePlayer: resetData = ${resetData}")

		if (resetData.resume == true) { state.play = true } 
		else { state.play = falase }
		setVolume(resetData.volume.toInteger())
		if (resetData.inputSource != null) {
			setInputSource(respData.inputSource)
		}
		if (device.currentValue("status") != "stopped") {
			stop()
		}
		def trackData = parseJson(device.currentValue("trackData"))
		setTrack(trackData)
	}
}
def kickStartQueue() {
	logInfo("kickStartQueue: playQueue: ${state.playQueue}")
	if (state.playQueue.size() > 0) {
		playViaQueue()
	}
}
def clearQueue() {
	logDebug("clearQueue")
	state.playQueue = []
	state.playingNotification = false
	state.play = false
	state.masterTrack = false
}

//	===== UPnP Interface =====
def upnpPlay() {
logTrace "upnpPlay"
	sendUpnpCmd("AVTransport",
				"Play",
				["InstanceID" :0,
				 "Speed": "1"])
}
def upnpPause() {
	sendUpnpCmd("AVTransport",
				"Pause",
				["InstanceID" :0])
}
def upnpStop() { 
	sendUpnpCmd("AVTransport",
				"Stop",
				["InstanceID" :0])
}
def getTransportInfo() {
	sendUpnpCmd("AVTransport",
				"GetTransportInfo",
				 [InstanceID: 0])
}
def getMediaInfo() {
	sendUpnpCmd("AVTransport",
				"GetMediaInfo",
				 [InstanceID: 0])
}

private sendUpnpCmd(type, action, body = []){
	def deviceIP = getDataValue("deviceIp")
	def host = "${deviceIp}:9197"
	def hubCmd = new hubitat.device.HubSoapAction(
		path:	"/upnp/control/${type}1",
		urn:	 "urn:schemas-upnp-org:service:${type}:1",
		action:  action,
		body:	body,
		headers: [Host: host, CONNECTION: "close"]
	)
	sendHubCommand(hubCmd)
}
def parse(resp) {
	resp = parseLanMessage(resp)
//	log.trace groovy.xml.XmlUtil.escapeXml(resp.body)
	def body = resp.xml.Body
	if (!body.size()) {
		logWarn("parse: No XML Body in resp: ${resp}")
	}
	else if (body.GetTransportInfoResponse.size()) { updatePlayStatus(body.GetTransportInfoResponse) }
	else if (body.GetMediaInfoResponse.size()) { parseMediaInfo(body.GetMediaInfoResponse) }
	else if (body.SetAVTransportURIResponse.size()) { parseAVTransport(body.SetAVTransportURIResponse) }
	else if (body.PlayResponse.size()) { runIn(2,getTransportInfo) }
	else if (body.PauseResponse.size()) { runIn(2,getTransportInfo) }
	else if (body.StopResponse.size()) { runIn(2,getTransportInfo) }
	else if (body.Fault.size()) { parseFault(body.Fault) }
	//	===== Fault Code =====
	else {
		logWarn("parse: [unhandledResponse: ${groovy.xml.XmlUtil.escapeXml(resp.body)}]")
	}
}

def parseAVTransport(data) {
	logDebug("parseAVTransport: [play: ${state.play}]")
logTrace("parseAVTransport: [play: ${state.play}]")
	if (state.play) {
		state.play = false
		upnpPlay()
	}
	runIn(2, getMediaInfo)
}
def parseMediaInfo(data) {
logTrace("parseMediaInfo: [masterTrack: ${state.masterTrack}, data: ${data}]")
	def currentUri =  data.CurrentURI.text()
	if (currentUri == "") { currentUri = "none" }
	sendEvent(name: "currentUri", value: currentUri)
	def logData = [currentUri: currentUri]
	if (state.masterTrack) {
		state.masterTrack = false
		def uriMetadata = groovy.xml.XmlUtil.escapeXml(data.CurrentURIMetaData.toString())
		uriMetadata = uriMetadata.toString().replaceAll("&lt;", "<").replaceAll("&gt;", ">")
		uriMetadata = uriMetadata.replaceAll("&quot;","\"")
		uriMetadata = new XmlSlurper().parseText(uriMetadata)
		def trackData = """{title: "${uriMetadata.item.title}", """ +
			"""uri: "${uri: currentUri}", duration: ${uriMetadata.item.duration}}"""
		trackData = new JSONObject(trackData)
		
		sendEvent(name: "trackDescription", value: trackData.title)
		sendEvent(name: "trackData", value: trackData)
		logData << [trackData: trackData]
		logData << [trackDescription: trackData.title]
	}
	logDebug("parseMediaInfo: ${logData}")
}
def updatePlayStatus(data) {
	def status = data.CurrentTransportState.text()
	def transStatus = device.currentValue("status")
	switch(status) {
		case "PLAYING":
			transStatus = "playing"
			break
		case "PAUSED_PLAYBACK":
			transStatus = "paused"
			break
		case "STOPPED":
			transStatus = "stopped"
			break
		case "TRANSITIONING":
			runIn(15,getTransportInfo)
			break
		case "NO_MEDIA_PRESENT": 
			transStatus = "stopped"
			logWarn("updatePlayStatus: [status: ${status}]")
			break
		default:
			logWarn("updatePlayStatus: [unhandled: ${status}]")
	}
	sendEvent(name: "status", value: transStatus)
}
def parseFault(data) {
	def faultData = data.detail.UPnPError.errorDescription.text()
	def code = data.faultstring.text()
	state.play = false
	state.masterTrack = false
	logWarn("parseFault: [errorDescription: ${code}, ${faultData}]")
}

//	===== Custom Implementation "UrlStreamPreset" =====
def urlPresetCreate(preset, name = "Not Set") {
	if (preset < 1 || preset > 8) {
		logWarn("urlPresetCreate: Preset Number out of range (1-8)!")
		return
	}
	def trackData = parseJson(device.currentValue("trackData").toString())
	def urlData = [:]
	urlData["title"] = name
	urlData["uri"] = trackData.uri
	urlData["duration"] = trackData.duration
	state.urlPresetData << ["${preset}":[urlData]]
	sendEvent(name: "urlPreset_${preset}", value: urlData.title)
	logInfo("urlPresetCreate: created preset ${preset}, data = ${urlData}")
}
def urlPresetPlay(preset) {
	if (preset < 1 || preset > 8) {
		logWarn("urlPresetPlay: Preset Number out of range (1-8)!")
		return
	} 
	def urlData = state.urlPresetData."${preset}"
	if (urlData == null || urlData == [:]) {
		logWarn("urlPresetPlay: Preset Not Set!")
	} else {
		playTrack(urlData[0])
		logDebug("urlPresetPlay: ${urlData}")
	}
}
def urlPresetDelete(preset) {
	def urlPresetData = state.urlPresetData
	if (preset < 1 || preset > 8) {
		logWarn("urlPresetDelete: Preset Number ${preset} out of range (1-8)!")
	} else if (urlPresetData."${preset}" == null || urlPresetData."${preset}" == [:]) {
		logWarn("urlPresetDelete: Preset Not Set!")
	} else {
		urlPresetData << ["${preset}":[]]
		sendEvent(name: "urlPreset_${preset}", value: " ")
		logInfo("urlPresetDelete: [preset: ${preset}]")
	}
}

//	===== capability "PushableButton" =====
def push(pushed) {
	logDebug("push: [button: ${pushed}, trigger: ${state.triggered}]")
	if (pushed == null) {
		logWarn("push: pushed is null.  Input ignored")
		return
	}
	sendEvent(name: "pushed", value: pushed)
	pushed = pushed.toInteger()
	switch(pushed) {
		case 0 :
			if (state.triggered == true) {
				state.triggered = false
				logDebug("push: Trigger is NOT ARMED")
			} else {
				state.triggered = true
				logDebug("push: Trigger is ARMED")
				runIn(15, unTrigger)
			}
			break
		case 1 :		//	Preset 1
		case 2 :		//	Preset 2
		case 3 :		//	Preset 3
		case 4 :		//	Preset 4
		case 5 :		//	Preset 5
		case 6 :		//	Preset 6
		case 7 :		//	Preset 7
		case 8 :		//	Preset 8
			if (state.triggered == false) {
				urlPresetPlay(pushed)
			} else {
				urlPresetCreate(pushed)
				sendEvent(name: "Trigger", value: "notArmed")
			}
			break
		case 10: refresh(); break
		case 11: toggleInputSource(); break
		case 12: volumeUp(); break
		case 13: volumeDown(); break
		
		case 20: clearQueue(); break
		case 21: kickStartQueue(); break
		default:
			logWarn("${device.label}: Invalid Preset Number (must be 0 thru 29)!")
			break
	}
}
def unTrigger() { state.triggered = false }

def distResp(resp, data) {
	def respLog = [:]
	if (resp.status == 200) {
		try {
			def respData = new JsonSlurper().parseText(resp.data)
			if (data.reason == "deviceSetup") {
				deviceSetupParse(respData.components.main)
			}
			statusParse(respData.components.main)
		} catch (err) {
			respLog << [status: "ERROR",
						errorMsg: err,
						respData: resp.data]
		}
	} else {
		respLog << [status: "ERROR",
					httpCode: resp.status,
					errorMsg: resp.errorMessage]
	}
	if (respLog != [:]) {
		logWarn("distResp: ${respLog}")
	}
}
def deviceSetupParse(mainData) {
	if (mainData.mediaInputSource != null) {
		def supportedInputs =  mainData.mediaInputSource.supportedInputSources.value
		sendEvent(name: "supportedInputs", value: supportedInputs)	
		state.supportedInputs = supportedInputs
	} else {
		state.remove("supportedInputs")
	}
	if (setupData != [:]) {
		logInfo("deviceSetupParse: ${setupData}")
	}
	listAttributes(true)
}
def statusParse(mainData) {
	def onOff = mainData.switch.switch.value
	if (device.currentValue("switch") != onOff) {
		sendEvent(name: "switch", value: onOff)
	}
	
	def volume = mainData.audioVolume.volume.value.toInteger()
	sendEvent(name: "volume", value: volume)

	def mute = mainData.audioMute.mute.value
	sendEvent(name: "mute", value: mute)

	def status = mainData.mediaPlayback.playbackStatus.value
	sendEvent(name: "status", value: status)
	
	if (mainData.mediaInputSource != null) {
		def mediaInputSource = mainData.mediaInputSource.inputSource.value
		sendEvent(name: "mediaInputSource", value: mediaInputSource)
	}
	
	if (mainData.audioTrackData != null) {
		def trackData = mainData.audioTrackData.audioTrackData.value
		sendEvent(name: "trackData", value: trackData)
	}

	if (simulate() == true) {
		runIn(1, listAttributes, [data: true])
	} else {
		runIn(1, listAttributes)
	}
}

//	===== Library Integration =====



def simulate() { return false }
//#include davegut.Samsung-Soundbar-Sim

// ~~~~~ start include (1072) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8

//	Logging during development // library marker davegut.Logging, line 10
def listAttributes(trace = false) { // library marker davegut.Logging, line 11
	def attrs = device.getSupportedAttributes() // library marker davegut.Logging, line 12
	def attrList = [:] // library marker davegut.Logging, line 13
	attrs.each { // library marker davegut.Logging, line 14
		def val = device.currentValue("${it}") // library marker davegut.Logging, line 15
		attrList << ["${it}": val] // library marker davegut.Logging, line 16
	} // library marker davegut.Logging, line 17
	if (trace == true) { // library marker davegut.Logging, line 18
		logInfo("Attributes: ${attrList}") // library marker davegut.Logging, line 19
	} else { // library marker davegut.Logging, line 20
		logDebug("Attributes: ${attrList}") // library marker davegut.Logging, line 21
	} // library marker davegut.Logging, line 22
} // library marker davegut.Logging, line 23

//	6.7.2 Change B.  Remove driverVer() // library marker davegut.Logging, line 25
def logTrace(msg){ // library marker davegut.Logging, line 26
	log.trace "${device.displayName}: ${msg}" // library marker davegut.Logging, line 27
} // library marker davegut.Logging, line 28

def logInfo(msg) {  // library marker davegut.Logging, line 30
	if (!infoLog || infoLog == true) { // library marker davegut.Logging, line 31
		log.info "${device.displayName}: ${msg}" // library marker davegut.Logging, line 32
	} // library marker davegut.Logging, line 33
} // library marker davegut.Logging, line 34

def debugLogOff() { // library marker davegut.Logging, line 36
	if (debug == true) { // library marker davegut.Logging, line 37
		device.updateSetting("debug", [type:"bool", value: false]) // library marker davegut.Logging, line 38
	} else if (debugLog == true) { // library marker davegut.Logging, line 39
		device.updateSetting("debugLog", [type:"bool", value: false]) // library marker davegut.Logging, line 40
	} // library marker davegut.Logging, line 41
	logInfo("Debug logging is false.") // library marker davegut.Logging, line 42
} // library marker davegut.Logging, line 43

def logDebug(msg) { // library marker davegut.Logging, line 45
	if (debug == true || debugLog == true) { // library marker davegut.Logging, line 46
		log.debug "${device.displayName}: ${msg}" // library marker davegut.Logging, line 47
	} // library marker davegut.Logging, line 48
} // library marker davegut.Logging, line 49

def logWarn(msg) { log.warn "${device.displayName}: ${msg}" } // library marker davegut.Logging, line 51

// ~~~~~ end include (1072) davegut.Logging ~~~~~

// ~~~~~ start include (1091) davegut.ST-Communications ~~~~~
library ( // library marker davegut.ST-Communications, line 1
	name: "ST-Communications", // library marker davegut.ST-Communications, line 2
	namespace: "davegut", // library marker davegut.ST-Communications, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Communications, line 4
	description: "ST Communications Methods", // library marker davegut.ST-Communications, line 5
	category: "utilities", // library marker davegut.ST-Communications, line 6
	documentationLink: "" // library marker davegut.ST-Communications, line 7
) // library marker davegut.ST-Communications, line 8
import groovy.json.JsonSlurper // library marker davegut.ST-Communications, line 9

private asyncGet(sendData, passData = "none") { // library marker davegut.ST-Communications, line 11
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 12
		logWarn("asyncGet: [status: ERROR, errorMsg: no stApiKey]") // library marker davegut.ST-Communications, line 13
	} else { // library marker davegut.ST-Communications, line 14
		logDebug("asyncGet: ${sendData}, ${passData}") // library marker davegut.ST-Communications, line 15
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 16
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 17
			path: sendData.path, // library marker davegut.ST-Communications, line 18
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()]] // library marker davegut.ST-Communications, line 19
		try { // library marker davegut.ST-Communications, line 20
			asynchttpGet(sendData.parse, sendCmdParams, [reason: passData]) // library marker davegut.ST-Communications, line 21
		} catch (error) { // library marker davegut.ST-Communications, line 22
			logWarn("asyncGet: [status: FAILED, errorMsg: ${error}]") // library marker davegut.ST-Communications, line 23
		} // library marker davegut.ST-Communications, line 24
	} // library marker davegut.ST-Communications, line 25
} // library marker davegut.ST-Communications, line 26

private syncGet(path){ // library marker davegut.ST-Communications, line 28
	def respData = [:] // library marker davegut.ST-Communications, line 29
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 30
		respData << [status: "FAILED", // library marker davegut.ST-Communications, line 31
					 errorMsg: "No stApiKey"] // library marker davegut.ST-Communications, line 32
	} else { // library marker davegut.ST-Communications, line 33
		logDebug("syncGet: ${sendData}") // library marker davegut.ST-Communications, line 34
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 35
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 36
			path: path, // library marker davegut.ST-Communications, line 37
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()] // library marker davegut.ST-Communications, line 38
		] // library marker davegut.ST-Communications, line 39
		try { // library marker davegut.ST-Communications, line 40
			httpGet(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 41
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 42
					respData << [status: "OK", results: resp.data] // library marker davegut.ST-Communications, line 43
				} else { // library marker davegut.ST-Communications, line 44
					respData << [status: "FAILED", // library marker davegut.ST-Communications, line 45
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 46
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 47
				} // library marker davegut.ST-Communications, line 48
			} // library marker davegut.ST-Communications, line 49
		} catch (error) { // library marker davegut.ST-Communications, line 50
			respData << [status: "FAILED", // library marker davegut.ST-Communications, line 51
						 httpCode: "Timeout", // library marker davegut.ST-Communications, line 52
						 errorMsg: error] // library marker davegut.ST-Communications, line 53
		} // library marker davegut.ST-Communications, line 54
	} // library marker davegut.ST-Communications, line 55
	return respData // library marker davegut.ST-Communications, line 56
} // library marker davegut.ST-Communications, line 57

private syncPost(sendData){ // library marker davegut.ST-Communications, line 59
	def respData = [:] // library marker davegut.ST-Communications, line 60
	if (!stApiKey || stApiKey.trim() == "") { // library marker davegut.ST-Communications, line 61
		respData << [status: "FAILED", // library marker davegut.ST-Communications, line 62
					 errorMsg: "No stApiKey"] // library marker davegut.ST-Communications, line 63
	} else { // library marker davegut.ST-Communications, line 64
		logDebug("syncPost: ${sendData}") // library marker davegut.ST-Communications, line 65

		def cmdBody = [commands: [sendData.cmdData]] // library marker davegut.ST-Communications, line 67
		def sendCmdParams = [ // library marker davegut.ST-Communications, line 68
			uri: "https://api.smartthings.com/v1", // library marker davegut.ST-Communications, line 69
			path: sendData.path, // library marker davegut.ST-Communications, line 70
			headers: ['Authorization': 'Bearer ' + stApiKey.trim()], // library marker davegut.ST-Communications, line 71
			body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.ST-Communications, line 72
		] // library marker davegut.ST-Communications, line 73
		try { // library marker davegut.ST-Communications, line 74
			httpPost(sendCmdParams) {resp -> // library marker davegut.ST-Communications, line 75
				if (resp.status == 200 && resp.data != null) { // library marker davegut.ST-Communications, line 76
					respData << [status: "OK", results: resp.data.results] // library marker davegut.ST-Communications, line 77
				} else { // library marker davegut.ST-Communications, line 78
					respData << [status: "FAILED", // library marker davegut.ST-Communications, line 79
								 httpCode: resp.status, // library marker davegut.ST-Communications, line 80
								 errorMsg: resp.errorMessage] // library marker davegut.ST-Communications, line 81
				} // library marker davegut.ST-Communications, line 82
			} // library marker davegut.ST-Communications, line 83
		} catch (error) { // library marker davegut.ST-Communications, line 84
			respData << [status: "FAILED", // library marker davegut.ST-Communications, line 85
						 httpCode: "Timeout", // library marker davegut.ST-Communications, line 86
						 errorMsg: error] // library marker davegut.ST-Communications, line 87
		} // library marker davegut.ST-Communications, line 88
	} // library marker davegut.ST-Communications, line 89
	return respData // library marker davegut.ST-Communications, line 90
} // library marker davegut.ST-Communications, line 91

// ~~~~~ end include (1091) davegut.ST-Communications ~~~~~

// ~~~~~ start include (1090) davegut.ST-Common ~~~~~
library ( // library marker davegut.ST-Common, line 1
	name: "ST-Common", // library marker davegut.ST-Common, line 2
	namespace: "davegut", // library marker davegut.ST-Common, line 3
	author: "Dave Gutheinz", // library marker davegut.ST-Common, line 4
	description: "ST Wash/Dryer Common Methods", // library marker davegut.ST-Common, line 5
	category: "utilities", // library marker davegut.ST-Common, line 6
	documentationLink: "" // library marker davegut.ST-Common, line 7
) // library marker davegut.ST-Common, line 8

def commonUpdate() { // library marker davegut.ST-Common, line 10
	if (!stApiKey || stApiKey == "") { // library marker davegut.ST-Common, line 11
		return [status: "FAILED", reason: "No stApiKey"] // library marker davegut.ST-Common, line 12
	} // library marker davegut.ST-Common, line 13
	if (!stDeviceId || stDeviceId == "") { // library marker davegut.ST-Common, line 14
		getDeviceList() // library marker davegut.ST-Common, line 15
		return [status: "FAILED", reason: "No stDeviceId"] // library marker davegut.ST-Common, line 16
	} // library marker davegut.ST-Common, line 17

	unschedule() // library marker davegut.ST-Common, line 19
	def updateData = [:] // library marker davegut.ST-Common, line 20
	updateData << [status: "OK"] // library marker davegut.ST-Common, line 21
	if (debugLog) { runIn(1800, debugLogOff) } // library marker davegut.ST-Common, line 22
	updateData << [stDeviceId: stDeviceId] // library marker davegut.ST-Common, line 23
	updateData << [debugLog: debugLog, infoLog: infoLog] // library marker davegut.ST-Common, line 24
	if (!getDataValue("driverVersion") ||  // library marker davegut.ST-Common, line 25
		getDataValue("driverVersion") != driverVer()) { // library marker davegut.ST-Common, line 26
		updateDataValue("driverVersion", driverVer()) // library marker davegut.ST-Common, line 27
		updateData << [driverVer: driverVer()] // library marker davegut.ST-Common, line 28
	} // library marker davegut.ST-Common, line 29
	setPollInterval(pollInterval) // library marker davegut.ST-Common, line 30
	updateData << [pollInterval: pollInterval] // library marker davegut.ST-Common, line 31

	runIn(5, refresh) // library marker davegut.ST-Common, line 33
	return updateData // library marker davegut.ST-Common, line 34
} // library marker davegut.ST-Common, line 35

def setPollInterval(pollInterval) { // library marker davegut.ST-Common, line 37
	logDebug("setPollInterval: ${pollInterval}") // library marker davegut.ST-Common, line 38
	state.pollInterval = pollInterval // library marker davegut.ST-Common, line 39
	switch(pollInterval) { // library marker davegut.ST-Common, line 40
		case "10sec":  // library marker davegut.ST-Common, line 41
			schedule("*/10 * * * * ?", "poll")		 // library marker davegut.ST-Common, line 42
			break // library marker davegut.ST-Common, line 43
		case "20sec": // library marker davegut.ST-Common, line 44
			schedule("*/20 * * * * ?", "poll")		 // library marker davegut.ST-Common, line 45
			break // library marker davegut.ST-Common, line 46
		case "30sec": // library marker davegut.ST-Common, line 47
			schedule("*/30 * * * * ?", "poll")		 // library marker davegut.ST-Common, line 48
			break // library marker davegut.ST-Common, line 49
		case "1" : runEvery1Minute(poll); break // library marker davegut.ST-Common, line 50
		case "5" : runEvery5Minutes(poll); break // library marker davegut.ST-Common, line 51
		case "10" : runEvery10Minutes(poll); break // library marker davegut.ST-Common, line 52
		case "30" : runEvery30Minutes(poll); break // library marker davegut.ST-Common, line 53
		default: runEvery10Minutes(poll) // library marker davegut.ST-Common, line 54
	} // library marker davegut.ST-Common, line 55
} // library marker davegut.ST-Common, line 56

def deviceCommand(cmdData) { // library marker davegut.ST-Common, line 58
	def respData = [:] // library marker davegut.ST-Common, line 59
	if (simulate() == true) { // library marker davegut.ST-Common, line 60
		respData = testResp(cmdData) // library marker davegut.ST-Common, line 61
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 62
		respData << [status: "FAILED", data: "no stDeviceId"] // library marker davegut.ST-Common, line 63
	} else { // library marker davegut.ST-Common, line 64
		def sendData = [ // library marker davegut.ST-Common, line 65
			path: "/devices/${stDeviceId.trim()}/commands", // library marker davegut.ST-Common, line 66
			cmdData: cmdData // library marker davegut.ST-Common, line 67
		] // library marker davegut.ST-Common, line 68
		respData = syncPost(sendData) // library marker davegut.ST-Common, line 69
	} // library marker davegut.ST-Common, line 70
	if (cmdData.capability && cmdData.capability != "refresh") { // library marker davegut.ST-Common, line 71
		refresh() // library marker davegut.ST-Common, line 72
	} else { // library marker davegut.ST-Common, line 73
		poll() // library marker davegut.ST-Common, line 74
	} // library marker davegut.ST-Common, line 75
	return respData // library marker davegut.ST-Common, line 76
} // library marker davegut.ST-Common, line 77

def refresh() { // library marker davegut.ST-Common, line 79
	if (stApiKey!= null) { // library marker davegut.ST-Common, line 80
		def cmdData = [ // library marker davegut.ST-Common, line 81
			component: "main", // library marker davegut.ST-Common, line 82
			capability: "refresh", // library marker davegut.ST-Common, line 83
			command: "refresh", // library marker davegut.ST-Common, line 84
			arguments: []] // library marker davegut.ST-Common, line 85
		deviceCommand(cmdData) // library marker davegut.ST-Common, line 86
	} // library marker davegut.ST-Common, line 87
} // library marker davegut.ST-Common, line 88

def poll() { // library marker davegut.ST-Common, line 90
	if (simulate() == true) { // library marker davegut.ST-Common, line 91
		def children = getChildDevices() // library marker davegut.ST-Common, line 92
		if (children) { // library marker davegut.ST-Common, line 93
			children.each { // library marker davegut.ST-Common, line 94
				it.statusParse(testData()) // library marker davegut.ST-Common, line 95
			} // library marker davegut.ST-Common, line 96
		} // library marker davegut.ST-Common, line 97
		statusParse(testData()) // library marker davegut.ST-Common, line 98
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 99
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 100
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 101
	} else { // library marker davegut.ST-Common, line 102
		def sendData = [ // library marker davegut.ST-Common, line 103
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 104
			parse: "distResp" // library marker davegut.ST-Common, line 105
			] // library marker davegut.ST-Common, line 106
		asyncGet(sendData, "statusParse") // library marker davegut.ST-Common, line 107
	} // library marker davegut.ST-Common, line 108
} // library marker davegut.ST-Common, line 109

def deviceSetup() { // library marker davegut.ST-Common, line 111
	if (simulate() == true) { // library marker davegut.ST-Common, line 112
		def children = getChildDevices() // library marker davegut.ST-Common, line 113
		deviceSetupParse(testData()) // library marker davegut.ST-Common, line 114
	} else if (!stDeviceId || stDeviceId.trim() == "") { // library marker davegut.ST-Common, line 115
		respData = "[status: FAILED, data: no stDeviceId]" // library marker davegut.ST-Common, line 116
		logWarn("poll: [status: ERROR, errorMsg: no stDeviceId]") // library marker davegut.ST-Common, line 117
	} else { // library marker davegut.ST-Common, line 118
		def sendData = [ // library marker davegut.ST-Common, line 119
			path: "/devices/${stDeviceId.trim()}/status", // library marker davegut.ST-Common, line 120
			parse: "distResp" // library marker davegut.ST-Common, line 121
			] // library marker davegut.ST-Common, line 122
		asyncGet(sendData, "deviceSetup") // library marker davegut.ST-Common, line 123
	} // library marker davegut.ST-Common, line 124
} // library marker davegut.ST-Common, line 125

def getDeviceList() { // library marker davegut.ST-Common, line 127
	def sendData = [ // library marker davegut.ST-Common, line 128
		path: "/devices", // library marker davegut.ST-Common, line 129
		parse: "getDeviceListParse" // library marker davegut.ST-Common, line 130
		] // library marker davegut.ST-Common, line 131
	asyncGet(sendData) // library marker davegut.ST-Common, line 132
} // library marker davegut.ST-Common, line 133

def getDeviceListParse(resp, data) { // library marker davegut.ST-Common, line 135
	def respData // library marker davegut.ST-Common, line 136
	if (resp.status != 200) { // library marker davegut.ST-Common, line 137
		respData = [status: "ERROR", // library marker davegut.ST-Common, line 138
					httpCode: resp.status, // library marker davegut.ST-Common, line 139
					errorMsg: resp.errorMessage] // library marker davegut.ST-Common, line 140
	} else { // library marker davegut.ST-Common, line 141
		try { // library marker davegut.ST-Common, line 142
			respData = new JsonSlurper().parseText(resp.data) // library marker davegut.ST-Common, line 143
		} catch (err) { // library marker davegut.ST-Common, line 144
			respData = [status: "ERROR", // library marker davegut.ST-Common, line 145
						errorMsg: err, // library marker davegut.ST-Common, line 146
						respData: resp.data] // library marker davegut.ST-Common, line 147
		} // library marker davegut.ST-Common, line 148
	} // library marker davegut.ST-Common, line 149
	if (respData.status == "ERROR") { // library marker davegut.ST-Common, line 150
		logWarn("getDeviceListParse: ${respData}") // library marker davegut.ST-Common, line 151
	} else { // library marker davegut.ST-Common, line 152
		log.info "" // library marker davegut.ST-Common, line 153
		respData.items.each { // library marker davegut.ST-Common, line 154
			log.trace "${it.label}:   ${it.deviceId}" // library marker davegut.ST-Common, line 155
		} // library marker davegut.ST-Common, line 156
		log.trace "<b>Copy your device's deviceId value and enter into the device Preferences.</b>" // library marker davegut.ST-Common, line 157
	} // library marker davegut.ST-Common, line 158
} // library marker davegut.ST-Common, line 159

def calcTimeRemaining(completionTime) { // library marker davegut.ST-Common, line 161
	Integer currTime = now() // library marker davegut.ST-Common, line 162
	Integer compTime // library marker davegut.ST-Common, line 163
	try { // library marker davegut.ST-Common, line 164
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 165
	} catch (e) { // library marker davegut.ST-Common, line 166
		compTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", completionTime,TimeZone.getTimeZone('UTC')).getTime() // library marker davegut.ST-Common, line 167
	} // library marker davegut.ST-Common, line 168
	Integer timeRemaining = ((compTime-currTime) /1000).toInteger() // library marker davegut.ST-Common, line 169
	if (timeRemaining < 0) { timeRemaining = 0 } // library marker davegut.ST-Common, line 170
	return timeRemaining // library marker davegut.ST-Common, line 171
} // library marker davegut.ST-Common, line 172

// ~~~~~ end include (1090) davegut.ST-Common ~~~~~
