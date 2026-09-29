import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { BookingService } from '../../core/booking.service';
import { BookingSummary, BookingStatus } from '../../core/booking.model';

const STATUS_LABELS: Record<BookingStatus, string> = {
  PENDING_PAYMENT: 'Awaiting payment',
  PAYMENT_FAILED: 'Payment failed',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
};

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './my-bookings.component.html',
  styleUrl: './my-bookings.component.scss',
})
export class MyBookingsComponent {
  private readonly bookingService = inject(BookingService);

  readonly bookings = signal<BookingSummary[]>([]);
  readonly loading = signal(true);
  readonly cancellingId = signal<string | null>(null);

  constructor() {
    this.load();
  }

  statusLabel(status: BookingStatus): string {
    return STATUS_LABELS[status];
  }

  cancel(booking: BookingSummary): void {
    this.cancellingId.set(booking.id);
    this.bookingService.cancel(booking.id).subscribe({
      next: () => {
        this.cancellingId.set(null);
        this.load();
      },
      error: () => this.cancellingId.set(null),
    });
  }

  private load(): void {
    this.loading.set(true);
    this.bookingService.listMine().subscribe({
      next: (bookings) => {
        this.bookings.set(bookings);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
