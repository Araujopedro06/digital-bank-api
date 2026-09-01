package com.pedro.bank.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedro.bank.web.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every request a browser makes carries an Origin header, and Spring rejects the
 * whole request with 403 when it does not match. curl sends no Origin, so these
 * cases stay invisible to a hand-written API check — which is exactly how a
 * phone on the LAN ended up unable to log in while curl reported everything fine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginIsAllowedFromAPrivateNetworkOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "https://192.168.0.7:4200")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nobody@test.com", "password123"))))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://192.168.0.7:4200"))
                // Credentials are wrong on purpose: 401 proves the request reached
                // the controller rather than being turned away by the CORS filter.
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsAllowedFromAnyLocalhostPort() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "http://localhost:4300")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nobody@test.com", "password123"))))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4300"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preflightFromAnAllowedOriginSucceeds() throws Exception {
        mockMvc.perform(options("/api/transfers")
                        .header("Origin", "https://192.168.1.42:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://192.168.1.42:4200"));
    }

    @Test
    void anUnrelatedPublicOriginIsStillRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "https://evil.example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nobody@test.com", "password123"))))
                .andExpect(status().isForbidden());
    }
}
