# Requirements Document

## Introduction

El sistema CRM de Anuncios Luminosos es una plataforma integral de gestión comercial, operativa y administrativa (CRM/ERP) diseñada para una empresa que fabrica y distribuye anuncios luminosos. Su propósito es acompañar todo el ciclo de vida de cada proyecto, desde la captación del cliente hasta el mantenimiento posterior a la venta y su reflejo en la contabilidad, dentro de un único entorno de trabajo. En pocas palabras, es el lugar donde el equipo comercial, el área de producción, el personal de instalación, el servicio de mantenimiento, compras, contabilidad, finanzas y recursos humanos colaboran con una misma información y bajo un mismo proceso, del primer contacto a la cobranza.

Hoy en día, buena parte de la información y de las tareas de este tipo de negocio suele quedar dispersa entre correos, hojas de cálculo, mensajes y llamadas telefónicas. Eso dificulta saber en qué punto va cada proyecto, quién es responsable de cada paso, qué quedó pendiente y cómo impacta cada operación en las finanzas de la empresa. El sistema resuelve ese problema al centralizar todo en un solo lugar, ofreciendo trazabilidad y control de punta a punta: cada cliente, cotización, diseño aprobado, permiso, orden de fabricación, instalación, compra, factura y asiento contable queda registrado y visible para quienes deben darle seguimiento.

El recorrido completo de un proyecto se acompaña de forma ordenada y natural. Todo parte del rumbo que la dirección define para la empresa (su misión, su visión, sus valores y sus objetivos estratégicos) y baja hasta la operación diaria. En el día a día, el recorrido comienza con la captación de oportunidades de venta, que se gestionan en un embudo comercial (pipeline) hasta que maduran. A partir de ahí se registran los clientes y sus contactos, se elaboran las cotizaciones y, una vez que el cliente aprueba el diseño o arte propuesto, el proyecto avanza al levantamiento en sitio, la gestión de permisos y zonificación, la fabricación del anuncio y el control del inventario de materiales. Después se programa e instala el anuncio con las cuadrillas de trabajo, se cierra el proyecto asegurando que no queden puntos pendientes y, finalmente, se ofrece mantenimiento y servicio post-venta con acuerdos de nivel de servicio (SLA). En paralelo, el sistema sostiene la operación con compras a proveedores, facturación electrónica, contabilidad, tesorería, presupuestos, recursos humanos y nómina, de modo que cada operación de negocio queda reflejada de forma completa y consistente, y toda esta actividad se atiende también desde las redes sociales y la mensajería con el cliente.

Para que un lector no técnico entienda con claridad todo lo que hace la plataforma, a continuación se enumeran sus áreas funcionales, ordenadas del rumbo estratégico a la operación, las finanzas y el soporte de la plataforma:

- **Planeación estratégica y objetivos**: es el punto de partida donde la dirección plasma la esencia de la empresa (su misión, su visión y sus valores) y la conecta con la operación del día a día. Aquí se definen y se les da seguimiento a objetivos estratégicos medibles (tipo OKR) con un responsable, un periodo y un avance expresado de 0% a 100%, sustentado en resultados clave ponderados (métricas con valor objetivo y valor actual). Cada objetivo muestra además un estado claro (en riesgo, en curso o cumplido), de modo que la empresa siempre sabe hacia dónde va y qué tan cerca está de lograrlo.
- **Comercial y CRM**: gestión de oportunidades y embudo de ventas (pipeline), clientes y contactos, elaboración y seguimiento de cotizaciones, aprobación de diseño o arte del anuncio (pruebas de arte) por parte del cliente, y clasificación de las operaciones por canal de venta para su análisis.
- **Catálogo de productos y listas de precios**: definición de los productos (tipos de anuncio y servicios), su información comercial de apoyo (cliente meta, alianzas y competencia) y listas de precios vigentes por periodo o segmento de cliente para cotizar con precios consistentes.
- **Redes sociales y mensajería omnicanal (WhatsApp, Facebook e Instagram)**: atención al cliente por los tres canales desde una única bandeja unificada, donde cada conversación queda ligada al CRM (al cliente, contacto u oportunidad correspondiente) para dar seguimiento comercial sin salir del sistema; captación de prospectos que llegan por mensajes o anuncios (incluido click-to-WhatsApp) y su conversión en oportunidades de venta; publicación y programación de contenido en las páginas y perfiles de la empresa; gestión básica de campañas publicitarias; y analítica social por canal. La comunicación se realiza a través de las APIs oficiales de Meta, con consentimiento previo (opt-in) del destinatario y respeto a las reglas de mensajería vigentes de cada canal.
- **Operación y producción**: levantamiento en sitio, gestión de permisos y zonificación, órdenes de fabricación, inventario de materiales, y programación e instalación con cuadrillas de trabajo, incluyendo una lista de pendientes al cierre de cada instalación.
- **Inventario avanzado**: control de existencias por almacén (sucursales y bodegas), kardex de entradas, salidas y saldos, máximos y mínimos, punto de reorden para anticipar el reabastecimiento, control de lotes con trazabilidad, costeo del inventario (promedio ponderado o PEPS) y transferencias de existencias entre almacenes.
- **Mantenimiento y post-venta**: contratos de mantenimiento con acuerdos de nivel de servicio (SLA), tickets de servicio preventivo y correctivo, y control de garantías.
- **Proyectos multi-sitio**: agrupación coordinada de muchas ubicaciones para clientes con numerosas sucursales, como cadenas bancarias o grupos comerciales, con un avance consolidado del proyecto a partir del progreso de cada sitio.
- **Compras y proveedores**: catálogo de proveedores, requisiciones de compra, órdenes de compra, recepción de mercancía con integración al inventario, y conciliación de tres vías de las facturas de proveedor (orden de compra, recepción y factura) antes del pago.
- **Facturación electrónica (CFDI 4.0)**: emisión, timbrado y cancelación de comprobantes ante el PAC/SAT, complementos de pago y notas de crédito.
- **Contabilidad y finanzas**: catálogo de cuentas, pólizas contables balanceadas, cuentas por cobrar y por pagar, tesorería y conciliación bancaria, activos fijos y su depreciación, y estados financieros (balance general, estado de resultados y balanza de comprobación).
- **Presupuestos y control de costos**: definición de presupuestos por área y periodo y su comparación contra el ejercicio real, con la variación expresada en importe y en porcentaje y alertas ante desviaciones que superan un umbral.
- **Recursos humanos y nómina**: expedientes de empleados, contratos laborales, incidencias, cálculo de nómina (ISR, IMSS, Infonavit, subsidio al empleo, aguinaldo y PTU) y timbrado del CFDI de nómina.
- **Organización de personal**: organigrama de la empresa, descripción de puestos y su jerarquía (sin ciclos), asignación de empleados a puestos y evaluación periódica del desempeño.
- **Portal de autoservicio del cliente**: acceso restringido para que el cliente consulte y apruebe la información relacionada con sus propios proyectos.
- **Notificaciones**: avisos automáticos por correo electrónico, WhatsApp y demás canales sociales ante eventos clave del proceso.
- **Reportes, tablero e inteligencia de negocio**: indicadores y reportes por área (comercial, producción, instalación, mantenimiento, inventario, compras, finanzas y facturación, recursos humanos y nómina, tesorería, cuentas por pagar, planeación estratégica, presupuesto y redes sociales) y análisis avanzado consolidado que reúne datos de todas las áreas —incluidas las métricas de redes sociales— en tendencias, comparativos y tableros para apoyar la toma de decisiones.
- **Seguridad, auditoría y multi-empresa (SaaS)**: la base de confianza que protege la información del cliente. Incluye control de acceso por roles adaptables (cada persona ve y hace solo lo que le corresponde), un registro de auditoría inmutable con garantías de integridad y no repudio (siempre se sabe quién hizo qué y cuándo), aislamiento total de los datos entre empresas (multi-empresa/multi-tenant), cifrado de los datos sensibles en reposo, gestión y revocación de sesiones para cortar accesos de inmediato ante un riesgo, y la baja y portabilidad de los datos de la empresa cuando así lo decide, respetando la retención fiscal aplicable.
- **Cumplimiento y calidad (ISO 9001:2026)**: capacidades que habilitan y evidencian el Sistema de Gestión de Calidad de la empresa conforme a la sexta edición de la norma ISO 9001 (2026): captura de quejas del cliente como entrada a acciones correctivas, gestión de no conformidades y acciones correctivas con verificación de eficacia, registros separados de riesgos y de oportunidades, gestión del cambio, determinación del contexto de la organización (incluida la pertinencia del cambio climático), uso del registro de auditoría inmutable como evidencia documentada, indicadores de cultura de calidad en el tablero y las redes sociales como fuente de percepción del cliente.

Al iniciar sesión, cada usuario llega a una página principal propia de su empresa que muestra la esencia de la empresa (su misión, visión y valores) junto con el avance de sus objetivos estratégicos y un tablero con los indicadores de las áreas a las que tiene acceso, todo presentado con la identidad visual (logotipo y colores) de la empresa. Además, el sistema cuenta con un área administrativa organizada en dos niveles: por un lado, la administración de la plataforma, que sirve para dar de alta y administrar empresas y sus planes (pensada para cuando el propio cliente quiera rentar el sistema a otras empresas o sucursales); y por otro, la administración de cada empresa, donde se crean los usuarios y se les asignan sus roles y permisos, se configura la marca y se ajustan las integraciones. En todo momento, cada persona ve y usa únicamente lo que su rol le permite, mientras que los clientes finales cuentan con un portal aparte y restringido para consultar solo la información de sus propios proyectos.

Dado que se atienden clientes corporativos exigentes, incluidas instituciones financieras, la confianza y la seguridad son una prioridad. El sistema aplica controles de seguridad de nivel empresarial, control de acceso por roles adaptables según la función de cada persona (cada usuario ve y hace únicamente lo que le corresponde) y un registro de auditoría robusto de las acciones sensibles, con garantías de integridad y no repudio y con retención para fines de cumplimiento, de manera que siempre se sabe quién hizo qué y cuándo. La interfaz es de nivel empresarial: responsiva en cualquier dispositivo, con una identidad visual profesional y sobria, modales de confirmación para acciones sensibles, animaciones sutiles y accesibilidad conforme a estándares (WCAG 2.1 AA).

La plataforma es multi-empresa (SaaS): un mismo sistema puede ser utilizado y rentado por varias empresas de forma independiente, con los datos de cada una totalmente aislados de las demás. Sus roles se adaptan al tamaño de cada empresa, ya sea pequeña, mediana o grande, y su arquitectura queda preparada para escalar y, en el futuro, migrar a la nube sin necesidad de rehacer el sistema.

Este documento describe el sistema completo, de extremo a extremo, propuesto como primer entregable: una única solución integral, comparable a las soluciones líderes del mercado, que abarca toda la operación de la empresa de anuncios luminosos —desde el rumbo estratégico y la venta hasta la fabricación, la instalación, el mantenimiento, las finanzas y el soporte de la plataforma— sin depender de herramientas dispersas. Cabe señalar que ciertas capacidades avanzadas (manufactura avanzada / MRP, logística y envíos con transportistas, y firma electrónica de contratos) se contemplan como evolución futura y no forman parte de este entregable.

Como contexto técnico de referencia, la solución contempla un backend en Spring Boot/Java ejecutado como servicio en un servidor Windows, un frontend en Angular servido por IIS como proxy inverso con HTTPS, y base de datos PostgreSQL con autenticación propia, con un despliegue inicial on-premise portable a nube.

## Glossary

