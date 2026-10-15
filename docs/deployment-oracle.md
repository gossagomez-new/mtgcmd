# Oracle Cloud deployment using a public IPv4

This guide prepares one Linux VM running PostgreSQL, one Java app serving React,
and Caddy. A domain is not required. Use the standalone
[production Compose file](../compose.production.yaml); do not merge it with
the local `compose.yaml`.

**Readiness:** configuration/preflight checks can run without Docker daemon
access. Container execution, the pinned Caddy configuration, public certificate
issuance/renewal, and cloud gameplay still require operator verification. This
guide does not mean those checks have passed.

See the [specification](../spec/public-ip-docker-deployment.md) and
[ADR-0009](adr/0009-public-ip-docker-deployment.md) for scope and decisions.

## 1. Prepare the instance

- Record the Linux distribution, `uname -m`, and `free -h`. Ampere shapes are
  ARM64; x86 shapes need AMD64 images. Build on the VM for its native architecture,
  or transfer an image explicitly built for it. Local builds need additional RAM
  beyond the running services' budgets.
- Use a stable public IPv4. Oracle distinguishes reserved and ephemeral public
  addresses; check the assignment before relying on it for player access.
- Install Docker Engine with its Compose plugin using instructions appropriate
  to the actual distribution. Enable Docker at boot and confirm `docker info`
  and `docker compose version` work as your deployment user. Do not make the
  Docker socket world-writable.
- Allow inbound **80/TCP and 443/TCP** in the applicable OCI security lists/NSGs
  and the Linux firewall. Keep SSH restricted to your administration sources.
  Do not open 8080 or 5432. Keep SELinux enabled; the proxy bind mount uses a
  private `Z` label for SELinux hosts.
- Allow DNS and outbound HTTPS for image pulls, ACME, Moxfield, and Scryfall.
  Players also need access to externally hosted card images.

