package com.hamferd.breading.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal val longDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
internal val mediumDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
internal val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

@Composable
internal fun ToolbarIcon(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = description)
    }
}
