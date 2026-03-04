package org.ethereumphone.andyclaw.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dgenlibrary.SystemColorManager
import com.example.dgenlibrary.ui.theme.PitagonsSans
import com.example.dgenlibrary.ui.theme.SpaceMono
import com.example.dgenlibrary.ui.theme.dgenWhite
import com.example.dgenlibrary.ui.theme.label_fontSize
import org.ethereumphone.andyclaw.skills.AndyClawSkill
import org.ethereumphone.andyclaw.skills.Tier
import org.ethereumphone.andyclaw.skills.tier.OsCapabilities
import org.ethereumphone.andyclaw.ui.components.DgenSquareSwitch

@Composable
fun SkillManagementSection(
    skills: List<AndyClawSkill>,
    enabledSkills: Set<String>,
    onToggleSkill: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tier = OsCapabilities.currentTier()
    val primaryColor = SystemColorManager.primaryColor
    val titleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = label_fontSize,
        lineHeight = label_fontSize,
        letterSpacing = 1.sp,
        textDecoration = TextDecoration.None,
        textAlign = TextAlign.Left,
    )
    val sectionTitleStyle = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Left,
    )
    val bodyStyle = TextStyle(
        fontFamily = PitagonsSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Left,
    )

    Column(modifier = modifier) {
        Text(
            text = "REGISTERED SKILLS",
            style = titleStyle,
            color = primaryColor,
        )
        Spacer(Modifier.height(8.dp))
        for (skill in skills) {
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

@Composable
private fun SkillRow(
    skill: AndyClawSkill,
    tier: Tier,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    primaryColor: Color,
    titleColor: Color,
    sectionTitleStyle: TextStyle,
    bodyStyle: TextStyle,
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
            activeColor = primaryColor,
        )
    }
}
