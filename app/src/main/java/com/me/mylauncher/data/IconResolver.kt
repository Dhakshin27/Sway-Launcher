package com.me.mylauncher.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.util.LruCache
import android.util.Xml
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import kotlin.math.roundToInt

class IconResolver(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "IconResolver"
        private const val APPFILTER_PATH = "lawnicons/appfilter.xml"
        private const val SVG_FOLDER = "lawnicons/svgs/"
        private const val ICON_SIZE = 128
    }

    @Volatile
    private var iconMappings: Map<ComponentName, String>? = null

    private val mappingLock = Any()

    // 4 MB in-memory cache for rendered SVG bitmaps
    private val bitmapCache = object : LruCache<String, Bitmap>(4 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    suspend fun resolve(componentName: ComponentName): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val mappings = getMappings()
                val drawableName = mappings[componentName] ?: return@withContext null

                synchronized(bitmapCache) {
                    bitmapCache.get(drawableName)?.let {
                        return@withContext it
                    }
                }

                val fileName = if (drawableName.endsWith(".svg")) {
                    drawableName
                } else {
                    "$drawableName.svg"
                }

                val assetPath = "$SVG_FOLDER$fileName"
                val svg = SVG.getFromAsset(appContext.assets, assetPath)

                // 1. Ensure the SVG has a valid viewBox so it can scale down from 192x192 to 128x128.
                if (svg.documentViewBox == null) {
                    val docW = if (svg.documentWidth > 0f) svg.documentWidth else 192f
                    val docH = if (svg.documentHeight > 0f) svg.documentHeight else 192f
                    svg.setDocumentViewBox(0f, 0f, docW, docH)
                }

                // 2. Calculate target inner size with an 8% safe margin on all sides.
                val inset = (ICON_SIZE * 0.08f).roundToInt()
                val targetSize = (ICON_SIZE - (inset * 2)).toFloat()

                // 3. Force the SVG document dimensions to match our exact target size
                // and center-scale it proportionally.
                svg.setDocumentWidth(targetSize)
                svg.setDocumentHeight(targetSize)
                svg.setDocumentPreserveAspectRatio(PreserveAspectRatio.LETTERBOX)

                val bitmap = Bitmap.createBitmap(
                    ICON_SIZE,
                    ICON_SIZE,
                    Bitmap.Config.ARGB_8888
                )

                val canvas = Canvas(bitmap)
                canvas.save()
                canvas.translate(inset.toFloat(), inset.toFloat())
                svg.renderToCanvas(canvas)
                canvas.restore()

                synchronized(bitmapCache) {
                    bitmapCache.put(drawableName, bitmap)
                }

                bitmap
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Could not resolve Lawnicons icon for $componentName", exception)
                null
            }
        }

    private fun getMappings(): Map<ComponentName, String> {
        iconMappings?.let { return it }

        synchronized(mappingLock) {
            iconMappings?.let { return it }

            val mappings = mutableMapOf<ComponentName, String>()

            appContext.assets.open(APPFILTER_PATH).use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, "UTF-8")

                var eventType = parser.eventType

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val componentValue = parser.getAttributeValue(null, "component")
                        val drawableValue = parser.getAttributeValue(null, "drawable")

                        if (componentValue != null && drawableValue != null) {
                            val flattenedComponent = componentValue
                                .removePrefix("ComponentInfo{")
                                .removeSuffix("}")

                            val component = ComponentName.unflattenFromString(flattenedComponent)
                            if (component != null) {
                                mappings[component] = drawableValue
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }

            Log.d(TAG, "Loaded ${mappings.size} Lawnicons mappings")
            iconMappings = mappings
            return mappings
        }
    }

    fun clearMemoryCache() {
        synchronized(bitmapCache) {
            bitmapCache.evictAll()
        }
    }
}