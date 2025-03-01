package com.pbemgs.dko;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DSLContextFactory {
    private static DSLContext productionDslContext;
    private static DSLContext testDslContext;

    // Returns the production DSLContext
    public static DSLContext getProductionInstance() {
        if (productionDslContext == null) {
            productionDslContext = createProductionDslContext();
        }
        return productionDslContext;
    }

    // Returns the test DSLContext (intended for unit tests)
    public static DSLContext getTestInstance() {
        if (testDslContext == null) {
            testDslContext = createTestDslContext();
        }
        return testDslContext;
    }

    private static DSLContext createProductionDslContext() {
        String dbURL = System.getenv("PBEMGS_RDS_DB_URL");
        String dbUser = System.getenv("PBEMGS_RDS_DB_USER");
        String dbPw = System.getenv("PBEMGS_RDS_DB_PASSWORD");

        try {
            Connection connection = DriverManager.getConnection(
                    dbURL, dbUser, dbPw
            );
            if (connection == null) {
                throw new RuntimeException("Failed to connect for DSL Context");
            }
            return DSL.using(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize DSLContext", e);
        }
    }

    private static DSLContext createTestDslContext() {
        // Use H2 or a similar lightweight in-memory database for tests
        String dbURL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
        String dbUser = "sa";
        String dbPw = "";

        try {
            Connection connection = DriverManager.getConnection(
                    dbURL, dbUser, dbPw
            );
            return DSL.using(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize DSLContext for Test environment", e);
        }
    }

    // For unit testing, you can inject a mock DSLContext
    public static void setTestDslContext(DSLContext mockContext) {
        testDslContext = mockContext;
    }
}
