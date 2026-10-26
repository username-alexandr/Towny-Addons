package ru.neverland.townytaxes.model;

import java.util.Locale;

public final class Domain {
    private Domain() {}
    public enum Scope { GLOBAL, NATION, TOWN, PLAYER;
        public static Scope parse(String value) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { try { return valueOf(value.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return null; } } }
    }
    public enum TaxType { FIXED, INCOME, TRANSACTION;
        public static TaxType parse(String value) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { try { return valueOf(value.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return null; } } }
    }
    public enum SanctionEffect { TRADE_BLOCK, COMMAND_BLOCK, TAX_MULTIPLIER;
        public static SanctionEffect parse(String value) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { try { return valueOf(value.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return null; } } }
    }
    public enum AgreementType { TAX_EXEMPTION, TRADE_PREFERENCE, SANCTION_RELIEF;
        public static AgreementType parse(String value) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { try { return valueOf(value.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return null; } } }
    }
    public enum AgreementStatus { PENDING, ACTIVE, REJECTED, CANCELLED, EXPIRED }
}
