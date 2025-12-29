package com.foxya.coin.utils;

import com.foxya.coin.common.database.LockType;
import com.foxya.coin.common.database.ParametersMapped;

import java.util.*;
import java.util.stream.IntStream;

/**
 * 쿼리문 생성 유틸 클래스입니다.
 */
public abstract class QueryBuilder {

    /**
     * INSERT 문을 생성합니다.
     *
     * @param tableName INSERT 를 실행할 테이블명
     * @param params    INSERT 문의 파라미터
     * @param returning 반환할 컬럼
     * @return 만들어진 INSERT 문
     */
    public static String insert(String tableName, ParametersMapped params, String returning) {
        Map<String, Object> map = params.toMap();
        Set<String> keys = map.keySet();
        String columns = String.join(",", keys);

        String[] valueList = IntStream.rangeClosed(0, map.size() - 1).mapToObj(i -> "#{" + keys.toArray()[i] + "}")
            .toArray(String[]::new);
        String values = String.join(",", valueList);

        String query = "INSERT INTO " + tableName
            + "(" + columns + ") "
            + "VALUES (" + values + ")";

        if (returning != null) {
            return query + " returning " + returning;
        } else {
            return query;
        }
    }

    /**
     * INSERT 문을 생성합니다.
     *
     * @param tableName INSERT 를 실행할 테이블명
     * @param params    INSERT 문의 파라미터
     * @param returning 반환할 컬럼
     * @return 만들어진 INSERT 문
     */
    public static String insert(String tableName, Map<String, Object> params, String returning) {
        Set<String> keys = params.keySet();
        String columns = String.join(",", keys);

        String[] valueList = IntStream.rangeClosed(0, params.size() - 1)
            .mapToObj(i -> "#{" + keys.toArray()[i].toString().replace("`", "") + "}")
            .toArray(String[]::new);
        String values = String.join(",", valueList);

        String query = "INSERT INTO " + tableName
            + "(" + columns + ") "
            + "VALUES (" + values + ")";

        if (returning != null) {
            return query + " returning " + returning;
        } else {
            return query;
        }
    }

    /**
     * COUNT 문을 생성합니다.
     *
     * @param tableName COUNT 를 실행할 테이블명
     * @return COUNT QueryBuilder
     */
    public static SelectQueryBuilder count(String tableName) {
        return select(tableName, "COUNT(*) as count");
    }

    /**
     * COUNT 문을 생성합니다.
     *
     * @param tableName COUN T를 실행할 테이블명
     * @param alias     테이블의 별칭
     * @return COUNT QueryBuilder
     */
    public static SelectQueryBuilder countAlias(String tableName, String alias) {
        return selectAlias(tableName, alias, "COUNT(*) as count");
    }

    /**
     * COUNT 문을 생성합니다.
     *
     * @param tableName  COUNT 를 실행할 테이블명
     * @param aliase     테이블의 별칭
     * @param countAlias count 의 별칭
     * @return SELECT QueryBuilder
     */
    public static SelectQueryBuilder count(String tableName, String aliase, String countAlias) {
        String countString = String.format("COUNT(*) as %s", countAlias);
        return selectAlias(tableName, aliase, countString);
    }

    public static SelectQueryBuilder selectStringQuery(String query) {
        return new SelectQueryBuilder(query);
    }

    /**
     * 전체 컬럼에대한 SELECT 문을 생성합니다.
     *
     * @param tableName SELECT 를 실행할 테이블명
     * @return SELECT QueryBuilder
     */
    public static SelectQueryBuilder select(String tableName) {
        return select(tableName, "*");
    }

    /**
     * SELECT 문을 생성합니다.
     *
     * @param tableName SELECT 를 실행할 테이블
     * @param columns   SELECT 할 컬럼
     * @return SELECT QueryBuilder
     */
    public static SelectQueryBuilder select(String tableName, String... columns) {
        return new SelectQueryBuilder(tableName, columns);
    }

    public static SelectQueryBuilder unionSelect(String tableName, String... columns) {
        return new SelectQueryBuilder("union", tableName, columns);
    }

    /**
     * DELETE 문을 생성합니다.
     *
     * @param tableName DELETE 문을 실행할 테이블명
     * @return DELETE QueryBuilder
     */
    public static DeleteQueryBuilder delete(String tableName) {
        return new DeleteQueryBuilder(tableName);
    }

