package com.paynest.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.paynest.user.domain.User;
import com.paynest.user.domain.UserRepository;

/** T-USR-1 .. T-USR-4. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class UserApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Test
    void registerStoresUserWithHashedPassword() throws Exception { // T-USR-1
        String email = uniqueEmail();

        mvc.perform(register(email, "secret-pass"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.kycStatus").value("PENDING"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User stored = users.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("secret-pass").startsWith("$2");
    }

    @Test
    void duplicateEmailIsRejected() throws Exception { // T-USR-2
        String email = uniqueEmail();
        mvc.perform(register(email, "secret-pass")).andExpect(status().isCreated());

        mvc.perform(register(email.toUpperCase(), "other-pass"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void loginReturnsJwtOrRejectsWrongPassword() throws Exception { // T-USR-3
        String email = uniqueEmail();
        mvc.perform(register(email, "secret-pass")).andExpect(status().isCreated());

        String token = login(email, "secret-pass");
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void kycDefaultsToPendingAndUpdatesPersist() throws Exception { // T-USR-4
        String email = uniqueEmail();
        mvc.perform(register(email, "secret-pass")).andExpect(status().isCreated());
        String token = login(email, "secret-pass");

        mvc.perform(post("/users/me/kyc").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"documentNumber\":\"012345678901\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycStatus").value("VERIFIED"));

        assertThat(users.findByEmail(email).orElseThrow().getKycStatus().name()).isEqualTo("VERIFIED");
    }

    @Test
    void invalidRegistrationIsRejected() throws Exception {
        mvc.perform(register("not-an-email", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static MockHttpServletRequestBuilder register(String email, String password) {
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"fullName\":\"Nguyen Van A\"}");
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
