package org.csstudio.platform.libs.yamcs.vtype;

import org.epics.vtype.VDouble;
import org.yamcs.protostuff.ParameterValue;

public class DoubleVType extends YamcsVType implements VDouble {

    public DoubleVType(ParameterValue pval) {
        super(pval);
    }

    @Override
    public Double getValue() {
        return pval.getEngValue().getDoubleValue();
    }
    
    @Override
    public String toString() {
        return String.valueOf(pval.getEngValue().getDoubleValue());
    }
}