    /**
     * SELECT 절에 서브 쿼리가 있는 SELECT 문을 생성합니다.
     *
     * @param tableName SELECT 를 실행할 테이블명
     * @param alias     테이블의 별칭
     * @param subQuery  SELECT 절에 들어갈 서브 쿼리
     * @param columns   SELECT 할 컬럼
     * @return SELECT QueryBuilder
     */
    public static SelectQueryBuilder selectSubQuery(String tableName, String alias, String subQuery, String... columns) {
        return new SelectQueryBuilder(tableName, alias, subQuery, columns);
    }

    /**
     * SELECT 문을 생성합니다.
     *
     * @param tableName SELECT 를 실행할 테이블명
     * @param alias     테이블의 별칭
     * @param columns   SELECT 할 컬럼
     * @return SELECT QueryBuilder
     */
    public static SelectQueryBuilder selectAlias(String tableName, String alias, String... columns) {
        return new SelectQueryBuilder(tableName, alias, false, columns);
    }

    /**
     * FROM 절에 서브 쿼리가 있는 SELECT 문 생성
     *
     * @param subQuery FROM 절에 들어갈 서브 쿼리
     * @param alias    서브 쿼리 결과의 별칭
     * @param columns  SELECT 할 컬럼
     * @return SELECT QueryBuilder
     */
    public static SelectQueryBuilder selectSubQueryAlias(String subQuery, String alias, String... columns) {
        return new SelectQueryBuilder(subQuery, alias, true, columns);
    }

    /**
     * INSERT 문을 생성합니다.
     *
     * @param tableName INSERT 를 실행할 테이블명
     * @param columns   INSERT 할 컬럼
     * @return INSERT QueryBuilder
     */
    public static InsertQueryBuilder insert(String tableName, String... columns) {
        return new InsertQueryBuilder(tableName, columns);
    }

    /**
     * UPDATE 문을 생성합니다.
     *
     * @param tableName UPDATE 를 실행할 테이블명
     * @return UPDATE QueryBuilder
     */
    public static UpdateQueryBuilder update(String tableName) {
        return new UpdateQueryBuilder(tableName);
    }

    /**
     * UPDATE 문을 생성합니다.
     *
     * @param tableName UPDATE 를 실행할 테이블명
     * @param columns   UPDATE 할 컬럼
     * @return UPDATE QueryBuilder
     */
    public static UpdateQueryBuilder update(String tableName, String... columns) {
        return new UpdateQueryBuilder(tableName, columns);
    }

    /**
     * UPDATE 문을 생성합니다.
     *
     * @param tableName UPDATE 를 실행할 테이블명
     * @param params    UPDATE 파라미터
     * @return UPDATE QueryBuilder
     */
    public static UpdateQueryBuilder update(String tableName, ParametersMapped params) {
        Map<String, Object> map = params.toMap();
        Set<String> keys = map.keySet();
        return new UpdateQueryBuilder(tableName, keys.toArray(String[]::new));
    }

    /**
     * UPDATE 문을 생성합니다.
     *
     * @param tableName UPDATE 를 실행할 테이블명
     * @param tuple     UPDATE 파라미터
     * @return UPDATE QueryBuilder
     */
    public static UpdateQueryBuilder update(String tableName, Map<String, Object> tuple) {
        Set<String> keys = tuple.keySet();
        return new UpdateQueryBuilder(tableName, keys.toArray(String[]::new));
    }

    /**
     * DELETE QueryBuilder 클래스입니다.
     */
    public static class DeleteQueryBuilder extends BaseQueryBuilder<DeleteQueryBuilder> {

        /**
         * DELETE QueryBuilder 를 생성합니다.
         *
         * @param tableName DELETE 를 실행할 테이블명
         */
        DeleteQueryBuilder(String tableName) {
            super();

            append("DELETE FROM ")
                .append(tableName);
        }
    }

    /**
     * SELECT QueryBuilder 클래스입니다.
     */
    public static class SelectQueryBuilder extends BaseQueryBuilder<SelectQueryBuilder> {


        SelectQueryBuilder(String string) {
            super();

            append(string);
        }

