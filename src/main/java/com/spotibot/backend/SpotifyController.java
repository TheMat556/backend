package com.spotibot.backend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.hc.core5.http.ParseException;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.security.SecurityProperties.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import se.michaelthelin.spotify.requests.data.artists.GetArtistRequest;
import se.michaelthelin.spotify.requests.data.player.GetInformationAboutUsersCurrentPlaybackRequest;
import se.michaelthelin.spotify.requests.data.player.PauseUsersPlaybackRequest;
import se.michaelthelin.spotify.requests.data.player.SkipUsersPlaybackToNextTrackRequest;
import se.michaelthelin.spotify.requests.data.player.StartResumeUsersPlaybackRequest;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;


@RestController
@RequestMapping("/spotify")
public class SpotifyController {

	private static final URI REDIRECT_URI = SpotifyHttpManager.makeUri("http://localhost:8080/spotify/get-user-code");

	private static final SpotifyApi spotifyApi = new SpotifyApi.Builder()
			.setClientId(Credentials.CLIENT_ID)
			.setClientSecret(Credentials.CLIENT_SECRET)
			.setRedirectUri(REDIRECT_URI)
			.build();

	private static final AuthorizationCodeRefreshRequest authorizationCodeRefreshRequest = spotifyApi.authorizationCodeRefresh().build();

	//TODO: Some sort of error checking for each request

	/**
	 Generates an authorization code URI for the Spotify Web API and returns it in a {@link ResponseEntity} object.
	 This method uses the authorization code flow to authenticate the user with Spotify and obtain an access token for API requests.
	 @return A {@link ResponseEntity} object containing the authorization code URI to redirect the user to for authentication.
	 */
	@RequestMapping(path = "/login", method = RequestMethod.GET)
	public ResponseEntity<URI> SpotifyLogin() {
		final String scopes = "user-read-playback-state user-modify-playback-state user-read-currently-playing";

		AuthorizationCodeUriRequest authorizationCodeUriRequest = spotifyApi
				.authorizationCodeUri()
				.scope(scopes)
				.show_dialog(true)
				.build();

		return ResponseEntity.ok(authorizationCodeUriRequest.execute());
	}

	/**
	 This method asynchronously processes the user code received from Spotify's authorization endpoint and updates or creates a token for the user.
	 @param request the HTTP servlet request object
	 @param userCode the user code received from the Spotify authorization endpoint
	 @param response the HTTP servlet response object
	 @return a CompletableFuture object that will eventually hold the ResponseEntity object with the HTTP response
	 @throws IOException if an I/O error occurs while processing the request or response
	 */
	@RequestMapping(path = "/get-user-code")
	public CompletableFuture<ResponseEntity<String>> getSpotifyUserCode(HttpServletRequest request, @RequestParam("code") String userCode, HttpServletResponse response) throws IOException {
		HttpSession session = request.getSession();
		String userIdentifier = (String) session.getAttribute("userIdentifier");

		UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);

