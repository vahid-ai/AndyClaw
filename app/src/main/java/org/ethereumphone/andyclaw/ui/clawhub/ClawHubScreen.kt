package org.ethereumphone.andyclaw.ui.clawhub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import com.example.dgenlibrary.DgenLoadingMatrix
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import org.ethereumphone.andyclaw.ui.components.GlowStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dgenlibrary.SystemColorManager
import com.example.dgenlibrary.button.DgenPrimaryButton
import com.example.dgenlibrary.button.DgenSecondaryButton
import org.ethereumphone.andyclaw.ui.DgenCursorSearchTextfield
import com.example.dgenlibrary.ui.theme.PitagonsSans
import com.example.dgenlibrary.ui.theme.SpaceMono
import com.example.dgenlibrary.ui.theme.dgenBlack
import com.example.dgenlibrary.ui.theme.dgenRed
import com.example.dgenlibrary.ui.theme.dgenWhite
import com.example.dgenlibrary.ui.theme.label_fontSize
import com.example.dgenlibrary.ui.theme.lazerBurn
import com.example.dgenlibrary.ui.theme.neonOpacity
import com.example.dgenlibrary.ui.theme.orcheAsh
import com.example.dgenlibrary.ui.theme.orcheCore
import com.example.dgenlibrary.ui.theme.terminalCore
import com.example.dgenlibrary.ui.theme.terminalHack
import org.ethereumphone.andyclaw.R
import org.ethereumphone.andyclaw.extensions.clawhub.ClawHubSearchResult
import org.ethereumphone.andyclaw.extensions.clawhub.ClawHubSkillSummary
import org.ethereumphone.andyclaw.extensions.clawhub.InstalledClawHubSkill
import org.ethereumphone.andyclaw.extensions.clawhub.SkillThreatAnalyzer
import org.ethereumphone.andyclaw.extensions.clawhub.ThreatAssessment
import org.ethereumphone.andyclaw.extensions.clawhub.ThreatIndicator
import org.ethereumphone.andyclaw.extensions.clawhub.ThreatLevel
import org.ethereumphone.andyclaw.ui.SearchBar
import org.ethereumphone.andyclaw.ui.components.AppTextStyles
import org.ethereumphone.andyclaw.ui.components.DgenBackNavigationBackground
import org.ethereumphone.andyclaw.ui.components.ClawHubSkillRow
import org.ethereumphone.andyclaw.ui.components.DgenSmallPrimaryButton
import org.ethereumphone.andyclaw.ui.components.InstalledSkillRow
import org.ethereumphone.andyclaw.ui.components.ThreatLevelBadge

