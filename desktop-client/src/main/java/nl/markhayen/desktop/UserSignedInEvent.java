package nl.markhayen.desktop;

import org.springframework.context.ApplicationEvent;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Objects;

class UserSignedInEvent extends ApplicationEvent {

	UserSignedInEvent(OAuth2AuthenticationToken stage) {
		super(stage);
	}

	OidcUser user() {
		return (OidcUser) authentication().getPrincipal();
	}

	String name() {
		return user().getClaim("name");
	}

	String sub() {
		return Objects.requireNonNull(user().getPreferredUsername());
	}
	String email() {
		return Objects.requireNonNull(user().getEmail());
	}

	OAuth2AuthenticationToken authentication() {
		return (OAuth2AuthenticationToken) getSource();
	}

}
