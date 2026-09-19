import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AdminService } from '../services/admin.service';
import { AuthService } from '../auth/auth.service';
import { Job, JobStatus } from '../models/job.model';
import {
  AdminTab,
  CostSummary,
  FilamentUsage,
  PrinterCard,
  RefundRequest,
} from '../models/admin.model';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.css',
})
export class AdminDashboardComponent implements OnInit, OnDestroy {
  activeTab: AdminTab = 'farm-status';

  // Farm Status Data
  printers: PrinterCard[] = [];
  filamentUsage: FilamentUsage = {};
  isLoadingPrinters = false;

  // Job Management Data
  allJobs: Job[] = [];
  filteredJobs: Job[] = [];
  selectedStatusFilter: JobStatus | 'ALL' = 'ALL';
  searchQuery = '';
  isLoadingJobs = false;

  // Financial & Refunds Data
  costSummary: CostSummary | null = null;
  pendingRefunds: RefundRequest[] = [];
  isLoadingFinancial = false;
  processingRefundIds = new Set<number>();

  // Global & State management
  isInitialLoading = true;
  isRefreshing = false;
  lastRefreshed: Date = new Date();
  actionMessage: { text: string; type: 'success' | 'error' } | null = null;
  private pollTimer: any = null;

  // Status options for filtering
  readonly statusOptions: Array<JobStatus | 'ALL'> = [
    'ALL',
    'QUEUED',
    'PRINTING',
    'COMPLETED',
    'FAILED',
    'CANCELLED',
  ];

