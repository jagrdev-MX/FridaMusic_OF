package com.jagr.fridamusic.presentation.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import java.nio.charset.StandardCharsets

/**
 * Small, dependency-free QR encoder for the short FridaMusic URLs used by share cards.
 * It emits byte-mode QR codes with high error correction so the centered brand mark
 * can be rendered without compromising normal scanner compatibility.
 */
internal object FridaQrCodeGenerator {
    private const val QUIET_ZONE_MODULES = 4
    private const val OUTPUT_SIZE = 512

    private val cache = object : android.util.LruCache<String, Bitmap>(12) {}

    @Synchronized
    fun create(url: String): Bitmap = cache.get(url) ?: encode(url).also { cache.put(url, it) }

    private fun encode(text: String): Bitmap {
        val data = text.toByteArray(StandardCharsets.UTF_8)
        val version = DATA_CODEWORDS_HIGH.indices
            .drop(1)
            .firstOrNull { candidate -> dataFits(candidate, data.size) }
            ?: error("FridaMusic share URL is too long for the built-in QR encoder")
        val dataCodewords = createDataCodewords(data, version)
        val allCodewords = addErrorCorrectionAndInterleave(dataCodewords, version)
        val matrix = QrMatrix(version).apply {
            drawFunctionPatterns()
            drawCodewords(allCodewords)
            applyMaskZero()
            drawFormatBits()
        }
        return matrix.toBitmap(OUTPUT_SIZE)
    }

    private fun dataFits(version: Int, byteCount: Int): Boolean {
        val countBits = if (version <= 9) 8 else 16
        return 4 + countBits + byteCount * 8 <= DATA_CODEWORDS_HIGH[version] * 8
    }

    private fun createDataCodewords(data: ByteArray, version: Int): ByteArray {
        val capacityBits = DATA_CODEWORDS_HIGH[version] * 8
        val bits = BitBuffer()
        bits.append(0b0100, 4)
        bits.append(data.size, if (version <= 9) 8 else 16)
        data.forEach { bits.append(it.toInt() and 0xFF, 8) }
        bits.append(0, minOf(4, capacityBits - bits.size))
        bits.append(0, (8 - bits.size % 8) % 8)

        val result = bits.toByteArray().toMutableList()
        var pad = 0xEC
        while (result.size < DATA_CODEWORDS_HIGH[version]) {
            result += pad.toByte()
            pad = pad xor (0xEC xor 0x11)
        }
        return result.toByteArray()
    }

    private fun addErrorCorrectionAndInterleave(data: ByteArray, version: Int): ByteArray {
        val blockCount = ERROR_CORRECTION_BLOCKS_HIGH[version]
        val eccLength = ERROR_CORRECTION_CODEWORDS_PER_BLOCK_HIGH[version]
        val rawCodewords = TOTAL_CODEWORDS[version]
        val shortBlockCount = blockCount - rawCodewords % blockCount
        val shortBlockLength = rawCodewords / blockCount
        val divisor = reedSolomonDivisor(eccLength)
        val blocks = ArrayList<ByteArray>(blockCount)
        var dataOffset = 0

        repeat(blockCount) { blockIndex ->
            val dataLength = shortBlockLength - eccLength + if (blockIndex < shortBlockCount) 0 else 1
            val blockData = data.copyOfRange(dataOffset, dataOffset + dataLength)
            dataOffset += dataLength
            val ecc = reedSolomonRemainder(blockData, divisor)
            val block = ByteArray(shortBlockLength + 1)
            blockData.copyInto(block)
            ecc.copyInto(block, block.size - eccLength)
            blocks += block
        }

        check(dataOffset == data.size)
        val result = ByteArray(rawCodewords)
        var outputOffset = 0
        for (column in blocks.first().indices) {
            blocks.forEachIndexed { blockIndex, block ->
                val isShortBlockPadding = column == shortBlockLength - eccLength && blockIndex < shortBlockCount
                if (!isShortBlockPadding) result[outputOffset++] = block[column]
            }
        }
        check(outputOffset == result.size)
        return result
    }

