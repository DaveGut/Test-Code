/*
Test only.  Light the programming fire for WS Samsung Remote.
Test is for 2014 Samsung TV Only.  Will NOT work on 2015 TV.
A.	Turn on your TV.  Be prepared to approve a connection approval on your TV display.
B.  Install Driver
C.	Open logging in a spearate window
D.  Enter IP Address and MAC (no colons).
E.	Save preferences then refresh your browser.
F.  Note that state macAddress64 now exists
	If the above do not occur - STOP  Next steps will not work.
G.	Select "Authenticate".  Looking for no ERROR message in the log.
H.	Run the command "Menu" and observe TV response.  Menu should appear.
I.	Run the command "Menu" again and observe the TV response.  Menu should disappear.

After finishing, copy log and paste so I can see results.  Also report any observations.
*/
import groovy.json.JsonOutput
import org.json.JSONObject
metadata {
	definition (name: "Ole Fashioned Samsung Remote",
				namespace: "davegut",
				author: "David Gutheinz"
			   ){
		command "authenticate"
		command "mute"
		command "menu"
		command "sendKey", [[name: "Key name (i.e., MUTE)", type: "STRING"]]
	}
	preferences {
		input ("deviceIp", "text", title: "Device Ip")
		input ("deviceMac", "text", title: "Device MAC, format AABBCCDDEEFF")
	}
}
def installed() { }
def updated() {
	state.macAddress64 = deviceMac.encodeAsBase64().toString()
}

def authenticate() { sendKey("AUTHENTICATE") }
def mute() { sendKey("MUTE") }
def menu() { sendKey("MENU") }
private sendKey(key) {
	log.info "tvAction ${key}"
//	Data about the Hubitat Device
	def app = "iphone..iapp.samsung"
	def appLength = app.getBytes().size()
    def tvApp = "iphone.UN60ES8000.iapp.samsung"
	def tvAppLength = tvApp.getBytes().size()
	def remote = "SmartThings".encodeAsBase64().toString()
	def remoteLength = remote.getBytes().size()
//	Data about the TV
	def ipAddress = deviceIp.encodeAsBase64().toString()
	def ipAddressLength = ipAddress.getBytes().size()
	def macAddressLength = state.macAddress64.getBytes().size()
	
	// The Authentication Messages
	def authMsg = "${(char)0x64}${(char)0x00}${(char)ipAddressLength}${(char)0x00}${ipAddress}${(char)macAddressLength}${(char)0x00}${state.macAddress64}${(char)remoteLength}${(char)0x00}${remote}"
log.trace "authMsg = ${authMsg}"
	def authMsgLength = authMsg.getBytes().size()
log.trace "authMsgLength = ${authMsgLength}"
	def authPacket = "${(char)0x00}${(char)appLength}${(char)0x00}${app}${(char)authMsgLength}${(char)0x00}${authMsg}"
log.trace "authPacket = ${authPacket}"
	
	if (key == "AUTHENTICATE") {
//	Send Authenticate Message
		sendHubCommand(new hubitat.device.HubAction(
			authPacket,
			hubitat.device.Protocol.LAN,
			[destinationAddress: "${deviceIp}:55000",
			 timeout: 10]
		))
	} else {
//	Send Key Message
		key = key.toUpperCase()
		def command = "KEY_${key}".encodeAsBase64().toString()
		def commandLength = command.getBytes().size()
		def actionMsg = "${(char)0x00}${(char)0x00}${(char)0x00}${(char)commandLength}${(char)0x00}${command}"
log.trace "actionMsg = ${actionMsg}"
		def actionMsgLength = actionMsg.getBytes().size()
log.trace "actionMsgLength = ${actionMsgLength}"
		def actionPacket = "${(char)0x00}${(char)tvAppLength}${(char)0x00}${tvApp}${(char)actionMsgLength}${(char)0x00}${actionMsg}"
log.trace "actionPacket = ${actionPacket}"
		sendHubCommand(new hubitat.device.HubAction(
			authPacket + actionPacket,
			hubitat.device.Protocol.LAN,
			[destinationAddress: "${deviceIp}:55000",
			 timeout: 10]
		))
	}
}

def parse(resp) {
	log.trace "at Parse"
}
