package org.ethereumphone.andyclaw.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ethereumphone.andyclaw.ui.theme.PitagonsSans
import org.ethereumphone.andyclaw.ui.theme.SpaceMono
import org.ethereumphone.andyclaw.ui.theme.label_fontSize
import org.ethereumphone.andyclaw.skills.AndyClawSkill
import org.ethereumphone.andyclaw.skills.Tier
import org.ethereumphone.andyclaw.skills.tier.OsCapabilities
import org.ethereumphone.andyclaw.ui.components.DgenSmallPrimaryButton
import org.ethereumphone.andyclaw.ui.components.GlowStyle
import org.ethereumphone.andyclaw.ui.components.SkillRow

@Composable
fun SkillManagementSection(
    skills: List<AndyClawSkill>,
    enabledSkills: Set<String>,
    onToggleSkill: (String, Boolean) -> Unit,
    onSkillClick: (AndyClawSkill) -> Unit,
    onUploadSkill: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tier = OsCapabilities.currentTier()
    val primaryColor = MaterialTheme.colorScheme.primary
    val titleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = label_fontSize,
        lineHeight = label_fontSize,
        letterSpacing = 1.sp,
        textDecoration = TextDecoration.None,
        textAlign = TextAlign.Left,
        shadow = GlowStyle.subtitle(primaryColor),
    )
    val sectionTitleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Left,
        shadow = GlowStyle.subtitle(primaryColor),
    )
    val bodyStyle = TextStyle(
        fontFamily = PitagonsSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Left,
        shadow = GlowStyle.body(primaryColor),
    )

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "REGISTERED SKILLS",
                style = titleStyle,
                color = primaryColor,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            DgenSmallPrimaryButton(
                text = "Upload",
                primaryColor = primaryColor,
                onClick = onUploadSkill,
            )
        }
        Spacer(Modifier.height(8.dp))
        for (skill in skills) {
            Box(modifier = Modifier.clickable { onSkillClick(skill) }) {
                SkillRow(
                    skill = skill,
                    tier = tier,
                    enabled = skill.id in enabledSkills,
                    onToggle = { onToggleSkill(skill.id, it) },
                    primaryColor = primaryColor,
                    titleColor = primaryColor,
                    sectionTitleStyle = sectionTitleStyle,
                    bodyStyle = bodyStyle,
                )
            }
        }
    }
}
