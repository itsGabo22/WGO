package com.wgo.navigator.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.theme.WgoBlack
import com.wgo.navigator.ui.theme.WgoPrimary
import com.wgo.navigator.ui.theme.WgoWhite

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
    val backgroundColor = when (variant) {
        MassiveButtonVariant.Primary -> WgoPrimary
        MassiveButtonVariant.Inverted -> WgoBlack
        MassiveButtonVariant.Secondary -> Color(0xFFF2F4F7)
    }

    val contentColor = when (variant) {
        MassiveButtonVariant.Primary -> WgoWhite
        MassiveButtonVariant.Inverted -> WgoWhite
        MassiveButtonVariant.Secondary -> WgoBlack
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
