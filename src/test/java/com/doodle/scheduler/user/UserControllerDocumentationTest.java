package com.doodle.scheduler.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.shared.exception.NotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * @WebMvcTest, not a full context: this documents the HTTP contract by
 * mocking UserService, repository/DB behavior is out of scope here.
 */
@WebMvcTest(UserController.class)
@ExtendWith(RestDocumentationExtension.class)
class UserControllerDocumentationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    void createUser_returns201AndDocumentsTheContract() throws Exception {
        UUID id = UUID.randomUUID();
        User created = User.builder()
                .id(id)
                .name("Ada Lovelace")
                .email("ada@example.com")
                .createdAt(Instant.parse("2027-01-01T00:00:00Z"))
                .build();

        when(userService.createUser(any(CreateUserCommand.class))).thenReturn(created);

        mockMvc.perform(post("/users")
                        .contentType("application/json")
                        .content(
                                """
                                {"name":"Ada Lovelace","email":"ada@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andDo(document(
                        "create-user",
                        requestFields(
                                fieldWithPath("name").description("Must not be blank"),
                                fieldWithPath("email").description("Must be a valid, unique email address")),
                        responseFields(
                                fieldWithPath("id").description("Generated user id"),
                                fieldWithPath("name").description("Echoes the request"),
                                fieldWithPath("email").description("Echoes the request"),
                                fieldWithPath("createdAt").description("Server-assigned creation timestamp"))));
    }

    @Test
    void createUser_returns409_whenEmailAlreadyExists() throws Exception {
        when(userService.createUser(any(CreateUserCommand.class)))
                .thenThrow(new ConflictException("A user with email 'ada@example.com' already exists"));

        mockMvc.perform(post("/users")
                        .contentType("application/json")
                        .content(
                                """
                                {"name":"Ada Lovelace","email":"ada@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andDo(document("create-user-conflict"));
    }

    @Test
    void createUser_returns400_whenValidationFails() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType("application/json")
                        .content(
                                """
                                {"name":"","email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andDo(document("create-user-validation-error"));
    }

    @Test
    void getUser_returns200AndDocumentsTheContract() throws Exception {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id)
                .name("Ada Lovelace")
                .email("ada@example.com")
                .createdAt(Instant.parse("2027-01-01T00:00:00Z"))
                .build();

        when(userService.getUser(id)).thenReturn(user);

        mockMvc.perform(get("/users/{userId}", id))
                .andExpect(status().isOk())
                .andDo(document(
                        "get-user",
                        pathParameters(parameterWithName("userId").description("The user's id")),
                        responseFields(
                                fieldWithPath("id").description("User id"),
                                fieldWithPath("name").description("User name"),
                                fieldWithPath("email").description("User email"),
                                fieldWithPath("createdAt").description("Creation timestamp"))));
    }

    @Test
    void getUser_returns404_whenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUser(id)).thenThrow(new NotFoundException("No user found with id '%s'".formatted(id)));

        mockMvc.perform(get("/users/{userId}", id))
                .andExpect(status().isNotFound())
                .andDo(document("get-user-not-found"));
    }
}