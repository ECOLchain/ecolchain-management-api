package com.ecolchain.api.catalog.application.port.out;

import com.ecolchain.api.catalog.domain.AttributeDef;
import com.ecolchain.api.catalog.domain.ProfileType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogStore {
    List<ProfileType> listProfileTypes(boolean onlyActive);
    Optional<ProfileType> profileTypeByCode(String code);
    Optional<ProfileType> profileTypeById(UUID id);
    ProfileType saveProfileType(ProfileType pt);

    List<AttributeDef> listAttributes(boolean onlyActive);
    Optional<AttributeDef> attributeByCode(String code);
    Optional<AttributeDef> attributeById(UUID id);
    AttributeDef saveAttribute(AttributeDef def);

    /** passos + atributos de um tipo de perfil, ordenados */
    List<FlowStep> flowOf(UUID profileTypeId);
    void replaceFlow(UUID profileTypeId, List<FlowStep> steps);
    boolean profileTypeInUse(UUID profileTypeId);

    record FlowStep(String code, String titlePt, String titleEn, int position,
                    List<FlowAttr> attributes) {
        public record FlowAttr(AttributeDef attribute, boolean required, int position) {}
    }
}
