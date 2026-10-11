# Angular core authentication

`auth/` owns typed AuthService orchestration, signal session/CSRF state, functional interceptors and route guards. Startup awaits one bootstrap then backend identity. Same-origin `/api/v1` uses browser-managed HttpOnly SESSION cookies and in-memory masked X-CSRF-TOKEN, with no bearer header or browser authentication storage. Backend authorization remains authoritative.

401 clears private state; 403/503 remain distinct. Generations discard stale responses. Login/logout transitions refresh CSRF separately; confirmed transitions survive refresh failure and offer explicit token retry. Transport-ambiguous sign-out hides protected content and requires deliberate retry after bootstrap. Never automatically replay unsafe requests or poll identity as a keepalive. See [operational/session runbook](../../../../docs/operations/authentication.md). Historical foundation specifications remain unchanged.
