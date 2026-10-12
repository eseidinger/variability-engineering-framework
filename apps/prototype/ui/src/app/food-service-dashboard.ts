import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, isDevMode, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule, NonNullableFormBuilder } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { KeyValuePipe, PercentPipe } from '@angular/common';
import {
  AnalysisRequest,
  DimensionId,
  FoodServiceApiService,
  LeverageResponse,
  PopulationSummary,
  UnavailabilityResponse,
} from './food-service-api.service';
import { PlatformAuthService, PlatformGroup } from './platform-auth.service';

const dimensionLabels: Record<DimensionId, string> = {
  cuisine: 'Cuisine',
  diet: 'Diet',
  dishType: 'Dish type',
};

@Component({
  selector: 'app-food-service-dashboard',
  imports: [
    KeyValuePipe,
    PercentPipe,
    DragDropModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDividerModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  templateUrl: './food-service-dashboard.html',
  styleUrl: './food-service-dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FoodServiceDashboardComponent {
  private readonly api = inject(FoodServiceApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly platformAuth = inject(PlatformAuthService);

  protected readonly summary = signal<PopulationSummary | null>(null);
  protected readonly analysis = signal<import('./food-service-api.service').AnalysisResponse | null>(null);
  protected readonly leverageResult = signal<LeverageResponse | null>(null);
  protected readonly impactResult = signal<UnavailabilityResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly groupingDimensions = signal<DimensionId[]>(['cuisine', 'diet', 'dishType']);
  protected readonly dimensionLabels = dimensionLabels;
  protected readonly cuisineOptions = computed(
    () => this.summary()?.dimensions.cuisine?.observedValues ?? [],
  );
  protected readonly dietOptions = computed(
    () => this.summary()?.dimensions.diet?.observedValues ?? [],
  );
  protected readonly elements = computed(() => Object.keys(this.summary()?.metrics.elementRecordCounts ?? {}).sort());
  protected readonly platformGroup = this.platformAuth.group;
  protected readonly developmentAuthControls = isDevMode();

  protected readonly filters = this.formBuilder.group({
    cuisines: this.formBuilder.control<string[]>([]),
    diets: this.formBuilder.control<string[]>([]),
    leverageElement: this.formBuilder.control('garlic'),
    unavailableElement: this.formBuilder.control('garlic'),
  });

  constructor() {
    this.loadSummary();
  }

  protected applyFilters(): void {
    this.runAnalysis();
  }

  protected clearFilters(): void {
    this.filters.controls.cuisines.setValue([]);
    this.filters.controls.diets.setValue([]);
    this.runAnalysis();
  }

  protected reorderGrouping(event: CdkDragDrop<DimensionId[]>): void {
    this.groupingDimensions.update((dimensions) => {
      const reordered = [...dimensions];
      moveItemInArray(reordered, event.previousIndex, event.currentIndex);
      return reordered;
    });
    this.runAnalysis();
  }

  protected inspectLeverage(): void {
    const elementId = this.filters.controls.leverageElement.value;
    this.api.leverage(elementId, this.query()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => this.leverageResult.set(result),
      error: () => this.error.set('Ingredient leverage could not be calculated.'),
    });
  }

  protected inspectImpact(): void {
    const elementId = this.filters.controls.unavailableElement.value;
    this.api.unavailability(elementId, this.query()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => this.impactResult.set(result),
      error: () => this.error.set('Ingredient-unavailability impact could not be calculated.'),
    });
  }

  protected setPlatformGroup(group: PlatformGroup): void {
    this.platformAuth.setGroup(group);
    this.error.set(null);
    this.leverageResult.set(null);
    this.impactResult.set(null);
    this.loadSummary();
  }

  protected groupLabel(group: { path: Array<{ valueKind: string; value?: string }> }): string {
    const item = group.path.at(-1);
    if (!item || item.valueKind === 'KNOWN_EMPTY') return 'Known empty';
    if (item.valueKind === 'UNKNOWN') return 'Unknown';
    return item.value ?? 'Unspecified';
  }

  private loadSummary(): void {
    this.api.summary().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.loading.set(false);
        this.runAnalysis();
      },
      error: () => {
        this.error.set('The food-service population could not be loaded. Start the API and try again.');
        this.loading.set(false);
      },
    });
  }

  private runAnalysis(): void {
    this.error.set(null);
    this.api.analyse(this.query()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (analysis) => this.analysis.set(analysis),
      error: () => this.error.set('The current analysis could not be calculated.'),
    });
  }

  private query(): AnalysisRequest {
    const dimensionFilters: AnalysisRequest['dimensionFilters'] = {};
    const cuisines = this.filters.controls.cuisines.value;
    const diets = this.filters.controls.diets.value;
    if (cuisines.length > 0) dimensionFilters.cuisine = { includedValues: cuisines };
    if (diets.length > 0) dimensionFilters.diet = { includedValues: diets };
    return {
      ...(Object.keys(dimensionFilters).length > 0 ? { dimensionFilters } : {}),
      groupingDimensions: this.groupingDimensions(),
    };
  }
}
