# Changelog

## 1.4.0

- A home screen widget: the clock and date, your next alarm and the alert status. Tap the status to check right away; tap anywhere else to open the app. It refreshes on its own while you use the phone and does nothing while the screen is off. Add it from **More → Widget** or from the home screen's widget list. It follows the app's theme.
- The main screen shows the current time and date at the top.
- When a new version is out, the app asks right away when you open it, instead of leaving a card on the main screen. **Later** hides it for a few hours; the update stays in **More**, and a dot on the More tab reminds you.
- Removed the line about where updates come from.

## 1.3.0

- A sound for each alarm: a phone sound, your own audio file, or vibration only. You can preview it in the alarm editor.
- Gentle wake-up (the volume rises over 30 seconds) or full volume right away, plus a vibration switch, set per alarm.
- Weather for your oblast's main city and a short "what to wear" tip, on the main screen and on the ringing screen.
- Four themes: Night, Graphite, Ocean and Light.
- The interface follows the phone's language: Ukrainian, Russian or English.
- An **Uninstall app** button in More.
- Rewrote all the interface text to be shorter and clearer.
- The add-alarm button moved next to the Alarms heading, so it no longer covers a switch.
- Updates are now published as versioned releases, and only after the emulator check passes.

## 1.2.0

- Renamed the app to bylarik.
- Checks for updates hourly when opened and in the morning after an alarm, and shows a notification when a new version is out.

## 1.1.x

- Moved to this repository. The app updates itself from its releases.
- Added an emulator check for every build, including a test that a previous version updates itself to the new one.

## 1.0.x

- A backup alert source (vadimklimenko.com), used when siren.pp.ua doesn't answer.
- Rings right after the all-clear by default (it used to wait 10 minutes). While waiting, it checks every 20 seconds.
- A battery saver option: check once a minute while waiting.
- First version: per-alarm rules for alerts, oblast/district/community picker, waiting for the all-clear in the background, and Telegram news with an alert filter.
