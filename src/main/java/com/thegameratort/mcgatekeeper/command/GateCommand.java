package com.thegameratort.mcgatekeeper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.thegameratort.mcgatekeeper.Mcgatekeeper;
import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import com.thegameratort.mcgatekeeper.auth.KeyStore;
import com.thegameratort.mcgatekeeper.auth.PendingAuthManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class GateCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("gate")
                .requires(source -> source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.ADMINS)))
                .then(literal("allow")
                    .then(argument("player", StringArgumentType.word())
                        .suggests(GateCommand::suggestPendingPlayers)
                        .then(argument("label", StringArgumentType.word())
                            .executes(GateCommand::executeAllow))))
                .then(literal("reset")
                    .then(argument("player", StringArgumentType.word())
                        .suggests(GateCommand::suggestKnownPlayers)
                        .executes(GateCommand::executeResetAll)
                        .then(argument("label", StringArgumentType.word())
                            .executes(GateCommand::executeResetLabel))))
                .then(literal("list")
                    .then(argument("player", StringArgumentType.word())
                        .suggests(GateCommand::suggestKnownPlayers)
                        .executes(GateCommand::executeList)))
        );
    }

    // -------------------------------------------------------------------------
    // Suggestion providers
    // -------------------------------------------------------------------------

    private static CompletableFuture<Suggestions> suggestPendingPlayers(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        for (UUID uuid : PendingAuthManager.getPendingUuids()) {
            String name = PendingAuthManager.getUsername(uuid);
            if (name != null) builder.suggest(name);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestKnownPlayers(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        for (String uuidStr : Mcgatekeeper.KEY_STORE.getAllUuids()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                builder.suggest(Mcgatekeeper.KEY_STORE.getUsername(uuid));
            } catch (IllegalArgumentException ignored) {}
        }
        return builder.buildFuture();
    }

    // -------------------------------------------------------------------------
    // /gate allow <player> <label>
    // -------------------------------------------------------------------------

    private static int executeAllow(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String label = StringArgumentType.getString(ctx, "label");
        ServerCommandSource source = ctx.getSource();

        UUID uuid = findPendingByName(playerName);
        if (uuid == null) {
            source.sendError(Text.literal(playerName + " is not currently pending authorization."));
            return 0;
        }

        String pendingKey = PendingAuthManager.getPendingPublicKey(uuid);
        if (pendingKey == null) {
            source.sendError(Text.literal(playerName + " has not sent a key response yet."));
            return 0;
        }

        Mcgatekeeper.KEY_STORE.addKey(uuid, playerName, label, pendingKey);
        Mcgatekeeper.KEY_STORE.save();

        PendingAuthManager.complete(uuid);
        source.sendFeedback(() -> Text.literal("Registered key [" + label + "] for " + playerName + " and released."), true);
        return 1;
    }

    // -------------------------------------------------------------------------
    // /gate reset <player> [<label>]
    // -------------------------------------------------------------------------

    private static int executeResetAll(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerCommandSource source = ctx.getSource();

        UUID uuid = resolveUuid(playerName, source);
        if (uuid == null) return 0;

        Mcgatekeeper.KEY_STORE.removeAllKeys(uuid);
        Mcgatekeeper.KEY_STORE.save();
        source.sendFeedback(() -> Text.literal("Removed all keys for " + playerName + "."), true);
        return 1;
    }

    private static int executeResetLabel(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String label = StringArgumentType.getString(ctx, "label");
        ServerCommandSource source = ctx.getSource();

        UUID uuid = resolveUuid(playerName, source);
        if (uuid == null) return 0;

        boolean removed = Mcgatekeeper.KEY_STORE.removeKey(uuid, label);
        Mcgatekeeper.KEY_STORE.save();
        if (removed) {
            source.sendFeedback(() -> Text.literal("Removed key [" + label + "] for " + playerName + "."), true);
            return 1;
        } else {
            source.sendError(Text.literal("No key with label '" + label + "' found for " + playerName + "."));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // /gate list <player>
    // -------------------------------------------------------------------------

    private static int executeList(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerCommandSource source = ctx.getSource();

        UUID uuid = resolveUuid(playerName, source);
        if (uuid == null) return 0;

        List<KeyStore.KeyEntry> keys = Mcgatekeeper.KEY_STORE.getKeys(uuid);
        if (keys.isEmpty()) {
            source.sendFeedback(() -> Text.literal(playerName + " has no registered keys."), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal(playerName + " — " + keys.size() + " key(s):"), false);
        for (KeyStore.KeyEntry entry : keys) {
            String fp = Ed25519Util.fingerprint(entry.publicKey());
            source.sendFeedback(() -> Text.literal("  [" + entry.label() + "]  fp: " + fp + "..."), false);
        }
        return 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Finds the UUID of a player currently pending authorization by their username. */
    private static UUID findPendingByName(String playerName) {
        for (UUID uuid : PendingAuthManager.getPendingUuids()) {
            if (playerName.equalsIgnoreCase(PendingAuthManager.getUsername(uuid))) {
                return uuid;
            }
        }
        return null;
    }

    /** Resolves a UUID for /reset and /list — tries online players then the name→id cache. */
    private static UUID resolveUuid(String playerName, ServerCommandSource source) {
        var online = source.getServer().getPlayerManager().getPlayer(playerName);
        if (online != null) return online.getUuid();

        Optional<PlayerConfigEntry> profile = source.getServer().getApiServices().nameToIdCache().findByName(playerName);
        if (profile.isPresent()) return profile.get().id();

        source.sendError(Text.literal("Could not find player: " + playerName));
        return null;
    }
}
