#!/usr/bin/env python3
"""Resolve and validate deployment settings without contacting the Docker daemon."""

import argparse
import ipaddress
import json
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
PRODUCTION_CA = "https://acme-v02.api.letsencrypt.org/directory"
STAGING_CA = "https://acme-staging-v02.api.letsencrypt.org/directory"


def validate(config, allow_staging):
    services = config["services"]
    app = services["app"]
    proxy = services["https"]
    address = proxy["environment"]["PUBLIC_IPV4"]
    try:
        ip = ipaddress.IPv4Address(address)
    except ipaddress.AddressValueError:
        raise ValueError("PUBLIC_IPV4 must be an IPv4 address without a scheme, port, or path.") from None
    if not ip.is_global or ip.is_multicast or ip.is_unspecified:
        raise ValueError("PUBLIC_IPV4 must be a publicly routable unicast address.")
    image = app["image"]
    if "@" in image:
        valid_image = re.fullmatch(r".+@sha256:[0-9a-f]{64}", image)
    else:
        name = image.rsplit("/", 1)[-1]
        valid_image = ":" in name and name.rsplit(":", 1)[-1] not in ("", "latest")
    if not valid_image:
        raise ValueError("APP_IMAGE needs an explicit release tag (not latest) or sha256 digest.")
    if app["environment"]["PUBLIC_ORIGIN"] != f"https://{address}":
        raise ValueError("The app HTTPS origin must match PUBLIC_IPV4.")
    if app["environment"]["SECURE_COOKIE"] != "true":
        raise ValueError("Production must enable secure cookies.")
    for name in ("app", "db"):
        if services[name].get("ports"):
            raise ValueError("Only the HTTPS proxy may publish host ports.")
    ports = {(p["published"], p["target"], p["protocol"]) for p in proxy["ports"]}
    if ports != {("80", 80, "tcp"), ("443", 443, "tcp")}:
        raise ValueError("The proxy must publish only 80/TCP and 443/TCP.")
    issuer = proxy["environment"]["ACME_DIRECTORY"]
    if issuer != PRODUCTION_CA and not (allow_staging and issuer == STAGING_CA):
        raise ValueError("Use the production Let's Encrypt CA, or --allow-staging for a staging rehearsal.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=Path(".env.production"))
    parser.add_argument("--allow-staging", action="store_true")
    args = parser.parse_args()
    env_file = args.env_file.resolve()
    if not env_file.is_file():
        parser.exit(1, "Environment file is missing; copy .env.production.example and fill it in.\n")
    try:
        result = subprocess.run(
            ["docker", "compose", "--env-file", str(env_file), "-f",
             str(ROOT / "compose.production.yaml"), "config", "--format", "json"],
            cwd=ROOT, capture_output=True, text=True, timeout=30,
        )
        if result.returncode:
            # Compose output can include interpolated credentials; never echo it.
            raise ValueError("Compose rejected configuration; check required settings, image syntax, and memory limits.")
        validate(json.loads(result.stdout), args.allow_staging)
    except FileNotFoundError:
        parser.exit(1, "Docker CLI is missing; install Docker with the Compose plugin.\n")
    except subprocess.TimeoutExpired:
        parser.exit(1, "Compose configuration resolution timed out.\n")
    except (KeyError, TypeError, json.JSONDecodeError):
        parser.exit(1, "Unexpected Compose configuration; check Docker Compose compatibility.\n")
    except ValueError as error:
        parser.exit(1, f"{error}\n")
    print("Configuration valid. Containers, TLS issuance, and cloud reachability are not tested.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
