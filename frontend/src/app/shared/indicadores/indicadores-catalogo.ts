// =============================================================================
// Catalogo central de indicadores del Tablero / Scorecard (Req 22, 48, 57)
// -----------------------------------------------------------------------------
// Fuente unica de verdad para la CAPA DE PRESENTACION de cada indicador de
// negocio: su explicacion en lenguaje llano (que es / como se calcula / por que
// importa), una descripcion corta para la tarjeta, un icono y un TONO de color
// semantico. Las claves coinciden con las que emite el backend (adaptadores
// Indicador*Adapter). Al vivir en un solo lugar, el modal explicativo y el color
// de las tarjetas quedan consistentes en TODO el tablero y en el scorecard.
//
// Buenas practicas aplicadas (dashboards ejecutivos): paleta acotada con
// significado, color de alerta reservado para excepciones, y jerarquia de la
// informacion (valor > etiqueta > contexto). Contenido redactado para el cliente.
// =============================================================================

/** Tono semantico de color de una tarjeta (mapea a los acentos del sistema de diseno). */
export type TonoIndicador =
  | 'primario'
  | 'exito'
  | 'advertencia'
  | 'error'
  | 'info'
  | 'neutro';

/** Ficha de presentacion de un indicador. */
export interface FichaIndicador {
  /** Icono de Material Symbols. */
  icono: string;
  /** Tono de color semantico de la tarjeta. */
  tono: TonoIndicador;
  /** Subtitulo breve (una linea) que se muestra en la tarjeta. */
  corta: string;
  /** Que es el indicador (definicion en lenguaje de negocio). */
  que: string;
  /** Como se calcula. */
  como: string;
  /** Por que importa / que decision habilita. */
  porque: string;
}

/**
 * Catalogo por clave estable. Si una clave no esta aqui, se resuelve una ficha
 * generica por heuristica (ver {@link fichaIndicador}), de modo que el sistema
 * nunca queda sin explicacion ni color.
 */
