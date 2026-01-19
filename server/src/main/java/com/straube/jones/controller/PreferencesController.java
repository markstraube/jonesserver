package com.straube.jones.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import com.straube.jones.model.User;
import com.straube.jones.model.UserPreference;
import com.straube.jones.repository.UserPreferenceRepository;
import com.straube.jones.repository.UserRepository;
import com.straube.jones.dto.UserPrefsResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.parameters.RequestBody;

@RestController
@RequestMapping("/api/preferences")
@Tag(name = "User Preferences", description = "Endpoints for managing user-specific preferences")
public class PreferencesController
{

    @Autowired
    UserPreferenceRepository userPreferenceRepository;

    @Autowired
    UserRepository userRepository;

    @Operation(summary = "Get user preferences", description = "Retrieves the preference settings (JSON) for the authenticated user.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Preferences retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserPrefsResponse.class))),
                           @ApiResponse(responseCode = "401", description = "Unauthorized (User not logged in)", content = @Content)})
    @GetMapping
    public ResponseEntity<UserPrefsResponse> getPreferences()
    {
        User user = getCurrentUser();
        UserPreference prefs = userPreferenceRepository.findByUser(user).orElse(new UserPreference()); // Return empty

        UserPrefsResponse response = new UserPrefsResponse();
        response.setTopic("global");
        response.setPreferences(prefs.getPreferences() != null ? prefs.getPreferences() : "{}");
        // Also set success
        response.setSuccess(true);
        response.setMessage("Preferences loaded");

        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Update user preferences", description = "Updates or creates preference settings for the authenticated user.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Preferences updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserPrefsResponse.class))),
                           @ApiResponse(responseCode = "401", description = "Unauthorized (User not logged in)", content = @Content)})
    @PutMapping
    public ResponseEntity<UserPrefsResponse> updatePreferences(@RequestBody(description = "JSON string containing user preferences", required = true, content = @Content(mediaType = "application/json", schema = @Schema(type = "string", example = "{\"theme\":\"dark\"}")))
    @org.springframework.web.bind.annotation.RequestBody
    String preferencesJson)
    {

        User user = getCurrentUser();
        UserPreference prefs = userPreferenceRepository.findByUser(user).orElse(new UserPreference());

        if (prefs.getUser() == null)
        {
            prefs.setUser(user);
        }

        prefs.setPreferences(preferencesJson);
        userPreferenceRepository.save(prefs);

        UserPrefsResponse response = new UserPrefsResponse();
        response.setTopic("global");
        response.setPreferences(prefs.getPreferences());
        response.setSuccess(true);
        response.setMessage("Preferences saved");

        return ResponseEntity.ok(response);
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
