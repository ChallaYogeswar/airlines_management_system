import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AdminService } from '../../../core/admin.service';
import { AdminFlightOfferSummary } from '../../../core/admin.model';

@Component({
  selector: 'app-admin-flights',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-flights.component.html',
  styleUrl: './admin-flights.component.scss',
})
export class AdminFlightsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly adminService = inject(AdminService);

  readonly flights = signal<AdminFlightOfferSummary[]>([]);
  readonly loading = signal(true);
  readonly showCreateForm = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly savingId = signal<string | null>(null);
  readonly deletingId = signal<string | null>(null);

  readonly createForm = this.fb.group({
    flightNumber: ['', Validators.required],
    origin: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
    destination: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
    departureTime: ['', Validators.required],
    arrivalTime: ['', Validators.required],
    price: [0, [Validators.required, Validators.min(0)]],
    totalSeats: [1, [Validators.required, Validators.min(1)]],
  });

  readonly editForm = this.fb.group({
    price: [0, [Validators.required, Validators.min(0)]],
    totalSeats: [1, [Validators.required, Validators.min(1)]],
    departureTime: ['', Validators.required],
    arrivalTime: ['', Validators.required],
  });

  constructor() {
    this.load();
  }

  toggleCreateForm(): void {
    this.showCreateForm.update((v) => !v);
    this.errorMessage.set(null);
  }

  submitCreate(): void {
    if (this.createForm.invalid) return;
    const value = this.createForm.getRawValue();
    this.errorMessage.set(null);

    this.adminService
      .createFlight({
        flightNumber: value.flightNumber!,
        origin: value.origin!.toUpperCase(),
        destination: value.destination!.toUpperCase(),
        departureTime: new Date(value.departureTime!).toISOString(),
        arrivalTime: new Date(value.arrivalTime!).toISOString(),
        price: value.price!,
        totalSeats: value.totalSeats!,
      })
      .subscribe({
        next: () => {
          this.createForm.reset({ price: 0, totalSeats: 1 });
          this.showCreateForm.set(false);
          this.load();
        },
        error: (err: HttpErrorResponse) => this.setError(err, 'Could not create the flight.'),
      });
  }

  startEdit(offer: AdminFlightOfferSummary): void {
    this.editingId.set(offer.id);
    this.errorMessage.set(null);
    this.editForm.setValue({
      price: offer.price,
      totalSeats: offer.totalSeats,
      departureTime: toDateTimeLocal(offer.departureTime),
      arrivalTime: toDateTimeLocal(offer.arrivalTime),
    });
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  submitEdit(id: string): void {
    if (this.editForm.invalid) return;
    this.savingId.set(id);
    this.errorMessage.set(null);
    const value = this.editForm.getRawValue();

    this.adminService
      .updateFlight(id, {
        price: value.price!,
        totalSeats: value.totalSeats!,
        departureTime: new Date(value.departureTime!).toISOString(),
        arrivalTime: new Date(value.arrivalTime!).toISOString(),
      })
      .subscribe({
        next: () => {
          this.savingId.set(null);
          this.editingId.set(null);
          this.load();
        },
        error: (err: HttpErrorResponse) => {
          this.savingId.set(null);
          this.setError(err, 'Could not update the flight.');
        },
      });
  }

  deleteFlight(offer: AdminFlightOfferSummary): void {
    if (!confirm(`Delete ${offer.flightNumber} (${offer.origin} → ${offer.destination})? This can't be undone.`)) {
      return;
    }
    this.deletingId.set(offer.id);
    this.adminService.deleteFlight(offer.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.deletingId.set(null);
        this.setError(err, 'Could not delete the flight.');
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.adminService.listFlights(0, 100).subscribe({
      next: (page) => {
        this.flights.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private setError(err: HttpErrorResponse, fallback: string): void {
    const body = err.error as { message?: string } | null;
    this.errorMessage.set(body?.message ?? fallback);
  }
}

/** <input type="datetime-local"> wants "yyyy-MM-ddTHH:mm" with no
 * timezone suffix, in local time - not the ISO string the backend sends. */
function toDateTimeLocal(isoInstant: string): string {
  const d = new Date(isoInstant);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
