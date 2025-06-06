package io.bokun.inventory.plugin.tourcms.api;

import io.bokun.inventory.plugin.tourcms.service.RestService;
import io.bokun.inventory.plugin.tourcms.util.AppLogger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BookingCustomerRepository {

  private static final String TAG = BookingCustomerRepository.class.getSimpleName();

  private static final String DB_URL = System.getenv("DB_URL");
  private static final String DB_USERNAME = System.getenv("DB_USERNAME");
  private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

  public static boolean insertCustomer(String id, String customerId) {
    if (id == null || customerId == null || id.trim().isEmpty() || customerId.trim().isEmpty()) {
      AppLogger.error(TAG, String.format("ID and Customer ID cannot be null or empty"));
      throw new IllegalArgumentException("ID and Customer ID cannot be null or empty");
    }

    AppLogger.info(TAG, String.format("InsertCustomer booking_id: %s and customer_id: %s", id, customerId));

    String sql = "INSERT INTO booking_customer (id, customer_id) VALUES (?, ?)";

    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, id);
      stmt.setString(2, customerId);
      int rowsAffected = stmt.executeUpdate();

      return rowsAffected > 0;

    } catch (SQLException e) {
      AppLogger.error(TAG, String.format("Database error inserting customer: %s", e.getMessage()));
      return false;
    }
  }

  public static String findCustomerIdById(String id) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("ID cannot be null or empty");
    }

    String sql = "SELECT customer_id FROM booking_customer WHERE id = ?";

    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, id);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getString("customer_id");
        }
        return null;
      }

    } catch (SQLException e) {
      AppLogger.error(TAG, String.format("Database error finding customer: %s", e.getMessage()));
      return null;
    }
  }
}
