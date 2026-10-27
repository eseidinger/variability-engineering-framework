import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFileSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { dirname, extname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { chromium } from 'playwright';

// Synthetic HTTP responses test the real production browser bundle, not the backend or live AI.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const output = resolve(root, 'dist/variability-engineering-ui/browser');
const require = createRequire(import.meta.url);
const request = {
  schemaVersion: 'energy-optimization-request/v1',
  dataset: { datasetId: 'energy-reference-wasserburg-am-inn-2024', version: '1.0.0', schemaRef: 'urn:portfolio:energy-configuration-input:v1' },
  site: { regionCode: 'DE-BY-09187182', usableRoofAreaM2: 35, annualDemandKWh: 4500, existingComponentIds: [] },
  constraints: { maxInitialInvestmentEUR: 18000, minSelfSufficiency: 0.55 },
  objective: 'MIN_ANNUALIZED_COST', assumptions: { analysisYears: 20, discountRate: 0.04 },
};
const checks = [];
rmSync(resolve(root, 'test-results/energy-browser.json'), { force: true });
const server = createServer((req, res) => {
  try {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    const filename = pathname === '/' || pathname === '/energy' ? 'index.html' : pathname.slice(1);
    const file = resolve(output, filename);
    if (!file.startsWith(output + '/')) throw new Error('Invalid asset path');
    res.setHeader('Content-Type', ({ '.js': 'application/javascript', '.css': 'text/css', '.html': 'text/html' })[extname(file)] ?? 'application/octet-stream');
    res.end(readFileSync(file));
  } catch { res.statusCode = 404; res.end(); }
});
await new Promise(done => server.listen(0, '127.0.0.1', done));
let browser;
try {
  browser = await chromium.launch();
  for (const width of [1280, 390]) {
    const page = await browser.newPage({ viewport: { width, height: 900 } });
    page.setDefaultTimeout(10000);
    let deny = 0;
    let clarification = false;
    let providerDown = false;
    let expireOnPoll = false;
    let created = 0;
    let current;
    let submitted;
    await page.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname;
      if (deny) return route.fulfill({ status: deny, json: { message: 'Denied' } });
      if (path.endsWith('/capabilities')) return route.fulfill({ json: { maxCandidateEvaluations: 100000, runTimeoutSeconds: 300 } });
      if (path.endsWith('/request-proposals')) return route.fulfill({ json: {
        schemaVersion: 'energy-ai-request-proposal/v1', status: clarification ? 'NEEDS_CLARIFICATION' : 'READY_FOR_REVIEW',
        requiresConfirmation: true, request: clarification ? null : request,
        clarificationQuestions: clarification ? ['What is your annual demand?'] : [], assumptionsToReview: ['20 years, 4% discount rate.'],
      } });
      if (path.endsWith('/optimization-runs')) {
        created++;
        submitted = route.request().postDataJSON();
        assert.ok(route.request().headers()['idempotency-key']);
        current = { runId: 'run-browser-' + created, status: submitted.constraints.maxInitialInvestmentEUR === 0 ? 'INFEASIBLE' : 'SUCCEEDED',
          createdAt: '2026-10-27T10:00:00Z', completedAt: '2026-10-27T10:00:01Z', dataset: { ...request.dataset, manifestSha256: 'a'.repeat(64) },
          modelVersion: 'energy-model/1.0.0', simulationVersion: 'energy-simulation/1.0.0', optimizerVersion: 'energy-optimizer/1.0.0',
          optimalityStatus: 'PROVEN_OPTIMAL', recommendedConfigurationId: 'pv-browser-inverter-battery-none', evaluatedCandidateCount: 1, feasibleCandidateCount: 1, warnings: [] };
        return route.fulfill({ status: 202, json: { ...current, status: 'QUEUED' } });
      }
      if (path.endsWith('/candidates')) return route.fulfill({ json: { items: [], page: 0, pageSize: 20, total: 0 } });
      if (path.endsWith('/explanation')) return providerDown
        ? route.fulfill({ status: 503, json: { code: 'ai_unavailable' } })
        : route.fulfill({ json: { schemaVersion: 'energy-ai-explanation/v1', runId: current.runId,
          claims: [{ text: 'Synthetic browser explanation <script>alert(1)</script>', category: 'CALCULATED_OUTPUT',
            citations: [{ source: 'result', pointer: '/runId', value: current.runId }] }], missingEvidence: ['Measured building data is unavailable.'] } });
      if (expireOnPoll) return route.fulfill({ status: 401, json: { code: 'session_expired' } });
      return route.fulfill({ json: current });
    });
    async function check(name) {
      await page.addScriptTag({ path: require.resolve('axe-core/axe.min.js') });
      const violations = await page.evaluate(async () => (await window.axe.run(document)).violations.map(v => ({ id: v.id, targets: v.nodes.map(n => n.target) })));
      assert.deepEqual(violations, [], name);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, 'Horizontal page overflow');
      checks.push({ name, width, result: 'PASS' });
    }
    const url = 'http://127.0.0.1:' + server.address().port + '/energy';
    await page.goto(url);
    await page.getByRole('heading', { name: 'Compare solar and storage options' }).waitFor();
    await check('initial form');
    await page.getByLabel('Annual demand (kWh)').fill('-1');
    await page.getByRole('button', { name: 'Evaluate configurations', exact: true }).click();
    assert.equal(await page.getByLabel('Annual demand (kWh)').evaluate(e => e === document.activeElement), true);
    await page.locator('input[formcontrolname="annualDemand"][aria-invalid="true"]').waitFor();
    assert.equal(created, 0);
    await check('invalid input focus');
    await page.getByLabel('Annual demand (kWh)').fill('4500');
    clarification = true;
    await page.locator('#proposal-description').fill('I need solar power.');
    await page.getByRole('button', { name: 'Prepare request', exact: true }).click();
    await page.getByText('What is your annual demand?', { exact: true }).waitFor();
    assert.equal(created, 0);
    await check('clarification');
    clarification = false;
    await page.getByRole('button', { name: 'Prepare request', exact: true }).click();
    const editor = page.getByLabel('Energy request JSON');
    await editor.waitFor();
    const confirm = page.getByRole('checkbox');
    await confirm.focus();
    await page.keyboard.press('Space');
    await editor.fill((await editor.inputValue()).replace('18000', '15000'));
    assert.equal(await confirm.isChecked(), false);
    await page.locator('section[aria-labelledby="proposal-title"] form:last-child button:disabled').waitFor();
    assert.equal(created, 0);
    await check('confirmation invalidated by edit');
    await confirm.focus();
    await page.keyboard.press('Space');
    await page.waitForFunction(() => !document.querySelector('section[aria-labelledby="proposal-title"] form:last-child button').disabled);
    await page.keyboard.press('Tab');
    assert.equal(await page.getByRole('button', { name: 'Confirm and evaluate' }).evaluate(e => e === document.activeElement), true);
    await page.keyboard.press('Enter');
    await page.getByText('Recommended configuration', { exact: true }).waitFor();
    assert.equal(created, 1);
    assert.equal(submitted.constraints.maxInitialInvestmentEUR, 15000);
    await check('keyboard confirmed result');
    const evidence = page.getByText('Dataset and calculation evidence', { exact: true });
    await evidence.focus();
    await page.keyboard.press('Enter');
    await check('expanded deterministic evidence');
    providerDown = true;
    await page.getByRole('button', { name: 'Explain this result with AI' }).click();
    await page.getByRole('alert').filter({ hasText: 'AI explanation unavailable' }).waitFor();
    assert.equal(await page.getByText('Recommended configuration', { exact: true }).isVisible(), true);
    await check('provider outage preserves result');
    providerDown = false;
    await page.getByRole('button', { name: 'Explain this result with AI' }).click();
    await page.getByText('Synthetic browser explanation <script>alert(1)</script>', { exact: true }).waitFor();
    await page.getByText('Evidence references for this claim (CALCULATED_OUTPUT)', { exact: true }).click();
    await check('explanation references and escaped metadata');
    await page.getByLabel('Maximum initial investment (EUR)').fill('0');
    await page.getByRole('button', { name: 'Evaluate configurations', exact: true }).click();
    await page.getByText('No configuration met both constraints.', { exact: false }).waitFor();
    await check('infeasible result');
    expireOnPoll = true;
    await page.getByLabel('Maximum initial investment (EUR)').fill('18000');
    await page.getByRole('button', { name: 'Evaluate configurations', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: 'Sign in again' }).waitFor();
    await check('session expiry during polling');
    expireOnPoll = false;
    for (deny of [401, 403]) {
      await page.reload();
      await page.getByRole('alert').filter({ hasText: 'Sign in again' }).waitFor();
      const before = created;
      await page.getByRole('button', { name: 'Evaluate configurations', exact: true }).click();
      await page.getByRole('alert').filter({ hasText: 'Sign in again' }).waitFor();
      assert.equal(created, before);
      await check('session/access denial ' + deny);
    }
    await page.close();
  }
  mkdirSync(resolve(root, 'test-results'), { recursive: true });
  writeFileSync(resolve(root, 'test-results/energy-browser.json'), JSON.stringify({ environment: 'local-production-bundle-with-mocked-http', completed: true, checks }, null, 2) + '\n');
  console.log(`PASS: ${checks.length} browser/accessibility checks across desktop and mobile widths.`);
} finally {
  if (browser) await browser.close();
  await new Promise(done => server.close(done));
}
