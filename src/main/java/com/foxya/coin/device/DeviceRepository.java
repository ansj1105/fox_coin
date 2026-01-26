package com.foxya.coin.device;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.device.entities.Device;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DeviceRepository extends BaseRepository {

    private final RowMapper<Device> deviceMapper = row -> Device.builder()
        .id(getLongColumnValue(row, "id"))
        .userId(getLongColumnValue(row, "user_id"))
        .deviceId(getStringColumnValue(row, "device_id"))
        .deviceType(getStringColumnValue(row, "device_type"))
        .deviceOs(getStringColumnValue(row, "device_os"))
        .appVersion(getStringColumnValue(row, "app_version"))
        .userAgent(getStringColumnValue(row, "user_agent"))
        .lastIp(getStringColumnValue(row, "last_ip"))
        .lastLoginAt(getLocalDateTimeColumnValue(row, "last_login_at"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .deletedAt(getLocalDateTimeColumnValue(row, "deleted_at"))
        .build();

    public Future<Device> getActiveDeviceByUserAndType(SqlClient client, Long userId, String deviceType) {
        String sql = QueryBuilder.select("devices")
            .where("user_id", Op.Equal, "userId")
            .andWhere("device_type", Op.Equal, "deviceType")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("deviceType", deviceType);

        return query(client, sql, params)
            .map(rows -> fetchOne(deviceMapper, rows));
    }

    public Future<Device> getActiveDeviceByUserAndDeviceId(SqlClient client, Long userId, String deviceId) {
        String sql = QueryBuilder.select("devices")
            .where("user_id", Op.Equal, "userId")
            .andWhere("device_id", Op.Equal, "deviceId")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("deviceId", deviceId);

        return query(client, sql, params)
            .map(rows -> fetchOne(deviceMapper, rows));
    }

    public Future<Device> createDevice(SqlClient client, Device device) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", device.getUserId());
        params.put("device_id", device.getDeviceId());
        params.put("device_type", device.getDeviceType());
        params.put("device_os", device.getDeviceOs());
        params.put("app_version", device.getAppVersion());
        params.put("user_agent", device.getUserAgent());
        params.put("last_ip", device.getLastIp());
        params.put("last_login_at", device.getLastLoginAt());
        params.put("created_at", device.getCreatedAt());
        params.put("updated_at", device.getUpdatedAt());

        String sql = QueryBuilder.insert("devices", params, "*");

        return query(client, sql, params)
            .map(rows -> fetchOne(deviceMapper, rows));
    }

    public Future<Void> updateDeviceLogin(SqlClient client, Long id, String deviceOs, String appVersion, String userAgent, String lastIp, LocalDateTime lastLoginAt) {
        String sql = QueryBuilder.update("devices", "device_os", "app_version", "user_agent", "last_ip", "last_login_at", "updated_at")
            .where("id", Op.Equal, "id")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("device_os", deviceOs);
        params.put("app_version", appVersion);
        params.put("user_agent", userAgent);
        params.put("last_ip", lastIp);
        params.put("last_login_at", lastLoginAt);
        params.put("updated_at", LocalDateTime.now());

        return query(client, sql, params)
            .mapEmpty();
    }

    public Future<Void> softDeleteDeviceByUserAndType(SqlClient client, Long userId, String deviceType) {
        String sql = QueryBuilder.update("devices", "deleted_at", "updated_at")
            .where("user_id", Op.Equal, "userId")
            .andWhere("device_type", Op.Equal, "deviceType")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("deviceType", deviceType);
        params.put("deleted_at", LocalDateTime.now());
        params.put("updated_at", LocalDateTime.now());

        return query(client, sql, params)
            .mapEmpty();
    }

    public Future<Void> softDeleteDeviceByUserAndDeviceId(SqlClient client, Long userId, String deviceId) {
        String sql = QueryBuilder.update("devices", "deleted_at", "updated_at")
            .where("user_id", Op.Equal, "userId")
            .andWhere("device_id", Op.Equal, "deviceId")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("deviceId", deviceId);
        params.put("deleted_at", LocalDateTime.now());
        params.put("updated_at", LocalDateTime.now());

        return query(client, sql, params)
            .mapEmpty();
    }

    public Future<Void> softDeleteDevicesByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder.update("devices", "deleted_at", "updated_at")
            .where("user_id", Op.Equal, "userId")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("deleted_at", LocalDateTime.now());
        params.put("updated_at", LocalDateTime.now());

        return query(client, sql, params)
            .mapEmpty();
    }
}
