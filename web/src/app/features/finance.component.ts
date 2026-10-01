import { Component, OnInit, inject, signal } from '@angular/core';

import { AppComponent } from '../app.component';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { EodRun, PayrollBatch, TrialBalance } from '../core/models';
import { UI } from '../core/shared';

/** Finance: end-of-day (and demo fast-forward), trial balance, payroll deduction files, provisioning, returns. */
@Component({
  selector: 'app-finance',
  standalone: true,
  imports: [UI],
  template: `
    <h1>Finance & end of day</h1>
    @if (auth.hasAnyRole('FINANCE')) {
      <div class="card">
        <h2>Run end-of-day</h2>
        <p class="muted">Accrues interest, charges penalties (in duplum capped), ages arrears, creates collections tasks,
          sends reminders, runs IFRS 9 at month-end, checks the trial balance, then advances the business date.
          More than one day fast-forwards the demo.</p>
        <div class="actions">
          <mat-form-field style="width: 120px"><mat-label>Days</mat-label><input matInput type="number" min="1" max="366" [(ngModel)]="days"></mat-form-field>
          <button mat-flat-button color="primary" [disabled]="busy()" (click)="runEod()">Run EOD</button>
          <button mat-stroked-button (click)="provision()">Run IFRS 9 provisioning now</button>
          <button mat-stroked-button (click)="downloadReturn()">Regulatory return (CSV)</button>
        </div>
        @if (busy()) { <mat-progress-bar mode="indeterminate" /> }
      </div>
      <div class="card">
        <h2>Payroll deduction file</h2>
        <p class="muted">CSV: employer,national_id,amount,period — matched to the borrower's active loan by national ID.</p>
        <input type="file" accept=".csv" (change)="upload($event)">
        @if (batch(); as b) {
          <p>{{ b.fileName }}: {{ b.matched }} posted ({{ b.totalPosted | number: '1.2-2' }}), {{ b.unmatched }} for review</p>
          <ul>@for (l of b.lines; track l.lineNo) { <li>Line {{ l.lineNo }}: {{ l.nationalIdMasked }} {{ l.amount }} — {{ l.status }} {{ l.loanNumber ?? '' }} ({{ l.message }})</li> }</ul>
        }
      </div>
    }
    <div class="row">
      <div class="card grow">
        <h2>Trial balance <span class="chip" [ngClass]="tb()?.balanced ? 'ok' : 'bad'">{{ tb()?.balanced ? 'balanced' : 'out of balance' }}</span></h2>
        <table mat-table [dataSource]="tb()?.rows ?? []">
          <ng-container matColumnDef="acc"><th mat-header-cell *matHeaderCellDef>Account</th><td mat-cell *matCellDef="let r">{{ r.accountCode }} {{ r.accountName }}</td></ng-container>
          <ng-container matColumnDef="ccy"><th mat-header-cell *matHeaderCellDef>Ccy</th><td mat-cell *matCellDef="let r">{{ r.currency }}</td></ng-container>
          <ng-container matColumnDef="dr"><th mat-header-cell *matHeaderCellDef class="num">Debits</th><td mat-cell *matCellDef="let r" class="num">{{ r.debits | number: '1.2-2' }}</td></ng-container>
          <ng-container matColumnDef="cr"><th mat-header-cell *matHeaderCellDef class="num">Credits</th><td mat-cell *matCellDef="let r" class="num">{{ r.credits | number: '1.2-2' }}</td></ng-container>
          <ng-container matColumnDef="bal"><th mat-header-cell *matHeaderCellDef class="num">Balance</th><td mat-cell *matCellDef="let r" class="num">{{ r.balance | number: '1.2-2' }}</td></ng-container>
          <tr mat-header-row *matHeaderRowDef="tbCols"></tr><tr mat-row *matRowDef="let row; columns: tbCols"></tr>
        </table>
      </div>
      <div class="card grow">
        <h2>EOD history</h2>
        <table mat-table [dataSource]="runs()">
          <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Business date</th><td mat-cell *matCellDef="let r">{{ r.businessDate }}{{ r.monthEnd ? ' (month-end)' : '' }}</td></ng-container>
          <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let r"><span class="chip" [ngClass]="r.status | statusClass">{{ r.status }}</span></td></ng-container>
          <ng-container matColumnDef="loans"><th mat-header-cell *matHeaderCellDef class="num">Loans</th><td mat-cell *matCellDef="let r" class="num">{{ r.loansProcessed }}</td></ng-container>
          <ng-container matColumnDef="arrears"><th mat-header-cell *matHeaderCellDef class="num">Bucket moves</th><td mat-cell *matCellDef="let r" class="num">{{ r.arrearsChanges }}</td></ng-container>
          <ng-container matColumnDef="tasks"><th mat-header-cell *matHeaderCellDef class="num">Tasks</th><td mat-cell *matCellDef="let r" class="num">{{ r.tasksCreated }}</td></ng-container>
          <tr mat-header-row *matHeaderRowDef="runCols"></tr><tr mat-row *matRowDef="let row; columns: runCols"></tr>
        </table>
      </div>
    </div>
  `,
})
export class FinanceComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly shell = inject(AppComponent, { optional: true });
  readonly tb = signal<TrialBalance | null>(null);
  readonly runs = signal<EodRun[]>([]);
  readonly batch = signal<PayrollBatch | null>(null);
  readonly busy = signal(false);
  readonly tbCols = ['acc', 'ccy', 'dr', 'cr', 'bal'];
  readonly runCols = ['date', 'status', 'loans', 'arrears', 'tasks'];
  days = 1;

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.trialBalance().subscribe(t => this.tb.set(t));
    this.api.eodHistory().subscribe(r => this.runs.set(r));
  }

  runEod(): void {
    this.busy.set(true);
    this.api.runEod(this.days).subscribe({
      next: () => { this.busy.set(false); this.load(); this.shell?.refreshDate(); },
      error: () => this.busy.set(false),
    });
  }

  provision(): void { this.api.runProvisioning().subscribe(() => this.load()); }

  upload(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.api.uploadPayroll(file).subscribe(b => { this.batch.set(b); this.load(); });
  }

  downloadReturn(): void {
    this.api.regulatoryReturn('USD').subscribe(csv => {
      const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
      const a = document.createElement('a');
      a.href = url;
      a.download = 'regulatory-return-USD.csv';
      a.click();
      URL.revokeObjectURL(url);
    });
  }
}
