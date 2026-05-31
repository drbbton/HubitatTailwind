preferences {
    input name: "IP", type: "string", title: "Tailwind Controller IP", required: "True"
    input name: "cName", type: "string", title: "Tailwind Controller Name", required: "True",  description: '<em>Changes the name for the controller displayed in dashboards, DOES affect children unique deviceNetworkId.  Changing this will re-create the children devices.</em>'
    input name: "token", type: "password", title: "Local Command Key", required: "True", description: 'login with your tailwind app credentials to https://web.gotailwind.com/, go to Local Control Key, create a new local command key.  This is per-account and is the same for each device you may have on your account.'
    input name: "doorCount", type: "number", title: "Number of Doors", required: "True", range: "0..3", defaultValue : 1
    input name: "interval", type: "enum", title: "Polling interval", required: "True", options: ["1", "5", "10", "15", "30"], defaultValue : 5, description: '<em> Fallback polling interval in Minutes. Notifications handle real-time updates; polling is a safety net.</em>'
    input name: "fastPollInterval", type: "number", title: "Fast Polling interval", required: "True", range: "1-30", defaultValue : 2, description: '<em>This polling interval is used when an action has been performed (such as open/close) and will update the status of the door triggered.</em>'
    input name: "garageDoorTimeout", type: "number", title: "Door Open/Close timeout", required: "True", defaultValue : 60, description: '<em> Seconds. How long should faster polling be run before giving up on waiting to check and see if the door status has changed after issuing a command.</em>'
    input name: "debugEnable", type: "bool", title: "Enable debug logging?", defaultValue: true,  description: '<em>for 2 hours</em>'
    if(doorCount > 0){input name: "d1Name", type: "string", title: "Door 1 Name", required: "false", defaultValue : "Door 1",  description: '<em>Changes the name for Door 1 displayed in dashboards, does not affect children unique deviceNetworkId.  Changing this will have no effect on the children devices being re-created.</em>'}
    if(doorCount > 1){input name: "d2Name", type: "string", title: "Door 2 Name", required: "false", defaultValue : "Door 2",  description: '<em>Changes the name for Door 2 displayed in dashboards, does not affect children unique deviceNetworkId.  Changing this will have no effect on the children devices being re-created.</em>'}
    if(doorCount == 3){input name: "d3Name", type: "string", title: "Door 3 Name", required: "false", defaultValue : "Door 3",  description: '<em>Changes the name for Door 3 displayed in dashboards, does not affect children unique deviceNetworkId.  Changing this will have no effect on the children devices being re-created.</em>'}
}

metadata {
    definition (
        name: "Hubitat Tailwind Garage Door 2.0",
		namespace: "drbbton",
		author: "drbbton",
        importUrl: "https://raw.githubusercontent.com/drbbton/HubitatTailwind/main/tailwinddriver.groovy"
    ) {
        capability "Polling"
        attribute "ledBrightness", "number"
        command "childOpen", ["integer"]
        command "childClose", ["integer"]
        command "reboot"
        command "setLedBrightness", [[name: "brightness", type: "NUMBER", range: "0..100", description: "LED brightness (0-100)"]]
    }
}



def installed() {
}

def uninstalled() {
    getChildDevices().each { deleteChildDevice("${it.deviceNetworkId}") }
}

def updated() {
    log.info "Clearing schedule for Polling interval"
    unschedule()
    if (debugEnable) runIn(7200, disableDebug)
    init()
}

def init() {
    log.info "Scheduling Polling interval for ${settings.interval} minute(s)..."
    addChildren()
    // Set DNI to hex-encoded IP so the hub routes inbound LAN notifications to this driver
    def hexIP = getHexIP(IP)
    if (device.deviceNetworkId != hexIP) {
        if(debugEnable) log.debug "Updating DNI to ${hexIP}"
        device.deviceNetworkId = hexIP
    }

    registerNotifyUrl()

    if (settings.interval == "1") runEvery1Minute(poll)
    else if (settings.interval == "5") runEvery5Minutes(poll)
    else if (settings.interval == "10") runEvery10Minutes(poll)
    else if (settings.interval == "15") runEvery15Minutes(poll)
    else if (settings.interval == "30") runEvery30Minutes(poll)
    poll()
}

def disableDebug(String level) {
    log.info "Timed elapsed, disabling debug logging"
    device.updateSetting("debugEnable", [value: 'false', type: 'bool'])
}

// Receives push notifications from the Tailwind device
def parse(String description) {
    def msg = parseLanMessage(description)
    if (!msg?.body) return
    def body = parseJson(msg.body)
    if(debugEnable) log.debug "Notification received: ${body}"
    if (!body?.notify) return
    def data = body?.data
    if (!data) return
    def statuses = []
    int dc = doorCount.toString().toInteger()
    for (int i = 0; i < dc; i++) {
        def s = data?."door${i+1}"?.status ?: "unknown"
        statuses << (s == "close" ? "closed" : s)
    }
    if(debugEnable) log.debug "Notification: statuses = ${statuses}"
    setDoorStatus(statuses)
}