- **Sistema**: La aplicación CRM de Anuncios Luminosos considerada en su conjunto (backend, API y frontend).
- **API**: La interfaz REST versionada expuesta por el backend bajo la ruta base `/api/v1`.
- **Servicio_Autenticacion**: Componente del Sistema responsable de autenticar credenciales y emitir, validar y renovar tokens de acceso.
- **Servicio_Autorizacion**: Componente del Sistema responsable de evaluar los permisos de un usuario autenticado antes de permitir una operación.
- **Servicio_Auditoria**: Componente del Sistema responsable de registrar eventos de seguridad y de negocio.
- **Proxy_Inverso**: Componente de infraestructura (IIS en el servidor Windows) que termina TLS, sirve el frontend y enruta las peticiones hacia la API.
- **Usuario**: Persona con credenciales registradas en el Sistema que accede a través de la interfaz.
- **Administrador**: En el contexto multi-empresa se refiere al Administrador_Empresa (rol `admin_empresa`) que gestiona usuarios, roles, permisos y configuración dentro del ámbito de su propia Empresa.
- **Empresa (Tenant)**: Empresa cliente que renta y usa el Sistema; unidad de aislamiento de datos identificada por un tenant_id.
- **tenant_id**: Identificador único de la Empresa que segmenta y aísla todos los datos de negocio de esa Empresa.
- **Super_Administrador**: Usuario de nivel plataforma con el rol `super_admin` que administra las Empresas (alta, planes, suspensión) pero no opera los datos de negocio de una Empresa.
- **Administrador_Empresa**: Usuario con el rol `admin_empresa` que administra usuarios, roles y configuración dentro de su propia Empresa.
- **Plan**: Definición de suscripción que establece límites (número de Usuarios, módulos habilitados) y estado de una Empresa.
- **Suscripcion**: Relación entre una Empresa y un Plan, con estado (activa, suspendida, cancelada) y periodo de vigencia.
- **Rol**: Conjunto nombrado de permisos asignable a un Usuario. Roles iniciales: nivel plataforma `super_admin`; nivel empresa `admin_empresa`, `gerente`, `supervisor`, `ventas`, `diseño`, `producción`, `almacén`, `instalación`, `mantenimiento`, `contabilidad`, `rh`, `marketing`. Adicionalmente, `cliente_portal` es un rol externo restringido, asignado a un Cliente para el acceso limitado al Portal_Cliente.
- **Rol_Personalizado**: Rol definido por un Administrador_Empresa combinando Permisos existentes, adicional a los roles predefinidos del Sistema.
- **Permiso**: Autorización atómica para ejecutar una operación específica sobre un tipo de recurso.
- **Token_Acceso**: Credencial JWT de vida corta que autoriza peticiones a la API.
- **Token_Refresco**: Credencial de vida más larga usada para obtener un nuevo Token_Acceso sin reingreso de credenciales.
- **Cliente**: Persona física o moral registrada en el Sistema como destinatario comercial de anuncios luminosos.
- **Contacto**: Persona asociada a un Cliente con datos de contacto para gestión comercial.
- **Cotizacion**: Documento comercial que detalla productos, cantidades, precios y condiciones ofrecidos a un Cliente.
- **Partida_Cotizacion**: Renglón individual de una Cotizacion que describe un producto o concepto, su cantidad y su precio.
- **Orden_Fabricacion**: Documento operativo que instruye la producción de uno o más anuncios luminosos derivados de una Cotizacion aprobada.
- **Catalogo**: Conjunto de datos de referencia de cambio lento (por ejemplo, tipos de anuncio, unidades, estados) utilizado por otros módulos.
- **DTO**: Objeto de transferencia de datos expuesto por la API, distinto de las entidades de persistencia internas.
- **Registro_Auditoria**: Entrada inmutable que documenta un evento con actor, acción, recurso afectado y marca temporal.
- **ISO_9001_2026**: Sexta edición de la norma internacional ISO 9001 (Sistemas de gestión de la calidad — Requisitos), cuya publicación reemplaza a ISO 9001:2015. Introduce, entre otros, el énfasis en la cultura de calidad y el comportamiento ético, la separación de riesgos y oportunidades, la gestión del cambio, la integración del cambio climático en el contexto y la información documentada como evidencia.
- **Sistema_Gestion_Calidad (SGC)**: Conjunto de procesos, responsabilidades y registros con los que la Empresa gestiona la calidad conforme a ISO_9001_2026; el Sistema aporta las capacidades y la evidencia que lo sustentan.
- **Contexto_Organizacion**: Cuestiones internas y externas pertinentes para el SGC de la Empresa (incluida la pertinencia del cambio climático) y las expectativas de las partes interesadas (cláusulas 4.1 y 4.2 de ISO_9001_2026).
- **Queja_Cliente**: Reclamación o insatisfacción reportada por un Cliente, registrada como entrada potencial (no obligatoria) a una Accion_Correctiva (cláusula 10.2 de ISO_9001_2026).
- **No_Conformidad**: Incumplimiento de un requisito detectado en un proceso, producto o servicio, que puede originar una Accion_Correctiva.
- **Accion_Correctiva**: Acción para eliminar la causa de una No_Conformidad y evitar su recurrencia, con responsable, causa raíz, acciones, evidencia de cierre y verificación de eficacia (cláusula 10.2 de ISO_9001_2026).
- **Riesgo**: Efecto de la incertidumbre que puede afectar la capacidad de entregar productos y servicios conformes; se determina, analiza y evalúa con acciones propias (cláusula 6.1.2 de ISO_9001_2026).
- **Oportunidad**: Circunstancia favorable para mejorar resultados, tratada de forma separada del Riesgo, con acciones propias (cláusula 6.1.3 de ISO_9001_2026).
- **Cambio_SGC**: Cambio planificado al SGC o a un proceso, que exige documentar propósito, consecuencias, recursos y responsable antes de su aprobación (gestión del cambio, cláusula 6.3 de ISO_9001_2026).
- **TLS**: Protocolo de seguridad de la capa de transporte usado para cifrar la comunicación entre el cliente y el Proxy_Inverso.
- **Oportunidad**: Prospecto o lead de venta gestionado en el pipeline comercial antes de convertirse en una Cotizacion.
- **Prueba_Diseno**: Prueba de diseño o arte enviada al Cliente para su aprobación, gestionada con versiones sucesivas.
- **Levantamiento_Sitio**: Registro del levantamiento en sitio con mediciones, tipo de superficie o estructura, condiciones eléctricas y fotografías.
- **Permiso_Instalacion**: Permiso municipal o de zonificación, o autorización del arrendador, requerido para instalar en un Sitio, con fecha de vencimiento.
- **Proyecto**: Agrupación de varios Sitio de un mismo Cliente bajo un objetivo común (por ejemplo, sucursales bancarias).
- **Sitio**: Ubicación o sucursal individual perteneciente a un Proyecto donde se instala un anuncio luminoso.
- **Material**: Artículo de inventario consumido durante la fabricación de anuncios luminosos.
- **Movimiento_Inventario**: Registro de un cambio de existencias de un Material: entrada, salida o ajuste.
- **Orden_Trabajo_Instalacion**: Orden de trabajo de instalación derivada de una Orden_Fabricacion terminada y asignada a una Cuadrilla.
- **Cuadrilla**: Equipo de trabajo responsable de ejecutar instalaciones o servicios de mantenimiento en sitio.
- **Lista_Pendientes**: Relación de puntos pendientes por resolver al cierre de una instalación (punch list).
- **Contrato_Mantenimiento**: Acuerdo de mantenimiento con un Cliente que define el SLA y el tipo de servicio (preventivo o correctivo).
- **Ticket_Servicio**: Solicitud de atención de mantenimiento, preventivo o correctivo, con seguimiento de estado.
- **SLA**: Acuerdo de nivel de servicio que define los tiempos máximos de respuesta y de resolución comprometidos.
- **Tablero**: Panel de indicadores que consolida métricas de las distintas áreas del Sistema para su consulta.
- **Proveedor**: Persona física o moral que suministra Materiales o servicios a la Empresa.
- **Requisicion_Compra**: Solicitud interna de compra de Materiales que, una vez aprobada, puede convertirse en una Orden_Compra.
- **Orden_Compra**: Documento que formaliza la compra de Materiales a un Proveedor, con cantidades y precios acordados.
- **Partida_Orden_Compra**: Renglón individual de una Orden_Compra que describe un Material, su cantidad y su precio unitario.
- **Recepcion_Mercancia**: Registro de la recepción física de los Materiales de una Orden_Compra.
- **Factura_Proveedor**: Documento de cobro emitido por un Proveedor asociado a una Orden_Compra.
- **Conciliacion_Tres_Vias**: Control que verifica la coincidencia en cantidad y precio entre la Orden_Compra, la Recepcion_Mercancia y la Factura_Proveedor antes de autorizar el pago.
- **Factura (CFDI)**: Comprobante Fiscal Digital por Internet (CFDI 4.0) emitido a un Cliente, con validez fiscal una vez timbrado por un PAC.
- **PAC**: Proveedor Autorizado de Certificación que timbra (certifica) los CFDI ante el SAT y devuelve el folio fiscal (UUID).
- **Timbrado**: Proceso de certificación de un CFDI ante el SAT a través de un PAC que le asigna un folio fiscal (UUID).
- **Folio_Fiscal**: Identificador único (UUID) que el SAT/PAC asigna a un CFDI timbrado.
- **Complemento_Pago**: CFDI de tipo pago que documenta los pagos recibidos en parcialidades o de forma diferida.
- **Nota_Credito**: CFDI de tipo egreso que documenta una devolución, descuento o corrección sobre una Factura previa.
- **Pago_Cliente**: Registro de un pago recibido de un Cliente y su aplicación a una o varias Facturas.
- **Cuenta_Contable**: Cuenta del catálogo contable de la Empresa usada para clasificar los movimientos.
- **Poliza_Contable**: Asiento contable (conjunto de cargos y abonos balanceados) que registra el efecto contable de una operación.
- **IVA**: Impuesto al Valor Agregado aplicable a las operaciones, con tasa vigente en México (16%).
- **Retencion**: Impuesto retenido aplicable a ciertas operaciones conforme a la normativa fiscal.
- **Cuenta_Por_Cobrar**: Saldo pendiente de cobro de un Cliente derivado de Facturas emitidas y no pagadas.
- **Empleado**: Persona que labora para la Empresa, con sus datos laborales y fiscales.
- **Contrato_Laboral**: Relación laboral de un Empleado con su tipo, salario y vigencia.
- **Incidencia**: Registro de asistencia, falta, permiso, incapacidad o tiempo extra de un Empleado en un periodo.
- **Periodo_Nomina**: Intervalo (semanal, quincenal o mensual) para el cálculo de la Nomina.
- **Nomina**: Cálculo de percepciones, deducciones y neto a pagar de los Empleados en un Periodo_Nomina.
- **Recibo_Nomina**: Comprobante por Empleado dentro de una Nomina, documentado como CFDI de nómina.
- **ISR**: Impuesto Sobre la Renta retenido a los Empleados.
- **IMSS**: Cuotas obrero-patronales del Instituto Mexicano del Seguro Social.
- **Infonavit**: Aportaciones y descuentos por crédito de vivienda.
- **Cuenta_Bancaria**: Cuenta bancaria de la Empresa registrada para tesorería.
- **Estado_Cuenta_Bancario**: Conjunto de movimientos bancarios importados de una Cuenta_Bancaria en un periodo.
- **Movimiento_Bancario**: Cargo o abono individual en un Estado_Cuenta_Bancario.
- **Conciliacion_Bancaria**: Proceso de emparejar Movimiento_Bancario con Poliza_Contable o Pago para verificar la coincidencia del saldo.
- **Cuenta_Por_Pagar**: Saldo pendiente de pago a un Proveedor derivado de Facturas de Proveedor conciliadas.
- **Programacion_Pago**: Calendario y autorización de pagos a Proveedores.
- **Activo_Fijo**: Bien de la Empresa sujeto a depreciación.
- **Depreciacion**: Registro periódico de la pérdida de valor de un Activo_Fijo.
- **Portal_Cliente**: Acceso restringido para que un Cliente consulte y apruebe información relacionada con sus proyectos.
- **Notificacion**: Mensaje enviado por el Sistema a un destinatario ante un evento, a través de un canal disponible (correo electrónico, WhatsApp u otro Canal_Social), respetando cuando aplique la Ventana_Servicio, las Plantilla_Mensaje aprobadas y el Opt_In del destinatario.
- **Estado_Financiero**: Reporte contable estructurado (balance general, estado de resultados o balanza de comprobación).
- **Inteligencia_Negocio**: Capacidad de análisis avanzado que consolida datos de todos los módulos en indicadores, tendencias y reportes analíticos para la toma de decisiones.
- **Sistema_Diseno**: Conjunto de tokens de diseño (colores, tipografía, espaciados, sombras) y componentes reutilizables que garantizan una apariencia consistente y profesional en toda la interfaz.
- **Modal_Confirmacion**: Ventana emergente que solicita al Usuario confirmar explícitamente una acción sensible o irreversible antes de ejecutarla.
- **Notificacion_Interfaz**: Mensaje breve en la interfaz (por ejemplo, tipo "toast") que informa al Usuario del resultado de una acción.
- **WCAG**: Pautas de Accesibilidad para el Contenido Web (Web Content Accessibility Guidelines), referencia de accesibilidad de la interfaz.
- **Punto_Quiebre**: Umbral de ancho de pantalla (breakpoint) a partir del cual la interfaz adapta su disposición (escritorio, tablet o móvil).
- **Objetivo_Estrategico**: Meta medible de la Empresa (por ejemplo, objetivo/OKR) con responsable, periodo y avance, alineada a su misión, visión y valores.
- **Producto**: Bien o servicio del catálogo de la Empresa (por ejemplo, un tipo de anuncio luminoso) con su descripción y precios.
- **Lista_Precios**: Conjunto de precios vigentes de los Productos, aplicable por periodo o por segmento de Cliente.
- **Almacen**: Ubicación física de la Empresa (sucursal o bodega) donde se resguardan existencias de Material o Producto.
- **Kardex**: Reporte cronológico de entradas, salidas y saldos de un Material o Producto en un Almacen.
- **Lote**: Conjunto identificable de existencias de un Material o Producto recibido o producido en conjunto, con trazabilidad.
- **Puesto**: Definición de un cargo dentro de la Empresa con sus responsabilidades, ubicable en el organigrama.
- **Evaluacion_Desempeno**: Registro de la evaluación periódica del desempeño de un Empleado.
- **Presupuesto**: Estimación planificada de ingresos y/o egresos por área y periodo, contra la cual se compara el ejercicio real.
- **Canal_Social**: Medio de mensajería o publicación integrado con las APIs oficiales de Meta: WhatsApp, Facebook Messenger o Instagram.
- **Cuenta_Canal_Social**: Configuración de conexión de una Empresa a un Canal_Social (por ejemplo, número de WhatsApp Business, página de Facebook o perfil de Instagram) con sus credenciales de acceso gestionadas fuera del código.
- **Bandeja_Unificada**: Vista consolidada que reúne las Conversacion de los tres Canal_Social en un único hilo por Cliente o Contacto, con contexto e historial compartidos.
- **Conversacion**: Hilo de intercambio de Mensaje_Social entre la Empresa y un Cliente o Contacto a través de uno o varios Canal_Social, persistido como registro del CRM.
- **Mensaje_Social**: Mensaje individual entrante o saliente de una Conversacion en un Canal_Social, con su contenido, sentido, canal, estado de entrega y marca temporal.
- **Plantilla_Mensaje**: Plantilla de mensaje aprobada por el proveedor del Canal_Social (por ejemplo, plantilla de WhatsApp o mensaje etiquetado de Messenger/Instagram) requerida para iniciar o continuar la comunicación fuera de la Ventana_Servicio.
- **Ventana_Servicio**: Intervalo de 24 horas contado desde el último Mensaje_Social entrante del Cliente durante el cual la Empresa puede responder con texto libre; fuera de este intervalo se requiere una Plantilla_Mensaje.
- **Opt_In**: Consentimiento explícito y registrado del Cliente o Contacto para recibir Mensaje_Social o Notificacion por un Canal_Social; el Opt_Out es su revocación.
- **Publicacion_Social**: Contenido programado o publicado por la Empresa en una de sus páginas o perfiles de Facebook o Instagram, con su estado y fecha de publicación.
- **Campaña_Publicitaria**: Campaña publicitaria gestionada de forma básica en un Canal_Social (creación, consulta de estado, presupuesto y periodo) a través de la Marketing API de Meta.
- **Llave_Cifrado**: Clave criptográfica utilizada para cifrar y descifrar los datos sensibles en reposo, gestionada fuera del código y sujeta a rotación.
- **Cifrado_En_Reposo**: Protección criptográfica de los datos almacenados (a nivel de almacenamiento o de base de datos) que impide su lectura sin la Llave_Cifrado correspondiente.
- **Sesion**: Contexto de acceso autenticado de un Usuario, sustentado por un Token_Acceso vigente y un Token_Refresco asociado, que puede cerrarse o revocarse.
- **Periodo_Gracia**: Intervalo configurable durante el cual el Sistema conserva los datos de una Empresa tras la cancelación de su Suscripcion, antes de cualquier eliminación definitiva.
- **Offboarding_Empresa**: Proceso de exportación y posterior eliminación o anonimización de los datos de negocio de una Empresa (tenant) al término de su relación con la plataforma, respetando la retención fiscal aplicable.

## Requirements

### Requisito 1: Autenticación de usuarios

**Historia de Usuario:** Como Usuario del área comercial u operativa, quiero iniciar sesión con mis credenciales, para acceder a las funciones del Sistema autorizadas para mi rol.

#### Criterios de Aceptación

1. WHEN un Usuario envía un identificador y contraseña que coinciden con una cuenta activa registrada, THE Servicio_Autenticacion SHALL emitir un Token_Acceso y un Token_Refresco.
2. THE Servicio_Autenticacion SHALL almacenar las contraseñas usando un algoritmo de hashing adaptativo con sal (BCrypt o Argon2).
3. IF un Usuario envía credenciales que no coinciden con una cuenta activa registrada, THEN THE Servicio_Autenticacion SHALL rechazar la solicitud sin emitir tokens, responder con un error de autenticación genérico que no indique cuál credencial fue incorrecta, y conservar sin cambios el estado de la cuenta.
4. THE Servicio_Autenticacion SHALL emitir Token_Acceso con una vigencia máxima de 15 minutos.
5. WHEN un Usuario presenta un Token_Refresco válido y vigente, THE Servicio_Autenticacion SHALL emitir un nuevo Token_Acceso.
6. IF un Token_Acceso ha expirado, THEN THE API SHALL rechazar la petición con un código de estado 401.
7. THE Servicio_Autenticacion SHALL emitir Token_Refresco con una vigencia máxima de 7 días.
8. IF un Usuario acumula 5 intentos de autenticación fallidos consecutivos para la misma cuenta, THEN THE Servicio_Autenticacion SHALL bloquear los intentos de inicio de sesión de esa cuenta durante 15 minutos y rechazar toda solicitud de autenticación adicional con un error indicando que la cuenta está temporalmente bloqueada.
9. IF un Usuario presenta un Token_Refresco expirado, revocado o inválido, THEN THE Servicio_Autenticacion SHALL rechazar la solicitud sin emitir un nuevo Token_Acceso y responder con un error de autenticación.

### Requisito 2: Bloqueo por intentos fallidos y limitación de tasa

**Historia de Usuario:** Como Administrador, quiero que el Sistema limite los intentos de inicio de sesión y la frecuencia de peticiones, para reducir el riesgo de ataques de fuerza bruta y abuso.

#### Criterios de Aceptación

1. IF una cuenta de Usuario acumula 5 intentos de inicio de sesión fallidos consecutivos dentro de una ventana de 15 minutos, THEN THE Servicio_Autenticacion SHALL bloquear el inicio de sesión de esa cuenta durante 15 minutos y reiniciar el contador de intentos fallidos a 0 al finalizar el periodo de bloqueo.
2. WHILE una cuenta de Usuario está bloqueada, THE Servicio_Autenticacion SHALL rechazar cada intento de inicio de sesión de esa cuenta con un mensaje que indique el bloqueo temporal y el tiempo restante en minutos hasta el desbloqueo.
3. WHEN un intento de inicio de sesión finaliza, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el identificador de cuenta, la dirección de origen, la marca temporal en formato UTC y el resultado del intento (exitoso o fallido).
4. IF una dirección de origen supera 100 peticiones por minuto a la API, THEN THE API SHALL rechazar cada petición excedente con un código de estado 429 y un mensaje que indique el límite de tasa superado, sin procesar la petición.
5. WHEN un intento de inicio de sesión de una cuenta bloqueada es rechazado, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el identificador de cuenta, la dirección de origen, la marca temporal en formato UTC y la indicación de rechazo por bloqueo.

### Requisito 3: Autorización basada en roles con privilegio mínimo

**Historia de Usuario:** Como Administrador, quiero que cada operación se autorice según el rol del Usuario, para que cada persona acceda únicamente a lo que su función requiere.

#### Criterios de Aceptación

1. WHEN un Usuario autenticado solicita una operación de la API, THE Servicio_Autorizacion SHALL verificar que el Rol del Usuario posee el Permiso requerido antes de ejecutar la operación.
2. IF un Usuario autenticado solicita una operación para la que su Rol carece de Permiso, THEN THE Servicio_Autorizacion SHALL rechazar la operación con un código de estado 403 y conservar sin modificaciones el estado del recurso solicitado.
3. THE Sistema SHALL soportar al menos los roles de nivel empresa `admin_empresa`, `gerente`, `supervisor`, `ventas`, `diseño`, `producción`, `almacén`, `instalación`, `mantenimiento`, `contabilidad`, `rh` y `marketing`.
4. WHERE un Usuario tiene el rol `admin_empresa`, THE Sistema SHALL permitir la gestión de Usuarios, Roles y Permisos dentro de su propia Empresa.
5. THE Servicio_Autorizacion SHALL denegar por defecto toda operación cuyo Permiso no esté explícitamente concedido al Rol del Usuario.
6. IF un Usuario autenticado no tiene ningún Rol válido asignado, THEN THE Servicio_Autorizacion SHALL rechazar toda operación de la API con un código de estado 403.
7. THE Sistema SHALL reconocer el rol `super_admin` como un rol de nivel plataforma cuyo ámbito es la administración de Empresas (tenants) y NO los datos de negocio de una Empresa.

### Requisito 4: Gestión de usuarios, roles y permisos

**Historia de Usuario:** Como Administrador, quiero administrar cuentas de Usuario y sus Roles, para controlar quién accede al Sistema y con qué privilegios.

#### Criterios de Aceptación

1. WHEN un Administrador crea una cuenta de Usuario con datos válidos, THE Sistema SHALL registrar la cuenta y asignar al menos un Rol.
2. WHEN un Administrador desactiva una cuenta de Usuario, THE Sistema SHALL impedir el inicio de sesión de esa cuenta.
3. WHEN un Administrador modifica el Rol de un Usuario, THE Sistema SHALL aplicar los Permisos del nuevo Rol en la siguiente evaluación de autorización.
4. IF un Administrador intenta crear una cuenta de Usuario con un identificador de acceso ya existente, THEN THE Sistema SHALL rechazar la creación e informar el conflicto.
5. WHEN un Administrador realiza una operación de gestión de Usuarios, Roles o Permisos, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción y el recurso afectado.

### Requisito 5: Gestión de clientes y contactos

**Historia de Usuario:** Como Usuario del área de ventas, quiero registrar y consultar Clientes y sus Contactos, para gestionar la relación comercial de forma centralizada.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso registra un Cliente proporcionando los datos obligatorios (razón social o nombre entre 1 y 200 caracteres, identificador fiscal entre 12 y 13 caracteres, y al menos un dato de contacto consistente en un correo electrónico válido o un teléfono de 10 a 15 dígitos), THE Sistema SHALL persistir el Cliente y asignarle un identificador único.
2. IF un Usuario intenta registrar un Cliente omitiendo alguno de los datos obligatorios o con un identificador fiscal cuyo formato es inválido, THEN THE Sistema SHALL rechazar el registro, no persistir ningún dato e informar un mensaje que indique el campo inválido o faltante.
3. IF un Usuario intenta registrar un Cliente con un identificador fiscal ya existente en un Cliente activo, THEN THE Sistema SHALL rechazar el registro, conservar sin cambios el Cliente existente e informar un mensaje que indique el identificador fiscal duplicado.
4. WHEN un Usuario con Permiso actualiza los datos de un Cliente, THE Sistema SHALL persistir los cambios y registrar un Registro_Auditoria con el actor y la marca temporal.
5. WHEN un Usuario con Permiso asocia un Contacto a un Cliente activo, THE Sistema SHALL persistir el Contacto vinculado a ese Cliente.
6. IF un Usuario intenta asociar un Contacto a un Cliente inexistente o inactivo, THEN THE Sistema SHALL rechazar la operación, no persistir el Contacto e informar un mensaje que indique que el Cliente no está disponible.
7. WHEN un Usuario con Permiso consulta el listado de Clientes, THE API SHALL devolver los resultados de forma paginada con un tamaño de página configurable entre 1 y 100 registros, aplicando un tamaño de página por defecto de 20 registros cuando no se especifique.
8. WHEN un Usuario con Permiso consulta el listado de Clientes indicando un criterio de filtro por nombre o por identificador fiscal, THE API SHALL devolver únicamente los Clientes cuyo nombre o identificador fiscal contenga la cadena indicada, sin distinguir mayúsculas de minúsculas.
9. WHEN un Usuario con Permiso elimina un Cliente que se encuentra activo, THE Sistema SHALL marcar el Cliente como inactivo conservando sus datos históricos y registrar un Registro_Auditoria con el actor y la marca temporal.

### Requisito 6: Gestión y seguimiento de cotizaciones

**Historia de Usuario:** Como Usuario del área de ventas, quiero elaborar Cotizaciones y dar seguimiento a su estado, para conducir el proceso comercial hasta el cierre.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso crea una Cotizacion asociada a un Cliente existente con al menos una y como máximo 500 Partida_Cotizacion, THE Sistema SHALL persistir la Cotizacion, asignarle un identificador único e inmutable y establecer su estado inicial en "borrador".
2. IF un Usuario intenta crear una Cotizacion sin un Cliente asociado o sin al menos una Partida_Cotizacion, THEN THE Sistema SHALL rechazar la creación, no persistir ningún dato e informar un mensaje de error que indique la causa de la validación fallida.
3. WHEN un Usuario agrega una Partida_Cotizacion con una cantidad entre 1 y 999,999 y un precio unitario entre 0.01 y 999,999,999.99 en la moneda única del sistema, THE Sistema SHALL calcular el subtotal de la partida como el producto de la cantidad por el precio unitario, redondeado a 2 decimales mediante redondeo al valor más cercano.
4. IF un Usuario agrega una Partida_Cotizacion con una cantidad fuera del rango de 1 a 999,999 o con un precio unitario fuera del rango de 0.01 a 999,999,999.99, THEN THE Sistema SHALL rechazar la partida, no calcular su subtotal e informar un mensaje de error que indique el valor inválido.
5. THE Sistema SHALL calcular el total de una Cotizacion como la suma de los subtotales de sus Partida_Cotizacion, expresado con 2 decimales en la moneda única del sistema mediante redondeo al valor más cercano.
6. WHEN un Usuario con Permiso cambia el estado de una Cotizacion, THE Sistema SHALL permitir únicamente las transiciones definidas: de "borrador" a "enviada", de "enviada" a "aprobada" o de "enviada" a "rechazada".
7. IF un Usuario intenta una transición de estado no definida para una Cotizacion, THEN THE Sistema SHALL rechazar el cambio, conservar el estado anterior sin modificaciones e informar un mensaje de error que indique la transición inválida.
8. WHEN un Usuario con Permiso consulta el listado de Cotizaciones sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página.
9. THE API SHALL permitir filtrar el listado de Cotizaciones por Cliente y por estado.
10. WHEN el estado de una Cotizacion cambia, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, el estado anterior, el estado nuevo y la marca temporal.

