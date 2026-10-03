import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const ICONS = {
  hash: ['M4 9h16', 'M4 15h16', 'M10 3 8 21', 'M16 3l-2 18'],
  user: ['M8 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0', 'M4 21c0-4 3.6-6 8-6s8 2 8 6'],
  cash: [
    'M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z',
    'M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0',
    'M6 12h.01',
    'M18 12h.01',
  ],
  calendar: ['M5 4h14a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z', 'M3 10h18', 'M8 2v4', 'M16 2v4'],
  clock: ['M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0', 'M12 6v6l4 2'],
  send: ['M22 2 11 13', 'M22 2l-7 20-4-9-9-4z'],
  search: ['M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0', 'm21 21-4.3-4.3'],
  refresh: ['M21 12a9 9 0 1 1-2.6-6.4L21 8', 'M21 3v5h-5'],
  check: ['M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0', 'm8 12 3 3 5-6'],
  cross: ['M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0', 'm15 9-6 6', 'm9 9 6 6'],
  document: ['M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z', 'M14 2v6h6', 'M8 13h8', 'M8 17h5'],
  trend: ['M3 17l6-6 4 4 8-8', 'M14 7h7v7'],
  inbox: ['M22 12h-6l-2 3h-4l-2-3H2', 'M5.5 5h13L22 12v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-6z'],
  shield: ['M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z', 'm9 12 2 2 4-4'],
  info: ['M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0', 'M12 16v-4', 'M12 8h.01'],
  filter: ['M3 5h18l-7 8v6l-4 2v-8z'],
  wallet: [
    'M19 7V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-3',
    'M21 11h-5a2 2 0 0 0 0 4h5z',
  ],
} as const;

export type IconName = keyof typeof ICONS;

/** Íconos SVG en línea: sin fuentes externas, compatibles con la CSP del sitio. */
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 24 24" [attr.width]="size()" [attr.height]="size()" fill="none" stroke="currentColor"
         stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
      @for (d of paths(); track $index) {
        <path [attr.d]="d" />
      }
    </svg>
  `,
  styles: `
    :host { display: inline-flex; line-height: 0; flex-shrink: 0; }
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(20);
  protected readonly paths = computed(() => ICONS[this.name()]);
}
