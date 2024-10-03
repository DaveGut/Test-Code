/*	TP-Link TAPO plug, switches, lights, hub, and hub sensors.
		Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md



DEVELOPMENT ONLY

Get Tapo Discovery Data
=================================================================================================*/
//import groovy.json.JsonOutput
//import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
//import org.json.JSONObject


metadata {
	definition (name: "Get Tapo Discovery Data", namespace: "davegut", author: "Dave Gutheinz", 
				importUrl: "https://raw.githubusercontent.com/DaveGut/tpLink_Hubitat/main/Drivers/tpLink_plug.groovy")
	{
		command "getTapoData"
	}
	preferences {
		input ("ip", "string", title: "IP Address of Tapo Device", defaultValue: "notSet")
	}
}

def installed() {
}
def updated() {
	if (ip == "notSet") {
		logData << [error: "IP NOT SET"]
		log.warn(logData)
	} else {
		runIn(1, getTapoData)
	}
}

def getTapoData() {
	Integer port = 20002
	def cmdData = "0200000101e51100095c11706d6f58577b22706172616d73223a7b227273615f6b6579223a222d2d2d2d2d424547494e205055424c4943204b45592d2d2d2d2d5c6e4d494942496a414e42676b71686b6947397730424151454641414f43415138414d49494243674b43415145416d684655445279687367797073467936576c4d385c6e54646154397a61586133586a3042712f4d6f484971696d586e2b736b4e48584d525a6550564134627532416257386d79744a5033445073665173795679536e355c6e6f425841674d303149674d4f46736350316258367679784d523871614b33746e466361665a4653684d79536e31752f564f2f47474f795436507459716f384e315c6e44714d77373563334b5a4952387a4c71516f744657747239543337536e50754a7051555a7055376679574b676377716e7338785a657a78734e6a6465534171765c6e3167574e75436a5356686d437931564d49514942576d616a37414c47544971596a5442376d645348562f2b614a32564467424c6d7770344c7131664c4f6a466f5c6e33737241683144744a6b537376376a624f584d51695666453873764b6877586177717661546b5658382f7a4f44592b2f64684f5374694a4e6c466556636c35585c6e4a514944415141425c6e2d2d2d2d2d454e44205055424c4943204b45592d2d2d2d2d5c6e227d7d"
	log.info([method: "findDevice", ip: ip, port: port])
	sendLanCmd(ip, port, cmdData, parseLanData)
}
private sendLanCmd(ip, port, cmdData, action, commsTo = 5) {
	Map data = [port: port, action: action]
	log.info("sendLanCmd: ${data}")
	def myHubAction = new hubitat.device.HubAction(
		cmdData,
		hubitat.device.Protocol.LAN,
		[
			type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
			destinationAddress: "${ip}:${port}",
			encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
			parseWarning: true,
			timeout: commsTo,
			callback: action])
	try {
		sendHubCommand(myHubAction)
	} catch (error) {
		log.warn("sendLanCmd: command failed. Error = ${error}")
	}
}
def parseLanData(response) {
log.trace response
	def respData = parseLanMessage(response)
	byte[] payloadByte = hubitat.helper.HexUtils.hexStringToByteArray(respData.payload.drop(32))
	def payload = new String(payloadByte)
	if (payload.length() > 1007) {
		payload = payload + """"}}}"""
	}
	Map pollResp = new JsonSlurper().parseText(payload).result
	log.debug([POLLRESP: new JsonSlurper().parseText(payload).result])
}

