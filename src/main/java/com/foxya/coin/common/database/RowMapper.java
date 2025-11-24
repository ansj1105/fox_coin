package com.foxya.coin.common.database;

import io.vertx.sqlclient.Row;

@FunctionalInterface
public interface RowMapper<T> {
    T map(Row row);
}

