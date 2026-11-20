package io.github.rkaushikdev.galleyworks.index;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.StoreError;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The few JDBC moves the store makes, with parameters bound positionally and rows read back as
 * maps by column label. A failure becomes a {@link StoreError} naming the statement.
 */
public final class Sql {

    private Sql() {}

    /** Run one statement with {@code params}, returning the update count. */
    public static int execute(Connection connection, String sql, Object... params) {
        try (PreparedStatement statement = prepare(connection, sql, params)) {
            statement.execute();
            return Math.max(0, statement.getUpdateCount());
        } catch (SQLException exc) {
            throw failure(sql, exc);
        }
    }

    /** Run one statement once per parameter row, in one batch. */
    public static void executeMany(Connection connection, String sql, List<Object[]> rows) {
        if (rows.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Object[] row : rows) {
                bind(statement, row);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exc) {
            throw failure(sql, exc);
        }
    }

    /** Every row the query returns. */
    public static List<Map<String, Object>> rows(Connection connection, String sql, Object... params) {
        try (PreparedStatement statement = prepare(connection, sql, params);
                ResultSet result = statement.executeQuery()) {
            ResultSetMetaData meta = result.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (result.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int column = 1; column <= meta.getColumnCount(); column++) {
                    row.put(meta.getColumnLabel(column), result.getObject(column));
                }
                rows.add(row);
            }
            return rows;
        } catch (SQLException exc) {
            throw failure(sql, exc);
        }
    }

    /** The first row the query returns, if any. */
    public static Optional<Map<String, Object>> first(Connection connection, String sql, Object... params) {
        List<Map<String, Object>> found = rows(connection, sql, params);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    /** The first column of the first row, read as a long. */
    public static long scalar(Connection connection, String sql, Object... params) {
        Map<String, Object> row = first(connection, sql, params)
                .orElseThrow(() -> new StoreError("query returned no rows", Context.of("sql", sql)));
        return ((Number) row.values().iterator().next()).longValue();
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            bind(statement, params);
        } catch (SQLException exc) {
            statement.close();
            throw exc;
        }
        return statement;
    }

    private static void bind(PreparedStatement statement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    private static StoreError failure(String sql, SQLException exc) {
        return new StoreError("index statement failed", Context.of("sql", sql, "reason", exc.getMessage()), exc);
    }
}
