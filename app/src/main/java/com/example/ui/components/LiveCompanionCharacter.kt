package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

    // Smoothly slide character upward out of the chat reading zone while user touches/scrolls,
    // and glide back down when touch/scroll ends.
    val targetSlideUp = if (isScreenInteracting) {
        -(maxContainerHeight * 0.48f).coerceIn(140.dp, 300.dp)
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
        targetValue = if (isScreenInteracting) 0.36f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "companion_touch_alpha"
    )
    val focusScale by animateFloatAsState(
        targetValue = if (isScreenInteracting) 0.80f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "companion_touch_scale"
    )

    val motionTransition = rememberInfiniteTransition(label = "companion_live_motion")
    val bobPeriod = when (motionState) {
        CompanionMotionState.SPEAKING -> 340
        CompanionMotionState.LISTENING -> 850
        CompanionMotionState.THINKING -> 1150
        CompanionMotionState.IDLE -> 2200
    }
    val bobAmplitude = when (motionState) {
        CompanionMotionState.SPEAKING -> 7f
        CompanionMotionState.LISTENING -> 4f
        CompanionMotionState.THINKING -> 5f
        CompanionMotionState.IDLE -> 4.5f
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
        CompanionMotionState.SPEAKING -> 2.8f
        CompanionMotionState.LISTENING -> 1.8f
        CompanionMotionState.THINKING -> 2.0f
        CompanionMotionState.IDLE -> 1.1f
    }
    val rotationAngle by motionTransition.animateFloat(
        initialValue = -swayDegrees,
        targetValue = swayDegrees,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = bobPeriod + 220, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "companion_sway"
    )

    val breathTarget = when (motionState) {
        CompanionMotionState.SPEAKING -> 1.055f
        CompanionMotionState.LISTENING -> 1.04f
        CompanionMotionState.THINKING -> 1.025f
        CompanionMotionState.IDLE -> 1.018f
    }
    val breathScale by motionTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = breathTarget,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = bobPeriod, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "companion_breath"
    )

    val glowColor = when (motionState) {
        CompanionMotionState.SPEAKING -> EmeraldSuccess
        CompanionMotionState.LISTENING -> MaterialTheme.colorScheme.primary
        CompanionMotionState.THINKING -> MaterialTheme.colorScheme.secondary
        CompanionMotionState.IDLE -> MaterialTheme.colorScheme.primary
    }

    val statusLabel = when (motionState) {
        CompanionMotionState.LISTENING -> "শুনছি..."
        CompanionMotionState.SPEAKING -> "বলছি..."
        CompanionMotionState.THINKING -> "ভাবছি..."
        CompanionMotionState.IDLE -> null
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
            visible = statusLabel != null && !isScreenInteracting,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            statusLabel?.let { label ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 4.dp,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = glowColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 104.dp, height = 132.dp)
                .graphicsLayer {
                    translationY = bobOffset.dp.toPx()
                    rotationZ = rotationAngle
                    scaleX = breathScale
                    scaleY = breathScale
                }
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .align(Alignment.BottomCenter)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(
                                    alpha = if (motionState == CompanionMotionState.IDLE) 0.22f else 0.42f
                                ),
                                Color.Transparent
                            )
                        )
                    )
            )
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = "$assistantName Live Companion",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
