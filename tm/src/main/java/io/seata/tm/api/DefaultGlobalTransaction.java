/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.tm.api;

import io.seata.common.exception.ShouldNeverHappenException;
import io.seata.config.ConfigurationFactory;
import io.seata.core.constants.ConfigurationKeys;
import io.seata.core.context.RootContext;
import io.seata.core.exception.TransactionException;
import io.seata.core.model.GlobalStatus;
import io.seata.core.model.TransactionManager;
import io.seata.tm.TransactionManagerHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Default global transaction.
 *
 * @author sharajava
 */
public class DefaultGlobalTransaction implements GlobalTransaction {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultGlobalTransaction.class);

    private static final int DEFAULT_GLOBAL_TX_TIMEOUT = 60000;

    private static final String DEFAULT_GLOBAL_TX_NAME = "default";

    private TransactionManager transactionManager;

    private String xid;

    private GlobalStatus status;

    private GlobalTransactionRole role;

    private static final int COMMIT_RETRY_COUNT = ConfigurationFactory.getInstance().getInt(
        ConfigurationKeys.CLIENT_TM_COMMIT_RETRY_COUNT, 1);

    private static final int ROLLBACK_RETRY_COUNT = ConfigurationFactory.getInstance().getInt(
        ConfigurationKeys.CLIENT_TM_ROLLBACK_RETRY_COUNT, 1);

    /**
     * Instantiates a new Default global transaction.
     */
    DefaultGlobalTransaction() {
        this(null, GlobalStatus.UnKnown, GlobalTransactionRole.Launcher);
    }

    /**
     * Instantiates a new Default global transaction.
     *
     * @param xid    the xid
     * @param status the status
     * @param role   the role
     */
    DefaultGlobalTransaction(String xid, GlobalStatus status, GlobalTransactionRole role) {
        this.transactionManager = TransactionManagerHolder.get();
        this.xid = xid;
        this.status = status;
        this.role = role;
    }

    DefaultGlobalTransaction(String xid, GlobalStatus status, GlobalTransactionRole role, TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
        this.xid = xid;
        this.status = status;
        this.role = role;
    }

    @Override
    public void begin() throws TransactionException {
        begin(DEFAULT_GLOBAL_TX_TIMEOUT);
    }

    @Override
    public void begin(int timeout) throws TransactionException {
        begin(timeout, DEFAULT_GLOBAL_TX_NAME);
    }

    @Override
    public void begin(int timeout, String name) throws TransactionException {
        if (role != GlobalTransactionRole.Launcher) {
            check();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Ignore Begin(): just involved in global transaction [" + xid + "]");
            }
            return;
        }
        if (xid != null) {
            throw new IllegalStateException();
        }
        if (RootContext.getXID() != null) {
            throw new IllegalStateException();
        }
        xid = transactionManager.begin(null, null, name, timeout);
        status = GlobalStatus.Begin;
        RootContext.bind(xid);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Begin new global transaction [" + xid + "]");
        }

    }

    @Override
    public void commit() throws TransactionException {
        if (role == GlobalTransactionRole.Participant) {
            // Participant has no responsibility of committing
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Ignore Commit(): just involved in global transaction [" + xid + "]");
            }
            return;
        }
        if (xid == null) {
            throw new IllegalStateException();
        }
        int retry = 4;
        try {
            try {
                status = transactionManager.commit(xid);
            } catch (TransactionException e) {
                status = transactionManager.commit(xid);
            }
        } finally {
            if (RootContext.getXID() != null) {
                if (xid.equals(RootContext.getXID())) {
                    RootContext.unbind();
                }
            }
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[" + xid + "] commit status:" + status);
        }

    }

    @Override
    public void rollback() throws TransactionException {
        if (role == GlobalTransactionRole.Participant) {
            // Participant has no responsibility of committing
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Ignore Rollback(): just involved in global transaction [" + xid + "]");
            }
            return;
        }
        if (xid == null) {
            throw new IllegalStateException();
        }

        int retry = ROLLBACK_RETRY_COUNT;
        try {
            status = transactionManager.rollback(xid);
        } finally {
            if (RootContext.getXID() != null) {
                if (xid.equals(RootContext.getXID())) {
                    RootContext.unbind();
                }
            }
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[" + xid + "] rollback status:" + status);
        }
    }

    @Override
    public GlobalStatus getStatus() throws TransactionException {
        if (xid == null) {
            return GlobalStatus.UnKnown;
        }
        status = transactionManager.getStatus(xid);
        return status;
    }

    @Override
    public String getXid() {
        return xid;
    }

    private void check() {
        if (xid == null) {
            throw new ShouldNeverHappenException();
        }

    }
}
