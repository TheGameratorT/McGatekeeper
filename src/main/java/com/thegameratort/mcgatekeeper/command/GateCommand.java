package com.thegameratort.mcgatekeeper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.thegameratort.mcgatekeeper.Mcgatekeeper;
import com.thegameratort.mcgatekeeper.auth.ChallengeStore;
import com.thegameratort.mcgatekeeper.auth.Ed25519Util;
import com.thegameratort.mcgatekeeper.auth.KeyStore;
import com.thegameratort.mcgatekeeper.limbo.LimboManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
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
                        .suggests(GateCommand::suggestLimboPlayers)
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

    private static CompletableFuture<Suggestions> suggestLimboPlayers(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        for (ServerPlayerEntity p : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
            if (LimboManager.isInLimbo(p.getUuid())) {
                builder.suggest(p.getGameProfile().name());
            }
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

        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(playerName);
        if (target == null || !LimboManager.isInLimbo(target.getUuid())) {
            source.sendError(Text.literal(playerName + " is not currently in limbo."));
            return 0;
        }

        String pendingKey = ChallengeStore.getPendingPublicKey(target.getUuid());
        if (pendingKey == null) {
            source.sendError(Text.literal(playerName + " has not sent a key response yet."));
            return 0;
        }

        Mcgatekeeper.KEY_STORE.addKey(target.getUuid(), target.getGameProfile().name(), label, pendingKey);
        Mcgatekeeper.KEY_STORE.save();

        LimboManager.release(source.getServer(), target);
        source.sendFeedback(() -> Text.literal("Registered key [" + label + "] for " + playerName + " and released from limbo."), true);
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

    private static UUID resolveUuid(String playerName, ServerCommandSource source) {
        // Try online player first
        ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(playerName);
        if (online != null) return online.getUuid();

        // Fall back to name→id cache for offline players
        Optional<PlayerConfigEntry> profile = source.getServer().getApiServices().nameToIdCache().findByName(playerName);
        if (profile.isPresent()) return profile.get().id();

        source.sendError(Text.literal("Could not find player: " + playerName));
        return null;
    }
}
