package org.ethereumphone.andyclaw.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dgenlibrary.DgenLoadingMatrix
import org.ethereumphone.andyclaw.ui.theme.SpaceMono
import org.ethereumphone.andyclaw.ui.theme.dgenWhite
import org.ethereumphone.andyclaw.ui.theme.lazerBurn
import org.ethereumphone.andyclaw.ui.theme.lazerCore
import org.ethereumphone.andyclaw.ui.theme.terminalCore
import org.ethereumphone.andyclaw.ui.theme.terminalHack
import org.ethereumphone.andyclaw.extensions.clawhub.InstalledClawHubSkill
import org.ethereumphone.andyclaw.extensions.clawhub.ThreatLevel
import org.ethereumphone.andyclaw.skills.AndyClawSkill
import org.ethereumphone.andyclaw.skills.Tier

@Composable
fun SkillRow(
    skill: AndyClawSkill,
    tier: Tier,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    primaryColor: Color,
    titleColor: Color,
    sectionTitleStyle: TextStyle,
    bodyStyle: TextStyle,
    disabledByYolo: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skill.name.uppercase(),
                style = sectionTitleStyle,
                color = titleColor,
            )
            val baseToolCount = skill.baseManifest.tools.size
            val privToolCount = skill.privilegedManifest?.tools?.size ?: 0
            val toolText = buildString {
                append("$baseToolCount base tool${if (baseToolCount != 1) "s" else ""}")
                if (privToolCount > 0) {
                    append(", $privToolCount privileged")
                    if (tier != Tier.PRIVILEGED) append(" (locked)")
                }
            }
            Text(
                text = toolText,
                style = bodyStyle,
                color = dgenWhite,
            )
        }
        Spacer(Modifier.width(20.dp))
        DgenSquareSwitch(
            checked = enabled,
            onCheckedChange = onToggle,
            enabled = !disabledByYolo,
            activeColor = primaryColor,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClawHubSkillRow(
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
    secondaryColor: Color,
    contentTitleStyle: TextStyle,
    contentBodyStyle: TextStyle,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { expanded = !expanded },
            )
            .animateContentSize()
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
                DgenLoadingMatrix(
                    size = 20.dp,
                    LEDSize = 5.dp,
                    activeLEDColor = primaryColor,
                    unactiveLEDColor = secondaryColor,
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
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
    }
}

@Composable
fun InstalledSkillRow(
    skill: InstalledClawHubSkill,
    isOperating: Boolean,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    contentTitleStyle: TextStyle,
    contentBodyStyle: TextStyle,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        )
        {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = skill.displayName,
                    style = contentTitleStyle,
                    color = primaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(skill.slug)
                        if (skill.version != null) append(" · v${skill.version}")
                    },
                    style = contentBodyStyle.copy(fontSize = 13.sp),
                    color = dgenWhite.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            if (isOperating) {
                DgenLoadingMatrix(
                    size = 20.dp,
                    LEDSize = 5.dp,
                    activeLEDColor = primaryColor,
                    unactiveLEDColor = secondaryColor,
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
        HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))
    }
}

@Composable
fun ThreatLevelBadge(level: ThreatLevel, primaryColor: Color) {
    val (textColor, bgColor) = when (level) {
        ThreatLevel.LOW -> terminalCore to terminalHack
        ThreatLevel.MEDIUM -> Color(0xFFF57F17) to Color(0xFFFFA000).copy(alpha = 0.14f)
        ThreatLevel.HIGH -> Color(0xFFE65100) to Color(0xFFFF6D00).copy(alpha = 0.14f)
        ThreatLevel.CRITICAL -> lazerCore to lazerBurn
    }

    Text(
        text = level.displayName.uppercase(),
        style = TextStyle(
            fontFamily = SpaceMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            shadow = GlowStyle.subtitle(textColor),
        ),
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
