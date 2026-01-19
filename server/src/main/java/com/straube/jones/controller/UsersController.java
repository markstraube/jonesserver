package com.straube.jones.controller;


import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.dataprovider.userprefs.UserPrefsRepo;
import com.straube.jones.dto.UserPrefsResponse;
import com.straube.jones.model.User;
import com.straube.jones.repository.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/api/users")
@Tag(name = "User Preferences API", description = "API for managing user preferences and settings. Allows saving and retrieving user-specific configurations such as filters, watchlists, and application settings.")
public class UsersController
{
    @Autowired
    UserRepository userRepository;

    @Operation(summary = "Save User Preferences", description = "**Use Case:** Persistent storage of user-specific configurations. \n"
                    + "Stores preferences for various application modules identified by a 'topic'. \n"
                    + "**When to use:** Whenever a user changes settings that should be remembered, such as adjusting a search filter, modifying a watchlist, or changing UI themes. \n"
                    + "**Topics:** \n"
                    + "- 'filter': Stores global search criteria.\n"
                    + "- 'watchlist': Stores the user's personal stock watchlist.\n"
                    + "- Custom topics: Can be used for flexible grouping of settings.\n"
                    + "**Data Handling:** The system acts as a key-value store where the 'topic' is the key and the body is the stored JSON value.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Preferences successfully saved.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserPrefsResponse.class))),
                           @ApiResponse(responseCode = "500", description = "Internal Server Error. Failed to save preferences due to file system or storage issues.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserPrefsResponse.class)))})
    @PostMapping(path = "/prefs/{topic}", produces = "application/json")
    public UserPrefsResponse setUserPref(@Parameter(description = "The category or key for the preferences. Common values: 'filter', 'watchlist'.", required = true, example = "filter")
    @PathVariable
    String topic,

                                         @Parameter(description = "The JSON string representation of the preferences to store. This payload is stored blindly and returned as-is.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(type = "string", example = "{\"minPrice\": 10, \"maxPrice\": 100}")))
                                         @RequestBody
                                         String userPrefs)
    {
        try
        {
            User currentUser = getCurrentUser();
            UserPrefsRepo.savePrefs(currentUser, topic, userPrefs);
            return UserPrefsResponse.success(topic, userPrefs);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return UserPrefsResponse.error(topic, "Failed to save preferences: " + e.getMessage());
        }
    }


    @Operation(summary = "Load User Preferences", description = "**Use Case:** Retrieval of stored user customizations. \n"
                    + "Fetches the JSON configuration associated with a specific 'topic'. \n"
                    + "**When to use:** At application startup or when navigating to a module that requires user-specific settings (e.g., loading the saved filter when opening the search view). \n"
                    + "**Behavior:** Returns the exact JSON string that was previously saved.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Preferences successfully retrieved.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserPrefsResponse.class))),
                           @ApiResponse(responseCode = "500", description = "Internal Server Error. Failed to load preferences, possibly due to missing file or read permissions.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserPrefsResponse.class)))})
    @GetMapping(path = "/prefs/{topic}", produces = "application/json")
    public UserPrefsResponse getUserPrefs(@Parameter(description = "The category or key of the preferences to retrieve.", required = true, example = "filter")
    @PathVariable
    String topic)
    {
        try
        {
            User currentUser = getCurrentUser();
            String preferences;
            preferences = UserPrefsRepo.getPrefs(currentUser, topic);
            return UserPrefsResponse.success(topic, preferences);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return UserPrefsResponse.error(topic, "Failed to load preferences: " + e.getMessage());
        }
    }


    private User getCurrentUser()
    {
        UserDetails userDetails = (UserDetails)SecurityContextHolder.getContext()
                                                                    .getAuthentication()
                                                                    .getPrincipal();
        return userRepository.findByUsername(userDetails.getUsername())
                             .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
