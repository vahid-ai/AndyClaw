package org.ethereumphone.andyclaw.ui.chat

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dgenlibrary.components.ChatListInfo
import org.ethereumphone.andyclaw.ui.theme.PitagonsSans
import org.ethereumphone.andyclaw.ui.theme.SpaceMono
import org.ethereumphone.andyclaw.ui.theme.dgenRed
import org.ethereumphone.andyclaw.ui.theme.dgenWhite
import org.ethereumphone.andyclaw.ui.theme.pulseOpacity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ethereumphone.andyclaw.NodeApp
import org.ethereumphone.andyclaw.sessions.model.Session
import org.ethereumphone.andyclaw.ui.clawhub.EmptyState
import org.ethereumphone.andyclaw.ui.components.AppTextStyles
import org.ethereumphone.andyclaw.ui.components.DgenBackNavigationBackground
import org.ethereumphone.andyclaw.ui.components.GlowStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionListViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = (application as NodeApp).sessionManager

    val sessions = sessionManager.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            sessionManager.deleteSession(sessionId)
        }
    }
}

@Composable
fun SessionListScreen(
    onNavigateToChat: (sessionId: String?) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SessionListViewModel = viewModel(),
) {
    val sessions by viewModel.sessions.collectAsState()
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val contentTitleStyle = AppTextStyles.contentTitle(primaryColor)
    val contentBodyStyle = AppTextStyles.contentBody(primaryColor)

    DgenBackNavigationBackground(
        title = "Chat Sessions",
        primaryColor = primaryColor,
        onNavigateBack = onNavigateBack,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = "NO CHATS FOUND",
                        subtitle = "Start a chat session",
                        primaryColor = primaryColor,
                        contentTitleStyle = contentTitleStyle,
                        contentBodyStyle = contentBodyStyle,
                    )
                    
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionItem(
                            session = session,
                            dateFormat = dateFormat,
                            onClick = { onNavigateToChat(session.id) },
                            onDelete = { viewModel.deleteSession(session.id) },
                            primaryColor = primaryColor
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { onNavigateToChat(null) },
                shape = RoundedCornerShape(0.dp),
                containerColor = primaryColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .drawBehind {
                        drawContext.canvas.nativeCanvas.drawRect(
                            0f, 0f, size.width, size.height,
                            android.graphics.Paint().apply {
                                color = primaryColor.copy(alpha = 0.6f).toArgb()
                                maskFilter = android.graphics.BlurMaskFilter(
                                    16.dp.toPx(),
                                    android.graphics.BlurMaskFilter.Blur.NORMAL,
                                )
                            },
                        )
                    },
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat", tint = secondaryColor)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(
    session: Session,
    dateFormat: SimpleDateFormat,
    primaryColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (showDelete) showDelete = false
                    else onClick()
                },
                onLongClick = { showDelete = !showDelete }
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = session.title,
                style = TextStyle(
                    fontFamily = SpaceMono,
                    color = primaryColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 0.sp,
                    textDecoration = TextDecoration.None,
                    shadow = GlowStyle.title(primaryColor),
                ),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 80.dp, max = 400.dp)
            )
            Text(
                text = dateFormat.format(Date(session.updatedAt)),
                style = TextStyle(
                    fontFamily = PitagonsSans,
                    color = dgenWhite.copy(pulseOpacity),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 0.sp,
                    textDecoration = TextDecoration.None,
                    shadow = GlowStyle.subtitle(dgenWhite),
                ),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                modifier = Modifier.widthIn(min = 80.dp, max = 180.dp)
            )
        }
        AnimatedVisibility(
            visible = showDelete,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = dgenRed,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}
