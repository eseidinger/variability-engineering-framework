import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { platformCsrfInterceptor } from '../platform-csrf.interceptor';
import { EnergyWorkflowComponent } from './energy-workflow';

describe('EnergyWorkflowComponent', () => {
  const readyProposal = {
    schemaVersion: 'energy-ai-request-proposal/v1', status: 'READY_FOR_REVIEW', requiresConfirmation: true,
    request: {
      schemaVersion: 'energy-optimization-request/v1',
      dataset: { datasetId: 'energy-reference-wasserburg-am-inn-2024', version: '1.0.0',
        schemaRef: 'urn:portfolio:energy-configuration-input:v1' },
      site: { regionCode: 'DE-BY-09187182', usableRoofAreaM2: 35, annualDemandKWh: 4500,
        existingComponentIds: [] },
      constraints: { maxInitialInvestmentEUR: 18000, minSelfSufficiency: 0.55 },
      objective: 'MIN_ANNUALIZED_COST', assumptions: { analysisYears: 20, discountRate: 0.04 },
    }, clarificationQuestions: [], assumptionsToReview: ['20-year analysis and 4% discount rate'],
  };
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnergyWorkflowComponent],
      providers: [provideHttpClient(withInterceptors([platformCsrfInterceptor])), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    vi.useRealTimers();
  });

  it('submits the pinned request and displays completed evidence', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(EnergyWorkflowComponent);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/capabilities').flush({ maxModelsPerType: 20, maxPvModules: 100,
      inverterCount: 1, maxBatteries: 4, maxCandidateEvaluations: 100000, runTimeoutSeconds: 300, maxPageSize: 100 });
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector('section[aria-labelledby="inputs-title"] form')
      ?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const create = http.expectOne('/api/v1/optimization-runs');
    expect(create.request.body.dataset.version).toBe('1.0.0');
    expect(create.request.body.site.annualDemandKWh).toBe(4500);
    expect(create.request.headers.has('Idempotency-Key')).toBe(true);
    expect(create.request.headers.has('X-Platform-Principal-Group-Ids')).toBe(false);
    create.flush({ runId: 'run-example-0001', status: 'QUEUED', createdAt: '2026-10-13T12:00:00Z' });
    vi.advanceTimersByTime(2000);
    http.expectOne('/api/v1/optimization-runs/run-example-0001').flush({
      runId: 'run-example-0001', status: 'SUCCEEDED', createdAt: '2026-10-13T12:00:00Z',
      completedAt: '2026-10-13T12:00:01Z', recommendedConfigurationId: 'pv-demo-q10-inv-demo-battery-none',
      feasibleCandidateCount: 8, evaluatedCandidateCount: 120,
      dataset: { datasetId: 'energy-reference-wasserburg-am-inn-2024', version: '1.0.0',
        schemaRef: 'urn:portfolio:energy-configuration-input:v1', manifestSha256: 'a'.repeat(64) },
      modelVersion: 'energy-model/1.0.0', simulationVersion: 'energy-simulation/1.0.0',
      optimizerVersion: 'energy-optimizer/1.0.0', warnings: [],
    });
    http.expectOne(request => request.url.endsWith('/candidates')).flush({ items: [], page: 0, pageSize: 20, total: 0 });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('pv-demo-q10-inv-demo-battery-none');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Manifest SHA-256');
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      'section[aria-labelledby="result-title"] button')?.click();
    http.expectOne('/api/v1/ai/optimization-runs/run-example-0001/explanation').flush({
      schemaVersion: 'energy-ai-explanation/v1', runId: 'run-example-0001',
      claims: [{ text: 'The recorded run succeeded.', category: 'CALCULATED_OUTPUT',
        citations: [{ source: 'result', pointer: '/runId', value: 'run-example-0001' }] }],
      missingEvidence: ['Measured consumption is unavailable.'],
    });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('The recorded run succeeded.');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('result/runId');
    fixture.destroy();
  });

  it('asks for clarification without creating a run', () => {
    const fixture = TestBed.createComponent(EnergyWorkflowComponent);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/capabilities').flush({});
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const description = root.querySelector('#proposal-description') as HTMLTextAreaElement;
    description.value = 'I need a solar system';
    description.dispatchEvent(new Event('input'));
    root.querySelector('section[aria-labelledby="proposal-title"] form')
      ?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    http.expectOne('/api/v1/ai/request-proposals').flush({
      schemaVersion: 'energy-ai-request-proposal/v1', status: 'NEEDS_CLARIFICATION',
      requiresConfirmation: true, request: null,
      clarificationQuestions: ['What is the usable roof area?'], assumptionsToReview: [],
    });
    fixture.detectChanges();
    expect(root.textContent).toContain('What is the usable roof area?');
    expect(root.querySelector('#proposal-request')).toBeNull();
    http.expectNone('/api/v1/optimization-runs');
    fixture.destroy();
  });

  it('requires confirmation of the current proposal after editing', () => {
    const fixture = TestBed.createComponent(EnergyWorkflowComponent);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/capabilities').flush({});
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const description = root.querySelector('#proposal-description') as HTMLTextAreaElement;
    description.value = '35 m2 roof, 4500 kWh demand, 18000 EUR budget, 0.55 self-sufficiency, dataset 1.0.0';
    description.dispatchEvent(new Event('input'));
    root.querySelector('section[aria-labelledby="proposal-title"] form')
      ?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    http.expectOne('/api/v1/ai/request-proposals').flush(readyProposal);
    fixture.detectChanges();
    const request = root.querySelector('#proposal-request') as HTMLTextAreaElement;
    const checkbox = root.querySelector('.confirmation input') as HTMLInputElement;
    expect(request.value).toContain('annualDemandKWh');
    expect(root.textContent).toContain('20-year analysis and 4% discount rate');
    checkbox.click();
    fixture.detectChanges();
    expect(checkbox.checked).toBe(true);
    request.value = request.value.replace('18000', '20000');
    request.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(checkbox.checked).toBe(false);
    expect((root.querySelectorAll('section[aria-labelledby="proposal-title"] form')[1]
      .querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(true);
    http.expectNone('/api/v1/optimization-runs');
    checkbox.click();
    root.querySelectorAll('section[aria-labelledby="proposal-title"] form')[1]
      ?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const create = http.expectOne('/api/v1/optimization-runs');
    expect(create.request.body.constraints.maxInitialInvestmentEUR).toBe(20000);
    create.flush({ runId: 'run-example-0002', status: 'QUEUED', createdAt: '2026-10-13T12:00:00Z' });
    fixture.destroy();
  });
});
