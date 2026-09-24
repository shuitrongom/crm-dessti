-- ============================================================================
-- V71__contabilidad_electronica_sat.sql
--
-- Bloque enterprise "Contabilidad Electronica SAT (Anexo 24)".
--
-- Agrega la capa fiscal de Contabilidad Electronica al modulo `contabilidad`
-- (Req SAT: Anexo 24 de la RMF), reutilizando el catalogo de cuentas y las
-- polizas ya existentes (V33). Introduce:
--
--   1) Columna `codigo_agrupador_sat` en `cuenta_contable` (tabla tenant-scoped
--      con RLS ya existente): el "amarre" opcional de cada Cuenta_Contable al
--      codigo agrupador del SAT (Apartado B del Anexo 24). Hereda el aislamiento
--      multi-tenant de la tabla.
--
--   2) Tabla maestra `codigo_agrupador_sat_catalogo`: el catalogo oficial de
--      codigos agrupadores del SAT. Es DATO DE PLATAFORMA (comun a todas las
--      Empresas, como `permiso`/`plan`): SIN RLS, solo lectura para los tenants.
--      Se siembra con un subconjunto AMPLIO y representativo del Apartado B
--      (niveles 1 y 2). Es AMPLIABLE en migraciones futuras sin tocar datos de
--      tenant. La generacion XML no exige que el catalogo este completo: solo
--      valida que el codigo amarrado exista.
--
--   3) Permisos nuevos (patron idempotente ON CONFLICT DO NOTHING) enlazados al
--      rol predefinido `contabilidad` (a0000000-0000-0000-0000-00000000000b, V5):
--        * contabilidad_electronica:leer     (vista previa)
--        * contabilidad_electronica:exportar (descarga de XML)
--        * cuenta_contable:actualizar        (amarre del codigo agrupador)
--
-- DECISIONES DE DISENO
--   - Sin FK dura cuenta_contable.codigo_agrupador_sat -> catalogo: el amarre se
--     guarda como texto validado por la aplicacion contra el catalogo. Esto
--     permite evolucionar el catalogo del SAT sin migrar datos de tenant y no
--     acopla la RLS de cuenta_contable a una tabla sin RLS.
--   - El catalogo agrupador es de plataforma (sin tenant_id, sin RLS). El nivel
--     (1 mayor / 2 subcuenta) y la naturaleza (D/A) provienen del propio Anexo 24.
--   - Numero de migracion V71: siguiente libre tras V70.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) Amarre opcional en cuenta_contable (tenant-scoped, RLS heredada de V33).
-- ----------------------------------------------------------------------------
ALTER TABLE cuenta_contable
    ADD COLUMN codigo_agrupador_sat VARCHAR(10);

-- Apoyo a la exportacion del catalogo/balanza por tenant, acotado al tenant.
CREATE INDEX ix_cuenta_contable_tenant_agrupador
    ON cuenta_contable (tenant_id, codigo_agrupador_sat);

-- ----------------------------------------------------------------------------
-- 2) Catalogo maestro de codigos agrupadores del SAT (dato de plataforma, SIN RLS).
--    codigo: clave del Anexo 24 (p. ej. '101.01'); nivel 1=mayor, 2=subcuenta;
--    naturaleza D=deudora / A=acreedora; codigo_padre para la jerarquia.
-- ----------------------------------------------------------------------------
CREATE TABLE codigo_agrupador_sat_catalogo (
    codigo        VARCHAR(10)  NOT NULL,
    nombre        VARCHAR(200) NOT NULL,
    nivel         SMALLINT     NOT NULL,
    naturaleza    VARCHAR(1)   NOT NULL,
    codigo_padre  VARCHAR(10),
    CONSTRAINT pk_codigo_agrupador_sat PRIMARY KEY (codigo),
    CONSTRAINT ck_agrup_nivel CHECK (nivel IN (1, 2)),
    CONSTRAINT ck_agrup_natur CHECK (naturaleza IN ('D', 'A'))
);

-- Apoyo a la busqueda por nombre (autocompletar en el frontend).
CREATE INDEX ix_codigo_agrupador_sat_nombre ON codigo_agrupador_sat_catalogo (nombre);