void addChildren(){
    int dc = doorCount.toString().toInteger()
    getChildDevices().each {
        spl = it.deviceNetworkId.split(':')
        if(spl[0] != cName  || spl[1].toInteger() > dc){
            if(debugEnable) log.debug  "delete ${it.deviceNetworkId}"
            deleteChildDevice("${it.deviceNetworkId}")
        }
    }
    for (int c = 0; c < dc; c++) {
        def d = c + 1
        def dn=""
        if (d == 1){dn = d1Name}
        if (d == 2){dn = d2Name}
        if (d == 3){dn = d3Name}
        if(debugEnable) log.debug ("${cName}:${d}")
        def cd = getChildDevice("${cName}:${d}")
        if(cd && cd.typeName != "Hubitat Tailwind Garage Door 2.0 - Child") {
            if(debugEnable) log.debug "Migrating child ${cd.deviceNetworkId} from '${cd.typeName}' to new driver"
            deleteChildDevice("${cd.deviceNetworkId}")
            cd = null
        }
        if(!cd) {
            cd = addChildDevice("drbbton","Hubitat Tailwind Garage Door 2.0 - Child","${cName}:${d}", [label: "${cName} : ${dn}", name: "${d}", isComponent: true])
            if(cd && debugEnable){
                if(debugEnable) log.debug "Child device ${cd.displayName} was created"
            }else if (!cd){
                log.error "Could not create child device"
            }
        }
        if(debugEnable) log.debug "deviceNetworkId ${cd.deviceNetworkId}=${cName}:${d} name ${cd.name}=${d} label ${cd.label}=${cName} : ${dn}"
        if(cd.label != "${cName} : ${dn}"){
            if(debugEnable) log.debug "Correcting child label mismatch ${cd.label}=${cName} : ${dn}"
            cd.label = "${cName} : ${dn}"
        }
        if(cd.name != "${d}"){
            if(debugEnable) log.debug "Correcting child name mismatch ${cd.name}=${d}"
            cd.name = "${d}"
        }
    }
}

def poll() {
    def statuses = checkStatus()
    if (statuses == null) return
    if(debugEnable) log.debug "Poll: statuses = ${statuses}"
    setDoorStatus(statuses)
}

def openClose(String command, Integer doorNumber){
    log.info "Attempting to ${command} door ${doorNumber}"
    def postParams = [
        uri: "http://${IP}/json",
        headers: ['TOKEN': "${token}"],
        body: [
            product: "iQ3",
            version: "0.1",
            data: [
                type: "set",
                name: "door_op",
                value: [
                    door_idx: doorNumber - 1,
                    cmd: command
                ]
            ]
        ]
    ]
    if(debugEnable) log.debug postParams
    httpPostJson(postParams) { resp ->
        if(debugEnable) log.debug "${command} response: ${resp.data}"
        if (resp.data?.result == "OK") {
            runIn(1, "postActionRefresh", [data: ["desiredStatus": command, "doorNumber": doorNumber]])
        } else {
            log.warn "Command '${command}' on door ${doorNumber} did not return OK: ${resp.data}"
        }
    }
}

void postActionRefresh(data){
    def Integer loopSpeed = fastPollInterval
    String desiredStatus = data.get("desiredStatus")
    Integer doorNumber = data.get("doorNumber").toInteger()
    if(debugEnable) log.debug "Now polling every ${loopSpeed} seconds for door ${doorNumber} to ${desiredStatus}."
    def Integer i = 0
    def List statuses = null

    while (true) {
        // Check child state first — notification may have already updated it
        def currentStatus = getChildDevice("${cName}:${doorNumber}")?.latestValue("door")
        if(debugEnable) log.debug "Door #${doorNumber} Desired: ${desiredStatus} Current: ${currentStatus}"
        if (currentStatus == desiredStatus) {
            log.info "Door #${doorNumber} successfully ${desiredStatus} (via notification)"
            break
        }
        // Notification hasn't arrived yet — poll the device directly
        statuses = checkStatus()
        currentStatus = (statuses != null && doorNumber <= statuses.size()) ? statuses[doorNumber - 1] : null
        if(debugEnable) log.debug "Door #${doorNumber} Desired: ${desiredStatus} Polled: ${currentStatus}"
        if (currentStatus == desiredStatus) {
            setDoorStatus(statuses)
            log.info "Door #${doorNumber} successfully ${desiredStatus} (via poll)"
            break
        }
        pauseExecution(loopSpeed * 1000)
        i += loopSpeed
        if (i >= garageDoorTimeout) {
            log.warn "${garageDoorTimeout} seconds elapsed waiting for door ${doorNumber} to ${desiredStatus}, may be stuck."
            if (statuses != null) setDoorStatus(statuses)
            break
        }
    }
}

