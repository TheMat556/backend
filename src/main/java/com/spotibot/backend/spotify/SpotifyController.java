package com.spotibot.backend.spotify;

import com.spotibot.backend.Credentials;
import com.spotibot.backend.DataManagement;
import com.spotibot.backend.UserSession;
import jakarta.annotation.Nullable;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import se.michaelthelin.spotify.requests.data.artists.GetArtistRequest;
import se.michaelthelin.spotify.requests.data.player.GetInformationAboutUsersCurrentPlaybackRequest;
import se.michaelthelin.spotify.requests.data.player.PauseUsersPlaybackRequest;
import se.michaelthelin.spotify.requests.data.player.SkipUsersPlaybackToNextTrackRequest;
import se.michaelthelin.spotify.requests.data.player.StartResumeUsersPlaybackRequest;

import java.io.IOException;
import java.net.URI;


public class SpotifyController {

    private static URI REDIRECT_URI;
    private static SpotifyApi spotifyApi;
    private static AuthorizationCodeRefreshRequest authorizationCodeRefreshRequest;
    private static AuthorizationCodeRequest authorizationCodeRequest;

    AuthorizationCodeCredentials authorizationCodeCredentials;
    GetInformationAboutUsersCurrentPlaybackRequest getInformationAboutUsersCurrentPlaybackRequest;
    PauseUsersPlaybackRequest pauseUsersPlaybackRequest;
    StartResumeUsersPlaybackRequest startResumeUsersPlayback;
    SkipUsersPlaybackToNextTrackRequest skipUsersPlaybackToNextTrackRequest;
    GetArtistRequest getArtistRequest;

    SpotifyController() {
        REDIRECT_URI = SpotifyHttpManager.makeUri(Credentials.apiUri);
        spotifyApi = new SpotifyApi.Builder().setClientId(Credentials.CLIENT_ID).setClientSecret(Credentials.CLIENT_SECRET).setRedirectUri(REDIRECT_URI).build();
        authorizationCodeRefreshRequest = spotifyApi.authorizationCodeRefresh().build();
    }

    public AuthorizationCodeUriRequest authorizationCodeUriRequest () {
        return spotifyApi.authorizationCodeUri().scope(Credentials.scopes).show_dialog(true).build();
    }

    @Nullable
    public String authorizationCodeRequest(String userIdentifier, String userCode) {
        authorizationCodeRequest = spotifyApi.authorizationCode(userCode).build();
        try {
            authorizationCodeCredentials = authorizationCodeRequest.execute();
            spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
            spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());
            System.out.println("Expires in: " + authorizationCodeCredentials.getExpiresIn());

            updateOrCreateToken(userIdentifier, authorizationCodeCredentials);

            return "<html><head><script>window.close();</script></head><body>Closing tab...</body></html>";
        } catch (IOException | SpotifyWebApiException | org.apache.hc.core5.http.ParseException e) {
            System.out.println("Error" + e.getMessage());
            return null;
        }
    }

    public CurrentlyPlayingContext currentlyPlayingContext(SpotifyToken spotifyToken) {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        getInformationAboutUsersCurrentPlaybackRequest = spotifyApi.getInformationAboutUsersCurrentPlayback().build();

        try
        {
            return getInformationAboutUsersCurrentPlaybackRequest.execute();
        }
        catch (IOException | ParseException | SpotifyWebApiException e)
        {
            return null;
        }
    }

    public boolean pauseCurrentlyPlayingSong(SpotifyToken spotifyToken) {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        pauseUsersPlaybackRequest = spotifyApi.pauseUsersPlayback().build();
        try
        {
            pauseUsersPlaybackRequest.execute();
            return true;
        }
        catch (ParseException | SpotifyWebApiException | IOException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public boolean resumeCurrentlyPausedSong(SpotifyToken spotifyToken) {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        startResumeUsersPlayback = spotifyApi.startResumeUsersPlayback().build();
        try
        {
            startResumeUsersPlayback.execute();
            return true;
        }
        catch (ParseException | SpotifyWebApiException | IOException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public boolean skipCurrentlyPlayingSong(SpotifyToken spotifyToken) {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        skipUsersPlaybackToNextTrackRequest = spotifyApi.skipUsersPlaybackToNextTrack().build();

        try
        {
            skipUsersPlaybackToNextTrackRequest.execute();
            return true;
        }
        catch (ParseException | SpotifyWebApiException | IOException e)
        {
            e.printStackTrace();
            return false;
        }
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

    public String getArtistName(String artistId) {
        getArtistRequest = spotifyApi.getArtist(artistId).build();

        try
        {
            final Artist artist = getArtistRequest.execute();
            return artist.getName();
        }
        catch (ParseException | SpotifyWebApiException | IOException e)
        {
            e.printStackTrace();
            return null;
        }
    }

}