### Requisito 7: Gestión de órdenes de fabricación

**Historia de Usuario:** Como Usuario del área de producción, quiero generar y dar seguimiento a Órdenes de Fabricación a partir de Cotizaciones aprobadas, para coordinar la manufactura de los anuncios luminosos.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso genera una Orden_Fabricacion a partir de una Cotizacion en estado "aprobada", THE Sistema SHALL crear la Orden_Fabricacion vinculada a esa Cotizacion, asignarle un identificador único y devolver una confirmación que incluya dicho identificador.
2. IF un Usuario intenta generar una Orden_Fabricacion a partir de una Cotizacion cuyo estado no es "aprobada", THEN THE Sistema SHALL rechazar la generación, no crear ninguna Orden_Fabricacion e informar mediante un mensaje que indique que se requiere una Cotizacion aprobada.
3. IF un Usuario intenta generar una Orden_Fabricacion a partir de una Cotizacion que ya tiene una Orden_Fabricacion vinculada, THEN THE Sistema SHALL rechazar la generación, no crear una segunda Orden_Fabricacion e informar mediante un mensaje que indique que la Cotizacion ya tiene una Orden_Fabricacion asociada.
4. THE Sistema SHALL establecer el estado inicial de una Orden_Fabricacion en "pendiente".
5. WHEN un Usuario con Permiso cambia el estado de una Orden_Fabricacion, THE Sistema SHALL permitir únicamente las siguientes transiciones: de "pendiente" a "en_producción", de "en_producción" a "terminada", de "pendiente" a "cancelada" y de "en_producción" a "cancelada"; y THE Sistema SHALL considerar "terminada" y "cancelada" como estados finales que no admiten ninguna transición posterior.
6. IF un Usuario intenta una transición de estado no incluida en las transiciones permitidas de una Orden_Fabricacion, incluida cualquier transición que parta de un estado final, THEN THE Sistema SHALL rechazar el cambio, conservar el estado actual sin modificarlo e informar mediante un mensaje que indique la transición inválida.
7. WHEN un Usuario con Permiso consulta el listado de Órdenes de Fabricación, THE API SHALL devolver los resultados de forma paginada, con un tamaño de página predeterminado de 20 elementos, un tamaño de página máximo de 100 elementos e incluyendo el total de elementos y el total de páginas.
8. IF un Usuario solicita el listado con un tamaño de página superior a 100 elementos, THEN THE API SHALL rechazar la solicitud e informar mediante un mensaje que indique el tamaño de página máximo permitido.
9. THE API SHALL permitir filtrar el listado de Órdenes de Fabricación por estado y por Cliente, y WHEN ningún resultado coincide con los filtros aplicados, THE API SHALL devolver un listado vacío con el total de elementos en 0.
10. WHEN el estado de una Orden_Fabricacion cambia, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, el estado anterior, el estado nuevo y la marca temporal.

### Requisito 8: Validación de entradas

**Historia de Usuario:** Como Administrador responsable de la calidad de los datos, quiero que el Sistema valide toda entrada recibida, para evitar datos inválidos y reducir vectores de ataque por inyección.

#### Criterios de Aceptación

1. WHEN la API recibe una petición con datos, THE API SHALL validar el formato, tipo y obligatoriedad de cada campo antes de procesar la operación.
2. IF una petición contiene datos que incumplen las reglas de validación, THEN THE API SHALL rechazar la petición con un código de estado 400 y una descripción de los campos inválidos.
3. THE Sistema SHALL usar consultas parametrizadas para todo acceso a la base de datos, de modo que los valores de entrada no se interpreten como instrucciones ejecutables.
4. WHEN la API devuelve datos originados por el Usuario, THE API SHALL codificar dichos datos de forma que el navegador no los interprete como código ejecutable.

### Requisito 9: Protección de la comunicación y cabeceras de seguridad

**Historia de Usuario:** Como cliente institucional que exige confidencialidad, quiero que toda comunicación esté cifrada y protegida por controles del navegador, para preservar la integridad y confidencialidad de la información.

#### Criterios de Aceptación

1. WHEN un cliente establece una conexión con el Proxy_Inverso, THE Proxy_Inverso SHALL requerir TLS y rechazar conexiones no cifradas.
2. WHEN el Proxy_Inverso recibe una petición mediante HTTP no cifrado, THE Proxy_Inverso SHALL redirigirla a su equivalente cifrado por HTTPS.
3. THE Sistema SHALL incluir en cada respuesta las cabeceras de seguridad Content-Security-Policy, Strict-Transport-Security y X-Frame-Options.
4. WHEN la API recibe una petición que modifica estado desde un contexto de navegador, THE API SHALL exigir un mecanismo de protección contra falsificación de peticiones entre sitios.

### Requisito 10: Registro de auditoría

**Historia de Usuario:** Como Administrador responsable de cumplimiento, quiero un registro inmutable de acciones sensibles, para poder responder quién hizo qué y cuándo.

#### Criterios de Aceptación

1. WHEN un Usuario ejecuta una operación de autenticación, gestión de acceso, o de creación, modificación o cambio de estado de Clientes, Cotizaciones u Órdenes de Fabricación, THE Servicio_Auditoria SHALL crear un Registro_Auditoria.
2. THE Registro_Auditoria SHALL contener el identificador del actor, la acción, el recurso afectado, el tenant_id de la Empresa del recurso afectado y la marca temporal en tiempo universal coordinado (UTC).
3. WHEN un Super_Administrador ejecuta una acción de nivel plataforma, THE Servicio_Auditoria SHALL crear un Registro_Auditoria con ámbito de plataforma, sin tenant_id de negocio o con una marca de ámbito de plataforma.
4. THE Sistema SHALL conservar los Registro_Auditoria sin permitir su modificación ni su eliminación por parte de los Usuarios.
5. WHEN un Administrador consulta los Registro_Auditoria, THE API SHALL devolver los resultados de forma paginada y filtrables por actor, tipo de recurso y rango de fechas.
6. WHEN un Usuario consulta, lee o exporta datos sensibles (Nomina, Recibo_Nomina, facturación, reportes financieros o Registro_Auditoria), THE Servicio_Auditoria SHALL crear un Registro_Auditoria con el actor, la acción de lectura o exportación, el recurso consultado, el tenant_id y la marca temporal en tiempo universal coordinado (UTC).
7. THE Servicio_Auditoria SHALL proteger la integridad e inalterabilidad de cada Registro_Auditoria mediante un mecanismo de verificación de integridad (por ejemplo, encadenamiento o hash de cada entrada con la anterior) que permita detectar cualquier manipulación posterior y sustente el no repudio.
8. THE Sistema SHALL conservar los Registro_Auditoria durante un periodo de retención configurable no menor al exigido por la normativa aplicable.
9. WHEN un Administrador con Permiso lo solicita, THE API SHALL permitir exportar los Registro_Auditoria para fines de cumplimiento, aplicando el filtrado por actor, tipo de recurso y rango de fechas.
10. THE Servicio_Auditoria SHALL registrar, para cada Registro_Auditoria de modificación, el valor anterior y el valor nuevo de los campos relevantes cuando aplique, sin incluir secretos.
11. THE Sistema SHALL permitir a un Administrador con Permiso configurar alertas ante patrones sensibles de auditoría (por ejemplo, múltiples accesos denegados, exportaciones masivas de datos sensibles o intentos de acceso a otra Empresa) y notificar cuando se detecten.
12. THE Sistema SHALL registrar el identificador de correlación de la petición (trace id) en el Registro_Auditoria para permitir la trazabilidad de extremo a extremo.
13. WHEN se consulta la auditoría, THE Sistema SHALL permitir verificar la integridad de la cadena de hash y reportar si detecta alguna ruptura.

### Requisito 11: Gestión de secretos

**Historia de Usuario:** Como Administrador de despliegue, quiero que los secretos se gestionen fuera del código, para evitar la exposición de credenciales en el repositorio o en los artefactos.

#### Criterios de Aceptación

1. THE Sistema SHALL obtener los secretos de configuración (credenciales de base de datos, claves de firma de tokens) desde variables de entorno o un almacén de configuración externo al código fuente.
2. IF un secreto requerido no está disponible al iniciar, THEN THE Sistema SHALL detener el arranque y registrar el secreto faltante sin exponer su valor.
3. THE Sistema SHALL evitar registrar valores de secretos en los registros de aplicación y de auditoría.

### Requisito 12: Eficiencia de la API y separación de datos

**Historia de Usuario:** Como consumidor de la API y responsable de rendimiento, quiero listados paginados, contratos estables y catálogos en caché, para mantener respuestas eficientes y desacopladas del modelo interno.

#### Criterios de Aceptación

1. WHEN la API devuelve una colección de recursos, THE API SHALL entregarla paginada e incluir metadatos de paginación (página actual, tamaño de página y total de elementos).
2. THE API SHALL exponer y recibir datos mediante DTO distintos de las entidades de persistencia internas.
3. WHERE un recurso corresponde a un Catalogo de cambio lento, THE Sistema SHALL servir sus respuestas desde una caché.
4. THE API SHALL exponer todos sus recursos bajo la ruta base versionada `/api/v1`.

### Requisito 13: Documentación de la API

**Historia de Usuario:** Como desarrollador integrador, quiero documentación navegable y actualizada de la API, para consumir los endpoints sin ambigüedad.

#### Criterios de Aceptación

1. THE API SHALL publicar una especificación OpenAPI que describa sus recursos, operaciones y esquemas de DTO.
2. THE Sistema SHALL exponer una interfaz de documentación navegable (Swagger UI) para la especificación OpenAPI.

### Requisito 14: Gestión de oportunidades y pipeline de ventas

**Historia de Usuario:** Como Usuario del área de ventas, quiero registrar y dar seguimiento a Oportunidades a lo largo de un pipeline, para conducir cada prospecto hasta su cierre y convertir las Oportunidades ganadas en Cotizaciones.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso registra una Oportunidad asociada a un Cliente existente con los datos obligatorios (título entre 1 y 200 caracteres y valor estimado entre 0.01 y 999,999,999.99 en la moneda única del sistema), THE Sistema SHALL persistir la Oportunidad, asignarle un identificador único y establecer su etapa inicial en "nuevo".
2. WHEN un Usuario con Permiso asigna una Oportunidad a un Usuario del área de ventas, THE Sistema SHALL registrar al Usuario asignado como responsable de esa Oportunidad.
3. WHEN un Usuario con Permiso cambia la etapa de una Oportunidad, THE Sistema SHALL permitir únicamente las transiciones definidas: de "nuevo" a "calificado", de "calificado" a "propuesta", de "propuesta" a "negociación", de "negociación" a "ganado", de "negociación" a "perdido", y desde cualquier etapa no final a "perdido"; y THE Sistema SHALL considerar "ganado" y "perdido" como etapas finales que no admiten ninguna transición posterior.
4. IF un Usuario intenta una transición de etapa no incluida en las transiciones permitidas de una Oportunidad, incluida cualquier transición que parta de una etapa final, THEN THE Sistema SHALL rechazar el cambio, conservar la etapa actual sin modificarla e informar un mensaje que indique la transición inválida.
5. WHEN un Usuario con Permiso convierte una Oportunidad en etapa "ganado" en una Cotizacion, THE Sistema SHALL crear una Cotizacion vinculada al mismo Cliente y a la Oportunidad de origen, y devolver el identificador de la Cotizacion creada.
6. IF un Usuario intenta convertir en Cotizacion una Oportunidad cuya etapa no es "ganado", THEN THE Sistema SHALL rechazar la conversión, no crear ninguna Cotizacion e informar un mensaje que indique que se requiere una Oportunidad en etapa "ganado".
7. WHEN un Usuario con Permiso consulta el listado de Oportunidades, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página.
8. THE API SHALL permitir filtrar el listado de Oportunidades por Cliente, por etapa y por Usuario responsable.
9. WHEN la etapa de una Oportunidad cambia, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la etapa anterior, la etapa nueva y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 15: Aprobación de diseño

**Historia de Usuario:** Como Usuario del área de diseño, quiero generar Pruebas de Diseño versionadas a partir de una Cotizacion y gestionar su aprobación por el Cliente, para asegurar que solo se fabrique un arte aprobado.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso genera una Prueba_Diseno a partir de una Cotizacion existente, THE Sistema SHALL crear una Prueba_Diseno vinculada a esa Cotizacion, asignarle el número de versión 1 y establecer su estado en "pendiente".
2. WHEN un Cliente, o un Usuario en su representación, aprueba una Prueba_Diseno en estado "pendiente", THE Sistema SHALL registrar el estado de la Prueba_Diseno como "aprobada", junto con el actor y la marca temporal en tiempo universal coordinado (UTC).
3. WHEN un Cliente, o un Usuario en su representación, rechaza una Prueba_Diseno en estado "pendiente", THE Sistema SHALL registrar el estado de la Prueba_Diseno como "rechazada" y generar una nueva Prueba_Diseno con el número de versión incrementado en 1 y estado "pendiente".
4. THE Sistema SHALL conservar el historial completo de versiones de las Prueba_Diseno de una Cotizacion sin permitir su modificación ni su eliminación por parte de los Usuarios.
5. IF un Usuario intenta generar una Orden_Fabricacion para una Cotizacion que no tiene al menos una Prueba_Diseno en estado "aprobada", THEN THE Sistema SHALL rechazar la generación, no crear ninguna Orden_Fabricacion e informar un mensaje que indique que se requiere una Prueba_Diseno aprobada.
6. WHEN un Usuario con Permiso consulta el listado de Prueba_Diseno de una Cotizacion, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página.
7. WHEN el estado de una Prueba_Diseno cambia, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la versión, el estado anterior, el estado nuevo y la marca temporal.

### Requisito 16: Levantamiento en sitio

**Historia de Usuario:** Como Usuario del área de instalación, quiero registrar el Levantamiento_Sitio de cada Sitio, para contar con las condiciones físicas y eléctricas necesarias antes de fabricar e instalar.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso registra un Levantamiento_Sitio con los datos obligatorios (mediciones, tipo de superficie o estructura y condiciones eléctricas), THE Sistema SHALL persistir el Levantamiento_Sitio, asignarle un identificador único y establecer su estado en "en_proceso".
2. WHEN un Usuario con Permiso vincula un Levantamiento_Sitio a un Sitio, a una Cotizacion o a una Orden_Fabricacion existentes, THE Sistema SHALL registrar la asociación correspondiente.
3. WHEN un Usuario con Permiso adjunta fotografías a un Levantamiento_Sitio, THE Sistema SHALL conservar las fotografías vinculadas a ese Levantamiento_Sitio.
4. WHEN un Usuario con Permiso marca un Levantamiento_Sitio como "completado", THE Sistema SHALL registrar el estado "completado" junto con el actor y la marca temporal en tiempo universal coordinado (UTC).
5. IF un Usuario intenta programar la instalación de un Sitio cuyo Levantamiento_Sitio no está en estado "completado", THEN THE Sistema SHALL rechazar la programación e informar un mensaje que indique que se requiere un Levantamiento_Sitio completado.
6. WHEN un Usuario con Permiso consulta el listado de Levantamiento_Sitio, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página.
7. WHEN un Levantamiento_Sitio se crea o cambia de estado, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal.

### Requisito 17: Permisos y zonificación

**Historia de Usuario:** Como Usuario del área de instalación, quiero registrar y dar seguimiento a los Permiso_Instalacion de cada Sitio, para instalar únicamente donde exista la autorización requerida y anticipar vencimientos.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso registra un Permiso_Instalacion asociado a un Sitio existente con los datos obligatorios (tipo "municipal" o "arrendador" y fecha de vencimiento), THE Sistema SHALL persistir el Permiso_Instalacion, asignarle un identificador único y establecer su estado en "solicitado".
2. WHEN un Usuario con Permiso cambia el estado de un Permiso_Instalacion, THE Sistema SHALL permitir únicamente las transiciones definidas: de "solicitado" a "aprobado" y de "solicitado" a "rechazado".
3. IF un Usuario intenta una transición de estado no incluida en las transiciones permitidas de un Permiso_Instalacion, THEN THE Sistema SHALL rechazar el cambio, conservar el estado actual sin modificarlo e informar un mensaje que indique la transición inválida.
4. IF un Usuario intenta programar la instalación de un Sitio que tiene un Permiso_Instalacion requerido cuyo estado no es "aprobado", THEN THE Sistema SHALL rechazar la programación e informar un mensaje que indique que se requiere un Permiso_Instalacion aprobado.
5. WHEN la fecha actual se encuentra dentro de los 30 días previos a la fecha de vencimiento de un Permiso_Instalacion en estado "aprobado", THE Sistema SHALL notificar el vencimiento próximo al Usuario responsable del Sitio.
6. WHEN un Usuario con Permiso consulta el listado de Permiso_Instalacion, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Sitio, tipo y estado.
7. WHEN un Permiso_Instalacion se crea o cambia de estado, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal.

### Requisito 18: Gestión de inventario de materiales

**Historia de Usuario:** Como Usuario del área de producción, quiero administrar el inventario de Materiales y sus movimientos, para conocer las existencias disponibles y evitar faltantes durante la fabricación.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso da de alta un Material con los datos obligatorios (nombre entre 1 y 200 caracteres, unidad de medida y stock mínimo mayor o igual a 0), THE Sistema SHALL persistir el Material, asignarle un identificador único y establecer sus existencias iniciales en 0.
2. WHEN un Usuario con Permiso registra un Movimiento_Inventario de tipo "entrada", "salida" o "ajuste" sobre un Material existente, THE Sistema SHALL actualizar las existencias del Material conforme al tipo y la cantidad del movimiento.
3. IF un Usuario intenta registrar un Movimiento_Inventario de tipo "salida" cuya cantidad dejaría las existencias del Material por debajo de 0, THEN THE Sistema SHALL rechazar el movimiento, conservar las existencias sin modificarlas e informar un mensaje que indique existencias insuficientes.
4. WHEN un Usuario con Permiso consume Materiales asociados a una Orden_Fabricacion, THE Sistema SHALL registrar un Movimiento_Inventario de tipo "salida" por cada Material consumido y descontar la cantidad correspondiente de sus existencias.
5. WHEN las existencias de un Material quedan por debajo de su stock mínimo tras un Movimiento_Inventario, THE Sistema SHALL notificar la condición de stock bajo al Usuario responsable del inventario.
6. WHEN un Usuario con Permiso consulta el listado de Materiales, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por nombre y por condición de stock bajo.
7. WHEN un Material se da de alta o se registra un Movimiento_Inventario, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal.

