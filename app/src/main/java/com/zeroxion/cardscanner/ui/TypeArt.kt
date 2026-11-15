package com.zeroxion.cardscanner.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/*
 * Eigene, frei gezeichnete Motive zu jeder Farbwelt – Wellen und Blasen, Flammen und Funken,
 * Gräser und Blätter, Blitze, Wellenringe und Glitzer. Alles läuft in einer Schleife von
 * 24 Sekunden, jede Bewegung macht darin ganze Umläufe, damit es nahtlos weiterläuft.
 */

private const val TWO_PI = (2 * PI).toFloat()

private class Particle(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Int,
    val phase: Float,
    val rot: Float,
)

private fun particles(seed: Int, count: Int): List<Particle> {
    val r = Random(seed)
    return List(count) {
        Particle(
            x = r.nextFloat(),
            y = r.nextFloat(),
            size = r.nextFloat(),
            speed = 1 + r.nextInt(3),
            phase = r.nextFloat(),
            rot = r.nextFloat() * 360f,
        )
    }
}

private val Bubbles = particles(11, 20)
private val Embers = particles(23, 26)
private val Blades = particles(37, 46)
private val Leaves = particles(41, 12)
private val Sparks = particles(53, 28)
private val Orbs = particles(67, 10)

private fun frac(v: Float): Float = v - floor(v)
private fun wave(t: Float, cycles: Int, phase: Float = 0f): Float = sin(TWO_PI * (t * cycles + phase))

/** Zeitwert 0…1, der alle 24 Sekunden von vorn beginnt. */
@Composable
fun rememberArtTime(): State<Float> {
    val transition = rememberInfiniteTransition(label = "typeArt")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "typeArtTime",
    )
}

/** Animierte Motive der Farbwelt über die ganze Fläche (liegt hinter dem Inhalt). */
@Composable
fun TypeDecoration(theme: TypeTheme, modifier: Modifier = Modifier) {
    val time = rememberArtTime()
    Canvas(modifier.fillMaxSize()) {
        drawTypeArt(theme, time.value)
    }
}

/** Zeichnet die Motive; [scale] verkleinert sie für Vorschauen. */
fun DrawScope.drawTypeArt(theme: TypeTheme, t: Float, scale: Float = 1f) {
    val u = density * scale
    when (theme) {
        TypeTheme.WATER -> drawWater(t, u)
        TypeTheme.FIRE -> drawFire(t, u)
        TypeTheme.GRASS -> drawGrass(t, u)
        TypeTheme.ELECTRIC -> drawElectric(t, u)
        TypeTheme.PSYCHIC -> drawPsychic(t, u)
        TypeTheme.CLASSIC -> drawSparkles(t, u, theme.glows)
    }
}

// ---------------------------------------------------------------- Formen

/** Tropfen, Spitze zeigt nach oben (vor dem Drehen). */
private fun dropPath(s: Float) = Path().apply {
    moveTo(0f, -s * 1.7f)
    cubicTo(s * 0.9f, -s * 0.5f, s, s * 0.55f, 0f, s)
    cubicTo(-s, s * 0.55f, -s * 0.9f, -s * 0.5f, 0f, -s * 1.7f)
    close()
}

/** Blatt mit Spitzen oben und unten. */
private fun leafPath(s: Float) = Path().apply {
    moveTo(0f, -s)
    cubicTo(s * 0.85f, -s * 0.55f, s * 0.75f, s * 0.6f, 0f, s)
    cubicTo(-s * 0.75f, s * 0.6f, -s * 0.85f, -s * 0.55f, 0f, -s)
    close()
}

/** Vierzackiger Glitzerstern. */
private fun sparklePath(s: Float) = Path().apply {
    val c = s * 0.16f
    moveTo(0f, -s)
    cubicTo(c, -c, c, -c, s, 0f)
    cubicTo(c, c, c, c, 0f, s)
    cubicTo(-c, c, -c, c, -s, 0f)
    cubicTo(-c, -c, -c, -c, 0f, -s)
    close()
}

/** Klassischer Zickzack-Blitz, Höhe [s]. */
private fun boltPath(s: Float) = Path().apply {
    val w = s * 0.55f
    moveTo(w * 0.25f, -s / 2)
    lineTo(w * 0.95f, -s / 2)
    lineTo(w * 0.45f, -s * 0.06f)
    lineTo(w * 0.9f, -s * 0.06f)
    lineTo(-w * 0.35f, s / 2)
    lineTo(w * 0.05f, s * 0.05f)
    lineTo(-w * 0.4f, s * 0.05f)
    close()
}

