@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.watchfaces

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.support.wearable.watchface.WatchFaceStyle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.FontRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.forEach
import androidx.viewbinding.ViewBinding
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ActivityCustomBinding
import info.nightscout.androidaps.watchfaces.utils.BaseWatchFace
import info.nightscout.rx.logging.LTag
import info.nightscout.rx.weardata.CUSTOM_VERSION
import info.nightscout.rx.weardata.CwfData
import info.nightscout.rx.weardata.ResFileMap
import info.nightscout.rx.weardata.CwfResDataMap
import info.nightscout.rx.weardata.CwfMetadataKey
import info.nightscout.rx.weardata.CwfMetadataMap
import info.nightscout.rx.weardata.ResData
import info.nightscout.rx.weardata.ResFormat
import info.nightscout.rx.weardata.EventData
import info.nightscout.rx.weardata.JsonKeyValues
import info.nightscout.rx.weardata.JsonKeys.*
import info.nightscout.rx.weardata.ViewKeys
import info.nightscout.rx.weardata.ZipWatchfaceFormat
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.extensions.toVisibilityKeepSpace
import info.nightscout.shared.sharedPreferences.SP
import org.joda.time.TimeOfDay
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class CustomWatchface : BaseWatchFace() {

    @Inject lateinit var context: Context
    private lateinit var binding: ActivityCustomBinding
    private var zoomFactor = 1.0
    private val displaySize = Point()
    private val TEMPLATE_RESOLUTION = 400
    private var lowBatColor = Color.RED
    private var bgColor = Color.WHITE
    private var resDataMap: CwfResDataMap = mutableMapOf()

    override fun onCreate() {
        super.onCreate()
        FontMap.init(context, resDataMap)
    }

    @Suppress("DEPRECATION")
    override fun inflateLayout(inflater: LayoutInflater): ViewBinding {
        binding = ActivityCustomBinding.inflate(inflater)
        setDefaultColors()
        persistence.store(defaultWatchface(), true)
        (context.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getSize(displaySize)
        zoomFactor = (displaySize.x).toDouble() / TEMPLATE_RESOLUTION.toDouble()
        return binding
    }

    override fun getWatchFaceStyle(): WatchFaceStyle {
        return WatchFaceStyle.Builder(this)
            .setAcceptsTapEvents(true)
            .setHideNotificationIndicator(false)
            .setShowUnreadCountIndicator(true)
            .build()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun setDataFields() {
        super.setDataFields()
        binding.direction2.setImageDrawable(TrendArrowMap.drawable(singleBg.slopeArrow, resources, resDataMap))
        // rotate the second hand.
        binding.secondHand.rotation = TimeOfDay().secondOfMinute * 6f
        // rotate the minute hand.
        binding.minuteHand.rotation = TimeOfDay().minuteOfHour * 6f
        // rotate the hour hand.
        binding.hourHand.rotation = TimeOfDay().hourOfDay * 30f + TimeOfDay().minuteOfHour * 0.5f
    }

    override fun setColorDark() {
        setWatchfaceStyle()
        binding.sgv.setTextColor(bgColor)
        binding.direction2.setColorFilter(changeDrawableColor(bgColor))

        if (ageLevel != 1)
            binding.timestamp.setTextColor(ContextCompat.getColor(this, R.color.dark_TimestampOld))
        if (status.batteryLevel != 1)
            binding.uploaderBattery.setTextColor(lowBatColor)
        when (loopLevel) {
            -1   -> binding.loop.setBackgroundResource(R.drawable.loop_grey_25)
            1    -> binding.loop.setBackgroundResource(R.drawable.loop_green_25)
            else -> binding.loop.setBackgroundResource(R.drawable.loop_red_25)
        }

        setupCharts()
    }

    override fun setColorBright() {
        setColorDark()
    }

    override fun setColorLowRes() {
        setColorDark()
    }

    override fun setSecond() {
        binding.time.text = if (showSecond)
            getString(R.string.hour_minute_second, dateUtil.hourString(), dateUtil.minuteString(), dateUtil.secondString())
        else
            getString(R.string.hour_minute, dateUtil.hourString(), dateUtil.minuteString())
        binding.second.text = dateUtil.secondString()
        // rotate the second hand.
        binding.secondHand.rotation = TimeOfDay().secondOfMinute * 6f
    }

    override fun updateSecondVisibility() {
        binding.second.visibility = (binding.second.visibility == View.VISIBLE && showSecond).toVisibility()
        binding.secondHand.visibility = (binding.secondHand.visibility == View.VISIBLE && showSecond).toVisibility()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun setWatchfaceStyle() {
        val customWatchface = persistence.readCustomWatchface() ?: persistence.readCustomWatchface(true)
        customWatchface?.let {
            updatePref(it.customWatchfaceData.metadata)
            try {
                val json = JSONObject(it.customWatchfaceData.json)
                resDataMap = it.customWatchfaceData.resDatas
                FontMap.init(context, resDataMap)
                enableSecond = json.optBoolean(ENABLESECOND.key) && sp.getBoolean(R.string.key_show_seconds, true)
                highColor = getColor(json.optString(HIGHCOLOR.key), ContextCompat.getColor(this, R.color.dark_highColor))
                midColor = getColor(json.optString(MIDCOLOR.key), ContextCompat.getColor(this, R.color.inrange))
                lowColor = getColor(json.optString(LOWCOLOR.key), ContextCompat.getColor(this, R.color.low))
                lowBatColor = getColor(json.optString(LOWBATCOLOR.key), ContextCompat.getColor(this, R.color.dark_uploaderBatteryEmpty))
                carbColor = getColor(json.optString(CARBCOLOR.key), ContextCompat.getColor(this, R.color.carbs))
                basalBackgroundColor = getColor(json.optString(BASALBACKGROUNDCOLOR.key), ContextCompat.getColor(this, R.color.basal_dark))
                basalCenterColor = getColor(json.optString(BASALCENTERCOLOR.key), ContextCompat.getColor(this, R.color.basal_light))
                gridColor = getColor(json.optString(GRIDCOLOR.key), Color.WHITE)
                pointSize = json.optInt(POINTSIZE.key, 2)
                dayNameFormat = json.optString(DAYNAMEFORMAT.key, "E")
                    .takeIf { it.matches(Regex("E{1,4}")) } ?: "E"
                monthFormat = json.optString(MONTHFORMAT.key, "MMM")
                    .takeIf { it.matches(Regex("M{1,4}")) } ?: "MMM"
                binding.dayName.text = dateUtil.dayNameString(dayNameFormat).substringBeforeLast(".") // Update daynName and month according to format on cwf loading
                binding.month.text = dateUtil.monthString(monthFormat).substringBeforeLast(".")
                bgColor = when (singleBg.sgvLevel) {
                    1L   -> highColor
                    0L   -> midColor
                    -1L  -> lowColor
                    else -> midColor
                }

                binding.mainLayout.forEach { view ->
                    ViewMap.fromId(view.id)?.let { id ->
                        json.optJSONObject(id.key)?.also { viewJson ->
                            val width = (viewJson.optInt(WIDTH.key) * zoomFactor).toInt()
                            val height = (viewJson.optInt(HEIGHT.key) * zoomFactor).toInt()
                            val params = FrameLayout.LayoutParams(width, height)
                            params.topMargin = (viewJson.optInt(TOPMARGIN.key) * zoomFactor).toInt()
                            params.leftMargin = (viewJson.optInt(LEFTMARGIN.key) * zoomFactor).toInt()
                            view.layoutParams = params
                            view.visibility = setVisibility(viewJson.optString(VISIBILITY.key, JsonKeyValues.GONE.key), id.visibility(sp))
                            when (view) {
                                is TextView  -> {
                                    view.rotation = viewJson.optInt(ROTATION.key).toFloat()
                                    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, (viewJson.optInt(TEXTSIZE.key, 22) * zoomFactor).toFloat())
                                    view.gravity = GravityMap.gravity(viewJson.optString(GRAVITY.key, GravityMap.CENTER.key))
                                    view.setTypeface(
                                        FontMap.font(viewJson.optString(FONT.key, FontMap.DEFAULT.key)),
                                        StyleMap.style(viewJson.optString(FONTSTYLE.key, StyleMap.NORMAL.key))
                                    )
                                    view.setTextColor(getColor(viewJson.optString(FONTCOLOR.key)))
                                    view.isAllCaps = viewJson.optBoolean(ALLCAPS.key)
                                    if (viewJson.has(TEXTVALUE.key))
                                        view.text = viewJson.optString(TEXTVALUE.key)
                                    view.background = resDataMap[viewJson.optString(BACKGROUND.key)]?.toDrawable(resources, width, height)
                                }

                                is ImageView -> {
                                    view.clearColorFilter()
                                    id.drawable(resources, resDataMap, singleBg.sgvLevel)?.let {
                                        if (viewJson.has(COLOR.key))        // Note only works on bitmap (png or jpg) or xml included into res, not for svg files
                                            it.colorFilter = changeDrawableColor(getColor(viewJson.optString(COLOR.key)))
                                        else
                                            it.clearColorFilter()
                                        view.setImageDrawable(it)
                                    } ?: apply {
                                        view.setImageDrawable(id.defaultDrawable?.let {resources.getDrawable(it)})
                                        if (viewJson.has(COLOR.key))
                                            view.setColorFilter(getColor(viewJson.optString(COLOR.key)))
                                        else
                                            view.clearColorFilter()
                                    }
                                }

                            }
                        } ?:apply {
                            view.visibility = View.GONE
                            if (view is TextView) {
                                view.text = ""
                            }
                        }
                    }
                }
                manageSpecificViews()
            } catch (e: Exception) {
                aapsLogger.debug(LTag.WEAR, "Crash during Custom watch load")
                persistence.store(defaultWatchface(), false) // relaod correct values to avoid crash of watchface
            }
        }
    }

    private fun updatePref(metadata: CwfMetadataMap) {
        val cwfAuthorization = metadata[CwfMetadataKey.CWF_AUTHORIZATION]?.toBooleanStrictOrNull()
        cwfAuthorization?.let { authorization ->
            if (authorization) {
                PrefMap.values().forEach { pref ->
                    metadata[CwfMetadataKey.fromKey(pref.key)]?.toBooleanStrictOrNull()?.let { sp.putBoolean(pref.prefKey, it) }
                }
            }
        }
    }

    private fun defaultWatchface(): EventData.ActionSetCustomWatchface {
        val metadata = JSONObject()
            .put(CwfMetadataKey.CWF_NAME.key, getString(info.nightscout.shared.R.string.wear_default_watchface))
            .put(CwfMetadataKey.CWF_FILENAME.key, getString(info.nightscout.shared.R.string.wear_default_watchface))
            .put(CwfMetadataKey.CWF_AUTHOR.key, "Philoul")
            .put(CwfMetadataKey.CWF_CREATED_AT.key, dateUtil.dateString(dateUtil.now()))
            .put(CwfMetadataKey.CWF_AUTHOR_VERSION.key, CUSTOM_VERSION)
            .put(CwfMetadataKey.CWF_VERSION.key, CUSTOM_VERSION)
            .put(CwfMetadataKey.CWF_COMMENT.key, getString(info.nightscout.shared.R.string.default_custom_watchface_comment))
        val json = JSONObject()
            .put(METADATA.key, metadata)
            .put(HIGHCOLOR.key, String.format("#%06X", 0xFFFFFF and highColor))
            .put(MIDCOLOR.key, String.format("#%06X", 0xFFFFFF and midColor))
            .put(LOWCOLOR.key, String.format("#%06X", 0xFFFFFF and lowColor))
            .put(LOWBATCOLOR.key, String.format("#%06X", 0xFFFFFF and lowBatColor))
            .put(CARBCOLOR.key, String.format("#%06X", 0xFFFFFF and carbColor))
            .put(BASALBACKGROUNDCOLOR.key, String.format("#%06X", 0xFFFFFF and basalBackgroundColor))
            .put(BASALCENTERCOLOR.key, String.format("#%06X", 0xFFFFFF and basalCenterColor))
            .put(GRIDCOLOR.key, String.format("#%06X", 0xFFFFFF and Color.WHITE))
            .put(POINTSIZE.key, 2)
            .put(ENABLESECOND.key, true)

        binding.mainLayout.forEach { view ->
            val params = view.layoutParams as FrameLayout.LayoutParams
            ViewMap.fromId(view.id)?.let {
                if (view is TextView) {
                    json.put(
                        it.key,
                        JSONObject()
                            .put(WIDTH.key, (params.width / zoomFactor).toInt())
                            .put(HEIGHT.key, (params.height / zoomFactor).toInt())
                            .put(TOPMARGIN.key, (params.topMargin / zoomFactor).toInt())
                            .put(LEFTMARGIN.key, (params.leftMargin / zoomFactor).toInt())
                            .put(ROTATION.key, view.rotation.toInt())
                            .put(VISIBILITY.key, getVisibility(view.visibility))
                            .put(TEXTSIZE.key, view.textSize.toInt())
                            .put(GRAVITY.key, GravityMap.key(view.gravity))
                            .put(FONT.key, FontMap.key())
                            .put(FONTSTYLE.key, StyleMap.key(view.typeface.style))
                            .put(FONTCOLOR.key, String.format("#%06X", 0xFFFFFF and view.currentTextColor))
                    )
                }
                if (view is ImageView || view is lecho.lib.hellocharts.view.LineChartView) {
                    json.put(
                        it.key,
                        JSONObject()
                            .put(WIDTH.key, (params.width / zoomFactor).toInt())
                            .put(HEIGHT.key, (params.height / zoomFactor).toInt())
                            .put(TOPMARGIN.key, (params.topMargin / zoomFactor).toInt())
                            .put(LEFTMARGIN.key, (params.leftMargin / zoomFactor).toInt())
                            .put(VISIBILITY.key, getVisibility(view.visibility))
                    )
                }
            }
        }
        val metadataMap = ZipWatchfaceFormat.loadMetadata(json)
        val drawableDataMap: CwfResDataMap = mutableMapOf()
        getResourceByteArray(info.nightscout.shared.R.drawable.watchface_custom)?.let {
            drawableDataMap[ResFileMap.CUSTOM_WATCHFACE.fileName] = ResData(it, ResFormat.PNG)
        }
        return EventData.ActionSetCustomWatchface(CwfData(json.toString(4), metadataMap, drawableDataMap))
    }

    private fun setDefaultColors() {
        highColor = Color.parseColor("#FFFF00")
        midColor = Color.parseColor("#00FF00")
        lowColor = Color.parseColor("#FF0000")
        carbColor = ContextCompat.getColor(this, R.color.carbs)
        basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark)
        basalCenterColor = ContextCompat.getColor(this, R.color.basal_light)
        lowBatColor = ContextCompat.getColor(this, R.color.dark_uploaderBatteryEmpty)
        gridColor = Color.WHITE
    }

    private fun setVisibility(visibility: String, pref: Boolean = true): Int = when (visibility) {
        JsonKeyValues.VISIBLE.key   -> pref.toVisibility()
        JsonKeyValues.INVISIBLE.key -> pref.toVisibilityKeepSpace()
        JsonKeyValues.GONE.key      -> View.GONE
        else                        -> View.GONE
    }

    private fun getVisibility(visibility: Int): String = when (visibility) {
        View.VISIBLE   -> JsonKeyValues.VISIBLE.key
        View.INVISIBLE -> JsonKeyValues.INVISIBLE.key
        View.GONE      -> JsonKeyValues.GONE.key
        else           -> JsonKeyValues.GONE.key
    }

    private fun getResourceByteArray(resourceId: Int): ByteArray? {
        val inputStream = resources.openRawResource(resourceId)
        val byteArrayOutputStream = ByteArrayOutputStream()

        val buffer = ByteArray(1024)
        var count: Int
        while (inputStream.read(buffer).also { count = it } != -1) {
            byteArrayOutputStream.write(buffer, 0, count)
        }
        byteArrayOutputStream.close()
        inputStream.close()

        return byteArrayOutputStream.toByteArray()
    }

    private fun changeDrawableColor(color: Int): ColorFilter {
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)

        colorMatrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    Color.red(color) / 255f, 0f, 0f, 0f, 0f,
                    0f, Color.green(color) / 255f, 0f, 0f, 0f,
                    0f, 0f, Color.blue(color) / 255f, 0f, 0f,
                    0f, 0f, 0f, Color.alpha(color) / 255f, 0f
                )
            )
        )
        return ColorMatrixColorFilter(colorMatrix)
    }

    private fun getColor(color: String, defaultColor: Int = Color.GRAY): Int =
        if (color == JsonKeyValues.BGCOLOR.key)
            bgColor
        else
            try {
                Color.parseColor(color)
            } catch (e: Exception) {
                defaultColor
            }

    private fun manageSpecificViews() {
        //Background should fill all the watchface and must be visible
        val params = FrameLayout.LayoutParams((TEMPLATE_RESOLUTION * zoomFactor).toInt(), (TEMPLATE_RESOLUTION * zoomFactor).toInt())
        params.topMargin = 0
        params.leftMargin = 0
        binding.background.layoutParams = params
        binding.background.visibility = View.VISIBLE
        // Update second visibility
        updateSecondVisibility()
        setSecond() // Update second visibility for time view
        // Update timePeriod visibility
        binding.timePeriod.visibility = (binding.timePeriod.visibility == View.VISIBLE && android.text.format.DateFormat.is24HourFormat(this).not()).toVisibility()
    }

    private enum class ViewMap(
        val key: String,
        @IdRes val id: Int,
        @StringRes val pref: Int?,
        @IdRes val defaultDrawable: Int?,
        val customDrawable: ResFileMap?,
        val customHigh:ResFileMap?,
        val customLow: ResFileMap?
    ) {

        BACKGROUND(
            ViewKeys.BACKGROUND.key,
            R.id.background,
            null,
            info.nightscout.shared.R.drawable.background,
            ResFileMap.BACKGROUND,
            ResFileMap.BACKGROUND_HIGH,
            ResFileMap.BACKGROUND_LOW
        ),
        CHART(ViewKeys.CHART.key, R.id.chart, null, null, null, null, null),
        COVER_CHART(
            ViewKeys.COVER_CHART.key,
            R.id.cover_chart,
            null,
            null,
            ResFileMap.COVER_CHART,
            ResFileMap.COVER_CHART_HIGH,
            ResFileMap.COVER_CHART_LOW
        ),
        FREETEXT1(ViewKeys.FREETEXT1.key, R.id.freetext1, null, null, null, null, null),
        FREETEXT2(ViewKeys.FREETEXT2.key, R.id.freetext2, null, null, null, null, null),
        FREETEXT3(ViewKeys.FREETEXT3.key, R.id.freetext3, null, null, null, null, null),
        FREETEXT4(ViewKeys.FREETEXT4.key, R.id.freetext4, null, null, null, null, null),
        IOB1(ViewKeys.IOB1.key, R.id.iob1, R.string.key_show_iob, null, null, null, null),
        IOB2(ViewKeys.IOB2.key, R.id.iob2, R.string.key_show_iob, null, null, null, null),
        COB1(ViewKeys.COB1.key, R.id.cob1, R.string.key_show_cob, null, null, null, null),
        COB2(ViewKeys.COB2.key, R.id.cob2, R.string.key_show_cob, null, null, null, null),
        DELTA(ViewKeys.DELTA.key, R.id.delta, R.string.key_show_delta, null, null, null, null),
        AVG_DELTA(ViewKeys.AVG_DELTA.key, R.id.avg_delta, R.string.key_show_avg_delta, null, null, null, null),
        UPLOADER_BATTERY(ViewKeys.UPLOADER_BATTERY.key, R.id.uploader_battery, R.string.key_show_uploader_battery, null, null, null, null),
        RIG_BATTERY(ViewKeys.RIG_BATTERY.key, R.id.rig_battery, R.string.key_show_rig_battery, null, null, null, null),
        BASALRATE(ViewKeys.BASALRATE.key, R.id.basalRate, R.string.key_show_temp_basal, null, null, null, null),
        BGI(ViewKeys.BGI.key, R.id.bgi, R.string.key_show_bgi, null, null, null, null),
        TIME(ViewKeys.TIME.key, R.id.time, null, null, null, null, null),
        HOUR(ViewKeys.HOUR.key, R.id.hour, null, null, null, null, null),
        MINUTE(ViewKeys.MINUTE.key, R.id.minute, null, null, null, null, null),
        SECOND(ViewKeys.SECOND.key, R.id.second, R.string.key_show_seconds, null, null, null, null),
        TIMEPERIOD(ViewKeys.TIMEPERIOD.key, R.id.timePeriod, null, null, null, null, null),
        DAY_NAME(ViewKeys.DAY_NAME.key, R.id.day_name, null, null, null, null, null),
        DAY(ViewKeys.DAY.key, R.id.day, null, null, null, null, null),
        WEEKNUMBER(ViewKeys.WEEKNUMBER.key, R.id.week_number, R.string.key_show_week_number, null, null, null, null),
        MONTH(ViewKeys.MONTH.key, R.id.month, null, null, null, null, null),
        LOOP(ViewKeys.LOOP.key, R.id.loop, R.string.key_show_external_status, null, null, null, null),
        DIRECTION(ViewKeys.DIRECTION.key, R.id.direction2, R.string.key_show_direction, null, null, null, null),
        TIMESTAMP(ViewKeys.TIMESTAMP.key, R.id.timestamp, R.string.key_show_ago, null, null, null, null),
        SGV(ViewKeys.SGV.key, R.id.sgv, R.string.key_show_bg, null, null, null, null),
        COVER_PLATE(
            ViewKeys.COVER_PLATE.key,
            R.id.cover_plate,
            null,
            info.nightscout.shared.R.drawable.simplified_dial,
            ResFileMap.COVER_PLATE,
            ResFileMap.COVER_PLATE_HIGH,
            ResFileMap.COVER_PLATE_LOW
        ),
        HOUR_HAND(
            ViewKeys.HOUR_HAND.key,
            R.id.hour_hand,
            null,
            info.nightscout.shared.R.drawable.hour_hand,
            ResFileMap.HOUR_HAND,
            ResFileMap.HOUR_HAND_HIGH,
            ResFileMap.HOUR_HAND_LOW
        ),
        MINUTE_HAND(
            ViewKeys.MINUTE_HAND.key,
            R.id.minute_hand,
            null,
            info.nightscout.shared.R.drawable.minute_hand,
            ResFileMap.MINUTE_HAND,
            ResFileMap.MINUTE_HAND_HIGH,
            ResFileMap.MINUTE_HAND_LOW
        ),
        SECOND_HAND(
            ViewKeys.SECOND_HAND.key,
            R.id.second_hand,
            R.string.key_show_seconds,
            info.nightscout.shared.R.drawable.second_hand,
            ResFileMap.SECOND_HAND,
            ResFileMap.SECOND_HAND_HIGH,
            ResFileMap.SECOND_HAND_LOW
        );

        companion object {

            fun fromId(id: Int): ViewMap? = values().firstOrNull { it.id == id }
        }

        fun visibility(sp: SP): Boolean = this.pref?.let { sp.getBoolean(it, true) }
            ?: true

        fun drawable(resources: Resources, drawableDataMap: CwfResDataMap, sgvLevel: Long): Drawable? = customDrawable?.let { cd ->
            when (sgvLevel) {
                1L   -> { customHigh?.let {drawableDataMap[customHigh.fileName]}?.toDrawable(resources) ?: drawableDataMap[cd.fileName]?.toDrawable(resources) }
                0L   -> { drawableDataMap[cd.fileName]?.toDrawable(resources) }
                -1L  -> { customLow?.let {drawableDataMap[customLow.fileName]}?.toDrawable(resources) ?: drawableDataMap[cd.fileName]?.toDrawable(resources) }
                else -> drawableDataMap[cd.fileName]?.toDrawable(resources)
            }
        }
    }
}

