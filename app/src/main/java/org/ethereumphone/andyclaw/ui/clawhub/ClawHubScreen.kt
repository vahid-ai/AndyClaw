package org.ethereumphone.andyclaw.ui.clawhub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dgenlibrary.SystemColorManager
import com.example.dgenlibrary.ui.theme.PitagonsSans
import com.example.dgenlibrary.ui.theme.SpaceMono
import com.example.dgenlibrary.ui.theme.dgenWhite
import org.ethereumphone.andyclaw.extensions.clawhub.ClawHubSearchResult
import org.ethereumphone.andyclaw.extensions.clawhub.ClawHubSkillSummary
import org.ethereumphone.andyclaw.extensions.clawhub.InstalledClawHubSkill
import org.ethereumphone.andyclaw.extensions.clawhub.SkillThreatAnalyzer
import org.ethereumphone.andyclaw.extensions.clawhub.ThreatAssessment
import org.ethereumphone.andyclaw.extensions.clawhub.ThreatIndicator
import org.ethereumphone.andyclaw.extensions.clawhub.ThreatLevel
import org.ethereumphone.andyclaw.ui.components.DgenBackNavigationBackground
import org.ethereumphone.andyclaw.ui.components.DgenSmallPrimaryButton

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
    LaunchedEffect(Unit) { SystemColorManager.refresh(context) }
    val primaryColor = SystemColorManager.primaryColor

    val contentTitleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Left,
    )
    val contentBodyStyle = TextStyle(
        fontFamily = PitagonsSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Left,
    )

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
            onConfirm = viewModel::confirmPendingInstall,
            onDismiss = viewModel::cancelPendingInstall,
        )
    }

    DgenBackNavigationBackground(
        title = "ClawHub",
        primaryColor = primaryColor,
        onNavigateBack = onNavigateBack,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                                ),
                                color = if (selectedTab == tab) primaryColor
                                    else dgenWhite.copy(alpha = 0.5f),
                            )
                        },
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(targetState = selectedTab, label = "clawhub_tab_crossfade") { tab ->
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
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onSubmit = onSubmitSearch,
                isSearching = isSearching,
                primaryColor = primaryColor,
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
                    SkillRow(
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
                    SkillRow(
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = primaryColor,
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
                    contentTitleStyle = contentTitleStyle,
                    contentBodyStyle = contentBodyStyle,
                )
            }
        }
    }
}

// ── Search bar ──────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isSearching: Boolean,
    primaryColor: Color,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search skills on ClawHub…", color = dgenWhite.copy(alpha = 0.5f)) },
        leadingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = primaryColor,
                )
            } else {
                Icon(Icons.Default.Search, contentDescription = null, tint = primaryColor)
            }
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = dgenWhite)
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
    )
}

// ── Skill rows ──────────────────────────────────────────────────────

@Composable
private fun SkillRow(
    name: String,
    slug: String?,
    description: String?,
    version: String?,
    threatLevel: ThreatLevel,
    installed: Boolean,
    isOperating: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    primaryColor: Color,
    contentTitleStyle: TextStyle,
    contentBodyStyle: TextStyle,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = name,
                        style = contentTitleStyle,
                        color = primaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    ThreatLevelBadge(level = threatLevel, primaryColor = primaryColor)
                }
                if (slug != null) {
                    Text(
                        text = buildString {
                            append(slug)
                            if (version != null) append(" · v$version")
                        },
                        style = contentBodyStyle.copy(fontSize = 13.sp),
                        color = dgenWhite.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            if (isOperating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = primaryColor,
                )
            } else if (installed) {
                DgenSmallPrimaryButton(
                    text = "Uninstall",
                    primaryColor = Color(0xFFFF6B6B),
                    onClick = onUninstall,
                )
            } else {
                DgenSmallPrimaryButton(
                    text = "Install",
                    primaryColor = primaryColor,
                    onClick = onInstall,
                )
            }
        }

        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = contentBodyStyle,
                color = dgenWhite,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
    }
}

