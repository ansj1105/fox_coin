# Project Rules

## Backend Query Rule

- Prefer the repository `QueryBuilder` helpers over raw SQL strings when the query fits the existing builder.
- Use typed operators such as `Op.GreaterThan`, `Op.LessThan`, `Op.Equal`, and builder helpers instead of embedding comparison operators inline.
- If the builder is missing a safe primitive needed by the codebase, extend the builder first, then use it from repositories.

## Backend Contract Rule

- Any endpoint addition or response/request shape change must be reflected in `openapi.yaml` in the same task.
- Keep Swagger and generated contract artifacts up to date with the implemented handler, DTO, and repository behavior.
- Do not leave backend behavior changed while OpenAPI or Swagger docs still describe the old contract.

## Backend Verification Rule

- Add or update tests for backend behavior changes. Prefer the narrowest test that proves the contract or regression fix.
- Run compile and targeted tests for the touched area before finishing.
- For data-integrity or accounting changes, verify with concrete queries or fixtures, not code inspection alone.

## Backend Data Rule

- When display totals and ledger totals can diverge, document which table or aggregation is authoritative and keep the write path consistent with that source.
- For balance-affecting logic, preserve full precision in storage and apply display formatting only at the presentation layer.
- For one-off production reconciliations, keep the operational SQL or batch file in the repo so the applied fix is auditable.
