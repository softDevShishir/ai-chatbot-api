package com.example.aichatbot;

import com.example.aichatbot.provider.AiCompletionService;
import com.example.aichatbot.provider.AiResult;
import com.example.aichatbot.provider.CompletionOutcome;
import com.example.aichatbot.retrieval.KnowledgeSearch;
import com.example.aichatbot.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over HTTP against the real H2 database (migrations from
 * db/migration), with only the AI call and knowledge retrieval mocked — so
 * no network call or Postgres is ever needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KnowledgeSearch knowledgeSearch;

    @MockBean
    private AiCompletionService aiCompletionService;

    private String ownerToken;

    @BeforeEach
    void setUp() {
        ownerToken = "Bearer " + jwtService.generateToken("test-admin");
        when(knowledgeSearch.search(anyString())).thenReturn(List.of());
        when(aiCompletionService.complete(any(), anyString(), any())).thenReturn(
                new CompletionOutcome(new AiResult("Yes, we have running shoes in stock.", "claude-test", 12, 8),
                        "claude", false));
    }

    @Test
    void createListGetSendAndDeleteChat() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/chats")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Shoe question\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        String chatId = readField(createResult, "id");

        mockMvc.perform(get("/api/chats")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(chatId));

        mockMvc.perform(post("/api/chats/" + chatId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Do you have running shoes?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Yes, we have running shoes in stock."))
                .andExpect(jsonPath("$.provider").value("claude"))
                .andExpect(jsonPath("$.handoff").value(false))
                .andExpect(jsonPath("$.fallbackUsed").value(false));

        mockMvc.perform(get("/api/chats/" + chatId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"));

        mockMvc.perform(delete("/api/chats/" + chatId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/chats/" + chatId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersChatIsNotFound() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/chats")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String chatId = readField(createResult, "id");

        String otherUserToken = "Bearer " + jwtService.generateToken("someone-else");

        mockMvc.perform(get("/api/chats/" + chatId)
                        .header(HttpHeaders.AUTHORIZATION, otherUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(post("/api/chats/" + chatId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hi\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/chats/" + chatId)
                        .header(HttpHeaders.AUTHORIZATION, otherUserToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownProviderReturns400() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/chats")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        String chatId = readField(createResult, "id");

        when(aiCompletionService.complete(org.mockito.ArgumentMatchers.eq("mistral"), anyString(), any()))
                .thenThrow(new com.example.aichatbot.exception.ApiBadRequestException(
                        "Unknown provider \"mistral\". Valid providers: [claude, gemini, openai]"));

        mockMvc.perform(post("/api/chats/" + chatId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hi\", \"provider\": \"mistral\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messageTooLongReturns400() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/chats")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        String chatId = readField(createResult, "id");

        String tooLong = "a".repeat(2001);

        mockMvc.perform(post("/api/chats/" + chatId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Object() {
                            public final String message = tooLong;
                        })))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void providerFailureReturns502() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/chats")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        String chatId = readField(createResult, "id");

        when(aiCompletionService.complete(any(), anyString(), any()))
                .thenThrow(new com.example.aichatbot.provider.AiProviderException(
                        com.example.aichatbot.provider.AiProviderException.Kind.TEMPORARY, "Claude is down"));

        mockMvc.perform(post("/api/chats/" + chatId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hi\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    private String readField(MvcResult result, String field) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get(field).asText();
    }
}
