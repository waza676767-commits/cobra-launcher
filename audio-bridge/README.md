# Audio Bridge

Sends your PC's sound to your phone over the USB cable (USB tethering), so you can listen on Bluetooth
headphones connected to the phone. It also sends the phone's mic, or the headphone mic, back to the PC
as a microphone.

## Get the apps
GitHub → this repo → **Actions** → **Audio Bridge** → latest run → **Artifacts**:
- `AudioBridge-PC` → `AudioBridge.exe` (no install, just run it; click "More info → Run anyway" if SmartScreen asks,
  and **Allow** in the Windows Firewall prompt)
- `AudioBridge-Android` → `AudioBridge.apk` (copy to phone, open it, allow "install unknown apps")

## One-time: the PC microphone
Windows only shows a new microphone if a driver adds one. Install the free **VB-CABLE** driver
(https://vb-audio.com/Cable/), then restart the PC. Audio Bridge plays your mic into **CABLE Input**, and you pick
**CABLE Output** as the microphone in Discord, games, or Windows sound settings.

## Use it
1. Connect the headphones to the phone.
2. USB cable → turn on **USB tethering** on the phone.
3. Phone: open Audio Bridge → choose the mic → **Start**.
4. PC: open AudioBridge.exe → **Start**. It finds the phone automatically.

## About delay
- The cable and the apps add roughly 15–50 ms; set it with the slider on the phone.
- Bluetooth headphones add their own delay, usually 100–250 ms. No app can remove that part.
  Headphones with a "low latency" or "game mode" setting help.
- **Headphone mic mode:** Bluetooth headphones can only use their mic in "call mode" (HFP). In call mode the sound
  becomes mono and lower quality. That is a Bluetooth limit, not an app limit. For good sound plus a mic,
  choose **Phone mic**.
