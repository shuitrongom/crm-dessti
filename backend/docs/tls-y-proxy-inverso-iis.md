# Terminación TLS y proxy inverso en IIS (Req 9)

Este documento describe cómo se protege la comunicación del CRM de Anuncios
Luminosos en el despliegue on-premise sobre Windows, donde **IIS actúa como
proxy inverso** delante del backend Spring Boot y del frontend Angular.

Complementa la configuración de cabeceras de seguridad y CSRF que hace la
aplicación en `com.empresa.crm.platform.security.SecurityConfig` (tarea 12).

## Arquitectura de red

```
Cliente (navegador)
    │  HTTPS (TLS)
    ▼
┌─────────────────────────────────────────────┐
│ IIS (Windows) — Proxy inverso                │
│  • Termina TLS (certificado del sitio)       │
│  • Redirige HTTP → HTTPS                      │
│  • Sirve la SPA de Angular en  /             │
│  • Enruta  /api  → backend (ARR/URL Rewrite) │
└─────────────────────────────────────────────┘
    │  HTTP interno (localhost) + cabeceras X-Forwarded-*
    ▼
Backend Spring Boot (Tomcat embebido, context-path /api/v1)
```

- El backend **no** expone TLS directamente: escucha en HTTP en la interfaz
  local (por ejemplo `http://127.0.0.1:8080`) y solo es alcanzable a través de
  IIS.
- El frontend (Angular) se sirve como **archivos estáticos** desde IIS y
  gestiona su propia `Content-Security-Policy` a nivel de su sitio.

## 1. Terminación TLS en IIS (Req 9.1)

- Instalar el certificado del servidor (idealmente de una CA reconocida; en
  entornos internos, una CA corporativa) en el almacén de certificados de
  Windows y enlazarlo (`binding`) al sitio en el puerto **443**.
- Configurar el binding HTTPS del sitio con el certificado y, si aplica,
  requerir **TLS 1.2+** deshabilitando protocolos y cifradores obsoletos
  (SSL 3.0, TLS 1.0/1.1) mediante la configuración de Schannel de Windows.
- El sitio debe **rechazar conexiones no cifradas** al recurso protegido: solo
  el binding HTTP mínimo necesario para la redirección permanece disponible
  (ver sección 2).

## 2. Redirección HTTP → HTTPS en IIS (Req 9.2)

- Habilitar un binding HTTP en el puerto **80** cuya única función sea redirigir.
- Con el módulo **URL Rewrite** de IIS, aplicar una regla que reescriba toda
  petición entrante por HTTP hacia su equivalente HTTPS con un redirect
  permanente (301) o temporal (302). Ejemplo conceptual de regla en
  `web.config` del sitio:

  ```xml
  <rewrite>
    <rules>
      <rule name="Redirigir a HTTPS" stopProcessing="true">
        <match url="(.*)" />
        <conditions>
          <add input="{HTTPS}" pattern="off" ignoreCase="true" />
        </conditions>
        <action type="Redirect" url="https://{HTTP_HOST}/{R:1}"
                redirectType="Permanent" />
      </rule>
    </rules>
  </rewrite>
  ```

- Así, cualquier acceso por `http://` termina en `https://` antes de tocar la
  aplicación.

## 3. Cabeceras de reenvío (X-Forwarded-*)

Como IIS termina TLS y habla HTTP con el backend, debe **propagar el contexto
original** mediante cabeceras estándar para que la aplicación sepa que el
cliente llegó por HTTPS:

- `X-Forwarded-Proto: https` — esquema original (permite a Spring reconstruir
  URLs `https://` para redirecciones y enlaces).
- `X-Forwarded-For: <ip-cliente>` — dirección de origen real del cliente (útil
  para el rate limiting y la auditoría por IP, Req 2).
- `X-Forwarded-Host` / `X-Forwarded-Port` — host y puerto originales.

Con **Application Request Routing (ARR)** de IIS, habilitar el reenvío del
encabezado del cliente (por ejemplo, `X-Forwarded-For`) y añadir mediante URL
Rewrite (server variables) los encabezados `X-Forwarded-Proto` y
`X-Forwarded-Host` en la regla de enrutamiento hacia el backend.

> Nota de confianza: estas cabeceras solo deben ser establecidas por el proxy.
> El backend únicamente es accesible a través de IIS, por lo que se confía en
> ellas. No expongas el puerto HTTP del backend a la red.

## 4. Confianza del proxy en Spring (application.yml)

Para que Spring Boot **confíe en las cabeceras del proxy** y reconstruya el
esquema/host originales, se configura en `application.yml`:

```yaml
server:
  forward-headers-strategy: framework
```

Con `framework`, Spring registra un `ForwardedHeaderFilter` que interpreta
`X-Forwarded-*`. Esto asegura que:

- Las redirecciones y los enlaces absolutos usen `https://`.
- La cabecera `Strict-Transport-Security` (HSTS) emitida por la aplicación sea
  coherente con el origen seguro.
- La IP registrada para rate limiting/auditoría sea la del cliente real.

## 5. Cabeceras de seguridad emitidas por la aplicación (Req 9.3)

Además de lo que IIS pueda añadir, el backend emite en **cada respuesta** (ver
`SecurityConfig`):

| Cabecera | Valor | Propósito |
| --- | --- | --- |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'` | API solo-JSON; sin recursos ni enmarcado. La SPA define su propia CSP. |
| `Strict-Transport-Security` | `max-age=31536000 ; includeSubDomains` | Fuerza HTTPS en visitas posteriores. |
| `X-Frame-Options` | `DENY` | Anti-clickjacking (reforzado por `frame-ancestors`). |
| `Referrer-Policy` | `no-referrer` | No filtra la URL de origen a terceros. |
| `X-Content-Type-Options` | `nosniff` | Evita el sniffing de tipo MIME (por defecto en Spring Security). |

## 6. CSRF (Req 9.4)

La API es **stateless** y se autentica con `Authorization: Bearer <token>`. Al
no usar cookies de sesión, no hay credenciales ambientales y por tanto **no hay
superficie de ataque CSRF clásico**; CSRF se mantiene deshabilitado en la API.

Si en el futuro se introduce cualquier **sesión basada en cookie** para el
navegador, debe habilitarse la protección CSRF con `CookieCsrfTokenRepository`
(patrón de doble envío de token) para las operaciones que cambian estado.
