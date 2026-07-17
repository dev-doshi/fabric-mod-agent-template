package com.example.sim;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import org.jspecify.annotations.Nullable;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/**
 * A server-side {@link Connection} with no real network peer, used to join simulated players.
 *
 * <p>Design (grounded in .agent-docs/mc-src + Fabric's own FakePlayerPacketListener.FakeConnection):
 * a real Netty {@link EmbeddedChannel} backs the connection so {@code channelActive} sets the
 * channel field and the pipeline operations in {@code PlayerList.placeNewPlayer}
 * (setupInboundProtocol, isConnected, tick, disconnect) all work on a live-but-local channel.
 * Outbound {@code send} is swallowed so the clientbound login/packet stream is never encoded to a
 * (non-existent) client — we drive the player by invoking the server's packet HANDLERS directly.
 */
public final class SimConnection extends Connection {
	public SimConnection() {
		super(PacketFlow.SERVERBOUND);
		// Registering this handler on an EmbeddedChannel fires channelActive -> sets this.channel,
		// so isConnected() is true and pipeline setup in placeNewPlayer succeeds.
		new EmbeddedChannel(this);
	}

	@Override
	public void send(Packet<?> packet) {
		// swallow: there is no client to receive clientbound packets
	}

	@Override
	public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
	}

	@Override
	public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
	}

	@Override
	public String getLoggableAddress(boolean logIps) {
		return "sim";
	}
}
