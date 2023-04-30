package com.spotibot.backend;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.security.SecurityProperties.User;

@Getter
@Setter
public class Vote {
	private User user;
	private LocalDateTime createdAt;
	private String songId;
	
	Vote(User user, String songId){
		setUser(user);
		setCreatedAt(LocalDateTime.now());
		setSongId(songId);
	}
}
