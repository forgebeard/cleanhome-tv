#!/usr/bin/env python3
"""Serve one APK to a TV browser on the local network.

For TVs where ADB is locked (Yandex TV Stations on YaOS and other vendor builds): run this on a computer
in the same network, open the printed address in the TV browser and press the download button.
Only the APK and the download page are served; nothing else from the computer is reachable.

    python3 scripts/serve-apk.py CleanHome-4.6.apk [--port 8000]
"""
import argparse
import html
import http.server
import os
import re
import socket
import subprocess
import sys

PAGE = """<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><title>CleanHome</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  body {{ margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
         background: #070a14; color: #f2f4f8; font-family: sans-serif; text-align: center; }}
  h1 {{ font-size: 64px; margin: 0 0 16px; }}
  p {{ font-size: 28px; color: #aab3c5; margin: 8px 0; }}
  a.btn {{ display: inline-block; margin: 40px 0 32px; padding: 28px 64px; border-radius: 24px;
          background: #f2f4f8; color: #070a14; font-size: 40px; font-weight: bold; text-decoration: none; }}
  a.btn:focus, a.btn:hover {{ outline: 6px solid #7aa7ff; outline-offset: 6px; }}
</style></head>
<body><main>
  <h1>CleanHome</h1>
  <p>{name} &middot; {size}</p>
  <a class="btn" href="/{name}" autofocus>Скачать / Download</a>
  <p>1. Нажмите кнопку и откройте скачанный файл.</p>
  <p>2. Разрешите браузеру установку приложений, если телевизор спросит.</p>
  <p>3. Установите и нажмите «Домой» на пульте.</p>
</main></body></html>
"""


def lan_ips():
    """Private IPv4 addresses of this computer, home Wi-Fi style (192.168.x.x) first.

    A VPN can own the default route, so the route-picked address alone may be a tunnel the TV cannot reach;
    every interface address is listed as well."""
    found = []
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("192.0.2.1", 9))  # TEST-NET address: no packet is sent, the OS only picks the route
        found.append(s.getsockname()[0])
    except OSError:
        pass
    finally:
        s.close()
    for cmd in (["ifconfig"], ["ip", "-4", "-o", "addr"], ["ipconfig"]):
        try:
            out = subprocess.run(cmd, capture_output=True, text=True, timeout=5).stdout
        except (OSError, subprocess.SubprocessError):
            continue
        found += re.findall(r"(?:inet |IPv4[^:]*:\s*)(\d+\.\d+\.\d+\.\d+)", out)
        if found:
            break

    def private(ip):
        a, b = (int(x) for x in ip.split(".")[:2])
        return a == 10 or (a == 192 and b == 168) or (a == 172 and 16 <= b <= 31)

    rank = {"192": 0, "10": 1, "172": 2}
    ips = sorted({ip for ip in found if private(ip)}, key=lambda ip: rank[ip.split(".")[0]])
    return ips or ["127.0.0.1"]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("apk", help="path to the .apk file")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()

    apk = os.path.abspath(args.apk)
    if not apk.endswith(".apk") or not os.path.isfile(apk):
        sys.exit(f"Not an APK file: {args.apk}")
    name = os.path.basename(apk)
    size = os.path.getsize(apk)
    page = PAGE.format(name=html.escape(name), size=f"{size / 1024 / 1024:.1f} MB").encode()

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            path = self.path.split("?", 1)[0]
            if path in ("/", "/index.html"):
                self._send(200, "text/html; charset=utf-8", len(page))
                self.wfile.write(page)
            elif path == "/" + name:
                self._send(200, "application/vnd.android.package-archive", size,
                           {"Content-Disposition": f'attachment; filename="{name}"'})
                with open(apk, "rb") as f:
                    while chunk := f.read(1 << 16):
                        self.wfile.write(chunk)
                print(f"APK sent to {self.client_address[0]}", flush=True)
            else:
                self._send(404, "text/plain", 0)

        def _send(self, code, ctype, length, extra=None):
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(length))
            self.send_header("Cache-Control", "no-store")
            for k, v in (extra or {}).items():
                self.send_header(k, v)
            self.end_headers()

        def log_message(self, fmt, *a):
            pass

    server = http.server.ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    ips = lan_ips()
    print(f"Open in the TV browser: http://{ips[0]}:{args.port}", flush=True)
    for ip in ips[1:]:
        print(f"  if it does not open, try: http://{ip}:{args.port}", flush=True)
    print("Ctrl+C stops the server.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
