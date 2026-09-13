package ru.neverland.core;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ApiServices {
   private static final Map<String, ApiServices.Status> statuses = new HashMap<>();
   private static final Map<String, Long> warnings = new HashMap<>();

   private ApiServices() {
   }

   /** For APIs that access live Bukkit/Towny state or mutate data. */
   public static void primaryThread() {
      if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("API требует основного потока сервера");
   }

   public static ApiServices.Connection require(String plugin, String contract, String... capabilities) {
      ApiServices.Connection connection = connect(plugin, contract, 1, capabilities);
      if (!connection.ready()) {
         throw new IllegalStateException(plugin + ": " + connection.state() + " " + connection.detail());
      } else {
         return connection;
      }
   }

   public static Object call(String plugin, String contract, String method, Class<?>[] signature, Object... args) throws ReflectiveOperationException {
      return require(plugin, contract, method).invoke(method, signature, args);
   }

   public static synchronized List<ApiServices.Status> statuses() {
      return List.copyOf(statuses.values());
   }

   private static synchronized void report(String plugin, String contract, ApiServices.State state, String detail) {
      String key = plugin + "/" + contract;
      long now = System.currentTimeMillis();
      ApiServices.Status old = statuses.put(key, new ApiServices.Status(plugin, contract, state, detail, now));
      if (state == ApiServices.State.READY
         || state == ApiServices.State.NOT_INSTALLED
         || old != null && old.state() == state && now - warnings.getOrDefault(key, 0L) < 60000L) {
         if (state == ApiServices.State.READY && old != null && old.state() != ApiServices.State.READY && old.state() != ApiServices.State.NOT_INSTALLED) {
            Bukkit.getLogger().info("[NeverLand API] " + plugin + ": связь восстановлена");
         }
      } else {
         warnings.put(key, now);
         Bukkit.getLogger().warning("[NeverLand API] " + plugin + " " + state + ": " + detail);
      }
   }

   private static synchronized void reportConnection(String plugin, String contract, ApiServices.State state, String detail) {
      ApiServices.Status old = statuses.get(plugin + "/" + contract);
      if (state != ApiServices.State.READY || old == null || old.state() != ApiServices.State.INVOCATION_ERROR) {
         report(plugin, contract, state, detail);
      }
   }

   public static ApiServices.Connection connect(String pluginName, String contractName, int major, String... required) {
      String detail = "";
      Class<?> type = null;
      Object service = null;

      ApiServices.State state;
      try {
         if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("API требует основного потока сервера");
         }

         Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
         if (plugin == null) {
            state = ApiServices.State.NOT_INSTALLED;
         } else if (!plugin.isEnabled()) {
            state = ApiServices.State.DISABLED;
         } else {
            type = Class.forName(contractName, true, plugin.getClass().getClassLoader());
            service = Bukkit.getServicesManager().load(type);
            if (service == null) {
               state = ApiServices.State.SERVICE_UNAVAILABLE;
            } else {
               Object version = type.getMethod("apiVersion").invoke(service);
               Object caps = type.getMethod("capabilities").invoke(service);
               if (version instanceof Integer n && n == major && caps instanceof Set<?> set && set.containsAll(Arrays.asList(required))) {
                  state = ApiServices.State.READY;
               } else {
                  state = ApiServices.State.INCOMPATIBLE_API;
                  detail = "Нужен API " + major + " с возможностями " + Arrays.toString((Object[])required) + "; получен " + version + " " + caps;
               }
            }
         }
      } catch (NoSuchMethodException | IllegalAccessException | LinkageError | ClassNotFoundException var13) {
         state = ApiServices.State.INCOMPATIBLE_API;
         detail = var13.toString();
      } catch (RuntimeException | InvocationTargetException var14) {
         state = ApiServices.State.INVOCATION_ERROR;
         detail = var14.toString();
      }

      reportConnection(pluginName, contractName, state, detail);
      return new ApiServices.Connection(pluginName, type, service, state, detail);
   }

   public record Connection(String plugin, Class<?> contract, Object service, ApiServices.State state, String detail) {
      public boolean ready() {
         return this.state == ApiServices.State.READY;
      }

      public Object invoke(String method, Class<?>[] signature, Object... args) throws ReflectiveOperationException {
         if (!this.ready()) {
            throw new IllegalStateException(this.plugin + ": " + this.state + " — " + this.detail);
         } else {
            // A Connection is a snapshot. Never invoke a disabled or replaced provider,
            // and keep the lifecycle diagnosis separate from a provider call failure.
            try {
               primaryThread();
            } catch (IllegalStateException ex) {
               ApiServices.report(this.plugin, this.contract.getName(), ApiServices.State.INVOCATION_ERROR, ex.getMessage());
               throw ex;
            }
            Plugin current = Bukkit.getPluginManager().getPlugin(this.plugin);
            if (current == null || !current.isEnabled()) {
               ApiServices.report(this.plugin, this.contract.getName(), current == null ? ApiServices.State.NOT_INSTALLED : ApiServices.State.DISABLED, "Сохранённое соединение больше недоступно");
               throw new IllegalStateException(this.plugin + ": provider unavailable");
            }
            try {
               if (Class.forName(this.contract.getName(), false, current.getClass().getClassLoader()) != this.contract
                     || Bukkit.getServicesManager().load(this.contract) != this.service) {
                  ApiServices.report(this.plugin, this.contract.getName(), ApiServices.State.SERVICE_UNAVAILABLE, "Поставщик API изменился; получите новое соединение");
                  throw new IllegalStateException(this.plugin + ": stale API connection");
               }
            } catch (ClassNotFoundException | LinkageError ex) {
               ApiServices.report(this.plugin, this.contract.getName(), ApiServices.State.INCOMPATIBLE_API, ex.toString());
               throw ex;
            }
            try {
               Object advertised = this.contract.getMethod("capabilities").invoke(this.service);
               if (!Set.of("apiVersion", "capabilities", "storageMetrics", "integrationDiagnostics").contains(method)
                     && (!(advertised instanceof Set<?> capabilities) || !capabilities.contains(method))) {
                  throw new NoSuchMethodException(this.contract.getName() + ": capability not advertised: " + method);
               }
               Object result = this.contract.getMethod(method, signature).invoke(this.service, args);
               ApiServices.report(this.plugin, this.contract.getName(), ApiServices.State.READY, "");
               return result;
            } catch (IllegalAccessException | NoSuchMethodException var5) {
               ApiServices.report(this.plugin, this.contract.getName(), ApiServices.State.INCOMPATIBLE_API, var5.toString());
               throw var5;
            } catch (InvocationTargetException var6) {
               ApiServices.report(this.plugin, this.contract.getName(), ApiServices.State.INVOCATION_ERROR, String.valueOf(var6.getCause()));
               throw var6;
            } catch (LinkageError | RuntimeException var7) {
               ApiServices.report(this.plugin, this.contract.getName(), ApiServices.State.INVOCATION_ERROR, var7.toString());
               throw var7;
            }
         }
      }
   }

   public static enum State {
      READY,
      NOT_INSTALLED,
      DISABLED,
      SERVICE_UNAVAILABLE,
      INCOMPATIBLE_API,
      INVOCATION_ERROR;
   }

   public record Status(String plugin, String contract, ApiServices.State state, String detail, long at) {
   }
}
