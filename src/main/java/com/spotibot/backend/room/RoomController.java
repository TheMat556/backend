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
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;


@RestController
@RequestMapping("/room")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class RoomController {
    private final RandomStringGenerator randomStringGenerator = new RandomStringGenerator();

    //TODO: rewrite comments

    /**
     * Creates a new {@link Room} for music playback and returns it as a response entity. This method is asynchronous
     * and returns a CompletableFuture to allow for non-blocking processing.
     *
     * @param createdRoom The Room object containing the details of the new room to be created.
     * @return A CompletableFuture containing a ResponseEntity with either the newly created room or the user's current room.
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

    @GetMapping(path = "/check-room-owner")
    public ResponseEntity<Boolean> checkRoomOwner(HttpServletRequest request, @RequestParam("roomIdentifier") String roomIdentifier)
    {
        String userIdentifier = checkOrCreateUserIdentifierInSession(request);
        Optional<Map.Entry<String, UserSession>> userEntry = DataManagement.getMatchingEntry(roomIdentifier);

        if (userEntry.isPresent())
        {
            if (userEntry.get().getKey().equals(userIdentifier))
            {
                return ResponseEntity.ok().body(true);
            }
            else
            {
                return ResponseEntity.ok().body(false);
            }

        }
        else
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

    }

    /**
     * Retrieves the {@link Room} object associated with the given room identifier and returns it as a ResponseEntity.
     * This method is asynchronous and returns a CompletableFuture to allow for non-blocking processing.
     *
     * @param request        The HttpServletRequest for the current request.
     * @param roomIdentifier The identifier of the room to retrieve.
     * @return A CompletableFuture containing a ResponseEntity with either the requested Room object or a no-content response.
     */
    @GetMapping(path = "/get_room")
    public ResponseEntity<Object> getRoom(HttpServletRequest request, @RequestParam String roomIdentifier)
    {
        checkOrCreateUserIdentifierInSession(request);
        Optional<UserSession> matchingUserSession = DataManagement.getMatchingUserSession(roomIdentifier);

        //Check if the room still exists
        if (matchingUserSession.isEmpty())
        {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(matchingUserSession.get().getUserRoom());
    }

    /**
     * Removes the current user from the {@link Room} object associated with the specified room identifier in the user session cache.
     * Returns an empty {@link ResponseEntity} if no matching {@link Room} object is found.
     *
     * @param request        The {@link HttpServletRequest} object representing the current HTTP request.
     * @param roomIdentifier The room identifier of the {@link Room} object to leave.
     * @return A {@link ResponseEntity} object indicating whether the operation was successful, or an empty response if no matching {@link Room} object is found.
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
