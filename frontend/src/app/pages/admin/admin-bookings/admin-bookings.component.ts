import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { AdminService } from '../../../core/admin.service';
import { BookingSummary, BookingStatus } from '../../../core/booking.model';

const STATUS_LABELS: Record<BookingStatus, string> = {
  PENDING_PAYMENT: 'Awaiting payment',
  PAYMENT_FAILED: 'Payment failed',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
};

@Component({
  selector: 'app-admin-bookings',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-bookings.component.html',
  styleUrl: './admin-bookings.component.scss',
})
export class AdminBookingsComponent {
  private readonly adminService = inject(AdminService);

  readonly bookings = signal<BookingSummary[]>([]);
  readonly loading = signal(true);

  statusLabel(status: BookingStatus): string {
    return STATUS_LABELS[status];
  }

  constructor() {
    this.adminService.listAllBookings(0, 100).subscribe({
      next: (page) => {
        this.bookings.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
