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

import spa.Account.ControlType;
import spa.AccountLedger.LedgerEvent;
import spa.Attachment.AbstractAttachment;
import spa.SpaException.ValidationException;
import spa.util.Convert;
import spa.util.Logger;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public abstract class TransactionType {

    private static final byte TYPE_PAYMENT = 0;
    private static final byte TYPE_MESSAGING = 1;
    private static final byte TYPE_ACCOUNT_CONTROL = 4;
    private static final byte TYPE_DATA = 6;

    private static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;

    private static final byte SUBTYPE_MESSAGING_ARBITRARY_MESSAGE = 0;
    private static final byte SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT = 1;
    private static final byte SUBTYPE_MESSAGING_POLL_CREATION = 2;
    private static final byte SUBTYPE_MESSAGING_VOTE_CASTING = 3;
    private static final byte SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT = 4;
    private static final byte SUBTYPE_MESSAGING_ACCOUNT_INFO = 5;
    private static final byte SUBTYPE_MESSAGING_ALIAS_SELL = 6;
    private static final byte SUBTYPE_MESSAGING_ALIAS_BUY = 7;
    private static final byte SUBTYPE_MESSAGING_ALIAS_DELETE = 8;
    private static final byte SUBTYPE_MESSAGING_PHASING_VOTE_CASTING = 9;
    private static final byte SUBTYPE_MESSAGING_ACCOUNT_PROPERTY = 10;
    private static final byte SUBTYPE_MESSAGING_ACCOUNT_PROPERTY_DELETE = 11;

    private static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;
    private static final byte SUBTYPE_ACCOUNT_CONTROL_PHASING_ONLY = 1;

    private static final byte SUBTYPE_DATA_TAGGED_DATA_UPLOAD = 0;
    private static final byte SUBTYPE_DATA_TAGGED_DATA_EXTEND = 1;

    public static TransactionType findTransactionType(byte type, byte subtype) {
        switch (type) {
            case TYPE_PAYMENT:
                switch (subtype) {
                    case SUBTYPE_PAYMENT_ORDINARY_PAYMENT:
                        return Payment.ORDINARY;
                    default:
                        return null;
                }
            case TYPE_MESSAGING:
                switch (subtype) {
                    case SUBTYPE_MESSAGING_ARBITRARY_MESSAGE:
                        return Messaging.ARBITRARY_MESSAGE;
                    case SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT:
                        return Messaging.ALIAS_ASSIGNMENT;
                    case SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT:
                        return Messaging.HUB_ANNOUNCEMENT;
                    case SUBTYPE_MESSAGING_ACCOUNT_INFO:
                        return Messaging.ACCOUNT_INFO;
                    case SUBTYPE_MESSAGING_ALIAS_SELL:
                        return Messaging.ALIAS_SELL;
                    case SUBTYPE_MESSAGING_ALIAS_BUY:
                        return Messaging.ALIAS_BUY;
                    case SUBTYPE_MESSAGING_ALIAS_DELETE:
                        return Messaging.ALIAS_DELETE;
                    case SUBTYPE_MESSAGING_ACCOUNT_PROPERTY:
                        return Messaging.ACCOUNT_PROPERTY;
                    case SUBTYPE_MESSAGING_ACCOUNT_PROPERTY_DELETE:
                        return Messaging.ACCOUNT_PROPERTY_DELETE;
                    default:
                        return null;
                }
            case TYPE_ACCOUNT_CONTROL:
                switch (subtype) {
                    case SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING:
                        return TransactionType.AccountControl.EFFECTIVE_BALANCE_LEASING;
                    default:
                        return null;
                }
            case TYPE_DATA:
                switch (subtype) {
                    case SUBTYPE_DATA_TAGGED_DATA_UPLOAD:
                        return Data.TAGGED_DATA_UPLOAD;
                    case SUBTYPE_DATA_TAGGED_DATA_EXTEND:
                        return Data.TAGGED_DATA_EXTEND;
                    default:
                        return null;
                }
            default:
                return null;
        }
    }


    TransactionType() {}

    public abstract byte getType();

    public abstract byte getSubtype();

    public abstract LedgerEvent getLedgerEvent();

    abstract Attachment.AbstractAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException;

    abstract Attachment.AbstractAttachment parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException;

    abstract void validateAttachment(Transaction transaction) throws SpaException.ValidationException;

    // return false iff double spending
    final boolean applyUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        long amountAPL = transaction.getAmountAPL();
        long feeAPL = transaction.getFeeAPL();
        if (transaction.referencedTransactionFullHash() != null
                && transaction.getTimestamp() > Constants.REFERENCED_TRANSACTION_FULL_HASH_BLOCK_TIMESTAMP) {
            feeAPL = Math.addExact(feeAPL, Constants.UNCONFIRMED_POOL_DEPOSIT_APL);
        }
        long totalAmountAPL = Math.addExact(amountAPL, feeAPL);
        if (senderAccount.getUnconfirmedBalanceAPL() < totalAmountAPL
                && !(transaction.getTimestamp() == 0 && Arrays.equals(transaction.getSenderPublicKey(), Genesis.CREATOR_PUBLIC_KEY))) {
            return false;
        }
        senderAccount.addToUnconfirmedBalanceAPL(getLedgerEvent(), transaction.getId(), -amountAPL, -feeAPL);
        if (!applyAttachmentUnconfirmed(transaction, senderAccount)) {
            senderAccount.addToUnconfirmedBalanceAPL(getLedgerEvent(), transaction.getId(), amountAPL, feeAPL);
            return false;
        }
        return true;
    }

    abstract boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

    final void apply(TransactionImpl transaction, Account senderAccount, Account recipientAccount) {
        long amount = transaction.getAmountAPL();
        long transactionId = transaction.getId();
        if (!transaction.attachmentIsPhased()) {
            senderAccount.addToBalanceAPL(getLedgerEvent(), transactionId, -amount, -transaction.getFeeAPL());
        } else {
            senderAccount.addToBalanceAPL(getLedgerEvent(), transactionId, -amount);
        }
        if (recipientAccount != null) {
            recipientAccount.addToBalanceAndUnconfirmedBalanceAPL(getLedgerEvent(), transactionId, amount);
        }
        applyAttachment(transaction, senderAccount, recipientAccount);
    }

    abstract void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount);

    final void undoUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        undoAttachmentUnconfirmed(transaction, senderAccount);
        senderAccount.addToUnconfirmedBalanceAPL(getLedgerEvent(), transaction.getId(),
                transaction.getAmountAPL(), transaction.getFeeAPL());
        if (transaction.referencedTransactionFullHash() != null
                && transaction.getTimestamp() > Constants.REFERENCED_TRANSACTION_FULL_HASH_BLOCK_TIMESTAMP) {
            senderAccount.addToUnconfirmedBalanceAPL(getLedgerEvent(), transaction.getId(), 0,
                    Constants.UNCONFIRMED_POOL_DEPOSIT_APL);
        }
    }

    abstract void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

    boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    // isBlockDuplicate and isDuplicate share the same duplicates map, but isBlockDuplicate check is done first
    boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    static boolean isDuplicate(TransactionType uniqueType, String key, Map<TransactionType, Map<String, Integer>> duplicates, boolean exclusive) {
        return isDuplicate(uniqueType, key, duplicates, exclusive ? 0 : Integer.MAX_VALUE);
    }

    static boolean isDuplicate(TransactionType uniqueType, String key, Map<TransactionType, Map<String, Integer>> duplicates, int maxCount) {
        Map<String,Integer> typeDuplicates = duplicates.get(uniqueType);
        if (typeDuplicates == null) {
            typeDuplicates = new HashMap<>();
            duplicates.put(uniqueType, typeDuplicates);
        }
        Integer currentCount = typeDuplicates.get(key);
        if (currentCount == null) {
            typeDuplicates.put(key, maxCount > 0 ? 1 : 0);
            return false;
        }
        if (currentCount == 0) {
            return true;
        }
        if (currentCount < maxCount) {
            typeDuplicates.put(key, currentCount + 1);
            return false;
        }
        return true;
    }

    boolean isPruned(long transactionId) {
        return false;
    }

    public abstract boolean canHaveRecipient();

    public boolean mustHaveRecipient() {
        return canHaveRecipient();
    }

    public abstract boolean isPhasingSafe();

    public boolean isPhasable() {
        return true;
    }

    Fee getBaselineFee(Transaction transaction) {
        return Fee.DEFAULT_FEE;
    }

    Fee getNextFee(Transaction transaction) {
        return getBaselineFee(transaction);
    }

    int getBaselineFeeHeight() {
        return 1;
    }

    int getNextFeeHeight() {
        return Integer.MAX_VALUE;
    }

    long[] getBackFees(Transaction transaction) {
        return Convert.EMPTY_LONG;
    }

    public abstract String getName();

    @Override
    public final String toString() {
        return getName() + " type: " + getType() + ", subtype: " + getSubtype();
    }

    public static abstract class Payment extends TransactionType {

        private Payment() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_PAYMENT;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            if (recipientAccount == null) {
                Account.getAccount(Genesis.CREATOR_ID).addToBalanceAndUnconfirmedBalanceAPL(getLedgerEvent(),
                        transaction.getId(), transaction.getAmountAPL());
            }
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        public final boolean canHaveRecipient() {
            return true;
        }

        @Override
        public final boolean isPhasingSafe() {
            return true;
        }

        public static final TransactionType ORDINARY = new Payment() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT;
            }

            @Override
            public final LedgerEvent getLedgerEvent() {
                return LedgerEvent.ORDINARY_PAYMENT;
            }

            @Override
            public String getName() {
                return "OrdinaryPayment";
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
                return Attachment.ORDINARY_PAYMENT;
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException {
                return Attachment.ORDINARY_PAYMENT;
            }

            @Override
            void validateAttachment(Transaction transaction) throws SpaException.ValidationException {
                if (transaction.getAmountAPL() <= 0 || transaction.getAmountAPL() >= Constants.MAX_BALANCE_APL) {
                    throw new SpaException.NotValidException("Invalid ordinary payment");
                }
            }

        };

    }

    public static abstract class Messaging extends TransactionType {

        private Messaging() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_MESSAGING;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        public final static TransactionType ARBITRARY_MESSAGE = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ARBITRARY_MESSAGE;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ARBITRARY_MESSAGE;
            }

            @Override
            public String getName() {
                return "ArbitraryMessage";
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
                return Attachment.ARBITRARY_MESSAGE;
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException {
                return Attachment.ARBITRARY_MESSAGE;
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            }

            @Override
            void validateAttachment(Transaction transaction) throws SpaException.ValidationException {
                Attachment attachment = transaction.getAttachment();
                if (transaction.getAmountAPL() != 0) {
                    throw new SpaException.NotValidException("Invalid arbitrary message: " + attachment.getJSONObject());
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID && Spa.getBlockchain().getHeight() > Constants.MONETARY_SYSTEM_BLOCK) {
                    throw new SpaException.NotValidException("Sending messages to Genesis not allowed.");
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean mustHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

        public static final TransactionType ALIAS_ASSIGNMENT = new Messaging() {

            private final Fee ALIAS_FEE = new Fee.SizeBasedFee(2 * Constants.ONE_SPA, 2 * Constants.ONE_SPA, 32) {
                @Override
                public int getSize(TransactionImpl transaction, Appendix appendage) {
                    Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
                    return attachment.getAliasName().length() + attachment.getAliasURI().length();
                }
            };

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ALIAS_ASSIGNMENT;
            }

            @Override
            public String getName() {
                return "AliasAssignment";
            }

            @Override
            Fee getBaselineFee(Transaction transaction) {
                return ALIAS_FEE;
            }

            @Override
            Attachment.MessagingAliasAssignment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
                return new Attachment.MessagingAliasAssignment(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAliasAssignment parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException {
                return new Attachment.MessagingAliasAssignment(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
                Alias.addOrUpdateAlias(transaction, attachment);
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
                return isDuplicate(Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
            }

            @Override
            boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                return Spa.getBlockchain().getHeight() > Constants.SHUFFLING_BLOCK
                        && Alias.getAlias(((Attachment.MessagingAliasAssignment) transaction.getAttachment()).getAliasName()) == null
                        && isDuplicate(Messaging.ALIAS_ASSIGNMENT, "", duplicates, true);
            }

            @Override
            void validateAttachment(Transaction transaction) throws SpaException.ValidationException {
                Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
                if (attachment.getAliasName().length() == 0
                        || attachment.getAliasName().length() > Constants.MAX_ALIAS_LENGTH
                        || attachment.getAliasURI().length() > Constants.MAX_ALIAS_URI_LENGTH) {
                    throw new SpaException.NotValidException("Invalid alias assignment: " + attachment.getJSONObject());
                }
                String normalizedAlias = attachment.getAliasName().toLowerCase();
                for (int i = 0; i < normalizedAlias.length(); i++) {
                    if (Constants.ALPHABET.indexOf(normalizedAlias.charAt(i)) < 0) {
                        throw new SpaException.NotValidException("Invalid alias name: " + normalizedAlias);
                    }
                }
                Alias alias = Alias.getAlias(normalizedAlias);
                if (alias != null && alias.getAccountId() != transaction.getSenderId()) {
                    throw new SpaException.NotCurrentlyValidException("Alias already owned by another account: " + normalizedAlias);
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

        public static final TransactionType ALIAS_SELL = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ALIAS_SELL;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ALIAS_SELL;
            }
            @Override
            public String getName() {
                return "AliasSell";
            }

            @Override
            Attachment.MessagingAliasSell parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
                return new Attachment.MessagingAliasSell(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAliasSell parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException {
                return new Attachment.MessagingAliasSell(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingAliasSell attachment = (Attachment.MessagingAliasSell) transaction.getAttachment();
                Alias.sellAlias(transaction, attachment);
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.MessagingAliasSell attachment = (Attachment.MessagingAliasSell) transaction.getAttachment();
                // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
                return isDuplicate(Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
            }

            @Override
            void validateAttachment(Transaction transaction) throws SpaException.ValidationException {
                if (transaction.getAmountAPL() != 0) {
                    throw new SpaException.NotValidException("Invalid sell alias transaction: " +
                            transaction.getJSONObject());
                }
                final Attachment.MessagingAliasSell attachment =
                        (Attachment.MessagingAliasSell) transaction.getAttachment();
                final String aliasName = attachment.getAliasName();
                if (aliasName == null || aliasName.length() == 0) {
                    throw new SpaException.NotValidException("Missing alias name");
                }
                long priceAPL = attachment.getPriceAPL();
                if (priceAPL < 0 || priceAPL > Constants.MAX_BALANCE_APL) {
                    throw new SpaException.NotValidException("Invalid alias sell price: " + priceAPL);
                }
                if (priceAPL == 0) {
                    if (Genesis.CREATOR_ID == transaction.getRecipientId()) {
                        throw new SpaException.NotValidException("Transferring aliases to Genesis account not allowed");
                    } else if (transaction.getRecipientId() == 0) {
                        throw new SpaException.NotValidException("Missing alias transfer recipient");
                    }
                }
                final Alias alias = Alias.getAlias(aliasName);
                if (alias == null) {
                    throw new SpaException.NotCurrentlyValidException("No such alias: " + aliasName);
                } else if (alias.getAccountId() != transaction.getSenderId()) {
                    throw new SpaException.NotCurrentlyValidException("Alias doesn't belong to sender: " + aliasName);
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new SpaException.NotValidException("Selling alias to Genesis not allowed");
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean mustHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

        public static final TransactionType ALIAS_BUY = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ALIAS_BUY;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ALIAS_BUY;
            }

            @Override
            public String getName() {
                return "AliasBuy";
            }

            @Override
            Attachment.MessagingAliasBuy parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
                return new Attachment.MessagingAliasBuy(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAliasBuy parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException {
                return new Attachment.MessagingAliasBuy(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                final Attachment.MessagingAliasBuy attachment =
                        (Attachment.MessagingAliasBuy) transaction.getAttachment();
                final String aliasName = attachment.getAliasName();
                Alias.changeOwner(transaction.getSenderId(), aliasName);
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.MessagingAliasBuy attachment = (Attachment.MessagingAliasBuy) transaction.getAttachment();
                // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
                return isDuplicate(Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
            }

            @Override
            void validateAttachment(Transaction transaction) throws SpaException.ValidationException {
                final Attachment.MessagingAliasBuy attachment =
                        (Attachment.MessagingAliasBuy) transaction.getAttachment();
                final String aliasName = attachment.getAliasName();
                final Alias alias = Alias.getAlias(aliasName);
                if (alias == null) {
                    throw new SpaException.NotCurrentlyValidException("No such alias: " + aliasName);
                } else if (alias.getAccountId() != transaction.getRecipientId()) {
                    throw new SpaException.NotCurrentlyValidException("Alias is owned by account other than recipient: "
                            + Long.toUnsignedString(alias.getAccountId()));
                }
                Alias.Offer offer = Alias.getOffer(alias);
                if (offer == null) {
                    throw new SpaException.NotCurrentlyValidException("Alias is not for sale: " + aliasName);
                }
                if (transaction.getAmountAPL() < offer.getPriceAPL()) {
                    String msg = "Price is too low for: " + aliasName + " ("
                            + transaction.getAmountAPL() + " < " + offer.getPriceAPL() + ")";
                    throw new SpaException.NotCurrentlyValidException(msg);
                }
                if (offer.getBuyerId() != 0 && offer.getBuyerId() != transaction.getSenderId()) {
                    throw new SpaException.NotCurrentlyValidException("Wrong buyer for " + aliasName + ": "
                            + Long.toUnsignedString(transaction.getSenderId()) + " expected: "
                            + Long.toUnsignedString(offer.getBuyerId()));
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

        public static final TransactionType ALIAS_DELETE = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ALIAS_DELETE;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ALIAS_DELETE;
            }

            @Override
            public String getName() {
                return "AliasDelete";
            }

            @Override
            Attachment.MessagingAliasDelete parseAttachment(final ByteBuffer buffer, final byte transactionVersion) throws SpaException.NotValidException {
                return new Attachment.MessagingAliasDelete(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAliasDelete parseAttachment(final JSONObject attachmentData) throws SpaException.NotValidException {
                return new Attachment.MessagingAliasDelete(attachmentData);
            }

            @Override
            void applyAttachment(final Transaction transaction, final Account senderAccount, final Account recipientAccount) {
                final Attachment.MessagingAliasDelete attachment =
                        (Attachment.MessagingAliasDelete) transaction.getAttachment();
                Alias.deleteAlias(attachment.getAliasName());
            }

            @Override
            boolean isDuplicate(final Transaction transaction, final Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.MessagingAliasDelete attachment = (Attachment.MessagingAliasDelete) transaction.getAttachment();
                // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
                return isDuplicate(Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
            }

            @Override
            void validateAttachment(final Transaction transaction) throws SpaException.ValidationException {
                final Attachment.MessagingAliasDelete attachment =
                        (Attachment.MessagingAliasDelete) transaction.getAttachment();
                final String aliasName = attachment.getAliasName();
                if (aliasName == null || aliasName.length() == 0) {
                    throw new SpaException.NotValidException("Missing alias name");
                }
                final Alias alias = Alias.getAlias(aliasName);
                if (alias == null) {
                    throw new SpaException.NotCurrentlyValidException("No such alias: " + aliasName);
                } else if (alias.getAccountId() != transaction.getSenderId()) {
                    throw new SpaException.NotCurrentlyValidException("Alias doesn't belong to sender: " + aliasName);
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

        public static final TransactionType HUB_ANNOUNCEMENT = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.HUB_ANNOUNCEMENT;
            }

            @Override
            public String getName() {
                return "HubAnnouncement";
            }

            @Override
            Attachment.MessagingHubAnnouncement parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
                return new Attachment.MessagingHubAnnouncement(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingHubAnnouncement parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException {
                return new Attachment.MessagingHubAnnouncement(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingHubAnnouncement attachment = (Attachment.MessagingHubAnnouncement) transaction.getAttachment();
                Hub.addOrUpdateHub(transaction, attachment);
            }

            @Override
            void validateAttachment(Transaction transaction) throws SpaException.ValidationException {
                Attachment.MessagingHubAnnouncement attachment = (Attachment.MessagingHubAnnouncement) transaction.getAttachment();
                if (attachment.getMinFeePerByteAPL() < 0 || attachment.getMinFeePerByteAPL() > Constants.MAX_BALANCE_APL
                        || attachment.getUris().length > Constants.MAX_HUB_ANNOUNCEMENT_URIS) {
                    // cfb: "0" is allowed to show that another way to determine the min fee should be used
                    throw new SpaException.NotValidException("Invalid hub terminal announcement: " + attachment.getJSONObject());
                }
                for (String uri : attachment.getUris()) {
                    if (uri.length() > Constants.MAX_HUB_ANNOUNCEMENT_URI_LENGTH) {
                        throw new SpaException.NotValidException("Invalid URI length: " + uri.length());
                    }
                    //also check URI validity here?
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

        };

        public static final Messaging ACCOUNT_INFO = new Messaging() {

            private final Fee ACCOUNT_INFO_FEE = new Fee.SizeBasedFee(Constants.ONE_SPA, 2 * Constants.ONE_SPA, 32) {
                @Override
                public int getSize(TransactionImpl transaction, Appendix appendage) {
                    Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo) transaction.getAttachment();
                    return attachment.getName().length() + attachment.getDescription().length();
                }
            };

            @Override
            public byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ACCOUNT_INFO;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_INFO;
            }

            @Override
            public String getName() {
                return "AccountInfo";
            }

            @Override
            Fee getBaselineFee(Transaction transaction) {
                return ACCOUNT_INFO_FEE;
            }

            @Override
            Attachment.MessagingAccountInfo parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
                return new Attachment.MessagingAccountInfo(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAccountInfo parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException {
                return new Attachment.MessagingAccountInfo(attachmentData);
            }

            @Override
            void validateAttachment(Transaction transaction) throws SpaException.ValidationException {
                Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo)transaction.getAttachment();
                if (attachment.getName().length() > Constants.MAX_ACCOUNT_NAME_LENGTH
                        || attachment.getDescription().length() > Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH) {
                    throw new SpaException.NotValidException("Invalid account info issuance: " + attachment.getJSONObject());
                }
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo) transaction.getAttachment();
                senderAccount.setAccountInfo(attachment.getName(), attachment.getDescription());
            }

            @Override
            boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                return Spa.getBlockchain().getHeight() > Constants.SHUFFLING_BLOCK
                        && isDuplicate(Messaging.ACCOUNT_INFO, getName(), duplicates, true);
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

        };

        public static final Messaging ACCOUNT_PROPERTY = new Messaging() {

            private final Fee ACCOUNT_PROPERTY_FEE = new Fee.SizeBasedFee(Constants.ONE_SPA, Constants.ONE_SPA, 32) {
                @Override
                public int getSize(TransactionImpl transaction, Appendix appendage) {
                    Attachment.MessagingAccountProperty attachment = (Attachment.MessagingAccountProperty) transaction.getAttachment();
                    return attachment.getValue().length();
                }
            };

            @Override
            public byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ACCOUNT_PROPERTY;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_PROPERTY;
            }

            @Override
            public String getName() {
                return "AccountProperty";
            }

            @Override
            Fee getBaselineFee(Transaction transaction) {
                return ACCOUNT_PROPERTY_FEE;
            }

            @Override
            Attachment.MessagingAccountProperty parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
                return new Attachment.MessagingAccountProperty(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAccountProperty parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException {
                return new Attachment.MessagingAccountProperty(attachmentData);
            }

            @Override
            void validateAttachment(Transaction transaction) throws SpaException.ValidationException {
                Attachment.MessagingAccountProperty attachment = (Attachment.MessagingAccountProperty)transaction.getAttachment();
                if (attachment.getProperty().length() > Constants.MAX_ACCOUNT_PROPERTY_NAME_LENGTH
                        || attachment.getProperty().length() == 0
                        || attachment.getValue().length() > Constants.MAX_ACCOUNT_PROPERTY_VALUE_LENGTH) {
                    throw new SpaException.NotValidException("Invalid account property: " + attachment.getJSONObject());
                }
                if (transaction.getAmountAPL() != 0) {
                    throw new SpaException.NotValidException("Account property transaction cannot be used to send SPA");
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new SpaException.NotValidException("Setting Genesis account properties not allowed");
                }
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingAccountProperty attachment = (Attachment.MessagingAccountProperty) transaction.getAttachment();
                recipientAccount.setProperty(transaction, senderAccount, attachment.getProperty(), attachment.getValue());
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

        };

        public static final Messaging ACCOUNT_PROPERTY_DELETE = new Messaging() {

            @Override
            public byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ACCOUNT_PROPERTY_DELETE;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_PROPERTY_DELETE;
            }

            @Override
            public String getName() {
                return "AccountPropertyDelete";
            }

            @Override
            Attachment.MessagingAccountPropertyDelete parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
                return new Attachment.MessagingAccountPropertyDelete(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAccountPropertyDelete parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException {
                return new Attachment.MessagingAccountPropertyDelete(attachmentData);
            }

            @Override
            void validateAttachment(Transaction transaction) throws SpaException.ValidationException {
                Attachment.MessagingAccountPropertyDelete attachment = (Attachment.MessagingAccountPropertyDelete)transaction.getAttachment();
                Account.AccountProperty accountProperty = Account.getProperty(attachment.getPropertyId());
                if (accountProperty == null) {
                    throw new SpaException.NotCurrentlyValidException("No such property " + Long.toUnsignedString(attachment.getPropertyId()));
                }
                if (accountProperty.getRecipientId() != transaction.getSenderId() && accountProperty.getSetterId() != transaction.getSenderId()) {
                    throw new SpaException.NotValidException("Account " + Long.toUnsignedString(transaction.getSenderId())
                            + " cannot delete property " + Long.toUnsignedString(attachment.getPropertyId()));
                }
                if (accountProperty.getRecipientId() != transaction.getRecipientId()) {
                    throw new SpaException.NotValidException("Account property " + Long.toUnsignedString(attachment.getPropertyId())
                            + " does not belong to " + Long.toUnsignedString(transaction.getRecipientId()));
                }
                if (transaction.getAmountAPL() != 0) {
                    throw new SpaException.NotValidException("Account property transaction cannot be used to send SPA");
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new SpaException.NotValidException("Deleting Genesis account properties not allowed");
                }
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingAccountPropertyDelete attachment = (Attachment.MessagingAccountPropertyDelete) transaction.getAttachment();
                senderAccount.deleteProperty(attachment.getPropertyId());
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

        };

    }

    public static abstract class AccountControl extends TransactionType {

        private AccountControl() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_ACCOUNT_CONTROL;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        public static final TransactionType EFFECTIVE_BALANCE_LEASING = new AccountControl() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
            }

            @Override
            public String getName() {
                return "EffectiveBalanceLeasing";
            }

            @Override
            Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
                return new Attachment.AccountControlEffectiveBalanceLeasing(buffer, transactionVersion);
            }

            @Override
            Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException {
                return new Attachment.AccountControlEffectiveBalanceLeasing(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing) transaction.getAttachment();
                Account.getAccount(transaction.getSenderId()).leaseEffectiveBalance(transaction.getRecipientId(), attachment.getPeriod());
            }

            @Override
            void validateAttachment(Transaction transaction) throws SpaException.ValidationException {
                Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing)transaction.getAttachment();
                if (transaction.getSenderId() == transaction.getRecipientId()) {
                    throw new SpaException.NotValidException("Account cannot lease balance to itself");
                }
                if (transaction.getAmountAPL() != 0) {
                    throw new SpaException.NotValidException("Transaction amount must be 0 for effective balance leasing");
                }
                if (attachment.getPeriod() < Constants.LEASING_DELAY || attachment.getPeriod() > 65535) {
                    throw new SpaException.NotValidException("Invalid effective balance leasing period: " + attachment.getPeriod());
                }
                byte[] recipientPublicKey = Account.getPublicKey(transaction.getRecipientId());
                if (recipientPublicKey == null && Spa.getBlockchain().getHeight() > Constants.PHASING_BLOCK) {
                    throw new SpaException.NotCurrentlyValidException("Invalid effective balance leasing: "
                            + " recipient account " + Long.toUnsignedString(transaction.getRecipientId()) + " not found or no public key published");
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new SpaException.NotValidException("Leasing to Genesis account not allowed");
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

        };
    }

    public static abstract class Data extends TransactionType {

        private static final Fee TAGGED_DATA_FEE = new Fee.SizeBasedFee(Constants.ONE_SPA, Constants.ONE_SPA /10) {
            @Override
            public int getSize(TransactionImpl transaction, Appendix appendix) {
                return appendix.getFullSize();
            }
        };

        private Data() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_DATA;
        }

        @Override
        final Fee getBaselineFee(Transaction transaction) {
            return TAGGED_DATA_FEE;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        public final boolean canHaveRecipient() {
            return false;
        }

        @Override
        public final boolean isPhasingSafe() {
            return false;
        }

        @Override
        public final boolean isPhasable() {
            return false;
        }

        public static final TransactionType TAGGED_DATA_UPLOAD = new Data() {

            @Override
            public byte getSubtype() {
                return SUBTYPE_DATA_TAGGED_DATA_UPLOAD;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.TAGGED_DATA_UPLOAD;
            }

            @Override
            Attachment.TaggedDataUpload parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
                return new Attachment.TaggedDataUpload(buffer, transactionVersion);
            }

            @Override
            Attachment.TaggedDataUpload parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException {
                return new Attachment.TaggedDataUpload(attachmentData);
            }

            @Override
            void validateAttachment(Transaction transaction) throws SpaException.ValidationException {
                Attachment.TaggedDataUpload attachment = (Attachment.TaggedDataUpload) transaction.getAttachment();
                if (attachment.getData() == null && Spa.getEpochTime() - transaction.getTimestamp() < Constants.MIN_PRUNABLE_LIFETIME) {
                    throw new SpaException.NotCurrentlyValidException("Data has been pruned prematurely");
                }
                if (attachment.getData() != null) {
                    if (attachment.getName().length() == 0 || attachment.getName().length() > Constants.MAX_TAGGED_DATA_NAME_LENGTH) {
                        throw new SpaException.NotValidException("Invalid name length: " + attachment.getName().length());
                    }
                    if (attachment.getDescription().length() > Constants.MAX_TAGGED_DATA_DESCRIPTION_LENGTH) {
                        throw new SpaException.NotValidException("Invalid description length: " + attachment.getDescription().length());
                    }
                    if (attachment.getTags().length() > Constants.MAX_TAGGED_DATA_TAGS_LENGTH) {
                        throw new SpaException.NotValidException("Invalid tags length: " + attachment.getTags().length());
                    }
                    if (attachment.getType().length() > Constants.MAX_TAGGED_DATA_TYPE_LENGTH) {
                        throw new SpaException.NotValidException("Invalid type length: " + attachment.getType().length());
                    }
                    if (attachment.getChannel().length() > Constants.MAX_TAGGED_DATA_CHANNEL_LENGTH) {
                        throw new SpaException.NotValidException("Invalid channel length: " + attachment.getChannel().length());
                    }
                    if (attachment.getFilename().length() > Constants.MAX_TAGGED_DATA_FILENAME_LENGTH) {
                        throw new SpaException.NotValidException("Invalid filename length: " + attachment.getFilename().length());
                    }
                    if (attachment.getData().length == 0 || attachment.getData().length > Constants.MAX_TAGGED_DATA_DATA_LENGTH) {
                        throw new SpaException.NotValidException("Invalid data length: " + attachment.getData().length);
                    }
                }
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.TaggedDataUpload attachment = (Attachment.TaggedDataUpload) transaction.getAttachment();
                TaggedData.add((TransactionImpl)transaction, attachment);
            }

            @Override
            public String getName() {
                return "TaggedDataUpload";
            }

            @Override
            boolean isPruned(long transactionId) {
                return TaggedData.isPruned(transactionId);
            }

        };

        public static final TransactionType TAGGED_DATA_EXTEND = new Data() {

            @Override
            public byte getSubtype() {
                return SUBTYPE_DATA_TAGGED_DATA_EXTEND;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.TAGGED_DATA_EXTEND;
            }

            @Override
            Attachment.TaggedDataExtend parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
                return new Attachment.TaggedDataExtend(buffer, transactionVersion);
            }

            @Override
            Attachment.TaggedDataExtend parseAttachment(JSONObject attachmentData) throws SpaException.NotValidException {
                return new Attachment.TaggedDataExtend(attachmentData);
            }

            @Override
            void validateAttachment(Transaction transaction) throws SpaException.ValidationException {
                Attachment.TaggedDataExtend attachment = (Attachment.TaggedDataExtend) transaction.getAttachment();
                if ((attachment.jsonIsPruned() || attachment.getData() == null) && Spa.getEpochTime() - transaction.getTimestamp() < Constants.MIN_PRUNABLE_LIFETIME) {
                    throw new SpaException.NotCurrentlyValidException("Data has been pruned prematurely");
                }
                TransactionImpl uploadTransaction = TransactionDb.findTransaction(attachment.getTaggedDataId(), Spa.getBlockchain().getHeight());
                if (uploadTransaction == null) {
                    throw new SpaException.NotCurrentlyValidException("No such tagged data upload " + Long.toUnsignedString(attachment.getTaggedDataId()));
                }
                if (uploadTransaction.getType() != TAGGED_DATA_UPLOAD) {
                    throw new SpaException.NotValidException("Transaction " + Long.toUnsignedString(attachment.getTaggedDataId())
                            + " is not a tagged data upload");
                }
                if (attachment.getData() != null) {
                    Attachment.TaggedDataUpload taggedDataUpload = (Attachment.TaggedDataUpload)uploadTransaction.getAttachment();
                    if (!Arrays.equals(attachment.getHash(), taggedDataUpload.getHash())) {
                        throw new SpaException.NotValidException("Hashes don't match! Extend hash: " + Convert.toHexString(attachment.getHash())
                                + " upload hash: " + Convert.toHexString(taggedDataUpload.getHash()));
                    }
                }
                TaggedData taggedData = TaggedData.getData(attachment.getTaggedDataId());
                if (taggedData != null && taggedData.getTransactionTimestamp() > Spa.getEpochTime() + 6 * Constants.MIN_PRUNABLE_LIFETIME) {
                    throw new SpaException.NotCurrentlyValidException("Data already extended, timestamp is " + taggedData.getTransactionTimestamp());
                }
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.TaggedDataExtend attachment = (Attachment.TaggedDataExtend) transaction.getAttachment();
                TaggedData.extend(transaction, attachment);
            }

            @Override
            public String getName() {
                return "TaggedDataExtend";
            }

            @Override
            boolean isPruned(long transactionId) {
                return false;
            }

        };

    }

}
