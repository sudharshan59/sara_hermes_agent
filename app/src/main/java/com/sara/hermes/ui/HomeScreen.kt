package com.sara.hermes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sara.hermes.data.AssistantState
import com.sara.hermes.data.ChatMessage

@Composable
fun HomeScreen(
    state: AssistantState,
    audioRms: Float,
    currentTranscript: String,
    currentStatus: String,
    messages: List<ChatMessage>,
    onMicToggle: () -> Unit,
    onSendMessage: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (state) {
                                    AssistantState.LISTENING -> SiriCyan
                                    AssistantState.THINKING -> SiriPurple
                                    AssistantState.SPEAKING -> SiriPink
                                    AssistantState.IDLE -> SiriBlue
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Siri",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = when (state) {
                            AssistantState.LISTENING -> "Listening..."
                            AssistantState.THINKING -> "Thinking..."
                            AssistantState.SPEAKING -> "Speaking..."
                            AssistantState.IDLE -> if (currentStatus.isNotBlank()) currentStatus else "Ready"
                        },
                        color = when (state) {
                            AssistantState.LISTENING -> SiriCyan
                            AssistantState.THINKING -> SiriPurple
                            AssistantState.SPEAKING -> SiriPink
                            AssistantState.IDLE -> TextSecondary
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary
                    )
                }
            }

            // Center Area: Siri Orb + Live Transcript
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    SiriOrb(
                        state = state,
                        audioRms = audioRms,
                        size = if (messages.isEmpty()) 200.dp else 130.dp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(
                        visible = currentTranscript.isNotBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = currentTranscript,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(horizontal = 32.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceCard.copy(alpha = 0.7f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (messages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        ) {
                            items(messages) { msg ->
                                MessageBubble(msg)
                            }
                        }
                    }
                }
            }

            // Bottom Input Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Text Input Bar
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Ask Siri anything...", color = TextSecondary, fontSize = 14.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        trailingIcon = {
                            if (textInput.isNotBlank()) {
                                IconButton(onClick = {
                                    val msg = textInput.trim()
                                    if (msg.isNotEmpty()) {
                                        onSendMessage(msg)
                                        textInput = ""
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send",
                                        tint = SiriCyan
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Push to talk microphone button
                    val isMicActive = state == AssistantState.LISTENING
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMicActive) {
                                    Brush.linearGradient(listOf(SiriCyan, SiriPurple))
                                } else {
                                    Brush.linearGradient(listOf(SurfaceCard, SurfaceDark))
                                }
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(listOf(SiriCyan.copy(alpha = 0.6f), SiriPurple.copy(alpha = 0.6f))),
                                shape = CircleShape
                            )
                            .clickable { onMicToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMicActive) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Microphone",
                            tint = if (isMicActive) Color.Black else TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.isUser
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) {
                        Brush.linearGradient(listOf(SiriPurple.copy(alpha = 0.35f), SiriBlue.copy(alpha = 0.35f)))
                    } else {
                        Brush.linearGradient(listOf(SurfaceCard, SurfaceDark))
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) SiriPurple.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                color = TextPrimary,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}