### Requisito 19: Programación e instalación

**Historia de Usuario:** Como Usuario del área de instalación, quiero crear y ejecutar Órdenes de Trabajo de Instalación a partir de Órdenes de Fabricación terminadas, para coordinar a las Cuadrillas y cerrar la instalación sin pendientes.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso crea una Orden_Trabajo_Instalacion a partir de una Orden_Fabricacion en estado "terminada", asignando una Cuadrilla y una fecha programada, THE Sistema SHALL crear la Orden_Trabajo_Instalacion vinculada a esa Orden_Fabricacion, asignarle un identificador único y establecer su estado en "programada".
2. IF un Usuario intenta crear una Orden_Trabajo_Instalacion a partir de una Orden_Fabricacion cuyo estado no es "terminada", THEN THE Sistema SHALL rechazar la creación, no crear ninguna Orden_Trabajo_Instalacion e informar un mensaje que indique que se requiere una Orden_Fabricacion terminada.
3. IF un Usuario intenta programar una Orden_Trabajo_Instalacion para un Sitio cuyo Levantamiento_Sitio no está "completado" o cuyo Permiso_Instalacion requerido no está "aprobado", THEN THE Sistema SHALL rechazar la programación e informar un mensaje que indique el requisito no cumplido.
4. WHEN un Usuario con Permiso registra avance de una Orden_Trabajo_Instalacion, THE Sistema SHALL conservar la evidencia fotográfica y las entradas de la Lista_Pendientes asociadas a esa Orden_Trabajo_Instalacion.
5. WHEN un Usuario con Permiso cambia el estado de una Orden_Trabajo_Instalacion, THE Sistema SHALL permitir únicamente las transiciones definidas: de "programada" a "en_curso", de "en_curso" a "completada", de "programada" a "cancelada" y de "en_curso" a "cancelada"; y THE Sistema SHALL considerar "completada" y "cancelada" como estados finales que no admiten ninguna transición posterior.
6. IF un Usuario intenta cambiar el estado de una Orden_Trabajo_Instalacion a "completada" mientras su Lista_Pendientes contiene al menos un elemento sin resolver, THEN THE Sistema SHALL rechazar el cambio, conservar el estado actual sin modificarlo e informar un mensaje que indique que existen pendientes por resolver.
7. WHEN un Usuario con Permiso consulta el listado de Órdenes de Trabajo de Instalación, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por estado, Cuadrilla y Cliente.
8. WHEN una Orden_Trabajo_Instalacion se crea o cambia de estado, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, el estado anterior, el estado nuevo y la marca temporal.

### Requisito 20: Mantenimiento y servicio post-venta

**Historia de Usuario:** Como Usuario del área de mantenimiento, quiero administrar Contratos de Mantenimiento y Tickets de Servicio con seguimiento de SLA, para atender a los Clientes de forma oportuna y controlar las garantías.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso registra un Contrato_Mantenimiento asociado a un Cliente existente con los datos obligatorios (tipo "preventivo" o "correctivo", tiempo de respuesta del SLA en horas y tiempo de resolución del SLA en horas), THE Sistema SHALL persistir el Contrato_Mantenimiento y asignarle un identificador único.
2. WHEN un Usuario con Permiso genera un Ticket_Servicio, de forma manual o de forma automática por el mantenimiento preventivo programado de un Contrato_Mantenimiento, THE Sistema SHALL crear el Ticket_Servicio, asignarle un identificador único y establecer su estado en "abierto".
3. WHEN un Usuario con Permiso asigna un Ticket_Servicio a un técnico o a una Cuadrilla, THE Sistema SHALL registrar la asignación en el Ticket_Servicio.
4. WHEN un Usuario con Permiso cambia el estado de un Ticket_Servicio, THE Sistema SHALL permitir únicamente las transiciones definidas: de "abierto" a "asignado", de "asignado" a "en_proceso", de "en_proceso" a "resuelto" y de "resuelto" a "cerrado".
5. IF un Usuario intenta una transición de estado no incluida en las transiciones permitidas de un Ticket_Servicio, THEN THE Sistema SHALL rechazar el cambio, conservar el estado actual sin modificarlo e informar un mensaje que indique la transición inválida.
6. WHEN un Ticket_Servicio cambia a estado "resuelto", THE Sistema SHALL registrar el cumplimiento o el incumplimiento del SLA comparando el tiempo transcurrido desde la apertura con los tiempos de respuesta y resolución del Contrato_Mantenimiento asociado.
7. WHEN un Usuario con Permiso consulta el listado de Tickets de Servicio, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por estado, por Cliente y por vencimiento de SLA.
8. WHEN un Ticket_Servicio se crea o cambia de estado, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, el estado anterior, el estado nuevo y la marca temporal.

### Requisito 21: Proyectos multi-sitio

**Historia de Usuario:** Como Usuario del área de ventas, quiero agrupar varios Sitio de un mismo Cliente en un Proyecto, para coordinar despliegues de gran escala y consultar el avance consolidado.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso crea un Proyecto asociado a un Cliente existente con un nombre entre 1 y 200 caracteres, THE Sistema SHALL persistir el Proyecto y asignarle un identificador único.
2. WHEN un Usuario con Permiso agrega un Sitio a un Proyecto existente, THE Sistema SHALL persistir el Sitio vinculado a ese Proyecto.
3. THE Sistema SHALL mantener para cada Sitio su avance individual en las fases de Levantamiento_Sitio, Permiso_Instalacion, Orden_Fabricacion y Orden_Trabajo_Instalacion.
4. WHEN un Usuario con Permiso consulta un Proyecto, THE Sistema SHALL devolver el estado consolidado del Proyecto derivado del avance de sus Sitio.
5. WHEN un Usuario con Permiso consulta el listado de Proyectos, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Cliente.
6. WHEN un Proyecto o un Sitio se crea o se modifica, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal.

### Requisito 22: Reportes y tablero de indicadores

**Historia de Usuario:** Como Usuario responsable de la operación, quiero consultar un Tablero con indicadores por área y exportar reportes, para tomar decisiones basadas en datos consolidados.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso consulta el Tablero, THE Sistema SHALL presentar indicadores por área: comercial (pipeline de Oportunidades y Cotizaciones), producción (Órdenes de Fabricación por estado), instalación (cumplimiento de fechas programadas), mantenimiento (cumplimiento de SLA), inventario (Materiales por debajo de su stock mínimo), compras (Órdenes de Compra por estado y Facturas de Proveedor con discrepancia), finanzas/facturación (facturación del periodo, Cuenta_Por_Cobrar vencidas e IVA del periodo), recursos humanos/nómina (costo de nómina del periodo), tesorería (saldos bancarios y partidas en conciliación pendientes), cuentas por pagar (Cuenta_Por_Pagar vencidas), planeación estratégica (avance de Objetivos_Estrategicos), inventario avanzado (existencias por Almacen y valuación del inventario), presupuesto (variación presupuestal) y redes sociales (mensajes por Canal_Social, tiempo de respuesta y leads generados conforme al Requisito 66); adicionalmente, THE Tablero SHALL poder presentar indicadores analíticos consolidados de Inteligencia_Negocio conforme al Requisito 48.
2. THE Sistema SHALL calcular los indicadores del Tablero y los reportes como agregaciones de solo lectura, sin modificar los datos de origen.
3. WHEN un Usuario con Permiso solicita un reporte, THE API SHALL permitir filtrar los resultados por rango de fechas y por Cliente.
4. WHEN un Usuario con Permiso exporta un reporte, THE Sistema SHALL generar el reporte exportable con los datos correspondientes a los filtros aplicados.
5. IF un Usuario solicita un reporte de un área para la que su Rol carece de Permiso, THEN THE Servicio_Autorizacion SHALL rechazar la solicitud con un código de estado 403 conforme al Requisito 3, sin devolver dato alguno del reporte.
6. WHEN un Usuario consulta el Tablero o exporta un reporte, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso consultado y la marca temporal.

### Requisito 23: Aislamiento de datos multi-empresa (multi-tenant)

**Historia de Usuario:** Como Empresa que renta el Sistema, quiero que mis datos permanezcan aislados de los de otras Empresas, para preservar la confidencialidad y la integridad de mi información de negocio.

#### Criterios de Aceptación

1. THE Sistema SHALL asociar cada registro de negocio (Cliente, Contacto, Oportunidad, Cotizacion, Orden_Fabricacion, Prueba_Diseno, Levantamiento_Sitio, Permiso_Instalacion, Material, Movimiento_Inventario, Orden_Trabajo_Instalacion, Cuadrilla, Contrato_Mantenimiento, Ticket_Servicio, Proyecto, Sitio, Proveedor, Requisicion_Compra, Orden_Compra, Recepcion_Mercancia, Factura_Proveedor, Factura (CFDI), Complemento_Pago, Nota_Credito, Pago_Cliente, Cuenta_Contable, Poliza_Contable, Cuenta_Por_Cobrar, Empleado, Contrato_Laboral, Incidencia, Nomina, Recibo_Nomina, Cuenta_Bancaria, Estado_Cuenta_Bancario, Movimiento_Bancario, Cuenta_Por_Pagar, Programacion_Pago, Activo_Fijo, Objetivo_Estrategico, Producto, Lista_Precios, Almacen, Lote, Puesto, Evaluacion_Desempeno, Presupuesto, Cuenta_Canal_Social, Conversacion, Mensaje_Social, Plantilla_Mensaje, Publicacion_Social, Campaña_Publicitaria, Usuario y Rol) a exactamente una Empresa identificada por su tenant_id.
2. WHEN un Usuario autenticado que no sea Super_Administrador realiza cualquier operación de la API, THE Sistema SHALL restringir el acceso exclusivamente a los datos cuyo tenant_id coincide con la Empresa del Usuario.
3. IF un Usuario intenta acceder, modificar o referenciar un recurso cuyo tenant_id no coincide con su Empresa, THEN THE Sistema SHALL rechazar la operación con un código de estado 404 para no revelar la existencia del recurso y registrar el intento en auditoría.
4. THE Sistema SHALL derivar el tenant_id del contexto de autenticación del Usuario y NO aceptar el tenant_id como parámetro manipulable de la petición.
5. THE Servicio_Autorizacion SHALL evaluar los Permisos siempre dentro del ámbito de la Empresa del Usuario.
6. THE Sistema SHALL garantizar que los identificadores de unicidad de negocio (por ejemplo, el identificador fiscal de un Cliente) sean únicos dentro de una Empresa y no de forma global.

### Requisito 24: Administración de plataforma y gestión de empresas (tenants)

**Historia de Usuario:** Como Super_Administrador de la plataforma, quiero administrar las Empresas que rentan el Sistema, para controlar su alta, su estado y su suscripción sin acceder a sus datos de negocio.

#### Criterios de Aceptación

1. WHERE un Usuario tiene el rol `super_admin`, THE Sistema SHALL permitir crear, activar, suspender y consultar Empresas.
2. WHEN un Super_Administrador crea una Empresa con datos válidos (nombre, identificador fiscal y Plan inicial), THE Sistema SHALL crear la Empresa con un tenant_id único y crear al menos un Usuario con rol `admin_empresa` para esa Empresa.
3. THE Super_Administrador SHALL NOT tener acceso a los datos de negocio (Clientes, Cotizaciones y demás) de las Empresas, salvo métricas agregadas de operación de la plataforma.
4. WHEN un Super_Administrador suspende una Empresa, THE Sistema SHALL impedir el inicio de sesión de los Usuarios de esa Empresa mientras dure la suspensión.
5. WHEN un Super_Administrador consulta el listado de Empresas, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por estado.
6. WHEN un Super_Administrador ejecuta una operación de plataforma sobre una Empresa, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, la Empresa afectada y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 25: Planes y suscripciones

**Historia de Usuario:** Como Super_Administrador de la plataforma, quiero definir Planes y gestionar la Suscripcion de cada Empresa, para controlar los límites y las capacidades contratadas por cada Empresa.

#### Criterios de Aceptación

1. WHEN un Super_Administrador define un Plan con límites (número máximo de Usuarios y módulos habilitados), THE Sistema SHALL persistir el Plan y asignarle un identificador único.
2. WHEN un Super_Administrador asocia una Suscripcion entre una Empresa y un Plan, THE Sistema SHALL registrar la Suscripcion con su estado (activa, suspendida o cancelada) y su periodo de vigencia.
3. IF una Empresa alcanza el límite de Usuarios de su Plan, THEN THE Sistema SHALL rechazar la creación de nuevos Usuarios e informar un mensaje que indique el límite alcanzado.
4. WHERE un módulo no está habilitado en el Plan de la Empresa, THE Servicio_Autorizacion SHALL denegar el acceso a las operaciones de ese módulo con un código de estado 403.
5. WHEN un Plan o una Suscripcion se crea o se modifica, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 26: Personalización por empresa (branding)

**Historia de Usuario:** Como Administrador_Empresa, quiero personalizar el nombre visible y el logotipo de mi Empresa, para que la interfaz refleje la identidad de mi Empresa.

#### Criterios de Aceptación

1. WHEN un Administrador_Empresa configura el nombre visible y el logotipo de su Empresa, THE Sistema SHALL conservar dichos datos de personalización asociados a esa Empresa.
2. THE Sistema SHALL aplicar en la interfaz la personalización (nombre visible y logotipo) correspondiente a la Empresa del Usuario.
3. WHEN un Administrador_Empresa modifica la personalización de su Empresa, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 27: Modelo de roles y responsabilidades

**Historia de Usuario:** Como Administrador_Empresa, quiero un modelo de roles claro con ámbitos bien definidos, para asignar a cada Usuario únicamente las capacidades que su función requiere.

#### Criterios de Aceptación

1. THE Sistema SHALL soportar el rol de nivel plataforma `super_admin` y los roles de nivel empresa `admin_empresa`, `gerente`, `supervisor`, `ventas`, `diseño`, `producción`, `almacén`, `instalación`, `mantenimiento`, `contabilidad`, `rh` y `marketing`.
2. WHERE un Usuario tiene el rol `ventas`, THE Servicio_Autorizacion SHALL permitir la gestión de Clientes, Oportunidades, Cotizaciones y Proyectos dentro de su Empresa, así como la atención de la Bandeja_Unificada y las Conversacion de los Canal_Social dentro de su Empresa.
3. WHERE un Usuario tiene el rol `diseño`, THE Servicio_Autorizacion SHALL permitir la gestión de Prueba_Diseno dentro de su Empresa.
4. WHERE un Usuario tiene el rol `producción`, THE Servicio_Autorizacion SHALL permitir la gestión de Orden_Fabricacion dentro de su Empresa.
5. WHERE un Usuario tiene el rol `almacén`, THE Servicio_Autorizacion SHALL permitir la gestión de Material, Movimiento_Inventario, Proveedor, Requisicion_Compra, Orden_Compra, Recepcion_Mercancia, Factura_Proveedor, Producto, Lista_Precios, Almacen y Lote, así como la consulta del Kardex, dentro de su Empresa.
6. WHERE un Usuario tiene el rol `instalación`, THE Servicio_Autorizacion SHALL permitir la gestión de Levantamiento_Sitio, Permiso_Instalacion, Orden_Trabajo_Instalacion y Cuadrilla dentro de su Empresa.
7. WHERE un Usuario tiene el rol `mantenimiento`, THE Servicio_Autorizacion SHALL permitir la gestión de Contrato_Mantenimiento y Ticket_Servicio dentro de su Empresa.
8. WHERE un Usuario tiene el rol `gerente`, THE Servicio_Autorizacion SHALL permitir el acceso de lectura a todos los módulos de su Empresa, la consulta de todos los reportes y Tablero de todas las áreas de su Empresa, y las aprobaciones transversales que se le asignen (por ejemplo, aprobar Cotizaciones), sin permitir la gestión de Usuarios ni Roles.
9. WHERE un Usuario tiene el rol `supervisor`, THE Servicio_Autorizacion SHALL permitir el acceso de solo lectura dentro del área que se le asigne, además de las aprobaciones que se le deleguen, con un alcance más limitado que el del rol `gerente`, cuyo alcance abarca todas las áreas de su Empresa.
10. WHERE un Usuario tiene el rol `admin_empresa`, THE Servicio_Autorizacion SHALL permitir la gestión de Usuarios, Roles y configuración dentro de su propia Empresa.
11. WHERE un Usuario tiene el rol `contabilidad`, THE Servicio_Autorizacion SHALL permitir la gestión de Factura (CFDI), Complemento_Pago, Nota_Credito, Pago_Cliente, Cuenta_Contable, Poliza_Contable, Cuenta_Por_Pagar, Programacion_Pago, Cuenta_Bancaria, Conciliacion_Bancaria, Activo_Fijo, Depreciacion, Estado_Financiero y la consulta de reportes financieros dentro de su Empresa.
12. WHERE un Usuario tiene el rol `rh`, THE Servicio_Autorizacion SHALL permitir la gestión de Empleado, Contrato_Laboral, Incidencia, Nomina, Puesto, el organigrama y Evaluacion_Desempeno dentro de su Empresa.
13. WHERE un Usuario tiene el rol `cliente_portal`, THE Servicio_Autorizacion SHALL restringir su acceso exclusivamente al ámbito del Portal_Cliente conforme al Requisito 45, tratándolo como un rol externo limitado a la información de su propia relación comercial, sin acceso a operaciones internas de la Empresa.
14. WHERE un Usuario tiene el rol `gerente` o el rol `admin_empresa`, THE Servicio_Autorizacion SHALL permitir la gestión de la planeación estratégica (misión, visión y valores y Objetivos_Estrategicos) y de los Presupuestos dentro de su Empresa.
15. WHERE un Usuario tiene el rol `marketing`, THE Servicio_Autorizacion SHALL permitir la gestión de Cuenta_Canal_Social, Publicacion_Social y Campaña_Publicitaria, la atención de la Bandeja_Unificada y las Conversacion, y la consulta de la analítica de redes sociales dentro de su Empresa.
16. THE modelo de roles SHALL respetar el privilegio mínimo y la denegación por defecto establecidos en el Requisito 3, y todos los roles de empresa SHALL operar únicamente dentro del tenant_id de su Empresa conforme al Requisito 23.

### Requisito 28: Roles personalizables por empresa

**Historia de Usuario:** Como Administrador_Empresa, quiero definir Roles personalizados combinando Permisos existentes, para adaptar el control de acceso a la estructura de mi Empresa, ya sea pequeña, mediana o grande.

#### Criterios de Aceptación

1. THE Sistema SHALL proveer un conjunto de Permisos atómicos, cada uno correspondiente a una operación sobre un tipo de recurso.
2. WHEN un Administrador_Empresa crea un Rol_Personalizado seleccionando un conjunto de Permisos existentes, THE Sistema SHALL persistir el Rol_Personalizado dentro del ámbito de su Empresa (tenant_id) y permitir asignarlo a Usuarios de esa Empresa.
3. THE Sistema SHALL evaluar los Rol_Personalizado con las mismas reglas de privilegio mínimo y denegación por defecto del Requisito 3.
4. WHERE una Empresa requiere una estructura reducida, THE Sistema SHALL permitir asignar a un mismo Usuario un Rol que combine Permisos de varias áreas mediante un Rol_Personalizado.
5. IF un Administrador_Empresa intenta incluir en un Rol_Personalizado un Permiso que no existe o que corresponde a operaciones de nivel plataforma (super_admin), THEN THE Sistema SHALL rechazar la creación e informar el Permiso inválido.
6. THE Sistema SHALL impedir la eliminación o modificación de los roles predefinidos del Sistema, permitiendo únicamente gestionar los Rol_Personalizado.
7. WHEN un Rol_Personalizado se crea, modifica o elimina, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 29: Gestión de proveedores

