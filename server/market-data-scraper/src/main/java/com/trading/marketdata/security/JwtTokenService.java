package com.trading.marketdata.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Collection;
import java.util.List;

@Component
public class JwtTokenService {

    @Value("${jones.app.jwtSecret:T2hTeW1fc2VjcmV0X2tleV9mb3JfSldVX3Rva2VuX2dlbmVyYXRpb25fMTIzNDU2Nzg=}")
    private String jwtSecret;

    public Claims parseAndValidate(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String username(Claims claims) {
        return claims.getSubject();
    }

    public Collection<SimpleGrantedAuthority> authorities(Claims claims) {
        Object permissions = claims.get("permissions");
        if (!(permissions instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    private Key signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }
}
