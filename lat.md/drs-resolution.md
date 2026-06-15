# DRSHub Lore and Techniques

## DRS Passport Auth Flow

### What it is

RAS passport auth is an alternative to Terra bearer token auth for accessing controlled-access data (NIH DbGaP datasets, BDC, etc.). Instead of the user's Terra token, TDR accepts a GA4GH passport JWT — a signed credential from NIH's Research Auth Service (RAS) that encodes the user's dbGaP permissions.

The user links their NIH account in Terra via ECM, which stores the passport. DRSHub brokers the resolution using that stored passport.

### The flow

```
Terra UI / Cromwell / WDS
    → DRSHub  POST /api/v4/drs/resolve
         → ECM  GET /api/oidc/v1/{provider}/passport   (fetch stored RAS passport JWT)
         → TDR  POST /ga4gh/drs/v1/objects/{id}        (fetch object metadata with passport)
         → TDR  POST /ga4gh/drs/v1/objects/{id}/access/{accessId}  (get signed access URL)
```

**Key distinction from normal auth:**
- Normal: `GET /objects/{id}` with `Authorization: Bearer <terra-token>`
- Passport: `POST /objects/{id}` with body `{"passports": ["<passport_jwt>"]}`

### Where it's configured

Each DRS provider in `DrsHubConfig` has `accessMethodConfigs` with an `auth` enum (`DrsAuthEnum`):
- `current_request` — use the user's Terra bearer token
- `provider_access_token` — use a fence token fetched from ECM
- `passport` — use the RAS passport fetched from ECM

If the provider implements `OPTIONS /{objectId}`, DRSHub defers to its response (`BEARERAUTH` / `PASSPORTAUTH` / `NONE`). Otherwise it falls back to the config. See `AuthService.buildAuthorizations()`.

### ECM passport caching

`AuthService` caches the passport (keyed on bearer token) for 1 minute. This prevents hammering ECM during batch analyses where many DRS URIs are resolved in parallel. If a user's passport expires mid-batch, the cached stale value will be used until the cache entry expires.

### The requester-pays / bearer token gap (incident CTM-473)

**Problem:** When `googleProject` (requester pays) is specified, TDR needs to sign the URL in the context of the user's billing project. TDR's `postAccessURL` endpoint accepts an `x-user-project` header for this, but the GA4GH-generated client (`drsApi.postAccessURL(...)`) doesn't support it.

**What was broken:** DRSHub was calling the GA4GH client for all passport-auth access URL requests. For requester-pays requests, TDR couldn't attribute the billing, and the signed URL failed.

**The fix** (`DrsResolutionService.getAccessURL`, PR #242): When `googleProject != null`, DRSHub switches to `callDataRepoPostAccessUrl()`, which uses the TDR-specific `DataRepositoryServiceApi` client. That client:
1. Sets the user's bearer token as the API access token (so TDR can validate billing project access)
2. Passes passports in the request body
3. Passes `googleProject` as the `x-user-project` header

```java
// passport-only path (no requester pays)
drsApi.postAccessURL(Map.of("passports", passports), objectId, accessId)

// passport + requester pays path
callDataRepoPostAccessUrl(bearerToken.getToken(), passports, objectId, accessId, googleProject)
//  → tdrApiFactory.getApi(accessToken).postAccessURL(body, objectId, accessId, xUserProject)
```

### Debugging tips

**User has no passport:** ECM returns 404 on `GET /passport` → `AuthService.fetchPassports()` returns `Optional.empty()` → passport path is skipped, DRSHub falls back to bearer auth.

**Passport auth failure logs:** Look for `"failed via passport, using bearer token"` in DRSHub logs. `fetchObjectInfo()` catches any exception from `postObject` (passport path) and falls back to `getObject` (bearer path).

**RAS issuer misconfiguration:** The RAS issuer URL is configured in terra-helmfile. If it points to the wrong environment, passport validation in TDR will fail silently from DRSHub's perspective — TDR rejects the passport but the error may not surface clearly.

**ECM linkage check:** To verify a user has a linked RAS passport: `GET /api/oidc/v1/ras/passport` using the user's bearer token. 404 = not linked.
