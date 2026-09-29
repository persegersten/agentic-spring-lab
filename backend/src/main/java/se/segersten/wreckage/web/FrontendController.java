package se.segersten.wreckage.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {

    @GetMapping({"/game/{id}", "/game/{id}/"})
    public String gamePage() {
        return "forward:/index.html";
    }
}
