package io.github.rupakc.travelplanner.core.domain.map

import java.io.DataInputStream
import java.io.InputStream

/** One closed ring of a country outline: interleaved x, y in map units (x 0..360 = lon + 180, y 0..180 = 90 - lat). */
class MapRing(val xy: FloatArray) {
    val size: Int get() = xy.size / 2
    val minX: Float = (0 until size).minOfOrNull { xy[it * 2] } ?: 0f
    val maxX: Float = (0 until size).maxOfOrNull { xy[it * 2] } ?: 0f
    val minY: Float = (0 until size).minOfOrNull { xy[it * 2 + 1] } ?: 0f
    val maxY: Float = (0 until size).maxOfOrNull { xy[it * 2 + 1] } ?: 0f

    /** Even-odd point-in-polygon test in map units. */
    fun contains(x: Float, y: Float): Boolean {
        if (x !in minX..maxX || y !in minY..maxY) return false
        var inside = false
        var j = size - 1
        for (i in 0 until size) {
            val xi = xy[i * 2]
            val yi = xy[i * 2 + 1]
            val xj = xy[j * 2]
            val yj = xy[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}

/** The bundled world map: ISO 3166-1 alpha-2 code to outline rings. */
class WorldMapData(val countries: Map<String, List<MapRing>>) {
    /**
     * Draw order: larger bounding boxes first, so an enclave (Lesotho inside South Africa) is painted over its host and
     * stays visible. Ties keep the code order.
     */
    val drawOrder: List<String> =
        countries.keys.sortedWith(
            compareByDescending<String> { code ->
                val rings = countries.getValue(code)
                if (rings.isEmpty()) {
                    0f
                } else {
                    (rings.maxOf { it.maxX } - rings.minOf { it.minX }) * (rings.maxOf { it.maxY } - rings.minOf { it.minY })
                }
            }.thenBy { it },
        )

    /** The ISO2 code of the country under the map-unit point (the topmost drawn one), or null over sea. */
    fun hit(x: Float, y: Float): String? = drawOrder.asReversed().firstOrNull { code -> countries.getValue(code).any { it.contains(x, y) } }

    companion object {
        const val WIDTH = 360f
        const val HEIGHT = 180f
        private const val SCALE = 100f

        /** Parses the format written by `tools/worldmap/build_world_map.py` (big-endian, 0.01 degree grid). */
        fun parse(input: InputStream): WorldMapData {
            val data = DataInputStream(input.buffered())
            val out = LinkedHashMap<String, List<MapRing>>()
            repeat(data.readUnsignedShort()) {
                val code = String(charArrayOf(data.readUnsignedByte().toChar(), data.readUnsignedByte().toChar()))
                val rings =
                    List(data.readUnsignedShort()) {
                        val n = data.readUnsignedShort()
                        val xy = FloatArray(n * 2)
                        for (i in 0 until n) {
                            xy[i * 2] = data.readUnsignedShort() / SCALE
                            xy[i * 2 + 1] = data.readUnsignedShort() / SCALE
                        }
                        MapRing(xy)
                    }
                out[code] = rings
            }
            return WorldMapData(out)
        }
    }
}

/** Fits the 360 x 180 map into a view of [width] x [height] pixels, centred, equirectangular. */
class MapProjection(val width: Float, val height: Float) {
    val scale: Float = if (width <= 0f || height <= 0f) 0f else minOf(width / WorldMapData.WIDTH, height / WorldMapData.HEIGHT)
    val offsetX: Float = (width - WorldMapData.WIDTH * scale) / 2f
    val offsetY: Float = (height - WorldMapData.HEIGHT * scale) / 2f

    fun toScreenX(x: Float): Float = offsetX + x * scale

    fun toScreenY(y: Float): Float = offsetY + y * scale

    /** Pixel to map units (x, y), or null when the pixel is outside the map or the view is empty. */
    fun toMap(px: Float, py: Float): Pair<Float, Float>? {
        if (scale <= 0f) return null
        val x = (px - offsetX) / scale
        val y = (py - offsetY) / scale
        return if (x in 0f..WorldMapData.WIDTH && y in 0f..WorldMapData.HEIGHT) x to y else null
    }
}

/** Which bundled countries a list of visited codes fills. Codes are case- and whitespace-insensitive; unknown ones are ignored. */
object ScratchMapRules {
    fun normalize(codes: Iterable<String>): Set<String> = codes.map { it.trim().uppercase() }.filter { it.length == 2 }.toSet()

    fun filled(map: WorldMapData, codes: Iterable<String>): Set<String> = normalize(codes).filter { it in map.countries }.toSet()

    /** Tap at pixel (px, py): the country code under it, or null. */
    fun tap(map: WorldMapData, projection: MapProjection, px: Float, py: Float): String? =
        projection.toMap(px, py)?.let { (x, y) -> map.hit(x, y) }
}
