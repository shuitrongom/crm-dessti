package com.dessti.crm.platform.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertidor de atributos JPA reutilizable para <b>cifrar campos altamente
 * sensibles en reposo</b> (Requisito 67).
 *
 * <p>Al aplicarlo a un atributo de tipo {@code String}, el valor se cifra con
 * {@link ServicioCifrado} (AES-256-GCM, con versión de llave) antes de escribirse
 * en la columna, y se descifra al leerse. Uso previsto en entidades futuras:</p>
 *
 * <pre>
 * &#64;Convert(converter = CampoCifradoConverter.class)
 * private String rfc;
 * </pre>
 *
 * <p>Se marca con {@code autoApply = false} para aplicarlo <b>explícitamente</b>
 * solo a los campos que lo requieran (datos fiscales, financieros o personales,
 * credenciales de integraciones), no a todos los {@code String}.</p>
 *
 * <p><b>Integración con Spring:</b> JPA instancia los converters fuera del
 * contexto de Spring, por lo que el servicio de cifrado se obtiene mediante
 * {@link CifradoHolder} (holder estático inicializado por Spring al arranque),
 * evitando acoplar el converter al contenedor de inyección.</p>
 */
@Converter(autoApply = false)
public class CampoCifradoConverter implements AttributeConverter<String, String> {

    /**
     * Cifra el valor del atributo antes de persistirlo en la base de datos.
     *
     * @param atributo valor en claro del atributo (puede ser {@code null}).
     * @return valor cifrado con prefijo de versión de llave, o {@code null}.
     */
    @Override
    public String convertToDatabaseColumn(String atributo) {
        return CifradoHolder.servicio().cifrar(atributo);
    }

    /**
     * Descifra el valor almacenado al cargar la entidad.
     *
     * @param columna valor cifrado en la columna (puede ser {@code null}).
     * @return valor en claro, o {@code null}.
     */
    @Override
    public String convertToEntityAttribute(String columna) {
        return CifradoHolder.servicio().descifrar(columna);
    }
}
