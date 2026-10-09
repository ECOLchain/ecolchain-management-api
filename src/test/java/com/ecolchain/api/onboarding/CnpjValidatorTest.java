package com.ecolchain.api.onboarding;

import com.ecolchain.api.onboarding.domain.CnpjValidator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CnpjValidatorTest {

    @Test
    void acceptsOfficialAlphanumericExample() {
        assertTrue(CnpjValidator.isValid("12.ABC.345/01DE-35"));
    }

    @Test
    void acceptsClassicNumericCnpj() {
        assertTrue(CnpjValidator.isValid("04.252.011/0001-10"));
        assertTrue(CnpjValidator.isValid("04252011000110"));
    }

    @Test
    void rejectsWrongCheckDigits() {
        assertFalse(CnpjValidator.isValid("12.ABC.345/01DE-34"));
        assertFalse(CnpjValidator.isValid("04.252.011/0001-11"));
    }

    @Test
    void rejectsGarbage() {
        assertFalse(CnpjValidator.isValid(null));
        assertFalse(CnpjValidator.isValid(""));
        assertFalse(CnpjValidator.isValid("123"));
        assertFalse(CnpjValidator.isValid("12.ABC.345/01DE-3X"));
    }

    @Test
    void normalizeStripsMaskAndUppercases() {
        assertEquals("12ABC34501DE35", CnpjValidator.normalize("12.abc.345/01de-35"));
    }
}
