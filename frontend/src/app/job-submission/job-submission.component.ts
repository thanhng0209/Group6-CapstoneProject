import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { JobService } from '../services/job.service';
import { UserService } from '../services/user.service';
import { GcodeUploadResponse, Job, PrinterOption } from '../models/job.model';

@Component({
  selector: 'app-job-submission',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './job-submission.component.html',
  styleUrl: './job-submission.component.css',
})
export class JobSubmissionComponent implements OnInit {
  printers: PrinterOption[] = [];
  selectedPrinterId: string = '';
  selectedFile: File | null = null;
  fileSizeFormatted: string = '';

  isDragOver = false;
  isValidating = false;
  isSubmitting = false;
  showGuide = false;
  showConfirmDialog = false;

  validationResult: GcodeUploadResponse | null = null;
  errorMessage: string | null = null;
  errorDetails: string[] = [];

  estimatedCost = 0;
  userBalance: number | null = null;
  submissionSuccessJob: Job | null = null;

  readonly MAX_SIZE = 50 * 1024 * 1024; // 50MB

  constructor(
    private jobService: JobService,
    private userService: UserService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadPrinters();
    this.loadUserBalance();
  }

  loadPrinters(): void {
    this.jobService.getPrinters().subscribe({
      next: (list) => {
        this.printers = list;
        if (list.length > 0 && !this.selectedPrinterId) {
          this.selectedPrinterId = list[0].id;
        }
      },
      error: () => {
        // Fallback default printers already provided in JobService
      },
    });
  }

  loadUserBalance(): void {
    this.userService.getDashboard().subscribe({
      next: (dashboard) => {
        this.userBalance = dashboard.balance;
      },
      error: () => {
        this.userBalance = null;
      },
    });
  }

  toggleGuide(): void {
    this.showGuide = !this.showGuide;
  }

  onPrinterChange(): void {
    if (this.validationResult) {
      // Re-validation needed if printer changed
      this.validationResult = null;
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.handleFile(files[0]);
    }
  }

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.handleFile(input.files[0]);
    }
  }

  handleFile(file: File): void {
    this.errorMessage = null;
    this.errorDetails = [];
    this.validationResult = null;
    this.submissionSuccessJob = null;

    if (!file.name.toLowerCase().endsWith('.gcode')) {
      this.errorMessage = 'Only .gcode files are accepted.';
      return;
    }

    if (file.size > this.MAX_SIZE) {
      this.errorMessage = 'File exceeds the 50 MB upload limit.';
      return;
    }

    this.selectedFile = file;
    this.fileSizeFormatted = this.formatBytes(file.size);
  }

  clearFile(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.selectedFile = null;
    this.fileSizeFormatted = '';
    this.validationResult = null;
    this.errorMessage = null;
    this.errorDetails = [];
  }

  validateGcode(): void {
    if (!this.selectedFile || !this.selectedPrinterId) {
      return;
    }

    this.isValidating = true;
    this.errorMessage = null;
    this.errorDetails = [];
    this.validationResult = null;
    this.submissionSuccessJob = null;

    this.jobService.validateGcode(this.selectedFile, this.selectedPrinterId).subscribe({
      next: (res) => {
        this.isValidating = false;
        this.validationResult = res;
        this.estimatedCost = this.jobService.calculateEstimatedCost(
          res.material,
          res.estimatedGrams,
          res.estimatedMinutes
        );
      },
      error: (err) => {
        this.isValidating = false;
        const errBody = err.error;
        if (errBody && Array.isArray(errBody.errors) && errBody.errors.length > 0) {
          this.errorMessage = 'Validation failed';
          this.errorDetails = errBody.errors;
        } else if (err.status === 413) {
          this.errorMessage = 'File exceeds the 50 MB upload limit.';
        } else {
          this.errorMessage =
            'Could not connect to the Print Farm server. Please verify the backend is running.';
        }
      },
    });
  }

  openConfirmationModal(): void {
    if (!this.validationResult || !this.validationResult.compatible || !this.selectedFile) {
      return;
    }
    // Refresh user balance right before showing dialog
    this.loadUserBalance();
    this.showConfirmDialog = true;
  }

  closeConfirmationModal(): void {
    if (this.isSubmitting) return;
    this.showConfirmDialog = false;
  }

  confirmAndSubmit(): void {
    if (!this.selectedFile || !this.selectedPrinterId || !this.validationResult) {
      return;
    }

    this.isSubmitting = true;
    const request = {
      printerId: this.selectedPrinterId,
      printerProfileId: this.validationResult.printerProfileId,
      fileName: this.selectedFile.name,
      material: this.validationResult.material,
      estimatedGrams: this.validationResult.estimatedGrams ?? 0,
      estimatedMinutes: this.validationResult.estimatedMinutes ?? 0,
    };

    this.jobService.submitJob(request).subscribe({
      next: (job) => {
        this.isSubmitting = false;
        this.showConfirmDialog = false;
        this.submissionSuccessJob = job;
        // update balance
        if (this.userBalance !== null) {
          this.userBalance = Math.max(0, this.userBalance - (job.cost || this.estimatedCost));
        }
      },
      error: (err) => {
        this.isSubmitting = false;
        const errBody = err.error;
        if (errBody && errBody.code === 'INSUFFICIENT_FUNDS') {
          alert('Insufficient balance to submit this print job. Please top up your account.');
        } else if (errBody && Array.isArray(errBody.errors)) {
          alert('Submission failed: ' + errBody.errors.join(', '));
        } else {
          alert('Failed to submit print job. Please check your connection and try again.');
        }
      },
    });
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }

  formatMinutes(mins: number | null | undefined): string {
    if (mins == null) return 'N/A';
    const m = Math.round(mins);
    const h = Math.floor(m / 60);
    const rem = m % 60;
    return h > 0 ? `${h}h ${rem}m` : `${rem}m`;
  }

  getPrinterLabel(p: PrinterOption): string {
    let label = p.name || p.displayName || p.id;
    if (p.currentMaterial) {
      label += ` — ${p.currentMaterial}`;
      if (p.currentColour) {
        label += ` (${p.currentColour})`;
      }
    }
    if (p.status) {
      label += ` · ${p.status}`;
    }
    return label;
  }

  getSelectedPrinterName(): string {
    const found = this.printers.find((p) => p.id === this.selectedPrinterId);
    return found ? (found.name || found.displayName || found.id) : this.selectedPrinterId;
  }

  startNewSubmission(): void {
    this.clearFile();
    this.submissionSuccessJob = null;
  }
}
