import { Injectable, OnDestroy, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AircraftPosition } from './aircraft.model';
import { RealtimeService } from './realtime.service';
import { backendConfig } from './backend.config';
import { RADAR_BOUNDING_BOX } from './radar.config';

export type LiveSource = 'connecting' | 'backend' | 'simulated';

/**
 * Live aircraft-position feed for the radar view.
 *
 * Same pattern as FlightBoardService: REST snapshot + WS subscription
 * first, local fallback if the backend doesn't answer in time. The
 * difference here is what the fallback actually simulates - there's no
 * believable way to fake individual flight lifecycles the way the board
 * does, so instead this drifts a handful of aircraft in straight lines
 * within RADAR_BOUNDING_BOX and bounces them off the edges, which reads
 * as "live traffic" without pretending to be real ADS-B data.
 */
@Injectable({ providedIn: 'root' })
export class RadarService implements OnDestroy {
  readonly aircraft = signal<AircraftPosition[]>(this.seedSimulated());
  readonly liveSource = signal<LiveSource>('connecting');

  private fallbackTimer: ReturnType<typeof setInterval> | null = null;
  private connectTimeoutHandle: ReturnType<typeof setTimeout> | null = null;
  private wsSub: Subscription | null = null;

  constructor(
    private readonly http: HttpClient,
    private readonly realtime: RealtimeService,
  ) {
    this.connect();
  }

  ngOnDestroy(): void {
    this.wsSub?.unsubscribe();
    this.stopFallback();
    if (this.connectTimeoutHandle) clearTimeout(this.connectTimeoutHandle);
  }

  private connect(): void {
    this.connectTimeoutHandle = setTimeout(() => {
      if (this.liveSource() !== 'backend') this.startFallback();
    }, backendConfig.connectTimeoutMs);

    this.http.get<AircraftPosition[]>(`${backendConfig.httpBase}/api/radar`).subscribe({
      next: (states) => {
        if (states.length > 0) this.aircraft.set(states);
      },
      error: () => {},
    });

    this.wsSub = this.realtime.subscribe<AircraftPosition[]>('/topic/radar').subscribe({
      next: (states) => {
        this.liveSource.set('backend');
        this.stopFallback();
        this.aircraft.set(states);
      },
    });
  }

  private startFallback(): void {
    if (this.fallbackTimer) return;
    this.liveSource.set('simulated');
    this.fallbackTimer = setInterval(() => this.driftSimulated(), 2000);
  }

  private stopFallback(): void {
    if (this.fallbackTimer) {
      clearInterval(this.fallbackTimer);
      this.fallbackTimer = null;
    }
  }

  private seedSimulated(): AircraftPosition[] {
    const seeds = [
      { icao24: 'sim01', callsign: 'SEJ757', lat: 13.6, lon: 79.1, heading: 300 },
      { icao24: 'sim02', callsign: 'AKJ123', lat: 15.9, lon: 78.0, heading: 60 },
      { icao24: 'sim03', callsign: 'IND667', lat: 16.4, lon: 80.4, heading: 210 },
      { icao24: 'sim04', callsign: 'AIC667', lat: 14.2, lon: 81.9, heading: 140 },
    ];
    return seeds.map((s) => ({
      icao24: s.icao24,
      callsign: s.callsign,
      originCountry: 'India',
      longitude: s.lon,
      latitude: s.lat,
      baroAltitudeM: 8500 + Math.random() * 3500,
      onGround: false,
      velocityMs: 190 + Math.random() * 60,
      trueTrackDeg: s.heading,
      verticalRateMs: 0,
    }));
  }

  /** Straight-line drift with edge bounce, kept inside RADAR_BOUNDING_BOX
   * so simulated aircraft never wander off the display. */
  private driftSimulated(): void {
    const box = RADAR_BOUNDING_BOX;
    const step = 0.05;

    this.aircraft.set(
      this.aircraft().map((a) => {
        let heading = a.trueTrackDeg ?? 0;
        const rad = (heading * Math.PI) / 180;
        let lat = (a.latitude ?? 0) + Math.cos(rad) * step;
        let lon = (a.longitude ?? 0) + Math.sin(rad) * step;

        if (lat < box.lamin || lat > box.lamax) {
          heading = (180 - heading + 360) % 360;
          lat = Math.max(box.lamin, Math.min(box.lamax, lat));
        }
        if (lon < box.lomin || lon > box.lomax) {
          heading = (360 - heading) % 360;
          lon = Math.max(box.lomin, Math.min(box.lomax, lon));
        }

        return { ...a, latitude: lat, longitude: lon, trueTrackDeg: heading };
      }),
    );
  }
}
