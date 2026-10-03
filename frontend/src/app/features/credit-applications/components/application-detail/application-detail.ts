import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icon } from '../../../../core/components/icon/icon';
import { CreditApplication } from '../../models/credit-application.model';
import { DecisionReasonLabelPipe } from '../../pipes/credit-application-labels.pipe';
import { StatusBadge } from '../status-badge/status-badge';

@Component({
  selector: 'app-application-detail',
  imports: [CurrencyPipe, DatePipe, DecisionReasonLabelPipe, StatusBadge, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './application-detail.html',
  styleUrl: './application-detail.scss',
})
export class ApplicationDetail {
  readonly application = input.required<CreditApplication>();
}