**Historia de Usuario:** Como Usuario del área de almacén, quiero registrar y consultar Proveedores, para gestionar de forma centralizada a quienes suministran los Materiales que consume la producción.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso da de alta un Proveedor proporcionando los datos obligatorios (nombre o razón social entre 1 y 200 caracteres, identificador fiscal, y al menos un dato de contacto consistente en un correo electrónico válido o un teléfono de 10 a 15 dígitos), THE Sistema SHALL persistir el Proveedor y asignarle un identificador único dentro de su Empresa.
2. IF un Usuario intenta dar de alta un Proveedor omitiendo alguno de los datos obligatorios, THEN THE Sistema SHALL rechazar el alta, no persistir ningún dato e informar un mensaje que indique el campo inválido o faltante.
3. IF un Usuario intenta dar de alta un Proveedor con un identificador fiscal ya existente en un Proveedor activo dentro de su Empresa, THEN THE Sistema SHALL rechazar el alta, conservar sin cambios el Proveedor existente e informar un mensaje que indique el identificador fiscal duplicado.
4. WHEN un Usuario con Permiso actualiza los datos de un Proveedor, THE Sistema SHALL persistir los cambios y THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).
5. WHEN un Usuario con Permiso da de baja un Proveedor que se encuentra activo, THE Sistema SHALL marcar el Proveedor como inactivo conservando sus datos históricos y THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).
6. WHEN un Usuario con Permiso consulta el listado de Proveedores sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página.
7. WHEN un Usuario con Permiso consulta el listado de Proveedores indicando un criterio de filtro por nombre o por identificador fiscal, THE API SHALL devolver únicamente los Proveedores cuyo nombre o identificador fiscal contenga la cadena indicada, sin distinguir mayúsculas de minúsculas.
8. WHEN un Proveedor se da de alta o se modifica, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 30: Requisiciones de compra

**Historia de Usuario:** Como Usuario del área de almacén, quiero crear y dar seguimiento a Requisiciones de Compra, para solicitar y aprobar internamente la compra de Materiales antes de emitir una Orden de Compra.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso crea una Requisicion_Compra con al menos un Material y una cantidad entre 1 y 999,999 por Material, THE Sistema SHALL persistir la Requisicion_Compra, asignarle un identificador único y establecer su estado inicial en "borrador".
2. IF un Usuario intenta crear una Requisicion_Compra sin al menos un Material o con una cantidad fuera del rango de 1 a 999,999, THEN THE Sistema SHALL rechazar la creación, no persistir ningún dato e informar un mensaje que indique la causa de la validación fallida.
3. WHEN un Usuario con Permiso cambia el estado de una Requisicion_Compra, THE Sistema SHALL permitir únicamente las transiciones definidas: de "borrador" a "enviada", de "enviada" a "aprobada" y de "enviada" a "rechazada"; y THE Sistema SHALL considerar "aprobada", "rechazada" y "cancelada" como estados finales que no admiten ninguna transición posterior.
4. IF un Usuario intenta una transición de estado no incluida en las transiciones permitidas de una Requisicion_Compra, incluida cualquier transición que parta de un estado final, THEN THE Sistema SHALL rechazar el cambio, conservar el estado actual sin modificarlo e informar un mensaje que indique la transición inválida.
5. WHEN un Usuario con Permiso genera una Orden_Compra a partir de una Requisicion_Compra en estado "aprobada", THE Sistema SHALL crear la Orden_Compra vinculada a esa Requisicion_Compra y devolver una confirmación que incluya el identificador de la Orden_Compra.
6. IF un Usuario intenta generar una Orden_Compra a partir de una Requisicion_Compra cuyo estado no es "aprobada", THEN THE Sistema SHALL rechazar la generación, no crear ninguna Orden_Compra e informar un mensaje que indique que se requiere una Requisicion_Compra aprobada.
7. WHEN un Usuario con Permiso consulta el listado de Requisiciones de Compra sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por estado.
8. WHEN el estado de una Requisicion_Compra cambia, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, el estado anterior, el estado nuevo y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 31: Órdenes de compra

**Historia de Usuario:** Como Usuario del área de almacén, quiero emitir y dar seguimiento a Órdenes de Compra a un Proveedor, para formalizar la adquisición de Materiales con cantidades y precios acordados.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso crea una Orden_Compra asociada a un Proveedor existente con al menos una y como máximo 500 Partida_Orden_Compra, cada una con una cantidad entre 1 y 999,999 y un precio unitario entre 0.01 y 999,999,999.99 en la moneda única del sistema, THE Sistema SHALL persistir la Orden_Compra, asignarle un identificador único e inmutable y establecer su estado inicial en "abierta".
2. IF un Usuario intenta crear una Orden_Compra sin un Proveedor asociado o sin al menos una Partida_Orden_Compra, THEN THE Sistema SHALL rechazar la creación, no persistir ningún dato e informar un mensaje que indique la causa de la validación fallida.
3. WHEN un Usuario agrega una Partida_Orden_Compra con una cantidad entre 1 y 999,999 y un precio unitario entre 0.01 y 999,999,999.99, THE Sistema SHALL calcular el subtotal de la partida como el producto de la cantidad por el precio unitario, redondeado a 2 decimales mediante redondeo al valor más cercano.
4. IF un Usuario agrega una Partida_Orden_Compra con una cantidad fuera del rango de 1 a 999,999 o con un precio unitario fuera del rango de 0.01 a 999,999,999.99, THEN THE Sistema SHALL rechazar la partida, no calcular su subtotal e informar un mensaje que indique el valor inválido.
5. THE Sistema SHALL calcular el total de una Orden_Compra como la suma de los subtotales de sus Partida_Orden_Compra, expresado con 2 decimales en la moneda única del sistema mediante redondeo al valor más cercano.
6. WHEN un Usuario con Permiso cambia el estado de una Orden_Compra, THE Sistema SHALL permitir únicamente las transiciones definidas: de "abierta" a "recibida_parcial", de "recibida_parcial" a "recibida_total", de "recibida_total" a "cerrada", de "abierta" a "cancelada" y de "recibida_parcial" a "cancelada"; y THE Sistema SHALL considerar "cerrada" y "cancelada" como estados finales que no admiten ninguna transición posterior.
7. IF un Usuario intenta una transición de estado no incluida en las transiciones permitidas de una Orden_Compra, incluida cualquier transición que parta de un estado final, THEN THE Sistema SHALL rechazar el cambio, conservar el estado actual sin modificarlo e informar un mensaje que indique la transición inválida.
8. WHEN un Usuario con Permiso consulta el listado de Órdenes de Compra sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Proveedor y por estado.
9. WHEN una Orden_Compra se crea o cambia de estado, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, el estado anterior, el estado nuevo y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 32: Recepción de mercancía e integración con inventario

**Historia de Usuario:** Como Usuario del área de almacén, quiero registrar la recepción de la mercancía de una Orden de Compra, para actualizar automáticamente las existencias de inventario y controlar cuánto se ha recibido de cada partida.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso registra una Recepcion_Mercancia contra una Orden_Compra en estado "abierta" o "recibida_parcial", indicando las cantidades recibidas por Partida_Orden_Compra, THE Sistema SHALL persistir la Recepcion_Mercancia y asignarle un identificador único.
2. IF un Usuario intenta registrar una Recepcion_Mercancia contra una Orden_Compra cuyo estado no es "abierta" ni "recibida_parcial", THEN THE Sistema SHALL rechazar la recepción, no persistir ningún dato e informar un mensaje que indique que la Orden_Compra no admite recepciones.
3. IF la cantidad recibida acumulada de una Partida_Orden_Compra excede la cantidad ordenada en esa partida, THEN THE Sistema SHALL rechazar la Recepcion_Mercancia, conservar sin cambios las existencias e informar un mensaje que indique la cantidad en exceso.
4. WHEN un Usuario con Permiso registra una Recepcion_Mercancia, THE Sistema SHALL generar un Movimiento_Inventario de tipo "entrada" por cada Material recibido y actualizar sus existencias conforme a la cantidad recibida, en integración con el Requisito 18.
5. WHEN todas las Partida_Orden_Compra de una Orden_Compra quedan totalmente recibidas, THE Sistema SHALL cambiar el estado de la Orden_Compra a "recibida_total".
6. WHEN una Recepcion_Mercancia registra una cantidad recibida parcial respecto a la cantidad ordenada de la Orden_Compra, THE Sistema SHALL cambiar el estado de la Orden_Compra a "recibida_parcial".
7. WHEN un Usuario con Permiso consulta el listado de Recepciones de Mercancía sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Orden_Compra.
8. WHEN una Recepcion_Mercancia se registra, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 33: Facturas de proveedor y conciliación de tres vías

**Historia de Usuario:** Como Usuario del área de almacén, quiero registrar las Facturas de Proveedor y conciliarlas contra la Orden de Compra y la mercancía recibida, para autorizar el pago únicamente cuando coinciden la cantidad y el precio.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso registra una Factura_Proveedor asociada a una Orden_Compra existente, proporcionando el monto y el identificador de factura del Proveedor, THE Sistema SHALL persistir la Factura_Proveedor, asignarle un identificador único y establecer su estado inicial en "registrada".
2. IF un Usuario intenta registrar una Factura_Proveedor sin una Orden_Compra asociada, sin monto o sin identificador de factura del Proveedor, THEN THE Sistema SHALL rechazar el registro, no persistir ningún dato e informar un mensaje que indique la causa de la validación fallida.
3. WHEN un Usuario con Permiso solicita autorizar el pago de una Factura_Proveedor, THE Sistema SHALL ejecutar la Conciliacion_Tres_Vias verificando, por cada Partida_Orden_Compra, que la cantidad facturada no exceda la cantidad recibida en las Recepcion_Mercancia asociadas y que el precio facturado coincida con el precio de la Orden_Compra dentro de una tolerancia configurable.
4. IF la Conciliacion_Tres_Vias detecta una discrepancia de cantidad o de precio fuera de la tolerancia configurable, THEN THE Sistema SHALL marcar la Factura_Proveedor como "discrepancia", NO autorizar el pago e informar un mensaje que indique la discrepancia detectada.
5. WHEN la Conciliacion_Tres_Vias resulta satisfactoria, THE Sistema SHALL marcar la Factura_Proveedor como "conciliada" y habilitarla para pago.
6. WHEN un Usuario con Permiso cambia el estado de una Factura_Proveedor, THE Sistema SHALL permitir únicamente las transiciones definidas: de "registrada" a "conciliada", de "conciliada" a "pagada" y de "registrada" a "discrepancia".
7. IF un Usuario intenta autorizar el pago de una Factura_Proveedor cuyo estado no es "conciliada", THEN THE Sistema SHALL rechazar la autorización de pago, conservar el estado actual sin modificarlo e informar un mensaje que indique que se requiere una Factura_Proveedor conciliada.
8. WHEN un Usuario con Permiso consulta el listado de Facturas de Proveedor sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Proveedor, por Orden_Compra y por estado.
9. WHEN el estado de una Factura_Proveedor cambia, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, el estado anterior, el estado nuevo y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 34: Emisión de facturas electrónicas (CFDI 4.0)

**Historia de Usuario:** Como Usuario del área de contabilidad, quiero emitir Facturas (CFDI 4.0) a partir de una Cotizacion aprobada o de una Orden_Fabricacion, para documentar fiscalmente las ventas de la Empresa.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso genera una Factura a partir de una Cotizacion en estado "aprobada" o de una Orden_Fabricacion, proporcionando los datos fiscales obligatorios del receptor (RFC, nombre, código postal y régimen fiscal) y el uso de CFDI, THE Sistema SHALL crear la Factura vinculada a esa Cotizacion u Orden_Fabricacion, asignarle un identificador único dentro de su Empresa y establecer su estado inicial en "borrador".
2. WHEN un Usuario con Permiso genera una Factura, THE Sistema SHALL calcular el IVA con la tasa del 16% sobre el subtotal y, WHERE la operación está sujeta a Retencion, THE Sistema SHALL calcular la Retencion sobre el subtotal, y THE Sistema SHALL calcular el total como el subtotal más el IVA menos las Retenciones, expresado con 2 decimales en la moneda única del sistema mediante redondeo al valor más cercano.
3. IF faltan datos fiscales obligatorios del receptor (RFC, nombre, código postal o régimen fiscal) o alguno de dichos datos tiene un formato inválido, THEN THE Sistema SHALL rechazar la emisión, no crear la Factura e informar un mensaje que indique el dato inválido o faltante.
4. WHEN un Usuario con Permiso consulta el listado de Facturas sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Cliente y por estado.
5. WHEN una Factura se crea o se modifica, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 35: Timbrado y cancelación de CFDI ante el PAC

**Historia de Usuario:** Como Usuario del área de contabilidad, quiero timbrar y cancelar las Facturas ante el PAC conforme a la normativa del SAT, para que los CFDI tengan validez fiscal y su ciclo de vida quede debidamente controlado.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso solicita timbrar una Factura en estado "borrador" válida, THE Sistema SHALL enviarla al PAC para su Timbrado y, al recibir respuesta exitosa, registrar el Folio_Fiscal (UUID) devuelto y cambiar el estado de la Factura a "timbrada".
2. IF el PAC rechaza el Timbrado, THEN THE Sistema SHALL conservar la Factura en estado "borrador", no asignar Folio_Fiscal e informar el motivo de rechazo devuelto por el PAC.
3. THE Sistema SHALL impedir modificar los datos fiscales de una Factura una vez que se encuentra en estado "timbrada".
4. WHEN un Usuario con Permiso solicita cancelar una Factura en estado "timbrada", THE Sistema SHALL requerir un motivo de cancelación conforme a los catálogos del SAT y solicitar la cancelación al PAC.
5. IF la normativa exige la aceptación del receptor para la cancelación, THEN THE Sistema SHALL registrar el estado "cancelacion_en_proceso" hasta contar con la aceptación del receptor o el vencimiento del plazo correspondiente, y solo entonces cambiar el estado de la Factura a "cancelada".
6. THE Sistema SHALL conservar de forma inmutable el CFDI timbrado y su Folio_Fiscal aun después de la cancelación, preservándolo como histórico.
7. WHEN un Usuario con Permiso cambia el estado de una Factura, THE Sistema SHALL permitir únicamente las transiciones definidas: de "borrador" a "timbrada", de "timbrada" a "cancelacion_en_proceso" y de "cancelacion_en_proceso" a "cancelada"; y THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, el estado anterior, el estado nuevo y la marca temporal en tiempo universal coordinado (UTC).
8. THE integración con el PAC SHALL realizarse a través de un puerto/adaptador desacoplado que preserve la portabilidad, y THE Sistema SHALL obtener las credenciales del PAC conforme a la gestión de secretos del Requisito 11, sin exponerlas en el código ni en los registros.

### Requisito 36: Cuentas por cobrar y pagos de clientes

**Historia de Usuario:** Como Usuario del área de contabilidad, quiero controlar las Cuenta_Por_Cobrar y registrar los Pago_Cliente, para dar seguimiento a la cobranza de las Facturas emitidas.

#### Criterios de Aceptación

1. WHEN una Factura queda en estado "timbrada", THE Sistema SHALL registrar una Cuenta_Por_Cobrar por el saldo equivalente al total de la Factura.
2. WHEN un Usuario con Permiso registra un Pago_Cliente y lo aplica a una o varias Facturas, THE Sistema SHALL disminuir el saldo de las Cuenta_Por_Cobrar correspondientes por el monto aplicado a cada Factura.
3. IF el monto aplicado de un Pago_Cliente a una Factura excede el saldo pendiente de esa Factura, THEN THE Sistema SHALL rechazar la aplicación, conservar sin cambios los saldos e informar un mensaje que indique el excedente.
4. WHEN un Pago_Cliente corresponde a un pago en parcialidades o a un pago diferido, THE Sistema SHALL generar un Complemento_Pago (CFDI de tipo pago) y timbrarlo a través del PAC conforme al Requisito 35.
5. WHEN un Usuario con Permiso consulta la antigüedad de saldos (aging), THE Sistema SHALL presentar los saldos de Cuenta_Por_Cobrar por Cliente clasificados por rango de días de vencimiento.
6. WHEN un Usuario con Permiso consulta el listado de Pago_Cliente o de Cuenta_Por_Cobrar sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Cliente y por estado.
7. WHEN un Pago_Cliente se registra o se aplica, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 37: Notas de crédito

**Historia de Usuario:** Como Usuario del área de contabilidad, quiero emitir Notas de Crédito referenciando Facturas timbradas, para documentar devoluciones, descuentos o correcciones sobre ventas ya facturadas.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso emite una Nota_Credito referenciando una Factura en estado "timbrada", THE Sistema SHALL generar un CFDI de tipo egreso vinculado a esa Factura, timbrarlo a través del PAC conforme al Requisito 35 y disminuir la Cuenta_Por_Cobrar asociada por el monto de la Nota_Credito.
2. IF el monto de la Nota_Credito excede el saldo pendiente o el total de la Factura referenciada, THEN THE Sistema SHALL rechazar la emisión, conservar sin cambios la Factura y su Cuenta_Por_Cobrar e informar un mensaje que indique el excedente.
3. THE Sistema SHALL conservar de forma inmutable el CFDI de egreso y su Folio_Fiscal como histórico.
4. WHEN una Nota_Credito se emite, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 38: Contabilidad — catálogo de cuentas y pólizas

**Historia de Usuario:** Como Usuario del área de contabilidad, quiero mantener el catálogo de Cuenta_Contable y registrar Poliza_Contable balanceadas, para reflejar contablemente las operaciones de la Empresa con integridad.

#### Criterios de Aceptación

1. THE Sistema SHALL permitir a la Empresa mantener un catálogo de Cuenta_Contable dentro de su tenant_id.
2. WHEN ocurre una operación contable relevante (Factura timbrada, Pago_Cliente aplicado, Nota_Credito, Factura_Proveedor conciliada o pagada, o Movimiento_Inventario), THE Sistema SHALL generar una Poliza_Contable con sus cargos y abonos correspondientes.
3. THE Sistema SHALL asegurar que cada Poliza_Contable esté balanceada, de modo que la suma de los cargos sea igual a la suma de los abonos.
4. IF una Poliza_Contable no está balanceada, THEN THE Sistema SHALL rechazar su registro, no persistir la Poliza_Contable e informar un mensaje que indique la diferencia entre cargos y abonos.
5. THE Sistema SHALL conservar las Poliza_Contable sin permitir su modificación ni su eliminación, admitiendo únicamente pólizas de corrección o reverso, para preservar la integridad contable.
6. WHEN un Usuario con Permiso consulta el listado de Poliza_Contable sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por rango de fechas y por Cuenta_Contable.
7. WHEN una Cuenta_Contable o una Poliza_Contable se crea, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 39: Reportes financieros y fiscales

**Historia de Usuario:** Como Usuario del área de contabilidad, quiero consultar reportes financieros y fiscales, para dar seguimiento a los ingresos, los impuestos y la cobranza de la Empresa.

#### Criterios de Aceptación

