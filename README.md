# Hubitat Tailwind Garage Door 2.0

Hubitat device drivers for local Tailwind iQ3 API control, updated to use the modern `/json` API with real-time push notifications.

## What's new in 2.0

- **Real-time push notifications** — the Tailwind device pushes door state changes directly to the hub the moment they happen, no waiting for a poll cycle
- **Modern `/json` API** — all communication now uses the `/json` endpoint instead of the legacy `/status` and `/cmd` endpoints
- **Auto-registration** — on every save, the driver automatically registers the hub as the Tailwind's notification target; no manual setup required
- **Reboot command** — reboot the Tailwind controller directly from the device page
- **Polling as fallback** — polling is kept as a safety net (default every 5 minutes) in case a notification is missed
- **Namespace and authorship** updated to `drbbton`

## Requirements

- Tailwind iQ3 controller
- Firmware 10.10 or later
- Hubitat hub with a static IP (or DHCP reservation) — the Tailwind needs a stable address to push notifications to

## Installation

1. Create a new driver on your Hubitat hub using:
   ```
   https://raw.githubusercontent.com/drbbton/HubitatTailwind/main/tailwinddriver.groovy
   ```
2. Create a new driver for the child device using:
   ```
   https://raw.githubusercontent.com/drbbton/HubitatTailwind/main/tailwinddriver-child.groovy
   ```
3. Create a virtual device on Hubitat with **Hubitat Tailwind Garage Door 2.0** as the device type
4. Configure the device settings:
   - **Tailwind Controller IP** — local IP of your Tailwind controller
   - **Tailwind Controller Name** — used as a prefix for child device names
   - **Number of Doors** — 1 to 3
   - **Local Command Key** — log in at https://web.gotailwind.com/, go to Local Control Key, and generate a key. This is per-account and applies to all your Tailwind devices.
   - **Door Names** — optional labels for each door shown in dashboards
5. Save preferences. The driver will automatically register the hub as the push notification target and run an initial poll.
6. Child devices named `ControllerName : DoorName` will appear and can be used in dashboards, Rules, or webCoRE.

## How notifications work

When a door opens or closes, the Tailwind pushes a JSON payload to the hub on port 39501. The driver's `parse()` method receives it and immediately updates the child device states — no polling round-trip needed. The driver re-registers the notification URL every time settings are saved, so it stays current if your hub IP changes.

Polling runs in the background as a fallback. The default interval is 5 minutes; you can lower it if you want more frequent catch-up checks.

## Notes

- HTTPS is not supported by the Tailwind notification system — communication is HTTP on your local network only
- The notification URL is limited to 200 bytes; a standard local hub IP is well within this limit
- The parent device's network ID is set to the hex-encoded Tailwind IP so the hub can route inbound notifications to the correct driver. Child device IDs are unaffected.
- If you have duplicate drivers from a version prior to 1.0.3, delete the unused ones and use HPM to "Match up" with the correct driver.
