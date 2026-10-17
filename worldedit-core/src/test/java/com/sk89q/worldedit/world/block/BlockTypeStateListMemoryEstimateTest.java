/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.world.block;

import com.google.common.collect.ImmutableMap;
import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.registry.Registry;
import com.sk89q.worldedit.registry.state.IntegerProperty;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.registry.BlockRegistry;
import com.sk89q.worldedit.world.registry.BundledBlockRegistry;
import com.sk89q.worldedit.world.registry.BundledRegistries;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Estimates retained neighbour-index memory of the old per-{@link BlockState}
 * Guava tables versus the shared array + stride index.
 */
class BlockTypeStateListMemoryEstimateTest extends BaseWorldEditTest {

    private static final String TYPE_ID = "worldedit:memory_estimate";

    /**
     * Conservative retained size of one Guava {@code ImmutableTable} cell
     * (row/column/value pointers plus map entries), matching the heap-dump
     * mix of {@code ImmutableMapEntry} / {@code SparseImmutableTable} seen
     * on heavily modded 7.3.8 servers.
     */
    private static final long OLD_BYTES_PER_NEIGHBOUR_ENTRY = 96L;

    @BeforeAll
    static void installType() {
        List<Integer> values = IntStream.range(0, 8).boxed().toList();
        Map<String, Property<?>> properties = new LinkedHashMap<>();
        properties.put("p0", new IntegerProperty("p0", values));
        properties.put("p1", new IntegerProperty("p1", values));
        properties.put("p2", new IntegerProperty("p2", values));
        properties.put("p3", new IntegerProperty("p3", values));
        Map<String, ? extends Property<?>> frozen = ImmutableMap.copyOf(properties);

        BundledRegistries registries = new BundledRegistries() {
            private final BlockRegistry blockRegistry = new BundledBlockRegistry() {
                @Override
                public Map<String, ? extends Property<?>> getProperties(BlockType blockType) {
                    if (TYPE_ID.equals(blockType.id())) {
                        return frozen;
                    }
                    return super.getProperties(blockType);
                }
            };

            @Override
            public BlockRegistry getBlockRegistry() {
                return blockRegistry;
            }
        };
        when(MOCKED_PLATFORM.getRegistries()).thenReturn(registries);
        BlockType.REGISTRY.register(TYPE_ID, new BlockType(TYPE_ID));
    }

    @AfterAll
    static void tearDownType() throws Exception {
        when(MOCKED_PLATFORM.getRegistries()).thenReturn(new BundledRegistries() {
        });
        Field map = Registry.class.getDeclaredField("map");
        map.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, BlockType> registry = (Map<String, BlockType>) map.get(BlockType.REGISTRY);
        registry.remove(TYPE_ID);
    }

    @Test
    void neighbourTableWouldDominateSharedList() {
        BlockType type = BlockType.REGISTRY.get(TYPE_ID);
        List<BlockState> states = type.getAllStates();
        assertEquals(8 * 8 * 8 * 8, states.size());

        long neighbourEntries = 0;
        for (Property<?> property : type.getProperties()) {
            neighbourEntries += (long) (property.getValues().size() - 1);
        }
        neighbourEntries *= states.size();

        long oldIndexBytes = neighbourEntries * OLD_BYTES_PER_NEIGHBOUR_ENTRY;
        // New index: one BlockState reference per state in a shared array (~8 bytes + header),
        // plus a handful of PropertyEntry stride records. Values maps remain either way.
        long newIndexBytes = 16L * states.size() + 256L;

        assertTrue(oldIndexBytes > newIndexBytes * 20,
            () -> "Expected old neighbour tables to be much larger than the shared list. old="
                + oldIndexBytes + " new=" + newIndexBytes);

        System.out.println("State-map memory estimate for " + states.size() + " states / "
            + type.getProperties().size() + " properties:");
        System.out.println("  old neighbour-table cells: " + neighbourEntries
            + " (~" + formatMib(oldIndexBytes) + " if ~" + OLD_BYTES_PER_NEIGHBOUR_ENTRY + " B/cell)");
        System.out.println("  new shared list index: ~" + formatMib(newIndexBytes));
        System.out.println("  ATM10-scale (1.6e6 states) with ~9 neighbours/state: old ~"
            + formatMib(1_600_000L * 9L * OLD_BYTES_PER_NEIGHBOUR_ENTRY)
            + ", new index ~" + formatMib(16L * 1_600_000L));
    }

    private static String formatMib(long bytes) {
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }
}
