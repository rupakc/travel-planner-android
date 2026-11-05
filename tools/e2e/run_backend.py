"""Run the backend with every SerpAPI URL pointed at the local fake (E2E only).

Usage (cwd = the travel-planner web repo's backend/ directory, with its Python
dependencies installed and the E2E environment set; see docs/Testing.md):

    DATA_DIR=/tmp/tp-e2e ADMIN_PASSWORD=<test value> SERPAPI_KEY=fake \\
    RATE_LIMIT_ENABLED=false \\
    python3 /path/to/travel-planner-android/tools/e2e/run_backend.py http://127.0.0.1:9100/search 8001

It patches serp_flights.SERP_URL / _SERP_URL, hotel_prices.SERP_URL and
activity_prices.SERP_URL to <fake_url> before importing app.main (the backend has
no setting for this), then serves the app on 127.0.0.1:<port>. Test use only.
"""

import os
import sys

sys.path.insert(0, os.getcwd())

import uvicorn  # noqa: E402

from app.services import activity_prices, hotel_prices, serp_flights

FAKE = sys.argv[1]
serp_flights.SERP_URL = FAKE
serp_flights._SERP_URL = FAKE
hotel_prices.SERP_URL = FAKE
activity_prices.SERP_URL = FAKE

from app.main import app  # noqa: E402

uvicorn.run(app, host="127.0.0.1", port=int(sys.argv[2]))
