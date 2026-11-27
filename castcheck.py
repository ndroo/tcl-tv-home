#!/usr/bin/env python3
"""Tiny Chromecast tester (Python standard library only).

Talks to the TV's Chromecast built-in the same way a phone does, so casting can be
checked from this Mac without ADB.

  python3 castcheck.py status            what the TV's Chromecast is doing now
  python3 castcheck.py play [URL]        cast a test video and wait until it plays
  python3 castcheck.py launch APPID      open a cast app (YouTube 233637DE, Netflix CA5E8412)
  python3 castcheck.py stop              stop whatever is being cast

Set TV=<ip> to point it at a different TV (default 192.168.2.50).
"""
import json, os, socket, ssl, struct, sys, time

HOST = os.environ.get("TV", "192.168.2.50")
# Must send CORS headers: cast receivers load media cross-origin (W3C's samples don't; Google's old one 403s).
TEST_VIDEO = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
NS_CONN = "urn:x-cast:com.google.cast.tp.connection"
NS_HEART = "urn:x-cast:com.google.cast.tp.heartbeat"
NS_RECV = "urn:x-cast:com.google.cast.receiver"
NS_MEDIA = "urn:x-cast:com.google.cast.media"
APPS = {"CC1AD845": "Default Media Receiver", "233637DE": "YouTube", "CA5E8412": "Netflix",
        "E8C28D3C": "Backdrop"}


# --- minimal protobuf for the CastMessage envelope -------------------------------------
def _varint(n):
    out = b""
    while True:
        b = n & 0x7F
        n >>= 7
        out += bytes([b | (0x80 if n else 0)])
        if not n:
            return out

def _field_str(num, s):
    data = s.encode()
    return _varint(num << 3 | 2) + _varint(len(data)) + data

def encode(src, dst, ns, payload):
    return (_varint(1 << 3) + _varint(0) + _field_str(2, src) + _field_str(3, dst) +
            _field_str(4, ns) + _varint(5 << 3) + _varint(0) + _field_str(6, json.dumps(payload)))

def decode(buf):
    i, out = 0, {}
    while i < len(buf):
        key, i = _read_varint(buf, i)
        num, wt = key >> 3, key & 7
        if wt == 0:
            val, i = _read_varint(buf, i)
        elif wt == 2:
            ln, i = _read_varint(buf, i)
            val, i = buf[i:i + ln], i + ln
        else:
            raise ValueError("unexpected wire type %d" % wt)
        out[num] = val
    return {"src": out.get(2, b"").decode(), "dst": out.get(3, b"").decode(),
            "ns": out.get(4, b"").decode(), "data": json.loads(out.get(6, b"{}") or b"{}")}

def _read_varint(buf, i):
    shift = n = 0
    while True:
        b = buf[i]; i += 1
        n |= (b & 0x7F) << shift; shift += 7
        if not b & 0x80:
            return n, i


class Cast:
    def __init__(self, host):
        ctx = ssl.create_default_context()
        ctx.check_hostname, ctx.verify_mode = False, ssl.CERT_NONE  # TVs use self-signed certs
        self.s = ctx.wrap_socket(socket.create_connection((host, 8009), timeout=10))
        self.req = 0
        self.send("receiver-0", NS_CONN, {"type": "CONNECT"})

    def send(self, dst, ns, payload, src="sender-0"):
        msg = encode(src, dst, ns, payload)
        self.s.sendall(struct.pack(">I", len(msg)) + msg)

    def recv(self):
        hdr = self._exact(4)
        return decode(self._exact(struct.unpack(">I", hdr)[0]))

    def _exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.s.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("TV closed the connection")
            buf += chunk
        return buf

    def request(self, dst, ns, payload, want, timeout=20):
        self.req += 1
        payload = dict(payload, requestId=self.req)
        self.send(dst, ns, payload)
        return self.wait(lambda m: m["data"].get("type") in want, timeout)

    def wait(self, pred, timeout=20):
        end = time.time() + timeout
        while time.time() < end:
            m = self.recv()
            if m["ns"] == NS_HEART and m["data"].get("type") == "PING":
                self.send(m["src"], NS_HEART, {"type": "PONG"})
                continue
            if pred(m):
                return m
        raise TimeoutError("no matching reply from the TV")

    def status(self):
        return self.request("receiver-0", NS_RECV, {"type": "GET_STATUS"}, {"RECEIVER_STATUS"})["data"]["status"]

    def launch(self, app_id):
        self.request("receiver-0", NS_RECV, {"type": "LAUNCH", "appId": app_id},
                     {"RECEIVER_STATUS", "LAUNCH_ERROR"}, timeout=30)
        end = time.time() + 30
        while time.time() < end:  # wait until the app reports in
            for a in self.status().get("applications", []):
                if a.get("appId") == app_id and a.get("transportId"):
                    return a
            time.sleep(1)
        raise TimeoutError("app %s did not start" % app_id)


def describe(st):
    apps = st.get("applications", [])
    vol = st.get("volume", {})
    running = ", ".join("%s (%s)" % (a.get("displayName"), a.get("appId")) for a in apps) or "nothing"
    return "casting: %s | volume %s%s" % (running, round(vol.get("level", 0) * 100),
                                         " muted" if vol.get("muted") else "")


def main(argv):
    cmd = argv[1] if len(argv) > 1 else "status"
    c = Cast(HOST)
    if cmd == "status":
        print(describe(c.status()))
    elif cmd == "launch":
        a = c.launch(argv[2])
        print("launched %s; %s" % (a.get("displayName"), describe(c.status())))
    elif cmd == "play":
        url = argv[2] if len(argv) > 2 else TEST_VIDEO
        a = c.launch("CC1AD845")
        t = a["transportId"]
        c.send(t, NS_CONN, {"type": "CONNECT"})
        ctype = ("application/x-mpegurl" if ".m3u8" in url else
                 "application/dash+xml" if ".mpd" in url else "video/mp4")
        c.request(t, NS_MEDIA, {"type": "LOAD", "autoplay": True, "media": {
            "contentId": url, "contentType": ctype, "streamType": "BUFFERED"}},
            {"MEDIA_STATUS", "LOAD_FAILED", "LOAD_CANCELLED", "INVALID_REQUEST"}, timeout=30)
        m = c.wait(lambda m: m["ns"] == NS_MEDIA and (
            m["data"].get("type") != "MEDIA_STATUS" or
            any(s.get("playerState") == "PLAYING" for s in m["data"].get("status", []))), timeout=60)
        if m["data"].get("type") == "MEDIA_STATUS":
            print("PLAYING on the TV: %s" % url)
        else:
            print("FAILED: %s" % m["data"])
            return 1
    elif cmd == "stop":
        for a in c.status().get("applications", []):
            if a.get("appId") != "E8C28D3C":
                c.request("receiver-0", NS_RECV, {"type": "STOP", "sessionId": a["sessionId"]},
                          {"RECEIVER_STATUS"})
        print("stopped; " + describe(c.status()))
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
