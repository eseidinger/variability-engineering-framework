import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { platformCsrfInterceptor } from '../platform-csrf.interceptor';
import { EnergyWorkflowComponent } from './energy-workflow';

describe('EnergyWorkflowComponent', () => {
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
    (fixture.nativeElement as HTMLElement).querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
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
    fixture.destroy();
  });
});
