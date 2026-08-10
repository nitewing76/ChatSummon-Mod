package com.thiswuming.voicesummon.network

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier


class ClientLogPacket(
    val entries: List<LogEntry>
) : CustomPayload {

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    data class LogEntry(
        val timestamp: String,
        val level: String,
        val loggerName: String,
        val message: String
    )

    companion object {
        val ID: CustomPayload.Id<ClientLogPacket> = CustomPayload.Id(
            Identifier.of("voicesummon", "client_log")
        )

        val CODEC: PacketCodec<PacketByteBuf, ClientLogPacket> =
            PacketCodec.of(
                { packet, buf ->
                    buf.writeVarInt(packet.entries.size)
                    packet.entries.forEach { e ->
                        buf.writeString(e.timestamp)
                        buf.writeString(e.level)
                        buf.writeString(e.loggerName)
                        buf.writeString(e.message)
                    }
                },
                { buf ->
                    val count = buf.readVarInt()
                    val list = ArrayList<LogEntry>(count)
                    repeat(count) {
                        list.add(
                            LogEntry(
                                timestamp = buf.readString(),
                                level = buf.readString(),
                                loggerName = buf.readString(),
                                message = buf.readString()
                            )
                        )
                    }
                    ClientLogPacket(entries = list)
                }
            )
    }
}