1. WHERE un Usuario tiene permiso del área contable (por ejemplo, el rol `contabilidad`, el rol `admin_empresa` o el rol `gerente`), THE Sistema SHALL permitir consultar reportes financieros: estado de cuenta por Cliente, ingresos por periodo, IVA trasladado y retenido por periodo, antigüedad de saldos, y libro de Poliza_Contable.
2. THE Sistema SHALL calcular los reportes financieros como agregaciones de solo lectura, sin modificar los datos de origen.
3. THE API SHALL permitir filtrar los reportes financieros por rango de fechas y por Cliente, y permitir su exportación.
4. IF un Usuario sin permiso del área contable solicita un reporte financiero, THEN THE Servicio_Autorizacion SHALL rechazar la solicitud con un código de estado 403 conforme al Requisito 3, sin devolver dato alguno del reporte.
5. WHEN un Usuario con Permiso consulta o exporta un reporte financiero, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso consultado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 40: Gestión de recursos humanos (empleados y contratos)

**Historia de Usuario:** Como Usuario del área de recursos humanos, quiero registrar y administrar Empleados, sus Contrato_Laboral e Incidencias, para mantener actualizada la información laboral y fiscal necesaria para la nómina.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso da de alta un Empleado proporcionando los datos obligatorios (nombre, RFC, CURP, NSS del IMSS y fecha de ingreso) junto con su Contrato_Laboral (tipo, salario diario y periodicidad), THE Sistema SHALL persistir el Empleado y su Contrato_Laboral y asignarles un identificador único dentro de su Empresa.
2. IF un Usuario intenta dar de alta un Empleado o su Contrato_Laboral omitiendo alguno de los datos obligatorios o con un formato inválido de RFC, CURP o NSS del IMSS, THEN THE Sistema SHALL rechazar el alta, no persistir ningún dato e informar un mensaje que indique el campo inválido o faltante.
3. WHEN un Usuario con Permiso registra una Incidencia para un Empleado en un Periodo_Nomina indicando su tipo (asistencia, falta, permiso, incapacidad o tiempo extra), THE Sistema SHALL persistir la Incidencia vinculada a ese Empleado y a ese Periodo_Nomina.
4. WHEN un Usuario con Permiso da de baja un Empleado que se encuentra activo, THE Sistema SHALL marcar el Empleado como inactivo conservando su información histórica y la de sus Contrato_Laboral e Incidencia.
5. WHEN un Usuario con Permiso consulta el listado de Empleados sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página.
6. WHEN un Usuario con Permiso consulta el listado de Empleados indicando un criterio de filtro por nombre o por estado, THE API SHALL devolver únicamente los Empleados que coincidan con el criterio indicado, sin distinguir mayúsculas de minúsculas.
7. WHEN un Empleado, un Contrato_Laboral o una Incidencia se crea, modifica o da de baja, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 41: Cálculo y timbrado de nómina

**Historia de Usuario:** Como Usuario del área de recursos humanos, quiero calcular y timbrar la Nomina de un Periodo_Nomina, para pagar a los Empleados y cumplir con las obligaciones fiscales del SAT.

#### Criterios de Aceptación

1. WHEN se procesa una Nomina para un Periodo_Nomina, THE Sistema SHALL calcular por Empleado las percepciones (salario y, cuando aplique, tiempo extra, aguinaldo y PTU), las deducciones (ISR, IMSS e Infonavit) y el subsidio al empleo cuando corresponda, y determinar el neto a pagar.
2. THE Sistema SHALL calcular el ISR conforme a las tablas vigentes y las cuotas del IMSS e Infonavit conforme a la normativa aplicable.
3. IF faltan datos fiscales del Empleado necesarios para el cálculo de la Nomina, THEN THE Sistema SHALL rechazar el cálculo para ese Empleado, no determinar su neto a pagar e informar un mensaje que indique el dato fiscal faltante.
4. WHEN la Nomina se autoriza, THE Sistema SHALL generar un Recibo_Nomina por Empleado y timbrarlo como CFDI de nómina a través del PAC conforme al Requisito 35.
5. WHEN un Usuario con Permiso cambia el estado de una Nomina, THE Sistema SHALL permitir únicamente las transiciones definidas: de "borrador" a "calculada", de "calculada" a "autorizada", de "autorizada" a "timbrada" y de "timbrada" a "pagada".
6. IF un Usuario intenta una transición de estado no incluida en las transiciones permitidas de una Nomina, THEN THE Sistema SHALL rechazar el cambio, conservar el estado actual sin modificarlo e informar un mensaje que indique la transición inválida.
7. THE Sistema SHALL conservar de forma inmutable los Recibo_Nomina timbrados y sus Folio_Fiscal como histórico.
8. WHEN una Nomina cambia de estado o se timbra un Recibo_Nomina, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 42: Cuentas por pagar y programación de pagos

**Historia de Usuario:** Como Usuario del área de contabilidad, quiero gestionar las Cuenta_Por_Pagar y programar los pagos a Proveedores, para controlar los compromisos de pago de la Empresa.

#### Criterios de Aceptación

1. WHEN una Factura_Proveedor queda "conciliada", THE Sistema SHALL registrar una Cuenta_Por_Pagar por el saldo de esa Factura_Proveedor.
2. WHEN un Usuario con Permiso crea una Programacion_Pago, THE Sistema SHALL registrar la fecha programada y el monto por Cuenta_Por_Pagar.
3. WHEN un pago se ejecuta y se aplica a una Cuenta_Por_Pagar, THE Sistema SHALL disminuir el saldo de la Cuenta_Por_Pagar por el monto aplicado y, cuando el saldo llegue a 0, marcar la Factura_Proveedor asociada como "pagada" conforme al Requisito 33.
4. IF el monto de un pago excede el saldo de la Cuenta_Por_Pagar, THEN THE Sistema SHALL rechazar el pago, conservar sin cambios la Cuenta_Por_Pagar e informar un mensaje que indique el excedente.
5. WHEN un Usuario con Permiso consulta la antigüedad de saldos por Proveedor, THE API SHALL devolver las Cuenta_Por_Pagar agrupadas por Proveedor con su saldo pendiente.
6. WHEN un Usuario con Permiso consulta el listado de Cuenta_Por_Pagar o de Programacion_Pago sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Proveedor y por estado.
7. WHEN una Cuenta_Por_Pagar o una Programacion_Pago se crea, modifica o aplica, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 43: Tesorería y conciliación bancaria

**Historia de Usuario:** Como Usuario del área de contabilidad, quiero administrar las Cuenta_Bancaria de la Empresa y conciliar sus movimientos, para verificar que el saldo bancario coincide con el saldo contable.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso da de alta una Cuenta_Bancaria proporcionando los datos obligatorios, THE Sistema SHALL persistir la Cuenta_Bancaria y asignarle un identificador único dentro de su Empresa.
2. WHEN un Usuario con Permiso importa un Estado_Cuenta_Bancario de una Cuenta_Bancaria, THE Sistema SHALL persistir el Estado_Cuenta_Bancario con sus Movimiento_Bancario.
3. WHEN se ejecuta la Conciliacion_Bancaria, THE Sistema SHALL emparejar automáticamente cada Movimiento_Bancario con una Poliza_Contable o un Pago cuando coincidan el monto, la fecha dentro de una tolerancia configurable y la referencia.
4. WHEN existen Movimiento_Bancario sin coincidencia, THE Sistema SHALL marcarlos como excepciones para revisión manual.
5. THE Sistema SHALL calcular la diferencia entre el saldo bancario y el saldo contable y considerar la Conciliacion_Bancaria completa únicamente cuando dicha diferencia sea 0 una vez explicadas las partidas.
6. WHEN un Usuario con Permiso consulta el listado de Movimiento_Bancario o de conciliaciones sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Cuenta_Bancaria, por periodo y por estado de conciliación.
7. WHEN un Estado_Cuenta_Bancario se importa o una Conciliacion_Bancaria se ejecuta, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 44: Activos fijos y depreciación

**Historia de Usuario:** Como Usuario del área de contabilidad, quiero registrar los Activo_Fijo de la Empresa y su Depreciacion, para reflejar contablemente su pérdida de valor a lo largo del tiempo.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso da de alta un Activo_Fijo proporcionando los datos obligatorios (costo, fecha de adquisición, vida útil y método de depreciación), THE Sistema SHALL persistir el Activo_Fijo y asignarle un identificador único dentro de su Empresa.
2. IF un Usuario intenta dar de alta un Activo_Fijo omitiendo alguno de los datos obligatorios o con un valor inválido, THEN THE Sistema SHALL rechazar el alta, no persistir ningún dato e informar un mensaje que indique el campo inválido o faltante.
3. WHEN corre el periodo de depreciación, THE Sistema SHALL calcular y registrar la Depreciacion del periodo por Activo_Fijo y generar la Poliza_Contable correspondiente conforme al Requisito 38.
4. WHEN un Usuario con Permiso registra la baja o venta de un Activo_Fijo, THE Sistema SHALL marcar el Activo_Fijo como dado de baja conservando su información histórica.
5. WHEN un Usuario con Permiso consulta el listado de Activo_Fijo sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por estado.
6. WHEN un Activo_Fijo se da de alta, se deprecia o se da de baja, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 45: Portal de autoservicio del cliente

**Historia de Usuario:** Como Cliente de la Empresa, quiero acceder a un portal de autoservicio, para consultar y aprobar la información relacionada con mis propios proyectos.

#### Criterios de Aceptación

1. WHERE un Cliente accede al Portal_Cliente con credenciales propias de alcance restringido, THE Sistema SHALL permitirle consultar únicamente la información de su propia relación comercial: sus Cotizaciones, sus Prueba_Diseno, el avance de sus Proyectos y Sitios, sus Ticket_Servicio y sus Facturas.
2. WHERE un Cliente accede al Portal_Cliente, THE Sistema SHALL permitirle aprobar o rechazar sus propias Prueba_Diseno.
3. THE Sistema SHALL impedir que un Cliente acceda a datos de otros Clientes o a operaciones internas de la Empresa.
4. THE acceso del Cliente al Portal_Cliente SHALL respetar el aislamiento por tenant_id conforme al Requisito 23.
5. WHEN un Cliente ejecuta una acción en el Portal_Cliente, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC).
6. WHEN un Cliente consulta un listado en el Portal_Cliente sin especificar tamaño de página, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página.

### Requisito 46: Notificaciones (correo electrónico y WhatsApp)

**Historia de Usuario:** Como Usuario responsable de la operación, quiero que el Sistema envíe Notificaciones ante eventos relevantes, para que los destinatarios se enteren oportunamente por correo electrónico o WhatsApp.

#### Criterios de Aceptación

1. WHEN ocurre un evento relevante (Prueba_Diseno enviada para aprobación, Permiso_Instalacion próximo a vencer, Ticket_Servicio próximo a incumplir su SLA, Factura timbrada o Nomina timbrada), THE Sistema SHALL generar una Notificacion al destinatario correspondiente por correo electrónico o WhatsApp.
2. THE integración con los proveedores de correo electrónico y de WhatsApp SHALL realizarse mediante un puerto/adaptador desacoplado, obteniendo sus credenciales conforme a la gestión de secretos del Requisito 11.
3. IF el envío de una Notificacion falla, THEN THE Sistema SHALL reintentar el envío conforme a una política de reintentos configurable y registrar el resultado de cada intento.
4. THE Sistema SHALL limitar el contenido de cada Notificacion a la información necesaria, sin exponer datos sensibles innecesarios.
5. WHEN una Notificacion se envía, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor o el evento origen, el destinatario, el canal, el resultado del envío y la marca temporal en tiempo universal coordinado (UTC).
6. WHERE una Notificacion se dirige a un Canal_Social (WhatsApp, Facebook Messenger o Instagram), THE Sistema SHALL enviarla reutilizando la integración del Requisito 64, respetando la Ventana_Servicio y empleando una Plantilla_Mensaje aprobada cuando el envío ocurra fuera de dicha ventana.
7. IF el destinatario no cuenta con un Opt_In vigente para el Canal_Social correspondiente, THEN THE Sistema SHALL abstenerse de enviar la Notificacion de marketing por ese Canal_Social y registrar el motivo de la omisión.

### Requisito 47: Estados financieros

**Historia de Usuario:** Como Usuario del área de contabilidad, quiero generar los Estado_Financiero de un periodo, para conocer la situación financiera y los resultados de la Empresa.

#### Criterios de Aceptación

1. WHERE un Usuario con permiso contable lo solicita, THE Sistema SHALL generar los Estado_Financiero: balance general, estado de resultados y balanza de comprobación, derivados de las Poliza_Contable de un periodo.
2. THE Sistema SHALL calcular los Estado_Financiero como agregaciones de solo lectura, sin modificar los datos de origen.
3. THE balance general SHALL cumplir la ecuación contable, de modo que el activo sea igual a la suma del pasivo y el capital.
4. THE API SHALL permitir filtrar los Estado_Financiero por periodo y exportarlos.
5. IF un Usuario sin permiso contable solicita un Estado_Financiero, THEN THE Servicio_Autorizacion SHALL rechazar la solicitud con un código de estado 403 conforme al Requisito 3, sin devolver dato alguno.
6. WHEN un Usuario con Permiso consulta o exporta un Estado_Financiero, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso consultado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 48: Inteligencia de negocio (Business Intelligence avanzado)

**Historia de Usuario:** Como Usuario con permiso analítico responsable de la dirección, quiero generar análisis avanzados que consoliden datos de todas las áreas, para tomar decisiones basadas en indicadores, tendencias y comparativos.

#### Criterios de Aceptación

1. WHERE un Usuario con permiso analítico lo solicita, THE Sistema SHALL generar análisis avanzados (Inteligencia_Negocio) que consoliden datos de las distintas áreas (comercial, producción, instalación, mantenimiento, inventario, compras, finanzas, recursos humanos y tesorería) en indicadores, tendencias históricas y comparativos por periodo.
2. THE Sistema SHALL calcular los análisis de Inteligencia_Negocio como agregaciones de solo lectura, sin modificar los datos de origen.
3. THE Sistema SHALL permitir al Usuario definir y guardar tableros analíticos personalizados combinando métricas de distintas áreas dentro de su Empresa.
4. THE API SHALL permitir filtrar los análisis por rango de fechas, por área y por dimensiones de negocio (por ejemplo, Cliente, Proyecto o periodo), y exportar los resultados.
5. THE Sistema SHALL respetar el aislamiento por tenant_id conforme al Requisito 23, de modo que los análisis solo incluyan datos de la Empresa del Usuario.
6. IF un Usuario sin permiso analítico solicita un análisis de Inteligencia_Negocio, THEN THE Servicio_Autorizacion SHALL rechazar la solicitud con un código de estado 403 conforme al Requisito 3.
7. WHEN un Usuario consulta o exporta un análisis de Inteligencia_Negocio, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso consultado y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 49: Control de concurrencia

**Historia de Usuario:** Como Usuario que edita información compartida, quiero que el Sistema evite que dos ediciones simultáneas se sobrescriban entre sí, para preservar la integridad de los datos de negocio.

#### Criterios de Aceptación

1. WHEN dos Usuarios editan el mismo registro de forma concurrente, THE Sistema SHALL aplicar un control de concurrencia optimista basado en una versión del registro.
2. IF un Usuario intenta guardar cambios sobre un registro cuya versión ya fue modificada por otra operación, THEN THE Sistema SHALL rechazar la actualización con un código de estado 409, no sobrescribir los cambios existentes e informar que el registro fue modificado por otro Usuario.
3. THE Sistema SHALL aplicar el control de concurrencia a los registros de negocio modificables del Sistema.

### Requisito 50: Respaldo y recuperación de datos

**Historia de Usuario:** Como Administrador responsable de la continuidad del negocio, quiero que el Sistema respalde y permita restaurar los datos, para recuperar la operación ante una pérdida o desastre.

#### Criterios de Aceptación

1. THE Sistema SHALL respaldar periódicamente los datos de negocio (incluidos los datos fiscales y contables) con una frecuencia configurable.
2. THE Sistema SHALL permitir restaurar los datos a partir de un respaldo para recuperación ante desastres, cumpliendo un objetivo de punto de recuperación (RPO) y un objetivo de tiempo de recuperación (RTO) configurables.
3. THE Sistema SHALL conservar los respaldos de forma cifrada y con acceso restringido.
4. WHEN se ejecuta un respaldo o una restauración, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el alcance y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 51: Rendimiento y disponibilidad

**Historia de Usuario:** Como Usuario del Sistema, quiero que el Sistema responda con rapidez y se mantenga disponible bajo la carga esperada, para trabajar de forma fluida sin interrupciones.

#### Criterios de Aceptación

1. WHEN el Sistema opera bajo la carga concurrente esperada, THE API SHALL responder al menos el 95% de las peticiones de lectura en 2 segundos o menos y al menos el 95% de las peticiones de escritura en 4 segundos o menos, medido en el servidor.
2. THE Sistema SHALL soportar una cantidad configurable de Usuarios concurrentes sin degradación funcional.
3. WHERE una operación es intensiva (por ejemplo, cálculo de Nomina, generación de Estado_Financiero o análisis de Inteligencia_Negocio), THE Sistema SHALL procesarla de forma que no bloquee la operación interactiva del resto de los Usuarios.
4. THE Sistema SHALL exponer un punto de verificación de estado (health check) para su monitoreo.

### Requisito 52: Interfaz responsiva (multi-dispositivo)

**Historia de Usuario:** Como Usuario del Sistema, quiero que la interfaz se adapte al dispositivo que utilice (escritorio, tablet o móvil), para trabajar con comodidad y sin perder funcionalidad en cualquier pantalla.

#### Criterios de Aceptación

1. THE interfaz del Sistema SHALL ser responsiva y adaptarse a escritorio, tablet y móvil mediante Punto_Quiebre definidos, sin pérdida de funcionalidad.
2. WHEN el ancho de la pantalla corresponde a un dispositivo móvil, THE interfaz SHALL reorganizar la navegación (por ejemplo, menú colapsable) y presentar las tablas de datos de forma legible (desplazamiento horizontal o vista de tarjetas) sin recortar información.
3. THE interfaz SHALL adoptar un enfoque de diseño mobile-first y mantener áreas táctiles con un tamaño mínimo accesible.
4. THE interfaz SHALL conservar la legibilidad y la operación en resoluciones desde 320 px de ancho en adelante.

### Requisito 53: Sistema de diseño e identidad visual profesional

**Historia de Usuario:** Como Usuario del Sistema, quiero una interfaz con una identidad visual profesional y consistente, para transmitir confianza y trabajar de forma cómoda y coherente en todos los módulos.

#### Criterios de Aceptación

1. THE Sistema SHALL basar su interfaz en un Sistema_Diseno con tokens de diseño (colores, tipografía, espaciados, radios y sombras) para garantizar consistencia visual en todos los módulos.
2. THE Sistema_Diseno SHALL emplear una paleta de colores profesional y sobria (base neutra, un color primario corporativo y colores semánticos moderados para éxito, advertencia, error e información), evitando colores estridentes.
3. THE Sistema SHALL aplicar la personalización de marca por Empresa (Requisito 26) sobre el Sistema_Diseno sin romper la armonía visual ni el contraste.
4. THE Sistema SHALL mantener una jerarquía tipográfica legible e iconografía uniforme en toda la interfaz.
5. WHERE el Usuario lo seleccione, THE Sistema SHALL ofrecer un modo claro y un modo oscuro que conserven el contraste y la legibilidad.

### Requisito 54: Modales de confirmación para acciones sensibles

