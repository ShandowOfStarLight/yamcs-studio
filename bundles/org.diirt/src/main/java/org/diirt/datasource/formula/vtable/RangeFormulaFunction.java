/**
 * Copyright (C) 2010-14 diirt developers. See COPYRIGHT.TXT
 * All rights reserved. Use is subject to license terms. See LICENSE.TXT
 */
package org.diirt.datasource.formula.vtable;

import java.util.Arrays;
import java.util.List;
import org.diirt.datasource.formula.FormulaFunction;
import org.diirt.vtype.VNumber;
import org.diirt.vtype.table.ListNumberProvider;
import org.diirt.vtype.table.VTableFactory;

/**
 *
 * @author carcassi
 */
class RangeFormulaFunction implements FormulaFunction {

    @Override
    public boolean isPure() {
        return true;
    }

    @Override
    public boolean isVarArgs() {
        return false;
    }

    @Override
    public String getName() {
        return "range";
    }

    @Override
    public String getDescription() {
        return "A generator for values between a range";
    }

    @Override
    public List<Class<?>> getArgumentTypes() {
        return Arrays.<Class<?>>asList(VNumber.class, VNumber.class);
    }

    @Override
    public List<String> getArgumentNames() {
        return Arrays.asList("minValue", "maxValue");
    }

    @Override
    public Class<?> getReturnType() {
        return ListNumberProvider.class;
    }

    @Override
    public Object calculate(final List<Object> args) {
        VNumber minValue = (VNumber) args.get(0);
        VNumber maxValue = (VNumber) args.get(1);

        if (minValue == null || maxValue == null) {
            return null;
        }

        return VTableFactory.range(minValue.getValue().doubleValue(), maxValue.getValue().doubleValue());
    }

}