private enum class TrendArrowMap(val symbol: String, @DrawableRes val icon: Int,val customDrawable: ResFileMap?) {
    NONE("??", R.drawable.ic_invalid, ResFileMap.ARROW_NONE),
    TRIPLE_UP("X", R.drawable.ic_doubleup, ResFileMap.ARROW_DOUBLE_UP),
    DOUBLE_UP("\u21c8", R.drawable.ic_doubleup, ResFileMap.ARROW_DOUBLE_UP),
    SINGLE_UP("\u2191", R.drawable.ic_singleup, ResFileMap.ARROW_SINGLE_UP),
    FORTY_FIVE_UP("\u2197", R.drawable.ic_fortyfiveup, ResFileMap.ARROW_FORTY_FIVE_UP),
    FLAT("\u2192", R.drawable.ic_flat, ResFileMap.ARROW_FLAT),
    FORTY_FIVE_DOWN("\u2198", R.drawable.ic_fortyfivedown, ResFileMap.ARROW_FORTY_FIVE_DOWN),
    SINGLE_DOWN("\u2193", R.drawable.ic_singledown, ResFileMap.ARROW_SINGLE_DOWN),
    DOUBLE_DOWN("\u21ca", R.drawable.ic_doubledown, ResFileMap.ARROW_DOUBLE_DOWN),
    TRIPLE_DOWN("X", R.drawable.ic_doubledown, ResFileMap.ARROW_DOUBLE_DOWN);