**Historia de Usuario:** Como Usuario del Sistema, quiero confirmar de forma explícita las acciones sensibles o irreversibles, para evitar cambios accidentales sobre la información.

#### Criterios de Aceptación

1. WHEN un Usuario solicita una acción sensible o irreversible (por ejemplo, eliminar o desactivar un registro, cancelar una Cotizacion u Orden, timbrar o cancelar una Factura, autorizar un pago, procesar una Nomina), THE Sistema SHALL presentar un Modal_Confirmacion con una descripción clara de la acción y sus consecuencias, y con una acción primaria de confirmación y una acción secundaria de cancelación.
2. IF el Usuario cancela o cierra el Modal_Confirmacion, THEN THE Sistema SHALL no ejecutar la acción y conservar el estado sin cambios.
3. WHEN el Usuario confirma en el Modal_Confirmacion, THE Sistema SHALL ejecutar la acción solicitada y presentar una Notificacion_Interfaz con el resultado.

### Requisito 55: Animaciones, microinteracciones y estados de carga

**Historia de Usuario:** Como Usuario del Sistema, quiero animaciones sutiles e indicadores de carga claros, para percibir una interfaz fluida y saber cuándo el Sistema está procesando.

#### Criterios de Aceptación

1. THE interfaz SHALL emplear animaciones y microinteracciones sutiles y con propósito (transiciones de vistas, apertura de modales, estados de foco y hover) que no distraigan ni degraden el rendimiento.
2. WHILE una operación está en progreso, THE interfaz SHALL mostrar un estado de carga (por ejemplo, indicador de progreso o esqueleto de contenido) para informar al Usuario que el Sistema está procesando.
3. THE interfaz SHALL evitar cambios bruscos de disposición durante la carga de contenido.
4. WHERE el Usuario haya activado la preferencia de reducción de movimiento del sistema operativo o navegador, THE interfaz SHALL reducir o desactivar las animaciones no esenciales.

### Requisito 56: Retroalimentación al usuario y manejo de estados

**Historia de Usuario:** Como Usuario del Sistema, quiero recibir mensajes claros sobre el resultado de mis acciones y sobre el estado de la información, para entender qué ocurre y cómo continuar.

#### Criterios de Aceptación

1. WHEN una operación finaliza con éxito o con error, THE interfaz SHALL mostrar una Notificacion_Interfaz clara que indique el resultado.
2. IF una operación falla por validación, THEN THE interfaz SHALL mostrar los mensajes de error asociados a los campos correspondientes de forma clara y no técnica.
3. WHEN una vista o listado no contiene datos, THE interfaz SHALL presentar un estado vacío con una indicación de la acción sugerida.
4. THE interfaz SHALL presentar los mensajes de error de negocio provenientes de la API sin exponer detalles técnicos internos.

### Requisito 57: Accesibilidad de la interfaz (WCAG 2.1 AA)

**Historia de Usuario:** Como Usuario del Sistema, quiero una interfaz accesible conforme a estándares reconocidos, para poder operarla mediante teclado y con lectores de pantalla sin barreras.

#### Criterios de Aceptación

1. THE interfaz del Sistema SHALL cumplir con las pautas WCAG 2.1 en su nivel AA.
2. THE interfaz SHALL permitir la operación completa mediante teclado, con un indicador de foco visible en los elementos interactivos.
3. THE interfaz SHALL proporcionar el texto alternativo y las etiquetas necesarias para su uso con lectores de pantalla.
4. THE Sistema_Diseno SHALL garantizar una relación de contraste de color suficiente conforme a WCAG 2.1 AA para el texto y los elementos esenciales.

### Requisito 58: Planeación estratégica y objetivos

**Historia de Usuario:** Como gerente o Administrador_Empresa, quiero registrar la esencia de la Empresa y dar seguimiento a objetivos estratégicos medibles, para alinear la operación con la misión, la visión y los valores de la Empresa.

#### Criterios de Aceptación

1. THE Sistema SHALL permitir a la Empresa registrar su misión, visión y valores.
2. WHEN un Usuario con Permiso crea un Objetivo_Estrategico con nombre, responsable, periodo y meta medible, THE Sistema SHALL persistir el Objetivo_Estrategico y establecer su avance inicial en 0.
3. IF un Usuario intenta crear un Objetivo_Estrategico omitiendo el nombre, el responsable, el periodo o la meta medible, THEN THE Sistema SHALL rechazar la creación, no persistir ningún dato e informar un mensaje que indique el campo faltante.
4. WHEN un Usuario con Permiso actualiza el avance de un Objetivo_Estrategico, THE Sistema SHALL registrar el nuevo avance y conservar el historial de actualizaciones.
5. WHEN un Usuario con Permiso consulta el listado de Objetivos_Estrategicos, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página.
6. THE API SHALL permitir filtrar el listado de Objetivos_Estrategicos por periodo y por responsable.
7. WHEN un Objetivo_Estrategico o la misión, visión o valores de la Empresa se crea o se modifica, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal.
8. THE Sistema SHALL permitir asociar a cada Objetivo_Estrategico uno o varios resultados clave medibles (métricas con valor objetivo y valor actual), y calcular el avance del Objetivo_Estrategico como el porcentaje ponderado de cumplimiento de sus resultados clave, como agregación de solo lectura.
9. THE avance de un Objetivo_Estrategico SHALL expresarse entre 0% y 100% y nunca exceder el 100%.
10. WHEN un Usuario con Permiso consulta un Objetivo_Estrategico, THE Sistema SHALL mostrar su estado derivado (en riesgo, en curso o cumplido) a partir del avance y del periodo.

### Requisito 59: Catálogo de productos y listas de precios

**Historia de Usuario:** Como Usuario del área comercial o de almacén, quiero mantener el catálogo de Productos y sus listas de precios, para cotizar con precios vigentes y consistentes.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso da de alta un Producto con datos obligatorios (nombre entre 1 y 200 caracteres, unidad y descripción), THE Sistema SHALL persistir el Producto y asignarle un identificador único dentro de su Empresa.
2. IF un Usuario intenta dar de alta un Producto omitiendo el nombre, la unidad o la descripción, THEN THE Sistema SHALL rechazar el alta, no persistir ningún dato e informar un mensaje que indique el campo faltante.
3. WHEN un Usuario con Permiso define una Lista_Precios que asigna precios a Productos, THE Sistema SHALL persistir los precios con su vigencia, con cada precio entre 0.01 y 999,999,999.99 en la moneda única del sistema.
4. WHERE una Cotizacion agrega una Partida_Cotizacion referida a un Producto con una Lista_Precios vigente, THE Sistema SHALL sugerir el precio unitario correspondiente, permitiendo que el Usuario lo ajuste.
5. THE Sistema SHALL permitir registrar información comercial de apoyo del Producto (cliente meta, alianzas y competencia) como datos descriptivos.
6. WHEN un Usuario con Permiso elimina un Producto que se encuentra activo, THE Sistema SHALL marcar el Producto como inactivo conservando sus datos históricos.
7. WHEN un Usuario con Permiso consulta el listado de Productos, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por nombre.
8. WHEN un Producto o una Lista_Precios se crea, modifica o elimina, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal.
9. WHERE existen varias Lista_Precios vigentes aplicables a un Cliente, THE Sistema SHALL aplicar la de mayor prioridad o la específica del segmento del Cliente antes que la general.
10. IF un Usuario intenta definir un precio de Producto fuera del rango de 0.01 a 999,999,999.99, THEN THE Sistema SHALL rechazar el precio e informar el valor inválido.

### Requisito 60: Inventario avanzado (Kardex, máximos, lotes, costeo y almacenes)

**Historia de Usuario:** Como Usuario del área de almacén, quiero controlar existencias por almacén con kardex, máximos y mínimos, lotes y costeo, para mantener trazabilidad y una valuación precisa del inventario.

#### Criterios de Aceptación

1. THE Sistema SHALL permitir registrar Almacenes (sucursales o bodegas) de la Empresa y mantener existencias por Material o Producto y por Almacen.
2. THE Material SHALL admitir un stock mínimo y un stock máximo; WHEN las existencias superan el stock máximo o caen por debajo del stock mínimo tras un Movimiento_Inventario, THE Sistema SHALL notificar la condición correspondiente.
3. WHEN un Usuario con Permiso consulta el Kardex de un Material o Producto en un Almacen y un periodo, THE Sistema SHALL presentar cronológicamente sus entradas, salidas y saldos como una agregación de solo lectura, sin modificar los datos de origen.
4. WHERE el control por Lote está habilitado para un Material o Producto, THE Sistema SHALL registrar el Lote en cada Movimiento_Inventario y permitir su trazabilidad.
5. THE Sistema SHALL valuar el inventario conforme a un método de costeo configurable (por ejemplo, costo promedio o PEPS) y reflejar el costo en las salidas.
6. WHEN un Usuario con Permiso consulta el listado de existencias o de Movimiento_Inventario, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Almacen y por Material o Producto.
7. WHEN un Almacen se crea o se modifica, o cuando se registra un Movimiento_Inventario con Lote, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal.
8. THE Sistema SHALL integrar este control avanzado de inventario con el inventario de Materiales establecido en el Requisito 18.
9. THE Sistema SHALL calcular, por Material o Producto, un punto de reorden como el consumo promedio por el tiempo de entrega más el stock de seguridad, ambos configurables, y WHEN las existencias alcanzan o caen por debajo del punto de reorden, THE Sistema SHALL notificar la necesidad de reabastecimiento.
10. THE Sistema SHALL mantener un inventario perpetuo, actualizando el saldo y el costo tras cada Movimiento_Inventario.
11. WHERE el método de costeo configurado es promedio ponderado, THE Sistema SHALL recalcular el costo unitario promedio en cada entrada; WHERE el método es PEPS, THE Sistema SHALL consumir en las salidas el costo de las capas de inventario más antiguas primero.
12. THE Kardex SHALL reflejar, por cada movimiento, la cantidad, el costo unitario, el costo total y el saldo resultante.
13. WHEN un Usuario con Permiso transfiere existencias entre dos Almacenes, THE Sistema SHALL registrar una salida en el Almacen origen y una entrada en el Almacen destino por la misma cantidad, conservando el costo.

### Requisito 61: Organización de personal (organigrama, puestos y evaluación)

**Historia de Usuario:** Como Usuario del área de recursos humanos, quiero definir el organigrama, los puestos y las evaluaciones de desempeño, para estructurar la organización y dar seguimiento al desarrollo del personal.

#### Criterios de Aceptación

1. WHEN un Administrador_Empresa o un Usuario con el rol `rh` define Puestos y su jerarquía, THE Sistema SHALL conformar el organigrama de la Empresa.
2. WHEN un Usuario con Permiso asigna un Empleado a un Puesto, THE Sistema SHALL registrar la asignación y reflejarla en el organigrama.
3. WHEN un Usuario con Permiso registra una Evaluacion_Desempeno de un Empleado en un periodo, THE Sistema SHALL persistir la Evaluacion_Desempeno vinculada a ese Empleado y periodo, y conservar su historial.
4. WHEN un Usuario con Permiso consulta el listado de Puestos o de Evaluacion_Desempeno, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Empleado o por periodo.
5. WHEN un Puesto, una asignación de Empleado a Puesto o una Evaluacion_Desempeno se crea o se modifica, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal.
6. THE Sistema SHALL integrar esta organización de personal con la gestión de Empleado establecida en el Requisito 40.
7. IF un Usuario intenta crear un Puesto cuya jerarquía genera un ciclo en el organigrama (por ejemplo, un puesto que sea su propio superior directo o indirecto), THEN THE Sistema SHALL rechazar la operación e informar la jerarquía inválida.
8. THE Evaluacion_Desempeno SHALL registrar una calificación dentro de una escala definida y conservar los comentarios asociados.

### Requisito 62: Presupuestos y control de costos

**Historia de Usuario:** Como gerente o Administrador_Empresa, quiero definir presupuestos por área y periodo y compararlos con el ejercicio real, para controlar los costos e identificar desviaciones.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso crea un Presupuesto por área y periodo con montos estimados de ingresos y/o egresos, THE Sistema SHALL persistir el Presupuesto.
2. THE Sistema SHALL comparar el Presupuesto contra el ejercicio real derivado de las operaciones (por ejemplo, facturación, compras y nómina) y calcular la variación como una agregación de solo lectura, sin modificar los datos de origen.
3. WHERE la variación de un Presupuesto supera un umbral configurable, THE Sistema SHALL destacar la desviación en los reportes.
4. WHEN un Usuario con Permiso consulta el listado de Presupuestos, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por área y por periodo.
5. WHEN un Presupuesto se crea o se modifica, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal.
6. THE Sistema SHALL calcular la variación de un Presupuesto como el importe real menos el importe presupuestado por área y periodo, e indicar si la variación es favorable o desfavorable.
7. THE Sistema SHALL calcular la variación tanto en importe absoluto como en porcentaje respecto al presupuesto.
8. WHERE existe un Presupuesto vigente para un área y periodo, THE Sistema SHALL derivar el importe real de las operaciones registradas (por ejemplo, Facturas, Órdenes de Compra y Nómina) de esa área y periodo, como agregación de solo lectura.

### Requisito 63: Canales de venta y clasificación comercial

**Historia de Usuario:** Como Usuario del área comercial, quiero clasificar las operaciones por canal de venta, para analizar el desempeño comercial por canal.

#### Criterios de Aceptación

1. THE Sistema SHALL permitir clasificar Oportunidades y Cotizaciones por canal de venta (por ejemplo, directo, referido o en línea) para su análisis.
2. WHEN un Usuario con Permiso consulta los reportes comerciales, THE Sistema SHALL permitir segmentar los resultados por canal de venta, en integración con los Requisitos 14, 22 y 48.
3. WHEN el canal de venta de una Oportunidad o de una Cotizacion se asigna o se modifica, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal.

### Requisito 64: Mensajería omnicanal y bandeja unificada (WhatsApp, Facebook Messenger e Instagram)

**Historia de Usuario:** Como Usuario del área comercial o de atención al cliente, quiero atender por WhatsApp, Facebook Messenger e Instagram desde una única bandeja ligada al CRM, para responder de forma oportuna, dar seguimiento comercial y captar prospectos sin salir del Sistema.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso configura una Cuenta_Canal_Social para un Canal_Social (WhatsApp, Facebook Messenger o Instagram), THE Sistema SHALL persistir la Cuenta_Canal_Social vinculada al tenant_id de su Empresa y obtener sus credenciales de acceso conforme a la gestión de secretos del Requisito 11, sin exponerlas en el código ni en los registros.
2. THE integración con las APIs oficiales de Meta (WhatsApp Business Cloud API y Graph API de Facebook Messenger e Instagram) SHALL realizarse a través de un puerto/adaptador desacoplado que preserve la portabilidad del núcleo de negocio.
3. WHEN el Sistema recibe un evento entrante mediante webhook de un Canal_Social, THE Sistema SHALL exigir que la recepción ocurra sobre HTTPS con TLS conforme al Requisito 9, validar la autenticidad del evento y persistir el Mensaje_Social entrante asociado a su Conversacion.
4. WHEN se recibe un Mensaje_Social entrante, THE Sistema SHALL asociarlo a un Cliente o Contacto existente cuando el identificador del remitente coincida, y WHERE no exista coincidencia, THE Sistema SHALL crear un Contacto o una Oportunidad a partir del remitente.
5. THE Sistema SHALL presentar una Bandeja_Unificada que consolide las Conversacion de los tres Canal_Social en un único hilo por Cliente o Contacto, con historial y contexto compartidos, dentro del tenant_id de la Empresa.
6. WHEN un Usuario con Permiso envía un Mensaje_Social dentro de la Ventana_Servicio de una Conversacion, THE Sistema SHALL permitir el envío de contenido de texto libre.
7. IF un Usuario con Permiso intenta enviar un Mensaje_Social fuera de la Ventana_Servicio de una Conversacion, THEN THE Sistema SHALL exigir el uso de una Plantilla_Mensaje aprobada o de un mensaje etiquetado y rechazar el envío de texto libre e informar un mensaje que indique el requisito de plantilla.
8. IF un Usuario con Permiso intenta enviar un Mensaje_Social de marketing a un Cliente o Contacto sin un Opt_In vigente para ese Canal_Social, THEN THE Sistema SHALL rechazar el envío e informar un mensaje que indique la ausencia de consentimiento.
9. WHEN un Cliente o Contacto otorga o revoca su consentimiento, THE Sistema SHALL registrar el Opt_In o el Opt_Out con el Canal_Social, el actor y la marca temporal en tiempo universal coordinado (UTC).
10. WHEN un Usuario con Permiso asigna o transfiere (handover) una Conversacion a otro Usuario o agente, THE Sistema SHALL registrar la asignación y conservar el historial de la Conversacion.
11. WHERE un Canal_Social admite mensajes interactivos, THE Sistema SHALL permitir enviar Mensaje_Social con botones de respuesta o listas de opciones.
12. WHEN un prospecto llega por un anuncio o por click-to-WhatsApp, THE Sistema SHALL crear o vincular una Oportunidad conforme al Requisito 14 a partir de esa Conversacion.
13. IF el envío de un Mensaje_Social falla, THEN THE Sistema SHALL reintentar el envío conforme a una política de reintentos configurable y registrar el resultado de cada intento.
14. WHEN un Usuario con Permiso consulta el listado de Conversacion o de Mensaje_Social, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Canal_Social, por Cliente y por estado de la Conversacion.
15. WHEN un Mensaje_Social se envía o se recibe, o cuando el estado de una Conversacion cambia, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor o el evento origen, la acción, el Canal_Social, el recurso afectado, el tenant_id y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 65: Publicación de contenido y campañas en redes sociales

**Historia de Usuario:** Como Usuario del área comercial o de marketing, quiero programar y publicar contenido en las páginas y perfiles de la Empresa y gestionar campañas publicitarias básicas, para mantener presencia en redes sociales y promover los productos desde el Sistema.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso crea una Publicacion_Social para una Cuenta_Canal_Social de Facebook o Instagram con contenido válido y una fecha programada, THE Sistema SHALL persistir la Publicacion_Social, asignarle un identificador único dentro de su Empresa y establecer su estado inicial en "borrador".
2. IF un Usuario intenta crear una Publicacion_Social sin contenido o con una fecha programada anterior al momento actual, THEN THE Sistema SHALL rechazar la creación, no persistir ningún dato e informar un mensaje que indique el dato inválido.
3. WHEN un Usuario con Permiso cambia el estado de una Publicacion_Social, THE Sistema SHALL permitir únicamente las transiciones definidas: de "borrador" a "programada", de "programada" a "publicada" y de "programada" a "fallida"; y THE Sistema SHALL considerar "publicada" y "fallida" como estados finales que no admiten ninguna transición posterior.
4. IF un Usuario intenta una transición de estado no incluida en las transiciones permitidas de una Publicacion_Social, THEN THE Sistema SHALL rechazar el cambio, conservar el estado actual sin modificarlo e informar un mensaje que indique la transición inválida.
5. WHEN llega la fecha programada de una Publicacion_Social en estado "programada", THE Sistema SHALL publicarla en la página o perfil correspondiente a través del adaptador desacoplado del Requisito 64 y, al confirmar el resultado, cambiar su estado a "publicada" o a "fallida".
6. IF la publicación de una Publicacion_Social falla, THEN THE Sistema SHALL reintentar el envío conforme a una política de reintentos configurable y registrar el resultado de cada intento.
7. WHEN un Usuario con Permiso crea una Campaña_Publicitaria con un presupuesto entre 0.01 y 999,999,999.99 en la moneda única del sistema y un periodo con fecha de inicio y fin válidas, THE Sistema SHALL persistir la Campaña_Publicitaria y asignarle un identificador único dentro de su Empresa.
8. IF un Usuario intenta crear una Campaña_Publicitaria con un presupuesto fuera del rango de 0.01 a 999,999,999.99 o con una fecha de fin anterior a la fecha de inicio, THEN THE Sistema SHALL rechazar la creación, no persistir ningún dato e informar un mensaje que indique el dato inválido.
9. WHEN un Usuario con Permiso consulta el estado de una Campaña_Publicitaria, THE Sistema SHALL obtenerlo de la Marketing API de Meta a través del adaptador desacoplado y presentarlo como información de solo lectura.
10. WHEN un Usuario con Permiso consulta el listado de Publicacion_Social o de Campaña_Publicitaria, THE API SHALL devolver los resultados de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página, y permitir filtrar por Canal_Social y por estado.
11. WHEN una Publicacion_Social o una Campaña_Publicitaria se crea, se modifica o cambia de estado, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el Canal_Social, el recurso afectado, el tenant_id y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 66: Analítica de redes sociales e integración con inteligencia de negocio

