package com.ecolchain.api.onboarding.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "company_profile")
@IdClass(CompanyProfileEntity.PK.class)
public class CompanyProfileEntity extends PanacheEntityBase {
    @Id @Column(name = "company_id") public UUID companyId;
    @Id @Column(name = "profile_type_id") public UUID profileTypeId;

    public static class PK implements Serializable {
        public UUID companyId;
        public UUID profileTypeId;

        @Override public boolean equals(Object o) {
            return o instanceof PK p && companyId.equals(p.companyId) && profileTypeId.equals(p.profileTypeId);
        }
        @Override public int hashCode() { return java.util.Objects.hash(companyId, profileTypeId); }
    }
}
