package app.duinch.agentremote.core.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mark I / III Classic Circular Arc Reactor Emblem:
 * - 10 glowing cyan-white plasma ring segments
 * - 10 copper/brass electromagnetic coil brackets
 * - Concentric titanium bezel & counter-rotating inner turbine ring
 * - Radiant white-cyan palladium core with breathing glow
 */
@Composable
fun ArcReactorEmblem(
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "ArcReactorTransition")

    val glowPulse by if (animated) {
        transition.animateFloat(
            initialValue = 0.78f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "GlowPulse"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1.0f) }
    }

    val rotorAngle by if (animated) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "RotorAngle"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    }

    Canvas(modifier = modifier) {
        val minDim = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = minDim / 2f

        val cyanGlow = Color(0xFF00E5FF)
        val cyanCore = Color(0xFF80F8FF)
        val whiteHot = Color(0xFFF4FFFF)
        val copperDark = Color(0xFF9E5037)
        val copperMain = Color(0xFFD4785A)
        val copperBright = Color(0xFFF3A688)
        val titaniumDark = Color(0xFF171C24)
        val titaniumEdge = Color(0xFF334155)

        // 1. Ambient outer plasma aura (breathing)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    cyanGlow.copy(alpha = 0.32f * glowPulse),
                    cyanGlow.copy(alpha = 0.10f * glowPulse),
                    Color.Transparent
                ),
                center = center,
                radius = maxR
            ),
            radius = maxR,
            center = center
        )

        // 2. Outer Titanium Chassis Ring
        val bezelRadius = maxR * 0.84f
        drawCircle(
            color = titaniumDark,
            radius = bezelRadius,
            center = center
        )
        drawCircle(
            color = titaniumEdge.copy(alpha = 0.85f),
            radius = bezelRadius,
            center = center,
            style = Stroke(width = maxR * 0.035f)
        )

        // 3. Main 10-Segment Plasma Ring + Copper Induction Coils
        val ringRadius = maxR * 0.68f
        val ringThickness = maxR * 0.16f
        val arcTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
        val arcSize = Size(ringRadius * 2f, ringRadius * 2f)

        // Subtle continuous under-glow track
        drawCircle(
            color = cyanGlow.copy(alpha = 0.22f * glowPulse),
            radius = ringRadius,
            center = center,
            style = Stroke(width = ringThickness * 1.35f)
        )

        val segmentCount = 10
        val stepDeg = 360f / segmentCount // 36 deg per sector
        val plasmaSweep = 23f
        val gapSweep = stepDeg - plasmaSweep // 13 deg for copper coil bracket

        for (i in 0 until segmentCount) {
            val startAngle = -90f + i * stepDeg + (gapSweep / 2f)

            // Outer cyan bloom of the segment
            drawArc(
                color = cyanGlow.copy(alpha = 0.65f * glowPulse),
                startAngle = startAngle,
                sweepAngle = plasmaSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = ringThickness * 1.05f, cap = StrokeCap.Butt)
            )

            // Inner white-hot plasma core of the segment
            drawArc(
                color = whiteHot.copy(alpha = 0.92f * glowPulse),
                startAngle = startAngle + 1.2f,
                sweepAngle = plasmaSweep - 2.4f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = ringThickness * 0.58f, cap = StrokeCap.Butt)
            )
        }

        // 4. Inner dark separation ring between outer plasma ring and center core
        val innerChassisR = maxR * 0.52f
        drawCircle(
            color = Color(0xFF11161D),
            radius = innerChassisR,
            center = center,
            style = Stroke(width = maxR * 0.055f)
        )
        drawCircle(
            color = titaniumEdge.copy(alpha = 0.9f),
            radius = innerChassisR - maxR * 0.025f,
            center = center,
            style = Stroke(width = maxR * 0.02f)
        )

        // 5. 10 Copper-Gold Electromagnetic Coil Brackets straddling the ring
        val coilInnerR = ringRadius - ringThickness * 0.68f
        val coilOuterR = ringRadius + ringThickness * 0.68f
        for (i in 0 until segmentCount) {
            val angleDeg = -90f + i * stepDeg
            val rad = angleDeg * (PI / 180.0).toFloat()
            val cosA = cos(rad)
            val sinA = sin(rad)

            // Dark metallic bracket shadow/base
            drawLine(
                color = copperDark,
                start = Offset(center.x + coilInnerR * cosA, center.y + coilInnerR * sinA),
                end = Offset(center.x + coilOuterR * cosA, center.y + coilOuterR * sinA),
                strokeWidth = maxR * 0.095f,
                cap = StrokeCap.Round
            )
            // Warm copper coil body
            drawLine(
                color = copperMain,
                start = Offset(center.x + (coilInnerR + maxR * 0.015f) * cosA, center.y + (coilInnerR + maxR * 0.015f) * sinA),
                end = Offset(center.x + (coilOuterR - maxR * 0.015f) * cosA, center.y + (coilOuterR - maxR * 0.015f) * sinA),
                strokeWidth = maxR * 0.062f,
                cap = StrokeCap.Round
            )
            // Specular copper-gold wire highlight
            drawLine(
                color = copperBright,
                start = Offset(center.x + (coilInnerR + maxR * 0.03f) * cosA, center.y + (coilInnerR + maxR * 0.03f) * sinA),
                end = Offset(center.x + (coilOuterR - maxR * 0.03f) * cosA, center.y + (coilOuterR - maxR * 0.03f) * sinA),
                strokeWidth = maxR * 0.024f,
                cap = StrokeCap.Round
            )
        }

        // 6. Slowly rotating inner turbine ring & radial struts
        rotate(degrees = rotorAngle, pivot = center) {
            val turbineR = maxR * 0.39f
            val turbineTopLeft = Offset(center.x - turbineR, center.y - turbineR)
            val turbineSize = Size(turbineR * 2f, turbineR * 2f)

            for (i in 0 until 6) {
                val a = i * 60f
                drawArc(
                    color = cyanCore.copy(alpha = 0.75f * glowPulse),
                    startAngle = a + 6f,
                    sweepAngle = 36f,
                    useCenter = false,
                    topLeft = turbineTopLeft,
                    size = turbineSize,
                    style = Stroke(width = maxR * 0.035f, cap = StrokeCap.Round)
                )

                val rad = a * (PI / 180.0).toFloat()
                val strutInner = maxR * 0.24f
                val strutOuter = maxR * 0.48f
                drawLine(
                    color = titaniumEdge,
                    start = Offset(center.x + strutInner * cos(rad), center.y + strutInner * sin(rad)),
                    end = Offset(center.x + strutOuter * cos(rad), center.y + strutOuter * sin(rad)),
                    strokeWidth = maxR * 0.025f,
                    cap = StrokeCap.Round
                )
            }
        }

        // 7. Central Palladium Plasma Core
        val coreRadius = maxR * 0.25f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    whiteHot,
                    cyanCore.copy(alpha = 0.95f * glowPulse),
                    cyanGlow.copy(alpha = 0.35f * glowPulse),
                    Color.Transparent
                ),
                center = center,
                radius = coreRadius * 1.45f
            ),
            radius = coreRadius * 1.45f,
            center = center
        )

        drawCircle(
            color = whiteHot,
            radius = coreRadius * 0.72f,
            center = center
        )

        // Inner precision core ring (Stark signature detail)
        drawCircle(
            color = cyanGlow.copy(alpha = 0.85f),
            radius = coreRadius * 0.48f,
            center = center,
            style = Stroke(width = maxR * 0.022f)
        )
    }
}
