package io.openliberty.security.jakartasec.fat.utils;

public class Jakartasec40TestConstants {

    /**
     * Identity Store
     */

    // Test credentials
    public static final String VALID_PASSWORD = "secret1";
    public static final String INVALID_PASSWORD = "bad_password";

    // Users with valid groups
    public static final String USER_JASMINE = "jasmine"; // plain text password
    public static final String USER_LISA = "lisa"; // XOR encoded password
    public static final String USER_FRANK = "frank"; // Hash encoded password
    public static final String USER_SALLY = "sally"; // AES encoded password
    public static final String USER_THEO = "theo"; // AES encoded password

    // Users with invalid groups or bad encoding
    public static final String USER_BILL = "bill"; // valid password but wrong groups
    public static final String USER_JOHNNY = "johnny"; // bad XOR encoding

    // Expected messages
    public static final String PRODUCTION_USE_WARNING_MSG = "CWWKS2600W"; // Warning about using in-memory store
    public static final String WRONG_CRED_ERROR_MSG = "CWWKS1859E"; // The password was not decrypted because a decoding error was reported

    // XOR and AES-256 encoded (All decode to "secret1")
    public static final String PASSWORD_XOR_VALID = "{xor}LDo8LTorbg==";
    public static final String PASSWORD_AES_VALID = "{aes}ARAFCrWIYJCL7ZBNjN+MKcJoozBmbZyJPood6X6sCqMIRBaouZSd4B0u0Gcgdp/tXWwuOwi/mqYLS0cGTwuU95tgP4Y+6hgtvG2ST2gT+ghTPhLJWfiXTrvBRvR5yf2F9hkmM7SG/WRQNA==";
    public static final String PASSWORD_HASH_VALID = "{hash}ARAAAAAUUEJLREYyV2l0aEhtYWNTSEE1MTIwAAAAIKJLaCvuDfiQYK8H/6SWdTzmbMxndGqWyUWnCaA3ZPOZQAAAACBnjkRtcavFNC2k2qbwEitLlmHZKTYRmeuuCh8z1nFyyw==";

    // XOR, but bad encoding
    public static final String PASSWORD_XOR_INVALID = "{xor}LLTxlkwjljsdforbg=";
}
