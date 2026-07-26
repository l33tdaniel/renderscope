package dev.renderscope.client;

import dev.renderscope.RenderScope;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = RenderScope.MOD_ID, dist = Dist.CLIENT)
public final class RenderScopeClient {
    public RenderScopeClient() {
        NeoForge.EVENT_BUS.addListener(RenderScopeCommands::register);
    }
}
