package com.spotibot.backend;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SearchSongResult {
    String artistName;
    String songName;
    String pictureURI;
    String songHref;
}
