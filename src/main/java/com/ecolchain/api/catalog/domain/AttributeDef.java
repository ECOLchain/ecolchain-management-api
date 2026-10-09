package com.ecolchain.api.catalog.domain;

import java.util.UUID;

public class AttributeDef {
    public enum Kind { DATA, DOCUMENT }
    public UUID id;
    public String code;
    public Kind kind;
    public String labelPt;
    public String labelEn;
    public String helpPt;
    public String helpEn;
    public String rules; // JSON
    public boolean active;
}
