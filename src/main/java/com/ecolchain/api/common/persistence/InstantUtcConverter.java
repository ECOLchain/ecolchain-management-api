package com.ecolchain.api.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Persiste {@link Instant} como {@link java.sql.Timestamp} (TIMESTAMP puro, sem
 * fuso). O mapeamento default do Hibernate ORM 7 para {@code Instant} usa
 * TIMESTAMP_UTC e lê via {@code OffsetDateTime}, que falha no driver Oracle
 * (ORA-18716) quando a coluna é TIMESTAMP simples — todas as colunas de data do
 * schema são TIMESTAMP com valores em UTC.
 */
@Converter(autoApply = true)
public class InstantUtcConverter implements AttributeConverter<Instant, Timestamp> {

    @Override
    public Timestamp convertToDatabaseColumn(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    @Override
    public Instant convertToEntityAttribute(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
