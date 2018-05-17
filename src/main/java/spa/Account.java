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

import spa.AccountLedger.LedgerEntry;
import spa.AccountLedger.LedgerEvent;
import spa.AccountLedger.LedgerHolding;
import spa.crypto.Crypto;
import spa.db.DbKey;
import spa.db.DbUtils;
import spa.db.DerivedDbTable;
import spa.db.VersionedEntityDbTable;
import spa.db.VersionedPersistentDbTable;
import spa.util.Convert;
import spa.util.Listener;
import spa.util.Listeners;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings({"UnusedDeclaration", "SuspiciousNameCombination"})
public final class Account {

    public enum Event {
        BALANCE,
        UNCONFIRMED_BALANCE
    }

    public enum ControlType {
        PHASING_ONLY
    }

    public static final class PublicKey {

        private final long accountId;
        private final DbKey dbKey;
        private byte[] publicKey;
        private int height;

        private PublicKey(long accountId, byte[] publicKey) {
            this.accountId = accountId;
            this.dbKey = publicKeyDbKeyFactory.newKey(accountId);
            this.publicKey = publicKey;
            this.height = Spa.getBlockchain().getHeight();
        }

        private PublicKey(ResultSet rs, DbKey dbKey) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.dbKey = dbKey;
            this.publicKey = rs.getBytes("public_key");
            this.height = rs.getInt("height");
        }

        private void save(Connection con) throws SQLException {
            height = Spa.getBlockchain().getHeight();
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO public_key (account_id, public_key, height, latest) "
                    + "KEY (account_id, height) VALUES (?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, accountId);
                DbUtils.setBytes(pstmt, ++i, publicKey);
                pstmt.setInt(++i, height);
                pstmt.executeUpdate();
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public int getHeight() {
            return height;
        }

    }

    static class DoubleSpendingException extends RuntimeException {

        DoubleSpendingException(String message, long accountId, long confirmed, long unconfirmed) {
            super(message + " account: " + Long.toUnsignedString(accountId) + " confirmed: " + confirmed + " unconfirmed: " + unconfirmed);
        }

    }

    private static final DbKey.LongKeyFactory<Account> accountDbKeyFactory = new DbKey.LongKeyFactory<Account>("id") {

        @Override
        public DbKey newKey(Account account) {
            return account.dbKey == null ? newKey(account.id) : account.dbKey;
        }

        @Override
        public Account newEntity(DbKey dbKey) {
            return new Account(((DbKey.LongKey)dbKey).getId());
        }

    };

    private static final VersionedEntityDbTable<Account> accountTable = new VersionedEntityDbTable<Account>("account", accountDbKeyFactory) {

        @Override
        protected Account load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new Account(rs, dbKey);
        }

