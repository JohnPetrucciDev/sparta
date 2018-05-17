/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package spa.http;

import spa.util.Convert;
import spa.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.util.Arrays;

public final class JSONResponses {

    public static final JSONStreamAware MISSING_DEADLINE = missing("deadline");
    public static final JSONStreamAware INCORRECT_DEADLINE = incorrect("deadline");
    public static final JSONStreamAware MISSING_TRANSACTION_BYTES_OR_JSON = missing("transactionBytes", "transactionJSON");
    public static final JSONStreamAware MISSING_HALLMARK = missing("hallmark");
    public static final JSONStreamAware INCORRECT_HALLMARK = incorrect("hallmark");
    public static final JSONStreamAware MISSING_WEBSITE = missing("website");
    public static final JSONStreamAware INCORRECT_WEBSITE = incorrect("website");
    public static final JSONStreamAware MISSING_TOKEN = missing("token");
    public static final JSONStreamAware INCORRECT_TOKEN = incorrect("token");
    public static final JSONStreamAware MISSING_ACCOUNT = missing("account");
    public static final JSONStreamAware INCORRECT_ACCOUNT = incorrect("account");
    public static final JSONStreamAware INCORRECT_TIMESTAMP = incorrect("timestamp");
    public static final JSONStreamAware UNKNOWN_ACCOUNT = unknown("account");
    public static final JSONStreamAware UNKNOWN_BLOCK = unknown("block");
    public static final JSONStreamAware INCORRECT_BLOCK = incorrect("block");
    public static final JSONStreamAware UNKNOWN_ENTRY = unknown("entry");
    public static final JSONStreamAware MISSING_PEER = missing("peer");
    public static final JSONStreamAware UNKNOWN_PEER = unknown("peer");
    public static final JSONStreamAware MISSING_TRANSACTION = missing("transaction");
    public static final JSONStreamAware UNKNOWN_TRANSACTION = unknown("transaction");
    public static final JSONStreamAware INCORRECT_TRANSACTION = incorrect("transaction");
    public static final JSONStreamAware MISSING_HOST = missing("host");
    public static final JSONStreamAware MISSING_DATE = missing("date");
    public static final JSONStreamAware MISSING_WEIGHT = missing("weight");
    public static final JSONStreamAware INCORRECT_HOST = incorrect("host", "(the length exceeds 100 chars limit)");
    public static final JSONStreamAware INCORRECT_WEIGHT = incorrect("weight");
    public static final JSONStreamAware INCORRECT_DATE = incorrect("date");
    public static final JSONStreamAware INCORRECT_WHITELIST = incorrect("whitelist");
    public static final JSONStreamAware MISSING_SIGNATURE_HASH = missing("signatureHash");
    public static final JSONStreamAware INCORRECT_HEIGHT = incorrect("height");
    public static final JSONStreamAware MISSING_HEIGHT = missing("height");
    public static final JSONStreamAware INCORRECT_ADMIN_PASSWORD = incorrect("adminPassword", "(the specified password does not match spa.adminPassword)");
    public static final JSONStreamAware LOCKED_ADMIN_PASSWORD = incorrect("adminPassword", "(locked for 1 hour, too many incorrect password attempts)");
    public static final JSONStreamAware OVERFLOW = error("overflow");
    public static final JSONStreamAware INCORRECT_LINKED_FULL_HASH = incorrect("phasingLinkedFullHash");
    public static final JSONStreamAware INCORRECT_HASH_ALGORITHM = incorrect("hashAlgorithm");
    public static final JSONStreamAware MISSING_SECRET = missing("secret");
    public static final JSONStreamAware INCORRECT_SECRET = incorrect("secret");
    public static final JSONStreamAware INCORRECT_EC_BLOCK = incorrect("ecBlockId", "ecBlockId does not match the block id at ecBlockHeight");

