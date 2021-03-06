package com.taobao.tddl.optimizer.rule;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.taobao.tddl.common.exception.TddlRuntimeException;
import com.taobao.tddl.common.utils.TStringUtil;
import com.taobao.tddl.optimizer.core.ASTNodeFactory;
import com.taobao.tddl.optimizer.core.expression.IBooleanFilter;
import com.taobao.tddl.optimizer.core.expression.IColumn;
import com.taobao.tddl.optimizer.core.expression.IFilter;
import com.taobao.tddl.optimizer.core.expression.IFilter.OPERATION;
import com.taobao.tddl.optimizer.core.expression.IFunction;
import com.taobao.tddl.optimizer.core.expression.ILogicalFilter;
import com.taobao.tddl.optimizer.utils.OptimizerUtils;
import com.taobao.tddl.rule.TableRule;
import com.taobao.tddl.rule.TddlRule;
import com.taobao.tddl.rule.VirtualTableRoot;
import com.taobao.tddl.rule.exceptions.RouteCompareDiffException;
import com.taobao.tddl.rule.model.MatcherResult;
import com.taobao.tddl.rule.model.TargetDB;
import com.taobao.tddl.rule.model.sqljep.Comparative;
import com.taobao.tddl.rule.model.sqljep.ComparativeAND;
import com.taobao.tddl.rule.model.sqljep.ComparativeBaseList;
import com.taobao.tddl.rule.model.sqljep.ComparativeMapChoicer;
import com.taobao.tddl.rule.model.sqljep.ComparativeOR;

/**
 * 优化器中使用Tddl Rule的一些工具方法，需要依赖{@linkplain TddlRule}自己先做好初始化
 * 
 * @since 5.1.0
 */
public class OptimizerRule {

    private final static int DEFAULT_OPERATION_COMP = -1000;
    private TddlRule         tddlRule;

    public OptimizerRule(TddlRule tddlRule){
        this.tddlRule = tddlRule;
    }

    /**
     * 根据逻辑表和条件，计算一下目标库
     */
    public List<TargetDB> shard(String logicTable, final IFilter ifilter, boolean isWrite) {
        MatcherResult result;
        try {
            result = tddlRule.routeMverAndCompare(!isWrite, logicTable, new ComparativeMapChoicer() {

                public Map<String, Comparative> getColumnsMap(List<Object> arguments, Set<String> partnationSet) {
                    Map<String, Comparative> map = new HashMap<String, Comparative>();
                    for (String str : partnationSet) {
                        map.put(str, getColumnComparative(arguments, str));
                    }

                    return map;
                }

                public Comparative getColumnComparative(List<Object> arguments, String colName) {
                    return getComparative(ifilter, colName);
                }
            }, Lists.newArrayList());
        } catch (RouteCompareDiffException e) {
            throw new TddlRuntimeException(e);
        }

        List<TargetDB> targetDbs = result.getCalculationResult();
        if (targetDbs == null || targetDbs.isEmpty()) {
            throw new IllegalArgumentException("can't find target db. table is " + logicTable + ". fiter is " + ifilter);
        }

        return targetDbs;
    }

    /**
     * 允许定义期望的expectedGroups，如果broadcast的逻辑表的物理拓扑结构包含了该节点，那说明可以做本地节点join，下推sql
     */
    public List<TargetDB> shardBroadCast(String logicTable, final IFilter ifilter, boolean isWrite,
                                         List<String> expectedGroups) {
        if (expectedGroups == null) {
            return this.shard(logicTable, ifilter, isWrite);
        }

        if (!isBroadCast(logicTable)) {
            throw new TddlRuntimeException(logicTable + "不是broadCast的表");
        }

        List<TargetDB> targets = this.shard(logicTable, null, isWrite);
        List<TargetDB> targetsMatched = new ArrayList<TargetDB>();
        for (TargetDB target : targets) {
            if (expectedGroups.contains(target.getDbIndex())) {
                targetsMatched.add(target);
            }
        }

        return targetsMatched;

    }

    /**
     * 根据逻辑表返回一个随机的物理目标库TargetDB
     * 
     * @param logicTable
     * @return
     */
    public TargetDB shardAny(String logicTable) {
        TableRule tableRule = getTableRule(logicTable);
        for (String group : tableRule.getActualTopology().keySet()) {
            Set<String> tableNames = tableRule.getActualTopology().get(group);
            if (tableNames == null || tableNames.isEmpty()) {
                continue;
            }

            TargetDB target = new TargetDB();
            target.setDbIndex(group);
            target.addOneTable(tableNames.iterator().next());
            return target;
        }

        throw new IllegalArgumentException("can't find any target db. table is " + logicTable + ". ");
    }

    public boolean isSameRule(String t1, String t2) {
        String t1Rule = getJoinGroup(t1);
        String t2Rule = getJoinGroup(t2);
        if (t1 == null || t2 == null) {
            return false;
        }

        return TStringUtil.equals(t1Rule, t2Rule);
    }

