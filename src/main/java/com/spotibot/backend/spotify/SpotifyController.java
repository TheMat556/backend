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
import se.michaelthelin.spotify.model_objects.miscellaneous.Device;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import se.michaelthelin.spotify.requests.data.artists.GetArtistRequest;
import se.michaelthelin.spotify.requests.data.player.*;
import se.michaelthelin.spotify.requests.data.search.simplified.SearchTracksRequest;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;


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
    SearchTracksRequest searchTracksRequest;

    SpotifyController() {
        REDIRECT_URI = SpotifyHttpManager.makeUri(Credentials.apiUri);
        spotifyApi = new SpotifyApi.Builder().setClientId(Credentials.CLIENT_ID).setClientSecret(Credentials.CLIENT_SECRET).setRedirectUri(REDIRECT_URI).build();
        authorizationCodeRefreshRequest = spotifyApi.authorizationCodeRefresh().build();
    }

    public AuthorizationCodeUriRequest authorizationCodeUriRequest() {
        return spotifyApi.authorizationCodeUri().scope(Credentials.scopes).show_dialog(true).build();
    }

    @Nullable
    public String authorizationCodeRequest(UserSession userSession, String userCode) {
        authorizationCodeRequest = spotifyApi.authorizationCode(userCode).build();
        try
        {
            authorizationCodeCredentials = authorizationCodeRequest.execute();
            //TODO: Update or create insertion!!!

            SpotifyToken spotifyToken = new SpotifyToken();
            spotifyToken.setAuthorizationCodeCredentials(authorizationCodeCredentials);
            spotifyToken.setExpiresIn(System.currentTimeMillis() / 1000 + authorizationCodeCredentials.getExpiresIn());

            userSession.setUserSpotifyToken(spotifyToken);

            return "<html><head><script>window.close();</script></head><body>Closing tab...</body></html>";
        } catch (IOException | SpotifyWebApiException | org.apache.hc.core5.http.ParseException e)
        {
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
        } catch (IOException | ParseException | SpotifyWebApiException e)
        {
            return null;
        }
    }

    public boolean refreshSpotifyToken(SpotifyToken spotifyToken) {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        authorizationCodeRefreshRequest = spotifyApi
                .authorizationCodeRefresh()
                .refresh_token(spotifyToken.getRefreshToken())
                .build();

        try
        {
            AuthorizationCodeCredentials authorizationCodeCredentials = authorizationCodeRefreshRequest.execute();
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

    public Device[] getDevices(SpotifyToken spotifyToken) {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        GetUsersAvailableDevicesRequest getUsersAvailableDevicesRequest = spotifyApi.getUsersAvailableDevices().build();

        try
        {
            return getUsersAvailableDevicesRequest.execute();
        } catch (IOException | ParseException | SpotifyWebApiException e)
        {
            return null;
        }
    }

    public String forceDeviceToPlay(SpotifyToken spotifyToken, String deviceId) {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        StartResumeUsersPlaybackRequest startResumeUsersPlaybackRequest = spotifyApi.startResumeUsersPlayback().device_id(deviceId).build();

        try
        {
            return startResumeUsersPlaybackRequest.execute();
        } catch (IOException | ParseException | SpotifyWebApiException e)
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
        } catch (ParseException | SpotifyWebApiException | IOException e)
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
        } catch (ParseException | SpotifyWebApiException | IOException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public Boolean checkSpotifyAuthenticationStatus(SpotifyToken spotifyToken) {
        if (spotifyToken.getExpiresIn() <= (System.currentTimeMillis() / 1000 + 10))
        {
            if (refreshSpotifyToken(spotifyToken))
            {
                return true;
            } else
            {
                return false;
            }
        }

        return true;
    }

    public boolean skipCurrentlyPlayingSong(SpotifyToken spotifyToken) {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        skipUsersPlaybackToNextTrackRequest = spotifyApi.skipUsersPlaybackToNextTrack().build();

        try
        {
            skipUsersPlaybackToNextTrackRequest.execute();
            return true;
        } catch (ParseException | SpotifyWebApiException | IOException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public boolean rollBackToPreviousSong(SpotifyToken spotifyToken) {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        SkipUsersPlaybackToPreviousTrackRequest skipUsersPlaybackToPreviousTrackRequest = spotifyApi.skipUsersPlaybackToPreviousTrack().build();

        try
        {
            skipUsersPlaybackToPreviousTrackRequest.execute();
            return true;
        } catch (IOException | ParseException | SpotifyWebApiException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public Track[] searchSong(SpotifyToken spotifyToken, String queryString) {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        searchTracksRequest = spotifyApi.searchTracks(queryString).build();

        try
        {
            var result = searchTracksRequest.execute();
            return (Track[]) Arrays.copyOfRange(result.getItems(), 0, 5);
        } catch (IOException | ParseException | SpotifyWebApiException e)
        {
            throw new RuntimeException(e);
        }
    }

    public Boolean addTrackToPlayBack(SpotifyToken spotifyToken, String songHref) {
        spotifyApi.setAccessToken(spotifyToken.getAccessToken());
        spotifyApi.setRefreshToken(spotifyToken.getRefreshToken());

        AddItemToUsersPlaybackQueueRequest addItemToUsersPlaybackQueueRequest = spotifyApi.addItemToUsersPlaybackQueue(songHref).build();

        try
        {
            addItemToUsersPlaybackQueueRequest.execute();
            return true;
        } catch (IOException | ParseException | SpotifyWebApiException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates or creates a new Spotify access token for the user identified by {@code userIdentifier}.
     *
     * @param userIdentifier the identifier of the user
     * @param acc            the new authorization code credentials to create or update the token
     */
    public void updateOrCreateToken(String userIdentifier, AuthorizationCodeCredentials acc) {
        UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);
        SpotifyToken currentUserToken = userSession.getUserSpotifyToken();

        if (currentUserToken == null)
        {
            SpotifyToken newUserToken = new SpotifyToken(acc);
            userSession.setUserSpotifyToken(newUserToken);
        } else
        {
            currentUserToken.setAuthorizationCodeCredentials(acc);
        }
        currentUserToken.setExpiresIn(System.currentTimeMillis() + 3600);
    }

    public String getArtistName(String artistId) {
        getArtistRequest = spotifyApi.getArtist(artistId).build();

        try
        {
            final Artist artist = getArtistRequest.execute();
            return artist.getName();
        } catch (ParseException | SpotifyWebApiException | IOException e)
        {
            e.printStackTrace();
            return null;
        }
    }

}
