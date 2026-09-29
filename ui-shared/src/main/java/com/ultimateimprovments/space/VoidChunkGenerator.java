package com.ultimateimprovments.space;

import org.bukkit.generator.ChunkGenerator;

/**
 * Produces completely empty (void) chunks for the space dimension.
 * <p>
 * Paper 26.3 deprecated the {@code generateChunkData}/{@code createChunkData}
 * override pair — an empty generator is now expressed purely by disabling
 * every vanilla generation stage ({@code shouldGenerate*} below); the base
 * class then fills the chunk with air on its own.
 */
public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public boolean shouldGenerateNoise() { return false; }

    @Override
    public boolean shouldGenerateSurface() { return false; }

    @Override
    public boolean shouldGenerateBedrock() { return false; }

    @Override
    public boolean shouldGenerateCaves() { return false; }

    @Override
    public boolean shouldGenerateDecorations() { return false; }

    @Override
    public boolean shouldGenerateMobs() { return false; }

    @Override
    public boolean shouldGenerateStructures() { return false; }
}
