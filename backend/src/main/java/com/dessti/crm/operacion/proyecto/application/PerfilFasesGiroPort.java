package com.dessti.crm.operacion.proyecto.application;

import com.dessti.crm.operacion.proyecto.domain.PerfilFasesGiro;

/**
 * Puerto de <strong>solo lectura</strong> del submodulo proyecto que resuelve el
 * {@link PerfilFasesGiro} aplicable al <strong>giro del tenant vigente</strong>
 * (Decision D5, &sect;A3). Lo consume {@code ServicioProyectos} para parametrizar la
 * derivacion del estado consolidado del Proyecto y para seleccionar el adaptador de
 * avance por Sitio adecuado (las cuatro fases de anuncios, o solo produccion para el
 * resto de giros), sin ramas por giro embebidas en el Nucleo (Req 3.1, 3.2, 3.5).
 *
 * <p>Publicarlo como puerto propio mantiene la arquitectura hexagonal: el Nucleo
 * {@code proyecto} depende de esta <strong>interfaz estable</strong> y no de la
 * resolucion del giro (que vive en la capa de plataforma). El adaptador
 * {@code PerfilFasesGiroAdapter} lo implementa consultando el giro del tenant via
 * {@code GiroEmpresaPort}: {@code anuncios-luminosos -> PerfilFasesGiro.ANUNCIOS};
 * cualquier otro giro (o giro indeterminado) {@code -> PerfilFasesGiro.GENERICO}.</p>
 */
public interface PerfilFasesGiroPort {

    /**
     * Resuelve el perfil de fases aplicable al giro del tenant vigente (Req 3.2).
     *
     * @return {@link PerfilFasesGiro#ANUNCIOS} si el tenant pertenece al giro
     *         {@code anuncios-luminosos}; {@link PerfilFasesGiro#GENERICO} en
     *         cualquier otro caso (incluido giro no resoluble); nunca {@code null}.
     */
    PerfilFasesGiro perfilDelTenant();
}
