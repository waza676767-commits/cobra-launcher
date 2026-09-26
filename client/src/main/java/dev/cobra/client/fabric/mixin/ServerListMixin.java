package dev.cobra.client.fabric.mixin;

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
 * The Cobra server is always first in the Multiplayer list and can't be deleted or have its
 * address changed (renaming it is fine).
 */
@Mixin(ServerList.class)
public abstract class ServerListMixin {
    @Unique
    private static final String COBRA_ADDRESS = "65.109.88.105:25577";

    @Shadow
    @Final
    private List<ServerInfo> servers;

    @Unique
    private void cobra$ensure() {
        ServerInfo ours = null;
        for (ServerInfo s : servers) {
            if (s != null && COBRA_ADDRESS.equals(s.address)) {
                ours = s;
                break;
            }
        }
        if (ours == null) ours = new ServerInfo("Cobra Server", COBRA_ADDRESS, ServerInfo.ServerType.OTHER);
        servers.remove(ours);
        servers.add(0, ours);
    }

    @Inject(method = "loadFile", at = @At("TAIL"), require = 0)
    private void cobra$afterLoad(CallbackInfo ci) {
        cobra$ensure();
    }

    @Inject(method = "saveFile", at = @At("HEAD"), require = 0)
    private void cobra$beforeSave(CallbackInfo ci) {
        cobra$ensure();
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true, require = 0)
    private void cobra$keep(ServerInfo info, CallbackInfo ci) {
        if (info != null && COBRA_ADDRESS.equals(info.address)) ci.cancel();
    }
}
