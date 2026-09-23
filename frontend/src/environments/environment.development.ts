// Configuración de entorno para desarrollo.
// Por defecto apunta a `/api/v1` en el mismo origen; el proxy de desarrollo de
// Angular puede redirigir `/api` al backend de Spring Boot cuando se requiera.
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
};
