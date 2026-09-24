package com.dessti.crm.contabilidad.electronica.adapter.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.contabilidad.electronica.domain.CodigoAgrupadorSat;

/**
 * Repositorio Spring Data JPA del catalogo oficial de codigos agrupadores del SAT
 * ({@link CodigoAgrupadorSat}). Es un catalogo de PLATAFORMA (no tenant-scoped): no
 * hay filtro por tenant ni RLS, todas las Empresas lo consultan igual.
 */
public interface CodigoAgrupadorSatRepository extends JpaRepository<CodigoAgrupadorSat, String> {

    /**
     * Busca codigos agrupadores por coincidencia (contiene, sin distinguir
     * mayusculas/minusculas) en el codigo o el nombre, ordenados por codigo. Sirve
     * al autocompletar del frontend.
     *
     * @param q        texto de busqueda; si es nulo/vacio la consulta lo trata como
     *                 comodin (devuelve el inicio del catalogo).
     * @param pageable acota el numero de resultados.
     * @return los codigos que coinciden, ordenados por codigo.
     */
    @Query("""
            SELECT c FROM CodigoAgrupadorSat c
            WHERE (:q IS NULL OR :q = ''
                   OR LOWER(c.codigo) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY c.codigo ASC
            """)
    List<CodigoAgrupadorSat> buscar(@Param("q") String q, Pageable pageable);
}
