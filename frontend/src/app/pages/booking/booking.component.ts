import { ChangeDetectionStrategy, Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FlightSearchService } from '../../core/flight-search.service';
import { BookingService } from '../../core/booking.service';
import { SeatService } from '../../core/seat.service';
import { PaymentService } from '../../core/payment.service';
import { BookingSummary, FlightOfferSummary } from '../../core/booking.model';
import { SeatSummary } from '../../core/seat.model';
import { SeatMapComponent } from '../../shared/seat-map/seat-map.component';

const MAX_PASSENGERS = 9;
const BUSINESS_MULTIPLIER = 1.6;

type Step = 'seats' | 'details' | 'payment' | 'confirmed';

@Component({
  selector: 'app-booking',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe, RouterLink, SeatMapComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './booking.component.html',
  styleUrl: './booking.component.scss',
})
export class BookingComponent implements OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly searchService = inject(FlightSearchService);
  private readonly bookingService = inject(BookingService);
  private readonly seatService = inject(SeatService);
  private readonly paymentService = inject(PaymentService);

  private readonly flightOfferId = this.route.snapshot.paramMap.get('flightOfferId')!;

  readonly step = signal<Step>('seats');
  readonly flight = signal<FlightOfferSummary | null>(null);
  readonly loadingFlight = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly seats = signal<SeatSummary[]>([]);
  readonly loadingSeats = signal(true);
  readonly selectedSeats = signal<SeatSummary[]>([]);
  readonly seatError = signal<string | null>(null);
  readonly holdBusy = signal(false);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly pendingBooking = signal<BookingSummary | null>(null);
  readonly paying = signal(false);
  readonly declineReason = signal<string | null>(null);
  readonly confirmed = signal<BookingSummary | null>(null);

  readonly form = this.fb.group({ passengers: this.fb.array<FormGroup>([]) });

  readonly paymentForm = this.fb.group({
    cardholderName: ['', Validators.required],
    cardNumber: ['', [Validators.required, Validators.pattern(/^[\d\s-]{12,23}$/)]],
    expiryMonth: ['', [Validators.required, Validators.pattern(/^(0[1-9]|1[0-2])$/)]],
    expiryYear: ['', [Validators.required, Validators.pattern(/^\d{2}(\d{2})?$/)]],
    cvv: ['', [Validators.required, Validators.pattern(/^\d{3,4}$/)]],
  });

  readonly selectedSeatIds = computed(() => this.selectedSeats().map((s) => s.id));

  readonly totalPrice = computed(() => {
    const flight = this.flight();
    if (!flight) return 0;
    return this.selectedSeats().reduce(
      (sum, seat) => sum + flight.price * (seat.seatClass === 'BUSINESS' ? BUSINESS_MULTIPLIER : 1),
      0,
    );
  });

  get passengers(): FormArray<FormGroup> {
    return this.form.get('passengers') as FormArray<FormGroup>;
  }

  constructor() {
    this.searchService.getById(this.flightOfferId).subscribe({
      next: (offer) => {
        this.flight.set(offer);
        this.loadingFlight.set(false);
      },
      error: () => {
        this.loadingFlight.set(false);
        this.loadError.set('This flight could not be found — it may no longer be available.');
      },
    });
    this.loadSeats();
  }

  ngOnDestroy(): void {
    // Best-effort cleanup for an abandoned checkout. If this fails (page
    // closing, network gone), the backend's own hold-expiry sweep clears
    // it within a few minutes regardless.
    if (this.confirmed()) return;

    const booking = this.pendingBooking();
    if (booking) {
      // A PENDING_PAYMENT/PAYMENT_FAILED booking exists but was never
      // paid - cancelling it also releases its seats, so there's nothing
      // else to clean up separately.
      this.bookingService.cancel(booking.id).subscribe({ error: () => {} });
      return;
    }

    for (const seat of this.selectedSeats()) {
      this.seatService.release(this.flightOfferId, seat.id).subscribe({ error: () => {} });
    }
  }

  toggleSeat(seat: SeatSummary): void {
    const alreadySelected = this.selectedSeatIds().includes(seat.id);
    this.seatError.set(null);

    if (alreadySelected) {
      this.holdBusy.set(true);
      this.seatService.release(this.flightOfferId, seat.id).subscribe({
        next: () => {
          this.holdBusy.set(false);
          this.selectedSeats.update((seats) => seats.filter((s) => s.id !== seat.id));
        },
        error: () => this.holdBusy.set(false),
      });
      return;
    }

    if (this.selectedSeats().length >= MAX_PASSENGERS) return;

    this.holdBusy.set(true);
    this.seatService.hold(this.flightOfferId, [seat.id]).subscribe({
      next: (result) => {
        this.holdBusy.set(false);
        this.selectedSeats.update((seats) => [...seats, ...result.heldSeats]);
      },
      error: (err: HttpErrorResponse) => {
        this.holdBusy.set(false);
        const body = err.error as { message?: string } | null;
        this.seatError.set(body?.message ?? 'That seat was just taken — pick another.');
        this.loadSeats();
      },
    });
  }

  proceedToDetails(): void {
    if (this.selectedSeats().length === 0) return;
    this.passengers.clear();
    for (const _ of this.selectedSeats()) {
      this.passengers.push(this.buildPassengerGroup());
    }
    this.step.set('details');
  }

  backToSeats(): void {
    this.step.set('seats');
  }

  submitDetails(): void {
    const flight = this.flight();
    if (!flight || this.form.invalid) return;

    this.submitting.set(true);
    this.errorMessage.set(null);

    const seats = this.selectedSeats();
    const passengers = this.passengers.controls.map((group, i) => ({
      firstName: group.get('firstName')!.value as string,
      lastName: group.get('lastName')!.value as string,
      dateOfBirth: group.get('dateOfBirth')!.value as string,
      seatId: seats[i].id,
    }));

    this.bookingService.create({ flightOfferId: flight.id, passengers }).subscribe({
      next: (booking) => {
        this.submitting.set(false);
        this.pendingBooking.set(booking);
        this.step.set('payment');
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        const body = err.error as { message?: string } | null;
        this.errorMessage.set(body?.message ?? 'Could not create the booking — try again.');
      },
    });
  }

  submitPayment(): void {
    const booking = this.pendingBooking();
    if (!booking || this.paymentForm.invalid) return;

    this.paying.set(true);
    this.declineReason.set(null);
    const value = this.paymentForm.getRawValue();

    this.paymentService
      .pay(booking.id, {
        cardholderName: value.cardholderName!,
        cardNumber: value.cardNumber!,
        expiryMonth: value.expiryMonth!,
        expiryYear: value.expiryYear!,
        cvv: value.cvv!,
      })
      .subscribe({
        next: (result) => {
          this.paying.set(false);
          if (result.success) {
            this.confirmed.set(result.booking);
            this.step.set('confirmed');
          } else {
            this.pendingBooking.set(result.booking); // now PAYMENT_FAILED
            this.declineReason.set(result.declineReason ?? 'Payment was declined.');
          }
        },
        error: (err: HttpErrorResponse) => {
          this.paying.set(false);
          const body = err.error as { message?: string } | null;
          this.declineReason.set(body?.message ?? 'Payment could not be processed — try again.');
        },
      });
  }

  seatLabel(index: number): string {
    return this.selectedSeats()[index]?.seatNumber ?? '';
  }

  private buildPassengerGroup(): FormGroup {
    return this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      dateOfBirth: ['', Validators.required],
    });
  }

  private loadSeats(): void {
    this.loadingSeats.set(true);
    this.seatService.list(this.flightOfferId).subscribe({
      next: (seats) => {
        this.seats.set(seats);
        this.loadingSeats.set(false);
      },
      error: () => this.loadingSeats.set(false),
    });
  }
}
