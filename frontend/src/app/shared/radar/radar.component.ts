import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RadarService } from '../../core/radar.service';
import { RADAR_BOUNDING_BOX } from '../../core/radar.config';

interface PlottedAircraft {
  id: string;
  callsign: string;
  xPct: number;
  yPct: number;
  altitudeFt: number | null;
  onGround: boolean;
}

const METERS_TO_FEET = 3.28084;

@Component({
  selector: 'app-radar',
  standalone: true,
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './radar.component.html',
  styleUrl: './radar.component.scss',
})
export class RadarComponent {
  private readonly radarService = inject(RadarService);

  readonly liveSource = this.radarService.liveSource;

  readonly plotted = computed<PlottedAircraft[]>(() => {
    const box = RADAR_BOUNDING_BOX;
    return this.radarService
      .aircraft()
      .filter((a) => a.latitude != null && a.longitude != null)
      .map((a) => {
        const xRaw = (a.longitude! - box.lomin) / (box.lomax - box.lomin);
        const yRaw = 1 - (a.latitude! - box.lamin) / (box.lamax - box.lamin);
        const { x, y } = this.clampToCircle(xRaw, yRaw);
        return {
          id: a.icao24,
          callsign: a.callsign?.trim() || a.icao24,
          xPct: x * 100,
          yPct: y * 100,
          altitudeFt: a.baroAltitudeM != null ? Math.round(a.baroAltitudeM * METERS_TO_FEET) : null,
          onGround: !!a.onGround,
        };
      });
  });

  readonly count = computed(() => this.plotted().length);

  readonly statusLabel = computed(() => {
    switch (this.liveSource()) {
      case 'backend':
        return 'LIVE OPENSKY FEED';
      case 'connecting':
        return 'CONNECTING';
      default:
        return 'SIMULATED';
    }
  });

  /** Keeps projected points within the outer ring instead of letting a
   * corner-of-the-bounding-box aircraft sit outside the visible circle. */
  private clampToCircle(xRaw: number, yRaw: number): { x: number; y: number } {
    const dx = xRaw - 0.5;
    const dy = yRaw - 0.5;
    const dist = Math.sqrt(dx * dx + dy * dy);
    const maxDist = 0.48;
    if (dist <= maxDist) return { x: xRaw, y: yRaw };
    const scale = maxDist / dist;
    return { x: 0.5 + dx * scale, y: 0.5 + dy * scale };
  }
}
