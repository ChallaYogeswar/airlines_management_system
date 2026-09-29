import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { AdminFlightsComponent } from './admin-flights/admin-flights.component';
import { AdminUsersComponent } from './admin-users/admin-users.component';
import { AdminBookingsComponent } from './admin-bookings/admin-bookings.component';

type AdminTab = 'flights' | 'users' | 'bookings';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [AdminFlightsComponent, AdminUsersComponent, AdminBookingsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
})
export class AdminComponent {
  readonly tab = signal<AdminTab>('flights');

  setTab(tab: AdminTab): void {
    this.tab.set(tab);
  }
}
