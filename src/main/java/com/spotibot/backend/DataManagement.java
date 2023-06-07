package com.spotibot.backend;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.NoArgsConstructor;

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

	public static Optional<Map.Entry<String, UserSession>> getMatchingEntry(String roomIdentifier) {
		return userSessionCache
				.entrySet()
				.stream()
				.filter(entry -> entry.getValue().getUserRoom().getRoomIdentifier().equals(roomIdentifier))
				.findFirst();
	}
}