    private fun reedSolomonDivisor(degree: Int): ByteArray {
        val result = ByteArray(degree)
        result[degree - 1] = 1
        var root = 1
        repeat(degree) {
            for (index in result.indices) {
                result[index] = reedSolomonMultiply(result[index].toInt() and 0xFF, root).toByte()
                if (index + 1 < result.size) {
                    result[index] = (result[index].toInt() xor (result[index + 1].toInt() and 0xFF)).toByte()
                }
            }
            root = reedSolomonMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
        val result = ByteArray(divisor.size)
        data.forEach { value ->
            val factor = (value.toInt() xor result[0].toInt()) and 0xFF
            result.copyInto(result, destinationOffset = 0, startIndex = 1)
            result[result.lastIndex] = 0
            result.indices.forEach { index ->
                result[index] = (
                    result[index].toInt() xor reedSolomonMultiply(divisor[index].toInt() and 0xFF, factor)
                    ).toByte()
            }
        }
        return result
    }

    private fun reedSolomonMultiply(left: Int, right: Int): Int {
        var product = 0
        repeat(8) { index ->
            product = (product shl 1) xor ((product ushr 7) * 0x11D)
            product = product xor (((left ushr (7 - index)) and 1) * right)
        }
        return product
    }

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val size: Int get() = bits.size

        fun append(value: Int, length: Int) {
            require(length in 0..31 && (length == 31 || value ushr length == 0))
            for (index in length - 1 downTo 0) bits += (value ushr index and 1) != 0
        }

        fun toByteArray(): ByteArray = ByteArray((bits.size + 7) / 8).also { result ->
            bits.forEachIndexed { index, bit ->
                if (bit) result[index ushr 3] = (result[index ushr 3].toInt() or (1 shl (7 - (index and 7)))).toByte()
            }
        }
    }

    private class QrMatrix(private val version: Int) {
        private val size = version * 4 + 17
        private val modules = Array(size) { BooleanArray(size) }
        private val functionModules = Array(size) { BooleanArray(size) }

        fun drawFunctionPatterns() {
            for (index in 0 until size) {
                setFunction(6, index, index % 2 == 0)
                setFunction(index, 6, index % 2 == 0)
            }
            drawFinderPattern(3, 3)
            drawFinderPattern(size - 4, 3)
            drawFinderPattern(3, size - 4)

            val positions = alignmentPatternPositions()
            positions.indices.forEach { verticalIndex ->
                positions.indices.forEach { horizontalIndex ->
                    val overlapsFinder =
                        verticalIndex == 0 && horizontalIndex == 0 ||
                            verticalIndex == 0 && horizontalIndex == positions.lastIndex ||
                            verticalIndex == positions.lastIndex && horizontalIndex == 0
                    if (!overlapsFinder) drawAlignmentPattern(positions[horizontalIndex], positions[verticalIndex])
                }
            }
            reserveFormatAreas()
            if (version >= 7) drawVersionBits()
        }

        fun drawCodewords(data: ByteArray) {
            var bitIndex = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right--
                for (vertical in 0 until size) {
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vertical else vertical
                    for (offset in 0..1) {
                        val x = right - offset
                        if (!functionModules[y][x] && bitIndex < data.size * 8) {
                            modules[y][x] = ((data[bitIndex ushr 3].toInt() ushr (7 - (bitIndex and 7))) and 1) != 0
                            bitIndex++
                        }
                    }
                }
                right -= 2
            }
            check(bitIndex == data.size * 8)
        }

