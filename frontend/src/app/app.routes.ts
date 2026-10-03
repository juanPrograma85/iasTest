import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'credit-applications/new' },
  {
    path: 'credit-applications/new',
    title: 'Nueva solicitud | BancoIAS',
    loadComponent: () =>
      import('./features/credit-applications/pages/credit-application-form/credit-application-form').then(
        (m) => m.CreditApplicationForm,
      ),
  },
  {
    path: 'credit-applications/recent',
    title: 'Solicitudes recientes | BancoIAS',
    loadComponent: () =>
      import('./features/credit-applications/pages/recent-applications/recent-applications').then(
        (m) => m.RecentApplications,
      ),
  },
  {
    path: 'http-messages',
    title: 'Mensajes HTTP | BancoIAS',
    loadComponent: () =>
      import('./features/http-messages/pages/http-messages/http-messages').then((m) => m.HttpMessages),
  },
  { path: '**', redirectTo: 'credit-applications/new' },
];
