import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Pattern;

/** Run from the repository root. Reads credentials locally; never writes them to the fixture. */
public class InstallProblem {
    private static final Path ROOT = Path.of("test_codes/java/book_oop");

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("--inspect") || args[0].equals("--install") || args[0].equals("--verify")
                || args[0].equals("--update-description"))) {
            throw new IllegalArgumentException("Use --inspect, --install, --verify or --update-description from the repository root.");
        }
        Properties settings = new Properties();
        settings.setProperty("user", setting("DB_USER"));
        settings.setProperty("password", setting("DB_PASSWORD"));
        settings.setProperty("connectTimeout", "15");
        settings.setProperty("socketTimeout", "30");
        DriverManager.setLoginTimeout(15);
        try (Connection connection = DriverManager.getConnection(setting("DB_URL"), settings)) {
            if (args[0].equals("--inspect")) {
                try (var stmt = connection.prepareStatement(
                        "SELECT id,title,created_by FROM problems WHERE problem_type='JAVA_OOP' ORDER BY id");
                     var rows = stmt.executeQuery()) {
                    while (rows.next()) System.out.printf("id=%d | teacherId=%d | %s%n",
                            rows.getInt("id"), rows.getInt("created_by"), rows.getString("title"));
                }
                return;
            }
            if (args[0].equals("--verify")) verify(connection);
            else if (args[0].equals("--update-description")) updateDescription(connection);
            else install(connection);
        } catch (SQLException error) {
            // Do not expose connection strings or passwords in console logs.
            System.err.println("Database operation failed; SQLState=" + error.getSQLState());
            System.exit(1);
        }
    }

    private static String setting(String name) throws Exception {
        String environment = System.getenv(name);
        if (environment != null && !environment.isBlank()) return environment;
        String localScript = Files.readString(Path.of("server/run-server.bat"), StandardCharsets.UTF_8);
        var matcher = Pattern.compile("(?im)^\\s*SET\\s+" + Pattern.quote(name) + "=(.+)$").matcher(localScript);
        if (!matcher.find()) throw new IllegalStateException("Missing local setting: " + name);
        return matcher.group(1).trim();
    }

    private static void verify(Connection connection) throws Exception {
        JsonObject problem = JsonParser.parseString(Files.readString(ROOT.resolve("problem/create_problem.json"))).getAsJsonObject();
        int problemId;
        try (var stmt = connection.prepareStatement("SELECT * FROM problems WHERE title=?")) {
            stmt.setString(1, problem.get("title").getAsString());
            try (var rows = stmt.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Problem not found");
                problemId = rows.getInt("id");
                if (!rows.getString("description").equals(problem.get("description").getAsString())
                        || !rows.getString("problem_type").equals("JAVA_OOP") || !rows.getBoolean("is_active")
                        || rows.getInt("difficulty") != problem.get("difficulty").getAsInt()
                        || rows.getInt("time_limit_ms") != problem.get("timeLimitMs").getAsInt()
                        || rows.getInt("memory_limit_kb") != problem.get("memoryLimitKb").getAsInt()
                        || !JsonParser.parseString(rows.getString("oop_spec")).equals(JsonParser.parseString(problem.get("oopSpec").getAsString()))) {
                    throw new IllegalStateException("Stored problem differs from the tested fixture");
                }
                if (rows.next()) throw new IllegalStateException("Duplicate problem title");
            }
        }
        JsonArray tests = problem.getAsJsonArray("testCases");
        int index = 0;
        try (var stmt = connection.prepareStatement("SELECT * FROM test_cases WHERE problem_id=? ORDER BY order_num")) {
            stmt.setInt(1, problemId);
            try (var rows = stmt.executeQuery()) {
                while (rows.next()) {
                    if (index >= tests.size()) throw new IllegalStateException("Unexpected stored test case");
                    JsonObject expected = tests.get(index++).getAsJsonObject();
                    if (!rows.getString("input_data").equals(expected.get("inputData").getAsString())
                            || !rows.getString("expected_output").equals(expected.get("expectedOutput").getAsString())
                            || rows.getBoolean("is_sample") != expected.get("isSample").getAsBoolean()
                            || rows.getInt("order_num") != expected.get("orderNum").getAsInt()) {
                        throw new IllegalStateException("Stored test case differs from fixture: " + index);
                    }
                }
            }
        }
        if (index != tests.size()) throw new IllegalStateException("Missing stored test cases");
        System.out.printf("VERIFIED problemId=%d | tests=%d | metadata, UTF-8 statement, OOP spec and test cases match%n", problemId, index);
    }

    private static void updateDescription(Connection connection) throws Exception {
        JsonObject problem = JsonParser.parseString(Files.readString(ROOT.resolve("problem/create_problem.json"))).getAsJsonObject();
        connection.setAutoCommit(false);
        try (var update = connection.prepareStatement(
                "UPDATE problems SET description=? WHERE title=? AND problem_type='JAVA_OOP'")) {
            update.setString(1, problem.get("description").getAsString());
            update.setString(2, problem.get("title").getAsString());
            if (update.executeUpdate() != 1) throw new IllegalStateException("Expected exactly one Book OOP problem");
            verify(connection);
            connection.commit();
            System.out.println("UPDATED description only; OOP specification and test cases are unchanged.");
        } catch (Exception error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static void install(Connection connection) throws Exception {
        JsonObject problem = JsonParser.parseString(Files.readString(ROOT.resolve("problem/create_problem.json"))).getAsJsonObject();
        JsonArray tests = problem.getAsJsonArray("testCases");
        String title = problem.get("title").getAsString();
        connection.setAutoCommit(false);
        try {
            // Prevent duplicate inserts when the installer is run concurrently.
            try (var lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                lock.setString(1, title);
                lock.execute();
            }
            try (var existing = connection.prepareStatement("SELECT id FROM problems WHERE title=?")) {
                existing.setString(1, title);
                try (var rows = existing.executeQuery()) {
                    if (rows.next()) {
                        System.out.println("ALREADY_EXISTS problemId=" + rows.getInt("id") + "; no changes made.");
                        connection.rollback();
                        return;
                    }
                }
            }
            int teacherId;
            try (var stmt = connection.prepareStatement(
                    "SELECT u.id FROM users u LEFT JOIN problems p ON p.created_by=u.id AND p.problem_type='JAVA_OOP' " +
                    "WHERE u.role='TEACHER' ORDER BY (p.id IS NOT NULL) DESC,u.id LIMIT 1");
                 var rows = stmt.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("No teacher account found; no changes made.");
                teacherId = rows.getInt(1);
            }
            int problemId;
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO problems (title,description,time_limit_ms,memory_limit_kb,difficulty,created_by,is_active,problem_type,oop_spec) " +
                    "VALUES (?,?,?,?,?,?,TRUE,'JAVA_OOP',?) RETURNING id")) {
                insert.setString(1, title);
                insert.setString(2, problem.get("description").getAsString());
                insert.setInt(3, problem.get("timeLimitMs").getAsInt());
                insert.setInt(4, problem.get("memoryLimitKb").getAsInt());
                insert.setInt(5, problem.get("difficulty").getAsInt());
                insert.setInt(6, teacherId);
                insert.setString(7, problem.get("oopSpec").getAsString());
                try (ResultSet rows = insert.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Insert returned no ID");
                    problemId = rows.getInt(1);
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO test_cases (problem_id,input_data,expected_output,is_sample,order_num) VALUES (?,?,?,?,?)")) {
                for (var element : tests) {
                    JsonObject test = element.getAsJsonObject();
                    insert.setInt(1, problemId);
                    insert.setString(2, test.get("inputData").getAsString());
                    insert.setString(3, test.get("expectedOutput").getAsString());
                    insert.setBoolean(4, test.get("isSample").getAsBoolean());
                    insert.setInt(5, test.get("orderNum").getAsInt());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            try (var verify = connection.prepareStatement("SELECT COUNT(*) FROM test_cases WHERE problem_id=?")) {
                verify.setInt(1, problemId);
                try (var rows = verify.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) != tests.size()) throw new SQLException("Test case verification failed");
                }
            }
            connection.commit();
            System.out.printf("CREATED problemId=%d | teacherId=%d | tests=%d | active=true | %s%n",
                    problemId, teacherId, tests.size(), title);
        } catch (Exception error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
