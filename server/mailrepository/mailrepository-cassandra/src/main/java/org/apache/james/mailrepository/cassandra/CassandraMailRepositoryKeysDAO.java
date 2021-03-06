/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailrepository.cassandra;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.KEYS_TABLE_NAME;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.MAIL_KEY;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.REPOSITORY_NAME;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

public class CassandraMailRepositoryKeysDAO {

    private final CassandraAsyncExecutor executor;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement insertKey;
    private final PreparedStatement deleteKey;
    private final PreparedStatement listKeys;

    @Inject
    public CassandraMailRepositoryKeysDAO(Session session, CassandraUtils cassandraUtils) {
        this.executor = new CassandraAsyncExecutor(session);
        this.cassandraUtils = cassandraUtils;

        this.insertKey = prepareInsert(session);
        this.deleteKey = prepareDelete(session);
        this.listKeys = prepareList(session);
    }

    private PreparedStatement prepareList(Session session) {
        return session.prepare(select(MAIL_KEY)
            .from(KEYS_TABLE_NAME)
            .where(eq(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME))));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(delete()
            .from(KEYS_TABLE_NAME)
            .where(eq(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME)))
            .and(eq(MAIL_KEY, bindMarker(MAIL_KEY))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(KEYS_TABLE_NAME)
            .value(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME))
            .value(MAIL_KEY, bindMarker(MAIL_KEY)));
    }

    public CompletableFuture<Void> store(String url, String key) {
        return executor.executeVoid(insertKey.bind()
            .setString(REPOSITORY_NAME, url)
            .setString(MAIL_KEY, key));
    }

    public CompletableFuture<Stream<String>> list(String url) {
        return executor.execute(listKeys.bind()
            .setString(REPOSITORY_NAME, url))
            .thenApply(cassandraUtils::convertToStream)
            .thenApply(stream -> stream.map(row -> row.getString(MAIL_KEY)));
    }

    public CompletableFuture<Void> remove(String url, String key) {
        return executor.executeVoid(deleteKey.bind()
            .setString(REPOSITORY_NAME, url)
            .setString(MAIL_KEY, key));
    }
}
