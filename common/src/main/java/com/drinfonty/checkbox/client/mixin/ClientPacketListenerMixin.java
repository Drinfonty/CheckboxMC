package com.drinfonty.checkbox.client.mixin;

import com.drinfonty.checkbox.CheckboxClient;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads the two packets that tell a client-only mod what the player did (DESIGN §5.1, §5.2).
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	/**
	 * Damage attribution. Injected at {@code TAIL} rather than {@code HEAD} deliberately: the
	 * vanilla body opens with {@code PacketUtils.ensureRunningOnSameThread}, so a {@code HEAD}
	 * injection would still be on the netty thread.
	 */
	@Inject(method = "handleDamageEvent", at = @At("TAIL"))
	private void checkbox$onDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int self = player.getId();
		// sourceCauseId is the *causing* entity, so an arrow attributes to whoever fired it.
		if (packet.sourceCauseId() == self || packet.sourceDirectId() == self) {
			// Resolve the victim's type now, while it is certainly still in the level - by the
			// time it dies, a death-effect mod may have removed it. Nothing is recorded if it
			// cannot be resolved: without a type the kill could not be matched anyway, and a
			// record with no payload would be indistinguishable from a death that is not ours.
			ClientLevel level = Minecraft.getInstance().level;
			Entity victim = level == null ? null : level.getEntity(packet.entityId());
			if (victim != null) {
				CheckboxClient.onLocalPlayerDamaged(packet.entityId(), victim.getType());
			}
		}
	}

	/**
	 * Deaths, straight from the packet.
	 *
	 * <p>This event is the server telling the client the mob died, so it is authoritative in a
	 * way nothing client-side is. Kill detection used to watch the death *animation* instead
	 * ({@code deathTime == 1}), which is fine in a vanilla client and useless in a modded one:
	 * a ragdoll mod replaces the death, so the animation never runs and no kill was ever
	 * credited.
	 *
	 * <p>Injected immediately after Minecraft's own thread guard rather than at {@code HEAD}
	 * or {@code TAIL}. {@code HEAD} would run on the netty thread; {@code TAIL} is too late,
	 * because such a mod discards the dying mob while handling the event and any lookup then
	 * returns null - which is precisely how this failed in the wild.
	 */
	@Inject(
			method = "handleEntityEvent",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread"
							+ "(Lnet/minecraft/network/protocol/Packet;"
							+ "Lnet/minecraft/network/PacketListener;"
							+ "Lnet/minecraft/network/PacketProcessor;)V",
					shift = At.Shift.AFTER))
	private void checkbox$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		if (packet.getEventId() != EntityEvent.DEATH) {
			return;
		}
		// The id comes off the packet rather than from a level lookup, so this works even if
		// the mob has already been removed. The type is recalled from the hit.
		CheckboxClient.onEntityDeath(((EntityEventPacketAccessor) packet).checkbox$entityId());
	}

	/**
	 * Ground pickups.
	 *
	 * <p>The injection point is load-bearing, for the same reason as the death hook above. The
	 * vanilla handler shrinks the {@link ItemStack} by the picked-up amount and then discards
	 * the {@code ItemEntity}, so at {@code TAIL} there is nothing left to read; {@code HEAD}
	 * would run before the thread guard. Injecting at the {@code shrink} call is both on the
	 * client thread and still holding an intact stack, which MixinExtras captures as a local.
	 */
	@Inject(
			method = "handleTakeItemEntity",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
	private void checkbox$onTakeItemEntity(ClientboundTakeItemEntityPacket packet, CallbackInfo ci,
			@Local ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && packet.getPlayerId() == player.getId()) {
			CheckboxClient.onItemPickedUp(stack, packet.getAmount());
		}
	}
}
