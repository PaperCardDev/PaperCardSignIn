package cn.paper_card.sign_in;

import cn.paper_card.mc_command.TheMcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

class MainCommand extends TheMcCommand.HasSub {

    private final @NotNull Permission permission;

    private final @NotNull ThePlugin plugin;

    public MainCommand(@NotNull ThePlugin plugin) {
        super("sign-in");
        this.plugin = plugin;
        this.permission = Objects.requireNonNull(plugin.getServer().getPluginManager().getPermission("paper-card-sign-in.command"));

        final PluginCommand command = plugin.getCommand(this.getLabel());
        assert command != null;
        command.setExecutor(this);
        command.setTabCompleter(this);

        this.addSubCommand(new TodayYesterdays(true));
        this.addSubCommand(new TodayYesterdays(false));
        this.addSubCommand(new Reload());
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    class TodayYesterdays extends TheMcCommand {

        private final @NotNull Permission permission;

        private final boolean isToday;

        protected TodayYesterdays(boolean isToday) {
            super(isToday ? "today" : "yesterday");
            this.isToday = isToday;
            this.permission = plugin.addPermission(MainCommand.this.permission.getName() + "." + this.getLabel());
        }


        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {

                final SignInServiceImpl service = plugin.getSignInService();

                final long cur = System.currentTimeMillis();

                final long begin;

                final long ONE_DAY = 24 * 60 * 60 * 1000L;

                if (this.isToday) {
                    begin = plugin.getTodayBeginTime(cur);
                } else {
                    begin = plugin.getTodayBeginTime(cur) + ONE_DAY;
                }


                final List<SignInInfo> list;
                try {
                    list = service.queryAllTimeBetween(begin, begin + ONE_DAY);
                } catch (SQLException e) {
                    plugin.getSLF4JLogger().error("", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                final TextComponent.Builder text = Component.text();

                plugin.appendPrefix(text);
                text.appendSpace();
                text.append(Component.text("%s一共有 ".formatted(this.isToday ? "今日" : "昨日")).color(NamedTextColor.GREEN));
                text.append(Component.text(list.size()).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                text.append(Component.text(" 个玩家签到：").color(NamedTextColor.GREEN));

                final StringBuilder builder = new StringBuilder();
                for (SignInInfo signInInfo : list) {
                    final String name = plugin.getServer().getOfflinePlayer(signInInfo.playerId()).getName();
                    builder.append(name != null ? name : "null");
                    builder.append(' ');
                }

                text.append(Component.text(builder.toString()));

                commandSender.sendMessage(text.build());
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

    class Reload extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Reload() {
            super("reload");
            this.permission = plugin.addPermission(MainCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            plugin.reloadConfig();
            final TextComponent.Builder text = Component.text();
            plugin.appendPrefix(text);
            text.appendSpace();
            text.append(Component.text("已重载配置").color(NamedTextColor.GREEN));

            commandSender.sendMessage(text.build());
            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

}
