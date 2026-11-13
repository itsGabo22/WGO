package com.wgo.navigator.places

import com.wgo.navigator.Language
import com.wgo.navigator.ai.LocalIntentParser
import com.wgo.navigator.core.IntentKind
import com.wgo.navigator.geo.PlaceCandidate
import com.wgo.navigator.geo.PlaceRequest
import com.wgo.navigator.geo.PlaceResolver
import com.wgo.navigator.geo.Resolution
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.RadarProjection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

/**
 * P11 place coverage benchmark (docs/PLACE_SEARCH.md, "Coverage benchmark"): 120 utterances generated from the index by
 * `tools/build_place_benchmark.py`, through the local parser and the resolver with the REAL index and no network, from
 * the Pasto centre. Targets: top-3 >= 90 % and top-1 >= 75 % on groups a-c, no automatic pick on group d, p95 local
 * latency <= 150 ms. The full report goes to `build/place_benchmark_report.txt`.
 */
class PlaceCoverageBenchmarkTest {
    private class Query(val group: String, val utterance: String, val expect: String, val coord: GeoCoord?, val branches: List<GeoCoord>)

    private val stop = setOf("de", "la", "el", "los", "las", "del", "en", "por", "y", "a", "al")

    /** The builder's plain normalisation (independent of the app's variant forms). */
    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^0-9a-z]+"), " ").split(' ').filter { it.isNotEmpty() && it !in stop }.joinToString(" ")

    private fun coord(s: String): GeoCoord = s.split(',').let { GeoCoord(it[0].toDouble(), it[1].toDouble()) }

    private val queries: List<Query> = File("src/test/resources/place_benchmark.tsv").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val f = line.split('\t')
            Query(
                f[0], f[1], f[2], if (f[3].isNotEmpty()) GeoCoord(f[3].toDouble(), f[4].toDouble()) else null,
                f.getOrNull(5).orEmpty().split(';').filter { it.isNotBlank() }.map(::coord)
            )
        }

    private fun near(a: GeoCoord, b: GeoCoord, m: Double) = RadarProjection.distanceMeters(a, b) <= m

    private fun hit(q: Query, c: PlaceCandidate): Boolean = when (q.group) {
        "a" -> norm(c.place.name) == q.expect && near(c.place.coord, q.coord!!, 150.0)
        "b" -> q.branches.any { near(c.place.coord, it, 30.0) }
        "c" -> norm(c.place.name) == q.expect || near(c.place.coord, q.coord!!, 300.0)
        else -> false
    }

    @Test
    fun coverageTargets() {
        assertEquals(120, queries.size)
        val index = PlaceIndex { File("src/main/assets/${PlaceIndex.ASSET}").inputStream() }
        assertTrue(runBlocking { index.ensureLoaded() })
        val resolver = PlaceResolver(index, null)
        val centre = GeoCoord(1.2136, -77.2811)
        val n = HashMap<String, Int>()
        val top1 = HashMap<String, Int>()
        val top3 = HashMap<String, Int>()
        var autoPicks = 0
        var nearestFirst = 0
        val times = ArrayList<Long>()
        val report = StringBuilder()
        repeat(2) { pass -> // the first pass warms the JIT and the phonetic cache
            for (q in queries) {
                val t0 = System.nanoTime()
                val i = LocalIntentParser.parse(q.utterance, Language.ES)
                val r = if (i.kind == IntentKind.Navigate && i.placeQuery != null) runBlocking {
                    resolver.resolve(
                        PlaceRequest(
                            i.placeQuery!!, i.placeHint, i.placeCategory, i.placeFromAlias, q.utterance,
                            vague = i.placeVague, address = i.placeIsAddress, nearest = i.placeNearest
                        ),
                        centre, Language.ES
                    )
                } else null
                val dt = System.nanoTime() - t0
                if (pass == 0) continue
                times += dt
                n.merge(q.group, 1, Int::plus)
                val cands = (r as? Resolution.Found)?.candidates.orEmpty()
                val shown = cands.joinToString(" ; ") { it.place.name }
                if (q.group == "d") {
                    val auto = r is Resolution.Found && !r.uncertain
                    if (auto) autoPicks++
                    report.append("d ${if (auto) "AUTO" else "ok"} ${q.utterance} -> $shown\n")
                    continue
                }
                val h1 = cands.firstOrNull()?.let { hit(q, it) } == true
                val h3 = cands.take(3).any { hit(q, it) }
                if (h1) top1.merge(q.group, 1, Int::plus)
                if (h3) top3.merge(q.group, 1, Int::plus)
                val nf = q.group == "b" && cands.isNotEmpty() && near(cands[0].place.coord, q.coord!!, 30.0)
                if (nf) nearestFirst++
                val u = (r as? Resolution.Found)?.uncertain
                report.append("${q.group}${if (q.group == "b" && !nf) "*" else ""} ${if (h1) "1" else if (h3) "3" else "-"} ${q.utterance} [${i.placeQuery}] u=$u -> $shown\n")
            }
        }
        times.sort()
        val abc = listOf("a", "b", "c")
        val total = abc.sumOf { n[it] ?: 0 }
        val rate1 = abc.sumOf { top1[it] ?: 0 } * 100.0 / total
        val rate3 = abc.sumOf { top3[it] ?: 0 } * 100.0 / total
        val p95 = times[(times.size * 95) / 100] / 1e6
        val summary = buildString {
            for (g in abc) append("$g: top1 ${top1[g] ?: 0}/${n[g]} top3 ${top3[g] ?: 0}/${n[g]}\n")
            append(String.format(java.util.Locale.US, "a-c: top1 %.1f%% top3 %.1f%%\n", rate1, rate3))
            append("b: nearest branch first $nearestFirst/${n["b"]}\n")
            append("d: automatic picks $autoPicks/${n["d"]}\n")
            append(String.format(java.util.Locale.US, "latency p50 %.1f ms p95 %.1f ms max %.1f ms\n", times[times.size / 2] / 1e6, p95, times.last() / 1e6))
        }
        File("build").mkdirs()
        File("build/place_benchmark_report.txt").writeText(summary + "\n" + report)
        println(summary)
        assertTrue("top-1 $rate1 %", rate1 >= 75.0)
        assertTrue("top-3 $rate3 %", rate3 >= 90.0)
        assertEquals("automatic picks of places that do not exist", 0, autoPicks)
        assertTrue("p95 $p95 ms", p95 <= 150.0)
    }
}
