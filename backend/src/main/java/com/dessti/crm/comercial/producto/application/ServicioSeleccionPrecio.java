package com.dessti.crm.comercial.producto.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dessti.crm.comercial.producto.adapter.out.persistence.PrecioProductoRepository;
import com.dessti.crm.comercial.producto.adapter.out.persistence.PrecioProductoRepository.PrecioVigente;
import com.dessti.crm.platform.error.ReglaNegocioException;

/**
 * Implementacion del {@link SugerenciaPrecioPort}: selecciona el precio unitario
 * sugerido de un Producto a partir de sus Listas_Precios vigentes (Req 59.4,
 * 59.9). Es el servicio de <em>seleccion de precio</em> que el submodulo de
 * Cotizaciones (bloque 17) consume a traves del puerto.
 *
 * <h2>Regla de seleccion (Req 59.9), documentada con precision</h2>
 * <p>Dado un Producto, un (opcional) segmento de Cliente y una fecha, se
 * consideran unicamente los precios cuya {@code ListaPrecios} esta activa y
 * vigente a esa fecha (dentro de {@code [vigenciaInicio, vigenciaFin]}, con fin
 * abierto si {@code vigenciaFin} es {@code null}). Entre ellos se elige el precio
 * ganador con este orden de preferencia:</p>
 * <ol>
 *   <li><strong>Segmento especifico sobre general:</strong> si existe al menos
 *       una lista vigente cuyo segmento coincide (sin distinguir mayusculas) con
 *       el segmento del Cliente, la seleccion se restringe a esas listas de
 *       segmento; en caso contrario se consideran las generales (sin segmento) y,
 *       a falta de preferencia de segmento, cualquier lista aplicable.</li>
 *   <li><strong>Mayor prioridad:</strong> dentro del grupo elegido, gana la lista
 *       de mayor {@code prioridad} (entero; a mayor valor, mayor prioridad).</li>
 *   <li><strong>Sin aplicable:</strong> si ninguna lista vigente asigna precio al
 *       Producto, no se sugiere precio ({@link Optional#empty()}).</li>
 * </ol>
 *
 * <p>La consulta al repositorio ya devuelve solo candidatas vigentes y ordenadas
 * por prioridad; este servicio aplica el criterio de segmento sobre ese conjunto
 * para no depender del orden exacto del SQL.</p>
 *
 * <h2>Aislamiento multi-tenant (Req 23)</h2>
 * <p>La consulta subyacente queda acotada al tenant vigente por el filtro global
 * de Hibernate (Capa 1) y la RLS (Capa 2, V12), de modo que solo participan las
 * listas y precios de la Empresa del Usuario.</p>
 */
@Service
public class ServicioSeleccionPrecio implements SugerenciaPrecioPort {

    private final PrecioProductoRepository precioProductoRepository;

    public ServicioSeleccionPrecio(PrecioProductoRepository precioProductoRepository) {
        this.precioProductoRepository = precioProductoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> sugerirPrecioUnitario(ConsultaSugerenciaPrecio consulta) {
        if (consulta == null || consulta.productoId() == null) {
            throw new ReglaNegocioException("El Producto de la sugerencia de precio es obligatorio.");
        }
        LocalDate fecha = (consulta.fecha() == null) ? LocalDate.now() : consulta.fecha();
        String segmento = normalizarSegmento(consulta.segmentoCliente());

        List<PrecioVigente> candidatas =
                precioProductoRepository.buscarPreciosVigentes(consulta.productoId(), fecha);
        if (candidatas.isEmpty()) {
            return Optional.empty();
        }

        // 1) Preferir listas de segmento coincidente si existe alguna (Req 59.9).
        List<PrecioVigente> grupo = candidatas;
        if (segmento != null) {
            List<PrecioVigente> deSegmento = candidatas.stream()
                    .filter(c -> c.segmento() != null && c.segmento().equalsIgnoreCase(segmento))
                    .toList();
            if (!deSegmento.isEmpty()) {
                grupo = deSegmento;
            }
        }

        // 2) Dentro del grupo elegido, gana la de mayor prioridad (Req 59.9).
        return grupo.stream()
                .max(Comparator.comparingInt(PrecioVigente::prioridad))
                .map(PrecioVigente::precio);
    }

    private static String normalizarSegmento(String segmentoCliente) {
        if (segmentoCliente == null || segmentoCliente.isBlank()) {
            return null;
        }
        return segmentoCliente.strip();
    }
}
