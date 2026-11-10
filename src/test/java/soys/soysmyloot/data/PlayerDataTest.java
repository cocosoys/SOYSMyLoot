package soys.soysmyloot.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PlayerData} 纯逻辑单元测试：进度累计、复合键、快照隔离与脏标记版本。
 */
class PlayerDataTest {

    private static final UUID UUID_A = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void progressAccumulatesAndSeparatesWorlds() {
        PlayerData data = new PlayerData(UUID_A);
        data.addDamage("world", "zombie", 10.5);
        data.addDamage("world", "zombie", 4.5);
        data.addKill("world", "zombie", 1);
        data.addDamage("nether", "zombie", 7.0);

        assertEquals(15.0, data.getDamage("world", "zombie"), 0.0001);
        assertEquals(7.0, data.getDamage("nether", "zombie"), 0.0001);
        assertEquals(1, data.getKills("world", "zombie"));
        assertEquals(0, data.getKills("nether", "zombie"));
        assertEquals(22.0, data.getTotalDamage(), 0.0001);
        assertEquals(1, data.getTotalKills());
    }

    @Test
    void compositeKeyRoundTripsIncludingEmptyWorld() {
        String key = PlayerData.keyOf("", "mythic_boss");
        String[] parts = PlayerData.splitKey(key);
        assertEquals("", parts[0]);
        assertEquals("mythic_boss", parts[1]);

        String[] withWorld = PlayerData.splitKey(PlayerData.keyOf("world_nether", "creeper"));
        assertEquals("world_nether", withWorld[0]);
        assertEquals("creeper", withWorld[1]);
    }

    @Test
    void snapshotIsIsolatedFromLaterWrites() {
        PlayerData data = new PlayerData(UUID_A);
        data.addDamage("world", "zombie", 5.0);
        data.setClaimed("daily", 1000L);

        PlayerData snapshot = data.snapshot();
        assertNotSame(data, snapshot);
        assertEquals(5.0, snapshot.getDamage("world", "zombie"), 0.0001);
        assertEquals(1, snapshot.getClaimCount("daily"));
        assertFalse(snapshot.isDirty(), "快照本身不应带脏标记");

        data.addDamage("world", "zombie", 5.0);
        data.setClaimed("daily", 2000L);

        assertEquals(5.0, snapshot.getDamage("world", "zombie"), 0.0001);
        assertEquals(1000L, snapshot.getLastClaim("daily"));
        assertEquals(10.0, data.getDamage("world", "zombie"), 0.0001);
        assertEquals(2000L, data.getLastClaim("daily"));
    }

    @Test
    void dirtyVersionOnlyClearedWhenNoNewWriteHappened() {
        PlayerData data = new PlayerData(UUID_A);
        assertEquals(0L, data.getVersion());
        assertFalse(data.isDirty());

        data.addKill("world", "zombie", 1);
        long savedVersion = data.getVersion();
        assertTrue(data.isDirty());

        // 保存期间没有新写入：允许清除
        assertTrue(data.clearDirtyIfVersion(savedVersion));
        assertFalse(data.isDirty());

        // 产生新写入后，旧的保存版本不能清除脏标记
        data.addKill("world", "zombie", 1);
        assertFalse(data.clearDirtyIfVersion(savedVersion));
        assertTrue(data.isDirty());
        assertTrue(data.getVersion() > savedVersion);
    }

    @Test
    void claimTracksCountAndTimestamp() {
        PlayerData data = new PlayerData(UUID_A);
        assertEquals(0L, data.getLastClaim("reward"));
        assertEquals(0, data.getClaimCount("reward"));

        data.setClaimed("reward", 1234L);
        assertEquals(1234L, data.getLastClaim("reward"));
        assertEquals(1, data.getClaimCount("reward"));

        data.setClaimed("reward", 5678L);
        assertEquals(5678L, data.getLastClaim("reward"));
        assertEquals(2, data.getClaimCount("reward"));
    }

    @Test
    void missingWorldIsTreatedAsGlobal() {
        PlayerData data = new PlayerData(UUID_A);
        data.addDamage(null, "zombie", 3.0);
        assertEquals(3.0, data.getDamage("", "zombie"), 0.0001);
    }
}
