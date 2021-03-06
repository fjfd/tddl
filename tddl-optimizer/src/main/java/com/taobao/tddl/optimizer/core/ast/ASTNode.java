package com.taobao.tddl.optimizer.core.ast;

import java.util.Map;

import com.taobao.tddl.common.exception.NotSupportException;
import com.taobao.tddl.common.jdbc.ParameterContext;
import com.taobao.tddl.optimizer.core.plan.IDataNodeExecutor;
import com.taobao.tddl.optimizer.exceptions.QueryException;

/**
 * 可优化的语法树
 * 
 * @since 5.1.0
 */
public abstract class ASTNode<RT extends ASTNode> implements Comparable {

    protected String dataNode = null;
    protected Object extra;
    // TODO 该属性待定
    protected String sql;

    public abstract void build();

    /**
     * 构造执行计划
     */
    public abstract IDataNodeExecutor toDataNodeExecutor() throws QueryException;

    public abstract void assignment(Map<Integer, ParameterContext> parameterSettings);

    public abstract boolean isNeedBuild();

    public String getDataNode() {
        return dataNode;
    }

    public RT executeOn(String dataNode) {
        this.dataNode = dataNode;
        return (RT) this;
    }

    public String getSql() {
        return this.sql;
    }

    public RT setSql(String sql) {
        this.sql = sql;
        return (RT) this;
    }

    public abstract String toString(int inden);

    public Object getExtra() {
        return this.extra;
    }

    public void setExtra(Object obj) {
        this.extra = obj;
    }

    public int compareTo(Object arg) {
        // 主要是将自己包装为Comparable对象，可以和Number/string类型具有相同的父类，构建嵌套的查询树
        throw new NotSupportException();
    }

    // ----------------- 复制 ----------------

    public abstract RT deepCopy();

    public abstract RT copy();

}
