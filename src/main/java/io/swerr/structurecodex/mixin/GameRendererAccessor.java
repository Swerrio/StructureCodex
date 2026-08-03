package io.swerr.structurecodex.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.UiLightmap;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor("fogRenderer")
    FogRenderer structurecodex$fogRenderer();

    @Accessor("uiLightmap")
    UiLightmap structurecodex$uiLightmap();
}
