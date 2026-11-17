package dev.valhalla.sinhalax.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp),
    )
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary),
        )
    }
}

/** Slider that only writes the value when the finger lifts. */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(format(local), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
fun InfoCard(icon: ImageVector, title: String, body: String) {
    SettingsCard {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
