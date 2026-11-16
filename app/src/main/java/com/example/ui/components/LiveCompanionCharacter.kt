package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.CompanionCharacterCatalog
import com.example.ui.theme.EmeraldSuccess
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class CompanionMotionState {
    IDLE,
    THINKING,
    LISTENING,
    SPEAKING
}

@Composable
fun LiveCompanionCharacter(
    selectedSlot: Int,
    motionState: CompanionMotionState,
    isScreenInteracting: Boolean,
    maxContainerHeight: Dp,
    assistantName: String,
    onTapCompanion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawableRes = remember(selectedSlot, context.packageName) {
        CompanionCharacterCatalog.resolveDrawableId(context, selectedSlot)
    }
    if (drawableRes == 0) return

    // Prominent in-app companion dimensions scaled cleanly to viewport height
    val characterHeight = (maxContainerHeight * 0.30f).coerceIn(174.dp, 224.dp)
    val characterWidth = characterHeight * 0.78f

    // Smoothly slide character upward out of chat reading area on touch/scroll,
    // and glide gently back down once the 3-4s idle timer expires.
    val targetSlideUp = if (isScreenInteracting) {
        -(maxContainerHeight * 0.46f).coerceIn(155.dp, 315.dp)
    } else {
        0.dp
    }
    val slideOffsetY by animateDpAsState(
        targetValue = targetSlideUp,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "companion_touch_slide_y"
    )
    val focusAlpha by animateFloatAsState(
        targetValue = if (isScreenInteracting) 0.42f else 1f,
        animationSpec = tween(durationMillis = 240),
        label = "companion_touch_alpha"
    )
    val focusScale by animateFloatAsState(
        targetValue = if (isScreenInteracting) 0.80f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "companion_touch_scale"
    )

    val motionTransition = rememberInfiniteTransition(label = "companion_live_motion")
    val bobPeriod = when (motionState) {
        CompanionMotionState.SPEAKING -> 310
        CompanionMotionState.LISTENING -> 780
        CompanionMotionState.THINKING -> 1080
        CompanionMotionState.IDLE -> 2100
    }
    val bobAmplitude = when (motionState) {
        CompanionMotionState.SPEAKING -> 7.5f
        CompanionMotionState.LISTENING -> 5f
        CompanionMotionState.THINKING -> 5.5f
        CompanionMotionState.IDLE -> 5f
    }
    val bobOffset by motionTransition.animateFloat(
        initialValue = -bobAmplitude,
        targetValue = bobAmplitude,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = bobPeriod, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "companion_bob"
    )

    val swayDegrees = when (motionState) {
        CompanionMotionState.SPEAKING -> 3.6f
        CompanionMotionState.LISTENING -> 2.1f
        CompanionMotionState.THINKING -> 2.2f
        CompanionMotionState.IDLE -> 1.4f
    }
    val rotationAngle by motionTransition.animateFloat(
        initialValue = -swayDegrees,
        targetValue = swayDegrees,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = bobPeriod + 240, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "companion_sway"
    )

    val breathTarget = when (motionState) {
        CompanionMotionState.SPEAKING -> 1.06f
        CompanionMotionState.LISTENING -> 1.045f
        CompanionMotionState.THINKING -> 1.03f
        CompanionMotionState.IDLE -> 1.022f
    }
    val breathScale by motionTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = breathTarget,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = bobPeriod, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "companion_breath"
    )

    val sparklePhase by motionTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "companion_sparkle_phase"
    )

    val auraPulse by motionTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (motionState == CompanionMotionState.SPEAKING) 460 else 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "companion_aura_pulse"
    )

    val glowColor = when (motionState) {
        CompanionMotionState.SPEAKING -> EmeraldSuccess
        CompanionMotionState.LISTENING -> MaterialTheme.colorScheme.primary
        CompanionMotionState.THINKING -> MaterialTheme.colorScheme.secondary
        CompanionMotionState.IDLE -> MaterialTheme.colorScheme.primary
    }
    val secondaryGlow = MaterialTheme.colorScheme.secondary

    val statusLabel = when (motionState) {
        CompanionMotionState.LISTENING -> "শুনছি..."
        CompanionMotionState.SPEAKING -> "বলছি..."
        CompanionMotionState.THINKING -> "ভাবছি..."
        CompanionMotionState.IDLE -> "লাইভ কথা"
    }

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .offset(y = slideOffsetY)
            .alpha(focusAlpha)
            .scale(focusScale)
            .then(
                if (!isScreenInteracting) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onTapCompanion
                    )
                } else {
                    Modifier
                }
            )
            .testTag("live_companion_character")
    ) {
        AnimatedVisibility(
            visible = !isScreenInteracting,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 4.dp,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = glowColor,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = characterWidth, height = characterHeight)
                .graphicsLayer {
                    translationY = bobOffset.dp.toPx()
                    rotationZ = rotationAngle
                    scaleX = breathScale
                    scaleY = breathScale
                }
        ) {
            // Ambient glow + gentle orbiting sparkles around the active companion
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width * 0.5f, size.height * 0.58f)
                val baseRadius = size.minDimension * 0.46f * auraPulse
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(
                                alpha = when (motionState) {
                                    CompanionMotionState.SPEAKING -> 0.42f
                                    CompanionMotionState.LISTENING -> 0.36f
                                    else -> 0.24f
                                }
                            ),
                            secondaryGlow.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius.coerceAtLeast(1f)
                    ),
                    radius = baseRadius,
                    center = center
                )

                val sparkleCount = 5
                for (i in 0 until sparkleCount) {
                    val angle = sparklePhase + i * (2f * PI.toFloat() / sparkleCount)
                    val rx = size.width * 0.40f
                    val ry = size.height * 0.36f
                    val sx = center.x + cos(angle) * rx
                    val sy = (size.height * 0.45f) + sin(angle) * ry
                    val twinkle = (0.35f + 0.65f * ((sin(angle * 2f) + 1f) * 0.5f))
                    val dotColor = if (i % 2 == 0) glowColor else secondaryGlow
                    drawCircle(
                        color = dotColor.copy(alpha = twinkle * 0.78f),
                        radius = 3.2.dp.toPx() * twinkle,
                        center = Offset(sx, sy)
                    )
                }
            }

            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = "$assistantName Live Companion",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
