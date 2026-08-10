package com.drinfonty.checkbox.client.mixin;

import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the entity id straight off the death packet.
 *
 * <p>The packet only offers {@code getEntity(Level)}, which resolves against the level - and
 * returns null once a death-effect mod has removed the dying mob. The id is all we need, since
 * what the victim *was* is remembered from the hit that killed it.
 */
@Mixin(ClientboundEntityEventPacket.class)
public interface EntityEventPacketAccessor {
	@Accessor("entityId")
	int checkbox$entityId();
}
