import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly step = signal<'credentials' | 'mfa'>('credentials');
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private mfaChallenge: string | null = null;

  readonly credentialsForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  readonly mfaForm = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  submitCredentials(): void {
    if (this.credentialsForm.invalid) return;
    this.loading.set(true);
    this.errorMessage.set(null);
    const { email, password } = this.credentialsForm.getRawValue();

    this.authService.login(email!, password!, this.deviceName()).subscribe({
      next: (result) => {
        this.loading.set(false);
        if (result.requiresMfa && result.mfaChallenge) {
          this.mfaChallenge = result.mfaChallenge;
          this.step.set('mfa');
        } else {
          this.router.navigateByUrl('/');
        }
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(this.extractMessage(err, 'Invalid email or password.'));
      },
    });
  }

  submitMfaCode(): void {
    if (this.mfaForm.invalid || !this.mfaChallenge) return;
    this.loading.set(true);
    this.errorMessage.set(null);
    const { code } = this.mfaForm.getRawValue();

    this.authService.verifyMfa(this.mfaChallenge, code!).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigateByUrl('/');
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(this.extractMessage(err, 'Invalid authentication code.'));
      },
    });
  }

  backToCredentials(): void {
    this.step.set('credentials');
    this.errorMessage.set(null);
    this.mfaForm.reset();
  }

  private deviceName(): string {
    return typeof navigator !== 'undefined' ? navigator.userAgent.slice(0, 60) : 'web';
  }

  private extractMessage(err: HttpErrorResponse, fallback: string): string {
    const body = err.error as { message?: string } | null;
    return body?.message ?? fallback;
  }
}
