package com.thiswuming.voicesummon.network

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier


class VoiceSummonPacket(
    val mobNames: List<String>,
    val matchType: String,
    val confidence: Float
) : CustomPayload {

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID: CustomPayload.Id<VoiceSummonPacket> = CustomPayload.Id(
            Identifier.of("voicesummon", "summon")
        )

        val CODEC: PacketCodec<PacketByteBuf, VoiceSummonPacket> =
            PacketCodec.of(
                { packet, buf ->
                    buf.writeVarInt(packet.mobNames.size)
                    packet.mobNames.forEach { buf.writeString(it) }
                    buf.writeString(packet.matchType)
                    buf.writeFloat(packet.confidence)
                },
                { buf ->
                    val count = buf.readVarInt()
                    val names = ArrayList<String>(count)
                    repeat(count) { names.add(buf.readString()) }
                    VoiceSummonPacket(
                        mobNames = names,
                        matchType = buf.readString(),
                        confidence = buf.readFloat()
                    )
                }
            )
    }
}