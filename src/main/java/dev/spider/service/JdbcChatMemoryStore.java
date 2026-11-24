package dev.spider.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcChatMemoryStore implements ChatMemoryStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcChatMemoryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return jdbcTemplate.query(
                "SELECT message_json FROM spider_chat_memory WHERE memory_id = ? ORDER BY message_order",
                (resultSet, rowNumber) -> ChatMessageDeserializer.messageFromJson(resultSet.getString("message_json")),
                memoryId.toString());
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String id = memoryId.toString();
        jdbcTemplate.update("DELETE FROM spider_chat_memory WHERE memory_id = ?", id);

        if (messages.isEmpty()) {
            return;
        }

        List<Object[]> rows = new ArrayList<>(messages.size());
        for (int index = 0; index < messages.size(); index++) {
            rows.add(new Object[]{id, index, ChatMessageSerializer.messageToJson(messages.get(index))});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO spider_chat_memory (memory_id, message_order, message_json) VALUES (?, ?, ?)",
                rows);
    }

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        jdbcTemplate.update("DELETE FROM spider_chat_memory WHERE memory_id = ?", memoryId.toString());
    }
}
