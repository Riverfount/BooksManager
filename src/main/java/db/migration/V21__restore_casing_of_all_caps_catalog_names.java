package db.migration;

import com.riverfount.booksmanager.catalog.domain.TitleCase;
import java.sql.Connection;
import java.sql.SQLException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Restores the casing of the book titles, author names and category names that were stored entirely in uppercase
 * (typically from the ISBN lookup source or hand-typed in caps). It uses the same {@link TitleCase} rule the
 * application applies on every write, so old and new data agree. Only rows that are wholly uppercase change: a value
 * with any lowercase letter has a person's casing in it and is never touched.
 *
 * <p>It is a Java migration because the rule (connectives, roman numerals, initials) is not worth duplicating in SQL.
 * It runs once, in the migration's transaction, so it is all or nothing. A fresh database has no rows to change.
 */
public class V21__restore_casing_of_all_caps_catalog_names extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws SQLException {
        var connection = context.getConnection();
        restore(connection, "book", "title");
        restore(connection, "author", "name");
        restore(connection, "category", "name");
    }

    private static void restore(Connection connection, String table, String column) throws SQLException {
        try (var select = connection.createStatement();
                var rows = select.executeQuery("select id, " + column + " from " + table
                        + " where " + column + " = upper(" + column + ")");
                var update = connection.prepareStatement("update " + table + " set " + column + " = ? where id = ?")) {
            while (rows.next()) {
                var current = rows.getString(2);
                var restored = TitleCase.normalize(current);
                if (!restored.equals(current)) {
                    update.setString(1, restored);
                    update.setLong(2, rows.getLong(1));
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }
}
