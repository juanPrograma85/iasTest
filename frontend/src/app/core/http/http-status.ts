const STATUS_INFO: Record<number, { name: string; title: string }> = {
  0: { name: 'Network Error', title: 'Sin conexión' },
  200: { name: 'OK', title: 'Consulta exitosa' },
  201: { name: 'Created', title: 'Solicitud procesada' },
  400: { name: 'Bad Request', title: 'Solicitud con formato inválido' },
  404: { name: 'Not Found', title: 'No encontrada' },
  409: { name: 'Conflict', title: 'Referencia ya utilizada con otros datos' },
  422: { name: 'Unprocessable Entity', title: 'La solicitud no cumple las reglas de negocio' },
  500: { name: 'Internal Server Error', title: 'Error del servidor' },
};

export function httpStatusTitle(status: number): string {
  return STATUS_INFO[status]?.title ?? (status >= 500 ? 'Error del servidor' : 'Respuesta inesperada');
}

export function httpStatusLabel(status: number): string {
  if (status === 0) return 'Sin conexión';
  return `${status} ${STATUS_INFO[status]?.name ?? ''}`.trim();
}
