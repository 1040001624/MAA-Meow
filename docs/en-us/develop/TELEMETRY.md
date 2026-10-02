# Telemetry

MaaMeow uses [Sentry](https://sentry.io) to report crashes and task results anonymously, so we can see which tasks fail and at which step.

The setting is **Settings → Third-party services → Help improve this project**. It is on by default. When it is off, Sentry is never initialized and no request is sent.

## What is sent

| Kind | When | Content |
|---|---|---|
| Run statistics | Every run | Name, duration and result of each task chain, plus the parameter summary described below |
| Task failure | A task chain fails | The failing subtask and the recognition node it was stuck on; the logs of that task; in background mode, also the game screenshot MaaCore saved on failure |
| Start failure | Resource loading, instance creation, virtual display or connection fails | The failing stage; the logs of that run |
| Service death | The Shizuku / Root process exits unexpectedly during a run | The task chain that was running; the logs of that run and MaaCore's `crash.log` |
| App crash | Uncaught Java exception | Stack trace |
| Activity | App goes to foreground or background | Session start and end, used for daily active users and crash rate |

"Logs" means the MaaCore log (`asst.log`), the run log of that run and the app error log, plus the service connection diagnostics for start failures and service deaths. Only the part written around the incident is taken, with a little preceding context, 1 MiB in total at most.

Every record carries:

- An anonymous device ID: a salted SHA-256 of `ANDROID_ID`, which cannot be reversed. The same value appears as `Telemetry ID` in the `device_info.txt` of an exported log package; include it in bug reports so the matching records can be found
- App version, MaaCore version, resource version and build type
- Client type, run mode, elevation backend and data location
- Device model, OS version, SoC, total memory and ABI
- The device and OS information the Sentry SDK adds on its own: battery level, free memory and storage, screen size, language and time zone, connection type, whether the device is rooted, and similar

## What is not sent

- Logs and screenshots are not touched unless something failed
- No screenshot in foreground mode: it would capture the phone's main screen, which may show other apps
- Task parameters are summarized: booleans and numbers are sent as-is, enum-like values such as stage, theme and client are sent as-is, any other string (account, reporting ID, file path) is reported only as filled or empty, and lists only by length
- Logs are redacted before they leave the device: the values of `account_name`, `penguin_id` and `yituliu_id`, and the stored Penguin Statistics ID, Yituliu token, MirrorChyan CDK and notification channel secrets are replaced with `***`
- No ANR, view hierarchy, tap or navigation trail, network request trail, or system event trail (screen on/off, battery, connectivity changes) is collected
- No IP address: default PII is disabled in the SDK and IP storage is disabled in the Sentry project

Two things redaction cannot remove:

- MaaCore logs contain recognition results, which may include in-game friend names
- The failure screenshot is the game screen on the virtual display at that moment, which may show your Doctor name and level

Turn off "Help improve this project" if you are not comfortable with that.
