package com.dessti.crm.contabilidad.polizas.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dessti.crm.contabilidad.polizas.domain.PeriodoContable;

/**
 * Repositorio Spring Data JPA de los {@link PeriodoContable Periodos_Contables} del
 * tenant vigente (cierre de periodo mensual). Como {@code PeriodoContable} extiende
 * {@code TenantScopedEntity}, el filtro global de Hibernate (Capa 1) y la RLS de V72
 * (Capa 2) acotan estas consultas al tenant vigente (Req 23).
 */
public interface PeriodoContableRepository extends JpaRepository<PeriodoContable, UUID> {

    /**
     * Busca el registro de periodo por {@code (anio, mes)} dentro del tenant vigente.
     * Un {@link Optional#empty()} significa periodo ABIERTO por defecto (no
     * materializado).
     *
     * @param anio anio del periodo.
     * @param mes  mes del periodo (1..12).
     * @return el periodo, o vacio si no existe fila (se interpreta como abierto).
     */
    @Query("SELECT p FROM PeriodoContable p WHERE p.anio = :anio AND p.mes = :mes")
    Optional<PeriodoContable> buscarPorAnioMes(@Param("anio") int anio, @Param("mes") int mes);

    /**
     * Lista los registros de periodo del anio dado dentro del tenant vigente,
     * ordenados por mes. Solo devuelve los meses materializados (cerrados alguna vez);
     * los ausentes se tratan como abiertos en la capa de aplicacion.
     *
     * @param anio anio a listar.
     * @return los periodos materializados del anio, ordenados por mes.
     */
    @Query("SELECT p FROM PeriodoContable p WHERE p.anio = :anio ORDER BY p.mes ASC")
    List<PeriodoContable> listarPorAnio(@Param("anio") int anio);
}