def childOpen(Integer doorNumber){ open(doorNumber) }
def childClose(Integer doorNumber){ close(doorNumber) }
def open(Integer doorNumber) { openClose("open", doorNumber) }
def close(Integer doorNumber) { openClose("close", doorNumber) }

def checkStatus() {
    def params = [
        uri: "http://${IP}/json",
        headers: ['TOKEN': "${token}"],
        body: [version: "0.1", data: [type: "get", name: "dev_st"]]
    ]
    def doorStatuses = []
    httpPostJson(params) { resp ->
        if(debugEnable) log.debug "Status response: ${resp.data}"
        def respData = resp.data?.data
        int dc = doorCount.toString().toInteger()
        for (int i = 0; i < dc; i++) {
            def s = respData?."door${i+1}"?.status ?: "unknown"
            // API returns "close", normalize to "closed" to match Hubitat conventions
            doorStatuses << (s == "close" ? "closed" : s)
        }
        def ledVal = respData?.led_brightness
        if (ledVal != null) sendEvent(name: "ledBrightness", value: ledVal)
    }
    return doorStatuses ?: null
}

void setDoorStatus(List statuses){
    for (int i = 0; i < statuses.size(); i++) {
        def doorNum = i + 1
        if(debugEnable) log.debug "Door ${doorNum} is ${statuses[i]}"
        setChildStatus(doorNum, statuses[i])
    }
}

void setChildStatus(dNum, status){
    def cd = getChildDevice("${cName}:${dNum}")
    if(cd.latestValue("door") == status){
        if(debugEnable) log.debug "Child device ${cName}:${dNum} matches real door"
    } else {
        if(debugEnable) log.debug "Child device ${cName}:${dNum} DOESN'T match real door, updating to ${status}"
        cd.sendEvent(name:"door", value:"${status}")
    }
    if (status ==~ /open|closed/ && cd.latestValue('contact') != status) {
        cd.sendEvent(name:'contact', value:"${status}")
    }
}

def reboot() {
    log.info "Sending reboot command to Tailwind device"
    def postParams = [
        uri: "http://${IP}/json",
        headers: ['TOKEN': "${token}"],
        body: [
            product: "iQ3",
            version: "0.1",
            data: [
                type: "set",
                name: "reboot"
            ]
        ]
    ]
    httpPostJson(postParams) { resp ->
        if(debugEnable) log.debug "Reboot response: ${resp.data}"
        if (resp.data?.result == "OK") {
            log.info "Tailwind reboot initiated — device will be back in ~2 seconds"
        } else {
            log.warn "Reboot command failed: ${resp.data}"
        }
    }
}

def setLedBrightness(Number brightness) {
    def brightnessInt = brightness.toInteger().clamp(0, 100)
    log.info "Setting LED brightness to ${brightnessInt}"
    def postParams = [
        uri: "http://${IP}/json",
        headers: ['TOKEN': "${token}"],
        body: [
            product: "iQ3",
            version: "0.1",
            data: [
                type: "set",
                name: "led_brightness",
                value: brightnessInt
            ]
        ]
    ]
    httpPostJson(postParams) { resp ->
        if(debugEnable) log.debug "Set LED brightness response: ${resp.data}"
        if (resp.data?.result == "OK") {
            sendEvent(name: "ledBrightness", value: brightnessInt)
            log.info "LED brightness set to ${brightnessInt}"
        } else {
            log.warn "Failed to set LED brightness: ${resp.data}"
        }
    }
}

private void registerNotifyUrl() {
    def hubIP = location.hub.localIP
    def notifyUrl = "http://${hubIP}:39501/"
    log.info "Registering Tailwind notify URL: ${notifyUrl}"
    def postParams = [
        uri: "http://${IP}/json",
        headers: ['TOKEN': "${token}"],
        body: [
            product: "iQ3",
            version: "0.1",
            data: [
                type: "set",
                name: "notify_url",
                value: [
                    url: notifyUrl,
                    proto: "http",
                    enable: 1
                ]
            ]
        ]
    ]
    httpPostJson(postParams) { resp ->
        if (resp.data?.result == "OK") {
            log.info "Notify URL registered successfully"
        } else {
            log.warn "Failed to register notify URL: ${resp.data}"
        }
    }
}

private String getHexIP(String ip) {
    return ip.tokenize('.').collect { String.format('%02X', it.toInteger()) }.join()
}
