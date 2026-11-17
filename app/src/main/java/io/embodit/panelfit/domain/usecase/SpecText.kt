package io.embodit.panelfit.domain.usecase

import io.embodit.panelfit.domain.model.Layout
import io.embodit.panelfit.domain.model.PfSettings
import io.embodit.panelfit.domain.model.Specimen
import io.embodit.panelfit.domain.model.Surface
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** What the label, the card and the files say, built from [Readings] (pure; tested). */
object SpecText {
    /** Label line 1: "1.20 m · 眼下 12° · 正对". */
    fun labelLine1(en: Boolean, r: Readings): String = Copy.labelLine1.format(en, Copy.f2(r.dist), Copy.elev(en, r.elevDeg), Copy.facing(r.facing)(en))

    /** Label line 2: "14.8° × 8.8° · 等效 325 dp". */
    fun labelLine2(en: Boolean, r: Readings): String = Copy.labelLine2.format(en, Copy.f1(r.angW), Copy.f1(r.angH), r.eqW.roundToInt())

    /** The front panel's label when it covers another one: "挡住了右边那块的下半". */
    fun covers(en: Boolean, o: Overlap): String = Copy.labelCovers.format(en, Copy.side(o.side)(en), Copy.parts.getValue(o.part)(en))

    /** Card row "遮挡". */
    fun occlusionRow(en: Boolean, id: Int, overlaps: List<Overlap>): String {
        val mine = overlaps.firstOrNull { it.front == id } ?: return overlaps.firstOrNull { it.back == id }?.let { o ->
            // the one that covers this panel: on the other side, or straight in front when this one is behind it
            val by = if (o.side == Side.BEHIND) Copy.sideFront(en) else Copy.side(sideFlip(o.side))(en)
            Copy.isCovered.format(en, Copy.parts.getValue(o.part)(en), if (en) "the $by one" else "${by}那块")
        } ?: Copy.noOverlap(en)
        return Copy.coveredBy.format(en, Copy.side(mine.side)(en), Copy.parts.getValue(mine.part)(en))
    }

    private fun sideFlip(s: Side): Side =
        when (s) {
            Side.LEFT -> Side.RIGHT
            Side.RIGHT -> Side.LEFT
            Side.BEHIND -> Side.BEHIND // not used: a panel behind is covered by the front one
        }

    fun distRow(en: Boolean, r: Readings): String = "${Copy.f2(r.dist)} m · ${Copy.place(en, r.bearingDeg)} · ${Copy.elev(en, r.elevDeg)}"

    fun mmRow(r: Readings): String = "${r.mmW.roundToInt()} × ${r.mmH.roundToInt()} mm"

    fun angleRow(en: Boolean, r: Readings): String = "${Copy.f1(r.angW)}° × ${Copy.f1(r.angH)}° · " + Copy.fovPart.format(en, Copy.f1(r.fovShare * 100f))

    fun eqRow(r: Readings): String = "${r.eqW.roundToInt()} × ${r.eqH.roundToInt()} dp"

    fun reachRow(en: Boolean, r: Readings): String = (if (r.reach == Reach.DIRECT) Copy.reachYes else Copy.reachNo)(en)

    fun smallPrint(en: Boolean, s: PfSettings): String = Copy.smallPrint.format(en, Copy.f2(s.k), fmt0(s.refHigh), fmt0(s.refLow))

    private fun fmt0(v: Float): String = if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else Copy.f1(v)

    /**
     * The verdict sentence (design `rules.verdicts.textSelf`: only the self-rating judges): "这块常用控制面板放 0.9 m：15.2° × 9.6°，
     * 等效 333 dp。14 sp 正文 22.5′，你点了「勉强」；手够不着，用射线点。"
     */
    fun sentence(en: Boolean, s: Specimen, r: Readings): String {
        val name = s.name(en).let { if (en) it else it }
        val self: String
        if (s.template.layout == Layout.IMAGE) {
            val rated = s.ratings[TypeRoles.WHOLE]?.rating
            self = Copy.sentenceWhole.format(en, Copy.rating(rated)?.let { Copy.youSaid.format(en, it(en)) } ?: Copy.notRated(en))
        } else {
            val row = r.styles.firstOrNull { it.role.role == TypeRoles.KEY_ROLE } ?: r.styles.firstOrNull()
            self =
                if (row == null) Copy.notRated(en)
                else Copy.sentenceRole.format(en, row.role.usedSp.roundToInt(), Copy.f1(row.arcmin), Copy.rating(row.self?.rating)?.let { Copy.youSaid.format(en, it(en)) } ?: Copy.notRated(en))
        }
        val reach = (if (r.reach == Reach.DIRECT) Copy.reachShortYes else Copy.reachShortNo)(en)
        return Copy.sentence.format(en, name, Copy.f2(r.dist), Copy.f1(r.angW), Copy.f1(r.angH), r.eqW.roundToInt(), self, reach)
    }

    /** File base name: panelfit-<template id>-<yyyyMMdd-HHmm>, only letters, digits and "-"; [n] > 1 adds "-n". */
    fun fileBase(s: Specimen, at: Date, n: Int = 1): String {
        val short = s.template.id.replace(Regex("[^A-Za-z0-9-]"), "")
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(at)
        return "panelfit-$short-$stamp" + if (n > 1) "-$n" else ""
    }

