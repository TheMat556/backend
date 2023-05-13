package com.spotibot.backend;

public final class Credentials {
	
	private Credentials() {
		
	}
	public static final String CLIENT_ID = "38d09828a8a84a78ac5b5cbe9d7c856d";
	public static final String CLIENT_SECRET = "6089e6ee3d894653bbc687592672ebca";

	public static final String apiUri = "http://localhost:8080/spotify/get-user-code";
	public static final String scopes = "user-read-playback-state user-modify-playback-state user-read-currently-playing";
}
