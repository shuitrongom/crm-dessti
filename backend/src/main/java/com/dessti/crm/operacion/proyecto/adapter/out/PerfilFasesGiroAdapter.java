package com.dessti.crm.operacion.proyecto.adapter.out;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.dessti.crm.operacion.proyecto.application.PerfilFasesGiroPort;
import com.dessti.crm.operacion.proyecto.domain.PerfilFasesGiro;
import com.dessti.crm.platform.security.rbac.GiroEmpresaPort;
import com.dessti.crm.platform.tenant.TenantContext;

/**
 * Adaptador de salida que implementa {@link PerfilFasesGiroPort} resolviendo el
 * {@link PerfilFasesGiro} del tenant vigente a partir de su <strong>giro</strong>
 * (Decision D5, &sect;A3). Consulta la clave de giro via {@link GiroEmpresaPort}
 * —el mismo puerto que usa el {@code Autorizador} para el gating por giro— acotado
 * al tenant del {@link TenantContext} (nunca de la peticion, Req 23.4):
 * <ul>
 *   <li>{@code anuncios-luminosos} (normalizado, sin distinguir mayusculas ni
 *       espacios) &rarr; {@link PerfilFasesGiro#ANUNCIOS} (las cuatro fases).</li>
 *   <li>cualquier otro giro, o giro no resoluble (Empresa sin giro / tenant sin
 *       contexto) &rarr; {@link PerfilFasesGiro#GENERICO} (solo produccion).</li>
 * </ul>
 *
 * <p>Al depender solo de {@link GiroEmpresaPort} (plataforma) y del dominio propio,
 * el Nucleo {@code proyecto} no se acopla a ningun vertical, respetando la regla de
 * dependencias hexagonal.</p>
 */
@Component
public class PerfilFasesGiroAdapter implements PerfilFasesGiroPort {

    /** Clave canonica del giro de anuncios luminosos (la unica con las cuatro fases). */
    static final String GIRO_ANUNCIOS = "anuncios-luminosos";

    private final GiroEmpresaPort giroEmpresa;

    public PerfilFasesGiroAdapter(GiroEmpresaPort giroEmpresa) {
        this.giroEmpresa = giroEmpresa;
    }

    @Override
    public PerfilFasesGiro perfilDelTenant() {
        // El tenant se deriva del contexto autenticado (Req 23.4); si no hay
        // contexto, se degrada de forma segura al perfil generico.
        return TenantContext.getCurrent()
                .flatMap(giroEmpresa::giroDeTenant)
                .map(PerfilFasesGiroAdapter::normalizar)
                .filter(GIRO_ANUNCIOS::equals)
                .map(clave -> PerfilFasesGiro.ANUNCIOS)
                .orElse(PerfilFasesGiro.GENERICO);
    }

    /**
     * Normaliza la clave de giro para la comparacion: recorta espacios y pasa a
     * minusculas con {@link Locale#ROOT}, de forma consistente con la normalizacion
     * que aplica el {@code Autorizador} al gating por giro.
     *
     * @param clave clave de giro cruda (puede venir con espacios o mayusculas).
     * @return la clave normalizada.
     */
    private static String normalizar(String clave) {
        return clave.trim().toLowerCase(Locale.ROOT);
    }
}
