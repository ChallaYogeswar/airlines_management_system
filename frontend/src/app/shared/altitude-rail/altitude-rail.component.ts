import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  Renderer2,
  computed,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';

interface Tick {
  value: number;
  offset: number; // px from top of the virtual tape
}

/**
 * Left-edge altimeter tape.
 *
 * Behaviour (intentional, do not "fix" back to autoplay):
 * - It does NOT animate on its own. It only moves in direct response to the
 *   user's scroll position on the page.
 * - Top of page  -> ~10,000 ft (TOP_ALTITUDE_FT)
 * - Bottom of page -> 0 ft
 * - The tick values are NOT a clean linear countdown (10000, 9000, 8000...).
 *   They're randomised, monotonically-decreasing readouts, generated once
 *   per page load, so the tape reads like a real instrument log rather than
 *   a uniform ruler.
 */
@Component({
  selector: 'app-altitude-rail',
  standalone: true,
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './altitude-rail.component.html',
  styleUrl: './altitude-rail.component.scss',
})
export class AltitudeRailComponent implements OnInit, OnDestroy {
  private static readonly TOP_ALTITUDE_FT = 10000;
  private static readonly TICK_COUNT = 26;
  private static readonly TICK_SPACING_PX = 64;

  private readonly tapeHeight =
    (AltitudeRailComponent.TICK_COUNT - 1) * AltitudeRailComponent.TICK_SPACING_PX;

  readonly ticks = signal<Tick[]>(this.generateRandomDescendingTicks());
  readonly currentAltitude = signal<number>(AltitudeRailComponent.TOP_ALTITUDE_FT);

  private readonly scrollFraction = signal(0);

  readonly tapeOffsetPx = computed(() => {
    // How far the tape has to travel so that fraction 0 shows the top tick
    // and fraction 1 shows the bottom tick, regardless of viewport height.
    const travel = Math.max(this.tapeHeight - this.viewportHeight, 0);
    return -this.scrollFraction() * travel;
  });

  private viewportHeight = 0;
  private onScroll = () => this.updateFromScroll();
  private onResize = () => this.updateFromScroll();

  constructor(
    private readonly host: ElementRef<HTMLElement>,
    private readonly renderer: Renderer2,
  ) {}

  ngOnInit(): void {
    this.viewportHeight = window.innerHeight;
    this.updateFromScroll();
    window.addEventListener('scroll', this.onScroll, { passive: true });
    window.addEventListener('resize', this.onResize);
  }

  ngOnDestroy(): void {
    window.removeEventListener('scroll', this.onScroll);
    window.removeEventListener('resize', this.onResize);
  }

  /** Generates strictly-descending, randomly-spaced altitude readouts from
   *  TOP_ALTITUDE_FT down to 0. Random deltas are drawn then normalised so
   *  they sum exactly to TOP_ALTITUDE_FT - no two ticks land on a round
   *  multiple, and spacing between ticks is uneven on purpose. */
  private generateRandomDescendingTicks(): Tick[] {
    const n = AltitudeRailComponent.TICK_COUNT;
    const raw = Array.from({ length: n - 1 }, () => 0.4 + Math.random());
    const rawSum = raw.reduce((a, b) => a + b, 0);
    const deltas = raw.map((v) => (v / rawSum) * AltitudeRailComponent.TOP_ALTITUDE_FT);

    let remaining = AltitudeRailComponent.TOP_ALTITUDE_FT;
    const ticks: Tick[] = [];
    for (let i = 0; i < n; i++) {
      ticks.push({
        value: Math.max(0, Math.round(remaining)),
        offset: i * AltitudeRailComponent.TICK_SPACING_PX,
      });
      remaining -= deltas[i] ?? remaining;
    }
    ticks[ticks.length - 1].value = 0;
    return ticks;
  }

  private updateFromScroll(): void {
    const doc = document.documentElement;
    const maxScroll = Math.max(doc.scrollHeight - window.innerHeight, 1);
    const fraction = Math.min(Math.max(window.scrollY / maxScroll, 0), 1);
    this.scrollFraction.set(fraction);
    this.currentAltitude.set(
      Math.round(AltitudeRailComponent.TOP_ALTITUDE_FT * (1 - fraction)),
    );
  }
}
