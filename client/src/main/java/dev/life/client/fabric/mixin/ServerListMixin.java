package dev.life.client.fabric.mixin;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * The Life server is always first in the Multiplayer list and can't be deleted or have its
 * address changed (renaming it is fine).
 */
@Mixin(ServerList.class)
public abstract class ServerListMixin {
    @Unique
    private static final String LIFE_ADDRESS = "65.109.88.105:25577";

    @Shadow
    @Final
    private List<ServerInfo> servers;

    @Unique
    private void life$ensure() {
        ServerInfo ours = null;
        for (ServerInfo s : servers) {
            if (s != null && LIFE_ADDRESS.equals(s.address)) {
                ours = s;
                break;
            }
        }
        if (ours == null) ours = new ServerInfo("Life Server", LIFE_ADDRESS, ServerInfo.ServerType.OTHER);
        else if ("Abyss Server".equals(ours.name) || "Cobra Server".equals(ours.name)) ours.name = "Life Server";   // its old names
        servers.remove(ours);
        servers.add(0, ours);
    }

    @Inject(method = "loadFile", at = @At("TAIL"), require = 0)
    private void life$afterLoad(CallbackInfo ci) {
        life$ensure();
    }

    @Inject(method = "saveFile", at = @At("HEAD"), require = 0)
    private void life$beforeSave(CallbackInfo ci) {
        life$ensure();
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true, require = 0)
    private void life$keep(ServerInfo info, CallbackInfo ci) {
        if (info != null && LIFE_ADDRESS.equals(info.address)) ci.cancel();
    }
}