    /** The JSON export (design `rules.export.schema`). */
    fun json(
        s: Specimen,
        r: Readings?,
        settings: PfSettings,
        mine: Float?,
        overlaps: List<Overlap>,
        sentence: String,
        sdkVersion: String,
        device: String,
        fontScale: Float,
    ): JSONObject {
        val o = JSONObject()
        o.put("schemaVersion", 1)
        o.put("sdkVersion", sdkVersion)
        o.put("device", device)
        o.put("k", settings.k.toDouble())
        o.put("ppd", settings.ppd.toDouble())
        o.put("fontScale", fontScale.toDouble())
        o.put("thresholds", JSONObject().put("ref24", settings.refHigh.toDouble()).put("ref16", settings.refLow.toDouble()).put("mine", mine?.toDouble() ?: JSONObject.NULL))
        o.put(
            "panel",
            JSONObject()
                .put("template", s.template.id)
                .put("name", s.template.zh)
                .put("dp", JSONArray().put(s.wDp).put(s.hDp))
                .put("scale", s.scale.toDouble())
                .put("mm", if (r != null) JSONArray().put(r1(r.mmW)).put(r1(r.mmH)) else JSONObject.NULL)
                .put("surface", if (s.surface == Surface.GLASS) "glass" else "solid")
                .put("image", s.image ?: JSONObject.NULL)
                .put("imageScaled", s.imageScaled),
        )
        o.put(
            "pose",
            JSONObject()
                .put("dist", r?.let { r1x(it.dist, 100) } ?: r1x(s.pose.dist, 100))
                .put("heightCm", (s.pose.heightM * 100f).roundToInt())
                .put("yawFromBody", r?.let { r1(it.bearingDeg) } ?: r1(s.pose.yawDeg))
                .put("facing", r?.let { r1(it.facingOff) } ?: JSONObject.NULL)
                .put("tilt", r1(s.pose.tiltDeg)),
        )
        if (r != null) {
            o.put("angles", JSONObject().put("w", r1(r.angW)).put("h", r1(r.angH)))
            o.put("eqDp", JSONArray().put(r.eqW.roundToInt()).put(r.eqH.roundToInt()))
            o.put("fovShare", r1x(r.fovShare, 1000))
            val styles = JSONArray()
            for (row in r.styles) {
                styles.put(
                    JSONObject()
                        .put("role", row.role.role)
                        .put("sp", r1(row.role.themeSp))
                        .put("renderedPx", row.role.renderedSp?.let { r1(it) } ?: JSONObject.NULL)
                        .put("glyphMm", r1x(row.glyphMm, 100))
                        .put("arcmin", r1(row.arcmin))
                        .put("px", r1(row.px))
                        .put("ref", row.ref.name.lowercase(Locale.US))
                        .put("self", row.self?.rating?.name?.lowercase(Locale.US) ?: JSONObject.NULL)
                        .put("source", row.role.source),
                )
            }
            o.put("styles", styles)
            o.put("reach", if (r.reach == Reach.DIRECT) "direct" else "rayOnly")
            o.put("neck", r.neck.name.lowercase(Locale.US))
        } else {
            o.put("angles", JSONObject.NULL)
            o.put("styles", JSONArray())
        }
        val occ = JSONArray()
        for (ov in overlaps.filter { it.front == s.id || it.back == s.id }) {
            occ.put(JSONObject().put("with", if (ov.front == s.id) ov.back else ov.front).put("part", ov.part.name.lowercase(Locale.US)).put("covers", ov.front == s.id))
        }
        o.put("occlusion", occ)
        o.put("verdictSentence", sentence)
        o.put("note", "Stage 附着面板口径，Shared Space 窗口未校")
        return o
    }

    /** The Markdown export: sentence, one table, the type table, the small print. */
    fun markdown(s: Specimen, r: Readings?, settings: PfSettings, overlaps: List<Overlap>, sentence: String): String {
        val en = false
        val sb = StringBuilder()
        sb.append("# ").append(s.template.zh).append(" · ").append(s.template.en).append("\n\n")
        sb.append(sentence).append("\n\n")
        sb.append("| 项 | 值 |\n|---|---|\n")
        if (r != null) {
            sb.append("| ").append(Copy.cardDist(en)).append(" | ").append(distRow(en, r)).append(" |\n")
            sb.append("| ").append(Copy.cardMm(en)).append(" | ").append(mmRow(r)).append(" |\n")
            sb.append("| ").append(Copy.cardAngle(en)).append(" | ").append(angleRow(en, r)).append(" |\n")
            sb.append("| ").append(Copy.cardEq(en)).append(" | ").append(eqRow(r)).append(" |\n")
            sb.append("| ").append(Copy.cardReach(en)).append(" | ").append(reachRow(en, r)).append(" |\n")
            sb.append("| ").append(Copy.cardNeck(en)).append(" | ").append(Copy.neck(r.neck)(en)).append(" |\n")
        }
        sb.append("| ").append(Copy.cardOcclusion(en)).append(" | ").append(occlusionRow(en, s.id, overlaps)).append(" |\n")
        sb.append("| dp | ").append(s.wDp).append(" × ").append(s.hDp).append(" · 缩放 ").append(Copy.f2(s.scale)).append(" |\n\n")
        if (r != null && r.styles.isNotEmpty()) {
            sb.append("| 字号 | sp | 角分 | 像素 | 参考 | 你的自评 |\n|---|---|---|---|---|---|\n")
            for (row in r.styles) {
                sb.append("| ").append(row.role.role).append(" | ").append(Copy.f1(row.role.usedSp)).append(" | ").append(Copy.f1(row.arcmin)).append("′ | ")
                    .append(row.px.roundToInt()).append(" | ").append(Copy.ref(row.ref)(en)).append(" | ").append(Copy.rating(row.self?.rating)?.invoke(en) ?: Copy.notRated(en)).append(" |\n")
            }
            sb.append("\n")
        }
        sb.append("<small>").append(smallPrint(en, settings)).append("</small>\n")
        return sb.toString()
    }

    private fun r1(v: Float): Double = (v * 10f).roundToInt() / 10.0

    private fun r1x(v: Float, f: Int): Double = (v * f).roundToInt().toDouble() / f
}
