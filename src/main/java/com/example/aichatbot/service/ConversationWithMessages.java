package com.example.aichatbot.service;

import com.example.aichatbot.domain.Conversation;
import com.example.aichatbot.domain.Message;

import java.util.List;

public record ConversationWithMessages(Conversation conversation, List<Message> messages) {
}
