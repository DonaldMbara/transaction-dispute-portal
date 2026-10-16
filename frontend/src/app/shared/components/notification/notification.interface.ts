export type NotificationTone = 'success' | 'info' | 'error';

export interface Notification {
  message: string;
  tone: NotificationTone;
}