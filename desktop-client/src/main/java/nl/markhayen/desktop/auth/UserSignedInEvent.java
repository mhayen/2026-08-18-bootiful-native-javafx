package nl.markhayen.desktop.auth;

import org.springframework.context.ApplicationEvent;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Objects;

public class UserSignedInEvent extends ApplicationEvent {

	public UserSignedInEvent(OAuth2AuthenticationToken stage) {
		super(stage);
	}

	public OidcUser user() {
		return (OidcUser) authentication().getPrincipal();
	}

	public String name() {
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
