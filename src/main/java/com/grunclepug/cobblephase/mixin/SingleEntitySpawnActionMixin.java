package com.grunclepug.cobblephase.mixin;

import com.cobblemon.mod.common.api.spawning.detail.EntitySpawnResult;
import com.cobblemon.mod.common.api.spawning.detail.SingleEntitySpawnAction;
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.grunclepug.cobblephase.pda.Pda;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records the spawn bucket (common, rare, boss, ...) on every Pokémon a spawn action creates. Cobblemon picks the
 * bucket before spawning and then drops it; this covers the natural spawner, herds and habitat spawns alike.
 */
@Mixin(value = SingleEntitySpawnAction.class, remap = false)
abstract class SingleEntitySpawnActionMixin {
    @Inject(method = "run()Lcom/cobblemon/mod/common/api/spawning/detail/EntitySpawnResult;", at = @At("RETURN"))
    private void cobblephase$recordBucket(CallbackInfoReturnable<EntitySpawnResult> cir) {
        EntitySpawnResult result = cir.getReturnValue();
        if (result == null) {
            return;
        }
        String bucket = ((SpawnAction<?>) (Object) this).getBucket();
        for (Entity entity : result.getEntities()) {
            if (entity instanceof PokemonEntity) {
                entity.setAttached(Pda.SPAWN_BUCKET, bucket);
            }
        }
    }
}