@Composable
fun ClawHubScreen(
    onNavigateBack: () -> Unit,
    viewModel: ClawHubViewModel = viewModel(),
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val browseSkills by viewModel.browseSkills.collectAsState()
    val isBrowsing by viewModel.isBrowsing.collectAsState()
    val installedSkills by viewModel.installedSkills.collectAsState()
    val operatingSlug by viewModel.operatingSlug.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val pendingInstall by viewModel.pendingInstall.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { SystemColorManager.refresh(context) }
    val primaryColor = SystemColorManager.primaryColor
    val secondaryColor = SystemColorManager.secondaryColor

    val contentTitleStyle = AppTextStyles.contentTitle(primaryColor)
    val contentBodyStyle = AppTextStyles.contentBody(primaryColor)

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    pendingInstall?.let { pending ->
        ThreatConfirmationDialog(
            slug = pending.slug,
            assessment = pending.assessment,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            onConfirm = viewModel::confirmPendingInstall,
            onDismiss = viewModel::cancelPendingInstall,
        )
    }

    DgenBackNavigationBackground(
        title = "ClawHub",
        primaryColor = primaryColor,
        onNavigateBack = onNavigateBack,
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(top=8.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }) {
            val tabs = ClawHubTab.entries
            TabRow(
                selectedTabIndex = tabs.indexOf(selectedTab),
                containerColor = Color.Transparent,
                contentColor = primaryColor,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(selectedTab)]),
                        color = primaryColor,
                    )
                },
                divider = {
                    HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
                },
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = tab.name,
                                style = TextStyle(
                                    fontFamily = SpaceMono,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    letterSpacing = 1.sp,
                                    shadow = GlowStyle.subtitle(primaryColor),
                                ),
                                color = if (selectedTab == tab) primaryColor
                                    else primaryColor.copy(alpha = 0.5f),
                            )
                        },
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(modifier= Modifier.fillMaxSize(), targetState = selectedTab, label = "clawhub_tab_crossfade") { tab ->
                    when (tab) {
                        ClawHubTab.BROWSE -> {
                            BrowseTab(
                                searchQuery = searchQuery,
                                onSearchQueryChange = viewModel::onSearchQueryChange,
                                onSubmitSearch = viewModel::submitSearch,
                                isSearching = isSearching,
                                searchResults = searchResults,
                                browseSkills = browseSkills,
                                isBrowsing = isBrowsing,
                                onLoadMore = viewModel::loadMoreBrowse,
                                operatingSlug = operatingSlug,
                                isInstalled = viewModel::isSkillInstalled,
                                onInstall = viewModel::installSkill,
                                onUninstall = viewModel::uninstallSkill,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                contentTitleStyle = contentTitleStyle,
                                contentBodyStyle = contentBodyStyle,
                            )
                        }

                        ClawHubTab.INSTALLED -> {
                            InstalledTab(
                                skills = installedSkills,
                                operatingSlug = operatingSlug,
                                onUninstall = viewModel::uninstallSkill,
                                onUpdate = viewModel::updateSkill,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                contentTitleStyle = contentTitleStyle,
                                contentBodyStyle = contentBodyStyle,
                            )
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

// ── Browse tab ──────────────────────────────────────────────────────

@Composable
private fun BrowseTab(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    isSearching: Boolean,
    searchResults: List<ClawHubSearchResult>,
    browseSkills: List<ClawHubSkillSummary>,
    isBrowsing: Boolean,
    onLoadMore: () -> Unit,
    operatingSlug: String?,
    isInstalled: (String) -> Boolean,
    onInstall: (String) -> Unit,
    onUninstall: (String) -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    contentTitleStyle: TextStyle,
    contentBodyStyle: TextStyle,
) {
    val listState = rememberLazyListState()
    val showSearch = searchQuery.isNotBlank() || searchResults.isNotEmpty()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 3 && !isBrowsing
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !showSearch) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "search") {
            DgenCursorSearchTextfield(
                modifier = Modifier.padding(vertical = 4.dp),
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.search_icon),
                        contentDescription = "Search",
                        tint = dgenWhite,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(22.dp)
                            .drawBehind {
                                drawContext.canvas.nativeCanvas.apply {
                                    drawCircle(
                                        center.x,
                                        center.y,
                                        size.minDimension / 2 + 8.dp.toPx(),
                                        android.graphics.Paint().apply {
                                            color = android.graphics.Color.WHITE
                                            maskFilter = android.graphics.BlurMaskFilter(
                                                16.dp.toPx(),
                                                android.graphics.BlurMaskFilter.Blur.NORMAL
                                            )
                                            alpha = 40
                                        }
                                    )
                                }
                            },
                    )
                },
                placeholder = {
                    Text(
                        text = "Search skills on ClawHub…",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                            color = dgenWhite.copy(alpha = 0.35f),
                            shadow = GlowStyle.placeholder(dgenWhite),
                        ),
                    )
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = dgenWhite,
                    shadow = GlowStyle.body(dgenWhite),
                ),
                cursorColor = dgenWhite,
            )

            Spacer(Modifier.height(12.dp))
        }

        if (showSearch) {
            if (searchResults.isEmpty() && !isSearching) {
                item(key = "no-results") {
                    EmptyState(
                        title = "NO RESULTS",
                        subtitle = "Try a different search query",
                        primaryColor = primaryColor,
                        contentTitleStyle = contentTitleStyle,
                        contentBodyStyle = contentBodyStyle,
                    )
                }
            } else {
                items(
                    items = searchResults,
                    key = { it.slug ?: it.hashCode().toString() },
                ) { result ->
                    val slug = result.slug ?: return@items
                    ClawHubSkillRow(
                        name = result.displayName ?: result.slug ?: "Unknown",
                        slug = slug,
                        description = result.summary,
                        version = result.version,
                        threatLevel = remember(result.slug) {
                            SkillThreatAnalyzer.quickAssess(result.summary, result.displayName, result.moderation)
                        },
                        installed = isInstalled(slug),
                        isOperating = operatingSlug == slug,
                        onInstall = { onInstall(slug) },
                        onUninstall = { onUninstall(slug) },
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        contentTitleStyle = contentTitleStyle,
                        contentBodyStyle = contentBodyStyle,
                    )
                }
            }
        } else {
            if (browseSkills.isEmpty() && !isBrowsing) {
                item(key = "empty-browse") {
                    EmptyState(
                        title = "NO SKILLS AVAILABLE",
                        subtitle = "Check back later or try searching",
                        primaryColor = primaryColor,
                        contentTitleStyle = contentTitleStyle,
                        contentBodyStyle = contentBodyStyle,
                    )
                }
            } else {
                items(
                    items = browseSkills,
                    key = { it.slug },
                ) { skill ->
                    ClawHubSkillRow(
                        name = skill.displayName,
                        slug = skill.slug,
                        description = skill.summary,
                        version = skill.latestVersion?.version,
                        threatLevel = remember(skill.slug) {
                            SkillThreatAnalyzer.quickAssess(skill.summary, skill.displayName, skill.moderation)
                        },
                        installed = isInstalled(skill.slug),
                        isOperating = operatingSlug == skill.slug,
                        onInstall = { onInstall(skill.slug) },
                        onUninstall = { onUninstall(skill.slug) },
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        contentTitleStyle = contentTitleStyle,
                        contentBodyStyle = contentBodyStyle,
                    )
                }
            }

            if (isBrowsing) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        DgenLoadingMatrix(
                            size = 24.dp,
                            LEDSize = 6.dp,
                            activeLEDColor = primaryColor,
                            unactiveLEDColor = secondaryColor,
                        )
                    }
                }
            }
        }
    }
}

