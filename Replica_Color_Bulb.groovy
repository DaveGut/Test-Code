/**
*  Copyright 2023 David Gutheinz
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*
*/
@SuppressWarnings('unused')
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.Field
@Field volatile static Map<String,Long> g_mEventSendTime = [:]
public static String driverVer() { return "0.0.1" }

metadata {
	definition (name: "Replica Color Bulb",
				namespace: "replica",
				author: "Dave Gutheinz",
				importUrl: ""
			   ) {
		capability "Light"
		capability "Switch"
		capability "Switch Level"
		capability "Change Level"
		capability "Color Temperature"
		command "setColorTemperature", [[
			name: "Color Temperature",
			type: "NUMBER"]]
		capability "Color Temperature"
		capability "Color Mode"
		capability "Color Control"
		capability "Refresh"
		capability "Actuator"
		capability "Configuration"
		
//		attribute "healthStatus", "enum", ["offline", "online"]
	}
	preferences {
		input ("textEnable", "bool", 
			   title: "Enable descriptionText logging",
			   defaultValue: true)
		input ("logEnable", "bool",
			   title: "Enable debug logging",
			   defaultValue: false)
		input ("transTime", "number",
			   title: "Default Transition time (seconds)",
			   defaultValue: 1)
		input ("ctLow", "number", title: "lowerLimit of Color Temp", defaultValue: 2000)
		input ("ctHigh", "number", title: "UpperLimit of Color Temp", defaultValue: 9000)
	}
}

def installed() {
	initialize()	
}

def updated() {
	initialize()	
}

def initialize() {
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers()))
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands()))
}

def configure() {
    logInfo "configure: configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
	sendCommand("configure")
}

void refresh() {
	sendCommand("refresh")
}

//	Capability Switch
def on() {
	sendCommand("on")
}

def off() {
	sendCommand("off")
}

def setSwitchValue(value) {
    sendEvent(name: "switch", value: value)
	logInfo("setSwitchValue: ${value}")
}


//	Capability SwitchLevel
def setLevel(level, transTime = transTime) {
	level = checkLevel(level)
	transTime = checkTransTime(transTime)
	sendCommand("setLevel", level, null, [rate:transTime])
}

def startLevelChange(direction) {
	unschedule(levelUp)
	unschedule(levelDown)
	if (direction == "up") { levelUp() }
	else { levelDown() }
}

def stopLevelChange() {
	unschedule(levelUp)
	unschedule(levelDown)
}

def levelUp() {
	def curLevel = device.currentValue("level").toInteger()
	if (curLevel == 100) { return }
	def newLevel = curLevel + 4
	if (newLevel > 100) { newLevel = 100 }
	setLevel(newLevel, 0)
	runIn(1, levelUp)
}

def levelDown() {
	def curLevel = device.currentValue("level").toInteger()
	if (curLevel == 0 || device.currentValue("switch") == "off") { return }
	def newLevel = curLevel - 4
	if (newLevel < 0) { off() }
	else {
		setLevel(newLevel, 0)
		runIn(1, levelDown)
	}
}

def setLevelValue(value) {
	sendEvent(name: "level", value: value, unit: "%")
	logInfo("setLevelValue: ${value}%")
}


//	Capability Color Control
def setColorTemperature(colorTemp) {
	if (colorTemp > ctHigh) { colorTemp = ctHigh}
	else if (colorTemp < ctLow) { colorTemp = ctLow}
	sendCommand("setColorTemperature", colorTemp)
}

def setColorTemperatureValue(value) {
	if (value.toInteger() != device.currentValue("colorTemperature")) {
		sendEvent(name: "colorTemperature", value: value, unit: "°K")
		def colorName = convertTemperatureToGenericColorName(value.toInteger())
		sendEvent(name: "colorName", value: colorName)
		sendEvent(name: "colorMode", value: "CT")
		logInfo("setColorTemperatureValue: [colorTemperature: ${value}°K, colorName: ${colorName}]")
	}
}


def setHue(hue) {
//	hue = checkLevel(hue)
	sendCommand("setHue", hue)
log.trace "hue: $hue"
}

def setHueValue(value) {
	if (value.toInteger() != device.currentValue("hue")) {
		sendEvent(name: "hue", value: value, unit: "%")
		def colorName = convertHueToGenericColorName(value.toInteger())
		sendEvent(name: "colorName", value: colorName)
		sendEvent(name: "colorMode", value: "COLOR")
		logInfo("setHueValue: ${value}%")
	}
}


def setSaturation(saturation) {
//	saturation = checkLevel(saturation)
	sendCommand("setSaturation", saturation)
}

