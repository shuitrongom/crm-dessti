package com.dessti.crm.platform.empresas;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dessti.crm.comercial.cotizacion.application.DatosEmisor;
import com.dessti.crm.comercial.cotizacion.application.EmpresaEmisorPort;

/**
 * Adaptador de salida que implementa {@link EmpresaEmisorPort} leyendo la
 * {@link Empresa} del tenant por su id (= {@code tenant_id}) y proyectandola a un
 * {@link DatosEmisor} (V60, Req 1). Reside en el modulo de plataforma
 * ({@code empresas}) porque consume el {@link EmpresaRepository}, replicando el
 * estilo del {@code DatosClienteAdapter}.
 *
 * <p>La tabla {@code empresa} es dato de PLATAFORMA (no tenant-scoped, sin RLS),
 * por lo que la carga por id no depende del filtro de tenant. El {@code tenantId}
 * proviene SIEMPRE del contexto autenticado en el llamador
 * ({@code ServicioCotizaciones}), nunca de la peticion (Req 1.7). El adaptador NO
 * expone la entidad {@code Empresa}: solo el objeto de valor {@link DatosEmisor}.</p>
 */
@Component
public class EmpresaEmisorAdapter implements EmpresaEmisorPort {

    private final EmpresaRepository empresaRepository;

    public EmpresaEmisorAdapter(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    @Override
    public Optional<DatosEmisor> emisorDeTenant(UUID tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return empresaRepository.findById(tenantId).map(EmpresaEmisorAdapter::proyectar);
    }

    /** Proyecta la Empresa a los datos fiscales del emisor, sin exponer la entidad. */
    private static DatosEmisor proyectar(Empresa empresa) {
        return new DatosEmisor(
                empresa.getNombre(),
                empresa.getNombreComercial(),
                empresa.getRfc(),
                armarDireccion(empresa),
                empresa.getEmailContacto(),
                empresa.getSitioWeb());
    }

    /**
     * Arma la direccion fiscal en una sola linea uniendo con ", " las partes no
     * vacias de calle, ciudad, estado, codigo postal y pais. Devuelve {@code null}
     * si no hay ninguna parte capturada (para que el PDF omita la linea).
     */
    private static String armarDireccion(Empresa empresa) {
        List<String> partes = new ArrayList<>();
        agregarSiPresente(partes, empresa.getDireccionCalle());
        agregarSiPresente(partes, empresa.getDireccionCiudad());
        agregarSiPresente(partes, empresa.getDireccionEstado());
        agregarSiPresente(partes, empresa.getDireccionCp());
        agregarSiPresente(partes, empresa.getDireccionPais());
        return partes.isEmpty() ? null : String.join(", ", partes);
    }

    private static void agregarSiPresente(List<String> partes, String valor) {
        if (valor != null && !valor.isBlank()) {
            partes.add(valor.strip());
        }
    }
}
