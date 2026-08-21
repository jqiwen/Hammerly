package com.hammerly.backend.repository;

import com.hammerly.backend.model.PaymentMethod;
import com.hammerly.backend.util.TimeUtils;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentMethodRepository {
    private static final String SELECT_COLUMNS = """
        SELECT id, user_id, card_type, COALESCE(NULLIF(card_number, ''), last_four) AS card_number,
               expiry_month, expiry_year, cardholder_name, is_default, billing_address, billing_city,
               billing_province, billing_postal_code, billing_country, created_at
        FROM payment_methods
        """;
    private final JdbcTemplate jdbc;

    public PaymentMethodRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PaymentMethod> findByUser(long userId) {
        return jdbc.query(SELECT_COLUMNS + " WHERE user_id = ? ORDER BY is_default DESC, created_at DESC",
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
                    (user_id, card_type, card_number, last_four, expiry_month, expiry_year, cardholder_name,
                     is_default, billing_address, billing_city, billing_province, billing_postal_code, billing_country)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setLong(1, userId);
            statement.setString(2, cardType);
            statement.setString(3, cardNumber);
            statement.setString(4, lastFour);
            statement.setInt(5, expiryMonth);
            statement.setInt(6, expiryYear);
            statement.setString(7, cardholderName);
            statement.setBoolean(8, isDefault != 0);
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
        jdbc.update("UPDATE payment_methods SET is_default = FALSE WHERE user_id = ?", userId);
    }

    public void setDefault(long id) {
        jdbc.update("UPDATE payment_methods SET is_default = TRUE WHERE id = ?", id);
    }

    public void delete(long id, long userId) {
        jdbc.update("DELETE FROM payment_methods WHERE id = ? AND user_id = ?", id, userId);
    }

    public Optional<Long> newestId(long userId) {
        return jdbc.query("SELECT id FROM payment_methods WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
            (rs, row) -> rs.getLong(1), userId).stream().findFirst();
    }

    private PaymentMethod map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new PaymentMethod(rs.getLong("id"), rs.getLong("user_id"), rs.getString("card_type"),
            rs.getString("card_number"), rs.getInt("expiry_month"), rs.getInt("expiry_year"),
            rs.getString("cardholder_name"), rs.getBoolean("is_default") ? 1 : 0,
            rs.getString("billing_address"), rs.getString("billing_city"), rs.getString("billing_province"),
            rs.getString("billing_postal_code"), rs.getString("billing_country"),
            TimeUtils.readInstant(rs, "created_at"));
    }
}
