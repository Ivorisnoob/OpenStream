package com.ivor.openstream.data.streaming.providers

import java.util.Base64

internal object VidkingPayloadCipher {
    private val keySchedule = uintArrayOf(
        1116352408u, 1899447441u, 3049323471u, 3921009573u,
        961987163u, 1508970993u, 2453635748u, 2870763221u,
        3624381080u, 310598401u, 607225278u, 1426881987u,
        1925078388u, 2162078206u, 2614888103u, 3248222580u
    )
    private val initialHash = uintArrayOf(1732584193u, 4023233417u, 2562383102u, 271733878u)
    private val magic = byteArrayOf(109, 118, 109, 49)
    private const val sparseSize = 61
    private const val rounds = 8
    private const val golden = 2654435769u

    fun decrypt(encoded: String, seed: String, mediaId: Int): String {
        val encrypted = Base64.getUrlDecoder().decode(encoded)
        val stream = keyStream(seed, mediaId, encrypted.size)
        val decrypted = ByteArray(encrypted.size) { index ->
            (encrypted[index].toInt() xor stream[index].toInt()).toByte()
        }
        require(decrypted.copyOfRange(0, magic.size).contentEquals(magic)) {
            "Vidking payload failed integrity validation"
        }
        return decrypted.copyOfRange(magic.size, decrypted.size).toString(Charsets.UTF_8)
    }

    private fun keyStream(seed: String, mediaId: Int, size: Int): ByteArray {
        val state = createState(seed, mediaId)
        val bytes = ByteArray(size)
        var outputIndex = 0
        var blockIndex = 0
        while (outputIndex < size) {
            val value = next(state, blockIndex++)
            repeat(4) { byteIndex ->
                if (outputIndex < size) {
                    bytes[outputIndex++] = (value shr (byteIndex * 8)).toByte()
                }
            }
        }
        return bytes
    }

    private fun createState(seed: String, mediaId: Int): CipherState {
        if (hasOddTriangularParity(seed.length)) {
            val values = Array<UInt?>(256) { it.toUInt() }
            var cursor = 0
            for (index in values.indices) {
                cursor = (cursor + values[index]!!.toInt() + seed[index % seed.length].code) and 255
                val previous = values[index]
                values[index] = values[cursor]
                values[cursor] = previous
            }
            return CipherState(values, hashSeed(seed))
        }

        val values = arrayOfNulls<UInt>(sparseSize)
        var accumulator = mix(fnv(seed) xor mix(mediaId.toUInt() xor golden))
        repeat(rounds) { round ->
            if (hasEvenTriangularParity(round)) {
                val index = (accumulator % sparseSize.toUInt()).toInt()
                accumulator = rotateLeft(accumulator + golden, 7 + (round and 7))
                values[index] = accumulator xor mix(accumulator)
                accumulator = mix(accumulator + index.toUInt())
            } else {
                values[round] = keySchedule[round and 15]
            }
        }
        return CipherState(values, mix(accumulator xor 0xA5A5A5A5u))
    }

    private fun next(state: CipherState, outputIndex: Int): UInt {
        val accumulator = state.accumulator
        val stateIndex = (accumulator % sparseSize.toUInt()).toInt()
        val stored = state.values[stateIndex]
        val derived = (stored ?: 0u) xor (golden * (outputIndex + 1).toUInt())
        val combined = if (stored != null) {
            (accumulator xor derived) or (accumulator and derived)
        } else {
            accumulator xor derived
        }
        val rotated = rotateLeft(combined + accumulator, stateIndex and 31) xor
            rotateLeft(accumulator, (stateIndex * 7) and 31)
        val nextAccumulator = mix(rotated + golden)
        state.values[stateIndex] = nextAccumulator
        state.accumulator = nextAccumulator
        return nextAccumulator
    }

    private fun mix(input: UInt): UInt {
        var value = input
        value = value xor (value shr 16)
        value *= 2246822507u
        value = value xor (value shr 13)
        value *= 3266489909u
        value = value xor (value shr 16)
        return value
    }

    private fun hashSeed(seed: String): UInt {
        var value = initialHash[0]
        seed.forEachIndexed { index, character ->
            value = rotateLeft(
                value xor (character.code.toUInt() * keySchedule[index and 15]),
                5
            )
        }
        return mix(value)
    }

    private fun fnv(seed: String): UInt {
        var value = 2166136261u
        seed.forEach { character ->
            value = (value xor character.code.toUInt()) * 16777619u
        }
        return mix(value)
    }

    private fun rotateLeft(value: UInt, bits: Int): UInt =
        Integer.rotateLeft(value.toInt(), bits and 31).toUInt()

    private fun hasEvenTriangularParity(value: Int): Boolean =
        ((value * (value + 1)) and 1) == 0

    private fun hasOddTriangularParity(value: Int): Boolean =
        ((value * (value + 1)) and 1) == 1

    private data class CipherState(
        val values: Array<UInt?>,
        var accumulator: UInt
    )
}
