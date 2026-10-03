import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { HttpActivityStore } from '../../../../core/http/http-activity.store';
import { httpStatusLabel } from '../../../../core/http/http-status';

@Component({
  selector: 'app-http-messages',
  imports: [DatePipe],
  templateUrl: './http-messages.html',
  styleUrl: './http-messages.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HttpMessages {
  protected readonly activity = inject(HttpActivityStore);
  protected readonly statusLabel = httpStatusLabel;

  protected statusClass(status: number): string {
    if (status >= 200 && status < 300) return 'code--ok';
    if (status >= 400 && status < 500) return 'code--client';
    return 'code--server';
  }
}
