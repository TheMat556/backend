package com.spotibot.backend;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Random;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {
    private Room userRoom;
    private SpotifyToken userSpotifyToken;
}
