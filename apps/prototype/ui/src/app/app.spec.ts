import { provideHttpClient, withInterceptors } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { FoodServiceDashboardComponent } from "./food-service-dashboard";
import { platformAuthInterceptor } from "./platform-auth.interceptor";
import { PlatformAuthService } from "./platform-auth.service";

const metrics = { recordCount: 12, variantCount: 11, uniqueElementCount: 24, elementRecordCounts: {}, elementRecordFrequencies: {} };
const dimensions = {
  cuisine: { cardinality: "SINGLE", origin: "IMPORTED", observedValues: ["ITALIAN", "MEDITERRANEAN"] },
  dishType: { cardinality: "SINGLE", origin: "IMPORTED", observedValues: ["MAIN"] },
  diet: { cardinality: "MULTI", origin: "DERIVED", observedValues: ["VEGAN", "VEGETARIAN"] },
  allergen: { cardinality: "MULTI", origin: "DERIVED", observedValues: ["GLUTEN"] },
} as const;

describe("FoodServiceDashboardComponent", () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [FoodServiceDashboardComponent],
      providers: [provideHttpClient(withInterceptors([platformAuthInterceptor])), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it("shows population metrics after loading the summary", () => {
    const fixture = TestBed.createComponent(FoodServiceDashboardComponent);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((request) => request.url === "/api/populations/food-service-mvp"
      && request.headers.get("X-Platform-Principal-Group-Ids") === "grp_d8113bc5c0ca4b6092ae13386310268f").flush({
      population: { id: "food-service-mvp", version: "1.0.0", provenance: {} },
      dimensions, metrics, rejectedRecords: [{ sourceRecordId: "13", reason: "missing ingredients", detail: "test" }],
    });
    http.expectOne((request) => request.url === "/api/populations/food-service-mvp/analysis"
      && request.headers.get("X-Platform-Principal-Group-Ids") === "grp_d8113bc5c0ca4b6092ae13386310268f").flush({
      population: { id: "food-service-mvp", version: "1.0.0", provenance: {} }, query: {}, selectedRecordIds: [], metrics, groups: [],
    });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain("Accepted recipes");
    expect((fixture.nativeElement as HTMLElement).textContent).toContain("12");
  });

  it("explains when the population cannot be loaded", () => {
    const fixture = TestBed.createComponent(FoodServiceDashboardComponent);
    TestBed.inject(HttpTestingController).expectOne("/api/populations/food-service-mvp").flush("Unavailable", { status: 503, statusText: "Unavailable" });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector("[role=alert]")?.textContent).toContain("could not be loaded");
  });

  it("persists and sends the guest group header", () => {
    const auth = TestBed.inject(PlatformAuthService);
    auth.setGroup("guest");
    const fixture = TestBed.createComponent(FoodServiceDashboardComponent);

    TestBed.inject(HttpTestingController).expectOne((request) => request.url === "/api/populations/food-service-mvp"
      && request.headers.get("X-Platform-Principal-Group-Ids") === "grp_0c98f1205bfe456a8517417a38de98e0").flush("Unavailable", { status: 403, statusText: "Forbidden" });

    expect(localStorage.getItem("variability-engineering.platform-group")).toBe("guest");
    fixture.destroy();
  });
});