    public String getJoinGroup(String logicTable) {
        TableRule table = getTableRule(logicTable);
        return table.getJoinGroup();
    }

    public boolean isBroadCast(String logicTable) {
        TableRule table = getTableRule(logicTable);
        return table.isBroadcast();
    }

    private TableRule getTableRule(String logicTable) {
        VirtualTableRoot root = tddlRule.getCurrentRule();
        logicTable = logicTable.toLowerCase();
        TableRule table = root.getTableRules().get(logicTable);
        return table;
    }

    /**
     * 将一个{@linkplain IFilter}表达式转化为Tddl Rule所需要的{@linkplain Comparative}对象
     * 
     * @param ifilter
     * @param colName
     * @return
     */
    public static Comparative getComparative(IFilter ifilter, String colName) {
        // 前序遍历，找到所有符合要求的条件
        if (ifilter == null) {
            return null;
        }

        if ("NOT".equalsIgnoreCase(ifilter.getFunctionName())) {
            return null;
        }

        if (ifilter instanceof ILogicalFilter) {
            if (ifilter.isNot()) {
                return null;
            }

            ComparativeBaseList comp = null;
            ILogicalFilter logicalFilter = (ILogicalFilter) ifilter;
            switch (ifilter.getOperation()) {
                case AND:
                    comp = new ComparativeAND();
                    break;
                case OR:
                    comp = new ComparativeOR();
                    break;
                default:
                    throw new IllegalArgumentException();
            }

            for (Object sub : logicalFilter.getSubFilter()) {
                if (!(sub instanceof IFilter)) {
                    return null;
                }

                IFilter subFilter = (IFilter) sub;
                Comparative subComp = getComparative(subFilter, colName);// 递归
                if (subComp != null) {
                    comp.addComparative(subComp);
                }

            }

            if (comp == null || comp.getList() == null || comp.getList().isEmpty()) {
                return null;
            } else if (comp.getList().size() == 1) {
                return comp.getList().get(0);// 可能只有自己一个and
            }
            return comp;
        } else if (ifilter instanceof IBooleanFilter) {
            Comparative comp = null;
            IBooleanFilter booleanFilter = (IBooleanFilter) ifilter;
            // 判断非常量
            if (!(booleanFilter.getColumn() instanceof IColumn || booleanFilter.getValue() instanceof IColumn)) {
                return null;
            }

            // 判断非空
            if (booleanFilter.getColumn() == null
                || (booleanFilter.getValue() == null && booleanFilter.getValues() == null)) {
                return null;
            }

            if (booleanFilter.isNot()) {
                return null;
            }

            if (booleanFilter.getOperation() == OPERATION.IN) {// in不能出现isReverse
                ComparativeBaseList orComp = new ComparativeOR();
                for (Comparable value : booleanFilter.getValues()) {
                    IBooleanFilter ef = ASTNodeFactory.getInstance().createBooleanFilter();
                    ef.setOperation(OPERATION.EQ);
                    ef.setColumn(booleanFilter.getColumn());
                    ef.setValue(value);

                    Comparative subComp = getComparative(ef, colName);
                    if (subComp != null) {
                        orComp.addComparative(subComp);
                    }
                }

                if (orComp.getList().isEmpty()) {// 所有都被过滤
                    return null;
                }

                return orComp;
            } else {
                int operationComp = DEFAULT_OPERATION_COMP;
                switch (booleanFilter.getOperation()) {
                    case GT:
                        operationComp = Comparative.GreaterThan;
                        break;
                    case EQ:
                        operationComp = Comparative.Equivalent;
                        break;
                    case GT_EQ:
                        operationComp = Comparative.GreaterThanOrEqual;
                        break;
                    case LT:
                        operationComp = Comparative.LessThan;
                        break;
                    case LT_EQ:
                        operationComp = Comparative.LessThanOrEqual;
                    default:
                        return null;
                }

                IColumn column = null;
                Comparable value = null;
                if (booleanFilter.getColumn() instanceof IColumn) {
                    column = OptimizerUtils.getColumn(booleanFilter.getColumn());
                    value = getComparableWhenTypeIsNowReturnDate(booleanFilter.getValue());
                } else {// 出现 1 = id 的写法
                    column = OptimizerUtils.getColumn(booleanFilter.getValue());
                    value = getComparableWhenTypeIsNowReturnDate(booleanFilter.getColumn());
                    operationComp = Comparative.exchangeComparison(operationComp); // 反转一下
                }

                if (colName.equalsIgnoreCase(column.getColumnName()) && operationComp != DEFAULT_OPERATION_COMP) {
                    comp = new Comparative(operationComp, value);
                }

                return comp;
            }
        } else {
            // 为null,全表扫描
            return null;
        }
    }

    private static Comparable getComparableWhenTypeIsNowReturnDate(Comparable val) {
        if (val instanceof IFunction) {
            IFunction func = (IFunction) val;
            if ("NOW".equalsIgnoreCase(func.getFunctionName())) {
                return new Date();
            }
        }

        return val;
    }
}
