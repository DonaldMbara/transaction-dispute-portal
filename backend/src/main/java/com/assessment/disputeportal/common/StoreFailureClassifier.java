package com.assessment.disputeportal.common;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import java.sql.SQLException;

public final class StoreFailureClassifier {
    private StoreFailureClassifier() {}
    public static boolean isStoreFailure(Throwable error) {
        for (int depth = 0; error != null && depth < 32; depth++, error = error.getCause()) {
            if (error instanceof DataAccessException || error instanceof TransactionException || error instanceof SQLException) return true;
        }
        return false;
    }
}
