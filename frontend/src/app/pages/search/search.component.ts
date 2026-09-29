import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FlightSearchService } from '../../core/flight-search.service';
import { FlightOfferSummary } from '../../core/booking.model';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './search.component.html',
  styleUrl: './search.component.scss',
})
export class SearchComponent {
  private readonly fb = inject(FormBuilder);
  private readonly searchService = inject(FlightSearchService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly searched = signal(false);
  readonly results = signal<FlightOfferSummary[]>([]);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    origin: ['MAA', [Validators.required, Validators.minLength(3)]],
    destination: ['HYD', [Validators.required, Validators.minLength(3)]],
    date: [this.todayIso(), [Validators.required]],
  });

  search(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.errorMessage.set(null);
    const { origin, destination, date } = this.form.getRawValue();

    this.searchService.search(origin!.trim(), destination!.trim(), date!).subscribe({
      next: (offers) => {
        this.loading.set(false);
        this.searched.set(true);
        this.results.set(offers);
      },
      error: () => {
        this.loading.set(false);
        this.searched.set(true);
        this.errorMessage.set('Could not search flights right now — try again shortly.');
      },
    });
  }

  selectFlight(offer: FlightOfferSummary): void {
    this.router.navigate(['/book', offer.id]);
  }

  private todayIso(): string {
    return new Date().toISOString().slice(0, 10);
  }
}