@Composable
private fun InstalledSkillRow(
    skill: InstalledClawHubSkill,
    isOperating: Boolean,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
    primaryColor: Color,
    contentTitleStyle: TextStyle,
    contentBodyStyle: TextStyle,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = skill.displayName,
            style = contentTitleStyle,
            color = primaryColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildString {
                append(skill.slug)
                if (skill.version != null) append(" · v${skill.version}")
            },
            style = contentBodyStyle.copy(fontSize = 13.sp),
            color = dgenWhite.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isOperating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = primaryColor,
                )
            } else {
                DgenSmallPrimaryButton(
                    text = "Update",
                    primaryColor = primaryColor,
                    onClick = onUpdate,
                )
                Spacer(Modifier.width(8.dp))
                DgenSmallPrimaryButton(
                    text = "Uninstall",
                    primaryColor = Color(0xFFFF6B6B),
                    onClick = onUninstall,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
    }
}

// ── Threat level badge ──────────────────────────────────────────────

@Composable
private fun ThreatLevelBadge(level: ThreatLevel, primaryColor: Color) {
    val (textColor, bgColor) = when (level) {
        ThreatLevel.LOW -> Color(0xFF2E7D32) to Color(0xFF4CAF50).copy(alpha = 0.14f)
        ThreatLevel.MEDIUM -> Color(0xFFF57F17) to Color(0xFFFFA000).copy(alpha = 0.14f)
        ThreatLevel.HIGH -> Color(0xFFE65100) to Color(0xFFFF6D00).copy(alpha = 0.14f)
        ThreatLevel.CRITICAL -> Color(0xFFC62828) to Color(0xFFD50000).copy(alpha = 0.14f)
    }

    Text(
        text = level.displayName,
        style = TextStyle(
            fontFamily = SpaceMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        ),
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ── Threat confirmation dialog ──────────────────────────────────────

@Composable
private fun ThreatConfirmationDialog(
    slug: String,
    assessment: ThreatAssessment,
    primaryColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val iconTint = when (assessment.level) {
        ThreatLevel.LOW -> Color(0xFF4CAF50)
        ThreatLevel.MEDIUM -> Color(0xFFFFA000)
        ThreatLevel.HIGH -> Color(0xFFFF6D00)
        ThreatLevel.CRITICAL -> Color(0xFFD50000)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                "Security Warning",
                style = TextStyle(
                    fontFamily = SpaceMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "\"$slug\"",
                        style = TextStyle(
                            fontFamily = SpaceMono,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    ThreatLevelBadge(level = assessment.level, primaryColor = primaryColor)
                }

                Text(
                    text = assessment.summary,
                    style = TextStyle(
                        fontFamily = PitagonsSans,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                )

                if (assessment.indicators.isNotEmpty()) {
                    Text(
                        text = "DETECTED ISSUES",
                        style = TextStyle(
                            fontFamily = SpaceMono,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        ),
                    )

                    for (indicator in assessment.indicators) {
                        ThreatIndicatorRow(indicator)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "By installing this skill you accept the associated risks. " +
                        "Only proceed if you trust the skill author.",
                    style = TextStyle(
                        fontFamily = PitagonsSans,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "Accept & Install",
                    color = if (assessment.level >= ThreatLevel.HIGH)
                        Color(0xFFFF6B6B)
                    else
                        primaryColor,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ThreatIndicatorRow(indicator: ThreatIndicator) {
    val dotColor = when (indicator.severity) {
        ThreatLevel.LOW -> Color(0xFF4CAF50)
        ThreatLevel.MEDIUM -> Color(0xFFFFA000)
        ThreatLevel.HIGH -> Color(0xFFFF6D00)
        ThreatLevel.CRITICAL -> Color(0xFFD50000)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = indicator.category,
                style = TextStyle(
                    fontFamily = SpaceMono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = indicator.description,
                style = TextStyle(
                    fontFamily = PitagonsSans,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Empty state ─────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    primaryColor: Color,
    contentTitleStyle: TextStyle,
    contentBodyStyle: TextStyle,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = contentTitleStyle,
            color = primaryColor,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = contentBodyStyle,
            color = dgenWhite.copy(alpha = 0.7f),
        )
    }
}