        fun applyMaskZero() {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (!functionModules[y][x] && (x + y) % 2 == 0) modules[y][x] = !modules[y][x]
                }
            }
        }

        fun drawFormatBits() {
            val data = 0b10 shl 3 // High error correction, mask pattern 0.
            var remainder = data
            repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
            val bits = ((data shl 10) or remainder) xor 0x5412
            fun bit(index: Int) = (bits ushr index and 1) != 0

            for (index in 0..5) setFunction(8, index, bit(index))
            setFunction(8, 7, bit(6))
            setFunction(8, 8, bit(7))
            setFunction(7, 8, bit(8))
            for (index in 9..14) setFunction(14 - index, 8, bit(index))
            for (index in 0..7) setFunction(size - 1 - index, 8, bit(index))
            for (index in 8..14) setFunction(8, size - 15 + index, bit(index))
            setFunction(8, size - 8, true)
        }

        fun toBitmap(outputSize: Int): Bitmap {
            val moduleArea = size + QUIET_ZONE_MODULES * 2
            val scale = (outputSize / moduleArea).coerceAtLeast(1)
            val renderedSize = moduleArea * scale
            val offset = (outputSize - renderedSize) / 2
            return createBitmap(outputSize, outputSize).also { bitmap ->
                bitmap.eraseColor(Color.WHITE)
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        if (!modules[y][x]) continue
                        val left = offset + (x + QUIET_ZONE_MODULES) * scale
                        val top = offset + (y + QUIET_ZONE_MODULES) * scale
                        for (pixelY in top until top + scale) {
                            for (pixelX in left until left + scale) bitmap.setPixel(pixelX, pixelY, Color.BLACK)
                        }
                    }
                }
            }
        }

        private fun drawFinderPattern(centerX: Int, centerY: Int) {
            for (vertical in -4..4) {
                for (horizontal in -4..4) {
                    val x = centerX + horizontal
                    val y = centerY + vertical
                    if (x !in 0 until size || y !in 0 until size) continue
                    val distance = maxOf(kotlin.math.abs(horizontal), kotlin.math.abs(vertical))
                    setFunction(x, y, distance != 2 && distance != 4)
                }
            }
        }

        private fun drawAlignmentPattern(centerX: Int, centerY: Int) {
            for (vertical in -2..2) {
                for (horizontal in -2..2) {
                    val distance = maxOf(kotlin.math.abs(horizontal), kotlin.math.abs(vertical))
                    setFunction(centerX + horizontal, centerY + vertical, distance != 1)
                }
            }
        }

        private fun alignmentPatternPositions(): IntArray {
            if (version == 1) return intArrayOf()
            val count = version / 7 + 2
            val step = if (version == 32) 26 else (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2
            return IntArray(count).also { positions ->
                positions[0] = 6
                var position = size - 7
                for (index in positions.lastIndex downTo 1) {
                    positions[index] = position
                    position -= step
                }
            }
        }

        private fun reserveFormatAreas() {
            for (index in 0..8) {
                if (index != 6) {
                    setFunction(8, index, false)
                    setFunction(index, 8, false)
                }
            }
            for (index in 0..7) {
                setFunction(size - 1 - index, 8, false)
                setFunction(8, size - 1 - index, false)
            }
            setFunction(8, size - 8, true)
        }

        private fun drawVersionBits() {
            var remainder = version
            repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
            val bits = version shl 12 or remainder
            for (index in 0 until 18) {
                val color = (bits ushr index and 1) != 0
                val firstX = size - 11 + index % 3
                val firstY = index / 3
                setFunction(firstX, firstY, color)
                setFunction(firstY, firstX, color)
            }
        }

        private fun setFunction(x: Int, y: Int, color: Boolean) {
            modules[y][x] = color
            functionModules[y][x] = true
        }
    }

    private val TOTAL_CODEWORDS = intArrayOf(0, 26, 44, 70, 100, 134, 172, 196, 242, 292, 346)
    private val DATA_CODEWORDS_HIGH = intArrayOf(0, 9, 16, 26, 36, 46, 60, 66, 86, 100, 122)
    private val ERROR_CORRECTION_BLOCKS_HIGH = intArrayOf(0, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8)
    private val ERROR_CORRECTION_CODEWORDS_PER_BLOCK_HIGH = intArrayOf(0, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28)
}
