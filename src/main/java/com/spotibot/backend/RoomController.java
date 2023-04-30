package com.spotibot.backend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.security.SecurityProperties.User;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Random;


@RestController
@RequestMapping("/api")
public class RoomController {
	private RandomStringGenerator randomStringGenerator = new RandomStringGenerator();

	/**
	 This method handles the HTTP POST request to create a room.
	 The request path is "/create_room" and it consumes JSON data.
	 It checks if the user is identified and if not, generates a random user identifier
	 and stores it in the user's session. It creates a new room with the specified
	 parameters and puts it in a new UserSession object, which is then added to a
	 userSessionCache. If the user is already identified, it retrieves the UserSession
	 object from the userSessionCache and returns the room associated with the user.
	 @param session - the HttpSession object containing the user's session data
	 @param createdRoom - the Room object containing the room parameters specified in the request body
	 @return a ResponseEntity object containing the created room object or the room associated with the user
	 */
	@RequestMapping(path = "/create_room", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> createRoom(HttpSession session, @RequestBody Room createdRoom) {
		String userIdentifier = (String) session.getAttribute("userIdentifier");

		if(userIdentifier == "" || userIdentifier == null)
		{
			String randomUserIdentifier = randomStringGenerator.generateRandomIdentifier(10);
			session.setAttribute("userIdentifier", randomUserIdentifier);

			UserSession userSession = new UserSession();
			Room room = new Room(randomStringGenerator.generateRandomIdentifier(5),  true, createdRoom.isGuestCanPause(), createdRoom.getVotesToSkip());
			userSession.setUserRoom(room);

			DataManagement.userSessionCache.put(randomUserIdentifier, userSession);
			return ResponseEntity.ok(room);
		}
		else
		{
			UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);
			return ResponseEntity.ok(userSession.getUserRoom());
		}
	}

	/**
	 Retrieves a {@link Room} object associated with the given room identifier from the user session cache.
	 If no room identifier is specified, returns the {@link Room} object associated with the current user's session.
	 Returns an empty {@link ResponseEntity} if no matching {@link Room} object is found.
	 @param request The {@link HttpServletRequest} object representing the current HTTP request.
	 @param roomIdentifier The room identifier of the {@link Room} object to retrieve, or an empty string to retrieve the {@link Room} object associated with the current user's session.
	 @return A {@link ResponseEntity} object containing the matching {@link Room} object, or an empty response if no matching {@link Room} object is found.
	 */
	@RequestMapping(path = "/get_room", method = RequestMethod.GET)
	public ResponseEntity<Object> getRoom(HttpServletRequest request, @RequestParam String roomIdentifier) {
		HttpSession session = request.getSession();
		String userIdentifier = (String) session.getAttribute("userIdentifier");

		if (roomIdentifier == "" || roomIdentifier == null)
		{
			if (userIdentifier.equals(""))
			{
				 return ResponseEntity.noContent().build();
			}
			return ResponseEntity.ok(DataManagement.userSessionCache.get("userIdentifier"));
		}
		else
		{
			Optional<UserSession> matchingUserSession = getMatchingUserSession(roomIdentifier);

			if(matchingUserSession.isEmpty())
			{
				return ResponseEntity.noContent().build();
			}

			return ResponseEntity.ok(matchingUserSession);
		}
	}

	/**
	 Removes the current user from the {@link Room} object associated with the specified room identifier in the user session cache.
	 Returns an empty {@link ResponseEntity} if no matching {@link Room} object is found.
	 @param request The {@link HttpServletRequest} object representing the current HTTP request.
	 @param roomIdentifier The room identifier of the {@link Room} object to leave.
	 @return A {@link ResponseEntity} object indicating whether the operation was successful, or an empty response if no matching {@link Room} object is found.
	 */
	@RequestMapping(path = "/leave_room", method = RequestMethod.GET)
	public ResponseEntity<Object> leaveRoom(HttpServletRequest request, @RequestParam String roomIdentifier) {
		HttpSession session = request.getSession();
		User userSession = (User) session.getAttribute("userIdentifier");

		if (!(roomIdentifier == ""  || roomIdentifier == null))
		{
			Optional<UserSession> matchingUserSession = getMatchingUserSession(roomIdentifier);

			if(matchingUserSession.isEmpty())
			{
				return ResponseEntity.noContent().build();
			}

			return ResponseEntity.ok(matchingUserSession);
		}
		else
		{
			return ResponseEntity.noContent().build();
		}
	}
	
	//##############
	//HELPER METHODS
	//##############

	public Optional<UserSession> getMatchingUserSession (String roomIdentifier) {
		Optional<UserSession> matchingUserSession = DataManagement.userSessionCache
				.values()
				.stream()
				.filter(userSession -> userSession.getUserRoom().getRoomIdentifier().equals(roomIdentifier))
				.findFirst();

		return matchingUserSession;
	}

}
