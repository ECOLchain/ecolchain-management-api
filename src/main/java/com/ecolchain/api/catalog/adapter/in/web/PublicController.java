package com.ecolchain.api.catalog.adapter.in.web;

import com.ecolchain.api.catalog.application.port.out.CatalogStore;
import com.ecolchain.api.common.i18n.RequestLocale;
import com.ecolchain.api.contract.api.PublicApi;
import com.ecolchain.api.contract.model.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.util.List;

/** Catálogo público de tipos de perfil (para o formulário de cadastro). docs/features/admin-catalogo.md */
public class PublicController implements PublicApi {

    @Inject CatalogStore catalog;
    @Inject RequestLocale locale;

    @Override
    public Response listPublicProfileTypes() {
        boolean en = locale.isEn();
        var data = new PublicProfileTypesResponseAllOfData();
        data.setTipos(catalog.listProfileTypes(true).stream().map(pt -> {
            var s = new ProfileTypeSummary();
            s.setCodigo(pt.code);
            s.setNome(en ? pt.nameEn : pt.namePt);
            s.setDescricao(en ? pt.descEn : pt.descPt);
            s.setOrdem(pt.position);
            return s;
        }).toList());
        var body = new PublicProfileTypesResponse();
        body.setData(data);
        body.setLinks(List.of());
        body.setErros(List.of());
        return Response.ok(body).build();
    }
}
