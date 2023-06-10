package com.spotibot.backend.spotify;

import com.spotibot.backend.DataManagement;
import com.spotibot.backend.UserSession;
import com.spotibot.backend.Vote;
import com.spotibot.backend.room.Room;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.hc.core5.http.ParseException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
@RestController
@RequestMapping("/spotify")
public class SpotifyApiController {
    private static final String sessionAttribute = "userIdentifier";
    SpotifyController spotifyController = new SpotifyController();

    //TODO: Some sort of error checking for each request
    //TODO: Check if hostPriv. var really is needed -> not so clean with this var -> there could also a single endpoint for this

    //NOTE: At this point we do NOT create sessions anymore! This logic is alone for the room!

    /**
     * Generates an authorization code URI for the Spotify Web API and returns it in a {@link ResponseEntity} object.
     * This method uses the authorization code flow to authenticate the user with Spotify and obtain an access token for API requests.
     *
     * @return A {@link ResponseEntity} object containing the authorization code URI to redirect the user to for authentication.
     */
    @GetMapping(path = "/login")
    public ResponseEntity<URI> SpotifyLogin(HttpServletRequest request, @RequestParam String roomIdentifier) {
        HttpSession httpSession = request.getSession();
        String userIdentifier = (String) httpSession.getAttribute(sessionAttribute);
        Optional<Map.Entry<String, UserSession>> userSessionEntry = DataManagement.getMatchingEntry(roomIdentifier);

        if (userSessionEntry.isPresent()) {
            if (Objects.equals(userSessionEntry.get().getKey(), userIdentifier)) {
                if (userSessionEntry.get().getValue().getUserSpotifyToken() == null) {
                    return ResponseEntity.ok(spotifyController.authorizationCodeUriRequest().execute());
                } else {
                    //Already authenticated
                    return ResponseEntity.status(HttpStatus.CREATED).build();
                }
            } else {
                //Guest joins the room
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
            }
        } else {
            //No Room found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * This method asynchronously processes the user code received from Spotify's authorization endpoint and updates or creates a token for the user.
     *
     * @param request  the HTTP servlet request object
     * @param userCode the user code received from the Spotify authorization endpoint
     * @param response the HTTP servlet response object
     * @return a CompletableFuture object that will eventually hold the ResponseEntity object with the HTTP response
     * @throws IOException if an I/O error occurs while processing the request or response
     */
    @GetMapping(path = "/get-user-code")
    public ResponseEntity<String> getSpotifyUserCode(HttpServletRequest request, @RequestParam("code") String spotifyUserCode) throws IOException {
        HttpSession httpSession = request.getSession();
        String userIdentifier = (String) httpSession.getAttribute(sessionAttribute);
        UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);

        if (userSession != null) {
            var requestResponse = spotifyController.authorizationCodeRequest(userSession, spotifyUserCode);

            if (requestResponse != null) {
                return ResponseEntity.ok(requestResponse);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * This method checks if the user is authenticated with Spotify.
     *
     * @param request the HttpServletRequest containing the user's session
     * @return a ResponseEntity with a Boolean indicating whether the user is authenticated or not. If the user is not authenticated, returns noContent(); otherwise, returns ok(true) or ok(false) depending on the authentication status.
     */
    //TODO: I don't think the UI should hold that logic, the backend should check if the user is already authenticated if a request is sent
    @RequestMapping(path = "/is-authenticated")
    public ResponseEntity<Boolean> checkSpotifyAuthenticationStatus(HttpServletRequest request) {
        HttpSession httpSession = request.getSession();
        String userIdentifier = (String) httpSession.getAttribute(sessionAttribute);
        UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);

        if (userSession.getUserSpotifyToken() == null) {
            return ResponseEntity.status(HttpStatus.OK).body(false);
        } else if (userSession.getUserSpotifyToken().getExpiresIn() <= System.currentTimeMillis() / 1000) {
            if (spotifyController.refreshSpotifyToken(userSession.getUserSpotifyToken())) {
                return ResponseEntity.status(HttpStatus.OK).body(true);
            } else {
                return ResponseEntity.status(HttpStatus.OK).body(false);
            }
        }

        return ResponseEntity.status(HttpStatus.OK).body(true);
    }

    /**
     * This endpoint retrieves information about the current song being played in a given room,
     * identified by its room identifier. It returns a JSON response containing information about the
     * song such as its title, artist, duration, image url, and playback status. Additionally, it includes
     * information about the number of votes that have been cast to skip the current song, and the number
     * of votes required to skip it.
     *
     * @param roomIdentifier the identifier of the room for which to retrieve the current song
     * @return a ResponseEntity<String> containing a JSON object with information about the current song
     * @throws SpotifyWebApiException if there is an error retrieving information from the Spotify API
     * @throws IOException            if there is an error parsing the Spotify API response
     * @throws ParseException         if there is an error parsing the Spotify API response
     */
    //TODO: There seems to be an issue if nothing is currently playing! -> currently playing context is null then.
    @RequestMapping(path = "/current-song")
    public ResponseEntity<String> currentSong(@RequestParam("code") String roomIdentifier) {
        Optional<UserSession> userSession = DataManagement.getMatchingUserSession(roomIdentifier);

        if (userSession.isPresent()) {
            UserSession currentUserSession = userSession.get();

            if (currentUserSession.getUserSpotifyToken() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Not authenticated!");
            } else {
                CurrentlyPlayingContext currentlyPlayingContext = spotifyController.currentlyPlayingContext(userSession.get().getUserSpotifyToken());

                if (currentlyPlayingContext == null) {
                    var devices = spotifyController.getDevices(userSession.get().getUserSpotifyToken());
                    return ResponseEntity.status(HttpStatus.UPGRADE_REQUIRED).body(JSONObject.valueToString(devices));
                } else {
                    return ResponseEntity.ok(buildCurrentSongContextJSON(currentlyPlayingContext, userSession.get()).toString());
                }

            }

        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping(path = "/devices")
    public ResponseEntity<String> getDevices(HttpServletRequest request) {
        HttpSession session = request.getSession();
        String userIdentifier = (String) session.getAttribute(sessionAttribute);
        UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);

        var devices = spotifyController.getDevices(userSession.getUserSpotifyToken());
        return ResponseEntity.status(HttpStatus.OK).body(JSONObject.valueToString(devices));
    }

    @RequestMapping(path = "/toggle-playing-status")
    public ResponseEntity<String> togglePlayingStatus(HttpServletRequest request, @RequestParam("code") String roomIdentifier) {
        HttpSession session = request.getSession();
        String userIdentifier = (String) session.getAttribute(sessionAttribute);
        Optional<UserSession> userSession = DataManagement.getMatchingUserSession(roomIdentifier);

        if (userSession.isPresent()) {
            SpotifyToken spotifyToken = userSession.get().getUserSpotifyToken();
            Room userRoom = userSession.get().getUserRoom();

            if (spotifyToken == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not authenticated!");
            }

            if (userRoom.isGuestCanPause() || hasHostPrivileges(userSession.get(), userIdentifier)) {
                if (userRoom.isCurrentlyPlaying()) {
                    if (spotifyController.pauseCurrentlyPlayingSong(spotifyToken)) {
                        return ResponseEntity.status(HttpStatus.OK).build();
                    } else {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    }
                } else {
                    if (spotifyController.resumeCurrentlyPausedSong(spotifyToken)) {
                        return ResponseEntity.status(HttpStatus.OK).build();
                    } else {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    }
                }

            } else {
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
            }

        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping(path = "skip-song")
    public ResponseEntity<String> skipSong(HttpServletRequest request, @RequestParam("code") String roomIdentifier) {
        HttpSession session = request.getSession();
        String userIdentifier = (String) session.getAttribute(sessionAttribute);
        Optional<UserSession> userSession = DataManagement.getMatchingUserSession(roomIdentifier);

        if (userSession.isPresent()) {
            SpotifyToken spotifyToken = userSession.get().getUserSpotifyToken();
            UserSession currentUserSession = userSession.get();

            if (spotifyToken == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!hasHostPrivileges(currentUserSession, userIdentifier)) {
                if (!currentUserSession.getUserRoom().hasUserAlreadyVoted(userIdentifier)) {
                    currentUserSession.getUserRoom().getVoteList().add(new Vote(userIdentifier, currentUserSession.getUserRoom().getCurrentSong()));
                    return ResponseEntity.status(HttpStatus.OK).body("Successfully voted!");
                } else {
                    return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("Already voted!");
                }
            }

            if (hasHostPrivileges(currentUserSession, userIdentifier) ||
                    currentUserSession.getUserRoom().getVoteListLength() >= currentUserSession.getUserRoom().getVotesToSkip()) {
                if (spotifyController.skipCurrentlyPlayingSong(currentUserSession.getUserSpotifyToken())) {
                    return ResponseEntity.status(HttpStatus.OK).body("");
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("");
                }
            }
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        //TODO: Why do we need this?
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    //################
    //HELPER FUNCTIONS
    //################

    private JSONObject buildCurrentSongContextJSON(CurrentlyPlayingContext currentlyPlayingContext, UserSession userSession) {
        JSONObject jsonResponse = new JSONObject();
        Track track = (Track) currentlyPlayingContext.getItem();
        Integer votesToSkip = userSession.getUserRoom().getVotesToSkip();
        Integer currentVotesToSkip = userSession.getUserRoom().getVoteList().size();

        jsonResponse.put("title", track.getName());
        jsonResponse.put("artist", track.getArtists());
        jsonResponse.put("duration", track.getDurationMs());
        jsonResponse.put("time", currentlyPlayingContext.getProgress_ms());
        jsonResponse.put("image_url", track.getAlbum().getImages());
        jsonResponse.put("is_playing", currentlyPlayingContext.getIs_playing());
        jsonResponse.put("currentVotesToSkip", currentVotesToSkip);
        jsonResponse.put("votesToSkip", votesToSkip);
        jsonResponse.put("id", track.getId());

        return jsonResponse;
    }

    private boolean hasHostPrivileges(UserSession userSession, String userIdentifier) {
        UserSession currentSession = DataManagement.userSessionCache.get(userIdentifier);
        return userSession.equals(currentSession);
    }

    //TODO: updateRoomSong
}








