package io.taucoin.android.wallet.db.greendao;

import java.util.Map;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.AbstractDaoSession;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.identityscope.IdentityScopeType;
import org.greenrobot.greendao.internal.DaoConfig;

import io.taucoin.android.wallet.db.entity.TransactionHistory;
import io.taucoin.android.wallet.db.entity.BlockInfo;
import io.taucoin.android.wallet.db.entity.KeyValue;
import io.taucoin.android.wallet.db.entity.IncreasePower;

import io.taucoin.android.wallet.db.greendao.TransactionHistoryDao;
import io.taucoin.android.wallet.db.greendao.BlockInfoDao;
import io.taucoin.android.wallet.db.greendao.KeyValueDao;
import io.taucoin.android.wallet.db.greendao.IncreasePowerDao;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT.

/**
 * {@inheritDoc}
 * 
 * @see org.greenrobot.greendao.AbstractDaoSession
 */
public class DaoSession extends AbstractDaoSession {

    private final DaoConfig transactionHistoryDaoConfig;
    private final DaoConfig blockInfoDaoConfig;
    private final DaoConfig keyValueDaoConfig;
    private final DaoConfig increasePowerDaoConfig;

    private final TransactionHistoryDao transactionHistoryDao;
    private final BlockInfoDao blockInfoDao;
    private final KeyValueDao keyValueDao;
    private final IncreasePowerDao increasePowerDao;

    public DaoSession(Database db, IdentityScopeType type, Map<Class<? extends AbstractDao<?, ?>>, DaoConfig>
            daoConfigMap) {
        super(db);

        transactionHistoryDaoConfig = daoConfigMap.get(TransactionHistoryDao.class).clone();
        transactionHistoryDaoConfig.initIdentityScope(type);

        blockInfoDaoConfig = daoConfigMap.get(BlockInfoDao.class).clone();
        blockInfoDaoConfig.initIdentityScope(type);

        keyValueDaoConfig = daoConfigMap.get(KeyValueDao.class).clone();
        keyValueDaoConfig.initIdentityScope(type);

        increasePowerDaoConfig = daoConfigMap.get(IncreasePowerDao.class).clone();
        increasePowerDaoConfig.initIdentityScope(type);

        transactionHistoryDao = new TransactionHistoryDao(transactionHistoryDaoConfig, this);
        blockInfoDao = new BlockInfoDao(blockInfoDaoConfig, this);
        keyValueDao = new KeyValueDao(keyValueDaoConfig, this);
        increasePowerDao = new IncreasePowerDao(increasePowerDaoConfig, this);

        registerDao(TransactionHistory.class, transactionHistoryDao);
        registerDao(BlockInfo.class, blockInfoDao);
        registerDao(KeyValue.class, keyValueDao);
        registerDao(IncreasePower.class, increasePowerDao);
    }
    
    public void clear() {
        transactionHistoryDaoConfig.clearIdentityScope();
        blockInfoDaoConfig.clearIdentityScope();
        keyValueDaoConfig.clearIdentityScope();
        increasePowerDaoConfig.clearIdentityScope();
    }

    public TransactionHistoryDao getTransactionHistoryDao() {
        return transactionHistoryDao;
    }

    public BlockInfoDao getBlockInfoDao() {
        return blockInfoDao;
    }

    public KeyValueDao getKeyValueDao() {
        return keyValueDao;
    }

    public IncreasePowerDao getIncreasePowerDao() {
        return increasePowerDao;
    }

}
