package ru.neverland.archaeology.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class CryptoUtil {
    private CryptoUtil() { }
    public static String secret() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); } }
    public static String sign(String secret, String value) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException("HMAC unavailable", exception); } } }
    public static boolean equals(String left, String right) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8)); } }
}
