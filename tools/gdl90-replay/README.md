# GDL 90 replay tool

Test pilot mode without flying: sends the same bytes a Stratux sends (GDL 90 per Garmin 560-1058-00 Rev A, framed with
CRC) over UDP to a phone or the Android emulator. Python 3.8+, no dependencies.

```bash
python tools/gdl90-replay/gdl90_replay.py list
python tools/gdl90-replay/gdl90_replay.py scenario converging            # to 127.0.0.1:4000, loops
python tools/gdl90-replay/gdl90_replay.py scenario pattern --to 192.168.1.23:4000
python tools/gdl90-replay/gdl90_replay.py selftest
```

| Scenario | What happens |
| --- | --- |
| `converging` | Traffic from 2 o'clock, 500 ft above, crossing just ahead; a second aircraft at 10 o'clock; a TIS-B copy of ownship that must never be called out |
| `overtaking` | A faster aircraft overtakes from 6 o'clock, slightly below and climbing |
| `pattern` | Inbound to Palo Alto (KPAO) with three aircraft circling the field, one taxiing (never called out) and one departing |

Scenarios live in `app/android/data/src/main/resources/gdl90/scenarios.json`, the same file the apps' **Simulated receiver** uses.
A unit test on each platform checks that the apps produce exactly the bytes this tool sends.

## Android emulator

```bash
adb emu redir add udp:4000:4000
python tools/gdl90-replay/gdl90_replay.py scenario converging
```

In the app: Settings → Open pilot mode → (Pilot settings: "Listen for a receiver" on, "Simulated receiver" off).

## Real phone

Put the computer and the phone on the same Wi-Fi network and send to the phone's IP address (`--to 192.168.x.y:4000`).
On iPhone, allow "Local Network" when asked.

## Record and replay a real receiver

On a laptop joined to the receiver's Wi-Fi (Stratux sends to every Wi-Fi client):

```bash
python tools/gdl90-replay/gdl90_replay.py listen                         # decode and print what arrives
python tools/gdl90-replay/gdl90_replay.py record flight1.gdl90 --seconds 3600
python tools/gdl90-replay/gdl90_replay.py play flight1.gdl90 --to 127.0.0.1:4000
```

Recordings contain aircraft positions and IDs only. Keep large captures out of git (see `data/README.md`).