  constructor(
    private adminService: AdminService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadAllData(true);

    // Background polling every 10 seconds for real-time telemetry
    this.pollTimer = setInterval(() => {
      this.refreshCurrentTabDataSilently();
    }, 10000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  /**
   * Loads all admin datasets initially.
   */
  loadAllData(isInitial = false): void {
    if (isInitial) {
      this.isInitialLoading = true;
    } else {
      this.isRefreshing = true;
    }

    forkJoin({
      printers: this.adminService.getPrinters(),
      filament: this.adminService.getFilamentUsage(),
      jobs: this.adminService.getJobs(),
      costs: this.adminService.getCostSummary(),
      refunds: this.adminService.getPendingRefunds(),
    }).subscribe({
      next: (data) => {
        this.printers = data.printers;
        this.filamentUsage = data.filament;
        this.allJobs = data.jobs;
        this.applyJobFilters();
        this.costSummary = data.costs;
        this.pendingRefunds = data.refunds;
        this.lastRefreshed = new Date();
        this.isInitialLoading = false;
        this.isRefreshing = false;
      },
      error: () => {
        this.isInitialLoading = false;
        this.isRefreshing = false;
        this.setActionMessage('Failed to load admin monitoring data from server.', 'error');
      },
    });
  }

  /**
   * Switches the active tab and refreshes its relevant data.
   */
  setActiveTab(tab: AdminTab): void {
    this.activeTab = tab;
    if (tab === 'farm-status') {
      this.refreshFarmStatus();
    } else if (tab === 'jobs') {
      this.refreshJobs();
    } else if (tab === 'financial') {
      this.refreshFinancial();
    }
  }

  manualRefresh(): void {
    this.isRefreshing = true;
    this.loadAllData(false);
  }

  private refreshCurrentTabDataSilently(): void {
    if (this.activeTab === 'farm-status') {
      this.adminService.getPrinters().subscribe({
        next: (printers) => {
          this.printers = printers;
          this.lastRefreshed = new Date();
        },
      });
      this.adminService.getFilamentUsage().subscribe({
        next: (usage) => {
          this.filamentUsage = usage;
        },
      });
    } else if (this.activeTab === 'jobs') {
      const filter = this.selectedStatusFilter === 'ALL' ? undefined : this.selectedStatusFilter;
      this.adminService.getJobs(filter).subscribe({
        next: (jobs) => {
          this.allJobs = jobs;
          this.applyJobFilters();
          this.lastRefreshed = new Date();
        },
      });
    } else if (this.activeTab === 'financial') {
      this.adminService.getCostSummary().subscribe({
        next: (costs) => {
          this.costSummary = costs;
        },
      });
      this.adminService.getPendingRefunds().subscribe({
        next: (refunds) => {
          this.pendingRefunds = refunds;
          this.lastRefreshed = new Date();
        },
      });
    }
  }

  // --- Farm Status Tab ---
  refreshFarmStatus(): void {
    this.isLoadingPrinters = true;
    forkJoin({
      printers: this.adminService.getPrinters(),
      filament: this.adminService.getFilamentUsage(),
    }).subscribe({
      next: (res) => {
        this.printers = res.printers;
        this.filamentUsage = res.filament;
        this.isLoadingPrinters = false;
        this.lastRefreshed = new Date();
      },
      error: () => {
        this.isLoadingPrinters = false;
      },
    });
  }

  get printingPrintersCount(): number {
    return this.printers.filter((p) => (p.status || '').toUpperCase() === 'PRINTING').length;
  }

  get idlePrintersCount(): number {
    return this.printers.filter((p) => (p.status || '').toUpperCase() === 'IDLE').length;
  }

  get totalFilamentGrams(): number {
    return Object.values(this.filamentUsage).reduce((sum, val) => sum + (val || 0), 0);
  }

  // --- Job Management Tab ---
  refreshJobs(): void {
    this.isLoadingJobs = true;
    const filter = this.selectedStatusFilter === 'ALL' ? undefined : this.selectedStatusFilter;
    this.adminService.getJobs(filter).subscribe({
      next: (jobs) => {
        this.allJobs = jobs;
        this.applyJobFilters();
        this.isLoadingJobs = false;
        this.lastRefreshed = new Date();
      },
      error: () => {
        this.isLoadingJobs = false;
        this.setActionMessage('Failed to refresh job listings.', 'error');
      },
    });
  }

  setStatusFilter(status: JobStatus | 'ALL'): void {
    this.selectedStatusFilter = status;
    this.refreshJobs();
  }

  onSearchChange(): void {
    this.applyJobFilters();
  }

  applyJobFilters(): void {
    let result = [...this.allJobs];

    if (this.selectedStatusFilter !== 'ALL') {
      result = result.filter((j) => j.status === this.selectedStatusFilter);
    }

    if (this.searchQuery && this.searchQuery.trim()) {
      const q = this.searchQuery.trim().toLowerCase();
      result = result.filter(
        (j) =>
          j.id.toString().includes(q) ||
          j.ownerUniId?.toLowerCase().includes(q) ||
          j.fileName?.toLowerCase().includes(q) ||
          j.printerId?.toLowerCase().includes(q) ||
          j.material?.toLowerCase().includes(q)
      );
    }

    this.filteredJobs = result;
  }

  getJobCountByStatus(status: JobStatus | 'ALL'): number {
    if (status === 'ALL') {
      return this.allJobs.length;
    }
    return this.allJobs.filter((j) => j.status === status).length;
  }

  // --- Financial & Refunds Tab ---
  refreshFinancial(): void {
    this.isLoadingFinancial = true;
    forkJoin({
      costs: this.adminService.getCostSummary(),
      refunds: this.adminService.getPendingRefunds(),
      filament: this.adminService.getFilamentUsage(),
    }).subscribe({
      next: (res) => {
        this.costSummary = res.costs;
        this.pendingRefunds = res.refunds;
        this.filamentUsage = res.filament;
        this.isLoadingFinancial = false;
        this.lastRefreshed = new Date();
      },
      error: () => {
        this.isLoadingFinancial = false;
        this.setActionMessage('Failed to load financial statistics.', 'error');
      },
    });
  }

  approveRefund(request: RefundRequest): void {
    const confirmApprove = window.confirm(
      `Approve refund request #${request.id} for $${request.amount.toFixed(2)} (User: ${request.ownerUniId}, Job #${request.jobId})?`
    );
    if (!confirmApprove) return;

    this.processingRefundIds.add(request.id);

    this.adminService.approveRefund(request.id).subscribe({
      next: (updated) => {
        this.processingRefundIds.delete(request.id);
        this.setActionMessage(
          `Refund #${request.id} approved. $${request.amount.toFixed(2)} has been refunded to ${request.ownerUniId}.`,
          'success'
        );
        this.refreshFinancial();
      },
      error: (err) => {
        this.processingRefundIds.delete(request.id);
        const msg = err.error?.errors?.[0] || 'Failed to approve refund request.';
        this.setActionMessage(msg, 'error');
      },
    });
  }

  rejectRefund(request: RefundRequest): void {
    const confirmReject = window.confirm(
      `Reject refund request #${request.id} for $${request.amount.toFixed(2)} (User: ${request.ownerUniId}, Job #${request.jobId})?`
    );
    if (!confirmReject) return;

    this.processingRefundIds.add(request.id);

    this.adminService.rejectRefund(request.id).subscribe({
      next: (updated) => {
        this.processingRefundIds.delete(request.id);
        this.setActionMessage(
          `Refund #${request.id} has been rejected.`,
          'success'
        );
        this.refreshFinancial();
      },
      error: (err) => {
        this.processingRefundIds.delete(request.id);
        const msg = err.error?.errors?.[0] || 'Failed to reject refund request.';
        this.setActionMessage(msg, 'error');
      },
    });
  }

  private setActionMessage(text: string, type: 'success' | 'error'): void {
    this.actionMessage = { text, type };
    setTimeout(() => {
      if (this.actionMessage?.text === text) {
        this.actionMessage = null;
      }
    }, 6000);
  }

  // --- Helpers & UI formatting ---
  formatDate(isoString: string | null | undefined): string {
    if (!isoString) return '-';
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  }

  formatMinutes(mins: number | null | undefined): string {
    if (mins == null || mins === 0) return '-';
    const m = Math.round(mins);
    const h = Math.floor(m / 60);
    const rem = m % 60;
    return h > 0 ? `${h}h ${rem}m` : `${rem}m`;
  }

  formatCurrency(val: number | null | undefined): string {
    if (val == null) return '$0.00';
    return `$${Number(val).toFixed(2)}`;
  }

  getFilamentKeys(): string[] {
    return Object.keys(this.filamentUsage);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
