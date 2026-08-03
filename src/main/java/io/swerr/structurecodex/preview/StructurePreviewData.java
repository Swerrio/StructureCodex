package io.swerr.structurecodex.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record StructurePreviewData(List<PlacedBlock> blocks,
                                   Map<BlockPos, BlockState> all,
                                   Vec3i size,
                                   int totalBlocks,
                                   float centreX,
                                   float centreY,
                                   float centreZ,
                                   List<BlockPos> extremes) {

    public record PlacedBlock(BlockPos pos, BlockState state) {
    }

    public static StructurePreviewData of(Map<BlockPos, BlockState> all, Vec3i size) {
        int sizeX = size.getX() + 2;
        int sizeY = size.getY() + 2;
        int sizeZ = size.getZ() + 2;

        boolean[] blocked = new boolean[sizeX * sizeY * sizeZ];
        for (Map.Entry<BlockPos, BlockState> entry : all.entrySet()) {
            if (entry.getValue().canOcclude()) {
                BlockPos pos = entry.getKey();
                blocked[index(pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, sizeX, sizeY)] = true;
            }
        }

        boolean[] outside = floodFromShell(blocked, sizeX, sizeY, sizeZ);

        List<PlacedBlock> visible = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : all.entrySet()) {
            BlockPos pos = entry.getKey();
            if (touchesOutside(outside, pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, sizeX, sizeY, sizeZ)) {
                visible.add(new PlacedBlock(pos, entry.getValue()));
            }
        }

        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        for (PlacedBlock block : visible) {
            sumX += block.pos().getX();
            sumY += block.pos().getY();
            sumZ += block.pos().getZ();
        }
        int count = Math.max(1, visible.size());

        return new StructurePreviewData(List.copyOf(visible), Map.copyOf(all), size, all.size(),
                (float) (sumX / count) + 0.5F,
                (float) (sumY / count) + 0.5F,
                (float) (sumZ / count) + 0.5F,
                extremePoints(visible));
    }

    public static List<BlockPos> enclosedCells(Map<BlockPos, BlockState> all, Vec3i size) {
        int sizeX = size.getX() + 2;
        int sizeY = size.getY() + 2;
        int sizeZ = size.getZ() + 2;

        boolean[] blocked = new boolean[sizeX * sizeY * sizeZ];
        for (BlockPos pos : all.keySet()) {
            blocked[index(pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, sizeX, sizeY)] = true;
        }

        boolean[] outside = floodFromShell(blocked, sizeX, sizeY, sizeZ);

        List<BlockPos> result = new ArrayList<>();
        for (int y = 0; y < size.getY(); y++) {
            for (int z = 0; z < size.getZ(); z++) {
                for (int x = 0; x < size.getX(); x++) {
                    int i = index(x + 1, y + 1, z + 1, sizeX, sizeY);
                    if (!blocked[i] && !outside[i]) {
                        result.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return result;
    }

    private static final int[] SUPPORT_STEPS = {-2, -1, 0, 1, 2};

    private static List<BlockPos> extremePoints(List<PlacedBlock> visible) {
        if (visible.isEmpty()) {
            return List.of();
        }

        List<int[]> directions = new ArrayList<>();
        for (int x : SUPPORT_STEPS) {
            for (int y : SUPPORT_STEPS) {
                for (int z : SUPPORT_STEPS) {
                    if (x != 0 || y != 0 || z != 0) {
                        directions.add(new int[]{x, y, z});
                    }
                }
            }
        }

        BlockPos[] support = new BlockPos[directions.size()];
        long[] reach = new long[directions.size()];
        Arrays.fill(reach, Long.MIN_VALUE);

        for (PlacedBlock block : visible) {
            BlockPos pos = block.pos();
            for (int i = 0; i < directions.size(); i++) {
                int[] direction = directions.get(i);
                long score = (long) direction[0] * pos.getX()
                        + (long) direction[1] * pos.getY()
                        + (long) direction[2] * pos.getZ();
                if (score > reach[i]) {
                    reach[i] = score;
                    support[i] = pos;
                }
            }
        }

        LinkedHashSet<BlockPos> points = new LinkedHashSet<>();
        for (BlockPos pos : support) {
            if (pos != null) {
                points.add(pos);
            }
        }
        return List.copyOf(points);
    }

    private static boolean[] floodFromShell(boolean[] blocked, int sizeX, int sizeY, int sizeZ) {
        boolean[] reached = new boolean[blocked.length];
        Deque<int[]> queue = new ArrayDeque<>();

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    boolean shell = x == 0 || y == 0 || z == 0
                            || x == sizeX - 1 || y == sizeY - 1 || z == sizeZ - 1;
                    if (shell) {
                        int i = index(x, y, z, sizeX, sizeY);
                        if (!blocked[i] && !reached[i]) {
                            reached[i] = true;
                            queue.add(new int[]{x, y, z});
                        }
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            for (Direction direction : Direction.values()) {
                int nx = current[0] + direction.getStepX();
                int ny = current[1] + direction.getStepY();
                int nz = current[2] + direction.getStepZ();
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ) {
                    continue;
                }
                int i = index(nx, ny, nz, sizeX, sizeY);
                if (!blocked[i] && !reached[i]) {
                    reached[i] = true;
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }

        return reached;
    }

    private static boolean touchesOutside(boolean[] outside, int x, int y, int z,
                                          int sizeX, int sizeY, int sizeZ) {
        for (Direction direction : Direction.values()) {
            int nx = x + direction.getStepX();
            int ny = y + direction.getStepY();
            int nz = z + direction.getStepZ();
            if (nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ) {
                continue;
            }
            if (outside[index(nx, ny, nz, sizeX, sizeY)]) {
                return true;
            }
        }
        return false;
    }

    private static int index(int x, int y, int z, int sizeX, int sizeY) {
        return (z * sizeY + y) * sizeX + x;
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public int longestSide() {
        return Math.max(size.getX(), Math.max(size.getY(), size.getZ()));
    }
}