// ---------------------------------------------------------------- Wasser

private fun DrawScope.drawWater(t: Float, u: Float) {
    val w = size.width
    val h = size.height
    val deep = Color(0xFF2563EB)
    val light = Color(0xFF7DD3FC)

    // Drei Wellenschichten am unteren Rand, die gegeneinander laufen.
    for (i in 0 until 3) {
        val base = h * (0.80f + i * 0.065f)
        val amp = (12f + i * 5f) * u
        val length = w * (0.95f - i * 0.18f)
        val dir = if (i % 2 == 0) 1 else -1
        val shift = TWO_PI * t * (2 + i) * dir
        val path = Path().apply {
            moveTo(0f, h)
            var x = 0f
            while (x <= w + 8f) {
                lineTo(x, base + amp * sin(TWO_PI * x / length + shift))
                x += 6f
            }
            lineTo(w, h)
            close()
        }
        drawPath(
            path,
            Brush.verticalGradient(
                listOf(light.copy(alpha = 0.26f - i * 0.04f), deep.copy(alpha = 0.10f)),
                startY = base - amp,
                endY = h,
            ),
        )
        // Schaumkante
        val crest = Path().apply {
            var x = 0f
            moveTo(0f, base + amp * sin(shift))
            while (x <= w + 8f) {
                lineTo(x, base + amp * sin(TWO_PI * x / length + shift))
                x += 6f
            }
        }
        drawPath(crest, Color.White.copy(alpha = 0.18f - i * 0.04f), style = Stroke(1.6f * u, cap = StrokeCap.Round))
    }

    // Aufsteigende Luftblasen
    Bubbles.forEach { p ->
        val rise = frac(p.y + t * (p.speed + 1))
        val y = h * (1.05f - rise * 1.15f)
        val x = p.x * w + wave(t, 4, p.phase) * 10f * u
        val r = (3f + p.size * 10f) * u
        val fade = if (rise > 0.85f) (1f - rise) / 0.15f else 1f
        drawCircle(light.copy(alpha = 0.10f * fade), r, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.38f * fade), r, Offset(x, y), style = Stroke(1.2f * u))
        drawCircle(Color.White.copy(alpha = 0.6f * fade), r * 0.22f, Offset(x - r * 0.38f, y - r * 0.38f))
    }

    // Wasserspritzer oben rechts: Krone aus Tropfen über einem Ring
    val center = Offset(w * 0.84f, h * 0.17f)
    val bob = wave(t, 3) * 4f * u
    drawOval(
        light.copy(alpha = 0.22f),
        topLeft = Offset(center.x - 46f * u, center.y + 18f * u),
        size = Size(92f * u, 18f * u),
        style = Stroke(2f * u),
    )
    // Aufspritzende Wassersäule in der Mitte
    val column = Path().apply {
        moveTo(center.x - 14f * u, center.y + 27f * u)
        cubicTo(center.x - 8f * u, center.y + 8f * u, center.x - 5f * u, center.y - 4f * u + bob, center.x, center.y - 10f * u + bob)
        cubicTo(center.x + 5f * u, center.y - 4f * u + bob, center.x + 8f * u, center.y + 8f * u, center.x + 14f * u, center.y + 27f * u)
        close()
    }
    drawPath(column, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), light.copy(alpha = 0.2f)), center.y - 10f * u, center.y + 27f * u))
    drawCircle(Color.White.copy(alpha = 0.55f), 5f * u, Offset(center.x, center.y - 22f * u + bob * 1.5f))
        val drops = listOf(-62f to 1.0f, -32f to 1.25f, 0f to 1.45f, 32f to 1.2f, 62f to 0.95f)
    drops.forEachIndexed { i, (angle, reach) ->
        val rad = Math.toRadians(angle.toDouble() - 90).toFloat()
        val dist = (34f + reach * 18f) * u + if (i % 2 == 0) bob else -bob
        val pos = Offset(center.x + cos(rad) * dist, center.y + 22f * u + sin(rad) * dist)
        translate(pos.x, pos.y) {
            // Spitze zeigt zur Aufprallstelle – der Tropfen fliegt von ihr weg.
            rotate(angle + 180f, pivot = Offset.Zero) {
                val s = (5f + reach * 3f) * u
                drawPath(dropPath(s), Brush.verticalGradient(listOf(light.copy(alpha = 0.35f), Color.White.copy(alpha = 0.6f)), -s * 1.7f, s))
            }
        }
    }
    // Einzelne Tropfen links oben
    listOf(Offset(0.10f, 0.12f) to 7f, Offset(0.22f, 0.07f) to 5f, Offset(0.16f, 0.22f) to 4f).forEachIndexed { i, (pos, s) ->
        val y = h * pos.y + wave(t, 2, i * 0.3f) * 5f * u
        translate(w * pos.x, y) {
            drawPath(dropPath(s * u), light.copy(alpha = 0.3f))
            drawCircle(Color.White.copy(alpha = 0.5f), s * u * 0.25f, Offset(-s * u * 0.3f, s * u * 0.1f))
        }
    }
}

