package com.dessti.crm.platform.security.crypto;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Entidad de PRUEBA (solo en el árbol de test) para verificar el cifrado de
 * campos en reposo con {@link CampoCifradoConverter} sobre una base de datos
 * PostgreSQL real (Tarea 6.3, Req 67, 11).
 *
 * <p>El campo {@code datoSensible} se cifra transparentemente al persistir y se
 * descifra al leer, gracias a {@code @Convert(converter = CampoCifradoConverter.class)}.
 * El campo {@code datoClaro} NO se cifra y sirve de control para contrastar la
 * columna cifrada frente a una en claro.</p>
 */
@Entity
@Table(name = "entidad_sensible_prueba")
class EntidadSensiblePrueba {

    @Id
    @Column(name = "id")
    private UUID id;

    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "dato_sensible")
    private String datoSensible;

    @Column(name = "dato_claro")
    private String datoClaro;

    protected EntidadSensiblePrueba() {
        // Requerido por JPA.
    }

    EntidadSensiblePrueba(UUID id, String datoSensible, String datoClaro) {
        this.id = id;
        this.datoSensible = datoSensible;
        this.datoClaro = datoClaro;
    }

    UUID getId() {
        return id;
    }

    String getDatoSensible() {
        return datoSensible;
    }

    String getDatoClaro() {
        return datoClaro;
    }
}
