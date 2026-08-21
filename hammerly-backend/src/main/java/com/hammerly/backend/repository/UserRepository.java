package com.hammerly.backend.repository;

import com.hammerly.backend.model.User;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private static final String PROFILE_COLUMNS =
        "id, firstName, lastName, email, password, phone, avatarImage, createdAt";
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByEmail(String email) {
        return first(jdbc.query("SELECT " + PROFILE_COLUMNS + " FROM users WHERE email = ?", this::mapUser, email));
    }

    public Optional<User> findById(long id) {
        return first(jdbc.query("SELECT " + PROFILE_COLUMNS + " FROM users WHERE id = ?", this::mapUser, id));
    }

    public boolean emailBelongsToAnotherUser(String email, long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ? AND id != ?",
            Integer.class, email, userId);
        return count != null && count > 0;
    }

    public long insert(String email, String password, String firstName, String lastName,
                       String phone, String avatarImage) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (email, password, firstName, lastName, phone, avatarImage) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, email);
            statement.setString(2, password);
            statement.setString(3, firstName);
            statement.setString(4, lastName);
            statement.setString(5, phone);
            statement.setString(6, avatarImage);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateProfile(long id, String firstName, String lastName, String email, String phone) {
        jdbc.update("UPDATE users SET firstName = ?, lastName = ?, email = ?, phone = ?, " +
            "updatedAt = CURRENT_TIMESTAMP WHERE id = ?", firstName, lastName, email, phone, id);
    }

    public void updatePassword(long id, String encodedPassword) {
        jdbc.update("UPDATE users SET password = ?, updatedAt = CURRENT_TIMESTAMP WHERE id = ?", encodedPassword, id);
    }

    public void updateAvatar(long id, String avatarImage) {
        jdbc.update("UPDATE users SET avatarImage = ?, updatedAt = CURRENT_TIMESTAMP WHERE id = ?", avatarImage, id);
    }

    public void updateSeedUser(long id, String firstName, String lastName, String password,
                               String phone, String avatarImage) {
        jdbc.update("UPDATE users SET firstName = ?, lastName = ?, password = ?, phone = ?, avatarImage = ?, " +
                "updatedAt = CURRENT_TIMESTAMP WHERE id = ?",
            firstName, lastName, password, phone, avatarImage, id);
    }

    private User mapUser(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new User(rs.getLong("id"), rs.getString("firstName"), rs.getString("lastName"),
            rs.getString("email"), rs.getString("password"), valueOrEmpty(rs.getString("phone")),
            valueOrEmpty(rs.getString("avatarImage")), rs.getString("createdAt"));
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }
}