        /**
         * SELECT QueryBuilder 를 생성합니다.
         *
         * @param tableName SELECT 를 실행할 테이블명
         * @param columns   SELECT 할 컬럼
         */
        SelectQueryBuilder(String tableName, String[] columns) {
            super();

            append("SELECT ")
                .append(String.join(",", columns))
                .append(" FROM ")
                .append(tableName);
        }

        /**
         * SELECT QueryBuilder 를 생성합니다.
         *
         * @param tableName       SELECT 를 실행할 테이블명
         * @param alias           테이블의 별칭
         * @param subQueryYesOrNo FROM 절에 서브쿼리 사용 유무
         * @param columns         SELECT 할 컬럼
         */
        SelectQueryBuilder(String tableName, String alias, Boolean subQueryYesOrNo, String[] columns) {
            super();

            if (subQueryYesOrNo) {
                append("SELECT ")
                    .append(String.join(",", columns))
                    .append(" FROM ")
                    .append("(")
                    .append(tableName)
                    .append(" ) ")
                    .append("as ")
                    .append(alias);
            } else {
                append("SELECT ")
                    .append(String.join(",", columns))
                    .append(" FROM ")
                    .append(tableName)
                    .append(" ")
                    .append(alias);
            }
        }

        /**
         * SELECT QueryBuilder 를 생성합니다.
         *
         * @param tableName SELECT 를 실행할 테이블명
         * @param alias     테이블 별칭
         * @param subQuery  SELECT 절에 사용할 서브 쿼리
         * @param columns   SELECT 할 컬럼
         */
        SelectQueryBuilder(String tableName, String alias, String subQuery, String[] columns) {
            super();

            append("SELECT ")
                .append(String.join(",", columns))
                .append(", (")
                .append(subQuery)
                .append(")")
                .append(" FROM ")
                .append(tableName)
                .append(" ")
                .append(alias);
        }

        SelectQueryBuilder(String ty, String tableName, String[] columns) {
            super();

            if (ty.equals("union")) {
                append("UNION SELECT ")
                    .append(String.join(",", columns))
                    .append(" FROM ")
                    .append(tableName);
            }

        }

        /**
         * SELECT QueryBuilder 에 OFFSET 을 추가합니다.
         *
         * @param condition OFFSET 에 들어갈 조건
         * @return OFFSET 을 추가한 SELECT QueryBuilder
         */
        public SelectQueryBuilder offset(Integer condition) {
            append("OFFSET ").append(condition.toString());
            return this;
        }

        /**
         * SELECT QueryBuilder 에 OFFSET 과 offset 매개 변수를 추가합니다.
         *
         * @return OFFSET 과 offset 매개 변수를 추가한 SELECT QueryBuilder
         */
        public SelectQueryBuilder offsetRefactoring() {
            append("OFFSET ").append("#{offset}");
            return this;
        }

        /**
         * SELECT QueryBuilder 에 LIMIT 를 추가합니다.
         *
         * @param condition LIMIT 에 들어갈 조건
         * @return LIMIT 을 추가한 SELECT QueryBuilder
         */
        public SelectQueryBuilder limit(Integer condition) {
            append("LIMIT ").append(condition.toString());
            return this;
        }

        /**
         * SELECT QueryBuilder 에 LIMIT 과 limit 매개 변수를 추가합니다.
         *
         * @return LIMIT 과 limit 매개 변수를 추가한 SELECT QueryBuilder
         */
        public SelectQueryBuilder limitRefactoring() {
            append("LIMIT ").append("#{limit}");
            return this;
        }

        /**
         * SELECT QueryBuilder 에 GROUP BY 를 추가합니다.
         *
         * @param columns GROUP BY 에 들어갈 컬럼
         * @return GROUP BY 를 추가한 SELECT QueryBuilder
         */
        public SelectQueryBuilder groupBy(String... columns) {
            append("group by ").append(String.join(", ", columns));
            return this;
        }

        public SelectQueryBuilder having(String condition) {
            append("having ").append(condition);
            return this;
        }

        /**
         * SELECT QueryBuilder 에 ORDER BY 를 추가합니다.
         *
         * @param column ORDER BY 할 컬럼
         * @param order  ASC 또는 DESC
         * @return ORDER BY 를 추가한 SELECT QueryBuilder
         */
        public SelectQueryBuilder orderBy(String column, Sort order) {
            append("order by ").append(column).append(" ").append(order);
            return this;
        }

