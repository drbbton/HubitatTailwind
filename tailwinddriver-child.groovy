metadata {
    definition (
        name: "Hubitat Tailwind Garage Door 2.0 - Child",
		namespace: "drbbton",
		author: "drbbton",
        importUrl: "https://raw.githubusercontent.com/drbbton/HubitatTailwind/main/tailwinddriver-child.groovy"
    ) {
        capability "GarageDoorControl"
        capability "Actuator"
        capability "ContactSensor"
        capability "Sensor"
        command "open"
        command "close"
    }
}

void close(){
    log.debug "Child says Door #${device.deviceNetworkId[-1].toInteger()} to Close"
    parent.close(device.deviceNetworkId[-1].toInteger())
}

void open(){
    log.debug "Child says Door #${device.deviceNetworkId[-1].toInteger()} to Open"
    parent.open(device.deviceNetworkId[-1].toInteger())
}