// ---------------------------------------------------------------- Feuer

private fun flamePath(cx: Float, baseY: Float, height: Float, width: Float, sway: Float) = Path().apply {
    moveTo(cx - width / 2, baseY)
    cubicTo(cx - width * 0.62f, baseY - height * 0.45f, cx - width * 0.1f + sway * 0.4f, baseY - height * 0.72f, cx + sway, baseY - height)
    cubicTo(cx + width * 0.2f + sway * 0.4f, baseY - height * 0.66f, cx + width * 0.66f, baseY - height * 0.4f, cx + width / 2, baseY)
    close()
}

private fun DrawScope.drawFire(t: Float, u: Float) {
    val w = size.width
    val h = size.height
    val red = Color(0xFFFF3B30)
    val orange = Color(0xFFFF8A3D)
    val yellow = Color(0xFFFFD166)

    // Flammenreihe am unteren Rand
    val count = maxOf(6, (w / (70f * u)).toInt())
    for (i in 0 until count) {
        val p = Embers[i % Embers.size]
        val cx = w * (i + 0.5f) / count + (p.x - 0.5f) * 24f * u
        val flicker = 1f + 0.12f * wave(t, 18 + p.speed * 4, p.phase)
        val height = (90f + p.size * 110f) * u * flicker
        val width = height * 0.5f
        val sway = wave(t, 6 + p.speed, p.phase) * 10f * u
        val base = h + 6f * u
        drawPath(
            flamePath(cx, base, height, width, sway),
            Brush.verticalGradient(listOf(red.copy(alpha = 0.0f), red.copy(alpha = 0.32f)), base - height, base),
        )
        drawPath(
            flamePath(cx, base, height * 0.68f, width * 0.7f, sway * 0.8f),
            Brush.verticalGradient(listOf(orange.copy(alpha = 0.05f), orange.copy(alpha = 0.36f)), base - height * 0.68f, base),
        )
        drawPath(
            flamePath(cx, base, height * 0.4f, width * 0.42f, sway * 0.6f),
            Brush.verticalGradient(listOf(yellow.copy(alpha = 0.1f), yellow.copy(alpha = 0.42f)), base - height * 0.4f, base),
        )
    }

    // Aufsteigende Funken
    Embers.forEach { p ->
        val rise = frac(p.y + t * (p.speed + 2))
        val y = h * (1.0f - rise * 0.95f)
        val x = p.x * w + wave(t, 5, p.phase) * 16f * u * rise
        val r = (1.4f + p.size * 2.4f) * u
        val alpha = (1f - rise) * 0.85f
        drawCircle(orange.copy(alpha = alpha * 0.25f), r * 3.2f, Offset(x, y))
        drawCircle(yellow.copy(alpha = alpha), r, Offset(x, y))
    }

    // Wärmeglühen oben links
    drawCircle(
        Brush.radialGradient(listOf(orange.copy(alpha = 0.16f), Color.Transparent), Offset(0f, 0f), w * 0.6f),
        radius = w * 0.6f,
        center = Offset(0f, 0f),
    )
}

// ---------------------------------------------------------------- Pflanze

