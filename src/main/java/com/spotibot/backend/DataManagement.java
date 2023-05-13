package com.spotibot.backend;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.NoArgsConstructor;
import org.springframework.boot.autoconfigure.security.SecurityProperties.User;

@NoArgsConstructor
public final class DataManagement {
	public static final Map<String, UserSession> userSessionCache = new HashMap<String, UserSession>();

	public static Optional<UserSession> getMatchingUserSession(String roomIdentifier) {
		return userSessionCache
				.values()
				.stream()
				.filter(userSession -> userSession.getUserRoom().getRoomIdentifier().equals(roomIdentifier))
				.findFirst();
	}
}

