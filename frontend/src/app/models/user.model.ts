export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  uniId: string;
  fullName: string;
  role: "STUDENT" | "STAFF" | "ADMIN";
}

export interface PrintJobSummary {
  jobId: number;
  fileName: string;
  printerName: string;
  status:
    | "QUEUED"
    | "PRINTING"
    | "PAUSED"
    | "COMPLETED"
    | "FAILED"
    | "CANCELLED";
  submittedAt: string;
  completedAt?: string | null;
  progressPercent: number;
  remainingSeconds: number;
}

export interface UserDashboard {
  uniId: string;
  fullName: string;
  email: string;
  role: "STUDENT" | "STAFF" | "ADMIN";
  balance: number;
  currentJobs: PrintJobSummary[];
  printHistory: PrintJobSummary[];
  pendingCollectionJobs: PrintJobSummary[];
}

export type WalletTransactionType = "TOPUP" | "DEBIT" | "REFUND";

export interface WalletTransaction {
  id: number;
  jobId: number | null;
  type: WalletTransactionType;
  amount: number;
  balanceAfter: number;
  occurredAt: string;
  description: string | null;
}

export interface WalletTransactionPage {
  transactions: WalletTransaction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
