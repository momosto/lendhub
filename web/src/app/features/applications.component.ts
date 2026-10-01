import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Application, Borrower, Product } from '../core/models';
import { UI } from '../core/shared';

/** LH-10: capture applications (draft → submit runs affordability + scorecard); list for approvers. */
@Component({
  selector: 'app-applications',
  standalone: true,
  imports: [UI],
  template: `
    <h1>Applications</h1>
    @if (auth.hasAnyRole('OFFICER')) {
      <div class="card">
        <h2>Capture an application</h2>
        <form [formGroup]="form" (ngSubmit)="capture()">
          <div class="form-grid">
            <mat-form-field><mat-label>Borrower</mat-label>
              <mat-select formControlName="borrowerId">
                @for (b of borrowers(); track b.id) { <mat-option [value]="b.id">{{ b.customerRef }} · {{ b.firstName }} {{ b.lastName }}</mat-option> }
              </mat-select></mat-form-field>
            <mat-form-field><mat-label>Product</mat-label>
              <mat-select formControlName="productCode">
                @for (p of products(); track p.code) { <mat-option [value]="p.code">{{ p.name }} (US{{ p.minAmount }}–{{ p.maxAmount }})</mat-option> }
              </mat-select></mat-form-field>
            <mat-form-field><mat-label>Amount</mat-label><input matInput type="number" formControlName="amount"></mat-form-field>
            <mat-form-field><mat-label>Currency</mat-label>
              <mat-select formControlName="currency"><mat-option value="USD">USD</mat-option><mat-option value="ZWG">ZWG</mat-option></mat-select></mat-form-field>
            <mat-form-field><mat-label>Repayment frequency</mat-label>
              <mat-select formControlName="frequency"><mat-option value="MONTHLY">Monthly</mat-option><mat-option value="WEEKLY">Weekly</mat-option></mat-select></mat-form-field>
            <mat-form-field><mat-label>Number of instalments</mat-label><input matInput type="number" formControlName="instalments"></mat-form-field>
            <mat-form-field><mat-label>Monthly income</mat-label><input matInput type="number" formControlName="monthlyIncome"></mat-form-field>
            <mat-form-field><mat-label>Monthly expenses</mat-label><input matInput type="number" formControlName="monthlyExpenses"></mat-form-field>
            <mat-form-field><mat-label>Other debt repayments</mat-label><input matInput type="number" formControlName="otherDebtRepayments"></mat-form-field>
            <mat-form-field><mat-label>Purpose</mat-label><input matInput formControlName="purpose"></mat-form-field>
          </div>
          <div class="actions"><button mat-flat-button color="primary" [disabled]="form.invalid">Save draft</button></div>
        </form>
      </div>
    }
    <div class="card">
      <table mat-table [dataSource]="applications()">
        <ng-container matColumnDef="no"><th mat-header-cell *matHeaderCellDef>Application</th>
          <td mat-cell *matCellDef="let a"><a [routerLink]="['/applications', a.id]">{{ a.applicationNumber }}</a></td></ng-container>
        <ng-container matColumnDef="who"><th mat-header-cell *matHeaderCellDef>Borrower</th><td mat-cell *matCellDef="let a">{{ a.borrowerName }}</td></ng-container>
        <ng-container matColumnDef="product"><th mat-header-cell *matHeaderCellDef>Product</th><td mat-cell *matCellDef="let a">{{ a.productCode }}</td></ng-container>
        <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef class="num">Amount</th><td mat-cell *matCellDef="let a" class="num">{{ a.amount | money: a.currency }}</td></ng-container>
        <ng-container matColumnDef="term"><th mat-header-cell *matHeaderCellDef>Term</th><td mat-cell *matCellDef="let a">{{ a.instalments }} × {{ a.frequency | lowercase }}</td></ng-container>
        <ng-container matColumnDef="grade"><th mat-header-cell *matHeaderCellDef>Grade</th><td mat-cell *matCellDef="let a">{{ a.grade ?? '—' }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let a"><span class="chip" [ngClass]="a.status | statusClass">{{ a.status }}</span></td></ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr><tr mat-row *matRowDef="let row; columns: cols"></tr>
      </table>
      @if (applications().length === 0) { <p class="muted">No applications yet.</p> }
    </div>
  `,
})
export class ApplicationsComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  /** Pre-selects the borrower when coming from the borrowers list (?borrower=…). */
  readonly borrower = input<string>();

  readonly applications = signal<Application[]>([]);
  readonly borrowers = signal<Borrower[]>([]);
  readonly products = signal<Product[]>([]);
  readonly cols = ['no', 'who', 'product', 'amount', 'term', 'grade', 'status'];

  readonly form = this.fb.nonNullable.group({
    borrowerId: ['', Validators.required],
    productCode: ['TRADER', Validators.required],
    amount: [500, [Validators.required, Validators.min(1)]],
    currency: ['USD'],
    frequency: ['MONTHLY'],
    instalments: [6, [Validators.required, Validators.min(1)]],
    monthlyIncome: [900, Validators.required],
    monthlyExpenses: [350, Validators.required],
    otherDebtRepayments: [0],
    purpose: ['Stock for market stall'],
  });

  ngOnInit(): void {
    this.api.applications().subscribe(a => this.applications.set(a));
    if (this.auth.hasAnyRole('OFFICER')) {
      this.api.borrowers().subscribe(b => this.borrowers.set(b));
      this.api.products().subscribe(p => this.products.set(p));
      if (this.borrower()) this.form.patchValue({ borrowerId: this.borrower()! });
    }
  }

  capture(): void {
    this.api.captureApplication(this.form.getRawValue())
      .subscribe(a => this.router.navigate(['/applications', a.id]));
  }
}
