package com.taobao.tddl.optimizer.exceptions;

import com.taobao.tddl.common.exception.TddlRuntimeException;

/**
 * @author jianghang 2013-11-8 下午3:25:08
 * @since 5.1.0
 */
public class FunctionException extends TddlRuntimeException {

    private static final long serialVersionUID = 1786910155001806970L;

    public FunctionException(String errorCode, String errorDesc, Throwable cause){
        super(errorCode, errorDesc, cause);
    }

    public FunctionException(String errorCode, String errorDesc){
        super(errorCode, errorDesc);
    }

    public FunctionException(String errorCode, Throwable cause){
        super(errorCode, cause);
    }

    public FunctionException(String errorCode){
        super(errorCode);
    }

    public FunctionException(Throwable cause){
        super(cause);
    }

}
