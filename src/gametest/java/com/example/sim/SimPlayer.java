package com.example.sim;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * A fully-joined, tickable simulated player. Generalizes Fabric's {@code FakePlayer} into a bot that
 * the server treats as a real connected player (in the PlayerList, ticked, broadcast to others),
 * driven by injecting REAL serverbound packets into its real {@code ServerGamePacketListenerImpl}.
 *
 * <p>Because actions go through the actual packet handlers (handleMovePlayer, handlePlayerAction,
 * handleClientInformation, …), the real server-side logic runs: movement validation, interaction
 * events, settings sync. That is what makes the simulation realistic rather than a mock.
 *
 * <p>Recipe verified against Carpet's EntityPlayerMPFake + .agent-docs/mc-src PlayerList.placeNewPlayer.
 */
public final class SimPlayer {
	private final ServerPlayer player;

	private SimPlayer(ServerPlayer player) {
		this.player = player;
	}

	/** Join a new simulated player at {@code pos} on {@code level}. */
	public static SimPlayer join(ServerLevel level, String name, Vec3 pos) {
		MinecraftServer server = level.getServer();
		GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
		ClientInformation info = ClientInformation.createDefault();

		ServerPlayer player = new ServerPlayer(server, level, profile, info);
		player.setServerLevel(level);
		player.snapTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);

		// placeNewPlayer wires a real ServerGamePacketListenerImpl over our fake connection,
		// adds the player to the PlayerList, and spawns it into the world.
		server.getPlayerList().placeNewPlayer(new SimConnection(), player, new CommonListenerCookie(profile, 0, info, false));
		return new SimPlayer(player);
	}

	public ServerPlayer entity() {
		return player;
	}

	public String name() {
		return player.getGameProfile().name();
	}

	// --- Actions: each injects a real serverbound packet into the real handler. ---

	/** Move to an absolute position via a real movement packet (runs server movement validation). */
	public void moveTo(Vec3 pos, boolean onGround) {
		player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y, pos.z, onGround, false));
	}

	/** Move and rotate via a real movement packet. */
	public void moveTo(Vec3 pos, float yaw, float pitch, boolean onGround) {
		player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(pos.x, pos.y, pos.z, yaw, pitch, onGround, false));
	}

	/** Swing the main hand (a real animation/interaction packet). */
	public void swing() {
		player.connection.handleAnimate(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
	}

	/** Start destroying a block at the given position (a real player-action packet). */
	public void startBreak(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face) {
		player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
				ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, face, 0));
	}

	/** Change client settings (language, view distance, etc.) via a real settings packet. */
	public void changeSettings(ClientInformation info) {
		player.connection.handleClientInformation(new ServerboundClientInformationPacket(info));
	}

	/** Disconnect the simulated player from the server. */
	public void leave() {
		player.connection.onDisconnect(new net.minecraft.network.DisconnectionDetails(
				net.minecraft.network.chat.Component.literal("sim end")));
	}
}
