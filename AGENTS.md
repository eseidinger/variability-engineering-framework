# Repository guidelines

Follow the more specific instructions in `apps/prototype/ui/AGENTS.md` when
working on the Angular UI.

## Linting

- UI: run `npm run lint` from `apps/prototype/ui` after changing TypeScript,
  Angular templates, or lint configuration. Use `npm run lint:fix` for automatic
  fixes, then review the diff. CI treats warnings as failures.
- UI lint uses the recommended ESLint, TypeScript, Angular, and template
  accessibility rules. Use strict equality, avoid `any`, remove unused variables,
  use dependency injection through `inject()`, use OnPush components, and prefer
  Angular's built-in template control flow. Prefix intentionally unused bindings
  with `_`. Accessibility lint supplements browser accessibility testing.
- API: run `./mvnw --batch-mode --no-transfer-progress validate` from
  `apps/prototype/api` after changing Java or lint configuration. Checkstyle runs
  automatically during Maven `test`, `package`, and `verify` as well.
- API checks cover production and test sources: use explicit imports, remove
  unused and redundant imports, follow Java naming conventions, avoid tabs and
  empty statements, preserve equals/hashCode consistency, avoid switch
  fall-through, and do not catch `Throwable` or `Error`.
- Fix violations rather than weakening rules. Any necessary suppression must be
  narrow and explain why the rule does not apply. Keep generated files and build
  outputs outside lint scope. Prettier remains the UI formatter; ESLint handles
  code quality. Run relevant tests and builds in addition to lint checks.

## Logging

- Use the application logger (`org.jboss.logging.Logger` in the API), never
  `System.out`, `System.err`, or `printStackTrace()`. UI console output is limited
  to actionable warnings and errors.
- Log operational failures when a boundary handles an exception and returns a
  generic 5xx response or marks background work failed. Do not silently swallow
  dependency, TLS, authentication, configuration, or persistence failures.
- Log once at the handling boundary. Include the operation, stable error code,
  exception/cause types, upstream HTTP status when available, and an existing
  trace or run ID when safe. Preserve interruption semantics.
- Use WARN for recoverable dependency failures and ERROR for unexpected internal
  failures. Do not log expected validation failures as server errors.
- Never log credentials, tokens, API keys, cookies, authorization headers, raw
  request/response bodies, AI prompts/completions, or sensitive personal data.
  Exception messages and stack traces can contain these values; log sanitized
  cause summaries unless the full exception is known to be safe.
- Keep client-facing errors concise and stable; diagnostic details belong in
  server logs. Test diagnostic sanitization when changing error handling.