private fun DrawScope.drawGrass(t: Float, u: Float) {
    val w = size.width
    val h = size.height
    val dark = Color(0xFF15803D)
    val green = Color(0xFF4ADE80)
    val lime = Color(0xFFA3E635)

    // Grashalme, die im Wind schwingen
    val count = maxOf(24, (w / (12f * u)).toInt())
    for (i in 0 until count) {
        val p = Blades[i % Blades.size]
        val x = w * i / count + (p.x - 0.5f) * 10f * u
        val height = (45f + p.size * 95f) * u
        val sway = (wave(t, 3, x / w * 1.5f) * 0.7f + wave(t, 7, p.phase) * 0.3f) * 16f * u * (height / (140f * u))
        val half = (2.2f + p.size * 1.8f) * u
        val path = Path().apply {
            moveTo(x - half, h)
            cubicTo(x - half * 0.7f, h - height * 0.5f, x + sway * 0.5f, h - height * 0.82f, x + sway, h - height)
            cubicTo(x + sway * 0.5f + half * 0.4f, h - height * 0.8f, x + half * 0.9f, h - height * 0.5f, x + half, h)
            close()
        }
        val color = if (i % 3 == 0) lime else if (i % 3 == 1) green else dark
        drawPath(path, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.42f)), h - height, h))
    }

    // Fallende Blätter
    Leaves.forEach { p ->
        val fall = frac(p.y + t * p.speed)
        val y = -30f * u + fall * (h + 60f * u)
        val x = p.x * w + wave(t, 3, p.phase) * 34f * u
        val s = (9f + p.size * 9f) * u
        val angle = p.rot + t * 360f * p.speed + wave(t, 4, p.phase) * 35f
        translate(x, y) {
            rotate(angle, pivot = Offset.Zero) {
                drawPath(leafPath(s), Brush.linearGradient(listOf(lime.copy(alpha = 0.5f), dark.copy(alpha = 0.45f)), Offset(-s, -s), Offset(s, s)))
                drawLine(dark.copy(alpha = 0.5f), Offset(0f, -s * 0.85f), Offset(0f, s * 0.9f), strokeWidth = 1.1f * u)
            }
        }
    }

    // Ranke oben rechts
    val vine = Path().apply {
        moveTo(w, h * 0.02f)
        cubicTo(w * 0.86f, h * 0.05f, w * 0.9f, h * 0.15f, w * 0.78f, h * 0.18f)
        cubicTo(w * 0.7f, h * 0.2f, w * 0.68f, h * 0.12f, w * 0.73f, h * 0.11f)
    }
    drawPath(vine, green.copy(alpha = 0.32f), style = Stroke(2.2f * u, cap = StrokeCap.Round))
    listOf(Offset(0.92f, 0.06f) to 30f, Offset(0.86f, 0.13f) to -40f, Offset(0.79f, 0.165f) to 70f).forEachIndexed { i, (pos, a) ->
        translate(w * pos.x, h * pos.y) {
            rotate(a + wave(t, 4, i * 0.2f) * 6f, pivot = Offset.Zero) {
                drawPath(leafPath(11f * u), green.copy(alpha = 0.38f))
            }
        }
    }
}

// ---------------------------------------------------------------- Elektro

private fun DrawScope.drawElectric(t: Float, u: Float) {
    val w = size.width
    val h = size.height
    val yellow = Color(0xFFFFD23F)
    val white = Color(0xFFFFF7CC)

    // Große Blitze, die immer wieder aufleuchten
    val bolts = listOf(
        Triple(Offset(0.82f, 0.14f), 120f, 12f),
        Triple(Offset(0.12f, 0.40f), 90f, -14f),
        Triple(Offset(0.88f, 0.78f), 100f, 18f),
        Triple(Offset(0.20f, 0.90f), 70f, -8f),
    )
    bolts.forEachIndexed { i, (pos, s, angle) ->
        val beat = frac(t * (5 + i) + i * 0.27f)
        val flash = when {
            beat < 0.04f -> 1f
            beat < 0.07f -> 0.3f
            beat < 0.10f -> 0.8f
            else -> 0f
        }
        val alpha = 0.16f + flash * 0.5f
        translate(w * pos.x, h * pos.y) {
            rotate(angle, pivot = Offset.Zero) {
                val path = boltPath(s * u)
                if (flash > 0f) drawCircle(yellow.copy(alpha = 0.12f * flash), s * u, Offset.Zero)
                drawPath(path, yellow.copy(alpha = alpha * 0.5f), style = Stroke(6f * u, join = StrokeJoin.Round))
                drawPath(path, yellow.copy(alpha = alpha))
                drawPath(path, white.copy(alpha = alpha * 0.9f), style = Stroke(1.2f * u, join = StrokeJoin.Round))
            }
        }
    }

    // Knisternde Entladung quer über den oberen Rand
    val step = floor(t * 40f)
    val beat = frac(t * 40f)
    if (beat < 0.35f) {
        val r = Random(step.toInt())
        val startY = h * (0.05f + r.nextFloat() * 0.2f)
        val path = Path().apply {
            moveTo(0f, startY)
            var x = 0f
            var y = startY
            while (x < w) {
                x += (14f + r.nextFloat() * 26f) * u
                y += (r.nextFloat() - 0.5f) * 30f * u
                lineTo(x, y)
            }
        }
        val a = (0.35f - beat) / 0.35f * 0.4f
        drawPath(path, yellow.copy(alpha = a * 0.4f), style = Stroke(5f * u, join = StrokeJoin.Round))
        drawPath(path, white.copy(alpha = a), style = Stroke(1.3f * u, join = StrokeJoin.Round))
    }

    // Funken
    Sparks.forEach { p ->
        val tw = (wave(t, 10 + p.speed * 3, p.phase) + 1f) / 2f
        val s = (3f + p.size * 5f) * u * (0.6f + tw * 0.4f)
        translate(p.x * w, p.y * h) {
            drawPath(sparklePath(s), yellow.copy(alpha = 0.15f + tw * 0.35f))
        }
    }
}

