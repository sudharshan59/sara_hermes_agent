package com.sara.hermes.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sara.hermes.data.AssistantState
import kotlin.math.sin

@Composable
fun SiriOrb(
    state: AssistantState,
    audioRms: Float = 0f,
    size: Dp = 160.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "siri_orb_transition")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING -> 2000
                    AssistantState.THINKING -> 1200
                    AssistantState.SPEAKING -> 2500
                    AssistantState.IDLE -> 6000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING -> 500
                    AssistantState.THINKING -> 800
                    AssistantState.SPEAKING -> 600
                    AssistantState.IDLE -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    // Dynamic audio level scale during listening
    val dynamicScale = if (state == AssistantState.LISTENING) {
        pulseScale + (audioRms.coerceIn(0f, 10f) / 50f)
    } else {
        pulseScale
    }

    val gradientColors = listOf(
        SiriCyan.copy(alpha = 0.85f),
        SiriBlue.copy(alpha = 0.90f),
        SiriPurple.copy(alpha = 0.85f),
        SiriPink.copy(alpha = 0.90f),
        SiriCyan.copy(alpha = 0.85f)
    )

    // Container clipped to CircleShape to guarantee 100% roundness on all Android hardware
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    ) {
        // 1. Outer Circular Ambient Glow (Pure radial gradient - no square blur clipping)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .scale(dynamicScale)
        ) {
            val centerOffset = Offset(size.toPx() / 2f, size.toPx() / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SiriCyan.copy(alpha = 0.45f),
                        SiriPurple.copy(alpha = 0.35f),
                        SiriBlue.copy(alpha = 0.20f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = size.toPx() / 2f
                )
            )
        }

        // 2. Rotating 3D Fluid Light Disk
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .scale(dynamicScale * 0.88f)
        ) {
            rotate(rotation) {
                drawCircle(
                    brush = Brush.sweepGradient(gradientColors),
                    radius = size.toPx() / 2.3f
                )
            }
        }

        // 3. Spherical 3D Depth Layer (Offset radial lighting for 3D sphere volume)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .scale(dynamicScale * 0.86f)
        ) {
            val lightCenter = Offset(size.toPx() * 0.38f, size.toPx() * 0.35f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        SiriCyan.copy(alpha = 0.70f),
                        SiriPurple.copy(alpha = 0.50f),
                        Color(0xFF0A001A).copy(alpha = 0.80f)
                    ),
                    center = lightCenter,
                    radius = size.toPx() / 2.2f
                )
            )
        }

        // 4. Specular Gloss Reflection (Apple Siri glass reflection at top curve)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .scale(dynamicScale * 0.86f)
        ) {
            val highlightCenter = Offset(size.toPx() * 0.40f, size.toPx() * 0.30f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f),
                        Color.White.copy(alpha = 0.40f),
                        Color.Transparent
                    ),
                    center = highlightCenter,
                    radius = size.toPx() / 5.2f
                )
            )
        }
    }
}
