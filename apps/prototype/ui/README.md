# Variability Engineering Framework UI

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.2.24.

## Development server

To start a local development server, run:

```bash
npm ci
npm start
```

Once the server is running, open `http://localhost:4200/`. The development server
proxies `/api` to the API at `http://127.0.0.1:8080`, so start the API first.

The API's energy routes require identity headers supplied by the hosting
platform. For a local-only UI smoke test, start the UI with
`npm run start:local-energy` and open `http://127.0.0.1:4200/`. This opt-in
development proxy sends the fixture principal used by the API tests on `/api`
requests. It binds the UI to loopback and is not part of the production build.
Restart the UI after changing proxy configuration. A successful
`GET /api/v1/capabilities` through this proxy returns HTTP 200.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
npm exec ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
npm exec ng generate --help
```

## Building

Run `npm run lint` to check TypeScript and Angular templates, including template
accessibility. CI rejects lint warnings. `npm run lint:fix` applies available
automatic fixes. Prettier remains configured separately for formatting.

To build the project run:

```bash
npm run build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
npm test
```

## End-to-end tests

No browser end-to-end test target is configured yet. The repository-level Compose
verifier checks the packaged shell and API, but it does not render the Angular UI.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