    companion object {

        fun drawable(direction: String?, resources: Resources, drawableDataMap: CwfResDataMap): Drawable {
            val arrow = values().firstOrNull { it.symbol == direction } ?:NONE
            return arrow.customDrawable?. let {drawableDataMap[arrow.customDrawable.fileName]}?.toDrawable(resources)  ?:resources.getDrawable(arrow.icon)
        }

    }
}

private enum class GravityMap(val key: String, val gravity: Int) {
    CENTER(JsonKeyValues.CENTER.key, Gravity.CENTER),
    LEFT(JsonKeyValues.LEFT.key, Gravity.LEFT),
    RIGHT(JsonKeyValues.RIGHT.key, Gravity.RIGHT);

    companion object {

        fun gravity(key: String?) = values().firstOrNull { it.key == key }?.gravity ?: CENTER.gravity
        fun key(gravity: Int) = values().firstOrNull { it.gravity == gravity }?.key ?: CENTER.key
    }
}

private enum class FontMap(val key: String, var font: Typeface, @FontRes val fontRessources: Int?) {
    SANS_SERIF(JsonKeyValues.SANS_SERIF.key, Typeface.SANS_SERIF, null),
    DEFAULT(JsonKeyValues.DEFAULT.key, Typeface.DEFAULT, null),
    DEFAULT_BOLD(JsonKeyValues.DEFAULT_BOLD.key, Typeface.DEFAULT_BOLD, null),
    MONOSPACE(JsonKeyValues.MONOSPACE.key, Typeface.MONOSPACE, null),
    SERIF(JsonKeyValues.SERIF.key, Typeface.SERIF, null),
    ROBOTO_CONDENSED_BOLD(JsonKeyValues.ROBOTO_CONDENSED_BOLD.key, Typeface.DEFAULT, R.font.roboto_condensed_bold),
    ROBOTO_CONDENSED_LIGHT(JsonKeyValues.ROBOTO_CONDENSED_LIGHT.key, Typeface.DEFAULT, R.font.roboto_condensed_light),
    ROBOTO_CONDENSED_REGULAR(JsonKeyValues.ROBOTO_CONDENSED_REGULAR.key, Typeface.DEFAULT, R.font.roboto_condensed_regular),
    ROBOTO_SLAB_LIGHT(JsonKeyValues.ROBOTO_SLAB_LIGHT.key, Typeface.DEFAULT, R.font.roboto_slab_light);