        /**
         * SELECT QueryBuilder 에 LOCK 을 추가합니다.
         *
         * @param type ForUpdate 또는 ForShare
         * @return LOCK 을 추가한 SELECT QueryBuilder
         */
        public SelectQueryBuilder lock(LockType type) {
            if (type == null) {
                return this;
            }

            switch (type) {
                case ForUpdate:
                    append("FOR UPDATE");
                    break;
                case ForShare:
                    append("FOR SHARE");
                    break;
                default: // nothing
            }

            return this;
        }

        /**
         * SELECT QueryBuilder 에 LEFT JOIN 을 추가합니다.
         *
         * @param tableName  LEFT JOIN 할 테이블명
         * @param tableAlias 테이블의 별칭
         * @return LEFT JOIN 을 추가한 SELECT QueryBuilder
         */
        public SelectQueryBuilder leftJoin(String tableName, String tableAlias) {
            append("LEFT JOIN ").append(tableName).append(" ").append(tableAlias);
            return this;
        }

        public SelectQueryBuilder innerJoin(String tableName, String tableAlias) {
            append("INNER JOIN ").append(tableName).append(" ").append(tableAlias);
            return this;
        }

        /**
         * SELECT QueryBuilder 에 ON 을 추가합니다.
         *
         * @param fTable ON 절에 들어갈 테이블1의 컬럼
         * @param op     두 컬럼의 관계
         * @param sTable ON 절에 들어갈 테이블2의 컬럼
         * @return ON 을 추가한 SELECT QueryBuilder
         */
        public SelectQueryBuilder on(String fTable, Op op, String sTable) {
            append("ON ").append(fTable).append(" ").append(op.value()).append(" ").append(sTable);
            return this;
        }

        public SelectQueryBuilder on() {
            append("ON ").append("true");
            return this;
        }

        /**
         * SELECT QueryBuilder 에 AND 를 추가합니다.
         *
         * @param fTable AND 절에 들어갈 컬럼
         * @param op     관계 Op
         * @return AND 를 추가한 SELECT QueryBuilder
         */
        public SelectQueryBuilder and(String fTable, Op op) {
            append("AND ").append(fTable).append(" ").append(op.value());
            return this;
        }

        /**
         * SELECT QueryBuilder 에 AND 를 추가합니다.
         *
         * @param fTable AND 절에 들어갈 컬럼
         * @param op     관계 Op
         * @param sTable AND 절에 들어갈 값
         * @return AND 를 추가한 SELECT QueryBuilder
         */
        public SelectQueryBuilder and(String fTable, Op op, String sTable) {
            append("AND ").append(fTable).append(" ").append(op.value()).append(" ").append(sTable);
            return this;
        }

        public SelectQueryBuilder appendQueryString(String query) {
            append(query);
            return this;
        }

    }

    /**
     * INSERT QueryBuilder 클래스입니다.
     */
    public static class InsertQueryBuilder extends BaseQueryBuilder<InsertQueryBuilder> {

        private int columnCount = 0;
        private boolean hasOnConflict = false;

        /**
         * INSERT QueryBuilder 를 생성합니다.
         *
         * @param tableName INSERT 를 실행할 테이블명
         * @param columns   INSERT 할 컬럼
         */
        InsertQueryBuilder(String tableName, String[] columns) {
            super();

            append("INSERT INTO ").append(tableName);

            String columnsStr = String.join(", ", columns);
            String[] valueList = Arrays.stream(columns)
                .map(col -> "#{" + col + "}")
                .toArray(String[]::new);
            String valuesStr = String.join(", ", valueList);

            columnCount = columns.length;

            appendNotSpace("(").append(columnsStr).append(")");
            append("VALUES");
            appendNotSpace("(").append(valuesStr).append(")");
        }

        /**
         * ON CONFLICT 절을 추가합니다.
         *
         * @param conflictColumns 충돌 컬럼 (예: "user_id, mission_id, mission_date")
         * @return ON CONFLICT 절이 추가된 INSERT QueryBuilder
         */
        public InsertQueryBuilder onConflict(String conflictColumns) {
            if (hasOnConflict) {
                throw new IllegalStateException("ON CONFLICT is already specified");
            }
            append("ON CONFLICT");
            appendNotSpace("(").append(conflictColumns).append(")");
            hasOnConflict = true;
            return this;
        }

