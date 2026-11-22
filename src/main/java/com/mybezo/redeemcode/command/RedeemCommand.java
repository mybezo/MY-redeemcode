package com.mybezo.redeemcode.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mybezo.redeemcode.RedeemCodePlugin;
import com.mybezo.redeemcode.gui.EditorMenu;
import com.mybezo.redeemcode.model.ClaimRecord;
import com.mybezo.redeemcode.model.RedeemCode;
import com.mybezo.redeemcode.util.Messages;
import com.mybezo.redeemcode.util.TimeUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static com.mybezo.redeemcode.util.Messages.ph;

/**
 * Brigadier implementation of {@code /redeem}.
 */
public final class RedeemCommand {

    public static final String ADMIN = "redeemcode.admin";
    public static final String USE = "redeemcode.use";
    private static final Set<String> RESERVED =
            Set.of("create", "edit", "delete", "info", "list", "reload", "resetclaims", "help");

    private final RedeemCodePlugin plugin;

    public RedeemCommand(RedeemCodePlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        Predicate<CommandSourceStack> admin = src -> src.getSender().hasPermission(ADMIN);

        return Commands.literal("redeem")
                .requires(src -> src.getSender().hasPermission(USE))
                .executes(this::help)
                .then(Commands.literal("help").executes(this::help))
                .then(Commands.literal("create").requires(admin)
                        .then(Commands.argument("code", StringArgumentType.word()).executes(this::create)))
                .then(Commands.literal("edit").requires(admin)
                        .then(Commands.argument("code", StringArgumentType.word())
                                .suggests(codes()).executes(this::edit)))
                .then(Commands.literal("delete").requires(admin)
                        .then(Commands.argument("code", StringArgumentType.word())
                                .suggests(codes()).executes(this::delete)))
                .then(Commands.literal("info").requires(admin)
                        .then(Commands.argument("code", StringArgumentType.word())
                                .suggests(codes()).executes(this::info)))
                .then(Commands.literal("resetclaims").requires(admin)
                        .then(Commands.argument("code", StringArgumentType.word())
                                .suggests(codes()).executes(ctx -> resetClaims(ctx, null))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(claimers())
                                        .executes(ctx -> resetClaims(ctx, StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("list").requires(admin).executes(this::list))
                .then(Commands.literal("reload").requires(admin).executes(this::reload))
                // Codes are intentionally NOT suggested to regular players.
                .then(Commands.argument("code", StringArgumentType.word()).executes(this::redeem))
                .build();
    }

    private SuggestionProvider<CommandSourceStack> codes() {
        return (ctx, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            plugin.storage().all().stream()
                    .map(RedeemCode::name)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(remaining))
                    .sorted()
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> claimers() {
        return (ctx, builder) -> {
            RedeemCode code = plugin.storage().get(StringArgumentType.getString(ctx, "code"));
            if (code != null) {
                String remaining = builder.getRemainingLowerCase();
                code.claims().values().stream().map(ClaimRecord::name)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(remaining))
                        .limit(50).forEach(builder::suggest);
            }
            return builder.buildFuture();
        };
    }

    private int help(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Messages msg = plugin.messages();
        msg.sendRaw(sender, "help-header");
        msg.sendRaw(sender, "help-player");
        if (sender.hasPermission(ADMIN)) {
            msg.list("help-admin").forEach(sender::sendMessage);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int redeem(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return 0;
        }
        plugin.redeemService().redeem(player, StringArgumentType.getString(ctx, "code"));
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "code");
        String pattern = plugin.getConfig().getString("settings.code-pattern", "^[A-Za-z0-9_-]{3,32}$");
        if (!name.matches(pattern)) {
            plugin.messages().send(sender, "invalid-code-format");
            return 0;
        }
        if (RESERVED.contains(name.toLowerCase(Locale.ROOT))) {
            plugin.messages().send(sender, "reserved-code");
            return 0;
        }
        if (plugin.storage().exists(name)) {
            plugin.messages().send(sender, "code-exists", ph("code", name));
            return 0;
        }
        RedeemCode code = new RedeemCode(name, System.currentTimeMillis(), sender.getName());
        plugin.storage().add(code);
        plugin.messages().send(sender, "code-created", ph("code", name));
        if (sender instanceof Player player) {
            new EditorMenu(plugin, player, code).open();
        }
        return Command.SINGLE_SUCCESS;
    }

    private int edit(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return 0;
        }
        RedeemCode code = find(ctx);
        if (code == null) {
            return 0;
        }
        new EditorMenu(plugin, player, code).open();
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<CommandSourceStack> ctx) {
        RedeemCode code = find(ctx);
        if (code == null) {
            return 0;
        }
        plugin.storage().remove(code.key());
        plugin.messages().send(ctx.getSource().getSender(), "code-deleted", ph("code", code.name()));
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        CommandSender s = ctx.getSource().getSender();
        RedeemCode code = find(ctx);
        if (code == null) {
            return 0;
        }
        String max = code.maxClaims() == 0 ? "Unlimited" : String.valueOf(code.maxClaims());
        String perIp = code.maxPerIp() == 0 ? "Unlimited" : String.valueOf(code.maxPerIp());
        String expiry = code.expiresAt() == 0 ? "Never" : TimeUtil.formatDate(code.expiresAt())
                + (code.isExpired() ? " (expired)" : "");

        s.sendMessage(Messages.mm("<gold><st>          </st> <yellow><bold><code></bold> <gold><st>          </st>", ph("code", code.name())));
        line(s, "Status", code.enabled() ? "<green>Enabled" : "<red>Disabled");
        line(s, "Claims", "<white>" + code.claimCount() + "/" + max);
        line(s, "Limit per IP", "<white>" + perIp);
        line(s, "Expires", "<white>" + expiry);
        line(s, "Permission", "<white>" + (code.permission() == null ? "None" : "<perm>"), ph("perm", String.valueOf(code.permission())));
        line(s, "Item rewards", "<white>" + code.items().size() + " stack(s)");
        line(s, "Commands", "<white>" + code.commands().size());
        for (String cmd : code.commands()) {
            s.sendMessage(Messages.mm("  <dark_gray>» <gray><cmd>", ph("cmd", cmd)));
        }
        line(s, "Created", "<white>" + TimeUtil.formatDate(code.createdAt()) + " by <by>", ph("by", code.createdBy()));

        List<ClaimRecord> recent = new ArrayList<>(code.claims().values());
        recent.sort(Comparator.comparingLong(ClaimRecord::timestamp).reversed());
        if (!recent.isEmpty()) {
            s.sendMessage(Messages.mm("<gray>Latest claims:"));
            boolean showIp = s.hasPermission(ADMIN);
            for (ClaimRecord c : recent.subList(0, Math.min(10, recent.size()))) {
                s.sendMessage(Messages.mm("  <dark_gray>» <white><name></white> <gray><date><ip>",
                        ph("name", c.name()), ph("date", TimeUtil.formatDate(c.timestamp())),
                        ph("ip", showIp ? " (" + c.ip() + ")" : "")));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void line(CommandSender s, String label, String value, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... r) {
        s.sendMessage(Messages.mm("<gray>" + label + ": " + value, r));
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<RedeemCode> all = new ArrayList<>(plugin.storage().all());
        if (all.isEmpty()) {
            plugin.messages().send(sender, "list-empty");
            return Command.SINGLE_SUCCESS;
        }
        all.sort(Comparator.comparing(RedeemCode::key));
        plugin.messages().send(sender, "list-header", ph("count", all.size()));
        for (RedeemCode code : all) {
            Component status = Messages.mm(!code.enabled() ? "<red>●" : code.isExpired() || code.isFull() ? "<gold>●" : "<green>●");
            Component line = plugin.messages().get("list-entry",
                    Messages.phc("status", status),
                    ph("code", code.name()),
                    ph("claims", code.claimCount()),
                    ph("max", code.maxClaims() == 0 ? "∞" : code.maxClaims()));
            sender.sendMessage(line.clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/redeem info " + code.name()))
                    .hoverEvent(Messages.mm("<yellow>Click to view details")));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int resetClaims(CommandContext<CommandSourceStack> ctx, String playerName) {
        CommandSender sender = ctx.getSource().getSender();
        RedeemCode code = find(ctx);
        if (code == null) {
            return 0;
        }
        if (playerName == null) {
            code.claims().clear();
            plugin.storage().save();
            plugin.messages().send(sender, "claims-reset", ph("code", code.name()));
            return Command.SINGLE_SUCCESS;
        }
        UUID target = null;
        for (Map.Entry<UUID, ClaimRecord> e : code.claims().entrySet()) {
            if (e.getValue().name().equalsIgnoreCase(playerName)) {
                target = e.getKey();
                break;
            }
        }
        if (target == null) {
            plugin.messages().send(sender, "claims-reset-player-none", ph("player", playerName), ph("code", code.name()));
            return 0;
        }
        code.claims().remove(target);
        plugin.storage().save();
        plugin.messages().send(sender, "claims-reset-player", ph("player", playerName), ph("code", code.name()));
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        plugin.reload();
        plugin.messages().send(ctx.getSource().getSender(), "reloaded");
        return Command.SINGLE_SUCCESS;
    }

    private RedeemCode find(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "code");
        RedeemCode code = plugin.storage().get(name);
        if (code == null) {
            plugin.messages().send(ctx.getSource().getSender(), "code-not-found", ph("code", name));
        }
        return code;
    }
}
