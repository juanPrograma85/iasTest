import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icon, IconName } from '../../../../core/components/icon/icon';

@Component({
  selector: 'app-empty-state',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty">
      <span class="empty__icon"><app-icon [name]="icon()" [size]="28" /></span>
      <strong>{{ title() }}</strong>
      @if (description()) {
        <p>{{ description() }}</p>
      }
      <ng-content />
    </div>
  `,
  styles: `
    .empty {
      display: grid;
      justify-items: center;
      gap: 0.4rem;
      padding: 2.5rem 1rem;
      text-align: center;
      color: var(--muted);
    }
    .empty__icon {
      display: grid;
      place-items: center;
      width: 3.5rem;
      height: 3.5rem;
      margin-bottom: 0.4rem;
      border-radius: 50%;
      background: var(--surface-alt);
      color: var(--secondary);
    }
    strong { color: var(--text); }
    p { margin: 0; max-width: 28rem; }
  `,
})
export class EmptyState {
  readonly icon = input<IconName>('inbox');
  readonly title = input.required<string>();
  readonly description = input<string>('');
}
