package com.ecolchain.api.onboarding.domain;

/**
 * CNPJ alfanumérico (módulo 11 sobre os 12 primeiros chars, ASCII − 48,
 * pesos 2–9 da direita p/ esquerda). Guardado em maiúsculo sem máscara, 14 chars.
 * Exemplo oficial: 12.ABC.345/01DE-35
 */
public final class CnpjValidator {

    private CnpjValidator() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return s;
    }

    public static boolean isValid(String raw) {
        String s = normalize(raw);
        if (s.length() != 14 || !s.matches("[A-Z0-9]{12}[0-9]{2}")) {
            return false;
        }
        int[] v = s.chars().map(c -> c - '0').toArray();
        int d1 = digit(v, 12);
        int d2 = digit(v, 13);
        return v[12] == d1 && v[13] == d2;
    }

    private static int digit(int[] v, int len) {
        int sum = 0;
        int weight = 2;
        for (int i = len - 1; i >= 0; i--) {
            sum += v[i] * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int mod = sum % 11;
        return mod < 2 ? 0 : 11 - mod;
    }
}
