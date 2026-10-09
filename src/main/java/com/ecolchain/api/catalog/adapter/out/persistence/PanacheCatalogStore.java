package com.ecolchain.api.catalog.adapter.out.persistence;

import com.ecolchain.api.catalog.application.port.out.CatalogStore;
import com.ecolchain.api.catalog.domain.AttributeDef;
import com.ecolchain.api.catalog.domain.ProfileType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class PanacheCatalogStore implements CatalogStore {

    @Override
    public List<ProfileType> listProfileTypes(boolean onlyActive) {
        var list = ProfileTypeEntity.<ProfileTypeEntity>findAll().list().stream()
                .filter(e -> !onlyActive || e.active)
                .sorted(Comparator.comparingInt(e -> e.position))
                .toList();
        return list.stream().map(this::toProfileType).toList();
    }

    @Override
    public Optional<ProfileType> profileTypeByCode(String code) {
        return ProfileTypeEntity.<ProfileTypeEntity>find("code", code)
                .firstResultOptional().map(this::toProfileType);
    }

    @Override
    public Optional<ProfileType> profileTypeById(UUID id) {
        return Optional.ofNullable(ProfileTypeEntity.findById(id))
                .map(e -> toProfileType((ProfileTypeEntity) e));
    }

    @Override
    @Transactional
    public ProfileType saveProfileType(ProfileType pt) {
        ProfileTypeEntity e = pt.id != null ? ProfileTypeEntity.findById(pt.id) : null;
        if (e == null) {
            e = new ProfileTypeEntity();
            e.id = pt.id == null ? UUID.randomUUID() : pt.id;
            e.createdAt = Instant.now();
        }
        e.code = pt.code; e.namePt = pt.namePt; e.nameEn = pt.nameEn;
        e.descPt = pt.descPt; e.descEn = pt.descEn;
        e.active = pt.active; e.position = pt.position;
        e.updatedAt = Instant.now();
        e.persist();
        pt.id = e.id;
        return pt;
    }

    @Override
    public List<AttributeDef> listAttributes(boolean onlyActive) {
        return AttributeDefinitionEntity.<AttributeDefinitionEntity>findAll().list().stream()
                .filter(e -> !onlyActive || e.active)
                .map(this::toAttributeDef).toList();
    }

    @Override
    public Optional<AttributeDef> attributeByCode(String code) {
        return AttributeDefinitionEntity.<AttributeDefinitionEntity>find("code", code)
                .firstResultOptional().map(this::toAttributeDef);
    }

    @Override
    public Optional<AttributeDef> attributeById(UUID id) {
        return Optional.ofNullable(AttributeDefinitionEntity.findById(id))
                .map(e -> toAttributeDef((AttributeDefinitionEntity) e));
    }

    @Override
    @Transactional
    public AttributeDef saveAttribute(AttributeDef def) {
        AttributeDefinitionEntity e = def.id != null ? AttributeDefinitionEntity.findById(def.id) : null;
        if (e == null) {
            e = new AttributeDefinitionEntity();
            e.id = def.id == null ? UUID.randomUUID() : def.id;
            e.createdAt = Instant.now();
        }
        e.code = def.code; e.kind = def.kind.name();
        e.labelPt = def.labelPt; e.labelEn = def.labelEn;
        e.helpPt = def.helpPt; e.helpEn = def.helpEn;
        e.rules = def.rules; e.active = def.active;
        e.updatedAt = Instant.now();
        e.persist();
        def.id = e.id;
        return def;
    }

    @Override
    public List<FlowStep> flowOf(UUID profileTypeId) {
        var steps = OnboardingStepEntity.<OnboardingStepEntity>find(
                "profileTypeId = ?1 order by position", profileTypeId).list();
        var stepIds = steps.stream().map(s -> s.id).toList();
        var links = stepIds.isEmpty() ? List.<StepAttributeEntity>of()
                : StepAttributeEntity.<StepAttributeEntity>find("stepId in ?1 order by position", stepIds).list();
        var attrIds = links.stream().map(l -> l.attributeId).distinct().toList();
        var attrs = attrIds.isEmpty() ? Map.<UUID, AttributeDefinitionEntity>of()
                : AttributeDefinitionEntity.<AttributeDefinitionEntity>find("id in ?1", attrIds).list()
                        .stream().collect(Collectors.toMap(a -> a.id, a -> a));
        var byStep = links.stream().collect(Collectors.groupingBy(l -> l.stepId));
        return steps.stream().map(s -> new FlowStep(s.code, s.titlePt, s.titleEn, s.position,
                byStep.getOrDefault(s.id, List.of()).stream()
                        .filter(l -> attrs.containsKey(l.attributeId))
                        .map(l -> new FlowStep.FlowAttr(toAttributeDef(attrs.get(l.attributeId)), l.required, l.position))
                        .toList()))
                .toList();
    }

    @Override
    @Transactional
    public void replaceFlow(UUID profileTypeId, List<FlowStep> steps) {
        var old = OnboardingStepEntity.<OnboardingStepEntity>find("profileTypeId", profileTypeId).list();
        if (!old.isEmpty()) {
            StepAttributeEntity.delete("stepId in ?1", old.stream().map(s -> s.id).toList());
            OnboardingStepEntity.delete("profileTypeId", profileTypeId);
        }
        for (FlowStep step : steps) {
            var s = new OnboardingStepEntity();
            s.id = UUID.randomUUID();
            s.profileTypeId = profileTypeId;
            s.code = step.code();
            s.titlePt = step.titlePt();
            s.titleEn = step.titleEn();
            s.position = step.position();
            s.persist();
            for (var attr : step.attributes()) {
                var link = new StepAttributeEntity();
                link.stepId = s.id;
                link.attributeId = attr.attribute().id;
                link.required = attr.required();
                link.position = attr.position();
                link.persist();
            }
        }
    }

    @Override
    public boolean profileTypeInUse(UUID profileTypeId) {
        return com.ecolchain.api.onboarding.adapter.out.persistence.CompanyProfileEntity
                .count("profileTypeId", profileTypeId) > 0;
    }

    private ProfileType toProfileType(ProfileTypeEntity e) {
        var p = new ProfileType();
        p.id = e.id; p.code = e.code; p.namePt = e.namePt; p.nameEn = e.nameEn;
        p.descPt = e.descPt; p.descEn = e.descEn; p.active = e.active; p.position = e.position;
        return p;
    }

    private AttributeDef toAttributeDef(AttributeDefinitionEntity e) {
        var d = new AttributeDef();
        d.id = e.id; d.code = e.code; d.kind = AttributeDef.Kind.valueOf(e.kind);
        d.labelPt = e.labelPt; d.labelEn = e.labelEn; d.helpPt = e.helpPt; d.helpEn = e.helpEn;
        d.rules = e.rules; d.active = e.active;
        return d;
    }
}
