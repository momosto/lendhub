import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Currency, ParReport, Portfolio } from '../core/models';
import { UI } from '../core/shared';

/** Portfolio dashboard (LH-80): GLP, disbursements, collection efficiency and PAR 30/60/90. */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [UI],
  template: `
    <h1>Portfolio</h1>
    @if (!canSeeReports()) {
      <p class="muted">Welcome, {{ auth.user()?.displayName }}. Use the menu to work on borrowers and applications.</p>
    } @else {
      <div class="actions">
        <mat-form-field style="width: 140px">
          <mat-label>Currency</mat-label>
          <mat-select [(ngModel)]="currency" (selectionChange)="load()">
            <mat-option value="USD">USD</mat-option><mat-option value="ZWG">ZWG</mat-option>
          </mat-select>
        </mat-form-field>
      </div>
      @if (portfolio(); as p) {
        <div class="tiles">
          <div class="tile"><div class="label">Gross loan portfolio</div><div class="value">{{ p.grossLoanPortfolio | money: p.currency }}</div></div>
          <div class="tile"><div class="label">Active loans</div><div class="value">{{ p.activeLoans }}</div></div>
          <div class="tile"><div class="label">Disbursed this month</div><div class="value">{{ p.disbursedMtdAmount | money: p.currency }}</div>
            <div class="muted">{{ p.disbursedMtdCount }} loans</div></div>
          <div class="tile"><div class="label">Collection efficiency (MTD)</div>
            <div class="value">{{ p.collectionEfficiencyPercent === null || p.collectionEfficiencyPercent === undefined ? '—' : (p.collectionEfficiencyPercent + '%') }}</div>
            <div class="muted">{{ p.collectedMtd | money: p.currency }} of {{ p.dueMtd | money: p.currency }} due</div></div>
          <div class="tile"><div class="label">PAR 30</div><div class="value">{{ p.par30Percent }}%</div></div>
        </div>
      }
      @if (par(); as r) {
        <div class="row">
          <div class="card grow">
            <h2>Arrears buckets (outstanding principal)</h2>
            @for (b of buckets(); track b.key) {
              <div class="bucket">
                <span class="name">{{ b.label }}</span>
                <div class="track"><div class="bar" [class.risk]="b.key !== 'CURRENT'" [style.width.%]="b.pct"></div></div>
                <span class="num">{{ b.value | money: r.currency }}</span>
              </div>
            }
          </div>
          <div class="card grow">
            <h2>PAR by branch</h2>
            <table mat-table [dataSource]="r.byBranch">
              <ng-container matColumnDef="key"><th mat-header-cell *matHeaderCellDef>Branch</th><td mat-cell *matCellDef="let l">{{ l.key }}</td></ng-container>
              <ng-container matColumnDef="loans"><th mat-header-cell *matHeaderCellDef class="num">Loans</th><td mat-cell *matCellDef="let l" class="num">{{ l.loans }}</td></ng-container>
              <ng-container matColumnDef="portfolio"><th mat-header-cell *matHeaderCellDef class="num">Portfolio</th><td mat-cell *matCellDef="let l" class="num">{{ l.portfolio | money: r.currency }}</td></ng-container>
              <ng-container matColumnDef="par30"><th mat-header-cell *matHeaderCellDef class="num">PAR 30</th><td mat-cell *matCellDef="let l" class="num">{{ l.par30Percent }}%</td></ng-container>
              <tr mat-header-row *matHeaderRowDef="cols"></tr><tr mat-row *matRowDef="let row; columns: cols"></tr>
            </table>
            <h2>PAR by product</h2>
            <table mat-table [dataSource]="r.byProduct">
              <ng-container matColumnDef="key"><th mat-header-cell *matHeaderCellDef>Product</th><td mat-cell *matCellDef="let l">{{ l.key }}</td></ng-container>
              <ng-container matColumnDef="loans"><th mat-header-cell *matHeaderCellDef class="num">Loans</th><td mat-cell *matCellDef="let l" class="num">{{ l.loans }}</td></ng-container>
              <ng-container matColumnDef="portfolio"><th mat-header-cell *matHeaderCellDef class="num">Portfolio</th><td mat-cell *matCellDef="let l" class="num">{{ l.portfolio | money: r.currency }}</td></ng-container>
              <ng-container matColumnDef="par30"><th mat-header-cell *matHeaderCellDef class="num">PAR 30</th><td mat-cell *matCellDef="let l" class="num">{{ l.par30Percent }}%</td></ng-container>
              <tr mat-header-row *matHeaderRowDef="cols"></tr><tr mat-row *matRowDef="let row; columns: cols"></tr>
            </table>
          </div>
        </div>
      }
    }
  `,
  styles: [`
    .bucket { display: grid; grid-template-columns: 110px 1fr 130px; align-items: center; gap: 8px; margin: 8px 0; }
    .track { background: #eef0f4; border-radius: 5px; }
  `],
})
export class DashboardComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  readonly portfolio = signal<Portfolio | null>(null);
  readonly par = signal<ParReport | null>(null);
  readonly cols = ['key', 'loans', 'portfolio', 'par30'];
  currency: Currency = 'USD';

  readonly canSeeReports = computed(() => this.auth.hasAnyRole('MANAGER', 'COMMITTEE', 'FINANCE', 'AUDITOR', 'COLLECTIONS'));

  private static readonly LABELS: Record<string, string> = {
    CURRENT: 'Current', DPD_1_30: '1–30 days', DPD_31_60: '31–60 days', DPD_61_90: '61–90 days', DPD_90_PLUS: '90+ days',
  };

  readonly buckets = computed(() => {
    const r = this.par();
    if (!r) return [];
    const max = Math.max(1, ...Object.values(r.buckets).map(Number));
    return Object.entries(r.buckets).map(([key, value]) => ({
      key, label: DashboardComponent.LABELS[key] ?? key, value: Number(value), pct: (Number(value) / max) * 100,
    }));
  });

  ngOnInit(): void {
    if (this.canSeeReports()) this.load();
  }

  load(): void {
    this.api.portfolio(this.currency).subscribe(p => this.portfolio.set(p));
    this.api.par(this.currency).subscribe(r => this.par.set(r));
  }
}
