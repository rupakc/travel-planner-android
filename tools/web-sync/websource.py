"""Reads the pinned web-repo commit that generated resources are synced from."""

from pathlib import Path


def read_pin(repo_root: Path) -> tuple[str, str]:
    """Return (repo, sha) from config/web-source.properties."""
    props = {}
    for line in (Path(repo_root) / "config" / "web-source.properties").read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, _, value = line.partition("=")
            props[key.strip()] = value.strip()
    return props["web.repo"], props["web.sha"]