def setSaturationValue(value) {
	sendEvent(name: "saturation", value: value, unit: "%")
	logInfo("setSaturationValue: ${value}%")
}


def setColor(Map color) {
	def cmdData = [hue: color.hue,
				   saturation: color.saturation,
				   level: color.level]
	cmdData = JsonOutput.toJson(cmdData)
	sendCommand("setColor", cmdData)
	logTrace("setColor: ${cmdData}")
}

def setColorValue(value) {
	sendEvent(name: "color", value: value, unit: "%")
	logInfo("setColorValue: ${value}%")
}


def checkTransTime(transTime) {
	if (transTime == null || transTime < 0) { transTime = 0 }
	transTime = 1000 * transTime.toInteger()
	if (transTime > 8000) { transTime = 8000 }
	return transTime
}

def checkLevel(level) {
	if (level == null || level < 0) {
		level = 0
	} else if (level > 100) {
		level = 100
	}
	return level
}


private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
}

Map getReplicaCommands() {
	Map replicaCommands = [ 
		setSwitchValue:[[name:"switch*",type:"ENUM"]], 
		setLevelValue:[[name:"level*",type:"NUMBER"]] ,
		setColorTemperatureValue:[[name: "colorTemperature", type: "NUMBER"]],
		setHueValue:[[name: "hue", type: "NUMBER"]],
		setSaturationValue:[[name: "saturation", type: "NUMBER"]],
		setColorValue:[[name: "color", type: "STRING"]]]
	return replicaCommands
}

Map getReplicaTriggers() {
	def replicaTriggers = [
		off:[],
		on:[],
		setLevel: [
			[name:"level*", type: "NUMBER"],
			[name:"rate", type:"NUMBER",data:"rate"]],
		setColorTemperature: [
			[name:"colorTemperature", type: "NUMBER"]],
		setHue: [
			[name:"hue", type: "NUMBER"]],
		setSaturation: [
			[name:"saturation", type: "NUMBER"]],
		setColor: [
			[name: "color", type: "OBJECT"]],
		refresh:[]]
	return replicaTriggers
}

