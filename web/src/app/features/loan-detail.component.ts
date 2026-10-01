import { Component, OnInit, inject, input, signal } from '@angular/core';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { CollectionTask, Loan, Repayment, SettlementQuote } from '../core/models';
import { UI } from '../core/shared';

/** Loan servicing: schedule vs actual, repayments and allocations, settlement, restructure/write-off, audit. */
@Component({
  selector: 'app-loan-detail',
  standalone: true,
  imports: [UI],
  template: `
    @if (loan(); as l) {
      <h1>{{ l.loanNumber }} · {{ l.borrowerName }} <span class="chip" [ngClass]="l.status | statusClass">{{ l.status }}</span></h1>
      <div class="tiles">
        <div class="tile"><div class="label">Principal</div><div class="value">{{ l.principal | money: l.currency }}</div></div>
        <div class="tile"><div class="label">Outstanding principal</div><div class="value">{{ l.outstandingPrincipal | money: l.currency }}</div></div>
        <div class="tile"><div class="label">Arrears</div><div class="value">{{ l.arrearsAmount | money: l.currency }}</div>
          <div class="muted">{{ l.daysPastDue }} days past due · {{ l.arrearsBucket }}</div></div>
        <div class="tile"><div class="label">Interest receivable</div><div class="value">{{ l.interestReceivable | money: l.currency }}</div></div>
        <div class="tile"><div class="label">Credit balance</div><div class="value">{{ l.creditBalance | money: l.currency }}</div></div>
      </div>
      <p class="muted">{{ l.productCode }} · {{ l.instalmentCount }} {{ l.frequency | lowercase }} instalments · credit life
        {{ l.creditLifePolicyNumber ?? 'pending' }} · disbursed {{ l.disbursedOn ?? '—' }} (net {{ l.netDisbursed | money: l.currency }})
        · captured by {{ l.capturedBy }}, approved by {{ l.approvedBy }}, released by {{ l.disbursedBy ?? '—' }}</p>
      @if (l.coverError) { <p class="chip bad">Credit life request failed: {{ l.coverError }}</p> }

      <div class="actions">
        @if (auth.hasAnyRole('FINANCE') && l.status === 'AWAITING_COVER') { <button mat-stroked-button (click)="retryCover()">Retry credit life</button> }
        @if (auth.hasAnyRole('FINANCE') && l.status === 'COVERED') {
          <button mat-flat-button color="primary" (click)="disburse()">Release disbursement to EcoCash</button>
        }
        @if (servicing(l) && auth.hasAnyRole('OFFICER', 'COLLECTIONS')) {
          <button mat-stroked-button (click)="ecoCash()">Send EcoCash prompt ({{ nextDue(l) | money: l.currency }})</button>
        }
        @if (servicing(l)) { <button mat-stroked-button (click)="quote()">Settlement quote</button> }
      </div>

      @if (settlement(); as s) {
        <div class="card">
          <h2>Early settlement today: {{ s.total.amount | money: l.currency }}</h2>
          <p>Principal {{ s.outstandingPrincipal.amount | money: l.currency }} + accrued interest {{ s.accruedInterest.amount | money: l.currency }}
            + charges {{ s.arrearsCharges.amount | money: l.currency }} + settlement fee {{ s.settlementFee.amount | money: l.currency }}
            − credit {{ s.creditBalance.amount | money: l.currency }}. Future interest is waived.</p>
          @if (auth.hasAnyRole('FINANCE')) {
            <div class="actions">
              <mat-form-field><mat-label>Bank / RTGS reference</mat-label><input matInput [(ngModel)]="settleRef"></mat-form-field>
              <button mat-flat-button color="primary" [disabled]="!settleRef" (click)="settle(s.total.amount)">Record settlement</button>
            </div>
          }
        </div>
      }

      <mat-tab-group>
        <mat-tab label="Schedule">
          <table mat-table [dataSource]="l.schedule ?? []">
            <ng-container matColumnDef="seq"><th mat-header-cell *matHeaderCellDef>#</th><td mat-cell *matCellDef="let i">{{ i.seq }}</td></ng-container>
            <ng-container matColumnDef="due"><th mat-header-cell *matHeaderCellDef>Due</th><td mat-cell *matCellDef="let i">{{ i.dueDate }}</td></ng-container>
            <ng-container matColumnDef="principal"><th mat-header-cell *matHeaderCellDef class="num">Principal</th><td mat-cell *matCellDef="let i" class="num">{{ i.principalDue | number: '1.2-2' }}</td></ng-container>
            <ng-container matColumnDef="interest"><th mat-header-cell *matHeaderCellDef class="num">Interest</th><td mat-cell *matCellDef="let i" class="num">{{ i.interestDue | number: '1.2-2' }}</td></ng-container>
            <ng-container matColumnDef="cl"><th mat-header-cell *matHeaderCellDef class="num">Credit life</th><td mat-cell *matCellDef="let i" class="num">{{ i.creditLifeDue | number: '1.2-2' }}</td></ng-container>
            <ng-container matColumnDef="pen"><th mat-header-cell *matHeaderCellDef class="num">Penalty/fees</th><td mat-cell *matCellDef="let i" class="num">{{ i.penaltyDue + i.feesDue | number: '1.2-2' }}</td></ng-container>
            <ng-container matColumnDef="paid"><th mat-header-cell *matHeaderCellDef class="num">Paid</th><td mat-cell *matCellDef="let i" class="num">{{ i.totalPaid | number: '1.2-2' }}</td></ng-container>
            <ng-container matColumnDef="out"><th mat-header-cell *matHeaderCellDef class="num">Outstanding</th><td mat-cell *matCellDef="let i" class="num">{{ i.outstanding | number: '1.2-2' }}</td></ng-container>
            <ng-container matColumnDef="state"><th mat-header-cell *matHeaderCellDef>State</th><td mat-cell *matCellDef="let i"><span class="chip" [ngClass]="i.state | statusClass">{{ i.state }}</span></td></ng-container>
            <tr mat-header-row *matHeaderRowDef="scheduleCols"></tr><tr mat-row *matRowDef="let row; columns: scheduleCols"></tr>
          </table>
        </mat-tab>
        <mat-tab label="Repayments">
          @if (auth.hasAnyRole('FINANCE') && servicing(l)) {
            <div class="actions">
              <mat-form-field><mat-label>Amount</mat-label><input matInput type="number" [(ngModel)]="receiptAmount"></mat-form-field>
              <mat-form-field><mat-label>Channel</mat-label><mat-select [(ngModel)]="receiptChannel">
                <mat-option value="CASH">Cash</mat-option><mat-option value="BANK">Bank</mat-option></mat-select></mat-form-field>
              <mat-form-field><mat-label>Receipt reference</mat-label><input matInput [(ngModel)]="receiptRef"></mat-form-field>
              <button mat-flat-button color="primary" [disabled]="!receiptRef || receiptAmount <= 0" (click)="receipt()">Record receipt</button>
            </div>
          }
          @for (r of repayments(); track r.id) {
            <div class="card">
              <strong>{{ r.amount | money: r.currency }}</strong> · {{ r.channel }} · {{ r.valueDate }} · {{ r.providerReference }}
              <span class="chip" [ngClass]="r.status | statusClass">{{ r.status }}</span> {{ r.type !== 'REPAYMENT' ? r.type : '' }}
              <div class="muted">
                @for (a of r.allocations; track $index) { #{{ a.instalmentSeq }} {{ a.component }} {{ a.amount | number: '1.2-2' }} · }
                @if (r.creditAdded > 0) { held as credit {{ r.creditAdded | number: '1.2-2' }} }
              </div>
              @if (r.status === 'POSTED' && auth.hasAnyRole('FINANCE')) {
                <button mat-button color="warn" (click)="reverse(r)">Reverse</button>
              }
            </div>
          } @empty { <p class="muted">No repayments yet.</p> }
        </mat-tab>
        <mat-tab label="Collections">
          @for (t of tasks(); track t.id) {
            <div class="card">{{ t.createdOn }} · <strong>{{ t.type }}</strong> at {{ t.dpdAtCreation }} DPD ·
              {{ t.arrearsAmount | money: t.currency }} · <span class="chip" [ngClass]="t.status | statusClass">{{ t.status }}</span>
              <div class="muted">{{ t.notes }}</div></div>
          } @empty { <p class="muted">No collections activity.</p> }
          @if (servicing(l) && auth.hasAnyRole('MANAGER', 'COLLECTIONS')) {
            <h2>Ask the credit committee</h2>
            <div class="actions">
              <mat-form-field><mat-label>Reason</mat-label><input matInput [(ngModel)]="changeReason"></mat-form-field>
              <mat-form-field style="width: 150px"><mat-label>New instalments</mat-label><input matInput type="number" [(ngModel)]="newInstalments"></mat-form-field>
              <button mat-stroked-button [disabled]="!changeReason" (click)="requestChange('restructure')">Request restructure</button>
              <button mat-stroked-button color="warn" [disabled]="!changeReason" (click)="requestChange('write-off')">Request write-off</button>
            </div>
          }
        </mat-tab>
        @if (auth.hasAnyRole('AUDITOR', 'COMMITTEE')) {
          <mat-tab label="Audit trail">
            <table mat-table [dataSource]="audit()">
              <ng-container matColumnDef="rev"><th mat-header-cell *matHeaderCellDef>Rev</th><td mat-cell *matCellDef="let a">{{ a.revision }}</td></ng-container>
              <ng-container matColumnDef="at"><th mat-header-cell *matHeaderCellDef>When</th><td mat-cell *matCellDef="let a">{{ a.at | date: 'medium' }}</td></ng-container>
              <ng-container matColumnDef="user"><th mat-header-cell *matHeaderCellDef>Who</th><td mat-cell *matCellDef="let a">{{ a.user }}</td></ng-container>
              <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let a">{{ a.status }} ({{ a.daysPastDue }} DPD)</td></ng-container>
              <tr mat-header-row *matHeaderRowDef="auditCols"></tr><tr mat-row *matRowDef="let row; columns: auditCols"></tr>
            </table>
          </mat-tab>
        }
      </mat-tab-group>
    }
  `,
})
export class LoanDetailComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  readonly id = input.required<string>();
  readonly loan = signal<Loan | null>(null);
  readonly repayments = signal<Repayment[]>([]);
  readonly tasks = signal<CollectionTask[]>([]);
  readonly settlement = signal<SettlementQuote | null>(null);
  readonly audit = signal<{ revision: number; at: string; user: string; status: string; daysPastDue: number }[]>([]);
  readonly scheduleCols = ['seq', 'due', 'principal', 'interest', 'cl', 'pen', 'paid', 'out', 'state'];
  readonly auditCols = ['rev', 'at', 'user', 'status'];
  receiptAmount = 0;
  receiptChannel = 'CASH';
  receiptRef = '';
  settleRef = '';
  changeReason = '';
  newInstalments = 12;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.loan(this.id()).subscribe(l => this.loan.set(l));
    this.api.repayments(this.id()).subscribe(r => this.repayments.set(r));
    if (this.auth.hasAnyRole('COLLECTIONS', 'MANAGER', 'AUDITOR')) this.api.loanTasks(this.id()).subscribe(t => this.tasks.set(t));
    if (this.auth.hasAnyRole('AUDITOR', 'COMMITTEE')) this.api.audit(this.id()).subscribe(a => this.audit.set(a));
  }

  servicing(l: Loan): boolean {
    return ['ACTIVE', 'IN_ARREARS', 'RESTRUCTURED'].includes(l.status);
  }

  nextDue(l: Loan): number {
    const next = (l.schedule ?? []).find(i => i.state === 'OPEN');
    return next ? next.outstanding : 0;
  }

  retryCover(): void { this.api.retryCover(this.id()).subscribe(() => this.load()); }
  disburse(): void { this.api.disburse(this.id()).subscribe(l => this.loan.set(l)); }
  quote(): void { this.api.settlementQuote(this.id()).subscribe(q => this.settlement.set(q)); }
  ecoCash(): void {
    const l = this.loan()!;
    // the simulated Payments hub "approves" after ~2 seconds; reload then
    this.api.requestEcoCash(this.id(), this.nextDue(l) || 10).subscribe(() => setTimeout(() => this.load(), 3000));
  }
  receipt(): void {
    this.api.recordReceipt(this.id(), this.receiptAmount, this.receiptChannel, this.receiptRef).subscribe(() => {
      this.receiptRef = '';
      this.load();
    });
  }
  settle(amount: number): void {
    this.api.settle(this.id(), amount, 'BANK', this.settleRef).subscribe(() => {
      this.settlement.set(null);
      this.load();
    });
  }
  reverse(r: Repayment): void {
    const reason = prompt('Reason for reversing ' + r.providerReference + '?');
    if (reason) this.api.reverse(r.id, reason).subscribe(() => this.load());
  }
  requestChange(type: 'restructure' | 'write-off'): void {
    this.api.requestChange(this.id(), type, this.changeReason, type === 'restructure' ? this.newInstalments : undefined)
      .subscribe(() => {
        this.changeReason = '';
        this.load();
      });
  }
}
