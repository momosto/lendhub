import { Component, computed, effect, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { ApiService } from './core/api.service';
import { AuthService } from './core/auth.service';
import { Role } from './core/models';

interface NavItem { path: string; label: string; icon: string; roles?: Role[]; }

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatSidenavModule, MatListModule, MatButtonModule, MatIconModule],
  template: `
    <div class="demo-banner">Fictional demo — InsureHub Microfinance is not a real lender. Do not enter real personal data.</div>
    @if (auth.signedIn()) {
      <mat-toolbar color="primary">
        <span class="brand">LendHub</span>
        <span class="muted">InsureHub Microfinance back office</span>
        <span class="spacer"></span>
        <span class="date">Business date: <strong>{{ businessDate() ?? '…' }}</strong></span>
        <span class="who">{{ auth.user()?.displayName }}</span>
        <button mat-button (click)="auth.logout()">Sign out</button>
      </mat-toolbar>
      <mat-sidenav-container>
        <mat-sidenav mode="side" opened>
          <mat-nav-list>
            @for (item of nav(); track item.path) {
              <a mat-list-item [routerLink]="item.path" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: item.path === '/' }">
                <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
                <span matListItemTitle>{{ item.label }}</span>
              </a>
            }
          </mat-nav-list>
        </mat-sidenav>
        <mat-sidenav-content><main><router-outlet /></main></mat-sidenav-content>
      </mat-sidenav-container>
    } @else {
      <router-outlet />
    }
  `,
  styles: [`
    .brand { font-weight: 700; margin-right: 12px; }
    .muted { opacity: .75; font-size: 14px; }
    .spacer { flex: 1; }
    .date, .who { font-size: 14px; margin-right: 16px; }
    mat-sidenav-container { height: calc(100vh - 64px - 28px); }
    mat-sidenav { width: 220px; }
    main { padding: 24px; max-width: 1400px; }
    .active { background: rgba(0,0,0,.06); }
    @media (max-width: 800px) { .muted, .who { display: none; } mat-sidenav { width: 64px; } }
  `],
})
export class AppComponent {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  readonly businessDate = signal<string | null>(null);

  private readonly items: NavItem[] = [
    { path: '/', label: 'Dashboard', icon: 'insights' },
    { path: '/borrowers', label: 'Borrowers', icon: 'group', roles: ['OFFICER', 'MANAGER', 'COMMITTEE', 'FINANCE', 'COLLECTIONS', 'AUDITOR'] },
    { path: '/applications', label: 'Applications', icon: 'assignment', roles: ['OFFICER', 'MANAGER', 'COMMITTEE', 'FINANCE', 'AUDITOR'] },
    { path: '/loans', label: 'Loans', icon: 'account_balance_wallet', roles: ['OFFICER', 'MANAGER', 'COMMITTEE', 'FINANCE', 'COLLECTIONS', 'AUDITOR'] },
    { path: '/collections', label: 'Collections', icon: 'phone_in_talk', roles: ['COLLECTIONS', 'MANAGER', 'AUDITOR'] },
    { path: '/committee', label: 'Committee', icon: 'gavel', roles: ['COMMITTEE', 'MANAGER', 'COLLECTIONS', 'AUDITOR'] },
    { path: '/finance', label: 'Finance & EOD', icon: 'calculate', roles: ['FINANCE', 'AUDITOR'] },
  ];

  readonly nav = computed(() => this.auth.signedIn()
    ? this.items.filter(i => !i.roles || this.auth.hasAnyRole(...i.roles)) : []);

  constructor() {
    effect(() => {
      if (this.auth.signedIn()) this.refreshDate();
    }, { allowSignalWrites: true });
  }

  refreshDate(): void {
    this.api.businessDate().subscribe(d => this.businessDate.set(d.businessDate));
  }
}
