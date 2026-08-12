package com.co.kc.imchat.plugin.session.token;

import com.co.kc.imchat.common.utils.JsonUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Random;

public class JwtTokenService implements TokenService {
    private static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.HS512;

    private static final String SECRET = "3d990d2276917dfac04467df11fff26d";
    private static final Key SECRET_KEY = new SecretKeySpec(SECRET.getBytes(), SIGNATURE_ALGORITHM.getJcaName());

    private static final Random RAN = new Random();

    private static final String KEY_BODY = "body";
    private static final String KEY_RAN = "ran";

    @Override
    public String create(TokenDTO tokenDTO) {
        return Jwts.builder()
                .setSubject(null)
                .claim(KEY_BODY, JsonUtils.toJson(tokenDTO))
                .claim(KEY_RAN, RAN.nextInt())
                .signWith(SIGNATURE_ALGORITHM, SECRET_KEY)
                .compact();
    }

    @Override
    public TokenDTO parse(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        try {
            Claims claims = Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody();
            return JsonUtils.fromJson((String) claims.get(KEY_BODY), TokenDTO.class);
        } catch (Exception ignoreEx) {
            return null;
        }
    }
}
