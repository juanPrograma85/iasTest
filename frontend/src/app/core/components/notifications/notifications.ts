import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NotificationType } from '../../models/notification.model';
import { NotificationService } from '../../services/notification.service';

const ICONS: Record<NotificationType, string> = {
  success: '✓',
  error: '✕',
  warning: '!',
  info: 'i',
};

@Component({
  selector: 'app-notifications',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack" aria-live="polite">
      @for (item of service.items(); track item.id) {
        <div class="toast toast--{{ item.type }}" [attr.role]="item.type === 'error' ? 'alert' : 'status'"
             [attr.data-type]="item.type">
          <span class="toast__icon" aria-hidden="true">{{ icons[item.type] }}</span>
          <div class="toast__body">
            <strong>{{ item.title }}</strong>
            <p>{{ item.message }}</p>
            @if (item.httpStatus) {
              <small>HTTP {{ item.httpStatus }}</small>
            }
          </div>
          <button type="button" class="toast__close" aria-label="Cerrar notificación"
                  (click)="service.dismiss(item.id)">×</button>
        </div>
      }
    </div>
  `,
  styles: `
    .stack {
      position: fixed;
      bottom: 1rem;
      right: 1rem;
      z-index: 1000;
      display: grid;
      gap: 0.6rem;
      width: min(24rem, calc(100vw - 2rem));
    }
    .toast {
      display: grid;
      grid-template-columns: auto 1fr auto;
      gap: 0.75rem;
      align-items: start;
      padding: 0.8rem 0.9rem;
      border-radius: var(--radius);
      border-left: 5px solid var(--accent);
      background: var(--surface);
      box-shadow: 0 6px 20px rgb(16 32 74 / 18%);
      animation: enter 160ms ease-out;
    }
    .toast--success { --accent: var(--approved-fg); }
    .toast--error { --accent: var(--rejected-fg); }
    .toast--warning { --accent: var(--warning-fg); }
    .toast--info { --accent: var(--info-fg); }
    .toast__icon {
      display: grid;
      place-items: center;
      width: 1.6rem;
      height: 1.6rem;
      border-radius: 50%;
      background: var(--accent);
      color: #fff;
      font-weight: 700;
      font-size: 0.85rem;
    }
    .toast__body p { margin: 0.2rem 0; }
    .toast__body small { color: var(--muted); }
    .toast__close {
      border: none;
      background: none;
      font-size: 1.3rem;
      line-height: 1;
      cursor: pointer;
      color: var(--muted);
    }
    @keyframes enter {
      from { opacity: 0; transform: translateY(6px); }
      to { opacity: 1; transform: none; }
    }
  `,
})
export class Notifications {
  protected readonly service = inject(NotificationService);
  protected readonly icons = ICONS;
}
