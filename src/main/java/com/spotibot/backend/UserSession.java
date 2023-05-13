package com.spotibot.backend;

import com.spotibot.backend.room.Room;
import com.spotibot.backend.spotify.SpotifyToken;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {
    private Room userRoom;
    private SpotifyToken userSpotifyToken;
}
