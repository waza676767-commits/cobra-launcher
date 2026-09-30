#!/usr/bin/env python3
"""Cobra online list: which players are using Cobra Client (for the Cobra icon in the Tab list).

Run on your VPS (the one with the Minecraft server):
    python3 cobra-online.py            # listens on port 25581
Open the port:  sudo ufw allow 25581/tcp   (or your firewall's equivalent)
Keep it running: see cobra-online.service next to this file.

Clients POST /ping {"uuid": "...", "cosmetics": "catears,halo"} every 30 s and GET /online for the
UUIDs seen in the last 90 s and the cosmetics each one wears (so only Cobra players see them).
Nothing else is stored, and it's all in memory.
"""
import json, re, time, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 25581
TTL = 90
UUID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
COSMETICS = re.compile(r"^[a-z0-9_,:]{0,300}$")
seen = {}
wearing = {}
lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        if self.path != "/ping":
            return self._send(404, {"error": "not found"})
        try:
            n = min(int(self.headers.get("Content-Length", "0")), 1024)
            body = json.loads(self.rfile.read(n) or b"{}")
            uuid = body.get("uuid", "").lower()
            cos = str(body.get("cosmetics", ""))
        except Exception:
            return self._send(400, {"error": "bad request"})
        if not UUID.match(uuid):
            return self._send(400, {"error": "bad uuid"})
        if not COSMETICS.match(cos):
            cos = ""
        with lock:
            seen[uuid] = time.time()
            wearing[uuid] = cos
        self._send(200, {"ok": True})

    def do_GET(self):
        if self.path != "/online":
            return self._send(404, {"error": "not found"})
        now = time.time()
        with lock:
            for u in [u for u, t in seen.items() if now - t > TTL]:
                del seen[u]
                wearing.pop(u, None)
            uuids = list(seen)
            cos = {u: wearing.get(u, "") for u in uuids}
        self._send(200, {"uuids": uuids, "cosmetics": cos})

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print(f"Cobra online list on port {PORT}")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
