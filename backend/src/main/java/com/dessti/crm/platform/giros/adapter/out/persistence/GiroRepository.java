package com.dessti.crm.platform.giros.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dessti.crm.platform.giros.domain.Giro;

/**
 * Puerto de persistencia (repositorio) del {@link Giro}, el catalogo de Giros de
 * plataforma (Req 1.2). Es el <strong>puerto de salida</strong> del hexagono de
 * {@code platform.giros}: se declara como interfaz de Spring Data JPA y es el
 * propio contenedor quien provee su implementacion en tiempo de ejecucion,
 * replicando <em>exactamente</em> el patron del proyecto para catalogos de
 * plataforma (p. ej.
 * {@code platform.monetizacion.adapter.out.persistence.CatalogoModuloRepository}
 * y {@code MonedaRepository}). No existe una interfaz de dominio adicional ni un
 * adaptador que envuelva un {@code JpaRepository}: en este proyecto la interfaz
 * Spring Data <em>es</em> el puerto y su adaptador.
 *
 * <p>Los metodos aqui expuestos cubren las necesidades del {@code ServicioGiros}
 * (tarea 2.5): alta/guardado, consulta por id, consulta por
 * {@link Giro#getClave() clave} canonica, verificacion de existencia por clave
 * (para el conflicto de clave duplicada, Req 1.3) y listados paginados con
 * filtro por estado {@code activo} (Req 1.6).</p>
 *
 * <p><strong>Conteo de empresas por giro (Req 1.5):</strong> la regla que impide
 * desactivar un Giro en uso necesita contar cuantas Empresas lo referencian.
 * Ese conteo <em>no</em> vive en este repositorio: la columna
 * {@code empresa.giro_id} todavia no existe (la crea la migracion V51 en la
 * tarea 4.1) y, ademas, acoplar {@code platform.giros} a la persistencia de
 * {@code platform.empresas} romperia la frontera hexagonal. Por eso el conteo se
 * modela como un puerto de aplicacion independiente,
 * {@link com.dessti.crm.platform.giros.application.ConteoEmpresasPorGiroPort},
 * que la tarea 4.x conectara con la implementacion real.</p>
 *
 * <p>Req 1.2, 1.5.</p>
 */
public interface GiroRepository extends JpaRepository<Giro, UUID> {

    /**
     * Busca un Giro por su clave canonica normalizada (unica, Req 1.2). La clave
     * debe venir ya normalizada por {@link Giro#normalizarClave(String)}, pues la
     * columna almacena siempre la forma canonica.
     *
     * @param clave clave canonica normalizada (minusculas, kebab).
     * @return el Giro con esa clave, o {@link Optional#empty()} si no existe.
     */
    Optional<Giro> findByClave(String clave);

    /**
     * Indica si ya existe un Giro con la clave dada, para detectar la clave
     * duplicada antes del alta (conflicto 422, Req 1.3). La clave debe venir ya
     * normalizada.
     *
     * @param clave clave canonica normalizada a comprobar.
     * @return {@code true} si ya existe un Giro con esa clave.
     */
    boolean existsByClave(String clave);

    /**
     * Indica si ya existe un Giro con ese nombre visible, sin distinguir
     * mayusculas/minusculas, para detectar el nombre duplicado antes del alta
     * (conflicto 409). Complementa la unicidad de la clave canonica
     * ({@link #existsByClave(String)}) con la del nombre visible, respaldada en
     * BD por el indice unico funcional {@code uq_giro_nombre_visible}
     * ({@code lower(nombre_visible)}, V56).
     *
     * @param nombreVisible nombre visible a comprobar (se compara ignorando
     *                      mayusculas/minusculas).
     * @return {@code true} si ya existe un Giro con ese nombre visible.
     */
    boolean existsByNombreVisibleIgnoreCase(String nombreVisible);

    /**
     * Igual que {@link #existsByNombreVisibleIgnoreCase(String)} pero EXCLUYENDO
     * un Giro por su id. Se usa en la EDICION para permitir conservar el propio
     * nombre visible sin que cuente como conflicto, detectando solo colisiones
     * con OTROS Giros (409).
     *
     * @param nombreVisible nombre visible a comprobar (ignora mayusculas).
     * @param id            id del Giro que se edita (se excluye de la busqueda).
     * @return {@code true} si OTRO Giro ya usa ese nombre visible.
     */
    boolean existsByNombreVisibleIgnoreCaseAndIdNot(String nombreVisible, UUID id);

    /**
     * Listado paginado de <strong>todos</strong> los Giros (activos e inactivos),
     * ordenado segun el {@link Pageable} recibido. Lo usa el listado sin filtro
     * de estado del {@code ServicioGiros} (Req 1.6).
     *
     * @param pageable pagina, tamano y orden solicitados.
     * @return la pagina de Giros.
     */
    Page<Giro> findAllBy(Pageable pageable);

    /**
     * Listado paginado de Giros filtrado por estado {@code activo} (Req 1.6). Con
     * {@code true} devuelve solo los Giros disponibles para el alta de Empresas;
     * con {@code false}, solo los dados de baja logica.
     *
     * @param activo   estado a filtrar.
     * @param pageable pagina, tamano y orden solicitados.
     * @return la pagina de Giros que coinciden con el estado.
     */
    Page<Giro> findByActivo(boolean activo, Pageable pageable);
}
