library (
	name: "Logging",
	namespace: "davegut",
	author: "Dave Gutheinz",
	description: "Common Logging and info gathering Methods",
	category: "utilities",
	documentationLink: ""
)

def label() {
	if (device) { return device.displayName } 
	else { return app.getLabel() }
}

def listAttributes() {
	def attrData = device.getCurrentStates()
	Map attrs = [:]
	attrData.each {
		attrs << ["${it.name}": it.value]
	}
	return attrs
}

def setLogsOff() {
	def logData = [logEnable: logEnable]
	if (logEnable) {
		runIn(1800, debugLogOff)
		logData << [debugLogOff: "scheduled"]
	}
	return logData
}

def logTrace(msg){ log.trace "${label()} ${getVer()}: ${msg}" }

def logInfo(msg) { 
	if (infoLog) { log.info "${label()} ${getVer()}: ${msg}" }
}

def debugLogOff() {
	if (device) {
		device.updateSetting("logEnable", [type:"bool", value: false])
	} else {
		app.updateSetting("logEnable", false)
	}
	logInfo("debugLogOff")
}

def logDebug(msg) {
	if (logEnable) { log.debug "${label()} ${getVer()}: ${msg}" }
}

def logWarn(msg) { log.warn "${label()} ${getVer()}: ${msg}" }

def logError(msg) { log.error "${label()} ${getVer()}}: ${msg}" }
