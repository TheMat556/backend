package com.spotibot.backend;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.security.SecurityProperties.User;

@Getter
@Setter
public class Vote {
	private String userIdentifier;
	private LocalDateTime createdAt;
	private String songId;
	
	public Vote(String userIdentifier, String songId){
		this.userIdentifier = userIdentifier;
		createdAt = LocalDateTime.now();
		this.songId = songId;
	}
}
