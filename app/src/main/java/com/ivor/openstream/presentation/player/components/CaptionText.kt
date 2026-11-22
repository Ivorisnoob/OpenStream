package com.ivor.openstream.presentation.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ivor.openstream.R
import kotlin.math.roundToInt
import com.ivor.openstream.data.settings.CaptionEdge
import com.ivor.openstream.data.settings.CaptionFont
import com.ivor.openstream.data.settings.CaptionPlacement
import com.ivor.openstream.data.settings.CaptionStyleSettings

/**
 * One caption drawn in the user's style: colour, font, weight and edge, on a black plate at the
 * chosen opacity. The player, its Caption style preview and the one in Settings all draw through
 * this, so what the previews show is what plays.
 */
@Composable
fun CaptionText(
    text: String,
    style: CaptionStyleSettings,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    plateShape: Shape = RoundedCornerShape(6.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
) {
    val textStyle = TextStyle(
        color = Color(style.textColor.argb),
        fontSize = fontSize,
        fontWeight = if (style.bold) FontWeight.SemiBold else FontWeight.Normal,
        fontFamily = when (style.font) {
            CaptionFont.DEFAULT -> FontFamily.Default
            CaptionFont.SERIF -> FontFamily.Serif
            CaptionFont.MONOSPACE -> FontFamily.Monospace
        },
        textAlign = TextAlign.Center,
        shadow = if (style.edge == CaptionEdge.SHADOW) Shadow(color = Color.Black, blurRadius = 4f) else null
    )
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = style.backgroundOpacity.coerceIn(0f, 1f)), plateShape)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        if (style.edge == CaptionEdge.OUTLINE) {
            // A black stroke under the fill: a stroke drawn on top would eat into the letters.
            val strokeWidth = with(LocalDensity.current) { fontSize.toPx() } * OUTLINE_WIDTH_RATIO
            Text(
                text = text,
                style = textStyle.copy(
                    color = Color.Black,
                    drawStyle = Stroke(width = strokeWidth, join = StrokeJoin.Round)
                )
            )
        }
        Text(text = text, style = textStyle)
    }
}

/**
 * The space between a caption and its edge: [base] moved by the chosen offset as a share of
 * [height]. Moving toward the edge stops short of it, so the result is never negative.
 */
fun captionEdgePadding(base: Dp, height: Dp, offset: Float): Dp {
    val share = offset.coerceIn(CaptionStyleSettings.MIN_VERTICAL_OFFSET, CaptionStyleSettings.MAX_VERTICAL_OFFSET)
    return (base + height * share).coerceAtLeast(MIN_CAPTION_BOTTOM)
}

private const val OUTLINE_WIDTH_RATIO = 0.16f
private val MIN_CAPTION_BOTTOM = 4.dp

/**
 * "Default", "10% higher" or "5% lower", for the position slider's value. The offset moves away
 * from the caption's edge, so at the top a positive one reads as lower.
 */
@Composable
fun captionPositionLabel(offset: Float, placement: CaptionPlacement): String {
    val percent = (offset * 100).roundToInt()
    val up = if (placement == CaptionPlacement.TOP) -percent else percent
    return when {
        up > 0 -> stringResource(R.string.sheet_caption_position_raised, up)
        up < 0 -> stringResource(R.string.sheet_caption_position_lowered, -up)
        else -> stringResource(R.string.sheet_default)
    }
}
