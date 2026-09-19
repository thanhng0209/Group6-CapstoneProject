import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, of } from 'rxjs';
import {
  CancelJobResponse,
  GcodeUploadResponse,
  Job,
  JobSubmitRequest,
  PrinterOption,
} from '../models/job.model';

const API_BASE = 'http://localhost:8080/api';

const DEFAULT_PRINTERS: PrinterOption[] = [
  { id: 'PRUSA_XL_1', name: 'Prusa XL (Dual Tool)', displayName: 'Prusa XL (Dual Tool)', model: 'PRUSA_XL', status: 'IDLE', currentMaterial: 'PLA', currentColour: 'Prusa Orange' },
  { id: 'PRUSA_MK4S_1', name: 'Prusa MK4S #1', displayName: 'Prusa MK4S #1', model: 'PRUSA_MK4S', status: 'IDLE', currentMaterial: 'PETG', currentColour: 'Galaxy Black' },
  { id: 'PRUSA_CORE_ONE_1', name: 'Prusa Core One #1', displayName: 'Prusa Core One #1', model: 'PRUSA_CORE_ONE', status: 'IDLE', currentMaterial: 'PLA', currentColour: 'White' },
];

const MATERIAL_RATES: Record<string, number> = {
  PLA: 0.05,
  PETG: 0.07,
  ABS: 0.06,
};
const DEFAULT_MATERIAL_RATE = 0.06;
const MACHINE_RATE_PER_MINUTE = 0.02;

@Injectable({ providedIn: 'root' })
export class JobService {
  constructor(private http: HttpClient) {}

  getPrinters(): Observable<PrinterOption[]> {
    return this.http.get<PrinterOption[]>(`${API_BASE}/printers`).pipe(
      catchError(() => of(DEFAULT_PRINTERS))
    );
  }

  validateGcode(file: File, printerId: string): Observable<GcodeUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('printerId', printerId);
    return this.http.post<GcodeUploadResponse>(`${API_BASE}/gcode/upload`, formData);
  }

  submitJob(request: JobSubmitRequest): Observable<Job> {
    return this.http.post<Job>(`${API_BASE}/jobs/submit`, request);
  }

  getMyJobs(): Observable<Job[]> {
    return this.http.get<Job[]>(`${API_BASE}/jobs/my`);
  }

  cancelJob(jobId: number): Observable<CancelJobResponse> {
    return this.http.post<CancelJobResponse>(`${API_BASE}/jobs/${jobId}/cancel`, {});
  }

  calculateEstimatedCost(material: string | null | undefined, grams: number | null | undefined, minutes: number | null | undefined): number {
    const matKey = (material || '').toUpperCase();
    const rate = MATERIAL_RATES[matKey] ?? DEFAULT_MATERIAL_RATE;
    const g = grams ?? 0;
    const m = minutes ?? 0;
    const cost = rate * g + MACHINE_RATE_PER_MINUTE * m;
    return Math.round(cost * 100) / 100;
  }
}