        /**
         * DO UPDATE SET 절을 추가합니다.
         *
         * @param updateColumns 업데이트할 컬럼 (예: "current_count", "reset_at")
         * @return DO UPDATE SET 절이 추가된 INSERT QueryBuilder
         */
        public InsertQueryBuilder doUpdate(String... updateColumns) {
            if (!hasOnConflict) {
                throw new IllegalStateException("ON CONFLICT must be specified before DO UPDATE");
            }

            List<String> sets = new ArrayList<>();
            for (String column : updateColumns) {
                sets.add(column + " = EXCLUDED." + column);
            }

            append("DO UPDATE SET");
            append(String.join(", ", sets));
            return this;
        }

        /**
         * DO UPDATE SET 절을 추가합니다 (컬럼 증가용).
         *
         * @param incrementColumn 증가시킬 컬럼 (예: "current_count")
         * @return DO UPDATE SET 절이 추가된 INSERT QueryBuilder
         */
        public InsertQueryBuilder doUpdateIncrement(String incrementColumn) {
            if (!hasOnConflict) {
                throw new IllegalStateException("ON CONFLICT must be specified before DO UPDATE");
            }

            append("DO UPDATE SET");
            append(incrementColumn + " = " + incrementColumn + " + 1");
            return this;
        }

        /**
         * DO UPDATE SET 절을 추가합니다 (커스텀 업데이트).
         *
         * @param updateExpression 업데이트 표현식 (예: "current_count = EXCLUDED.current_count, reset_at = EXCLUDED.reset_at")
         * @return DO UPDATE SET 절이 추가된 INSERT QueryBuilder
         */
        public InsertQueryBuilder doUpdateCustom(String updateExpression) {
            if (!hasOnConflict) {
                throw new IllegalStateException("ON CONFLICT must be specified before DO UPDATE");
            }

            append("DO UPDATE SET");
            append(updateExpression);
            return this;
        }

        /**
         * DO NOTHING 절을 추가합니다.
         *
         * @return DO NOTHING 절이 추가된 INSERT QueryBuilder
         */
        public InsertQueryBuilder doNothing() {
            if (!hasOnConflict) {
                throw new IllegalStateException("ON CONFLICT must be specified before DO NOTHING");
            }

            append("DO NOTHING");
            return this;
        }

        /**
         * RETURNING 절을 추가합니다.
         *
         * @param columns 반환할 컬럼 (예: "id, user_id, mission_id")
         * @return RETURNING 절이 추가된 INSERT QueryBuilder
         */
        public InsertQueryBuilder returningColumns(String columns) {
            append("RETURNING ");
            append(columns);
            return this;
        }
    }

    /**
     * UPDATE QueryBuilder 클래스입니다.
     */
    public static class UpdateQueryBuilder extends BaseQueryBuilder<UpdateQueryBuilder> {

        private int columnCount = 0;

        /**
         * UPDATE QueryBuilder 를 생성합니다.
         *
         * @param tableName UPDATE 를 실행할 테이블명
         */
        UpdateQueryBuilder(String tableName) {
            super();

            append("UPDATE ").append(tableName);
            append("SET");
        }

        /**
         * UPDATE QueryBuilder 를 생성합니다.
         *
         * @param tableName UPDATE 를 실행할 테이블명
         * @param columns   UPDATE 할 컬럼
         */
        UpdateQueryBuilder(String tableName, String[] columns) {
            super();

            append("UPDATE ").append(tableName);

            List<String> sets = new ArrayList<String>();
            Arrays.asList(columns).forEach(column -> {
                sets.add(column + " = #{" + column + "}");
            });

            columnCount = sets.size();

            this.append("SET ")
                .append(String.join(", ", sets));
        }

        /**
         * UPDATE QueryBuilder 에 컬럼이 1개 이상인지 체크합니다.
         *
         * @return UPDATE QueryBuilder 에 컬럼이 1개 이상이면 true, 아니면 false
         */
        protected boolean hasColumns() {
            return columnCount > 0;
        }