// ---------------------------------------------------------------- Psycho

private fun DrawScope.drawPsychic(t: Float, u: Float) {
    val w = size.width
    val h = size.height
    val pink = Color(0xFFF0ABFC)
    val violet = Color(0xFFA78BFA)
    val magenta = Color(0xFFE879F9)

    // Sich ausbreitende Wellenringe an zwei Punkten
    listOf(Offset(w * 0.82f, h * 0.16f), Offset(w * 0.14f, h * 0.84f)).forEachIndexed { c, center ->
        for (i in 0 until 5) {
            val f = frac(i / 5f + t * 3 + c * 0.1f)
            val r = f * 150f * u
            drawCircle(
                (if (i % 2 == 0) magenta else violet).copy(alpha = (1f - f) * 0.35f),
                r,
                center,
                style = Stroke(1.6f * u),
            )
        }
        // Kreisende Lichtkugeln
        for (i in 0 until 5) {
            val angle = TWO_PI * (t * (2 + c) + i / 5f) * (if (c == 0) 1 else -1)
            val rx = 70f * u
            val ry = 40f * u
            val pos = Offset(center.x + cos(angle) * rx, center.y + sin(angle) * ry)
            drawCircle(pink.copy(alpha = 0.15f), 7f * u, pos)
            drawCircle(Color.White.copy(alpha = 0.55f), 2.2f * u, pos)
        }
    }

    // Langsam drehende Spirale in der Mitte oben
    translate(w * 0.5f, h * 0.3f) {
        rotate(t * 360f * 2, pivot = Offset.Zero) {
            val spiral = Path().apply {
                moveTo(0f, 0f)
                var a = 0f
                while (a < TWO_PI * 3.2f) {
                    val r = a * 9f * u
                    lineTo(cos(a) * r, sin(a) * r)
                    a += 0.12f
                }
            }
            drawPath(spiral, violet.copy(alpha = 0.10f), style = Stroke(2.2f * u, cap = StrokeCap.Round))
        }
    }

    // Funkelnde Sterne
    Orbs.forEach { p ->
        val tw = (wave(t, 6 + p.speed * 2, p.phase) + 1f) / 2f
        val s = (4f + p.size * 6f) * u
        val y = p.y * h + wave(t, 2, p.phase) * 8f * u
        translate(p.x * w, y) {
            rotate(p.rot, pivot = Offset.Zero) {
                drawPath(sparklePath(s), pink.copy(alpha = 0.12f + tw * 0.4f))
            }
        }
    }
}

// ---------------------------------------------------------------- Klassik

private fun DrawScope.drawSparkles(t: Float, u: Float, colors: List<Color>) {
    val w = size.width
    val h = size.height

    // Holo-Lichtstreifen, die langsam schräg durchziehen
    for (i in 0 until 3) {
        val x = (frac(t * (1 + i) + i * 0.33f) * 1.6f - 0.3f) * w
        drawLine(
            Brush.linearGradient(
                listOf(Color.Transparent, colors[i % colors.size].copy(alpha = 0.10f), Color.Transparent),
                start = Offset(x - 60f * u, 0f),
                end = Offset(x + 60f * u, 0f),
            ),
            start = Offset(x - h * 0.35f, h),
            end = Offset(x + h * 0.35f, 0f),
            strokeWidth = 90f * u,
        )
    }

    // Funkelnde Sterne in den Typfarben
    Sparks.forEachIndexed { i, p ->
        val tw = (wave(t, 8 + p.speed * 3, p.phase) + 1f) / 2f
        val s = (3f + p.size * 7f) * u * (0.5f + tw * 0.5f)
        val color = colors[i % colors.size]
        translate(p.x * w, p.y * h) {
            drawCircle(color.copy(alpha = 0.08f * tw), s * 2.2f, Offset.Zero)
            drawPath(sparklePath(s), Color.White.copy(alpha = 0.12f + tw * 0.45f))
        }
    }
}
