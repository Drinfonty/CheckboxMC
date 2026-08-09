package com.drinfonty.checkbox.client.mixin;

import com.drinfonty.checkbox.CheckboxClient;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
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
			CheckboxClient.onLocalPlayerDamaged(packet.entityId());
		}
	}

	/**
	 * Ground pickups.
	 *
	 * <p>The injection point is load-bearing. The vanilla handler shrinks the {@link ItemStack}
	 * by the picked-up amount and then discards the {@code ItemEntity}, so at {@code TAIL}
	 * there is nothing left to read; {@code HEAD} would run before the thread guard. Injecting
	 * at the {@code shrink} call is both on the client thread and still holding an intact
	 * stack, which MixinExtras hands over as a captured local.
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
