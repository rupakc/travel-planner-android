"""Local stand-in for serpapi.com/search used only for E2E (live quota exhausted).

Replays the repo's recorded fixtures; every price is multiplied by the number in
SCALE_FILE (default 1.0) so drops can be simulated between checks.

Usage:
    python3 -I tools/e2e/fake_serpapi.py <fixtures_dir> <scale_file> <port>

    <fixtures_dir>  the travel-planner web repo's backend/tests/fixtures/serpapi
                    (contains hotels/ and activities/; there is no flights fixture,
                    so Google Flights requests get HTTP 500 and the backend falls back)
    <scale_file>    text file holding a number; prices are multiplied by it
                    (write 0.8 to simulate a 20 % drop; missing file = 1.0)
    <port>          listens on 127.0.0.1:<port>

Pair it with tools/e2e/run_backend.py. Contains no secrets; the api_key query
parameter is discarded.
"""

import json
import re
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

FIX = Path(sys.argv[1])
SCALE_FILE = Path(sys.argv[2])
PORT = int(sys.argv[3])
LOG = []


def scale() -> float:
    try:
        return float(SCALE_FILE.read_text().strip())
    except Exception:
        return 1.0


def scaled(obj, k):
    if isinstance(obj, dict):
        out = {}
        for key, v in obj.items():
            if isinstance(v, (int, float)) and not isinstance(v, bool) and (
                key.startswith("extracted_") or key == "price"
            ):
                out[key] = round(v * k, 2)
            else:
                out[key] = scaled(v, k)
        return out
    if isinstance(obj, list):
        return [scaled(v, k) for v in obj]
    if isinstance(obj, str) and k != 1.0:
        return re.sub(
            r"([$€£])\s?(\d[\d,]*(?:\.\d+)?)",
            lambda m: f"{m.group(1)}{float(m.group(2).replace(',', '')) * k:,.2f}",
            obj,
        )
    return obj


def pick(params: dict) -> Path | None:
    engine = params.get("engine", "")
    q = params.get("q", "").lower()
    if engine == "google_hotels":
        if params.get("property_token"):
            return FIX / "hotels" / "property_details_lumen_generic_q.json"
        return FIX / "hotels" / "city_search_lisbon.json"
    if engine == "google":
        if "viator" in q:
            return FIX / "activities" / "viator_belem_tower.json"
        return FIX / "activities" / "gyg_belem_tower.json"
    return None


class H(BaseHTTPRequestHandler):
    def do_GET(self):
        params = {k: v[0] for k, v in parse_qs(urlparse(self.path).query).items()}
        params.pop("api_key", None)
        path = pick(params)
        print(json.dumps({"engine": params.get("engine"), "q": params.get("q"),
                          "token": bool(params.get("property_token")),
                          "file": path.name if path else None, "scale": scale()}),
              flush=True)
        if path is None:
            self.send_response(500)
            self.end_headers()
            self.wfile.write(b'{"error":"no fixture for this engine"}')
            return
        body = json.dumps(scaled(json.loads(path.read_text()), scale())).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *a):
        pass


ThreadingHTTPServer(("127.0.0.1", PORT), H).serve_forever()
