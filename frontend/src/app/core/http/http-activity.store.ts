import { Injectable, signal } from '@angular/core';

export interface HttpActivityEntry {
  id: number;
  timestamp: Date;
  method: string;
  url: string;
  status: number;
  ok: boolean;
  code?: string;
  message: string;
  durationMs: number;
}

const MAX_ENTRIES = 50;

@Injectable({ providedIn: 'root' })
export class HttpActivityStore {
  private nextId = 0;
  private readonly _entries = signal<HttpActivityEntry[]>([]);

  /** Más recientes primero. */
  readonly entries = this._entries.asReadonly();

  record(entry: Omit<HttpActivityEntry, 'id' | 'timestamp'>): void {
    const item: HttpActivityEntry = { ...entry, id: ++this.nextId, timestamp: new Date() };
    this._entries.update((entries) => [item, ...entries].slice(0, MAX_ENTRIES));
  }

  clear(): void {
    this._entries.set([]);
  }
}
