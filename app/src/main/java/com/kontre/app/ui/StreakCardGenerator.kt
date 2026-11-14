package com.kontre.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.kontre.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Generates an ultra-crisp, dark aesthetic graphic card for sharing Kontre streaks.
 * Exports a 1080x1440 PNG ready for Instagram Stories, WhatsApp Status, and social feeds.
 */
object StreakCardGenerator {

    private const val TAG = "StreakCardGenerator"
    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1440

    /**
     * Builds the graphic card bitmap, saves it to internal cache, and launches the system share sheet.
     */
    suspend fun generateAndShareCard(
        context: Context,
        stage: DragonStage,
        currentStreak: Int,
        highestStreak: Int,
        startDate: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = createCardBitmap(context, stage, currentStreak, highestStreak, startDate)
            val cacheDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            
            // Clean up old cards to save disk space
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("kontre_streak_") && file.name.endsWith(".png")) {
                    file.delete()
                }
            }

            val file = File(cacheDir, "kontre_streak_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "I'm on a $currentStreak-day streak reclaiming my focus with Kontre! 🐉 Guarded from doomscrolling loops. #Kontre"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Kontre Streak").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate or share streak card", e)
            false
        }
    }

    /**
     * Renders the complete graphic card using Android Canvas.
     */
    fun createCardBitmap(
        context: Context,
        stage: DragonStage,
        currentStreak: Int,
        highestStreak: Int,
        startDate: String
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background Gradient (Deep obsidian to twilight slate)
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_HEIGHT.toFloat(),
                Color.parseColor("#0C0D11"),
                Color.parseColor("#15181F"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

        // 2. Outer Card Shell with Subtle Emerald Border
        val cardBounds = RectF(36f, 36f, CARD_WIDTH - 36f, CARD_HEIGHT - 36f)
        val cardBgPaint = Paint().apply {
            color = Color.parseColor("#111318")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardBounds, 44f, 44f, cardBgPaint)

        val cardBorderPaint = Paint().apply {
            color = Color.parseColor("#2807D98C") // 16% emerald outline
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardBounds, 44f, 44f, cardBorderPaint)

        // 3. Header: App Logo, Wordmark & Focus Shield Badge
        // Emerald logo squircle
        val logoSquircle = RectF(76f, 76f, 148f, 148f)
        val squircleBgPaint = Paint().apply {
            color = Color.parseColor("#173B2C")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(logoSquircle, 22f, 22f, squircleBgPaint)

        val squircleBorderPaint = Paint().apply {
            color = Color.parseColor("#07D98C")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(logoSquircle, 22f, 22f, squircleBorderPaint)

        // Draw dragon emblem inside squircle
        try {
            val emblemBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_kontre_emblem)
            if (emblemBitmap != null) {
                val dst = Rect(88, 88, 136, 136)
                canvas.drawBitmap(emblemBitmap, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
        } catch (_: Exception) {}

        // KONTRE Brand Wordmark
        val brandPaint = Paint().apply {
            color = Color.WHITE
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.08f
        }
        canvas.drawText("KONTRE", 168f, 125f, brandPaint)

        // Focus Shield Pill (Top Right)
        val shieldPill = RectF(CARD_WIDTH - 320f, 84f, CARD_WIDTH - 76f, 140f)
        val pillBgPaint = Paint().apply {
            color = Color.parseColor("#13281E")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(shieldPill, 28f, 28f, pillBgPaint)

        val pillBorderPaint = Paint().apply {
            color = Color.parseColor("#07D98C")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(shieldPill, 28f, 28f, pillBorderPaint)

        val pillTextPaint = Paint().apply {
            color = Color.parseColor("#07D98C")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.06f
        }
        canvas.drawText("SHIELD ACTIVE", shieldPill.centerX(), shieldPill.centerY() + 7f, pillTextPaint)

        // 4. Central Feathered Emerald Glow Aura
        val glowPaint = Paint().apply {
            shader = RadialGradient(
                CARD_WIDTH / 2f, 530f, 380f,
                intArrayOf(
                    Color.parseColor("#5A07D98C"),
                    Color.parseColor("#2807D98C"),
                    Color.parseColor("#0807D98C"),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.45f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }
        canvas.drawCircle(CARD_WIDTH / 2f, 530f, 380f, glowPaint)

        // 5. Hero Dragon Illustration
        try {
            val dragonBitmap = BitmapFactory.decodeResource(context.resources, stage.drawableRes)
            if (dragonBitmap != null) {
                val size = 420
                val left = (CARD_WIDTH - size) / 2
                val top = 320
                val dst = Rect(left, top, left + size, top + size)
                val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(dragonBitmap, null, dst, filterPaint)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing dragon illustration", e)
        }

        // 6. Streak Number & Badge
        val streakNumPaint = Paint().apply {
            color = Color.WHITE
            textSize = 120f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("$currentStreak", CARD_WIDTH / 2f, 850f, streakNumPaint)

        val streakLabelPaint = Paint().apply {
            color = Color.parseColor("#07D98C")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
            isAntiAlias = true
        }
        canvas.drawText("DAYS BLOOM STREAK", CARD_WIDTH / 2f, 900f, streakLabelPaint)

        // Stage Title Pill (e.g. "Stage 4: Adult Dragon")
        val stagePill = RectF(CARD_WIDTH / 2f - 240f, 935f, CARD_WIDTH / 2f + 240f, 995f)
        val stagePillBg = Paint().apply {
            color = Color.parseColor("#181C23")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(stagePill, 30f, 30f, stagePillBg)

        val stagePillBorder = Paint().apply {
            color = Color.parseColor("#2A3440")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(stagePill, 30f, 30f, stagePillBorder)

        // Pulsing emerald dot inside pill
        val dotPaint = Paint().apply {
            color = Color.parseColor("#07D98C")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(stagePill.left + 38f, stagePill.centerY(), 8f, dotPaint)

        val stageTextPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(stage.title, stagePill.left + 60f, stagePill.centerY() + 8f, stageTextPaint)

        // 7. Statistics Card Container (Starting Date & Personal Record)
        val statsBounds = RectF(76f, 1040f, CARD_WIDTH - 76f, 1220f)
        val statsBg = Paint().apply {
            color = Color.parseColor("#14171E")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(statsBounds, 28f, 28f, statsBg)

        val statsBorder = Paint().apply {
            color = Color.parseColor("#1E242F")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(statsBounds, 28f, 28f, statsBorder)

        // Left stat: STARTING DATE OF STREAK
        val labelPaint = Paint().apply {
            color = Color.parseColor("#808A9D")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
            isAntiAlias = true
        }
        canvas.drawText("STARTING DATE", 116f, 1105f, labelPaint)

        val valuePaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(startDate, 116f, 1160f, valuePaint)

        // Vertical divider
        val divPaint = Paint().apply {
            color = Color.parseColor("#202735")
            strokeWidth = 2f
        }
        canvas.drawLine(CARD_WIDTH / 2f + 20f, 1070f, CARD_WIDTH / 2f + 20f, 1190f, divPaint)

        // Right stat: PERSONAL RECORD
        canvas.drawText("PERSONAL RECORD", CARD_WIDTH / 2f + 60f, 1105f, labelPaint)

        val recordPaint = Paint().apply {
            color = Color.parseColor("#07D98C")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("$highestStreak Days", CARD_WIDTH / 2f + 60f, 1162f, recordPaint)

        // 8. Footer Callout & Watermark
        val footerPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Guarded from compulsive doomscrolling loops", CARD_WIDTH / 2f, 1295f, footerPaint)

        val watermarkPaint = Paint().apply {
            color = Color.parseColor("#07D98C")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
            isAntiAlias = true
        }
        canvas.drawText("kontre.app", CARD_WIDTH / 2f, 1345f, watermarkPaint)

        return bitmap
    }
}

