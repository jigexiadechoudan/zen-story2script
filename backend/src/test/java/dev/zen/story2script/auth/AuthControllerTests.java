package dev.zen.story2script.auth;

import dev.zen.story2script.api.service.NovelToScreenplayService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "story2script.auth.registration-invite-code=test-invite",
        "story2script.auth.token-secret=test-secret-with-more-than-32-characters",
        "story2script.auth.cookie-secure=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private NovelToScreenplayService novelToScreenplayService;

    @Test
    void registersUserAndSetsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "User@Example.com",
                                  "password": "password123",
                                  "displayName": "Writer",
                                  "inviteCode": "test-invite"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("Writer"))
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        register("duplicate@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("duplicate@example.com", "test-invite")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_already_registered"));
    }

    @Test
    void rejectsInvalidInviteCode() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("invite@example.com", "wrong-code")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("invalid_invite_code"));
    }

    @Test
    void logsInRegisteredUser() throws Exception {
        register("login@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "login@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("story2script_session")))
                .andExpect(jsonPath("$.user.email").value("login@example.com"));
    }

    @Test
    void rejectsInvalidPassword() throws Exception {
        register("bad-password@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "bad-password@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
    }

    @Test
    void returnsCurrentUserWithSessionCookie() throws Exception {
        MockCookie cookie = register("me@example.com");

        mockMvc.perform(get("/api/auth/me")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("me@example.com"));
    }

    @Test
    void logoutClearsSessionCookie() throws Exception {
        MockCookie cookie = register("logout@example.com");

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("story2script_session", 0));
    }

    @Test
    void convertRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Test",
                                  "sourceText": "Chapter 1\\nText",
                                  "targetFormat": "short_drama"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));

        verifyNoInteractions(novelToScreenplayService);
    }

    @Test
    void healthRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void schemaRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").exists());
    }

    private MockCookie register(String email) throws Exception {
        String setCookie = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "test-invite")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("Set-Cookie");
        return MockCookie.parse(setCookie);
    }

    private String registerJson(String email, String inviteCode) {
        return """
                {
                  "email": "%s",
                  "password": "password123",
                  "displayName": "Writer",
                  "inviteCode": "%s"
                }
                """.formatted(email, inviteCode);
    }
}
