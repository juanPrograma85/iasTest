export type NotificationType = 'success' | 'error' | 'warning' | 'info';

export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  /** Ej.: "201 Created", "409 Conflict". */
  httpStatus?: string;
}
