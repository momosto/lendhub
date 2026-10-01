/** API shapes (mirrors the Spring DTOs; see /v3/api-docs). */
export type Role = 'OFFICER' | 'MANAGER' | 'COMMITTEE' | 'FINANCE' | 'COLLECTIONS' | 'AUDITOR' | 'CHANNEL';
export type Currency = 'USD' | 'ZWG';

export interface Money { amount: number; currency: Currency; }

export interface Session {
  accessToken: string;
  username: string;
  displayName: string;
  roles: Role[];
  branch?: string;
  expiresAt: number;
}

export interface DemoUser { username: string; displayName: string; roles: Role[]; }

export interface Product {
  code: string; name: string; method: string; frequencies: string[];
  monthlyInterestRate: number; establishmentFeeRate: number; creditLifeMonthlyRate: number;
  minAmount: number; maxAmount: number; minTermMonths: number; maxTermMonths: number; groupLending: boolean;
}

export interface Borrower {
  id: string; customerRef: string; firstName: string; lastName: string; nationalIdMasked: string; msisdn: string;
  branch: string; employmentType: string; employer?: string; yearsInEmployment: number; createdAt: string;
}

export interface Offer {
  instalment: number; totalInterest: number; establishmentFee: number; totalCreditLife: number;
  totalRepayable: number; netDisbursed: number; effectiveAnnualRatePercent: number; expiresOn: string;
}

export interface Application {
  id: string; applicationNumber: string; borrowerId: string; customerRef: string; borrowerName: string; branch: string;
  productCode: string; currency: Currency; amount: number; instalments: number; frequency: string; purpose?: string;
  monthlyIncome: number; monthlyExpenses: number; status: string; capturedBy: string; createdAt: string;
  monthlyInstalmentEstimate?: number; disposableIncome?: number; affordabilityRatio?: number; affordable: boolean;
  scorePoints?: number; grade?: string; scoreReasons: string[]; bureauStatus?: string; relatedPartyFlag: boolean;
  decidedBy?: string; declineReason?: string; offer?: Offer; acceptedAt?: string; demoOtp?: string;
}

export interface Instalment {
  seq: number; dueDate: string; state: string; principalDue: number; interestDue: number; creditLifeDue: number;
  feesDue: number; penaltyDue: number; totalDue: number; totalPaid: number; outstanding: number; interestAccrued: number;
  paidOn?: string;
}

export interface Loan {
  id: string; loanNumber: string; applicationNumber: string; customerRef: string; borrowerName: string; branch: string;
  productCode: string; currency: Currency; principal: number; frequency: string; instalmentCount: number;
  establishmentFee?: number; netDisbursed?: number; status: string; creditLifePolicyNumber?: string; coverError?: string;
  disbursedOn?: string; maturityDate?: string; daysPastDue: number; arrearsBucket: string; restructured: boolean;
  outstandingPrincipal: number; interestReceivable: number; arrearsAmount: number; creditBalance: number;
  capturedBy?: string; approvedBy?: string; disbursedBy?: string; schedule?: Instalment[];
}

export interface Repayment {
  id: string; providerReference: string; channel: string; type: string; currency: Currency; amount: number;
  creditAdded: number; valueDate: string; status: string; postedBy?: string; reversalReason?: string;
  allocations: { instalmentSeq: number; component: string; amount: number }[];
}

export interface SettlementQuote {
  outstandingPrincipal: Money; accruedInterest: Money; arrearsCharges: Money; settlementFee: Money;
  creditBalance: Money; total: Money;
}

export interface CollectionTask {
  id: string; loanId: string; loanNumber: string; customerRef: string; borrowerName: string; type: string;
  dpdAtCreation: number; currency: Currency; arrearsAmount: number; priority: number; status: string; createdOn: string;
  notes?: string;
}

export interface ChangeRequest {
  id: string; loanId: string; loanNumber: string; type: 'RESTRUCTURE' | 'WRITE_OFF'; newInstalments?: number;
  reason: string; requestedBy: string; requestedAt: string; status: string; decidedBy?: string;
}

export interface EodRun {
  id: string; businessDate: string; status: string; startedBy: string; loansProcessed: number; arrearsChanges: number;
  tasksCreated: number; remindersSent: number; offersExpired: number; promisesBroken: number; monthEnd: boolean; error?: string;
}

export interface TrialBalanceRow {
  accountCode: string; accountName: string; type: string; currency: Currency; debits: number; credits: number; balance: number;
}
export interface TrialBalance {
  rows: TrialBalanceRow[]; totalDebits: Record<string, number>; totalCredits: Record<string, number>; balanced: boolean;
}

export interface ParLine {
  key: string; loans: number; portfolio: number; par1: number; par30: number; par60: number; par90: number; par30Percent: number;
}
export interface ParReport {
  asOf: string; currency: Currency; total: ParLine; byBranch: ParLine[]; byProduct: ParLine[]; byOfficer: ParLine[];
  buckets: Record<string, number>;
}
export interface Portfolio {
  asOf: string; currency: Currency; activeLoans: number; grossLoanPortfolio: number; disbursedMtdCount: number;
  disbursedMtdAmount: number; dueMtd: number; collectedMtd: number; collectionEfficiencyPercent?: number;
  par30Percent: number; loansByStatus: Record<string, number>;
}

export interface PayrollBatch {
  id: string; fileName: string; uploadedAt: string; matched: number; unmatched: number; totalPosted: number;
  lines: { lineNo: number; employer: string; nationalIdMasked?: string; amount: number; period: string; status: string;
    loanNumber?: string; message: string }[];
}

export interface Problem { title?: string; detail?: string; status?: number; errors?: { field: string; message: string }[]; }
