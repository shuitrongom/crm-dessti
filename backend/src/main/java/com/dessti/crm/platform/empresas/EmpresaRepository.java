package com.dessti.crm.platform.empresas;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de la entidad {@link Empresa} (Req 24).
 *
 * <p>A diferencia de las entidades de negocio, {@code empresa} NO es
 * tenant-scoped (es la propia unidad tenant), por lo que estas consultas operan
 * a nivel de plataforma: el {@code super_admin} las usa para crear, consultar y
 * listar Empresas (Req 24.1, 24.5). El acceso queda restringido por RBAC en el
 * controlador ({@code empresa:*}, V5), no por filtro de tenant.</p>
 */
public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {

    /**
     * Comprueba si ya existe una Empresa con ese RFC. Sirve para anticipar el
     * conflicto de unicidad de RFC a nivel de plataforma (Req 24.2) antes de
     * confiar en el indice unico {@code uq_empresa_rfc} de la migracion V8. El
     * RFC se compara tal como se almacena (normalizado a mayusculas por la
     * entidad).
     *
     * @param rfc identificador fiscal normalizado (mayusculas).
     * @return {@code true} si ya existe una Empresa con ese RFC.
     */
    boolean existsByRfc(String rfc);

    /**
     * Comprueba si existe OTRA Empresa (distinta de {@code id}) con ese RFC.
     * Sirve para validar la unicidad del RFC al EDITAR una Empresa (CHANGE 1):
     * conservar el mismo RFC de la propia Empresa NO debe considerarse conflicto,
     * pero adoptar el RFC de otra Empresa produce 409. Complementa al indice
     * unico {@code uq_empresa_rfc} (V8). El RFC se compara tal como se almacena
     * (normalizado a mayusculas por la entidad).
     *
     * @param rfc identificador fiscal normalizado (mayusculas).
     * @param id  identificador de la Empresa que se esta editando (se excluye).
     * @return {@code true} si ya existe OTRA Empresa con ese RFC.
     */
    boolean existsByRfcAndIdNot(String rfc, UUID id);

    /**
     * Lista paginada de Empresas filtrada por estado (Req 24.5).
     *
     * @param estado   estado por el que filtrar.
     * @param pageable parametros de paginacion (tamano por defecto 20, maximo
     *                 100; los aplica {@code PageRequestFactory}).
     * @return la pagina de Empresas en el estado indicado.
     */
    Page<Empresa> findByEstado(EstadoEmpresa estado, Pageable pageable);

    /**
     * Lista las Empresas <strong>canceladas</strong> cuyo Periodo_Gracia ya
     * expiro respecto al instante indicado (offboarding, Req 69.3): estan en
     * estado {@link EstadoEmpresa#CANCELADA} y su {@code fin_periodo_gracia} no
     * es posterior a {@code ahora} ({@code fin_periodo_gracia <= ahora}).
     *
     * <p>Sustenta el barrido opcional de eliminacion definitiva por lotes: son
     * las Empresas elegibles para que el {@code super_admin} ejecute la
     * eliminacion/anonimizacion de sus datos de negocio (Req 69.3).</p>
     *
     * @param ahora    instante de referencia en UTC.
     * @param pageable parametros de paginacion (acotados a 20/100).
     * @return la pagina de Empresas canceladas con Periodo_Gracia expirado.
     */
    Page<Empresa> findByEstadoAndFinPeriodoGraciaLessThanEqual(
            EstadoEmpresa estado, java.time.Instant ahora, Pageable pageable);

    /**
     * Cuenta cuantas Empresas pertenecen al Giro indicado (Req 1.5). Spring Data
     * deriva la consulta {@code SELECT count(*) FROM empresa WHERE giro_id = ?}
     * a partir de la propiedad {@code giroId} de {@link Empresa} (columna
     * {@code empresa.giro_id}, existente desde la migracion V51).
     *
     * <p>Sustenta la regla que impide desactivar un Giro en uso: lo consume
     * {@code ConteoEmpresasPorGiroAdapter}, el adaptador real de
     * {@link com.dessti.crm.platform.giros.application.ConteoEmpresasPorGiroPort},
     * que el {@code ServicioGiros} usa para rechazar (422) la desactivacion de un
     * Giro con Empresas asociadas.</p>
     *
     * @param giroId identificador del Giro cuyo uso se consulta.
     * @return numero de Empresas asociadas al Giro; {@code 0} si ninguna. Nunca
     *         negativo.
     */
    long countByGiroId(UUID giroId);

    /**
     * Busqueda textual paginada de Empresas por coincidencia (contiene, sin
     * distinguir mayusculas/minusculas) en el nombre, el RFC o el nombre
     * comercial (Req 24). Sirve para el buscador del listado de plataforma del
     * {@code super_admin}. El {@code nombre_comercial} puede ser {@code null}
     * (dato descriptivo opcional de V54): {@code LIKE} sobre {@code NULL} es
     * {@code NULL} (no coincide), lo que es el comportamiento deseado.
     *
     * @param q        texto a buscar; se compara ya normalizado por el servicio.
     * @param pageable parametros de paginacion (acotados a 20/100).
     * @return la pagina de Empresas que coinciden en cualquiera de los tres campos.
     */
    @Query("""
            SELECT e FROM Empresa e
            WHERE LOWER(e.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(e.rfc) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(e.nombreComercial) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    Page<Empresa> buscar(@Param("q") String q, Pageable pageable);

    /**
     * Igual que {@link #buscar(String, Pageable)} pero acotando el resultado a un
     * estado concreto (Req 24), para combinar el buscador con el filtro por
     * estado del listado de plataforma.
     *
     * @param estado   estado por el que filtrar.
     * @param q        texto a buscar; se compara ya normalizado por el servicio.
     * @param pageable parametros de paginacion (acotados a 20/100).
     * @return la pagina de Empresas del estado indicado que coinciden con la busqueda.
     */
    @Query("""
            SELECT e FROM Empresa e
            WHERE e.estado = :estado
              AND (LOWER(e.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(e.rfc) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(e.nombreComercial) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Empresa> buscarPorEstado(
            @Param("estado") EstadoEmpresa estado, @Param("q") String q, Pageable pageable);
}
