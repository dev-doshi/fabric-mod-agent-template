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
	private String lastAction = "idle";
	private int viewDistance = 12;
	// The bot's OWN model of where it is — advanced by cheat drivers independent of the server.
	// A real cheat client tracks its own position and keeps moving even when the server rubberbands
	// it (setback), which the server only sees as a still-larger delta. Reading the server entity
	// instead would let an "awaiting teleport" freeze hide the cheat, so cheat drivers use this.
	private Vec3 intendedPos;

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
		SimPlayer sim = new SimPlayer(player);
		sim.intendedPos = pos;
		return sim;
	}

	public ServerPlayer entity() {
		return player;
	}

	public String name() {
		return player.getGameProfile().name();
	}

	/** Label of the most recent action this player took (for the trace/viewer). */
	public String lastAction() {
		return lastAction;
	}

	/** Current view-distance setting (last value applied via {@link #changeSettings}). */
	public int viewDistance() {
		return viewDistance;
	}

	// --- Actions: each injects a real serverbound packet into the real handler. ---

	/** Move to an absolute position via a real movement packet (runs server movement validation). */
	public void moveTo(Vec3 pos, boolean onGround) {
		player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y, pos.z, onGround, false));
		lastAction = "walk";
	}

	/** Move and rotate via a real movement packet. */
	public void moveTo(Vec3 pos, float yaw, float pitch, boolean onGround) {
		player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(pos.x, pos.y, pos.z, yaw, pitch, onGround, false));
		lastAction = "walk";
	}

	// --- Cheat drivers: advance the bot's OWN position model and send it, ignoring server setbacks
	// (a real cheat client does exactly this). Use these in anticheat tests so an "awaiting teleport"
	// freeze on the server entity can't mask the cheat. ---

	/** The bot's current self-tracked position (independent of the server entity). */
	public Vec3 intended() {
		return intendedPos;
	}

	/** Advance the bot's intended position by a delta and send it as a real movement packet. */
	public void cheatMoveBy(double dx, double dy, double dz, boolean claimOnGround) {
		intendedPos = intendedPos.add(dx, dy, dz);
		player.connection.handleMovePlayer(
				new ServerboundMovePlayerPacket.Pos(intendedPos.x, intendedPos.y, intendedPos.z, claimOnGround, false));
		lastAction = "cheat";
	}

	/** Swing the main hand (a real animation/interaction packet). */
	public void swing() {
		player.connection.handleAnimate(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
		lastAction = "swing";
	}

	/** Start destroying a block at the given position (a real player-action packet). */
	public void startBreak(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face) {
		player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
				ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, face, 0));
		lastAction = "break";
	}

	/** Change client settings (language, view distance, etc.) via a real settings packet. */
	public void changeSettings(ClientInformation info) {
		player.connection.handleClientInformation(new ServerboundClientInformationPacket(info));
		this.viewDistance = info.viewDistance();
		lastAction = "settings";
	}

	/** Disconnect the simulated player from the server. */
	public void leave() {
		player.connection.onDisconnect(new net.minecraft.network.DisconnectionDetails(
				net.minecraft.network.chat.Component.literal("sim end")));
	}
}
