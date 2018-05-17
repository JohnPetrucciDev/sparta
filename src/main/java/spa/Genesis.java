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

import spa.util.Convert;

public final class Genesis
{
    public static final long GENESIS_BLOCK_ID = Long.parseUnsignedLong("18442393382139747590");

    public static final long CREATOR_ID = Long.parseUnsignedLong("10928131326576999708");

    public static final byte[] CREATOR_PUBLIC_KEY = Convert.parseHexString("59e7b4f25da56edd010d0c8b48374cade798afa60434f12c29794f0067f0bf6a");

    public static final long[] GENESIS_RECIPIENTS = {
            Long.parseUnsignedLong("2493852803425131099"),
            Long.parseUnsignedLong("14320879361344776120"),
            Long.parseUnsignedLong("2293823112475660450"),
            Long.parseUnsignedLong("10329514730617193232"),
            Long.parseUnsignedLong("7894068279220640975"),
            Long.parseUnsignedLong("10263303497855355163"),
            Long.parseUnsignedLong("4028405726484572256"),
            Long.parseUnsignedLong("9493070465178532257")
    };

    private Genesis() {} // never
}