        @Override
        protected void save(Connection con, Account account) throws SQLException {
            account.save(con);
        }

    };

    private static final DbKey.LongKeyFactory<PublicKey> publicKeyDbKeyFactory = new DbKey.LongKeyFactory<PublicKey>("account_id") {

        @Override
        public DbKey newKey(PublicKey publicKey) {
            return publicKey.dbKey;
        }

        @Override
        public PublicKey newEntity(DbKey dbKey) {
            return new PublicKey(((DbKey.LongKey)dbKey).getId(), null);
        }

    };

    private static final VersionedPersistentDbTable<PublicKey> publicKeyTable = new VersionedPersistentDbTable<PublicKey>("public_key", publicKeyDbKeyFactory) {

        @Override
        protected PublicKey load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new PublicKey(rs, dbKey);
        }

        @Override
        protected void save(Connection con, PublicKey publicKey) throws SQLException {
            publicKey.save(con);
        }

    };

    private static final DerivedDbTable accountGuaranteedBalanceTable = new DerivedDbTable("account_guaranteed_balance") {

        @Override
        public void trim(int height) {
            try (Connection con = Db.db.getConnection();
                 PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM account_guaranteed_balance "
                         + "WHERE height < ? AND height >= 0")) {
                pstmtDelete.setInt(1, height - Constants.GUARANTEED_BALANCE_CONFIRMATIONS);
                pstmtDelete.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }

    };

    private static final ConcurrentMap<DbKey, byte[]> publicKeyCache = Spa.getBooleanProperty("spa.enablePublicKeyCache") ?
            new ConcurrentHashMap<>() : null;

    private static final Listeners<Account,Event> listeners = new Listeners<>();

    public static boolean addListener(Listener<Account> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    public static boolean removeListener(Listener<Account> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }

    public static int getCount() {
        return publicKeyTable.getCount();
    }

    public static Account getAccount(long id) {
        DbKey dbKey = accountDbKeyFactory.newKey(id);
        Account account = accountTable.get(dbKey);
        if (account == null) {
            PublicKey publicKey = publicKeyTable.get(dbKey);
            if (publicKey != null) {
                account = accountTable.newEntity(dbKey);
                account.publicKey = publicKey;
            }
        }
        return account;
    }

    public static Account getAccount(long id, int height) {
        DbKey dbKey = accountDbKeyFactory.newKey(id);
        Account account = accountTable.get(dbKey, height);
        if (account == null) {
            PublicKey publicKey = publicKeyTable.get(dbKey, height);
            if (publicKey != null) {
                account = accountTable.newEntity(dbKey);
                account.publicKey = publicKey;
            }
        }
        return account;
    }

    public static Account getAccount(byte[] publicKey) {
        long accountId = getId(publicKey);
        Account account = getAccount(accountId);
        if (account == null) {
            return null;
        }
        if (account.publicKey == null) {
            account.publicKey = publicKeyTable.get(accountDbKeyFactory.newKey(account));
        }
        if (account.publicKey == null || account.publicKey.publicKey == null || Arrays.equals(account.publicKey.publicKey, publicKey)) {
            return account;
        }
        throw new RuntimeException("DUPLICATE KEY for account " + Long.toUnsignedString(accountId)
                + " existing key " + Convert.toHexString(account.publicKey.publicKey) + " new key " + Convert.toHexString(publicKey));
    }

    public static long getId(byte[] publicKey) {
        byte[] publicKeyHash = Crypto.sha256().digest(publicKey);
        return Convert.fullHashToId(publicKeyHash);
    }

    public static byte[] getPublicKey(long id) {
        DbKey dbKey = publicKeyDbKeyFactory.newKey(id);
        byte[] key = null;
        if (publicKeyCache != null) {
            key = publicKeyCache.get(dbKey);
        }
        if (key == null) {
            PublicKey publicKey = publicKeyTable.get(dbKey);
            if (publicKey == null || (key = publicKey.publicKey) == null) {
                return null;
            }
            if (publicKeyCache != null) {
                publicKeyCache.put(dbKey, key);
            }
        }
        return key;
    }

    static Account addOrGetAccount(long id) {
        if (id == 0) {
            throw new IllegalArgumentException("Invalid accountId 0");
        }
        DbKey dbKey = accountDbKeyFactory.newKey(id);
        Account account = accountTable.get(dbKey);
        if (account == null) {
            account = accountTable.newEntity(dbKey);
            PublicKey publicKey = publicKeyTable.get(dbKey);
            if (publicKey == null) {
                publicKey = publicKeyTable.newEntity(dbKey);
                publicKeyTable.insert(publicKey);
            }
            account.publicKey = publicKey;
        }
        return account;
    }

    static {

        if (publicKeyCache != null) {

            Spa.getBlockchainProcessor().addListener(block -> {
                publicKeyCache.remove(accountDbKeyFactory.newKey(block.getGeneratorId()));
                block.getTransactions().forEach(transaction -> {
                    publicKeyCache.remove(accountDbKeyFactory.newKey(transaction.getSenderId()));
                    if (!transaction.getAppendages(appendix -> (appendix instanceof Appendix.PublicKeyAnnouncement), false).isEmpty()) {
                        publicKeyCache.remove(accountDbKeyFactory.newKey(transaction.getRecipientId()));
                    }
                });
            }, BlockchainProcessor.Event.BLOCK_POPPED);

            Spa.getBlockchainProcessor().addListener(block -> publicKeyCache.clear(), BlockchainProcessor.Event.RESCAN_BEGIN);

        }

    }

    static void init() {}


    private final long id;
    private final DbKey dbKey;
    private PublicKey publicKey;
    private long balanceAPL;
    private long unconfirmedBalanceAPL;
    private long forgedBalanceAPL;
    private long activeLesseeId;
    private Set<ControlType> controls;

    private Account(long id) {
        this.id = id;
        this.dbKey = accountDbKeyFactory.newKey(this.id);
        this.controls = Collections.emptySet();
    }

    private Account(ResultSet rs, DbKey dbKey) throws SQLException {
        this.id = rs.getLong("id");
        this.dbKey = dbKey;
        this.balanceAPL = rs.getLong("balance");
        this.unconfirmedBalanceAPL = rs.getLong("unconfirmed_balance");
        this.forgedBalanceAPL = rs.getLong("forged_balance");
        this.activeLesseeId = rs.getLong("active_lessee_id");
        if (rs.getBoolean("has_control_phasing")) {
            controls = Collections.unmodifiableSet(EnumSet.of(ControlType.PHASING_ONLY));
        } else {
            controls = Collections.emptySet();
        }
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account (id, "
                + "balance, unconfirmed_balance, forged_balance, "
                + "active_lessee_id, has_control_phasing, height, latest) "
                + "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.balanceAPL);
            pstmt.setLong(++i, this.unconfirmedBalanceAPL);
            pstmt.setLong(++i, this.forgedBalanceAPL);
            DbUtils.setLongZeroToNull(pstmt, ++i, this.activeLesseeId);
            pstmt.setBoolean(++i, controls.contains(ControlType.PHASING_ONLY));
            pstmt.setInt(++i, Spa.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    private void save() {
        if (balanceAPL == 0 && unconfirmedBalanceAPL == 0 && forgedBalanceAPL == 0 && activeLesseeId == 0 && controls.isEmpty()) {
            accountTable.delete(this, true);
        } else {
            accountTable.insert(this);
        }
    }

    public long getId() {
        return id;
    }

    public long getBalanceAPL() {
        return balanceAPL;
    }

    public long getUnconfirmedBalanceAPL() {
        return unconfirmedBalanceAPL;
    }

    public long getForgedBalanceAPL() {
        return forgedBalanceAPL;
    }

    public long getEffectiveBalanceSPA() {
        return getEffectiveBalanceSPA(Spa.getBlockchain().getHeight());
    }

    public long getEffectiveBalanceSPA(int height) {
        if (height >= 2880) {
            if (this.publicKey == null) {
                this.publicKey = publicKeyTable.get(accountDbKeyFactory.newKey(this));
            }
            if (this.publicKey == null || this.publicKey.publicKey == null || this.publicKey.height == 0 || height - this.publicKey.height <= 1440) {
                return 0; // cfb: Accounts with the public key revealed less than 1440 blocks ago are not allowed to generate blocks
            }
        }
        if (height < 2880) {
            if (Arrays.binarySearch(Genesis.GENESIS_RECIPIENTS, id) >= 0) {
                return balanceAPL / Constants.ONE_SPA;
            }
            long receivedInLastBlock = 0;
            for (Transaction transaction : Spa.getBlockchain().getBlockAtHeight(height).getTransactions()) {
                if (id == transaction.getRecipientId()) {
                    receivedInLastBlock += transaction.getAmountAPL();
                }
            }
            return (balanceAPL - receivedInLastBlock) / Constants.ONE_SPA;
        }
        Spa.getBlockchain().readLock();
        try {
            long effectiveBalanceAPL = getGuaranteedBalanceAPL(Constants.GUARANTEED_BALANCE_CONFIRMATIONS, height);
            return (effectiveBalanceAPL < Constants.MIN_FORGING_BALANCE_APL) ? 0 : effectiveBalanceAPL / Constants.ONE_SPA;
        } finally {
            Spa.getBlockchain().readUnlock();
        }
    }

    public long getGuaranteedBalanceAPL() {
        return getGuaranteedBalanceAPL(Constants.GUARANTEED_BALANCE_CONFIRMATIONS, Spa.getBlockchain().getHeight());
    }

    public long getGuaranteedBalanceAPL(final int numberOfConfirmations, final int currentHeight) {
        Spa.getBlockchain().readLock();
        try {
            int height = currentHeight - numberOfConfirmations;
            if (height + Constants.GUARANTEED_BALANCE_CONFIRMATIONS < Spa.getBlockchainProcessor().getMinRollbackHeight()
                    || height > Spa.getBlockchain().getHeight()) {
                throw new IllegalArgumentException("Height " + height + " not available for guaranteed balance calculation");
            }
            try (Connection con = Db.db.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT SUM (additions) AS additions "
                         + "FROM account_guaranteed_balance WHERE account_id = ? AND height > ? AND height <= ?")) {
                pstmt.setLong(1, this.id);
                pstmt.setInt(2, height);
                pstmt.setInt(3, currentHeight);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next()) {
                        return balanceAPL;
                    }
                    return Math.max(Math.subtractExact(balanceAPL, rs.getLong("additions")), 0);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        } finally {
            Spa.getBlockchain().readUnlock();
        }
    }

    public Set<ControlType> getControls() {
        return controls;
    }

    void addControl(ControlType control) {
        if (controls.contains(control)) {
            return;
        }
        EnumSet<ControlType> newControls = EnumSet.of(control);
        newControls.addAll(controls);
        controls = Collections.unmodifiableSet(newControls);
        accountTable.insert(this);
    }

    void removeControl(ControlType control) {
        if (!controls.contains(control)) {
            return;
        }
        EnumSet<ControlType> newControls = EnumSet.copyOf(controls);
        newControls.remove(control);
        controls = Collections.unmodifiableSet(newControls);
        save();
    }

    static boolean setOrVerify(long accountId, byte[] key) {
        DbKey dbKey = publicKeyDbKeyFactory.newKey(accountId);
        PublicKey publicKey = publicKeyTable.get(dbKey);
        if (publicKey == null) {
            publicKey = publicKeyTable.newEntity(dbKey);
        }
        if (publicKey.publicKey == null) {
            publicKey.publicKey = key;
            publicKey.height = Spa.getBlockchain().getHeight();
            return true;
        }
        return Arrays.equals(publicKey.publicKey, key);
    }

    void apply(byte[] key) {
        PublicKey publicKey = publicKeyTable.get(dbKey);
        if (publicKey == null) {
            publicKey = publicKeyTable.newEntity(dbKey);
        }
        if (publicKey.publicKey == null) {
            publicKey.publicKey = key;
            publicKeyTable.insert(publicKey);
        } else if (! Arrays.equals(publicKey.publicKey, key)) {
            throw new IllegalStateException("Public key mismatch");
        } else if (publicKey.height >= Spa.getBlockchain().getHeight() - 1) {
            PublicKey dbPublicKey = publicKeyTable.get(dbKey, false);
            if (dbPublicKey == null || dbPublicKey.publicKey == null) {
                publicKeyTable.insert(publicKey);
            }
        }
        if (publicKeyCache != null) {
            publicKeyCache.put(dbKey, key);
        }
        this.publicKey = publicKey;
    }

    void addToBalanceAPL(LedgerEvent event, long eventId, long amountAPL) {
        addToBalanceAPL(event, eventId, amountAPL, 0);
    }

    void addToBalanceAPL(LedgerEvent event, long eventId, long amountAL, long feeAPL) {
        if (amountAL == 0 && feeAPL == 0) {
            return;
        }
        long totalAmountAPL = Math.addExact(amountAL, feeAPL);
        this.balanceAPL = Math.addExact(this.balanceAPL, totalAmountAPL);
        addToGuaranteedBalanceAPL(totalAmountAPL);
        checkBalance(this.id, this.balanceAPL, this.unconfirmedBalanceAPL);
        save();
        listeners.notify(this, Event.BALANCE);
        if (AccountLedger.mustLogEntry(this.id, false)) {
            if (feeAPL != 0) {
                AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.SPA_BALANCE, null, feeAPL, this.balanceAPL - amountAL));
            }
            if (amountAL != 0) {
                AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                        LedgerHolding.SPA_BALANCE, null, amountAL, this.balanceAPL));
            }
        }
    }

    void addToUnconfirmedBalanceAPL(LedgerEvent event, long eventId, long amountAPL) {
        addToUnconfirmedBalanceAPL(event, eventId, amountAPL, 0);
    }

    void addToUnconfirmedBalanceAPL(LedgerEvent event, long eventId, long amountAPL, long feeAPL) {
        if (amountAPL == 0 && feeAPL == 0) {
            return;
        }
        long totalAmountAPL = Math.addExact(amountAPL, feeAPL);
        this.unconfirmedBalanceAPL = Math.addExact(this.unconfirmedBalanceAPL, totalAmountAPL);
        checkBalance(this.id, this.balanceAPL, this.unconfirmedBalanceAPL);
        save();
        listeners.notify(this, Event.UNCONFIRMED_BALANCE);
        if (AccountLedger.mustLogEntry(this.id, true)) {
            if (feeAPL != 0) {
                AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_SPA_BALANCE, null, feeAPL, this.unconfirmedBalanceAPL - amountAPL));
            }
            if (amountAPL != 0) {
                AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_SPA_BALANCE, null, amountAPL, this.unconfirmedBalanceAPL));
            }
        }
    }

    void addToBalanceAndUnconfirmedBalanceAPL(LedgerEvent event, long eventId, long amountAPL) {
        addToBalanceAndUnconfirmedBalanceAPL(event, eventId, amountAPL, 0);
    }

    void addToBalanceAndUnconfirmedBalanceAPL(LedgerEvent event, long eventId, long amountAPL, long feeAPL) {
        if (amountAPL == 0 && feeAPL == 0) {
            return;
        }
        long totalAmountAPL = Math.addExact(amountAPL, feeAPL);
        this.balanceAPL = Math.addExact(this.balanceAPL, totalAmountAPL);
        this.unconfirmedBalanceAPL = Math.addExact(this.unconfirmedBalanceAPL, totalAmountAPL);
        addToGuaranteedBalanceAPL(totalAmountAPL);
        checkBalance(this.id, this.balanceAPL, this.unconfirmedBalanceAPL);
        save();
        listeners.notify(this, Event.BALANCE);
        listeners.notify(this, Event.UNCONFIRMED_BALANCE);
        if (AccountLedger.mustLogEntry(this.id, true)) {
            if (feeAPL != 0) {
                AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_SPA_BALANCE, null, feeAPL, this.unconfirmedBalanceAPL - amountAPL));
            }
            if (amountAPL != 0) {
                AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_SPA_BALANCE, null, amountAPL, this.unconfirmedBalanceAPL));
            }
        }
        if (AccountLedger.mustLogEntry(this.id, false)) {
            if (feeAPL != 0) {
                AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.SPA_BALANCE, null, feeAPL, this.balanceAPL - amountAPL));
            }
            if (amountAPL != 0) {
                AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                        LedgerHolding.SPA_BALANCE, null, amountAPL, this.balanceAPL));
            }
        }
    }

    void addToForgedBalanceAPL(long amountAPL) {
        if (amountAPL == 0) {
            return;
        }
        this.forgedBalanceAPL = Math.addExact(this.forgedBalanceAPL, amountAPL);
        save();
    }

    private static void checkBalance(long accountId, long confirmed, long unconfirmed) {
        if (accountId == Genesis.CREATOR_ID) {
            return;
        }
        if (confirmed < 0) {
            throw new DoubleSpendingException("Negative balance or quantity: ", accountId, confirmed, unconfirmed);
        }
        if (unconfirmed < 0) {
            throw new DoubleSpendingException("Negative unconfirmed balance or quantity: ", accountId, confirmed, unconfirmed);
        }
        if (unconfirmed > confirmed) {
            throw new DoubleSpendingException("Unconfirmed exceeds confirmed balance or quantity: ", accountId, confirmed, unconfirmed);
        }
    }

    private void addToGuaranteedBalanceAPL(long amountAPL) {
        if (amountAPL <= 0) {
            return;
        }
        int blockchainHeight = Spa.getBlockchain().getHeight();
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmtSelect = con.prepareStatement("SELECT additions FROM account_guaranteed_balance "
                     + "WHERE account_id = ? and height = ?");
             PreparedStatement pstmtUpdate = con.prepareStatement("MERGE INTO account_guaranteed_balance (account_id, "
                     + " additions, height) KEY (account_id, height) VALUES(?, ?, ?)")) {
            pstmtSelect.setLong(1, this.id);
            pstmtSelect.setInt(2, blockchainHeight);
            try (ResultSet rs = pstmtSelect.executeQuery()) {
                long additions = amountAPL;
                if (rs.next()) {
                    additions = Math.addExact(additions, rs.getLong("additions"));
                }
                pstmtUpdate.setLong(1, this.id);
                pstmtUpdate.setLong(2, additions);
                pstmtUpdate.setInt(3, blockchainHeight);
                pstmtUpdate.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public String toString() {
        return "Account " + Long.toUnsignedString(getId());
    }
}
