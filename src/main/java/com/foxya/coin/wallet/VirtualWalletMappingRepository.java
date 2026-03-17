package com.foxya.coin.wallet;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.common.utils.DateUtils;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.wallet.entities.VirtualWalletMapping;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;

import java.util.HashMap;
import java.util.Map;

public class VirtualWalletMappingRepository extends BaseRepository {

    private final RowMapper<VirtualWalletMapping> mappingMapper = row -> VirtualWalletMapping.builder()
        .id(getLongColumnValue(row, "id"))
        .userId(getLongColumnValue(row, "user_id"))
        .network(getStringColumnValue(row, "network"))
        .hotWalletAddress(getStringColumnValue(row, "hot_wallet_address"))
        .virtualAddress(getStringColumnValue(row, "virtual_address"))
        .ownerAddress(getStringColumnValue(row, "owner_address"))
        .mappingSeed(getStringColumnValue(row, "mapping_seed"))
        .status(getStringColumnValue(row, "status"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();

    public Future<VirtualWalletMapping> findByUserIdAndNetwork(SqlClient client, Long userId, String network) {
        String sql = QueryBuilder
            .select("virtual_wallet_mappings")
            .where("user_id", Op.Equal, "user_id")
            .andWhere("network", Op.Equal, "network")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("network", network);

        return query(client, sql, params)
            .map(rows -> fetchOne(mappingMapper, rows));
    }

    public Future<VirtualWalletMapping> create(SqlClient client, Long userId, String network, String hotWalletAddress, String virtualAddress, String mappingSeed) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("network", network);
        params.put("hot_wallet_address", hotWalletAddress);
        params.put("virtual_address", virtualAddress);
        params.put("owner_address", null);
        params.put("mapping_seed", mappingSeed);
        params.put("status", "ACTIVE");

        String sql = QueryBuilder.insert("virtual_wallet_mappings", params, "*");
        return query(client, sql, params)
            .map(rows -> fetchOne(mappingMapper, rows))
            .recover(throwable -> findByUserIdAndNetwork(client, userId, network));
    }

    public Future<VirtualWalletMapping> create(SqlClient client, Long userId, String network, String hotWalletAddress, String virtualAddress, String ownerAddress, String mappingSeed) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("network", network);
        params.put("hot_wallet_address", hotWalletAddress);
        params.put("virtual_address", virtualAddress);
        params.put("owner_address", ownerAddress);
        params.put("mapping_seed", mappingSeed);
        params.put("status", "ACTIVE");

        String sql = QueryBuilder.insert("virtual_wallet_mappings", params, "*");
        return query(client, sql, params)
            .map(rows -> fetchOne(mappingMapper, rows))
            .recover(throwable -> findByUserIdAndNetwork(client, userId, network));
    }

    public Future<VirtualWalletMapping> findByOwnerAddressAndNetwork(SqlClient client, String ownerAddress, String network) {
        String sql = QueryBuilder
            .select("virtual_wallet_mappings")
            .where("owner_address", Op.Equal, "owner_address")
            .andWhere("network", Op.Equal, "network")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("owner_address", ownerAddress);
        params.put("network", network);

        return query(client, sql, params)
            .map(rows -> fetchOne(mappingMapper, rows));
    }

    public Future<VirtualWalletMapping> findByVirtualAddressAndNetwork(SqlClient client, String virtualAddress, String network) {
        String sql = QueryBuilder
            .select("virtual_wallet_mappings")
            .where("virtual_address", Op.Equal, "virtual_address")
            .andWhere("network", Op.Equal, "network")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("virtual_address", virtualAddress);
        params.put("network", network);

        return query(client, sql, params)
            .map(rows -> fetchOne(mappingMapper, rows));
    }

    public Future<VirtualWalletMapping> updateBinding(SqlClient client, Long id, String hotWalletAddress, String ownerAddress) {
        String sql = QueryBuilder
            .update("virtual_wallet_mappings", "hot_wallet_address", "owner_address", "updated_at")
            .where("id", Op.Equal, "id")
            .andWhere("deleted_at", Op.IsNull)
            .returning("*");

        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("hot_wallet_address", hotWalletAddress);
        params.put("owner_address", ownerAddress);
        params.put("updated_at", DateUtils.now());

        return query(client, sql, params)
            .map(rows -> fetchOne(mappingMapper, rows));
    }
}
