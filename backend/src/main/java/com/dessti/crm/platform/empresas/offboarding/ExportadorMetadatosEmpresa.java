package com.dessti.crm.platform.empresas.offboarding;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.platform.empresas.Empresa;
import com.dessti.crm.platform.empresas.EmpresaRepository;

/**
 * Implementacion de referencia de {@link RecursoTenantOffboarding} que exporta
 * los <strong>metadatos de plataforma NO secretos</strong> de la propia Empresa
 * (identidad, estado y marcas de ciclo de vida) — Req 69.1.
 *
 * <p><strong>Proposito:</strong> demuestra y ejercita el pipeline de offboarding
 * mientras los modulos de negocio (clientes, cotizaciones, facturas, etc.) aun
 * no existen (bloques 15+). Prueba que {@code ServicioOffboarding} recolecta e
 * invoca correctamente los beans de la SPI y ensambla una exportacion
 * estructurada.</p>
 *
 * <h2>Sin secretos (Req 10.10, 11.3)</h2>
 * <p>Exporta unicamente atributos publicos de la Empresa (nombre, RFC, estado,
 * fechas). <em>Nunca</em> incluye contrasenas, hashes de Usuario, claves de
 * cifrado ni credenciales de integraciones.</p>
 *
 * <h2>Preservacion (lapida del tenant)</h2>
 * <p>La fila {@code empresa} es la <strong>lapida</strong> (tombstone) del
 * tenant y el ancla de auditoria del offboarding: por eso este componente
 * <em>no</em> la elimina. {@link #eliminarOAnonimizar(UUID)} devuelve {@code 0}
 * (no borra). No se marca como comprobante fiscal, pero su conservacion es una
 * decision de plataforma documentada, no una omision.</p>
 */
@Component
public class ExportadorMetadatosEmpresa implements RecursoTenantOffboarding {

    /** Nombre canonico del recurso exportado. */
    static final String RECURSO = "empresa";

    private final EmpresaRepository empresaRepository;

    public ExportadorMetadatosEmpresa(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    @Override
    public String nombreRecurso() {
        return RECURSO;
    }

    /**
     * Exporta los metadatos NO secretos de la Empresa objetivo (Req 69.1).
     *
     * <p>La entidad {@code empresa} no es tenant-scoped (es el propio tenant),
     * por lo que se localiza por su PK ({@code tenant_id}). Devuelve un mapa
     * ordenado (serializable a JSON) con los campos publicos; si la Empresa no
     * existe, devuelve un mapa vacio.</p>
     *
     * @param tenantId identificador de la Empresa objetivo; no {@code null}.
     * @return un {@link Map} con los metadatos publicos de la Empresa.
     */
    @Override
    public Object exportar(UUID tenantId) {
        return empresaRepository.findById(tenantId)
                .map(ExportadorMetadatosEmpresa::aMapa)
                .orElseGet(LinkedHashMap::new);
    }

    /**
     * No elimina la Empresa: es la lapida del tenant y el ancla de auditoria del
     * offboarding (Req 69.6). Devuelve siempre {@code 0}.
     *
     * @param tenantId identificador de la Empresa objetivo (ignorado, no se borra).
     * @return {@code 0} (no se elimina ningun registro).
     */
    @Override
    public long eliminarOAnonimizar(UUID tenantId) {
        return 0L;
    }

    private static Map<String, Object> aMapa(Empresa empresa) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tenantId", empresa.getId().toString());
        datos.put("nombre", empresa.getNombre());
        datos.put("rfc", empresa.getRfc());
        datos.put("estado", empresa.getEstado().valorBd());
        datos.put("brandingNombreVisible", empresa.getBrandingNombreVisible());
        datos.put("fechaCancelacion",
                empresa.getFechaCancelacion() == null ? null : empresa.getFechaCancelacion().toString());
        datos.put("finPeriodoGracia",
                empresa.getFinPeriodoGracia() == null ? null : empresa.getFinPeriodoGracia().toString());
        datos.put("createdAt",
                empresa.getCreatedAt() == null ? null : empresa.getCreatedAt().toString());
        datos.put("updatedAt",
                empresa.getUpdatedAt() == null ? null : empresa.getUpdatedAt().toString());
        return datos;
    }
}
