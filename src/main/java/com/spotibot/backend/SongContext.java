package com.spotibot.backend;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SongContext {
    private String songTitle;
    private String artist;
    private long songDuration;
    private long currentProgress;
    private String currentImgUrl;
    private boolean playingStatus;
    private int currentVotes;
    private int neededVotesToSkip;
}
