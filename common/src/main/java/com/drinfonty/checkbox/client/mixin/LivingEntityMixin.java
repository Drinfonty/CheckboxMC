package com.drinfonty.checkbox.client.mixin;

import com.drinfonty.checkbox.CheckboxClient;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects deaths for kill counters (DESIGN §5.2).
 *
 * <p>Edge-detects the first tick of the death animation, the same pattern RedFX uses for
 * {@code hurtTime} on 26.2. The client never reliably reaches {@code LivingEntity#die}, and
 * the entity is gone by the time it is removed from the level.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void checkbox$onTick(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;

		// In singleplayer the integrated server ticks its own copy of every entity in this
		// same JVM. Without this guard every kill would be counted twice.
		if (self.deathTime == 1 && self.level().isClientSide()) {
			CheckboxClient.onEntityDeath(self);
		}
	}
}
