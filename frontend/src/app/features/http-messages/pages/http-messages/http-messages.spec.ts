import { TestBed } from '@angular/core/testing';
import { HttpActivityStore } from '../../../../core/http/http-activity.store';
import { HttpMessages } from './http-messages';

describe('HttpMessages', () => {
  it('lists recorded HTTP activity', () => {
    TestBed.configureTestingModule({ imports: [HttpMessages] });
    const activity = TestBed.inject(HttpActivityStore);
    activity.record({
      method: 'POST',
      url: '/api/v1/credit-applications',
      status: 409,
      ok: false,
      code: 'APPLICATION_REFERENCE_CONFLICT',
      message: 'La referencia ya fue procesada con datos diferentes y no puede reutilizarse.',
      durationMs: 12,
    });
    const fixture = TestBed.createComponent(HttpMessages);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    const rows = el.querySelectorAll('[data-testid="activity-row"]');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('409 Conflict');
    expect(rows[0].textContent).toContain('APPLICATION_REFERENCE_CONFLICT');
    expect(el.textContent).not.toContain('Catálogo');
  });

  it('shows an empty state and clears the activity', () => {
    TestBed.configureTestingModule({ imports: [HttpMessages] });
    const activity = TestBed.inject(HttpActivityStore);
    activity.record({ method: 'GET', url: '/api/v1/x', status: 200, ok: true, message: 'ok', durationMs: 1 });
    const fixture = TestBed.createComponent(HttpMessages);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    el.querySelector<HTMLButtonElement>('.page__header button')!.click();
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="activity-empty"]')).not.toBeNull();
  });
});
