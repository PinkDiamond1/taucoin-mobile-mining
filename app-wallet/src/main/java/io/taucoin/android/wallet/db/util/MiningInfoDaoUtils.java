/**
 * Copyright 2018 Taucoin Core Developers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.taucoin.android.wallet.db.util;

import java.util.List;

import io.taucoin.android.wallet.db.GreenDaoManager;
import io.taucoin.android.wallet.db.entity.MiningInfo;
import io.taucoin.android.wallet.db.greendao.MiningInfoDao;

/**
 * @version 1.0
 * Edited by yang
 * @description: mining info
 */
public class MiningInfoDaoUtils {

    private final GreenDaoManager daoManager;
    private static MiningInfoDaoUtils mMiningDaoUtils;

    private MiningInfoDaoUtils() {
        daoManager = GreenDaoManager.getInstance();
    }

    public static MiningInfoDaoUtils getInstance() {
        if (mMiningDaoUtils == null) {
            mMiningDaoUtils = new MiningInfoDaoUtils();
        }
        return mMiningDaoUtils;
    }

    private MiningInfoDao getMiningInfoDao() {
        return daoManager.getDaoSession().getMiningInfoDao();
    }


    public List<MiningInfo> queryByPubicKey(String pubicKey) {
        return getMiningInfoDao().queryBuilder()
                .where(MiningInfoDao.Properties.PublicKey.eq(pubicKey),
                        MiningInfoDao.Properties.Valid.eq(1))
                .orderDesc(MiningInfoDao.Properties.Mid)
                .list();
    }

    public boolean insertOrReplace(MiningInfo miningInfo) {
        long result = getMiningInfoDao().insertOrReplace(miningInfo);
        return result > -1;
    }

    public MiningInfo queryByNumber(String number) {
        List<MiningInfo> list = getMiningInfoDao().queryBuilder()
                .where(MiningInfoDao.Properties.BlockNo.eq(number))
                .orderDesc(MiningInfoDao.Properties.Mid)
                .list();
        if(list != null && list.size() > 0){
            return list.get(0);
        }
        return null;
    }
}