package com.foxya.coin.common;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.templates.SqlTemplate;
import com.foxya.coin.common.database.RowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public abstract class BaseRepository {
    
    public static final RowMapper<Integer> COUNT_MAPPER = row -> row.getInteger("count");
    
    public Future<RowSet<Row>> query(SqlClient client, String sql, Map<String, Object> parameter) {
        log.debug("{}\n{}", sql, parameter);
        return SqlTemplate.forQuery(client, sql).execute(parameter);
    }
    
    public Future<RowSet<Row>> query(SqlClient client, String sql) {
        log.debug(sql);
        return client.query(sql).execute();
    }
    
    protected boolean success(RowSet<Row> rows) {
        return rows.rowCount() == 1;
    }
    
    protected boolean successAll(RowSet<Row> rows) {
        return rows.rowCount() >= 1;
    }
    
    public boolean checkColumn(Row row, String column) {
        return row.getColumnIndex(column) == -1;
    }
    
    public String getStringColumnValue(Row row, String column) {
        return checkColumn(row, column) ? null : row.getString(column);
    }
    
    public Integer getIntegerColumnValue(Row row, String column) {
        return checkColumn(row, column) ? null : row.getInteger(column);
    }
    
    public Long getLongColumnValue(Row row, String column) {
        return checkColumn(row, column) ? null : row.getLong(column);
    }
    
    public BigDecimal getBigDecimalColumnValue(Row row, String column) {
        return checkColumn(row, column) ? null : row.getBigDecimal(column);
    }
    
    public LocalDateTime getLocalDateTimeColumnValue(Row row, String column) {
        return checkColumn(row, column) ? null : row.getLocalDateTime(column);
    }
    
    public JsonObject getJsonObjectColumnValue(Row row, String column) {
        return checkColumn(row, column) ? null : row.getJsonObject(column);
    }
    
    public Double getDoubleColumnValue(Row row, String column) {
        return checkColumn(row, column) ? null : row.getDouble(column);
    }
    
    public Boolean getBooleanColumnValue(Row row, String column) {
        return checkColumn(row, column) ? null : row.getBoolean(column);
    }
    
    public <T> T getColumnValue(Row row, Class<T> type, String column) {
        return checkColumn(row, column) ? null : row.getString(column) != null ? row.get(type, column) : null;
    }
    
    protected <T> T fetchOne(RowMapper<T> mapper, RowSet<Row> rows) {
        if (rows.size() == 0) {
            return null;
        }
        return mapper.map(rows.iterator().next());
    }
    
    protected <T> List<T> fetchAll(RowMapper<T> mapper, RowSet<Row> rows) {
        if (rows.size() == 0) {
            return List.of();
        }
        
        List<T> data = new ArrayList<>();
        for (Row row : rows) {
            data.add(mapper.map(row));
        }
        return data;
    }
}

