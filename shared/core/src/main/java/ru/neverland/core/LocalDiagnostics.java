package ru.neverland.core;

import java.util.Map;

public final class LocalDiagnostics {
   private LocalDiagnostics() {
   }

   public static Map<String, Object> snapshot() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
      var source = new ApiContract() {};
      return Map.of("integrations", source.integrationDiagnostics(), "storage", source.storageMetrics());
   } }
}
