import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { ApiService } from '../core/api.service';
import { Loan } from '../core/models';
import { UI } from '../core/shared';

@Component({
  selector: 'app-loans',
  standalone: true,
  imports: [UI],
  template: `
    <h1>Loans</h1>
    <div class="actions">
      <mat-form-field style="width: 220px"><mat-label>Status</mat-label>
        <mat-select [(ngModel)]="status">
          <mat-option value="">All</mat-option>
          @for (s of statuses; track s) { <mat-option [value]="s">{{ s }}</mat-option> }
        </mat-select></mat-form-field>
      <mat-form-field style="width: 260px"><mat-label>Search loan / customer / name</mat-label><input matInput [(ngModel)]="q"></mat-form-field>
    </div>
    <div class="card">
      <table mat-table [dataSource]="filtered()">
        <ng-container matColumnDef="no"><th mat-header-cell *matHeaderCellDef>Loan</th>
          <td mat-cell *matCellDef="let l"><a [routerLink]="['/loans', l.id]">{{ l.loanNumber }}</a></td></ng-container>
        <ng-container matColumnDef="who"><th mat-header-cell *matHeaderCellDef>Borrower</th><td mat-cell *matCellDef="let l">{{ l.borrowerName }} <span class="muted">{{ l.customerRef }}</span></td></ng-container>
        <ng-container matColumnDef="product"><th mat-header-cell *matHeaderCellDef>Product</th><td mat-cell *matCellDef="let l">{{ l.productCode }}</td></ng-container>
        <ng-container matColumnDef="principal"><th mat-header-cell *matHeaderCellDef class="num">Principal</th><td mat-cell *matCellDef="let l" class="num">{{ l.principal | money: l.currency }}</td></ng-container>
        <ng-container matColumnDef="out"><th mat-header-cell *matHeaderCellDef class="num">Outstanding</th><td mat-cell *matCellDef="let l" class="num">{{ l.outstandingPrincipal | money: l.currency }}</td></ng-container>
        <ng-container matColumnDef="dpd"><th mat-header-cell *matHeaderCellDef class="num">DPD</th><td mat-cell *matCellDef="let l" class="num">{{ l.daysPastDue }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let l"><span class="chip" [ngClass]="l.status | statusClass">{{ l.status }}</span></td></ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr><tr mat-row *matRowDef="let row; columns: cols"></tr>
      </table>
      @if (filtered().length === 0) { <p class="muted">No loans match.</p> }
    </div>
  `,
})
export class LoansComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly loans = signal<Loan[]>([]);
  readonly cols = ['no', 'who', 'product', 'principal', 'out', 'dpd', 'status'];
  readonly statuses = ['AWAITING_COVER', 'COVERED', 'ACTIVE', 'IN_ARREARS', 'RESTRUCTURED', 'CLOSED', 'WRITTEN_OFF'];
  private readonly statusSig = signal('');
  private readonly qSig = signal('');
  get status(): string { return this.statusSig(); }
  set status(v: string) { this.statusSig.set(v); }
  get q(): string { return this.qSig(); }
  set q(v: string) { this.qSig.set(v); }

  readonly filtered = computed(() => {
    const q = this.qSig().toLowerCase();
    return this.loans().filter(l => (!this.statusSig() || l.status === this.statusSig())
      && (!q || `${l.loanNumber} ${l.customerRef} ${l.borrowerName}`.toLowerCase().includes(q)));
  });

  ngOnInit(): void {
    this.api.loans().subscribe(l => this.loans.set(l));
  }
}
