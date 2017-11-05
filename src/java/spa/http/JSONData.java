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
import spa.AccountLedger;
import spa.AccountLedger.LedgerEntry;
import spa.Alias;
import spa.Appendix;
import spa.Block;
import spa.Constants;
import spa.Generator;
import spa.Spa;
import spa.TaggedData;
import spa.Token;
import spa.Transaction;
import spa.crypto.Crypto;
import spa.crypto.EncryptedData;
import spa.peer.Hallmark;
import spa.peer.Peer;
import spa.util.Convert;
import spa.util.Filter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Collections;
import java.util.Map;

public final class JSONData {

    static JSONObject alias(Alias alias) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", alias.getAccountId());
        json.put("aliasName", alias.getAliasName());
        json.put("aliasURI", alias.getAliasURI());
        json.put("timestamp", alias.getTimestamp());
        json.put("alias", Long.toUnsignedString(alias.getId()));
        Alias.Offer offer = Alias.getOffer(alias);
        if (offer != null) {
            json.put("priceAPL", String.valueOf(offer.getPriceAPL()));
            if (offer.getBuyerId() != 0) {
                json.put("buyer", Long.toUnsignedString(offer.getBuyerId()));
            }
        }
        return json;
    }

    static JSONObject accountBalance(Account account, boolean includeEffectiveBalance) {
        return accountBalance(account, includeEffectiveBalance, Spa.getBlockchain().getHeight());
    }

    static JSONObject accountBalance(Account account, boolean includeEffectiveBalance, int height) {
        JSONObject json = new JSONObject();
        if (account == null) {
            json.put("balanceAPL", "0");
            json.put("unconfirmedBalanceAPL", "0");
            json.put("forgedBalanceAPL", "0");
            if (includeEffectiveBalance) {
                json.put("effectiveBalanceSPA", "0");
                json.put("guaranteedBalanceAPL", "0");
            }
        } else {
            json.put("balanceAPL", String.valueOf(account.getBalanceAPL()));
            json.put("unconfirmedBalanceAPL", String.valueOf(account.getUnconfirmedBalanceAPL()));
            json.put("forgedBalanceAPL", String.valueOf(account.getForgedBalanceAPL()));
            if (includeEffectiveBalance) {
                json.put("effectiveBalanceSPA", account.getEffectiveBalanceSPA(height));
                json.put("guaranteedBalanceAPL", String.valueOf(account.getGuaranteedBalanceAPL(Constants.GUARANTEED_BALANCE_CONFIRMATIONS, height)));
            }
        }
        return json;
    }

    static JSONObject lessor(Account account, boolean includeEffectiveBalance) {
        JSONObject json = new JSONObject();
        Account.AccountLease accountLease = account.getAccountLease();
        if (accountLease.getCurrentLesseeId() != 0) {
            putAccount(json, "currentLessee", accountLease.getCurrentLesseeId());
            json.put("currentHeightFrom", String.valueOf(accountLease.getCurrentLeasingHeightFrom()));
            json.put("currentHeightTo", String.valueOf(accountLease.getCurrentLeasingHeightTo()));
            if (includeEffectiveBalance) {
                json.put("effectiveBalanceSPA", String.valueOf(account.getGuaranteedBalanceAPL() / Constants.ONE_SPA));
            }
        }
        if (accountLease.getNextLesseeId() != 0) {
            putAccount(json, "nextLessee", accountLease.getNextLesseeId());
            json.put("nextHeightFrom", String.valueOf(accountLease.getNextLeasingHeightFrom()));
            json.put("nextHeightTo", String.valueOf(accountLease.getNextLeasingHeightTo()));
        }
        return json;
    }

    static JSONObject accountAsset(Account.AccountAsset accountAsset, boolean includeAccount, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        if (includeAccount) {
            putAccount(json, "account", accountAsset.getAccountId());
        }
        json.put("asset", Long.toUnsignedString(accountAsset.getAssetId()));
        json.put("quantityQNT", String.valueOf(accountAsset.getQuantityQNT()));
        json.put("unconfirmedQuantityQNT", String.valueOf(accountAsset.getUnconfirmedQuantityQNT()));
        if (includeAssetInfo) {
//            putAssetInfo(json, accountAsset.getAssetId());
        }
        return json;
    }

    static JSONObject accountProperty(Account.AccountProperty accountProperty, boolean includeAccount, boolean includeSetter) {
        JSONObject json = new JSONObject();
        if (includeAccount) {
            putAccount(json, "recipient", accountProperty.getRecipientId());
        }
        if (includeSetter) {
            putAccount(json, "setter", accountProperty.getSetterId());
        }
        json.put("property", accountProperty.getProperty());
        json.put("value", accountProperty.getValue());
        return json;
    }

    static JSONObject block(Block block, boolean includeTransactions, boolean includeExecutedPhased) {
        JSONObject json = new JSONObject();
        json.put("block", block.getStringId());
        json.put("height", block.getHeight());
        putAccount(json, "generator", block.getGeneratorId());
        json.put("generatorPublicKey", Convert.toHexString(block.getGeneratorPublicKey()));
        json.put("timestamp", block.getTimestamp());
        json.put("numberOfTransactions", block.getTransactions().size());
        json.put("totalAmountAPL", String.valueOf(block.getTotalAmountAPL()));
        json.put("totalFeeAPL", String.valueOf(block.getTotalFeeAPL()));
        json.put("payloadLength", block.getPayloadLength());
        json.put("version", block.getVersion());
        json.put("baseTarget", Long.toUnsignedString(block.getBaseTarget()));
        json.put("cumulativeDifficulty", block.getCumulativeDifficulty().toString());
        if (block.getPreviousBlockId() != 0) {
            json.put("previousBlock", Long.toUnsignedString(block.getPreviousBlockId()));
        }
        if (block.getNextBlockId() != 0) {
            json.put("nextBlock", Long.toUnsignedString(block.getNextBlockId()));
        }
        json.put("payloadHash", Convert.toHexString(block.getPayloadHash()));
        json.put("generationSignature", Convert.toHexString(block.getGenerationSignature()));
        if (block.getVersion() > 1) {
            json.put("previousBlockHash", Convert.toHexString(block.getPreviousBlockHash()));
        }
        json.put("blockSignature", Convert.toHexString(block.getBlockSignature()));
        JSONArray transactions = new JSONArray();
        if (includeTransactions) {
            block.getTransactions().forEach(transaction -> transactions.add(transaction(transaction)));
        } else {
            block.getTransactions().forEach(transaction -> transactions.add(transaction.getStringId()));
        }
        json.put("transactions", transactions);
        return json;
    }

    static JSONObject encryptedData(EncryptedData encryptedData) {
        JSONObject json = new JSONObject();
        json.put("data", Convert.toHexString(encryptedData.getData()));
        json.put("nonce", Convert.toHexString(encryptedData.getNonce()));
        return json;
    }

    static JSONObject hallmark(Hallmark hallmark) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", Account.getId(hallmark.getPublicKey()));
        json.put("host", hallmark.getHost());
        json.put("port", hallmark.getPort());
        json.put("weight", hallmark.getWeight());
        String dateString = Hallmark.formatDate(hallmark.getDate());
        json.put("date", dateString);
        json.put("valid", hallmark.isValid());
        return json;
    }

    static JSONObject token(Token token) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", Account.getId(token.getPublicKey()));
        json.put("timestamp", token.getTimestamp());
        json.put("valid", token.isValid());
        return json;
    }

    static JSONObject peer(Peer peer) {
        JSONObject json = new JSONObject();
        json.put("address", peer.getHost());
        json.put("port", peer.getPort());
        json.put("state", peer.getState().ordinal());
        json.put("announcedAddress", peer.getAnnouncedAddress());
        json.put("shareAddress", peer.shareAddress());
        if (peer.getHallmark() != null) {
            json.put("hallmark", peer.getHallmark().getHallmarkString());
        }
        json.put("weight", peer.getWeight());
        json.put("downloadedVolume", peer.getDownloadedVolume());
        json.put("uploadedVolume", peer.getUploadedVolume());
        json.put("application", peer.getApplication());
        json.put("version", peer.getVersion());
        json.put("platform", peer.getPlatform());
        if (peer.getApiPort() != 0) {
            json.put("apiPort", peer.getApiPort());
        }
        if (peer.getApiSSLPort() != 0) {
            json.put("apiSSLPort", peer.getApiSSLPort());
        }
        json.put("blacklisted", peer.isBlacklisted());
        json.put("lastUpdated", peer.getLastUpdated());
        json.put("lastConnectAttempt", peer.getLastConnectAttempt());
        json.put("inbound", peer.isInbound());
        json.put("inboundWebSocket", peer.isInboundWebSocket());
        json.put("outboundWebSocket", peer.isOutboundWebSocket());
        if (peer.isBlacklisted()) {
            json.put("blacklistingCause", peer.getBlacklistingCause());
        }
        JSONArray servicesArray = new JSONArray();
        for (Peer.Service service : Peer.Service.values()) {
            if (peer.providesService(service)) {
                servicesArray.add(service.name());
            }
        }
        json.put("services", servicesArray);
        json.put("blockchainState", peer.getBlockchainState());
        return json;
    }

    static JSONObject unconfirmedTransaction(Transaction transaction) {
        return unconfirmedTransaction(transaction, null);
    }

    static JSONObject unconfirmedTransaction(Transaction transaction, Filter<Appendix> filter) {
        JSONObject json = new JSONObject();
        json.put("type", transaction.getType().getType());
        json.put("subtype", transaction.getType().getSubtype());
//        json.put("phased", transaction.getPhasing() != null);
        json.put("timestamp", transaction.getTimestamp());
        json.put("deadline", transaction.getDeadline());
        json.put("senderPublicKey", Convert.toHexString(transaction.getSenderPublicKey()));
        if (transaction.getRecipientId() != 0) {
            putAccount(json, "recipient", transaction.getRecipientId());
        }
        json.put("amountAPL", String.valueOf(transaction.getAmountAPL()));
        json.put("feeAPL", String.valueOf(transaction.getFeeAPL()));
        String referencedTransactionFullHash = transaction.getReferencedTransactionFullHash();
        if (referencedTransactionFullHash != null) {
            json.put("referencedTransactionFullHash", referencedTransactionFullHash);
        }
        byte[] signature = Convert.emptyToNull(transaction.getSignature());
        if (signature != null) {
            json.put("signature", Convert.toHexString(signature));
            json.put("signatureHash", Convert.toHexString(Crypto.sha256().digest(signature)));
            json.put("fullHash", transaction.getFullHash());
            json.put("transaction", transaction.getStringId());
        }
        JSONObject attachmentJSON = new JSONObject();
        if (filter == null) {
            for (Appendix appendage : transaction.getAppendages(true)) {
                attachmentJSON.putAll(appendage.getJSONObject());
            }
        } else {
            for (Appendix appendage : transaction.getAppendages(filter, true)) {
                attachmentJSON.putAll(appendage.getJSONObject());
            }
        }
        if (! attachmentJSON.isEmpty()) {
            for (Map.Entry entry : (Iterable<Map.Entry>) attachmentJSON.entrySet()) {
                if (entry.getValue() instanceof Long) {
                    entry.setValue(String.valueOf(entry.getValue()));
                }
            }
            json.put("attachment", attachmentJSON);
        }
        putAccount(json, "sender", transaction.getSenderId());
        json.put("height", transaction.getHeight());
        json.put("version", transaction.getVersion());
        if (transaction.getVersion() > 0) {
            json.put("ecBlockId", Long.toUnsignedString(transaction.getECBlockId()));
            json.put("ecBlockHeight", transaction.getECBlockHeight());
        }

        return json;
    }

    static JSONObject transaction(Transaction transaction) {
        return transaction(transaction, false);
    }

    static JSONObject transaction(Transaction transaction, boolean includePhasingResult) {
        JSONObject json = transaction(transaction, null);
        return json;
    }

    static JSONObject transaction(Transaction transaction, Filter<Appendix> filter) {
        JSONObject json = unconfirmedTransaction(transaction, filter);
        json.put("block", Long.toUnsignedString(transaction.getBlockId()));
        json.put("confirmations", Spa.getBlockchain().getHeight() - transaction.getHeight());
        json.put("blockTimestamp", transaction.getBlockTimestamp());
        json.put("transactionIndex", transaction.getIndex());
        return json;
    }

    static JSONObject generator(Generator generator, int elapsedTime) {
        JSONObject response = new JSONObject();
        long deadline = generator.getDeadline();
        putAccount(response, "account", generator.getAccountId());
        response.put("deadline", deadline);
        response.put("hitTime", generator.getHitTime());
        response.put("remaining", Math.max(deadline - elapsedTime, 0));
        return response;
    }

    static JSONObject taggedData(TaggedData taggedData, boolean includeData) {
        JSONObject json = new JSONObject();
        json.put("transaction", Long.toUnsignedString(taggedData.getId()));
        putAccount(json, "account", taggedData.getAccountId());
        json.put("name", taggedData.getName());
        json.put("description", taggedData.getDescription());
        json.put("tags", taggedData.getTags());
        JSONArray tagsJSON = new JSONArray();
        Collections.addAll(tagsJSON, taggedData.getParsedTags());
        json.put("parsedTags", tagsJSON);
        json.put("type", taggedData.getType());
        json.put("channel", taggedData.getChannel());
        json.put("filename", taggedData.getFilename());
        json.put("isText", taggedData.isText());
        if (includeData) {
            json.put("data", taggedData.isText() ? Convert.toString(taggedData.getData()) : Convert.toHexString(taggedData.getData()));
        }
        json.put("transactionTimestamp", taggedData.getTransactionTimestamp());
        json.put("blockTimestamp", taggedData.getBlockTimestamp());
        return json;
	}

    static JSONObject dataTag(TaggedData.Tag tag) {
        JSONObject json = new JSONObject();
        json.put("tag", tag.getTag());
        json.put("count", tag.getCount());
        return json;
    }

    static JSONObject apiRequestHandler(APIServlet.APIRequestHandler handler) {
        JSONObject json = new JSONObject();
        json.put("allowRequiredBlockParameters", handler.allowRequiredBlockParameters());
        if (handler.getFileParameter() != null) {
            json.put("fileParameter", handler.getFileParameter());
        }
        json.put("requireBlockchain", handler.requireBlockchain());
        json.put("requirePost", handler.requirePost());
        json.put("requirePassword", handler.requirePassword());
        json.put("requireFullClient", handler.requireFullClient());
        return json;
    }

    static void putPrunableAttachment(JSONObject json, Transaction transaction) {
        JSONObject prunableAttachment = transaction.getPrunableAttachmentJSON();
        if (prunableAttachment != null) {
            json.put("prunableAttachmentJSON", prunableAttachment);
        }
    }

    static void putException(JSONObject json, Exception e) {
        putException(json, e, "");
    }

    static void putException(JSONObject json, Exception e, String error) {
        json.put("errorCode", 4);
        if (error.length() > 0) {
            error += ": ";
        }
        json.put("error", e.toString());
        json.put("errorDescription", error + e.getMessage());
    }

    static void putAccount(JSONObject json, String name, long accountId) {
        json.put(name, Long.toUnsignedString(accountId));
        json.put(name + "SPA", Convert.spaAccount(accountId));
    }

    private static void putExpectedTransaction(JSONObject json, Transaction transaction) {
        json.put("height", Spa.getBlockchain().getHeight() + 1);
//        json.put("phased", transaction.getPhasing() != null);
        if (transaction.getBlockId() != 0) { // those values may be wrong for unconfirmed transactions
            json.put("transactionHeight", transaction.getHeight());
            json.put("confirmations", Spa.getBlockchain().getHeight() - transaction.getHeight());
        }
    }

    static void ledgerEntry(JSONObject json, LedgerEntry entry, boolean includeTransactions, boolean includeHoldingInfo) {
        putAccount(json, "account", entry.getAccountId());
        json.put("ledgerId", Long.toUnsignedString(entry.getLedgerId()));
        json.put("block", Long.toUnsignedString(entry.getBlockId()));
        json.put("height", entry.getHeight());
        json.put("timestamp", entry.getTimestamp());
        json.put("eventType", entry.getEvent().name());
        json.put("event", Long.toUnsignedString(entry.getEventId()));
        json.put("isTransactionEvent", entry.getEvent().isTransaction());
        json.put("change", String.valueOf(entry.getChange()));
        json.put("balance", String.valueOf(entry.getBalance()));
        AccountLedger.LedgerHolding ledgerHolding = entry.getHolding();
        if (ledgerHolding != null) {
            json.put("holdingType", ledgerHolding.name());
            if (entry.getHoldingId() != null) {
                json.put("holding", Long.toUnsignedString(entry.getHoldingId()));
            }
            if (includeHoldingInfo) {
                JSONObject holdingJson = null;
                if (ledgerHolding == AccountLedger.LedgerHolding.ASSET_BALANCE
                        || ledgerHolding == AccountLedger.LedgerHolding.UNCONFIRMED_ASSET_BALANCE) {
                    holdingJson = new JSONObject();
//                    putAssetInfo(holdingJson, entry.getHoldingId());
                } else if (ledgerHolding == AccountLedger.LedgerHolding.CURRENCY_BALANCE
                        || ledgerHolding == AccountLedger.LedgerHolding.UNCONFIRMED_CURRENCY_BALANCE) {
                    holdingJson = new JSONObject();
//                    putCurrencyInfo(holdingJson, entry.getHoldingId());
                }
                if (holdingJson != null) {
                    json.put("holdingInfo", holdingJson);
                }
            }
        }
        if (includeTransactions && entry.getEvent().isTransaction()) {
            Transaction transaction = Spa.getBlockchain().getTransaction(entry.getEventId());
            json.put("transaction", JSONData.transaction(transaction));
        }
    }

    private JSONData() {} // never

}
