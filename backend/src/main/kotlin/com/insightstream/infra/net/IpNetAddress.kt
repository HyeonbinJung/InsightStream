package com.insightstream.infra.net

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.net.InetAddress

data class IpNetAddress private constructor(
    private val normalized: String,
) {
    @JsonValue
    fun value(): String = normalized

    fun toInetAddress(): InetAddress = InetAddress.getByName(normalized)

    override fun toString(): String = normalized

    companion object {
        @JvmStatic
        @JsonCreator
        fun of(value: String): IpNetAddress {
            val parsed = InetAddress.getByName(value.trim())
            return IpNetAddress(parsed.hostAddress)
        }
    }
}
