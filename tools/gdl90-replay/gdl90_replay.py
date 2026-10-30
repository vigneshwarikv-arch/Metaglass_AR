#!/usr/bin/env python3
"""GDL 90 replay tool: test pilot mode without flying. Python 3.8+, no dependencies.

Sends the same bytes a Stratux sends (heartbeat, ForeFlight ID, ownship, ownship geometric altitude,
traffic reports; framed with CRC per the Garmin GDL 90 spec 560-1058-00 Rev A) over UDP to a phone
or the Android emulator.

  python gdl90_replay.py list                                  # show scenarios
  python gdl90_replay.py scenario converging                   # to 127.0.0.1:4000, loops
  python gdl90_replay.py scenario pattern --to 192.168.1.23:4000
  python gdl90_replay.py record capture.gdl90 --seconds 600    # save what a real receiver sends
  python gdl90_replay.py play capture.gdl90 --to 192.168.1.23:4000
  python gdl90_replay.py listen                                # decode and print what arrives on :4000
  python gdl90_replay.py selftest                              # check CRC/encoding against the spec examples

Android emulator: run `adb emu redir add udp:4000:4000` once, then send to 127.0.0.1:4000 (the default).
A real phone: put the computer and phone on the same Wi-Fi and use the phone's IP address.

Scenarios come from app/android/data/src/main/resources/gdl90/scenarios.json, the same file the apps'
"Simulated receiver" uses, so what you hear should match.
"""
import argparse
import json
import math
import socket
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCENARIOS = ROOT / "app/android/data/src/main/resources/gdl90/scenarios.json"
DEFAULT_PORT = 4000
EARTH_RADIUS_M = 6_371_000.0
M_PER_NM = 1852.0
LAT_LON_RES = 180.0 / (1 << 23)
ELLIPSOID_OFFSET_FT = 100.0

# ---------------------------------------------------------------- GDL 90 framing (spec §2.2)

CRC_TABLE = []
for i in range(256):
    crc = i << 8
    for _ in range(8):
        crc = ((crc << 1) ^ (0x1021 if crc & 0x8000 else 0)) & 0xFFFF
    CRC_TABLE.append(crc)


def crc16(data):
    crc = 0
    for b in data:
        crc = (CRC_TABLE[crc >> 8] ^ (crc << 8) ^ b) & 0xFFFF
    return crc


def frame(msg):
    c = crc16(msg)
    clear = bytes(msg) + bytes([c & 0xFF, c >> 8])
    out = bytearray([0x7E])
    for b in clear:
        if b in (0x7D, 0x7E):
            out += bytes([0x7D, b ^ 0x20])
        else:
            out.append(b)
    out.append(0x7E)
    return bytes(out)


def deframe(data):
    """Yields clear messages with a good CRC; counts bad ones in deframe.bad."""
    buf, in_frame, esc = bytearray(), False, False
    for b in data:
        if b == 0x7E:
            if in_frame and len(buf) >= 3:
                n = len(buf) - 2
                if crc16(buf[:n]) == buf[n] | (buf[n + 1] << 8):
                    yield bytes(buf[:n])
                else:
                    deframe.bad += 1
            buf, in_frame, esc = bytearray(), True, False
        elif not in_frame:
            continue
        elif esc:
            buf.append(b ^ 0x20)
            esc = False
        elif b == 0x7D:
            esc = True
        else:
            buf.append(b)


deframe.bad = 0

# ---------------------------------------------------------------- messages (spec §3)


def rhu(x):
    """Round half up, like the apps (Kotlin roundToInt)."""
    return int(math.floor(x + 0.5))


def heartbeat(gps_valid, ts):
    s1 = (0x80 if gps_valid else 0) | 0x01
    s2 = (((ts >> 16) & 1) << 7) | 0x01
    return bytes([0x00, s1, s2, ts & 0xFF, (ts >> 8) & 0xFF, 0, 0])


