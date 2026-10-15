# ADR-0009: Public-IP Docker deployment

- Status: Accepted
- Date: 2026-10-15
- Related specification: [Public-IP Docker deployment](../../spec/public-ip-docker-deployment.md)

## Context

The first deployment is one Oracle Cloud Linux VM with a public IP and no domain.
The existing application image serves both Java and React. WebSocket connection
registrations are process-local. IP certificates are short-lived and need reliable
automated renewal; local development defaults must not become public defaults.

## Decision

Use standalone production Compose with one app instance, PostgreSQL, and pinned
Caddy 2.11.7. Caddy owns certificate issuance, persistence, renewal, and serving,
using an explicit Let's Encrypt shortlived ACME issuer for the public IPv4 address.
Set default SNI to that address for browsers which omit SNI for IP literals.
Only Caddy publishes ports. Require an explicitly versioned application image.
Do not deploy until the pinned Caddy configuration and cloud checks are verified.

## Alternatives considered

### Certbot plus Caddy

- Benefits: Explicit IP-certificate tooling and separate renewal testing.
- Drawbacks: Additional process/scheduler, shared private keys, and proxy reload coordination.
- Reason not selected: Caddy can own the complete lifecycle; retain this fallback
  if pinned-version verification reveals an incompatibility.

### Obtain a domain first

- Benefits: Stable user-facing name and conventional certificate configuration.
- Drawbacks: Requires DNS/domain setup the user does not currently have.
- Reason not selected: Public-IP access is the approved deployment requirement.

### HTTP or a private certificate authority

- Benefits: Simpler initial setup.
- Drawbacks: HTTP exposes guest credentials; private trust requires player device setup.
- Reason not selected: Public browser access should preserve transport security.

## Rationale

Retaining Caddy avoids a second certificate lifecycle while preserving same-origin
HTTP/WebSocket routing. Separate Compose configuration prevents accidental merging
of local port mappings. One app instance preserves existing broadcast behavior.

## Consequences

### Benefits

- No purchased domain or per-player certificate installation.
- Existing application packaging and persistence remain intact.

### Drawbacks and risks

- Six-day certificates make renewal monitoring essential.
- Fixed IP access is less portable; changing IP can strand browser guest cookies.
- Version tags need ongoing review; this does not establish high availability.

### Operational impact

Validate staging issuance before production, preserve Caddy storage, monitor TLS
expiry, back up PostgreSQL outside the VM, and schedule maintenance for updates.
The operator supplies architecture/resource settings and authorizes deployment.

## Follow-up actions

- [ ] Verify the pinned Caddy binary and cloud TLS/renewal checks.
- [ ] Rehearse database restoration before hosting real games.

## References

- [Let's Encrypt IP certificates](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability)
- [Caddy ACME configuration](https://caddyserver.com/docs/caddyfile/directives/tls)
- [Caddy default SNI](https://caddyserver.com/docs/caddyfile/options#default-sni)
- [Caddy 2.11.7 release](https://github.com/caddyserver/caddy/releases/tag/v2.11.7)
