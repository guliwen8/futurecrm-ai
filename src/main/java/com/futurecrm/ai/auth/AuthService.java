package com.futurecrm.ai.auth;

import com.futurecrm.ai.common.CurrentUser;
import com.futurecrm.ai.common.PasswordUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, CurrentUser> sessions = new ConcurrentHashMap<>();

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AuthModels.LoginResponse> login(String username, String password) {
        String sql = """
                SELECT id, username, password_hash, real_name, role
                FROM users
                WHERE username = ? AND status = 'ACTIVE'
                """;
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            String expectedHash = rs.getString("password_hash");
            if (!PasswordUtil.sha256(password).equals(expectedHash)) {
                return Optional.empty();
            }
            CurrentUser user = new CurrentUser(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("real_name"),
                    rs.getString("role")
            );
            String token = UUID.randomUUID().toString().replace("-", "");
            sessions.put(token, user);
            return Optional.of(new AuthModels.LoginResponse(token, user.id(), user.username(), user.realName(), user.role()));
        }, username);
    }

    public Optional<CurrentUser> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(token));
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}
