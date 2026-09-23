package com.dessti.crm.platform.empresas;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueba de <strong>esquema/smoke</strong> del CONTENIDO de la migracion Flyway
 * {@code V67__empresa_branding_color_primario.sql} (spec
 * tematizacion-empresa-enterprise, tareas 4.1/4.5; Req 6.1).
 *
 * <p>NO arranca base de datos ni Flyway (no hay Docker en este entorno): lee el
 * texto del archivo de migracion desde el classpath de test
 * ({@code db/migration/V67__empresa_branding_color_primario.sql}, copiado desde
 * {@code src/main/resources}) y verifica por aserciones de texto que la
 * migracion:</p>
 * <ul>
 *   <li>agrega la columna {@code branding_color_primario VARCHAR(7)} de forma
 *       idempotente ({@code ADD COLUMN IF NOT EXISTS});</li>
 *   <li>incluye un {@code CHECK} con el patron hexadecimal
 *       {@code ^#[0-9a-fA-F]{6}$}; y</li>
 *   <li>NO introduce politicas RLS (la tabla {@code empresa} no lleva RLS por
 *       decision de diseno documentada en V1/V2).</li>
 * </ul>
 *
 * <p><strong>Nota:</strong> la aplicacion <em>real</em> de Flyway sobre una base
 * de datos (que la columna exista, sea nullable, con default nulo y el CHECK
 * activo) se valida en la tarea de verificacion integral final (tarea 12.1) al
 * arrancar con Testcontainers/PostgreSQL. Aqui solo se verifica el contenido
 * estatico del script, sin dependencias de infraestructura.</p>
 */
class V67MigracionSmokeTest {

    private static final String RUTA_CLASSPATH =
            "db/migration/V67__empresa_branding_color_primario.sql";

    private static String sql;

    @BeforeAll
    static void leerMigracion() throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(RUTA_CLASSPATH)) {
            assertThat(in)
                    .as("la migracion V67 debe estar en el classpath: " + RUTA_CLASSPATH)
                    .isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("V67 agrega la columna branding_color_primario VARCHAR(7) de forma idempotente")
    void agregaColumnaVarchar7() {
        // Se normalizan espacios internos para tolerar variaciones de formato.
        String normalizado = sql.replaceAll("\\s+", " ");
        assertThat(normalizado)
                .as("debe agregar la columna con ADD COLUMN IF NOT EXISTS ... VARCHAR(7)")
                .contains("ADD COLUMN IF NOT EXISTS branding_color_primario VARCHAR(7)");
    }

    @Test
    @DisplayName("V67 incluye un CHECK con el patron hexadecimal #RRGGBB")
    void incluyeCheckDeFormatoHexadecimal() {
        String enMinusculas = sql.toLowerCase(Locale.ROOT);
        assertThat(enMinusculas)
                .as("la migracion debe definir un CHECK")
                .contains("check");
        assertThat(sql)
                .as("el CHECK debe validar el patron hexadecimal ^#[0-9a-fA-F]{6}$")
                .contains("^#[0-9a-fA-F]{6}$");
    }

    @Test
    @DisplayName("V67 NO agrega politicas RLS sobre la tabla empresa")
    void noAgregaRls() {
        String enMayusculas = sql.toUpperCase(Locale.ROOT);
        assertThat(enMayusculas)
                .as("la migracion no debe habilitar RLS")
                .doesNotContain("ENABLE ROW LEVEL SECURITY");
        assertThat(enMayusculas)
                .as("la migracion no debe crear politicas RLS")
                .doesNotContain("CREATE POLICY");
        assertThat(enMayusculas)
                .as("la migracion no debe forzar RLS")
                .doesNotContain("FORCE ROW LEVEL SECURITY");
    }
}
