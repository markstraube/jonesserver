package com.straube.jones.security.jwt;


import java.security.Key;
import java.util.Date;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import com.straube.jones.security.services.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtils
{
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${jones.app.jwtSecret:T2hTeW1fc2VjcmV0X2tleV9mb3JfSldVX3Rva2VuX2dlbmVyYXRpb25fMTIzNDU2Nzg=}")
    private String jwtSecret;

    @Value("${jones.app.jwtExpirationMs:86400000}")
    private int jwtExpirationMs;

    public String generateJwtToken(Authentication authentication)
    {

        UserDetailsImpl userPrincipal = (UserDetailsImpl)authentication.getPrincipal();

        return Jwts.builder()
                   .setSubject((userPrincipal.getUsername()))
                   .claim("userId", userPrincipal.getId())
                   .claim("permissions",
                          userPrincipal.getAuthorities()
                                       .stream()
                                       .map(GrantedAuthority::getAuthority)
                                       .collect(Collectors.toList()))
                   .setIssuedAt(new Date())
                   .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                   .signWith(key(), SignatureAlgorithm.HS256)
                   .compact();
    }


    private Key key()
    {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }


    public String getUserNameFromJwtToken(String token)
    {
        return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody().getSubject();
    }


    public boolean validateJwtToken(String authToken)
    {
        try
        {
            Jwts.parserBuilder().setSigningKey(key()).build().parse(authToken);
            return true;
        }
        catch (MalformedJwtException e)
        {
            logger.error("Invalid JWT token: {}", e.getMessage());
        }
        catch (ExpiredJwtException e)
        {
            logger.error("JWT token is expired: {}", e.getMessage());
        }
        catch (UnsupportedJwtException e)
        {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        }
        catch (IllegalArgumentException e)
        {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }

        return false;
    }
}
