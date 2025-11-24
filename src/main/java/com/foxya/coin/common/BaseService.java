package com.foxya.coin.common;

import io.vertx.pgclient.PgPool;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class BaseService {
    protected final PgPool pool;
}