-- ----------------------------------------------------------------------------
-- Siembra del catalogo agrupador (subconjunto amplio y representativo del
-- Apartado B del Anexo 24 de la RMF). Ampliable en migraciones futuras.
-- Naturaleza: Activo/Gasto/Costo = D (deudora); Pasivo/Capital/Ingreso = A.
-- ----------------------------------------------------------------------------
INSERT INTO codigo_agrupador_sat_catalogo (codigo, nombre, nivel, naturaleza, codigo_padre) VALUES
    -- ===== ACTIVO (naturaleza D) =====
    ('100',    'Activo',                                         1, 'D', NULL),
    ('101',    'Caja',                                           1, 'D', NULL),
    ('101.01', 'Caja y efectivo',                                2, 'D', '101'),
    ('102',    'Bancos',                                         1, 'D', NULL),
    ('102.01', 'Bancos nacionales',                              2, 'D', '102'),
    ('102.02', 'Bancos extranjeros',                             2, 'D', '102'),
    ('103',    'Inversiones',                                    1, 'D', NULL),
    ('103.01', 'Inversiones temporales',                         2, 'D', '103'),
    ('105',    'Clientes',                                       1, 'D', NULL),
    ('105.01', 'Clientes nacionales',                            2, 'D', '105'),
    ('105.02', 'Clientes extranjeros',                           2, 'D', '105'),
    ('106',    'Cuentas y documentos por cobrar a corto plazo',  1, 'D', NULL),
    ('106.01', 'Documentos por cobrar nacionales',               2, 'D', '106'),
    ('107',    'Deudores diversos',                              1, 'D', NULL),
    ('107.01', 'Funcionarios y empleados',                       2, 'D', '107'),
    ('107.05', 'Otros deudores diversos',                        2, 'D', '107'),
    ('108',    'Estimacion de cuentas incobrables',              1, 'D', NULL),
    ('108.01', 'Estimacion de cuentas incobrables nacionales',   2, 'D', '108'),
    ('109',    'Pagos anticipados',                              1, 'D', NULL),
    ('109.01', 'Seguros y fianzas pagados por anticipado',       2, 'D', '109'),
    ('110',    'IVA acreditable pagado',                         1, 'D', NULL),
    ('110.01', 'IVA acreditable pagado tasa 16%',                2, 'D', '110'),
    ('113',    'IVA acreditable de pagos',                       1, 'D', NULL),
    ('113.01', 'IVA pendiente de pago',                          2, 'D', '113'),
    ('115',    'Inventario',                                     1, 'D', NULL),
    ('115.01', 'Inventario de mercancias',                       2, 'D', '115'),
    ('115.04', 'Materia prima y materiales',                     2, 'D', '115'),
    ('118',    'Subsidio al empleo por aplicar',                 1, 'D', NULL),
    ('118.01', 'Subsidio al empleo por aplicar',                 2, 'D', '118'),
    ('120',    'Anticipo a proveedores',                         1, 'D', NULL),
    ('120.01', 'Anticipo a proveedores nacionales',              2, 'D', '120'),
    ('151',    'Terrenos',                                       1, 'D', NULL),
    ('151.01', 'Terrenos',                                       2, 'D', '151'),
    ('152',    'Edificios',                                      1, 'D', NULL),
    ('152.01', 'Edificios',                                      2, 'D', '152'),
    ('153',    'Maquinaria y equipo',                            1, 'D', NULL),
    ('153.01', 'Maquinaria y equipo',                            2, 'D', '153'),
    ('154',    'Automoviles, autobuses, camiones de carga',      1, 'D', NULL),
    ('154.01', 'Automoviles',                                    2, 'D', '154'),
    ('155',    'Mobiliario y equipo de oficina',                 1, 'D', NULL),
    ('155.01', 'Mobiliario y equipo de oficina',                 2, 'D', '155'),
    ('156',    'Equipo de computo',                              1, 'D', NULL),
    ('156.01', 'Equipo de computo',                              2, 'D', '156'),
    ('157',    'Depreciacion acumulada de activo fijo',          1, 'A', NULL),
    ('157.01', 'Depreciacion acumulada de edificios',            2, 'A', '157'),
    -- ===== PASIVO (naturaleza A) =====
    ('201',    'Proveedores',                                    1, 'A', NULL),
    ('201.01', 'Proveedores nacionales',                         2, 'A', '201'),
    ('201.02', 'Proveedores extranjeros',                        2, 'A', '201'),
    ('205',    'Cuentas y documentos por pagar a corto plazo',   1, 'A', NULL),
    ('205.01', 'Documentos por pagar bancario y financiero nacional', 2, 'A', '205'),
    ('206',    'Acreedores diversos a corto plazo',              1, 'A', NULL),
    ('206.01', 'Acreedores diversos nacionales',                 2, 'A', '206'),
    ('208',    'Anticipo de clientes',                           1, 'A', NULL),
    ('208.01', 'Anticipo de clientes nacionales',                2, 'A', '208'),
    ('209',    'Impuestos por pagar',                            1, 'A', NULL),
    ('209.01', 'ISR por pagar',                                  2, 'A', '209'),
    ('210',    'IVA trasladado cobrado',                         1, 'A', NULL),
    ('210.01', 'IVA trasladado cobrado tasa 16%',                2, 'A', '210'),
    ('213',    'IVA trasladado no cobrado',                      1, 'A', NULL),
    ('213.01', 'IVA trasladado no cobrado tasa 16%',             2, 'A', '213'),
    ('216',    'Impuestos retenidos por pagar',                  1, 'A', NULL),
    ('216.01', 'ISR retenido por sueldos y salarios',            2, 'A', '216'),
    ('216.10', 'IVA retenido',                                   2, 'A', '216'),
    ('219',    'Provision de sueldos y salarios por pagar',      1, 'A', NULL),
    ('219.01', 'Provision de sueldos y salarios por pagar',      2, 'A', '219'),
    -- ===== CAPITAL (naturaleza A) =====
    ('301',    'Capital social',                                 1, 'A', NULL),
    ('301.01', 'Capital fijo',                                   2, 'A', '301'),
    ('304',    'Resultado de ejercicios anteriores',             1, 'A', NULL),
    ('304.01', 'Utilidad de ejercicios anteriores',              2, 'A', '304'),
    ('305',    'Resultado del ejercicio',                        1, 'A', NULL),
    ('305.01', 'Utilidad del ejercicio',                         2, 'A', '305'),
    ('306',    'Otras cuentas de capital',                       1, 'A', NULL),
    ('306.01', 'Aportaciones para futuros aumentos de capital',  2, 'A', '306'),
    -- ===== INGRESOS (naturaleza A) =====
    ('401',    'Ingresos',                                       1, 'A', NULL),
    ('401.01', 'Ventas y/o servicios gravados a la tasa general',2, 'A', '401'),
    ('401.05', 'Ventas y/o servicios a la tasa del 0%',          2, 'A', '401'),
    ('402',    'Devoluciones, descuentos o bonificaciones sobre ingresos', 1, 'D', NULL),
    ('402.01', 'Devoluciones, descuentos o bonificaciones',      2, 'D', '402'),
    ('403',    'Otros ingresos',                                 1, 'A', NULL),
    ('403.01', 'Otros ingresos',                                 2, 'A', '403'),
    -- ===== COSTOS (naturaleza D) =====
    ('501',    'Costo de venta y/o servicio',                    1, 'D', NULL),
    ('501.01', 'Costo de venta',                                 2, 'D', '501'),
    ('502',    'Compras',                                        1, 'D', NULL),
    ('502.01', 'Compras nacionales',                             2, 'D', '502'),
    -- ===== GASTOS (naturaleza D) =====
    ('601',    'Gastos generales',                               1, 'D', NULL),
    ('601.01', 'Sueldos y salarios',                             2, 'D', '601'),
    ('601.06', 'Honorarios',                                     2, 'D', '601'),
    ('601.10', 'Combustibles y lubricantes',                     2, 'D', '601'),
    ('601.19', 'Papeleria y articulos de oficina',               2, 'D', '601'),
    ('601.52', 'Otros gastos generales',                         2, 'D', '601'),
    ('602',    'Gastos de venta',                                1, 'D', NULL),
    ('602.01', 'Sueldos y salarios de venta',                    2, 'D', '602'),
    ('603',    'Gastos de administracion',                       1, 'D', NULL),
    ('603.01', 'Sueldos y salarios de administracion',           2, 'D', '603'),
    ('604',    'Gastos financieros',                             1, 'D', NULL),
    ('604.01', 'Intereses pagados',                              2, 'D', '604'),
    -- ===== CUENTAS DE ORDEN (naturaleza D) =====
    ('899',    'Otras cuentas de orden',                         1, 'D', NULL),
    ('899.01', 'Otras cuentas de orden deudoras',                2, 'D', '899')
ON CONFLICT (codigo) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3) Permisos nuevos + enlace al rol `contabilidad` (a0000000-...-00b, V5).
--    Patron idempotente identico a V33/V70.
-- ----------------------------------------------------------------------------
INSERT INTO permiso (recurso, operacion) VALUES
    ('contabilidad_electronica', 'leer'),
    ('contabilidad_electronica', 'exportar'),
    ('cuenta_contable',          'actualizar')
ON CONFLICT (recurso, operacion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT 'a0000000-0000-0000-0000-00000000000b', p.id
FROM permiso p
WHERE (p.recurso = 'contabilidad_electronica' AND p.operacion IN ('leer', 'exportar'))
   OR (p.recurso = 'cuenta_contable'          AND p.operacion = 'actualizar')
ON CONFLICT DO NOTHING;
