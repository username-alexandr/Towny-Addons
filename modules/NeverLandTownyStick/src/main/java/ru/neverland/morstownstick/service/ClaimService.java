package ru.neverland.morstownstick.service;

import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.WorldCoord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.morstownstick.integration.TownyClaimAdapter;
import ru.neverland.morstownstick.integration.TownyFacade;
import ru.neverland.morstownstick.model.CellKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClaimService {
    private final JavaPlugin plugin;
    private final TownyFacade towny;
    private final TownyClaimAdapter adapter;
    private final SelectionService selections;
    private final MessageService messages;
    private final BorderCache borders;
    private final Map<UUID, ClaimJob> jobs = new HashMap<>();

    public ClaimService(JavaPlugin plugin, TownyFacade towny, TownyClaimAdapter adapter,
                        SelectionService selections, MessageService messages, BorderCache borders) {
        this.plugin = plugin;
        this.towny = towny;
        this.adapter = adapter;
        this.selections = selections;
        this.messages = messages;
        this.borders = borders;
    }

    public boolean isBusy(UUID playerId) {
        return jobs.containsKey(playerId);
    }

    public void start(Player player) {
        UUID playerId = player.getUniqueId();
        if (!plugin.getConfig().getBoolean("claim.enabled", true)) {
            messages.send(player, "claim-disabled");
            return;
        }
        if (!player.hasPermission("morstownstick.claim")) {
            messages.send(player, "no-permission");
            return;
        }
        if (jobs.containsKey(playerId)) {
            messages.send(player, "claim-already-running");
            return;
        }
        Town town = towny.town(player);
        if (town == null) {
            messages.send(player, "no-town");
            return;
        }
        if (!towny.isMayorOrAssistant(player)) {
            messages.send(player, "not-mayor-or-assistant");
            return;
        }
        Set<CellKey> snapshot = selections.snapshot(playerId);
        if (snapshot.isEmpty()) {
            messages.send(player, "no-selected-chunks");
            return;
        }
        if (plugin.getConfig().getBoolean("claim.respect-towny-claim-permissions", true)
                && (!player.hasPermission("towny.command.town.claim.town")
                || (snapshot.size() > 1 && !player.hasPermission("towny.command.town.claim.town.multiple")))) {
            messages.send(player, "missing-towny-permission");
            return;
        }

        List<CellKey> valid = new ArrayList<>();
        ClaimJob job = new ClaimJob(playerId, town, snapshot.size());
        for (CellKey cell : snapshot) {
            if (!towny.isClaimableWorld(cell)) fail(player, job, cell, "reason-world-disabled", null);
            else if (!towny.isWilderness(cell)) fail(player, job, cell, "reason-already-claimed", null);
            else valid.add(cell);
        }

        BfsPlanner.Plan plan = BfsPlanner.plan(valid, towny.ownedCells(town));
        for (CellKey cell : plan.unreachable()) fail(player, job, cell, "reason-not-adjacent", null);
        job.remaining.addAll(plan.ordered());
        if (job.remaining.isEmpty()) {
            finish(player, job);
            return;
        }
        jobs.put(playerId, job);
        messages.send(player, "claim-start", Map.of("count", job.remaining.size()));
        processNext(job);
    }

    public void playerQuit(UUID playerId) {
        ClaimJob job = jobs.remove(playerId);
        if (job != null) job.cancelled = true;
    }

    public void shutdown() {
        for (ClaimJob job : jobs.values()) job.cancelled = true;
        jobs.clear();
    }

    private void processNext(ClaimJob job) {
        if (job.cancelled || jobs.get(job.playerId) != job) return;
        Player player = Bukkit.getPlayer(job.playerId);
        if (player == null || !player.isOnline()) {
            jobs.remove(job.playerId);
            return;
        }
        if (job.remaining.isEmpty()) {
            finish(player, job);
            return;
        }
        if (towny.town(player) == null || !towny.town(player).getUUID().equals(job.town.getUUID()) || !towny.isMayorOrAssistant(player)) {
            failRemaining(player, job, "reason-towny-rejected", "роль или город игрока изменились");
            finish(player, job);
            return;
        }

        int configuredBatch = Math.max(1, plugin.getConfig().getInt("claim.batch-size", 5));
        int batchSize = Math.min(configuredBatch, job.remaining.size());
        if (!job.town.hasUnlimitedClaims()) {
            int available = Math.max(0, job.town.availableTownBlocks());
            if (available == 0) {
                failRemaining(player, job, "reason-limit", null);
                finish(player, job);
                return;
            }
            batchSize = Math.min(batchSize, available);
        }

        try {
            while (batchSize > 0 && !adapter.canAfford(job.town, batchSize)) batchSize--;
        } catch (TownyException exception) {
            failRemaining(player, job, "reason-towny-rejected", exception.getMessage(player));
            finish(player, job);
            return;
        }
        if (batchSize == 0) {
            failRemaining(player, job, "reason-insufficient-funds", null);
            finish(player, job);
            return;
        }

        List<CellKey> requestedCells = removeFirst(job.remaining, batchSize);
        List<WorldCoord> requested = requestedCells.stream().map(CellKey::worldCoord).toList();
        final List<WorldCoord> accepted;
        try {
            accepted = adapter.validateAndCharge(player, job.town, requested);
        } catch (TownyException exception) {
            String reason = exception.getMessage(player);
            String path = classify(reason);
            for (CellKey cell : requestedCells) fail(player, job, cell, path,
                    path.equals("reason-towny-rejected") ? reason : null);
            scheduleNext(job);
            return;
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Towny claim validation failed: " + exception.getMessage());
            for (CellKey cell : requestedCells) fail(player, job, cell, "reason-internal-error", null);
            scheduleNext(job);
            return;
        }

        Set<CellKey> acceptedCells = new LinkedHashSet<>();
        for (WorldCoord coord : accepted) acceptedCells.add(CellKey.from(coord));
        for (CellKey cell : requestedCells) {
            if (!acceptedCells.contains(cell)) fail(player, job, cell, "reason-towny-rejected", "ограничения биома или близости");
        }
        if (accepted.isEmpty()) {
            scheduleNext(job);
            return;
        }

        adapter.claimAsync(player, job.town, accepted, successfulCoords -> {
            if (job.cancelled || jobs.get(job.playerId) != job) return;
            Set<CellKey> successful = new HashSet<>();
            for (WorldCoord coord : successfulCoords) successful.add(CellKey.from(coord));
            for (CellKey cell : acceptedCells) {
                if (successful.contains(cell)) {
                    job.success++;
                    selections.remove(job.playerId, cell);
                } else {
                    fail(player, job, cell, "reason-towny-rejected", "участок изменился во время обработки");
                }
            }
            borders.invalidate(job.town.getUUID());
            scheduleNext(job);
        });
    }

    private void scheduleNext(ClaimJob job) {
        long delay = Math.max(1L, plugin.getConfig().getLong("claim.delay-ticks", 1L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> processNext(job), delay);
    }

    private void failRemaining(Player player, ClaimJob job, String reasonPath, String detail) {
        while (!job.remaining.isEmpty()) fail(player, job, job.remaining.removeFirst(), reasonPath, detail);
    }

    private void fail(Player player, ClaimJob job, CellKey cell, String reasonPath, String detail) {
        job.failed++;
        selections.remove(job.playerId, cell);
        String reason = messages.raw(reasonPath);
        if (detail != null) reason = messages.replace(reason, Map.of("reason", detail));
        messages.send(player, "claim-error-line", Map.of("x", cell.x(), "z", cell.z(), "reason", reason));
    }

    private void finish(Player player, ClaimJob job) {
        jobs.remove(job.playerId, job);
        if (job.success == job.total) messages.send(player, "claim-success", Map.of("success", job.success));
        else if (job.success == 0) messages.send(player, "claim-failed", Map.of("failed", job.failed));
        else messages.send(player, "claim-partial", Map.of("success", job.success, "failed", job.failed, "total", job.total));
    }

    private String classify(String message) {
        String value = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (value.contains("fund") || value.contains("afford") || value.contains("money") || value.contains("средств"))
            return "reason-insufficient-funds";
        if (value.contains("enough blocks") || value.contains("claim block") || value.contains("лимит"))
            return "reason-limit";
        if (value.contains("already claimed") || value.contains("уже занят")) return "reason-already-claimed";
        if (value.contains("not claimable") || value.contains("use towny off")) return "reason-world-disabled";
        if (value.contains("attached") || value.contains("adjacent") || value.contains("гранич")) return "reason-not-adjacent";
        return "reason-towny-rejected";
    }

    private static <T> List<T> removeFirst(Deque<T> queue, int count) {
        List<T> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) result.add(queue.removeFirst());
        return result;
    }

    private static final class ClaimJob {
        private final UUID playerId;
        private final Town town;
        private final int total;
        private final Deque<CellKey> remaining = new ArrayDeque<>();
        private int success;
        private int failed;
        private boolean cancelled;

        private ClaimJob(UUID playerId, Town town, int total) {
            this.playerId = playerId;
            this.town = town;
            this.total = total;
        }
    }
}
