package com.spotibot.backend.spotify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotibot.backend.*;
import com.spotibot.backend.room.Room;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.hc.core5.http.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.model_objects.miscellaneous.Device;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
@RestController
@RequestMapping("/spotify")
public class SpotifyApiController {
    private static final String SESSION_ATTRIBUTE = "userIdentifier";
    private static final String ERROR_NOT_AUTHENTICATED = "Not authenticated!";

    SpotifyController spotifyController = new SpotifyController();
    private static final Logger logger = LoggerFactory.getLogger(SpotifyApiController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Handles the Spotify login process for a specific room identified by 'roomIdentifier'.
     * This method is accessed via HTTP GET at the path "/login".
     *
     * @param request        The HttpServletRequest object representing the incoming HTTP request.
     * @param roomIdentifier The unique identifier for the room that the user wants to join.
     * @return ResponseEntity containing a URI to initiate Spotify login if the conditions are met.
     * The response may include one of the following HTTP statuses:
     * - HttpStatus.OK (200) if the user is allowed to join the room and is not authenticated with Spotify yet.
     * - HttpStatus.CREATED (201) if the user is allowed to join the room and is already authenticated with Spotify.
     * - HttpStatus.NOT_ACCEPTABLE (406) if the user is not allowed to join the room as a guest.
     * - HttpStatus.NOT_FOUND (404) if the room with the specified 'roomIdentifier' does not exist.
     * @see SpotifyController#authorizationCodeUriRequest()
     * @see DataManagement#getMatchingEntry(String)
     */
    @GetMapping(path = "/login")
    public ResponseEntity<URI> spotifyLogin(HttpServletRequest request, @RequestParam String roomIdentifier)
    {
        HttpSession httpSession = request.getSession();
        String userIdentifier = (String) httpSession.getAttribute(SESSION_ATTRIBUTE);
        Optional<Map.Entry<String, UserSession>> userSessionEntry = DataManagement.getMatchingEntry(roomIdentifier);

        if (userSessionEntry.isEmpty())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Map.Entry<String, UserSession> entry = userSessionEntry.get();
        String roomOwnerIdentifier = entry.getKey();
        UserSession userSession = entry.getValue();

        if (!Objects.equals(roomOwnerIdentifier, userIdentifier))
        {
            //Guest joins the room
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
        }

        SpotifyToken spotifyToken = userSession.getUserSpotifyToken();
        if (spotifyToken == null)
        {
            return ResponseEntity.ok(spotifyController.authorizationCodeUriRequest().execute());
        }

        // Already authenticated
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Retrieves the Spotify user code from the request and processes it to obtain an access token and other related data.
     * This method is accessed via HTTP GET at the path "/get-user-code".
     *
     * @param request         The HttpServletRequest object representing the incoming HTTP request.
     * @param spotifyUserCode The Spotify user code obtained from the user's authorization process.
     * @return ResponseEntity containing a String representing the result of processing the user code.
     * The response may include one of the following HTTP statuses:
     * - HttpStatus.OK (200) if the user code is successfully processed, and the response body contains the result data.
     * - HttpStatus.INTERNAL_SERVER_ERROR (500) if an internal error occurs during the processing of the user code.
     * - HttpStatus.BAD_REQUEST (400) if the user's session is not found or is invalid.
     * @see SpotifyController#authorizationCodeRequest(UserSession, String)
     * @see DataManagement#userSessionCache
     */
    @GetMapping(path = "/get-user-code")
    public ResponseEntity<String> getSpotifyUserCode(HttpServletRequest request, @RequestParam("code") String spotifyUserCode)
    {
        HttpSession httpSession = request.getSession();
        String userIdentifier = (String) httpSession.getAttribute(SESSION_ATTRIBUTE);
        UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);

        if (userSession == null)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try
        {
            String requestResponse = spotifyController.authorizationCodeRequest(userSession, spotifyUserCode);
            return ResponseEntity.ok(requestResponse);
        } catch (IOException | ParseException | SpotifyWebApiException e)
        {
            logger.warn("getSpotifyUserCode(): An error occurred: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves the current song information for the specified room identified by 'roomIdentifier'.
     * This method is accessed via HTTP GET at the path "/current-song".
     *
     * @param roomIdentifier The unique identifier for the room to fetch the current song information.
     * @return ResponseEntity containing a String representing the current song context information in JSON format.
     * The response may include one of the following HTTP statuses:
     * - HttpStatus.OK (200) if the current song information is successfully retrieved and authenticated with Spotify.
     * - HttpStatus.BAD_REQUEST (400) if the room is not authenticated with Spotify.
     * - HttpStatus.UPGRADE_REQUIRED (426) if playing context is null and a device has to be choosen.
     * - HttpStatus.NOT_FOUND (404) if the specified room does not exist.
     * @see DataManagement#getMatchingUserSession(String)
     * @see SpotifyController#checkSpotifyAuthenticationStatus(SpotifyToken)
     * @see SpotifyController#currentlyPlayingContext(SpotifyToken)
     * @see SpotifyController#getDevices(SpotifyToken)
     * @see #buildCurrentSongContextJSON(CurrentlyPlayingContext, UserSession)
     */
    @RequestMapping(path = "/current-song")
    public ResponseEntity<String> currentSong(@RequestParam("code") String roomIdentifier)
    {
        Optional<UserSession> userSession = DataManagement.getMatchingUserSession(roomIdentifier);

        if (userSession.isEmpty())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        UserSession currentUserSession = userSession.get();
        SpotifyToken spotifyToken = currentUserSession.getUserSpotifyToken();

        if (spotifyToken == null)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ERROR_NOT_AUTHENTICATED);
        }

        spotifyController.checkSpotifyAuthenticationStatus(spotifyToken);
        CurrentlyPlayingContext currentlyPlayingContext;

        try
        {
            currentlyPlayingContext = spotifyController.currentlyPlayingContext(spotifyToken);
        } catch (IOException | ParseException | SpotifyWebApiException e)
        {
            logger.warn("currentSong(): An error occurred: ", e);
            return ResponseEntity.internalServerError().build();
        }

        if (currentlyPlayingContext == null)
        {
            return ResponseEntity.status(HttpStatus.UPGRADE_REQUIRED).build();
        }

        currentUserSession.getUserRoom().setCurrentlyPlaying(currentlyPlayingContext.getIs_playing());
        return ResponseEntity.ok(buildCurrentSongContextJSON(currentlyPlayingContext, userSession.get()).toString());
    }


    /**
     * Retrieves a list of available devices associated with the user's authenticated Spotify account.
     * This method is accessed via HTTP GET at the path "/devices".
     *
     * @param request The HttpServletRequest object representing the incoming HTTP request.
     * @return ResponseEntity containing a String representing a JSON array of available devices.
     * The response may include one of the following HTTP statuses:
     * - HttpStatus.OK (200) if the list of devices is successfully retrieved and authenticated with Spotify.
     * - HttpStatus.INTERNAL_SERVER_ERROR (500) if an internal error occurs during the retrieval of devices.
     * - HttpStatus.NOT_FOUND (404) if the user's session is not found or is invalid.
     * @see SpotifyController#checkSpotifyAuthenticationStatus(SpotifyToken)
     * @see SpotifyController#getDevices(SpotifyToken)
     */
    @GetMapping(path = "/devices")
    public ResponseEntity<String> getDevices(HttpServletRequest request)
    {
        HttpSession session = request.getSession();
        String userIdentifier = (String) session.getAttribute(SESSION_ATTRIBUTE);
        UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);

        if (userSession == null)
        {
            return ResponseEntity.notFound().build();
        }

        SpotifyToken spotifyToken = userSession.getUserSpotifyToken();

        if (spotifyToken == null)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ERROR_NOT_AUTHENTICATED);
        }

