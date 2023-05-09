package com.spotibot.backend;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Room {
	private String roomIdentifier;
	private boolean hasHostPrivileges;
	private boolean guestCanPause;
	private int votesToSkip;
	private LocalDateTime createdAt;
	private String currentSong;
	private ArrayList<Vote> voteList = new ArrayList<Vote>();
	private RandomStringGenerator randomStringGenerator = new RandomStringGenerator();

	public Room(
			String roomIdentifier,
			boolean hasHostPrivileges,
			boolean guestCanPause,
			int votesToSkip
			) {
		this.roomIdentifier = roomIdentifier;
		this.hasHostPrivileges = hasHostPrivileges;
		this.guestCanPause = guestCanPause;
		this.votesToSkip = votesToSkip;
		this.setCreatedAt(LocalDateTime.now());
	}

	/**
	 Removes all elements from the voteList.
	 */
	public void clearVoteList() { voteList.clear(); }

	/**
	 Adds the given vote to the voteList.
	 @param vote the vote to be added to the voteList
	 */
	public void addVote(Vote vote) { voteList.add(vote); }

	/**
	 Returns the length of the vote list.
	 @return the length of the vote list
	 */
	public Integer getVoteListLength() {
		return voteList.size();
	}


}
