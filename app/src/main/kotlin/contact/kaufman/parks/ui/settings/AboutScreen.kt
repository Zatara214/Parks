package contact.kaufman.parks.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import androidx.compose.material3.MaterialTheme
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantColors
import com.mikepenz.aboutlibraries.ui.compose.style.ContrastLevel
import com.mikepenz.aboutlibraries.ui.compose.style.LicenseHueResolver
import androidx.compose.runtime.getValue
import contact.kaufman.parks.R

/**
 * The dependency licence list, generated at build time by the AboutLibraries plugin.
 * Kept honest rather than hand-maintained: GPLv3 obliges us to say what we ship.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val libraries by produceLibraries(R.raw.aboutlibraries)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Open-source licences") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LibrariesContainer(
            libraries,
            modifier = Modifier.fillMaxSize().padding(padding),
            // v15 tints each licence badge by a hue derived from the licence name.
            // Under a dynamic palette that lands as pale-mint text on a pale-mint chip
            // in light theme, and neither `licenseChipColors` nor ContrastLevel.High
            // fixes it — the hue resolver wins. Switching the tinting off entirely
            // falls back to the theme's own badge colours, which are contrast-checked.
            variantColors = LibraryDefaults.m3VariantColors(
                contrastLevel = ContrastLevel.High,
                licenseHueResolver = LicenseHueResolver.None,
                licenseBadgeContainer = MaterialTheme.colorScheme.secondaryContainer,
                licenseBadgeContent = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )
    }
}
