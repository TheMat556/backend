package com.spotibot.backend.spotify;

import com.spotibot.backend.Credentials;
import com.spotibot.backend.UserSession;
import jakarta.annotation.Nullable;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.model_objects.miscellaneous.Device;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import se.michaelthelin.spotify.requests.data.player.*;
import se.michaelthelin.spotify.requests.data.search.simplified.SearchTracksRequest;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;


public class SpotifyController {
    SpotifyApi spotifyApi;
    AuthorizationCodeRefreshRequest authorizationCodeRefreshRequest;
    AuthorizationCodeRequest authorizationCodeRequest;

    URI redirectionUri;
    AuthorizationCodeCredentials authorizationCodeCredentials;
    GetInformationAboutUsersCurrentPlaybackRequest getInformationAboutUsersCurrentPlaybackRequest;
    PauseUsersPlaybackRequest pauseUsersPlaybackRequest;
    StartResumeUsersPlaybackRequest startResumeUsersPlayback;
    SkipUsersPlaybackToNextTrackRequest skipUsersPlaybackToNextTrackRequest;
    SearchTracksRequest searchTracksRequest;

    SpotifyController()
    {
        redirectionUri = SpotifyHttpManager.makeUri(Credentials.apiUri);
        spotifyApi = new SpotifyApi.Builder().setClientId(Credentials.CLIENT_ID).setClientSecret(Credentials.CLIENT_SECRET).setRedirectUri(redirectionUri).build();
        authorizationCodeRefreshRequest = spotifyApi.authorizationCodeRefresh().build();
    }

    public AuthorizationCodeUriRequest authorizationCodeUriRequest()
    {
        return spotifyApi.authorizationCodeUri().scope(Credentials.scopes).show_dialog(true).build();
    }

    @Nullable
    public String authorizationCodeRequest(UserSession userSession, String userCode) throws IOException, ParseException, SpotifyWebApiException
    {
        authorizationCodeRequest = spotifyApi.authorizationCode(userCode).build();

        authorizationCodeCredentials = authorizationCodeRequest.execute();

        SpotifyToken spotifyToken = new SpotifyToken();
        spotifyToken.setAuthorizationCodeCredentials(authorizationCodeCredentials);
        spotifyToken.setExpiresIn(System.currentTimeMillis() / 1000 + authorizationCodeCredentials.getExpiresIn());

        userSession.setUserSpotifyToken(spotifyToken);

        return "<html><head><script>window.close();</script></head><body>Closing tab...</body></html>";

    }

    public CurrentlyPlayingContext currentlyPlayingContext(SpotifyToken spotifyToken) throws IOException, ParseException, SpotifyWebApiException
    {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        getInformationAboutUsersCurrentPlaybackRequest = spotifyApi.getInformationAboutUsersCurrentPlayback().build();


        return getInformationAboutUsersCurrentPlaybackRequest.execute();
    }

    public boolean refreshSpotifyToken(SpotifyToken spotifyToken)
    {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        authorizationCodeRefreshRequest = spotifyApi.authorizationCodeRefresh().refresh_token(spotifyToken.getRefreshToken()).build();

        try
        {
            authorizationCodeCredentials = authorizationCodeRefreshRequest.execute();
            var authorizationCodeCredentialsBuilder = new AuthorizationCodeCredentials.Builder();

            authorizationCodeCredentialsBuilder.setRefreshToken(spotifyToken.getRefreshToken());
            authorizationCodeCredentialsBuilder.setAccessToken(authorizationCodeCredentials.getAccessToken());
            authorizationCodeCredentialsBuilder.setTokenType(spotifyToken.getTokenType());
            authorizationCodeCredentialsBuilder.setExpiresIn(authorizationCodeCredentials.getExpiresIn());
            authorizationCodeCredentialsBuilder.setScope(spotifyToken.getScope());

            AuthorizationCodeCredentials refreshedAuthorizationCodeCredentials = authorizationCodeCredentialsBuilder.build();

            spotifyToken.setAuthorizationCodeCredentials(refreshedAuthorizationCodeCredentials);
            spotifyToken.setExpiresIn(refreshedAuthorizationCodeCredentials.getExpiresIn());
            return true;
        } catch (IOException | ParseException | SpotifyWebApiException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public Device[] getDevices(SpotifyToken spotifyToken) throws IOException, ParseException, SpotifyWebApiException
    {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        GetUsersAvailableDevicesRequest getUsersAvailableDevicesRequest = spotifyApi.getUsersAvailableDevices().build();

        return getUsersAvailableDevicesRequest.execute();
    }

    public String forceDeviceToPlay(SpotifyToken spotifyToken, String deviceId) throws IOException, ParseException, SpotifyWebApiException
    {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        StartResumeUsersPlaybackRequest startResumeUsersPlaybackRequest = spotifyApi.startResumeUsersPlayback().device_id(deviceId).build();
        return startResumeUsersPlaybackRequest.execute();
    }

    public void pauseCurrentlyPlayingSong(SpotifyToken spotifyToken) throws IOException, ParseException, SpotifyWebApiException
    {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        pauseUsersPlaybackRequest = spotifyApi.pauseUsersPlayback().build();
        pauseUsersPlaybackRequest.execute();
    }

    public void resumeCurrentlyPausedSong(SpotifyToken spotifyToken) throws IOException, ParseException, SpotifyWebApiException
    {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        startResumeUsersPlayback = spotifyApi.startResumeUsersPlayback().build();
        startResumeUsersPlayback.execute();
    }

    public void skipCurrentlyPlayingSong(SpotifyToken spotifyToken) throws IOException, ParseException, SpotifyWebApiException
    {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        skipUsersPlaybackToNextTrackRequest = spotifyApi.skipUsersPlaybackToNextTrack().build();
        skipUsersPlaybackToNextTrackRequest.execute();
    }

    public void rollBackToPreviousSong(SpotifyToken spotifyToken) throws IOException, ParseException, SpotifyWebApiException
    {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        SkipUsersPlaybackToPreviousTrackRequest skipUsersPlaybackToPreviousTrackRequest = spotifyApi.skipUsersPlaybackToPreviousTrack().build();
        skipUsersPlaybackToPreviousTrackRequest.execute();
    }

    public Track[] searchSong(SpotifyToken spotifyToken, String queryString) throws IOException, ParseException, SpotifyWebApiException
    {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        searchTracksRequest = spotifyApi.searchTracks(queryString).build();

        var result = searchTracksRequest.execute();
        return Arrays.copyOfRange(result.getItems(), 0, 5);
    }

    public void addTrackToPlayBack(SpotifyToken spotifyToken, String songHref) throws IOException, ParseException, SpotifyWebApiException
    {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        AddItemToUsersPlaybackQueueRequest addItemToUsersPlaybackQueueRequest = spotifyApi.addItemToUsersPlaybackQueue(songHref).build();
        addItemToUsersPlaybackQueueRequest.execute();
    }

    public Boolean checkSpotifyAuthenticationStatus(SpotifyToken spotifyToken)
    {
        if (spotifyToken.getExpiresIn() <= (System.currentTimeMillis() / 1000 + 10))
        {
            return refreshSpotifyToken(spotifyToken);
        }

        return true;
    }


}
