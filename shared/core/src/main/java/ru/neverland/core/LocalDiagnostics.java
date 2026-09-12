package ru.neverland.core;

import java.util.Map;

public final class LocalDiagnostics {
   private LocalDiagnostics() {
   }

   public static Map<String, Object> snapshot() {
      var source = new ApiContract() {};
      return Map.of("integrations", source.integrationDiagnostics(), "storage", source.storageMetrics());
   }
}
