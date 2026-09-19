package nl.markhayen.desktop.auth;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
class AuthorizationCodeRedirectController {

    private final SystemBrowserOAuth2Login login;

    AuthorizationCodeRedirectController(SystemBrowserOAuth2Login login) {
        this.login = login;
    }

    @GetMapping("/login/oauth2/code/google")
    String signedIn(@RequestParam Map<String, String> parameters, Model model) {
        model.addAttribute("name", this.login.finish("google-login", parameters).email());
        return "signed-in";
    }

}
