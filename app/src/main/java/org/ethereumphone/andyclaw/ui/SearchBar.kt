package org.ethereumphone.andyclaw.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Clear
import com.example.dgenlibrary.DgenLoadingMatrix
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dgenlibrary.ActionButton
import com.example.dgenlibrary.DgenBasicTextfield
import com.example.dgenlibrary.R
import org.ethereumphone.andyclaw.ui.components.CustomTextSelectionMenuContent
import org.ethereumphone.andyclaw.ui.components.CustomTextToolbar
import org.ethereumphone.andyclaw.ui.components.CustomTextToolbarState
import com.example.dgenlibrary.SystemColorManager.primaryColor
import com.example.dgenlibrary.ui.theme.PitagonsSans
import com.example.dgenlibrary.ui.theme.SpaceMono
import com.example.dgenlibrary.ui.theme.body1_fontSize
import com.example.dgenlibrary.ui.theme.dgenBlack
import com.example.dgenlibrary.ui.theme.dgenOcean
import com.example.dgenlibrary.ui.theme.dgenTurqoise
import com.example.dgenlibrary.ui.theme.dgenWhite
import com.example.dgenlibrary.ui.theme.label_fontSize
import com.example.dgenlibrary.ui.theme.pulseOpacity
import kotlinx.coroutines.delay
import org.ethereumphone.andyclaw.ui.components.GlowStyle


@Composable
fun DgenCursorSearchTextfield(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    keyboardtype: KeyboardType = KeyboardType.Text,
    placeholder: @Composable () -> Unit = {
        Text(
            text = "Search...",
            style = TextStyle(
                fontFamily = PitagonsSans,
                color = dgenTurqoise.copy(alpha = 0.45f),
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                shadow = GlowStyle.placeholder(dgenTurqoise),
            )
        )
    },
    leadingContent: @Composable (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.CenterStart,
    textStyle: TextStyle = TextStyle(
        fontFamily = PitagonsSans,
        color = dgenWhite,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        shadow = GlowStyle.body(dgenWhite),
    ),
    cursorColor: Color = dgenTurqoise,
    cursorWidth: Dp = 18.dp,
    cursorHeight: Dp = 32.dp,
    blinkDuration: Int = 500,
    maxFieldHeight: Dp = 150.dp,
    singleLine: Boolean = false,
    textfieldFocusManager: FocusManager? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(blinkDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorBlink"
    )
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var isFocused by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    LaunchedEffect(scrollState.maxValue) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val focusManager = textfieldFocusManager ?: LocalFocusManager.current

    val customTextSelectionColors = TextSelectionColors(
        handleColor = Color.Transparent,
        backgroundColor = cursorColor.copy(alpha = 0.2f)
    )

    val textToolbarState = remember { CustomTextToolbarState(primaryColor) }
    val customTextToolbar = remember(textToolbarState) { CustomTextToolbar(textToolbarState) }

    Row(
        modifier = modifier.heightIn(max = maxFieldHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingContent?.invoke()

        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            contentAlignment = contentAlignment
        ) {
            if (value.text.isBlank() && !isFocused) {
                placeholder()
            }

            CompositionLocalProvider(
                LocalTextSelectionColors provides customTextSelectionColors,
                LocalTextToolbar provides customTextToolbar
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            if (isFocused) {
                                textLayoutResult
                                    ?.takeIf { value.selection.collapsed }
                                    ?.let { tlr ->
                                        val rect = tlr.getCursorRect(value.selection.start)
                                        val y = ((rect.top + rect.bottom) / 2) - (cursorHeight.toPx() / 2)
                                        val left = rect.left.coerceIn(0f, size.width - cursorWidth.toPx())
                                        val cursorAlpha = if (isFocused) blinkAlpha else 0f

                                        drawContext.canvas.nativeCanvas.drawRect(
                                            left - 4.dp.toPx(),
                                            y - 4.dp.toPx(),
                                            left + cursorWidth.toPx() + 4.dp.toPx(),
                                            y + cursorHeight.toPx() + 4.dp.toPx(),
                                            android.graphics.Paint().apply {
                                                color = cursorColor.copy(alpha = cursorAlpha * 0.5f).toArgb()
                                                maskFilter = android.graphics.BlurMaskFilter(
                                                    12.dp.toPx(),
                                                    android.graphics.BlurMaskFilter.Blur.NORMAL
                                                )
                                            }
                                        )

                                        drawRect(
                                            color = cursorColor.copy(alpha = cursorAlpha),
                                            topLeft = Offset(left, y),
                                            size = Size(cursorWidth.toPx(), cursorHeight.toPx())
                                        )
                                    }
                            }
                        }
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                            onFocusChanged(focusState.isFocused)
                        },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                        keyboardType = keyboardtype
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { isFocused = true },
                        onDone = {
                            focusManager.clearFocus()
                            isFocused = false
                        }
                    ),
                    textStyle = textStyle,
                    visualTransformation = visualTransformation,
                    cursorBrush = SolidColor(Color.Unspecified),
                    onTextLayout = { textLayoutResult = it },
                    singleLine = singleLine
                )
            }
        }
    }

    CustomTextSelectionMenuContent(textToolbarState)
}