        spotifyController.checkSpotifyAuthenticationStatus(spotifyToken);
        Device[] devices;

        try
        {
            devices = spotifyController.getDevices(spotifyToken);
        } catch (IOException | ParseException | SpotifyWebApiException e)
        {
            logger.warn("getDevices(): Error retrieving devices.", e);
            return ResponseEntity.internalServerError().build();
        }

        String devicesJson = valueAsString(devices);
        return ResponseEntity.status(HttpStatus.OK).body(devicesJson);
    }

    /**
     * Forces the specified device to start playing music on the user's authenticated Spotify account.
     * This method is accessed via HTTP GET at the path "/force-play".
     *
     * @param request  The HttpServletRequest object representing the incoming HTTP request.
     * @param deviceId The unique identifier of the device to be forced to play music on.
     * @return ResponseEntity containing a Boolean indicating the success of forcing the device to play.
     * The response may include one of the following HTTP statuses:
     * - HttpStatus.OK (200) if the device is successfully forced to play music and authenticated with Spotify.
     * - HttpStatus.NOT_FOUND (404) if the user's session is not found or is invalid.
     * - HttpStatus.INTERNAL_SERVER_ERROR (500) if an internal error occurs during the process of forcing the device to play.
     * @see SpotifyController#checkSpotifyAuthenticationStatus(SpotifyToken)
     * @see SpotifyController#forceDeviceToPlay(SpotifyToken, String)
     */
    @GetMapping(path = "/force-play")
    public ResponseEntity<Boolean> forceDeviceToPlay(HttpServletRequest request, @RequestParam("deviceId") String deviceId)
    {
        HttpSession session = request.getSession();
        String userIdentifier = (String) session.getAttribute(SESSION_ATTRIBUTE);
        UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);

        if (userSession == null)
        {
            return ResponseEntity.notFound().build();
        }

        spotifyController.checkSpotifyAuthenticationStatus(userSession.getUserSpotifyToken());

        try
        {
            spotifyController.forceDeviceToPlay(userSession.getUserSpotifyToken(), deviceId);
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (IOException | ParseException | SpotifyWebApiException e)
        {
            logger.warn("forceDeviceToPlay(): Error forcing device to play.", e);
            return ResponseEntity.internalServerError().build();
        }

    }

    /**
     * Toggles the playing status of the current song for the specified room identified by 'roomIdentifier'.
     * This method is accessed via HTTP GET at the path "/toggle-playing-status".
     *
     * @param request        The HttpServletRequest object representing the incoming HTTP request.
     * @param roomIdentifier The unique identifier for the room to toggle the playing status of the current song.
     * @return ResponseEntity containing a String representing the result of toggling the playing status.
     * The response may include one of the following HTTP statuses:
     * - HttpStatus.OK (200) if the playing status is successfully toggled and authenticated with Spotify.
     * - HttpStatus.INTERNAL_SERVER_ERROR (500) if an internal error occurs during the process of toggling the playing status.
     * - HttpStatus.NOT_FOUND (404) if the user is not authenticated with Spotify.
     * - HttpStatus.NOT_ACCEPTABLE (406) if the user does not have the privilege to toggle the playing status.
     * - HttpStatus.BAD_REQUEST (400) if the specified room does not exist.
     * @see DataManagement#getMatchingUserSession(String)
     * @see SpotifyController#checkSpotifyAuthenticationStatus(SpotifyToken)
     * @see SpotifyController#pauseCurrentlyPlayingSong(SpotifyToken)
     * @see SpotifyController#resumeCurrentlyPausedSong(SpotifyToken)
     * @see #hasHostPrivileges(UserSession, String)
     */
    @RequestMapping(path = "/toggle-playing-status")
    public ResponseEntity<String> togglePlayingStatus(HttpServletRequest request, @RequestParam("code") String roomIdentifier)
    {
        HttpSession session = request.getSession();
        String userIdentifier = (String) session.getAttribute(SESSION_ATTRIBUTE);
        Optional<UserSession> userSession = DataManagement.getMatchingUserSession(roomIdentifier);

        if (userSession.isEmpty())
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        SpotifyToken spotifyToken = userSession.get().getUserSpotifyToken();

        if (spotifyToken == null)
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ERROR_NOT_AUTHENTICATED);
        }

        spotifyController.checkSpotifyAuthenticationStatus(spotifyToken);
        Room userRoom = userSession.get().getUserRoom();

        if (!userRoom.isGuestCanPause() && !hasHostPrivileges(userSession.get(), userIdentifier))
        {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
        }

        if (userRoom.isCurrentlyPlaying())
        {

            try
            {
                spotifyController.pauseCurrentlyPlayingSong(spotifyToken);
                return ResponseEntity.status(HttpStatus.OK).build();
            } catch (ParseException | SpotifyWebApiException | IOException e)
            {
                logger.warn("togglePlayingStatus(): Error pause currently playing song.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

        }
        else
        {

            try
            {
                spotifyController.resumeCurrentlyPausedSong(spotifyToken);
                return ResponseEntity.status(HttpStatus.OK).build();
            } catch (ParseException | SpotifyWebApiException | IOException e)
            {
                logger.warn("togglePlayingStatus(): Error resuming currently paused song.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

        }
    }


    /**
     * Skips the currently playing song in the specified room identified by 'roomIdentifier'.
     * This method is accessed via HTTP GET at the path "/skip-song".
     *
     * @param request        The HttpServletRequest object representing the incoming HTTP request.
     * @param roomIdentifier The unique identifier for the room to skip the currently playing song.
     * @return ResponseEntity containing a String representing the result of skipping the song or a message indicating a vote cast.
     * The response may include one of the following HTTP statuses:
     * - HttpStatus.OK (200) if the song is successfully skipped and authenticated with Spotify.
     * - HttpStatus.INTERNAL_SERVER_ERROR (500) if an internal error occurs during the process of skipping the song.
     * - HttpStatus.NOT_FOUND (404) if the user is not authenticated with Spotify or the specified room does not exist.
     * - HttpStatus.NOT_ACCEPTABLE (406) if the user has already cast a vote to skip the song.
     * @see DataManagement#getMatchingUserSession(String)
     * @see SpotifyController#checkSpotifyAuthenticationStatus(SpotifyToken)
     * @see SpotifyController#skipCurrentlyPlayingSong(SpotifyToken)
     * @see #hasHostPrivileges(UserSession, String)
     * @see Vote
     */
    @GetMapping(path = "skip-song")
    public ResponseEntity<String> skipSong(HttpServletRequest request, @RequestParam("code") String roomIdentifier)
    {
        HttpSession session = request.getSession();
        String userIdentifier = (String) session.getAttribute(SESSION_ATTRIBUTE);
        Optional<UserSession> userSession = DataManagement.getMatchingUserSession(roomIdentifier);

        if (userSession.isEmpty())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        SpotifyToken spotifyToken = userSession.get().getUserSpotifyToken();
        spotifyController.checkSpotifyAuthenticationStatus(spotifyToken);
        UserSession currentUserSession = userSession.get();

        if (spotifyToken.getAuthorizationCodeCredentials() == null)
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }


        if (!hasHostPrivileges(currentUserSession, userIdentifier) && !currentUserSession.getUserRoom().hasUserAlreadyVoted(userIdentifier))
        {
            currentUserSession.getUserRoom().getVoteList().add(new Vote(userIdentifier, currentUserSession.getUserRoom().getCurrentSong()));
            if (currentUserSession.getUserRoom().getVoteListLength() < currentUserSession.getUserRoom().getVotesToSkip())
            {
                return ResponseEntity.ok(createSimpleJsonMessage("voted"));
            }
            else
            {
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("");
            }
        }


        if (hasHostPrivileges(currentUserSession, userIdentifier) || currentUserSession.getUserRoom().getVoteListLength() >= currentUserSession.getUserRoom().getVotesToSkip())
        {
            try
            {
                spotifyController.skipCurrentlyPlayingSong(currentUserSession.getUserSpotifyToken());
                currentUserSession.getUserRoom().clearVoteList();
                return ResponseEntity.ok().body(createSimpleJsonMessage("skipped"));
            } catch (ParseException | SpotifyWebApiException | IOException e)
            {
                logger.warn("skipSong(): Error skipping currently playing song.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("");
            }
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Rolls back to the previous song in the playlist for the specified room identified by 'roomIdentifier'.
     * This method is accessed via HTTP GET at the path "/rollback-song".
     *
     * @param request        The HttpServletRequest object representing the incoming HTTP request.
     * @param roomIdentifier The unique identifier for the room to roll back to the previous song.
     * @return ResponseEntity containing a String representing the result of the rollback or an error message.
     * The response may include one of the following HTTP statuses:
     * - HttpStatus.OK (200) if the rollback to the previous song is successful and authenticated with Spotify.
     * - HttpStatus.INTERNAL_SERVER_ERROR (500) if an internal error occurs during the process of the rollback.
     * - HttpStatus.NOT_FOUND (404) if the user is not authenticated with Spotify or the specified room does not exist.
     * - HttpStatus.NOT_ACCEPTABLE (406) if the user does not have the privilege to perform the rollback.
     * @see DataManagement#getMatchingUserSession(String)
     * @see SpotifyController#checkSpotifyAuthenticationStatus(SpotifyToken)
     * @see SpotifyController#rollBackToPreviousSong(SpotifyToken)
     * @see #hasHostPrivileges(UserSession, String)
     */
    @GetMapping(path = "rollback-song")
    public ResponseEntity<String> rollBack(HttpServletRequest request, @RequestParam("code") String roomIdentifier)
    {
        HttpSession session = request.getSession();
        String userIdentifier = (String) session.getAttribute(SESSION_ATTRIBUTE);
        Optional<UserSession> userSession = DataManagement.getMatchingUserSession(roomIdentifier);

        if (userSession.isEmpty())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        SpotifyToken spotifyToken = userSession.get().getUserSpotifyToken();
        spotifyController.checkSpotifyAuthenticationStatus(spotifyToken);
        UserSession currentUserSession = userSession.get();

        if (spotifyToken.getAuthorizationCodeCredentials() == null)
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (hasHostPrivileges(currentUserSession, userIdentifier))
        {

            try
            {
                spotifyController.rollBackToPreviousSong(spotifyToken);
                return ResponseEntity.ok().body(createSimpleJsonMessage("rollback"));
            } catch (IOException | ParseException | SpotifyWebApiException e)
            {
                logger.warn("rollBack() - Error rolling back to the previous song.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
        else
        {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
        }
    }


    /**
     * Searches for songs based on the specified query string and returns the search results in JSON format.
     * This method is accessed via HTTP POST at the path "/search-song".
     *
     * @param searchSongRequest The SearchSongRequest object containing the search query string and room identifier.
     * @return ResponseEntity containing a String representing the search results in JSON format.
     * The response may include one of the following HTTP statuses:
     * - HttpStatus.OK (200) if the search is successful, and the response body contains the search results in JSON format.
     * - HttpStatus.NOT_FOUND (404) if the user's session is not found or is invalid, or the specified room does not exist.
     * @see SearchSongRequest
     * @see DataManagement#getMatchingUserSession(String)
     * @see SpotifyController#checkSpotifyAuthenticationStatus(SpotifyToken)
     * @see SpotifyController#searchSong(SpotifyToken, String)
     * @see Track
     */
    @PostMapping(path = "/search-song")
    public ResponseEntity<String> searchSong(@RequestBody SearchSongRequest searchSongRequest)
    {
        Optional<UserSession> userSession = DataManagement.getMatchingUserSession(searchSongRequest.getRoomIdentifier());

        if (userSession.isEmpty())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        SpotifyToken spotifyToken = userSession.get().getUserSpotifyToken();
        spotifyController.checkSpotifyAuthenticationStatus(spotifyToken);

        try
        {
            Track[] searchResult = spotifyController.searchSong(spotifyToken, searchSongRequest.getQueryString());

            List<SearchSongResult> searchResults = Arrays.stream(searchResult)
                    .map(result -> new SearchSongResult(
                            result.getArtists()[0].getName(),
                            result.getName(),
                            result.getAlbum().getImages()[2].getUrl(),
                            result.getUri()
                    ))
                    .toList();

            return ResponseEntity.status(HttpStatus.OK).body(valueAsString(searchResults));
        } catch (IOException | ParseException | SpotifyWebApiException e)
        {
            logger.warn("searchSong() - Error searching song.");
            return ResponseEntity.internalServerError().build();
        }
    }


    /**
     * Adds a track to the playback queue for the specified room identified by 'addTrackToPlaybackRequest.getRoomIdentifier()'.
     * This method is accessed via HTTP POST at the path "/add-track-to-playback".
     *
     * @param addTrackToPlaybackRequest The AddTrackToPlaybackRequest object containing the room identifier and track URI to add.
     * @return ResponseEntity containing a Boolean indicating the success of adding the track to the playback queue.
     * The response may include one of the following HTTP statuses:
     * - HttpStatus.OK (200) if the track is successfully added to the playback queue and authenticated with Spotify.
     * - HttpStatus.NOT_FOUND (404) if the user's session is not found or is invalid, or the specified room does not exist.
     * @see AddTrackToPlaybackRequest
     * @see DataManagement#getMatchingUserSession(String)
     * @see SpotifyController#checkSpotifyAuthenticationStatus(SpotifyToken)
     * @see SpotifyController#addTrackToPlayBack(SpotifyToken, String)
     */
    @PostMapping(path = "add-track-to-playback")
    public ResponseEntity<Boolean> putSongInPlaybackQueue(@RequestBody AddTrackToPlaybackRequest addTrackToPlaybackRequest)
    {
        Optional<UserSession> userSession = DataManagement.getMatchingUserSession(addTrackToPlaybackRequest.getRoomIdentifier());

        if (userSession.isEmpty())
        {
            logger.warn("Method: putSongInPlaybackQueue() - User session not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        SpotifyToken spotifyToken = userSession.get().getUserSpotifyToken();
        spotifyController.checkSpotifyAuthenticationStatus(spotifyToken);

        try
        {
            spotifyController.addTrackToPlayBack(spotifyToken, addTrackToPlaybackRequest.getTrackHref());
            return ResponseEntity.status(HttpStatus.OK).body(true);
        } catch (IOException | ParseException | SpotifyWebApiException e)
        {
            return ResponseEntity.internalServerError().build();
        }
    }

    //################
    //HELPER FUNCTIONS
    //################

    private String createSimpleJsonMessage(String message)
    {
        return "{\"message\": \"" + message + "\"}";
    }

    private SongContext buildCurrentSongContextJSON(CurrentlyPlayingContext currentlyPlayingContext, UserSession userSession)
    {
        Track track = (Track) currentlyPlayingContext.getItem();
        int votesToSkip = userSession.getUserRoom().getVotesToSkip();
        int currentVotesToSkip = userSession.getUserRoom().getVoteList().size();

        return new SongContext(
                track.getName(),
                track.getArtists()[0].getName(),
                track.getDurationMs(),
                currentlyPlayingContext.getProgress_ms(),
                track.getAlbum().getImages()[0].getUrl(),
                currentlyPlayingContext.getIs_playing(),
                currentVotesToSkip,
                votesToSkip
        );
    }

    private boolean hasHostPrivileges(UserSession userSession, String userIdentifier)
    {
        UserSession currentSession = DataManagement.userSessionCache.get(userIdentifier);
        return userSession.equals(currentSession);
    }

    String valueAsString(Object value)
    {
        try
        {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e)
        {
            logger.warn("valueAsString(): Error parsing  devices.", e);
            return "";
        }
    }
}








