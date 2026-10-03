export interface ApiError {
  /** 0 = sin conexión con el servidor. */
  status: number;
  code: string;
  message: string;
  timestamp?: string;
}
