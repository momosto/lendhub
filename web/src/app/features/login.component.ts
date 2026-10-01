import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { DemoUser } from '../core/models';
import { UI } from '../core/shared';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [UI],
  template: `
    <div class="login">
      <div class="card">
        <h1>LendHub</h1>
        <p class="muted">Loan management for InsureHub Microfinance (fictional). Sign in as one of the demo staff —
          each role sees different screens, and the API enforces the same rules.</p>
        <form (ngSubmit)="signIn()" class="form">
          <mat-form-field>
            <mat-label>User</mat-label>
            <mat-select [(ngModel)]="username" name="username" required>
              @for (u of users(); track u.username) {
                <mat-option [value]="u.username">{{ u.displayName }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Password</mat-label>
            <input matInput type="password" [(ngModel)]="password" name="password" required>
            <mat-hint>All demo users: Demo123!</mat-hint>
          </mat-form-field>
          <button mat-flat-button color="primary" [disabled]="busy() || !username">Sign in</button>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .login { display: flex; justify-content: center; padding-top: 8vh; }
    .card { width: 420px; max-width: calc(100vw - 32px); }
    .form { display: flex; flex-direction: column; gap: 8px; }
  `],
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly users = signal<DemoUser[]>([]);
  readonly busy = signal(false);
  username = 'officer@lendhub.demo';
  password = 'Demo123!';

  ngOnInit(): void {
    this.auth.demoUsers().subscribe(u => this.users.set(u.filter(x => !x.roles.includes('CHANNEL'))));
  }

  signIn(): void {
    this.busy.set(true);
    this.auth.login(this.username, this.password).subscribe({
      next: () => this.router.navigate(['/']),
      error: () => this.busy.set(false),
    });
  }
}
