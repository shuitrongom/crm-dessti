package com.dessti.crm.platform.security.crypto;

import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;

/**
 * Implementación mínima y autocontenida de {@link PersistenceUnitInfo} para
 * arrancar un {@code EntityManagerFactory} de Hibernate de forma programática en
 * las pruebas de integración de cifrado (Tarea 6.3), sin depender de un
 * {@code persistence.xml} ni del contexto completo de Spring.
 *
 * <p>Se implementa directamente (en lugar de reutilizar
 * {@code MutablePersistenceUnitInfo} de Spring) porque esa clase lanza
 * {@code UnsupportedOperationException} en métodos que Hibernate invoca durante
 * el bootstrap ({@code getNewTempClassLoader}, {@code addTransformer}).</p>
 */
final class PersistenceUnitInfoPrueba implements PersistenceUnitInfo {

    private final String nombre;
    private final List<String> clasesGestionadas;
    private final Properties propiedades;

    PersistenceUnitInfoPrueba(String nombre, List<String> clasesGestionadas, Properties propiedades) {
        this.nombre = nombre;
        this.clasesGestionadas = new ArrayList<>(clasesGestionadas);
        this.propiedades = propiedades;
    }

    @Override
    public String getPersistenceUnitName() {
        return nombre;
    }

    @Override
    public String getPersistenceProviderClassName() {
        return "org.hibernate.jpa.HibernatePersistenceProvider";
    }

    @Override
    public PersistenceUnitTransactionType getTransactionType() {
        return PersistenceUnitTransactionType.RESOURCE_LOCAL;
    }

    @Override
    public DataSource getJtaDataSource() {
        return null;
    }

    @Override
    public DataSource getNonJtaDataSource() {
        return null;
    }

    @Override
    public List<String> getMappingFileNames() {
        return List.of();
    }

    @Override
    public List<URL> getJarFileUrls() {
        return List.of();
    }

    @Override
    public URL getPersistenceUnitRootUrl() {
        return null;
    }

    @Override
    public List<String> getManagedClassNames() {
        return clasesGestionadas;
    }

    @Override
    public boolean excludeUnlistedClasses() {
        return true;
    }

    @Override
    public SharedCacheMode getSharedCacheMode() {
        return SharedCacheMode.UNSPECIFIED;
    }

    @Override
    public ValidationMode getValidationMode() {
        return ValidationMode.AUTO;
    }

    @Override
    public Properties getProperties() {
        return propiedades;
    }

    @Override
    public String getPersistenceXMLSchemaVersion() {
        return "3.0";
    }

    @Override
    public ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    @Override
    public void addTransformer(ClassTransformer transformer) {
        // No se requiere instrumentación en la prueba.
    }

    @Override
    public ClassLoader getNewTempClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}
