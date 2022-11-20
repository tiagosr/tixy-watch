package esque.ma.tixy

import android.content.Context
import android.graphics.*
import android.view.SurfaceHolder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import java.lang.Math.pow
import java.time.ZonedDateTime
import kotlin.math.*

private const val FRAME_PERIOD_MS_DEFAULT: Long = 16L

class TixyWatchCanvasRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : Renderer.CanvasRenderer2<TixyWatchCanvasRenderer.ZeitpunktSharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    FRAME_PERIOD_MS_DEFAULT,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {
    class Configs {
        lateinit var mTypeface: Typeface
        lateinit var positivePaint: Paint
        lateinit var negativePaint: Paint


        fun init() {
            mTypeface = Typeface.DEFAULT_BOLD
            positivePaint = Paint().apply {
                color = Color.White.toArgb()
                isAntiAlias = true
            }
            negativePaint = Paint().apply {
                color = Color(255, 62, 75, 255).toArgb()
                isAntiAlias = true
            }
        }
    }

    private var configs = Configs()

    class ZeitpunktSharedAssets: SharedAssets {
        override fun onDestroy() {
        }
    }

    override suspend fun createSharedAssets(): ZeitpunktSharedAssets {
        return ZeitpunktSharedAssets()
    }

    override suspend fun init() {
        super.init()

        configs.init()
    }

    private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.render(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: ZeitpunktSharedAssets
    ) {
        renderWatchFace(configs, canvas, bounds, zonedDateTime) {
                canvas, zonedDateTime -> drawComplications(canvas, zonedDateTime)
        }

    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: ZeitpunktSharedAssets
    ) {
        TODO("Not yet implemented")
    }

