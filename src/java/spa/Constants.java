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

package spa;

import java.util.Calendar;
import java.util.TimeZone;

public final class Constants
{
    private Constants() {} // never

    public static final boolean isTestnet = Spa.getBooleanProperty("spa.isTestnet");
    public static final boolean isOffline = Spa.getBooleanProperty("spa.isOffline");
    public static final boolean isLightClient = Spa.getBooleanProperty("spa.isLightClient");

    public static final int MAX_NUMBER_OF_TRANSACTIONS = 888;
    public static final int MIN_TRANSACTION_SIZE = 176;
    public static final int MAX_PAYLOAD_LENGTH = MAX_NUMBER_OF_TRANSACTIONS * MIN_TRANSACTION_SIZE;
    public static final long MAX_BALANCE_SPA = 8888888888L;
    public static final long ONE_SPA = 100000000;
    public static final long MAX_BALANCE_APL = MAX_BALANCE_SPA * ONE_SPA;
    public static final long INITIAL_BASE_TARGET = 172938225; // 2^63 / ( 60 * 8888,888,888)
    public static final long MAX_BASE_TARGET = MAX_BALANCE_SPA * INITIAL_BASE_TARGET;
    public static final long MAX_BASE_TARGET_2 = INITIAL_BASE_TARGET * 50;
    public static final long MIN_BASE_TARGET = INITIAL_BASE_TARGET * 9 / 10;
    public static final int MIN_BLOCKTIME_LIMIT = 53;
    public static final int MAX_BLOCKTIME_LIMIT = 67;
    public static final int BASE_TARGET_GAMMA = 64;
    public static final int MAX_ROLLBACK = 720;
    public static final int GUARANTEED_BALANCE_CONFIRMATIONS = 1440;
    public static final int LEASING_DELAY = 1440;
    public static final long MIN_FORGING_BALANCE_APL = 888 * ONE_SPA;

    public static final int MAX_TIMEDRIFT = 15; // allow up to 15 s clock difference
    public static final int FORGING_DELAY = Spa.getIntProperty("spa.forgingDelay");
    public static final int FORGING_SPEEDUP = Spa.getIntProperty("spa.forgingSpeedup");

    public static final byte MAX_PHASING_VOTE_TRANSACTIONS = 10;

    public static final int MAX_ALIAS_URI_LENGTH = 1000;
    public static final int MAX_ALIAS_LENGTH = 100;

    public static final int MIN_PRUNABLE_LIFETIME = isTestnet ? 1440 * 60 : 14 * 1440 * 60;
    public static final int MAX_PRUNABLE_LIFETIME;
    public static final boolean ENABLE_PRUNING;
    static {
        int maxPrunableLifetime = Spa.getIntProperty("spa.maxPrunableLifetime");
        ENABLE_PRUNING = maxPrunableLifetime >= 0;
        MAX_PRUNABLE_LIFETIME = ENABLE_PRUNING ? Math.max(maxPrunableLifetime, MIN_PRUNABLE_LIFETIME) : Integer.MAX_VALUE;
    }
    public static final boolean INCLUDE_EXPIRED_PRUNABLE = Spa.getBooleanProperty("spa.includeExpiredPrunable");

    public static final int MAX_ACCOUNT_NAME_LENGTH = 100;
    public static final int MAX_ACCOUNT_DESCRIPTION_LENGTH = 1000;

    public static final int MAX_ACCOUNT_PROPERTY_NAME_LENGTH = 32;
    public static final int MAX_ACCOUNT_PROPERTY_VALUE_LENGTH = 160;

    public static final int MAX_DIVIDEND_PAYMENT_ROLLBACK = 1441;

    public static final int MAX_HUB_ANNOUNCEMENT_URIS = 100;
    public static final int MAX_HUB_ANNOUNCEMENT_URI_LENGTH = 1000;
    public static final long MIN_HUB_EFFECTIVE_BALANCE = 100000;

    public static final int MAX_TAGGED_DATA_NAME_LENGTH = 100;
    public static final int MAX_TAGGED_DATA_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_TAGGED_DATA_TAGS_LENGTH = 100;
    public static final int MAX_TAGGED_DATA_TYPE_LENGTH = 100;
    public static final int MAX_TAGGED_DATA_CHANNEL_LENGTH = 100;
    public static final int MAX_TAGGED_DATA_FILENAME_LENGTH = 100;
    public static final int MAX_TAGGED_DATA_DATA_LENGTH = 42 * 1024;

    public static final int TRANSPARENT_FORGING_BLOCK = 0;
    public static final int APL_BLOCK = 0;
    public static final int REFERENCED_TRANSACTION_FULL_HASH_BLOCK = APL_BLOCK;
    public static final int REFERENCED_TRANSACTION_FULL_HASH_BLOCK_TIMESTAMP = 1; // ????
    public static final int MAX_REFERENCED_TRANSACTION_TIMESPAN = 60 * 1440 * 60;
    public static final int DIGITAL_GOODS_STORE_BLOCK = 1;
    public static final int MONETARY_SYSTEM_BLOCK = 1;
    public static final int PHASING_BLOCK = 360;
    public static final int SHUFFLING_BLOCK = 360;

    public static final int LAST_CHECKSUM_BLOCK = -1;
    public static final int LAST_KNOWN_BLOCK = 0;

    public static final int[] MIN_VERSION = new int[] {1, 0, 0};
    public static final int[] MIN_PROXY_VERSION = new int[] {1, 0, 0};

    static final long UNCONFIRMED_POOL_DEPOSIT_APL = 100 * ONE_SPA;

    public static final boolean correctInvalidFees = Spa.getBooleanProperty("spa.correctInvalidFees");

    public static final long EPOCH_BEGINNING;
    static {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, 2017);
        calendar.set(Calendar.MONTH, Calendar.NOVEMBER);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        EPOCH_BEGINNING = calendar.getTimeInMillis();
    }

    public static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
}
