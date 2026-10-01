import { DatePipe, DecimalPipe, LowerCasePipe, NgClass, PercentPipe } from '@angular/common';
import { Pipe, PipeTransform } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';

/** "USD 1,234.56" — amounts are shown with their currency, never a bare number (USD and ZWG coexist). */
@Pipe({ name: 'money', standalone: true })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined, currency = 'USD'): string {
    if (value === null || value === undefined) return '—';
    return `${currency} ${Number(value).toLocaleString('en-ZW', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }
}

/** Status → chip colour class. */
@Pipe({ name: 'statusClass', standalone: true })
export class StatusClassPipe implements PipeTransform {
  transform(status: string | null | undefined): string {
    switch (status) {
      case 'ACTIVE': case 'COMPLETED': case 'ACCEPTED': case 'POSTED': case 'PAID': case 'CLOSED': return 'ok';
      case 'IN_ARREARS': case 'FAILED': case 'DECLINED': case 'WRITTEN_OFF': case 'REVERSED': return 'bad';
      case 'RESTRUCTURED': case 'OFFERED': case 'SCORED': case 'AWAITING_COVER': case 'COVERED': return 'warn';
      default: return 'neutral';
    }
  }
}

export const UI = [
  NgClass, DatePipe, DecimalPipe, LowerCasePipe, PercentPipe, FormsModule, ReactiveFormsModule, RouterLink, MatButtonModule, MatCardModule,
  MatChipsModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressBarModule, MatSelectModule,
  MatTableModule, MatTabsModule, MoneyPipe, StatusClassPipe,
];
