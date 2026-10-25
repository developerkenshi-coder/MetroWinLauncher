package com.metrowin.launcher.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.metrowin.launcher.R
import com.metrowin.launcher.data.PlaceMatch
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.launch

/** The city of the weather tile and its units. */
@Composable
fun LiveTilesPage(env: SettingsEnv) {
    val settings = env.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    // null: no search made yet. Empty: the search found nothing.
    var results by remember { mutableStateOf<List<PlaceMatch>?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun search() {
        if (query.isBlank()) return
        scope.launch {
            val found = env.searchPlaces(query)
            failed = found == null
            results = found
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) env.onUseLocation()
    }

    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingHeading(stringResource(R.string.weather_city))
            InfoSetting(
                label = stringResource(R.string.weather_city),
                value = settings.weatherCity.ifBlank { stringResource(R.string.weather_city_none) },
            )
            MetroTextField(
                value = query, onValueChange = { query = it },
                placeholder = stringResource(R.string.weather_search_hint),
                modifier = Modifier.testTag("weather:query"),
                onSearch = ::search,
            )
            ActionSetting(stringResource(R.string.weather_search), onClick = ::search, tag = "weather:search")
            when {
                failed -> SettingHint(stringResource(R.string.weather_search_failed), Modifier.testTag("weather:failed"))
                results?.isEmpty() == true -> SettingHint(stringResource(R.string.weather_no_results), Modifier.testTag("weather:none"))
            }
            results?.forEachIndexed { index, place ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .testTag("weather:result:$index")
                        .metroPress(onClick = {
                            env.onChoosePlace(place)
                            results = null
                            query = ""
                        })
                        .padding(vertical = 6.dp),
                ) {
                    MetroText(place.name, size = 22.sp)
                    val detail = listOf(place.region, place.country).filter { it.isNotBlank() }.joinToString(", ")
                    if (detail.isNotEmpty()) {
                        MetroText(detail, size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
                    }
                }
            }
            ActionSetting(
                stringResource(R.string.weather_use_location),
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) env.onUseLocation() else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                tag = "weather:location",
            )
        }
        ToggleSetting(
            label = stringResource(R.string.weather_fahrenheit),
            checked = settings.weatherFahrenheit,
            onChange = { value -> env.onChange { it.copy(weatherFahrenheit = value) } },
            hint = stringResource(R.string.weather_hint),
            tag = "weather:fahrenheit",
        )
    }
}
