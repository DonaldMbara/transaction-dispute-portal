# Transaction Dispute Portal frontend

Angular 20.3 standalone scaffold with concise component filenames. Node 22.23.3 is pinned in .nvmrc; install/select it with your preferred Node version manager. System Node 25 is outside this workspace's supported range. See the root README for the full foundation setup.

```bash
npm ci
npm ls
npm run build
npm run test:ci
```

Tests use the CLI Karma/Jasmine builder and zone.js/testing. Chrome/Chromium must be installed; export CHROME_BIN to its executable if automatic discovery fails. No browser sandbox-disabling flags are required by the configuration. npm test is the interactive watch command; test:ci runs once and returns a nonzero status on failure.

npm start serves on localhost:4200. The root hosts a router outlet. Welcome (/) and Login (/login) are separate inert pages; Customer (/customer) and Agent (/agent) render inside layouts/dashboard-layout. Unknown routes return to welcome. Future requests use relative /api/v1 URLs; npm start activates the /api/** proxy to localhost:8080. Restart the dev server after proxy changes. Global styles.scss composes token/reset/typography modules using @use. Nunito Sans is a font preference with system fallbacks, not an installed font asset. No authentication or business behavior exists.

Full npm audit currently reports six high-severity development-tooling entries through the Karma/braces chain (GHSA-vfj7-8cjw-p6xm); npm's proposed fixes downgrade the approved tooling incompatibly. Production-only audit reports zero advisories. The issue is recorded for review; no force fix or runner migration has been applied.

Current authentication architecture: Spring Security with Spring Session JDBC and PostgreSQL; opaque HttpOnly session cookie, Secure/SameSite=Lax in production, 30-minute server-enforced idle timeout, CSRF for unsafe requests including login/logout, backend CUSTOMER/AGENT authorization and admin-controlled provisioning with no public registration. No JWT/refresh/localStorage authentication tokens or bearer-token interceptor. Same-origin /api/v1 remains unchanged; valid-session reload restoration is permitted. Endpoint/payload/cookie/CSRF/provisioning details await authentication design. Historical foundation specifications and code retain their original baseline; this documentation change does not implement sessions or alter migrations. See [session architecture decision](../docs/design/session-architecture-decision.md) for the supersession boundary.
