package soys.soysmyloot.spring.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 主线程同步工具。
 *
 * <p>SOYSHTTPOverMC 网关通过独立线程池（ExecutorService）调用控制器方法，
 * 不在 Bukkit 主线程上执行。涉及玩家背包 / 经济 / 点券 / 在线状态的读写必须回到
 * 主线程，否则会触发线程安全问题。本工具封装「必要时回主线程同步执行」的逻辑：
 * 若调用方已在主线程则直接执行，否则通过 {@link Bukkit#getScheduler()} 的
 * callSyncMethod 调度到下一个 tick 的主线程并返回结果。</p>
 */
public final class Sync {

    /** 主线程同步执行超时（秒）。超时通常意味着主线程已卡死。 */
    private static final long TIMEOUT_SECONDS = 15;

    private Sync() {
    }

    /**
     * 在主线程同步执行并取回结果。
     *
     * @param plugin 插件实例（用于获取调度器）
     * @param task   待执行的任务
     * @param <T>    返回值类型
     * @return 任务返回值
     */
    public static <T> T onMain(Plugin plugin, Callable<T> task) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return task.call();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        Future<T> future = Bukkit.getScheduler().callSyncMethod(plugin, task);
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            throw new RuntimeException("主线程同步执行超时（主线程可能已卡死）", te);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 在主线程同步执行（无返回值）。
     *
     * @param plugin 插件实例（用于获取调度器）
     * @param task   待执行的任务
     */
    public static void onMain(Plugin plugin, Runnable task) {
        onMain(plugin, () -> {
            task.run();
            return null;
        });
    }
}
