package ru.neverland.archaeology;

import ru.neverland.archaeology.util.CryptoUtil;

public final class CryptoSmoke {
    public static void main(String[] args) { String secret = CryptoUtil.secret(); String signature = CryptoUtil.sign(secret, "sun_tablet|serial"); if (!CryptoUtil.equals(signature, CryptoUtil.sign(secret, "sun_tablet|serial"))) throw new AssertionError("valid signature rejected"); if (CryptoUtil.equals(signature, CryptoUtil.sign(secret, "sun_tablet|other"))) throw new AssertionError("forged signature accepted"); }
}
