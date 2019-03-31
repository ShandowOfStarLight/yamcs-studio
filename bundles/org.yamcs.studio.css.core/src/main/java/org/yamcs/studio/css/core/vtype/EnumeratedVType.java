package org.yamcs.studio.css.core.vtype;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.diirt.vtype.VEnum;
import org.yamcs.protobuf.Mdb.EnumValue;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.studio.css.core.pvmanager.PVConnectionInfo;

public class EnumeratedVType extends YamcsVType implements VEnum {

    private PVConnectionInfo info;

    public EnumeratedVType(PVConnectionInfo info, ParameterValue pval) {
        super(pval);
        this.info = info;
    }

    @Override
    public int getIndex() {
        Value rawValue = pval.getRawValue();
        return getIndexForValue(rawValue);
    }

    static int getIndexForValue(Value value) {
        switch (value.getType()) {
        case UINT32:
            return value.getUint32Value();
        case UINT64:
            return (int) value.getUint64Value();
        case SINT32:
            return value.getSint32Value();
        case SINT64:
            return (int) value.getSint64Value();
        case FLOAT:
            return (int) value.getFloatValue();
        case DOUBLE:
            return (int) value.getDoubleValue();
        case STRING:
            long longValue = Long.decode(value.getStringValue());
            return (int) longValue;
        default:
            return -1;
        }
    }

    @Override
    public String getValue() {
        return pval.getEngValue().getStringValue();
    }

    @Override
    public List<String> getLabels() {
        return getLabelsForType(info.parameter.getType());
    }

    static List<String> getLabelsForType(ParameterTypeInfo ptype) {
        List<EnumValue> enumValues = ptype.getEnumValueList();
        if (enumValues != null) {
            List<String> labels = new ArrayList<>(enumValues.size());
            for (EnumValue enumValue : enumValues) {
                labels.add(enumValue.getLabel());
            }
            return labels;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public String toString() {
        // Use String.valueOf, because it formats a nice "null" string
        // in case it is null
        return String.valueOf(pval.getEngValue().getStringValue());
    }
}
