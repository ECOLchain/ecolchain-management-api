package com.ecolchain.api.onboarding.domain;

import java.util.regex.Pattern;

/**
 * Endereço de carteira blockchain da empresa (usado para cadastro on-chain).
 * Aceita endereço Solana (base58, 32-44 chars) ou EVM (0x + 40 hex).
 * docs/features/cadastro-empresa.md
 */
public final class WalletValidator {

    private static final Pattern SOLANA = Pattern.compile("^[1-9A-HJ-NP-Za-km-z]{32,44}$");
    private static final Pattern EVM = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private WalletValidator() {
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    public static boolean isValid(String wallet) {
        return SOLANA.matcher(wallet).matches() || EVM.matcher(wallet).matches();
    }
}
