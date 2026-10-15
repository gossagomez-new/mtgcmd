# Public-IP Docker deployment preparation

## Metadata

- Status: In progress
- Priority: High
- Priority rationale: Prepare a reproducible single-host deployment before exposing the app.
- Depends on: [Build automation](build-automation.md), [Commander tabletop](commander-tabletop.md)

## Summary

Prepare an Oracle Cloud Linux VM deployment accessed through its public IPv4
address, without a domain. Deliver configuration, offline preflight checks, and
an operator runbook. Actual deployment requires a separate operator action.

## Goals

- Run one Java/React image, PostgreSQL, and Caddy through standalone Compose.
- Require public-IP HTTPS and secure cookies; publish only proxy ports.
- Preserve database and certificate data across container replacement.
- Document build, release, recovery, and cloud verification commands.

## Non-goals

- Provisioning cloud resources, opening firewalls, obtaining certificates, or deploying.
- Multiple application replicas, new API endpoints, CI registry integration, or secret-manager integration.
- IPv6-only hosting or a change to game/session behavior.

## Background

The existing Dockerfile embeds React and runs Java as a non-root user. Local
Compose publishes loopback app/database ports and allows HTTP defaults. The
existing Caddy configuration targets domains. GameSocket retains connection
registrations in process, so this preparation retains one app instance.

The pre-design audit identified required production configuration separation
and explicit IP certificate policy. Application readiness endpoints and a CI
release pipeline are follow-up work, not silently added to this scope.

## Requirements

### Functional requirements

1. A standalone production Compose file requires an image, public IPv4, ACME
   contact email, and database password. It must not inherit local port mappings.
2. Derive the exact HTTPS origin from that IP and enable secure cookies.
3. Configure a pinned Caddy release with an explicit Let's Encrypt short-lived
   ACME issuer, automatic renewal, and no-SNI certificate selection.
4. Persist PostgreSQL and Caddy data in named volumes, separate from local Compose.
5. Bound logs and container memory; allow resource tuning for the actual VM.
6. Provide a preflight CLI which resolves Compose without accessing the daemon,
   rejects invalid/non-public IPv4 and unversioned/latest app images, and does
   not print resolved credentials. Staging certificates require an explicit flag.
7. Document firewall/network preparation, architecture selection, local image
   transfer, certificate staging, release updates, backup/restore rehearsal,
   rollback constraints, and browser verification.

### Non-functional requirements

- No credential files or database backups in Git or Docker build context.
- No certificate issuance or deployment by validation commands.
- Keep local Docker configuration and Java/React behavior unchanged.

## Contracts and data

Inputs are an operator-owned environment file and the production Compose file.
The preflight CLI returns zero for valid resolved configuration and nonzero for
invalid input/tool failure. It prints a generic success or actionable safe error,
never the resolved Compose document. Environment overrides follow Compose rules.

## Technical constraints

Use existing Docker packaging. Build/tests precede release; the Dockerfile itself
skips Java tests. Caddy support must be verified using the pinned binary before
deployment. No cloud credentials or public IP are assumed available here.

## Invariants

- Only ports 80/TCP and 443/TCP are published by production Compose.
- App origin and proxy IP agree; secure cookies cannot be disabled by local defaults.
- One app instance; database owns durable room/session state.
- No automatic deletion of volumes, backups, or prior images.

## Edge cases and failures

- Missing/blank settings, malformed/private IP, latest image, missing Docker CLI,
  invalid Compose, and untrusted staging CA fail preflight clearly.
- Blocked ports or ACME outage may prevent issuance/renewal; monitor certificate
  expiry and proxy logs. Never fall back to insecure HTTP gameplay.
- An IP change changes cookie scope/origin and requires a new certificate;
  players may lose their previous guest identity.
- Database health does not establish application readiness; verify through HTTPS.

## Migration and compatibility

Local Compose is unchanged. Production uses project name mtgcmd-production and
separate volumes. Changing project name creates different volume names. Flyway
runs on application startup; rollback of an image may require restoring a
compatible database backup. Never restore over the live database as a rehearsal.

## Acceptance criteria

- [x] Automated configuration/preflight tests pass, including invalid inputs.
- [x] Production secrets/backups are excluded from Git and Docker context.
- [ ] Pinned Caddy validates the configuration in the operator environment.
- [ ] On the VM, trusted HTTPS, WebSockets, cookies/origin, and both table formats work.
- [ ] Restart persistence, certificate renewal, and isolated backup restoration are verified.

## Verification

User confirmed **comprehensive coverage**: required settings, invalid input,
private ports, origin/cookies, resource/persistence/log configuration, local
compatibility, proxy/WebSockets, restart recovery, renewal, and backup restoration.
Automated tests exercise resolved Compose and the preflight CLI using Python's
standard library. Cloud/container checks are operator-run and remain pending
until recorded; configuration parsing is not runtime verification.

Executed locally: 17 configuration/preflight tests passed using Docker Compose
5.6.0. The initial run failed because the production file and CLI were absent;
the existing local Compose check passed. After implementation all cases passed,
including tool failures, secret-safe output, configuration drift, resource
overrides, and staging safeguards. Container/cloud checks remain pending because
the agent cannot access the Docker socket, as agreed with the user.

## Architecture decisions

[ADR-0009](../docs/adr/0009-public-ip-docker-deployment.md) records deployment
and certificate ownership. Its creation was explicitly approved by the user.

## Open questions

- Operator: VM architecture, RAM, OS distribution, public IP, and release image.
- Operator: external backup destination and retention policy before real games.