const CATALOGO: Record<string, FichaIndicador> = {
  // ---- Comercial (CRM) ----
  valor_pipeline_abierto: {
    icono: 'trending_up', tono: 'primario', corta: 'Ventas potenciales en curso',
    que: 'Suma del valor estimado de las oportunidades comerciales que siguen abiertas (no ganadas ni perdidas).',
    como: 'Se suman los montos estimados de cada oportunidad en etapas activas del pipeline.',
    porque: 'Estima cuánto podrías cerrar si concretas las ventas en curso: tu potencial de ingresos a corto plazo.',
  },
  oportunidades_pipeline_abierto: {
    icono: 'filter_alt', tono: 'info', corta: 'Oportunidades activas',
    que: 'Número de oportunidades que aún están en el embudo de ventas.',
    como: 'Se cuentan las oportunidades en etapas de prospección, propuesta y negociación.',
    porque: 'Mide el volumen de negocios que tu equipo está trabajando ahora mismo.',
  },
  forecast_ponderado_pipeline: {
    icono: 'online_prediction', tono: 'info', corta: 'Pronóstico realista de cierre',
    que: 'Pronóstico de ventas que ajusta el valor de cada oportunidad por su probabilidad de cierre.',
    como: 'Por cada oportunidad abierta se multiplica su valor estimado por su probabilidad (%) y se suman los resultados.',
    porque: 'Es una estimación más realista que el valor total del pipeline: cuánto esperas cerrar de verdad.',
  },
  ticket_promedio_pipeline: {
    icono: 'sell', tono: 'info', corta: 'Valor medio por oportunidad',
    que: 'Valor promedio de cada oportunidad abierta en tu pipeline.',
    como: 'Se divide el valor total del pipeline abierto entre el número de oportunidades abiertas.',
    porque: 'Ayuda a dimensionar tus negocios típicos y a fijar metas por vendedor.',
  },
  tasa_ganados_pipeline: {
    icono: 'emoji_events', tono: 'exito', corta: '% de oportunidades ganadas',
    que: 'Porcentaje de oportunidades que terminaron en venta ganada.',
    como: 'Oportunidades en etapa "ganado" divididas entre el total de oportunidades, en porcentaje.',
    porque: 'Mide la efectividad de tu proceso comercial: qué tan seguido conviertes en venta.',
  },
  // ---- Productos (catálogo) ----
  productos_total: {
    icono: 'inventory_2', tono: 'primario', corta: 'Total en el catálogo',
    que: 'Número total de productos y servicios registrados en tu catálogo, activos e inactivos.',
    como: 'Se suman los productos activos y los dados de baja del tenant.',
    porque: 'Refleja la amplitud de lo que has registrado para cotizar y vender.',
  },
  productos_activos: {
    icono: 'check_circle', tono: 'exito', corta: 'Disponibles para vender',
    que: 'Productos activos, disponibles para cotizar y vender.',
    como: 'Se cuentan todos los productos del tenant marcados como activos.',
    porque: 'Son los productos con los que puedes trabajar hoy; los inactivos quedan archivados.',
  },
  productos_inactivos: {
    icono: 'inventory', tono: 'neutro', corta: 'Dados de baja',
    que: 'Productos dados de baja (inactivos), que ya no se ofrecen para cotizar.',
    como: 'Se cuentan todos los productos del tenant marcados como inactivos.',
    porque: 'Conservan su historial y puedes reactivarlos cuando vuelvas a ofrecerlos.',
  },

  // ---- Canales de venta ----
  canales_venta_total: {
    icono: 'hub', tono: 'primario', corta: 'Total en el catálogo',
    que: 'Número de canales de venta definidos (las vías por las que vende tu empresa).',
    como: 'Se cuentan todos los canales de venta activos del tenant.',
    porque: 'Clasificar oportunidades y cotizaciones por canal te permite medir de dónde vienen tus ventas.',
  },
  canales_venta_activos: {
    icono: 'check_circle', tono: 'exito', corta: 'En uso',
    que: 'Canales de venta activos, disponibles para clasificar oportunidades y cotizaciones.',
    como: 'Se cuentan los canales de venta marcados como activos en la página cargada.',
    porque: 'Son las vías comerciales con las que puedes trabajar hoy.',
  },

  // ---- Listas de precios ----
  listas_precios_total: {
    icono: 'sell', tono: 'primario', corta: 'Total en el catálogo',
    que: 'Número de listas de precios definidas (por vigencia, prioridad y segmento).',
    como: 'Se cuentan todas las listas de precios activas del tenant.',
    porque: 'Cada lista fija el precio de tus productos según el segmento y la vigencia.',
  },
  listas_precios_vigentes: {
    icono: 'event_available', tono: 'exito', corta: 'Aplican hoy',
    que: 'Listas de precios cuya ventana de vigencia incluye la fecha de hoy.',
    como: 'Se cuentan las listas activas cuya vigencia (inicio–fin) abarca la fecha actual.',
    porque: 'Son las listas desde las que se sugiere el precio al cotizar en este momento.',
  },

  // ---- Clientes (cartera) ----
  clientes_registrados: {
    icono: 'groups', tono: 'primario', corta: 'Total en la cartera',
    que: 'Número total de clientes registrados en tu cartera comercial.',
    como: 'Se cuentan todos los clientes dados de alta en el sistema.',
    porque: 'Refleja el tamaño de tu base de clientes: la base de tu negocio.',
  },
  clientes_activos: {
    icono: 'verified_user', tono: 'exito', corta: 'Clientes vigentes',
    que: 'Clientes que están activos (no dados de baja).',
    como: 'Se cuentan los clientes marcados como activos.',
    porque: 'Son tus clientes con los que puedes operar y facturar hoy.',
  },
  clientes_personas_morales: {
    icono: 'domain', tono: 'info', corta: 'Empresas',
    que: 'Clientes que son personas morales (empresas u organizaciones).',
    como: 'Se cuentan los clientes cuyo tipo de persona es "moral".',
    porque: 'Ayuda a entender la composición de tu cartera (empresas vs personas).',
  },
  clientes_personas_fisicas: {
    icono: 'person', tono: 'neutro', corta: 'Personas físicas',
    que: 'Clientes que son personas físicas (individuos).',
    como: 'Se cuentan los clientes cuyo tipo de persona es "física".',
    porque: 'Complementa el panorama de tu cartera junto con las personas morales.',
  },
  oportunidades_ganadas: {
    icono: 'emoji_events', tono: 'exito', corta: 'Negocios cerrados con éxito',
    que: 'Oportunidades comerciales que cerraste ganando la venta.',
    como: 'Se cuentan las oportunidades que llegaron a la etapa "ganado".',
    porque: 'Mide tu efectividad de ventas: cuántos negocios lograste concretar.',
  },
  oportunidades_perdidas: {
    icono: 'heart_broken', tono: 'error', corta: 'Negocios no concretados',
    que: 'Oportunidades que se cerraron sin lograr la venta.',
    como: 'Se cuentan las oportunidades que terminaron en la etapa "perdido".',
    porque: 'Revisarlas ayuda a entender por qué se pierden ventas y a mejorar el proceso.',
  },
  cotizaciones_borrador: {
    icono: 'edit_note', tono: 'advertencia', corta: 'Cotizaciones sin enviar',
    que: 'Cotizaciones que aún están en borrador, sin enviarse al cliente.',
    como: 'Se cuentan las cotizaciones en estado "borrador".',
    porque: 'Muchos borradores acumulados pueden indicar seguimiento pendiente.',
  },
  cotizaciones_enviadas: {
    icono: 'send', tono: 'info', corta: 'Cotizaciones enviadas al cliente',
    que: 'Cotizaciones que ya se enviaron y esperan respuesta del cliente.',
    como: 'Se cuentan las cotizaciones en estado "enviada".',
    porque: 'Representan ventas potenciales a la espera de aprobación.',
  },
  cotizaciones_aprobadas: {
    icono: 'verified', tono: 'exito', corta: 'Cotizaciones ya aprobadas',
    que: 'Cotizaciones que el cliente aprobó.',
    como: 'Se cuentan las cotizaciones en estado "aprobada".',
    porque: 'Anticipan ventas por facturar: trabajo aprobado que se convertirá en ingreso.',
  },
  cotizaciones_rechazadas: {
    icono: 'block', tono: 'error', corta: 'Cotizaciones rechazadas',
    que: 'Cotizaciones que el cliente rechazó.',
    como: 'Se cuentan las cotizaciones en estado "rechazada".',
    porque: 'Analizarlas ayuda a ajustar precios y propuestas.',
  },
  valor_cotizado: {
    icono: 'payments', tono: 'primario', corta: 'Monto cotizado a clientes',
    que: 'Suma del total de las cotizaciones mostradas en la página actual.',
    como: 'Se suman los totales (con IVA, descuentos y retenciones ya aplicados) de las cotizaciones visibles.',
    porque: 'Estima el valor comercial que has puesto sobre la mesa: tu potencial de venta cotizado.',
  },
  // Clave usada por la tarjeta "Cotizaciones" del RESUMEN COMERCIAL del home
  // (empresa-home), que se calcula en el cliente y NO proviene del tablero del
  // backend. No la emite ningun adaptador; existe a proposito para el modal del home.
  cotizaciones_registradas: {
    icono: 'request_quote', tono: 'neutro', corta: 'Cotizaciones en el sistema',
    que: 'Total de cotizaciones registradas en el sistema, en cualquier estado.',
    como: 'Se cuentan todas las cotizaciones creadas (borrador, enviadas, aprobadas, rechazadas).',
    porque: 'Da una idea del volumen de actividad comercial de tu empresa.',
  },

  // ---- Actividades (agenda de seguimiento) ----
  actividades_pendientes: {
    icono: 'pending_actions', tono: 'advertencia', corta: 'Seguimientos por hacer',
    que: 'Actividades de seguimiento (llamadas, correos, reuniones, tareas) que siguen pendientes.',
    como: 'Se cuentan todas las actividades del tenant en estado "pendiente".',
    porque: 'Es tu carga de trabajo comercial por atender: no dejar cabos sueltos con los clientes.',
  },
  actividades_vencidas: {
    icono: 'warning', tono: 'error', corta: 'Requieren atención inmediata',
    que: 'Actividades pendientes cuya fecha de vencimiento ya pasó.',
    como: 'Se cuentan las actividades pendientes con vencimiento (o fecha programada) anterior a hoy.',
    porque: 'Son compromisos atrasados con clientes: atenderlas evita perder oportunidades.',
  },
  actividades_completadas: {
    icono: 'task_alt', tono: 'exito', corta: 'Seguimientos cerrados',
    que: 'Actividades de seguimiento que ya se completaron.',
    como: 'Se cuentan todas las actividades del tenant en estado "completada".',
    porque: 'Refleja el trabajo de seguimiento efectivamente realizado.',
  },
  actividades_canceladas: {
    icono: 'cancel', tono: 'neutro', corta: 'Seguimientos descartados',
    que: 'Actividades de seguimiento que se cancelaron sin llegar a completarse.',
    como: 'Se cuentan todas las actividades del tenant en estado "cancelada".',
    porque: 'Muchas cancelaciones pueden indicar seguimientos mal planificados o que dejaron de ser relevantes.',
  },

  // ---- Produccion ----
  ordenes_fabricacion_pendientes: {
    icono: 'schedule', tono: 'advertencia', corta: 'Por iniciar',
    que: 'Órdenes de fabricación registradas que aún no entran a producción.',
    como: 'Se cuentan las órdenes en estado "pendiente".',
    porque: 'Es tu cola de trabajo: lo que está por comenzar en el taller.',
  },
  ordenes_fabricacion_en_produccion: {
    icono: 'precision_manufacturing', tono: 'info', corta: 'En proceso ahora',
    que: 'Órdenes que se están fabricando en este momento.',
    como: 'Se cuentan las órdenes en estado "en producción".',
    porque: 'Refleja la carga de trabajo activa de tu planta.',
  },
  ordenes_fabricacion_terminadas: {
    icono: 'task_alt', tono: 'exito', corta: 'Fabricación completada',
    que: 'Órdenes de fabricación ya terminadas.',
    como: 'Se cuentan las órdenes en estado "terminada".',
    porque: 'Mide la productividad: cuánto se completó en el periodo.',
  },
  ordenes_fabricacion_canceladas: {
    icono: 'cancel', tono: 'error', corta: 'Fabricación cancelada',
    que: 'Órdenes de fabricación que se cancelaron.',
    como: 'Se cuentan las órdenes en estado "cancelada".',
    porque: 'Muchas cancelaciones pueden señalar problemas de planeación.',
  },

  // ---- Proyectos (multi-sitio) ----
  proyecto_sucursales_total: {
    icono: 'store', tono: 'primario', corta: 'Sitios del proyecto',
    que: 'Número total de sitios (sucursales/ubicaciones) que componen el proyecto.',
    como: 'Se cuentan todos los sitios registrados en el proyecto.',
    porque: 'Dimensiona el alcance del despliegue: cuántas ubicaciones hay que atender.',
  },
  proyecto_sucursales_pendientes: {
    icono: 'schedule', tono: 'advertencia', corta: 'Aún sin iniciar',
    que: 'Sitios del proyecto que todavía no comienzan trabajos (fase pendiente).',
    como: 'Se cuentan los sitios en fase "pendiente".',
    porque: 'Es el trabajo por arrancar: ayuda a planear el siguiente paso del despliegue.',
  },
  proyecto_sucursales_en_curso: {
    icono: 'engineering', tono: 'info', corta: 'En preparación o instalación',
    que: 'Sitios del proyecto con trabajos en marcha (preparación o instalación).',
    como: 'Se cuentan los sitios en fase "en preparación" o "en instalación".',
    porque: 'Refleja la carga de trabajo activa del proyecto en este momento.',
  },
  proyecto_sucursales_entregadas: {
    icono: 'check_circle', tono: 'exito', corta: 'Sitios entregados',
    que: 'Sitios del proyecto ya entregados y aceptados.',
    como: 'Se cuentan los sitios en fase "entregado".',
    porque: 'Mide el avance real del proyecto: cuánto se ha completado.',
  },

  // ---- Levantamientos de sitio (vertical anuncios) ----
  levantamientos_en_proceso: {
    icono: 'straighten', tono: 'advertencia', corta: 'Levantamientos abiertos',
    que: 'Levantamientos de sitio que siguen en proceso (sin completar).',
    como: 'Se cuentan los levantamientos en estado "en proceso".',
    porque: 'Es el trabajo de medición por cerrar antes de fabricar e instalar.',
  },
  levantamientos_completados: {
    icono: 'task_alt', tono: 'exito', corta: 'Levantamientos cerrados',
    que: 'Levantamientos de sitio ya completados.',
    como: 'Se cuentan los levantamientos en estado "completado".',
    porque: 'Miden el avance de la fase de medición del proyecto.',
  },

  // ---- Permisos de instalacion (vertical anuncios) ----
  permisos_solicitados: {
    icono: 'hourglass_top', tono: 'advertencia', corta: 'Pendientes de decisión',
    que: 'Permisos de instalación solicitados y aún sin aprobar o rechazar.',
    como: 'Se cuentan los permisos en estado "solicitado".',
    porque: 'Un permiso pendiente puede frenar la instalación en el sitio.',
  },
  permisos_aprobados: {
    icono: 'verified', tono: 'exito', corta: 'Listos para instalar',
    que: 'Permisos de instalación aprobados.',
    como: 'Se cuentan los permisos en estado "aprobado".',
    porque: 'Son los sitios habilitados legalmente para instalar.',
  },
  permisos_rechazados: {
    icono: 'block', tono: 'error', corta: 'Permisos negados',
    que: 'Permisos de instalación que fueron rechazados.',
    como: 'Se cuentan los permisos en estado "rechazado".',
    porque: 'Requieren atención: sin permiso no se puede instalar en ese sitio.',
  },

  // ---- OTIs / instalación (vertical anuncios) ----
  otis_programadas: {
    icono: 'event', tono: 'info', corta: 'Instalaciones agendadas',
    que: 'Órdenes de trabajo de instalación programadas y aún no iniciadas.',
    como: 'Se cuentan las OTIs en estado "programada".',
    porque: 'Es tu agenda de instalaciones por comenzar.',
  },
  otis_en_curso: {
    icono: 'engineering', tono: 'advertencia', corta: 'Instalando ahora',
    que: 'Órdenes de trabajo de instalación en curso.',
    como: 'Se cuentan las OTIs en estado "en curso".',
    porque: 'Refleja la carga de trabajo activa de las cuadrillas.',
  },
  otis_completadas: {
    icono: 'check_circle', tono: 'exito', corta: 'Instalaciones terminadas',
    que: 'Órdenes de trabajo de instalación completadas.',
    como: 'Se cuentan las OTIs en estado "completada".',
    porque: 'Mide el trabajo de instalación efectivamente realizado.',
  },

  // ---- Instalacion (vertical anuncios) ----
  instalaciones_completadas_en_fecha: {
    icono: 'event_available', tono: 'exito', corta: 'Entregadas a tiempo',
    que: 'Instalaciones completadas dentro de la fecha comprometida.',
    como: 'Se cuentan las instalaciones cerradas en o antes de su fecha programada.',
    porque: 'Mide el cumplimiento con el cliente: entregar a tiempo genera confianza.',
  },
  instalaciones_completadas_fuera_de_fecha: {
    icono: 'running_with_errors', tono: 'advertencia', corta: 'Entregadas con retraso',
    que: 'Instalaciones que se completaron, pero después de la fecha comprometida.',
    como: 'Se cuentan las instalaciones cerradas después de su fecha programada.',
    porque: 'Los retrasos afectan la satisfacción; conviene reducirlos.',
  },
  instalaciones_vencidas: {
    icono: 'warning', tono: 'error', corta: 'Vencidas sin completar',
    que: 'Instalaciones cuya fecha ya venció y siguen sin completarse.',
    como: 'Se cuentan las instalaciones con fecha pasada aún abiertas.',
    porque: 'Son incumplimientos activos: requieren atención inmediata.',
  },
  cumplimiento_fechas_instalacion: {
    icono: 'verified', tono: 'primario', corta: '% entregas a tiempo',
    que: 'Porcentaje de instalaciones entregadas dentro de la fecha programada.',
    como: 'Instalaciones a tiempo dividido entre el total de instalaciones completadas.',
    porque: 'Un indicador clave de calidad de servicio y puntualidad.',
  },

  // ---- Mantenimiento ----
  tickets_sla_resolucion_cumplido: {
    icono: 'check_circle', tono: 'exito', corta: 'SLA de resolución cumplido',
    que: 'Tickets de soporte resueltos dentro del tiempo comprometido (SLA).',
    como: 'Se cuentan los tickets cerrados antes de vencer su SLA de resolución.',
    porque: 'Mide qué tan bien cumples los acuerdos de servicio con tus clientes.',
  },
  tickets_sla_resolucion_incumplido: {
    icono: 'error', tono: 'error', corta: 'SLA de resolución incumplido',
    que: 'Tickets resueltos fuera del tiempo comprometido, o aún vencidos.',
    como: 'Se cuentan los tickets que superaron su SLA de resolución.',
    porque: 'Cada incumplimiento es un riesgo de insatisfacción del cliente.',
  },
  cumplimiento_sla_resolucion: {
    icono: 'speed', tono: 'primario', corta: '% SLA de resolución',
    que: 'Porcentaje de tickets resueltos a tiempo.',
    como: 'Tickets resueltos a tiempo entre el total de tickets con SLA de resolución.',
    porque: 'Resume tu desempeño de soporte en un solo número.',
  },
  tickets_sla_respuesta_cumplido: {
    icono: 'mark_chat_read', tono: 'exito', corta: 'SLA de respuesta cumplido',
    que: 'Tickets atendidos (primera respuesta) dentro del tiempo comprometido.',
    como: 'Se cuentan los tickets con primera respuesta antes de vencer su SLA.',
    porque: 'La rapidez de la primera respuesta marca la experiencia del cliente.',
  },
  tickets_sla_respuesta_incumplido: {
    icono: 'chat_error', tono: 'error', corta: 'SLA de respuesta incumplido',
    que: 'Tickets cuya primera respuesta llegó tarde o no ha llegado.',
    como: 'Se cuentan los tickets que superaron su SLA de respuesta.',
    porque: 'Responder tarde erosiona la confianza del cliente.',
  },
  cumplimiento_sla_respuesta: {
    icono: 'speed', tono: 'primario', corta: '% SLA de respuesta',
    que: 'Porcentaje de tickets con primera respuesta a tiempo.',
    como: 'Tickets respondidos a tiempo entre el total con SLA de respuesta.',
    porque: 'Resume la agilidad de tu equipo de soporte.',
  },

  // ---- Inventario base ----
  materiales_bajo_stock_minimo: {
    icono: 'production_quantity_limits', tono: 'advertencia', corta: 'Por reabastecer',
    que: 'Materiales cuyas existencias están por debajo del mínimo definido.',
    como: 'Se cuentan los materiales con existencias menores a su stock mínimo.',
    porque: 'Avisa qué comprar antes de quedarte sin material para producir.',
  },
  materiales_activos: {
    icono: 'inventory_2', tono: 'exito', corta: 'Disponibles para operar',
    que: 'Materiales activos registrados en tu inventario.',
    como: 'Se cuentan los materiales marcados como activos.',
    porque: 'Son los insumos con los que puedes producir y operar hoy.',
  },
  materiales_total: {
    icono: 'inventory_2', tono: 'primario', corta: 'Total en el catálogo',
    que: 'Número total de materiales registrados, activos e inactivos.',
    como: 'Se suman los materiales activos y los dados de baja del tenant.',
    porque: 'Refleja la amplitud de tu catálogo de insumos.',
  },
  materiales_inactivos: {
    icono: 'inventory', tono: 'neutro', corta: 'Dados de baja',
    que: 'Materiales dados de baja (inactivos), que ya no se usan para operar.',
    como: 'Se cuentan los materiales marcados como inactivos.',
    porque: 'Conservan su historial y puedes reactivarlos cuando vuelvas a usarlos.',
  },

  // ---- Inventario avanzado ----
  valuacion_inventario_total: {
    icono: 'warehouse', tono: 'primario', corta: 'Valor del inventario',
    que: 'Valor económico total de las existencias en tus almacenes.',
    como: 'Se valúan las existencias de cada material por su costo y se suman.',
    porque: 'Es capital inmovilizado en inventario: conviene mantenerlo equilibrado.',
  },
  almacenes_con_existencias: {
    icono: 'store', tono: 'info', corta: 'Almacenes con stock',
    que: 'Número de almacenes que tienen existencias registradas.',
    como: 'Se cuentan los almacenes con al menos un material en existencia.',
    porque: 'Refleja la distribución física de tu inventario.',
  },

  // ---- Compras ----
  proveedores_pagina: {
    icono: 'local_shipping', tono: 'primario', corta: 'Proveedores',
    que: 'Proveedores mostrados en la página actual del listado.',
    como: 'Se cuentan los proveedores traídos en la página cargada según el filtro.',
    porque: 'Da una referencia rápida del tamaño de tu catálogo de abastecimiento.',
  },
  proveedores_activos: {
    icono: 'check_circle', tono: 'exito', corta: 'Proveedores activos',
    que: 'Proveedores activos, disponibles para nuevas órdenes de compra.',
    como: 'Se cuentan los proveedores en estado activo dentro de la página cargada.',
    porque: 'Son los orígenes de abastecimiento con los que puedes operar hoy.',
  },
  proveedores_inactivos: {
    icono: 'block', tono: 'neutro', corta: 'Proveedores inactivos',
    que: 'Proveedores dados de baja lógica (conservan su histórico).',
    como: 'Se cuentan los proveedores en estado inactivo dentro de la página cargada.',
    porque: 'Puedes reactivarlos cuando vuelvas a comprarles; su RFC queda liberado mientras tanto.',
  },
  ordenes_compra_abiertas: {
    icono: 'shopping_cart', tono: 'info', corta: 'Compras en curso',
    que: 'Órdenes de compra emitidas y aún abiertas.',
    como: 'Se cuentan las órdenes de compra en estado "abierta".',
    porque: 'Es tu gasto comprometido pendiente de recibir.',
  },
  ordenes_compra_recibidas_parcial: {
    icono: 'inventory', tono: 'advertencia', corta: 'Recibidas parcialmente',
    que: 'Órdenes de compra recibidas solo en parte.',
    como: 'Se cuentan las órdenes con recepción parcial de material.',
    porque: 'Indican entregas incompletas de proveedores por dar seguimiento.',
  },
  ordenes_compra_recibidas_total: {
    icono: 'inventory_2', tono: 'exito', corta: 'Recibidas completas',
    que: 'Órdenes de compra recibidas en su totalidad.',
    como: 'Se cuentan las órdenes con recepción total del material.',
    porque: 'Compras completadas y listas para pagar o usar.',
  },
  ordenes_compra_cerradas: {
    icono: 'task_alt', tono: 'neutro', corta: 'Ciclo de compra cerrado',
    que: 'Órdenes de compra ya cerradas por completo.',
    como: 'Se cuentan las órdenes en estado "cerrada".',
    porque: 'Refleja el volumen de compras finalizadas en el periodo.',
  },
  ordenes_compra_canceladas: {
    icono: 'cancel', tono: 'error', corta: 'Compras canceladas',
    que: 'Órdenes de compra que se cancelaron.',
    como: 'Se cuentan las órdenes en estado "cancelada".',
    porque: 'Muchas cancelaciones pueden señalar problemas con proveedores.',
  },
  facturas_proveedor_discrepancia: {
    icono: 'rule', tono: 'advertencia', corta: 'Facturas con diferencia',
    que: 'Facturas de proveedor que no cuadran con la orden o recepción.',
    como: 'Se cuentan las facturas de proveedor marcadas con discrepancia.',
    porque: 'Requieren revisión antes de pagar para evitar pagos indebidos.',
  },

  // ---- Facturacion / Finanzas ----
  facturacion_periodo: {
    icono: 'receipt_long', tono: 'exito', corta: 'Ingresos timbrados',
    que: 'Total facturado y timbrado ante el SAT en el periodo.',
    como: 'Se suman los totales de los CFDI timbrados (excluye borradores y cancelados).',
    porque: 'Refleja tus ingresos fiscales reales: la base de tu contabilidad e impuestos.',
  },
  facturas_timbradas_periodo: {
    icono: 'description', tono: 'info', corta: 'CFDI emitidos',
    que: 'Número de facturas timbradas en el periodo.',
    como: 'Se cuentan los CFDI que fueron timbrados exitosamente.',
    porque: 'Da idea del volumen de operaciones facturadas.',
  },
  iva_trasladado_periodo: {
    icono: 'account_balance', tono: 'info', corta: 'IVA por enterar',
    que: 'IVA trasladado en las facturas del periodo.',
    como: 'Se suma el IVA de los CFDI timbrados.',
    porque: 'Es el impuesto que deberás enterar al SAT; conviene tenerlo previsto.',
  },
  cxc_vencidas_saldo: {
    icono: 'account_balance_wallet', tono: 'error', corta: 'Cobros vencidos',
    que: 'Dinero que te deben clientes cuyo plazo de pago ya venció.',
    como: 'Se suman los saldos de las cuentas por cobrar con vencimiento pasado.',
    porque: 'Entre más alto, más dinero tuyo atrapado en cobros atrasados. Conviene reducirlo.',
  },
  cxc_vencidas_conteo: {
    icono: 'event_busy', tono: 'advertencia', corta: 'Facturas por cobrar vencidas',
    que: 'Número de cuentas por cobrar que ya vencieron.',
    como: 'Se cuentan las cuentas por cobrar con fecha de pago pasada.',
    porque: 'Ayuda a priorizar la gestión de cobranza.',
  },

  // ---- Cuentas por pagar ----
  cxp_vencidas_saldo: {
    icono: 'payments', tono: 'error', corta: 'Pagos vencidos',
    que: 'Dinero que debes a proveedores cuyo plazo de pago ya venció.',
    como: 'Se suman los saldos de las cuentas por pagar con vencimiento pasado.',
    porque: 'Pagar tarde puede dañar la relación con proveedores y generar recargos.',
  },
  cxp_vencidas_conteo: {
    icono: 'event_busy', tono: 'advertencia', corta: 'Facturas por pagar vencidas',
    que: 'Número de cuentas por pagar que ya vencieron.',
    como: 'Se cuentan las cuentas por pagar con fecha de pago pasada.',
    porque: 'Ayuda a organizar tu calendario de pagos.',
  },

  // ---- Tesoreria ----
  saldo_bancario_total: {
    icono: 'account_balance', tono: 'primario', corta: 'Liquidez disponible',
    que: 'Dinero disponible sumando todas tus cuentas bancarias.',
    como: 'Se consolida el saldo de cada cuenta bancaria activa.',
    porque: 'Es tu liquidez inmediata: cuánto efectivo tienes para operar hoy.',
  },
  cuentas_bancarias_activas: {
    icono: 'account_balance_wallet', tono: 'info', corta: 'Cuentas en uso',
    que: 'Número de cuentas bancarias activas registradas.',
    como: 'Se cuentan las cuentas bancarias marcadas como activas.',
    porque: 'Da contexto sobre cómo está distribuido tu efectivo.',
  },
  partidas_conciliacion_pendientes: {
    icono: 'rule', tono: 'advertencia', corta: 'Por conciliar',
    que: 'Movimientos bancarios que aún no se han conciliado con tu contabilidad.',
    como: 'Se cuentan las partidas pendientes de conciliación bancaria.',
    porque: 'Conciliar a tiempo evita errores y descuadres en tus finanzas.',
  },

  // ---- RH y nomina ----
  costo_nomina_periodo: {
    icono: 'groups', tono: 'advertencia', corta: 'Costo de personal',
    que: 'Costo total de la nómina del periodo (percepciones de los empleados).',
    como: 'Se suman las percepciones de todas las nóminas del periodo.',
    porque: 'Es uno de tus gastos fijos más grandes; vigilarlo cuida la rentabilidad.',
  },
  neto_nomina_periodo: {
    icono: 'payments', tono: 'primario', corta: 'Pagado a empleados',
    que: 'Neto que efectivamente se pagó a los empleados en el periodo.',
    como: 'Se suman los netos (percepciones menos deducciones) de las nóminas.',
    porque: 'Es la salida real de efectivo por concepto de sueldos.',
  },
  recibos_nomina_periodo: {
    icono: 'receipt', tono: 'info', corta: 'Recibos generados',
    que: 'Número de recibos de nómina emitidos en el periodo.',
    como: 'Se cuentan los recibos de nómina generados.',
    porque: 'Refleja el tamaño de tu plantilla activa en el periodo.',
  },

  // ---- Activos fijos ----
  activos_valor_neto_libros: {
    icono: 'savings', tono: 'primario', corta: 'Valor contable de bienes',
    que: 'Valor contable actual de tus activos fijos, ya descontada la depreciación.',
    como: 'Costo original de cada activo menos su depreciación acumulada.',
    porque: 'Indica cuánto valen hoy tus bienes (maquinaria, equipo) en libros.',
  },
  activos_costo_total: {
    icono: 'shopping_bag', tono: 'info', corta: 'Inversión original',
    que: 'Costo original de adquisición de todos tus activos fijos.',
    como: 'Se suman los costos de compra de cada activo.',
    porque: 'Muestra cuánto has invertido en bienes de la empresa.',
  },
  activos_depreciacion_acumulada: {
    icono: 'trending_down', tono: 'advertencia', corta: 'Desgaste acumulado',
    que: 'Pérdida de valor acumulada de tus activos por el uso y el tiempo.',
    como: 'Se suma la depreciación registrada de cada activo.',
    porque: 'Refleja el desgaste contable; ayuda a planear reemplazos.',
  },
  activos_vigentes: {
    icono: 'inventory_2', tono: 'exito', corta: 'Bienes en uso',
    que: 'Número de activos fijos que siguen en uso.',
    como: 'Se cuentan los activos en estado "activo".',
    porque: 'Da idea del tamaño de tu parque de bienes operativos.',
  },
  activos_baja: {
    icono: 'archive', tono: 'neutro', corta: 'Bienes dados de baja',
    que: 'Número de activos fijos que ya se dieron de baja.',
    como: 'Se cuentan los activos en estado "baja".',
    porque: 'Historial de bienes retirados de operación.',
  },

  // ---- Redes sociales ----
  mensajes_recibidos: {
    icono: 'inbox', tono: 'info', corta: 'Mensajes entrantes',
    que: 'Mensajes recibidos de clientes en tus redes sociales.',
    como: 'Se suman los mensajes entrantes de todos los canales conectados.',
    porque: 'Mide la demanda de atención y el interés de tu audiencia.',
  },
  mensajes_enviados: {
    icono: 'outbox', tono: 'primario', corta: 'Respuestas enviadas',
    que: 'Mensajes que tu equipo envió a clientes en redes sociales.',
    como: 'Se suman los mensajes salientes de todos los canales.',
    porque: 'Refleja qué tan activa es tu atención en redes.',
  },
  conversaciones_alcance: {
    icono: 'forum', tono: 'info', corta: 'Personas alcanzadas',
    que: 'Número de conversaciones/personas alcanzadas.',
    como: 'Se suma el alcance reportado por los canales sociales.',
    porque: 'Mide el tamaño de tu audiencia en contacto.',
  },
  tiempo_respuesta_promedio: {
    icono: 'timer', tono: 'advertencia', corta: 'Rapidez de respuesta',
    que: 'Tiempo promedio que tardas en responder un mensaje.',
    como: 'Promedio del tiempo entre recibir un mensaje y responderlo.',
    porque: 'Responder rápido mejora la satisfacción y las ventas.',
  },
  leads_captados: {
    icono: 'person_add', tono: 'exito', corta: 'Prospectos generados',
    que: 'Prospectos (leads) captados a través de redes sociales.',
    como: 'Se cuentan las conversaciones que se convirtieron en prospecto.',
    porque: 'Conecta el esfuerzo en redes con oportunidades de venta.',
  },

  // ---- Estrategia ----
  avance_promedio_objetivos: {
    icono: 'trending_up', tono: 'primario', corta: 'Avance de objetivos',
    que: 'Promedio de avance de todos tus objetivos estratégicos.',
    como: 'Se promedia el porcentaje de avance de cada objetivo activo.',
    porque: 'Resume en un número qué tan cerca estás de cumplir tu estrategia.',
  },
  objetivos_estrategicos_total: {
    icono: 'flag', tono: 'info', corta: 'Objetivos definidos',
    que: 'Número total de objetivos estratégicos definidos.',
    como: 'Se cuentan todos los objetivos estratégicos registrados.',
    porque: 'Da contexto sobre el alcance de tu planeación.',
  },
  objetivos_estrategicos_cumplidos: {
    icono: 'military_tech', tono: 'exito', corta: 'Metas logradas',
    que: 'Objetivos estratégicos que ya alcanzaron su meta.',
    como: 'Se cuentan los objetivos con avance del 100%.',
    porque: 'Mide resultados concretos de tu planeación estratégica.',
  },

  // ---- Presupuesto ----
  presupuesto_ingresos_estimados: {
    icono: 'request_quote', tono: 'info', corta: 'Ingresos planeados',
    que: 'Ingresos que presupuestaste para el periodo.',
    como: 'Se toman los ingresos estimados en tu presupuesto.',
    porque: 'Es tu meta de ingresos contra la cual comparar lo real.',
  },
  presupuesto_ingresos_reales: {
    icono: 'payments', tono: 'exito', corta: 'Ingresos logrados',
    que: 'Ingresos que realmente obtuviste en el periodo.',
    como: 'Se suman los ingresos reales registrados.',
    porque: 'Comparado con lo presupuestado, muestra si vas por buen camino.',
  },
  presupuesto_variacion_ingresos: {
    icono: 'compare_arrows', tono: 'primario', corta: 'Real vs presupuesto',
    que: 'Diferencia entre los ingresos reales y los presupuestados.',
    como: 'Ingresos reales menos ingresos presupuestados.',
    porque: 'Positivo = superaste la meta; negativo = te quedaste corto.',
  },
  presupuesto_egresos_estimados: {
    icono: 'request_quote', tono: 'info', corta: 'Gastos planeados',
    que: 'Egresos que presupuestaste para el periodo.',
    como: 'Se toman los egresos estimados en tu presupuesto.',
    porque: 'Es tu límite de gasto planeado.',
  },
  presupuesto_egresos_reales: {
    icono: 'shopping_cart_checkout', tono: 'advertencia', corta: 'Gastos ejercidos',
    que: 'Egresos que realmente ejerciste en el periodo.',
    como: 'Se suman los egresos reales registrados.',
    porque: 'Comparado con lo presupuestado, alerta si te estás pasando de gasto.',
  },
  presupuesto_variacion_egresos: {
    icono: 'compare_arrows', tono: 'primario', corta: 'Gasto real vs plan',
    que: 'Diferencia entre los egresos reales y los presupuestados.',
    como: 'Egresos reales menos egresos presupuestados.',
    porque: 'Ayuda a controlar el gasto: idealmente cercano a cero o negativo.',
  },
};

