import { Component, OnDestroy, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../auth.service';

interface VaseLayer {
  y: number;
  r: number;
  ry: number;
  fill: string;
  stroke: string;
}

// Accepts a UWA email (student or staff), or a bare 8-digit student number.
//   student: 24717854@student.uwa.edu.au
//   staff:   lab.coordinator@uwa.edu.au, first.last@uwa.edu.au
const UWA_EMAIL = /^[a-z0-9._%+'-]+@([a-z0-9-]+\.)*uwa\.edu\.au$/i;
const STUDENT_NUMBER = /^\d{8}$/;

export function uwaLoginValidator(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '').trim();
  if (!value) {
    return null; // empty is handled by Validators.required
  }
  return UWA_EMAIL.test(value) || STUDENT_NUMBER.test(value) ? null : { uwaEmail: true };
}

// A bare student number gets the student domain added; everything else is sent as typed (lower-cased).
function normalizeLogin(value: string): string {
  const v = value.trim().toLowerCase();
  return STUDENT_NUMBER.test(v) ? `${v}@student.uwa.edu.au` : v;
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent implements OnInit, OnDestroy {
  loginForm: FormGroup;
  errorMessage: string | null = null;
  isSubmitting = false;
  showPassword = false;

  // ---- Vase animation (decorative) ----
  private readonly layerCount = 46;
  private readonly baseY = 430;
  private readonly layerStep = 7.2;
  layers: VaseLayer[] = this.buildLayers();
  printed = 0; // how many layers are visible right now
  nozzleTransform = 'translate(200px, 430px)';
  animate = true;
  private timer?: ReturnType<typeof setInterval>;
  private hold = 0;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, uwaLoginValidator]],
      password: ['', [Validators.required]],
    });
  }

  get email(): AbstractControl | null {
    return this.loginForm.get('email');
  }

  get password(): AbstractControl | null {
    return this.loginForm.get('password');
  }

  ngOnInit(): void {
    const reduceMotion =
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

    if (reduceMotion) {
      // Show the finished vase, no nozzle, no timer
      this.animate = false;
      this.printed = this.layerCount;
      return;
    }
    this.timer = setInterval(() => this.tick(), 120);
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.errorMessage = null;
    this.isSubmitting = true;

    const { email, password } = this.loginForm.value;

    this.authService.login(normalizeLogin(email), password).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.isSubmitting = false;
        this.errorMessage =
          err.status === 401
            ? 'Incorrect email or password.'
            : 'Something went wrong. Please try again.';
      },
    });
  }

  // ---- helpers ----
  private buildLayers(): VaseLayer[] {
    const out: VaseLayer[] = [];
    for (let i = 0; i < this.layerCount; i++) {
      const t = i / (this.layerCount - 1);
      const r = 52 + 44 * Math.sin(Math.PI * Math.pow(t, 0.8));
      out.push({
        y: this.baseY - i * this.layerStep,
        r,
        ry: r * 0.24,
        fill: `hsl(208 60% ${26 + t * 12}%)`,
        stroke: `hsl(205 75% ${56 + t * 18}%)`,
      });
    }
    return out;
  }

  private tick(): void {
    if (this.printed < this.layerCount + 1) {
      this.printed++;
    } else if (++this.hold > 22) {
      // pause on the finished vase, then start over
      this.hold = 0;
      this.printed = 0;
    }
    this.moveNozzle();
  }

  private moveNozzle(): void {
    let x = 200;
    let y = this.baseY;
    if (this.printed > this.layerCount) {
      y = this.layers[this.layerCount - 1].y - 30;
    } else if (this.printed > 0) {
      const layer = this.layers[this.printed - 1];
      x = 200 + layer.r * 0.7 * Math.sin(this.printed * 1.9);
      y = layer.y;
    }
    this.nozzleTransform = `translate(${x}px, ${y}px)`;
  }
}