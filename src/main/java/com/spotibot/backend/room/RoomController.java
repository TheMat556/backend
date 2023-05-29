package com.spotibot.backend.room;

import com.spotibot.backend.DataManagement;
import com.spotibot.backend.RandomStringGenerator;
import com.spotibot.backend.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;


@RestController
@RequestMapping("/room")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class RoomController {
	private final RandomStringGenerator randomStringGenerator = new RandomStringGenerator();

	/**
	 * Creates a new {@link Room} for music playback and returns it as a response entity. This method is asynchronous
	 * and returns a CompletableFuture to allow for non-blocking processing.
	 *
	 * @param session The HttpSession for the current request.
	 * @param createdRoom The Room object containing the details of the new room to be created.
	 * @return A CompletableFuture containing a ResponseEntity with either the newly created room or the user's current room.
	 */

	@PostMapping(path = "/create_room", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> createRoom(HttpServletRequest request, @RequestBody Room createdRoom) {
		HttpSession httpSession = request.getSession();
		String userIdentifier = (String) httpSession.getAttribute("userIdentifier");

		System.out.println(userIdentifier);

		//TODO: Have to check if a user already created a room!
		if(userIdentifier == null || userIdentifier.isEmpty()) {
			//TODO outsource this the creation of useridentifier
			String randomUserIdentifier = randomStringGenerator.generateRandomIdentifier(10);
			httpSession.setAttribute("userIdentifier", randomUserIdentifier);

			UserSession userSession = new UserSession();
			Room room = new Room(randomStringGenerator.generateRandomIdentifier(5), true, createdRoom.isGuestCanPause(), createdRoom.getVotesToSkip());
			userSession.setUserRoom(room);

			DataManagement.userSessionCache.put(randomUserIdentifier, userSession);

			return ResponseEntity.ok(room);
		} else {
			UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);

			return ResponseEntity.ok(userSession.getUserRoom());
		}
	}

	/**
	 * Retrieves the {@link Room} object associated with the given room identifier and returns it as a ResponseEntity.
	 * This method is asynchronous and returns a CompletableFuture to allow for non-blocking processing.
	 *
	 * @param request The HttpServletRequest for the current request.
	 * @param roomIdentifier The identifier of the room to retrieve.
	 * @return A CompletableFuture containing a ResponseEntity with either the requested Room object or a no-content response.
	 */
	@GetMapping(path = "/get_room")
	public CompletableFuture<ResponseEntity<Object>> getRoom(HttpServletRequest request, @RequestParam String roomIdentifier) {
		HttpSession session = request.getSession();
		String userIdentifier = (String) session.getAttribute("userIdentifier");

		if (roomIdentifier == null || roomIdentifier.isEmpty()) {
			if (userIdentifier == null || userIdentifier.isEmpty()) {
				return CompletableFuture.completedFuture(ResponseEntity.noContent().build());
			}
			return CompletableFuture.completedFuture(ResponseEntity.ok(DataManagement.userSessionCache.get(userIdentifier).getUserRoom()));
		} else {
			Optional<UserSession> matchingUserSession = DataManagement.getMatchingUserSession(roomIdentifier);

			if (matchingUserSession.isEmpty()) {
				return CompletableFuture.completedFuture(ResponseEntity.noContent().build());
			}

			return CompletableFuture.completedFuture(ResponseEntity.ok(matchingUserSession.get().getUserRoom()));
		}
	}

	/**
	 Removes the current user from the {@link Room} object associated with the specified room identifier in the user session cache.
	 Returns an empty {@link ResponseEntity} if no matching {@link Room} object is found.
	 @param request The {@link HttpServletRequest} object representing the current HTTP request.
	 @param roomIdentifier The room identifier of the {@link Room} object to leave.
	 @return A {@link ResponseEntity} object indicating whether the operation was successful, or an empty response if no matching {@link Room} object is found.
	 */
	@GetMapping(path = "/leave_room")
	public CompletableFuture<ResponseEntity<Object>> leaveRoom(HttpServletRequest request, @RequestParam String roomIdentifier) {
		HttpSession session = request.getSession();
		String userIdentifier = (String) session.getAttribute("userIdentifier");

		if (roomIdentifier == null || roomIdentifier.isEmpty() || userIdentifier == null || userIdentifier.isEmpty()) {
			return CompletableFuture.completedFuture(ResponseEntity.noContent().build());
		}

		Optional<UserSession> matchingUserSession = DataManagement.getMatchingUserSession(roomIdentifier);

		if (matchingUserSession.isEmpty()) {
			return CompletableFuture.completedFuture(ResponseEntity.noContent().build());
		}

		DataManagement.userSessionCache.remove(userIdentifier);
		return CompletableFuture.completedFuture(ResponseEntity.ok().build());
	}

}