Oracle references: [public IP addresses](https://docs.oracle.com/en-us/iaas/Content/Network/Tasks/managingpublicIPs.htm),
[security rules](https://docs.oracle.com/en-us/iaas/Content/Network/Concepts/securityrules.htm).

## 2. Verify and build a release

From the repository root at the intended release commit, run the normal checks
on a non-root machine with Java 25, Maven, and Node 24:

```sh
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run format:check
./scripts/build.sh
npm --prefix frontend run test:e2e
python3 -B -m unittest discover -s scripts -p test_check_deployment.py
```

Browser tests need Chromium, as described in the [frontend guide](../frontend/README.md).
The build wrapper runs Java tests and packaging; PMD findings remain advisory.
Do not use a successful Docker build as evidence that tests passed: the Dockerfile
skips Java tests. Build from the same reviewed, tested source on the VM:

```sh
release_tag=$(git rev-parse --short=12 HEAD)
docker build --pull -t "mtgcmd:$release_tag" .
```

Keep that tag for the environment file; do not overwrite old release tags.
This first workflow builds locally on the VM without requiring a registry.
For an image built elsewhere for the same architecture, `docker image save`,
copy the archive over SSH, and `docker image load` on the VM. A registry image
tag or digest is also supported by `APP_IMAGE`; publishing is a separate action.
Review and pin the Dockerfile's base image digests for a reproducible release;
current major-version build and PostgreSQL tags need ongoing review.

## 3. Configure without exposing secrets

```sh
cp .env.production.example .env.production
chmod 600 .env.production
```

Edit `.env.production`:

- `PUBLIC_IPV4`: the VM's real public IPv4, with no scheme, port, or path.
- `ACME_EMAIL`: your operational contact address.
- `APP_IMAGE`: the exact built tag, for example `mtgcmd:0123456789ab`.
- `DATABASE_PASSWORD`: a long random private password. Use single quotes for
  literal dollar signs; follow Compose env-file quoting rules.

The starting limits are app 1 GiB, database 512 MiB, and proxy 128 MiB. Confirm
the VM has room for these limits **and** Linux, Docker, filesystem cache, and
builds. These are initial budgets, not measured sizing recommendations. JVM
heap is capped at 60% of app container memory, leaving space for native memory.

Production Compose sets `PUBLIC_ORIGIN=https://PUBLIC_IPV4` and secure cookies
directly. Keep one app instance: WebSocket broadcasts are process-local.

```sh
python3 -B scripts/check_deployment.py --env-file .env.production
```

This command reads and validates resolved Compose configuration without starting
containers or contacting Docker's daemon. It does not print credentials. Missing
settings, a non-public IP, an unversioned/latest image, and accidental staging CA
use are rejected. Shell environment variables override env-file values according
to Compose rules; unset old deployment overrides before checking or starting.
Do not paste raw `docker compose config` output: it includes passwords.

Define this convenience function in your terminal, at the repository root:

```sh
dc() {
  docker compose --env-file .env.production -f compose.production.yaml "$@"
}
```

## 4. Validate Caddy and rehearse certificate issuance

First validate syntax with the exact proxy image, without starting the stack:

```sh
dc run --rm --no-deps https caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
```

The configuration selects Let's Encrypt's `shortlived` ACME profile explicitly,
and sets default SNI for browsers that omit SNI when accessing a literal IP.
Caddy stores keys and renews certificates automatically. No private CA or
certificate-warning bypass is part of normal gameplay.

For an initial issuance rehearsal, create a separate environment file:

```sh
cp .env.production .env.production.staging
chmod 600 .env.production.staging
```

Change only `ACME_DIRECTORY` in that copy to:

```text
https://acme-staging-v02.api.letsencrypt.org/directory
```

Validate and start a separate staging project, **before production is running**:

```sh
python3 -B scripts/check_deployment.py --env-file .env.production.staging --allow-staging
docker compose --env-file .env.production.staging -f compose.production.yaml -p mtgcmd-tls-staging up -d
docker compose --env-file .env.production.staging -f compose.production.yaml -p mtgcmd-tls-staging logs --tail=100 https
```

Staging certificates are intentionally untrusted. Check issuance in logs; do not
ask players to bypass browser warnings. This project uses separate database and
certificate volumes. Stop it to release ports before starting production:

```sh
docker compose --env-file .env.production.staging -f compose.production.yaml -p mtgcmd-tls-staging down
```

No `-v`: the rehearsal does not delete volumes. Preserve evidence of success or
errors without sharing credentials. Creating certificates contacts an external
CA; run these steps only when you are ready to perform that operation.

References: [IP certificate availability](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability),
[Caddy ACME profile](https://caddyserver.com/docs/caddyfile/directives/tls),
[default SNI](https://caddyserver.com/docs/caddyfile/options#default-sni).

## 5. Start production and verify from another computer

With production settings restored and checked:

```sh
python3 -B scripts/check_deployment.py --env-file .env.production
dc up -d
dc ps
dc logs --tail=100 app https
```

Open `https://YOUR_PUBLIC_IPV4` from another computer. Use ordinary certificate
verification; do not use `curl -k` as a release check.

Verify all of the following and record results before hosting real games:

- HTTP redirects to HTTPS; the certificate is browser-trusted and covers the IP.
- The frontend loads and guest identity receives a Secure, HttpOnly cookie.
- A Standard table works with two separate browser profiles; Commander works
  with four. Import decks, start, move/tap cards, and check private-zone privacy.
- Browser developer tools show a successful `wss://` connection and updates reach
  other players. Writes from the correct origin succeed; unrelated origins fail.
- After `dc restart app`, reconnect using existing cookies and recover the room
  and seat. Repeat a full `dc down` / `dc up -d` cycle without deleting volumes.
- Inspect `docker stats --no-stream` and logs under representative play. Revise
  budgets if needed; no measured capacity guarantee is supplied here.

`dc ps` showing running containers is insufficient: only PostgreSQL has a health
check. There is no dedicated application readiness endpoint in this preparation.
Caddy may return 502 while Java starts; verify actual gameplay before declaring
the release available. Graceful shutdown allows HTTP requests to finish, but
WebSockets disconnect and clients must reconnect during application replacement.

## 6. Back up and rehearse restoration

Back up before upgrades and on a schedule appropriate to acceptable data loss.
From the repository root, create a private custom-format database dump:

```sh
umask 077
mkdir -p backups
backup_path="backups/mtgcmd-$(date -u +%Y%m%dT%H%M%SZ).dump"
dc exec -T db pg_dump -U mtgcmd -d mtgcmd -Fc > "$backup_path"
test -s "$backup_path"
dc exec -T db pg_restore --list < "$backup_path" > /dev/null
```

Check every command's exit status; an interrupted dump is not a backup. Transfer
successful dumps to a protected location outside the VM and define retention.
Dumps contain guest credentials/private game state; restrict access and encrypt
as appropriate. Back up the environment file securely, and preserve Caddy's data
volume for ACME identity and certificates. Named volumes alone are not backups.

For a restore rehearsal, use a fresh project name with no existing database:

```sh
docker compose --env-file .env.production -f compose.production.yaml -p mtgcmd-restore-check up -d db
docker compose --env-file .env.production -f compose.production.yaml -p mtgcmd-restore-check exec -T db pg_isready -U mtgcmd -d mtgcmd
```

Wait for readiness, then load only into this isolated database:

```sh
docker compose --env-file .env.production -f compose.production.yaml -p mtgcmd-restore-check exec -T db pg_restore --exit-on-error --no-owner -U mtgcmd -d mtgcmd < "$backup_path"
docker compose --env-file .env.production -f compose.production.yaml -p mtgcmd-restore-check exec -T db psql -U mtgcmd -d mtgcmd -c '\dt'
docker compose --env-file .env.production -f compose.production.yaml -p mtgcmd-restore-check down
```

Confirm expected room/session data through appropriate read-only queries. A table
listing alone does not verify semantic recovery. Keep this database isolated;
never use the production project for a rehearsal. Reusing the restore project
with old tables will fail rather than overwrite them; use a fresh project name.

## 7. Upgrade, rollback, and monitor

1. Verify the candidate release and create a restorable database backup.
2. Retain the old image and record old configuration. Build/load the new image,
   update `APP_IMAGE`, run preflight, and use `dc up -d app` during maintenance.
3. Review Flyway/application logs and repeat browser/reconnect checks.
4. Roll back to the old image only if its database schema/snapshots are compatible.
   Otherwise plan restoration into a separate database and explicitly authorize
   switching over, accounting for writes since the backup. Do not blindly undo
   migrations or overwrite production data.

Restart policies recover exited containers; they do not restart a hung app just
because a health check fails. Monitor HTTPS availability, app/database failures,
disk space, memory, backup freshness, and certificate expiry externally. Docker
logs rotate at three 10 MiB files per service.

IP certificates last about six days. Keep Caddy storage intact and ports 80/443
reachable. Observe a real renewal and verify the served certificate changes
before marking renewal complete; a successful first issuance is insufficient.
Changing a CA setting does not necessarily replace an already cached certificate,
which is why staging and production use separate volumes.

If the public IP changes, update configuration and obtain a certificate for the
new address. Browser cookies are scoped to the old IP, so players may lose seat
access; arrange stable addressing before real games.

## Operator verification record

Record date, image tag/digest, architecture, proxy version, results, and evidence
for each item. Initial state is pending:

| Check                                                             | Status  |
| ----------------------------------------------------------------- | ------- |
| Image build/start on target architecture                          | Pending |
| Pinned Caddy configuration validation                             | Pending |
| Staging issuance, then trusted production TLS without SNI         | Pending |
| Two/four-player gameplay, privacy, origin/cookies, and WebSockets | Pending |
| Restart recovery and resource observations                        | Pending |
| Off-VM backup and isolated restore                                | Pending |
| Actual automated certificate renewal                              | Pending |
