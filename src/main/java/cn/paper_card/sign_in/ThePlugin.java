package cn.paper_card.sign_in;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.player_coins.api.PlayerCoinsApi;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.*;

public class ThePlugin extends JavaPlugin implements Listener {

    private final @NotNull HashMap<UUID, Long> signInBeginTime;

    private final @NotNull TaskScheduler taskScheduler;

    private SignInServiceImpl signInService = null;

    private PlayerCoinsApi playerCoinsApi = null;

    private final static String PATH_NEED_ONLINE_TIME = "need-online-time";
    private final static String PATH_HARVEST_COINS = "harvest-coins";


    public ThePlugin() {
        this.signInBeginTime = new HashMap<>();
        this.taskScheduler = UniversalScheduler.getScheduler(this);
    }


    long getTodayBeginTime(long cur) {
        final long delta = (cur + TimeZone.getDefault().getRawOffset()) % (24 * 60 * 60 * 1000L);
        return cur - delta;
    }


    @Override
    public void onLoad() {
        final DatabaseApi api = this.getServer().getServicesManager().load(DatabaseApi.class);
        if (api == null) throw new NoSuchElementException("未加载%s！".formatted(DatabaseApi.class.getSimpleName()));

        final DatabaseApi.MySqlConnection connectionNormal = api.getRemoteMySQL().getConnectionNormal();

        this.signInService = new SignInServiceImpl(connectionNormal);
    }

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);

        new MainCommand(this);

        this.setNeedOnlineTime(this.getNeedOnlineTime());
        this.setHarvestCoins(this.getHarvestCoins());
        this.saveConfig();

        taskScheduler.runTaskTimerAsynchronously(this::checkAllSignIn, 20, 20);

        this.playerCoinsApi = this.getServer().getServicesManager().load(PlayerCoinsApi.class);
        if (this.playerCoinsApi == null) {
            getSLF4JLogger().warn("未连接到PlayerCoinsApi");
        } else {
            getSLF4JLogger().info("已连接到PlayerCoinsApi");
        }
    }

    @Override
    public void onDisable() {
        this.playerCoinsApi = null;
        this.taskScheduler.cancelTasks(this);
        this.saveConfig();

        if (this.signInService != null) {
            try {
                this.signInService.close();
            } catch (SQLException e) {
                getSLF4JLogger().error("", e);
            }
            this.signInService = null;
        }
    }

    private long getNeedOnlineTime() {
        return getConfig().getLong(PATH_NEED_ONLINE_TIME, 30L * 60L * 1000L);
    }

    void setNeedOnlineTime(long v) {
        getConfig().set(PATH_NEED_ONLINE_TIME, v);
    }

    int getHarvestCoins() {
        return getConfig().getInt(PATH_HARVEST_COINS, 6);
    }

    void setHarvestCoins(int v) {
        getConfig().set(PATH_HARVEST_COINS, v);
    }


    private void onPlayerSignIn(@NotNull Player player, @NotNull SignInServiceImpl service, long todayBegin) {

        final PlayerCoinsApi api = this.playerCoinsApi;
        final int c = getHarvestCoins();

        String coins;
        if (api != null) {
            try {
                api.addCoins(player.getUniqueId(), c);
                coins = "%d".formatted(c);
            } catch (Exception e) {
                getSLF4JLogger().error("", e);
                coins = "ERROR";
            }
        } else {
            coins = "NULL";
        }

        // 查询序号
        String no;
        try {
            final Integer n = service.queryNo(player.getUniqueId(), todayBegin);
            if (n != null)
                no = "%d".formatted(n);
            else no = "NULL";
        } catch (SQLException e) {
            this.getSLF4JLogger().error("", e);
            no = "ERROR";
        }

        final String finalCoins = coins;
        final String finalNo = no;

        taskScheduler.runTask(player, () -> {

            final TextComponent.Builder text = Component.text();
            appendPrefix(text);

            final TextComponent build = text.appendSpace()
                    .append(player.displayName())
                    .append(Component.text(" 完成了今日签到，并获得 "))
                    .append(Component.text(finalCoins).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                    .append(Component.text(" 枚硬币，"))
                    .append(Component.text("这是今天第 "))
                    .append(Component.text(finalNo).color(NamedTextColor.YELLOW))
                    .append(Component.text(" 个签到的玩家~"))
                    .build().color(NamedTextColor.GREEN);

            getServer().broadcast(build);

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
        });
    }

    private void checkAllSignIn() {
        final SignInServiceImpl service = this.signInService;

        assert service != null;

        final long current = System.currentTimeMillis();
        final long todayBegin = this.getTodayBeginTime(current);

        synchronized (this.signInBeginTime) {

            final LinkedList<UUID> removes = new LinkedList<>();

            for (UUID uuid : this.signInBeginTime.keySet()) {
                final Long begin = this.signInBeginTime.get(uuid);
                if (begin == null) continue;

                // 时长不足
                if (current - begin < this.getNeedOnlineTime()) continue;

                taskScheduler.runTaskAsynchronously(() -> {
                    // 添加签到记录
                    try {
                        service.addSignIn(new SignInInfo(uuid, current));
                    } catch (Exception e) {
                        getSLF4JLogger().error("", e);
                        return;
                    }

                    // 完成签到，做一些动作
                    final Player player = getServer().getPlayer(uuid);
                    if (player != null) this.onPlayerSignIn(player, service, todayBegin);
                });


                // 在这里移除, 在下一次循环会抛出异常
                // this.signInBeginTime.remove(uuid);
                removes.add(uuid);
            }

            final int size = removes.size();

            for (final UUID uuid : removes) {
                this.signInBeginTime.remove(uuid);
            }

            if (size > 0) getSLF4JLogger().info("%d个玩家已经完成签到".formatted(size));
        }
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        final SignInService service = this.signInService;

        assert service != null;

        taskScheduler.runTaskAsynchronously(() -> {
            // 检查是否已经签到了

            final SignInInfo signInInfo;

            final long cur = System.currentTimeMillis();

            try {
                signInInfo = service.queryOneTimeAfter(player.getUniqueId(), getTodayBeginTime(cur));
            } catch (Exception e) {
                getSLF4JLogger().error("", e);
                return;
            }

            if (signInInfo != null) {
                getSLF4JLogger().info("玩家%s已完成今日签到".formatted(player.getName()));
                return;
            }

            synchronized (this.signInBeginTime) {
                this.signInBeginTime.put(player.getUniqueId(), cur);
            }
        });
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        synchronized (this.signInBeginTime) {
            final Long begin = this.signInBeginTime.remove(player.getUniqueId());
            if (begin != null) {
                getSLF4JLogger().info("玩家[%s]中断签到".formatted(player.getName()));
            }
        }
    }

    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        getServer().getPluginManager().addPermission(permission);
        return permission;
    }

    @NotNull TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }

    @NotNull SignInServiceImpl getSignInService() {
        return this.signInService;
    }

    void appendPrefix(@NotNull TextComponent.Builder text) {
        text.append(Component.text("[").color(NamedTextColor.GRAY));
        text.append(Component.text("每日签到").color(NamedTextColor.DARK_AQUA));
        text.append(Component.text("]").color(NamedTextColor.GRAY));
    }

    void sendException(@NotNull CommandSender sender, @NotNull Throwable e) {
        final TextComponent.Builder text = Component.text();

        appendPrefix(text);
        text.appendSpace();

        text.append(Component.text("==== 异常信息 ====").color(NamedTextColor.DARK_RED));

        for (Throwable t = e; t != null; t = t.getCause()) {
            text.appendNewline();
            text.append(Component.text(t.toString()).color(NamedTextColor.RED));
        }

        sender.sendMessage(text.build());
    }
}