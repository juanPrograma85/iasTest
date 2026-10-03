import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NotificationService } from '../../services/notification.service';
import { Notifications } from './notifications';

describe('Notifications', () => {
  it('renders success and error toasts with HTTP status and closes them', fakeAsync(() => {
    TestBed.configureTestingModule({ imports: [Notifications] });
    const service = TestBed.inject(NotificationService);
    const fixture = TestBed.createComponent(Notifications);
    const el: HTMLElement = fixture.nativeElement;

    service.success({ title: 'Solicitud aprobada', message: 'REF-1', httpStatus: '201 Created' });
    service.error({ title: 'No encontrada', message: 'REF-2', httpStatus: '404 Not Found' });
    fixture.detectChanges();

    const toasts = el.querySelectorAll('.toast');
    expect(toasts.length).toBe(2);
    expect(toasts[0].getAttribute('data-type')).toBe('success');
    expect(toasts[0].getAttribute('role')).toBe('status');
    expect(toasts[0].textContent).toContain('HTTP 201 Created');
    expect(toasts[1].getAttribute('role')).toBe('alert');

    toasts[0].querySelector<HTMLButtonElement>('.toast__close')!.click();
    fixture.detectChanges();
    expect(el.querySelectorAll('.toast').length).toBe(1);
    tick(10000);
  }));
});
