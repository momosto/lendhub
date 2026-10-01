import { Routes } from '@angular/router';

import { roleGuard } from './core/guards';

/** Lazy-loaded feature routes; role lists mirror the API's @PreAuthorize rules (the API is the real gate). */
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login.component').then(m => m.LoginComponent) },
  {
    path: '',
    canActivate: [roleGuard],
    loadComponent: () => import('./features/dashboard.component').then(m => m.DashboardComponent),
  },
  {
    path: 'borrowers',
    canActivate: [roleGuard],
    data: { roles: ['OFFICER', 'MANAGER', 'COMMITTEE', 'FINANCE', 'COLLECTIONS', 'AUDITOR'] },
    loadComponent: () => import('./features/borrowers.component').then(m => m.BorrowersComponent),
  },
  {
    path: 'applications',
    canActivate: [roleGuard],
    data: { roles: ['OFFICER', 'MANAGER', 'COMMITTEE', 'FINANCE', 'AUDITOR'] },
    loadComponent: () => import('./features/applications.component').then(m => m.ApplicationsComponent),
  },
  {
    path: 'applications/:id',
    canActivate: [roleGuard],
    data: { roles: ['OFFICER', 'MANAGER', 'COMMITTEE', 'FINANCE', 'AUDITOR'] },
    loadComponent: () => import('./features/application-detail.component').then(m => m.ApplicationDetailComponent),
  },
  {
    path: 'loans',
    canActivate: [roleGuard],
    data: { roles: ['OFFICER', 'MANAGER', 'COMMITTEE', 'FINANCE', 'COLLECTIONS', 'AUDITOR'] },
    loadComponent: () => import('./features/loans.component').then(m => m.LoansComponent),
  },
  {
    path: 'loans/:id',
    canActivate: [roleGuard],
    data: { roles: ['OFFICER', 'MANAGER', 'COMMITTEE', 'FINANCE', 'COLLECTIONS', 'AUDITOR'] },
    loadComponent: () => import('./features/loan-detail.component').then(m => m.LoanDetailComponent),
  },
  {
    path: 'collections',
    canActivate: [roleGuard],
    data: { roles: ['COLLECTIONS', 'MANAGER', 'AUDITOR'] },
    loadComponent: () => import('./features/collections.component').then(m => m.CollectionsComponent),
  },
  {
    path: 'committee',
    canActivate: [roleGuard],
    data: { roles: ['COMMITTEE', 'MANAGER', 'COLLECTIONS', 'AUDITOR'] },
    loadComponent: () => import('./features/committee.component').then(m => m.CommitteeComponent),
  },
  {
    path: 'finance',
    canActivate: [roleGuard],
    data: { roles: ['FINANCE', 'AUDITOR'] },
    loadComponent: () => import('./features/finance.component').then(m => m.FinanceComponent),
  },
  { path: '**', redirectTo: '' },
];
