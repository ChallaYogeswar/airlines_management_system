import { Component } from '@angular/core';
import { DepartureBoardComponent } from '../../shared/departure-board/departure-board.component';
import { RadarComponent } from '../../shared/radar/radar.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [DepartureBoardComponent, RadarComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {}
