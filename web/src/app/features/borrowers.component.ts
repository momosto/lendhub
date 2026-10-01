import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Borrower } from '../core/models';
import { UI } from '../core/shared';

/** LH-01: register borrowers with KYC and bureau consent; list is branch-scoped and masks national IDs. */
@Component({
  selector: 'app-borrowers',
  standalone: true,
  imports: [UI, MatCheckboxModule],
  template: `
    <h1>Borrowers</h1>
    @if (auth.hasAnyRole('OFFICER')) {
      <div class="card">
        <h2>Register a borrower</h2>
        <form [formGroup]="form" (ngSubmit)="register()">
          <div class="form-grid">
            <mat-form-field><mat-label>First name</mat-label><input matInput formControlName="firstName"></mat-form-field>
            <mat-form-field><mat-label>Last name</mat-label><input matInput formControlName="lastName"></mat-form-field>
            <mat-form-field><mat-label>National ID</mat-label><input matInput formControlName="nationalId" placeholder="63-123456A78">
              <mat-hint>Serial with "99" = adverse bureau listing (demo)</mat-hint></mat-form-field>
            <mat-form-field><mat-label>Mobile (EcoCash)</mat-label><input matInput formControlName="msisdn" placeholder="0771234567"></mat-form-field>
            <mat-form-field><mat-label>Date of birth</mat-label><input matInput type="date" formControlName="dateOfBirth"></mat-form-field>
            <mat-form-field><mat-label>Address</mat-label><input matInput formControlName="address"></mat-form-field>
            <mat-form-field><mat-label>Employment</mat-label>
              <mat-select formControlName="employmentType">
                <mat-option value="SALARIED">Salaried</mat-option><mat-option value="SELF_EMPLOYED">Self-employed / trader</mat-option>
              </mat-select></mat-form-field>
            <mat-form-field><mat-label>Employer / business</mat-label><input matInput formControlName="employer"></mat-form-field>
            <mat-form-field><mat-label>Years in job / business</mat-label><input matInput type="number" formControlName="yearsInEmployment"></mat-form-field>
          </div>
          <mat-checkbox formControlName="bureauConsent">Borrower consents to a credit bureau check (consent text v1)</mat-checkbox>
          <div class="actions"><button mat-flat-button color="primary" [disabled]="form.invalid">Register</button></div>
        </form>
      </div>
    }
    <div class="card">
      <table mat-table [dataSource]="borrowers()">
        <ng-container matColumnDef="ref"><th mat-header-cell *matHeaderCellDef>Customer</th><td mat-cell *matCellDef="let b">{{ b.customerRef }}</td></ng-container>
        <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Name</th><td mat-cell *matCellDef="let b">{{ b.firstName }} {{ b.lastName }}</td></ng-container>
        <ng-container matColumnDef="id"><th mat-header-cell *matHeaderCellDef>National ID</th><td mat-cell *matCellDef="let b">{{ b.nationalIdMasked }}</td></ng-container>
        <ng-container matColumnDef="msisdn"><th mat-header-cell *matHeaderCellDef>Mobile</th><td mat-cell *matCellDef="let b">{{ b.msisdn }}</td></ng-container>
        <ng-container matColumnDef="emp"><th mat-header-cell *matHeaderCellDef>Employment</th><td mat-cell *matCellDef="let b">{{ b.employmentType }} · {{ b.employer }}</td></ng-container>
        <ng-container matColumnDef="branch"><th mat-header-cell *matHeaderCellDef>Branch</th><td mat-cell *matCellDef="let b">{{ b.branch }}</td></ng-container>
        <ng-container matColumnDef="act"><th mat-header-cell *matHeaderCellDef></th><td mat-cell *matCellDef="let b">
          @if (auth.hasAnyRole('OFFICER')) { <a [routerLink]="['/applications']" [queryParams]="{ borrower: b.id }">New application</a> }
        </td></ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr><tr mat-row *matRowDef="let row; columns: cols"></tr>
      </table>
      @if (borrowers().length === 0) { <p class="muted">No borrowers yet.</p> }
    </div>
  `,
})
export class BorrowersComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  readonly borrowers = signal<Borrower[]>([]);
  readonly cols = ['ref', 'name', 'id', 'msisdn', 'emp', 'branch', 'act'];

  readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    nationalId: ['', [Validators.required, Validators.pattern(/^\d{2}-?\d{6,7}[A-Za-z]\d{2}$/)]],
    msisdn: ['', Validators.required],
    dateOfBirth: ['', Validators.required],
    address: [''],
    employmentType: ['SELF_EMPLOYED', Validators.required],
    employer: [''],
    yearsInEmployment: [1, [Validators.min(0)]],
    bureauConsent: [false, Validators.requiredTrue],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.borrowers().subscribe(b => this.borrowers.set(b));
  }

  register(): void {
    this.api.registerBorrower(this.form.getRawValue()).subscribe(() => {
      this.form.reset();
      this.load();
    });
  }
}