// ── Installed tab ───────────────────────────────────────────────────

@Composable
private fun InstalledTab(
    skills: List<InstalledClawHubSkill>,
    operatingSlug: String?,
    onUninstall: (String) -> Unit,
    onUpdate: (String) -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    contentTitleStyle: TextStyle,
    contentBodyStyle: TextStyle,
) {
    if (skills.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = "NO CLAWHUB SKILLS INSTALLED",
                subtitle = "Browse the registry and install skills to extend your agent",
                primaryColor = primaryColor,
                contentTitleStyle = contentTitleStyle,
                contentBodyStyle = contentBodyStyle,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = skills,
                key = { it.slug },
            ) { skill ->
                InstalledSkillRow(
                    skill = skill,
                    isOperating = operatingSlug == skill.slug,
                    onUninstall = { onUninstall(skill.slug) },
                    onUpdate = { onUpdate(skill.slug) },
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    contentTitleStyle = contentTitleStyle,
                    contentBodyStyle = contentBodyStyle,
                )
            }
        }
    }
}

// ── Search bar ──────────────────────────────────────────────────────

//@Composable
//private fun SearchBar(
//    query: String,
//    onQueryChange: (String) -> Unit,
//    onSubmit: () -> Unit,
//    isSearching: Boolean,
//    primaryColor: Color,
//) {
//    OutlinedTextField(
//        value = query,
//        onValueChange = onQueryChange,
//        modifier = Modifier.fillMaxWidth(),
//        placeholder = { Text("Search skills on ClawHub…", color = dgenWhite.copy(alpha = 0.5f)) },
//        leadingIcon = {
//            if (isSearching) {
//                CircularProgressIndicator(
//                    modifier = Modifier.size(20.dp),
//                    strokeWidth = 2.dp,
//                    color = primaryColor,
//                )
//            } else {
//                Icon(Icons.Default.Search, contentDescription = null, tint = primaryColor)
//            }
//        },
//        trailingIcon = {
//            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
//                IconButton(onClick = { onQueryChange("") }) {
//                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = dgenWhite)
//                }
//            }
//        },
//        singleLine = true,
//        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
//        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
//    )
//}

// ── Threat confirmation dialog ──────────────────────────────────────

