package io.spasum.hailshizuku.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import io.spasum.hailshizuku.HailApp.Companion.app
import io.spasum.hailshizuku.R
import io.spasum.hailshizuku.app.HailData
import io.spasum.hailshizuku.ui.main.MainFragment
import io.spasum.hailshizuku.ui.theme.AppTheme
import io.spasum.hailshizuku.utils.HPackages
import io.spasum.hailshizuku.utils.HUI
import java.text.SimpleDateFormat

class AboutFragment : MainFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    AboutScreen(HPackages.getUnhiddenPackageInfoOrNull(app.packageName)!!.firstInstallTime)
                }
            }
        }

    @Preview(showBackground = true)
    @Composable
    fun PreviewAboutScreen() = AppTheme { AboutScreen(System.currentTimeMillis()) }

    @Composable
    private fun AboutScreen(installTime: Long) {
        var openLicenseDialog by remember { mutableStateOf(false) }
        if (openLicenseDialog) LicenseDialog { openLicenseDialog = false }
        Column(modifier = Modifier.verticalScroll(state = rememberScrollState())) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
            Card(
                modifier = Modifier.height(dimensionResource(R.dimen.header_height))
                    .padding(horizontal = dimensionResource(R.dimen.padding_medium))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(
                        dimensionResource(R.dimen.padding_extra_small),
                        Alignment.CenterVertically
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier,
                        contentScale = ContentScale.None
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.app_slogan),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
            OutlinedCard(modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium))) {
                ClickableItem(
                    icon = Icons.Outlined.Update,
                    title = R.string.label_version,
                    desc = HailData.VERSION
                ) { HUI.openLink(HailData.URL_RELEASES) }
                ClickableItem(
                    icon = Icons.Outlined.InstallMobile,
                    title = R.string.label_time,
                    desc = SimpleDateFormat.getDateInstance().format(installTime)
                ) { HUI.showToast("\uD83E\uDD76\uD83D\uDCA8\uD83D\uDC09") }
            }
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
            OutlinedCard(modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium))) {
                ClickableItem(
                    icon = Icons.Outlined.Code,
                    title = R.string.action_github
                ) { HUI.openLink(HailData.URL_GITHUB) }
                ClickableItem(
                    icon = Icons.Outlined.Description,
                    title = R.string.action_licenses
                ) { openLicenseDialog = true }
            }
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))
        }
    }

    @Composable
    private fun ClickableItem(
        icon: ImageVector, @StringRes title: Int, desc: String? = null, onClick: () -> Unit
    ) = Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(
                horizontal = dimensionResource(R.dimen.padding_medium),
                vertical = dimensionResource(if (desc == null) R.dimen.padding_medium else R.dimen.padding_large)
            )
        )
        Column {
            Text(text = stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (desc != null) Text(text = desc, style = MaterialTheme.typography.bodyMedium)
        }
    }

    @Composable
    private fun LicenseDialog(onDismiss: () -> Unit) = AlertDialog(
        title = { Text(text = stringResource(R.string.action_licenses)) },
        text = {
            SelectionContainer {
                Text(
                    text = buildAnnotatedString {
                        val lines = resources.openRawResource(R.raw.licenses).bufferedReader().readLines()
                        lines.forEach {
                            if (it.isNotBlank()) withLink(
                                LinkAnnotation.Url(
                                    it.substringAfter(": "),
                                    TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary))
                                )
                            ) { append(it.substringBefore(": ")) }
                            if (it != lines.last()) append("\n\n")
                        }
                    },
                    modifier = Modifier.verticalScroll(state = rememberScrollState())
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.ok)) }
        }
    )
}
