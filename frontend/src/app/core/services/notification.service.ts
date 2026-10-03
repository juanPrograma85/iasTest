import { Injectable, signal } from '@angular/core';
import { AppNotification, NotificationType } from '../models/notification.model';

type NotificationInput = Omit<AppNotification, 'id' | 'type'>;

const MAX_VISIBLE = 5;
const DURATION_MS: Record<NotificationType, number> = {
  success: 5000,
  info: 5000,
  warning: 7000,
  error: 8000,
};

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 0;
  private readonly _items = signal<AppNotification[]>([]);

  readonly items = this._items.asReadonly();

  success(input: NotificationInput): number {
    return this.show('success', input);
  }

  error(input: NotificationInput): number {
    return this.show('error', input);
  }

  warning(input: NotificationInput): number {
    return this.show('warning', input);
  }

  info(input: NotificationInput): number {
    return this.show('info', input);
  }

  dismiss(id: number): void {
    this._items.update((items) => items.filter((item) => item.id !== id));
  }

  private show(type: NotificationType, input: NotificationInput): number {
    const id = ++this.nextId;
    this._items.update((items) => [...items, { ...input, id, type }].slice(-MAX_VISIBLE));
    setTimeout(() => this.dismiss(id), DURATION_MS[type]);
    return id;
  }
}
