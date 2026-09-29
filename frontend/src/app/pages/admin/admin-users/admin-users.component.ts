import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AdminService } from '../../../core/admin.service';
import { AdminUserSummary } from '../../../core/admin.model';

const AVAILABLE_ROLES = ['PASSENGER', 'STAFF', 'ADMIN'];

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.scss',
})
export class AdminUsersComponent {
  private readonly adminService = inject(AdminService);

  readonly roles = AVAILABLE_ROLES;
  readonly users = signal<AdminUserSummary[]>([]);
  readonly loading = signal(true);
  readonly savingId = signal<string | null>(null);
  readonly errorByUser = signal<Record<string, string>>({});

  constructor() {
    this.load();
  }

  hasRole(user: AdminUserSummary, role: string): boolean {
    return user.roles.includes(role);
  }

  toggleRole(user: AdminUserSummary, role: string, checked: boolean): void {
    const nextRoles = checked ? [...user.roles, role] : user.roles.filter((r) => r !== role);

    if (nextRoles.length === 0) return; // must keep at least one role

    this.savingId.set(user.id);
    this.clearError(user.id);

    this.adminService.updateUserRoles(user.id, nextRoles).subscribe({
      next: (updated) => {
        this.savingId.set(null);
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
      },
      error: (err: HttpErrorResponse) => {
        this.savingId.set(null);
        const body = err.error as { message?: string } | null;
        this.setError(user.id, body?.message ?? 'Could not update roles.');
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.adminService.listUsers(0, 100).subscribe({
      next: (page) => {
        this.users.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private setError(userId: string, message: string): void {
    this.errorByUser.update((map) => ({ ...map, [userId]: message }));
  }

  private clearError(userId: string): void {
    this.errorByUser.update((map) => {
      const { [userId]: _, ...rest } = map;
      return rest;
    });
  }
}