    companion object {

        private val customFonts = mutableMapOf<String, Typeface>()
        fun init(context: Context, resDataMap: CwfResDataMap) {
            customFonts.clear()
            values().forEach { fontMap ->
                customFonts[fontMap.key.lowercase()] = fontMap.fontRessources?.let { fontResource ->
                    ResourcesCompat.getFont(context, fontResource)
                } ?: fontMap.font
            }
            resDataMap.filter { (_, resData) ->
                resData.format == ResFormat.TTF || resData.format == ResFormat.OTF
            }.forEach { (key, resData) ->
                customFonts[key.lowercase()] = resData.toTypeface() ?:Typeface.DEFAULT
            }
        }

        fun font(key: String) = customFonts[key.lowercase()] ?: DEFAULT.font
        fun key() = DEFAULT.key
    }
}

private enum class StyleMap(val key: String, val style: Int) {
    NORMAL(JsonKeyValues.NORMAL.key, Typeface.NORMAL),
    BOLD(JsonKeyValues.BOLD.key, Typeface.BOLD),
    BOLD_ITALIC(JsonKeyValues.BOLD_ITALIC.key, Typeface.BOLD_ITALIC),
    ITALIC(JsonKeyValues.ITALIC.key, Typeface.ITALIC);

    companion object {

        fun style(key: String?) = values().firstOrNull { it.key == key }?.style ?: NORMAL.style
        fun key(style: Int) = values().firstOrNull { it.style == style }?.key ?: NORMAL.key
    }
}