/** Ficha generica cuando la clave no esta catalogada (nunca queda sin explicacion). */
const FICHA_GENERICA: FichaIndicador = {
  icono: 'insights',
  tono: 'neutro',
  corta: 'Indicador del negocio',
  que: 'Indicador de desempeño de tu empresa.',
  como: 'Se calcula a partir de la información registrada en el sistema durante el periodo.',
  porque: 'Te ayuda a entender cómo va tu empresa de un vistazo.',
};

/**
 * Resuelve la ficha de presentacion de un indicador por su clave. Si no esta en
 * el catalogo, deriva una ficha razonable por heuristica (tono por unidad y por
 * palabras clave como "vencid", "cancelad", "cumplimiento"), de modo que colores
 * y textos siempre tengan sentido aunque se agreguen indicadores nuevos.
 */
export function fichaIndicador(clave: string, unidad?: string): FichaIndicador {
  const ficha = CATALOGO[clave];
  if (ficha) {
    return ficha;
  }
  return { ...FICHA_GENERICA, tono: tonoHeuristico(clave, unidad) };
}

/** Deriva un tono por heuristica cuando la clave no esta catalogada. */
function tonoHeuristico(clave: string, unidad?: string): TonoIndicador {
  const c = clave.toLowerCase();
  if (/(vencid|incumplid|rechazad|discrepancia|perdid)/.test(c)) {
    return 'error';
  }
  if (/(pendiente|borrador|bajo_stock|fuera_de_fecha|por_)/.test(c)) {
    return 'advertencia';
  }
  if (/(cumplid|aprobad|ganad|terminad|completad|cerrad|vigente|reales)/.test(c)) {
    return 'exito';
  }
  const u = (unidad ?? '').toUpperCase();
  if (u === 'MXN' || u === 'USD' || u === 'EUR') {
    return 'primario';
  }
  return 'info';
}
