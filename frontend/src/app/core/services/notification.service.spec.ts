import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    service = TestBed.inject(NotificationService);
  });

  it('adds typed notifications and dismisses them manually', fakeAsync(() => {
    const id = service.success({ title: 'OK', message: 'Hecho' });
    service.error({ title: 'Error', message: 'Falló', httpStatus: '409 Conflict' });

    expect(service.items().map((n) => n.type)).toEqual(['success', 'error']);
    service.dismiss(id);
    expect(service.items().map((n) => n.type)).toEqual(['error']);
    tick(10000);
  }));

  it('auto-dismisses success sooner than errors', fakeAsync(() => {
    service.success({ title: 'OK', message: 'Hecho' });
    service.error({ title: 'Error', message: 'Falló' });

    tick(5000);
    expect(service.items().map((n) => n.type)).toEqual(['error']);
    tick(3000);
    expect(service.items()).toEqual([]);
  }));

  it('keeps at most five visible notifications', fakeAsync(() => {
    for (let i = 0; i < 7; i++) {
      service.info({ title: `N${i}`, message: '' });
    }

    expect(service.items().map((n) => n.title)).toEqual(['N2', 'N3', 'N4', 'N5', 'N6']);
    tick(10000);
  }));
});
