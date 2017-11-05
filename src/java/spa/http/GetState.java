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

import spa.Account;
import spa.AccountRestrictions;
import spa.Alias;
import spa.Constants;
import spa.Generator;
import spa.Spa;
import spa.TaggedData;
import spa.peer.Peers;
import spa.util.UPnP;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;

public final class GetState extends APIServlet.APIRequestHandler {

    static final GetState instance = new GetState();

    private GetState() {
        super(new APITag[] {APITag.INFO}, "includeCounts", "adminPassword");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        JSONObject response = GetBlockchainStatus.instance.processRequest(req);

        if ("true".equalsIgnoreCase(req.getParameter("includeCounts")) && API.checkPassword(req)) {
            response.put("numberOfTransactions", Spa.getBlockchain().getTransactionCount());
            response.put("numberOfAccounts", Account.getCount());
            response.put("numberOfAliases", Alias.getCount());
            response.put("numberOfTaggedData", TaggedData.getCount());
            response.put("numberOfDataTags", TaggedData.Tag.getTagCount());
            response.put("numberOfAccountLeases", Account.getAccountLeaseCount());
            response.put("numberOfActiveAccountLeases", Account.getActiveLeaseCount());
        }
        response.put("numberOfPeers", Peers.getAllPeers().size());
        response.put("numberOfActivePeers", Peers.getActivePeers().size());
        response.put("numberOfUnlockedAccounts", Generator.getAllGenerators().size());
        response.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        response.put("maxMemory", Runtime.getRuntime().maxMemory());
        response.put("totalMemory", Runtime.getRuntime().totalMemory());
        response.put("freeMemory", Runtime.getRuntime().freeMemory());
        response.put("peerPort", Peers.getDefaultPeerPort());
        response.put("isOffline", Constants.isOffline);
        response.put("needsAdminPassword", !API.disableAdminPassword);
        InetAddress externalAddress = UPnP.getExternalAddress();
        if (externalAddress != null) {
            response.put("upnpExternalAddress", externalAddress.getHostAddress());
        }
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

}