def report(ownship, address, lat, lon, alt_ft, airborne=True, track=None, gs=None, vs=None, callsign="",
           address_type=0, nic=9, nacp=9, emitter=1):
    la = rhu(lat / LAT_LON_RES) & 0xFFFFFF
    lo = rhu(lon / LAT_LON_RES) & 0xFFFFFF
    alt = 0xFFF if alt_ft is None else max(0, min(0xFFE, rhu((alt_ft + 1000) / 25)))
    misc = (0x08 if airborne else 0) | (1 if track is not None else 0)
    hv = 0xFFF if gs is None else max(0, min(0xFFE, rhu(gs)))
    vv = 0x800 if vs is None else max(-0x1FE, min(0x1FE, rhu(vs / 64))) & 0xFFF
    tt = 0 if track is None else rhu(track * 256 / 360) & 0xFF
    cs = (callsign.upper() + " " * 8)[:8].encode("ascii")
    m = bytearray(28)
    m[0] = 10 if ownship else 20
    m[1] = address_type & 0x0F
    m[2:5] = address.to_bytes(3, "big")
    m[5:8] = la.to_bytes(3, "big")
    m[8:11] = lo.to_bytes(3, "big")
    m[11] = alt >> 4
    m[12] = ((alt & 0x0F) << 4) | misc
    m[13] = (nic << 4) | nacp
    m[14] = hv >> 4
    m[15] = ((hv & 0x0F) << 4) | (vv >> 8)
    m[16] = vv & 0xFF
    m[17] = tt
    m[18] = emitter
    m[19:27] = cs
    return bytes(m)


def geo_altitude(alt_ft, vfom=10):
    a = max(-32768, min(32767, rhu(alt_ft / 5))) & 0xFFFF
    return bytes([0x0B, a >> 8, a & 0xFF, vfom >> 8, vfom & 0xFF])


def foreflight_id(name, long_name, msl=False):
    m = bytearray(39)
    m[0], m[1], m[2] = 0x65, 0, 1
    m[3:11] = b"\xff" * 8
    n = name.encode("utf-8")[:8]
    ln = long_name.encode("utf-8")[:16]
    m[11:11 + len(n)] = n
    m[19:19 + len(ln)] = ln
    m[38] = 1 if msl else 0
    return bytes(m)


def decode(m):
    """Short human-readable summary of one clear message."""
    mid = m[0]
    if mid == 0x00 and len(m) >= 7:
        ts = ((m[2] & 0x80) << 9) | (m[4] << 8) | m[3]
        return f"HEARTBEAT gps={'ok' if m[1] & 0x80 else 'NO FIX'} utc={m[2] & 1} t={ts}s"
    if mid in (10, 20) and len(m) >= 28:
        s24 = lambda i: (lambda v: v - 0x1000000 if v & 0x800000 else v)(int.from_bytes(m[i:i + 3], "big"))
        lat, lon = s24(5) * LAT_LON_RES, s24(8) * LAT_LON_RES
        nic = m[13] >> 4
        alt = (m[11] << 4) | (m[12] >> 4)
        hv = (m[14] << 4) | (m[15] >> 4)
        misc = m[12] & 0x0F
        pos = "no position" if (s24(5) == 0 and s24(8) == 0 and nic == 0) else f"{lat:.4f},{lon:.4f}"
        kind = "OWNSHIP" if mid == 10 else "TRAFFIC"
        return (f"{kind} {m[2:5].hex()} type={m[1] & 0x0F} {m[19:27].decode('ascii', 'replace').strip() or '-'} {pos} "
                f"alt={'?' if alt == 0xFFF else alt * 25 - 1000}ft gs={'?' if hv == 0xFFF else hv}kt "
                f"trk={(m[17] * 360 / 256) if misc & 3 else '?'} {'air' if misc & 8 else 'GROUND'} nic={nic} nacp={m[13] & 0x0F}")
    if mid == 11 and len(m) >= 5:
        return f"GEO ALT {int.from_bytes(m[1:3], 'big', signed=True) * 5}ft"
    if mid == 0x65 and len(m) >= 39 and m[1] == 0:
        return f"FOREFLIGHT ID {m[11:19].rstrip(bytes([0])).decode('utf-8', 'replace')} msl={m[38] & 1}"
    return f"OTHER id=0x{mid:02x} len={len(m)}"

# ---------------------------------------------------------------- geometry (same as the apps' Geo)


def destination(lat, lon, bearing_deg, dist_nm):
    d = (dist_nm * M_PER_NM / 1000.0) * 1000.0 / EARTH_RADIUS_M  # same steps as the apps (NM -> km -> m)
    b, la1, lo1 = math.radians(bearing_deg), math.radians(lat), math.radians(lon)
    la2 = math.asin(math.sin(la1) * math.cos(d) + math.cos(la1) * math.sin(d) * math.cos(b))
    lo2 = lo1 + math.atan2(math.sin(b) * math.sin(d) * math.cos(la1), math.cos(d) - math.sin(la1) * math.sin(la2))
    return math.degrees(la2), ((math.degrees(lo2) + 540) % 360) - 180


