// Configuración de entorno para producción.
// El cliente HTTP apunta a la API REST versionada bajo `/api/v1` (Req 12.4),
// servida por el mismo origen a través del Proxy Inverso (IIS).
export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
};
