import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { JsonPipe } from '@angular/common';
import { ReactiveFormsModule, NonNullableFormBuilder, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EnergyApiService, EnergyExplanation, EnergyRequest, EnergyRequestProposal, EnergyRun, CandidatePage, Capabilities } from './energy-api.service';

@Component({
  selector: 'app-energy-workflow',
  imports: [ReactiveFormsModule, JsonPipe],
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
  private proposalRevision = 0;

  protected readonly capabilities = signal<Capabilities | null>(null);
  protected readonly run = signal<EnergyRun | null>(null);
  protected readonly page = signal<CandidatePage | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly statusMessage = signal('');
  protected readonly proposal = signal<EnergyRequestProposal | null>(null);
  protected readonly explanation = signal<EnergyExplanation | null>(null);
  protected readonly explanationError = signal<string | null>(null);
  protected readonly explaining = signal(false);
  protected readonly proposalForm = this.builder.group({
    description: this.builder.control('', [Validators.required, Validators.maxLength(4000)]),
    requestJson: this.builder.control(''),
    confirmed: this.builder.control(false),
  });

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
    this.proposalForm.controls.description.valueChanges.pipe(takeUntilDestroyed(this.destroy)).subscribe(() => {
      this.proposalRevision++;
      this.busy.set(false);
      this.proposal.set(null);
      this.proposalForm.controls.requestJson.setValue('', { emitEvent: false });
      this.proposalForm.controls.confirmed.setValue(false, { emitEvent: false });
    });
    this.proposalForm.controls.requestJson.valueChanges.pipe(takeUntilDestroyed(this.destroy)).subscribe(() => {
      this.proposalForm.controls.confirmed.setValue(false, { emitEvent: false });
    });
    this.api.capabilities().pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: value => this.capabilities.set(value),
      error: error => this.error.set(this.errorMessage(error)),
    });
  }

  protected prepareProposal(): void {
    const description = this.proposalForm.controls.description;
    if (description.invalid) {
      description.markAsTouched();
      this.error.set('Describe the site and constraints in 4,000 characters or fewer.');
      return;
    }
    this.proposal.set(null);
    const revision = ++this.proposalRevision;
    this.proposalForm.controls.confirmed.setValue(false);
    this.busy.set(true);
    this.error.set(null);
    this.statusMessage.set('Preparing a request for review…');
    this.api.propose(description.value).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: proposal => {
        if (revision !== this.proposalRevision) return;
        this.busy.set(false);
        this.proposal.set(proposal);
        this.proposalForm.controls.requestJson.setValue(proposal.request ? JSON.stringify(proposal.request, null, 2) : '');
        this.statusMessage.set(proposal.status === 'READY_FOR_REVIEW'
          ? 'Review the complete request and confirm it before evaluation.'
          : 'More information is needed before a request can be evaluated.');
      },
      error: error => {
        if (revision !== this.proposalRevision) return;
        this.busy.set(false);
        this.error.set(this.errorMessage(error));
      },
    });
  }

  protected submitProposal(): void {
    if (this.proposal()?.status !== 'READY_FOR_REVIEW' || !this.proposalForm.controls.confirmed.value) {
      this.error.set('Review and confirm the current request before evaluation.');
      return;
    }
    let request: EnergyRequest;
    try {
      request = JSON.parse(this.proposalForm.controls.requestJson.value) as EnergyRequest;
      if (!request || request.schemaVersion !== 'energy-optimization-request/v1') throw new Error('Invalid request');
    } catch {
      this.error.set('The edited request must be valid energy request JSON.');
      return;
    }
    this.startRun(request);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Check the highlighted inputs before starting a run.');
      return;
    }
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
    this.startRun(request);
  }

  private startRun(request: EnergyRequest): void {
    this.clearTimer();
    this.error.set(null);
    this.page.set(null);
    this.run.set(null);
    this.explanation.set(null);
    this.explanationError.set(null);
    this.explaining.set(false);
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

  protected explainRun(): void {
    const run = this.run();
    if (!run || (run.status !== 'SUCCEEDED' && run.status !== 'INFEASIBLE')) return;
    this.explaining.set(true);
    this.explanationError.set(null);
    this.api.explain(run.runId).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: value => {
        if (this.run()?.runId !== run.runId) return;
        this.explaining.set(false);
        this.explanation.set(value);
      },
      error: error => {
        if (this.run()?.runId !== run.runId) return;
        this.explaining.set(false);
        this.explanation.set(null);
        this.explanationError.set(this.errorMessage(error));
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
