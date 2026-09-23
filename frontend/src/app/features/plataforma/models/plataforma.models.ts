// =============================================================================
// Modelos del ambito PLATAFORMA (super_admin) (Req 24, 25, 69)
// -----------------------------------------------------------------------------
// Interfaces TypeScript que reflejan los DTOs del backend (platform.empresas):
//   - EmpresaDto, EmpresaCreadaDto, PlanDto, SuscripcionDto, BrandingDto
//   - ExportacionTenantDto, ResultadoEliminacionTenantDto (offboarding)
// =============================================================================

/** Estado del ciclo de vida de una Empresa (Req 24). */
export type EstadoEmpresa = 'activa' | 'suspendida' | 'cancelada';

/**
 * Entrada del catalogo de modulos de la plataforma (GET /plataforma/modulos).
 * Refleja el {@code ModuloCatalogoDto} del backend, enriquecido con el precio
 * del bloque en la moneda principal de la plataforma.
 */
export interface ModuloCatalogo {
  /** Clave canonica del modulo (minusculas), la que se persiste en el Plan. */
  clave: string;
  /** Etiqueta humana en espanol del modulo. */
  nombreVisible: string;
  /** Clave del Giro que aporta el modulo, o `null` si es de Nucleo Comun. */
  giro: string | null;
  /**
   * Id de la fila del catalogo facturable del modulo, o `null` cuando la clave
   * es solo de vertical y todavia NO existe una fila facturable (no se puede
   * ponerle precio aun).
   */
  catalogoModuloId: string | null;
  /** Precio del bloque en la moneda principal, o `null` si aun no tiene precio. */
  precio: number | null;
  /** Codigo ISO de la moneda principal; el mismo para todas las entradas. */
  monedaCodigo: string;
}

/**
 * Mapa de dependencias entre modulos (GET /plataforma/dependencias-modulos).
 * Cada clave es un modulo DEPENDIENTE y su valor es la lista de modulos
 * REQUERIDOS por el (p. ej. `{ "inventario-avanzado": ["operacion"] }`). El
 * frontend del super_admin lo consume para derivar avisos y el bloqueo de
 * deseleccion SIN listas hardcodeadas en la vista.
 */
export type DependenciasModulos = Record<string, string[]>;

/** Moneda del catalogo de la plataforma (GET /monedas). */
export interface Moneda {
  codigo: string;
  nombre: string;
  activo: boolean;
}

/**
 * Estado de una Suscripcion (Req 25.2). Ampliado para reflejar el ciclo de vida
 * completo del contrato en el backend: ademas de los estados originales, incluye
 * `'en_prueba'` (suscripcion dentro de su periodo de prueba) y `'vencida'`
 * (contrato caducado tras su `vigenciaFin`).
 */
export type EstadoSuscripcion =
  | 'activa'
  | 'en_prueba'
  | 'suspendida'
  | 'cancelada'
  | 'vencida';

/**
 * Tipo de instrumento de contratacion que respalda el Contrato (Suscripcion).
 * Espejo del `TipoInstrumento` del backend, serializado en minusculas (`@JsonValue`):
 *   - `'plan'`: contrato de mas de 1 año (> 365 dias) que NO admite periodo de prueba.
 *   - `'suscripcion'`: contrato de 1 año o menos (<= 365 dias) que SI admite prueba.
 */
export type TipoInstrumento = 'plan' | 'suscripcion';

/**
 * Plan/suscripcion vigente de una Empresa (dato enriquecido del `EmpresaDto`)
 * (Req 1.3, 4.1, 4.2). El backend lo resuelve como fuente de verdad (regla de
 * "suscripcion vigente") y expone el NOMBRE del plan ya resuelto (patron
 * NO-UUID): `planId`, `suscripcionId` y `paqueteSuscripcionId` son de uso
 * INTERNO del frontend (preseleccion en selectores e invocacion de acciones) y
 * NUNCA se muestran al usuario.
 */
