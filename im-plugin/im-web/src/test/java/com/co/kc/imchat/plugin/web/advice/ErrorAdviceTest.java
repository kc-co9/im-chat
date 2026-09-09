package com.co.kc.imchat.plugin.web.advice;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorAdviceTest {

    @Test
    void handlesMissingStaticResourceAsNotFound() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new MissingResourceController())
                .setControllerAdvice(new ErrorAdvice())
                .build();

        mockMvc.perform(get("/missing-resource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(HttpErrorCode.NOT_FOUND.getCode()));
    }

    @RestController
    private static class MissingResourceController {

        @GetMapping("/missing-resource")
        void missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "favicon.ico");
        }
    }
}
