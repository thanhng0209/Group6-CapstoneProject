import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { AdminService } from '../services/admin.service';
import { AuthService } from '../auth/auth.service';
import { UsageTrendPoint } from '../models/admin.model';

function point(date: string, grams: number, charged: number, refunded: number, jobs: number): UsageTrendPoint {
  return { date, filamentGrams: grams, charged, refunded, jobCount: jobs };
}

describe('AdminDashboardComponent usage trends', () => {
  let fixture: ComponentFixture<AdminDashboardComponent>;
  let component: AdminDashboardComponent;
  let adminService: jasmine.SpyObj<AdminService>;

  const threeDays = [
    point('2026-10-09', 0, 0, 0, 0),
    point('2026-10-10', 20, 4, 1, 2),
    point('2026-10-11', 40, 8, 0, 3),
  ];

  beforeEach(async () => {
    adminService = jasmine.createSpyObj<AdminService>('AdminService', [
      'getPrinters',
      'getFilamentUsage',
      'getJobs',
      'getCostSummary',
      'getPendingRefunds',
      'getUsageTrends',
      'approveRefund',
      'rejectRefund',
    ]);
    adminService.getPrinters.and.returnValue(of([]));
    adminService.getFilamentUsage.and.returnValue(of({}));
    adminService.getJobs.and.returnValue(of([]));
    adminService.getCostSummary.and.returnValue(of({ totalCharged: 0, totalRefunded: 0, netRevenue: 0 }));
    adminService.getPendingRefunds.and.returnValue(of([]));
    adminService.getUsageTrends.and.returnValue(of(threeDays));

    await TestBed.configureTestingModule({
      imports: [AdminDashboardComponent],
      providers: [
        provideRouter([]),
        { provide: AdminService, useValue: adminService },
        { provide: AuthService, useValue: jasmine.createSpyObj<AuthService>('AuthService', ['logout']) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('does not load trends until the Usage Trends tab is opened', () => {
    expect(adminService.getUsageTrends).not.toHaveBeenCalled();
  });

  it('loads 30 days of trends when the tab is opened', () => {
    component.setActiveTab('trends');

    expect(adminService.getUsageTrends).toHaveBeenCalledOnceWith(30);
    expect(component.trendPoints).toEqual(threeDays);
    expect(component.trendsError).toBeFalse();
    expect(component.isLoadingTrends).toBeFalse();
  });

  it('reloads with the chosen range and ignores re-selecting the current range', () => {
    component.setActiveTab('trends');
    adminService.getUsageTrends.calls.reset();

    component.setTrendRange(30);
    expect(adminService.getUsageTrends).not.toHaveBeenCalled();

    component.setTrendRange(7);
    expect(component.trendDays).toBe(7);
    expect(adminService.getUsageTrends).toHaveBeenCalledOnceWith(7);
  });

  it('totals the range for the summary cards', () => {
    component.trendPoints = threeDays;

    expect(component.trendTotalFilament).toBe(60);
    expect(component.trendTotalCharged).toBe(12);
    expect(component.trendTotalRefunded).toBe(1);
    expect(component.trendTotalJobs).toBe(5);
    expect(component.hasTrendActivity).toBeTrue();
  });

  it('reports no activity for a zero-filled range', () => {
    component.trendPoints = [point('2026-10-10', 0, 0, 0, 0), point('2026-10-11', 0, 0, 0, 0)];

    expect(component.hasTrendActivity).toBeFalse();
    expect(component.barHeight(0, 'filamentGrams')).toBe(0);
  });

  it('scales bars against the largest value in the series', () => {
    component.trendPoints = threeDays;

    expect(component.barHeight(40, 'filamentGrams')).toBe(100);
    expect(component.barHeight(20, 'filamentGrams')).toBe(50);
    expect(component.barHeight(0, 'filamentGrams')).toBe(0);
  });

  it('keeps tiny non-zero bars visible', () => {
    component.trendPoints = [point('2026-10-10', 1, 0, 0, 1), point('2026-10-11', 1000, 0, 0, 1)];

    expect(component.barHeight(1, 'filamentGrams')).toBe(2);
  });

  it('labels roughly six dates and always the last one on long ranges', () => {
    component.trendPoints = Array.from({ length: 30 }, (_, i) =>
      point(`2026-09-${String(i + 1).padStart(2, '0')}`, 0, 0, 0, 0)
    );

    const labelled = component.trendPoints.map((_, i) => component.showTrendLabel(i)).filter(Boolean).length;
    expect(labelled).toBeLessThanOrEqual(7);
    expect(component.showTrendLabel(0)).toBeTrue();
    expect(component.showTrendLabel(29)).toBeTrue();
    expect(component.showTrendLabel(1)).toBeFalse();
  });

  it('flags an error when the request fails and clears it on a later success', () => {
    adminService.getUsageTrends.and.returnValue(throwError(() => new Error('boom')));
    component.setActiveTab('trends');

    expect(component.trendsError).toBeTrue();
    expect(component.isLoadingTrends).toBeFalse();

    adminService.getUsageTrends.and.returnValue(of(threeDays));
    component.loadTrends();
    expect(component.trendsError).toBeFalse();
  });

  it('renders the tab, summary cards and one bar column per day in each chart', () => {
    const tabs = Array.from(fixture.nativeElement.querySelectorAll('.tab-btn')) as HTMLElement[];
    const trendsTab = tabs.find((t) => t.textContent?.includes('Usage Trends'));
    expect(trendsTab).toBeDefined();

    trendsTab!.click();
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.finance-grid .stat-card').length).toBe(4);
    expect(el.querySelectorAll('.trend-chart').length).toBe(2);
    expect(el.querySelectorAll('.bar-col').length).toBe(threeDays.length * 2);
    expect(el.querySelector('.trend-empty-note')).toBeNull();
  });

  it('shows a note instead of bars when the range has no activity', () => {
    adminService.getUsageTrends.and.returnValue(of([point('2026-10-10', 0, 0, 0, 0)]));
    component.setActiveTab('trends');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.trend-empty-note')).not.toBeNull();
  });

  it('shows an error message when trends cannot be loaded', () => {
    adminService.getUsageTrends.and.returnValue(throwError(() => new Error('boom')));
    component.setActiveTab('trends');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Could not load usage trends');
  });
});
