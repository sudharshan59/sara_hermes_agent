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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        // Outer Ambient Glow
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(dynamicScale * 1.35f)
                .blur(28.dp)
        ) {
            rotate(rotation) {
                drawCircle(
                    brush = Brush.sweepGradient(gradientColors),
                    radius = size.toPx() / 2.3f
                )
            }
        }

        // Middle Fluid Light Layer
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(dynamicScale * 1.15f)
                .blur(14.dp)
        ) {
            rotate(-rotation * 1.3f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.9f),
                            SiriCyan.copy(alpha = 0.7f),
                            SiriPurple.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    ),
                    radius = size.toPx() / 2.5f
                )
            }
        }

        // Core Glowing Orb
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(dynamicScale)
        ) {
            rotate(rotation) {
                drawCircle(
                    brush = Brush.sweepGradient(gradientColors),
                    radius = size.toPx() / 3.0f
                )
            }
            // Bright center core reflection
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        SiriCyan.copy(alpha = 0.8f),
                        Color.Transparent
                    ),
                    center = Offset(size.toPx() / 2.1f, size.toPx() / 2.2f)
                ),
                radius = size.toPx() / 5.5f
            )
        }
    }
}
