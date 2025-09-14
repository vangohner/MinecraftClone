package com.minecraftclone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class OcclusionDebugStatsTest {
    @Test
    public void testRecordAndReset() {
        WorldRenderer.OcclusionDebugStats stats = new WorldRenderer.OcclusionDebugStats();
        stats.recordResult(true);
        stats.recordResult(false);
        stats.recordPending();
        assertEquals(1, stats.visible);
        assertEquals(1, stats.occluded);
        assertEquals(1, stats.pending);
        stats.reset();
        assertEquals(0, stats.visible);
        assertEquals(0, stats.occluded);
        assertEquals(0, stats.pending);
    }
}
