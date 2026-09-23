package com.dessti.crm.platform.config;

import java.time.Duration;
import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Configuracion de la cache de catalogos de cambio lento (Req 12.3).
 *
 * <p>Habilita la abstraccion Spring Cache ({@link EnableCaching}) con Caffeine
 * como implementacion y declara un {@link CacheManager} con <strong>una cache
 * por catalogo</strong>, cada una con su propio TTL y tope de tamano acorde a
 * su naturaleza. Al declararse explicitamente cada cache (SimpleCacheManager),
 * un nombre de cache no declarado provocaria un error temprano en lugar de
 * crear una cache silenciosa sin TTL.</p>
 *
 * <p><strong>Seguridad multi-tenant (Req 23):</strong> solo se cachean
 * catalogos de <em>plataforma</em> (globales, sin {@code tenant_id}), como el
 * catalogo de monedas. Al no depender del tenant, su clave de cache no necesita
 * incluirlo y no existe riesgo de fuga entre empresas. Ningun catalogo con
 * datos acotados por {@code tenant_id} se cachea aqui; de hacerse en el futuro,
 * su clave DEBERA incorporar el {@code tenant_id} explicitamente.</p>
 *
 * <p><strong>Invalidacion por escritura (Req 12.3):</strong> las operaciones de
 * escritura sobre cada catalogo evictan su cache mediante {@code @CacheEvict},
 * de modo que una lectura posterior refleje el cambio.</p>
 */
@Configuration
@EnableCaching
public class ConfiguracionCache {

    /**
     * Cache del catalogo de <strong>monedas</strong> activas (Req 12.3). Es un
     * catalogo de plataforma (global, sin tenant) y de cambio muy lento. TTL
     * moderado y tamano pequeno: el catalogo completo cabe en una sola entrada.
     */
    public static final String CACHE_MONEDAS = "catalogoMonedas";

    /** TTL de la cache de monedas. Cambio muy lento: 30 minutos es holgado. */
    static final Duration TTL_MONEDAS = Duration.ofMinutes(30);

    /** Tope de entradas de la cache de monedas (la lista activa es una sola entrada). */
    static final long MAX_MONEDAS = 16L;

    /**
     * Administrador de caches basado en Caffeine, con una cache declarada por
     * catalogo y su TTL/tamano especifico.
     *
     * @return el {@link CacheManager} con las caches de catalogos.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCache monedas = new CaffeineCache(
                CACHE_MONEDAS,
                Caffeine.newBuilder()
                        .expireAfterWrite(TTL_MONEDAS)
                        .maximumSize(MAX_MONEDAS)
                        .build());

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(monedas));
        // Se puebla el mapa interno de caches de inmediato (idempotente): Spring
        // volveria a invocarlo en el ciclo de init, pero asi el manager queda
        // utilizable al construirse fuera de dicho ciclo.
        manager.afterPropertiesSet();
        return manager;
    }
}
