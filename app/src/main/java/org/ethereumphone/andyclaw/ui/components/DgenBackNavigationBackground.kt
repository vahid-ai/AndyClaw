package org.ethereumphone.andyclaw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier.Companion
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.dgenlibrary.SystemColorManager.primaryColor
import com.example.dgenlibrary.ui.backgrounds.DgenNavigationBackground
import com.example.dgenlibrary.ui.headers.NavigationHeaderBar
import com.example.dgenlibrary.ui.theme.dgenBlack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DgenBackNavigationBackground(
    title: String,
    primaryColor: Color,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    headerContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    ChadBackground(modifier = modifier) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { focusManager.clearFocus() }
                    )
                },
        ) {
            if (headerContent != null) {
                NavigationHeaderBar(
                    content = headerContent,
                    onClick = onNavigateBack,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    primaryColor = primaryColor
                )
            } else {
                NavigationHeaderBar(
                    text = title,
                    onClick = onNavigateBack,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    primaryColor = primaryColor
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
        }
    }
}
