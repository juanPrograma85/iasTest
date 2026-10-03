import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icon, IconName } from '../../../../core/components/icon/icon';

export type KpiTone = 'neutral' | 'success' | 'danger' | 'brand';

@Component({
  selector: 'app-kpi-card',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="kpi kpi--{{ tone() }}">
      <span class="kpi__icon"><app-icon [name]="icon()" [size]="22" /></span>
      <div>
        <span class="kpi__label">{{ label() }}</span>
        <strong class="kpi__value" data-testid="kpi-value">{{ value() }}</strong>
      </div>
    </article>
  `,
  styles: `
    .kpi {
      --tone: var(--secondary);
      --tone-bg: var(--secondary-soft);
      display: flex;
      align-items: center;
      gap: 1rem;
      height: 100%;
      box-sizing: border-box;
      padding: 1.25rem;
      border: 1px solid var(--border);
      border-radius: var(--radius);
      background: var(--surface);
      box-shadow: var(--shadow);
      transition: box-shadow 160ms ease, transform 160ms ease;
    }
    .kpi:hover { box-shadow: var(--shadow-hover); transform: translateY(-1px); }
    .kpi--success { --tone: var(--approved-fg); --tone-bg: var(--approved-bg); }
    .kpi--danger { --tone: var(--rejected-fg); --tone-bg: var(--rejected-bg); }
    .kpi--brand { --tone: #fff; --tone-bg: var(--brand); }
    .kpi__icon {
      display: grid;
      place-items: center;
      width: 2.75rem;
      height: 2.75rem;
      border-radius: 12px;
      background: var(--tone-bg);
      color: var(--tone);
    }
    .kpi__label { display: block; font-size: 0.8rem; color: var(--muted); }
    .kpi__value { display: block; margin-top: 0.15rem; font-size: 1.45rem; color: var(--text); letter-spacing: -0.02em; }
  `,
})
export class KpiCard {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly icon = input.required<IconName>();
  readonly tone = input<KpiTone>('neutral');
}
