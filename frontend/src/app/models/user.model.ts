export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  uniId: string;
  fullName: string;
  role: 'STUDENT' | 'STAFF' | 'ADMIN';
}

export interface PrintJobSummary {
  jobId: number;
  fileName: string;
  printerName: string;
  status: 'QUEUED' | 'PRINTING' | 'COMPLETED' | 'FAILED';
  submittedAt: string;
}

export interface UserDashboard {
  uniId: string;
  fullName: string;
  email: string;
  role: 'STUDENT' | 'STAFF' | 'ADMIN';
  balance: number;
  currentJobs: PrintJobSummary[];
  printHistory: PrintJobSummary[];
}
