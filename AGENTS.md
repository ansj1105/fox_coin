# Project Rules

## Backend Query Rule

- Prefer the repository `QueryBuilder` helpers over raw SQL strings when the query fits the existing builder.
- Use typed operators such as `Op.GreaterThan`, `Op.LessThan`, `Op.Equal`, and builder helpers instead of embedding comparison operators inline.
- If the builder is missing a safe primitive needed by the codebase, extend the builder first, then use it from repositories.
- When the same raw SQL fragment appears repeatedly, add a small `QueryBuilder` helper and migrate the touched repositories to it instead of copying more `setCustom(...)`, `appendQueryString(...)`, or raw predicate strings.
- Do not force complex CTE-heavy or expression-heavy queries into the builder when that makes the result less readable; explicitly leave those as raw-query islands and document the reason.

## Backend Import Rule

- Prefer normal Java imports for shared enums, helper types, and builder classes used in a file. Do not reference package-qualified type names inline when a standard import keeps the code clearer.
- In repository code, use imported `Op`, `Sort`, `QueryBuilder`, and local helper methods consistently instead of mixing imported symbols with fully qualified class paths.
- If a file starts needing repeated fully qualified references, stop and clean up the imports in the same change.

## Backend Contract Rule

- Any endpoint addition or response/request shape change must be reflected in `openapi.yaml` in the same task.
- Keep Swagger and generated contract artifacts up to date with the implemented handler, DTO, and repository behavior.
- Do not leave backend behavior changed while OpenAPI or Swagger docs still describe the old contract.

## Backend Verification Rule

- Add or update tests for backend behavior changes. Prefer the narrowest test that proves the contract or regression fix.
- Run compile and targeted tests for the touched area before finishing.
- For data-integrity or accounting changes, verify with concrete queries or fixtures, not code inspection alone.

## Deployment Env Rule

- When a new runtime env var is introduced for Foxya backend behavior, update every production container environment block that must receive it, not just `.env.example` or Java config readers.
- In `docker-compose.prod.yml`, keep `app` and `app2` env passthrough lists in sync for backend runtime vars such as bridge credentials, external API keys, and wallet settings.
- If server-side `git pull` is blocked by missing GitHub credentials, sync only the touched runtime files to `/var/www/fox_coin` and redeploy from the synced tree rather than changing server git auth ad hoc.

## Bridge Architecture Rule

- Treat `foxya -> coin_manage` withdrawal integration as a cross-instance workflow, not a local method call.
- If Foxya stores `coin_manage_withdrawal_id`, check whether completion and failure states are also synchronized back; do not assume request success means lifecycle integration is complete.
- Keep canonical withdrawal lifecycle state in `coin_manage`; Foxya should cache only the fields needed for user display and recovery.
- Redis is acceptable for retry queues, stream delivery, or locks, but not as the source of truth for withdrawal status synchronization.
- Multi-node `app/app2` deployment improves availability, but state convergence still requires an explicit callback or polling contract.
- Do not reuse deposit-scanner credentials for `coin_manage` withdrawal callbacks once a dedicated callback key exists. Temporary fallback wiring is allowed only for rollout compatibility and should be removed after both sides are updated.
- When changing bridge behavior, inspect both sides:
  - request creation in `foxya_coin_service`
  - approval, broadcast, confirm, and failure propagation in `coin_manage`

## Monitoring Routing Rule

- The current public monitoring routes are:
  - Grafana: `https://dev.korion.io.kr/`
  - Prometheus: `https://api.korion.io.kr/`
- `https://api.korion.io.kr/` root is the Prometheus surface in the dedicated monitoring vhost. Use `/graph` for the UI and `/api/v1/...` for API queries.
- Keep `/prometheus/*` only as a compatibility redirect layer. Do not document it as the canonical route.
- Legacy paths on `https://korion.io.kr/6s9ex74204/...` should redirect to the public routes above rather than proxying directly.
- When updating monitoring docs or Nginx, verify both the public route and the localhost upstream:
  - Grafana upstream: `127.0.0.1:3001`
  - Prometheus upstream: `127.0.0.1:9090`

## Commit Rule

- Split unrelated work into separate commits. Keep one commit focused on one change purpose when the diff can be cleanly separated.
- Use a typed commit prefix that matches the change: `fix`, `refactor`, `test`, `docs`, `chore`, `feat`.
- Prefer commit messages in the form `type(scope): summary`.
- If a helper or builder primitive is introduced and then applied to repositories in the same batch, keep that in one `refactor(query): ...` commit.
- If a compile fix or regression test is logically separate from the query-builder cleanup, commit it separately with a matching tag such as `fix(wallet): ...` or `test(wallet): ...`.

## Backend Data Rule

- When display totals and ledger totals can diverge, document which table or aggregation is authoritative and keep the write path consistent with that source.
- For balance-affecting logic, preserve full precision in storage and apply display formatting only at the presentation layer.
- For one-off production reconciliations, keep the operational SQL or batch file in the repo so the applied fix is auditable.

## Backend Repository Rule

- When a repository creates or updates a row and the caller needs the created result immediately, prefer `RETURNING` / `returningColumns(...)` in the same query instead of insert-then-select follow-up reads.
- If post-create verification can be satisfied by the inserted or updated row itself, keep that logic inside the repository return value rather than issuing a second fetch from the service layer.
- Only do a follow-up select after create when `RETURNING` cannot provide the needed data or when the second read is intentionally observing a different source of truth.
