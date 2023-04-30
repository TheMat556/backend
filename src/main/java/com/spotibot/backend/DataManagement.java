package com.spotibot.backend;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import lombok.NoArgsConstructor;
import org.springframework.boot.autoconfigure.security.SecurityProperties.User;

@NoArgsConstructor
public final class DataManagement {
	public static final Map<String, UserSession> userSessionCache = new HashMap<String, UserSession>();

	//public static final ArrayList<User> userList = new ArrayList<User>();
	//public static final ArrayList<Room> roomList = new ArrayList<Room>();
	//public static final ArrayList<SpotifyToken> tokenList = new ArrayList<SpotifyToken>();
	
}

