package com.dessti.crm.contabilidad.electronica.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.dessti.crm.contabilidad.electronica.application.CatalogoAgrupadorSatPort;
import com.dessti.crm.contabilidad.electronica.domain.CodigoAgrupadorSat;

/**
 * Adaptador de salida que implementa {@link CatalogoAgrupadorSatPort} sobre el
 * {@link CodigoAgrupadorSatRepository}. Acota el {@code q} y el limite de resultados
 * para el autocompletar y normaliza el codigo antes de las busquedas puntuales.
 */
@Component
public class CatalogoAgrupadorSatAdapter implements CatalogoAgrupadorSatPort {

    private final CodigoAgrupadorSatRepository repository;

    public CatalogoAgrupadorSatAdapter(CodigoAgrupadorSatRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existe(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return false;
        }
        return repository.existsById(codigo.strip());
    }

    @Override
    public Optional<CodigoAgrupadorSat> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(codigo.strip());
    }

    @Override
    public List<CodigoAgrupadorSat> buscar(String q, int limite) {
        int tam = Math.max(1, Math.min(limite, 100));
        String texto = (q == null) ? null : q.strip();
        return repository.buscar(texto, PageRequest.of(0, tam));
    }
}
