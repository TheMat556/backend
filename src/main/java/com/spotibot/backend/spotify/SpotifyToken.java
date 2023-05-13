package com.spotibot.backend.spotify;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;

@Getter
@Setter
@NoArgsConstructor
public class SpotifyToken {
	AuthorizationCodeCredentials authorizationCodeCredentials;
	private long expiresIn;

	/**
	 Constructs a new Spotify token with the given user, authorization code credentials, and expiration time.
	 If the provided expiration time is equal to the current time in seconds since the Unix epoch, the token's
	 expiration time is calculated using the {@link #getNewExpireTime()} method. Otherwise, the provided expiration
	 time is set directly.
	 @param authorizationCodeCredentials the authorization code credentials associated with the token
	 */
	public SpotifyToken(AuthorizationCodeCredentials authorizationCodeCredentials) {
		setAuthorizationCodeCredentials(authorizationCodeCredentials);

		if(authorizationCodeCredentials.getExpiresIn() == (System.currentTimeMillis() / 1000)) {
			expiresIn = getNewExpireTime();
		} else {
			expiresIn = authorizationCodeCredentials.getExpiresIn();
		}
	}

	/**
	 Updates the Spotify token with new authorization code credentials.
	 This method sets the new authorization code credentials, and then calculates and sets the new
	 expiration time of the token using the {@link #getNewExpireTime()} method.
	 @param authorizationCodeCredentials the new authorization code credentials to set for the token
	 */
	public void updateToken(AuthorizationCodeCredentials authorizationCodeCredentials) {
		setAuthorizationCodeCredentials(authorizationCodeCredentials);
		setExpiresIn(getNewExpireTime());
	}

	/**
	 Returns the access token obtained from the authorization code credentials.
	 @return the access token obtained from the authorization code credentials.
	 */
	public String getAccessToken() {
		return authorizationCodeCredentials.getAccessToken();
	}

	/**
	 Returns the refresh token obtained from the authorization code credentials.
	 @return the refresh token obtained from the authorization code credentials.
	 */
	public String getRefreshToken() {
		return authorizationCodeCredentials.getRefreshToken();
	}

	/**
	 Returns the scope obtained from the authorization code credentials.
	 @return the scope obtained from the authorization code credentials.
	 */
	public String getScope() {
		return authorizationCodeCredentials.getScope();
	}

	/**
	 Returns the token type obtained from the authorization code credentials.
	 @return the token type obtained from the authorization code credentials.
	 */
	public String getTokenType() {
		return authorizationCodeCredentials.getTokenType();
	}


	/**
	 Returns a new expiration time for the current Spotify authorization code credentials.
	 This method calculates the new expiration time by adding the number of seconds until the
	 credentials expire to the current time in milliseconds since the epoch divided by 1000.
	 @return a long value representing the new expiration time in seconds since the epoch
	 */
	private long getNewExpireTime() {
		return System.currentTimeMillis() / 1000 + this.authorizationCodeCredentials.getExpiresIn();
	}

}