String getReplicaRules() {
	Map rules = [version:1,
				 components:[
					 [
						 trigger:[
							 name:"off", 
							 label:"command: off()", 
							 type:"command"],
						 command:[name:"off", type:"command", capability:"switch", label:"command: off()"],
						 type: "hubitatTrigger"],		//	works
					 [
						 trigger:[name: "on", label:"command: on()",type:"command"],
						 command:[name:"on",type:"command",capability:"switch",label:"command: on()"],
						 type:"hubitatTrigger"],		//	works
					 [
						 trigger:[type:"attribute",
								  properties:[value:["title":"switch",type:"string"]],
								  additionalProperties:false,"required":["value"],
								  capability:"switch",
								  attribute:"switch",
								  label:"attribute: switch.*"],
						 command:[name:"setSwitchValue",
								  label:"command: setSwitchValue(switch*)"
								  ,type:"command",
								  parameters:[
									  ["name":"switch*","type":"ENUM"]]],
						 type:"smartTrigger"],		//	works
					 [
						 trigger:[name: "setLevel",
								  label:"command: setLevel(level*, rate)",
								  type:"command",
								  parameters:[
									  [name:"level*",type:"NUMBER"],
									  [name:"rate",type:"NUMBER",data:"rate"]]],
						 command:[name:"setLevel", 
								  arguments:[
									  [name:"level",optional:false,schema:[type:"integer",minimum:0,maximum:100]],
									  [name:"rate",optional:true,schema:[title:"PositiveInteger",type:"integer",minimum:0]]],
								  type:"command",
								  capability:"Switch Level",
								  label:"command: setLevel(level*, rate)"],
						 type:"hubitatTrigger"],		//	works
					 [
						 trigger:[type:"attribute",
								  properties:[value:[type:"integer",minimum:0,maximum:100],
											  unit:[type:"string",enum:["%"],default:"%"]],
								  additionalProperties:false,
								  required:["value"],
								  capability:"switchLevel",
								  attribute:"level",
								  label:"attribute: level.*"],
						 command:[name:"setLevelValue",
								  label:"command: setLevelValue(level*)",
								  type:"command",
								  parameters:[[name:"level*",type:"NUMBER"]]],
						 type:"smartTrigger"],		//	works
					 [
						 trigger:[
							 name: "setColorTemperature", 
							 label:"command: setColorTemperature(temperature*)",
							 type:"command",
							 parameters:[[name:"colorTemperature*",type:"NUMBER"]]],
						 command:[name:"setColorTemperature", 
								  arguments:[
									  [name:"temperature",
									   optional:false,
									   schema:[type:"integer",minimum:1,maximum:30000]]],
								  type:"command",
								  capability:"colorTemperature",
								  label:"command: setColorTemperature(temperature*)"],
						 type:"hubitatTrigger"],
					 [
						 trigger:[type:"attribute",
								  properties:[value:[type:"integer",minimum:0,maximum:100],
											  unit:[type:"string",enum:["K"],default:"K"]],
								  additionalProperties:false,
								  required:["value"],
								  capability:"colorTemperature",
								  attribute:"colorTemperature",
								  label:"attribute: colorTemperature.*"],
						 command:[name:"setColorTemperatureValue",
								  label:"command: setColorTemperatureValue(colorTemperature*)",
								  type:"command",
								  parameters:[[name:"colorTemperature*",type:"NUMBER"]]],
						 type:"smartTrigger"],
					 [
						 trigger:[
							 name: "setHue",
							 label:"command: setHue(hue*)",
							 type:"command",
						 parameters:[[name:"hue*",type:"NUMBER"]]],
						 command:[name:"setHue", 
								  arguments:[
									  [name:"hue",
									   optional:false,
									   schema:[title: "PositiveNumber",type:"string",minimum:0]]],
								  type:"command",
								  capability:"colorControl",
								  label:"command: setHue(hue*)"],
						 type:"hubitatTrigger"],
					 [
						 trigger:[type:"attribute",
								  properties:[value:[title:"PositiveNumber",type:"NUMBER",minimum:0]],
								  additionalProperties:false,
								  required:[],
								  capability:"colorControl",
								  attribute:"hue",
								  label:"attribute: hue.*"],
						 command:[name:"setHueValue",
								  label:"command: setHueValue(hue*)",
								  type:"command",
								  parameters:[[name:"hue*",type:"NUMBER"]]],
						 type:"smartTrigger"],
					 [
						 trigger:[name: "setSaturation", label:"command: setSaturation(saturation*)",type:"command",
						 parameters:[[name:"setSaturation*",type:"NUMBER"]]],
						 command:[name:"setSaturation", 
								  arguments:[[name:"saturation",optional:false,schema:[type:"integer",minimum:0,maximum:100]]],
								  type:"command",capability:"colorControl",label:"command: setSaturation(saturation*)"],
						 type:"hubitatTrigger"],
					 [
						 trigger:[
							 name: "setColor", 
							 label:"command: setColor(color*)",
							 type:"command",
							 parameters:[[name:"color*",type:"object"]]],
						 command:[name:"setColor", 
								  arguments:[[
									  name:"color",
									  optional:false,
									  schema:[
										  title: "COLOR_MAP",
										  type: "object",
										  additionalProperties: false,
										  properties: [
											  hue:[type:"number"],
											  saturation:[type:"number"],
											  hex:[type:"string",maxLength:7],
											  level:[type:"integer"],
											  switch:[type:"string",maxLength:3]]]]],
								  type:"command",
								  capability:"colorControl",
								  label:"command: setColor(color*)"],
						 type:"hubitatTrigger"],
					 [
						 trigger:[type:"attribute",
								  properties:[value:[title:"saturation",type:"integer"],
											  unit:[type:"string",enum:["%"],default:"%"]],
								  additionalProperties:false,
								  required:["value"],
								  capability:"colorControl",
								  attribute:"saturation",
								  label:"attribute: saturation.*"],
						 command:[name:"setSaturationValue",
								  label:"command: setSaturationValue(saturation*)",
								  type:"command",
								  parameters:[[name:"saturation*",type:"NUMBER"]]],
						 type:"smartTrigger"],
					 [
						 trigger:[
							 type:"attribute",
							 properties:[value:[title:"String",type:"string",maxLength:255]],
							 additionalProperties:false,
							 required:[],
							 capability:"colorControl",
							 attribute:"color",
							 label:"attribute: color.*"],
						 command:[name:"setColorValue",
								  label:"command: setColorValue(coloe*)",
								  type:"command",
								  parameters:[[name:"color*",type:"string"]]],
						 type:"smartTrigger"]
				 ]
				]
	return new groovy.json.JsonBuilder(rules).toString()
}


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
	log.trace "${device.displayName}-${driverVer()}: ${msg}"
}

def logInfo(msg) { 
	if (textEnable) {
		log.info "${device.displayName}-${driverVer()}: ${msg}"
	}
}

def debugLogOff() {
	if (logEnable) {
		device.updateSetting("logEnable", [type:"bool", value: false])
	}
	logInfo("debugLogOff")
}

def logDebug(msg) {
	if (logEnable) {
		log.debug "${device.displayName}-${driverVer()}: ${msg}"
	}
}

def logWarn(msg) { log.warn "${device.displayName}-${driverVer()}: ${msg}" }