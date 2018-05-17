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

import spa.crypto.Crypto;
import spa.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;

public interface Appendix {

    int getSize();
    int getFullSize();
    void putBytes(ByteBuffer buffer);
    JSONObject getJSONObject();
    byte getVersion();
    int getBaselineFeeHeight();
    Fee getBaselineFee(Transaction transaction);
    int getNextFeeHeight();
    Fee getNextFee(Transaction transaction);
    boolean isPhased(Transaction transaction);

    interface Prunable {
        byte[] getHash();
        boolean hasPrunableData();
        void restorePrunableData(Transaction transaction, int blockTimestamp, int height);
//        default boolean shouldLoadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
//            return Spa.getEpochTime() - transaction.getTimestamp() <
//                    (includeExpiredPrunable && Constants.INCLUDE_EXPIRED_PRUNABLE ?
//                            Constants.MAX_PRUNABLE_LIFETIME : Constants.MIN_PRUNABLE_LIFETIME);
//        }
    }

    interface Encryptable {
        void encrypt(String secretPhrase);
    }


    abstract class AbstractAppendix implements Appendix {

        private final byte version;

        AbstractAppendix(JSONObject attachmentData) {
            Long l = (Long) attachmentData.get("version." + getAppendixName());
            version = (byte) (l == null ? 0 : l);
        }

        AbstractAppendix(ByteBuffer buffer, byte transactionVersion) {
            if (transactionVersion == 0) {
                version = 0;
            } else {
                version = buffer.get();
            }
        }

        AbstractAppendix(int version) {
            this.version = (byte) version;
        }

        AbstractAppendix() {
            this.version = 1;
        }

        abstract String getAppendixName();

        @Override
        public final int getSize() {
            return getMySize() + (version > 0 ? 1 : 0);
        }

        @Override
        public final int getFullSize() {
            return getMyFullSize() + (version > 0 ? 1 : 0);
        }

        abstract int getMySize();

        int getMyFullSize() {
            return getMySize();
        }

        @Override
        public final void putBytes(ByteBuffer buffer) {
            if (version > 0) {
                buffer.put(version);
            }
            putMyBytes(buffer);
        }

        abstract void putMyBytes(ByteBuffer buffer);

        @Override
        public final JSONObject getJSONObject() {
            JSONObject json = new JSONObject();
            json.put("version." + getAppendixName(), version);
            putMyJSON(json);
            return json;
        }

        abstract void putMyJSON(JSONObject json);

        @Override
        public final byte getVersion() {
            return version;
        }

        boolean verifyVersion(byte transactionVersion) {
            return transactionVersion == 0 ? version == 0 : version == 1;
        }

        @Override
        public int getBaselineFeeHeight() {
            return 360;
        }

        @Override
        public Fee getBaselineFee(Transaction transaction) {
            return Fee.NONE;
        }

        @Override
        public int getNextFeeHeight() {
            return Integer.MAX_VALUE;
        }

        @Override
        public Fee getNextFee(Transaction transaction) {
            return getBaselineFee(transaction);
        }

        abstract void validate(Transaction transaction) throws SpaException.ValidationException;

        void validateAtFinish(Transaction transaction) throws SpaException.ValidationException {
            if (!isPhased(transaction)) {
                return;
            }
            validate(transaction);
        }

        abstract void apply(Transaction transaction, Account senderAccount, Account recipientAccount);

        final void loadPrunable(Transaction transaction) {
            loadPrunable(transaction, false);
        }

        void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {}

        abstract boolean isPhasable();

        @Override
        public final boolean isPhased(Transaction transaction) {
            return false;
        }

    }

    static boolean hasAppendix(String appendixName, JSONObject attachmentData) {
        return attachmentData.get("version." + appendixName) != null;
    }

    class Message extends AbstractAppendix {

        private static final String appendixName = "Message";

        static Message parse(JSONObject attachmentData) {
            if (!hasAppendix(appendixName, attachmentData)) {
                return null;
            }
            return new Message(attachmentData);
        }

        private static final Fee MESSAGE_FEE = new Fee.SizeBasedFee(0, Constants.ONE_SPA, 32) {
            @Override
            public int getSize(TransactionImpl transaction, Appendix appendage) {
                return ((Message)appendage).getMessage().length;
            }
        };

        private final byte[] message;
        private final boolean isText;

