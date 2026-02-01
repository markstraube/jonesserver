package com.straube.jones.dto.auth;


import java.util.Set;

import jakarta.validation.constraints.*;

public class SignupRequest
{
    @NotBlank
    @Size(min = 3, max = 20)
    private String username;

    @NotBlank
    @Size(max = 50)
    @Email
    private String email;

    private Set<String> permissions;

    @NotBlank
    @Size(min = 6, max = 40)
    private String password;

    public String getUsername()
    {
        return username;
    }


    public void setUsername(String username)
    {
        this.username = username;
    }


    public String getEmail()
    {
        return email;
    }


    public void setEmail(String email)
    {
        this.email = email;
    }


    public String getPassword()
    {
        return password;
    }


    public void setPassword(String password)
    {
        this.password = password;
    }


    public Set<String> getPermissions()
    {
        return permissions;
    }


    public void setPermissions(Set<String> permissions)
    {
        this.permissions = permissions;
    }
}
