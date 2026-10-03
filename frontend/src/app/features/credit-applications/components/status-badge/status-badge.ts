import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CreditApplicationStatus } from '../../models/credit-application.model';
import { StatusLabelPipe } from '../../pipes/credit-application-labels.pipe';

@Component({
  selector: 'app-status-badge',
  imports: [StatusLabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="chip" [class.chip--approved]="status() === 'APPROVED'"
          [class.chip--rejected]="status() === 'REJECTED'" [attr.data-status]="status()">
      <span class="chip__dot" aria-hidden="true"></span>
      {{ status() | statusLabel }}
    </span>
  `,
  styles: `
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.25rem 0.7rem;
      border-radius: 999px;
      font-size: 0.78rem;
      font-weight: 600;
      white-space: nowrap;
    }
    .chip__dot { width: 0.45rem; height: 0.45rem; border-radius: 50%; background: currentColor; }
    .chip--approved { background: var(--approved-bg); color: var(--approved-fg); }
    .chip--rejected { background: var(--rejected-bg); color: var(--rejected-fg); }
  `,
})
export class StatusBadge {
  readonly status = input.required<CreditApplicationStatus>();
}
