package com.example.stopscrolling.ui.newgroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.stopscrolling.R
import com.example.stopscrolling.util.WeeklyLimitError

/*
 * US-13: optional weekly limit of a group, in hours and minutes.
 * This composable has no logic and no state: the ViewModel keeps the values and the error.
 * The group edit screen (US-09) can reuse it as it is.
 */
@Composable
fun WeeklyLimitSection(
    enabled: Boolean,
    hoursText: String,
    minutesText: String,
    error: WeeklyLimitError?,
    onEnabledChange: (Boolean) -> Unit,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // The whole row is the switch, so that TalkBack reads the label with the switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onEnabledChange
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.weekly_time_limit),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = enabled, onCheckedChange = null)
        }

        if (enabled) {
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hoursText,
                    // Digits only, 3 digits at most (168 hours = 3 digits)
                    onValueChange = { onHoursChange(it.filter(Char::isDigit).take(3)) },
                    label = { Text(stringResource(R.string.weekly_hours)) },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { onMinutesChange(it.filter(Char::isDigit).take(3)) },
                    label = { Text(stringResource(R.string.weekly_minutes)) },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(4.dp))

            if (error != null) {
                Text(
                    text = stringResource(errorMessage(error)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    text = stringResource(R.string.weekly_limit_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun errorMessage(error: WeeklyLimitError): Int = when (error) {
    WeeklyLimitError.EMPTY -> R.string.weekly_limit_error_empty
    WeeklyLimitError.NOT_POSITIVE -> R.string.weekly_limit_error_not_positive
    WeeklyLimitError.TOO_LONG -> R.string.weekly_limit_error_too_long
    WeeklyLimitError.BELOW_DAILY -> R.string.weekly_limit_error_below_daily
}
