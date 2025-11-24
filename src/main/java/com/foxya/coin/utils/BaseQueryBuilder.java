package com.foxya.coin.utils;

/**
 * 쿼리문 관련 유틸 클래스
 */
public abstract class BaseQueryBuilder<SELF> {
    
    private static final String SPACE = " ";
    
    private final StringBuilder sbuf = new StringBuilder();
    private int index = 0;
    
    /**
     * 공백 + 문자열 추가
     *
     * @param str 문자열
     * @return StringBuilder
     */
    protected StringBuilder append(String str) {
        return sbuf.append(SPACE).append(str);
    }
    
    /**
     * 문자열 추가
     *
     * @param str 문자열
     * @return StringBuilder
     */
    protected StringBuilder appendNotSpace(String str) {
        return sbuf.append(str);
    }
    
    /**
     * WHERE / AND 판별을 위한 index 판별
     *
     * @return index가 0 이상이면 true, 아니라면 false
     */
    public boolean hasCondition() {
        return this.index > 0;
    }
    
    @SuppressWarnings("unchecked")
    /**
     * 컬럼 + 조건 + 파라미터 매핑 추가
     * @param column 컬럼명
     * @param op 조건
     * @param mapping 매핑 문자열
     * @return 문자열이 추가된 StringBuilder
     * */
    public SELF appendParam(String column, Op op, String... mapping) {
        append(column);
        append(op.value());
        
        switch (op) {
            case IsNotNull:
            case IsNull:
                paramIndex();
                break;
            case Between:
                append("#{").append(mapping[0]).append("}").append(" and ").append("#{").append(mapping[0]).append("}");
                paramIndex();
                break;
            case In:
                append("(").append("#{").append(mapping[0]).append("}").append(")");
                paramIndex();
                break;
            case NotIn:
                append("(").append("#{").append(mapping[0]).append("}").append(")");
                paramIndex();
                break;
            default:
                append("#{").append(mapping[0]).append("}");
                paramIndex();
                break;
        }
        
        return (SELF) this;
    }
    
    @SuppressWarnings("unchecked")
    /**
     * 컬럼 + 조건 + 두번째 컬럼 추가
     * @param column 컬럼명
     * @param op 조건
     * @param secondColumn 두번째 컬럼
     * @return 문자열이 추가된 StringBuilder
     * */
    public SELF appendJoinParam(String column, Op op, String secondColumn) {
        paramIndex();
        
        append(column);
        append(op.value());
        append(secondColumn);
        
        return (SELF) this;
    }
    
    /**
     * 컬럼 + 조건 + 두번째 컬럼 리스트 추가
     *
     * @param column       컬럼명
     * @param op           조건
     * @param secondColumn , 구분 문자열
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF appendListParam(String column, Op op, String secondColumn) {
        if (!this.hasCondition()) {
            append("WHERE");
        } else {
            append("OR");
        }
        paramIndex();
        
        String[] columns = secondColumn.split(",");
        append(column);
        append(op.value());
        append("(");
        append("#{" + columns[0] + "}");
        for (int i = 1; i < columns.length; i++) {
            append(",");
            append("#{" + columns[i] + "}");
        }
        append(")");
        
        return (SELF) this;
    }
    
    /**
     * WHERE, 컬럼, 조건 추가
     *
     * @param column 컬럼명
     * @param op     조건
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF where(String column, Op op) {
        append("WHERE");
        return this.appendParam(column, op);
    }
    
    /**
     * HAVING, 컬럼, 조건 추가
     *
     * @param column 컬럼명
     * @param op     조건
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF having(String column, Op op) {
        append("HAVING");
        return this.appendParam(column, op);
    }
    
    /**
     * WHERE, 컬럼, 조건, 파라미터 매핑 추가
     *
     * @param column  컬럼명
     * @param op      조건
     * @param mapping 매핑 문자열
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF where(String column, Op op, String mapping) {
        append("WHERE");
        return this.appendParam(column, op, mapping);
    }
    
    public SELF where(String query) {
        append("WHERE");
        append(query);
        paramIndex();
        return (SELF) this;
    }
    
    /**
     * HAVING, 컬럼, 조건, 파라미터 매핑 추가
     *
     * @param column  컬럼명
     * @param op      조건
     * @param mapping 매핑 문자열
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF having(String column, Op op, String mapping) {
        append("HAVING");
        return this.appendParam(column, op, mapping);
    }
    
    /**
     * WHERE, 컬럼, 조건, 문자열 추가
     *
     * @param column       컬럼명
     * @param op           조건
     * @param secondColumn 두번째 컬럼명
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF joinWhere(String column, Op op, String secondColumn) {
        append("WHERE");
        return this.appendJoinParam(column, op, secondColumn);
    }
    
    /**
     * HAVING, 컬럼, 조건, 문자열 추가
     *
     * @param column       컬럼명
     * @param op           조건
     * @param secondColumn 두번째 컬럼명
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF joinHaving(String column, Op op, String secondColumn) {
        append("HAVING");
        return this.appendJoinParam(column, op, secondColumn);
    }
    
    /**
     * DB ID 매핑 추가
     *
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF whereById() {
        return where("id", Op.Equal, "id");
    }
    
    /**
     * 충전기 식별값 매핑 추가
     *
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF whereByChrgrIdntfr() {
        return where("chrgr_idntfr", Op.Equal, "chrgr_idntfr");
    }
    
    /**
     * AND + 컬럼 + 조건 추가
     *
     * @param column 컬럼명
     * @param op     조건
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF andWhere(String column, Op op) {
        if (!this.hasCondition()) {
            return this.where(column, op);
        }
        
        append("AND");
        return this.appendParam(column, op);
    }
    
    /**
     * AND + 컬럼 + 조건 + 파라미터 매핑 추가
     *
     * @param column  컬럼명
     * @param op      조건
     * @param mapping 매핑 문자열
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF andWhere(String column, Op op, String mapping) {
        if (!this.hasCondition()) {
            return this.where(column, op, mapping);
        }
        
        append("AND");
        return this.appendParam(column, op, mapping);
    }
    
    public SELF andWhere(String query) {
        if (!this.hasCondition()) {
            return this.where(query);
        }
        
        append("AND");
        append(query);
        paramIndex();
        return (SELF) this;
    }
    
    /**
     * OR + 컬럼 + 조건 추가
     *
     * @param column 컬럼명
     * @param op     조건
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF orWhere(String column, Op op) {
        if (!this.hasCondition()) {
            return this.where(column, op);
        }
        
        append("OR");
        return this.appendParam(column, op);
    }
    
    /**
     * OR + 컬럼 + 조건 + 파라미터 매핑 추가
     *
     * @param column  컬럼명
     * @param op      조건
     * @param mapping 매핑 문자열
     * @return 문자열이 추가된 StringBuilder
     */
    public SELF orWhere(String column, Op op, String mapping) {
        if (!this.hasCondition()) {
            return this.where(column, op, mapping);
        }
        
        append("OR");
        return this.appendParam(column, op, mapping);
    }
    
