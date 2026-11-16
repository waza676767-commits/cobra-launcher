#!/usr/bin/env python3
"""mathhelp backup bot (runs on GitHub Actions every ~15 min).

The free relay (ntfy.sh) forgets messages after 12 h and images after 3 h. This copies everything
new into the repo so phones that were offline longer can still catch up.
Everything stored here is end-to-end encrypted already; the bot can't read it.

Layout (append-only, the app reads it with HTTP Range requests):
  mathhelp-archive/topics.txt          inbox topics announced by apps (hashes, not usernames)
  mathhelp-archive/<topic>.jsonl       one relay event per line: {"id","time","message"}
  mathhelp-archive/files/<YYYY-MM-DD>/<id>.bin   encrypted images by UTC day of the message (kept FILE_DAYS)
"""
import json
import os
import re
import shutil
import time
import urllib.request

RELAY = "https://ntfy.sh/"
REGISTRY = "mh_registry_v1"
ROOT = "mathhelp-archive"
TOPIC_RE = re.compile(r"^mh_[0-9a-f]{40}$")
FILE_RE = re.compile(r"^[A-Za-z0-9]{6,20}$")
FILE_DAYS = 30
MAX_FILE = 2_200_000


def get(url, timeout=60):
    req = urllib.request.Request(url, headers={"User-Agent": "mathhelp-backup"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def poll(topics):
    out = []
    raw = get(RELAY + ",".join(topics) + "/json?poll=1&since=all").decode("utf-8", "replace")
    for line in raw.splitlines():
        try:
            ev = json.loads(line)
        except ValueError:
            continue
        if ev.get("event") == "message":
            out.append(ev)
    return out


def main():
    os.makedirs(os.path.join(ROOT, "files"), exist_ok=True)
    tp = os.path.join(ROOT, "topics.txt")
    topics = set()
    if os.path.exists(tp):
        topics = {t.strip() for t in open(tp) if TOPIC_RE.match(t.strip())}
    for ev in poll([REGISTRY]):
        t = (ev.get("message") or "").strip()
        if TOPIC_RE.match(t):
            topics.add(t)
    with open(tp, "w") as f:
        f.write("".join(t + "\n" for t in sorted(topics)))

    saved = files = 0
    ordered = sorted(topics)
    for i in range(0, len(ordered), 40):
        batch = ordered[i:i + 40]
        try:
            events = poll(batch)
        except Exception as e:  # keep going with the other batches
            print("poll failed:", e)
            continue
        by_topic = {}
        for ev in events:
            by_topic.setdefault(ev.get("topic"), []).append(ev)
        for topic, evs in by_topic.items():
            if topic not in topics:
                continue
            path = os.path.join(ROOT, topic + ".jsonl")
            have = set()
            if os.path.exists(path):
                for line in open(path, encoding="utf-8"):
                    try:
                        have.add(json.loads(line)["id"])
                    except (ValueError, KeyError):
                        pass
            new = [e for e in sorted(evs, key=lambda e: e.get("time", 0)) if e.get("id") not in have]
            if not new:
                continue
            with open(path, "a", encoding="utf-8") as f:
                for e in new:
                    msg = e.get("message", "")
                    f.write(json.dumps({"id": e["id"], "time": e.get("time", 0), "message": msg},
                                       separators=(",", ":")) + "\n")
                    saved += 1
                    # Envelopes with an image name the relay file id in plain text ("a").
                    try:
                        fid = json.loads(msg).get("a")
                    except (ValueError, AttributeError):
                        fid = None
                    if fid and FILE_RE.match(fid):
                        day = time.strftime("%Y-%m-%d", time.gmtime(e.get("time", 0)))
                        os.makedirs(os.path.join(ROOT, "files", day), exist_ok=True)
                        dest = os.path.join(ROOT, "files", day, fid + ".bin")
                        if not os.path.exists(dest):
                            try:
                                blob = get(RELAY + "file/" + fid + ".bin")
                                if len(blob) <= MAX_FILE:
                                    open(dest, "wb").write(blob)
                                    files += 1
                            except Exception as ex:
                                print("file", fid, "gone:", ex)

    cutoff = time.strftime("%Y-%m-%d", time.gmtime(time.time() - FILE_DAYS * 86400))
    fdir = os.path.join(ROOT, "files")
    for day in os.listdir(fdir):
        if re.match(r"^\d{4}-\d{2}-\d{2}$", day) and day < cutoff:
            shutil.rmtree(os.path.join(fdir, day))
    print(f"{len(topics)} inboxes, {saved} new messages, {files} new images")


if __name__ == "__main__":
    main()
