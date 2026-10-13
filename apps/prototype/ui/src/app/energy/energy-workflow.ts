import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ReactiveFormsModule, NonNullableFormBuilder, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EnergyApiService, EnergyRequest, EnergyRun, CandidatePage, Capabilities } from './energy-api.service';

@Component({
  selector: 'app-energy-workflow',
  imports: [ReactiveFormsModule],
  templateUrl: './energy-workflow.html',
  styleUrl: './energy-workflow.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnergyWorkflowComponent {
  private readonly api = inject(EnergyApiService);
  private readonly builder = inject(NonNullableFormBuilder);
  private readonly destroy = inject(DestroyRef);
  private timer: ReturnType<typeof setTimeout> | undefined;
  private requestFingerprint: string | null = null;
  private idempotencyKey: string | null = null;
  private attempts = 0;

  protected readonly capabilities = signal<Capabilities | null>(null);
  protected readonly run = signal<EnergyRun | null>(null);
  protected readonly page = signal<CandidatePage | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly statusMessage = signal('');

  protected readonly form = this.builder.group({
    datasetVersion: this.builder.control('1.0.0', [Validators.required, Validators.pattern(/^\d+\.\d+\.\d+$/)]),
    roofArea: this.builder.control(35, [Validators.required, Validators.min(0)]),
    annualDemand: this.builder.control(4500, [Validators.required, Validators.min(0.001)]),
    budget: this.builder.control(18000, [Validators.required, Validators.min(0)]),
    selfSufficiency: this.builder.control(0.55, [Validators.required, Validators.min(0), Validators.max(1)]),
    existingComponentIds: this.builder.control(''),
  });

  constructor() {
    this.destroy.onDestroy(() => this.clearTimer());
    this.api.capabilities().pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: value => this.capabilities.set(value),
      error: error => this.error.set(this.errorMessage(error)),
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Check the highlighted inputs before starting a run.');
      return;
    }
    this.clearTimer();
    this.error.set(null);
    this.page.set(null);
    this.run.set(null);
    const value = this.form.getRawValue();
    const request: EnergyRequest = {
      schemaVersion: 'energy-optimization-request/v1',
      dataset: { datasetId: 'energy-reference-wasserburg-am-inn-2024', version: value.datasetVersion,
        schemaRef: 'urn:portfolio:energy-configuration-input:v1' },
      site: { regionCode: 'DE-BY-09187182', usableRoofAreaM2: value.roofArea,
        annualDemandKWh: value.annualDemand, existingComponentIds: value.existingComponentIds
          .split(',').map(id => id.trim()).filter(Boolean) },
      constraints: { maxInitialInvestmentEUR: value.budget, minSelfSufficiency: value.selfSufficiency },
      objective: 'MIN_ANNUALIZED_COST',
      assumptions: { analysisYears: 20, discountRate: 0.04 },
    };
    const fingerprint = JSON.stringify(request);
    if (fingerprint !== this.requestFingerprint) {
      this.requestFingerprint = fingerprint;
      this.idempotencyKey = crypto.randomUUID();
    }
    this.busy.set(true);
    this.statusMessage.set('Submitting energy run…');
    this.api.create(request, this.idempotencyKey!).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: accepted => {
        this.run.set(accepted);
        this.attempts = 0;
        this.busy.set(false);
        this.statusMessage.set('Run queued. Checking progress…');
        this.poll(accepted.runId);
      },
      error: error => {
        this.busy.set(false);
        this.error.set(this.errorMessage(error));
      },
    });
  }

  protected loadPage(page: number): void {
    const run = this.run();
    if (!run || page < 0) return;
    this.api.candidates(run.runId, page).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: result => this.page.set(result),
      error: error => this.error.set(this.errorMessage(error)),
    });
  }

  private poll(runId: string): void {
    this.clearTimer();
    if (this.attempts++ >= 155) {
      this.error.set('The run is taking longer than expected. Submit the same inputs again to reopen it.');
      return;
    }
    this.timer = setTimeout(() => {
      this.api.run(runId).pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: result => {
          this.run.set(result);
          if (result.status === 'QUEUED' || result.status === 'RUNNING') {
            this.statusMessage.set(`Run ${result.status.toLowerCase()}. Checking progress…`);
            this.poll(runId);
          } else {
            this.statusMessage.set(`Run ${result.status.toLowerCase()}.`);
            if (result.status !== 'FAILED') this.loadPage(0);
          }
        },
        error: error => this.error.set(this.errorMessage(error)),
      });
    }, 2000);
  }

  private clearTimer(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = undefined;
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401 || error.status === 403 || error.url?.includes('/login')) {
        return 'Your platform session or access has expired. Sign in again, then retry.';
      }
      if (error.status === 503) return 'Energy data or platform access is temporarily unavailable. Retry shortly.';
      if (typeof error.error?.message === 'string') return error.error.message;
    }
    return 'The energy request could not be completed. Retry shortly.';
  }
}