    public static final JSONStreamAware NOT_ENOUGH_FUNDS;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 6);
        response.put("errorDescription", "Not enough funds");
        NOT_ENOUGH_FUNDS = JSON.prepare(response);
    }

    public static final JSONStreamAware NOT_ENOUGH_ASSETS;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 6);
        response.put("errorDescription", "Not enough assets");
        NOT_ENOUGH_ASSETS = JSON.prepare(response);
    }

    public static final JSONStreamAware ASSET_NOT_ISSUED_YET;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 6);
        response.put("errorDescription", "Asset not issued yet");
        ASSET_NOT_ISSUED_YET = JSON.prepare(response);
    }

    public static final JSONStreamAware NOT_ENOUGH_CURRENCY;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 6);
        response.put("errorDescription", "Not enough currency");
        NOT_ENOUGH_CURRENCY = JSON.prepare(response);
    }

    public static final JSONStreamAware ERROR_NOT_ALLOWED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 7);
        response.put("errorDescription", "Not allowed");
        ERROR_NOT_ALLOWED = JSON.prepare(response);
    }

    public static final JSONStreamAware ERROR_DISABLED;
    static {
        JSONObject response  = new JSONObject();
        response.put("errorCode", 16);
        response.put("errorDescription", "This API has been disabled");
        ERROR_DISABLED = JSON.prepare(response);
    }

    public static final JSONStreamAware ERROR_INCORRECT_REQUEST;
    static {
        JSONObject response  = new JSONObject();
        response.put("errorCode", 1);
        response.put("errorDescription", "Incorrect request");
        ERROR_INCORRECT_REQUEST = JSON.prepare(response);
    }

    public static final JSONStreamAware NOT_FORGING;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Account is not forging");
        NOT_FORGING = JSON.prepare(response);
    }

    public static final JSONStreamAware POST_REQUIRED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 1);
        response.put("errorDescription", "This request is only accepted using POST!");
        POST_REQUIRED = JSON.prepare(response);
    }

    public static final JSONStreamAware FEATURE_NOT_AVAILABLE;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 9);
        response.put("errorDescription", "Feature not available");
        FEATURE_NOT_AVAILABLE = JSON.prepare(response);
    }

    public static final JSONStreamAware DECRYPTION_FAILED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Decryption failed");
        DECRYPTION_FAILED = JSON.prepare(response);
    }

    public static final JSONStreamAware ALREADY_DELIVERED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Purchase already delivered");
        ALREADY_DELIVERED = JSON.prepare(response);
    }

    public static final JSONStreamAware DUPLICATE_REFUND;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Refund already sent");
        DUPLICATE_REFUND = JSON.prepare(response);
    }

    public static final JSONStreamAware NO_MESSAGE;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "No attached message found");
        NO_MESSAGE = JSON.prepare(response);
    }

    public static final JSONStreamAware HEIGHT_NOT_AVAILABLE;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Requested height not available");
        HEIGHT_NOT_AVAILABLE = JSON.prepare(response);
    }

    public static final JSONStreamAware NO_PASSWORD_IN_CONFIG;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Administrator's password is not configured. Please set spa.adminPassword");
        NO_PASSWORD_IN_CONFIG = JSON.prepare(response);
    }

    public static final JSONStreamAware HASHES_MISMATCH;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 10);
        response.put("errorDescription", "Hashes don't match. You should notify Jeff Garzik.");
        HASHES_MISMATCH = JSON.prepare(response);
    }

    public static final JSONStreamAware REQUIRED_BLOCK_NOT_FOUND;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 13);
        response.put("errorDescription", "Required block not found in the blockchain");
        REQUIRED_BLOCK_NOT_FOUND = JSON.prepare(response);
    }

    public static final JSONStreamAware REQUIRED_LAST_BLOCK_NOT_FOUND;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 14);
        response.put("errorDescription", "Current last block is different");
        REQUIRED_LAST_BLOCK_NOT_FOUND = JSON.prepare(response);
    }

    public static final JSONStreamAware MISSING_SECRET_PHRASE;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 3);
        response.put("errorDescription", "secretPhrase not specified or not submitted to the remote node");
        MISSING_SECRET_PHRASE = JSON.prepare(response);
    }

    public static final JSONStreamAware PROXY_MISSING_REQUEST_TYPE;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 17);
        response.put("errorDescription", "Proxy servlet needs requestType parameter in the URL query");
        PROXY_MISSING_REQUEST_TYPE = JSON.prepare(response);
    }

    public static final JSONStreamAware PROXY_SECRET_DATA_DETECTED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 18);
        response.put("errorDescription", "Proxied requests contains secret parameters");
        PROXY_SECRET_DATA_DETECTED = JSON.prepare(response);
    }

    public static final JSONStreamAware API_PROXY_NO_OPEN_API_PEERS;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 19);
        response.put("errorDescription", "No openAPI peers found");
        API_PROXY_NO_OPEN_API_PEERS = JSON.prepare(response);
    }

    public static final JSONStreamAware LIGHT_CLIENT_DISABLED_API;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 20);
        response.put("errorDescription", "This API is disabled when running as light client");
        LIGHT_CLIENT_DISABLED_API = JSON.prepare(response);
    }

    public static final JSONStreamAware PEER_NOT_CONNECTED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Peer not connected");
        PEER_NOT_CONNECTED = JSON.prepare(response);
    }

    public static final JSONStreamAware PEER_NOT_OPEN_API;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Peer is not providing open API");
        PEER_NOT_OPEN_API = JSON.prepare(response);
    }

    static JSONStreamAware missing(String... paramNames) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 3);
        if (paramNames.length == 1) {
            response.put("errorDescription", "\"" + paramNames[0] + "\"" + " not specified");
        } else {
            response.put("errorDescription", "At least one of " + Arrays.toString(paramNames) + " must be specified");
        }
        return JSON.prepare(response);
    }

    static JSONStreamAware either(String... paramNames) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 6);
        response.put("errorDescription", "Not more than one of " + Arrays.toString(paramNames) + " can be specified");
        return JSON.prepare(response);
    }

    static JSONStreamAware incorrect(String paramName) {
        return incorrect(paramName, null);
    }

    static JSONStreamAware incorrect(String paramName, String details) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 4);
        response.put("errorDescription", "Incorrect \"" + paramName + (details != null ? "\" " + details : "\""));
        return JSON.prepare(response);
    }

    static JSONStreamAware unknown(String objectName) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Unknown " + objectName);
        return JSON.prepare(response);
    }

    static JSONStreamAware unknownAccount(long id) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Unknown account");
        response.put("account", Long.toUnsignedString(id));
        response.put("accountSPA", Convert.spaAccount(id));
        return JSON.prepare(response);
    }

    static JSONStreamAware error(String error) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 11);
        response.put("errorDescription", error);
        return JSON.prepare(response);
    }

    public static final JSONStreamAware MONITOR_ALREADY_STARTED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Account monitor already started");
        MONITOR_ALREADY_STARTED = JSON.prepare(response);
    }

    public static final JSONStreamAware MONITOR_NOT_STARTED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Account monitor not started");
        MONITOR_NOT_STARTED = JSON.prepare(response);
    }

    private JSONResponses() {} // never

}
