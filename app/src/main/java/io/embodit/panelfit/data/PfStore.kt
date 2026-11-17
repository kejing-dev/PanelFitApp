package io.embodit.panelfit.data

import android.content.Context
import io.embodit.panelfit.domain.model.Language
import io.embodit.panelfit.domain.model.Overlays
import io.embodit.panelfit.domain.model.PfSettings
import io.embodit.panelfit.domain.model.PlaceMode
import io.embodit.panelfit.domain.model.PolarPose
import io.embodit.panelfit.domain.model.Posture
import io.embodit.panelfit.domain.model.Rating
import io.embodit.panelfit.domain.model.RatingEntry
import io.embodit.panelfit.domain.model.Specimen
import io.embodit.panelfit.domain.model.Surface
import io.embodit.panelfit.domain.model.Template
import org.json.JSONArray
import org.json.JSONObject

/** What is kept between runs (design `persistence`): the layout relative to the body anchor, ratings and settings. */
data class Saved(val specimens: List<Specimen>, val nextId: Int, val settings: PfSettings, val overlays: Overlays, val lastSaved: String?)

/** JSON codec of [Saved]; plain org.json so the unit tests can round-trip it. */
object PfCodec {
    fun encode(s: Saved): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("nextId", s.nextId)
        o.put("lastSaved", s.lastSaved ?: JSONObject.NULL)
        val st = s.settings
        o.put(
            "settings",
            JSONObject()
                .put("k", st.k.toDouble()).put("refHigh", st.refHigh.toDouble()).put("refLow", st.refLow.toDouble())
                .put("useMine", st.useMine).put("ppd", st.ppd.toDouble()).put("arm", st.armM.toDouble())
                .put("posture", st.posture.name).put("frames", st.frames).put("sound", st.sound).put("volumeHigh", st.volumeHigh)
                .put("language", st.language.name).put("welcomed", st.welcomed),
        )
        o.put("overlays", JSONObject().put("reach", s.overlays.reach).put("neck", s.overlays.neck).put("ruler", s.overlays.ruler))
        val arr = JSONArray()
        for (sp in s.specimens) {
            val r = JSONObject()
            for ((role, e) in sp.ratings) r.put(role, JSONObject().put("r", e.rating.name).put("a", e.arcmin.toDouble()))
            arr.put(
                JSONObject()
                    .put("id", sp.id).put("t", sp.template.id).put("w", sp.wDp).put("h", sp.hDp).put("scale", sp.scale.toDouble())
                    .put("surface", sp.surface.name).put("hidden", sp.hidden).put("image", sp.image ?: JSONObject.NULL).put("imageScaled", sp.imageScaled)
                    .put("dist", sp.pose.dist.toDouble()).put("hm", sp.pose.heightM.toDouble()).put("yaw", sp.pose.yawDeg.toDouble())
                    .put("facing", sp.pose.facingDeg.toDouble()).put("tilt", sp.pose.tiltDeg.toDouble())
                    .put("ratings", r),
            )
        }
        o.put("specimens", arr)
        return o.toString()
    }

    fun decode(text: String?): Saved? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(text)
            val st = o.optJSONObject("settings") ?: JSONObject()
            val d = PfSettings()
            val settings =
                PfSettings(
                    k = st.optDouble("k", d.k.toDouble()).toFloat(),
                    refHigh = st.optDouble("refHigh", d.refHigh.toDouble()).toFloat(),
                    refLow = st.optDouble("refLow", d.refLow.toDouble()).toFloat(),
                    useMine = st.optBoolean("useMine", d.useMine),
                    ppd = st.optDouble("ppd", d.ppd.toDouble()).toFloat(),
                    armM = st.optDouble("arm", d.armM.toDouble()).toFloat(),
                    posture = runCatching { Posture.valueOf(st.optString("posture")) }.getOrDefault(d.posture),
                    frames = st.optBoolean("frames", d.frames),
                    sound = st.optBoolean("sound", d.sound),
                    volumeHigh = st.optBoolean("volumeHigh", d.volumeHigh),
                    language = runCatching { Language.valueOf(st.optString("language")) }.getOrDefault(d.language),
                    welcomed = st.optBoolean("welcomed", d.welcomed),
                )
            val ov = o.optJSONObject("overlays") ?: JSONObject()
            val overlays = Overlays(ov.optBoolean("reach"), ov.optBoolean("neck"), ov.optBoolean("ruler"))
            val arr = o.optJSONArray("specimens") ?: JSONArray()
            val list = ArrayList<Specimen>()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val t = Template.of(j.getString("t")) ?: continue
                val ratings = HashMap<String, RatingEntry>()
                j.optJSONObject("ratings")?.let { r ->
                    for (key in r.keys()) {
                        val e = r.getJSONObject(key)
                        runCatching { ratings[key] = RatingEntry(Rating.valueOf(e.getString("r")), e.getDouble("a").toFloat()) }
                    }
                }
                list +=
                    Specimen(
                        id = j.getInt("id"),
                        template = t,
                        wDp = j.getInt("w"),
                        hDp = j.getInt("h"),
                        scale = j.getDouble("scale").toFloat(),
                        surface = runCatching { Surface.valueOf(j.getString("surface")) }.getOrDefault(Surface.GLASS),
                        pose =
                            PolarPose(
                                j.getDouble("dist").toFloat(),
                                j.getDouble("hm").toFloat(),
                                j.getDouble("yaw").toFloat(),
                                j.optDouble("facing", 0.0).toFloat(),
                                j.optDouble("tilt", 0.0).toFloat(),
                            ),
                        hidden = j.optBoolean("hidden"),
                        mode = PlaceMode.LOCKED,
                        image = if (j.isNull("image")) null else j.optString("image"),
                        imageScaled = j.optBoolean("imageScaled"),
                        ratings = ratings,
                    )
            }
            Saved(list.take(6), o.optInt("nextId", (list.maxOfOrNull { it.id } ?: 0) + 1), settings, overlays, if (o.isNull("lastSaved")) null else o.optString("lastSaved"))
        }.getOrNull()
    }
}

/** SharedPreferences behind [PfCodec]. */
class PfStore(context: Context) {
    private val prefs = context.getSharedPreferences("panelfit", Context.MODE_PRIVATE)

    fun load(): Saved? = PfCodec.decode(prefs.getString(KEY, null))

    fun save(s: Saved) {
        prefs.edit().putString(KEY, PfCodec.encode(s)).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY = "state"
    }
}