**Historia de Usuario:** Como Usuario con permiso analítico o comercial, quiero consultar métricas de redes sociales por canal y periodo integradas al Tablero y a la Inteligencia_Negocio, para medir el desempeño de la atención y la promoción en cada Canal_Social.

#### Criterios de Aceptación

1. WHERE un Usuario con Permiso lo solicita, THE Sistema SHALL calcular métricas por Canal_Social y por periodo (alcance, interacciones, Mensaje_Social recibidos y enviados, tiempo de respuesta, y conversiones o leads generados) como agregaciones de solo lectura, sin modificar los datos de origen.
2. THE Sistema SHALL permitir segmentar las métricas de redes sociales por canal de venta en integración con el Requisito 63.
3. THE Sistema SHALL consolidar las métricas de redes sociales en el Tablero y en la Inteligencia_Negocio conforme a los Requisitos 22 y 48.
4. THE API SHALL permitir filtrar las métricas de redes sociales por rango de fechas y por Canal_Social, y permitir su exportación.
5. IF un Usuario sin permiso analítico o comercial solicita las métricas de redes sociales, THEN THE Servicio_Autorizacion SHALL rechazar la solicitud con un código de estado 403 conforme al Requisito 3, sin devolver dato alguno.
6. THE Sistema SHALL respetar el aislamiento por tenant_id conforme al Requisito 23, de modo que las métricas solo incluyan datos de la Empresa del Usuario.
7. WHEN un Usuario con Permiso consulta o exporta las métricas de redes sociales, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso consultado, el Canal_Social, el tenant_id y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 67: Cifrado de datos sensibles en reposo

**Historia de Usuario:** Como Administrador responsable de cumplimiento, quiero que los datos sensibles se almacenen cifrados en reposo, para proteger la información fiscal, financiera y personal ante accesos no autorizados al almacenamiento.

#### Criterios de Aceptación

1. THE Sistema SHALL cifrar en reposo los datos sensibles (por ejemplo, datos fiscales, financieros y personales de Clientes, Empleados y Proveedores, así como Factura (CFDI) y Recibo_Nomina) mediante Cifrado_En_Reposo a nivel de almacenamiento o de base de datos.
2. THE Sistema SHALL gestionar las Llave_Cifrado conforme a la gestión de secretos del Requisito 11, obteniéndolas desde variables de entorno o un almacén de configuración externo al código fuente y sin exponer su valor en los registros de aplicación ni de auditoría.
3. THE Sistema SHALL permitir la rotación de las Llave_Cifrado conservando la capacidad de descifrar los datos previamente cifrados.
4. WHERE se almacenan credenciales o tokens de integraciones externas (PAC, Canal_Social, proveedores de correo electrónico o bancos), THE Sistema SHALL conservarlas cifradas en reposo y nunca en texto claro.
5. THE Cifrado_En_Reposo SHALL complementar el cifrado en tránsito por TLS establecido en el Requisito 9 y el cifrado de los respaldos establecido en el Requisito 50, sin reemplazar a ninguno de ellos.
6. IF una Llave_Cifrado requerida no está disponible, THEN THE Sistema SHALL impedir el acceso a los datos cifrados afectados y registrar el evento sin exponer el valor de la Llave_Cifrado.
7. WHEN se ejecuta una operación de gestión de Llave_Cifrado (por ejemplo, alta, rotación o revocación), THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado y la marca temporal en tiempo universal coordinado (UTC), sin incluir el valor de la Llave_Cifrado.

### Requisito 68: Gestión y revocación de sesiones

**Historia de Usuario:** Como Administrador responsable de seguridad, quiero poder cerrar y revocar sesiones y controlar la expiración de los tokens, para cortar el acceso de inmediato ante un riesgo, como el robo de credenciales o la baja de un empleado.

#### Criterios de Aceptación

1. WHEN un Usuario cierra sesión, THE Sistema SHALL revocar su Token_Refresco de modo que no pueda emitir nuevos Token_Acceso.
2. WHEN un Administrador con Permiso revoca las Sesion de una cuenta de Usuario o cuando la cuenta se desactiva conforme al Requisito 4, THE Sistema SHALL invalidar los Token_Refresco vigentes de esa cuenta y, tras la expiración del Token_Acceso vigente, impedir el acceso de esa cuenta.
3. THE Sistema SHALL mantener un registro de los Token_Refresco revocados, o un mecanismo equivalente, y rechazar todo Token_Refresco revocado conforme al Requisito 1.
4. WHERE se detecta un evento de seguridad relevante para una cuenta (por ejemplo, un cambio de contraseña), THE Sistema SHALL revocar los Token_Refresco emitidos previamente a esa cuenta.
5. WHEN un Usuario con Permiso consulta las Sesion activas de una cuenta, THE API SHALL devolver el listado de Sesion o Token_Refresco vigentes de forma paginada con un tamaño de página predeterminado de 20 registros y un tamaño máximo de 100 registros por página.
6. WHEN una Sesion se cierra o se revoca, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la cuenta afectada, la acción y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 69: Portabilidad y baja de datos de la empresa (offboarding del tenant)

**Historia de Usuario:** Como Super_Administrador de la plataforma, a solicitud de la Empresa, quiero exportar o eliminar los datos de una Empresa cuando cancela su suscripción, para cumplir con la portabilidad de datos y las obligaciones de privacidad, respetando la retención fiscal.

#### Criterios de Aceptación

1. WHEN un Super_Administrador con Permiso, o la Empresa a través de su Administrador_Empresa, solicita la exportación de los datos de una Empresa, THE Sistema SHALL generar una exportación de los datos de negocio de esa Empresa, limitada estrictamente a su tenant_id, en un formato estructurado y procesable.
2. WHEN una Suscripcion se cancela, THE Sistema SHALL conservar los datos de la Empresa durante un Periodo_Gracia configurable antes de cualquier eliminación y mantener el acceso restringido conforme al estado de la Empresa establecido en el Requisito 24.
3. WHEN un Super_Administrador con Permiso ejecuta la eliminación definitiva de los datos de una Empresa tras el Periodo_Gracia, THE Sistema SHALL eliminar o anonimizar los datos de negocio de esa Empresa identificados por su tenant_id sin afectar los datos de otras Empresas.
4. WHERE la normativa fiscal exige la conservación de comprobantes (Factura (CFDI), Recibo_Nomina y Poliza_Contable) durante un periodo mínimo, THE Sistema SHALL preservar dichos registros fiscales conforme al periodo de retención aplicable aun cuando se eliminen otros datos, o documentar su archivado seguro.
5. THE proceso de Offboarding_Empresa, tanto de exportación como de eliminación, SHALL respetar el aislamiento por tenant_id conforme al Requisito 23, de modo que solo afecte a la Empresa objetivo.
6. WHEN se ejecuta una exportación o una eliminación de datos de una Empresa, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la Empresa afectada, la acción, el alcance y la marca temporal en tiempo universal coordinado (UTC).

### Requisito 70: Cumplimiento y calidad conforme a ISO 9001:2026

**Historia de Usuario:** Como responsable de calidad de la Empresa, quiero que el Sistema habilite y evidencie de forma verificable las prácticas del Sistema de Gestión de Calidad conforme a la norma ISO 9001:2026 (sexta edición), para sostener la certificación de la Empresa y demostrar una cultura de calidad basada en datos confiables.

#### Criterios de Aceptación

1. WHEN un Usuario con Permiso registra una Queja_Cliente (reclamación o no conformidad reportada por un Cliente), THE Sistema SHALL persistirla con su origen (incluida la mensajería omnicanal del Requisito 64), su descripción, su Cliente asociado y su marca temporal en tiempo universal coordinado (UTC), y SHALL permitir vincularla como entrada a una Accion_Correctiva conforme a la cláusula 10.2 de ISO 9001:2026, sin que dicha vinculación sea obligatoria (la Queja_Cliente es una entrada potencial, no forzosa, a la Accion_Correctiva).
2. WHEN un Usuario con Permiso registra una No_Conformidad, THE Sistema SHALL permitir crear una Accion_Correctiva asociada con responsable, causa raíz, acciones planificadas, evidencia de cierre y estado mediante una máquina de estados con las transiciones definidas ("abierta" a "en_analisis", "en_analisis" a "en_ejecucion", "en_ejecucion" a "verificacion" y "verificacion" a "cerrada"), considerando "cerrada" como estado final; y SHALL registrar la eficacia verificada del cierre (cláusula 10.2).
3. THE Sistema SHALL gestionar un registro de Riesgo y, de forma separada, un registro de Oportunidad (cláusulas 6.1.2 y 6.1.3 de ISO 9001:2026, que ahora los tratan como conceptos distintos), cada uno con su descripción, su evaluación y sus acciones asociadas, de modo que las acciones para abordar Riesgos y las acciones para aprovechar Oportunidades se determinen y consulten por separado.
4. WHEN un Usuario con Permiso registra un Cambio_SGC (cambio planificado al Sistema de Gestión de Calidad o a un proceso), THE Sistema SHALL exigir y persistir su propósito, sus consecuencias potenciales, los recursos necesarios y el responsable asignado antes de aprobarlo (gestión del cambio, cláusula 6.3), y SHALL registrar en auditoría su aprobación con actor y marca temporal en UTC.
5. THE Sistema SHALL permitir determinar y documentar, dentro del Contexto_Organizacion de la Empresa, si el cambio climático y demás cuestiones internas y externas son pertinentes para el Sistema de Gestión de Calidad, así como las expectativas de las partes interesadas (cláusulas 4.1 y 4.2), conservando la justificación aun cuando la conclusión sea que no son pertinentes.
6. THE Servicio_Auditoria SHALL constituir la evidencia documentada de las operaciones sensibles con garantías de integridad y no repudio conforme al Requisito 10, de modo que todo Registro_Auditoria sirva como "información documentada disponible como evidencia" en el sentido de la cláusula 7.5 de ISO 9001:2026 (trazabilidad de quién hizo qué y cuándo).
7. WHERE la Empresa mide su cultura de calidad, THE Tablero SHALL presentar indicadores de cultura de calidad y de comportamiento ético derivados de datos del Sistema (por ejemplo, número de No_Conformidad abiertas y cerradas, tiempo de cierre de Accion_Correctiva, tasa de reincidencia de una misma No_Conformidad y quejas atendidas en tiempo), como apoyo a la responsabilidad de la alta dirección de promover la cultura de calidad (cláusulas 5.1.1 y 7.3), y THE indicadores SHALL calcularse como agregaciones de solo lectura conforme al Requisito 22.
8. THE Sistema SHALL considerar las redes sociales (Requisito 64 y Requisito 66) como una fuente legítima de percepción y satisfacción del Cliente, de modo que las métricas sociales puedan incorporarse a la evaluación de la satisfacción del Cliente (nota a la cláusula 9.1.2).
9. WHEN se crea o cambia de estado cualquier Queja_Cliente, No_Conformidad, Accion_Correctiva, Riesgo, Oportunidad o Cambio_SGC, THE Servicio_Auditoria SHALL registrar un Registro_Auditoria con el actor, la acción, el recurso afectado, el estado anterior, el estado nuevo y la marca temporal en UTC, y THE Sistema SHALL aplicar el control de acceso por roles del Requisito 3 y el aislamiento multi-empresa del Requisito 23 a todos estos recursos.
10. THE Sistema SHALL mantener una trazabilidad consultable entre las cláusulas relevantes de ISO 9001:2026 y las capacidades del Sistema que las habilitan, de modo que un auditor pueda verificar, para cada cláusula soportada, el recurso o la evidencia correspondiente.

## Notas de Restricciones Tecnológicas

Estas restricciones provienen de decisiones ya confirmadas y contextualizan los requisitos anteriores:

- Backend en Spring Boot 3.x sobre Java 21 LTS, con arquitectura hexagonal pragmática (Puertos y Adaptadores), empaquetado como JAR ejecutable con servidor embebido (Tomcat) y ejecutado como servicio de Windows.
- Frontend en Angular servido como archivos estáticos por IIS, que actúa como Proxy_Inverso (mediante ARR / URL Rewrite), termina TLS y enruta `/` al frontend y `/api` al backend de Spring Boot.
- Frontend en Angular con Angular Material como base de componentes y un Sistema_Diseno propio (tokens de diseño) para lograr una interfaz responsiva, accesible (WCAG 2.1 AA) y de apariencia profesional consistente.
- Base de datos PostgreSQL, aprovechando su seguridad de nivel de fila (Row-Level Security) para reforzar el aislamiento multi-empresa por tenant_id definido en el Requisito 23, además del filtrado por tenant_id en la capa de aplicación.
- Despliegue on-premise en un servidor existente con sistema operativo Windows e IIS, sin servicios en nube; la arquitectura debe permanecer portable para una migración posterior a nube (por ejemplo, empaquetando el backend en contenedores) sin reescribir el núcleo de negocio.
- Autenticación y autorización gestionadas por el propio Sistema con Spring Security y JWT (Token_Acceso de vida corta y Token_Refresco), sin dependencia de LDAP/AD.
- Integración de redes sociales mediante las APIs oficiales de Meta (WhatsApp Business Cloud API y Graph API para Facebook Messenger e Instagram; Marketing API para campañas), a través de puertos/adaptadores desacoplados. La autenticación emplea tokens de acceso con alcance de permisos (token de usuario de sistema permanente para WhatsApp y token de acceso de página para Messenger e Instagram) y requiere la verificación previa del negocio en Meta. Los webhooks entrantes se reciben sobre HTTPS con un certificado TLS válido, terminado por el Proxy_Inverso (IIS) conforme al Requisito 9; las credenciales se gestionan conforme al Requisito 11.
- La moneda única del Sistema es el peso mexicano (MXN); los cálculos monetarios y fiscales (IVA, retenciones, nómina) se realizan conforme a la normativa mexicana.

### Servicios de terceros y costos operativos

Esta nota informativa, redactada en lenguaje sencillo para el cliente, aclara qué servicios externos pueden implicar un costo al usar el Sistema. Es importante entender que el CRM en sí NO cobra cuotas de licencia adicionales por conectarse a estos servicios: los costos que se describen a continuación son de terceros y de naturaleza operativa, es decir, dependen del uso que cada empresa haga de ellos. Las tarifas mencionadas son orientativas y pueden variar según el proveedor y las condiciones vigentes.

- **Timbrado de facturas (CFDI)**: para que las facturas, las notas de crédito, los complementos de pago y los recibos de nómina tengan validez fiscal en México, deben timbrarse ante un PAC (Proveedor Autorizado de Certificación). El PAC cobra por cada timbre, es decir, por cada comprobante emitido, normalmente a través de paquetes de folios o de planes de consumo. Se trata de un costo obligatorio para operar fiscalmente en el país. El Sistema permite elegir el PAC de preferencia y llevar el control del consumo de timbres disponibles.

- **Mensajes de WhatsApp**: enviar mensajes por WhatsApp Business tiene un costo por mensaje de plantilla, de acuerdo con las reglas de Meta. En cambio, responder dentro de la ventana de 24 horas posterior al mensaje del cliente no tiene costo. Facebook Messenger e Instagram no cobran por conversar ni por publicar contenido, aunque sí requieren verificar el negocio en Meta como paso previo.

- **Campañas de anuncios**: si la empresa decide invertir en campañas publicitarias en redes sociales, ese gasto (el presupuesto de los anuncios) lo define y lo controla la propia empresa. El Sistema únicamente ayuda a gestionar y consultar dichas campañas; no añade ningún cargo por ese servicio.

- **Correo, dominio y certificado de seguridad (HTTPS)**: el envío de correos electrónicos, así como el dominio y el certificado de seguridad del sitio, dependen del proveedor que elija la empresa. Para estos servicios existen tanto opciones gratuitas como de pago, según las necesidades y el volumen de uso.

Gracias a que el Sistema está construido con una arquitectura desacoplada (los servicios externos se conectan mediante adaptadores independientes), la empresa puede cambiar de proveedor cuando lo desee (por ejemplo, cambiar de PAC o de servicio de mensajería) para optimizar sus costos, sin necesidad de rehacer el Sistema.

## Alcance Futuro (Segunda Versión)

Las siguientes capacidades avanzadas se contemplan como una SEGUNDA VERSIÓN del Sistema y NO forman parte del entregable actual:

- **Punto de venta (POS)**: venta en mostrador con manejo de caja y cortes de caja, ventas rápidas con lectura de código de barras, e integración con el inventario y la facturación, ideal para operaciones de venta directa.
- **Presencia en línea (páginas de aterrizaje, comercio electrónico y sitio propio)**: catálogo público, carrito de compras, captura de leads hacia el pipeline y pasarela de pago, para captación y venta en línea integradas al CRM.
- **Manufactura avanzada / MRP con listas de materiales (BOM) por producto**: planificación de requerimientos de materiales a partir de la demanda de fabricación.
- **Logística y envíos con transportistas**: guías, seguimiento de entregas y coordinación con paqueterías.
- **Firma electrónica de contratos**: formalización digital de contratos con validez legal.
- **Aplicación móvil de campo con modo sin conexión**: para cuadrillas y técnicos, con captura de firma, fotografías y formularios sin señal y sincronización al reconectar.
- **Asistentes y automatización con inteligencia artificial**: predicción de ventas, sugerencias y detección de anomalías en operaciones y finanzas.
- **Motor de automatización de flujos de trabajo de bajo código (low-code)**: reglas y flujos configurables por el usuario sin programar.
- **Gestión documental**: repositorio central de documentos con control de versiones y extracción de datos (OCR).
- **Plataforma de integración con API pública y webhooks**: para conectar el Sistema con bancos, contabilidad externa, comercio electrónico u otros sistemas.
- **Configurador de cotizaciones (CPQ)**: configurar, cotizar y fijar precios de anuncios complejos con reglas y aprobaciones.
- **Encuestas de satisfacción del cliente (CSAT/NPS)**: medición de satisfacción posterior a la instalación y al servicio.
- **Multi-idioma y multi-moneda**: soporte para operación internacional con varios idiomas y monedas.

Estas capacidades se documentan únicamente para orientar la evolución futura de la plataforma y no forman parte de los requisitos del entregable actual.
