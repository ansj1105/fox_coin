package com.foxya.coin.common;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaseRepositoryJsonColumnTest {

    private final BaseRepository repository = new BaseRepository() {
    };

    @Test
    void getJsonObjectColumnValue_returnsJsonObject_whenValueIsJsonObject() {
        Row row = mock(Row.class);
        when(row.getColumnIndex("metadata")).thenReturn(0);
        when(row.getValue("metadata")).thenReturn(new JsonObject().put("key", "value"));

        JsonObject value = repository.getJsonObjectColumnValue(row, "metadata");
        assertThat(value).isNotNull();
        assertThat(value.getString("key")).isEqualTo("value");
    }

    @Test
    void getJsonObjectColumnValue_parsesString_whenValueIsString() {
        Row row = mock(Row.class);
        when(row.getColumnIndex("metadata")).thenReturn(0);
        when(row.getValue("metadata")).thenReturn("{\"titleKey\":\"notifications.referralAirdrop.title\"}");

        JsonObject value = repository.getJsonObjectColumnValue(row, "metadata");
        assertThat(value).isNotNull();
        assertThat(value.getString("titleKey")).isEqualTo("notifications.referralAirdrop.title");
    }

    @Test
    void getJsonObjectColumnValue_returnsNull_whenStringIsInvalidJson() {
        Row row = mock(Row.class);
        when(row.getColumnIndex("metadata")).thenReturn(0);
        when(row.getValue("metadata")).thenReturn("not-json");

        JsonObject value = repository.getJsonObjectColumnValue(row, "metadata");
        assertThat(value).isNull();
    }

    @Test
    void getJsonObjectColumnValue_returnsNull_whenColumnMissing() {
        Row row = mock(Row.class);
        when(row.getColumnIndex("metadata")).thenReturn(-1);

        JsonObject value = repository.getJsonObjectColumnValue(row, "metadata");
        assertThat(value).isNull();
    }
}
