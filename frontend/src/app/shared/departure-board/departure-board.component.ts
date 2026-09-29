import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { animate, style, transition, trigger } from '@angular/animations';
import { FlightBoardService } from '../../core/flight-board.service';
import { STATUS_META } from '../../core/flight.model';

@Component({
  selector: 'app-departure-board',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './departure-board.component.html',
  styleUrl: './departure-board.component.scss',
  animations: [
    trigger('flap', [
      transition('* => *', [
        style({ transform: 'rotateX(-90deg)', opacity: 0.3 }),
        animate('340ms cubic-bezier(.2,.7,.3,1)', style({ transform: 'rotateX(0deg)', opacity: 1 })),
      ]),
    ]),
  ],
})
export class DepartureBoardComponent {
  private readonly board = inject(FlightBoardService);

  readonly flights = this.board.flights;
  readonly liveSource = this.board.liveSource;
  readonly statusMeta = STATUS_META;

  trackByFlightId(_index: number, flight: { id: string }): string {
    return flight.id;
  }
}