    public SELF orWhere(String query) {
        if (!this.hasCondition()) {
            return this.where(query);
        }
        
        append("OR");
        append(query);
        paramIndex();
        return (SELF) this;
    }
    
    /**
     * RETURNING * 추가
     *
     * @return 리턴문 추가 후 쿼리문 문자열
     */
    public String returning() {
        return returning("*");
    }
    
    /**
     * WHERE / AND 판별용 index 증가 후 반환
     *
     * @return index
     */
    public int paramIndex() {
        return ++index;
    }
    
    /**
     * RETURNING + 컬럼 추가
     *
     * @param columns 컬럼명
     * @return 문자열 추가 후 쿼리문 문자열
     */
    public String returning(String columns) {
        append("RETURNING ").append(columns);
        return build();
    }
    
    /**
     * StringBuilder 문자열 변환 후 반환
     *
     * @return 완성 문자열
     */
    public String build() {
        return sbuf.toString();
    }
    
    /**
     * 조건문 ENUM
     */
    public enum Op {
        /**
         * =
         */
        Equal("="),
        /**
         * greater than
         */
        GreaterThan(">"),
        /**
         * less than
         */
        LessThan("<"),
        /**
         * greater than or equal
         */
        GreaterThanOrEqual(">="),
        /**
         * less than or equal
         */
        LessThanOrEqual("<="),
        /**
         * not equal
         */
        NotEqual("<>"),
        /**
         * Between a certain range
         */
        Between("BETWEEN"),
        /**
         * Search for a pattern
         */
        Like("LIKE"),
        /**
         * Search for a not case-sensitive pattern
         */
        Ilike("ILIKE"),
        NotLike("NOT LIKE"),
        /**
         * To specify multiple possible values for a column
         */
        In("= ANY"),
        NotIn("NOT IN"),
        /**
         * select only the records with no NULL values
         */
        IsNotNull("IS NOT NULL"),
        /**
         * select only the records with NULL values
         */
        IsNull("IS NULL"),
        SIMILAR("similar to"),
        InIn("IN");
        
        private final String op;
        
        Op(String op) {
            this.op = op;
        }
        
        public String value() {
            return op;
        }
        
        /**
         * 받은 name과 일치하는 ENUM 반환
         *
         * @param name 찾을 ENUM
         * @return 일치하는 ENUM, 없다면 null
         */
        public static Op of(String name) {
            for (Op op : Op.values()) {
                if (op.name().equalsIgnoreCase(name)) {
                    return op;
                }
            }
            return null;
        }
    }
    
    /**
     * 정렬문 ENUM
     */
    public enum Sort {
        /**
         * ASC
         */
        ASC,
        /**
         * DESC
         */
        DESC
    }
}
