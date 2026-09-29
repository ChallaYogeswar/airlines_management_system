import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { SeatSummary } from '../../core/seat.model';

interface SeatRow {
  label: string;
  seats: SeatSummary[];
}

@Component({
  selector: 'app-seat-map',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './seat-map.component.html',
  styleUrl: './seat-map.component.scss',
})
export class SeatMapComponent {
  readonly seats = input.required<SeatSummary[]>();
  readonly selectedIds = input<string[]>([]);
  readonly seatClicked = output<SeatSummary>();

  private readonly columnsPerRow = 4;

  readonly rows = computed<SeatRow[]>(() => {
    const seats = this.seats();
    const rows: SeatRow[] = [];
    for (let i = 0; i < seats.length; i += this.columnsPerRow) {
      const rowSeats = seats.slice(i, i + this.columnsPerRow);
      const rowNumber = i / this.columnsPerRow + 1;
      rows.push({ label: String(rowNumber), seats: rowSeats });
    }
    return rows;
  });

  seatState(seat: SeatSummary): 'selected' | 'available' | 'taken' {
    if (this.selectedIds().includes(seat.id) || seat.heldByMe) return 'selected';
    if (seat.status === 'AVAILABLE') return 'available';
    return 'taken';
  }

  onSeatClick(seat: SeatSummary): void {
    if (this.seatState(seat) === 'taken') return;
    this.seatClicked.emit(seat);
  }
}