def bearing(a, b):
    la1, la2, dlo = math.radians(a[0]), math.radians(b[0]), math.radians(b[1] - a[1])
    y = math.sin(dlo) * math.cos(la2)
    x = math.cos(la1) * math.sin(la2) - math.sin(la1) * math.cos(la2) * math.cos(dlo)
    return math.degrees(math.atan2(y, x)) % 360


def distance_nm(a, b):
    la1, la2 = math.radians(a[0]), math.radians(b[0])
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin(math.radians(b[1] - a[1]) / 2) ** 2
    return 2 * math.atan2(math.sqrt(h), math.sqrt(abs(1 - h))) * EARTH_RADIUS_M / M_PER_NM

# ---------------------------------------------------------------- scenarios (same rules as Gdl90Scenario.kt)


def load_scenarios():
    return {s["id"]: s for s in json.loads(SCENARIOS.read_text(encoding="utf-8"))["scenarios"]}


def scenario_frames(sc, t, utc):
    o = sc["ownship"]
    own_at = lambda tt: destination(o["lat"], o["lon"], o["trackDeg"], o["gsKt"] * tt / 3600)
    pos = own_at(t)
    msgs = [
        heartbeat(True, utc),
        foreflight_id("Replay", "gdl90_replay.py"),
        report(True, int(o["icao"], 16), *pos, o["altFt"], track=o["trackDeg"], gs=o["gsKt"], vs=0, callsign=o["callsign"]),
        geo_altitude(o["altFt"] - ELLIPSOID_OFFSET_FT),
    ]
    for x in sc["traffic"]:
        k, icao, cs = x["kind"], int(x["icao"], 16), x.get("callsign", "")
        if k == "meet":
            start = destination(o["lat"], o["lon"], o["trackDeg"] + x["relBearingDeg"], x["distNm"])
            target = destination(*own_at(x["meetSec"]), o["trackDeg"], x["missNm"])
            trk = bearing(start, target)
            gs = distance_nm(start, target) / x["meetSec"] * 3600
            vs = x.get("vsFpm", 0)
            msgs.append(report(False, icao, *destination(*start, trk, gs * t / 3600), o["altFt"] + x["relAltFt"] + vs * t / 60,
                               track=trk, gs=gs, vs=vs, callsign=cs))
        elif k == "orbit":
            d = 1 if x.get("clockwise") else -1
            ang = x.get("startDeg", 0) + d * (x["gsKt"] / 3600) / (2 * math.pi * x["radiusNm"]) * 360 * t
            msgs.append(report(False, icao, *destination(x["centerLat"], x["centerLon"], ang, x["radiusNm"]), x["altFt"],
                               track=(ang + d * 90) % 360, gs=x["gsKt"], vs=0, callsign=cs))
        elif k == "ground":
            msgs.append(report(False, icao, *destination(x["lat"], x["lon"], x["trackDeg"], x["gsKt"] * t / 3600), 0,
                               airborne=False, track=x["trackDeg"], gs=x["gsKt"], vs=0, emitter=18))
        elif k == "shadow":
            msgs.append(report(False, icao, *pos, o["altFt"], track=o["trackDeg"], gs=o["gsKt"], vs=0, address_type=2))
    return b"".join(frame(m) for m in msgs)

# ---------------------------------------------------------------- commands


def parse_target(s):
    host, _, port = s.rpartition(":")
    return (host or "127.0.0.1", int(port or DEFAULT_PORT))


def sender(broadcast):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    if broadcast:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    return s


def cmd_list(_):
    for sid, s in load_scenarios().items():
        print(f"{sid:12} {s['durationSec']:4}s  {s['name']}")


def cmd_scenario(a):
    sc = load_scenarios().get(a.id) or sys.exit(f"unknown scenario '{a.id}' (try: list)")
    to, sock = parse_target(a.to), sender(a.broadcast)
    print(f"Sending '{sc['name']}' to {to[0]}:{to[1]} once a second{' (looping)' if a.loop else ''}. Ctrl+C to stop.")
    t0 = time.time()
    while True:
        t = (time.time() - t0) * a.speed
        if t >= sc["durationSec"]:
            if not a.loop:
                break
            t0, t = time.time(), 0.0
        sock.sendto(scenario_frames(sc, t, int(time.time()) % 86400), to)
        if a.verbose:
            print(f"t={t:5.0f}s")
        time.sleep(1.0)


