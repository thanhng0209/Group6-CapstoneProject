import { JobStatus } from './job.model';

export type AdminTab = 'farm-status' | 'jobs' | 'financial';

export type PrinterStatus = 'IDLE' | 'PRINTING' | 'MAINTENANCE' | 'OFFLINE';

export interface PrinterCard {
  id: string;
  name: string;
  model: string;
  status: string;
  currentMaterial: string;
  currentColour: string;
  loadedFilament?: string;
  jobCountsByStatus?: Record<string, number>;
}

export interface PrinterActivity {
  printerId: string;
  jobCountsByStatus: Record<string, number>;
}

export interface CostSummary {
  totalCharged: number;
  totalRefunded: number;
  netRevenue: number;
}

export type FilamentUsage = Record<string, number>;

export type RefundStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';

export interface RefundRequest {
  id: number;
  jobId: number;
  ownerUniId: string;
  amount: number;
  status: RefundStatus;
  requestedAt: string;
  decidedAt: string | null;
}