        /**
         * UPDATE QueryBuilder 에 입력된 컬럼을 추가합니다.
         *
         * @param str 추가할 컬럼
         * @return 컬럼을 추가한 UPDATE QueryBuilder
         */
        protected StringBuilder commaAppend(String str) {
            if (hasColumns()) {
                return this.appendNotSpace(", ").append(str);
            } else {
                return this.append(str);
            }
        }

        /**
         * UPDATE QueryBuilder 에 입력된 컬럼의 값을 1 증가 시켜주는 문자열을 추가합니다.
         *
         * @param column 값을 1 증가 시켜줄 컬럼
         * @return 입력된 컬럼의 값을 1 증가 시켜주는 문자열이 추가된 UPDATE QueryBuilder
         */
        public UpdateQueryBuilder increase(String column) {
            return this.increase(column, 1);
        }

        /**
         * UPDATE QueryBuilder 에 입력된 컬럼의 값을 amount 만큼 증가 시켜주는 문자열을 추가합니다.
         *
         * @param column 값을 증가 시켜줄 컬럼
         * @param amount 증가 시켜줄 크기
         * @return 입력된 컬럼의 값을 amount 만큼 증가 시켜주는 문자열이 추가된 UPDATE QueryBuilder
         */
        public UpdateQueryBuilder increase(String column, int amount) {
            this.commaAppend(column).append("=").append(column).append("+" + amount);
            columnCount++;
            return this;
        }

        /**
         * UPDATE QueryBuilder 에 입력된 컬럼의 값을 1 감소 시켜주는 문자열을 추가합니다.
         *
         * @param column 값을 1 감소 시켜줄 컬럼
         * @return 입력된 컬럼의 값을 1 감소 시켜주는 문자열이 추가된 UPDATE QueryBuilder
         */
        public UpdateQueryBuilder decrease(String column) {
            return this.decrease(column, 1);
        }

        /**
         * UPDATE QueryBuilder 에 입력된 컬럼의 값을 amount 만큼 감소 시켜주는 문자열을 추가합니다.
         *
         * @param column 값을 감소 시켜줄 컬럼
         * @param amount 감소 시켜줄 크기
         * @return 입력된 컬럼의 값을 amount 만큼 감소 시켜주는 문자열이 추가된 UPDATE QueryBuilder
         */
        public UpdateQueryBuilder decrease(String column, int amount) {
            this.commaAppend(column).append("=").append(column).append("-" + amount);
            columnCount++;
            return this;
        }

        /**
         * UPDATE QueryBuilder 에 입력된 컬럼의 값을 현재시간으로 업데이트하는 문자열을 추가합니다.
         *
         * @param column 현제시간으로 업데이트할 컬럼
         * @return 입력된 컬럼이 값을 현재 시간으로 업데이트 시켜주는 문자열을 추가한 UPDATE QueryBuilder
         */
        public UpdateQueryBuilder timestamp(String column) {
            this.commaAppend(column).append(" = ").append("CURRENT_TIMESTAMP");
            columnCount++;
            return this;
        }

        /**
         * UPDATE QueryBuilder 에 mdfcn_dt 를 현재시간으로 업데이트하는 문자열을 추가합니다.
         *
         * @return mdfcn_dt 를 현재시간으로 업데이트하는 문자열을 추가한 UPDATE QueryBuilder
         */
        public UpdateQueryBuilder updateTimestamp() {
            return this.timestamp("mdfcn_dt");
        }

        /**
         * UPDATE QueryBuilder 에 last_lgn_dt 를 현재시간으로 업데이트하는 문자열을 추가합니다.
         *
         * @return last_lgn_dt 를 현재시간으로 업데이트하는 문자열을 추가한 UPDATE QueryBuilder
         */
        public UpdateQueryBuilder updateLastLoginTimestamp() {
            return this.timestamp("last_lgn_dt");
        }

        /**
         * UPDATE QueryBuilder 에 del_dt 를 현재시간으로 업데이트하는 문자열을 추가합니다.
         *
         * @return del_dt 를 현재시간으로 업데이트하는 문자열을 추가한 UPDATE QueryBuilder
         */
        public UpdateQueryBuilder deleteTimestamp() {
            return this.timestamp("del_dt");
        }
    }
}