export interface PlanVigente {
  /**
   * Nombre legible del plan vigente resuelto por el backend. Se conserva por
   * compatibilidad; en adelante {@link PlanVigente.nombreInstrumento} es el
   * campo AUTORITATIVO para la etiqueta humana (Plan o Suscripcion).
   */
  nombrePlan: string;
  /** Estado de la suscripcion vigente ('activa' | 'en_prueba' | 'suspendida' | 'cancelada' | 'vencida'). */
  estado: EstadoSuscripcion;
  /** Id del plan vigente; uso INTERNO (preseleccion en selectores). NUNCA se muestra. */
  planId: string;
  /** Id de la suscripcion vigente; uso INTERNO (acciones). NUNCA se muestra. */
  suscripcionId: string;
  /** Inicio de vigencia de la suscripcion (ISO `YYYY-MM-DD`). */
  vigenciaInicio: string;
  /** Fin de vigencia (ISO `YYYY-MM-DD`), o `null` cuando no tiene fecha de fin. */
  vigenciaFin: string | null;
  /** Tipo del instrumento vigente ('plan' o 'suscripcion'). */
  tipoInstrumento: TipoInstrumento;
  /**
   * Nombre legible del Plan O del Paquete vigente resuelto por el backend
   * (patron NO-UUID); es el label humano PREFERIDO/autoritativo en adelante y
   * reemplaza semanticamente a {@link PlanVigente.nombrePlan}.
   */
  nombreInstrumento: string;
  /**
   * Id del Paquete de suscripcion vigente cuando el instrumento es una
   * suscripcion, o `null` si es un Plan; uso INTERNO (como `planId`). NUNCA se
   * muestra.
   */
  paqueteSuscripcionId: string | null;
  /** Dias que faltan hasta `vigenciaFin`, o `null` cuando `vigenciaFin` es `null`. */
  diasRestantes: number | null;
  /** `true` cuando el contrato vigente esta dentro de su periodo de prueba (estado EN_PRUEBA). */
  enPrueba: boolean;
  /** `true` cuando el contrato vigente esta vencido (dato derivado por el backend). */
  vencida: boolean;
  /** `true` cuando el contrato vigente esta por vencer (dentro del umbral de aviso). */
  porVencer: boolean;
}

/**
 * Giro (vertical de negocio) del catalogo de la plataforma (Req 9).
 * Refleja el {@code GiroDto} del backend (GET /plataforma/giros).
 */
export interface Giro {
  id: string;
  /** Clave canonica en kebab-case (minusculas y guiones), unica. */
  clave: string;
  /** Etiqueta humana en espanol del giro. */
  nombreVisible: string;
  /** Descripcion opcional del giro. */
  descripcion: string | null;
  /** Indica si el giro esta activo y puede asignarse a nuevas Empresas. */
  activo: boolean;
  version: number;
  /**
   * Completitud del giro. `true` si existe un vertical programado en el backend
   * (giro "Completo": aporta modulos base + reglas de negocio propias); `false`
   * si es un giro "Base" (solo hereda los modulos base compartidos, sin reglas
   * de negocio especificas todavia).
   */
  tieneReglasNegocio: boolean;
  /**
   * Numero de modulos especificos que aporta el vertical de este giro
   * (0 cuando el giro es "Base" y no tiene vertical programado).
   */
  modulosEspecificos: number;
}

/** Direccion postal de una Empresa; todos sus campos son opcionales (Req 24). */
export interface DireccionEmpresa {
  calle: string | null;
  ciudad: string | null;
  estado: string | null;
  cp: string | null;
  pais: string | null;
}

