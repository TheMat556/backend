package com.spotibot.backend.room;

import com.spotibot.backend.DataManagement;
import com.spotibot.backend.RandomStringGenerator;
import com.spotibot.backend.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;


@RestController
@RequestMapping("/room")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class RoomController {
    private final RandomStringGenerator randomStringGenerator = new RandomStringGenerator();

    /**
     * Creates a new room or updates an existing room with the provided Room details.
     * This method is accessed via HTTP POST at the path "/create_room".
     *
     * @param request      The HttpServletRequest object representing the incoming HTTP request.
     * @param createdRoom  The Room object containing the details of the room to be created or updated.
     * @return ResponseEntity containing an Object representing the created or updated Room object.
     *         The response may include one of the following HTTP statuses:
     *         - HttpStatus.OK (200) if the room is successfully created or updated.
     *         - HttpStatus.INTERNAL_SERVER_ERROR (500) if an internal error occurs during the process of creating or updating the room.
     *
     * @see Room
     * @see DataManagement#userSessionCache
     * @see UserSession
     * @see #checkOrCreateUserIdentifierInSession(HttpServletRequest)
     * @see RandomStringGenerator#generateRandomIdentifier(int)
     */
    @PostMapping(path = "/create_room", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> createRoom(HttpServletRequest request, @RequestBody Room createdRoom)
    {
        String userIdentifier = checkOrCreateUserIdentifierInSession(request);
        UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);

        if (userSession == null)
        {
            userSession = new UserSession();
            Room room = new Room(randomStringGenerator.generateRandomIdentifier(5), true, createdRoom.isGuestCanPause(), createdRoom.getVotesToSkip());
            userSession.setUserRoom(room);
            DataManagement.userSessionCache.put(userIdentifier, userSession);
        }
        else
        {
            userSession.getUserRoom().setVotesToSkip(createdRoom.getVotesToSkip());
            userSession.getUserRoom().setGuestCanPause(createdRoom.isGuestCanPause());
        }

        return ResponseEntity.status(HttpStatus.OK).body(userSession.getUserRoom());
    }

    /**
     * Checks if the user has an associated room and returns the Room object if found.
     * This method is accessed via HTTP GET at the path "/check-if-user-has-room".
     *
     * @param request The HttpServletRequest object representing the incoming HTTP request.
     * @return ResponseEntity containing a Room object if the user has an associated room.
     *         The response may include one of the following HTTP statuses:
     *         - HttpStatus.OK (200) if the user has an associated room and the Room object is returned successfully.
     *         - HttpStatus.NOT_FOUND (404) if the user's session is not found or the user has no associated room.
     *
     * @see DataManagement#userSessionCache
     * @see UserSession
     * @see Room
     * @see #checkOrCreateUserIdentifierInSession(HttpServletRequest)
     */
    @GetMapping(path = "/check-if-user-has-room")
    public ResponseEntity<Room> checkIfUserHasRoom(HttpServletRequest request)
    {
        String userIdentifier = checkOrCreateUserIdentifierInSession(request);
        UserSession userSession = DataManagement.userSessionCache.get(userIdentifier);

        if (userSession == null)
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        }

        return ResponseEntity.ok(userSession.getUserRoom());
    }

    /**
     * Checks if the current user is the owner of the specified room identified by 'roomIdentifier'.
     * This method is accessed via HTTP GET at the path "/check-room-owner".
     *
     * @param request The HttpServletRequest object representing the incoming HTTP request.
     * @param roomIdentifier The unique identifier for the room to check ownership.
     * @return ResponseEntity containing a Boolean indicating whether the current user is the owner of the specified room.
     *         The response may include one of the following HTTP statuses:
     *         - HttpStatus.OK (200) if the current user is the owner of the room, and the response body contains 'true'.
     *         - HttpStatus.OK (200) if the current user is not the owner of the room, and the response body contains 'false'.
     *         - HttpStatus.NOT_FOUND (404) if the user's session is not found or the specified room does not exist.
     *
     * @see DataManagement#getMatchingEntry(String)
     * @see #checkOrCreateUserIdentifierInSession(HttpServletRequest)
     */
    @GetMapping(path = "/check-room-owner")
    public ResponseEntity<Boolean> checkRoomOwner(HttpServletRequest request, @RequestParam("roomIdentifier") String roomIdentifier)
    {
        String userIdentifier = checkOrCreateUserIdentifierInSession(request);
        Optional<Map.Entry<String, UserSession>> userEntry = DataManagement.getMatchingEntry(roomIdentifier);

        return userEntry.map(stringUserSessionEntry -> ResponseEntity.ok().body(stringUserSessionEntry.getKey().equals(userIdentifier))).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());

    }

    /**
     * Retrieves the Room object associated with the specified room identifier.
     * This method is accessed via HTTP GET at the path "/get_room".
     *
     * @param request The HttpServletRequest object representing the incoming HTTP request.
     * @param roomIdentifier The unique identifier for the room to retrieve.
     * @return ResponseEntity containing an Object representing the Room associated with the specified identifier.
     *         The response may include one of the following HTTP statuses:
     *         - HttpStatus.OK (200) if the room is found and the response body contains the Room object.
     *         - HttpStatus.NOT_FOUND (404) if the specified room does not exist or the user's session is not found.
     *
     * @see DataManagement#getMatchingUserSession(String)
     * @see Room
     * @see #checkOrCreateUserIdentifierInSession(HttpServletRequest)
     */
    @GetMapping(path = "/get_room")
    public ResponseEntity<Object> getRoom(HttpServletRequest request, @RequestParam String roomIdentifier)
    {
        checkOrCreateUserIdentifierInSession(request);
        Optional<UserSession> matchingUserSession = DataManagement.getMatchingUserSession(roomIdentifier);

        return matchingUserSession.<ResponseEntity<Object>>map(userSession -> ResponseEntity.ok(userSession.getUserRoom())).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Allows a user to leave the room associated with the specified room identifier.
     * This method is accessed via HTTP GET at the path "/leave_room".
     *
     * @param request       The HttpServletRequest object representing the incoming HTTP request.
     * @param roomIdentifier The unique identifier for the room from which the user wants to leave.
     * @return ResponseEntity containing an Object representing the result of the leave operation.
     *         The response may include one of the following HTTP statuses:
     *         - HttpStatus.OK (200) if the user successfully leaves the room.
     *         - HttpStatus.NOT_FOUND (404) if the specified room does not exist.
     *         - HttpStatus.FORBIDDEN (403) if the user is not the owner of the room and not authorized to leave it.
     *
     * @see DataManagement#getMatchingEntry(String)
     * @see DataManagement#userSessionCache
     * @see #checkOrCreateUserIdentifierInSession(HttpServletRequest)
     */
    @GetMapping(path = "/leave_room")
    public ResponseEntity<Object> leaveRoom(HttpServletRequest request, @RequestParam String roomIdentifier)
    {
        String userIdentifier = checkOrCreateUserIdentifierInSession(request);
        Optional<Map.Entry<String, UserSession>> matchingUserSession = DataManagement.getMatchingEntry(roomIdentifier);

        if (matchingUserSession.isEmpty())
        {
            return ResponseEntity.notFound().build();
        }
        else if (!Objects.equals(userIdentifier, matchingUserSession.get().getKey()))
        {
            return ResponseEntity.status(HttpStatusCode.valueOf(403)).build();

        }

        DataManagement.userSessionCache.remove(userIdentifier);
        return ResponseEntity.ok().build();
    }

    private String checkOrCreateUserIdentifierInSession(HttpServletRequest request)
    {
        HttpSession httpSession = request.getSession();
        String userIdentifier = (String) httpSession.getAttribute("userIdentifier");

        if (userIdentifier == null || userIdentifier.isEmpty())
        {
            return bindUserIdentifierToSession(httpSession);
        }

        return userIdentifier;
    }

    private String bindUserIdentifierToSession(HttpSession httpSession)
    {
        String randomUserIdentifier = randomStringGenerator.generateRandomIdentifier(10);
        httpSession.setAttribute("userIdentifier", randomUserIdentifier);

        return randomUserIdentifier;
    }

}