		if (userSession != null) {
			AuthorizationCodeRequest authorizationCodeRequest = spotifyApi.authorizationCode(userCode).build();

			return CompletableFuture.supplyAsync(() -> {
				AuthorizationCodeCredentials authorizationCodeCredentials = null;
				try {
					authorizationCodeCredentials = authorizationCodeRequest.execute();
					spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
					spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());
					System.out.println("Expires in: " + authorizationCodeCredentials.getExpiresIn());

					updateOrCreateToken(userIdentifier, authorizationCodeCredentials);

					return ResponseEntity.ok("<html><head><script>window.close();</script></head><body>Closing tab...</body></html>");
				} catch (IOException | SpotifyWebApiException | org.apache.hc.core5.http.ParseException e) {
					System.out.println("Error" + e.getMessage());
					return ResponseEntity.badRequest().build();
				}
			});
		} else {
			return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
		}
	}

	/**
	 This method checks if the user is authenticated with Spotify.
	 @param request the HttpServletRequest containing the user's session
	 @return a ResponseEntity with a Boolean indicating whether the user is authenticated or not. If the user is not authenticated, returns noContent(); otherwise, returns ok(true) or ok(false) depending on the authentication status.
	 */
	@RequestMapping(path = "/is-authenticated")
	public ResponseEntity<Boolean> checkSpotifyAuthenticationStatus(HttpServletRequest request) {
		HttpSession session = request.getSession();
		String userIdentifier = (String) session.getAttribute("userIdentifier");

		if (userIdentifier == null || userIdentifier == "") {
			return ResponseEntity.noContent().build();
		} else {
			UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);
			SpotifyToken userToken = userSession.getUserSpotifyToken();

			if (userToken == null) {
				return ResponseEntity.ok(false);
			} else {
				if (userToken.getExpiresIn() <= System.currentTimeMillis() / 1000) {
					refreshSpotifyToken(userIdentifier);
				}
				return ResponseEntity.ok(true);
			}
		}
	}

	/**
	 This endpoint retrieves information about the current song being played in a given room,
	 identified by its room identifier. It returns a JSON response containing information about the
	 song such as its title, artist, duration, image url, and playback status. Additionally, it includes
	 information about the number of votes that have been cast to skip the current song, and the number
	 of votes required to skip it.
	 @param roomIdentifier the identifier of the room for which to retrieve the current song
	 @return a ResponseEntity<String> containing a JSON object with information about the current song
	 @throws SpotifyWebApiException if there is an error retrieving information from the Spotify API
	 @throws IOException if there is an error parsing the Spotify API response
	 @throws ParseException if there is an error parsing the Spotify API response
	 */
	@RequestMapping(path = "/current-song")
	public ResponseEntity<String> currentSong(@RequestParam("code") String roomIdentifier) {
		SpotifyToken userToken = DataManagement.userSessionCache.get(roomIdentifier).getUserSpotifyToken();
		Room userRoom = DataManagement.userSessionCache.get(roomIdentifier).getUserRoom();

		if(userToken == null || userRoom == null)
		{
			return ResponseEntity.badRequest().build();
		}
		else
		{
			spotifyApi.setAccessToken(userToken.getAccessToken());
			spotifyApi.setRefreshToken(userToken.getRefreshToken());
			GetInformationAboutUsersCurrentPlaybackRequest getInformationAboutUsersCurrentPlaybackRequest = spotifyApi.getInformationAboutUsersCurrentPlayback().build();
			JSONObject jsonResponse = new JSONObject();

			try
			{
				CurrentlyPlayingContext currentlyPlayingContext = getInformationAboutUsersCurrentPlaybackRequest.execute();

				Track track = (Track) currentlyPlayingContext.getItem();
				Integer votes = userRoom.getVoteListLength();

				jsonResponse.put("title", track.getName());
				jsonResponse.put("artist", track.getArtists());
				jsonResponse.put("duration", track.getDurationMs());
				jsonResponse.put("time", currentlyPlayingContext.getProgress_ms());
				jsonResponse.put("image_url", track.getAlbum().getImages());
				jsonResponse.put("is_playing", currentlyPlayingContext.getIs_playing());
				jsonResponse.put("votes", votes);
				jsonResponse.put("votes_required", userRoom.getVotesToSkip());
				jsonResponse.put("id", track.getId());

				return ResponseEntity.ok(jsonResponse.toString());
			}
			catch (IOException | ParseException | SpotifyWebApiException e)
			{
				return ResponseEntity.internalServerError().build();
			}
		}
	}

	@RequestMapping(path = "/pause-song")
	public ResponseEntity<String> pauseSong(HttpServletRequest request, @RequestParam("code") String roomID) {
		HttpSession session = request.getSession();
		User userSession = (User) session.getAttribute("user");

		SpotifyToken token = getTokenFromRoomCode(roomID);
		Room room = getRoom(roomID);

		if(token == null) {
			return ResponseEntity.notFound().build();
		}

		if(room.getUserSession().equals(userSession) || room.isGuestCanPause()) {
			spotifyApi.setAccessToken(token.getAccessToken());
			spotifyApi.setRefreshToken(token.getRefreshToken());

			PauseUsersPlaybackRequest pauseUsersPlaybackRequest= spotifyApi.pauseUsersPlayback().build();

			try {
				pauseUsersPlaybackRequest.execute();
			} catch (ParseException | SpotifyWebApiException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			return ResponseEntity.ok().build();
		}

		return ResponseEntity.badRequest().build();
	}

	@RequestMapping(path = "/resume-song")
	public ResponseEntity<String> playSong(HttpServletRequest request, @RequestParam("code") String roomID) {
		HttpSession session = request.getSession();
		User userSession = (User) session.getAttribute("user");

		SpotifyToken token = getTokenFromRoomCode(roomID);
		Room room = getRoom(roomID);

		if(token == null) {
			return ResponseEntity.notFound().build();
		}

		if(room.getUserSession().equals(userSession) || room.isGuestCanPause()) {
			spotifyApi.setAccessToken(token.getAccessToken());
			spotifyApi.setRefreshToken(token.getRefreshToken());

			StartResumeUsersPlaybackRequest startResumeUsersPlayback= spotifyApi.startResumeUsersPlayback().build();

			try {
				startResumeUsersPlayback.execute();
			} catch (ParseException | SpotifyWebApiException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			return ResponseEntity.ok().build();
		}

		return ResponseEntity.badRequest().build();
	}

	@RequestMapping(path = "skip-song")
	public ResponseEntity<String> skipSong(HttpServletRequest request, @RequestParam("code") String roomID) {
		Room room = getRoom(roomID);
		HttpSession session = request.getSession();
		User userSession = (User) session.getAttribute("user");


		if(room.getUserSession().equals(userSession) || room.getVoteListLength() + 1 >= room.getVotesToSkip()) {
			room.clearVoteList();
			skipSong(room);
			return ResponseEntity.ok().build();
		} else {

			if(userSession == null) {
				userSession = getUser();
				session.setAttribute("user", userSession);
			}

			if(getVoteFromUser(room, userSession) == null) {
				Vote vote = new Vote(userSession, room.getCurrentSong());
				room.addVote(vote);
				return ResponseEntity.ok().build();
			}

		}

		return ResponseEntity.ok().build();
	}

	//################
	//HELPER FUNCTIONS
	//################

	public Vote getVoteFromUser(Room room, User user) {
		System.out.println("--------");
		System.out.println(user);
		for(Vote v : room.getVoteList()) {
			System.out.println(v.getUser());
			if(v.getUser().equals(user)) {
				return v;
			}
		}

		return null;
	}


	public void updateRoomSong(Room room, String songId) {
		String current_song = room.getCurrentSong();

		if(!current_song.equals(songId)) {
			room.setCurrentSong(songId);
		}

	}

	public void skipSong(Room room) {
		SpotifyToken token = getTokenFromRoomCode(room.getCode());

		spotifyApi.setAccessToken(token.getAccessToken());
		spotifyApi.setRefreshToken(token.getRefreshToken());

		final SkipUsersPlaybackToNextTrackRequest skipUsersPlaybackToNextTrackRequest = spotifyApi.skipUsersPlaybackToNextTrack().build();

		try {
			skipUsersPlaybackToNextTrackRequest.execute();
		} catch (ParseException | SpotifyWebApiException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public String getArtitist(String id) {
		final GetArtistRequest getArtistRequest = spotifyApi.getArtist(id).build();

		try {
			final Artist artist = getArtistRequest.execute();
			return artist.getName();
		} catch (ParseException | SpotifyWebApiException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "";
	}

	public Room getRoom(String roomCode) {
		for(Room r : DataManagement.roomList) {
			if(r.getCode().equals(roomCode)) {
				return r;
			}
		}

		return null;
	}

	public SpotifyToken getToken(User user) {

		for(SpotifyToken t : DataManagement.tokenList) {
			if(t.getUser().equals(user)) {
				return t;
			}
		}

		return null;
	}

	public SpotifyToken getTokenFromRoomCode(String roomCode) {

		for(Room r : DataManagement.roomList) {
			if(r.getCode().equals(roomCode)) {
				return getToken(r.getUserSession());
			}
		}

		return null;
	}

	public void executeSpotifyAPIRequest(User session) {
		SpotifyToken token = getUserToken(session);
		spotifyApi.setAccessToken(token.getAccessToken());
		spotifyApi.getUsersCurrentlyPlayingTrack();

	}

	public SpotifyToken getUserToken(User user) {
		for(SpotifyToken s : DataManagement.tokenList) {
			if(user.equals(s.getUser())){
				return s;
			}
		}

		return null;
	}

	/**
	 Updates or creates a new Spotify access token for the user identified by {@code userIdentifier}.
	 @param userIdentifier the identifier of the user
	 @param acc the new authorization code credentials to create or update the token
	 */
	public void updateOrCreateToken(String userIdentifier, AuthorizationCodeCredentials acc) {
		UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);
		SpotifyToken currentUserToken = userSession.getUserSpotifyToken();

		if(currentUserToken == null)
		{
			SpotifyToken newUserToken = new SpotifyToken(acc);
			userSession.setUserSpotifyToken(newUserToken);
		}
		else
		{
			currentUserToken.setAuthorizationCodeCredentials(acc);
		}
	}

	public void refreshSpotifyToken(String userIdentifier) {
		AuthorizationCodeCredentials authorizationCodeCredentials = null;

	    try {
			authorizationCodeCredentials = authorizationCodeRefreshRequest.execute();

			spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());


		} catch (IOException | SpotifyWebApiException | ParseException e) {
		      System.out.println("Error: " + e.getMessage());
	    }

	    updateOrCreateToken(user, authorizationCodeCredentials);
	}

}