/** Empresa (tenant) — datos de plataforma (Req 24). */
export interface Empresa {
  id: string;
  nombre: string;
  rfc: string;
  /** Giro (vertical de negocio) al que pertenece la Empresa (Req 9, 24). */
  giroId: string;
  estado: EstadoEmpresa;
  /** Nombre visible del branding del tenant, o `null` si no se personalizo. */
  brandingNombreVisible: string | null;
  /** Logo del branding como data-URI, o `null` si no se cargo. */
  brandingLogo: string | null;
  /** Nombre comercial (marca), independiente de la razon social. */
  nombreComercial: string | null;
  /** Correo de contacto principal de la Empresa. */
  emailContacto: string | null;
  /** Telefono de contacto principal. */
  telefono: string | null;
  /** Sitio web corporativo. */
  sitioWeb: string | null;
  /** Direccion postal; `null` cuando no se capturo ningun dato. */
  direccion: DireccionEmpresa | null;
  /** Notas internas sobre la Empresa. */
  notas: string | null;
  /**
   * Instante ISO-8601 en que la Empresa se cancelo, o `null` mientras no este
   * en estado `cancelada` (Req 69.2). Marca el inicio del periodo de gracia.
   */
  fechaCancelacion: string | null;
  /**
   * Instante ISO-8601 en que vence el periodo de gracia, o `null` mientras la
   * Empresa no este `cancelada` (Req 69.2, 69.4). El backend define su duracion
   * (por defecto 30 dias, configurable); el frontend NUNCA la fija: deriva los
   * dias restantes comparando este instante contra el momento actual.
   */
  finPeriodoGracia: string | null;
  /** Plan vigente resuelto por el backend; null cuando la Empresa no tiene suscripcion. */
  planVigente: PlanVigente | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Resultado de restablecer la contrasena del administrador de una Empresa
 * (POST /empresas/{id}/admin/reset-password). `passwordTemporal` es NO nula
 * SOLO cuando el servidor la genero (cuerpo vacio); nula cuando el super_admin
 * envio una contrasena explicita.
 */
export interface ResetPasswordAdmin {
  usuarioId: string;
  identificador: string;
  passwordTemporal: string | null;
}

/** Resultado del alta de una Empresa; expone la contrasena temporal una unica vez (Req 24.2, 11.3). */
export interface EmpresaCreada {
  empresa: Empresa;
  adminUsuarioId: string;
  adminIdentificador: string;
  adminPasswordTemporal: string | null;
}

/**
 * Plan de suscripcion con sus limites y precios por modulo (Req 25.1).
 * El Plan ahora pertenece a un Giro y fija un precio explicito por cada modulo
 * habilitado en una moneda concreta; el {@link Plan.total} es la suma que
 * calcula el backend.
 */
export interface Plan {
  id: string;
  nombre: string;
  maxUsuarios: number;
  /** Duracion del contrato en dias; siempre `> 365` para un Plan (un año o menos es una Suscripcion). */
  duracionDias: number;
  /** Giro (vertical) del Plan, o `null` si aun no se asigno. */
  giroId: string | null;
  /** Codigo ISO 4217 de la moneda del Plan, o `null` si aun no se asigno. */
  monedaCodigo: string | null;
  /** Precio por modulo: `{ claveModulo: precio }`. Puede venir vacio (`{}`). */
  preciosModulos: Record<string, number>;
  /** Suma de los precios de los modulos habilitados (la calcula el backend). */
  total: number;
  /** Claves de los modulos habilitados (= llaves de {@link Plan.preciosModulos}). */
  modulosHabilitados: string[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Suscripcion Empresa-Plan (Req 25.2, 25.4). */
export interface Suscripcion {
  id: string;
  tenantId: string;
  planId: string;
  /** Tipo del instrumento contratado ('plan' o 'suscripcion'); refleja `SuscripcionDto`. */
  tipoInstrumento: TipoInstrumento;
  /**
   * Id del Paquete de suscripcion contratado cuando el instrumento es una
   * suscripcion, o `null` cuando es un Plan. Uso INTERNO, no se muestra como UUID.
   */
  paqueteSuscripcionId: string | null;
  estado: EstadoSuscripcion;
  vigenciaInicio: string;
  vigenciaFin: string | null;
  modulosHabilitados: string[] | null;
  monedaFacturacion: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Paquete de suscripcion con sus limites, precios por modulo y atributos de
 * contratacion (Req 3). Espejo del `PaqueteSuscripcionDto` del backend, paralelo
 * a {@link Plan}: pertenece a un Giro, fija un precio explicito por cada modulo
 * habilitado en una moneda concreta y su {@link PaqueteSuscripcion.total} es la
 * suma que calcula el backend. Anade los atributos propios del Paquete: la
 * duracion del contrato y el periodo de prueba.
 */
export interface PaqueteSuscripcion {
  id: string;
  /** Nombre del Paquete (unico). */
  nombre: string;
  maxUsuarios: number;
  /** Giro (vertical) del Paquete, o `null` si aun no se asigno. */
  giroId: string | null;
  /** Codigo ISO 4217 de la moneda de cotizacion, o `null` si aun no se asigno. */
  monedaCodigo: string | null;
  /** Precio por modulo: `{ claveModulo: precio }`. Puede venir vacio (`{}`). */
  preciosModulos: Record<string, number>;
  /** Suma de los precios de los modulos habilitados (la calcula el backend). */
  total: number;
  /** Claves de los modulos habilitados (= llaves de {@link PaqueteSuscripcion.preciosModulos}). */
  modulosHabilitados: string[];
  /** Duracion del contrato en dias; `<= 365` para un Paquete de suscripcion. */
  duracionDias: number;
  /** Indica si el Paquete admite periodo de prueba (Req 3.2). */
  admitePrueba: boolean;
  /** Duracion del periodo de prueba en meses, o `null` cuando no admite prueba. */
  duracionPruebaMeses: number | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Exportacion de datos de negocio del tenant (Req 69.1). Estructura abierta. */
export interface ExportacionTenant {
  [clave: string]: unknown;
}

/** Resultado de la eliminacion definitiva del tenant (Req 69.3). */
export interface ResultadoEliminacionTenant {
  [clave: string]: unknown;
}

// -----------------------------------------------------------------------------
// Facturacion de renta de modulos (super_admin) (plataforma-multigiro)
// -----------------------------------------------------------------------------
// Comprobante INTERNO de renta de modulos que la plataforma emite a cada Empresa
// (tenant) por periodo mensual. NO es un CFDI con validez fiscal (asi lo advierte
// el propio PDF). Los importes los calcula el backend; el frontend solo formatea.

/** Linea de una factura de renta: un modulo facturado con su precio aplicado. */
export interface FacturaRentaLinea {
  id: string;
  /** Clave canonica del modulo facturado. */
  moduloClave: string;
  /** Etiqueta humana del modulo. */
  moduloNombre: string;
  /** Precio aplicado a este modulo en el periodo (lo calcula el backend). */
  precioAplicado: number;
}

/**
 * Factura de renta de modulos de una Empresa para un periodo mensual. El
 * `periodo` viene normalizado al primer dia del mes (`YYYY-MM-01`). `emitidaEn`
 * es `null` en las previsualizaciones (aun no persistidas).
 */
export interface FacturaRenta {
  id: string;
  /** Empresa (tenant) a la que pertenece la factura. */
  tenantId: string;
  /** Periodo facturado, normalizado a primer dia de mes (`YYYY-MM-01`). */
  periodo: string;
  /** Codigo ISO 4217 de la moneda de facturacion. */
  monedaCodigo: string;
  /** Total del periodo (suma de las lineas); lo calcula el backend. */
  total: number;
  /** Estado del comprobante segun el backend (texto libre). */
  estado: string;
  /** Instante ISO-8601 de emision, o `null` si es una previsualizacion. */
  emitidaEn: string | null;
  /** Detalle por modulo facturado. */
  lineas: FacturaRentaLinea[];
}
