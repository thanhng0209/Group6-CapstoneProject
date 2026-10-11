import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AdminService } from './admin.service';
import { UsageTrendPoint } from '../models/admin.model';

describe('AdminService', () => {
  let service: AdminService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests usage trends with the given number of days', () => {
    const points: UsageTrendPoint[] = [
      { date: '2026-10-10', filamentGrams: 12.5, charged: 3.2, refunded: 0, jobCount: 1 },
    ];
    let result: UsageTrendPoint[] | undefined;

    service.getUsageTrends(7).subscribe((r) => (result = r));

    const req = http.expectOne((r) => r.url === 'http://localhost:8080/api/admin/usage-trends');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('days')).toBe('7');
    req.flush(points);
    expect(result).toEqual(points);
  });

  it('defaults usage trends to 30 days', () => {
    service.getUsageTrends().subscribe();

    const req = http.expectOne((r) => r.url === 'http://localhost:8080/api/admin/usage-trends');
    expect(req.request.params.get('days')).toBe('30');
    req.flush([]);
  });

  it('passes the status filter when listing jobs', () => {
    service.getJobs('COMPLETED').subscribe();

    const req = http.expectOne((r) => r.url === 'http://localhost:8080/api/admin/jobs');
    expect(req.request.params.get('status')).toBe('COMPLETED');
    req.flush([]);
  });

  it('lists jobs without a status param when no filter is given', () => {
    service.getJobs().subscribe();

    const req = http.expectOne((r) => r.url === 'http://localhost:8080/api/admin/jobs');
    expect(req.request.params.has('status')).toBeFalse();
    req.flush([]);
  });

  it('posts approve and reject decisions to the refund endpoints', () => {
    service.approveRefund(5).subscribe();
    const approve = http.expectOne('http://localhost:8080/api/admin/refunds/5/approve');
    expect(approve.request.method).toBe('POST');
    approve.flush({});

    service.rejectRefund(6).subscribe();
    const reject = http.expectOne('http://localhost:8080/api/admin/refunds/6/reject');
    expect(reject.request.method).toBe('POST');
    reject.flush({});
  });
});
