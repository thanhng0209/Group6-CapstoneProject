export type JobStatus = 'QUEUED' | 'PRINTING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface PrinterOption {
  id: string;
  name?: string;
  displayName?: string;
  model?: string;
  status?: string;
  currentMaterial?: string;
  currentColour?: string;
}

export interface GcodeUploadResponse {
  printerProfileId: string;
  printVolumeX: number | null;
  printVolumeY: number | null;
  printVolumeZ: number | null;
  nozzleDiameter: number | null;
  layerHeight: number | null;
  material: string;
  nozzleTemp: number | null;
  bedTemp: number | null;
  estimatedGrams: number | null;
  estimatedMinutes: number | null;
  compatible: boolean;
  compatibilityErrors: string[];
}

export interface JobSubmitRequest {
  printerId: string;
  printerProfileId?: string;
  fileName?: string;
  material?: string;
  estimatedGrams?: number;
  estimatedMinutes?: number;
  ownerUniId?: string;
}

export interface Job {
  id: number;
  ownerUniId: string;
  printerId: string;
  fileName: string;
  material: string;
  estimatedGrams: number;
  estimatedMinutes: number;
  cost: number;
  status: JobStatus;
  queuedAt: string;
  completedAt: string | null;
}

export interface CancelJobResponse {
  jobId: number;
  status: JobStatus | string;
  refundDecision: 'AUTO_REFUNDED' | 'REQUIRES_APPROVAL' | string;
  message: string;
}
