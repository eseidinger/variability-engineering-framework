import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface EnergyRequest {
  schemaVersion: 'energy-optimization-request/v1';
  dataset: { datasetId: string; version: string; schemaRef: 'urn:portfolio:energy-configuration-input:v1' };
  site: { regionCode: 'DE-BY-09187182'; usableRoofAreaM2: number; annualDemandKWh: number; existingComponentIds: string[] };
  constraints: { maxInitialInvestmentEUR: number; minSelfSufficiency: number };
  objective: 'MIN_ANNUALIZED_COST';
  assumptions: { analysisYears: 20; discountRate: 0.04 };
}

export interface EnergyRequestProposal {
  schemaVersion: 'energy-ai-request-proposal/v1';
  status: 'READY_FOR_REVIEW' | 'NEEDS_CLARIFICATION';
  requiresConfirmation: true;
  request: EnergyRequest | null;
  clarificationQuestions: string[];
  assumptionsToReview: string[];
}

export interface EnergyExplanation {
  schemaVersion: 'energy-ai-explanation/v1';
  runId: string;
  claims: Array<{ text: string; category: string; citations: Array<{ source: string; pointer: string; value: unknown }> }>;
  missingEvidence: string[];
}

export interface Capabilities {
  maxModelsPerType: number;
  maxPvModules: number;
  inverterCount: number;
  maxBatteries: number;
  maxCandidateEvaluations: number;
  runTimeoutSeconds: number;
  maxPageSize: number;
}

export interface EnergyRun {
  runId: string;
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'INFEASIBLE' | 'FAILED';
  createdAt: string;
  completedAt?: string;
  error?: { code: string; message: string };
  dataset?: EnergyRequest['dataset'] & { manifestSha256: string };
  modelVersion?: string;
  simulationVersion?: string;
  optimizerVersion?: string;
  optimalityStatus?: string;
  evaluatedCandidateCount?: number;
  feasibleCandidateCount?: number;
  recommendedConfigurationId?: string | null;
  warnings?: Array<{ code: string; message: string }>;
}

export interface EnergyCandidate {
  configurationId: string;
  feasible: boolean;
  components: Array<{ componentId: string; type: string; quantity: number; existing: boolean }>;
  constraints: Array<{ code: string; status: 'PASS' | 'FAIL'; actual: string | number; limit?: string | number; unit?: string }>;
  metrics: {
    initialInvestmentEUR: number;
    annualizedCostEUR: number;
    selfSufficiency: number;
    annualDemandKWh: number;
    gridImportKWh: number;
    gridExportKWh: number;
    onSiteSolarUseKWh: number;
    unmetDemandKWh: number;
    inverterClippingKWh: number;
    terminalDiscardedEnergyKWh: number;
  };
}

export interface CandidatePage {
  items: EnergyCandidate[];
  page: number;
  pageSize: number;
  total: number;
}

@Injectable({ providedIn: 'root' })
export class EnergyApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1';

  capabilities() { return this.http.get<Capabilities>(`${this.base}/capabilities`); }
  propose(description: string) {
    return this.http.post<EnergyRequestProposal>(`${this.base}/ai/request-proposals`, { description });
  }
  create(request: EnergyRequest, key: string) {
    return this.http.post<EnergyRun>(`${this.base}/optimization-runs`, request, {
      headers: { 'Idempotency-Key': key },
    });
  }
  explain(runId: string) {
    return this.http.post<EnergyExplanation>(
      `${this.base}/ai/optimization-runs/${encodeURIComponent(runId)}/explanation`, {});
  }
  run(id: string) { return this.http.get<EnergyRun>(`${this.base}/optimization-runs/${encodeURIComponent(id)}`); }
  candidates(id: string, page: number) {
    return this.http.get<CandidatePage>(`${this.base}/optimization-runs/${encodeURIComponent(id)}/candidates`, {
      params: { page, pageSize: 20 },
    });
  }
}
