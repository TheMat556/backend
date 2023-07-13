package com.spotibot.backend;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddTrackToPlaybackRequest {
    private String roomIdentifier;
    private String trackHref;
}
