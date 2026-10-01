import { Component, OnInit, inject, signal } from '@angular/core';

import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { CollectionTask } from '../core/models';
import { UI } from '../core/shared';

/** LH-60/61: today's worklist (biggest × oldest arrears first), outcomes and promises to pay. */
@Component({
  selector: 'app-collections',
  standalone: true,
  imports: [UI],
  template: `
    <h1>Collections worklist</h1>
    <p class="muted">Tasks are created by the end-of-day batch at 7 (call), 30 (visit), 60 (demand letter) and 90 (legal review)
      days past due. A broken promise creates a follow-up.</p>
    @for (t of tasks(); track t.id) {
      <div class="card">
        <div class="row">
          <div class="grow">
            <strong>{{ t.type }}</strong> · <a [routerLink]="['/loans', t.loanId]">{{ t.loanNumber }}</a> · {{ t.borrowerName }}
            <div class="muted">{{ t.dpdAtCreation }} DPD when created on {{ t.createdOn }} · arrears {{ t.arrearsAmount | money: t.currency }}</div>
            @if (t.notes) { <div class="muted">{{ t.notes }}</div> }
          </div>
          @if (auth.hasAnyRole('COLLECTIONS')) {
            <div class="actions">
              <mat-form-field style="width: 150px"><mat-label>Promise date</mat-label><input matInput type="date" [(ngModel)]="promiseDate[t.id]"></mat-form-field>
              <mat-form-field style="width: 120px"><mat-label>Amount</mat-label><input matInput type="number" [(ngModel)]="promiseAmount[t.id]"></mat-form-field>
              <button mat-stroked-button [disabled]="!promiseDate[t.id] || !promiseAmount[t.id]" (click)="promise(t)">Record promise</button>
              <button mat-flat-button color="primary" (click)="complete(t)">Done</button>
            </div>
          }
        </div>
      </div>
    } @empty { <p class="muted">No open tasks — run end-of-day from Finance to age the book.</p> }
  `,
})
export class CollectionsComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  readonly tasks = signal<CollectionTask[]>([]);
  promiseDate: Record<string, string> = {};
  promiseAmount: Record<string, number> = {};

  ngOnInit(): void { this.load(); }
  load(): void { this.api.tasks(true).subscribe(t => this.tasks.set(t)); }

  promise(t: CollectionTask): void {
    this.api.promise(t.id, this.promiseDate[t.id], this.promiseAmount[t.id]).subscribe(() => this.complete(t, 'Promise recorded'));
  }

  complete(t: CollectionTask, note = 'Contacted'): void {
    this.api.completeTask(t.id, note).subscribe(() => this.load());
  }
}
