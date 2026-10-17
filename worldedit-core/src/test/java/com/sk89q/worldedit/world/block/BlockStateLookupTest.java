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
import com.sk89q.worldedit.registry.state.BooleanProperty;
import com.sk89q.worldedit.registry.state.EnumProperty;
import com.sk89q.worldedit.registry.state.IntegerProperty;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.registry.BlockRegistry;
import com.sk89q.worldedit.world.registry.BundledBlockRegistry;
import com.sk89q.worldedit.world.registry.BundledRegistries;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class BlockStateLookupTest extends BaseWorldEditTest {

    private static final String STAIRS_ID = "worldedit:test_stairs";
    private static final String DOOR_ID = "worldedit:test_door";
    private static final String WIRE_ID = "worldedit:test_redstone_wire";
    private static final String DENSE_ID = "worldedit:test_dense";

    private static final Map<String, Map<String, ? extends Property<?>>> PROPERTIES = new ConcurrentHashMap<>();

    @BeforeAll
    static void installPropertyRegistry() {
        BundledRegistries registries = new BundledRegistries() {
            private final BlockRegistry blockRegistry = new BundledBlockRegistry() {
                @Override
                public Map<String, ? extends Property<?>> getProperties(BlockType blockType) {
                    Map<String, ? extends Property<?>> extra = PROPERTIES.get(blockType.id());
                    return extra != null ? extra : super.getProperties(blockType);
                }
            };

            @Override
            public BlockRegistry getBlockRegistry() {
                return blockRegistry;
            }
        };
        when(MOCKED_PLATFORM.getRegistries()).thenReturn(registries);

        registerStairs();
        registerDoor();
        registerWire();
        registerDense();
    }

    @AfterAll
    static void tearDownTypes() throws Exception {
        when(MOCKED_PLATFORM.getRegistries()).thenReturn(new BundledRegistries() {
        });
        Field map = Registry.class.getDeclaredField("map");
        map.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, BlockType> registry = (Map<String, BlockType>) map.get(BlockType.REGISTRY);
        registry.remove(STAIRS_ID);
        registry.remove(DOOR_ID);
        registry.remove(WIRE_ID);
        registry.remove(DENSE_ID);
    }

    private static void registerStairs() {
        Map<String, Property<?>> properties = new HashMap<>();
        properties.put("facing", new EnumProperty("facing", List.of("north", "south", "west", "east")));
        properties.put("half", new EnumProperty("half", List.of("top", "bottom")));
        properties.put("shape", new EnumProperty(
            "shape",
            List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right")
        ));
        properties.put("waterlogged", new BooleanProperty("waterlogged", List.of(false, true)));
        PROPERTIES.put(STAIRS_ID, ImmutableMap.copyOf(properties));
        BlockType.REGISTRY.register(STAIRS_ID, new BlockType(STAIRS_ID));
    }

    private static void registerDoor() {
        Map<String, Property<?>> properties = new HashMap<>();
        properties.put("facing", new EnumProperty("facing", List.of("north", "south", "west", "east")));
        properties.put("half", new EnumProperty("half", List.of("upper", "lower")));
        properties.put("hinge", new EnumProperty("hinge", List.of("left", "right")));
        properties.put("open", new BooleanProperty("open", List.of(false, true)));
        properties.put("powered", new BooleanProperty("powered", List.of(false, true)));
        PROPERTIES.put(DOOR_ID, ImmutableMap.copyOf(properties));
        BlockType.REGISTRY.register(DOOR_ID, new BlockType(DOOR_ID));
    }

    private static void registerWire() {
        Map<String, Property<?>> properties = new HashMap<>();
        List<String> side = List.of("up", "side", "none");
        properties.put("east", new EnumProperty("east", side));
        properties.put("north", new EnumProperty("north", side));
        properties.put("south", new EnumProperty("south", side));
        properties.put("west", new EnumProperty("west", side));
        properties.put("power", new IntegerProperty(
            "power",
            IntStream.rangeClosed(0, 15).boxed().toList()
        ));
        PROPERTIES.put(WIRE_ID, ImmutableMap.copyOf(properties));
        BlockType.REGISTRY.register(WIRE_ID, new BlockType(WIRE_ID));
    }

    private static void registerDense() {
        Map<String, Property<?>> properties = new HashMap<>();
        List<Integer> ten = IntStream.range(0, 10).boxed().toList();
        properties.put("a", new IntegerProperty("a", ten));
        properties.put("b", new IntegerProperty("b", ten));
        properties.put("c", new IntegerProperty("c", ten));
        PROPERTIES.put(DENSE_ID, ImmutableMap.copyOf(properties));
        BlockType.REGISTRY.register(DENSE_ID, new BlockType(DENSE_ID));
    }

    @Test
    void stairsWithFacingByRegistryProperty() {
        BlockType type = BlockType.REGISTRY.get(STAIRS_ID);
        Property<String> facing = type.getProperty("facing");
        BlockState west = type.getDefaultState().with(facing, "west");
        assertEquals("west", west.getState(facing));
        assertTrue(west.getAsString().contains("facing=west"));
        assertEquals(80, type.getAllStates().size());
    }

    @Test
    void stairsWithFacingUsingEqualDistinctPropertyInstance() {
        BlockType type = BlockType.REGISTRY.get(STAIRS_ID);
        EnumProperty facingCopy = new EnumProperty("facing", List.of("north", "south", "west", "east"));
        BlockState west = type.getDefaultState().with(facingCopy, "west");
        assertEquals("west", west.getState(type.getProperty("facing")));
        assertNotSame(type.getDefaultState(), west);
    }

    @Test
    void stairsGetStateByPropertyMap() {
        BlockType type = BlockType.REGISTRY.get(STAIRS_ID);
        Map<Property<?>, Object> key = new HashMap<>(type.getDefaultState().getStates());
        key.put(type.getProperty("facing"), "east");
        key.put(type.getProperty("half"), "top");
        BlockState state = type.getState(key);
        assertEquals("east", state.getState(type.getProperty("facing")));
        assertEquals("top", state.getState(type.getProperty("half")));
    }

    @Test
    void doorIndependentProperties() {
        BlockType type = BlockType.REGISTRY.get(DOOR_ID);
        Property<Boolean> open = type.getProperty("open");
        Property<Boolean> powered = type.getProperty("powered");
        Property<String> hinge = type.getProperty("hinge");
        BlockState state = type.getDefaultState()
            .with(open, true)
            .with(powered, true)
            .with(hinge, "right");
        assertEquals(Boolean.TRUE, state.getState(open));
        assertEquals(Boolean.TRUE, state.getState(powered));
        assertEquals("right", state.getState(hinge));
        assertEquals(64, type.getAllStates().size());
    }

    @Test
    void redstoneWireSidesAndPower() {
        BlockType type = BlockType.REGISTRY.get(WIRE_ID);
        Property<String> east = type.getProperty("east");
        Property<Integer> power = type.getProperty("power");
        BlockState state = type.getDefaultState()
            .with(east, "up")
            .with(power, 15);
        assertEquals("up", state.getState(east));
        assertEquals(15, state.getState(power).intValue());
        assertEquals(3 * 3 * 3 * 3 * 16, type.getAllStates().size());
    }

    @Test
    void withUnknownValueKeepsOriginalState() {
        BlockType type = BlockType.REGISTRY.get(STAIRS_ID);
        BlockState original = type.getDefaultState();
        assertSame(original, original.with(type.getProperty("facing"), "up"));
    }

    @Test
    void equalsAndHashCodeStableAcrossWith() {
        BlockType type = BlockType.REGISTRY.get(STAIRS_ID);
        Property<String> facing = type.getProperty("facing");
        BlockState a = type.getDefaultState().with(facing, "south");
        BlockState b = type.getDefaultState().with(facing, "south");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertSame(a, b);
    }

    @Test
    void getStatesIsUnmodifiable() {
        BlockState state = BlockType.REGISTRY.get(STAIRS_ID).getDefaultState();
        assertThrows(UnsupportedOperationException.class, () -> state.getStates().clear());
    }

    @Test
    void denseTypeHasCartesianProductOfValues() {
        BlockType type = BlockType.REGISTRY.get(DENSE_ID);
        assertEquals(1000, type.getAllStates().size());
        IntegerProperty aCopy = new IntegerProperty("a", IntStream.range(0, 10).boxed().toList());
        BlockState nine = type.getDefaultState().with(aCopy, 9);
        assertEquals(9, nine.getState(type.getProperty("a")).intValue());
    }
}
