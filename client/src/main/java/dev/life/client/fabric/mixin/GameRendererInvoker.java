package dev.life.client.fabric.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Post effects (Motion Blur): set / clear / read the active one, whatever their visibility is. */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
    @Invoker("setPostProcessor")
    void life$setPostProcessor(Identifier id);

    @Invoker("clearPostProcessor")
    void life$clearPostProcessor();

    @Invoker("getPostProcessorId")
    Identifier life$getPostProcessorId();
}
