import { Component, OnInit, inject, signal } from '@angular/core';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ChangeRequest } from '../core/models';
import { UI } from '../core/shared';

/** LH-62/63: the credit committee decides restructures and write-offs (requester ≠ approver). */
@Component({
  selector: 'app-committee',
  standalone: true,
  imports: [UI],
  template: `
    <h1>Credit committee</h1>
    <p class="muted">Applications above US$1,000, grade E and related-party flags are decided from the
      <a routerLink="/applications">applications</a> list. Restructures and write-offs are below.</p>
    @for (c of requests(); track c.id) {
      <div class="card">
        <strong>{{ c.type === 'RESTRUCTURE' ? 'Restructure to ' + c.newInstalments + ' instalments' : 'Write-off' }}</strong>
        · <a [routerLink]="['/loans', c.loanId]">{{ c.loanNumber }}</a>
        <div class="muted">Requested by {{ c.requestedBy }} on {{ c.requestedAt | date: 'medium' }}: {{ c.reason }}</div>
        @if (auth.hasAnyRole('COMMITTEE')) {
          <div class="actions">
            <button mat-flat-button color="primary" (click)="decide(c, true)">Approve</button>
            <button mat-stroked-button color="warn" (click)="decide(c, false)">Reject</button>
          </div>
        }
      </div>
    } @empty { <p class="muted">Nothing waiting for the committee.</p> }
  `,
})
export class CommitteeComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  readonly requests = signal<ChangeRequest[]>([]);

  ngOnInit(): void { this.load(); }
  load(): void { this.api.pendingChanges().subscribe(r => this.requests.set(r)); }
  decide(c: ChangeRequest, approve: boolean): void { this.api.decideChange(c.id, approve).subscribe(() => this.load()); }
}