// This class containt mapping between keys used within json of Custom Watchface and preferences
private enum class PrefMap(val key: String, @StringRes val prefKey: Int) {

    SHOW_IOB(CwfMetadataKey.CWF_PREF_WATCH_SHOW_IOB.key, R.string.key_show_iob),
    SHOW_DETAILED_IOB(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DETAILED_IOB.key, R.string.key_show_detailed_iob),
    SHOW_COB(CwfMetadataKey.CWF_PREF_WATCH_SHOW_COB.key, R.string.key_show_cob),
    SHOW_DELTA(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DELTA.key, R.string.key_show_delta),
    SHOW_AVG_DELTA(CwfMetadataKey.CWF_PREF_WATCH_SHOW_AVG_DELTA.key, R.string.key_show_avg_delta),
    SHOW_DETAILED_DELTA(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DETAILED_DELTA.key, R.string.key_show_detailed_delta),
    SHOW_UPLOADER_BATTERY(CwfMetadataKey.CWF_PREF_WATCH_SHOW_UPLOADER_BATTERY.key, R.string.key_show_uploader_battery),
    SHOW_RIG_BATTERY(CwfMetadataKey.CWF_PREF_WATCH_SHOW_RIG_BATTERY.key, R.string.key_show_rig_battery),
    SHOW_TEMP_BASAL(CwfMetadataKey.CWF_PREF_WATCH_SHOW_TEMP_BASAL.key, R.string.key_show_temp_basal),
    SHOW_DIRECTION(CwfMetadataKey.CWF_PREF_WATCH_SHOW_DIRECTION.key, R.string.key_show_direction),
    SHOW_AGO(CwfMetadataKey.CWF_PREF_WATCH_SHOW_AGO.key, R.string.key_show_ago),
    SHOW_BG(CwfMetadataKey.CWF_PREF_WATCH_SHOW_BG.key, R.string.key_show_bg),
    SHOW_BGI(CwfMetadataKey.CWF_PREF_WATCH_SHOW_BGI.key, R.string.key_show_bgi),
    SHOW_LOOP_STATUS(CwfMetadataKey.CWF_PREF_WATCH_SHOW_LOOP_STATUS.key, R.string.key_show_external_status),
    SHOW_WEEK_NUMBER(CwfMetadataKey.CWF_PREF_WATCH_SHOW_WEEK_NUMBER.key, R.string.key_show_week_number)
}


