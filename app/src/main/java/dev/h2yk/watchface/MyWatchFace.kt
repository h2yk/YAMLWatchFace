package dev.h2yk.watchface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.widget.Toast

import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn"t
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class MyWatchFace : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: MyWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<MyWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar
        private lateinit var mTimeZone: TimeZone

        private lateinit var mDateFormat: SimpleDateFormat
        private lateinit var mDayOfWeekFormat: SimpleDateFormat

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false

        private var mFontSize = 24F

        private var mKeyColor: Int = 0
        private var mStringColor: Int = 0
        private var mIntColor: Int = 0
        private var mStdColor: Int = 0
        private var mDateColor: Int = 0

        private lateinit var mTypeFace: Typeface

        private lateinit var mKeyPaint: Paint
        private lateinit var mStringPaint: Paint
        private lateinit var mIntPaint: Paint
        private lateinit var mStdPaint: Paint
        private lateinit var mDatePaint: Paint

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mBackgroundBitmap: Bitmap
        private lateinit var mGrayBackgroundBitmap: Bitmap

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()
            mTimeZone = TimeZone.getDefault()
            mDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            mDayOfWeekFormat = SimpleDateFormat("EEEE", Locale.ENGLISH)

            initializeBackground()
            initializeWatchFace()
        }

        private fun initializeBackground() {
            mBackgroundPaint = Paint().apply {
                color = Color.BLACK
            }
            mBackgroundBitmap =
                BitmapFactory.decodeResource(resources, R.drawable.watchface_service_bg)
        }

        private fun initializeWatchFace() {
            /* Set defaults for colors */
            mKeyColor = Color.parseColor("#66d9ef")
            mStringColor = Color.parseColor("#a6e22e")
            mIntColor = Color.parseColor("#ae81ff")
            mStdColor = Color.WHITE
            mDateColor = Color.parseColor("#fd971f")
            mTypeFace = resources.getFont(R.font.firacode)

            mKeyPaint = Paint().apply {
                color = mKeyColor
//                strokeWidth = HOUR_STROKE_WIDTH
                textSize = mFontSize
                typeface = mTypeFace
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }

            mStringPaint = Paint().apply {
                color = mStringColor
//                strokeWidth = MINUTE_STROKE_WIDTH
                textSize = mFontSize
                typeface = mTypeFace
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }

            mStdPaint = Paint().apply {
                color = mStdColor
//                strokeWidth = SECOND_TICK_STROKE_WIDTH
                textSize = mFontSize
                typeface = mTypeFace
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }

            mIntPaint = Paint().apply {
                color = mIntColor
//                strokeWidth = SECOND_TICK_STROKE_WIDTH
                textSize = mFontSize
                typeface = mTypeFace
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }

            mDatePaint = Paint().apply {
                color = mDateColor
//                strokeWidth = SECOND_TICK_STROKE_WIDTH
                textSize = mFontSize
                typeface = mTypeFace
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                // style = Paint.Style.STROKE
            }
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false
            )
            mBurnInProtection = properties.getBoolean(
                WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchHandStyle()

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        private fun updateWatchHandStyle() {
            if (mAmbient) {
                /* Set defaults for colors */
                // mKeyColor = Color.WHITE
                // mStringColor = Color.WHITE
                // mIntColor = Color.WHITE
                // mStdColor = Color.WHITE
                // mDateColor = Color.WHITE

                mKeyPaint.isAntiAlias = false
                mStringPaint.isAntiAlias = false
                mIntPaint.isAntiAlias = false
                mStdPaint.isAntiAlias = false
                mDatePaint.isAntiAlias = false

            } else {
                // mKeyPaint.color = mKeyColor
                // mStringPaint.color = mStringColor
                // mIntPaint.color = mIntColor
                // mStdPaint.color = mStdColor
                // mDatePaint.color = mDateColor

                mKeyPaint.isAntiAlias = true
                mStringPaint.isAntiAlias = true
                mIntPaint.isAntiAlias = true
                mStdPaint.isAntiAlias = true
                mDatePaint.isAntiAlias = true
            }
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                mKeyPaint.alpha = if (inMuteMode) 100 else 255
                mStringPaint.alpha = if (inMuteMode) 100 else 255
                mIntPaint.alpha = if (inMuteMode) 80 else 255
                mStdPaint.alpha = if (inMuteMode) 80 else 255
                mDatePaint.alpha = if (inMuteMode) 80 else 255
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /* Scale loaded background image (more efficient) if surface dimensions change. */
            val scale = width.toFloat() / mBackgroundBitmap.width.toFloat()

            mBackgroundBitmap = Bitmap.createScaledBitmap(
                mBackgroundBitmap,
                (mBackgroundBitmap.width * scale).toInt(),
                (mBackgroundBitmap.height * scale).toInt(), true
            )

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don"t want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap()
            }
        }

        private fun initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                mBackgroundBitmap.width,
                mBackgroundBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(mGrayBackgroundBitmap)
            val grayPaint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(colorMatrix)
            grayPaint.colorFilter = filter
            canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, grayPaint)
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP ->
                    {}
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    // Toast.makeText(applicationContext, R.string.message, Toast.LENGTH_SHORT)
                        // .show()
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            drawBackground(canvas)
            drawWatchFace(canvas)
        }

        private fun drawBackground(canvas: Canvas) {

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK)
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            }
        }

        private fun drawWatchFace(canvas: Canvas) {
            val props : Map<String, String> = mapOf(
                resources.getString(R.string.timezone) to "\"" + mTimeZone.getID() + "\"",
                resources.getString(R.string.localdatetime) to "",
                "  " + resources.getString(R.string.date) to "",
                "    " + resources.getString(R.string.today) to mDateFormat.format(mCalendar.getTime()),
                "    " + resources.getString(R.string.dayofweek) to "\"" + mDayOfWeekFormat.format(mCalendar.getTime()) + "\"",
                "  " + resources.getString(R.string.time) to "",
                "    " + resources.getString(R.string.now) to
                  " " + clockToStr(mCalendar.get(Calendar.HOUR_OF_DAY)) +
                  "  " + clockToStr(mCalendar.get(Calendar.MINUTE)) +
                  if(!mAmbient) "  " + clockToStr(mCalendar.get(Calendar.SECOND)) else ""
            )
            var height = 120F
            var counter = 0
            props.forEach {
                val width = mKeyPaint.measureText(it.key)
                canvas.drawText(it.key, 40F, height, mKeyPaint)
                canvas.drawText(":", 40F + width, height, mStdPaint)
                var paint: Paint = mStringPaint
                when(counter) {
                    3 -> paint = mDatePaint
                    6 -> {
                        paint = mIntPaint
                        canvas.drawText(if(mAmbient) "[  ,   ]" else "[  ,   ,   ]", 60F + width, height, mStdPaint)
                    }
                }
                canvas.drawText(it.value, 60F + width, height, paint)
                height += 32F
                counter++
            }
            

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {

            }
        }

        private fun clockToStr(time: Int): String {
            return (if(time < 10) "0" else "") + time
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}