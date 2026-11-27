package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compact wire/save format for a ship's voxel content.
 *
 * <pre>
 * int     blockCount
 * repeat  short x, short y, short z (local coordinates), int blockStateId
 * </pre>
 *
 * <p>The encoded bytes are Base64 encoded so they can travel inside a plain
 * synched-data string field, which works on every 1.20.1 version.</p>
 */
public final class ShipStructureCodec {
    private static final int BYTES_PER_VOXEL = Short.BYTES * 3 + Integer.BYTES;

    private ShipStructureCodec() {
    }

    public static byte[] encodeBytes(ShipStructure structure) {
        Map<BlockPos, BlockState> voxels = structure.voxels();
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + voxels.size() * BYTES_PER_VOXEL);
        buffer.putInt(voxels.size());
        for (Map.Entry<BlockPos, BlockState> entry : voxels.entrySet()) {
            BlockPos pos = entry.getKey();
            buffer.putShort((short) pos.getX());
            buffer.putShort((short) pos.getY());
            buffer.putShort((short) pos.getZ());
            buffer.putInt(Block.BLOCK_STATE_REGISTRY.getId(entry.getValue()));
        }
        return buffer.array();
    }

    public static String encode(ShipStructure structure) {
        return Base64.getEncoder().encodeToString(encodeBytes(structure));
    }

    public static ShipStructure decode(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        return decodeBytes(Base64.getDecoder().decode(base64));
    }

    public static ShipStructure decodeBytes(byte[] data) {
        if (data == null || data.length < Integer.BYTES) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int count = buffer.getInt();
        if (count <= 0 || count * BYTES_PER_VOXEL > buffer.remaining()) {
            return null;
        }

        Map<BlockPos, BlockState> voxels = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            int x = buffer.getShort();
            int y = buffer.getShort();
            int z = buffer.getShort();
            int stateId = buffer.getInt();
            voxels.put(new BlockPos(x, y, z), Block.stateById(stateId));
        }
        return voxels.isEmpty() ? null : ShipStructure.of(voxels);
    }
}
