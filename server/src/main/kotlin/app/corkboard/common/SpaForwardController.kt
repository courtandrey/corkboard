package app.corkboard.common

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class SpaForwardController {

    @GetMapping(
        "/",
        "/events/{id}",
        "/new",
        "/login",
        "/boards/**",
        "/me/**",
        "/admin/**",
        "/messages",
        "/messages/{id}",
    )
    fun spa(): String = "forward:/index.html"
}
