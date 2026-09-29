package ru.neverland.core;

import java.util.Map;

public final class LocalDiagnostics {
   private LocalDiagnostics() {
   }

   public static Map<String, Object> snapshot() { synchronized (ru.neverland.core.SuiteState.lock()) {
      var source = new ApiContract() {};
      return Map.of("integrations", source.integrationDiagnostics(), "storage", source.storageMetrics());
   } }
}