@Composable
private fun ThreatConfirmationDialog(
    slug: String,
    assessment: ThreatAssessment,
    primaryColor: Color,
    secondaryColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current

    val threatColor = when (assessment.level) {
        ThreatLevel.LOW -> Color(0xFF4CAF50)
        ThreatLevel.MEDIUM -> Color(0xFFFFA000)
        ThreatLevel.HIGH -> Color(0xFFFF6D00)
        ThreatLevel.CRITICAL -> Color(0xFFD50000)
    }
    val threatsecondaryColor = when (assessment.level) {
        ThreatLevel.LOW -> terminalHack
        ThreatLevel.MEDIUM -> orcheAsh
        ThreatLevel.HIGH -> orcheAsh
        ThreatLevel.CRITICAL -> lazerBurn
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(dgenBlack)
            .pointerInput(Unit) {
                detectTapGestures {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismiss()
                }
            }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Security Warning",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontFamily = PitagonsSans,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = primaryColor,
                        lineHeight = 32.sp,
                        letterSpacing = 0.sp,
                        textDecoration = TextDecoration.None
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "\"$slug\"",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontFamily = PitagonsSans,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                        ),
                    )
                    ThreatLevelBadge(level = assessment.level, primaryColor = primaryColor)
                    Spacer(Modifier.weight(1f))
                }

                Text(
                    text = assessment.summary,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontFamily = PitagonsSans,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        color = primaryColor.copy(neonOpacity),
                        lineHeight = 24.sp,
                        letterSpacing = 0.sp,
                        textDecoration = TextDecoration.None
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (assessment.indicators.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (indicator in assessment.indicators) {
                            ThreatIndicatorRow(indicator, primaryColor)
                        }
                    }
                }

                Text(
                    text = "By installing this skill you accept the associated risks. " +
                        "Only proceed if you trust the skill author.",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontFamily = PitagonsSans,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        color = primaryColor.copy(alpha = 0.5f),
                        lineHeight = 20.sp,
                        textDecoration = TextDecoration.None
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                DgenPrimaryButton(
                    text = "ACCEPT & INSTALL",
                    backgroundColor = if (assessment.level >= ThreatLevel.HIGH) threatColor else primaryColor,
                    containerColor = if (assessment.level >= ThreatLevel.HIGH) threatsecondaryColor else secondaryColor,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm()
                    }
                )

                Spacer(modifier = Modifier.width(24.dp))

                DgenSecondaryButton(
                    text = "CANCEL",
                    containerColor = primaryColor,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ThreatIndicatorRow(indicator: ThreatIndicator, primaryColor: Color) {
    val dotColor = when (indicator.severity) {
        ThreatLevel.LOW -> Color(0xFF4CAF50)
        ThreatLevel.MEDIUM -> Color(0xFFFFA000)
        ThreatLevel.HIGH -> Color(0xFFFF6D00)
        ThreatLevel.CRITICAL -> Color(0xFFD50000)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(

            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        )
        {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor),
            )
            Text(
                text = indicator.category,
                style = TextStyle(
                    fontFamily = PitagonsSans,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor,
                ),
            )
        }
        Text(
            text = indicator.description,
            style = TextStyle(
                fontFamily = PitagonsSans,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = primaryColor.copy(alpha = neonOpacity),
            ),
        )
    }
}

// ── Previews ────────────────────────────────────────────────────────

@Preview(
    showBackground = true,
    backgroundColor = 0xFF050505,
    widthDp = 720,
    heightDp = 720,
)
@Composable
private fun ThreatConfirmationDialogPreview() {
    ThreatConfirmationDialog(
        slug = "ethos-swap-skill",
        assessment = ThreatAssessment(
            level = ThreatLevel.MEDIUM,
            summary = "This skill requests broad permissions including network access, " +
                "wallet signing, and access to contacts. Proceed with caution.",
            indicators = listOf(
                ThreatIndicator(
                    severity = ThreatLevel.CRITICAL,
                    category = "Wallet Access",
                    description = "Requests permission to sign transactions on your behalf.",
                ),
                ThreatIndicator(
                    severity = ThreatLevel.HIGH,
                    category = "Network Access",
                    description = "Can make outbound HTTP requests to arbitrary endpoints.",
                ),
                ThreatIndicator(
                    severity = ThreatLevel.MEDIUM,
                    category = "Contact Access",
                    description = "Reads your contact list to suggest recipients.",
                ),
            ),
        ),
        primaryColor = orcheCore,
        secondaryColor = orcheAsh,
        onConfirm = {},
        onDismiss = {},
    )
}

// ── Empty state ─────────────────────────────────────────────────────

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    primaryColor: Color,
    contentTitleStyle: TextStyle,
    contentBodyStyle: TextStyle,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = contentTitleStyle,
                color = primaryColor,
            )
            Text(
                text = subtitle,
                style = contentBodyStyle,
                color = dgenWhite.copy(alpha = 0.7f),
            )
        }

    }
}
