"""Polite, persistent cache for official public pages and media."""

import hashlib
import json
import random
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen

from .parser import ORIGIN


class Fetcher:
    def __init__(self, root, delay=0.6, retries=3, refresh=False):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self.delay, self.retries, self.refresh = delay, retries, refresh
        self.lock = threading.Lock()
        self.last_request = 0.0
        self.downloaded = self.cached = 0

    def get(self, url):
        parsed = urlparse(url)
        if parsed.scheme != "https" or parsed.netloc != "etesty.md.gov.cz" or not parsed.path.startswith("/"):
            raise ValueError(f"Source outside official eTesty origin: {url}")
        name = hashlib.sha256(url.encode()).hexdigest()
        location = self.root / name[:2] / name
        if location.is_file() and not self.refresh:
            self.cached += 1
            return location.read_bytes()
        error = None
        for attempt in range(self.retries):
            try:
                with self.lock:
                    pause = max(0, self.delay - (time.monotonic() - self.last_request))
                    time.sleep(pause)
                    self.last_request = time.monotonic()
                request = Request(url, headers={"User-Agent": "AutoSkolaCZ-public-research/0.1 (offline snapshot)", "Accept": "*/*"})
                with urlopen(request, timeout=75) as response:
                    body = response.read()
                    if response.status != 200 or not body:
                        raise IOError(f"HTTP {response.status} or empty response")
                location.parent.mkdir(parents=True, exist_ok=True)
                temporary = location.with_suffix(".tmp")
                temporary.write_bytes(body)
                temporary.replace(location)
                self.downloaded += 1
                return body
            except Exception as exc:
                error = exc
                if attempt + 1 < self.retries:
                    time.sleep(min(12, (2 ** attempt) + random.random()))
        raise IOError(f"Failed {url} after {self.retries} attempts: {error}")

    def record(self, url):
        body = self.get(url)
        key = hashlib.sha256(url.encode()).hexdigest()
        file = self.root / key[:2] / key
        timestamp = datetime.fromtimestamp(file.stat().st_mtime, timezone.utc).isoformat()
        return {"sourceUrl": url, "sha256": hashlib.sha256(body).hexdigest(), "size": len(body),
                "retrievedAt": timestamp}, body
