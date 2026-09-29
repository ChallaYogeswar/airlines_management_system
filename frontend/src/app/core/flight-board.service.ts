import { Injectable, OnDestroy, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { Flight, FlightStatus, NEXT_STATES } from './flight.model';
import { backendConfig } from './backend.config';
import { RealtimeService } from './realtime.service';

export type LiveSource = 'connecting' | 'backend' | 'simulated';

/**
 * Live flight-ops feed.
 *
 * Fetches an initial snapshot over REST, then subscribes to
 * /topic/flights via the shared RealtimeService for live pushes from the
 * server-side FlightStatusEngine. If the backend never answers within
 * connectTimeoutMs - most likely because it isn't running locally - this
 * falls back to running the exact same state-machine logic client-side,
 * so the board still feels alive standalone. Whichever source is active,
 * the component and template are unaware of the difference; they just
 * read the `flights` signal.
 */
@Injectable({ providedIn: 'root' })
export class FlightBoardService implements OnDestroy {
  readonly flights = signal<Flight[]>(this.seed());
  readonly liveSource = signal<LiveSource>('connecting');

  private fallbackTimer: ReturnType<typeof setInterval> | null = null;
  private connectTimeoutHandle: ReturnType<typeof setTimeout> | null = null;
  private wsSub: Subscription | null = null;

  constructor(
    private readonly http: HttpClient,
    private readonly realtime: RealtimeService,
  ) {
    this.connectToBackend();
  }

  ngOnDestroy(): void {
    this.wsSub?.unsubscribe();
    this.stopFallbackSimulation();
    if (this.connectTimeoutHandle) clearTimeout(this.connectTimeoutHandle);
  }

  private connectToBackend(): void {
    this.connectTimeoutHandle = setTimeout(() => {
      if (this.liveSource() !== 'backend') {
        this.startFallbackSimulation();
      }
    }, backendConfig.connectTimeoutMs);

    this.http.get<Flight[]>(`${backendConfig.httpBase}/api/flights`).subscribe({
      next: (flights) => this.flights.set(flights),
      error: () => {
        // Backend REST not reachable yet - the WS subscription below still
        // gets its own chance; the timeout above is the real fallback
        // trigger either way.
      },
    });

    this.wsSub = this.realtime.subscribe<Flight[]>('/topic/flights').subscribe({
      next: (flights) => {
        this.liveSource.set('backend');
        this.stopFallbackSimulation();
        this.flights.set(flights);
      },
    });
  }

  private startFallbackSimulation(): void {
    if (this.fallbackTimer) return;
    this.liveSource.set('simulated');
    this.fallbackTimer = setInterval(() => this.tick(), 3200);
  }

  private stopFallbackSimulation(): void {
    if (this.fallbackTimer) {
      clearInterval(this.fallbackTimer);
      this.fallbackTimer = null;
    }
  }

  private seed(): Flight[] {
    const seedRows: Omit<Flight, 'status' | 'updatedAt'>[] = [
      { id: 'f1', time: '06:40', flightNumber: 'SpiceJet 757', destination: 'Hyderabad', gate: 'A14' },
      { id: 'f2', time: '07:10', flightNumber: 'IndiGo 340', destination: 'Mumbai', gate: 'B02' },
      { id: 'f3', time: '08:15', flightNumber: 'Akasa 123', destination: 'Bengaluru', gate: 'B03' },
      { id: 'f4', time: '09:05', flightNumber: 'Indigo 667', destination: 'Hyderabad', gate: 'A09' },
      { id: 'f5', time: '09:40', flightNumber: 'Air India 667', destination: 'Hyderabad', gate: 'C02' },
      { id: 'f6', time: '10:20', flightNumber: 'Vistara 220', destination: 'Kolkata', gate: 'A02' },
    ];
    return seedRows.map((f) => ({
      ...f,
      status: 'on-time' as FlightStatus,
      updatedAt: Date.now(),
    }));
  }

  /** Local twin of the backend's FlightStatusEngine.tick() - same states,
   * same transition odds. Only runs when the backend isn't reachable. */
  private tick(): void {
    const flights = this.flights();
    const movable = flights.filter((f) => NEXT_STATES[f.status].length > 0);
    if (movable.length === 0) return;

    const target = movable[Math.floor(Math.random() * movable.length)];
    const nextStatus = this.pickNextStatus(target.status);

    const delayMinutes =
      nextStatus === 'delayed' ? 10 + Math.floor(Math.random() * 50) : target.delayMinutes;

    this.flights.set(
      flights.map((f) =>
        f.id === target.id
          ? { ...f, status: nextStatus, delayMinutes, updatedAt: Date.now() }
          : f,
      ),
    );
  }

  private pickNextStatus(current: FlightStatus): FlightStatus {
    const options = NEXT_STATES[current];
    if (current === 'on-time') {
      return Math.random() < 0.2 ? 'delayed' : 'boarding';
    }
    return options[0];
  }
}
