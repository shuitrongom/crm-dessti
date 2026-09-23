package com.dessti.crm.platform.empresas;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.dessti.crm.platform.error.ReglaNegocioException;
import com.dessti.crm.platform.giros.domain.Giro;
import com.dessti.crm.platform.modulos.CatalogoModulosService;
import com.dessti.crm.platform.modulos.ModuloCatalogoDto;
import com.dessti.crm.platform.monetizacion.adapter.out.persistence.MonedaRepository;
import com.dessti.crm.platform.monetizacion.domain.Moneda;
import com.dessti.crm.platform.monetizacion.domain.MonetizacionValidaciones;

/**
 * Helper de aplicacion <strong>compartido</strong> que centraliza las
 * validaciones de <em>moneda activa</em> y de <em>modulos por Giro</em> contra el
 * catalogo de la plataforma.
 *
 * <p>Ambos catalogos comerciales de plataforma &mdash; {@code Plan}
 * ({@link ServicioPlanes}) y {@code PaqueteSuscripcion}
 * (futuro {@code ServicioPaquetesSuscripcion}) &mdash; comparten exactamente la
 * misma regla: se cotizan en una moneda que debe existir y estar activa, y
 * definen un precio por modulo cuyo mapa de claves debe existir en el catalogo y
 * pertenecer al Giro del catalogo comercial o al Nucleo Comun. Esta clase
 * extrae esa logica para evitar duplicarla en cada servicio.</p>
 *
 * <p>Todas las violaciones se reportan con {@link ReglaNegocioException} (HTTP
 * 422), conservando los mismos mensajes historicos.</p>
 */
@Component
public class CatalogoModulosGiroValidacion {

    private final CatalogoModulosService catalogoModulosService;
    private final MonedaRepository monedaRepository;

    public CatalogoModulosGiroValidacion(CatalogoModulosService catalogoModulosService,
                                         MonedaRepository monedaRepository) {
        this.catalogoModulosService = catalogoModulosService;
        this.monedaRepository = monedaRepository;
    }

    /**
     * Valida que la moneda exista y este activa (Req monetizacion); normaliza su
     * codigo ISO 4217.
     *
     * @param codigo codigo de moneda del comando.
     * @return el codigo normalizado (mayusculas).
     * @throws ReglaNegocioException si el codigo es invalido o la moneda no existe
     *                               o esta inactiva.
     */
    public String validarMonedaActiva(String codigo) {
        String normalizado = MonetizacionValidaciones.normalizarCodigoMoneda(codigo);
        Moneda moneda = monedaRepository.findByCodigo(normalizado)
                .orElseThrow(() -> new ReglaNegocioException(
                        "La moneda '" + normalizado + "' no existe en el catalogo."));
        if (!moneda.isActivo()) {
            throw new ReglaNegocioException(
                    "La moneda '" + normalizado + "' esta inactiva y no puede usarse en un Plan.");
        }
        return normalizado;
    }

    /**
     * Valida el mapa de precios por modulo contra el catalogo de la plataforma:
     * cada clave debe existir en el catalogo y, si es un modulo de vertical, debe
     * pertenecer al Giro del catalogo comercial; los modulos de Nucleo (sin Giro)
     * siempre se permiten. Devuelve un mapa con claves normalizadas (los precios
     * se validan despues en la entidad).
     *
     * @param preciosPorModulo mapa crudo {@code clave -> precio}; {@code null} =
     *                         vacio.
     * @param giro             Giro al que pertenece el catalogo comercial.
     * @return el mapa con claves normalizadas listo para la entidad.
     * @throws ReglaNegocioException si alguna clave no existe en el catalogo o
     *                               pertenece a un Giro distinto.
     */
    public Map<String, BigDecimal> validarModulosDelGiro(Map<String, BigDecimal> preciosPorModulo,
                                                         Giro giro) {
        Map<String, BigDecimal> normalizado = new LinkedHashMap<>();
        if (preciosPorModulo == null || preciosPorModulo.isEmpty()) {
            return normalizado;
        }

        // Indice clave -> giro (clave de Giro, o null para Nucleo) del catalogo real.
        Map<String, String> giroPorModulo = new LinkedHashMap<>();
        for (ModuloCatalogoDto modulo : catalogoModulosService.listar()) {
            giroPorModulo.put(modulo.clave(), modulo.giro());
        }

        String giroDelPlan = giro.getClave();
        Set<String> desconocidos = new LinkedHashSet<>();
        Set<String> ajenos = new LinkedHashSet<>();

        for (Map.Entry<String, BigDecimal> entrada : preciosPorModulo.entrySet()) {
            String clave = entrada.getKey();
            if (clave == null) {
                continue;
            }
            String claveNorm = clave.strip().toLowerCase(Locale.ROOT);
            if (claveNorm.isEmpty()) {
                continue;
            }
            if (!giroPorModulo.containsKey(claveNorm)) {
                desconocidos.add(claveNorm);
                continue;
            }
            String giroDelModulo = giroPorModulo.get(claveNorm);
            // Nucleo (giro null): siempre permitido. Vertical: debe ser del Giro del Plan.
            if (giroDelModulo != null && !giroDelModulo.equals(giroDelPlan)) {
                ajenos.add(claveNorm);
                continue;
            }
            normalizado.put(claveNorm, entrada.getValue());
        }

        if (!desconocidos.isEmpty()) {
            throw new ReglaNegocioException(
                    "Los siguientes modulos no existen en el catalogo de la plataforma: "
                            + String.join(", ", desconocidos) + ".");
        }
        if (!ajenos.isEmpty()) {
            throw new ReglaNegocioException(
                    "Los siguientes modulos pertenecen a un Giro distinto al del Plan ('"
                            + giroDelPlan + "') y no pueden incluirse: " + String.join(", ", ajenos) + ".");
        }
        return normalizado;
    }
}
