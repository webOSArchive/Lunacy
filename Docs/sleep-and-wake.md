# Sleep and wake: the TouchPad's on Android

How webOS slept and woke, what of that its apps and services depend on, what Android does
instead, and how Lunacy maps one onto the other. Written 2026-10-08, after Email stopped
fetching mail overnight on the Pixel Tablet. Everything marked *measured* was measured on the
reference TouchPad (webOS CE 3.1.0) or on the Galaxy Tab A7 Lite (Android 14), and says which.

## Why it matters

A great deal of webOS runs on a schedule, not on a person:

- **Synergy.** Every sync connector is built on `mojoservice.transport.sync` (on the device
  at `/usr/palm/frameworks/mojoservice.transport.sync`). For each account it creates a
  periodic-sync activity with the interval the connector asks for (24 h if it gives none) and
  a requirement of `internetConfidence: fair`, a db8-triggered "SyncOnEdit" activity for
  local changes, and on a network error restarts the activity to retry after `5m`
  (`synccommand.js`, `enabledaccountcommand.js`).
- **Email.** mojomail schedules its folder syncs, retries (5 min, growing by half each time:
  300, 450, 675 s were seen), IMAP IDLE renewals ("IMAP idle wakeup", about every 29 min)
  and network checks as activities.
- **Alarms.** Clock on the TouchPad sets each alarm as a persisted activity with a
  `schedule.start` whose callback relaunches Clock with `{"action":"ring"}`. Its older
  scheduler, and Mojo apps generally (SimpleChat's half-hourly refresh), use
  `com.palm.power/timeout/set`.
- **Calendar reminders**, the configurator's activity files, and anything an app registers.

All of it was written for a device that slept whenever its screen was off and woke for its
timers. If Lunacy's timers stop when Android sleeps, all of it stops with them.

## How the TouchPad slept

- **powerd suspended the device** when the screen was off and nothing held it awake. Clients
  vote on each suspend (`prepareSuspend`, `suspendRequest`, ACK or NACK); an active power
  activity vetoes it (*measured*, log: "IdleCheck: can't sleep because an activity is active").
  Plugged into USB, the reference TouchPad did not suspend at all, even on `forceSuspend`
  (*measured*).
- **It woke for its timers.** `com.palm.power/timeout/set` with `"wakeup":true` is an RTC
  alarm. The activity manager keeps exactly one, key `com.palm.activitymanager.wakeup`, for
  its earliest schedule, and powerd re-arms it as each fires (*measured*, log:
  `_power_timeout_set (,com.palm.activitymanager.wakeup,wakeup) at …`), as Open webOS's
  `PowerdScheduler` describes.
- **It stayed awake briefly after a timer**: 5 s, as a power activity
  `com.palm.power.timeout_fired` (*measured*), long enough for the callee to take its own.
- **Power activities kept it awake.** An activity of type `power` takes powerd's lock while
  it runs: 900 s at most, not renewed; ended, it is released, or 12 s later for
  `powerDebounce` (Open webOS `PowerdPowerActivity`).
- **Wi-Fi stayed up through a suspend**, and woke the device for traffic: the connection
  manager answers `isWakeOnWifiEnabled: true` (*measured*). Connections survived, which is why
  IMAP IDLE could push.
- **Signals** (*measured*, unplugged, 2026-10-08). Subscribed with
  `palm://com.palm.bus/signal/addmatch` on category `/com/palm/power`, each suspend sends
  `suspendRequest`, `prepareSuspend` and `suspended`, and each wake `resume`, every payload
  `{}`. A resume comes on a kernel wake (a timer, or something else: one came 65 s into a
  294 s sleep with no timer due) and on an aborted suspend. powerd logs each suspend with its
  next timer: "StateSleep: waking in 294 seconds for com.palm.activitymanager.wakeup". Plugged
  into USB it never suspends, even on `forceSuspend`.
- **Timeouts survived a restart**: powerd keeps them in sqlite (its strings:
  `kSysTimeoutDatabase…`).

### The timeout contract (measured on the TouchPad)

| Call | Answer |
|---|---|
| `timeout/set` with `key`, `uri`, `params` and `in` (`"HH:MM:SS"`, under 24 h) or `at` (`"MM/DD/YYYY HH:MM:SS"`, UTC) | `{"returnValue":true,"key":k}` |
| any of those missing, another time format (`"10:00"`, `"2026-10-09 10:00:00"`, `"30:00:00"`) or an unknown parameter (`activity_duration_ms` on its own) | `{"returnValue":false,"errorText":"Invalid format for 'timeout/set'."}` (no errorCode) |
| `keep_existing:true` with a timeout already under that key | `{"returnValue":true,"key":k,"kept_existing":true}`, the old one kept |
| an `at` already past, or `in` `"00:00:00"` | fires at once |
| `"params":"str"` (not an object) | accepted |
| `timeout/clear` with a key, set or not | `{"returnValue":true,"key":k}` |
| `timeout/clear` with no key | `{"returnValue":false,"errorText":"Invalid parameters."}` |

Keys belong to the caller: powerd logged each as (app id, key).

## How Android sleeps

| What | What it does to an app | What the app is told |
|---|---|---|
| **CPU suspend** (screen off, nothing holding it) | Nothing runs; `SystemClock.uptimeMillis()` stops, `elapsedRealtime()` doesn't. A `Handler` delay counts only time awake | Nothing. The gap between the two clocks shows afterwards |
| **Doze** (unplugged, screen off, still; deep and light) | Network cut off; wake locks ignored; alarms deferred to maintenance windows, except `setExactAndAllowWhileIdle`, rate-limited. After such an alarm the app gets about 10 s of network (*measured*, A7 Lite) | `PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED` (API 23); the default network's `onBlockedStatusChanged` (API 29). Existing sockets are cut: mojomail's IDLE connection hung up the moment Doze began (*measured*) |
| **App standby buckets** | Alarms and jobs rationed by how recently the app was used | Nothing directly |
| **Background network blocks** outside Doze | Seen on Android 14: the network blocked 2 min after the screen went off, Lunacy in the ACTIVE bucket (*measured*, A7 Lite; cause not identified) | `onBlockedStatusChanged`, as for Doze |
| **Cached-app freezer** (Android 11+, on by default from 14) | The whole process stopped while it is in the background | Nothing. A timer that comes very late shows it afterwards |
| **Process death** | Everything gone; Android may kill child processes (phantom processes) first | Nothing, until the next start |

The Pixel Tablet, unplugged at 21:40 on 2026-10-07, was in deep Doze (`device_idle=full`) by
22:20 (*measured*, battery history). Lunacy's activity timers were then `Handler` delays and
its connection manager reported Wi-Fi up throughout: syncs ran, failed and backed off, and
the next one that succeeded was the 07:30 retry.

## The mapping

Lunacy treats **every sign of having been away as a webOS resume**, and answers each the same
way: everything that has fallen due runs, late but not lost, and the network is reported as
it really is, so that services reconnect as they did after a TouchPad suspend.

| webOS | Lunacy |
|---|---|
| powerd's RTC timeouts | [`Power`](../AndroidLuna/src/main/java/org/webosarchive/lunacy/card/Power.kt): one exact `RTC_WAKEUP` alarm, allowed while idle, for the earliest waking time of any client; one `RTC` alarm for the earliest non-waking one |
| `com.palm.activitymanager.wakeup` | The activity manager's schedules, on the wall clock, through `Power` under that key |
| `com.palm.power/timeout/set`, `clear` | [`PowerService`](../AndroidLuna/src/main/java/org/webosarchive/lunacy/card/PowerService.kt), to the contract above; kept in SharedPreferences, so they outlive Lunacy's process; held until the webOS root's services are up, then any missed one fires |
| `com.palm.power/com/palm/power/activityStart`, `activityEnd` (an app's own hold: `id`, `duration_ms`; malformed calls answer `"Malformed json."`, measured) | `PowerService`, a named wake-lock hold of that length (`Power.lockFor`) |
| 5 s awake after a timer | `Power.hold(GRACE_MS)` |
| Power activities (900 s, 12 s debounce) | A partial wake lock, same lengths |
| Wi-Fi up through a suspend | **A delta** ([luna-deltas A18](luna-deltas.md#a18-the-network-goes-down-in-doze)): while Android blocks Lunacy's network, the connection manager reports it down, and up when it is back. The down and up is what mojomail, Synergy's requirements and every activity manager requirement already act on |
| powerd's resume | [`SleepWake`](../AndroidLuna/src/main/java/org/webosarchive/lunacy/card/SleepWake.kt): a resume on any of Doze ending, the network unblocked, CPU time lost (the two clocks' gap, over 30 s), a heartbeat over 30 s late (frozen), and the shell coming to the front. A resume runs `Power.due()`: every client runs what has fallen due |
| `suspended`/`resume` signals | Not sent yet. Measured and cheap to add (`Bus.signal`), but no app or service Lunacy runs has been seen listening for them |
| (no webOS equivalent) A retry a service set because Android's block cut its connection | Due as the block ends: a one-shot schedule with a network requirement, set during the block or in the 10 s before Android reported it ([luna-deltas A18](luna-deltas.md#a18-the-network-goes-down-in-doze)) |

Two details that matter:

- **Late, once, by the activity manager's own rules.** A missed interval runs once as soon as
  it can (or is skipped, with `skip:true`), as `Schedule.queue` already did; a missed timeout
  fires once. Nothing is replayed per missed tick.
- **Alarms come early when Android batches them.** A non-waking alarm went off 1.6 s early
  beside a waking one in Doze (*measured*, A7 Lite); a sweep that then finds nothing due would
  re-arm and wait a minute or more. So a time within 5 s keeps the CPU up and is swept again
  exactly then (`Power.soon`); nothing fires early, because an interval schedule fired early
  would land on the same slot and run twice.

## Measured on the Galaxy Tab A7 Lite, 2026-10-08

Forced Doze with `adb shell dumpsys battery unplug`, screen off,
`dumpsys deviceidle force-idle`; out again with `unforce` and `dumpsys battery reset`.

- Doze on: the connection manager answered `isInternetConnectionAvailable:false`, Wi-Fi
  `disconnected`. mojomail's IDLE socket hung up, it tried once to reconnect, failed, and
  scheduled "IMAP sync retry" for 5 minutes on with an internet requirement. Waiting blocked,
  it no longer fails and backs off.
- Doze off before the retry was due: the retry ran on time with `retry.interval` still 300 (no
  escalation), and IDLE was re-established.
- A waking activity schedule and a waking timeout due during Doze both ran on time
  (09:14:55); Android unblocked the network for about 10 s around them, and the connection
  manager said so.
- A non-waking timeout due during Doze fired 63 s late, on the resume when Doze ended.
- The process stopped with `kill -STOP` for 75 s: "resume (53 s frozen)" at the next heartbeat.
- A timeout set for 30 s on, with Lunacy force-stopped past it: on restart it fired once the
  services were up.
- **Not Doze, and still blocked.** Screen off on battery (simulated with `dumpsys battery
  unplug`), no forced idle: Android 14 blocked Lunacy's network 2 minutes after the screen went
  off and unblocked it as the screen came on, with Lunacy in the ACTIVE standby bucket; the
  cause isn't identified. The block cut mojomail's IDLE connection, the connection manager
  reported it, and mojomail's retry ran 5 minutes after the drop (09:27:44 to 09:32:45), as
  its own policy sets: a retry isn't brought forward when the network comes back. mojomail's
  source has the same TODO ("if we can tell that the interface went down, we can just
  reschedule the idle activity"), so a TouchPad that lost its connection waited the same 5
  minutes. After a long sleep the retry is overdue and runs at once.
- `activityStart` with `{"id","duration_ms":10000}` held the `lunacy:power` wake lock for
  10.003 s (`dumpsys power`); the malformed calls answered as the TouchPad does.

## Email and Synergy, case by case (Galaxy Tab A7 Lite, 2026-10-08)

Push was timed end to end: the test account sends to itself through Lunacy's own Email
(compose opened by launch parameters, then its send handler over DevTools), and the clock runs
until the receiving IMAP session reports `EXISTS`. Synergy was tested with webCal Sync 0.2.2
(Palm's sync framework, `mojoservice.transport.sync`), syncing a public `.ics` calendar every
15 minutes.

| Case | What happens | Measured |
|---|---|---|
| Email, Doze long enough for mojomail's retry to come due | The retry's alarm wakes the device inside Doze; in Android's ~10 s window mojomail reconnects and is idling again within a second; the block that follows doesn't close the socket, and it carries push after the device wakes | probe arrived 3.5 s after sending |
| Email, short Doze (woken before the retry) | The retry, set during the block, is due when the block ends (the A18 rule); IDLE is back within 3 s of waking | probe 3 s; before the rule, 3 min 18 s |
| Email, the IDLE-setup activity waiting on its requirement | It runs the moment the network is back | IDLE 3 s after waking |
| Email, Lunacy relaunched | The persisted IDLE-setup activity runs as the services come up | IDLE 12 s after launch, probe 3 s |
| Synergy, periodic sync due during Doze | Its alarm wakes the device on time; the sync fits in the ~10 s window, and re-arms with `complete(restart)` | 11:12:40 due, done 11:12:45 |
| Synergy, woken between ticks | Nothing due, nothing runs | |
| Synergy and Email, Lunacy stopped across a tick and relaunched | The persisted periodic sync, missed, runs once as the services come up; IDLE resumes | sync and IDLE 11 s after launch |

Three Lunacy bugs this turned up, all fixed the same day (fix-log): restored activities never
started when the webOS root came up before db8 answered (push never came back after a
relaunch); the webOS root had no `mojoservice.transport.sync` or `foundations.xml` (no Synergy
connector could run); and the JS service host never said a call came from the activity
manager, so Synergy services monitored their activities instead of adopting them, couldn't
complete them, and webCal's initial sync ran 1,207 times in 17 minutes.

## What this does not do

These are open, and each is a decision for codepoet (rule 0: they show on Android's side of
the screen, not Lunacy's).

- **Push mail while dozing.** Doze cuts the network; only a maintenance window or an
  allowed-while-idle alarm's few seconds get through. A foreground service (a permanent
  notification) keeps network access in Doze; the battery-optimisation exemption (a one-time
  system dialog) also lets alarms and wake locks through.
- **An alarm that must ring on the minute** on an unplugged, idle device. `setAlarmClock`
  isn't deferred by Doze and needs no permission, but Android shows its alarm icon while one
  is set.
- **Coming back after the process dies.** Persisted activities and timeouts come back when
  Lunacy next starts; non-persisted ones are gone, as on a webOS reboot. Nothing restarts
  Lunacy on its own: that needs the runtime (bus, services, activity manager) out of the
  shell's activity, a manifest receiver for the wakeup alarm, and one for boot.
- **Exact alarms (ratchet item).** Apps targeting API 31 and later need `SCHEDULE_EXACT_ALARM`
  or `USE_EXACT_ALARM`; Lunacy targets 24 and 28.

## Testing it

- Doze: `adb shell dumpsys battery unplug`, `adb shell input keyevent 223`,
  `adb shell dumpsys deviceidle force-idle`; leave with `dumpsys deviceidle unforce`,
  `dumpsys battery reset`. Nothing is changed for good.
- Frozen: on a debug build, `adb shell run-as org.webosarchive.lunacy kill -STOP <pid>`, then
  `-CONT`.
- Timeouts and activities: `luna-send` over the SDK's novacom relay
  (`novacom -d lunacy run file:///usr/bin/luna-send -- -n 1 …`); arguments with spaces need
  `novaterm -d lunacy`, since `novacom run` splits them.
- What to read: `adb logcat -s Lunacy:*`, lines starting `sleep:` and `power:`;
  `adb shell dumpsys alarm | grep -A3 POWER_` for the armed alarms and their history.