        Message(ByteBuffer buffer, byte transactionVersion) throws SpaException.NotValidException {
            super(buffer, transactionVersion);
            int messageLength = buffer.getInt();
            this.isText = messageLength < 0; // ugly hack
            if (messageLength < 0) {
                messageLength &= Integer.MAX_VALUE;
            }
            if (messageLength > 1000) {
                throw new SpaException.NotValidException("Invalid arbitrary message length: " + messageLength);
            }
            this.message = new byte[messageLength];
            buffer.get(this.message);
            if (isText && !Arrays.equals(message, Convert.toBytes(Convert.toString(message)))) {
                throw new SpaException.NotValidException("Message is not UTF-8 text");
            }
        }

        Message(JSONObject attachmentData) {
            super(attachmentData);
            String messageString = (String)attachmentData.get("message");
            this.isText = Boolean.TRUE.equals(attachmentData.get("messageIsText"));
            this.message = isText ? Convert.toBytes(messageString) : Convert.parseHexString(messageString);
        }

        public Message(byte[] message) {
            this(message, false);
        }

        public Message(String string) {
            this(Convert.toBytes(string), true);
        }

        public Message(String string, boolean isText) {
            this(isText ? Convert.toBytes(string) : Convert.parseHexString(string), isText);
        }

        public Message(byte[] message, boolean isText) {
            this.message = message;
            this.isText = isText;
        }

        @Override
        String getAppendixName() {
            return appendixName;
        }

        @Override
        int getMySize() {
            return 4 + message.length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putInt(isText ? (message.length | Integer.MIN_VALUE) : message.length);
            buffer.put(message);
        }

        @Override
        void putMyJSON(JSONObject json) {
            json.put("message", Convert.toString(message, isText));
            json.put("messageIsText", isText);
        }

        @Override
        public Fee getBaselineFee(Transaction transaction) {
            return MESSAGE_FEE;
        }

        @Override
        void validate(Transaction transaction) throws SpaException.ValidationException {
        }

        @Override
        void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {}

        public byte[] getMessage() {
            return message;
        }

        public boolean isText() {
            return isText;
        }

        @Override
        boolean isPhasable() {
            return false;
        }

    }

    final class PublicKeyAnnouncement extends AbstractAppendix {

        private static final String appendixName = "PublicKeyAnnouncement";

        static PublicKeyAnnouncement parse(JSONObject attachmentData) {
            if (!hasAppendix(appendixName, attachmentData)) {
                return null;
            }
            return new PublicKeyAnnouncement(attachmentData);
        }

        private final byte[] publicKey;

        PublicKeyAnnouncement(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.publicKey = new byte[32];
            buffer.get(this.publicKey);
        }

        PublicKeyAnnouncement(JSONObject attachmentData) {
            super(attachmentData);
            this.publicKey = Convert.parseHexString((String)attachmentData.get("recipientPublicKey"));
        }

        public PublicKeyAnnouncement(byte[] publicKey) {
            this.publicKey = publicKey;
        }

        @Override
        String getAppendixName() {
            return appendixName;
        }

        @Override
        int getMySize() {
            return 32;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.put(publicKey);
        }

        @Override
        void putMyJSON(JSONObject json) {
            json.put("recipientPublicKey", Convert.toHexString(publicKey));
        }

        @Override
        void validate(Transaction transaction) throws SpaException.ValidationException {
            if (transaction.getRecipientId() == 0) {
                throw new SpaException.NotValidException("PublicKeyAnnouncement cannot be attached to transactions with no recipient");
            }
            if (!Crypto.isCanonicalPublicKey(publicKey)) {
                throw new SpaException.NotValidException("Invalid recipient public key: " + Convert.toHexString(publicKey));
            }
            long recipientId = transaction.getRecipientId();
            if (Account.getId(this.publicKey) != recipientId) {
                throw new SpaException.NotValidException("Announced public key does not match recipient accountId");
            }
            byte[] recipientPublicKey = Account.getPublicKey(recipientId);
            if (recipientPublicKey != null && ! Arrays.equals(publicKey, recipientPublicKey)) {
                throw new SpaException.NotCurrentlyValidException("A different public key for this account has already been announced");
            }
        }

        @Override
        void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
            if (Account.setOrVerify(recipientAccount.getId(), publicKey)) {
                recipientAccount.apply(this.publicKey);
            }
        }

        @Override
        boolean isPhasable() {
            return false;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

    }

}
