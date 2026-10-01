import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Application } from '../core/models';
import { UI } from '../core/shared';

/** LH-11..13: score with reasons, maker-checker decision, offer with full cost disclosure, OTP acceptance. */
@Component({
  selector: 'app-application-detail',
  standalone: true,
  imports: [UI],
  template: `
    @if (app(); as a) {
      <h1>{{ a.applicationNumber }} · {{ a.borrowerName }} <span class="chip" [ngClass]="a.status | statusClass">{{ a.status }}</span></h1>
      <div class="row">
        <div class="card grow">
          <h2>Request</h2>
          <p>{{ a.productCode }} · <strong>{{ a.amount | money: a.currency }}</strong> over {{ a.instalments }} {{ a.frequency | lowercase }} instalments</p>
          <p class="muted">{{ a.purpose }} · captured by {{ a.capturedBy }} · {{ a.branch }}</p>
          <p>Income {{ a.monthlyIncome | money: a.currency }} · expenses {{ a.monthlyExpenses | money: a.currency }}</p>
          @if (a.status === 'DRAFT' && auth.hasAnyRole('OFFICER')) {
            <div class="actions"><button mat-flat-button color="primary" (click)="submit()">Submit for scoring</button></div>
          }
        </div>
        @if (a.grade) {
          <div class="card grow">
            <h2>Score: grade {{ a.grade }} ({{ a.scorePoints }} points)</h2>
            <p>Instalment ≈ {{ a.monthlyInstalmentEstimate | money: a.currency }}/month =
              <strong>{{ a.affordabilityRatio! * 100 | number: '1.0-0' }}%</strong> of disposable income
              ({{ a.disposableIncome | money: a.currency }}) — {{ a.affordable ? 'affordable' : 'NOT affordable (limit 40%)' }}</p>
            <ul class="reasons">@for (r of a.scoreReasons; track $index) { <li>{{ r }}</li> }</ul>
            <p class="muted">Bureau: {{ a.bureauStatus }}</p>
          </div>
        }
      </div>

      @if (a.status === 'SCORED' && auth.hasAnyRole('MANAGER', 'COMMITTEE')) {
        <div class="card">
          <h2>Decision</h2>
          <p class="muted">Maker ≠ checker: the capturer cannot decide. Branch managers approve up to US$1,000; above that,
            grade E and related-party flags go to the credit committee.</p>
          <div class="form-grid">
            <mat-form-field><mat-label>Decline reason</mat-label>
              <mat-select [(ngModel)]="reasonCode">@for (r of reasons(); track r) { <mat-option [value]="r">{{ r }}</mat-option> }</mat-select>
            </mat-form-field>
            <mat-form-field><mat-label>Comment</mat-label><input matInput [(ngModel)]="comment"></mat-form-field>
          </div>
          <div class="actions">
            <button mat-flat-button color="primary" (click)="decide(true)">Approve & issue offer</button>
            <button mat-stroked-button color="warn" [disabled]="!reasonCode" (click)="decide(false)">Decline</button>
          </div>
        </div>
      }

      @if (a.offer; as o) {
        <div class="card">
          <h2>Offer (valid until {{ o.expiresOn }})</h2>
          <div class="tiles">
            <div class="tile"><div class="label">First instalment</div><div class="value">{{ o.instalment | money: a.currency }}</div></div>
            <div class="tile"><div class="label">Total interest</div><div class="value">{{ o.totalInterest | money: a.currency }}</div></div>
            <div class="tile"><div class="label">Establishment fee</div><div class="value">{{ o.establishmentFee | money: a.currency }}</div></div>
            <div class="tile"><div class="label">Credit life</div><div class="value">{{ o.totalCreditLife | money: a.currency }}</div></div>
            <div class="tile"><div class="label">You receive</div><div class="value">{{ o.netDisbursed | money: a.currency }}</div></div>
            <div class="tile"><div class="label">Total to repay</div><div class="value">{{ o.totalRepayable | money: a.currency }}</div></div>
            <div class="tile"><div class="label">Effective annual rate</div><div class="value">{{ o.effectiveAnnualRatePercent }}%</div></div>
          </div>
          @if (a.status === 'OFFERED' && auth.hasAnyRole('OFFICER')) {
            @if (otp()) { <p>Demo SMS to the borrower: acceptance code <span class="otp">{{ otp() }}</span></p> }
            <div class="actions">
              <mat-form-field><mat-label>Code from SMS</mat-label><input matInput [(ngModel)]="otpInput" maxlength="6"></mat-form-field>
              <button mat-flat-button color="primary" [disabled]="otpInput.length !== 6" (click)="accept()">Borrower accepts</button>
              <button mat-button (click)="resend()">Re-send code</button>
            </div>
          }
          @if (a.status === 'ACCEPTED') {
            <p>Accepted. The loan account is booked and credit life cover is requested from InsureHub —
              <a routerLink="/loans">see Loans</a>.</p>
          }
        </div>
      }
      @if (a.status === 'DECLINED') { <div class="card">Declined by {{ a.decidedBy }}: {{ a.declineReason }}</div> }
    }
  `,
})
export class ApplicationDetailComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  readonly id = input.required<string>();
  readonly app = signal<Application | null>(null);
  readonly reasons = signal<string[]>([]);
  readonly otp = signal<string | null>(null);
  reasonCode = '';
  comment = '';
  otpInput = '';

  ngOnInit(): void {
    this.load();
    this.api.declineReasons().subscribe(r => this.reasons.set(r));
  }

  load(): void {
    this.api.application(this.id()).subscribe(a => this.app.set(a));
  }

  private show(a: Application): void {
    this.app.set(a);
    if (a.demoOtp) this.otp.set(a.demoOtp);
  }

  submit(): void { this.api.submitApplication(this.id()).subscribe(a => this.show(a)); }
  decide(approve: boolean): void {
    this.api.decide(this.id(), approve, approve ? undefined : this.reasonCode, this.comment).subscribe(a => this.show(a));
  }
  resend(): void { this.api.reissueOtp(this.id()).subscribe(a => this.show(a)); }
  accept(): void { this.api.acceptOffer(this.id(), this.otpInput).subscribe(a => this.show(a)); }
}
