import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { apiUrl } from './env';
import {
  Application, Borrower, ChangeRequest, CollectionTask, Currency, EodRun, Loan, ParReport, PayrollBatch, Portfolio,
  Product, Repayment, SettlementQuote, TrialBalance,
} from './models';

/** Typed client for the LendHub REST API. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private url(path: string): string {
    return `${apiUrl()}/api/v1${path}`;
  }

  businessDate(): Observable<{ businessDate: string }> { return this.http.get<{ businessDate: string }>(this.url('/eod/business-date')); }
  products(): Observable<Product[]> { return this.http.get<Product[]>(this.url('/products')); }

  borrowers(): Observable<Borrower[]> { return this.http.get<Borrower[]>(this.url('/borrowers')); }
  registerBorrower(body: object): Observable<Borrower> { return this.http.post<Borrower>(this.url('/borrowers'), body); }

  applications(): Observable<Application[]> { return this.http.get<Application[]>(this.url('/applications')); }
  application(id: string): Observable<Application> { return this.http.get<Application>(this.url(`/applications/${id}`)); }
  captureApplication(body: object): Observable<Application> { return this.http.post<Application>(this.url('/applications'), body); }
  submitApplication(id: string): Observable<Application> { return this.http.post<Application>(this.url(`/applications/${id}/submit`), {}); }
  decide(id: string, approve: boolean, reasonCode?: string, comment?: string): Observable<Application> {
    return this.http.post<Application>(this.url(`/applications/${id}/decisions`), { approve, reasonCode, comment });
  }
  acceptOffer(id: string, otp: string): Observable<Application> {
    return this.http.post<Application>(this.url(`/applications/${id}/offer/accept`), { otp });
  }
  reissueOtp(id: string): Observable<Application> { return this.http.post<Application>(this.url(`/applications/${id}/offer/otp`), {}); }
  declineReasons(): Observable<string[]> { return this.http.get<string[]>(this.url('/applications/decline-reasons')); }

  loans(): Observable<Loan[]> { return this.http.get<Loan[]>(this.url('/loans')); }
  loan(id: string): Observable<Loan> { return this.http.get<Loan>(this.url(`/loans/${id}`)); }
  disburse(id: string): Observable<Loan> { return this.http.post<Loan>(this.url(`/loans/${id}/disburse`), {}); }
  retryCover(id: string): Observable<Loan> { return this.http.post<Loan>(this.url(`/loans/${id}/credit-life`), {}); }
  settlementQuote(id: string): Observable<SettlementQuote> { return this.http.get<SettlementQuote>(this.url(`/loans/${id}/settlement-quote`)); }
  repayments(loanId: string): Observable<Repayment[]> { return this.http.get<Repayment[]>(this.url(`/loans/${loanId}/repayments`)); }
  recordReceipt(loanId: string, amount: number, channel: string, reference: string): Observable<Repayment> {
    return this.http.post<Repayment>(this.url(`/loans/${loanId}/repayments`), { amount, channel, reference });
  }
  settle(loanId: string, amount: number, channel: string, reference: string): Observable<Repayment> {
    return this.http.post<Repayment>(this.url(`/loans/${loanId}/settlements`), { amount, channel, reference });
  }
  reverse(repaymentId: string, reason: string): Observable<Repayment> {
    return this.http.post<Repayment>(this.url(`/repayments/${repaymentId}/reversal`), { reason });
  }
  requestEcoCash(loanId: string, amount: number): Observable<unknown> {
    return this.http.post(this.url(`/loans/${loanId}/repayment-requests`), { amount },
      { headers: new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID() }) });
  }
  requestChange(loanId: string, type: 'restructure' | 'write-off', reason: string, newInstalments?: number): Observable<ChangeRequest> {
    return this.http.post<ChangeRequest>(this.url(`/loans/${loanId}/${type}`), { reason, newInstalments });
  }
  pendingChanges(): Observable<ChangeRequest[]> { return this.http.get<ChangeRequest[]>(this.url('/loan-change-requests')); }
  decideChange(id: string, approve: boolean): Observable<ChangeRequest> {
    return this.http.post<ChangeRequest>(this.url(`/loan-change-requests/${id}/decision`), { approve });
  }
  audit(loanId: string): Observable<{ revision: number; at: string; user: string; type: string; status: string; daysPastDue: number }[]> {
    return this.http.get<{ revision: number; at: string; user: string; type: string; status: string; daysPastDue: number }[]>(
      this.url(`/audit/loans/${loanId}`));
  }

  tasks(open = true): Observable<CollectionTask[]> { return this.http.get<CollectionTask[]>(this.url(`/collections/tasks?open=${open}`)); }
  loanTasks(loanId: string): Observable<CollectionTask[]> {
    return this.http.get<CollectionTask[]>(this.url(`/collections/loans/${loanId}/tasks`));
  }
  completeTask(id: string, note: string): Observable<CollectionTask> {
    return this.http.post<CollectionTask>(this.url(`/collections/tasks/${id}/complete`), { note });
  }
  promise(taskId: string, promisedDate: string, amount: number): Observable<unknown> {
    return this.http.post(this.url(`/collections/tasks/${taskId}/promises`), { promisedDate, amount });
  }

  runEod(days: number): Observable<EodRun[]> { return this.http.post<EodRun[]>(this.url('/eod/runs'), { days }); }
  eodHistory(): Observable<EodRun[]> { return this.http.get<EodRun[]>(this.url('/eod/runs')); }
  trialBalance(): Observable<TrialBalance> { return this.http.get<TrialBalance>(this.url('/ledger/trial-balance')); }
  runProvisioning(): Observable<unknown[]> { return this.http.post<unknown[]>(this.url('/provisioning/runs'), {}); }
  uploadPayroll(file: File): Observable<PayrollBatch> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<PayrollBatch>(this.url('/payroll-batches'), form);
  }

  par(currency: Currency = 'USD'): Observable<ParReport> { return this.http.get<ParReport>(this.url(`/reports/par?currency=${currency}`)); }
  portfolio(currency: Currency = 'USD'): Observable<Portfolio> {
    return this.http.get<Portfolio>(this.url(`/reports/portfolio?currency=${currency}`));
  }
  regulatoryReturnUrl(currency: Currency = 'USD'): string { return this.url(`/reports/regulatory-return?currency=${currency}`); }
  regulatoryReturn(currency: Currency = 'USD'): Observable<string> {
    return this.http.get(this.regulatoryReturnUrl(currency), { responseType: 'text' });
  }
}