    companion object {
        private const val TAG = "ZeitpunktWatchCanvasRenderer"

        val font = arrayOf(
        //0
        0b0011, 0b0101,	0b0101,	0b0101,	0b0110,
        //1
        0b0010,	0b0110,	0b0010,	0b0010,	0b0111,
        //2
        0b0110,	0b0001,	0b0010,	0b0100,	0b0111,
        //3
        0b0110,	0b0001,	0b0011,	0b0001,	0b0110,
        //4
        0b0001,	0b0101,	0b0111,	0b0001,	0b0001,
        //5
        0b0111,	0b0100,	0b0110,	0b0001,	0b0110,
        //6
        0b0011,	0b0100,	0b0110,	0b0101,	0b0010,
        //7
        0b0111,	0b0001,	0b0010,	0b0100,	0b0100,
        //8
        0b0011,	0b0101,	0b0010,	0b0101,	0b0110,
        //9
        0b0011,	0b0101,	0b0111,	0b0001,	0b0110
        )

        fun map(value:Float, fromStart:Float, fromEnd:Float, toStart:Float, toEnd:Float):Float =
            toStart+(toEnd-toStart)*((value-fromStart)/(fromEnd-fromStart))
        fun lerp(value:Float, start:Float, end:Float):Float = start + ((end-start) * value)
        fun lerp(value:Double, start:Double, end:Double):Double = start + ((end-start) * value)
        fun smootherstep(x:Double): Double = when {
            x > 1.0 -> 1.0
            x < 0.0 -> 0.0
            else -> x * x * x * (10 - 15 * x + 6 * x * x)
        }

        fun hypot(x:Double, y:Double, z:Double): Double = sqrt(x*x + y*y + z*z)

        fun tixy_noise(t:Double, i:Int, x:Int, y:Int): Double = cos(t+i+x*y)
        fun tixy_raindrops(t:Double, i:Int, x:Int, y:Int): Double = -.4/(hypot(x-t%10,y-t%8)-t%2*9)
        fun tixy_dialogue_with_an_alien(t:Double, i:Int, x:Int, y:Int):Double = 1.0/32.0*tan(t/64.0*x*tan(i-x.toDouble()))
        fun tixy_circles(t:Double, i:Int, x:Int, y:Int): Double = sin(x/2.0) - sin(x-t) - y+6.0
        fun tixy_sunrise(t:Double, i:Int, x:Int, y:Int): Double = -sqrt((x-7.5)*(x-7.5)+(y-14)*(y-14))*0.05+sin(atan2(x-7.5,y-14.0)*12+t)
        fun tixy_heart1(t:Double, i:Int, x:Int, y:Int): Double = -acos((sin(t)*9+25-hypot(abs(x-7f),y-9f+abs(x-7)*.8f).pow(2.0f))/5)
        fun tixy_heart2(t:Double, i:Int, x:Int, y:Int): Double = hypot(x-8.0, y-9.0+abs(x-8.0), sin(t)*2.0) - 5.0
        fun tixy_xor(t:Double, i:Int, x:Int, y:Int): Double = 6f * cos(t) + 3*cos(t/3f) - (x xor y) + 7.5f
        val tixyArray = arrayOf(::tixy_noise, ::tixy_raindrops, ::tixy_dialogue_with_an_alien, ::tixy_circles, ::tixy_sunrise, ::tixy_heart1, ::tixy_heart2, ::tixy_xor )

        fun tixyChoose(t:Double, i:Int, x:Int, y:Int, n:Int):Double = tixyArray[n](t, i, x, y)
        fun tixyMix(t:Double, i:Int, x:Int, y:Int, n:Double):Double {
            val n_a = n.toInt() % tixyArray.size
            var n_b = (n.toInt()+1) % tixyArray.size
            var frac = n - floor(n)
            var stepped = smootherstep(10.0 * frac - 9.0)

            return lerp(stepped, tixyArray[n_a](t,i,x,y), tixyArray[n_b](t,i,x,y))
        }

        fun polar(x:Float, y:Float): Pair<Float, Float> = Pair(x * sqrt(1.0f - y * y / 2.0f), y * sqrt(1.0f - x * x / 2.0f))

        fun clamp(x:Float, a:Float, b:Float):Float = if (x.isNaN()) 0f else min(b, max(a, x))

        fun dist(p1:Pair<Float, Float>, p2:Pair<Float, Float>): Float = sqrt((p2.first - p1.first)*(p2.first-p1.first) + (p2.second-p1.second)*(p2.second-p1.second))

        const val MIN_COORD = -0.5f
        const val MAX_COORD = 15.5f

        fun renderWatchFace(
            configs: Configs, canvas: Canvas,
            bounds: Rect,
            now: ZonedDateTime,
            complicationsDraw: (canvas:Canvas, zonedDateTime:ZonedDateTime) -> Unit
        ) {

            val windowCenterX = bounds.exactCenterX()
            val windowCenterY = bounds.exactCenterY()
            val maxDim = min(bounds.width(), bounds.height()) / 2.0f;

            canvas.drawColor(Color.Black.toArgb())

            complicationsDraw(canvas, now)

            val timeR = FloatArray(256) { 0.0f }
            val mins = now.minute
            val ma = (mins/10)
            val mb = (mins%10)
            val hrs = now.hour
            val ha = (hrs/10);
            val hb = (hrs%10);
            for (y in 0..4) {
                val hla = font[ha*5+y];
                val hlb = font[hb*5+y];
                val mla = font[ma*5+y];
                val mlb = font[mb*5+y];
                for (x in 0..4) {
                    timeR[16*(y+6)+x+0]  = lerp(timeR[16*(y+6)+x+0],  if (hla and (0b1000 shr x) != 0) (if (ha==0) 0.0f else -1.0f) else 0.0f, 0.1f);
                    timeR[16*(y+6)+x+4]  = lerp(timeR[16*(y+6)+x+4],  if (hlb and (0b1000 shr x) != 0) -1.0f else 0.0f, 0.1f);
                    timeR[16*(y+6)+x+8]  = lerp(timeR[16*(y+6)+x+8],  if (mla and (0b1000 shr x) != 0) 1.0f else 0.0f, 0.1f);
                    timeR[16*(y+6)+x+12] = lerp(timeR[16*(y+6)+x+12], if (mlb and (0b1000 shr x) != 0) 1.0f else 0.0f, 0.1f);
                }
            }
            val colon = sin((now.toInstant().toEpochMilli())/(PI*125.0)).toFloat();
            timeR[16*7+8] = colon;
            timeR[16*9+8] = colon;

            val t = now.toInstant().toEpochMilli() / 1000.0
            for (y in 0..15) {
                for (x in 0..15) {
                    val v = min(1.0f, max(-1.0f,
                        lerp(
                            clamp((map(abs(y-8f), 2f, 7f, 0f, 1f)), 0f, 1f),
                            timeR[x + 16*y],
                            clamp(tixyMix(t, x + 16*y, x, y, t*0.025).toFloat(), -1.0f, 1.0f)
                        )

                    ))
                    val xp = map(x.toFloat(), MIN_COORD, MAX_COORD, -1.0f, 1.0f);
                    val yp = map(y.toFloat(), MIN_COORD, MAX_COORD, -1.0f, 1.0f);
                    val xw = map(x-1.0f, MIN_COORD, MAX_COORD, -1.0f, 1.0f);
                    val yn = map(y-1f, MIN_COORD, MAX_COORD, -1.0f, 1.0f);
                    val xe = map(x+1f, MIN_COORD, MAX_COORD, -1.0f, 1.0f);
                    val ys = map(y+1f, MIN_COORD, MAX_COORD, -1.0f, 1.0f);
                    val p = polar(xp, yp);
                    val pnw = polar(xw, yn);
                    val pne = polar(xe, yn);
                    val psw = polar(xw, ys);
                    val pse = polar(xe, ys);
                    val maxSize = min(dist(pnw,pse), dist(pne,psw)) * 0.18f;
                    val size = map(abs(v.toFloat()), 0.0f, 1.0f, 0.0f, maxDim*maxSize);

                    canvas.drawCircle(
                        map(p.first, -1.0f, 1.0f, windowCenterX-maxDim, windowCenterX+maxDim),
                        map(p.second, -1.0f, 1.0f, windowCenterY-maxDim, windowCenterY+maxDim),
                        size,
                        if (v>=0) configs.positivePaint else configs.negativePaint
                    )
                }
            }
        }
    }

}