package com.taobao.tddl.optimizer.exceptions;

import com.taobao.tddl.common.exception.TddlException;

/**
 * @author jianghang 2013-11-12 下午2:25:55
 * @since 5.1.0
 */
public class SqlParserException extends TddlException {

    private static final long serialVersionUID = 6432150590171245275L;

    public SqlParserException(String errorCode, String errorDesc, Throwable cause){
        super(errorCode, errorDesc, cause);
    }

    public SqlParserException(String errorCode, String errorDesc){
        super(errorCode, errorDesc);
    }

    public SqlParserException(String errorCode, Throwable cause){
        super(errorCode, cause);
    }

    public SqlParserException(String errorCode){
        super(errorCode);
    }

    public SqlParserException(Throwable cause){
        super(cause);
    }

}
