package se.segersten.wreckage.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FrontendControllerTest {

    @Test
    void directGameLinksLoadTheFrontend() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FrontendController()).build();
        var path = "/game/12345678-1234-1234-1234-123456789abc";
        for (var suffix : new String[] {"", "/"}) {
            mvc.perform(get(path + suffix))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
        mvc.perform(get("/games/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }
}
