package com.wgo.navigator.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.theme.Wgo

enum class MassiveButtonVariant {
    Primary, Inverted, Secondary
}

@Composable
fun MassiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MassiveButtonVariant = MassiveButtonVariant.Primary,
    content: @Composable () -> Unit
) {
    val palette = Wgo.palette
    val backgroundColor = when (variant) {
        MassiveButtonVariant.Primary -> palette.primary
        MassiveButtonVariant.Inverted -> palette.ink
        MassiveButtonVariant.Secondary -> palette.surface
    }

    val contentColor = when (variant) {
        MassiveButtonVariant.Primary -> palette.onPrimary
        MassiveButtonVariant.Inverted -> palette.inkInverse
        MassiveButtonVariant.Secondary -> palette.ink
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = backgroundColor,
            contentColor = contentColor
        )
    ) {
        content()
    }
}
