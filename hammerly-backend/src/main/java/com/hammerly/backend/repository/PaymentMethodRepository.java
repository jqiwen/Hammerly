package com.hammerly.backend.repository;

import com.hammerly.backend.model.PaymentMethod;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentMethodRepository {
    private static final String SELECT_COLUMNS = """
        SELECT id, user_id, cardType, COALESCE(NULLIF(cardNumber, ''), lastFour) AS cardNumber,
               expiryMonth, expiryYear, cardholderName, isDefault, billingAddress, billingCity,
               billingProvince, billingPostalCode, billingCountry, createdAt
        FROM payment_methods
        """;
    private final JdbcTemplate jdbc;

    public PaymentMethodRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PaymentMethod> findByUser(long userId) {
        return jdbc.query(SELECT_COLUMNS + " WHERE user_id = ? ORDER BY isDefault DESC, createdAt DESC",
            this::map, userId);
    }

    public Optional<PaymentMethod> findOwned(long id, long userId) {
        return jdbc.query(SELECT_COLUMNS + " WHERE id = ? AND user_id = ?", this::map, id, userId)
            .stream().findFirst();
    }

    public long insert(long userId, String cardType, String cardNumber, String lastFour,
                       int expiryMonth, int expiryYear, String cardholderName, int isDefault,
                       String billingAddress, String billingCity, String billingProvince,
                       String billingPostalCode, String billingCountry) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment_methods
                    (user_id, cardType, cardNumber, lastFour, expiryMonth, expiryYear, cardholderName,
                     isDefault, billingAddress, billingCity, billingProvince, billingPostalCode, billingCountry)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setString(2, cardType);
            statement.setString(3, cardNumber);
            statement.setString(4, lastFour);
            statement.setInt(5, expiryMonth);
            statement.setInt(6, expiryYear);
            statement.setString(7, cardholderName);
            statement.setInt(8, isDefault);
            statement.setString(9, billingAddress);
            statement.setString(10, billingCity);
            statement.setString(11, billingProvince);
            statement.setString(12, billingPostalCode);
            statement.setString(13, billingCountry);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public long countByUser(long userId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM payment_methods WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    public void clearDefault(long userId) {
        jdbc.update("UPDATE payment_methods SET isDefault = 0 WHERE user_id = ?", userId);
    }

    public void setDefault(long id) {
        jdbc.update("UPDATE payment_methods SET isDefault = 1 WHERE id = ?", id);
    }

    public void delete(long id, long userId) {
        jdbc.update("DELETE FROM payment_methods WHERE id = ? AND user_id = ?", id, userId);
    }

    public Optional<Long> newestId(long userId) {
        return jdbc.query("SELECT id FROM payment_methods WHERE user_id = ? ORDER BY createdAt DESC LIMIT 1",
            (rs, row) -> rs.getLong(1), userId).stream().findFirst();
    }

    private PaymentMethod map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new PaymentMethod(rs.getLong("id"), rs.getLong("user_id"), rs.getString("cardType"),
            rs.getString("cardNumber"), rs.getInt("expiryMonth"), rs.getInt("expiryYear"),
            rs.getString("cardholderName"), rs.getInt("isDefault"), rs.getString("billingAddress"),
            rs.getString("billingCity"), rs.getString("billingProvince"),
            rs.getString("billingPostalCode"), rs.getString("billingCountry"), rs.getString("createdAt"));
    }
}