@Composable
fun DgenCursorSearchTextfield(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardtype: KeyboardType = KeyboardType.Text,
    placeholder: @Composable () -> Unit = {
        Text(
            text = "Search...",
            style = TextStyle(
                fontFamily = PitagonsSans,
                color = dgenTurqoise.copy(alpha = 0.45f),
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                shadow = GlowStyle.placeholder(dgenTurqoise),
            )
        )
    },
    leadingContent: @Composable (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.CenterStart,
    textStyle: TextStyle = TextStyle(
        fontFamily = PitagonsSans,
        color = dgenWhite,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        shadow = GlowStyle.body(dgenWhite),
    ),
    cursorColor: Color = dgenTurqoise,
    cursorWidth: Dp = 18.dp,
    cursorHeight: Dp = 32.dp,
    blinkDuration: Int = 500,
    maxFieldHeight: Dp = 150.dp,
    singleLine: Boolean = false,
    textfieldFocusManager: FocusManager? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    DgenCursorSearchTextfield(
        value = textFieldValue,
        onValueChange = { newTfv ->
            val oldText = textFieldValue.text
            textFieldValue = newTfv
            if (newTfv.text != oldText) {
                onValueChange(newTfv.text)
            }
        },
        modifier = modifier,
        keyboardtype = keyboardtype,
        placeholder = placeholder,
        leadingContent = leadingContent,
        contentAlignment = contentAlignment,
        textStyle = textStyle,
        cursorColor = cursorColor,
        cursorWidth = cursorWidth,
        cursorHeight = cursorHeight,
        blinkDuration = blinkDuration,
        maxFieldHeight = maxFieldHeight,
        singleLine = singleLine,
        textfieldFocusManager = textfieldFocusManager,
        onFocusChanged = onFocusChanged,
        visualTransformation = visualTransformation,
    )
}


@Composable
fun SearchBar(
    searchValue: String,
    onSearchValueChange: (String) -> Unit,
    focusedSearch: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    textColor: Color,
    backgroundColor: Color,
    componentBackgroundColor: Color = Color.Transparent,
    leadingIcon: @Composable (() -> Unit)? = null,
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var internalTfv by remember {
        mutableStateOf(TextFieldValue(text = searchValue, selection = TextRange(searchValue.length)))
    }
    val focusManager = LocalFocusManager.current

    // Synchronize internalTfv with searchValue from parent if it changes externally.
    LaunchedEffect(searchValue) {
        if (internalTfv.text != searchValue) {
            internalTfv = TextFieldValue(text = searchValue, selection = TextRange(searchValue.length))
        }
    }

    LaunchedEffect(Unit) {
        // Small delay can help to ensure focus happens after composition.
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = componentBackgroundColor,
                    size = size
                )
            }
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier
                .drawBehind {
                    drawRoundRect(
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        color = backgroundColor,
                        alpha = 0.7f
                    )
                }
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
        ) {
            leadingIcon?.invoke()

            DgenBasicTextfield(
                value = internalTfv,
                onValueChange = { newTextFieldValueState ->
                    val oldText = internalTfv.text
                    internalTfv = newTextFieldValueState

                    if (oldText != newTextFieldValueState.text) {
                        onSearchValueChange(newTextFieldValueState.text)
                    }
                },
                minLines = 1,
                maxLines = 1,
                cursorWidth = 14.dp,
                cursorHeight = 24.dp,
                cursorColor = primaryColor,
                textStyle = TextStyle(
                    textAlign = TextAlign.Start,
                    fontFamily = SpaceMono,
                    color = dgenWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = body1_fontSize,
                    lineHeight = body1_fontSize,
                    letterSpacing = 0.sp,
                    textDecoration = TextDecoration.None,
                    shadow = GlowStyle.body(dgenWhite),
                ),
                modifier = modifier
                    .padding(end = 14.dp)
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        onFocusChanged(focusState.isFocused)
                    },
                keyboardtype = KeyboardType.Text,
                textfieldFocusManager = focusManager,
                focusRequester = focusRequester,
                keyboardController = keyboardController,
                placeholder = {
                    Text(
                        modifier = modifier.fillMaxWidth(),
                        text = "Search".uppercase(),
                        style = TextStyle(
                            textAlign = TextAlign.Start,
                            fontFamily = SpaceMono,
                            color = textColor.copy(pulseOpacity),
                            fontSize = label_fontSize,
                            lineHeight = label_fontSize,
                            letterSpacing = 0.sp,
                            textDecoration = TextDecoration.None,
                            shadow = GlowStyle.placeholder(textColor),
                        )
                    )
                }
            )

            AnimatedVisibility(
                modifier = modifier.padding(start = 0.dp, end = 0.dp),
                visible = focusedSearch,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = LinearEasing
                    )
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = 250,
                        easing = LinearEasing
                    )
                )
            ) {
                ActionButton(
                    modifier = modifier
                        .size(24.dp)
                        .drawBehind {
                            drawCircle(
                                color = textColor
                            )
                        },
                    onClick = onClear,
                    icon = {
                        Icon(
                            contentDescription = "Clear",
                            imageVector = Icons.Rounded.Clear,
                            tint = backgroundColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isSearching: Boolean,
    primaryColor: Color,
    secondaryColor: Color = dgenOcean,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search skills on ClawHub…", color = dgenWhite.copy(alpha = 0.5f), style = TextStyle(shadow = GlowStyle.placeholder(dgenWhite))) },
        leadingIcon = {
            if (isSearching) {
                DgenLoadingMatrix(
                    size = 20.dp,
                    LEDSize = 5.dp,
                    activeLEDColor = primaryColor,
                    unactiveLEDColor = secondaryColor,
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