def cmd_record(a):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("", a.port))
    s.settimeout(1.0)
    t0, n = time.time(), 0
    print(f"Recording UDP :{a.port} to {a.file} for {a.seconds}s. Ctrl+C to stop.")
    with open(a.file, "w", encoding="ascii") as f:
        f.write("# gdl90_replay capture: <milliseconds since start> <datagram hex>\n")
        try:
            while time.time() - t0 < a.seconds:
                try:
                    data, _ = s.recvfrom(4096)
                except socket.timeout:
                    continue
                f.write(f"{int((time.time() - t0) * 1000)} {data.hex()}\n")
                n += 1
        except KeyboardInterrupt:
            pass
    print(f"{n} datagrams saved.")


def cmd_play(a):
    lines = [ln.split() for ln in Path(a.file).read_text(encoding="ascii").splitlines() if ln and not ln.startswith("#")]
    to, sock = parse_target(a.to), sender(a.broadcast)
    print(f"Playing {len(lines)} datagrams to {to[0]}:{to[1]}{' (looping)' if a.loop else ''}.")
    while True:
        t0 = time.time()
        for ms, hexdata in lines:
            wait = int(ms) / 1000 / a.speed - (time.time() - t0)
            if wait > 0:
                time.sleep(wait)
            sock.sendto(bytes.fromhex(hexdata), to)
        if not a.loop:
            break


def cmd_listen(a):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("", a.port))
    print(f"Listening on UDP :{a.port}. Ctrl+C to stop.")
    while True:
        data, src = s.recvfrom(4096)
        for m in deframe(data):
            print(f"{src[0]}  {decode(m)}")
        if deframe.bad:
            print(f"   ({deframe.bad} frames with a bad CRC so far)")


def cmd_selftest(_):
    # GDL90 §2.2.4 heartbeat example.
    hb = bytes([0x00, 0x81, 0x41, 0xDB, 0xD0, 0x08, 0x02])
    assert crc16(hb) == 0x8BB3, hex(crc16(hb))
    assert frame(hb) == bytes([0x7E, 0x00, 0x81, 0x41, 0xDB, 0xD0, 0x08, 0x02, 0xB3, 0x8B, 0x7E])
    # GDL90 §3.5.2 Table 12 traffic report (lat/lon as decoded from the table bytes).
    table12 = bytes.fromhex("1400ab45491fef15a889780f09a907b00120014e3832355620202000")
    lat = 0x1FEF15 * LAT_LON_RES
    lon = (0xA88978 - 0x1000000) * LAT_LON_RES
    mine = report(False, 0xAB4549, lat, lon, 5000, track=45, gs=123, vs=64, callsign="N825V", nic=10, nacp=9)
    assert mine == table12, mine.hex()
    assert list(deframe(frame(table12))) == [table12]
    # Byte stuffing (§2.2.1).
    assert frame(bytes([0x02, 0x7E, 0x7D, 0x11]))[:7] == bytes([0x7E, 0x02, 0x7D, 0x5E, 0x7D, 0x5D, 0x11])
    for sid, sc in load_scenarios().items():
        assert list(deframe(scenario_frames(sc, 10, 0))), sid
    print("selftest OK: CRC, framing, byte stuffing and Table 12 encoding match the GDL 90 spec; scenarios load.")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list").set_defaults(fn=cmd_list)
    s = sub.add_parser("scenario")
    s.add_argument("id")
    s.add_argument("--to", default=f"127.0.0.1:{DEFAULT_PORT}", help="host:port (default 127.0.0.1:4000)")
    s.add_argument("--once", dest="loop", action="store_false", help="stop at the end instead of looping")
    s.add_argument("--speed", type=float, default=1.0, help="time multiplier (2 = twice as fast)")
    s.add_argument("--broadcast", action="store_true", help="allow sending to a broadcast address")
    s.add_argument("-v", "--verbose", action="store_true")
    s.set_defaults(fn=cmd_scenario)
    r = sub.add_parser("record")
    r.add_argument("file")
    r.add_argument("--port", type=int, default=DEFAULT_PORT)
    r.add_argument("--seconds", type=int, default=3600)
    r.set_defaults(fn=cmd_record)
    pl = sub.add_parser("play")
    pl.add_argument("file")
    pl.add_argument("--to", default=f"127.0.0.1:{DEFAULT_PORT}")
    pl.add_argument("--loop", action="store_true")
    pl.add_argument("--speed", type=float, default=1.0)
    pl.add_argument("--broadcast", action="store_true")
    pl.set_defaults(fn=cmd_play)
    li = sub.add_parser("listen")
    li.add_argument("--port", type=int, default=DEFAULT_PORT)
    li.set_defaults(fn=cmd_listen)
    sub.add_parser("selftest").set_defaults(fn=cmd_selftest)
    a = p.parse_args()
    try:
        a.fn(a)
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
