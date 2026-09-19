import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { forkJoin, map, Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Job, JobStatus } from '../models/job.model';
import {
  CostSummary,
  FilamentUsage,
  PrinterActivity,
  PrinterCard,
  RefundRequest,
} from '../models/admin.model';

const API_BASE = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class AdminService {
  constructor(private http: HttpClient) {}

  /**
   * Fetches hardware printers (/api/printers) and per-printer activity
   * (/api/admin/printers) and merges them into unified PrinterCard models.
   */
  getPrinters(): Observable<PrinterCard[]> {
    const printers$ = this.http.get<PrinterCard[]>(`${API_BASE}/printers`).pipe(
      catchError(() => of([] as PrinterCard[]))
    );
    const activities$ = this.http
      .get<PrinterActivity[]>(`${API_BASE}/admin/printers`)
      .pipe(catchError(() => of([] as PrinterActivity[])));

    return forkJoin({ printers: printers$, activities: activities$ }).pipe(
      map(({ printers, activities }) => {
        const activityMap = new Map<string, Record<string, number>>();
        for (const act of activities) {
          activityMap.set(act.printerId, act.jobCountsByStatus || {});
        }

        // Merge activity into known hardware printers
        const enriched: PrinterCard[] = printers.map((printer) => ({
          ...printer,
          loadedFilament: printer.loadedFilament || printer.currentMaterial,
          jobCountsByStatus: activityMap.get(printer.id) || {},
        }));

        // In case an activity entry has a printer not yet in the DB inventory
        const knownIds = new Set(printers.map((p) => p.id));
        for (const act of activities) {
          if (!knownIds.has(act.printerId)) {
            enriched.push({
              id: act.printerId,
              name: act.printerId,
              model: 'Unknown',
              status: 'IDLE',
              currentMaterial: 'N/A',
              currentColour: '',
              loadedFilament: 'N/A',
              jobCountsByStatus: act.jobCountsByStatus || {},
            });
          }
        }

        return enriched;
      })
    );
  }

  /**
   * Fetches all jobs across the system with optional status filtering.
   */
  getJobs(statusFilter?: JobStatus): Observable<Job[]> {
    let params = new HttpParams();
    if (statusFilter) {
      params = params.set('status', statusFilter);
    }
    return this.http.get<Job[]>(`${API_BASE}/admin/jobs`, { params });
  }

  /**
   * Fetches financial summary (totalCharged, totalRefunded, netRevenue).
   */
  getCostSummary(): Observable<CostSummary> {
    return this.http.get<CostSummary>(`${API_BASE}/admin/costs`);
  }

  /**
   * Fetches total filament grams consumed by material.
   */
  getFilamentUsage(): Observable<FilamentUsage> {
    return this.http.get<FilamentUsage>(`${API_BASE}/admin/filament-usage`);
  }

  /**
   * Fetches list of refund requests awaiting farm-manager approval.
   */
  getPendingRefunds(): Observable<RefundRequest[]> {
    return this.http.get<RefundRequest[]>(`${API_BASE}/admin/refunds/pending`);
  }

  /**
   * Approves a pending refund request and credit's user's account.
   */
  approveRefund(id: number): Observable<RefundRequest> {
    return this.http.post<RefundRequest>(`${API_BASE}/admin/refunds/${id}/approve`, {});
  }

  /**
   * Rejects a pending refund request.
   */
  rejectRefund(id: number): Observable<RefundRequest> {
    return this.http.post<RefundRequest>(`${API_BASE}/admin/refunds/${id}/reject`, {});
  }
}
